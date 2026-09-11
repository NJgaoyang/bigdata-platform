const CREATE_DIALOG_ID = 'settings-cluster-create-dialog';
const CREATE_BUTTON_ID = 'settingsNewCluster';

function accessToken() {
  return window.localStorage.getItem('platform_access_token') || '';
}

function api(path, options = {}) {
  const headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
  const token = accessToken();
  if (token) headers.Authorization = 'Bearer ' + token;
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

function currentType() {
  const active = document.querySelector('#settingsClusterSubnav [data-cluster-view].on, [data-cluster-view].on');
  const value = active && active.dataset.clusterView;
  return value === 'dolphin' || value === 'seatunnel' ? value : null;
}

function config(type) {
  return type === 'dolphin'
    ? { path: '/system/dolphinscheduler-clusters', label: 'DolphinScheduler' }
    : { path: '/system/clusters', label: 'SeaTunnel' };
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
  notify.timer = window.setTimeout(() => toast.classList.remove('show'), 2800);
}

function installStyles() {
  if (document.getElementById('settings-cluster-fix-style')) return;
  const style = document.createElement('style');
  style.id = 'settings-cluster-fix-style';
  style.textContent = `
    #${CREATE_BUTTON_ID}{height:34px;padding:0 15px;border:1px solid #3478f6;border-radius:8px;background:#3478f6;color:#fff;font-size:13px;cursor:pointer;display:inline-flex;align-items:center;gap:6px;white-space:nowrap}
    #${CREATE_BUTTON_ID}:hover{background:#286ce8;border-color:#286ce8}
    #${CREATE_DIALOG_ID}{display:none;position:fixed;inset:0;z-index:1400;background:rgba(15,27,45,.28);align-items:center;justify-content:center;padding:20px}
    #${CREATE_DIALOG_ID}.open{display:flex}
    #${CREATE_DIALOG_ID} .cluster-fix-card{width:660px;max-width:100%;max-height:calc(100vh - 40px);overflow:auto;background:#fff;border:1px solid #e3eaf3;border-radius:12px;box-shadow:0 14px 42px rgba(20,36,58,.18)}
    #${CREATE_DIALOG_ID} .cluster-fix-head{display:flex;align-items:center;justify-content:space-between;padding:17px 20px 13px;border-bottom:1px solid #edf1f6}
    #${CREATE_DIALOG_ID} .cluster-fix-head h3{margin:0;font-size:18px;color:#1d2e45;font-weight:650}
    #${CREATE_DIALOG_ID} .cluster-fix-close{border:0;background:none;color:#8996a8;font-size:24px;cursor:pointer}
    #${CREATE_DIALOG_ID} form{padding:18px 20px;display:grid;grid-template-columns:1fr 1fr;gap:14px}
    #${CREATE_DIALOG_ID} label{display:grid;gap:6px;color:#52647b;font-size:13px}
    #${CREATE_DIALOG_ID} label.full{grid-column:1/-1}
    #${CREATE_DIALOG_ID} input,#${CREATE_DIALOG_ID} textarea{height:38px;border:1px solid #d9e3ef;border-radius:8px;padding:0 11px;color:#28394e;background:#fff;font:inherit;font-size:13px;outline:none}
    #${CREATE_DIALOG_ID} textarea{height:72px;padding-top:9px;resize:vertical}
    #${CREATE_DIALOG_ID} input:focus,#${CREATE_DIALOG_ID} textarea:focus{border-color:#729ff2;box-shadow:0 0 0 2px #eaf2ff}
    #${CREATE_DIALOG_ID} .cluster-fix-hint{color:#8b98aa;font-size:11px}
    #${CREATE_DIALOG_ID} .cluster-fix-error{grid-column:1/-1;min-height:16px;color:#df4545;font-size:12px}
    #${CREATE_DIALOG_ID} .cluster-fix-foot{grid-column:1/-1;display:flex;justify-content:flex-end;gap:9px;border-top:1px solid #f0f2f5;padding-top:14px}
    #${CREATE_DIALOG_ID} .cluster-fix-foot button{height:34px;padding:0 17px;border-radius:8px;border:1px solid #d5dfeb;background:#fff;color:#536276;font-size:13px;cursor:pointer}
    #${CREATE_DIALOG_ID} .cluster-fix-foot .primary{border-color:#3478f6;background:#3478f6;color:#fff}
    #${CREATE_DIALOG_ID} .cluster-fix-foot button:disabled{opacity:.6;cursor:not-allowed}
    @media(max-width:720px){#${CREATE_DIALOG_ID} form{grid-template-columns:1fr}#${CREATE_DIALOG_ID} label.full,#${CREATE_DIALOG_ID} .cluster-fix-error,#${CREATE_DIALOG_ID} .cluster-fix-foot{grid-column:1}}
  `;
  document.head.appendChild(style);
}

function ensureDialog() {
  let host = document.getElementById(CREATE_DIALOG_ID);
  if (host) return host;
  host = document.createElement('div');
  host.id = CREATE_DIALOG_ID;
  host.setAttribute('aria-hidden', 'true');
  host.innerHTML = '<section class="cluster-fix-card" role="dialog" aria-modal="true" aria-labelledby="settings-cluster-fix-title"><div class="cluster-fix-head"><h3 id="settings-cluster-fix-title">新增集群</h3><button type="button" class="cluster-fix-close" aria-label="关闭">×</button></div><form id="settings-cluster-fix-form"></form></section>';
  document.body.appendChild(host);
  const close = () => closeDialog();
  host.querySelector('.cluster-fix-close').onclick = close;
  host.onclick = (event) => { if (event.target === host) close(); };
  return host;
}

function closeDialog() {
  const host = document.getElementById(CREATE_DIALOG_ID);
  if (!host) return;
  host.classList.remove('open');
  host.setAttribute('aria-hidden', 'true');
  delete host.dataset.clusterType;
  delete host.dataset.clusterId;
}

function dolphinUrl(item) {
  if (!item) return '';
  const path = String(item.basePath || '/dolphinscheduler');
  return 'http://' + String(item.host || '') + ':' + String(item.port || 12345) + (path.startsWith('/') ? path : '/' + path);
}

function parseDolphinUrl(value) {
  let raw = String(value || '').trim();
  if (!raw) return null;
  if (!/^https?:\/\//i.test(raw)) raw = 'http://' + raw;
  try {
    const parsed = new URL(raw);
    if (!parsed.hostname) return null;
    return {
      host: parsed.hostname,
      port: Number(parsed.port || (parsed.protocol === 'https:' ? 443 : 80)),
      basePath: parsed.pathname && parsed.pathname !== '/' ? parsed.pathname.replace(/\/+$/, '') : '/dolphinscheduler'
    };
  } catch (_) {
    return null;
  }
}

function dialogFields(type, existing) {
  const passwordHint = existing ? '留空表示不修改' : '请输入密码';
  if (type === 'dolphin') {
    return `
      <label><span>集群名称</span><input name="name" required placeholder="例如：生产调度集群"></label>
      <label><span>版本</span><input name="version" placeholder="例如：3.1.9"></label>
      <label class="full"><span>服务 URL</span><input name="url" required placeholder="例如：http://127.0.0.1:12345/dolphinscheduler"><span class="cluster-fix-hint">填写 DolphinScheduler Web 服务地址，保存时会拆分为主机、端口和 Base Path。</span></label>
      <label><span>用户名</span><input name="username" autocomplete="off" placeholder="DolphinScheduler 登录用户名"></label>
      <label><span>密码</span><input name="password" type="password" autocomplete="new-password" placeholder="${passwordHint}"></label>
      <label class="full"><span>安装目录</span><input name="installDir" placeholder="例如：/opt/dolphinscheduler"></label>
      <label class="full"><span>描述</span><textarea name="description" placeholder="集群描述（可选）"></textarea></label>
      <div class="cluster-fix-error" role="alert"></div>
      <div class="cluster-fix-foot"><button type="button" class="cancel">取消</button><button type="submit" class="primary">保存</button></div>`;
  }
  return `
    <label><span>集群名称</span><input name="name" required placeholder="例如：生产 SeaTunnel 集群"></label>
    <label><span>主机地址</span><input name="host" required placeholder="例如：10.0.0.20"></label>
    <label><span>服务端口</span><input name="port" type="number" min="1" max="65535" required placeholder="请输入实际服务端口"></label>
    <label><span>SSH 用户名</span><input name="sshUsername" autocomplete="off" placeholder="例如：root"></label>
    <label><span>SSH 端口</span><input name="sshPort" type="number" min="1" max="65535" value="22" required></label>
    <label><span>SSH 密码</span><input name="sshPassword" type="password" autocomplete="new-password" placeholder="${passwordHint}"></label>
    <label class="full"><span>SeaTunnel 安装目录</span><input name="seatunnelHome" required placeholder="例如：/opt/seatunnel"></label>
    <label class="full"><span>描述</span><textarea name="description" placeholder="集群描述（可选）"></textarea></label>
    <div class="cluster-fix-error" role="alert"></div>
    <div class="cluster-fix-foot"><button type="button" class="cancel">取消</button><button type="submit" class="primary">保存</button></div>`;
}

function fillForm(form, type, existing) {
  if (!existing) return;
  form.elements.name.value = existing.name || '';
  form.elements.description.value = existing.description || '';
  if (type === 'dolphin') {
    form.elements.url.value = dolphinUrl(existing);
    form.elements.version.value = existing.version || '';
    form.elements.username.value = existing.username || '';
    form.elements.installDir.value = existing.installDir || '';
  } else {
    form.elements.host.value = existing.host || '';
    form.elements.port.value = existing.port || '';
    form.elements.sshUsername.value = existing.sshUsername || '';
    form.elements.sshPort.value = existing.sshPort || 22;
    form.elements.seatunnelHome.value = existing.seatunnelHome || '';
  }
}

function submitDialog(event) {
  event.preventDefault();
  const host = document.getElementById(CREATE_DIALOG_ID);
  const form = event.currentTarget;
  const error = form.querySelector('.cluster-fix-error');
  const type = host.dataset.clusterType;
  const id = host.dataset.clusterId || '';
  const values = new FormData(form);
  let body;
  if (type === 'dolphin') {
    const parsed = parseDolphinUrl(values.get('url'));
    if (!parsed) {
      error.textContent = '请输入有效的 DolphinScheduler 服务 URL';
      return;
    }
    body = {
      name: String(values.get('name') || '').trim(),
      host: parsed.host,
      port: parsed.port,
      basePath: parsed.basePath,
      version: String(values.get('version') || '').trim(),
      username: String(values.get('username') || '').trim(),
      password: String(values.get('password') || ''),
      installDir: String(values.get('installDir') || '').trim(),
      description: String(values.get('description') || '').trim()
    };
  } else {
    body = {
      name: String(values.get('name') || '').trim(),
      host: String(values.get('host') || '').trim(),
      port: Number(values.get('port')),
      sshUsername: String(values.get('sshUsername') || '').trim(),
      sshPort: Number(values.get('sshPort')) || 22,
      sshPassword: String(values.get('sshPassword') || ''),
      seatunnelHome: String(values.get('seatunnelHome') || '').trim(),
      description: String(values.get('description') || '').trim()
    };
  }
  if (!body.name) {
    error.textContent = '请填写集群名称';
    return;
  }
  const cfg = config(type);
  const button = form.querySelector('.primary');
  button.disabled = true;
  button.textContent = id ? '保存中…' : '创建中…';
  api(cfg.path + (id ? '/' + encodeURIComponent(id) : ''), {
    method: id ? 'PUT' : 'POST',
    body
  }).then(() => {
    closeDialog();
    notify(id ? cfg.label + ' 集群已更新' : cfg.label + ' 集群已创建');
    refreshExistingClusterList(type);
  }).catch((e) => {
    error.textContent = e.message || '保存失败';
  }).finally(() => {
    button.disabled = false;
    button.textContent = '保存';
  });
}

function openDialog(type, existing) {
  if (type !== 'dolphin' && type !== 'seatunnel') return;
  installStyles();
  const host = ensureDialog();
  const form = host.querySelector('#settings-cluster-fix-form');
  host.dataset.clusterType = type;
  if (existing && existing.id != null) host.dataset.clusterId = String(existing.id);
  else delete host.dataset.clusterId;
  host.querySelector('#settings-cluster-fix-title').textContent = (existing ? '编辑 ' : '新增 ') + config(type).label + ' 集群';
  form.innerHTML = dialogFields(type, existing);
  fillForm(form, type, existing);
  form.querySelector('.cancel').onclick = closeDialog;
  form.onsubmit = submitDialog;
  host.classList.add('open');
  host.setAttribute('aria-hidden', 'false');
  window.setTimeout(() => form.elements.name && form.elements.name.focus(), 0);
}

function refreshExistingClusterList(type) {
  const parent = document.querySelector('[data-settings-tab="clusters"]');
  if (parent) parent.click();
  window.setTimeout(() => {
    const child = document.querySelector('[data-cluster-view="' + type + '"]');
    if (child) child.click();
    syncCreateButton();
  }, 40);
}

async function editCluster(type, id) {
  const cfg = config(type);
  const items = await api(cfg.path);
  const existing = (Array.isArray(items) ? items : []).find((item) => String(item.id) === String(id));
  if (!existing) throw new Error('集群不存在或已被删除');
  openDialog(type, existing);
}

function syncCreateButton() {
  const type = currentType();
  let button = document.getElementById(CREATE_BUTTON_ID);
  const check = document.getElementById('settingsCheckClusters');
  const toolbar = check && check.parentElement;
  if (!button && toolbar) {
    button = document.createElement('button');
    button.id = CREATE_BUTTON_ID;
    button.type = 'button';
    button.innerHTML = '<i class="ri-add-line"></i><span>新增集群</span>';
    toolbar.insertBefore(button, check);
  }
  if (!button) return;

  const settingsVisible = !!document.querySelector('[data-settings-panel="clusters"]:not([hidden])');
  const shouldHide = !type || !settingsVisible || !(window.platformAuth && window.platformAuth.isAdmin);
  if (button.hidden !== shouldHide) button.hidden = shouldHide;

  if (!shouldHide) {
    const label = button.querySelector('span');
    const nextLabel = type === 'dolphin' ? '新增 DolphinScheduler 集群' : '新增 SeaTunnel 集群';
    if (label && label.textContent !== nextLabel) label.textContent = nextLabel;
    if (button.dataset.clusterType !== type) button.dataset.clusterType = type;
  }
}

// live-data.js updates the authenticated admin state asynchronously. Expose a
// small refresh hook so the create entry is shown immediately after identity
// loading, rather than remaining hidden from the initial unauthenticated boot.
window.settingsClusterFixSync = syncCreateButton;

function installInteractions() {
  document.addEventListener('click', (event) => {
    const create = event.target.closest('#' + CREATE_BUTTON_ID);
    if (create) {
      event.preventDefault();
      event.stopImmediatePropagation();
      openDialog(create.dataset.clusterType || currentType(), null);
      return;
    }

    const edit = event.target.closest('[data-cluster-edit]');
    if (edit) {
      const type = edit.dataset.clusterType || currentType();
      if (type === 'dolphin' || type === 'seatunnel') {
        event.preventDefault();
        event.stopImmediatePropagation();
        editCluster(type, edit.dataset.clusterEdit).catch((e) => notify(e.message || '读取集群配置失败', true));
        return;
      }
    }

    if (event.target.closest('[data-cluster-view], [data-settings-tab="clusters"]')) {
      window.setTimeout(syncCreateButton, 0);
    }
  }, true);

  const root = document.getElementById('page-settings') || document.body;
  let syncQueued = false;
  const observer = new MutationObserver(() => {
    if (syncQueued) return;
    syncQueued = true;
    window.requestAnimationFrame(() => {
      syncQueued = false;
      syncCreateButton();
    });
  });
  observer.observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden', 'style'] });
}

function boot() {
  installStyles();
  installInteractions();
  syncCreateButton();
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
else boot();
