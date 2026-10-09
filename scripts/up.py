#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑥ 启动:目标角色重建启动(up -d --force-recreate;每台一个多行窗口,实时滚动)。

窗口大小按终端高度自动适配:16 台时每台挤进一屏;单台/少台时窗口更大(输出行更多)。
⚠️ 语义:重建即重置——每执行一次都是"全新环境"(MySQL 重跑 initdb、Redis/MQ 清空);
   仅想轻量重启(保数据)用 restart.py;仅重建单台用 `up.py svc1`。
用法:
    python scripts/up.py                # 全部 16 角色(窗口按终端高度自动适配)
    python scripts/up.py mysql          # 指定角色(单台窗口自动更大)
    python scripts/up.py --lines 6      # 手动指定每台窗口输出行数(1~30)
"""
import os
import shutil
import sys
import threading
import time
from collections import deque
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import NODES, ROLES, roles_from_args, ssh  # noqa: E402

POLL = 0.4  # 刷新间隔(秒)


def calc_content(n_roles):
    """每台窗口的输出行数:按终端高度自动适配(1~10 行)。"""
    lh = shutil.get_terminal_size().lines or 40
    return max(1, min(10, (lh - 3) // max(1, n_roles) - 1))


def stream_up(role, state, lock):
    """单台:流式执行 up,实时写入该台窗口滚动缓冲。"""
    comps, _, _, rdir = ROLES[role]
    flags = ' '.join('-f ' + c for c in comps)
    cmd = 'cd %s && docker compose %s up -d --force-recreate 2>&1' % (rdir, flags)
    t0 = time.time()
    try:
        cli = ssh(role, timeout=20)
        try:
            _, so, _ = cli.exec_command(cmd, timeout=300)
            for raw in so:  # 逐行实时读取(兼容 str/bytes)
                line = (raw if isinstance(raw, str) else raw.decode(errors='replace')).rstrip()
                if not line:
                    continue
                with lock:
                    state[role]['recent'].append(line[:120])
                    state[role]['elapsed'] = time.time() - t0
            code = so.channel.recv_exit_status()
        finally:
            cli.close()
        with lock:
            state[role]['status'] = 'done' if code == 0 else 'exit=%d' % code
            state[role]['elapsed'] = time.time() - t0
    except Exception as e:
        with lock:
            state[role]['status'] = 'fail'
            state[role]['recent'].append('FAIL: %s' % str(e)[:100])
            state[role]['elapsed'] = time.time() - t0


def draw(roles, state, first, content):
    """整屏重画:每台一个窗口(1 行标题 + content 行实时输出,自底向上滚动)。"""
    n = len(roles) * (content + 1)
    if not first:
        sys.stdout.write('\x1b[%dA' % n)  # 光标回到看板顶部
    for r in roles:
        st = state[r]
        flag = {'running': '>>', 'done': 'OK'}.get(st['status'], '!!')
        head = '%s %-8s (%-15s) %6.1fs  %s' % (flag, r, NODES[r][0], st['elapsed'], st['status'])
        sys.stdout.write('\x1b[2K' + head[:114] + '\n')
        rec = st['recent']
        pad = content - len(rec)
        for i in range(content):
            line = '' if i < pad else rec[i - pad]
            sys.stdout.write('\x1b[2K' + '      │ ' + line[:110] + '\n')
    sys.stdout.flush()


def main():
    if sys.platform == 'win32':
        os.system('')  # 启用控制台 ANSI(VT)渲染
    args = sys.argv[1:]
    force = None
    if '--lines' in args:
        i = args.index('--lines')
        try:
            force = max(1, min(30, int(args[i + 1])))
        except (IndexError, ValueError):
            sys.exit('!! 用法:python scripts/up.py [角色…] [--lines N](N 为每台窗口输出行数 1~30)')
        del args[i:i + 2]
    sys.argv = [sys.argv[0]] + args
    roles = roles_from_args(ROLES)
    content = force if force else calc_content(len(roles))
    lh = shutil.get_terminal_size().lines or 40
    total = len(roles) * (content + 1) + 3
    if total > lh:
        print('!! 窗口总高 %d 行 > 终端 %d 行(可能有滚动错位;建议拉高终端或减小 --lines)' % (total, lh))
    print('===== 启动 %d 台(每台窗口 %d 行输出,实时滚动;重建即重置)=====' % (len(roles), content))
    state = {r: {'status': 'running', 'recent': deque(maxlen=content), 'elapsed': 0.0} for r in roles}
    lock = threading.Lock()
    threads = [threading.Thread(target=stream_up, args=(r, state, lock), daemon=True) for r in roles]
    for t in threads:
        t.start()

    first = True
    while True:
        with lock:
            snap = {r: {'status': state[r]['status'], 'recent': list(state[r]['recent']),
                        'elapsed': state[r]['elapsed']} for r in roles}
        draw(roles, snap, first, content)
        first = False
        if all(v['status'] != 'running' for v in snap.values()):
            break
        time.sleep(POLL)

    fails = [r for r in roles if state[r]['status'] != 'done']
    print('---- 完成 %d/%d%s ----' % (len(roles) - len(fails), len(roles),
                                    (';异常: ' + ' '.join(fails)) if fails else ''))
    print('---- 查看全量状态:python scripts/check_up.py ----')


if __name__ == '__main__':
    main()
