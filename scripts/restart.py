#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑩ 重启:目标角色容器级重启(docker compose restart;保留容器与数据,轻量)。

用法:
    python scripts/restart.py            # 全部 15 角色
    python scripts/restart.py mixps      # 指定角色
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import NODES, ROLES, roles_from_args, run  # noqa: E402


def main():
    for r in roles_from_args(ROLES):
        comps, _, _, rdir = ROLES[r]
        flags = ' '.join('-f ' + c for c in comps)
        cmd = 'cd %s && docker compose %s restart 2>&1 | tail -2' % (rdir, flags)
        print('---- %s (%s) ----' % (r, NODES[r][0]), flush=True)
        print('  ' + run(r, cmd, timeout=300), flush=True)


if __name__ == '__main__':
    main()
