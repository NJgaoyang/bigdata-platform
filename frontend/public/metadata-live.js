(function () {
  'use strict';

  var state = { sources: [], source: null, databases: [], database: '', tables: [], table: null, columns: [], lineage: [], token: 0 };
  var params = new URLSearchParams(window.location.search);
  var preferredSourceId = params.get('dataSourceId') || window.localStorage.getItem('metadataDataSourceId') || '';
  var $ = function (selector) { return document.querySelector(selector); };
  var $$ = function (selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); };
  var esc = function (value) { return String(value == null ? '' : value).replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); };
  var display = function (value, fallback) { return value == null || value === '' ? (fallback == null ? '—' : fallback) : String(value); };

  function api(path, options) {
    options = options || {};
    var headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    var token = window.localStorage.getItem('platform_access_token');
    if (token) headers.Authorization = 'Bearer ' + token;
    return fetch('/api' + path, Object.assign({}, options, { headers: headers })).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (payload) {
        if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
        return payload.data;
      });
    });
  }

  function notify(message, error) {
    var toast = $('#toast');
    if (!toast) return;
    toast.textContent = message || '';
    toast.classList.toggle('error', !!error);
    toast.classList.add('on');
    clearTimeout(window.__metadataToastTimer);
    window.__metadataToastTimer = setTimeout(function () { toast.classList.remove('on'); }, 2400);
  }

  function formatTime(value) {
    if (!value) return '—';
    return String(value).replace('T', ' ').replace(/\.\d{1,9}(?=Z?$)/, '').replace(/Z$/, '').slice(0, 19);
  }

  function sourceType() { return String(state.source && state.source.type || '').toUpperCase(); }
  function qualifiedTable() { return state.table ? (state.database ? state.database + '.' + state.table.name : state.table.name) : ''; }
  function tableMatches(left, right) {
    var a = String(left || '').replace(/`/g, '').trim().toLowerCase();
    var b = String(right || '').replace(/`/g, '').trim().toLowerCase();
    return !!a && !!b && (a === b || a.endsWith('.' + b) || b.endsWith('.' + a));
  }

  function renderSourceState() {
    var host = $('#sourceState');
    if (!host) return;
    var raw = String(state.source && state.source.status || '').toUpperCase();
    var ok = ['UP', 'ACTIVE', 'SUCCESS', 'CONNECTED', 'ONLINE', 'AVAILABLE'].indexOf(raw) >= 0;
    var bad = ['DOWN', 'FAILED', 'ERROR', 'OFFLINE', 'UNAVAILABLE'].indexOf(raw) >= 0;
    host.className = 'source-state' + (ok ? ' ok' : bad ? ' bad' : '');
    host.innerHTML = '<i class="ri-checkbox-blank-circle-fill"></i><span>' + esc(raw || '未检测') + '</span>';
  }

  function renderSourcePicker() {
    var select = $('#metadataSourceSelect');
    if (!select) return;
    if (!state.sources.length) {
      select.innerHTML = '<option value="">暂无可展示元数据的数据源</option>';
      select.disabled = true;
      return;
    }
    select.disabled = false;
    select.innerHTML = state.sources.map(function (source) {
      return '<option value="' + esc(source.id) + '"' + (state.source && String(source.id) === String(state.source.id) ? ' selected' : '') + '>' + esc(display(source.name, '未命名数据源')) + ' · ' + esc(display(source.type, '')) + '</option>';
    }).join('');
  }

  function renderBreadcrumb() {
    $('#crumbSource').textContent = state.source ? display(state.source.name, '数据源') : '数据源';
    $('#crumbObject').textContent = state.table ? qualifiedTable() : state.database || '浏览';
  }

  function renderDatabases() {
    var host = $('#dbList');
    var count = $('#databaseCount');
    if (!host) return;
    if (count) count.textContent = String(state.databases.length);
    var query = String($('#dbSearch') && $('#dbSearch').value || '').trim().toLowerCase();
    var items = state.databases.filter(function (item) { return !query || String(item.name || '').toLowerCase().indexOf(query) >= 0; });
    host.innerHTML = items.length ? items.map(function (item) {
      var active = item.name === state.database;
      return '<div class="list-row' + (active ? ' on' : '') + '" data-db="' + esc(item.name) + '"><span class="db-icon"><i class="ri-database-2-line"></i></span><span class="row-main"><span class="row-title">' + esc(item.name) + '</span>' + (item.comment ? '<span class="row-sub">' + esc(item.comment) + '</span>' : '') + '</span></div>';
    }).join('') : '<div class="empty"><i class="ri-database-line"></i>' + (state.databases.length ? '没有匹配的数据库' : '暂无数据库') + '</div>';
    $$('#dbList [data-db]').forEach(function (node) { node.onclick = function () { selectDatabase(node.dataset.db); }; });
  }

  function renderTables() {
    var host = $('#tableList');
    var count = $('#tableCount');
    var title = $('#tablePaneTitle');
    if (title) title.textContent = state.database ? state.database + ' · 数据表' : '数据表';
    if (count) count.textContent = String(state.tables.length);
    if (!host) return;
    if (!state.database) {
      host.innerHTML = '<div class="empty"><i class="ri-table-line"></i>请先选择数据库</div>';
      return;
    }
    var query = String($('#tableSearch') && $('#tableSearch').value || '').trim().toLowerCase();
    var items = state.tables.filter(function (item) {
      return !query || String(item.name || '').toLowerCase().indexOf(query) >= 0 || String(item.comment || '').toLowerCase().indexOf(query) >= 0;
    });
    host.innerHTML = items.length ? items.map(function (item) {
      var active = state.table && item.name === state.table.name;
      return '<div class="list-row' + (active ? ' on' : '') + '" data-table="' + esc(item.name) + '"><span class="table-icon"><i class="ri-table-2"></i></span><span class="row-main"><span class="row-title">' + esc(item.name) + '</span><span class="row-sub">' + esc(item.comment || state.database) + '</span></span><span class="row-type">' + esc(display(item.type, 'TABLE')) + '</span></div>';
    }).join('') : '<div class="empty"><i class="ri-table-line"></i>' + (state.tables.length ? '没有匹配的数据表' : '当前数据库暂无数据表') + '</div>';
    $$('#tableList [data-table]').forEach(function (node) { node.onclick = function () { selectTable(node.dataset.table); }; });
  }

  function renderDetailHeader() {
    $('#detailTitle').textContent = state.table ? state.table.name : '请选择数据表';
    $('#detailPath').textContent = state.table ? display(state.source && state.source.name) + ' / ' + state.database + ' / ' + state.table.name : '从左侧选择数据库和数据表查看真实元数据';
    $('#detailType').textContent = state.table ? display(state.table.type, 'TABLE') : '—';
    $('#detailFieldCount').textContent = state.table ? '字段 ' + state.columns.length : '字段 —';
    renderBreadcrumb();
  }

  function renderOverview() {
    var host = $('#pane-overview');
    if (!host) return;
    if (!state.table) {
      host.innerHTML = '<div class="empty"><i class="ri-database-2-line"></i>请选择一张数据表</div>';
      return;
    }
    var source = state.source || {};
    var address = [display(source.host, ''), display(source.port, '')].filter(Boolean).join(':') || '—';
    var comment = state.table.comment || '当前数据表没有维护说明。';
    var checkMessage = source.lastCheckMessage || '暂无连接检测信息';
    host.innerHTML = '<div class="overview"><div class="facts">' +
      fact('数据源', display(source.name)) + fact('数据源类型', display(source.type)) + fact('数据库', state.database) +
      fact('对象类型', display(state.table.type, 'TABLE')) + fact('字段数量', state.columns.length) + fact('地址', address) +
      '</div><div class="section"><div class="section-title">表说明</div><div class="section-body">' + esc(comment) + '</div></div>' +
      '<div class="section"><div class="section-title">数据源信息</div><div class="source-grid">' +
      '<span>用户名</span><b>' + esc(display(source.username)) + '</b><span>元数据展示</span><b>' + (source.metadataVisible === false ? '关闭' : '开启') + '</b>' +
      '<span>连接状态</span><b>' + esc(display(source.status, '未检测')) + '</b><span>最近检测</span><b>' + esc(formatTime(source.lastCheckedAt)) + '</b>' +
      '<span>检测信息</span><b title="' + esc(checkMessage) + '">' + esc(checkMessage) + '</b><span>数据库</span><b>' + esc(state.database) + '</b>' +
      '</div></div></div>';
  }

  function fact(label, value) {
    return '<div class="fact"><label>' + esc(label) + '</label><strong title="' + esc(display(value)) + '">' + esc(display(value)) + '</strong></div>';
  }

  function renderFields() {
    var host = $('#pane-fields');
    if (!host) return;
    if (!state.table) { host.innerHTML = '<div class="empty">请选择一张数据表</div>'; return; }
    if (!state.columns.length) { host.innerHTML = '<div class="empty"><i class="ri-layout-column-line"></i>暂无字段元数据</div>'; return; }
    host.innerHTML = '<table class="data-table"><thead><tr><th style="width:28%">字段名</th><th style="width:22%">数据类型</th><th style="width:16%">可为空</th><th>字段说明</th></tr></thead><tbody>' + state.columns.map(function (column) {
      return '<tr><td><span class="field-name">' + esc(column.name) + '</span></td><td>' + esc(display(column.dataType)) + '</td><td><span class="nullable' + (column.nullable ? '' : ' no') + '">' + (column.nullable ? 'YES' : 'NO') + '</span></td><td title="' + esc(column.comment || '') + '">' + esc(column.comment || '—') + '</td></tr>';
    }).join('') + '</tbody></table>';
  }

  function renderLineage() {
    var host = $('#pane-lineage');
    if (!host) return;
    if (!state.table) { host.innerHTML = '<div class="empty">请选择一张数据表</div>'; return; }
    if (!state.lineage.length) { host.innerHTML = '<div class="empty"><i class="ri-node-tree"></i>暂无已解析的真实 SQL 血缘</div>'; return; }
    var current = qualifiedTable();
    host.innerHTML = '<div class="lineage-wrap"><div class="lineage-tip">当前表：<b>' + esc(current) + '</b>。以下关系来自平台已持久化的 SQL 血缘，不做推测补全。</div><table class="data-table"><thead><tr><th style="width:110px">方向</th><th>上游表</th><th>下游表</th><th style="width:90px">关系</th><th style="width:150px">来源版本</th></tr></thead><tbody>' + state.lineage.map(function (item) {
      var direction = tableMatches(item.targetTable, current) ? '上游 → 当前' : tableMatches(item.sourceTable, current) ? '当前 → 下游' : '关联';
      var version = item.fileId == null ? '—' : '文件 #' + item.fileId + (item.fileVersionId == null ? '' : ' / 版本 #' + item.fileVersionId);
      return '<tr><td><span class="direction">' + esc(direction) + '</span></td><td title="' + esc(item.sourceTable || '') + '">' + esc(display(item.sourceTable)) + '</td><td title="' + esc(item.targetTable || '') + '">' + esc(display(item.targetTable)) + '</td><td>' + esc(display(item.relationType, 'SQL')) + '</td><td>' + esc(version) + '</td></tr>';
    }).join('') + '</tbody></table></div>';
  }

  function renderDetail() {
    renderDetailHeader();
    renderOverview();
    renderFields();
    renderLineage();
  }

  function setLoading(node, loading) { if (node) node.classList.toggle('loading', !!loading); }

  function loadDatabases() {
    var token = ++state.token;
    state.databases = []; state.database = ''; state.tables = []; state.table = null; state.columns = []; state.lineage = [];
    renderDatabases(); renderTables(); renderDetail();
    setLoading($('#dbList'), true);
    return api('/metadata/databases?dataSourceId=' + encodeURIComponent(state.source.id) + '&type=' + encodeURIComponent(sourceType())).then(function (items) {
      if (token !== state.token) return;
      state.databases = Array.isArray(items) ? items : [];
      renderDatabases();
      if (state.databases.length) return selectDatabase(state.databases[0].name, token);
    }).finally(function () { if (token === state.token) setLoading($('#dbList'), false); });
  }

  function selectDatabase(database, parentToken) {
    state.database = database; state.tables = []; state.table = null; state.columns = []; state.lineage = [];
    renderDatabases(); renderTables(); renderDetail();
    var token = parentToken || ++state.token;
    setLoading($('#tableList'), true);
    return api('/metadata/tables?dataSourceId=' + encodeURIComponent(state.source.id) + '&database=' + encodeURIComponent(database)).then(function (items) {
      if (token !== state.token || state.database !== database) return;
      state.tables = Array.isArray(items) ? items : [];
      renderTables();
      if (state.tables.length) return selectTable(state.tables[0].name, token);
    }).catch(function (error) {
      if (token === state.token) notify(error.message, true);
    }).finally(function () { if (token === state.token) setLoading($('#tableList'), false); });
  }

  function selectTable(tableName, parentToken) {
    var table = state.tables.find(function (item) { return item.name === tableName; });
    if (!table) return;
    state.table = table; state.columns = []; state.lineage = [];
    renderTables(); renderDetail();
    var token = parentToken || ++state.token;
    var base = 'dataSourceId=' + encodeURIComponent(state.source.id) + '&database=' + encodeURIComponent(state.database) + '&table=' + encodeURIComponent(table.name);
    var columns = api('/metadata/columns?' + base);
    var lineage = api('/lineage/table?name=' + encodeURIComponent(qualifiedTable()));
    return Promise.allSettled([columns, lineage]).then(function (results) {
      if (token !== state.token || !state.table || state.table.name !== table.name) return;
      if (results[0].status === 'fulfilled') state.columns = Array.isArray(results[0].value) ? results[0].value : [];
      else notify(results[0].reason.message || '字段读取失败', true);
      if (results[1].status === 'fulfilled') state.lineage = Array.isArray(results[1].value) ? results[1].value : [];
      else notify(results[1].reason.message || '血缘读取失败', true);
      renderDetail();
    });
  }

  function selectSource(sourceId) {
    var source = state.sources.find(function (item) { return String(item.id) === String(sourceId); });
    if (!source) return;
    state.source = source;
    preferredSourceId = String(source.id);
    window.localStorage.setItem('metadataDataSourceId', preferredSourceId);
    window.history.replaceState(null, '', '/metadata.html?dataSourceId=' + encodeURIComponent(preferredSourceId));
    renderSourcePicker(); renderSourceState(); renderBreadcrumb();
    loadDatabases().catch(function (error) { notify(error.message, true); });
  }

  function refreshSource() {
    if (!state.source) return Promise.resolve();
    return api('/data-sources/' + encodeURIComponent(state.source.id)).then(function (source) {
      var index = state.sources.findIndex(function (item) { return String(item.id) === String(source.id); });
      if (index >= 0) state.sources[index] = source;
      state.source = source;
      renderSourcePicker(); renderSourceState(); renderDetail();
    });
  }

  function bind() {
    $('#metadataSourceSelect').onchange = function () { selectSource(this.value); };
    $('#metadataRefresh').onclick = function () {
      if (!state.source) return notify('暂无可刷新数据源', true);
      notify('正在刷新元数据…');
      loadDatabases().then(function () { notify('元数据已刷新'); }).catch(function (error) { notify(error.message, true); });
    };
    $('#testConnection').onclick = function () {
      if (!state.source) return notify('请先选择数据源', true);
      var button = this; button.disabled = true;
      api('/data-sources/' + encodeURIComponent(state.source.id) + '/test').then(function (result) {
        notify(result && result.message || '连接检测完成');
        return refreshSource();
      }).catch(function (error) { notify(error.message, true); }).finally(function () { button.disabled = false; });
    };
    $('#dbSearch').oninput = renderDatabases;
    $('#tableSearch').oninput = renderTables;
    $$('.tab').forEach(function (tab) { tab.onclick = function () {
      $$('.tab').forEach(function (item) { item.classList.toggle('on', item === tab); });
      $$('.tab-pane').forEach(function (pane) { pane.classList.toggle('on', pane.id === 'pane-' + tab.dataset.tab); });
    }; });
    $$('.nav button').forEach(function (button) { button.onclick = function () {
      var page = button.dataset.page || 'workbench';
      if (page === 'source') return;
      window.location.href = '/index.html?route=' + encodeURIComponent(page) + '#' + page;
    }; });
  }

  function loadIdentity() {
    return api('/auth/me').then(function (identity) {
      var name = identity && identity.authenticated ? display(identity.username, '用户') : '未登录';
      var roleCode = String(identity && (identity.roleCode || identity.role) || '').toUpperCase();
      $('.avatar').textContent = name.slice(0, 1).toUpperCase();
      $('.pname').textContent = name;
      $('.prole').textContent = identity && identity.authenticated ? display(identity.role, '用户') : '未启用认证';
      var settings = $('.nav button[data-page="settings"]');
      if (settings) settings.style.display = identity && identity.authenticated && (identity.superAdmin || roleCode === 'ADMIN' || roleCode === 'SUPER_ADMIN') ? '' : 'none';
    }).catch(function () {});
  }

  function start() {
    bind(); loadIdentity();
    api('/data-sources').then(function (sources) {
      var all = Array.isArray(sources) ? sources : [];
      state.sources = all.filter(function (item) {
        var type = String(item.type || '').toUpperCase();
        return item.metadataVisible !== false && (type === 'MYSQL' || type === 'STARROCKS');
      });
      if (!state.sources.length) throw new Error('暂无已开启元数据展示的 MySQL / StarRocks 数据源');
      state.source = state.sources.find(function (item) { return String(item.id) === String(preferredSourceId); }) || state.sources[0];
      renderSourcePicker(); renderSourceState(); renderBreadcrumb();
      return loadDatabases();
    }).catch(function (error) {
      state.source = null; state.sources = []; state.databases = [];
      renderSourcePicker(); renderSourceState(); renderDatabases(); renderTables(); renderDetail();
      notify(error.message, true);
    });
  }

  start();
})();
