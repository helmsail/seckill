#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""⑧ 冒烟:活动准备(必要时提前开始时间)+ 全链路验证(登录 → 库存 → 提交 → 出单)。

账号智能读取:**直接从数据库取(用户名+密码)**,不依赖配置文件。
用法:
    python scripts/smoke_test.py            # 数据库取一个 C 端账号(role=0)
    python scripts/smoke_test.py lt00001    # 指定用户名(密码仍从库读)

流程:
  1) 登录;
  2) 活动为 PENDING 时:把开始时间提前到 60 秒后(SQL)+ 刷新 redis 活动缓存;
  3) 轮询等待"库存>0 且 ACTIVE"(≤ 8 分钟;job 预热任务会自动补库存键);
  4) 提交秒杀 → 轮询出单。
⚠️ 防抢跑:仅 ACTIVE 后才提交(避免 24h 拉黑)。
"""
import json
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import ENV, MYSQL_PWD, run  # noqa: E402

ACTIVITY_NO = ENV.get('SECKILL_ACTIVITY_NO', '')
SKU = ENV.get('SECKILL_SKUS', '').split(',')[0].split(':')[0]  # 冒烟固定用第一个 SKU
WEB = 'http://127.0.0.1:%s' % ENV.get('WEB_PORT', '18087')      # 在 web 机上直连 nginx
LEAD_SECONDS = int(ENV.get('ADVANCE_SECONDS', '60'))           # 提前量:开始时间 = now + N 秒
MAX_WAIT = int(ENV.get('SMOKE_MAX_WAIT', '480'))               # 等待激活上限(秒)
R = 'docker exec seckill-redis redis-cli'


def curl(path, method='GET', body=None, token=None):
    cmd = 'curl -s -m 12 -X %s %s%s -H "Content-Type: application/json"' % (method, WEB, path)
    if token:
        cmd += ' -H "Authorization: Bearer %s"' % token
    if body is not None:
        cmd += " -d '%s'" % json.dumps(body)
    raw = run('web', cmd, timeout=30)
    if raw.startswith('FAIL'):
        return {'_err': raw}
    try:
        return json.loads(raw)
    except Exception:
        return {'_err': '无有效 JSON 响应: %s' % raw[:160]}


def db_query(sql):
    """在 mysql 机执行 -N 查询,返回单行输出。"""
    return run('mysql', 'docker exec seckill-mysql mysql -uroot -p%s -N -e "%s"' % (MYSQL_PWD, sql), timeout=30)


def pick_account(name=None):
    """从数据库读账号(用户名+密码):默认取 C 端第一个(role=0),可指定用户名。"""
    where = "username='%s'" % name if name else 'role=0'
    out = db_query('SELECT username, password FROM seckill.t_user WHERE %s ORDER BY id LIMIT 1' % where)
    parts = out.split()
    if len(parts) >= 2:
        return parts[0], parts[1]
    return None, None


def advance_activity():
    """把活动开始时间提前到 LEAD_SECONDS 秒后,并刷新 redis 活动缓存。"""
    out = run('mysql', 'docker exec seckill-mysql mysql -uroot -p%s -N -e '
                       '"UPDATE seckill.sk_activity SET '
                       'start_date=DATE(DATE_ADD(NOW(), INTERVAL %d SECOND)), '
                       'start_time=TIME(DATE_ADD(NOW(), INTERVAL %d SECOND)) '
                       'WHERE activity_no=\'%s\'; '
                       'SELECT CONCAT(\'start=\', start_date, \' \', start_time) FROM seckill.sk_activity '
                       'WHERE activity_no=\'%s\';"' % (MYSQL_PWD, LEAD_SECONDS, LEAD_SECONDS, ACTIVITY_NO, ACTIVITY_NO),
               timeout=30)
    run('redis', "%s HDEL seckill:activity:info %s; %s DEL seckill:activity:list:null" % (R, ACTIVITY_NO, R), timeout=30)
    return out.strip().replace('\n', ' ')


def main():
    arg = sys.argv[1] if len(sys.argv) > 1 else None
    user, pwd = pick_account(arg)
    if not user:
        print('✗ 无法从数据库读取账号 —— 环境可能未就绪;先:python scripts/check_up.py')
        return 1
    t0 = time.time()
    print('===== 冒烟:账号 %s(来自数据库)=====' % user)

    r = curl('/api/c/user/login', 'POST', {'username': user, 'password': pwd})
    token = (r.get('data') or {}).get('token')
    if not token:
        print('✗ 登录失败: %s' % json.dumps(r, ensure_ascii=False)[:200])
        if '_err' in r:
            print('  原始响应: %s' % r['_err'])
            print('  提示:先 python scripts/check_up.py 确认 15 台容器全 Up')
        return 1
    print('✓ 登录 OK')

    # ---- 等"库存>0 且 ACTIVE"(PENDING 则先提前开始时间)----
    deadline = time.time() + MAX_WAIT
    advanced = False
    while True:
        r = curl('/api/c/activity/%s' % ACTIVITY_NO)
        status = (r.get('data') or {}).get('activityStatus', '?')
        r2 = curl('/api/c/activity/%s/sku/%s/stock' % (ACTIVITY_NO, SKU))
        stock = r2.get('data') or 0
        print('  ...状态=%s 库存=%s' % (status, stock), flush=True)
        if status == 'ACTIVE' and str(stock).isdigit() and int(stock) > 0:
            break
        if not advanced and status == 'PENDING':
            print('✓ 活动 PENDING:提前开始时间(now+%ds)+ 刷新缓存 → %s' % (LEAD_SECONDS, advance_activity()))
            advanced = True
        if time.time() > deadline:
            print('✗ 等待超时(预热未完成/未激活)。检查 job 日志或活动状态;重建环境:python scripts/up.py')
            return 1
        time.sleep(10)

    print('✓ 活动 ACTIVE 且库存已预热')

    # ---- 秒杀提交 ----
    r = curl('/api/c/seckill', 'POST', {'activityNo': ACTIVITY_NO, 'skuNo': SKU, 'quantity': 1}, token=token)
    if r.get('code') == 'blacklisted':
        print('✗ 已被防抢跑拉黑(24h)。重建环境即可清除:python scripts/up.py')
        return 1
    data = r.get('data')
    if isinstance(data, dict):                      # 兼容 {traceId: ...} 形态
        trace_id = data.get('traceId') or data.get('traceID')
    elif isinstance(data, str) and data:
        trace_id = data                             # 契约:data 直接是 traceId(k6 同款)
    else:
        trace_id = None
    if not trace_id:
        print('✗ 提交失败(code=%s): %s' % (r.get('code'), json.dumps(r, ensure_ascii=False)[:200]))
        return 1
    print('✓ 提交成功 traceId=%s' % trace_id)

    # ---- 轮询出单 ----
    order_no = None
    for _ in range(10):
        time.sleep(0.5)
        r = curl('/api/c/seckill/poll?traceId=%s' % trace_id, token=token)
        d = r.get('data') if isinstance(r.get('data'), dict) else {}
        if d.get('orderNo'):
            order_no = d['orderNo']
            break
    if order_no:
        print('✓ 出单成功 orderNo=%s' % order_no)
    else:
        print('✗ 出单超时,最后响应: %s' % json.dumps(r, ensure_ascii=False)[:200])
        return 1

    print('===== 冒烟通过(耗时 %.1fs)=====' % (time.time() - t0))
    return 0


if __name__ == '__main__':
    sys.exit(main())
