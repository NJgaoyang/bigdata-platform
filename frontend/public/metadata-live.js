(function () {
  'use strict';

  var state = { source: null, sources: [], databases: [], tables: [], columns: [], database: '', table: '', expandedDatabase: '', tableIndex: {}, tableIndexLoading: false, tableIndexReady: false };
  var hashMatch = window.location.hash.match(/^#metadata(?:\?dataSourceId=([^&]+))?/);
  var id = new URLSearchParams(window.location.search).get('dataSourceId') || (hashMatch && hashMatch[1] ? decodeURIComponent(hashMatch[1]) : '') || window.localStorage.getItem('metadataDataSourceId') || '';
  var $ = function (selector) { return document.querySelector(selector); };
  var $$ = function (selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); };
  var esc = function (value) { return String(value == null ? '' : value).replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); };
  var value = function (item, key, fallback) { return item && item[key] != null && item[key] !== '' ? item[key] : (fallback == null ? '—' : fallback); };
  var formatDateTime = function (item, key) {
    var raw = value(item, key, '');
    if (!raw) return '—';
    return String(raw).replace('T', ' ').replace(/\.\d{1,9}(?=Z?$)/, '').replace(/Z$/, '').slice(0, 19);
  };
  var api = function (path, options) {
    options = options || {};
    var token = window.localStorage.getItem('platform_access_token');
    options.headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    if (token) options.headers.Authorization = 'Bearer ' + token;
    return fetch('/api' + path, options).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (payload) {
        if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
        return payload.data;
      });
    });
  };
  var notify = window.notify = function (message, error) {
    var toast = $('#toast');
    if (!toast) return;
    toast.textContent = message || '';
    toast.style.borderColor = error ? '#efc6c6' : '#dfe6ef';
    toast.style.color = error ? '#c94a4a' : '#37506f';
    toast.classList.add('on');
    clearTimeout(window.__metadataToastTimer);
    window.__metadataToastTimer = setTimeout(function () { toast.classList.remove('on'); }, 2200);
  };
  var empty = function (message, columns) {
    return '<tr><td colspan="' + (columns || 4) + '" class="empty">' + esc(message || '暂无数据') + '</td></tr>';
  };
  var sourceType = function () { return String(value(state.source, 'type', 'STARROCKS')).toUpperCase(); };
  var sourceStatus = function () { return String(value(state.source, 'status', '')).toUpperCase(); };

  function setText(selector, content) { var node = $(selector); if (node) node.textContent = content == null || content === '' ? '—' : String(content); }

  function renderSource() {
    var source = state.source;
    if (!source) return;
    var breadcrumb = $('.breadcrumb');
    if (breadcrumb) breadcrumb.innerHTML = '元数据 / ' + esc(value(source, 'name', '未命名数据源')) + ' / <span>查看元数据</span>';
  }

  function renderSourcePicker() {
    var select = $('#metadataSourceSelect');
    if (!select) return;
    select.innerHTML = state.sources.length ? state.sources.map(function (item) {
      return '<option value="' + esc(item.id) + '"' + (String(item.id) === String(id) ? ' selected' : '') + '>' + esc(value(item, 'name', '未命名数据源')) + '</option>';
    }).join('') : '<option value="">暂无可展示元数据的数据源</option>';
    select.onchange = function () { selectSource(select.value); };
  }

  function databaseTables(database) {
    if (Object.prototype.hasOwnProperty.call(state.tableIndex, database)) return state.tableIndex[database] || [];
    return database === state.database ? state.tables : [];
  }

  function tableMarkup(database, filter) {
    var query = String(filter || '').trim().toLowerCase();
    var sourceTables = databaseTables(database);
    var items = sourceTables.filter(function (item) { return !query || String(item.name || '').toLowerCase().indexOf(query) >= 0 || String(item.comment || '').toLowerCase().indexOf(query) >= 0; });
    var rows = items.length ? items.map(function (item) {
      var comment = String(item.comment || '').trim();
      return '<div class="table-row ' + (item.name === state.table && database === state.database ? 'on' : '') + '" data-db="' + esc(database) + '" data-table="' + esc(item.name) + '"><div class="table-name"><span style="color:#3378e7">▦</span><span>' + esc(item.name) + '</span></div>' + (comment ? '<div class="table-desc">' + esc(comment) + '</div>' : '') + '</div>';
    }).join('') : '<div class="empty">' + (sourceTables.length ? '没有匹配的数据表' : '暂无数据表') + '</div>';
    return '<div class="tree-table-list">' + rows + '</div>';
  }

  function renderDatabases() {
    var filter = ($('#dbSearch') || {}).value || '';
    var query = filter.trim().toLowerCase();
    var items = state.databases.filter(function (item) {
      if (!query) return true;
      var databaseName = String(item.name || '').toLowerCase();
      var tableMatch = databaseTables(item.name).some(function (table) { return String(table.name || '').toLowerCase().indexOf(query) >= 0 || String(table.comment || '').toLowerCase().indexOf(query) >= 0; });
      return databaseName.indexOf(query) >= 0 || tableMatch;
    });
    setText('.workspace > aside .panel-head > span:last-child', state.databases.length);
    $('#dbList').innerHTML = items.length ? items.map(function (item) {
      var databaseName = String(item.name || '').toLowerCase();
      var tableMatch = query && databaseTables(item.name).some(function (table) { return String(table.name || '').toLowerCase().indexOf(query) >= 0 || String(table.comment || '').toLowerCase().indexOf(query) >= 0; });
      var expanded = item.name === state.expandedDatabase || !!tableMatch;
      var nested = expanded ? '<div class="tree-tables">' + tableMarkup(item.name, tableMatch ? query : '') + '</div>' : '';
      return '<div class="db-tree-item"><div class="row db-tree-row ' + (expanded ? 'expanded on' : '') + '" data-db="' + esc(item.name) + '"><span class="chevron">›</span><span class="db-dot"></span><span>' + esc(item.name) + '</span></div>' + nested + '</div>';
    }).join('') : '<div class="empty">' + (state.databases.length ? '没有匹配的数据库' : '暂无数据库元数据') + '</div>';
    $$('#dbList [data-db]').forEach(function (node) { node.onclick = function () { selectDatabase(node.dataset.db); }; });
    $$('#dbList [data-table]').forEach(function (node) { node.onclick = function (event) { event.stopPropagation(); selectTable(node.dataset.table, node.dataset.db); }; });
  }

  function renderTables() {
    setText('#tablesTitle', state.database ? state.database + ' · 数据表' : '数据表');
    setText('#tableCount', state.tables.length);
    renderDatabases();
  }

  function renderColumns() {
    var body = $('#fieldBody');
    if (!body) return;
    body.innerHTML = state.columns.length ? state.columns.map(function (column) {
      return '<tr><td><span class="field-name">' + esc(column.name) + '</span></td><td>' + esc(value(column, 'dataType')) + '</td><td><span class="nullable ' + (column.nullable ? '' : 'no') + '">' + (column.nullable ? 'YES' : 'NO') + '</span></td><td>' + esc(value(column, 'comment', '')) + '</td></tr>';
    }).join('') : empty(state.table ? '暂无字段元数据' : '请选择数据表', 4);
  }

  function renderDetail() {
    var table = state.tables.filter(function (item) { return item.name === state.table; })[0] || null;
    setText('#detailTitle', table ? table.name : '请选择数据表');
    setText('#detailSub', table ? value(table, 'comment', '暂无表说明') + ' · ' + value(table, 'type', 'TABLE') + ' · ' + state.database : '请选择数据库和数据表');
    setText('#ovDb', state.database || '—');
    setText('#ovType', table ? value(table, 'type', 'TABLE') : '—');
    setText('#ovFields', state.table ? state.columns.length : '—');
    setText('#ovRows', '—');
    setText('#ovSize', '—');
    setText('#ovUpdated', '—');
    $('#ovComment').textContent = value(table, 'comment', '暂无表说明');
    var prop = $('#pane-overview .data-table tbody');
    if (prop) prop.innerHTML = '<tr><td>数据源</td><td>' + esc(value(state.source, 'name')) + '</td><td>对象类型</td><td>' + esc(value(table, 'type', '—')) + '</td></tr><tr><td>地址</td><td>' + esc(value(state.source, 'host')) + ':' + esc(value(state.source, 'port')) + '</td><td>元数据可见</td><td>' + (state.source && state.source.metadataVisible === false ? '否' : '是') + '</td></tr><tr><td>最近检测</td><td>' + esc(formatDateTime(state.source, 'lastCheckedAt')) + '</td><td>采集方式</td><td>实时读取</td></tr>';
    renderColumns();
    var preview = $('#pane-preview');
    if (preview) preview.innerHTML = '<div class="preview-toolbar"><span class="preview-meta">后端未提供数据预览接口，仅展示真实元数据</span><span class="spacer"></span><button class="btn" id="previewRefresh">↻ 刷新</button></div><div class="preview-wrap"><div class="empty">暂无数据预览</div></div>';
    var lineage = $('#pane-lineage');
    if (lineage) lineage.innerHTML = '<div class="empty" style="padding-top:120px">暂无血缘数据</div>';
    var refresh = $('#previewRefresh'); if (refresh) refresh.onclick = function () { notify('暂无数据预览'); };
  }

  function selectTab(name) {
    $$('.tab').forEach(function (tab) { tab.classList.toggle('on', tab.dataset.tab === name); });
    $$('.tab-pane').forEach(function (pane) { pane.classList.toggle('on', pane.id === 'pane-' + name); });
  }
  window.selectTab = selectTab;

  function loadTablesForDatabase(database) {
    if (Object.prototype.hasOwnProperty.call(state.tableIndex, database)) return Promise.resolve(state.tableIndex[database]);
    return api('/metadata/tables?dataSourceId=' + encodeURIComponent(id) + '&database=' + encodeURIComponent(database)).then(function (tables) {
      state.tableIndex[database] = Array.isArray(tables) ? tables : [];
      return state.tableIndex[database];
    });
  }

  function preloadTableIndex() {
    if (state.tableIndexReady || state.tableIndexLoading || !state.databases.length) return Promise.resolve();
    state.tableIndexLoading = true;
    return Promise.all(state.databases.map(function (item) { return loadTablesForDatabase(item.name).catch(function () { state.tableIndex[item.name] = []; }); })).then(function () {
      state.tableIndexReady = true;
      state.tableIndexLoading = false;
      renderDatabases();
    });
  }

  function selectDatabase(database) {
    if (state.expandedDatabase === database) {
      state.expandedDatabase = ''; state.database = ''; state.table = ''; state.tables = []; state.columns = [];
      renderDatabases(); renderTables(); renderDetail();
      return;
    }
    state.expandedDatabase = database;
    state.database = database; state.table = ''; state.tables = []; state.columns = [];
    renderDatabases(); renderTables(); renderDetail();
    loadTablesForDatabase(database).then(function (tables) {
      state.tables = tables;
      state.table = state.tables[0] ? state.tables[0].name : '';
      renderTables(); renderDetail();
      if (state.table) selectTable(state.table);
    }).catch(function (error) { notify(error.message, true); renderTables(); renderDetail(); });
  }

  function selectTable(table, database) {
    if (database) { state.database = database; state.expandedDatabase = database; state.tables = databaseTables(database); }
    state.table = table; state.columns = []; renderTables(); renderDetail();
    api('/metadata/columns?dataSourceId=' + encodeURIComponent(id) + '&database=' + encodeURIComponent(state.database) + '&table=' + encodeURIComponent(table)).then(function (columns) { state.columns = Array.isArray(columns) ? columns : []; renderDetail(); }).catch(function (error) { notify(error.message, true); renderDetail(); });
  }

  function selectSource(sourceId) {
    var source = state.sources.filter(function (item) { return String(item.id) === String(sourceId); })[0];
    if (!source) return;
    id = String(source.id);
    window.localStorage.setItem('metadataDataSourceId', id);
    state.source = source;
    state.database = ''; state.table = ''; state.tables = []; state.columns = []; state.expandedDatabase = '';
    renderSourcePicker(); renderSource(); renderDatabases(); renderTables(); renderDetail();
    loadDatabases().catch(function (error) { notify(error.message, true); });
  }

  function loadDatabases() {
    state.databases = []; state.database = ''; state.tables = []; state.table = ''; state.columns = []; state.expandedDatabase = ''; state.tableIndex = {}; state.tableIndexLoading = false; state.tableIndexReady = false;
    renderDatabases(); renderTables(); renderDetail(); renderSource();
    return api('/metadata/databases?dataSourceId=' + encodeURIComponent(id) + '&type=' + encodeURIComponent(sourceType())).then(function (databases) {
      state.databases = Array.isArray(databases) ? databases : [];
      renderDatabases(); renderSource();
      return preloadTableIndex();
    }).catch(function (error) { notify(error.message, true); renderDatabases(); renderSource(); });
  }

  window.refreshMetadata = function () { if (!state.source) return; notify('正在刷新元数据…'); loadDatabases().then(function () { notify('元数据已刷新'); }); };
  window.testConnection = function () { if (!id) return notify('缺少数据源标识', true); api('/data-sources/' + encodeURIComponent(id) + '/test').then(function (result) { notify((result && result.message) || '连接检测完成'); return api('/data-sources/' + encodeURIComponent(id)); }).then(function (source) { state.source = source; renderSource(); }).catch(function (error) { notify(error.message, true); }); };
  window.copyTableName = function () { if (!state.table) return notify('请先选择数据表', true); navigator.clipboard && navigator.clipboard.writeText(state.table).then(function () { notify('表名已复制'); }).catch(function () { notify(state.table); }); };

  function bind() {
    $('#dbSearch').oninput = function () { renderDatabases(); if (String(this.value || '').trim()) preloadTableIndex(); };
    $('#tableSearch').oninput = renderTables;
    var metadataRefresh = $('#metadataRefresh'); if (metadataRefresh) metadataRefresh.onclick = window.refreshMetadata;
    // 数据源管理已归入系统设置；返回时使用统一的 index hash 路由，避免再次打开元数据页。
    var goSource = function () { window.location.href = '/index.html?route=settings#settings'; };
    var backSourceTop = $('#backSourceTop'); if (backSourceTop) backSourceTop.onclick = goSource;
    var copy = $('.detail-actions .btn:nth-child(2)'); if (copy) copy.onclick = window.copyTableName;
    var nav = $$('.nav button'); nav.forEach(function (button) { button.onclick = function () { var page = button.dataset.page || 'workbench'; if (page === 'source') { window.location.href = '/metadata.html'; return; } window.location.href = '/index.html?route=' + encodeURIComponent(page) + '#' + page; }; });
  }

  function loadIdentity() {
    return api('/auth/me').then(function (identity) {
      var name = identity && identity.authenticated ? (identity.username || '用户') : '未登录';
      var roleCode = String((identity && (identity.roleCode || identity.role)) || '').toUpperCase();
      var settings = $('.nav button[data-page="settings"]');
      if (settings) settings.style.display = identity && identity.authenticated && (identity.superAdmin || roleCode === 'ADMIN' || roleCode === 'SUPER_ADMIN') ? '' : 'none';
      var avatar = $('.profile .avatar') || $('.user .avatar'); if (avatar) avatar.textContent = name.slice(0, 1).toUpperCase();
      var userName = $('.profile .pname') || $('.user b'); if (userName) userName.textContent = name;
      var role = $('.profile .prole') || $('.user small'); if (role) role.textContent = identity && identity.authenticated ? (identity.role || '用户') : '未启用认证';
    }).catch(function () { /* identity is optional for metadata browsing */ });
  }

  function start() {
    bind();
    loadIdentity();
    if (!id) { notify('缺少数据源标识', true); document.body.classList.remove('metadata-loading'); return; }
    api('/data-sources').then(function (sources) {
      var list = Array.isArray(sources) ? sources : [];
      state.sources = list.filter(function (item) { return item.metadataVisible !== false; });
      if (!state.sources.length) state.sources = list;
      if (!id || !state.sources.some(function (item) { return String(item.id) === String(id); })) id = state.sources[0] ? String(state.sources[0].id) : '';
      if (!id) throw new Error('暂无可展示元数据的数据源');
      window.localStorage.setItem('metadataDataSourceId', id);
      state.source = state.sources.filter(function (item) { return String(item.id) === String(id); })[0] || null;
      if (!state.source) throw new Error('数据源不存在或已被删除');
      window.history.replaceState(null, '', '/index.html#metadata');
      renderSourcePicker();
      return loadDatabases();
    }).catch(function (error) {
      notify(error.message, true);
      state.source = null; state.sources = []; state.databases = []; state.tables = []; state.columns = []; state.database = ''; state.table = ''; state.expandedDatabase = '';
      var breadcrumb = $('.breadcrumb');
      if (breadcrumb) breadcrumb.innerHTML = '元数据 / <span>查看元数据</span>';
      renderSourcePicker(); renderDatabases(); renderTables(); renderDetail();
    }).then(function () { document.body.classList.remove('metadata-loading'); });
  }
  start();
})();
