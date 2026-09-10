(function () {
  'use strict';
  var modules = [
    ['WORKBENCH', '工作台'], ['METADATA', '元数据'], ['DATA_INTEGRATION', '数据集成'], ['DATA_DEVELOPMENT', '数据开发'],
    ['WORKFLOW', '工作流'], ['OPERATIONS', '调度运维'], ['DATA_ASSETS', '数据资产'], ['SYSTEM_SETTINGS', '系统设置']
  ];
  var state = { users: [], permissions: new Set() };
  var $ = function (selector) { return document.querySelector(selector); };
  function api(path, options) {
    options = options || {};
    options.headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    var token = localStorage.getItem('platform_access_token'); if (token) options.headers.Authorization = 'Bearer ' + token;
    if (options.body && typeof options.body !== 'string') { options.headers['Content-Type'] = 'application/json'; options.body = JSON.stringify(options.body); }
    return fetch('/api' + path, options).then(function (response) { return response.json().catch(function () { return {}; }).then(function (payload) { if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败'); return payload.data; }); });
  }
  function message(value, error) {
    var toast = $('#toast'); if (!toast) return;
    toast.querySelector('i').className = error ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill'; toast.querySelector('span').textContent = value;
    toast.classList.add('show'); clearTimeout(message.timer); message.timer = setTimeout(function () { toast.classList.remove('show'); }, 2600);
  }
  function canManage() { return !!(window.platformAuth && window.platformAuth.isAdmin); }
  function render() {
    var body = $('#settingsPermissionsBody'); if (!body) return;
    body.innerHTML = modules.map(function (item) {
      var view = item[0] + '_VIEW', edit = item[0] + '_EDIT';
      var project = item[0] === 'DATA_DEVELOPMENT'
        ? '<label title="可管理项目空间内所有项目及其脚本"><input type="checkbox" data-module-permission="DATA_DEVELOPMENT_PROJECT_ALL"' + (state.permissions.has('DATA_DEVELOPMENT_PROJECT_ALL') ? ' checked' : '') + '>项目权限</label>' : '';
      return '<tr><td>' + item[1] + '</td><td><label><input type="checkbox" data-module-permission="' + view + '"' + (state.permissions.has(view) ? ' checked' : '') + '>查看</label><label><input type="checkbox" data-module-permission="' + edit + '"' + (state.permissions.has(edit) ? ' checked' : '') + '>编辑</label>' + project + '</td></tr>';
    }).join('');
  }
  function loadPermissions() {
    var select = $('#settingsPermissionUser'); if (!select || !select.value) { state.permissions = new Set(); render(); return Promise.resolve(); }
    return api('/system/users/' + encodeURIComponent(select.value) + '/permissions').then(function (items) { state.permissions = new Set(items || []); render(); });
  }
  function loadUsers() {
    return api('/system/users').then(function (users) {
      state.users = users || [];
      var select = $('#settingsPermissionUser'); if (!select) return;
      var value = select.value;
      select.innerHTML = state.users.map(function (user) { return '<option value="' + user.id + '">' + user.username + '（' + (user.displayName || user.username) + '）</option>'; }).join('');
      if (state.users.some(function (user) { return String(user.id) === String(value); })) select.value = value;
      return loadPermissions();
    }).catch(function (error) { message(error.message, true); });
  }
  function savePermissions() {
    var select = $('#settingsPermissionUser'); if (!select || !select.value) return;
    var permissions = Array.from(document.querySelectorAll('[data-module-permission]:checked')).map(function (node) { return node.dataset.modulePermission; });
    api('/system/users/' + encodeURIComponent(select.value) + '/permissions', { method: 'PUT', body: { permissions: permissions } }).then(function (items) { state.permissions = new Set(items || []); render(); message('权限已保存'); }).catch(function (error) { message(error.message, true); });
  }
  document.addEventListener('click', function (event) {
    var tab = event.target.closest('[data-settings-tab="permissions"]');
    if (tab && canManage()) loadUsers();
    if (event.target.closest('#settingsPermissionSave') && canManage()) savePermissions();
  });
  document.addEventListener('change', function (event) { if (event.target.matches('#settingsPermissionUser')) loadPermissions(); });
})();
