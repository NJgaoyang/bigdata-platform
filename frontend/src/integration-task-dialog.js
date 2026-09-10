const DIALOG_ID = 'integration-task-create-dialog';

function token() {
  return window.localStorage.getItem('platform_access_token') || '';
}

function api(path, options = {}) {
  const headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
  const accessToken = token();
  if (accessToken) headers.Authorization = 'Bearer ' + accessToken;
  if (options.body != null && typeof options.body !== 'string') {
    headers['Content-Type'] = 'application/json';
    options = Object.assign({}, options, { body: JSON.stringify(options.body) });
  }
  return window.fetch('/api' + path, Object.assign({}, options, { headers })).then(async (response) => {
    let payload = {};
    try { payload = await response.json(); } catch (_) { payload = {}; }
    if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
    return payload.data;
  });
}

function notify(message, error) {
  const toast = document.getElementById('toast');
  if (!toast) {
    if (error) window.alert(message);
    return;
  }
  const icon = toast.querySelector('i');
  const label = toast.querySelector('span');
  if (icon) icon.className = error ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';
  if (label) label.textContent = message;
  toast.classList.add('show');
  window.clearTimeout(notify.timer);
  notify.timer = window.setTimeout(() => toast.classList.remove('show'), 3200);
}

function installStyles() {
  if (document.getElementById('integration-task-dialog-style')) return;
  const style = document.createElement('style');
  style.id = 'integration-task-dialog-style';
  style.textContent = `
    #${DIALOG_ID}{display:none;position:fixed;inset:0;z-index:1450;background:rgba(15,27,45,.3);align-items:center;justify-content:center;padding:20px}
    #${DIALOG_ID}.open{display:flex}
    #${DIALOG_ID} .it-card{width:780px;max-width:100%;max-height:calc(100vh - 40px);overflow:auto;background:#fff;border:1px solid #e2e9f2;border-radius:12px;box-shadow:0 16px 48px rgba(20,36,58,.2)}
    #${DIALOG_ID} .it-head{display:flex;align-items:center;justify-content:space-between;padding:17px 20px 14px;border-bottom:1px solid #edf1f6}
    #${DIALOG_ID} .it-title{display:flex;align-items:center;gap:9px;color:#1e2f46;font-size:18px;font-weight:650}
    #${DIALOG_ID} .it-title i{color:#3478f6;font-size:20px}
    #${DIALOG_ID} .it-close{border:0;background:none;color:#8896a9;font-size:24px;cursor:pointer}
    #${DIALOG_ID} form{padding:18px 20px 20px;display:grid;grid-template-columns:1fr 1fr;gap:14px 16px}
    #${DIALOG_ID} .it-section{grid-column:1/-1;margin-top:2px;padding-top:12px;border-top:1px solid #edf1f6;color:#26394f;font-size:14px;font-weight:650}
    #${DIALOG_ID} .it-section.first{border-top:0;padding-top:0}
    #${DIALOG_ID} label{display:grid;gap:6px;color:#55677e;font-size:13px}
    #${DIALOG_ID} label.full{grid-column:1/-1}
    #${DIALOG_ID} input,#${DIALOG_ID} select{height:38px;border:1px solid #d8e2ee;border-radius:8px;padding:0 10px;background:#fff;color:#26394f;font:inherit;font-size:13px;outline:none}
    #${DIALOG_ID} input:focus,#${DIALOG_ID} select:focus{border-color:#6e9cf4;box-shadow:0 0 0 2px #edf4ff}
    #${DIALOG_ID} input:disabled,#${DIALOG_ID} select:disabled{background:#f7f9fc;color:#98a4b5}
    #${DIALOG_ID} .it-hint{grid-column:1/-1;padding:10px 12px;border-radius:8px;background:#f6f9ff;color:#64758b;font-size:12px;line-height:1.6}
    #${DIALOG_ID} .it-hint b{color:#3478f6}
    #${DIALOG_ID} .it-error{grid-column:1/-1;min-height:18px;color:#df4545;font-size:12px}
    #${DIALOG_ID} .it-foot{grid-column:1/-1;display:flex;justify-content:flex-end;gap:9px;padding-top:14px;border-top:1px solid #edf1f6}
    #${DIALOG_ID} .it-foot button{height:35px;padding:0 18px;border:1px solid #d3deea;border-radius:8px;background:#fff;color:#506176;font-size:13px;cursor:pointer}
    #${DIALOG_ID} .it-foot .primary{background:#3478f6;border-color:#3478f6;color:#fff}
    #${DIALOG_ID} .it-foot button:disabled{opacity:.6;cursor:not-allowed}
    @media(max-width:760px){#${DIALOG_ID} form{grid-template-columns:1fr}#${DIALOG_ID} label.full,#${DIALOG_ID} .it-section,#${DIALOG_ID} .it-hint,#${DIALOG_ID} .it-error,#${DIALOG_ID} .it-foot{grid-column:1}}
  `;
  document.head.appendChild(style);
}

function ensureDialog() {
  let host = document.getElementById(DIALOG_ID);
  if (host) return host;
  installStyles();
  host = document.createElement('div');
  host.id = DIALOG_ID;
  host.setAttribute('aria-hidden', 'true');
  host.innerHTML = `
    <section class="it-card" role="dialog" aria-modal="true" aria-labelledby="integration-task-title">
      <div class="it-head">
        <div class="it-title" id="integration-task-title"><i class="ri-node-tree"></i>新建同步任务</div>
        <button type="button" class="it-close" aria-label="关闭">×</button>
      </div>
      <form id="integration-task-create-form">
        <div class="it-section first">基本信息</div>
        <label class="full"><span>任务名称</span><input name="name" required maxlength="128" placeholder="例如：订单全量同步"></label>
        <div class="it-section">数据来源（MySQL）</div>
        <label><span>源数据源</span><select name="sourceDataSourceId" required><option value="">加载中...</option></select></label>
        <label><span>源数据库</span><select name="sourceDatabase" required disabled><option value="">请先选择源数据源</option></select></label>
        <label class="full"><span>源表</span><select name="sourceTable" required disabled><option value="">请先选择源数据库</option></select></label>
        <div class="it-section">数据目标（StarRocks）</div>
        <label><span>目标数据源</span><select name="targetDataSourceId" required><option value="">加载中...</option></select></label>
        <label><span>目标数据库</span><input name="targetDatabase" required placeholder="例如：ods"></label>
        <label class="full"><span>目标表</span><input name="targetTable" required placeholder="默认与源表同名"></label>
        <div class="it-section">SeaTunnel 2.3.12 参数</div>
        <label><span>并行度</span><input name="parallelism" type="number" min="1" max="128" value="1" required></label>
        <label><span>批次大小</span><input name="batchSize" type="number" min="1" value="1000" required></label>
        <label class="full"><span>WHERE 条件（可选）</span><input name="where" placeholder="例如：create_time >= '2026-09-01'"></label>
        <div class="it-hint">当前任务按 <b>MySQL JDBC Source → StarRocks JDBC Sink</b> 生成 SeaTunnel 2.3.12 HOCON 配置。账号密码由后端根据数据源 ID 读取，不会写入浏览器页面。</div>
        <div class="it-error" role="alert"></div>
        <div class="it-foot"><button type="button" class="cancel">取消</button><button type="submit" class="primary">创建任务</button></div>
      </form>
    </section>`;
  document.body.appendChild(host);
  host.querySelector('.it-close').onclick = closeDialog;
  host.querySelector('.cancel').onclick = closeDialog;
  host.onclick = (event) => { if (event.target === host) closeDialog(); };
  host.querySelector('form').onsubmit = submit;
  const form = host.querySelector('form');
  form.elements.sourceDataSourceId.onchange = () => loadSourceDatabases(form);
  form.elements.sourceDatabase.onchange = () => loadSourceTables(form);
  form.elements.sourceTable.onchange = () => {
    if (!form.elements.targetTable.value.trim()) form.elements.targetTable.value = form.elements.sourceTable.value;
  };
  form.elements.targetDataSourceId.onchange = () => {
    const selected = state.targets.find((item) => String(item.id) === form.elements.targetDataSourceId.value);
    if (selected && !form.elements.targetDatabase.value.trim()) form.elements.targetDatabase.value = selected.databaseName || 'ods';
  };
  return host;
}

const state = { sources: [], targets: [] };

function option(value, label) {
  const node = document.createElement('option');
  node.value = String(value == null ? '' : value);
  node.textContent = label;
  return node;
}

function setOptions(select, items, placeholder) {
  select.innerHTML = '';
  select.appendChild(option('', placeholder));
  items.forEach((item) => select.appendChild(option(item.value, item.label)));
}

async function loadDataSources(form) {
  const summary = await api('/dashboard/sources');
  const items = Array.isArray(summary && summary.items) ? summary.items : [];
  state.sources = items.filter((item) => String(item.type || '').toUpperCase() === 'MYSQL');
  state.targets = items.filter((item) => String(item.type || '').toUpperCase() === 'STARROCKS');
  setOptions(form.elements.sourceDataSourceId, state.sources.map((item) => ({ value: item.id, label: item.name + (item.databaseName ? ' · ' + item.databaseName : '') })), '请选择 MySQL 数据源');
  setOptions(form.elements.targetDataSourceId, state.targets.map((item) => ({ value: item.id, label: item.name + (item.databaseName ? ' · ' + item.databaseName : '') })), '请选择 StarRocks 数据源');
  if (state.sources.length === 1) {
    form.elements.sourceDataSourceId.value = String(state.sources[0].id);
    await loadSourceDatabases(form);
  }
  if (state.targets.length === 1) {
    form.elements.targetDataSourceId.value = String(state.targets[0].id);
    form.elements.targetDatabase.value = state.targets[0].databaseName || 'ods';
  } else if (!form.elements.targetDatabase.value) {
    form.elements.targetDatabase.value = 'ods';
  }
  if (!state.sources.length || !state.targets.length) {
    throw new Error('创建同步任务前需要至少一个 MySQL 数据源和一个 StarRocks 数据源');
  }
}

async function loadSourceDatabases(form) {
  const select = form.elements.sourceDatabase;
  const sourceId = form.elements.sourceDataSourceId.value;
  select.disabled = true;
  setOptions(select, [], sourceId ? '正在读取数据库...' : '请先选择源数据源');
  form.elements.sourceTable.disabled = true;
  setOptions(form.elements.sourceTable, [], '请先选择源数据库');
  if (!sourceId) return;
  try {
    const databases = await api('/integration/tasks/source-databases?dataSourceId=' + encodeURIComponent(sourceId));
    setOptions(select, (databases || []).map((item) => ({ value: item.name, label: item.name })), '请选择源数据库');
    select.disabled = false;
    const source = state.sources.find((item) => String(item.id) === String(sourceId));
    if (source && source.databaseName && (databases || []).some((item) => item.name === source.databaseName)) {
      select.value = source.databaseName;
      await loadSourceTables(form);
    }
  } catch (error) {
    setOptions(select, [], '数据库读取失败');
    throw error;
  }
}

async function loadSourceTables(form) {
  const select = form.elements.sourceTable;
  const sourceId = form.elements.sourceDataSourceId.value;
  const database = form.elements.sourceDatabase.value;
  select.disabled = true;
  setOptions(select, [], database ? '正在读取表...' : '请先选择源数据库');
  if (!sourceId || !database) return;
  try {
    const tables = await api('/integration/tasks/source-tables?dataSourceId=' + encodeURIComponent(sourceId) + '&database=' + encodeURIComponent(database));
    setOptions(select, (tables || []).map((item) => ({ value: item.name, label: item.comment ? item.name + ' · ' + item.comment : item.name })), '请选择源表');
    select.disabled = false;
  } catch (error) {
    setOptions(select, [], '表读取失败');
    throw error;
  }
}

function sourceById(id) {
  return state.sources.find((item) => String(item.id) === String(id));
}

function targetById(id) {
  return state.targets.find((item) => String(item.id) === String(id));
}

async function submit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const errorNode = form.querySelector('.it-error');
  const button = form.querySelector('.primary');
  errorNode.textContent = '';
  const values = new FormData(form);
  const source = sourceById(values.get('sourceDataSourceId'));
  const target = targetById(values.get('targetDataSourceId'));
  const sourceDatabase = String(values.get('sourceDatabase') || '').trim();
  const sourceTable = String(values.get('sourceTable') || '').trim();
  const targetDatabase = String(values.get('targetDatabase') || '').trim();
  const targetTable = String(values.get('targetTable') || '').trim() || sourceTable;
  if (!source || !target || !sourceDatabase || !sourceTable || !targetDatabase || !targetTable) {
    errorNode.textContent = '请完整选择源数据和目标数据';
    return;
  }
  const body = {
    name: String(values.get('name') || '').trim(),
    sourceType: 'MYSQL',
    targetType: 'STARROCKS',
    syncMode: 'FULL',
    sourceDataSourceId: Number(source.id),
    targetDataSourceId: Number(target.id),
    source: { host: source.host, port: Number(source.port), database: sourceDatabase, username: source.username || 'root', password: '', table: sourceTable },
    target: { host: target.host, port: Number(target.port), database: targetDatabase, username: target.username || 'root', password: '', table: targetTable },
    mappings: [],
    options: {
      parallelism: Number(values.get('parallelism')) || 1,
      batchSize: Number(values.get('batchSize')) || 1000,
      where: String(values.get('where') || '').trim()
    },
    tables: [{ sourceDatabase, sourceTable, targetDatabase, targetTable, partitionColumn: '' }]
  };
  if (!body.name) {
    errorNode.textContent = '请填写任务名称';
    return;
  }
  button.disabled = true;
  button.textContent = '创建中…';
  try {
    const created = await api('/integration/tasks', { method: 'POST', body });
    closeDialog();
    notify('同步任务已创建，配置按 SeaTunnel 2.3.12 生成');
    const refresh = document.querySelector('#page-integration .integration-table .filter-row .mbtn.sm');
    if (refresh) refresh.click();
    window.dispatchEvent(new CustomEvent('platform:integration-task-created', { detail: created }));
  } catch (error) {
    errorNode.textContent = error.message || '同步任务创建失败';
  } finally {
    button.disabled = false;
    button.textContent = '创建任务';
  }
}

function closeDialog() {
  const host = document.getElementById(DIALOG_ID);
  if (!host) return;
  host.classList.remove('open');
  host.setAttribute('aria-hidden', 'true');
}

async function openDialog() {
  const host = ensureDialog();
  const form = host.querySelector('form');
  form.reset();
  form.elements.parallelism.value = '1';
  form.elements.batchSize.value = '1000';
  form.elements.targetDatabase.value = 'ods';
  setOptions(form.elements.sourceDataSourceId, [], '加载中...');
  setOptions(form.elements.targetDataSourceId, [], '加载中...');
  setOptions(form.elements.sourceDatabase, [], '请先选择源数据源');
  setOptions(form.elements.sourceTable, [], '请先选择源数据库');
  form.elements.sourceDatabase.disabled = true;
  form.elements.sourceTable.disabled = true;
  form.querySelector('.it-error').textContent = '';
  host.classList.add('open');
  host.setAttribute('aria-hidden', 'false');
  try {
    await loadDataSources(form);
    window.setTimeout(() => form.elements.name.focus(), 0);
  } catch (error) {
    form.querySelector('.it-error').textContent = error.message || '数据源加载失败';
  }
}

function isCreateButton(target) {
  const button = target && target.closest ? target.closest('button') : null;
  if (!button) return null;
  const page = button.closest('#page-integration');
  if (!page) return null;
  return button.textContent && button.textContent.replace(/\s+/g, '').includes('新建同步任务') ? button : null;
}

// Capture phase intentionally overrides the older positional .module-actions binding.
// The integration toolbar moved during the UI refactor; matching by the actual action
// label keeps the button functional even when its container layout changes again.
document.addEventListener('click', (event) => {
  if (!isCreateButton(event.target)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  openDialog();
}, true);

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') closeDialog();
});

window.platformIntegrationTaskDialog = { open: openDialog };
