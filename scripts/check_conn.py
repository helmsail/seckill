#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""① 连接:验证全部节点 SSH 连通(15 角色 + k6 压测机;并行)。

用法:python scripts/check_conn.py
"""
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import all_targets, run  # noqa: E402


def probe(item):
    name, target = item
    return name, target, run(target, 'hostname', timeout=15)


def main():
    items = all_targets()
    print('===== 连接检查(%d 台)=====' % len(items))
    okn = 0
    with ThreadPoolExecutor(max_workers=len(items)) as ex:
        for name, target, out in ex.map(probe, items):
            ok = not out.startswith('FAIL')
            okn += 1 if ok else 0
            print('%-8s %s' % (name, ('OK ' + out) if ok else out), flush=True)
    print('---- 连通 %d/%d ----' % (okn, len(items)))


if __name__ == '__main__':
    main()
