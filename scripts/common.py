#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""公共基座:配置加载(deploy/.env:节点表/角色表/凭据/压测参数)+ SSH 原语 + k6 自举。

所有脚本 import 本文件(脚本之间不互相 import);配置唯一来源 = deploy/.env。
"""
import re
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


# ---- 公用:目标列表 / 角色参数(检查类、动作类脚本共用)----

def all_targets():
    """[(显示名, run 目标)]:全部 15 角色 + k6(已配置时)。"""
    t = [(r, r) for r in NODES]
    if K6_PUB:
        t.append(('k6', K6_PUB))
    return t


def roles_from_args(roles_map):
    """解析命令行角色参数(无参=全部;未知项提示)。"""
    args = sys.argv[1:]
    if not args:
        return list(roles_map)
    out = []
    for a in args:
        if a in roles_map:
            out.append(a)
        else:
            print('!! 未知角色: %s(可用: %s)' % (a, ' '.join(roles_map)))
    return out


def load_roles():
    """角色表:ROLE_角色=compose列表|附属列表|env覆盖(K=V,…)|远端目录。"""
    roles = {}
    for k, v in ENV.items():
        if not k.startswith('ROLE_') or '|' not in v:
            continue
        parts = (v.split('|') + ['', '', '', ''])[:4]
        roles[k[5:].lower()] = (
            [c for c in parts[0].split(',') if c],
            [f for f in parts[1].split(',') if f],
            [tuple(x.split('=', 1)) for x in parts[2].split(',') if '=' in x],
            parts[3] or '/root/seckill')
    return roles


ROLES = load_roles()


# ---- 环境就绪:确保 Docker 与日志轮转(幂等;换新机由 install_docker.py 补齐)----

ENSURE_DOCKER = r'''if command -v docker >/dev/null 2>&1; then echo "docker ok: $(docker --version)"; else
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq 2>&1 | tail -1
apt-get -o DPkg::Lock::Timeout=300 install -y -qq docker.io docker-compose-v2 2>&1 | tail -4
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {"max-size": "50m", "max-file": "2"}
}
EOF
systemctl restart docker >/dev/null 2>&1
sleep 1
if command -v docker >/dev/null 2>&1; then echo "已安装: $(docker --version) | $(docker compose version 2>/dev/null | head -1)"; else echo "FAIL 安装失败(apt 输出见上方)"; fi
fi'''


def ensure_docker(target):
    """确保目标机 Docker 就绪(幂等):未装则安装 + 日志轮转配置。"""
    return run(target, ENSURE_DOCKER, timeout=900)


# ---- 部署 .env 生成(全机共用:deploy/.env 唯一事实源,地址类键按节点表动态替换)----

def set_line(text, key, value):
    """按行替换 KEY=VALUE(动态生成部署地址,不依赖模板旧值)。"""
    return re.sub(r'(?m)^%s=.*$' % re.escape(key), lambda m: '%s=%s' % (key, value), text)


def render_env(host_priv, extra=()):
    """为目标机生成 .env:地址行全部动态替换(项目机与 k6 压测机共用;新增地址配置只改这里)。"""
    text = (ROOT / 'deploy' / '.env').read_text(encoding='utf-8')
    web = 'http://%s:%s' % (NODES['web'][1], require('WEB_PORT'))
    gw_port = require('GATEWAY_PORT')
    mq = NODES['mixmq'][1]
    text = set_line(text, 'HOST_IP', host_priv)
    text = set_line(text, 'MYSQL_HOST', NODES['mysql'][1])
    text = set_line(text, 'REDIS_HOST', NODES['redis'][1])
    text = set_line(text, 'NACOS_ADDR', mq + ':8848')
    text = set_line(text, 'SENTINEL_DASHBOARD_ADDR', mq + ':' + require('SENTINEL_PORT'))
    text = set_line(text, 'ROCKETMQ_ADDR', mq + ':9876')
    text = set_line(text, 'XXL_ADMIN_ADDR', 'http://%s:%s/xxl-job-admin' % (mq, require('XXL_ADMIN_PORT')))
    text = set_line(text, 'MOCK_PAY_CALLBACK_URL', web + '/api/c/pay/callback/mock')
    text = set_line(text, 'MOCK_PAY_STATUS_URL', web + '/api/c/order/status')
    text = set_line(text, 'GATEWAY1_ADDR', '%s:%s' % (NODES['gw1'][1], gw_port))
    text = set_line(text, 'GATEWAY2_ADDR', '%s:%s' % (NODES['gw2'][1], gw_port))
    text = set_line(text, 'GATEWAY3_ADDR', '%s:%s' % (NODES['gw3'][1], gw_port))
    text = set_line(text, 'BASE_URL', web)  # k6 压测入口 = web 内网
    for key, value in extra:
        text = set_line(text, key, value)
    return text


# ---- k6 压测机:文件推送 / 自举(供 deploy_push / bench 共用)----

def k6_push_files():
    """推送 k6 运行文件(脚本 + compose + .env)到压测机(.env 地址按节点表自动替换)。"""
    if not K6_PUB:
        raise RuntimeError('K6_PUB 未配置(deploy/.env【A】区;压测机重建后填入)')
    cli = ssh(K6_PUB, timeout=20)
    cli.exec_command('mkdir -p %s' % K6_DIR, timeout=30)
    sftp = cli.open_sftp()
    sftp.put(str(ROOT / 'deploy' / 'k6' / 'seckill-loadtest.js'), K6_DIR + '/seckill-loadtest.js')
    sftp.put(str(ROOT / 'deploy' / 'docker-compose.k6.yml'), K6_DIR + '/docker-compose.k6.yml')
    with sftp.file(K6_DIR + '/.env', 'w') as fh:
        fh.write(render_env(K6_PRIV or ''))
    sftp.close()
    cli.close()


def k6_bootstrap():
    """k6 压测机自举(幂等):Docker 就绪 + TCP 调优 + 推文件 + ACR 登录 + 拉镜像。"""
    if not K6_PUB:
        raise RuntimeError('K6_PUB 未配置(deploy/.env【A】区;压测机重建后填入)')
    print('  [k6] %s' % ensure_docker(K6_PUB), flush=True)
    print('  [k6] %s' % run(K6_PUB, 'sysctl -w net.ipv4.ip_local_port_range="1024 65535" >/dev/null; '
                                    'sysctl -w net.ipv4.tcp_tw_reuse=1 >/dev/null; '
                                    'sysctl -w net.ipv4.tcp_max_tw_buckets=2000000 >/dev/null; '
                                    'sysctl -w fs.file-max=2097152 >/dev/null; '
                                    'sysctl -w net.netfilter.nf_conntrack_max=2097152 >/dev/null 2>&1; '
                                    'echo "端口池=$(sysctl -n net.ipv4.ip_local_port_range) '
                                    'conntrack=$(sysctl -n net.netfilter.nf_conntrack_max 2>/dev/null || echo n/a)"',
                               timeout=60), flush=True)
    k6_push_files()
    print('  [k6] %s' % run(K6_PUB, 'docker login --username=%s --password=%s %s 2>&1 | tail -1'
                                % (ACR_USER, ACR_PASS, ACR), timeout=120), flush=True)
    print('  [k6] %s' % run(K6_PUB, 'cd %s && docker compose -f docker-compose.k6.yml pull -q && echo 镜像就绪' % K6_DIR,
                           timeout=600), flush=True)
