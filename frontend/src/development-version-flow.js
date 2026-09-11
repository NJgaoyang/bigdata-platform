const FLOW_STYLE_ID = 'development-version-flow-style';

let lastState = null;
let lastFileId = null;
let stateRequestToken = 0;
let syncQueued = false;

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

function page() {
  return document.getElementById('page-development');
}

function scope() {
  return page()?.querySelector('.dev-segment.on')?.dataset.devScope || 'mine';
}

function activeFileId() {
  const root = page();
  if (!root) return null;
  const row = root.querySelector('.live-file.on[data-id], .live-file[aria-current="true"][data-id]');
  if (row?.dataset.id) return Number(row.dataset.id);
  const activeTab = root.querySelector('.dev-tab.on');
  const candidates = [activeTab?.dataset.id, activeTab?.dataset.fileId, activeTab?.dataset.devFileId];
  for (const value of candidates) if (value && /^\d+$/.test(String(value))) return Number(value);
  return null;
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
  if (document.getElementById(FLOW_STYLE_ID)) return;
  const style = document.createElement('style');
  style.id = FLOW_STYLE_ID;
  style.textContent = `
    #page-development .dev-version-flow-online{display:inline-flex;align-items:center;gap:4px;color:#8794a6;font-size:11px}
    #page-development .dev-version-flow-online b{padding:2px 6px;border-radius:5px;background:#f3f6f9;color:#435a75;font-weight:650}
    #page-development .dev-flow-state{display:inline-flex;align-items:center;gap:5px;padding:3px 8px;border-radius:999px;font-size:10px;font-weight:650;white-space:nowrap}
    #page-development .dev-flow-state.pending{background:#fff6e8;color:#b66d12}
    #page-development .dev-flow-state.synced{background:#eefaf5;color:#148b66}
    #page-development .dev-flow-state.offline{background:#f2f4f7;color:#7c8999}
    #page-development .dev-create-development-version,#page-development .dev-unpublish-file{color:#286bc7}
    #page-development .dev-create-development-version:hover,#page-development .dev-unpublish-file:hover{background:#edf4ff;color:#1f60bc}
    .dev-flow-confirm{display:none;position:fixed;inset:0;z-index:1500;background:rgba(15,27,45,.3);align-items:center;justify-content:center;padding:20px}
    .dev-flow-confirm.open{display:flex}
    .dev-flow-confirm-card{width:min(480px,100%);background:#fff;border:1px solid #dfe7f1;border-radius:12px;box-shadow:0 18px 48px rgba(22,42,68,.22);overflow:hidden}
    .dev-flow-confirm-head{display:flex;align-items:flex-start;gap:12px;padding:17px 19px 14px;border-bottom:1px solid #edf1f5}
    .dev-flow-confirm-head i{width:34px;height:34px;display:grid;place-items:center;border-radius:9px;background:#edf4ff;color:#3478f6;font-size:19px}
    .dev-flow-confirm-head h3{margin:0;color:#203752;font-size:16px}
    .dev-flow-confirm-head p{margin:5px 0 0;color:#8190a8;font-size:11px;line-height:1.5}
    .dev-flow-confirm-close{margin-left:auto;border:0;background:none;color:#8b98a9;font-size:21px;cursor:pointer}
    .dev-flow-confirm-body{padding:16px 19px}
    .dev-flow-grid{display:grid;grid-template-columns:106px 1fr;gap:10px 12px;font-size:12px}
    .dev-flow-grid span{color:#8492a4}.dev-flow-grid b{color:#314b6c;font-weight:650;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .dev-flow-warning{display:flex;gap:8px;align-items:flex-start;margin-top:15px;padding:10px 11px;border:1px solid #f1d5a5;border-radius:8px;background:#fff8eb;color:#b87017;font-size:11px;line-height:1.55}
    .dev-flow-warning.info{border-color:#cfe0fb;background:#f3f7ff;color:#4671aa}
    .dev-flow-confirm-error{min-height:18px;margin-top:9px;color:#d84b4b;font-size:11px}
    .dev-flow-confirm-foot{display:flex;justify-content:flex-end;gap:8px;padding:12px 19px 15px;border-top:1px solid #edf1f5}
    .dev-flow-confirm-foot button{height:33px;padding:0 15px;border:1px solid #d5e0eb;border-radius:7px;background:#fff;color:#52657d;font-size:12px;cursor:pointer}
    .dev-flow-confirm-foot .primary{border-color:#3478f6;background:#3478f6;color:#fff}
    .dev-flow-confirm-foot .danger{border-color:#e55757;background:#e55757;color:#fff}
  `;
  document.head.appendChild(style);
}

function ensureActionButtons() {
  const toolbar = page()?.querySelector('.dev-toolbar');
  const publish = toolbar?.querySelector('.dev-publish-file');
  if (!toolbar || !publish) return;
  let create = toolbar.querySelector('.dev-create-development-version');
  if (!create) {
    create = document.createElement('button');
    create.type = 'button';
    create.className = 'mbtn dev-create-development-version';
    create.innerHTML = '<i class="ri-git-branch-line"></i>创建开发版本';
    create.hidden = true;
    publish.insertAdjacentElement('afterend', create);
  }
  let offline = toolbar.querySelector('.dev-unpublish-file');
  if (!offline) {
    offline = document.createElement('button');
    offline.type = 'button';
    offline.className = 'mbtn dev-unpublish-file';
    offline.innerHTML = '<i class="ri-stop-circle-line"></i>下线';
    offline.hidden = true;
    create.insertAdjacentElement('afterend', offline);
  }
}

function renamePushControls() {
  const button = page()?.querySelector('.dev-save-to-project');
  if (button && button.textContent.trim() !== '推送到项目') button.innerHTML = '<i class="ri-send-plane-2-line"></i>推送到项目';
  const host = document.getElementById('dev-project-save-modal');
  if (!host) return;
  const title = host.querySelector('h3');
  if (title) title.textContent = '推送到项目';
  const intro = host.querySelector('.dev-project-save-head p');
  if (intro) intro.textContent = '选择目标项目目录。下一步会再次确认版本信息，推送不会直接影响线上版本。';
  const submit = host.querySelector('.dev-project-save-submit');
  if (submit && !submit.disabled) submit.textContent = '下一步';
}

function versionLabel(value) {
  return value == null ? '—' : 'V' + value;
}

function renderState(state) {
  lastState = state || null;
  const root = page();
  if (!root) return;
  ensureActionButtons();
  renamePushControls();
  const activeScope = scope();
  const dev = root.querySelector('.dev-version-development');
  const online = root.querySelector('.dev-version-online');
  const owner = root.querySelector('.dev-version-owner');
  let flowOnline = root.querySelector('.dev-version-flow-online');
  let badge = root.querySelector('.dev-flow-state');
  if (!flowOnline && owner) {
    flowOnline = document.createElement('span');
    flowOnline.className = 'dev-version-flow-online';
    owner.insertAdjacentElement('beforebegin', flowOnline);
  }
  if (!badge && flowOnline) {
    badge = document.createElement('span');
    badge.className = 'dev-flow-state';
    flowOnline.insertAdjacentElement('afterend', badge);
  }

  if (!state) {
    if (flowOnline) flowOnline.hidden = true;
    if (badge) badge.hidden = true;
    return;
  }

  if (activeScope === 'mine') {
    if (dev) dev.innerHTML = '当前版本 <b>' + versionLabel(state.developmentVersion) + '</b>';
    if (online) online.innerHTML = '已推送 <b>' + versionLabel(state.pushedVersion) + '</b>';
    if (flowOnline) { flowOnline.hidden = false; flowOnline.innerHTML = '线上版本 <b>' + versionLabel(state.onlineVersion) + '</b>'; }
    if (badge) {
      badge.hidden = false;
      if (state.pendingPush) { badge.className = 'dev-flow-state pending'; badge.textContent = '有未推送版本'; }
      else if (state.pushedVersion != null) { badge.className = 'dev-flow-state synced'; badge.textContent = state.pendingPublish ? '已推送 · 待发布' : '版本已同步'; }
      else { badge.className = 'dev-flow-state offline'; badge.textContent = '尚未推送'; }
    }
  } else if (activeScope === 'all') {
    if (dev) dev.innerHTML = '项目版本 <b>' + versionLabel(state.pushedVersion ?? state.developmentVersion) + '</b>';
    if (online) online.innerHTML = '线上版本 <b>' + versionLabel(state.onlineVersion) + '</b>';
    if (flowOnline) flowOnline.hidden = true;
    if (badge) {
      badge.hidden = false;
      if (state.pendingPublish) { badge.className = 'dev-flow-state pending'; badge.textContent = '待发布'; }
      else if (state.online) { badge.className = 'dev-flow-state synced'; badge.textContent = '已上线'; }
      else { badge.className = 'dev-flow-state offline'; badge.textContent = '未上线'; }
    }
  } else {
    if (flowOnline) flowOnline.hidden = true;
    if (badge) badge.hidden = true;
  }

  const access = window.platformDevelopmentAccess || {};
  const publish = root.querySelector('.dev-publish-file');
  const create = root.querySelector('.dev-create-development-version');
  const offline = root.querySelector('.dev-unpublish-file');
  if (publish) publish.hidden = !(activeScope === 'all' && access.canPublish && state.pendingPublish);
  if (create) create.hidden = !(activeScope === 'all' && state.online && (access.canManage || access.canPublish));
  if (offline) offline.hidden = !(activeScope === 'all' && state.online && access.canPublish);
}

function refreshState(force = false) {
  const fileId = activeFileId();
  if (!fileId) {
    lastFileId = null;
    renderState(null);
    return Promise.resolve(null);
  }
  if (!force && fileId === lastFileId && lastState) {
    renderState(lastState);
    return Promise.resolve(lastState);
  }
  lastFileId = fileId;
  const requestToken = ++stateRequestToken;
  return api('/development/files/' + encodeURIComponent(fileId) + '/version-flow')
    .then((state) => {
      if (requestToken !== stateRequestToken || fileId !== activeFileId()) return null;
      renderState(state);
      return state;
    })
    .catch(() => {
      if (requestToken === stateRequestToken) renderState(null);
      return null;
    });
}

function currentFileName() {
  const row = page()?.querySelector('.live-file.on[data-id], .live-file[aria-current="true"][data-id]');
  if (row) return row.querySelector('.dev-resource-name,.live-file-name,.side-name')?.textContent?.trim() || row.textContent.trim();
  const tab = page()?.querySelector('.dev-tab.on .dev-tab-name');
  return tab?.textContent?.trim() || '当前文件';
}

function isDirty() {
  return !!page()?.querySelector('.dev-tab.on .dev-tab-dirty');
}

function confirmDialog(config) {
  let host = document.getElementById('dev-flow-confirm');
  if (!host) {
    host = document.createElement('div');
    host.id = 'dev-flow-confirm';
    host.className = 'dev-flow-confirm';
    host.setAttribute('aria-hidden', 'true');
    document.body.appendChild(host);
  }
  host.innerHTML = '<div class="dev-flow-confirm-card" role="dialog" aria-modal="true">' +
    '<div class="dev-flow-confirm-head"><i class="' + (config.icon || 'ri-send-plane-2-line') + '"></i><div><h3>' + escapeHtml(config.title) + '</h3><p>' + escapeHtml(config.subtitle || '') + '</p></div><button type="button" class="dev-flow-confirm-close">×</button></div>' +
    '<div class="dev-flow-confirm-body"><div class="dev-flow-grid">' + (config.rows || []).map((row) => '<span>' + escapeHtml(row[0]) + '</span><b title="' + escapeHtml(row[1]) + '">' + escapeHtml(row[1]) + '</b>').join('') + '</div>' +
    (config.warning ? '<div class="dev-flow-warning ' + (config.warningInfo ? 'info' : '') + '"><i class="ri-information-line"></i><span>' + escapeHtml(config.warning) + '</span></div>' : '') +
    '<div class="dev-flow-confirm-error"></div></div>' +
    '<div class="dev-flow-confirm-foot"><button type="button" class="cancel">取消</button><button type="button" class="confirm ' + (config.danger ? 'danger' : 'primary') + '">' + escapeHtml(config.confirmText || '确认') + '</button></div></div>';
  host.classList.add('open');
  host.setAttribute('aria-hidden', 'false');
  const close = () => { host.classList.remove('open'); host.setAttribute('aria-hidden', 'true'); };
  host.querySelector('.dev-flow-confirm-close').onclick = close;
  host.querySelector('.cancel').onclick = close;
  host.onclick = (event) => { if (event.target === host) close(); };
  host.querySelector('.confirm').onclick = async (event) => {
    const button = event.currentTarget;
    const error = host.querySelector('.dev-flow-confirm-error');
    button.disabled = true;
    const original = button.textContent;
    button.textContent = config.pendingText || '处理中…';
    try {
      await config.onConfirm();
      close();
    } catch (requestError) {
      error.textContent = requestError.message || '操作失败';
    } finally {
      button.disabled = false;
      button.textContent = original;
    }
  };
}

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>'"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]);
}

function closePushModal() {
  const host = document.getElementById('dev-project-save-modal');
  if (host) { host.classList.remove('open'); host.setAttribute('aria-hidden', 'true'); }
}

function focusFileInScope(scopeName, fileId) {
  const tab = page()?.querySelector('.dev-segment[data-dev-scope="' + scopeName + '"]');
  if (tab) tab.click();
  let attempts = 0;
  const timer = window.setInterval(() => {
    attempts += 1;
    const row = page()?.querySelector('.live-file[data-id="' + fileId + '"]');
    if (row) {
      window.clearInterval(timer);
      row.click();
      window.setTimeout(() => refreshState(true), 120);
    } else if (attempts >= 30) {
      window.clearInterval(timer);
      page()?.querySelector('.dev-refresh')?.click();
    }
  }, 100);
}

function handlePushSubmit(event) {
  const form = event.target;
  if (!(form instanceof HTMLFormElement) || form.id !== 'dev-project-save-form') return false;
  event.preventDefault();
  event.stopImmediatePropagation();
  if (scope() !== 'mine') { notify('只能从“我的开发”推送版本', true); return true; }
  if (isDirty()) { notify('当前编辑器有未保存修改，请先点击“保存”产生版本后再推送', true); return true; }
  const sourceFileId = activeFileId();
  if (!sourceFileId) { notify('请先保存当前开发文件后再推送', true); return true; }

  const host = document.getElementById('dev-project-save-modal');
  const folderSelect = document.getElementById('dev-project-save-folder');
  const nameInput = document.getElementById('dev-project-save-name');
  const descriptionInput = document.getElementById('dev-project-save-description');
  const error = document.getElementById('dev-project-save-error');
  const projectId = Number(host?.dataset.targetProjectId || 0);
  const folderValue = String(folderSelect?.value || '');
  const name = String(nameInput?.value || '').trim();
  if (!projectId) { if (error) error.textContent = '暂无项目空间，请先创建项目'; return true; }
  if (!folderValue) { if (error) error.textContent = '请选择目标目录'; return true; }
  if (!name) { if (error) error.textContent = '请输入文件名称'; return true; }
  const folderId = folderValue === '__root__' ? null : Number(folderValue);
  const folderName = folderSelect?.selectedOptions?.[0]?.textContent?.trim() || '项目根目录';

  refreshState(true).then((state) => {
    if (!state) throw new Error('无法读取当前版本信息');
    confirmDialog({
      title: '确认推送到项目',
      subtitle: '确认后将当前已保存版本提交到项目空间。',
      rows: [
        ['文件', name],
        ['开发版本', versionLabel(state.developmentVersion)],
        ['当前已推送', versionLabel(state.pushedVersion)],
        ['当前线上', versionLabel(state.onlineVersion)],
        ['目标目录', folderName]
      ],
      warning: '推送只更新项目空间的待发布版本，不会直接改变当前线上版本。',
      warningInfo: true,
      confirmText: '确认推送',
      pendingText: '推送中…',
      onConfirm: async () => {
        const saved = await api('/development/files/push-to-project', {
          method: 'POST',
          body: { sourceFileId, projectId, folderId, name, description: String(descriptionInput?.value || '').trim() }
        });
        closePushModal();
        notify('已推送 ' + versionLabel(state.developmentVersion) + ' 到项目，线上版本未改变');
        focusFileInScope('all', saved.id);
      }
    });
  }).catch((requestError) => {
    if (error) error.textContent = requestError.message || '读取版本失败';
  });
  return true;
}

function handlePublish(fileId) {
  return refreshState(true).then((state) => {
    if (!state) throw new Error('无法读取当前版本信息');
    if (!state.pendingPublish) { notify('当前没有待发布版本'); return; }
    confirmDialog({
      title: '确认发布上线',
      subtitle: '线上版本不可直接修改，后续修改需要回到“我的开发”。',
      icon: 'ri-upload-cloud-2-line',
      rows: [
        ['文件', currentFileName()],
        ['当前线上版本', versionLabel(state.onlineVersion)],
        ['即将上线版本', versionLabel(state.pushedVersion)]
      ],
      warning: '发布后，该版本将成为新的线上版本。推送到项目与发布上线是两个独立动作。',
      confirmText: '确认发布',
      pendingText: '发布中…',
      onConfirm: async () => {
        await api('/development/files/' + encodeURIComponent(fileId) + '/publish-pushed', { method: 'POST' });
        notify(versionLabel(state.pushedVersion) + ' 已发布上线');
        page()?.querySelector('.dev-refresh')?.click();
        window.setTimeout(() => refreshState(true), 180);
      }
    });
  });
}

function handleUnpublish(fileId) {
  return refreshState(true).then((state) => {
    if (!state?.online) { notify('当前文件没有线上版本'); return; }
    confirmDialog({
      title: '确认下线',
      subtitle: '下线只停止线上版本，不会删除开发版本或项目版本。',
      icon: 'ri-stop-circle-line',
      rows: [['文件', currentFileName()], ['当前线上版本', versionLabel(state.onlineVersion)]],
      warning: '如果工作流依赖该线上脚本，请先确认下线影响。',
      confirmText: '确认下线',
      pendingText: '下线中…',
      danger: true,
      onConfirm: async () => {
        await api('/development/files/' + encodeURIComponent(fileId) + '/unpublish', { method: 'POST' });
        notify('线上版本已下线');
        page()?.querySelector('.dev-refresh')?.click();
        window.setTimeout(() => refreshState(true), 180);
      }
    });
  });
}

function handleCreateDevelopment(fileId) {
  return refreshState(true).then((state) => {
    if (!state?.online) { notify('当前文件没有线上版本，无法创建开发版本', true); return; }
    confirmDialog({
      title: '创建开发版本',
      subtitle: '线上版本保持运行并保持只读，在“我的开发”中继续修改。',
      icon: 'ri-git-branch-line',
      rows: [['文件', currentFileName()], ['基于线上版本', versionLabel(state.onlineVersion)]],
      warning: '不会下线当前版本。进入“我的开发”后，下一次保存会产生新的版本号。',
      warningInfo: true,
      confirmText: '进入我的开发',
      pendingText: '准备中…',
      onConfirm: async () => {
        const source = await api('/development/files/' + encodeURIComponent(fileId) + '/create-development-version', { method: 'POST' });
        notify('已回到我的开发，线上版本保持不变');
        focusFileInScope('mine', source.id);
      }
    });
  });
}

function installEvents() {
  document.addEventListener('submit', (event) => {
    handlePushSubmit(event);
  }, true);

  document.addEventListener('click', (event) => {
    if (event.target.closest?.('#page-development .dev-save-to-project')) {
      window.setTimeout(renamePushControls, 0);
      return;
    }
    const publish = event.target.closest?.('#page-development .dev-publish-file');
    if (publish) {
      if (scope() !== 'all') return;
      event.preventDefault(); event.stopImmediatePropagation();
      const fileId = activeFileId();
      if (!fileId) return notify('请先选择项目文件', true);
      handlePublish(fileId).catch((error) => notify(error.message, true));
      return;
    }
    const create = event.target.closest?.('#page-development .dev-create-development-version');
    if (create) {
      event.preventDefault(); event.stopImmediatePropagation();
      const fileId = activeFileId();
      if (!fileId) return notify('请先选择项目文件', true);
      handleCreateDevelopment(fileId).catch((error) => notify(error.message, true));
      return;
    }
    const offline = event.target.closest?.('#page-development .dev-unpublish-file');
    if (offline) {
      event.preventDefault(); event.stopImmediatePropagation();
      const fileId = activeFileId();
      if (!fileId) return notify('请先选择项目文件', true);
      handleUnpublish(fileId).catch((error) => notify(error.message, true));
      return;
    }
    if (event.target.closest?.('#page-development .dev-save-file')) {
      window.setTimeout(() => refreshState(true), 650);
    }
  }, true);
}

function installObserver() {
  const root = page();
  if (!root) return;
  let observedFileId = activeFileId();
  let observedScope = scope();
  const observer = new MutationObserver(() => {
    if (syncQueued) return;
    syncQueued = true;
    window.requestAnimationFrame(() => {
      syncQueued = false;
      ensureActionButtons();
      renamePushControls();
      const nextFileId = activeFileId();
      const nextScope = scope();
      if (nextFileId !== observedFileId || nextScope !== observedScope) {
        observedFileId = nextFileId;
        observedScope = nextScope;
        lastState = null;
        refreshState(true);
      } else if (lastState) renderState(lastState);
    });
  });
  observer.observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
}

function boot() {
  installStyles();
  ensureActionButtons();
  renamePushControls();
  installEvents();
  installObserver();
  refreshState(true);
  window.platformDevelopmentVersionFlow = { refresh: () => refreshState(true) };
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
else boot();
