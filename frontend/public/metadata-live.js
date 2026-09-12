(function () {
  'use strict';

  var state = {
    sources: [], source: null, databases: [], database: '', table: null,
    tableCache: Object.create(null), tableLoading: Object.create(null), expanded: Object.create(null),
    columns: [], lineage: [], profile: null, selectionSeq: 0, searchSeq: 0
  };
  var params = new URLSearchParams(window.location.search);
  var preferredSourceId = params.get('dataSourceId') || window.localStorage.getItem('metadataDataSourceId') || '';
  var preferredDatabase = window.localStorage.getItem('metadataDatabase') || '';
  var $ = function (selector) { return document.querySelector(selector); };
  var $$ = function (selector) { return Array.prototype.slice.call(document.querySelectorAll(selector)); };
  var esc = function (value) { return String(value == null ? '' : value).replace(/[&<>'"]/g, function (c) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[c]; }); };
  var display = function (value, fallback) { return value == null || value === '' ? (fallback == null ? '—' : fallback) : String(value); };

  function api(path, options) {
    options = options || {};
    var headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    var accessToken = window.localStorage.getItem('platform_access_token');
    if (accessToken) headers.Authorization = 'Bearer ' + accessToken;
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

  function formatNumber(value) {
    if (value == null || value === '') return '—';
    var numeric = Number(value);
    return Number.isFinite(numeric) ? new Intl.NumberFormat('zh-CN').format(numeric) : '—';
  }

  function formatBytes(value) {
    if (value == null || value === '') return '—';
    var bytes = Number(value);
    if (!Number.isFinite(bytes) || bytes < 0) return '—';
    if (bytes === 0) return '0 B';
    var units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
    var index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    var scaled = bytes / Math.pow(1024, index);
    return (index === 0 ? Math.round(scaled) : scaled.toFixed(scaled >= 100 ? 0 : scaled >= 10 ? 1 : 2)) + ' ' + units[index];
  }

  function sourceType() { return String(state.source && state.source.type || '').toUpperCase(); }
  function qualifiedTable() { return state.table ? state.database + '.' + state.table.name : ''; }
  function currentTables(database) { return state.tableCache[database] || []; }
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

  function tableMatchesQuery(table, query) {
    return !query || String(table.name || '').toLowerCase().indexOf(query) >= 0 || String(table.comment || '').toLowerCase().indexOf(query) >= 0;
  }

  function renderCatalog() {
    var host = $('#catalogTree');
    if (!host) return;
    $('#databaseCount').textContent = state.databases.length + ' 个数据库';
    var query = String($('#catalogSearch') && $('#catalogSearch').value || '').trim().toLowerCase();
    var html = [];
    state.databases.forEach(function (database) {
      var tables = currentTables(database.name);
      var dbMatch = !query || String(database.name || '').toLowerCase().indexOf(query) >= 0 || String(database.comment || '').toLowerCase().indexOf(query) >= 0;
      var matchingTables = tables.filter(function (table) { return tableMatchesQuery(table, query); });
      if (query && !dbMatch && !matchingTables.length) return;
      var forcedOpen = !!query && matchingTables.length > 0;
      var expanded = !!state.expanded[database.name] || forcedOpen;
      var selectedDb = state.database === database.name && !state.table;
      html.push('<div class="tree-db' + (expanded ? ' expanded' : '') + '" data-db-node="' + esc(database.name) + '">');
      html.push('<div class="tree-db-row' + (selectedDb ? ' on' : '') + '" data-db="' + esc(database.name) + '"><span class="tree-chevron"><i class="ri-arrow-right-s-line"></i></span><span class="tree-icon"><i class="ri-database-2-line"></i></span><span class="tree-main"><span class="tree-title">' + esc(database.name) + '</span>' + (database.comment ? '<span class="tree-sub">' + esc(database.comment) + '</span>' : '') + '</span></div>');
      html.push('<div class="tree-children">');
      if (expanded) {
        if (state.tableLoading[database.name] && !state.tableCache[database.name]) {
          html.push('<div class="tree-loading">正在加载数据表…</div>');
        } else if (!state.tableCache[database.name]) {
          html.push('<div class="tree-loading">点击数据库加载数据表</div>');
        } else if (!matchingTables.length && query) {
          html.push('<div class="tree-loading">没有匹配的数据表</div>');
        } else if (!tables.length) {
          html.push('<div class="tree-loading">当前数据库暂无数据表</div>');
        } else {
          var visibleTables = query ? (dbMatch ? tables : matchingTables) : tables;
          visibleTables.forEach(function (table) {
            var active = state.table && state.database === database.name && state.table.name === table.name;
            html.push('<div class="tree-table-row' + (active ? ' on' : '') + '" data-table-db="' + esc(database.name) + '" data-table="' + esc(table.name) + '"><span class="tree-icon"><i class="ri-table-2"></i></span><span class="tree-main"><span class="tree-title">' + esc(table.name) + '</span>' + (table.comment ? '<span class="tree-sub">' + esc(table.comment) + '</span>' : '') + '</span></div>');
          });
        }
      }
      html.push('</div></div>');
    });
    host.innerHTML = html.length ? html.join('') : '<div class="empty"><i class="ri-search-line"></i>' + (state.databases.length ? '没有匹配的数据库或数据表' : '暂无数据库') + '</div>';
    $$('#catalogTree [data-db]').forEach(function (node) {
      node.onclick = function () { selectDatabase(node.dataset.db); };
    });
    $$('#catalogTree [data-db] .tree-chevron').forEach(function (toggle) {
      toggle.onclick = function (event) {
        event.stopPropagation();
        var row = toggle.closest('[data-db]');
        var database = row && row.dataset.db;
        if (!database) return;
        state.expanded[database] = !state.expanded[database];
        renderCatalog();
        if (state.expanded[database] && !state.tableCache[database]) loadTables(database).catch(function (error) { notify(error.message, true); });
      };
    });
    $$('#catalogTree [data-table]').forEach(function (node) {
      node.onclick = function (event) { event.stopPropagation(); selectTable(node.dataset.tableDb, node.dataset.table); };
    });
  }

  function setActiveTab(name) {
    $$('.tab').forEach(function (tab) { tab.classList.toggle('on', tab.dataset.tab === name); });
    $$('.tab-pane').forEach(function (pane) { pane.classList.toggle('on', pane.id === 'pane-' + name); });
  }

  function renderEmptyDetail() {
    $('#detailSymbol').innerHTML = '<i class="ri-database-2-line"></i>';
    $('#detailTitle').textContent = '请选择数据库';
    $('#detailPath').textContent = '从左侧元数据目录选择数据库或数据表';
    $('#detailType').textContent = '—';
    $('#detailFieldCount').textContent = '—';
    $('#detailTabs').classList.add('hidden');
    setActiveTab('overview');
    $('#pane-overview').innerHTML = '<div class="empty"><i class="ri-database-2-line"></i>请选择数据库</div>';
    $('#pane-fields').innerHTML = '<div class="empty">请选择数据表</div>';
    $('#pane-lineage').innerHTML = '<div class="empty">请选择数据表</div>';
    renderBreadcrumb();
  }

  function renderDatabaseDetail(filter) {
    if (!state.database || state.table) return;
    var tables = currentTables(state.database);
    var database = state.databases.find(function (item) { return item.name === state.database; }) || { name: state.database, comment: '' };
    $('#detailSymbol').innerHTML = '<i class="ri-database-2-line"></i>';
    $('#detailTitle').textContent = state.database;
    $('#detailPath').textContent = display(state.source && state.source.name) + ' / ' + state.database;
    $('#detailType').textContent = '数据库';
    $('#detailFieldCount').textContent = state.tableCache[state.database] ? tables.length + ' 张表' : '表 —';
    $('#detailTabs').classList.add('hidden');
    setActiveTab('overview');
    var query = String(filter == null ? ($('#dbTableSearch') && $('#dbTableSearch').value || '') : filter).trim().toLowerCase();
    var visible = tables.filter(function (table) { return tableMatchesQuery(table, query); });
    var rows = !state.tableCache[state.database] ? '<tr><td colspan="3" class="empty">正在加载数据表…</td></tr>' : visible.length ? visible.map(function (table) {
      return '<tr class="click-row" data-detail-table="' + esc(table.name) + '"><td><span class="field-name">' + esc(table.name) + '</span></td><td>' + esc(display(table.type, 'TABLE')) + '</td><td title="' + esc(table.comment || '') + '">' + esc(table.comment || '—') + '</td></tr>';
    }).join('') : '<tr><td colspan="3" class="empty">' + (tables.length ? '没有匹配的数据表' : '当前数据库暂无数据表') + '</td></tr>';
    $('#pane-overview').innerHTML = '<div class="db-browser"><div class="facts">' +
      fact('数据源', display(state.source && state.source.name)) + fact('数据库', state.database) + fact('数据表', state.tableCache[state.database] ? tables.length : '加载中') + fact('数据库说明', database.comment || '—') +
      '</div><div class="db-browser-head"><strong>数据表</strong><input class="search" id="dbTableSearch" placeholder="在当前数据库中搜索表"></div><table class="data-table"><thead><tr><th style="width:34%">表名</th><th style="width:18%">类型</th><th>说明</th></tr></thead><tbody>' + rows + '</tbody></table></div>';
    var search = $('#dbTableSearch');
    if (search) { search.value = filter || ''; search.oninput = function () { renderDatabaseDetail(this.value); }; }
    $$('[data-detail-table]').forEach(function (row) { row.onclick = function () { selectTable(state.database, row.dataset.detailTable); }; });
    $('#pane-fields').innerHTML = '<div class="empty">请选择数据表</div>';
    $('#pane-lineage').innerHTML = '<div class="empty">请选择数据表</div>';
    renderBreadcrumb();
  }

  function fact(label, value) {
    return '<div class="fact"><label>' + esc(label) + '</label><strong title="' + esc(display(value)) + '">' + esc(display(value)) + '</strong></div>';
  }

  function renderTableHeader() {
    $('#detailSymbol').innerHTML = '<i class="ri-table-2"></i>';
    $('#detailTitle').textContent = state.table ? state.table.name : '请选择数据表';
    $('#detailPath').textContent = state.table ? display(state.source && state.source.name) + ' / ' + state.database + ' / ' + state.table.name : '';
    $('#detailType').textContent = state.table ? display(state.table.type, 'TABLE') : '—';
    $('#detailFieldCount').textContent = state.table ? '字段 ' + state.columns.length : '字段 —';
    $('#detailTabs').classList.remove('hidden');
    renderBreadcrumb();
  }

  function renderTableOverview() {
    var host = $('#pane-overview');
    if (!state.table) return;
    var source = state.source || {};
    var profile = state.profile || {};
    var comment = state.table.comment || '当前数据表没有维护说明。';
    var owner = display(profile.owner);
    var ownerEdit = profile.ownerEditable ? '<button type="button" class="fact-action" id="editTableOwner" title="设置拥有者"><i class="ri-edit-line"></i></button>' : '';
    host.innerHTML = '<div class="overview"><div class="facts table-profile-facts">' +
      fact('数据条数', formatNumber(profile.rowCount)) + fact('预估大小', formatBytes(profile.estimatedSizeBytes)) +
      '<div class="fact owner-fact"><label>拥有者</label><div class="fact-value"><strong title="' + esc(owner) + '">' + esc(owner) + '</strong>' + ownerEdit + '</div></div>' +
      fact('最近更新时间', formatTime(profile.updateTime)) +
      '</div><div class="section"><div class="section-title">表说明</div><div class="section-body">' + esc(comment) + '</div></div>' +
      '<div class="section"><div class="section-title">基本信息</div><div class="source-grid">' +
      '<span>数据源</span><b>' + esc(display(source.name)) + '</b><span>数据库</span><b>' + esc(state.database) + '</b>' +
      '<span>表类型</span><b>' + esc(display(state.table.type, 'TABLE')) + '</b><span>字段数量</span><b>' + esc(state.columns.length) + '</b>' +
      '<span>创建时间</span><b>' + esc(formatTime(profile.createTime)) + '</b><span>最近更新时间</span><b>' + esc(formatTime(profile.updateTime)) + '</b>' +
      '</div></div></div>';
    var editOwner = $('#editTableOwner');
    if (editOwner) editOwner.onclick = updateTableOwner;
  }

  function updateTableOwner() {
    if (!state.table || !state.profile || !state.profile.ownerEditable) return;
    var current = state.profile.owner || '';
    var next = window.prompt('设置数据表拥有者（留空可清除）', current);
    if (next == null) return;
    var body = { dataSourceId: state.source.id, database: state.database, table: state.table.name, owner: String(next).trim() };
    api('/metadata/table-profile/owner', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(function (profile) {
      state.profile = profile || {};
      renderTableOverview();
      notify(body.owner ? '数据表拥有者已更新' : '数据表拥有者已清除');
    }).catch(function (error) { notify(error.message || '拥有者更新失败', true); });
  }

  function renderFields() {
    var host = $('#pane-fields');
    if (!state.table) { host.innerHTML = '<div class="empty">请选择数据表</div>'; return; }
    if (!state.columns.length) { host.innerHTML = '<div class="empty"><i class="ri-layout-column-line"></i>暂无字段元数据</div>'; return; }
    host.innerHTML = '<table class="data-table"><thead><tr><th style="width:28%">字段名</th><th style="width:22%">数据类型</th><th style="width:16%">可为空</th><th>字段说明</th></tr></thead><tbody>' + state.columns.map(function (column) {
      return '<tr><td><span class="field-name">' + esc(column.name) + '</span></td><td>' + esc(display(column.dataType)) + '</td><td><span class="nullable' + (column.nullable ? '' : ' no') + '">' + (column.nullable ? 'YES' : 'NO') + '</span></td><td title="' + esc(column.comment || '') + '">' + esc(column.comment || '—') + '</td></tr>';
    }).join('') + '</tbody></table>';
  }

  function renderLineage() {
    var host = $('#pane-lineage');
    if (!state.table) { host.innerHTML = '<div class="empty">请选择数据表</div>'; return; }
    if (!state.lineage.length) { host.innerHTML = '<div class="empty"><i class="ri-node-tree"></i>暂无已解析的真实 SQL 血缘</div>'; return; }
    var current = qualifiedTable();
    host.innerHTML = '<div class="lineage-wrap"><div class="lineage-tip">当前表：<b>' + esc(current) + '</b>。以下关系来自平台已持久化的 SQL 血缘，不做推测补全。</div><table class="data-table"><thead><tr><th style="width:110px">方向</th><th>上游表</th><th>下游表</th><th style="width:90px">关系</th><th style="width:150px">来源版本</th></tr></thead><tbody>' + state.lineage.map(function (item) {
      var direction = tableMatches(item.targetTable, current) ? '上游 → 当前' : tableMatches(item.sourceTable, current) ? '当前 → 下游' : '关联';
      var version = item.fileId == null ? '—' : '文件 #' + item.fileId + (item.fileVersionId == null ? '' : ' / 版本 #' + item.fileVersionId);
      return '<tr><td><span class="direction">' + esc(direction) + '</span></td><td title="' + esc(item.sourceTable || '') + '">' + esc(display(item.sourceTable)) + '</td><td title="' + esc(item.targetTable || '') + '">' + esc(display(item.targetTable)) + '</td><td>' + esc(display(item.relationType, 'SQL')) + '</td><td>' + esc(version) + '</td></tr>';
    }).join('') + '</tbody></table></div>';
  }

  function renderTableDetail() {
    renderTableHeader();
    renderTableOverview();
    renderFields();
    renderLineage();
  }

  function loadTables(database) {
    if (state.tableCache[database]) return Promise.resolve(state.tableCache[database]);
    if (state.tableLoading[database]) return state.tableLoading[database];
    state.tableLoading[database] = api('/metadata/tables?dataSourceId=' + encodeURIComponent(state.source.id) + '&database=' + encodeURIComponent(database)).then(function (items) {
      state.tableCache[database] = Array.isArray(items) ? items : [];
      return state.tableCache[database];
    }).finally(function () {
      delete state.tableLoading[database];
      renderCatalog();
    });
    renderCatalog();
    return state.tableLoading[database];
  }

  function selectDatabase(database) {
    state.selectionSeq += 1;
    state.database = database;
    state.table = null;
    state.columns = [];
    state.lineage = [];
    state.profile = null;
    state.expanded[database] = true;
    preferredDatabase = database;
    window.localStorage.setItem('metadataDatabase', database);
    renderCatalog();
    renderDatabaseDetail();
    return loadTables(database).then(function () {
      if (state.database !== database || state.table) return;
      renderCatalog();
      renderDatabaseDetail();
    }).catch(function (error) { notify(error.message, true); });
  }

  function selectTable(database, tableName) {
    state.expanded[database] = true;
    return loadTables(database).then(function (tables) {
      var table = tables.find(function (item) { return item.name === tableName; });
      if (!table) throw new Error('数据表不存在或已被删除：' + tableName);
      var seq = ++state.selectionSeq;
      state.database = database;
      state.table = table;
      state.columns = [];
      state.lineage = [];
      state.profile = null;
      preferredDatabase = database;
      window.localStorage.setItem('metadataDatabase', database);
      renderCatalog();
      renderTableDetail();
      $('#pane-fields').innerHTML = '<div class="skeleton">正在读取字段元数据…</div>';
      $('#pane-lineage').innerHTML = '<div class="skeleton">正在读取真实 SQL 血缘…</div>';
      var base = 'dataSourceId=' + encodeURIComponent(state.source.id) + '&database=' + encodeURIComponent(database) + '&table=' + encodeURIComponent(table.name);
      return Promise.allSettled([
        api('/metadata/columns?' + base),
        api('/lineage/table?name=' + encodeURIComponent(database + '.' + table.name)),
        api('/metadata/table-profile?' + base)
      ]).then(function (results) {
        if (seq !== state.selectionSeq || !state.table || state.database !== database || state.table.name !== table.name) return;
        if (results[0].status === 'fulfilled') state.columns = Array.isArray(results[0].value) ? results[0].value : [];
        else notify(results[0].reason.message || '字段读取失败', true);
        if (results[1].status === 'fulfilled') state.lineage = Array.isArray(results[1].value) ? results[1].value : [];
        else notify(results[1].reason.message || '血缘读取失败', true);
        if (results[2].status === 'fulfilled') state.profile = results[2].value || {};
        else notify(results[2].reason.message || '表画像读取失败', true);
        renderTableDetail();
      });
    }).catch(function (error) { notify(error.message, true); });
  }

  function loadDatabases(restoreDatabase) {
    state.selectionSeq += 1;
    state.databases = [];
    state.database = '';
    state.table = null;
    state.tableCache = Object.create(null);
    state.tableLoading = Object.create(null);
    state.expanded = Object.create(null);
    state.columns = [];
    state.lineage = [];
    state.profile = null;
    $('#catalogTree').innerHTML = '<div class="skeleton">正在加载数据库…</div>';
    renderEmptyDetail();
    return api('/metadata/databases?dataSourceId=' + encodeURIComponent(state.source.id) + '&type=' + encodeURIComponent(sourceType())).then(function (items) {
      state.databases = Array.isArray(items) ? items : [];
      renderCatalog();
      var wanted = restoreDatabase || preferredDatabase;
      if (wanted && state.databases.some(function (item) { return item.name === wanted; })) return selectDatabase(wanted);
    });
  }

  function selectSource(sourceId) {
    var source = state.sources.find(function (item) { return String(item.id) === String(sourceId); });
    if (!source) return;
    state.source = source;
    preferredSourceId = String(source.id);
    preferredDatabase = '';
    window.localStorage.setItem('metadataDataSourceId', preferredSourceId);
    window.localStorage.removeItem('metadataDatabase');
    window.history.replaceState(null, '', '/metadata.html?dataSourceId=' + encodeURIComponent(preferredSourceId));
    renderSourcePicker(); renderSourceState(); renderBreadcrumb();
    loadDatabases('').catch(function (error) { notify(error.message, true); });
  }

  function refreshSource() {
    if (!state.source) return Promise.resolve();
    return api('/data-sources/' + encodeURIComponent(state.source.id)).then(function (source) {
      var index = state.sources.findIndex(function (item) { return String(item.id) === String(source.id); });
      if (index >= 0) state.sources[index] = source;
      state.source = source;
      renderSourcePicker(); renderSourceState();
      if (state.table) renderTableDetail();
      else if (state.database) renderDatabaseDetail();
    });
  }

  function searchCatalog() {
    var query = String($('#catalogSearch').value || '').trim().toLowerCase();
    var seq = ++state.searchSeq;
    if (query.length < 2) { renderCatalog(); return; }
    var missing = state.databases.filter(function (database) { return !state.tableCache[database.name] && !state.tableLoading[database.name]; });
    if (!missing.length) { renderCatalog(); return; }
    Promise.allSettled(missing.map(function (database) { return loadTables(database.name); })).then(function () {
      if (seq === state.searchSeq) renderCatalog();
    });
    renderCatalog();
  }

  function bind() {
    $('#metadataSourceSelect').onchange = function () { selectSource(this.value); };
    $('#metadataRefresh').onclick = function () {
      if (!state.source) return notify('暂无可刷新数据源', true);
      var keepDatabase = state.database;
      notify('正在刷新元数据…');
      loadDatabases(keepDatabase).then(function () { notify('元数据已刷新'); }).catch(function (error) { notify(error.message, true); });
    };
    $('#testConnection').onclick = function () {
      if (!state.source) return notify('请先选择数据源', true);
      var button = this; button.disabled = true;
      api('/data-sources/' + encodeURIComponent(state.source.id) + '/test').then(function (result) {
        notify(result && result.message || '连接检测完成');
        return refreshSource();
      }).catch(function (error) { notify(error.message, true); }).finally(function () { button.disabled = false; });
    };
    var searchTimer = 0;
    $('#catalogSearch').oninput = function () {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(searchCatalog, 220);
    };
    $$('.tab').forEach(function (tab) { tab.onclick = function () { setActiveTab(tab.dataset.tab); }; });
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
    bind(); loadIdentity(); renderEmptyDetail();
    api('/data-sources').then(function (sources) {
      var all = Array.isArray(sources) ? sources : [];
      state.sources = all.filter(function (item) {
        var type = String(item.type || '').toUpperCase();
        return item.metadataVisible !== false && (type === 'MYSQL' || type === 'STARROCKS');
      });
      if (!state.sources.length) throw new Error('暂无已开启元数据展示的 MySQL / StarRocks 数据源');
      state.source = state.sources.find(function (item) { return String(item.id) === String(preferredSourceId); }) || state.sources[0];
      preferredSourceId = String(state.source.id);
      window.localStorage.setItem('metadataDataSourceId', preferredSourceId);
      renderSourcePicker(); renderSourceState(); renderBreadcrumb();
      return loadDatabases(preferredDatabase);
    }).catch(function (error) {
      state.source = null; state.sources = []; state.databases = [];
      renderSourcePicker(); renderSourceState(); renderCatalog(); renderEmptyDetail();
      notify(error.message, true);
    });
  }

  start();
})();
