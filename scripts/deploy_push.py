#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""④ 分发:本地 deploy/ 是唯一事实源;推送 compose/.env/附属文件到目标角色。

用法(在 d:\\seckill 下执行):
    python scripts/deploy_push.py list        # 列出角色、目标机与文件清单
    python scripts/deploy_push.py web gw1     # 推送指定角色
    python scripts/deploy_push.py k6          # 只推 k6 压测机文件
    python scripts/deploy_push.py all         # 全量推送(15 角色 + k6)

说明:
  - 分发=只上传(.env 按节点表自动生成:地址行全部动态替换,无需手改);
  - 拉镜像/启动:python scripts/pull.py、python scripts/up.py;
  - 换机/改布局:只改 deploy/.env【A】节点表 /【A2】角色表。
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))

from common import K6_DIR, K6_PUB, NODES, ROLES, k6_push_files, render_env, ssh  # noqa: E402

D = ROOT / 'deploy'


def push(role):
    comps, extras, extra_env, rdir = ROLES[role]
    pub, priv = NODES[role]
    cli = ssh(pub)
    sftp = cli.open_sftp()
    _, so, _ = cli.exec_command('mkdir -p %s %s/initdb' % (rdir, rdir), timeout=30)
    so.channel.recv_exit_status()
    with sftp.file(rdir + '/.env', 'w') as fh:
        fh.write(render_env(priv, extra_env))
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
