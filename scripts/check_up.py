#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑦ 检测 up:并行查看各机容器编排状态(compose ps;重点:非 Up 与缺容器)。

用法:python scripts/check_up.py
"""
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import NODES, ROLES, run  # noqa: E402


def check_role(role):
    comps, _, _, rdir = ROLES[role]
    flags = ' '.join('-f ' + c for c in comps)
    cmd = 'cd %s && docker compose %s ps --format "{{.Name}}|{{.Status}}" 2>&1' % (rdir, flags)
    return role, run(role, cmd, timeout=60)


def main():
    roles = list(ROLES)
    print('===== 容器状态(%d 台)=====' % len(roles))
    badn = 0
    with ThreadPoolExecutor(max_workers=len(roles)) as ex:
        for role, out in ex.map(check_role, roles):
            lines = [l.strip() for l in out.splitlines() if '|' in l]
            bad = [l for l in lines if 'Up' not in l.split('|')[-1]]
            if not lines:
                bad = ['(无容器 —— 未部署?)']
            badn += len(bad)
            print('---- %-8s [%s] %s ----' % (role, 'OK' if not bad else '!!', NODES[role][0]))
            for l in (lines or bad):
                print('   ' + l)
    print('==== 异常条目合计: %d ====' % badn)


if __name__ == '__main__':
    main()
