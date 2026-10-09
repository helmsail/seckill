#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑧ 部署更新(唯一运维脚本):从 ACR 拉取最新镜像并强制重建。

用法:
    python scripts/rollout.py             # 默认全部:项目 16 角色 + k6 压测机(自举)
    python scripts/rollout.py project     # 只重启项目(16 台)
    python scripts/rollout.py k6          # 只重装 k6 压测机(TCP 调优+推文件+拉镜像)
    python scripts/rollout.py svc1 gw1    # 指定角色

说明:
  - 项目角色:pull + up -d --force-recreate(无数据卷语义下,重建即数据重置);
  - k6:重新自举(推脚本/compose/.env + sysctl + ACR 登录 + 拉镜像);
  - 任何异常直接重跑本脚本 = 重启解决一切。
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import K6_PUB, NODES, k6_bootstrap, run  # noqa: E402
from deploy_push import ROLES  # noqa: E402


def restart_role(role):
    comps, _, _, rdir = ROLES[role]
    flags = ' '.join('-f ' + c for c in comps)
    cmd = ('cd %s && docker compose %s pull -q && docker compose %s up -d --force-recreate 2>&1 | tail -3'
           % (rdir, flags, flags))
    print('---- %s (%s) ----' % (role, NODES[role][0]))
    print(run(role, cmd, timeout=300), flush=True)


def main():
    args = sys.argv[1:] or ['all']
    roles = []
    k6_todo = False
    for a in args:
        if a == 'all':
            roles.extend(r for r in ROLES if r not in roles)
            k6_todo = True
        elif a == 'project':
            roles.extend(r for r in ROLES if r not in roles)
        elif a == 'k6':
            k6_todo = True
        elif a in ROLES:
            roles.append(a)
        else:
            print('!! 未知参数: %s(可用: all | project | k6 | 角色名)' % a)

    for r in roles:
        restart_role(r)

    if k6_todo:
        print('---- k6 (%s) ----' % (K6_PUB or '未配置'))
        try:
            k6_bootstrap()
        except Exception as e:
            print('FAIL k6: %s' % str(e)[:120])


if __name__ == '__main__':
    main()
