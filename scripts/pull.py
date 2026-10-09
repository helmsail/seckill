#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑤ 拉镜像:从 ACR 拉取目标角色最新镜像(自动登录 ACR;不启动)。

用法:
    python scripts/pull.py            # 全部 16 角色
    python scripts/pull.py svc1 gw1   # 指定角色
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import NODES, ROLES, require, roles_from_args, run  # noqa: E402

ACR = require('IMAGE_REGISTRY')
ACR_USER = require('ACR_USERNAME')
ACR_PASS = require('ACR_PASSWORD')


def main():
    for r in roles_from_args(ROLES):
        comps, _, _, rdir = ROLES[r]
        flags = ' '.join('-f ' + c for c in comps)
        cmd = ('cd %s && docker login --username=%s --password=%s %s >/dev/null 2>&1; '
               'docker compose %s pull -q && echo 镜像已就绪') % (rdir, ACR_USER, ACR_PASS, ACR, flags)
        print('---- %s (%s) ----' % (r, NODES[r][0]), flush=True)
        print('  ' + run(r, cmd, timeout=600), flush=True)


if __name__ == '__main__':
    main()
