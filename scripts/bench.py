#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""④ 压测:k6 执行 + 全机监控采样(机器级 & 容器级 avg/peak)+ 指标自动解析。

用法:python scripts/bench.py <rate> <duration>      # 例:python scripts/bench.py 4000 2m
前提:common.K6_PUB 已填(压测机;脚本会自动自举:推脚本/compose + TCP 调优 + ACR 登录);活动 ACTIVE 且库存已预热。

输出三块:
  A. k6 指标(秒杀/HTTP 分位、成功率、dropped、迭代吞吐);
  B. 机器级:每台 CPU avg/peak、iowait avg、Mem avg/peak;
  C. 容器级:每台机每个容器的 CPU avg/peak、Mem avg/peak(压测期间 10s 一采)。
"""
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import ENV, K6_DIR, K6_PUB, NODES, k6_bootstrap, run, ssh  # noqa: E402

INTERVAL = 10  # 监控采样间隔(秒)

# 每台采样:机器级(两次 /proc/stat 差 + 内存)+ 容器级(docker stats)
SAMPLE_CMD = (
    "grep '^cpu ' /proc/stat; sleep 1; grep '^cpu ' /proc/stat; "
    "free -m | sed -n '2p'; "
    "docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}'"
)


def avg(v):
    return sum(v) / len(v) if v else 0.0


def peak(v):
    return max(v) if v else 0.0


def parse_mib(s):
    s = s.strip()
    if 'GiB' in s:
        return float(s.replace('GiB', '')) * 1024
    return float(s.replace('MiB', '').replace('%', ''))


def sample_role(role):
    return role, run(role, SAMPLE_CMD, timeout=30)


def collect(mach, cont):
    with ThreadPoolExecutor(max_workers=len(NODES)) as ex:
        for role, out in ex.map(sample_role, list(NODES)):
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


def run_k6(rate, duration):
    cmd = ('cd %s && docker compose -f docker-compose.k6.yml run --rm k6 run '
           '-e SECKILL_RATE=%s -e DURATION=%s /scripts/seckill-loadtest.js 2>&1') % (K6_DIR, rate, duration)
    cli = ssh(K6_PUB, timeout=20)
    ch = cli.get_transport().open_session()
    ch.settimeout(900)
    ch.exec_command(cmd)
    buf = []
    while True:
        if ch.recv_ready():
            data = ch.recv(65536).decode(errors='replace')
            if 'level=warning' not in data and 'level=error' not in data:
                print(data, end='', flush=True)
            buf.append(data)
        elif ch.exit_status_ready():
            break
        else:
            time.sleep(0.2)
    while ch.recv_ready():
        buf.append(ch.recv(65536).decode(errors='replace'))
    cli.close()
    return ''.join(buf)


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
        'iters': g(r'iterations\.+:\s*(\d+)\s+([\d.]+)/s'),
        'pay_ok': g(r'pay_ok\.+:\s*([\d.]+)%\s+(\d+) out of (\d+)'),
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

    print('\n========== B. 机器级(CPU/iowait/内存)==========')
    print('%-10s %8s %8s %8s %8s %8s' % ('机器', 'CPU avg', 'CPU peak', 'io avg', 'Mem avg', 'Mem peak'))
    for role in NODES:
        d = mach[role]
        print('%-10s %7.1f%% %7.1f%% %7.1f%% %7.1f%% %7.1f%%' % (
            role, avg(d['cpu']), peak(d['cpu']), avg(d['io']), avg(d['mem']), peak(d['mem'])))

    print('\n========== C. 容器级(每机每容器)==========')
    print('%-12s %-26s %8s %8s %9s %9s' % ('机器', '容器', 'CPU avg', 'CPU peak', 'Mem avg', 'Mem peak'))
    for role in NODES:
        for name, d in sorted(cont[role].items()):
            print('%-12s %-26s %7.1f%% %7.1f%% %8.0fM %8.0fM' % (
                role, name, avg(d['cpu']), peak(d['cpu']), avg(d['mem']), peak(d['mem'])))


def main():
    rate = sys.argv[1] if len(sys.argv) > 1 else ENV.get('SECKILL_RATE', '4000')
    duration = sys.argv[2] if len(sys.argv) > 2 else ENV.get('DURATION', '2m')
    if not K6_PUB:
        print('!! 压测机未配置:请先在 scripts/common.py 填入 K6_PUB')
        sys.exit(1)
    print('===== 压测 %s QPS × %s(PAY_RATIO=%s;10s 采样)=====' % (rate, duration, ENV.get('PAY_RATIO', '?')))
    k6_bootstrap()

    mach = {r: {'cpu': [], 'io': [], 'mem': []} for r in NODES}
    cont = {r: {} for r in NODES}

    import threading
    stop = threading.Event()

    def loop():
        while not stop.is_set():
            t0 = time.time()
            collect(mach, cont)
            print('  [采样] %d 轮完成(%.1fs)' % (len(mach['web']['cpu']), time.time() - t0), flush=True)
            time.sleep(max(1, INTERVAL - (time.time() - t0)))

    th = threading.Thread(target=loop, daemon=True)
    th.start()
    time.sleep(2)

    k6text = run_k6(rate, duration)
    stop.set()
    time.sleep(1)

    report(mach, cont, k6text)


if __name__ == '__main__':
    main()
