#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""② 检测 Docker:并行确认各机 Docker 是否已装及版本(15 角色 + k6)。

用法:python scripts/check_docker.py
"""
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import all_targets, run  # noqa: E402

PROBE = ('if command -v docker >/dev/null 2>&1; then echo "OK $(docker --version)"; '
         'else echo "NO_DOCKER 未安装"; fi')


def probe(item):
    name, target = item
    return name, target, run(target, PROBE, timeout=30)


def main():
    items = all_targets()
    print('===== Docker 检测(%d 台)=====' % len(items))
    okn = 0
    with ThreadPoolExecutor(max_workers=len(items)) as ex:
        for name, target, out in ex.map(probe, items):
            ok = out.startswith('OK')
            okn += 1 if ok else 0
            print('%-8s %s' % (name, out), flush=True)
    print('---- 已装 %d/%d(未装的执行 install_docker.py)----' % (okn, len(items)))


if __name__ == '__main__':
    main()
