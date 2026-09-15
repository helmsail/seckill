/** 秒杀前端公共库：请求封装 / 登录态 / 轻提示 / 格式化工具（零依赖） */

const TOKEN_KEY = 'seckill_token';
const USER_KEY = 'seckill_user';

const auth = {
  token: () => localStorage.getItem(TOKEN_KEY),
  user: () => { try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; } },
  save(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clear() { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); },
};

/** 统一请求：自动附加 token；业务失败抛出带 message 的 Error；登录失效清理登录态 */
async function api(method, url, body) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth.token()) headers.Authorization = 'Bearer ' + auth.token();
  const res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 401 || res.status === 403) {
    auth.clear();
    setTimeout(() => location.reload(), 800); // 回到登录页
    throw new Error('登录已失效，请重新登录');
  }
  let data;
  try { data = await res.json(); } catch { throw new Error('服务响应异常（HTTP ' + res.status + '）'); }
  if (!data.success) throw new Error(data.message || '请求失败');
  return data.data;
}

const apiGet = (url) => api('GET', url);
const apiPost = (url, body) => api('POST', url, body);
const apiPut = (url, body) => api('PUT', url, body);
const apiDel = (url, body) => api('DELETE', url, body);

/** 轻提示：右下角浮层自动消失 */
function toast(msg, ok = true) {
  const el = document.createElement('div');
  el.className = 'toast' + (ok ? '' : ' toast-error');
  el.textContent = msg;
  document.body.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 2400);
}

/** HTML 转义（拼模板串时防 XSS） */
function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const money = (n) => (n == null ? '-' : Number(n).toFixed(2));

const ACTIVITY_STATUS = { PENDING: '待开始', ACTIVE: '进行中', PAUSED: '已暂停', CLOSED: '已关闭' };
const ORDER_STATUS = { PENDING: '待支付', PAID: '已支付', CLOSED: '已关闭' };
const DISCOUNT_TYPES = {
  FIXED_PRICE: '固定秒杀价（参数=秒杀价）',
  DISCOUNT: '折扣（参数=系数，如 0.6）',
  FIXED_REDUCTION: '固定扣减（参数=立减额）',
};

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

/** 简单弹层：title/body/foot 为 HTML；onReady(root, close) 渲染后回调；点击遮罩或 [data-close] 关闭 */
function modal({ title, body, foot, wide = false, onReady }) {
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.innerHTML = `
    <div class="modal${wide ? ' wide' : ''}">
      <div class="modal-head">${title}</div>
      <div class="modal-body">${body}</div>
      ${foot ? `<div class="modal-foot">${foot}</div>` : ''}
    </div>`;
  document.body.appendChild(mask);
  const close = () => mask.remove();
  mask.addEventListener('click', (e) => { if (e.target === mask) close(); });
  $$('[data-close]', mask).forEach((b) => { b.onclick = close; });
  onReady?.(mask, close);
  return close;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
