#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑨ 压测:k6 执行 + 全机监控(双窗口实时看板)+ 结束分析总结。

双窗口(同一终端上下两块,实时刷新):
  上窗:k6 执行全过程(实时滚动,最近 N 行);
  下窗:每个采样周期(10s)探测的机器使用率(周期跳动更新);
  结束后:A(k6 指标)/ B(机器级)/ C(容器级)分析总结。

用法:python scripts/bench.py <rate> <duration>      # 例:python scripts/bench.py 4000 2m
前提:K6_PUB 已填;活动 ACTIVE 且库存已预热(先跑 python scripts/smoke_test.py)。
"""
import os
import re
import shutil
import sys
import threading
import time
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import ENV, K6_DIR, K6_PUB, NODES, all_targets, k6_bootstrap, run, ssh  # noqa: E402

INTERVAL = 10   # 监控采样间隔(秒)
RENDER = 0.5    # 看板刷新间隔(秒)

# 每台采样:机器级(两次 /proc/stat 差 + 内存)+ 容器级(docker stats)
SAMPLE_CMD = (
    "grep '^cpu ' /proc/stat; sleep 1; grep '^cpu ' /proc/stat; "
    "free -m | sed -n '2p'; "
    "docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}'"
)

# ---- 监控目标(15 生产角色 + k6 压测机)----
TARGETS = all_targets()        # [(显示名, run 目标)]

# ---- 双窗口共享状态 ----
k6_lines = deque(maxlen=500)   # 上窗:k6 实时输出行
_partial = ''                  # 行缓冲残留(半个行)
cycles = 0                     # 已完成采样周期数
render_stop = threading.Event()
sample_stop = threading.Event()


def avg(v):
    return sum(v) / len(v) if v else 0.0


def peak(v):
    return max(v) if v else 0.0


def parse_mib(s):
    s = s.strip()
    if 'GiB' in s:
        return float(s.replace('GiB', '')) * 1024
    return float(s.replace('MiB', '').replace('%', ''))


def sample_role(item):
    name, target = item
    return name, run(target, SAMPLE_CMD, timeout=30)


def collect(mach, cont):
    with ThreadPoolExecutor(max_workers=len(TARGETS)) as ex:
        for role, out in ex.map(sample_role, TARGETS):
            lines = [l for l in out.splitlines() if l.strip()]
            if len(lines) < 4:
                continue
            try:
                a = [int(x) for x in lines[0].split()[1:]]
                b = [int(x) for x in lines[1].split()[1:]]
                dt = sum(b) - sum(a)
                if dt > 0:
                    cpu = 100.0 * (1 - ((b[3] + b[4]) - (a[3] + a[4])) / dt)
                    io = 100.0 * (b[4] - a[4]) / dt
                    mach[role]['cpu'].append(cpu)
                    mach[role]['io'].append(io)
                f = lines[2].split()
                total, avail = int(f[1]), int(f[-1])
                mach[role]['mem'].append(100.0 * (total - avail) / total)
                for l in lines[3:]:
                    parts = l.split('|')
                    if len(parts) != 3 or not parts[0].startswith('seckill-'):
                        continue
                    name = parts[0]
                    cont[role].setdefault(name, {'cpu': [], 'mem': []})
                    cont[role][name]['cpu'].append(float(parts[1].rstrip('%')))
                    cont[role][name]['mem'].append(parse_mib(parts[2].split('/')[0]))
            except Exception:
                continue


def sampler(mach, cont):
    """下窗数据源:每 INTERVAL 秒采样一轮 15 台。"""
    global cycles
    while not sample_stop.is_set():
        t0 = time.time()
        collect(mach, cont)
        cycles += 1
        time.sleep(max(1, INTERVAL - (time.time() - t0)))


def _feed(data):
    """k6 输出块 → 行缓冲(上窗滚动)。"""
    global _partial
    _partial += data
    parts = _partial.split('\n')
    _partial = parts.pop()
    for l in parts:
        l = l.replace('\r', '').rstrip()
        if not l or 'level=warning' in l or 'level=error' in l:
            continue
        k6_lines.append(l[:200])


def _dur_s(d):
    """'2m'/'90s'/'1h' → 秒(k6 duration;解析失败按 10 分钟余量处理)。"""
    m = re.match(r'\s*(\d+)\s*([smh]?)\s*$', str(d))
    if not m:
        return 600
    return int(m.group(1)) * {'': 1, 's': 1, 'm': 60, 'h': 3600}[m.group(2)]


def run_k6(rate, duration):
    cmd = ('cd %s && docker compose -f docker-compose.k6.yml run --rm k6 run '
           '-e SECKILL_RATE=%s -e DURATION=%s /scripts/seckill-loadtest.js 2>&1') % (K6_DIR, rate, duration)
    cli = ssh(K6_PUB, timeout=20)
    tr = cli.get_transport()
    ch = tr.open_session()
    ch.settimeout(900)
    ch.exec_command(cmd)
    buf = []
    deadline = time.time() + _dur_s(duration) + 600  # 压测时长 + 10 分钟余量(启动/优雅停止/裕量)
    while True:
        try:
            alive = tr.is_active()
        except Exception:
            alive = False
        if not alive:
            print('!! 与压测机连接中断(过载/失联)——提前结束', flush=True)
            break
        if time.time() > deadline:
            print('!! 超过预期时长仍未结束(连接假死)——提前结束', flush=True)
            try:
                ch.close()
                cli.close()
            except Exception:
                pass
            break
        if ch.recv_ready():
            data = ch.recv(65536).decode(errors='replace')
            buf.append(data)
            _feed(data)
        elif ch.exit_status_ready():
            break
        else:
            time.sleep(0.2)
    while ch.recv_ready():
        data = ch.recv(65536).decode(errors='replace')
        buf.append(data)
        _feed(data)
    global _partial
    last = _partial.replace('\r', '').rstrip()
    if last:
        k6_lines.append(last[:200])
    _partial = ''
    cli.close()
    return ''.join(buf)


def build_frame(mach, rows_k6, width):
    lines = []
    lines.append(('── k6 执行(实时滚动;最近 %d 行)──' % rows_k6)[:width])
    tail = list(k6_lines)[-rows_k6:]
    pad = rows_k6 - len(tail)
    for i in range(rows_k6):
        lines.append(('' if i < pad else tail[i - pad])[:width])
    lines.append(('── 机器使用率(第 %d 周期;每 %ds 刷新;%d 台)──' % (cycles, INTERVAL, len(mach)))[:width])
    lines.append(('%-8s %7s %7s %8s %7s %7s' % ('机器', 'CPU', 'CPU峰', 'iowait', 'Mem', 'Mem峰'))[:width])
    for r in mach:
        d = mach[r]
        lines.append(('%-8s %6.1f%% %6.1f%% %7.1f%% %6.1f%% %6.1f%%' % (
            r, avg(d['cpu']), peak(d['cpu']), avg(d['io']), avg(d['mem']), peak(d['mem'])))[:width])
    return lines


def render_loop(mach, rows_k6, width):
    """双窗口整屏重画(0.5s/帧)。"""
    first = True
    while not render_stop.is_set():
        frame = build_frame(mach, rows_k6, width)
        if not first:
            sys.stdout.write('\x1b[%dA' % len(frame))
        for l in frame:
            sys.stdout.write('\x1b[2K' + l + '\n')
        sys.stdout.flush()
        first = False
        time.sleep(RENDER)


def parse_k6(text):
    def g(pat, n=None):
        m = re.search(pat, text)
        if not m:
            return None
        gs = m.groups()
        return gs if n is None else gs[:n]

    return {
        'seckill': g(r'seckill_duration\.+:\s*avg=(\S+)\s+min=\S+\s+med=(\S+)\s+p\(90\)=(\S+)\s+p\(95\)=(\S+)\s+p\(99\)=(\S+)\s+max=(\S+)'),
        'seckill_ok': g(r'seckill_ok\.+:\s*([\d.]+)%\s+(\d+) out of (\d+)'),
        'http': g(r'http_req_duration\.+:\s*avg=(\S+)\s+min=\S+\s+med=(\S+)\s+p\(90\)=(\S+)\s+p\(95\)=(\S+)\s+p\(99\)=(\S+)\s+max=(\S+)'),
        'http_fail': g(r'http_req_failed\.+:\s*([\d.]+)%\s+(\d+) out of (\d+)'),
        'dropped': g(r'dropped_iterations\.+:\s*(\d+)'),
        'iters': g(r'(?<![\w])iterations\.+:\s*(\d+)\s+([\d.]+)/s'),
        'pay_ok': g(r'pay_ok\.+:\s*([\d.]+)%\s+(\d+) out of (\d+)'),
        'pay_dur': g(r'(?<![\w])pay_duration\.+:\s*avg=(\S+)\s+min=\S+\s+med=(\S+)\s+p\(90\)=(\S+)\s+p\(95\)=(\S+)\s+p\(99\)=(\S+)'),
        'seckill_biz': re.findall(r'(?m)^\s*seckill_biz\{([^}]+)\}[^:]*:\s*(\d+)', text),
        'pay_biz': re.findall(r'(?m)^\s*pay_biz\{([^}]+)\}[^:]*:\s*(\d+)', text),
        'final': re.findall(r'(?m)^\s*seckill_final\{([^}]+)\}[^:]*:\s*(\d+)', text),
        'iter_dur': g(r'iteration_duration\.+:\s*avg=(\S+)\s+min=\S+\s+med=(\S+)\s+p\(90\)=(\S+)\s+p\(95\)=(\S+)\s+p\(99\)=(\S+)'),
    }


def report(mach, cont, k6text):
    m = parse_k6(k6text)
    print('\n========== A. k6 指标 ==========')
    if m['seckill']:
        a, med, p90, p95, p99, mx = m['seckill']
        print('秒杀提交:  med=%s  p90=%s  p95=%s  p99=%s  (avg=%s max=%s)' % (med, p90, p95, p99, a, mx))
    if m['http']:
        a, med, p90, p95, p99, mx = m['http']
        print('HTTP 全部: med=%s  p90=%s  p95=%s  p99=%s  (avg=%s max=%s)' % (med, p90, p95, p99, a, mx))
    if m['seckill_ok']:
        print('秒杀受理: %s%%  (%s/%s)' % m['seckill_ok'])
    if m['http_fail']:
        print('HTTP 失败: %s%%  (%s/%s)' % m['http_fail'])
    if m['pay_ok']:
        print('支付链路: %s%%  (%s/%s)' % m['pay_ok'])
    if m['iters']:
        print('完成迭代: %s  (%s/s)' % m['iters'])
    if m['dropped']:
        print('dropped: %s' % m['dropped'][0])
    if m['iter_dur']:
        print('迭代时长: avg=%s med=%s p90=%s p95=%s p99=%s' % m['iter_dur'])
    if m['pay_dur']:
        print('支付耗时: avg=%s med=%s p90=%s p95=%s p99=%s' % m['pay_dur'])
    if m['seckill_biz']:
        print('受理细分: %s' % '; '.join('%s=%s' % (k, v) for k, v in m['seckill_biz']))
    if m['pay_biz']:
        print('支付细分: %s' % '; '.join('%s=%s' % (k, v) for k, v in m['pay_biz']))
    if m['final']:
        print('终态抽查: %s' % '; '.join('%s=%s' % (k, v) for k, v in m['final']))

    print('\n========== B. 机器级(CPU/iowait/内存)==========')
    print('%-10s %8s %8s %8s %8s %8s' % ('机器', 'CPU avg', 'CPU peak', 'io avg', 'Mem avg', 'Mem peak'))
    for role in mach:
        d = mach[role]
        print('%-10s %7.1f%% %7.1f%% %7.1f%% %7.1f%% %7.1f%%' % (
            role, avg(d['cpu']), peak(d['cpu']), avg(d['io']), avg(d['mem']), peak(d['mem'])))

    print('\n========== C. 容器级(每机每容器)==========')
    print('%-12s %-26s %8s %8s %9s %9s' % ('机器', '容器', 'CPU avg', 'CPU peak', 'Mem avg', 'Mem peak'))
    for role in mach:
        for name, d in sorted(cont[role].items()):
            print('%-12s %-26s %7.1f%% %7.1f%% %8.0fM %8.0fM' % (
                role, name, avg(d['cpu']), peak(d['cpu']), avg(d['mem']), peak(d['mem'])))


def main():
    if sys.platform == 'win32':
        os.system('')  # 启用控制台 ANSI(VT)渲染
    rate = sys.argv[1] if len(sys.argv) > 1 else ENV.get('SECKILL_RATE', '4000')
    duration = sys.argv[2] if len(sys.argv) > 2 else ENV.get('DURATION', '2m')
    if not K6_PUB:
        print('!! 压测机未配置:请先在 deploy/.env【A】区填 K6_PUB / K6_PRIV')
        sys.exit(1)

    term = shutil.get_terminal_size()
    width = max(80, term.columns - 1)
    rows_k6 = max(5, term.lines - len(TARGETS) - 5)
    total = rows_k6 + len(TARGETS) + 3
    if total > term.lines:
        print('!! 终端 %d 行 < 看板 %d 行:建议拉高终端窗口(可能滚动错位)' % (term.lines, total))
    print('===== 压测 %s QPS × %s(PAY_RATIO=%s;双窗口:上=k6 / 下=使用率)=====' % (rate, duration, ENV.get('PAY_RATIO', '?')))
    k6_bootstrap()

    mach = {name: {'cpu': [], 'io': [], 'mem': []} for name, _ in TARGETS}
    cont = {name: {} for name, _ in TARGETS}

    th_s = threading.Thread(target=sampler, args=(mach, cont), daemon=True)
    th_s.start()
    th_r = threading.Thread(target=render_loop, args=(mach, rows_k6, width), daemon=True)
    th_r.start()

    try:
        k6text = run_k6(rate, duration)
    finally:
        sample_stop.set()
        time.sleep(0.3)
        render_stop.set()
        th_r.join(timeout=3)

    report(mach, cont, k6text)


if __name__ == '__main__':
    main()
