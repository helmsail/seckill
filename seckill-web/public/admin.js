/** 运营端：登录 / 活动管理（增删改查/暂停恢复关闭）/ 选品 / 上下架 */

let currentNo = '';        // 当前查看详情的活动编号
let currentDetail = null;  // { activity, products: [{ product, skus }] }
const activityMap = {};    // activityNo -> ActivityDTO（列表缓存，供编辑表单取原值）

/* ---------- 视图与登录态 ---------- */

function show(view) {
  ['view-login', 'view-list', 'view-detail'].forEach((id) => { $('#' + id).hidden = id !== view; });
}

function refreshTopbar() {
  const user = auth.user();
  const logged = !!auth.token() && !!user;
  $('#who').textContent = logged ? user.username : '';
  $('#btnLogout').hidden = !logged;
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

/* ---------- 登录 ---------- */

$('#formLogin').onsubmit = async (e) => {
  e.preventDefault();
  try {
    const data = await apiPost('/api/admin/user/login', {
      username: $('#loginUser').value.trim(),
      password: $('#loginPass').value,
    });
    if (data.user.role !== 1) throw new Error('该账号非运营账号，无权进入管理端');
    auth.save(data.token, data.user);
    await showMain();
  } catch (err) {
    toast(err.message, false);
  }
};

$('#btnLogout').onclick = () => { auth.clear(); location.reload(); };

/* ---------- 活动列表 ---------- */

$('#filterStatus').onchange = () => loadActivities().catch((err) => toast(err.message, false));
$('#btnCreate').onclick = () => openActivityForm(null);
$('#btnBack').onclick = () => showMain();

async function loadActivities() {
  const status = $('#filterStatus').value;
  const list = await apiGet('/api/admin/activity/list' + (status ? '?status=' + status : ''));
  list.forEach((a) => { activityMap[a.activityNo] = a; });
  renderActivityTable(list);
}

function renderActivityTable(list) {
  const table = $('#activityTable');
  if (!list.length) {
    table.innerHTML = '<tbody><tr><td class="empty">暂无活动</td></tr></tbody>';
    return;
  }
  table.innerHTML = `
    <thead><tr>
      <th>活动</th><th>日期</th><th>时段</th><th>限购</th><th>状态</th><th style="width: 300px">操作</th>
    </tr></thead>
    <tbody>${list.map((a) => {
      const ops = [`<button class="btn btn-sm" data-act="detail" data-no="${esc(a.activityNo)}">详情</button>`];
      if (a.activityStatus === 'PENDING') {
        ops.push(`<button class="btn btn-sm" data-act="edit" data-no="${esc(a.activityNo)}">编辑</button>`);
        ops.push(`<button class="btn btn-sm btn-danger" data-act="delete" data-no="${esc(a.activityNo)}">删除</button>`);
      }
      if (a.activityStatus === 'ACTIVE') {
        ops.push(`<button class="btn btn-sm" data-act="pause" data-no="${esc(a.activityNo)}">暂停</button>`);
        ops.push(`<button class="btn btn-sm" data-act="close" data-no="${esc(a.activityNo)}">关闭</button>`);
      }
      if (a.activityStatus === 'PAUSED') {
        ops.push(`<button class="btn btn-sm" data-act="resume" data-no="${esc(a.activityNo)}">恢复</button>`);
        ops.push(`<button class="btn btn-sm" data-act="close" data-no="${esc(a.activityNo)}">关闭</button>`);
      }
      return `<tr>
        <td><div style="font-weight: 600">${esc(a.activityName)}</div><div class="muted">${esc(a.activityNo)}</div></td>
        <td>${esc(a.startDate)} ~ ${esc(a.endDate)}</td>
        <td>${esc((a.startTime || '').slice(0, 5))} ~ ${esc((a.endTime || '').slice(0, 5))}</td>
        <td>${a.purchaseLimit ?? 0}</td>
        <td><span class="badge badge-${esc(a.activityStatus)}">${ACTIVITY_STATUS[a.activityStatus] || esc(a.activityStatus)}</span></td>
        <td class="row" style="flex-wrap: nowrap">${ops.join('')}</td>
      </tr>`;
    }).join('')}</tbody>`;
}

$('#activityTable').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-act]');
  if (!btn) return;
  const { act, no } = btn.dataset;
  try {
    if (act === 'detail') return await openDetail(no);
    if (act === 'edit') return openActivityForm(activityMap[no]);
    if (act === 'delete') { if (!confirm('确认删除该活动？')) return; await apiDel('/api/admin/activity/' + no); toast('已删除'); }
    if (act === 'pause') { await apiPut(`/api/admin/activity/${no}/pause`); toast('已暂停'); }
    if (act === 'resume') { await apiPut(`/api/admin/activity/${no}/resume`); toast('已恢复'); }
    if (act === 'close') { if (!confirm('确认关闭该活动？关闭后将归还全部活动库存')) return; await apiPut(`/api/admin/activity/${no}/close`); toast('已关闭'); }
    await loadActivities();
  } catch (err) {
    toast(err.message, false);
  }
});

/* ---------- 新建 / 编辑活动 ---------- */

function openActivityForm(a) {
  const edit = !!a;
  modal({
    title: edit ? '编辑活动' : '新建活动',
    body: `
      <div class="form-grid">
        <div class="field full"><label>活动名称</label><input class="input" id="fName" value="${esc(a?.activityName ?? '')}"></div>
        <div class="field"><label>开始日期</label><input class="input" type="date" id="fStartDate" value="${a?.startDate ?? ''}"></div>
        <div class="field"><label>结束日期</label><input class="input" type="date" id="fEndDate" value="${a?.endDate ?? ''}"></div>
        <div class="field"><label>开始时间</label><input class="input" type="time" id="fStartTime" value="${a ? (a.startTime || '').slice(0, 5) : '10:00'}"></div>
        <div class="field"><label>结束时间</label><input class="input" type="time" id="fEndTime" value="${a ? (a.endTime || '').slice(0, 5) : '22:00'}"></div>
        <div class="field"><label>每人限购（0=不限）</label><input class="input" type="number" min="0" id="fLimit" value="${a?.purchaseLimit ?? 0}"></div>
      </div>
      <div class="field"><label>生效星期</label>
        <div class="checks">${['一', '二', '三', '四', '五', '六', '日'].map((d, i) => `
          <label><input type="checkbox" data-bit="${i}" ${!a || hasBit(a.weekBitmap, i) ? 'checked' : ''}>周${d}</label>`).join('')}
        </div>
      </div>`,
    foot: `
      <button class="btn" data-close>取消</button>
      <button class="btn btn-primary" id="btnSave">保存</button>`,
    onReady(root, close) {
      $('#btnSave', root).onclick = async () => {
        const weekBitmap = $$('input[type=checkbox]:checked', root)
          .reduce((m, c) => m | (1 << Number(c.dataset.bit)), 0);
        const body = {
          activityName: $('#fName', root).value.trim(),
          startDate: toYmd($('#fStartDate', root).value),
          endDate: toYmd($('#fEndDate', root).value),
          startTime: toHms($('#fStartTime', root).value),
          endTime: toHms($('#fEndTime', root).value),
          weekBitmap,
          purchaseLimit: Number($('#fLimit', root).value) || 0,
        };
        if (!body.activityName || !body.startDate || !body.endDate || !body.startTime || !body.endTime) {
          return toast('请完整填写活动信息', false);
        }
        if (!weekBitmap) return toast('请至少选择一个生效星期', false);
        try {
          if (edit) await apiPut('/api/admin/activity/' + a.activityNo, body);
          else await apiPost('/api/admin/activity', body);
          toast(edit ? '已保存' : '创建成功');
          close();
          await loadActivities();
        } catch (err) {
          toast(err.message, false);
        }
      };
    },
  });
}

const hasBit = (bitmap, i) => !bitmap || ((bitmap >> i) & 1) === 1;

/** 后端时间/日期格式：LocalTime=HH:mm:ss、LocalDate=yyyy-MM-dd（time 组件默认不带秒，须补齐） */
const toHms = (v) => {
  const m = /^(\d{1,2}):(\d{2})(?::(\d{2}))?/.exec(v || '');
  return m ? `${m[1].padStart(2, '0')}:${m[2]}:${m[3] || '00'}` : v;
};
const toYmd = (v) => {
  const m = /^(\d{4})[/.](\d{1,2})[/.](\d{1,2})/.exec(v || '');
  return m ? `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}` : v;
};

/* ---------- 活动详情 ---------- */

async function openDetail(activityNo) {
  currentNo = activityNo;
  currentDetail = await apiGet(`/api/admin/activity/${activityNo}/detail`);
  renderDetail();
  show('view-detail');
}

function renderDetail() {
  const a = currentDetail.activity;
  const st = a.activityStatus;
  const canEdit = st === 'PENDING';

  $('#detailHead').innerHTML = `
    <div class="row between">
      <div>
        <div style="font-weight: 600; font-size: 15px">${esc(a.activityName)}</div>
        <div class="muted">编号 ${esc(a.activityNo)}</div>
        <div class="muted">${esc(a.startDate)} ~ ${esc(a.endDate)} · ${esc((a.startTime || '').slice(0, 5))} ~ ${esc((a.endTime || '').slice(0, 5))} · 限购 ${a.purchaseLimit ?? 0}</div>
      </div>
      <span class="badge badge-${esc(st)}">${ACTIVITY_STATUS[st] || esc(st)}</span>
    </div>`;

  $('#detailTools').innerHTML = [
    canEdit ? '<button class="btn btn-sm btn-primary" id="btnAddProduct">+ 添加商品</button>' : '',
    canEdit ? '<button class="btn btn-sm btn-danger" id="btnRemoveSkus">删除所选</button>' : '',
    st !== 'CLOSED' && currentDetail.products.length
      ? '<button class="btn btn-sm" id="btnOnShelf">批量上架</button><button class="btn btn-sm" id="btnOffShelf">批量下架</button>' : '',
  ].filter(Boolean).join(' ');

  const body = currentDetail.products.map(({ product, skus }) => `
    <tr><td colspan="7" class="spu-cell" style="background: #fafbfc">
      ${esc(product.spuName)} <span class="muted">${esc(product.spuNo)} · ${esc(discountText(product.discountType, product.discountParameter))}</span>
    </td></tr>
    ${skus.map((s) => `
      <tr>
        <td><input type="checkbox" class="sku-check" data-sku="${esc(s.skuNo)}"></td>
        <td>${esc(s.skuName)}<div class="muted">${esc(s.skuNo)}</div></td>
        <td>¥${money(s.originalPrice)}</td>
        <td class="price" style="font-size: 13px">¥${money(s.seckillPrice)}</td>
        <td>${s.activityStock ?? '-'}</td>
        <td>${s.purchaseLimit ?? 0}</td>
        <td><span class="badge badge-${s.shelfStatus === 1 ? 'onshelf' : 'offshelf'}">${s.shelfStatus === 1 ? '在售' : '下架'}</span></td>
      </tr>`).join('')}`).join('');

  $('#skuTable').innerHTML = `
    <thead><tr>
      <th style="width: 36px"></th><th>SKU</th><th>定价</th><th>秒杀价</th><th>活动库存</th><th>限购</th><th>状态</th>
    </tr></thead>
    <tbody>${body || '<tr><td colspan="7" class="empty">尚未添加商品</td></tr>'}</tbody>`;

  if ($('#btnAddProduct')) $('#btnAddProduct').onclick = openAddProduct;
  if ($('#btnRemoveSkus')) $('#btnRemoveSkus').onclick = removeSelected;
  if ($('#btnOnShelf')) $('#btnOnShelf').onclick = () => shelfSelected(true);
  if ($('#btnOffShelf')) $('#btnOffShelf').onclick = () => shelfSelected(false);
}

function discountText(type, param) {
  if (type === 'FIXED_PRICE') return `固定价 ¥${money(param)}`;
  if (type === 'DISCOUNT') return `折扣系数 ${param}`;
  if (type === 'FIXED_REDUCTION') return `立减 ¥${money(param)}`;
  return type || '';
}

const selectedSkuNos = () => $$('#skuTable .sku-check:checked').map((c) => c.dataset.sku);

async function removeSelected() {
  const skuNos = selectedSkuNos();
  if (!skuNos.length) return toast('请先勾选 SKU', false);
  if (!confirm('确认从活动中删除所选 SKU？（活动库存将归还主域）')) return;
  try {
    await apiDel('/api/admin/product-sku', { activityNo: currentNo, skuNos });
    toast('已删除');
    await openDetail(currentNo);
  } catch (err) {
    toast(err.message, false);
  }
}

async function shelfSelected(onShelf) {
  const skuNos = selectedSkuNos();
  if (!skuNos.length) return toast('请先勾选 SKU', false);
  try {
    await apiPut('/api/admin/product-sku/shelf', { activityNo: currentNo, skuNos, onShelf });
    toast(onShelf ? '已上架' : '已下架');
    await openDetail(currentNo);
  } catch (err) {
    toast(err.message, false);
  }
}

/* ---------- 选品（添加商品到活动） ---------- */

async function openAddProduct() {
  let page;
  try {
    page = await apiGet('/api/admin/support/product/list?pageNum=1&pageSize=200');
  } catch (err) {
    toast(err.message, false);
    return;
  }
  const products = page.records || [];
  modal({
    title: '添加商品到活动',
    wide: true,
    body: `
      <p class="muted" style="margin-bottom: 10px">选择左侧主域商品 → 勾选右侧 SKU → 填写秒杀规则 → 添加</p>
      <div class="row" style="align-items: flex-start; gap: 16px; flex-wrap: nowrap">
        <div style="min-width: 250px; max-height: 420px; overflow: auto" id="pickProducts">
          ${products.map((p, i) => `
            <div class="pick-product" data-spu="${esc(p.spuNo)}" data-name="${esc(p.productName)}"
                 style="padding: 8px 10px; border-radius: 8px; cursor: pointer">
              <div>${esc(p.productName)}</div>
              <div class="muted">${esc(p.spuNo)}</div>
            </div>`).join('') || '<p class="muted">主域暂无商品</p>'}
        </div>
        <div class="grow" id="pickSkus" style="max-height: 420px; overflow: auto; min-width: 0">
          <p class="muted">请先选择左侧商品</p>
        </div>
      </div>`,
    foot: `
      <button class="btn" data-close>取消</button>
      <button class="btn btn-primary" id="btnAdd">添加所选</button>`,
    onReady(root, close) {
      let currentSpu = null;

      $$('.pick-product', root).forEach((el) => {
        el.onclick = async () => {
          $$('.pick-product', root).forEach((x) => { x.style.background = ''; });
          el.style.background = '#f0f1f3';
          currentSpu = { spuNo: el.dataset.spu, spuName: el.dataset.name };
          $('#pickSkus', root).innerHTML = '<p class="muted">加载中…</p>';
          try {
            const skus = await apiGet(`/api/admin/support/product/${encodeURIComponent(currentSpu.spuNo)}/skus`);
            renderSkuPick(root, skus);
          } catch (err) {
            $('#pickSkus', root).innerHTML = `<p class="muted">${esc(err.message)}</p>`;
          }
        };
      });

      $('#btnAdd', root).onclick = async () => {
        if (!currentSpu) return toast('请先选择商品', false);
        const items = [];
        $$('.pick-sku', root).forEach((row) => {
          if (!row.querySelector('.pick-check').checked) return;
          const discountParameter = row.querySelector('.pick-param').value.trim();
          const activityStock = Number(row.querySelector('.pick-stock').value);
          if (!discountParameter || Number(discountParameter) <= 0) return toast(`请填写 ${row.dataset.name} 的折扣参数`, false);
          if (!activityStock || activityStock <= 0) return toast(`请填写 ${row.dataset.name} 的活动库存`, false);
          items.push({
            spuNo: currentSpu.spuNo,
            spuName: currentSpu.spuName,
            skuNo: row.dataset.sku,
            skuName: row.dataset.name,
            originalPrice: row.dataset.price,
            discountType: row.querySelector('.pick-type').value,
            discountParameter,
            activityStock,
            purchaseLimit: Number(row.querySelector('.pick-limit').value) || 0,
          });
        });
        if (!items.length) return toast('请至少勾选一个 SKU', false);
        try {
          await apiPost('/api/admin/product-sku', { activityNo: currentNo, items });
          toast('添加成功');
          close();
          await openDetail(currentNo);
        } catch (err) {
          toast(err.message, false);
        }
      };
    },
  });
}

function renderSkuPick(root, skus) {
  const box = $('#pickSkus', root);
  if (!skus.length) {
    box.innerHTML = '<p class="muted">该商品暂无 SKU</p>';
    return;
  }
  box.innerHTML = `
    <div class="table-wrap"><table class="table">
      <thead><tr>
        <th style="width: 32px"></th><th>SKU</th><th>定价</th><th>秒杀方式</th><th>参数</th><th>库存</th><th>限购</th>
      </tr></thead>
      <tbody>${skus.map((s) => `
        <tr class="pick-sku" data-sku="${esc(s.skuNo)}" data-name="${esc(s.skuName)}" data-price="${s.price}">
          <td><input type="checkbox" class="pick-check" checked></td>
          <td>${esc(s.skuName)}<div class="muted">${esc(s.skuNo)}</div></td>
          <td>¥${money(s.price)}</td>
          <td><select class="input mini pick-type" style="width: 120px">
            <option value="FIXED_PRICE">固定秒杀价</option>
            <option value="DISCOUNT">折扣系数</option>
            <option value="FIXED_REDUCTION">固定扣减</option>
          </select></td>
          <td><input class="input mini pick-param" placeholder="50 / 0.6 / 20"></td>
          <td><input class="input mini pick-stock" type="number" min="1" value="10"></td>
          <td><input class="input mini pick-limit" type="number" min="0" value="0"></td>
        </tr>`).join('')}
      </tbody>
    </table></div>`;
}

/* ---------- 启动 ---------- */

(auth.token() ? showMain() : Promise.resolve(showLogin()));
