#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""② 部署推送:本地 deploy/ 是唯一事实源;推送 compose/.env/附属文件到目标角色。

用法(在 d:\\seckill 下执行):
    python scripts/deploy_push.py list        # 列出角色、目标机与文件清单
    python scripts/deploy_push.py web gw1     # 推送指定角色
    python scripts/deploy_push.py k6          # 只推 k6 压测机文件
    python scripts/deploy_push.py all         # 全量推送(16 角色 + k6)

说明:
  - 推送=只上传(.env 按节点表自动生成:地址行全部动态替换,无需手改);
  - 启动/重建:python scripts/rollout.py(ACR 拉取 + 强制重建);
  - 换机:只改 deploy/.env 的【A】节点表。
"""
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))

from common import K6_PUB, NODES, k6_push_files, require, ssh  # noqa: E402

D = ROOT / 'deploy'

# 角色 → (compose 文件[], 附属文件[](相对 deploy/), env 专属覆盖[(KEY, VALUE)], 远端目录)
ROLES = {
    'web':       (['docker-compose.web.yml'], ['nginx.conf', 'nginx-main.conf'], [], '/root/seckill'),
    'gw1':       (['docker-compose.gateway.yml'], [], [], '/root/seckill'),
    'gw2':       (['docker-compose.gateway.yml'], [], [], '/root/seckill'),
    'gw3':       (['docker-compose.gateway.yml'], [], [], '/root/seckill'),
    'svc1':      (['docker-compose.service.yml'], [], [], '/root/seckill'),
    'svc2':      (['docker-compose.service.yml'], [], [], '/root/seckill'),
    'svc3':      (['docker-compose.service.yml'], [], [], '/root/seckill'),
    'base1':     (['docker-compose.base.yml'], ['log4j2-quiet.xml'],
                  [('IMAGE_TAG', 'v2.1-w0')], '/root/seckill'),
    'base2':     (['docker-compose.base.yml'], ['log4j2-quiet.xml'],
                  [('IMAGE_TAG', 'v2.1-w1'), ('BASE1_SNOWFLAKE_ID', '116')], '/root/seckill'),
    'base3':     (['docker-compose.base.yml'], ['log4j2-quiet.xml'],
                  [('IMAGE_TAG', 'v2.1-w2'), ('BASE1_SNOWFLAKE_ID', '126')], '/root/seckill'),
    'base4':     (['docker-compose.base.yml'], ['log4j2-quiet.xml'],
                  [('IMAGE_TAG', 'v2.1-w3'), ('BASE1_SNOWFLAKE_ID', '136')], '/root/seckill'),
    'processor': (['docker-compose.processor.yml'], [], [], '/root/seckill'),
    'redis':     (['docker-compose.redis.yml'], [], [], '/root/seckill'),
    'support':   (['docker-compose.support.yml'], [], [], '/root/seckill'),
    'mixmq':     (['docker-compose.mixMQ.yml'], ['broker.conf'], [], '/root/seckill'),
    'mysql':     (['docker-compose.mysql.yml'],
                  ['initdb/seckill-init.sql', 'initdb/xxl-job-base.sql', 'initdb/xxl-job-init.sql'], [], '/root/seckill'),
}


def set_line(text, key, value):
    """按行替换 KEY=VALUE(动态生成部署地址,不依赖模板旧值)。"""
    return re.sub(r'(?m)^%s=.*$' % re.escape(key), lambda m: '%s=%s' % (key, value), text)


def make_env(priv, extra):
    env = (D / '.env').read_text(encoding='utf-8')
    web = 'http://%s:%s' % (NODES['web'][1], require('WEB_PORT'))
    gw_port = require('GATEWAY_PORT')
    mq = NODES['mixmq'][1]
    env = set_line(env, 'HOST_IP', priv)
    env = set_line(env, 'MYSQL_HOST', NODES['mysql'][1])
    env = set_line(env, 'REDIS_HOST', NODES['redis'][1])
    env = set_line(env, 'NACOS_ADDR', mq + ':8848')
    env = set_line(env, 'SENTINEL_DASHBOARD_ADDR', mq + ':' + require('SENTINEL_PORT'))
    env = set_line(env, 'ROCKETMQ_ADDR', mq + ':9876')
    env = set_line(env, 'XXL_ADMIN_ADDR', 'http://%s:%s/xxl-job-admin' % (mq, require('XXL_ADMIN_PORT')))
    env = set_line(env, 'MOCK_PAY_CALLBACK_URL', web + '/api/c/pay/callback/mock')
    env = set_line(env, 'MOCK_PAY_STATUS_URL', web + '/api/c/order/status')
    env = set_line(env, 'GATEWAY1_ADDR', '%s:%s' % (NODES['gw1'][1], gw_port))
    env = set_line(env, 'GATEWAY2_ADDR', '%s:%s' % (NODES['gw2'][1], gw_port))
    env = set_line(env, 'GATEWAY3_ADDR', '%s:%s' % (NODES['gw3'][1], gw_port))
    for key, value in extra:
        env = set_line(env, key, value)
    return env


def push(role):
    comps, extras, extra_env, rdir = ROLES[role]
    pub, priv = NODES[role]
    cli = ssh(pub)
    sftp = cli.open_sftp()
    _, so, _ = cli.exec_command('mkdir -p %s %s/initdb' % (rdir, rdir), timeout=30)
    so.channel.recv_exit_status()
    with sftp.file(rdir + '/.env', 'w') as fh:
        fh.write(make_env(priv, extra_env))
    for c in comps:
        sftp.put(str(D / c), rdir + '/' + c)
    for e in extras:
        dst = rdir + '/' + e if e.startswith('initdb/') else rdir + '/' + Path(e).name
        sftp.put(str(D / e), dst)
    sftp.close()
    cli.close()


def main():
    args = sys.argv[1:]
    if not args or args[0] == 'list':
        print('%-10s %-16s %-16s %s' % ('角色', '公网IP', '私网IP', '文件'))
        for r, (comps, extras, _, _) in ROLES.items():
            print('%-10s %-16s %-16s %s' % (r, NODES[r][0], NODES[r][1], ' '.join(comps + extras)))
        print('%-10s %-16s %-16s %s' % ('k6', K6_PUB or '(未填)', '-', 'seckill-loadtest.js docker-compose.k6.yml .env'))
        return
    todo = (list(ROLES) + ['k6']) if args[0] == 'all' else args
    for r in todo:
        if r == 'k6':
            try:
                k6_push_files()
                print('OK   k6 -> %s' % K6_PUB)
            except Exception as e:
                print('FAIL k6: %s' % str(e)[:90])
            continue
        if r not in ROLES:
            print('!! 未知角色: %s' % r)
            continue
        try:
            push(r)
            print('OK   %s -> %s' % (r, NODES[r][0]))
        except Exception as e:
            print('FAIL %s: %s' % (r, str(e)[:90]))


if __name__ == '__main__':
    main()
