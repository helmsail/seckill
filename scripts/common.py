#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""公共基座:配置加载(deploy/.env)+ SSH 原语 + k6 自举(所有脚本 import,不直接运行)。

配置唯一来源 = deploy/.env:节点表/凭据/压测参数全部从那里读取(要改就改 .env,脚本零默认值)。
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts' / '_pylib'))

import paramiko  # noqa: E402


def load_env():
    """解析 deploy/.env(KEY=VALUE;忽略注释与空行)。"""
    path = ROOT / 'deploy' / '.env'
    if not path.exists():
        sys.exit('!! 缺少配置文件: %s(参考 deploy/.env.example 创建)' % path)
    env = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        s = line.strip()
        if not s or s.startswith('#') or '=' not in s:
            continue
        k, v = s.split('=', 1)
        env[k.strip()] = v.strip()
    return env


ENV = load_env()


def require(key):
    """必填配置:缺失直接退出(脚本内不保留任何默认值)。"""
    v = ENV.get(key, '')
    if v == '':
        sys.exit('!! deploy/.env 缺少必填项: %s(见 deploy/.env.example)' % key)
    return v


# ---- 凭据与常量(全部来自 .env)----
SSH_PWD = require('SSH_PASSWORD')
ACR = require('IMAGE_REGISTRY')
ACR_USER = require('ACR_USERNAME')
ACR_PASS = require('ACR_PASSWORD')
MYSQL_PWD = require('MYSQL_PASSWORD')
K6_DIR = require('K6_DIR')

# ---- 节点表(NODE_角色=公网IP,内网IP;角色名统一小写)----
NODES = {}
for _k, _v in ENV.items():
    if _k.startswith('NODE_') and ',' in _v:
        _pub, _priv = _v.split(',', 1)
        NODES[_k[len('NODE_'):].lower()] = (_pub.strip(), _priv.strip())

# ---- 压测机(非生产;未重建时允许为空,相关脚本会给出提示)----
K6_PUB = ENV.get('K6_PUB', '')
K6_PRIV = ENV.get('K6_PRIV', '')


def ssh(target, timeout=15):
    """按角色名或 IP 建立 SSH 连接,返回 paramiko client。"""
    pub = NODES[target][0] if target in NODES else target
    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(pub, username='root', password=SSH_PWD,
                timeout=timeout, banner_timeout=timeout, auth_timeout=timeout)
    return cli


def run(target, cmd, timeout=60):
    """执行命令,返回 stdout(stderr 兜底;异常返回 'FAIL: ...')。"""
    try:
        cli = ssh(target)
        _, so, se = cli.exec_command(cmd, timeout=timeout)
        out = so.read().decode(errors='replace').strip()
        err = se.read().decode(errors='replace').strip()
        cli.close()
        return out or err
    except Exception as e:
        return 'FAIL: %s' % str(e)[:80]


# ---- k6 压测机:文件推送 / 自举(供 deploy_push、rollout、bench 共用)----

def k6_push_files():
    """推送 k6 运行文件(脚本 + compose + .env)到压测机。"""
    if not K6_PUB:
        raise RuntimeError('K6_PUB 未配置(deploy/.env【A】区;压测机重建后填入)')
    cli = ssh(K6_PUB, timeout=20)
    cli.exec_command('mkdir -p %s' % K6_DIR, timeout=30)
    sftp = cli.open_sftp()
    sftp.put(str(ROOT / 'deploy' / 'k6' / 'seckill-loadtest.js'), K6_DIR + '/seckill-loadtest.js')
    sftp.put(str(ROOT / 'deploy' / 'docker-compose.k6.yml'), K6_DIR + '/docker-compose.k6.yml')
    sftp.put(str(ROOT / 'deploy' / '.env'), K6_DIR + '/.env')
    sftp.close()
    cli.close()


def k6_bootstrap():
    """k6 压测机自举(幂等):TCP 调优 + 推文件 + ACR 登录 + 拉镜像。"""
    if not K6_PUB:
        raise RuntimeError('K6_PUB 未配置(deploy/.env【A】区;压测机重建后填入)')
    run(K6_PUB, 'sysctl -w net.ipv4.ip_local_port_range="1024 65535" >/dev/null; '
                'sysctl -w net.ipv4.tcp_tw_reuse=1 >/dev/null; '
                'sysctl -w net.ipv4.tcp_max_tw_buckets=2000000 >/dev/null; '
                'sysctl -n net.ipv4.ip_local_port_range', timeout=60)
    k6_push_files()
    print('  [k6] %s' % run(K6_PUB, 'docker login --username=%s --password=%s %s 2>&1 | tail -1'
                                % (ACR_USER, ACR_PASS, ACR), timeout=120), flush=True)
    print('  [k6] %s' % run(K6_PUB, 'cd %s && docker compose -f docker-compose.k6.yml pull -q && echo 镜像就绪' % K6_DIR,
                           timeout=600), flush=True)
