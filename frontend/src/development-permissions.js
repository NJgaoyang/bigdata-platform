const ACCESS_STYLE_ID = 'development-permission-controls';

const state = {
  identityLoaded: false,
  isAdmin: false,
  permissions: new Set(),
  moduleEdit: false,
  moduleView: false,
  canRun: false,
  projectAccess: new Map(),
  pendingProjectAccess: new Map()
};

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

function developmentPage() {
  return document.getElementById('page-development');
}

function scope() {
  return developmentPage()?.querySelector('.dev-segment.on')?.dataset.devScope || 'mine';
}

function currentProjectId() {
  const page = developmentPage();
  if (!page) return null;
  const favorite = page.querySelector('[data-favorite-project-id]');
  if (scope() === 'favorites' && favorite?.dataset.favoriteProjectId) return Number(favorite.dataset.favoriteProjectId);
  const activeFile = page.querySelector('.live-file.on[data-project-id], .live-file[data-project-id][aria-current="true"]');
  if (activeFile?.dataset.projectId) return Number(activeFile.dataset.projectId);
  const tree = page.querySelector('[data-project-files]');
  if (tree?.dataset.projectFiles) return Number(tree.dataset.projectFiles);
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
  if (document.getElementById(ACCESS_STYLE_ID)) return;
  const style = document.createElement('style');
  style.id = ACCESS_STYLE_ID;
  style.textContent = `
    #page-development:not([data-dev-can-edit="true"]) .dev-create-project,
    #page-development:not([data-dev-can-edit="true"]) .dev-create-file,
    #page-development:not([data-dev-can-edit="true"]) .dev-tab-add,
    #page-development:not([data-dev-can-edit="true"]) .folder-actions,
    #page-development:not([data-dev-can-edit="true"]) .favorite-row-actions,
    #page-development:not([data-dev-can-edit="true"]) .favorite-folder-delete,
    #page-development:not([data-dev-can-edit="true"]) .favorite-file-delete,
    #page-development:not([data-dev-can-edit="true"]) .dev-save-file,
    #page-development:not([data-dev-can-edit="true"]) .dev-format-file { display:none !important; }
    #page-development:not([data-dev-can-publish="true"]) .dev-publish-file { display:none !important; }
    #page-development:not([data-dev-can-save-to-project="true"]) .dev-save-to-project { display:none !important; }
    #page-development:not([data-dev-can-run="true"]) .dev-run-button { display:none !important; }
    #page-development[data-dev-readonly="true"] .code-shell { background:#fbfcfe; }
    #page-development[data-dev-readonly="true"] .dev-version-bar::after { content:'只读'; margin-left:auto; color:#8a97aa; font-size:11px; }
  `;
  document.head.appendChild(style);
}

function setAttr(page, name, value) {
  const next = String(value);
  if (page.dataset[name] !== next) page.dataset[name] = next;
}

function effectiveCanEdit(projectId, activeScope) {
  if (!state.moduleEdit) return false;
  if (activeScope === 'mine' || activeScope === 'favorites') return true;
  if (activeScope !== 'all' || projectId == null) return false;
  const access = state.projectAccess.get(String(projectId));
  return !!(access && access.edit === true);
}

function applyEditorReadOnly(readOnly) {
  const editor = window.platformMonaco?.getEditor?.();
  if (editor && typeof editor.updateOptions === 'function') editor.updateOptions({ readOnly: !!readOnly });
}

function applyControls() {
  const page = developmentPage();
  if (!page) return;
  const activeScope = scope();
  const projectId = currentProjectId();
  const canEdit = effectiveCanEdit(projectId, activeScope);
  const canPublish = activeScope === 'all' && canEdit;
  const canSaveToProject = activeScope === 'mine' && state.moduleEdit;

  setAttr(page, 'devScopeCurrent', activeScope);
  setAttr(page, 'devCanEdit', canEdit);
  setAttr(page, 'devCanPublish', canPublish);
  setAttr(page, 'devCanSaveToProject', canSaveToProject);
  setAttr(page, 'devCanRun', state.canRun);
  setAttr(page, 'devReadonly', !canEdit);

  window.platformAuth = window.platformAuth || {};
  window.platformAuth.permissions = Array.from(state.permissions);
  window.platformAuth.canDevelopmentView = state.moduleView;
  window.platformAuth.canDevelopmentEdit = state.moduleEdit;
  window.platformDevelopmentAccess = {
    scope: activeScope,
    projectId,
    canView: state.moduleView,
    canEdit,
    canPublish,
    canSaveToProject,
    canRun: state.canRun
  };

  if (!canEdit) {
    const menu = document.getElementById('dev-context-menu');
    if (menu) menu.hidden = true;
  }
  applyEditorReadOnly(!canEdit);
}

function loadProjectAccess(projectId) {
  if (projectId == null || !state.moduleView) return Promise.resolve(null);
  const key = String(projectId);
  if (state.projectAccess.has(key)) return Promise.resolve(state.projectAccess.get(key));
  if (state.pendingProjectAccess.has(key)) return state.pendingProjectAccess.get(key);
  const pending = api('/development/projects/' + encodeURIComponent(projectId) + '/access')
    .then((access) => {
      state.projectAccess.set(key, access || { projectId, view: true, edit: false });
      return access;
    })
    .catch(() => {
      state.projectAccess.set(key, { projectId, view: true, edit: false });
      return null;
    })
    .finally(() => {
      state.pendingProjectAccess.delete(key);
      applyControls();
    });
  state.pendingProjectAccess.set(key, pending);
  return pending;
}

function sync() {
  applyControls();
  const activeScope = scope();
  const projectId = currentProjectId();
  if (activeScope === 'all' && projectId != null) loadProjectAccess(projectId);
}

function loadIdentity() {
  return api('/auth/me').then((identity) => {
    const role = String(identity?.roleCode || identity?.role || '').toUpperCase();
    state.isAdmin = !!(identity?.authenticated && (identity?.superAdmin === true || role === 'ADMIN' || role === 'SUPER_ADMIN'));
    state.permissions = new Set(Array.isArray(identity?.permissions) ? identity.permissions : []);
    state.moduleView = state.isAdmin || state.permissions.has('DATA_DEVELOPMENT_VIEW') || state.permissions.has('DATA_DEVELOPMENT_EDIT') || state.permissions.has('DATA_DEVELOPMENT_PROJECT_ALL');
    state.moduleEdit = state.isAdmin || state.permissions.has('DATA_DEVELOPMENT_EDIT') || state.permissions.has('DATA_DEVELOPMENT_PROJECT_ALL');
    // QueryController is currently mapped to METADATA; POST execute/submit requires METADATA_EDIT.
    state.canRun = state.isAdmin || state.permissions.has('METADATA_EDIT');
    state.identityLoaded = true;
    const nav = document.querySelector('.nav button[data-page="development"]');
    if (nav) nav.style.display = state.moduleView ? '' : 'none';
    applyControls();
  }).catch(() => {
    state.identityLoaded = true;
    state.moduleView = false;
    state.moduleEdit = false;
    state.canRun = false;
    applyControls();
  });
}

function isMutationTarget(target) {
  return target.closest?.([
    '#page-development .dev-create-project',
    '#page-development .dev-create-file',
    '#page-development .dev-tab-add',
    '#page-development .folder-add',
    '#page-development .folder-more',
    '#page-development .favorite-file-delete',
    '#page-development .favorite-folder-delete',
    '#dev-context-menu [data-action]',
    '#page-development .dev-save-file',
    '#page-development .dev-format-file'
  ].join(','));
}

function installGuards() {
  document.addEventListener('click', (event) => {
    const page = developmentPage();
    if (!page) return;
    const access = window.platformDevelopmentAccess || {};
    if (event.target.closest?.('#page-development .dev-publish-file') && !access.canPublish) {
      event.preventDefault(); event.stopImmediatePropagation(); notify('当前项目仅有查看权限，不能发布', true); return;
    }
    if (event.target.closest?.('#page-development .dev-save-to-project') && !access.canSaveToProject) {
      event.preventDefault(); event.stopImmediatePropagation(); notify('当前没有保存到项目空间的权限', true); return;
    }
    if (event.target.closest?.('#page-development .dev-run-button') && !access.canRun) {
      event.preventDefault(); event.stopImmediatePropagation(); notify('当前账号没有 SQL 执行权限', true); return;
    }
    if (isMutationTarget(event.target) && !access.canEdit) {
      event.preventDefault(); event.stopImmediatePropagation(); notify('当前空间为只读，不能修改', true);
    }
  }, true);

  document.addEventListener('contextmenu', (event) => {
    if (!event.target.closest?.('#page-development .live-folder, #page-development .live-file, #page-development .live-project')) return;
    const access = window.platformDevelopmentAccess || {};
    if (!access.canEdit) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);

  document.addEventListener('dblclick', (event) => {
    if (!event.target.closest?.('#page-development .live-folder, #page-development .live-file')) return;
    const access = window.platformDevelopmentAccess || {};
    if (!access.canEdit) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);

  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement) || form.id !== 'dev-project-save-form') return;
    event.preventDefault();
    event.stopImmediatePropagation();
    saveToProject(form);
  }, true);
}

function inferFileType(name) {
  const lower = String(name || '').trim().toLowerCase();
  if (lower.endsWith('.py')) return 'PYTHON';
  if (lower.endsWith('.sh')) return 'SHELL';
  return 'SQL';
}

function editorValue() {
  if (window.platformMonaco?.getEditor?.()) return window.platformMonaco.getValue();
  return developmentPage()?.querySelector('.codeview')?.textContent || '';
}

function closeSaveToProjectModal() {
  const host = document.getElementById('dev-project-save-modal');
  if (!host) return;
  host.classList.remove('open');
  host.setAttribute('aria-hidden', 'true');
}

function focusSavedProjectFile(file) {
  const all = developmentPage()?.querySelector('.dev-segment[data-dev-scope="all"]');
  if (all) all.click();
  let attempts = 0;
  const timer = window.setInterval(() => {
    attempts += 1;
    const row = developmentPage()?.querySelector('.live-file[data-id="' + file.id + '"]');
    if (row) {
      window.clearInterval(timer);
      row.click();
      return;
    }
    if (attempts >= 20) window.clearInterval(timer);
  }, 100);
}

function saveToProject() {
  const access = window.platformDevelopmentAccess || {};
  if (!state.moduleEdit || scope() !== 'mine' || !access.canSaveToProject) {
    notify('当前没有保存到项目空间的权限', true);
    return;
  }
  const host = document.getElementById('dev-project-save-modal');
  const folderSelect = document.getElementById('dev-project-save-folder');
  const nameInput = document.getElementById('dev-project-save-name');
  const descriptionInput = document.getElementById('dev-project-save-description');
  const error = document.getElementById('dev-project-save-error');
  const projectId = Number(host?.dataset.targetProjectId || 0);
  const folderValue = String(folderSelect?.value || '');
  const name = String(nameInput?.value || '').trim();
  if (!projectId) { if (error) error.textContent = '暂无项目空间，请先创建一个项目'; return; }
  if (!folderValue) { if (error) error.textContent = '请选择目标目录'; return; }
  if (!name) { if (error) error.textContent = '请输入文件名称'; return; }

  const folderId = folderValue === '__root__' ? null : Number(folderValue);
  const submit = document.querySelector('.dev-project-save-submit');
  if (submit) { submit.disabled = true; submit.textContent = '保存中…'; }
  api('/development/files/save-to-project', {
    method: 'POST',
    body: {
      projectId,
      folderId,
      name,
      fileType: inferFileType(name),
      content: editorValue(),
      description: String(descriptionInput?.value || '').trim()
    }
  }).then((saved) => {
    closeSaveToProjectModal();
    state.projectAccess.delete(String(saved.projectId));
    notify('已保存到项目空间，后续同名保存将追加版本');
    focusSavedProjectFile(saved);
  }).catch((requestError) => {
    if (error) error.textContent = requestError.message || '保存失败';
  }).finally(() => {
    if (submit) { submit.disabled = false; submit.textContent = '保存到项目'; }
  });
}

function installObserver() {
  const page = developmentPage();
  if (!page) return;
  let queued = false;
  const observer = new MutationObserver(() => {
    if (queued) return;
    queued = true;
    window.requestAnimationFrame(() => {
      queued = false;
      sync();
    });
  });
  observer.observe(page, { subtree: true, childList: true, attributes: true, attributeFilter: ['class', 'hidden'] });
}

function boot() {
  installStyles();
  installGuards();
  installObserver();
  loadIdentity().finally(sync);
  window.addEventListener('platform-monaco-ready', sync);
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
else boot();
