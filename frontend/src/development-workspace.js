const FAVORITES_NAME = '我的收藏';
const FAVORITES_DESCRIPTION = '个人工作区';

let favoriteWorkspaceId = null;
let refreshTimer = null;
let loadingFavorites = false;

function token() {
  return window.localStorage.getItem('platform_access_token') || '';
}

function request(path, options = {}) {
  const headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
  const accessToken = token();
  if (accessToken) headers.Authorization = 'Bearer ' + accessToken;
  if (options.body) headers['Content-Type'] = 'application/json';
  return window.fetch('/api' + path, Object.assign({}, options, { headers })).then(async (response) => {
    let payload = {};
    try { payload = await response.json(); } catch (_) { payload = {}; }
    if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
    return payload.data;
  });
}

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>'"]/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  })[char]);
}

function developmentPage() {
  return document.getElementById('page-development');
}

function activeScopeButton() {
  return developmentPage()?.querySelector('.dev-segment.on') || null;
}

function isFavoritesActive() {
  const button = activeScopeButton();
  if (!button) return false;
  return button.dataset.devScope === 'favorites' || button.textContent.trim().indexOf('收藏') >= 0;
}

function sideList() {
  return developmentPage()?.querySelector('.dev-left .side-list') || null;
}

function syncFavoriteControls() {
  if (!isFavoritesActive()) return;
  const page = developmentPage();
  if (!page) return;
  const createFolder = page.querySelector('.dev-create-project');
  const createFile = page.querySelector('.dev-create-file');
  if (createFolder) {
    createFolder.hidden = false;
    createFolder.title = '新建收藏文件夹';
  }
  if (createFile) {
    createFile.hidden = false;
    createFile.title = '新建收藏 SQL';
  }
}

function renderTree(projectId, folders, files) {
  const target = sideList()?.querySelector('[data-favorite-files="' + projectId + '"]');
  if (!target) return;
  const byParent = new Map();
  (folders || []).forEach((folder) => {
    const key = folder.parentId == null ? 'root' : String(folder.parentId);
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key).push(folder);
  });
  const filesByFolder = new Map();
  (files || []).forEach((file) => {
    const key = file.folderId == null ? 'root' : String(file.folderId);
    if (!filesByFolder.has(key)) filesByFolder.set(key, []);
    filesByFolder.get(key).push(file);
  });

  const fileRow = (file) => '<button class="side-row dev-file-row live-file favorite-file" data-id="' + file.id +
    '" data-project-id="' + projectId + '" data-folder-id="' + (file.folderId == null ? '' : file.folderId) +
    '" title="打开收藏文件"><i class="ri-file-code-line ico-blue"></i><span class="dev-resource-name">' +
    escapeHtml(file.name || '未命名.sql') + '</span><span class="favorite-row-actions"><span class="favorite-file-delete" data-favorite-file-delete="' +
    file.id + '" title="从收藏工作区删除">×</span></span></button>';

  const branch = (parentId) => {
    const key = parentId == null ? 'root' : String(parentId);
    const orderedFolders = (byParent.get(key) || []).slice().sort((a, b) => String(a.createdAt || a.id).localeCompare(String(b.createdAt || b.id)));
    let html = orderedFolders.map((folder) => '<div class="dev-folder-group favorite-folder-group"><button class="side-row dev-folder-row live-folder open favorite-folder" data-id="' +
      folder.id + '" data-project-id="' + projectId + '" data-parent-id="' + (folder.parentId == null ? '' : folder.parentId) +
      '"><span class="folder-toggle">›</span><i class="ri-folder-fill ico-blue"></i><span class="dev-resource-name">' + escapeHtml(folder.name) +
      '</span><span class="folder-actions"><span class="folder-add favorite-folder-add" data-favorite-folder-add="' + folder.id + '" title="新建 SQL">＋</span><span class="favorite-folder-delete" data-favorite-folder-delete="' + folder.id + '" title="删除空文件夹">×</span></span></button><div class="dev-tree-children" data-folder-children="' + folder.id + '">' + branch(folder.id) + '</div></div>').join('');
    html += (filesByFolder.get(key) || []).map(fileRow).join('');
    return html;
  };

  const content = branch(null);
  target.innerHTML = content || '<div class="dev-empty">暂无收藏内容，请点击左上角“+”创建文件夹或 SQL</div>';
}

async function findFavoriteWorkspace() {
  const projects = await request('/development/projects?scope=favorites');
  const workspace = (Array.isArray(projects) ? projects : []).find((project) => String(project.name || '').trim() === FAVORITES_NAME) || null;
  favoriteWorkspaceId = workspace ? Number(workspace.id) : null;
  return workspace;
}

async function ensureFavoriteWorkspace() {
  const current = await findFavoriteWorkspace();
  if (current) return current;
  const created = await request('/development/projects', {
    method: 'POST',
    body: JSON.stringify({ name: FAVORITES_NAME, description: FAVORITES_DESCRIPTION })
  });
  favoriteWorkspaceId = Number(created.id);
  return created;
}

async function refreshFavorites(preferredFileId) {
  if (!isFavoritesActive() || loadingFavorites) return;
  const list = sideList();
  if (!list) return;
  loadingFavorites = true;
  try {
    const workspace = await findFavoriteWorkspace();
    if (!isFavoritesActive()) return;
    syncFavoriteControls();
    if (!workspace) {
      list.innerHTML = '<div class="dev-scope-note"><strong>我的收藏</strong>个人持久化收藏工作区</div><div data-favorite-workspace-ready="true"><div class="dev-empty">暂无收藏内容，请点击左上角“+”创建文件夹或 SQL</div></div>';
      return;
    }
    const projectId = Number(workspace.id);
    const [files, folders] = await Promise.all([
      request('/development/files?projectId=' + encodeURIComponent(projectId)),
      request('/development/folders?projectId=' + encodeURIComponent(projectId))
    ]);
    if (!isFavoritesActive()) return;
    list.innerHTML = '<div class="dev-scope-note"><strong>我的收藏</strong>保存在平台数据库中，重启或更换应用机器后仍可恢复</div><div data-favorite-workspace-ready="true" data-favorite-project-id="' + projectId + '"><div class="dev-files" data-project-files="' + projectId + '" data-favorite-files="' + projectId + '"></div></div>';
    renderTree(projectId, Array.isArray(folders) ? folders : [], Array.isArray(files) ? files : []);
    syncFavoriteControls();
    if (preferredFileId != null) {
      window.setTimeout(() => {
        const row = sideList()?.querySelector('.live-file[data-id="' + preferredFileId + '"]');
        if (row) row.click();
      }, 0);
    }
  } catch (error) {
    if (isFavoritesActive()) {
      list.innerHTML = '<div class="dev-scope-note"><strong>我的收藏</strong>个人持久化收藏工作区</div><div class="dev-empty">' + escapeHtml(error.message || '收藏加载失败') + '</div>';
    }
  } finally {
    loadingFavorites = false;
  }
}

function scheduleRefresh(delay = 30) {
  window.clearTimeout(refreshTimer);
  refreshTimer = window.setTimeout(() => refreshFavorites(), delay);
}

async function createFavoriteFolder(parentId) {
  const workspace = await ensureFavoriteWorkspace();
  const name = window.prompt('文件夹名称', '新建文件夹');
  if (!name || !name.trim()) return;
  await request('/development/folders', {
    method: 'POST',
    body: JSON.stringify({ projectId: Number(workspace.id), parentId: parentId == null ? null : Number(parentId), name: name.trim() })
  });
  await refreshFavorites();
}

async function createFavoriteFile(folderId) {
  const workspace = await ensureFavoriteWorkspace();
  const projectId = Number(workspace.id);
  const files = await request('/development/files?projectId=' + encodeURIComponent(projectId));
  const used = new Set((files || []).map((file) => String(file.name || '').toLowerCase()));
  let index = 0;
  let name = '未命名.sql';
  while (used.has(name.toLowerCase())) {
    index += 1;
    name = '未命名' + index + '.sql';
  }
  const created = await request('/development/files', {
    method: 'POST',
    body: JSON.stringify({ projectId, folderId: folderId == null ? null : Number(folderId), name, fileType: 'SQL', content: '', description: '' })
  });
  await refreshFavorites(created.id);
}

async function deleteFavoriteFile(id) {
  if (!window.confirm('确定删除这个收藏文件吗？')) return;
  await request('/development/files/' + encodeURIComponent(id), { method: 'DELETE' });
  await refreshFavorites();
}

async function deleteFavoriteFolder(id) {
  if (!window.confirm('确定删除这个收藏文件夹吗？文件夹必须为空。')) return;
  await request('/development/folders/' + encodeURIComponent(id), { method: 'DELETE' });
  await refreshFavorites();
}

async function renameFavoriteFolder(id, currentName) {
  const name = window.prompt('文件夹名称', currentName || '');
  if (!name || !name.trim() || name.trim() === currentName) return;
  await request('/development/folders/' + encodeURIComponent(id), {
    method: 'PUT',
    body: JSON.stringify({ name: name.trim() })
  });
  await refreshFavorites();
}

function installStyles() {
  const style = document.createElement('style');
  style.id = 'development-workspace-fixes';
  style.textContent = `
    #page-development .dev-project-root { display: none !important; }
    #page-development [data-favorite-workspace-ready] { min-width: 0; }
    #page-development .favorite-row-actions { margin-left: auto; display: inline-flex; align-items: center; }
    #page-development .favorite-file-delete,
    #page-development .favorite-folder-delete { width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; border-radius: 5px; color: #8b98aa; font-size: 16px; }
    #page-development .favorite-file-delete:hover,
    #page-development .favorite-folder-delete:hover { background: #fff0f0; color: #e5484d; }
  `;
  document.head.appendChild(style);
}

function installInteractions() {
  document.addEventListener('click', (event) => {
    const segment = event.target.closest('#page-development .dev-segment');
    if (segment) {
      const favorites = segment.dataset.devScope === 'favorites' || segment.textContent.trim().indexOf('收藏') >= 0;
      if (favorites) window.setTimeout(() => scheduleRefresh(20), 0);
      return;
    }
    if (!isFavoritesActive()) return;

    const createFolder = event.target.closest('#page-development .dev-create-project');
    const createFile = event.target.closest('#page-development .dev-create-file');
    const folderAdd = event.target.closest('[data-favorite-folder-add], #page-development .folder-add');
    const fileDelete = event.target.closest('[data-favorite-file-delete]');
    const folderDelete = event.target.closest('[data-favorite-folder-delete]');

    if (createFolder || createFile || folderAdd || fileDelete || folderDelete) {
      event.preventDefault();
      event.stopImmediatePropagation();
      const action = createFolder ? createFavoriteFolder(null)
        : createFile ? createFavoriteFile(null)
        : fileDelete ? deleteFavoriteFile(fileDelete.dataset.favoriteFileDelete)
        : folderDelete ? deleteFavoriteFolder(folderDelete.dataset.favoriteFolderDelete)
        : createFavoriteFile(folderAdd.dataset.favoriteFolderAdd || folderAdd.dataset.folderAdd);
      Promise.resolve(action).catch((error) => window.alert(error.message || '操作失败'));
    }
  }, true);

  document.addEventListener('dblclick', (event) => {
    if (!isFavoritesActive()) return;
    const folder = event.target.closest('#page-development .favorite-folder');
    if (!folder) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    const label = folder.querySelector('.dev-resource-name');
    renameFavoriteFolder(folder.dataset.id, label ? label.textContent.trim() : '').catch((error) => window.alert(error.message || '重命名失败'));
  }, true);

  document.addEventListener('contextmenu', (event) => {
    if (!isFavoritesActive()) return;
    const marker = event.target.closest('#page-development [data-favorite-workspace-ready]');
    if (marker) event.preventDefault();
  }, true);

  const page = developmentPage();
  if (page) {
    const observer = new MutationObserver(() => {
      if (!isFavoritesActive()) return;
      syncFavoriteControls();
      const list = sideList();
      if (list && !list.querySelector('[data-favorite-workspace-ready]')) scheduleRefresh(20);
    });
    observer.observe(page, { subtree: true, childList: true, attributes: true, attributeFilter: ['hidden', 'class'] });
  }
}

function boot() {
  if (document.getElementById('development-workspace-fixes')) return;
  installStyles();
  installInteractions();
  if (isFavoritesActive()) scheduleRefresh(20);
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
else boot();
