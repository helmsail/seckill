#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""① 巡检:全部节点连通性 + 容器运行状态(并行,每台独立超时)。

用法:python scripts/check_all.py
"""
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import NODES, run  # noqa: E402


def probe(role):
    out = run(role, 'docker ps --format "{{.Names}}" | tr "\\n" "," ', timeout=25)
    return role, out


def main():
    print('===== 全网巡检(16 生产节点)=====')
    results = []
    with ThreadPoolExecutor(max_workers=16) as ex:
        for role, names in ex.map(probe, list(NODES)):
            results.append((role, names))

    ok = 0
    for role, names in results:
        if names.startswith('FAIL'):
            print('%-10s %s' % (role, names))
        else:
            cnt = len([n for n in names.split(',') if n])
            print('%-10s OK  容器 %d 个: %s' % (role, cnt, names))
            ok += 1
    print('\n连通 %d / %d' % (ok, len(NODES)))


if __name__ == '__main__':
    main()
