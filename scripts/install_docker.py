#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""③ 安装 Docker:未装的机器安装 Docker + 日志轮转(幂等:已装跳过;15 角色 + k6)。

用法:python scripts/install_docker.py
"""
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import all_targets, ensure_docker  # noqa: E402


def install(item):
    name, target = item
    return name, target, ensure_docker(target)


def main():
    items = all_targets()
    print('===== 安装 Docker(%d 台;幂等)=====' % len(items))
    with ThreadPoolExecutor(max_workers=len(items)) as ex:
        for name, target, out in ex.map(install, items):
            print('%-8s %s' % (name, out.replace('\n', ' ')), flush=True)


if __name__ == '__main__':
    main()
