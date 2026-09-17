/**
 * 秒杀链路压测（k6）——通用版
 *
 * 覆盖功能：登录 → 秒杀下单（六项准入 + MQ 受理）→【按比例】模拟支付
 *          （等出单 → 预支付 → 模拟回调）→ 抽样终态轮询。
 *
 * ── 配置（只 4 项，都在 deploy/.env 的"压测 k6"段维护）──────────────
 *   BASE_URL       压测目标（本地联调 http://localhost:18080；服务器 http://<台8 IP>:18080）
 *   SECKILL_RATE   每秒秒杀提交数
 *   DURATION       压测时长
 *   PAY_RATIO      秒杀成功后按比例模拟支付（0=关，1=全付）
 *   临时覆盖用 -e KEY=VALUE（优先级：-e > .env > 代码默认）；
 *   其余（账号池/SKU/活动号等）均为代码内默认——种子环境专用，无需配置。
 *
 * ── 运行 ────────────────────────────────────────────────────────
 *   直接跑（读 .env 配置）
 *     k6 run deploy/k6/seckill-loadtest.js
 *   临时改压力
 *     k6 run -e SECKILL_RATE=3000 -e DURATION=10m deploy/k6/seckill-loadtest.js
 *   实时看板（过程中可视化各指标）
 *     PowerShell:  $env:K6_WEB_DASHBOARD='true'; k6 run deploy/k6/seckill-loadtest.js
 *     浏览器打开 http://127.0.0.1:5665
 *   结果导出
 *     k6 run --summary-export=summary.json ...（或 --out csv=result.csv）
 *
 * ── 前置条件 ────────────────────────────────────────────────────
 *   1. 环境已冷启动：setup 自动等待"库存键预热 + 活动 ACTIVE"（≤20 分钟；
 *      种子活动开始时间 = 数据库初始化 +5 分钟）；
 *   2. 压测账号 lt00001~lt20000（/123456）已就位（initdb 种子或热插）。
 *
 * ── 指标口径 ────────────────────────────────────────────────────
 *   seckill_duration       秒杀提交耗时（summary 输出 avg/med/p90/p95/p99/max）
 *   seckill_ok             受理成功率：HTTP200 且 code=success
 *   seckill_biz{code}      秒杀业务码分布（rate_limited / stock_insufficient / ... 观测用）
 *   seckill_final{status}  未支付样本的终态抽查（success/failed/processing）
 *   pay_duration           抽样支付的端到端耗时（等出单 → 预支付 → 回调）
 *   pay_ok                 支付链路成功率（预支付与回调均 success）
 *   pay_biz{step,code}     支付分步结果（step=wait_order/prepay/callback）
 *   seckill_http_fail / seckill_http_429   HTTP 层异常数
 *   （k6 内置：http_reqs 速率；dropped_iterations>0 说明 VU 不足——可降低 PAY_RATIO 或减少 SECKILL_RATE）
 *   ⚠️ code=success 仅代表"受理成功"（已投 MQ）；最终成单以 processor 消费为准，
 *      压测后按"成功订单数 + Redis 剩余 = 每 SKU 初始库存"核对（见 initdb 种子注释）。
 *
 * ── 设计要点 ────────────────────────────────────────────────────
 *   - 令牌桶限流 1 QPS/用户：VU 与账号 1:1 绑定，且每迭代 sleep(1.2s)，
 *     保证同一用户两次请求间隔 >1s，不触发 rate_limited；
 *   - VU 在 SKU 列表间均匀分布（默认 6 个 SKU 各 100 万库存，正常压测不售罄）；
 *   - PAY_RATIO：秒杀成功的请求按此比例继续走支付（模拟"部分用户付款"）；
 *     未付款的订单停留待支付，由超时延迟消息异步关单（不在压测观测窗内）；
 *   - VU 数上限 = 账号数，保证用户不重复分配。
 */
import http from 'k6/http';
import { sleep, fail } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// ============================================================
// 一、配置（读 deploy/.env；-e KEY=VALUE 可临时覆盖）
// ============================================================

/** 读取同包 .env（相对脚本路径 ../.env）；文件缺失时回退为空配置 */
function loadDotEnv() {
  try {
    const out = {};
    String(open('../.env')).split('\n').forEach((line) => {
      const s = line.trim();
      if (!s || s.startsWith('#')) return;
      const eq = s.indexOf('=');
      if (eq <= 0) return;
      out[s.slice(0, eq).trim()] = s.slice(eq + 1).replace(/\s+#.*$/, '').trim();
    });
    return out;
  } catch (e) {
    return {};
  }
}
const dotenv = loadDotEnv();

/** 取值优先级：-e（__ENV）> .env（非空）> 代码默认值 */
function cfg(key, def) {
  if (__ENV[key] !== undefined) return __ENV[key];
  return dotenv[key] !== undefined && dotenv[key] !== '' ? dotenv[key] : def;
}
function cfgNum(key, def) {
  return Number(cfg(key, def));
}

// ---- 可配置项（deploy/.env 顶部"压测 k6"段；-e 可临时覆盖）----
const BASE_URL = cfg('BASE_URL', 'http://localhost:18080');
const SECKILL_RATE = cfgNum('SECKILL_RATE', 500);
const DURATION = cfg('DURATION', '3m');
const PAY_RATIO = Math.max(0, Math.min(1, cfgNum('PAY_RATIO', 0.2))); // 0=不支付，1=全支付

// ---- 内置默认（种子环境专用，一般不改）----
const ACTIVITY_NO = 'LT-LOADTEST-001';
const USER_COUNT = 80000;
const USER_PREFIX = 'lt';
const USER_PASSWORD = '123456';
const BROWSE_RATE = 0; // 混合读流量（列表/商品/库存）；需要时改此值

// 就绪探测与运行节奏
const READY_TIMEOUT_MS = 20 * 60 * 1000; // 就绪等待上限
const PROBE_INTERVAL_S = 10;    // 就绪探测间隔（秒）
const ITER_SLEEP_S = 1.2;       // 迭代尾部休眠：同用户请求间隔 >1s（令牌桶）
const SAMPLE_POLL_RATE = 0.01;  // 未支付样本的终态抽查比例
const POLL_WAIT_S = 2;          // 抽查前的等待（留给 processor 消费）
const ORDER_WAIT_MS = 3000;     // 支付前等待出单的上限
const ORDER_POLL_S = 0.3;       // 等出单的轮询间隔

// 压测 SKU（initdb 种子，各 100 万库存）+ 秒杀价（支付回调金额核对用）
const SKU_LIST = [
  '2752035979942170624', // 茅台 单瓶
  '2752035988330778626', // 大米 5kg
  '2752035996719386628', // 褚橙 5kg
  '2752036013496602632', // 安踏 42码
  '2752036047051034640', // 小米 标准版
  '2752036055439642642', // 三只松鼠 8袋装
];
const SKU_PRICE_MAP = {
  '2752035979942170624': '2399.00',
  '2752035988330778626': '69.00',
  '2752035996719386628': '88.00',
  '2752036013496602632': '399.00',
  '2752036047051034640': '1199.00',
  '2752036055439642642': '99.00',
};

// ============================================================
// 二、自定义指标
// ============================================================

const seckillDuration = new Trend('seckill_duration', true);
const seckillOk = new Rate('seckill_ok');
const seckillBiz = new Counter('seckill_biz');
const seckillHttpFail = new Counter('seckill_http_fail');
const seckillHttp429 = new Counter('seckill_http_429');
const seckillFinal = new Counter('seckill_final');
const payDuration = new Trend('pay_duration', true);
const payOk = new Rate('pay_ok');
const payBiz = new Counter('pay_biz');
const browseFail = new Counter('browse_fail');

// ============================================================
// 三、场景
// ============================================================

export const options = {
  setupTimeout: '25m', // 就绪等待（≤20m）+ 批量登录
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    // 核心：恒定 SECKILL_RATE/s 秒杀提交
    seckill: {
      executor: 'constant-arrival-rate',
      rate: SECKILL_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 5000, // 服务器压测值（3000/s 实测需 ~4500+ VU）；本地联调（内存有限）时手动改小为 500
      maxVUs: USER_COUNT, // 与账号 1:1，用户不重复
    },
    // 混合读流量（BROWSE_RATE=0 时自动禁用）
    ...(BROWSE_RATE > 0
      ? {
          browse: {
            executor: 'constant-arrival-rate',
            exec: 'browseFn',
            rate: BROWSE_RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 20,
            maxVUs: 100,
          },
        }
      : {}),
  },
};

// ============================================================
// 四、内部工具函数
// ============================================================

/** 账号名：下标 → 前缀+五位序号（默认 lt00001 样式） */
function accountName(index) {
  return `${USER_PREFIX}${String(index + 1).padStart(5, '0')}`;
}

/** 提取业务码；非 JSON 响应（网关异常页等）返回 http_<status> */
function bizCode(res) {
  try {
    return res.json().code;
  } catch (e) {
    return `http_${res.status}`;
  }
}

/** 就绪探测：库存键已预热（>0）且活动缓存已可见 ACTIVE */
function waitUntilReady() {
  const probeSku = SKU_LIST[0];
  const deadline = Date.now() + READY_TIMEOUT_MS;
  for (let round = 1; Date.now() < deadline; round++) {
    let stock = -1;
    try {
      stock = http.get(`${BASE_URL}/api/c/activity/${ACTIVITY_NO}/sku/${probeSku}/stock`).json().data;
    } catch (e) { /* 接口未就绪 */ }
    let status = '';
    try {
      status = String(http.get(`${BASE_URL}/api/c/activity/${ACTIVITY_NO}`).json().data.activityStatus);
    } catch (e) { /* 接口未就绪 */ }
    if (stock > 0 && status === 'ACTIVE') {
      console.log(`[就绪] 预热完成：库存=${stock}，活动=ACTIVE（第 ${round} 次探测）`);
      return;
    }
    if ((round - 1) % 3 === 0) {
      console.log(`[等待] 第 ${round} 次探测：库存=${stock}，活动=${status || '未知'}——预热/激活中，${PROBE_INTERVAL_S}s 后重试`);
    }
    sleep(PROBE_INTERVAL_S);
  }
  fail('等待预热/激活超时：确认应用已全量启动、activityCacheJob / activityStatusJob 正常执行');
}

/** 懒登录：VU 首次迭代时登录自己的专属账号并缓存 token
 * （避免 setup 返回大数组被每个 VU 复制解析导致的 OOM）*/
let vuToken = null;
function ensureToken(i) {
  if (vuToken) return vuToken;
  const res = http.post(
    `${BASE_URL}/api/c/user/login`,
    JSON.stringify({ username: accountName(i % USER_COUNT), password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
  );
  let token = null;
  try { token = res.json().data.token; } catch (e) { /* 解析失败按登录失败处理 */ }
  if (res.status !== 200 || !token) {
    fail(`登录失败：${accountName(i % USER_COUNT)}，status=${res.status}，body=${String(res.body).slice(0, 200)}`);
  }
  vuToken = token;
  return token;
}

/** 记录秒杀提交结果（耗时/业务码/HTTP 异常），返回是否受理成功 */
function recordSeckillResult(res) {
  seckillDuration.add(res.timings.duration);
  const code = bizCode(res);
  if (res.status !== 200) {
    seckillHttpFail.add(1);
    if (res.status === 429) seckillHttp429.add(1);
  }
  seckillBiz.add(1, { code });
  const ok = res.status === 200 && code === 'success';
  seckillOk.add(ok);
  return ok;
}

/** 等待 processor 出单（轮询秒杀结果）；成功返回 orderNo，失败/超时返回 null 并记录指标 */
function waitOrderNo(traceId, token) {
  const deadline = Date.now() + ORDER_WAIT_MS;
  let last = 'timeout';
  while (Date.now() < deadline) {
    const res = http.get(
      `${BASE_URL}/api/c/seckill/poll?traceId=${encodeURIComponent(traceId)}`,
      { headers: { Authorization: `Bearer ${token}` }, tags: { name: 'seckill_poll' } }
    );
    try {
      const vo = res.json().data;
      if (vo && vo.status === 'success' && vo.orderNo) return vo.orderNo;
      if (vo && vo.status === 'failed') {
        last = 'failed';
        break;
      }
      last = 'processing';
    } catch (e) { /* 继续轮询 */ }
    sleep(ORDER_POLL_S);
  }
  payBiz.add(1, { step: 'wait_order', code: last });
  return null;
}

/** 完整支付链路（按比例抽样）：等出单 → 预支付（取码）→ 模拟回调（付款） */
function simulatePay(seckillRes, token, skuNo) {
  let traceId = null;
  try { traceId = seckillRes.json().data; } catch (e) { /* 忽略 */ }
  if (!traceId) return;

  const t0 = Date.now();
  const orderNo = waitOrderNo(traceId, token);
  if (!orderNo) return;

  // 预支付：归属与状态校验 + 渠道预创建（必须是下单人 token）
  const prepay = http.post(
    `${BASE_URL}/api/c/pay/prepay?orderNo=${encodeURIComponent(orderNo)}`,
    null,
    { headers: { Authorization: `Bearer ${token}` }, tags: { name: 'pay_prepay' } }
  );
  const prepayCode = bizCode(prepay);
  payBiz.add(1, { step: 'prepay', code: prepayCode });

  // 模拟支付回调（白名单接口）：带金额触发金额核对；无映射时缺省（服务端跳过核对）
  let callbackCode = 'skipped';
  if (prepayCode === 'success') {
    const amount = SKU_PRICE_MAP[skuNo];
    let url = `${BASE_URL}/api/c/pay/callback?out_trade_no=${encodeURIComponent(orderNo)}&trade_status=PAID`;
    if (amount) url += `&total_amount=${amount}`;
    callbackCode = bizCode(http.get(url, { tags: { name: 'pay_callback' } }));
    payBiz.add(1, { step: 'callback', code: callbackCode });
  }

  payDuration.add(Date.now() - t0);
  payOk.add(prepayCode === 'success' && callbackCode === 'success');
}

/** 抽样轮询终态：验证"受理 → processor 消费 → 终态"异步闭环 */
function samplePollFinal(res, token) {
  let traceId = null;
  try { traceId = res.json().data; } catch (e) { /* 忽略 */ }
  if (!traceId) return;

  sleep(POLL_WAIT_S);
  const poll = http.get(
    `${BASE_URL}/api/c/seckill/poll?traceId=${encodeURIComponent(traceId)}`,
    { headers: { Authorization: `Bearer ${token}` }, tags: { name: 'seckill_poll_final' } }
  );
  let status = `poll_http_${poll.status}`;
  try { status = String(poll.json().data && poll.json().data.status); } catch (e) { /* 忽略 */ }
  seckillFinal.add(1, { status });
}

// ============================================================
// 五、setup 与场景入口
// ============================================================

export function setup() {
  waitUntilReady();
  return {};
}

/** 秒杀提交（默认场景）：VU 与账号、SKU 固定绑定（token 懒登录缓存）*/
export default function () {
  const i = __VU - 1;
  const token = ensureToken(i % USER_COUNT);
  const skuNo = SKU_LIST[i % SKU_LIST.length];

  const res = http.post(
    `${BASE_URL}/api/c/seckill`,
    JSON.stringify({ activityNo: ACTIVITY_NO, skuNo, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } }
  );
  const ok = recordSeckillResult(res);

  if (ok && Math.random() < PAY_RATIO) {
    simulatePay(res, token, skuNo);        // 按比例走完整支付（含等出单）
  } else if (ok && Math.random() < SAMPLE_POLL_RATE) {
    samplePollFinal(res, token);           // 未支付样本的终态抽查
  }
  sleep(ITER_SLEEP_S);
}

/** 混合读流量（browse 场景） */
export function browseFn() {
  const r = Math.random();
  let url;
  if (r < 0.5) {
    url = `${BASE_URL}/api/c/activity/list`;
  } else if (r < 0.8) {
    url = `${BASE_URL}/api/c/activity/${ACTIVITY_NO}/products`;
  } else {
    url = `${BASE_URL}/api/c/activity/${ACTIVITY_NO}/sku/${SKU_LIST[Math.floor(Math.random() * SKU_LIST.length)]}/stock`;
  }
  if (http.get(url).status !== 200) browseFail.add(1);
}
