/** C 端（用户侧）：登录 / 活动列表 / 商品秒杀 / 模拟支付 / 订单查询 */

let currentActivityNo = '';
let currentActivityName = '';
let currentActivityStatus = 'ACTIVE';
const activityCache = {};

/* ---------- 视图与登录态 ---------- */

function show(view) {
  ['view-login', 'view-list', 'view-detail'].forEach((id) => { $('#' + id).hidden = id !== view; });
}

function refreshTopbar() {
  const user = auth.user();
  const logged = !!auth.token() && !!user;
  $('#who').textContent = logged ? user.username : '';
  ['btnOrders', 'btnLogout'].forEach((id) => { $('#' + id).hidden = !logged; });
}

function showLogin() {
  refreshTopbar();
  show('view-login');
}

async function showMain() {
  refreshTopbar();
  show('view-list');
  await loadActivities().catch((err) => toast(err.message, false));
}

/* ---------- 登录 / 退出 ---------- */

$('#formLogin').onsubmit = async (e) => {
  e.preventDefault();
  try {
    const data = await apiPost('/api/c/user/login', {
      username: $('#loginUser').value.trim(),
      password: $('#loginPass').value,
    });
    auth.save(data.token, data.user);
    await showMain();
  } catch (err) {
    toast(err.message, false);
  }
};

$('#btnLogout').onclick = () => { auth.clear(); location.reload(); };
$('#btnOrders').onclick = openQueryModal;
$('#btnBack').onclick = () => showMain();

/* ---------- 活动列表 ---------- */

async function loadActivities() {
  const list = await apiGet('/api/c/activity/list');
  list.forEach((a) => { activityCache[a.activityNo] = a; });
  if (!list.length) {
    $('#activityList').innerHTML = '<div class="empty">暂无活动</div>';
    return;
  }
  $('#activityList').innerHTML = list.map((a) => `
    <div class="card activity-card" data-no="${esc(a.activityNo)}">
      <div class="row between">
        <div>
          <div class="name">${esc(a.activityName)}</div>
          <div class="meta">${esc(a.startDate)} ~ ${esc(a.endDate)} · ${esc((a.startTime || '').slice(0, 5))} ~ ${esc((a.endTime || '').slice(0, 5))} · 限购 ${a.purchaseLimit ?? 0}</div>
        </div>
        <span class="badge badge-${esc(a.activityStatus)}">${ACTIVITY_STATUS[a.activityStatus] || esc(a.activityStatus)}</span>
      </div>
    </div>`).join('');
}

$('#activityList').addEventListener('click', (e) => {
  const card = e.target.closest('.activity-card');
  if (card) openActivity(card.dataset.no);
});

/* ---------- 活动详情与商品 ---------- */

async function openActivity(activityNo) {
  currentActivityNo = activityNo;
  const a = activityCache[activityNo] || {};
  currentActivityName = a.activityName || '';
  currentActivityStatus = a.activityStatus || 'ACTIVE';
  $('#detailHead').innerHTML = `
    <div class="row between">
      <div>
        <div class="name">${esc(a.activityName || '活动')}</div>
        <div class="meta">${esc(a.startDate || '')} ~ ${esc(a.endDate || '')} · ${esc((a.startTime || '').slice(0, 5))} ~ ${esc((a.endTime || '').slice(0, 5))}</div>
      </div>
      <span class="badge badge-${esc(a.activityStatus)}">${ACTIVITY_STATUS[a.activityStatus] || ''}</span>
    </div>`;
  show('view-detail');
  await loadProducts().catch((err) => toast(err.message, false));
}

async function loadProducts() {
  const list = await apiGet(`/api/c/activity/${encodeURIComponent(currentActivityNo)}/products`);
  const box = $('#productList');
  if (!list.length) {
    box.innerHTML = '<div class="empty">该活动暂无可购商品</div>';
    return;
  }
  box.innerHTML = list.map((p) => {
    const off = p.shelfStatus !== 1;
    const soldOut = !p.remainingStock || p.remainingStock <= 0;
    // 活动非进行中（待开始/已暂停/已关闭）：不可购买，按钮禁用并显示状态
    const inactive = currentActivityStatus !== 'ACTIVE';
    const disabled = off || soldOut || inactive;
    return `
    <div class="card product-card">
      <div class="info">
        <div>${esc(p.spuName)} <span class="sku">${esc(p.skuName)}</span></div>
        <div>
          <span class="price"><span class="unit">¥</span>${money(p.seckillPrice)}</span>
          <span class="price-old">¥${money(p.originalPrice)}</span>
          <span class="muted">　剩余 ${p.remainingStock ?? 0}</span>
        </div>
      </div>
      <span class="badge badge-${off ? 'offshelf' : 'onshelf'}">${off ? '已下架' : '在售'}</span>
      <input class="input mini qty" type="number" min="1" value="1" ${disabled ? 'disabled' : ''}>
      <button class="btn btn-primary btn-sm buy" data-sku="${esc(p.skuNo)}" data-price="${p.seckillPrice}" ${disabled ? 'disabled' : ''}>
        ${inactive ? (ACTIVITY_STATUS[currentActivityStatus] || '不可购买') : off ? '已下架' : soldOut ? '已售罄' : '立即抢购'}
      </button>
    </div>`;
  }).join('');
}

$('#productList').addEventListener('click', async (e) => {
  const btn = e.target.closest('.buy');
  if (!btn) return;
  const qtyInput = btn.closest('.product-card').querySelector('.qty');
  const quantity = Math.max(1, parseInt(qtyInput.value, 10) || 1);
  await doSeckill(btn.dataset.sku, Number(btn.dataset.price), quantity, btn);
});

/* ---------- 秒杀与轮询 ---------- */

async function doSeckill(skuNo, seckillPrice, quantity, btn) {
  btn.disabled = true;
  btn.textContent = '抢购中…';
  try {
    const traceId = await apiPost('/api/c/seckill', { activityNo: currentActivityNo, skuNo, quantity });
    let result = null;
    for (let i = 0; i < 15; i++) {
      await sleep(1000);
      result = await apiGet('/api/c/seckill/poll?traceId=' + encodeURIComponent(traceId));
      if (!result || String(result.status).toLowerCase() !== 'processing') break;
    }
    if (result && String(result.status).toLowerCase() === 'success') {
      const amount = (seckillPrice * quantity).toFixed(2);
      openOrderModal(result.orderNo, amount);
      await loadProducts();
    } else {
      toast('抢购失败：' + ((result && result.reason) || '请稍后再试'), false);
    }
  } catch (err) {
    toast(err.message, false);
  } finally {
    btn.disabled = false;
    btn.textContent = '立即抢购';
  }
}

/* ---------- 订单与支付 ---------- */

function openOrderModal(orderNo, amount) {
  modal({
    title: '抢购成功',
    body: `
      <p>订单号：<b>${esc(orderNo)}</b></p>
      <p>应付金额：<span class="price"><span class="unit">¥</span>${money(amount)}</span></p>
      <p class="muted" style="margin-top: 6px">请在 10 分钟内完成支付，超时将自动关单</p>`,
    foot: `
      <button class="btn" data-close>稍后支付</button>
      <button class="btn btn-primary" id="btnGoPay">去支付</button>`,
    onReady(root, close) {
      $('#btnGoPay', root).onclick = () => { close(); openPayModal(orderNo, amount); };
    },
  });
}

async function openPayModal(orderNo, amount) {
  let note;
  try {
    note = await apiPost('/api/c/pay/prepay?orderNo=' + encodeURIComponent(orderNo));
  } catch (err) {
    toast(err.message, false);
    return;
  }
  modal({
    title: '模拟支付',
    body: `
      <p style="margin-bottom: 10px">订单号：<b>${esc(orderNo)}</b>　应付：<b>¥${money(amount)}</b></p>
      <div class="pay-note">${esc(note)}</div>`,
    foot: `
      <button class="btn" data-close>关闭</button>
      <button class="btn btn-primary" id="btnPaid">模拟支付成功</button>`,
    onReady(root, close) {
      const btn = $('#btnPaid', root);
      btn.onclick = async () => {
        btn.disabled = true;
        try {
          await apiGet(`/api/c/pay/callback?out_trade_no=${encodeURIComponent(orderNo)}&trade_status=PAID&total_amount=${encodeURIComponent(amount)}`);
          toast('支付成功');
          close();
        } catch (err) {
          toast(err.message, false);
          btn.disabled = false;
        }
      };
    },
  });
}

function openQueryModal() {
  modal({
    title: '订单查询',
    body: `
      <div class="row">
        <input class="input grow" id="qOrderNo" placeholder="输入订单号">
        <button class="btn btn-primary" id="btnQuery">查询</button>
      </div>
      <div id="queryResult" style="margin-top: 14px"></div>`,
    foot: '<button class="btn" data-close>关闭</button>',
    onReady(root) {
      $('#btnQuery', root).onclick = async () => {
        const orderNo = $('#qOrderNo', root).value.trim();
        if (!orderNo) return;
        try {
          const s = await apiGet('/api/c/order/status?orderNo=' + encodeURIComponent(orderNo));
          const badge = s.status === 'PAID' ? 'ACTIVE' : s.status === 'PENDING' ? 'PENDING' : 'CLOSED';
          $('#queryResult', root).innerHTML = `
            <p>订单号：<b>${esc(s.orderNo)}</b></p>
            <p>状态：<span class="badge badge-${badge}">${ORDER_STATUS[s.status] || esc(s.status)}</span></p>
            ${s.paidTime ? `<p class="muted">支付时间：${esc(s.paidTime)}</p>` : ''}`;
        } catch (err) {
          $('#queryResult', root).innerHTML = `<p class="muted">${esc(err.message)}</p>`;
        }
      };
    },
  });
}

/* ---------- 启动 ---------- */

(auth.token() ? showMain() : Promise.resolve(showLogin()));
