(function () {
  'use strict';

  var colors = { success: '#31c18b', running: '#2d7df3', failed: '#ef4b45', pending: '#b7c1cf' };
  var page = function (id) { return document.getElementById(id); };
  var text = function (node, value) { if (node) node.textContent = value == null || value === '' ? '—' : String(value); };
  var escapeHtml = function (value) { return String(value == null ? '' : value).replace(/[&<>'"]/g, function (char) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]; }); };
  var number = function (value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)); };
  var when = function (value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—'; };
  var stateKind = function (value) {
    var status = String(value || '').toUpperCase();
    if (status.indexOf('SUCCESS') >= 0 || status.indexOf('PUBLISHED') >= 0 || status.indexOf('成功') >= 0) return 'success';
    if (status.indexOf('FAIL') >= 0 || status.indexOf('ERROR') >= 0 || status.indexOf('失败') >= 0) return 'failed';
    if (status.indexOf('RUNNING') >= 0 || status.indexOf('SUBMITTED') >= 0 || status.indexOf('运行') >= 0) return 'running';
    return 'pending';
  };
  var stateLabel = function (value) { var kind = stateKind(value); return kind === 'success' ? '成功' : kind === 'failed' ? '失败' : kind === 'running' ? '运行中' : '待运行'; };
  var stateTag = function (value) { var kind = stateKind(value); return '<span class="tag ' + (kind === 'success' ? 'ok' : kind === 'failed' ? 'fail' : kind === 'running' ? 'run' : 'purple') + '">● ' + stateLabel(value) + '</span>'; };
  var sourceStateTag = function (value) { var status = String(value || '').toUpperCase(); if (status === 'ACTIVE') return '<span class="tag ok">● 正常</span>'; if (/UNAVAILABLE|FAIL|ERROR|DOWN/.test(status)) return '<span class="tag fail">● 异常</span>'; return '<span class="tag purple">● 未检测</span>'; };
  var sourceTypeIcon = function (type) { var value = String(type || '').toUpperCase(); if (value === 'MYSQL') return '<img class="source-db-icon" src="/icons/database/mysql.svg" alt="MySQL">'; if (value === 'STARROCKS') return '<img class="source-db-icon" src="/icons/database/starrocks.svg" alt="StarRocks">'; return '<i class="ri-database-line ico-green" title="数据源"></i>'; };
  var liveState = { overview: null, sources: [], sourceCatalog: [], sourceViewItems: [], sourcePage: 1, sourcePageSize: 10, selectedSourceIds: {}, tasks: [], projects: [], selectedProjectId: null, developmentProjectIds: { mine: null, all: null }, projectFiles: [], folders: [], folderOpen: {}, selectedFile: null, openFiles: [], activeTabId: null, developmentScope: 'mine', currentUsername: '', selectedWorkflow: null, executionHistory: [], isAdmin: false, developmentRunning: false, developmentRunToken: 0 };

  function updateAdminControls(isAdmin) {
    liveState.isAdmin = !!isAdmin;
    window.platformAuth = window.platformAuth || {};
    window.platformAuth.isAdmin = liveState.isAdmin;
    var settingsNav = document.querySelector('.nav button[data-page="settings"]');
    if (settingsNav) settingsNav.style.display = liveState.isAdmin ? '' : 'none';
    document.querySelectorAll('[data-a="系统设置"], [data-a="数据源管理"]').forEach(function (node) {
      node.style.display = liveState.isAdmin ? '' : 'none';
      node.setAttribute('aria-hidden', liveState.isAdmin ? 'false' : 'true');
    });
  }

  function notify(message, error) {
    var toast = document.getElementById('toast');
    if (!toast) return;
    toast.querySelector('i').className = error ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';
    text(toast.querySelector('span'), message);
    toast.classList.add('show');
    window.clearTimeout(notify.timer);
    notify.timer = window.setTimeout(function () { toast.classList.remove('show'); }, 2800);
  }

  function ensurePasswordDialog() {
    var host = document.getElementById('password-dialog');
    if (!host || host.querySelector('.password-dialog')) return;
    host.innerHTML = '<style>#password-dialog{display:none;position:fixed;inset:0;z-index:1300;background:rgba(15,27,45,.28);align-items:center;justify-content:center;padding:20px}#password-dialog.open{display:flex}#password-dialog .password-dialog{width:420px;max-width:100%;background:#fff;border:1px solid #e2eaf4;border-radius:12px;box-shadow:0 12px 36px rgba(20,36,58,.16);overflow:hidden}#password-dialog .password-head{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #edf1f6}#password-dialog h3{margin:0;font-size:18px;color:#1d2e45}#password-dialog .password-close{border:0;background:none;color:#8996a8;font-size:23px;cursor:pointer}#password-dialog form{display:grid;gap:13px;padding:18px 20px}#password-dialog label{display:grid;gap:6px;color:#52647b;font-size:13px}#password-dialog input{height:38px;border:1px solid #d9e3ef;border-radius:8px;padding:0 11px;color:#28394e;background:#fff;font:inherit;font-size:13px;outline:none}#password-dialog input:focus{border-color:#729ff2;box-shadow:0 0 0 2px #eaf2ff}#password-dialog .password-error{min-height:16px;color:#df4545;font-size:12px}#password-dialog .password-foot{display:flex;justify-content:flex-end;gap:9px;border-top:1px solid #f0f2f5;padding-top:14px}#password-dialog .password-foot button{height:34px;padding:0 17px;border-radius:8px;border:1px solid #d5dfeb;background:#fff;color:#536276;font-size:13px;cursor:pointer}#password-dialog .password-foot .primary{border-color:#3478f6;background:#3478f6;color:#fff}</style><div class="password-dialog" role="dialog" aria-modal="true"><div class="password-head"><h3>修改密码</h3><button type="button" class="password-close">×</button></div><form><label>当前密码<input name="currentPassword" type="password" placeholder="请输入当前密码"></label><label>新密码<input name="newPassword" type="password" minlength="6" required placeholder="请输入至少 6 位的新密码"></label><label>确认新密码<input name="confirmPassword" type="password" minlength="6" required placeholder="请再次输入新密码"></label><div class="password-error" role="alert"></div><div class="password-foot"><button type="button" class="cancel">取消</button><button type="submit" class="primary">保存</button></div></form></div>';
    var close = function () { host.classList.remove('open'); host.setAttribute('aria-hidden', 'true'); };
    host.querySelector('.password-close').onclick = close;
    host.querySelector('.cancel').onclick = close;
    host.onclick = function (event) { if (event.target === host) close(); };
    host.querySelector('form').onsubmit = function (event) {
      event.preventDefault();
      var form = event.currentTarget;
      var error = form.querySelector('.password-error');
      var values = new FormData(form);
      var next = String(values.get('newPassword') || '');
      var confirm = String(values.get('confirmPassword') || '');
      if (next.length < 6) { error.textContent = '新密码至少需要 6 位'; return; }
      if (next !== confirm) { error.textContent = '两次输入的新密码不一致'; return; }
      var button = form.querySelector('.primary'); button.disabled = true;
      request('/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword: String(values.get('currentPassword') || ''), newPassword: next, confirmPassword: confirm }) }).then(function () { close(); notify('密码已修改，请使用新密码登录'); }).catch(function (e) { error.textContent = e.message; }).finally(function () { button.disabled = false; });
    };
  }
  function openPasswordDialog() {
    ensurePasswordDialog();
    var host = document.getElementById('password-dialog');
    var form = host && host.querySelector('form');
    if (!host || !form) return;
    form.reset(); form.querySelector('.password-error').textContent = '';
    host.classList.add('open'); host.setAttribute('aria-hidden', 'false');
    setTimeout(function () { if (form.elements.currentPassword) form.elements.currentPassword.focus(); }, 0);
  }

  function request(url, options) {
    options = options || {};
    var token = window.localStorage.getItem('platform_access_token');
    options.headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    if (token) options.headers.Authorization = 'Bearer ' + token;
    if (options.body) options.headers['Content-Type'] = 'application/json';
    return window.fetch('/api' + url, options).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (payload) {
        if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
        return payload.data;
      });
    });
  }

  function setKpis(container, values) {
    if (!container) return;
    container.querySelectorAll('.kpi-value').forEach(function (node, index) { text(node, values[index]); });
    container.querySelectorAll('.kpi-trend, .kpi-bars').forEach(function (node) { node.remove(); });
  }

  function emptyRow(columns, message) { return '<tr><td colspan="' + columns + '" style="height:88px;text-align:center;color:#8190a8">' + (message || '暂无数据') + '</td></tr>'; }

  function renderSourcePager(container, current, totalPages) {
    if (!container) return;
    var pages = [];
    for (var index = 1; index <= totalPages; index += 1) {
      if (index === 1 || index === totalPages || Math.abs(index - current) <= 1) pages.push(index);
    }
    var last = 0;
    var buttons = pages.map(function (index) {
      var gap = index - last > 1 ? '<span class="muted">…</span>' : '';
      last = index;
      return gap + '<button class="live-source-page ' + (index === current ? 'on' : '') + '" data-page="' + index + '">' + index + '</button>';
    }).join('');
    container.innerHTML = '<button class="live-source-page" data-page="' + Math.max(1, current - 1) + '" ' + (current === 1 ? 'disabled' : '') + '>‹</button>' + buttons + '<button class="live-source-page" data-page="' + Math.min(totalPages, current + 1) + '" ' + (current === totalPages ? 'disabled' : '') + '>›</button>';
  }

  function renderOverviewChart(overview) {
    var trendElement = document.getElementById('trend');
    var points = Array.isArray(overview.trend) ? overview.trend : [];
    if (trendElement) {
      var width = 900, height = 190, left = 42, right = 14, top = 16, bottom = 30;
      var plotWidth = width - left - right, plotHeight = height - top - bottom;
      var maxValue = points.reduce(function (max, item) { return Math.max(max, Number(item.success || 0), Number(item.failed || 0), Number(item.running || 0)); }, 0);
      var labels = points.length ? points.map(function (item) { return escapeHtml(String(item.date || '').slice(-5)); }) : ['—', '—', '—', '—', '—'];
      var grid = [0, 1, 2, 3, 4].map(function (row) { var y = top + plotHeight * row / 4; return '<line x1="' + left + '" y1="' + y + '" x2="' + (width - right) + '" y2="' + y + '" class="overview-grid"/><text x="' + (left - 8) + '" y="' + (y + 4) + '" text-anchor="end" class="overview-axis">' + (maxValue ? Math.round(maxValue * (4 - row) / 4) : '—') + '</text>'; }).join('');
      var series = [{ key: 'success', color: '#16b981' }, { key: 'failed', color: '#ff4d4f' }, { key: 'running', color: '#2878f0' }].map(function (line) {
        if (!points.length) return '';
        var denominator = Math.max(points.length - 1, 1);
        var coords = points.map(function (item, index) { var x = left + plotWidth * index / denominator; var value = Number(item[line.key] || 0); var y = top + plotHeight - (maxValue ? (value / maxValue) * plotHeight : plotHeight / 2); return { x: x, y: y }; });
        var path = 'M' + coords[0].x.toFixed(1) + ' ' + coords[0].y.toFixed(1);
        for (var i = 0; i < coords.length - 1; i += 1) {
          var p0 = coords[i - 1] || coords[i], p1 = coords[i], p2 = coords[i + 1], p3 = coords[i + 2] || p2;
          var c1x = p1.x + (p2.x - p0.x) / 6, c1y = p1.y + (p2.y - p0.y) / 6;
          var c2x = p2.x - (p3.x - p1.x) / 6, c2y = p2.y - (p3.y - p1.y) / 6;
          path += ' C' + c1x.toFixed(1) + ' ' + c1y.toFixed(1) + ' ' + c2x.toFixed(1) + ' ' + c2y.toFixed(1) + ' ' + p2.x.toFixed(1) + ' ' + p2.y.toFixed(1);
        }
        return '<path d="' + path + '" stroke="' + line.color + '" class="overview-line overview-line-' + line.key + '"/>';
      }).join('');
      var xLabels = labels.map(function (label, index) { var denominator = Math.max(labels.length - 1, 1); var x = left + plotWidth * index / denominator; return '<text x="' + x + '" y="' + (height - 8) + '" text-anchor="middle" class="overview-axis">' + label + '</text>'; }).join('');
      var hoverZones = points.map(function (item, index) { var denominator = Math.max(points.length - 1, 1); var x = left + plotWidth * index / denominator; var zoneWidth = plotWidth / Math.max(points.length, 1); return '<rect class="overview-hover" data-index="' + index + '" x="' + Math.max(left, x - zoneWidth / 2) + '" y="' + top + '" width="' + zoneWidth + '" height="' + plotHeight + '"/>'; }).join('');
      trendElement.innerHTML = '<svg class="overview-trend-svg" viewBox="0 0 ' + width + ' ' + height + '" preserveAspectRatio="none"><g>' + grid + '</g>' + series + xLabels + hoverZones + (points.length ? '' : '<text x="' + (width / 2) + '" y="' + (height / 2 + 4) + '" text-anchor="middle" class="overview-empty-label">暂无趋势数据</text>') + '</svg><div class="overview-tooltip"></div>';
      var tooltip = trendElement.querySelector('.overview-tooltip');
      trendElement.querySelectorAll('.overview-hover').forEach(function (zone) {
        zone.onmouseenter = zone.onmousemove = function (event) {
          var item = points[Number(zone.getAttribute('data-index'))] || {};
          var box = trendElement.getBoundingClientRect();
          var x = Math.min(Math.max(event.clientX - box.left + 10, 8), Math.max(8, box.width - 178));
          var y = Math.max(8, event.clientY - box.top - 74);
          tooltip.innerHTML = '<strong>' + escapeHtml(String(item.date || '')) + '</strong><span><i class="success-dot"></i>成功　' + number(item.success || 0) + '</span><span><i class="failed-dot"></i>失败　' + number(item.failed || 0) + '</span><span><i class="running-dot"></i>运行中　' + number(item.running || 0) + '</span>';
          tooltip.style.left = x + 'px'; tooltip.style.top = y + 'px'; tooltip.style.display = 'grid';
        };
      });
      trendElement.onmouseleave = function () { if (tooltip) tooltip.style.display = 'none'; };
      var legendItems = document.querySelectorAll('#page-workbench .mid .legend span');
      ['success', 'failed', 'running'].forEach(function (key, index) {
        var item = legendItems[index];
        if (!item) return;
        item.classList.remove('off');
        item.onclick = function () {
          item.classList.toggle('off');
          trendElement.querySelectorAll('.overview-line-' + key).forEach(function (line) { line.style.display = item.classList.contains('off') ? 'none' : ''; });
        };
      });
    }
    var donutElement = document.getElementById('donut');
    if (donutElement) {
      var tasks = overview.tasks || {};
      var total = Number(tasks.total || 0), success = Number(tasks.success || 0), running = Number(tasks.running || 0), failed = Number(tasks.failed || 0), pending = Number(tasks.pending || 0);
      var cursor = 0;
      var palette = ['#16b981', '#2878f0', '#ff4d4f', '#aab7c4'];
      var sliceItems = [{ name: '成功', value: success, color: palette[0] }, { name: '运行中', value: running, color: palette[1] }, { name: '失败', value: failed, color: palette[2] }, { name: '待运行', value: pending, color: palette[3] }];
      var slices = total ? sliceItems.map(function (slice) { var start = cursor / total * 100; cursor += slice.value; var end = cursor / total * 100; return slice.color + ' ' + start + '% ' + end + '%'; }) : palette.map(function (color, index) { return color + ' ' + (index * 25) + '% ' + ((index + 1) * 25) + '%'; });
      var center = total ? '<div><b>' + number(total) + '</b><span>总任务数</span></div>' : '';
      donutElement.innerHTML = '<div class="overview-donut-ring" style="background:conic-gradient(' + slices.join(',') + ')">' + center + '</div><div class="overview-donut-tooltip"></div>';
      var ring = donutElement.querySelector('.overview-donut-ring');
      var donutTooltip = donutElement.querySelector('.overview-donut-tooltip');
      if (ring && donutTooltip && total) {
        ring.onmousemove = function (event) {
          var box = ring.getBoundingClientRect();
          var dx = event.clientX - (box.left + box.width / 2), dy = event.clientY - (box.top + box.height / 2);
          var ratio = (Math.atan2(dy, dx) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2) / (Math.PI * 2);
          var accumulated = 0, active = sliceItems[0];
          sliceItems.some(function (slice) { accumulated += slice.value / total; if (ratio <= accumulated) { active = slice; return true; } return false; });
          var percent = (active.value / total * 100).toFixed(1);
          donutTooltip.innerHTML = '<strong>' + active.name + '</strong><span>' + number(active.value) + '　' + percent + '%</span>';
          var parent = donutElement.getBoundingClientRect();
          donutTooltip.style.left = Math.min(Math.max(event.clientX - parent.left + 8, 4), Math.max(4, parent.width - 130)) + 'px';
          donutTooltip.style.top = Math.max(4, event.clientY - parent.top - 42) + 'px';
          donutTooltip.style.display = 'grid';
        };
        ring.onmouseleave = function () { donutTooltip.style.display = 'none'; };
      }
    }
  }

  function renderOverview(overview) {
    liveState.overview = overview;
    var workbench = page('page-workbench');
    if (!workbench) return;
    var badge = document.querySelector('.badge'); if (badge) { text(badge, overview.tasks.failed); badge.style.display = overview.tasks.failed ? 'grid' : 'none'; }
    var metrics = workbench.querySelectorAll('.summary .metric .mv');
    [overview.tasks.total, overview.tasks.running, overview.tasks.success, overview.tasks.failed].forEach(function (value, index) { text(metrics[index], number(value)); });
    workbench.querySelectorAll('.summary .metric .trend, .summary .metric .bars').forEach(function (node) { node.remove(); });
    var online = overview.platform.status === 'UP';
    var statusCard = workbench.querySelector('.summary .status');
    if (statusCard) {
      text(statusCard.querySelector('.online'), online ? '运行正常' : '服务不可用');
      var count = statusCard.querySelector('.count strong');
      text(count, overview.platform.onlineDataSources + ' / ' + overview.platform.totalDataSources);
      text(statusCard.querySelector('.count small'), '数据源在线');
      var services = statusCard.querySelector('.services');
      if (services) {
        var dataSourcesHealthy = overview.platform.totalDataSources > 0 && overview.platform.onlineDataSources === overview.platform.totalDataSources;
        services.innerHTML = '<div class="service-track"><div class="service"><span class="dot"></span>DolphinScheduler <em>' + (overview.platform.schedulerAvailable ? '正常' : '异常') + '</em></div><div class="service"><span class="dot"></span>数据源连接 <em>' + (dataSourcesHealthy ? '正常' : '异常') + '</em></div><div class="service"><span class="dot"></span>平台服务 <em>' + (online ? '正常' : '异常') + '</em></div></div>';
        startPlatformStatusTicker(statusCard);
      }
    }
    var list = workbench.querySelector('.bottom .table tbody');
    if (list) list.innerHTML = overview.recentTasks.length ? overview.recentTasks.map(function (task) {
      return '<tr><td><i class="ri-file-code-line fi"></i>' + escapeHtml(task.name) + '</td><td>' + escapeHtml(task.type) + '</td><td><span class="state ' + stateKind(task.status) + '">' + stateLabel(task.status) + '</span></td><td>' + when(task.startedAt) + '</td><td>' + escapeHtml(task.detail || '—') + '</td><td><button class="tm" data-live-task="' + escapeHtml(task.id) + '">···</button></td></tr>';
    }).join('') : emptyRow(6, '暂无运行记录');
    var favourites = workbench.querySelector('.favs');
    if (favourites) favourites.innerHTML = overview.favorites.length ? overview.favorites.map(function (item) {
      return '<div class="fav"><span class="fic purple"><i class="ri-file-list-3-line"></i></span><div><div class="fname">' + escapeHtml(item.name) + '</div><div class="fmeta">' + escapeHtml(item.type) + (item.detail ? ' · ' + escapeHtml(item.detail) : '') + '</div></div></div>';
    }).join('') : '<div style="padding:28px;color:#8190a8;text-align:center">暂无收藏</div>';
    renderOverviewChart(overview);
    var statusList = workbench.querySelector('.status-list');
    if (statusList) statusList.innerHTML = [['成功', overview.tasks.success, 'green'], ['运行中', overview.tasks.running, 'blueDot'], ['失败', overview.tasks.failed, 'red'], ['待运行', overview.tasks.pending, 'gray']].map(function (item) {
      var total = overview.tasks.total || 0;
      return '<div class="srow"><span class="slabel"><i class="dot ' + item[2] + '"></i>' + item[0] + '</span><strong>' + number(item[1]) + '</strong><small>' + (total ? ((item[1] / total) * 100).toFixed(1) : '0.0') + '%</small></div>';
    }).join('');
  }

  function renderSources(sources, replaceCatalog) {
    liveState.sources = sources.items || [];
    if (replaceCatalog !== false) liveState.sourceCatalog = liveState.sources.slice();
    liveState.sourceViewItems = liveState.sources.slice();
    var sourcePage = page('page-source');
    if (!sourcePage) return;
    var statistics = replaceCatalog === false ? sourceSummary(sourceCatalog()) : sources;
    var sourceLabels = sourcePage.querySelectorAll('.kpi-label'); if (sourceLabels[3]) text(sourceLabels[3], '元数据可见数据源');
    if (replaceCatalog !== false) {
      var filters = sourcePage.querySelectorAll('.filter-row select');
      if (filters[0]) filters[0].innerHTML = '<option value="">全部类型</option>' + Object.keys(sources.typeDistribution || {}).map(function (type) { return '<option value="' + escapeHtml(type) + '">' + escapeHtml(type) + '</option>'; }).join('');
      if (filters[1]) filters[1].innerHTML = '<option value="">全部状态</option><option value="ACTIVE">正常</option><option value="UNAVAILABLE">异常</option><option value="UNKNOWN">未检测</option>';
    }
    setKpis(sourcePage, [number(statistics.total), number(statistics.healthy), number(statistics.unhealthy), number(statistics.metadataVisible)]);
    var side = sourcePage.querySelector('.side-list');
    if (side) {
      var types = Object.keys(sources.typeDistribution || {});
      side.innerHTML = '<div class="side-row on"><i class="ri-node-tree ico-blue"></i>全部数据源 <span class="num">' + number(sources.total) + '</span></div>' + (types.length ? types.map(function (type) { return '<div class="side-row"><i class="ri-database-2-line"></i>' + escapeHtml(type) + '<span class="num">' + number(sources.typeDistribution[type]) + '</span></div>'; }).join('') : '<div class="side-row muted">暂无数据源类型</div>');
    }
    var totalPages = Math.max(1, Math.ceil(liveState.sources.length / liveState.sourcePageSize));
    if (liveState.sourcePage > totalPages) liveState.sourcePage = totalPages;
    var offset = (liveState.sourcePage - 1) * liveState.sourcePageSize;
    var visibleItems = liveState.sources.slice(offset, offset + liveState.sourcePageSize);
    var sourceIds = {};
    sourceCatalog().forEach(function (item) { sourceIds[String(item.id)] = true; });
    Object.keys(liveState.selectedSourceIds).forEach(function (id) { if (!sourceIds[id]) delete liveState.selectedSourceIds[id]; });
    var body = sourcePage.querySelector('.data-table tbody');
    if (body) body.innerHTML = visibleItems.length ? visibleItems.map(function (item) {
      var id = String(item.id);
      return '<tr><td><input class="live-select-source" type="checkbox" data-id="' + escapeHtml(id) + '" aria-label="选择 ' + escapeHtml(item.name) + '"' + (liveState.selectedSourceIds[id] ? ' checked' : '') + '></td><td class="link">' + sourceTypeIcon(item.type) + escapeHtml(item.name) + '</td><td>' + escapeHtml(item.type) + '</td><td>' + sourceStateTag(item.status) + '</td><td title="' + escapeHtml(item.lastCheckMessage || '') + '">' + when(item.lastCheckedAt) + '</td><td class="source-actions"><button class="source-action source-action-test live-test-source" data-id="' + item.id + '">检测</button><button class="source-action source-action-edit live-edit-source" data-id="' + item.id + '">编辑</button><button class="source-action source-action-delete live-delete-source" data-id="' + item.id + '">删除</button></td></tr>';
    }).join('') : emptyRow(6, '暂无数据源，请新建数据源');
    var selectedCount = Object.keys(liveState.selectedSourceIds).length;
    var selectAll = sourcePage.querySelector('.live-select-all-sources');
    if (selectAll) {
      var selectedOnPage = visibleItems.filter(function (item) { return !!liveState.selectedSourceIds[String(item.id)]; }).length;
      selectAll.checked = visibleItems.length > 0 && selectedOnPage === visibleItems.length;
      selectAll.indeterminate = selectedOnPage > 0 && selectedOnPage < visibleItems.length;
    }
    var bulkDelete = sourcePage.querySelector('.source-bulk-delete');
    if (bulkDelete) { bulkDelete.hidden = selectedCount === 0; bulkDelete.classList.toggle('live-bulk-delete-sources', selectedCount > 0); bulkDelete.innerHTML = '<i class="ri-delete-bin-line"></i>删除已选 (' + selectedCount + ')'; }
    var footer = sourcePage.querySelector('.source-footer > span'); if (footer) text(footer, '共 ' + number(sources.total) + ' 条');
    var pageSize = sourcePage.querySelector('.source-page-size'); if (pageSize) pageSize.value = String(liveState.sourcePageSize);
    renderSourcePager(sourcePage.querySelector('.source-pager'), liveState.sourcePage, totalPages);
    var donut = sourcePage.querySelector('.source-donut');
    if (donut) {
      var dist = statistics.typeDistribution || {}; var total = statistics.total || 0; var palette = ['#2d7df3', '#7658eb', '#31c18b', '#f2a51f', '#67b7f7', '#bdc8d6']; var cursor = 0; var stops = Object.keys(dist).map(function (type, index) { var start = total ? cursor / total * 100 : 0; cursor += Number(dist[type] || 0); var end = total ? cursor / total * 100 : 0; return palette[index % palette.length] + ' ' + start + '% ' + end + '%'; }); donut.style.background = stops.length ? 'conic-gradient(' + stops.join(',') + ')' : '#dfe6ef'; var center = donut.querySelector('b'); if (center) text(center, number(total));
      var legend = sourcePage.querySelector('.source-donut + .legend-list'); if (legend) legend.innerHTML = Object.keys(dist).length ? Object.keys(dist).map(function (type, index) { return '<div class="legend-item"><span><i class="dot" style="background:' + palette[index % palette.length] + '"></i>' + escapeHtml(type) + '</span><b>' + number(dist[type]) + '</b><small>' + (total ? ((dist[type] / total) * 100).toFixed(1) : '0.0') + '%</small></div>'; }).join('') : '<div class="muted">暂无数据源</div>';
    }
    var healthBar = sourcePage.querySelector('.health-bar i'); if (healthBar) healthBar.style.width = (statistics.total ? (statistics.healthy / statistics.total * 100) : 0) + '%';
    var healthLabels = sourcePage.querySelectorAll('.health-bar + div span'); if (healthLabels[0]) text(healthLabels[0], '● 正常　' + number(statistics.healthy) + '　' + (statistics.total ? (statistics.healthy / statistics.total * 100).toFixed(1) : '0.0') + '%'); if (healthLabels[1]) text(healthLabels[1], '● 异常 ' + number(statistics.unhealthy) + '　未检测 ' + number(statistics.unchecked));
  }

  function sourceSummary(items) {
    var distribution = {};
    var healthy = 0; var unhealthy = 0;
    var visible = 0;
    items.forEach(function (item) {
      var type = item.type || '其他';
      distribution[type] = (distribution[type] || 0) + 1;
      if (item.healthy === true || String(item.status || '').toUpperCase() === 'ACTIVE') healthy += 1;
      if (/UNAVAILABLE|FAIL|ERROR|DOWN/.test(String(item.status || '').toUpperCase())) unhealthy += 1;
      if (item.metadataVisible) visible += 1;
    });
    return { items: items, total: items.length, healthy: healthy, unhealthy: unhealthy, unchecked: items.length - healthy - unhealthy, metadataVisible: visible, typeDistribution: distribution };
  }

  function filterSourceList(query) {
    var sourcePage = page('page-source'); var filters = sourcePage ? sourcePage.querySelectorAll('.filter-row select') : [];
    var keyword = String(query || '').trim().toLowerCase(); var type = filters[0] ? filters[0].value : ''; var status = filters[1] ? filters[1].value : '';
    var catalog = liveState.sourceCatalog || [];
    var matches = catalog.filter(function (item) {
      if (!keyword) return true;
      return [item.name, item.type, item.host, item.databaseName, item.username].some(function (value) { return String(value || '').toLowerCase().indexOf(keyword) >= 0; });
    });
    matches = matches.filter(function (item) { return (!type || item.type === type) && (!status || (status === 'UNAVAILABLE' ? /UNAVAILABLE|FAIL|ERROR|DOWN/.test(String(item.status || '').toUpperCase()) : String(item.status || '').toUpperCase() === status)); });
    renderSources(sourceSummary(matches), false);
  }

  function replaceSvgChart(scope, id, option) {
    if (!window.echarts || !scope) return;
    var svg = scope.querySelector('.static-line,.live-chart-empty');
    if (svg) {
      var holder = document.createElement('div');
      holder.id = id;
      holder.style.cssText = 'height:190px;width:100%;';
      svg.replaceWith(holder);
    }
    var element = document.getElementById(id);
    if (!element) return;
    var chart = window.echarts.getInstanceByDom(element) || window.echarts.init(element);
    chart.setOption(option, true);
  }

  function clearChart(id) {
    if (!window.echarts) return;
    var element = document.getElementById(id);
    var chart = element && window.echarts.getInstanceByDom(element);
    if (chart) chart.clear();
  }

  function trendOption(points) {
    var hasActualPoints = Array.isArray(points) && points.length > 1 && points.some(function (item) { return Number(item.success || 0) || Number(item.failed || 0) || Number(item.running || 0); });
    var chartPoints = hasActualPoints ? points : [
      { date: '—', success: 5.0, failed: 1.1, running: 2.5 }, { date: '—', success: 5.3, failed: 1.0, running: 2.6 },
      { date: '—', success: 5.1, failed: 1.2, running: 2.4 }, { date: '—', success: 5.5, failed: 1.1, running: 2.7 },
      { date: '—', success: 5.3, failed: 1.0, running: 2.6 }, { date: '—', success: 5.6, failed: 1.1, running: 2.8 },
      { date: '—', success: 5.4, failed: 1.0, running: 2.7 }
    ];
    return {
      animationDuration: 500, grid: { left: 40, right: 16, top: 18, bottom: 30 }, tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', boundaryGap: false, data: chartPoints.map(function (item) { return item.date; }) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '成功', type: 'line', smooth: 0.55, showSymbol: false, data: chartPoints.map(function (item) { return item.success || 0; }), itemStyle: { color: colors.success }, lineStyle: { color: colors.success, width: 2.2 }, opacity: hasActualPoints ? 1 : 0.7 },
        { name: '失败', type: 'line', smooth: 0.55, showSymbol: false, data: chartPoints.map(function (item) { return item.failed || 0; }), itemStyle: { color: colors.failed }, lineStyle: { color: colors.failed, width: 2 }, opacity: hasActualPoints ? 1 : 0.7 },
        { name: '运行中', type: 'line', smooth: 0.55, showSymbol: false, data: chartPoints.map(function (item) { return item.running || 0; }), itemStyle: { color: colors.running }, lineStyle: { color: colors.running, width: 2 }, opacity: hasActualPoints ? 1 : 0.7 }
      ]
    };
  }

  function renderIntegrationTrend(scope, points) {
    if (!scope) return;
    if (window.echarts) { replaceSvgChart(scope, 'live-integration-trend', trendOption(points || [])); return; }
    var actual = Array.isArray(points) && points.length > 1 && points.some(function (item) { return Number(item.success || 0) || Number(item.failed || 0) || Number(item.running || 0); });
    var values = actual ? points : [
      { date: '—', success: 5.0, failed: 1.1, running: 2.5 }, { date: '—', success: 5.3, failed: 1.0, running: 2.6 },
      { date: '—', success: 5.1, failed: 1.2, running: 2.4 }, { date: '—', success: 5.5, failed: 1.1, running: 2.7 },
      { date: '—', success: 5.3, failed: 1.0, running: 2.6 }, { date: '—', success: 5.6, failed: 1.1, running: 2.8 },
      { date: '—', success: 5.4, failed: 1.0, running: 2.7 }
    ];
    var width = 900, height = 190, left = 42, right = 16, top = 16, bottom = 30, plotWidth = width - left - right, plotHeight = height - top - bottom;
    var max = Math.max(1, values.reduce(function (result, item) { return Math.max(result, Number(item.success || 0), Number(item.failed || 0), Number(item.running || 0)); }, 0));
    function coordinates(key) { return values.map(function (item, index) { return { x: left + plotWidth * index / Math.max(values.length - 1, 1), y: top + plotHeight - Number(item[key] || 0) / max * plotHeight }; }); }
    function smoothPath(coords) { var path = 'M' + coords[0].x.toFixed(1) + ' ' + coords[0].y.toFixed(1); for (var i = 0; i < coords.length - 1; i += 1) { var p0 = coords[i - 1] || coords[i], p1 = coords[i], p2 = coords[i + 1], p3 = coords[i + 2] || p2; path += ' C' + (p1.x + (p2.x - p0.x) / 6).toFixed(1) + ' ' + (p1.y + (p2.y - p0.y) / 6).toFixed(1) + ' ' + (p2.x - (p3.x - p1.x) / 6).toFixed(1) + ' ' + (p2.y - (p3.y - p1.y) / 6).toFixed(1) + ' ' + p2.x.toFixed(1) + ' ' + p2.y.toFixed(1); } return path; }
    var grid = [0, 1, 2, 3, 4].map(function (index) { var y = top + plotHeight * index / 4; return '<line x1="' + left + '" y1="' + y + '" x2="' + (width - right) + '" y2="' + y + '" stroke="#e7edf5"/><text x="' + (left - 8) + '" y="' + (y + 4) + '" text-anchor="end" fill="#8190a8" font-size="10">' + Math.round(max * (4 - index) / 4) + '</text>'; }).join('');
    var labels = values.map(function (item, index) { return '<text x="' + (left + plotWidth * index / Math.max(values.length - 1, 1)) + '" y="' + (height - 8) + '" text-anchor="middle" fill="#8190a8" font-size="10">' + escapeHtml(String(item.date || '—').slice(-5)) + '</text>'; }).join('');
    var lines = [['success', colors.success, 2.3], ['failed', colors.failed, 1.9], ['running', colors.running, 2]].map(function (item) { return '<path class="integration-line integration-line-' + item[0] + '" d="' + smoothPath(coordinates(item[0])) + '" fill="none" stroke="' + item[1] + '" stroke-width="' + item[2] + '" stroke-linecap="round" opacity="' + (actual ? 1 : .72) + '"/>'; }).join('');
    var hoverZones = values.map(function (item, index) { var x = left + plotWidth * index / Math.max(values.length - 1, 1); var zoneWidth = plotWidth / Math.max(values.length, 1); return '<rect class="integration-hover" data-index="' + index + '" x="' + Math.max(left, x - zoneWidth / 2) + '" y="' + top + '" width="' + zoneWidth + '" height="' + plotHeight + '"/>'; }).join('');
    var old = scope.querySelector('.static-line,.live-chart-empty,#live-integration-trend');
    if (!old) return;
    old.outerHTML = '<svg class="static-line live-integration-svg" viewBox="0 0 ' + width + ' ' + height + '" preserveAspectRatio="none" aria-label="任务执行趋势">' + grid + lines + labels + hoverZones + '</svg><div class="integration-tooltip"></div>';
    var tooltip = scope.querySelector('.integration-tooltip');
    scope.querySelectorAll('.integration-hover').forEach(function (zone) {
      zone.onmouseenter = zone.onmousemove = function (event) {
        var item = values[Number(zone.getAttribute('data-index'))] || {};
        var box = scope.getBoundingClientRect();
        var x = Math.min(Math.max(event.clientX - box.left + 10, 8), Math.max(8, box.width - 172));
        var y = Math.max(38, event.clientY - box.top - 78);
        tooltip.innerHTML = '<strong>' + escapeHtml(actual ? String(item.date || '') : '暂无真实趋势数据') + '</strong><span><i class="success-dot"></i>成功　' + number(item.success || 0) + '</span><span><i class="failed-dot"></i>失败　' + number(item.failed || 0) + '</span><span><i class="running-dot"></i>运行中　' + number(item.running || 0) + '</span>';
        tooltip.style.left = x + 'px'; tooltip.style.top = y + 'px'; tooltip.style.display = 'grid';
      };
    });
    scope.onmouseleave = function () { if (tooltip) tooltip.style.display = 'none'; };
    var legendItems = scope.querySelectorAll('.legend span');
    ['success', 'failed', 'running'].forEach(function (key, index) {
      var legend = legendItems[index];
      if (!legend) return;
      legend.classList.remove('off');
      legend.onclick = function () {
        legend.classList.toggle('off');
        scope.querySelectorAll('.integration-line-' + key).forEach(function (line) { line.style.display = legend.classList.contains('off') ? 'none' : ''; });
      };
    });
  }

  function bindIntegrationStatusTooltip(scope, summary) {
    if (!scope) return;
    var total = Number(summary.total || 0);
    var slices = [
      { label: '成功', value: Number(summary.success || 0), key: 'success' },
      { label: '运行中', value: Number(summary.running || 0), key: 'running' },
      { label: '失败', value: Number(summary.failed || 0), key: 'failed' },
      { label: '待运行', value: Math.max(0, total - Number(summary.success || 0) - Number(summary.running || 0) - Number(summary.failed || 0)), key: 'pending' }
    ];
    var donut = scope.querySelector('.int-donut');
    if (!donut) return;
    var cursor = 0;
    var gradient = total ? slices.map(function (slice) { var start = cursor / total * 100; cursor += slice.value; return colors[slice.key] + ' ' + start + '% ' + (cursor / total * 100) + '%'; }).join(',') : slices.map(function (slice, index) { return colors[slice.key] + ' ' + index * 25 + '% ' + (index + 1) * 25 + '%'; }).join(',');
    donut.style.background = 'conic-gradient(' + gradient + ')';
    var tooltip = scope.querySelector('.integration-status-tooltip');
    if (!tooltip) { tooltip = document.createElement('div'); tooltip.className = 'integration-status-tooltip'; scope.appendChild(tooltip); }
    function show(event, selected) {
      var box = scope.getBoundingClientRect();
      var percent = total ? (selected.value / total * 100).toFixed(1) : '0.0';
      tooltip.innerHTML = '<strong>' + selected.label + '</strong><span><i class="' + selected.key + '-dot"></i>' + number(selected.value) + '　' + percent + '%</span>';
      tooltip.style.left = Math.min(Math.max(event.clientX - box.left + 9, 8), Math.max(8, box.width - 138)) + 'px';
      tooltip.style.top = Math.max(45, event.clientY - box.top - 42) + 'px';
      tooltip.style.display = 'grid';
    }
    donut.onmousemove = function (event) {
      var box = donut.getBoundingClientRect();
      var dx = event.clientX - (box.left + box.width / 2), dy = event.clientY - (box.top + box.height / 2);
      var ratio = (Math.atan2(dy, dx) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2) / (Math.PI * 2);
      var accumulated = 0, selected = slices[0];
      if (total) slices.some(function (slice) { accumulated += slice.value / total; if (ratio <= accumulated) { selected = slice; return true; } return false; });
      show(event, selected);
    };
    donut.onmouseleave = function () { tooltip.style.display = 'none'; };
    scope.querySelectorAll('.legend-item').forEach(function (legend, index) {
      legend.onmousemove = function (event) { show(event, slices[index] || slices[0]); };
      legend.onmouseleave = function () { tooltip.style.display = 'none'; };
    });
  }

  function renderIntegrationRows(tasks) {
    var body = document.querySelector('#page-integration .integration-table tbody');
    if (!body) return;
    body.innerHTML = tasks.length ? tasks.map(function (task) {
      return '<tr><td>—</td><td class="link"><i class="ri-file-list-3-line ico-blue"></i>' + escapeHtml(task.name) + '</td><td>' + escapeHtml(task.sourceType) + '</td><td>' + escapeHtml(task.targetType) + '</td><td><span class="tag run">' + escapeHtml(task.syncMode) + '</span></td><td>—</td><td>—</td><td>' + stateTag(task.status) + '</td><td>—</td><td><button class="link live-run-task" data-id="' + task.id + '">运行</button></td></tr>';
    }).join('') : emptyRow(10, '暂无集成任务');
  }

  function renderIntegrationPulse(tasks) {
    var track = document.getElementById('integrationPulseTrack');
    if (!track) return;
    var running = (tasks || []).filter(function (task) { return stateKind(task.status) === 'running'; });
    if (!running.length) { track.innerHTML = '<div class="pulse-empty">当前暂无执行中的任务</div>'; track.style.animation = 'none'; return; }
    var cards = running.map(function (task) { return '<div class="pulse-item"><b>' + escapeHtml(task.name || '未命名任务') + '</b><span><i></i>' + escapeHtml(task.sourceType || '—') + ' → ' + escapeHtml(task.targetType || '—') + '</span></div>'; }).join('');
    track.innerHTML = cards + cards;
    track.style.animation = running.length > 1 ? '' : 'integration-pulse-scroll 10s linear infinite';
  }

  function renderIntegration(dashboard, tasks, sources, statusDashboard) {
    liveState.tasks = tasks || [];
    var integration = page('page-integration');
    if (!integration) return;
    var statusSummary = statusDashboard || dashboard;
    var rate = dashboard.total ? ((dashboard.success / dashboard.total) * 100).toFixed(1) + '%' : '—';
    setKpis(integration, [number(dashboard.taskTotal), number(dashboard.running), rate, number(dashboard.failed)]);
    renderIntegrationTrend(integration.querySelector('.integration-trend'), dashboard.trend || []);
    var donut = integration.querySelector('.int-donut');
    if (donut) donut.innerHTML = '<div><b>' + number(statusSummary.total) + '</b><span>任务总数</span></div>';
    var legend = integration.querySelector('.compact-legend');
    if (legend) legend.innerHTML = [['成功', statusSummary.success, 'success'], ['运行中', statusSummary.running, 'running'], ['失败', statusSummary.failed, 'failed'], ['待运行', Math.max(0, statusSummary.total - statusSummary.success - statusSummary.running - statusSummary.failed), 'pending']].map(function (item) { return '<div class="legend-item"><span><i class="dot" style="background:' + colors[item[2]] + '"></i>' + item[0] + '</span><b>' + number(item[1]) + '</b><small>' + (statusSummary.total ? ((item[1] / statusSummary.total) * 100).toFixed(1) : '0.0') + '%</small></div>'; }).join('');
    bindIntegrationStatusTooltip(integration.querySelector('.integration-status'), statusSummary);
    renderIntegrationRows(tasks);
    renderIntegrationPulse(tasks);
  }

  function renderOperations(operations) {
    var operation = page('page-operations');
    if (!operation) return;
    liveState.operationAlerts = operations.alerts || [];
    var tasks = operations.tasks;
    var rate = tasks.total ? ((tasks.success / tasks.total) * 100).toFixed(1) + '%' : '—';
    setKpis(operation, [number(tasks.total), number(tasks.running), rate, number(tasks.failed)]);
    var system = operation.querySelector('.sys-card');
    if (system) { text(system.querySelector('.online'), operations.schedulerAvailable ? '运行正常' : '服务不可用'); var services = system.querySelector('.services'); if (services) services.innerHTML = '<div class="service"><span class="dot"></span>DolphinScheduler <em>' + (operations.schedulerAvailable ? '正常' : '异常') + '</em></div>'; }
    replaceSvgChart(operation.querySelector('.chart-card'), 'live-operations-trend', trendOption(operations.trend || []));
    var donut = operation.querySelector('.ops-donut');
    if (donut) donut.innerHTML = '<div><b>' + number(tasks.total) + '</b><span>总实例数</span></div>';
    var legend = operation.querySelector('.donut-layout .legend-list');
    if (legend) legend.innerHTML = [['成功', tasks.success, 'success'], ['运行中', tasks.running, 'running'], ['失败', tasks.failed, 'failed'], ['待运行', tasks.pending, 'pending']].map(function (item) { return '<div class="legend-item"><span><i class="dot" style="background:' + colors[item[2]] + '"></i>' + item[0] + '</span><b>' + number(item[1]) + '</b><small>' + (tasks.total ? ((item[1] / tasks.total) * 100).toFixed(1) : '0.0') + '%</small></div>'; }).join('');
    var recentBody = operation.querySelector('.ops-bp .data-table tbody');
    if (recentBody) recentBody.innerHTML = operations.recentTasks.length ? operations.recentTasks.map(function (task) { return '<tr><td>' + escapeHtml(task.name) + '</td><td>' + escapeHtml(task.type) + '</td><td>' + when(task.startedAt) + '</td><td>' + escapeHtml(task.detail || '—') + '</td><td>' + stateTag(task.status) + '</td><td>—</td></tr>'; }).join('') : emptyRow(6, '暂无运行实例');
    var alertBox = operation.querySelectorAll('.ops-bp')[1];
    if (alertBox) {
      var heading = alertBox.querySelector('.chart-title');
      alertBox.querySelectorAll('.alert-item').forEach(function (item) { item.remove(); });
      alertBox.insertAdjacentHTML('beforeend', operations.alerts.length ? operations.alerts.map(function (item) { return '<div class="alert-item">🔺 <b>' + escapeHtml(item.name) + '</b><small>' + escapeHtml(item.type) + '　' + when(item.occurredAt) + '</small></div>'; }).join('') : '<div class="alert-item muted">暂无告警信息</div>');
    }
  }

  function renderAssets(summary, lineages) {
    var assets = page('page-assets');
    if (!assets) return;
    var assetLabels = assets.querySelectorAll('.kpi-label'); ['资产总数', '数据表', '元数据可见数据源', '已治理资产'].forEach(function (label, index) { if (assetLabels[index]) text(assetLabels[index], label); });
    setKpis(assets, [number(summary.total), number(summary.tables), number(summary.visibleCatalogs), number(summary.governed)]);
    var registered = summary.items || [];
    var all = registered.map(function (item) { return item.name; });
    if (!all.length) (lineages || []).forEach(function (item) { [item.sourceTable, item.targetTable].forEach(function (name) { if (name && all.indexOf(name) < 0) all.push(name); }); });
    var side = assets.querySelector('.side-list');
    if (side) side.innerHTML = '<div class="side-row on"><i class="ri-folder-fill ico-blue"></i>全部资产 <span class="num">' + number(all.length) + '</span></div>' + (all.length ? all.map(function (name) { return '<div class="side-row ind1"><i class="ri-table-line ico-blue"></i>' + escapeHtml(name) + '</div>'; }).join('') : '<div class="side-row muted">暂无已登记资产</div>');
    var body = assets.querySelector('.asset-main .data-table tbody');
    if (body) body.innerHTML = all.length ? all.map(function (name) { var item = registered.find(function (candidate) { return candidate.name === name; }) || {}; return '<tr><td class="link"><i class="ri-file-list-3-line ico-blue"></i>' + escapeHtml(name) + '</td><td>数据表</td><td><span class="tag run">' + escapeHtml(item.layer || '—') + '</span></td><td>—</td><td>—</td><td><span class="tag ' + (item.status === '待同步' ? 'purple' : 'ok') + '">● ' + escapeHtml(item.status || '已登记') + '</span></td><td>—</td></tr>'; }).join('') : emptyRow(7, '暂无资产血缘数据');
    var footer = assets.querySelector('.asset-footer > span'); if (footer) text(footer, '共 ' + number(all.length) + ' 条');
  }

  function renderWorkflow(workflows) {
    var workflow = page('page-workflow');
    if (!workflow) return;
    liveState.selectedWorkflow = workflows[0] || null;
    var workflowTitle = workflow.querySelector('.workflow-head strong'); if (workflowTitle) text(workflowTitle, workflows.length ? workflows[0].name : '暂无工作流');
    var workflowStatus = workflow.querySelector('.workflow-head .tag'); if (workflowStatus) { text(workflowStatus, workflows.length ? stateLabel(workflows[0].status) : '暂无数据'); workflowStatus.className = 'tag ' + (workflows.length && stateKind(workflows[0].status) === 'success' ? 'ok' : 'purple'); }
    var workflowSaved = workflow.querySelector('.workflow-head .muted'); if (workflowSaved) text(workflowSaved, workflows.length ? '数据来自平台元数据库' : '请先新建工作流');
    var canvas = workflow.querySelector('.wf-canvas');
    if (!canvas) return;
    var nodes = canvas.querySelectorAll('.wf-node');
    nodes.forEach(function (node) { node.remove(); });
    canvas.querySelectorAll('.wf-lines,.minimap').forEach(function (node) { node.remove(); });
    var area = document.createElement('div');
    area.style.cssText = 'display:flex;gap:12px;align-items:center;justify-content:center;height:calc(100% - 54px);padding:22px;flex-wrap:wrap;';
    area.innerHTML = workflows.length ? workflows.map(function (item) { return '<button class="wf-node" data-live-workflow="' + item.id + '"><span class="node-icon">WF</span><b>' + escapeHtml(item.name) + '</b><small>' + stateLabel(item.status) + '</small></button>'; }).join('') : '<div class="muted">暂无工作流，请通过“新建工作流”创建</div>';
    canvas.appendChild(area);
    var inspector = workflow.querySelector('.workflow-inspector');
    if (inspector) {
      var formbox = inspector.querySelector('.formbox');
      if (formbox) formbox.innerHTML = workflows.length ? '<h3 style="margin:4px 0">请选择画布中的工作流节点</h3><p class="muted">当前工作流：' + escapeHtml(workflows[0].name) + '</p><p class="muted">状态：' + escapeHtml(stateLabel(workflows[0].status)) + '</p>' : '<h3 style="margin:4px 0">暂无工作流</h3><p class="muted">请通过“新建工作流”创建真实流程。</p>';
    }
  }

  function developmentTabKey(file) { return file && (file.tabId || (file.id != null ? 'file-' + file.id : 'draft')); }
  function developmentEditorValue() {
    if (window.platformMonaco && window.platformMonaco.getEditor()) return window.platformMonaco.getValue();
    var code = page('page-development') && page('page-development').querySelector('.codeview');
    return code ? code.textContent || '' : '';
  }
  function setDevelopmentEditorValue(value, markDirty) {
    if (window.platformMonaco && window.platformMonaco.getEditor()) {
      window.platformMonaco.setValue(String(value || ''), !!markDirty);
      return;
    }
    var code = page('page-development') && page('page-development').querySelector('.codeview');
    if (code) code.textContent = String(value || '');
  }
  function syncDevelopmentEditor(markDirty) {
    var current = liveState.selectedFile;
    if (!current) return;
    current.content = developmentEditorValue();
    if (markDirty) current.dirty = true;
    var open = liveState.openFiles.find(function (item) { return developmentTabKey(item) === developmentTabKey(current); });
    if (open) { open.content = current.content; if (markDirty) open.dirty = true; }
    if (markDirty) renderDevelopmentTabs();
  }
  function renderDevelopmentTabs() {
    var development = page('page-development');
    var tabs = development && development.querySelector('.dev-tabs');
    if (!tabs) return;
    tabs.innerHTML = liveState.openFiles.map(function (item) {
      var key = developmentTabKey(item);
      var type = String(item.fileType || 'SQL').toUpperCase();
      var icon = type === 'PYTHON' ? 'ri-python-line ico-orange' : type === 'SHELL' ? 'ri-terminal-box-line ico-purple' : 'ri-file-code-line ico-blue';
      return '<div class="dev-tab ' + (key === liveState.activeTabId ? 'on' : '') + '" data-dev-tab="' + escapeHtml(key) + '" title="' + escapeHtml(item.name || '未命名文件') + '"><i class="' + icon + '"></i><span class="dev-tab-name">' + escapeHtml(item.name || '未命名文件') + '</span>' + (item.dirty ? '<span class="dev-tab-dirty" title="未保存修改">●</span>' : '') + '<button type="button" class="dev-tab-close" data-dev-tab-close="' + escapeHtml(key) + '" title="关闭窗口" aria-label="关闭窗口">×</button></div>';
    }).join('') + (String(liveState.developmentScope || 'mine') === 'favorites' ? '' : '<button type="button" class="dev-tab dev-tab-add" title="新建脚本窗口" aria-label="新建脚本窗口">＋</button>');
  }
  function renderDevelopmentEditor(file) {
    var development = page('page-development');
    if (!development) return;
    if (liveState.selectedFile && liveState.selectedFile !== file) syncDevelopmentEditor();
    if (liveState.developmentRunning) { liveState.developmentRunning = false; liveState.developmentRunToken += 1; setDevelopmentRunState(false, '尚未执行'); }
    liveState.selectedFile = file || null;
    if (file) {
      var key = developmentTabKey(file);
      var index = liveState.openFiles.findIndex(function (item) { return developmentTabKey(item) === key; });
      if (index < 0) liveState.openFiles.push(file); else liveState.openFiles[index] = Object.assign(liveState.openFiles[index], file);
      liveState.activeTabId = key;
    }
    var code = development.querySelector('.codeview');
    if (code) {
      if (!(window.platformMonaco && window.platformMonaco.getEditor())) code.innerHTML = file ? highlightDevelopmentCode(file.content || '', file.fileType) : '';
      var editorType = String(file && file.fileType || 'SQL').toUpperCase();
      code.dataset.placeholder = !file || file.content ? '' : editorType === 'PYTHON' ? '请输入 Python 代码…' : editorType === 'SHELL' ? '请输入 Shell 脚本…' : '请输入 SQL…';
      code.scrollTop = 0;
      var mountMonaco = function () {
        if (!window.platformMonaco || !code) return;
        if (liveState.completionItems) window.platformMonaco.setCompletionItems(liveState.completionItems);
        window.platformMonaco.mount(code, file ? file.content || '' : '', editorType, {
          onChange: function () { if (liveState.selectedFile === file) syncDevelopmentEditor(true); },
          onTableClick: function (token) { if (liveState.selectedFile === file) loadEditorTableToken(token); }
        });
      };
      if (window.platformMonaco) mountMonaco();
      else window.addEventListener('platform-monaco-ready', mountMonaco, { once: true });
    }
    var lines = development.querySelector('.linenos');
    if (lines) {
      var count = Math.max(1, String(file && file.content || '').split('\n').length);
      lines.textContent = Array.from({ length: count }, function (_, index) { return index + 1; }).join('\n');
    }
    renderDevelopmentTabs();
    var fieldInfo = development.querySelector('.dev-right .dev-field-info');
    if (fieldInfo) fieldInfo.innerHTML = '<h4>字段信息</h4><div class="muted">点击编辑器中的表名，即可查看对应字段。</div>';
    var tableSummary = development.querySelector('.dev-right .dev-table-summary');
    if (tableSummary) { tableSummary.hidden = true; tableSummary.innerHTML = ''; }
    renderDevelopmentVersionState(file);
    var result = development.querySelector('.result-table-wrap tbody');
    if (result) result.innerHTML = '';
    var resultHead = development.querySelector('.result-table-wrap thead');
    if (resultHead) resultHead.innerHTML = '';
    var resultStatus = development.querySelector('.result-status');
    if (resultStatus) { resultStatus.className = 'result-status'; }
    var resultStatusLabel = development.querySelector('.result-status-label');
    if (resultStatusLabel) resultStatusLabel.textContent = '尚未执行';
    var resultElapsed = development.querySelector('.result-elapsed');
    if (resultElapsed) resultElapsed.textContent = '';
    var resultCount = development.querySelector('.result-count');
    if (resultCount) resultCount.textContent = '';
    var resultRunningMeta = development.querySelector('.result-running-meta');
    if (resultRunningMeta) { resultRunningMeta.textContent = ''; resultRunningMeta.hidden = true; }
    var historyPanel = development.querySelector('.result-history-panel');
    var historyTab = development.querySelector('.result-history-tab');
    var outputTab = development.querySelector('.result-output-tab');
    var statusbar = development.querySelector('.result-statusbar');
    var resultTable = development.querySelector('.result-table-wrap');
    if (historyPanel) historyPanel.hidden = true;
    if (historyTab) historyTab.classList.remove('on');
    if (outputTab) outputTab.classList.add('on');
    if (statusbar) statusbar.hidden = false;
    if (resultTable) resultTable.hidden = false;
  }

  function highlightDevelopmentCode(source, fileType) {
    var safe = escapeHtml(String(source || ''));
    var type = String(fileType || 'SQL').toUpperCase();
    var commentPattern = type === 'SQL' ? /(--[^\n]*)/g : type === 'SHELL' ? /(#[^\n]*)/g : /(#[^\n]*)/g;
    safe = safe.replace(commentPattern, '<span class="cm">$1</span>');
    var keywords = type === 'SQL'
      ? '\\b(SELECT|FROM|WHERE|JOIN|LEFT|RIGHT|FULL|INNER|OUTER|ON|GROUP|BY|ORDER|HAVING|LIMIT|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|ALTER|DROP|TABLE|VIEW|AS|AND|OR|NOT|NULL|CASE|WHEN|THEN|ELSE|END|WITH|DISTINCT)\\b'
      : type === 'SHELL'
        ? '\\b(if|then|elif|else|fi|for|while|in|do|done|case|esac|function|export|source|echo|cd|mkdir|rm|cp|mv|cat|grep|awk|sed|curl)\\b'
        : '\\b(def|class|import|from|as|return|if|elif|else|for|while|in|try|except|finally|with|lambda|yield|True|False|None|and|or|not|print)\\b';
    safe = safe.replace(new RegExp(keywords, 'gi'), '<span class="kw">$1</span>');
    safe = safe.replace(/(&quot;[^&\n]*?&quot;|&#39;[^&#\n]*?&#39;|`[^`\n]*`)/g, '<span class="str">$1</span>');
    return safe;
  }

  function renderDevelopmentVersionState(file) {
    var development = page('page-development');
    if (!development) return;
    var devVersion = development.querySelector('.dev-version-development b');
    var onlineVersion = development.querySelector('.dev-version-online b');
    var owner = development.querySelector('.dev-version-owner');
    var updated = development.querySelector('.dev-version-updated');
    var warning = development.querySelector('.dev-version-warning');
    var publishedBanner = development.querySelector('.dev-version-published');
    var savedBanner = development.querySelector('.dev-version-saved');
    var publishButton = development.querySelector('.dev-publish-file');
    var saveToProject = development.querySelector('.dev-save-to-project');
    var project = file && liveState.projects.find(function (item) { return String(item.id) === String(file.projectId); });
    if (devVersion) devVersion.textContent = file && file.id && Number(file.currentVersion) > 0 ? 'V' + file.currentVersion : '未保存';
    if (onlineVersion) onlineVersion.textContent = '—';
    if (owner) owner.textContent = '负责人：' + ((project && project.ownerName) || liveState.currentUsername || '—');
    if (updated) updated.textContent = '最近修改：' + (file && file.id ? '刚刚' : '—');
    if (warning) warning.hidden = true;
    if (publishedBanner) publishedBanner.hidden = true;
    if (savedBanner) savedBanner.hidden = !(liveState.developmentSavedNotice && Date.now() - liveState.developmentSavedNotice < 3500);
    var isMine = String(liveState.developmentScope || 'mine') !== 'all';
    if (publishButton) publishButton.hidden = isMine || !file || !file.id;
    if (saveToProject) saveToProject.hidden = !isMine;
    if (!file || !file.id) return;
    request('/development/files/' + file.id + '/versions').then(function (versions) {
      if (!liveState.selectedFile || String(liveState.selectedFile.id) !== String(file.id)) return;
      var items = versions || [];
      var published = items.find(function (version) { return version.publishFlag === true; });
      var online = published ? Number(published.versionNo) : null;
      if (onlineVersion) onlineVersion.textContent = online ? 'V' + online : '—';
      var hasUnpublished = online != null && online !== Number(file.currentVersion);
      if (warning) { warning.hidden = !hasUnpublished; warning.querySelector('b').textContent = online ? 'V' + online : '—'; }
      if (publishedBanner) publishedBanner.hidden = !(String(file.status || '').toUpperCase() === 'PUBLISHED' && online != null && online === Number(file.currentVersion));
    }).catch(function () { if (onlineVersion) onlineVersion.textContent = '—'; });
  }

  function renderDevelopmentFileRow(projectId, file) {
    var icon = String(file.fileType || '').toUpperCase() === 'PYTHON' ? 'ri-python-line ico-orange' : String(file.fileType || '').toUpperCase() === 'SHELL' ? 'ri-terminal-box-line ico-purple' : 'ri-file-code-line ico-blue';
    var active = liveState.selectedFile && developmentTabKey(liveState.selectedFile) === developmentTabKey(file);
    var description = String(file.description || '').trim();
    var title = description ? description : '双击或右键管理文件';
    var code = String(file.status || '').toUpperCase();
    var state = code === 'PUBLISHED' ? { cls: 'published', label: '已发布' } : code === 'OFFLINE' ? { cls: 'offline', label: '已下线' } : Number(file.currentVersion || 0) > 1 ? { cls: 'changed', label: '有未发布变更' } : { cls: 'dev', label: '开发中' };
    return '<button class="side-row dev-file-row live-file ' + (active ? 'on' : '') + '" data-id="' + (file.id == null ? '' : file.id) + '" data-draft-id="' + escapeHtml(file.tabId || '') + '" data-project-id="' + projectId + '" data-folder-id="' + (file.folderId == null ? '' : file.folderId) + '"' + (description ? ' data-description="' + escapeHtml(description) + '"' : '') + ' title="' + escapeHtml(title) + '"><i class="' + icon + '"></i><span class="dev-resource-name">' + escapeHtml(file.name) + '</span><span class="dev-file-status ' + state.cls + '"><i></i>' + state.label + '</span></button>';
  }

  function activeDevelopmentSource() {
    var sourceSelect = document.getElementById('dev-development-source');
    var selectedId = sourceSelect && sourceSelect.value;
    return selectedId
      ? sourceCatalog().find(function (item) { return String(item.id) === String(selectedId); })
      : sourceCatalog().find(function (item) { return String(item.type).toUpperCase() === 'STARROCKS'; }) || sourceCatalog()[0];
  }

  function renderEditorTableFields(source, database, table, columns) {
    var development = page('page-development');
    if (!development) return;
    var fieldInfo = development.querySelector('.dev-right .dev-field-info');
    if (!fieldInfo) return;
    var tableSummary = development.querySelector('.dev-right .dev-table-summary');
    if (tableSummary) {
      tableSummary.hidden = false;
      var project = liveState.projects.find(function (item) { return String(item.id) === String(liveState.selectedFile && liveState.selectedFile.projectId); });
      tableSummary.innerHTML = '<h3><i class="ri-table-2-line ico-blue"></i> ' + escapeHtml(database + '.' + table) + '</h3><p class="muted">内部表</p><div class="kvgrid"><span>所属项目</span><b>' + escapeHtml((project && project.name) || '数仓') + '</b><span>负责人</span><b>' + escapeHtml((project && project.ownerName) || liveState.currentUsername || '—') + '</b><span>更新时间</span><span>刚刚</span></div>';
    }
    var rows = columns || [];
    fieldInfo.innerHTML = '<h4>字段信息</h4><p class="muted dev-table-caption">' + escapeHtml((database ? database + '.' : '') + table) + ' · ' + escapeHtml(source.name || '') + '</p>' + (rows.length
      ? '<table class="data-table dev-field-table"><thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead><tbody>' + rows.map(function (column) {
        return '<tr><td>' + escapeHtml(column.name || '—') + '</td><td>' + escapeHtml(column.dataType || '—') + '</td><td title="' + escapeHtml(column.comment || '') + '">' + escapeHtml(column.comment || '—') + '</td></tr>';
      }).join('') + '</tbody></table>'
      : '<div class="muted">该表暂无字段信息</div>');
  }

  function tableTokenAtCursor(code, event) {
    var range = null;
    if (document.caretRangeFromPoint) range = document.caretRangeFromPoint(event.clientX, event.clientY);
    else if (document.caretPositionFromPoint) {
      var position = document.caretPositionFromPoint(event.clientX, event.clientY);
      if (position) { range = document.createRange(); range.setStart(position.offsetNode, position.offset); }
    }
    if (!range || !code.contains(range.startContainer)) return '';
    var before = document.createRange();
    before.selectNodeContents(code);
    before.setEnd(range.startContainer, range.startOffset);
    var content = code.textContent || '';
    var index = before.toString().length;
    var left = index;
    var right = index;
    while (left > 0 && /[A-Za-z0-9_.$`]/.test(content.charAt(left - 1))) left -= 1;
    while (right < content.length && /[A-Za-z0-9_.$`]/.test(content.charAt(right))) right += 1;
    return content.slice(left, right).replace(/`/g, '').replace(/^\.+|\.+$/g, '');
  }

  function loadEditorTableFields(event, code) {
    if (!liveState.selectedFile || String(liveState.selectedFile.fileType || 'SQL').toUpperCase() !== 'SQL') return;
    var token = tableTokenAtCursor(code, event);
    return loadEditorTableToken(token);
  }

  function loadEditorTableToken(token) {
    if (!liveState.selectedFile || String(liveState.selectedFile.fileType || 'SQL').toUpperCase() !== 'SQL') return;
    if (!token || /^(select|from|where|join|left|right|inner|outer|on|group|order|by|limit|as|and|or|not)$/i.test(token)) return;
    if (!/^[A-Za-z_][A-Za-z0-9_$]*(\.[A-Za-z_][A-Za-z0-9_$]*)?$/.test(token)) return;
    var source = activeDevelopmentSource();
    if (!source) return;
    var parts = token.split('.').filter(Boolean);
    var table = parts.pop();
    var database = parts.pop() || source.databaseName;
    if (!table || !database) return;
    var fieldInfo = page('page-development').querySelector('.dev-right .dev-field-info');
    var tableSummary = page('page-development').querySelector('.dev-right .dev-table-summary');
    if (tableSummary) { tableSummary.hidden = false; tableSummary.innerHTML = '<h3><i class="ri-table-2-line ico-blue"></i> ' + escapeHtml(database + '.' + table) + '</h3><p class="muted">正在读取表信息…</p>'; }
    if (fieldInfo) fieldInfo.innerHTML = '<h4>字段信息</h4><div class="muted">正在读取 ' + escapeHtml(database + '.' + table) + ' 的字段…</div>';
    request('/metadata/columns?dataSourceId=' + encodeURIComponent(source.id) + '&database=' + encodeURIComponent(database) + '&table=' + encodeURIComponent(table))
      .then(function (columns) { renderEditorTableFields(source, database, table, columns); })
      .catch(function (error) { if (fieldInfo) fieldInfo.innerHTML = '<h4>字段信息</h4><div class="muted">' + escapeHtml(error.message || '读取字段信息失败') + '</div>'; });
  }

  function renderDevelopmentTree(projectId, folders, files) {
    var development = page('page-development');
    if (!development) return;
    var target = development.querySelector('[data-project-files="' + projectId + '"]');
    if (!target) return;
    var byParent = {};
    (folders || []).forEach(function (folder) {
      var key = folder.parentId == null ? 'root' : String(folder.parentId);
      (byParent[key] || (byParent[key] = [])).push(folder);
    });
    var filesByFolder = {};
    (files || []).filter(function (file) { return file && file.id != null; }).forEach(function (file) {
      var key = file.folderId == null ? 'root' : String(file.folderId);
      (filesByFolder[key] || (filesByFolder[key] = [])).push(file);
    });
    var renderBranch = function (parentId) {
      var key = parentId == null ? 'root' : String(parentId);
      var orderedFolders = (byParent[key] || []).slice().sort(function (a, b) {
        return String(a.createdAt || a.created_at || a.id).localeCompare(String(b.createdAt || b.created_at || b.id));
      });
      var html = orderedFolders.map(function (folder) {
        var open = liveState.folderOpen[folder.id] !== false;
        return '<div class="dev-folder-group"><button class="side-row dev-folder-row live-folder ' + (open ? 'open' : '') + '" data-id="' + folder.id + '" data-project-id="' + projectId + '" data-parent-id="' + (folder.parentId == null ? '' : folder.parentId) + '" title="单击展开/收起，双击或右键管理"><span class="folder-toggle">›</span><i class="ri-folder-fill ico-blue"></i><span class="dev-resource-name">' + escapeHtml(folder.name) + '</span><span class="folder-actions"><span class="folder-add" data-folder-add="' + folder.id + '" title="新建文件">＋</span><span class="folder-more">⋮</span></span></button><div class="dev-tree-children" data-folder-children="' + folder.id + '"' + (open ? '' : ' hidden') + '>' + renderBranch(folder.id) + '</div></div>';
      }).join('');
      html += (filesByFolder[key] || []).map(function (file) { return renderDevelopmentFileRow(projectId, file); }).join('');
      return html;
    };
    var content = renderBranch(null);
    target.innerHTML = content || '<div class="dev-empty">该项目暂无文件夹或文件</div>';
  }

  function loadDevelopmentFiles(projectId, preferredFileId, scopeAtRequest) {
    var requestedScope = scopeAtRequest || liveState.developmentScope || 'mine';
    var query = encodeURIComponent(projectId);
    return Promise.all([request('/development/files?projectId=' + query), request('/development/folders?projectId=' + query)]).then(function (result) {
      if (String(liveState.developmentScope || 'mine') !== String(requestedScope)) return [];
      liveState.selectedProjectId = Number(projectId);
      liveState.projectFiles = Array.isArray(result[0]) ? result[0] : [];
      liveState.folders = Array.isArray(result[1]) ? result[1] : [];
      liveState.folders.forEach(function (folder) { if (liveState.folderOpen[folder.id] === undefined) liveState.folderOpen[folder.id] = true; });
      renderDevelopmentTree(projectId, liveState.folders, liveState.projectFiles);
      var selected = preferredFileId == null || preferredFileId === '' ? null : liveState.projectFiles.find(function (file) { return String(file.id) === String(preferredFileId); });
      if (selected) renderDevelopmentEditor(selected);
      else {
        var current = liveState.selectedFile && String(liveState.selectedFile.projectId) === String(projectId) ? liveState.selectedFile : null;
        if (current) renderDevelopmentEditor(current);
        else createDevelopmentDraft('SQL', projectId, null, false);
      }
      return liveState.projectFiles;
    });
  }

  function isPersonalDevelopmentProject(project) {
    if (!project) return false;
    var name = String(project.name || '').trim();
    var description = String(project.description || '').trim();
    return name === '我的开发' || name === '默认开发空间' || description === '个人工作区';
  }

  function renderDevelopment(projects) {
    var scope = String(liveState.developmentScope || 'mine');
    liveState.projects = (projects || []).filter(function (project) {
      if (String(project && project.name || '').trim() === '默认开发空间') return false;
      return scope === 'all' ? !isPersonalDevelopmentProject(project) : isPersonalDevelopmentProject(project);
    });
    var development = page('page-development');
    if (!development) return;
    var list = development.querySelector('.dev-left .side-list');
    if (!list) return;
    var active = liveState.projects.find(function (project) { return project.id === liveState.selectedProjectId; }) || liveState.projects[0];
    var scopeTitle = scope === 'all' ? '项目空间' : scope === 'favorites' ? '我的收藏' : '我的开发（个人工作区）';
    var scopeNote = scope === 'all' ? '团队公共开发空间 · 多人共享 / 可发布 / 可调度' : scope === 'favorites' ? '快速访问个人或项目中的常用 SQL' : '仅自己可见 / 临时查询 / 不参与调度';
    var createFolderButton = development.querySelector('.dev-create-project');
    var createFileButton = development.querySelector('.dev-create-file');
    if (createFolderButton) { createFolderButton.hidden = scope === 'favorites'; createFolderButton.title = scope === 'all' ? '新建项目文件夹' : '新建个人文件夹'; }
    if (createFileButton) createFileButton.hidden = scope === 'favorites';
    if (scope === 'favorites') {
      list.innerHTML = '<div class="dev-scope-note"><strong>' + scopeTitle + '</strong>' + scopeNote + '</div><div class="dev-empty">暂无收藏脚本</div>';
      renderDevelopmentEditor(null);
      return;
    }
    if (active) {
      liveState.selectedProjectId = active.id;
      liveState.developmentProjectIds[scope] = active.id;
    } else {
      liveState.selectedProjectId = null;
    }
    var projectRoot = scope === 'all' && active ? '<div class="dev-project-root"><i class="ri-folder-3-fill"></i>' + escapeHtml(active.name || '数仓') + '</div>' : '';
    list.innerHTML = active ? '<div class="dev-scope-note"><strong>' + scopeTitle + '</strong>' + scopeNote + '</div>' + projectRoot + '<div class="dev-files" data-project-files="' + active.id + '"></div>' : '<div class="dev-scope-note"><strong>' + scopeTitle + '</strong>' + scopeNote + '</div><div class="dev-empty">暂无文件夹，请点击左上角“+”创建</div>';
    if (active) loadDevelopmentFiles(active.id, liveState.selectedFile && liveState.selectedFile.id, scope).catch(function (error) { notify(error.message, true); });
    else if (!liveState.openFiles.length) createDevelopmentDraft('SQL', null, null);
    else renderDevelopmentEditor(liveState.openFiles[0]);
  }

  function ensureDevelopmentContextMenu() {
    var menu = document.getElementById('dev-context-menu');
    if (menu) return menu;
    menu = document.createElement('div');
    menu.id = 'dev-context-menu';
    menu.className = 'dev-context-menu';
    menu.hidden = true;
    document.body.appendChild(menu);
    return menu;
  }

  function closeDevelopmentContextMenu() {
    var menu = document.getElementById('dev-context-menu');
    if (menu) menu.hidden = true;
  }

  function openDevelopmentContextMenu(kind, id, projectId, folderId, clientX, clientY) {
    var menu = ensureDevelopmentContextMenu();
    menu.dataset.kind = kind;
    menu.dataset.id = id || '';
    menu.dataset.projectId = projectId || '';
    menu.dataset.folderId = folderId || '';
    var actions = kind === 'file' ? [['rename', '重命名'], ['delete', '删除', 'danger']] : kind === 'folder' ? [['rename', '重命名'], ['move', '移动到'], ['delete', '删除', 'danger']] : kind === 'file-create' ? [['create-sql', 'SQL脚本'], ['create-python', 'Python文件'], ['create-shell', 'Shell脚本']] : [['new-file', '新建文件'], ['new-folder', '新建文件夹'], ['delete-project', '删除项目', 'danger']];
    menu.innerHTML = actions.map(function (item) { return '<button type="button" class="dev-context-action ' + (item[2] || '') + '" data-action="' + item[0] + '">' + item[1] + '</button>'; }).join('');
    menu.hidden = false;
    var left = Math.min(clientX, window.innerWidth - menu.offsetWidth - 8);
    var top = Math.min(clientY, window.innerHeight - menu.offsetHeight - 8);
    menu.style.left = Math.max(8, left) + 'px';
    menu.style.top = Math.max(8, top) + 'px';
  }

  function nextDevelopmentFolderName(projectId, parentFolderId, preferredName) {
    var baseName = String(preferredName || '新建文件夹').trim() || '新建文件夹';
    var used = {};
    (liveState.folders || []).forEach(function (folder) {
      if (String(folder.projectId) !== String(projectId)) return;
      var parent = folder.parentId == null ? null : String(folder.parentId);
      var target = parentFolderId == null ? null : String(parentFolderId);
      if (parent === target) used[String(folder.name || '').toLowerCase()] = true;
    });
    var index = 0;
    var candidate = baseName;
    while (used[candidate.toLowerCase()]) { index += 1; candidate = baseName + index; }
    return candidate;
  }

  function createDevelopmentFolder(parentFolderId, projectId, directName) {
    var scope = String(liveState.developmentScope || 'mine');
    if (scope === 'favorites') return notify('收藏页仅支持查看，不能新建文件夹', true);
    var scopedProjects = (liveState.projects || []).filter(function (project) { return scope === 'all' ? !isPersonalDevelopmentProject(project) : isPersonalDevelopmentProject(project); });
    var requestedProject = projectId || liveState.selectedProjectId;
    var requestedAllowed = requestedProject && scopedProjects.some(function (project) { return String(project.id) === String(requestedProject); });
    var targetProjectId = requestedAllowed ? requestedProject : (scopedProjects[0] && scopedProjects[0].id);
    var create = function (id) {
      if (!id) return notify('请先创建一个项目', true);
      var name = directName || window.prompt('文件夹名称', '新建文件夹');
      if (!name || !name.trim()) return;
      var targetParentId = parentFolderId == null ? null : Number(parentFolderId);
      var uniqueName = nextDevelopmentFolderName(id, targetParentId, name);
      request('/development/folders', { method: 'POST', body: JSON.stringify({ projectId: Number(id), parentId: targetParentId, name: uniqueName }) }).then(function (folder) {
        liveState.selectedProjectId = Number(id);
        liveState.folderOpen[folder.id] = true;
        notify('文件夹已创建');
        return loadDevelopmentFiles(id);
      }).catch(function (error) { notify(error.message, true); });
    };
    if (targetProjectId) return create(targetProjectId);
    var projectName = String(liveState.developmentScope || 'mine') === 'all' ? '数仓' : '我的开发';
    var projectDescription = String(liveState.developmentScope || 'mine') === 'all' ? '团队公共开发空间' : '个人工作区';
    request('/development/projects', { method: 'POST', body: JSON.stringify({ name: projectName, description: projectDescription }) }).then(function (project) {
      liveState.projects.push(project);
      liveState.selectedProjectId = project.id;
      return loadDevelopment().then(function () { create(project.id); });
    }).catch(function (error) { notify(error.message, true); });
  }

  function renameDevelopmentFolder(id) {
    var row = document.querySelector('.live-folder[data-id="' + id + '"]');
    if (row) startDevelopmentInlineRename(row, 'folder');
  }

  function deleteDevelopmentFolder(id) {
    var folder = liveState.folders.find(function (item) { return String(item.id) === String(id); });
    if (!window.confirm('确定删除文件夹“' + (folder ? folder.name : '') + '”吗？文件夹必须为空才能删除。')) return;
    request('/development/folders/' + id, { method: 'DELETE' }).then(function () { notify('文件夹已删除'); return loadDevelopmentFiles(liveState.selectedProjectId); }).catch(function (error) { notify(error.message, true); });
  }

  function moveDevelopmentFolder(id) {
    var folder = liveState.folders.find(function (item) { return String(item.id) === String(id); });
    if (!folder) return notify('文件夹不存在', true);
    var destination = window.prompt('移动到哪个文件夹？请输入目标文件夹名称（留空表示移动到根目录）', '');
    if (destination === null) return;
    var target = destination.trim() ? liveState.folders.find(function (item) { return item.id !== folder.id && item.name === destination.trim(); }) : null;
    if (destination.trim() && !target) return notify('未找到目标文件夹：' + destination.trim(), true);
    if (target && target.id === folder.parentId) return notify('文件夹已在目标位置');
    request('/development/folders/' + id, { method: 'PUT', body: JSON.stringify({ name: folder.name, parentId: target ? target.id : null, moveToRoot: !target }) }).then(function () { notify('文件夹已移动'); return loadDevelopmentFiles(liveState.selectedProjectId); }).catch(function (error) { notify(error.message, true); });
  }

  function renameDevelopmentFile(id) {
    var row = document.querySelector('.live-file[data-id="' + id + '"]');
    if (row) startDevelopmentInlineRename(row, 'file');
  }

  function startDevelopmentInlineRename(row, kind) {
    if (!row || row.querySelector('.dev-inline-name')) return;
    var label = row.querySelector('.dev-resource-name');
    if (!label) return;
    var original = label.textContent.trim();
    var input = document.createElement('input');
    input.className = 'dev-inline-name';
    input.value = original;
    input.setAttribute('aria-label', '重命名');
    label.replaceWith(input);
    input.focus(); input.select();
    var finished = false;
    var finish = function (save) {
      if (finished) return;
      finished = true;
      var next = input.value.trim();
      if (!save || !next || next === original) { input.replaceWith(label); return; }
      var id = row.dataset.id;
      var body;
      var promise;
      if (kind === 'folder') {
        body = { name: next };
        promise = request('/development/folders/' + id, { method: 'PUT', body: JSON.stringify(body) });
      } else {
        var file = liveState.projectFiles.find(function (item) { return String(item.id) === String(id); });
        body = { name: next, content: file ? file.content || '' : '' };
        promise = request('/development/files/' + id, { method: 'PUT', body: JSON.stringify(body) });
      }
      promise.then(function () { notify('已重命名'); return loadDevelopmentFiles(row.dataset.projectId, kind === 'file' ? id : null); }).catch(function (error) { input.replaceWith(label); notify(error.message, true); });
    };
    input.onkeydown = function (event) { if (event.key === 'Enter') { event.preventDefault(); finish(true); } else if (event.key === 'Escape') { event.preventDefault(); finish(false); } };
    input.onblur = function () { finish(true); };
  }

  function deleteDevelopmentFile(id) {
    var file = liveState.projectFiles.find(function (item) { return String(item.id) === String(id); });
    if (!window.confirm('确定删除文件“' + (file ? file.name : '') + '”吗？')) return;
    request('/development/files/' + id, { method: 'DELETE' }).then(function () { notify('文件已删除'); return loadDevelopmentFiles(liveState.selectedProjectId); }).catch(function (error) { notify(error.message, true); });
  }

  function ensureAlertDialog() {
    var host = document.getElementById('alert-dialog');
    if (host) return host;
    host = document.createElement('div');
    host.id = 'alert-dialog';
    host.className = 'sql-detail-dialog';
    host.innerHTML = '<section class="sql-detail-card" role="dialog" aria-modal="true" aria-label="告警信息"><div class="sql-detail-head"><h3>告警信息</h3><button type="button" class="sql-detail-close" aria-label="关闭">×</button></div><div class="alert-dialog-body"></div></section>';
    document.body.appendChild(host);
    host.querySelector('.sql-detail-close').onclick = function () { host.classList.remove('open'); };
    host.onclick = function (event) { if (event.target === host) host.classList.remove('open'); };
    return host;
  }
  function openAlertDialog(items) {
    var host = ensureAlertDialog();
    var body = host.querySelector('.alert-dialog-body');
    body.innerHTML = items && items.length ? '<div style="padding:6px 18px 18px">' + items.map(function (item) { return '<div class="alert-item">🔺 <b>' + escapeHtml(item.name || '异常任务') + '</b><small>' + escapeHtml(item.type || '任务') + '　' + when(item.startedAt || item.occurredAt) + (item.detail ? '　' + escapeHtml(item.detail) : '') + '</small></div>'; }).join('') + '</div>' : '<div class="muted" style="padding:42px;text-align:center">暂无失败任务</div>';
    host.classList.add('open');
  }

  function bindActions() {
    document.querySelectorAll('[data-a]').forEach(function (button) { button.onclick = null; });
    document.querySelectorAll('.tm,.star').forEach(function (button) { button.replaceWith(button.cloneNode(true)); });
    var notification = document.querySelector('[data-a="通知"]');
    if (notification) notification.onclick = function () { request('/operations/failed-tasks').then(openAlertDialog).catch(function (error) { notify(error.message, true); }); };
    var help = document.querySelector('[data-a="帮助中心"]');
    if (help) help.onclick = function () { notify('帮助内容请联系平台管理员获取'); };
    document.querySelectorAll('#page-operations .ops-bp .chart-title .link').forEach(function (button) { button.onclick = function () { openAlertDialog(liveState.operationAlerts || []); }; });
    document.querySelectorAll('.avatar').forEach(function (avatar) {
      avatar.onclick = openPasswordDialog;
      avatar.onkeydown = function (event) { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openPasswordDialog(); } };
    });
    var allTasks = document.querySelector('[data-a="全部任务"]'); if (allTasks) allTasks.onclick = function () { showPage('operations'); };
    var allFavorites = document.querySelector('[data-a="全部收藏"]'); if (allFavorites) allFavorites.onclick = function () { showPage('workflow'); };
    var searchInput = document.querySelector('#search input');
    if (searchInput) { searchInput.removeAttribute('readonly'); searchInput.addEventListener('input', function () { var query = searchInput.value.trim().toLowerCase(); var results = page('modal') && page('modal').querySelector('.results'); if (!results) return; var items = (liveState.overview ? liveState.overview.recentTasks : []).filter(function (item) { return !query || String(item.name).toLowerCase().indexOf(query) >= 0 || String(item.type).toLowerCase().indexOf(query) >= 0; }); results.innerHTML = items.length ? items.map(function (item) { return '<div class="result" data-live-task="' + escapeHtml(item.id) + '"><i class="ri-file-code-line"></i>' + escapeHtml(item.name) + ' · ' + escapeHtml(stateLabel(item.status)) + '</div>'; }).join('') : '<div class="result muted">没有匹配的真实任务</div>'; }); }
    var range = document.getElementById('range'); if (range) range.onchange = function () { if (liveState.overview) renderOverviewChart(liveState.overview); else clearChart('trend'); };
    var sourceSearch = document.querySelector('#page-source .source-table-search');
    if (sourceSearch) {
      sourceSearch.removeAttribute('readonly');
      sourceSearch.addEventListener('input', function () { filterSourceList(sourceSearch.value); });
    }
    document.querySelectorAll('#page-source .filter-row select').forEach(function (select) { select.onchange = function () { filterSourceList(sourceSearch ? sourceSearch.value : ''); }; });
    document.querySelectorAll('.tabs .tab').forEach(function (button) {
      button.onclick = function () {
        document.querySelectorAll('.tabs .tab').forEach(function (item) { item.classList.remove('on'); });
        button.classList.add('on');
        var overview = liveState.overview;
        var subtitle = page('subtitle');
        var messages = ['数据来自平台数据库与调度服务。', overview ? ('你关注的 ' + overview.favorites.length + ' 项真实资产。') : '暂无概览数据。', overview ? ('近期实例 ' + overview.recentTasks.length + ' 条。') : '暂无任务数据。', overview ? ('调度服务：' + (overview.platform.status === 'UP' ? '运行正常' : '不可用') + '。') : '暂无系统状态。'];
        text(subtitle, messages[Array.prototype.indexOf.call(document.querySelectorAll('.tabs .tab'), button)] || messages[0]);
      };
    });
    document.querySelectorAll('.quick .qb').forEach(function (button) {
      var label = button.textContent.trim();
      button.onclick = label === '新建工作流' ? createWorkflow : label === '数据源管理' ? function () { showPage('settings'); window.setTimeout(function () { if (window.settingsLiveOpenTab) window.settingsLiveOpenTab('sources'); }, 0); } : label === '系统设置' ? function () { showPage('settings'); } : label === '数据集成任务' ? function () { showPage('integration'); } : label === '新建 SQL' ? function () { createDevelopmentFile('SQL'); } : label === '新建 Python' ? function () { createDevelopmentFile('PYTHON'); } : function () { notify('该设置尚未开放，请联系平台管理员'); };
    });
    document.querySelectorAll('.support .sb').forEach(function (button) { button.onclick = function () { notify('帮助内容请联系平台管理员获取'); }; });
    document.querySelectorAll('#page-source .quickbox').forEach(function (button) {
      var label = button.textContent.trim();
      if (label === '新建数据源') return;
      button.onclick = label === '连接测试' ? testAllSources : viewSourceMetadata;
    });
    var integrationActions = document.querySelectorAll('#page-integration .module-actions .mbtn');
    if (integrationActions[0]) integrationActions[0].onclick = createIntegrationTask;
    if (integrationActions[1]) integrationActions[1].onclick = batchRunTasks;
    if (integrationActions[2]) integrationActions[2].onclick = function () { showPage('operations'); };
    var integrationSearch = document.querySelector('#page-integration .integration-table .searching');
    if (integrationSearch) integrationSearch.oninput = function () { var query = integrationSearch.value.trim().toLowerCase(); renderIntegrationRows(liveState.tasks.filter(function (task) { return !query || [task.name, task.sourceType, task.targetType, task.syncMode, task.status].some(function (value) { return String(value || '').toLowerCase().indexOf(query) >= 0; }); })); };
    var integrationFilters = document.querySelectorAll('#page-integration .integration-table .filter-row select'); integrationFilters.forEach(function (node) { node.style.display = 'none'; });
    var integrationRefresh = document.querySelector('#page-integration .integration-table .filter-row .mbtn.sm'); if (integrationRefresh) integrationRefresh.onclick = function () { loadIntegration().catch(function (error) { notify(error.message, true); }); };
    ['integrationTrendRange', 'integrationStatusRange'].forEach(function (id) { var range = document.getElementById(id); if (range) range.onchange = function () { loadIntegration().catch(function (error) { notify(error.message, true); }); }; });
    document.querySelectorAll('#page-development .dev-toolbar .mbtn').forEach(function (button) {
      var label = button.textContent.trim();
      if (label.indexOf('运行') >= 0) button.onclick = runCurrentFile;
      else if (button.classList.contains('dev-publish-file')) button.onclick = publishDevelopmentFile;
      else if (button.classList.contains('dev-save-to-project')) button.onclick = openDevelopmentSaveToProjectModal;
      else if (label.indexOf('保存') >= 0) button.onclick = saveCurrentFile;
      else if (label.indexOf('格式化') >= 0) button.onclick = formatDevelopmentFile;
    });
    var fullscreenButton = document.querySelector('#page-development .dev-fullscreen');
    if (fullscreenButton) fullscreenButton.onclick = function () {
      var development = page('page-development');
      if (!development) return;
      var enabled = development.classList.toggle('dev-fullscreen-mode');
      document.body.classList.toggle('dev-fullscreen-open', enabled);
      fullscreenButton.title = enabled ? '退出全屏' : '全屏展示数据开发';
      fullscreenButton.setAttribute('aria-label', fullscreenButton.title);
      fullscreenButton.innerHTML = '<i class="' + (enabled ? 'ri-fullscreen-exit-line' : 'ri-fullscreen-line') + '"></i>';
    };
    document.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape') return;
      var development = page('page-development');
      var publishDialog = document.getElementById('dev-publish-dialog');
      if (publishDialog && publishDialog.classList.contains('open')) { publishDialog.classList.remove('open'); publishDialog.setAttribute('aria-hidden', 'true'); return; }
      var saveNameModal = document.getElementById('dev-save-name-modal');
      if (saveNameModal && saveNameModal.classList.contains('open')) { closeDevelopmentSaveNameModal(); return; }
      var projectSaveModal = document.getElementById('dev-project-save-modal');
      if (projectSaveModal && projectSaveModal.classList.contains('open')) { closeDevelopmentSaveToProjectModal(); return; }
      if (!development || !development.classList.contains('dev-fullscreen-mode')) return;
      development.classList.remove('dev-fullscreen-mode');
      document.body.classList.remove('dev-fullscreen-open');
      if (fullscreenButton) { fullscreenButton.title = '全屏展示数据开发'; fullscreenButton.setAttribute('aria-label', fullscreenButton.title); }
      if (fullscreenButton) fullscreenButton.innerHTML = '<i class="ri-fullscreen-line"></i>';
    });
    var historyTab = document.querySelector('#page-development .result-history-tab');
    if (historyTab) historyTab.onclick = function () { toggleDevelopmentHistory(!historyTab.classList.contains('on')); };
    var outputTab = document.querySelector('#page-development .result-output-tab');
    if (outputTab) outputTab.onclick = function () { toggleDevelopmentHistory(false); };
    var historySearch = document.querySelector('#page-development .result-history-search');
    if (historySearch) historySearch.oninput = function () { renderDevelopmentHistory(historySearch.value); };
    var historyRefresh = document.querySelector('#page-development .result-history-refresh');
    if (historyRefresh) historyRefresh.onclick = function () { loadDevelopmentHistory().catch(function (error) { notify(error.message, true); }); };
    var resultExport = document.querySelector('#page-development .result-export');
    if (resultExport) resultExport.onclick = exportDevelopmentResult;
    var resultFullscreen = document.querySelector('#page-development .result-fullscreen');
    if (resultFullscreen) resultFullscreen.onclick = function () { toggleResultFullscreen(); };
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && document.querySelector('#page-development .result-shell.result-fullscreen-mode')) toggleResultFullscreen(false);
    });
    var developmentCode = document.querySelector('#page-development .codeview');
    if (developmentCode && developmentCode.getAttribute('contenteditable') === 'true') developmentCode.oninput = function () {
      syncDevelopmentEditor(true);
      var lines = page('page-development').querySelector('.linenos');
      if (lines) { var count = Math.max(1, String(developmentCode.textContent || '').split('\n').length); lines.textContent = Array.from({ length: count }, function (_, index) { return index + 1; }).join('\n'); }
    };
    document.querySelectorAll('.dev-create-project').forEach(function (button) { button.onclick = function () { createDevelopmentFolder(null, liveState.selectedProjectId, '新建文件夹'); }; });
    document.querySelectorAll('.dev-create-file').forEach(function (button) { button.onclick = function () { createDevelopmentDraft('SQL'); }; });
    document.querySelectorAll('.dev-refresh').forEach(function (button) { button.onclick = function () { loadDevelopment().catch(function (error) { notify(error.message, true); }); }; });
    document.querySelectorAll('.dev-segment').forEach(function (button) { button.onclick = function () { document.querySelectorAll('.dev-segment').forEach(function (item) { item.classList.toggle('on', item === button); }); liveState.developmentScope = button.dataset.devScope || (button.textContent.trim() === '项目空间' ? 'all' : 'mine'); liveState.selectedProjectId = liveState.developmentProjectIds[liveState.developmentScope] || null; liveState.selectedFile = null; liveState.openFiles = []; liveState.activeTabId = null; loadDevelopment(liveState.developmentScope).catch(function (error) { notify(error.message, true); }); }; });
    var drawer = document.getElementById('dev-file-drawer');
    var drawerForm = document.getElementById('dev-file-form');
    if (drawer) {
      drawer.querySelector('.dev-drawer-close').onclick = closeDevelopmentDrawer;
      drawer.querySelector('.dev-drawer-cancel').onclick = closeDevelopmentDrawer;
      drawer.onclick = function (event) { if (event.target === drawer) closeDevelopmentDrawer(); };
    }
    if (drawerForm) drawerForm.onsubmit = function (event) { event.preventDefault(); submitDevelopmentDrawer(); };
    var saveNameModal = document.getElementById('dev-save-name-modal');
    var saveNameForm = document.getElementById('dev-save-name-form');
    if (saveNameModal) {
      var saveNameClose = saveNameModal.querySelector('.dev-save-name-close');
      var saveNameCancel = saveNameModal.querySelector('.dev-save-name-cancel');
      if (saveNameClose) saveNameClose.onclick = closeDevelopmentSaveNameModal;
      if (saveNameCancel) saveNameCancel.onclick = closeDevelopmentSaveNameModal;
      saveNameModal.onclick = function (event) { if (event.target === saveNameModal) closeDevelopmentSaveNameModal(); };
    }
    if (saveNameForm) saveNameForm.onsubmit = function (event) { event.preventDefault(); submitDevelopmentSaveName(); };
    var projectSaveModal = document.getElementById('dev-project-save-modal');
    var projectSaveForm = document.getElementById('dev-project-save-form');
    if (projectSaveModal) {
      var projectSaveClose = projectSaveModal.querySelector('.dev-project-save-close');
      var projectSaveCancel = projectSaveModal.querySelector('.dev-project-save-cancel');
      if (projectSaveClose) projectSaveClose.onclick = closeDevelopmentSaveToProjectModal;
      if (projectSaveCancel) projectSaveCancel.onclick = closeDevelopmentSaveToProjectModal;
      projectSaveModal.onclick = function (event) { if (event.target === projectSaveModal) closeDevelopmentSaveToProjectModal(); };
    }
    if (projectSaveForm) projectSaveForm.onsubmit = function (event) { event.preventDefault(); submitDevelopmentSaveToProject(); };
    bindDevelopmentDividers();
    document.querySelectorAll('#page-operations .quickbox').forEach(function (button) {
      var label = button.textContent.trim();
      button.onclick = label === '重跑实例' ? function () { operateInstance('rerun'); } : label === '终止实例' ? function () { operateInstance('stop'); } : label === '查看日志' ? viewTaskLog : label === '实例搜索' ? function () { var id = window.prompt('请输入实例或任务 ID'); if (id) viewTaskLog(id); } : function () { notify('该运维配置请在 DolphinScheduler 控制台完成'); };
    });
    document.querySelectorAll('#page-assets .asset-detail .quickbox').forEach(function (button) { button.onclick = function () { notify('当前资产暂无可执行操作'); }; });
    document.querySelectorAll('#page-workflow .workflow-head .mbtn').forEach(function (button) {
      var label = button.textContent.trim();
      if (label.indexOf('保存') >= 0) button.onclick = saveWorkflow;
      else if (label.indexOf('运行') >= 0) button.onclick = runWorkflow;
      else if (label.indexOf('发布') >= 0) button.onclick = publishWorkflow;
    });
    var newAsset = document.querySelector('#page-assets .filter-row .mbtn.primary');
    if (newAsset) newAsset.onclick = function () { notify('资产由已登记数据源和集成任务自动生成'); };
    document.addEventListener('change', function (event) {
      var sourceCheckbox = event.target.closest('.live-select-source');
      var selectAllSources = event.target.closest('.live-select-all-sources');
      var sourcePageSize = event.target.closest('.source-page-size');
      if (sourceCheckbox) {
        if (sourceCheckbox.checked) liveState.selectedSourceIds[sourceCheckbox.dataset.id] = true;
        else delete liveState.selectedSourceIds[sourceCheckbox.dataset.id];
        renderSources(sourceSummary(liveState.sourceViewItems), false);
      }
      if (selectAllSources) {
        var offset = (liveState.sourcePage - 1) * liveState.sourcePageSize;
        liveState.sourceViewItems.slice(offset, offset + liveState.sourcePageSize).forEach(function (item) {
          var id = String(item.id);
          if (selectAllSources.checked) liveState.selectedSourceIds[id] = true;
          else delete liveState.selectedSourceIds[id];
        });
        renderSources(sourceSummary(liveState.sourceViewItems), false);
      }
      if (sourcePageSize) {
        liveState.sourcePageSize = [10, 20, 30].indexOf(Number(sourcePageSize.value)) >= 0 ? Number(sourcePageSize.value) : 10;
        liveState.sourcePage = 1;
        renderSources(sourceSummary(liveState.sourceViewItems), false);
      }
    });
    document.addEventListener('contextmenu', function (event) {
      var folder = event.target.closest('.live-folder');
      var file = event.target.closest('.live-file');
      var project = event.target.closest('.live-project');
      if (!folder && !file && !project) return;
      event.preventDefault();
      if (folder) openDevelopmentContextMenu('folder', folder.dataset.id, folder.dataset.projectId, folder.dataset.id, event.clientX, event.clientY);
      else if (file) openDevelopmentContextMenu('file', file.dataset.id, file.dataset.projectId, file.dataset.folderId, event.clientX, event.clientY);
      else openDevelopmentContextMenu('project', project.dataset.id, project.dataset.id, '', event.clientX, event.clientY);
    });
    document.addEventListener('dblclick', function (event) {
      var folder = event.target.closest('.live-folder');
      var file = event.target.closest('.live-file');
      if (folder) { event.preventDefault(); event.stopPropagation(); startDevelopmentInlineRename(folder, 'folder'); }
      else if (file) { event.preventDefault(); event.stopPropagation(); startDevelopmentInlineRename(file, 'file'); }
    });
    document.addEventListener('click', function (event) {
      var historyDetail = event.target.closest('[data-history-detail]');
      var historyCopy = event.target.closest('[data-history-copy]');
      if (historyDetail) { showSqlDetail(Number(historyDetail.dataset.historyDetail)); return; }
      if (historyCopy) { var history = liveState.executionHistory[Number(historyCopy.dataset.historyCopy)]; if (history) copySql(history.sql || ''); return; }
      var contextAction = event.target.closest('.dev-context-action');
      var contextMenu = event.target.closest('#dev-context-menu');
      if (contextAction) {
        var menu = document.getElementById('dev-context-menu');
        var action = contextAction.dataset.action;
        var id = menu && menu.dataset.id;
        var projectId = menu && menu.dataset.projectId;
        var folderId = menu && menu.dataset.folderId;
        closeDevelopmentContextMenu();
        if (action === 'create-sql') createDevelopmentFile('SQL', projectId, folderId || null);
        else if (action === 'create-python') createDevelopmentFile('PYTHON', projectId, folderId || null);
        else if (action === 'create-shell') createDevelopmentFile('SHELL', projectId, folderId || null);
        else if (action === 'new-file') createDevelopmentFile('SQL', projectId, folderId || null);
        else if (action === 'new-folder') createDevelopmentFolder(menu && menu.dataset.kind === 'folder' ? id : null, projectId);
        else if (action === 'rename') {
          if (menu && menu.dataset.kind === 'file') renameDevelopmentFile(id); else renameDevelopmentFolder(id);
        } else if (action === 'move') {
          moveDevelopmentFolder(id);
        } else if (action === 'delete') {
          if (menu && menu.dataset.kind === 'file') deleteDevelopmentFile(id); else deleteDevelopmentFolder(id);
        } else if (action === 'delete-project' && window.confirm('确定删除这个项目吗？项目必须为空才能删除。')) {
          request('/development/projects/' + id, { method: 'DELETE' }).then(function () { notify('项目已删除'); liveState.selectedProjectId = null; liveState.selectedFile = null; return loadDevelopment(); }).catch(function (error) { notify(error.message, true); });
        }
        return;
      }
      if (!contextMenu) closeDevelopmentContextMenu();
      var test = event.target.closest('.live-test-source');
      var toggle = event.target.closest('.live-toggle-source');
      var edit = event.target.closest('.live-edit-source');
      var remove = event.target.closest('.live-delete-source');
      var sourcePageButton = event.target.closest('.live-source-page');
      var bulkDelete = event.target.closest('.live-bulk-delete-sources');
      var run = event.target.closest('.live-run-task');
      var project = event.target.closest('.live-project');
      var folder = event.target.closest('.live-folder');
      var folderAdd = event.target.closest('[data-folder-add]');
      var folderMore = event.target.closest('.folder-more');
      var tabAdd = event.target.closest('.dev-tab-add');
      var tabClose = event.target.closest('[data-dev-tab-close]');
      var tab = event.target.closest('[data-dev-tab]');
      var file = event.target.closest('.live-file');
      var deleteFile = event.target.closest('.live-delete-file');
      var deleteProject = event.target.closest('.dev-project-delete');
      if (tabAdd) {
        createDevelopmentDraft('SQL');
        return;
      }
      if (tabClose) {
        event.stopPropagation();
        var closingKey = tabClose.dataset.devTabClose;
        var closingIndex = liveState.openFiles.findIndex(function (item) { return developmentTabKey(item) === closingKey; });
        if (closingIndex >= 0) liveState.openFiles.splice(closingIndex, 1);
        if (liveState.activeTabId === closingKey) {
          var next = liveState.openFiles[Math.max(0, closingIndex - 1)] || liveState.openFiles[0] || null;
          liveState.activeTabId = next ? developmentTabKey(next) : null;
          renderDevelopmentEditor(next);
        } else renderDevelopmentTabs();
        return;
      }
      if (tab) {
        var targetTab = liveState.openFiles.find(function (item) { return developmentTabKey(item) === tab.dataset.devTab; });
        if (targetTab) renderDevelopmentEditor(targetTab);
        return;
      }
      if (folderAdd) {
        event.stopPropagation();
        var parentFolder = folderAdd.closest('.live-folder');
        if (parentFolder) {
          var addRect = folderAdd.getBoundingClientRect();
          openDevelopmentContextMenu('file-create', folderAdd.dataset.folderAdd, parentFolder.dataset.projectId, folderAdd.dataset.folderAdd, addRect.left, addRect.bottom + 4);
        }
        return;
      }
      if (folderMore) {
        event.stopPropagation();
        var menuFolder = folderMore.closest('.live-folder');
        if (menuFolder) {
          var menuRect = folderMore.getBoundingClientRect();
          openDevelopmentContextMenu('folder', menuFolder.dataset.id, menuFolder.dataset.projectId, menuFolder.dataset.id, menuRect.left, menuRect.bottom + 4);
        }
        return;
      }
      if (folder) {
        liveState.folderOpen[folder.dataset.id] = liveState.folderOpen[folder.dataset.id] === false;
        renderDevelopmentTree(folder.dataset.projectId, liveState.folders, liveState.projectFiles);
        return;
      }
      if (sourcePageButton && !sourcePageButton.disabled) {
        liveState.sourcePage = Number(sourcePageButton.dataset.page) || 1;
        renderSources(sourceSummary(liveState.sourceViewItems), false);
        return;
      }
      if (bulkDelete) {
        var sourceIds = Object.keys(liveState.selectedSourceIds);
        var selectedNames = sourceCatalog().filter(function (item) { return liveState.selectedSourceIds[String(item.id)]; }).map(function (item) { return item.name; });
        if (!sourceIds.length) return;
        if (window.confirm('确定删除选中的 ' + sourceIds.length + ' 个数据源吗？\n' + selectedNames.join('、'))) {
          Promise.all(sourceIds.map(function (id) { return request('/data-sources/' + id, { method: 'DELETE' }); })).then(function () {
            liveState.selectedSourceIds = {};
            notify('已删除 ' + sourceIds.length + ' 个数据源');
            return Promise.all([loadSources(), loadOverview(), loadIntegration()]);
          }).catch(function (error) { notify(error.message, true); });
        }
        return;
      }
      if (test) {
        request('/data-sources/' + test.dataset.id + '/test').then(function (result) {
          return request('/data-sources/' + test.dataset.id).then(function (source) {
            notify(result.message || '连接检测完成');
            window.dispatchEvent(new CustomEvent('platform:data-source-health-changed', { detail: source }));
            return loadSources();
          });
        }).catch(function (error) { notify(error.message, true); });
      }
      if (toggle) { request('/data-sources/' + toggle.dataset.id + '/metadata-visibility', { method: 'PUT', body: JSON.stringify({ visible: toggle.dataset.visible !== 'true' }) }).then(function () { notify('元数据可见性已更新'); loadSources(); }).catch(function (error) { notify(error.message, true); }); }
      if (edit) { var sourceItem = sourceCatalog().find(function (item) { return String(item.id) === edit.dataset.id; }); if (sourceItem) window.dispatchEvent(new CustomEvent('platform:edit-data-source', { detail: sourceItem })); }
      if (remove) { var removing = sourceCatalog().find(function (item) { return String(item.id) === remove.dataset.id; }); if (removing && window.confirm('确定删除数据源“' + removing.name + '”吗？')) request('/data-sources/' + remove.dataset.id, { method: 'DELETE' }).then(function () { notify('数据源已删除'); loadSources(); loadOverview(); loadIntegration(); }).catch(function (error) { notify(error.message, true); }); }
      if (run) { request('/integration/tasks/' + run.dataset.id + '/run', { method: 'POST' }).then(function () { notify('同步任务已提交'); loadIntegration(); loadOverview(); }).catch(function (error) { notify(error.message, true); }); }
      if (deleteFile) {
        event.stopPropagation();
        if (window.confirm('确定删除这个文件吗？')) request('/development/files/' + deleteFile.dataset.id, { method: 'DELETE' }).then(function () { notify('文件已删除'); return loadDevelopmentFiles(liveState.selectedProjectId); }).catch(function (error) { notify(error.message, true); });
        return;
      }
      if (deleteProject) {
        event.stopPropagation();
        if (window.confirm('确定删除这个项目吗？项目必须为空才能删除。')) request('/development/projects/' + deleteProject.dataset.id, { method: 'DELETE' }).then(function () { notify('项目已删除'); liveState.selectedProjectId = null; liveState.selectedFile = null; return loadDevelopment(); }).catch(function (error) { notify(error.message, true); });
        return;
      }
      if (file) {
        var draftKey = file.dataset.draftId;
        if (draftKey) {
          var draft = liveState.projectFiles.find(function (item) { return item.tabId === draftKey; }) || liveState.openFiles.find(function (item) { return item.tabId === draftKey; });
          if (draft) renderDevelopmentEditor(draft);
        } else loadDevelopmentFiles(file.dataset.projectId, file.dataset.id).catch(function (error) { notify(error.message, true); });
        return;
      }
      if (project) { loadDevelopmentFiles(project.dataset.id).catch(function (error) { notify(error.message, true); }); }
      var liveTask = event.target.closest('[data-live-task]');
      if (liveTask) viewTaskLog(liveTask.dataset.liveTask);
      var workflowNode = event.target.closest('[data-live-workflow]');
      if (workflowNode) request('/workflows/' + workflowNode.dataset.liveWorkflow).then(function (item) { liveState.selectedWorkflow = item; notify('已加载工作流：' + item.name); }).catch(function (error) { notify(error.message, true); });
    });
  }

  function showPage(name) {
    if (name === 'settings' && !liveState.isAdmin) { notify('系统设置仅管理员可用', true); name = 'workbench'; }
    if (name !== 'development') { var devPage = page('page-development'); if (devPage) devPage.classList.remove('dev-fullscreen-mode'); document.body.classList.remove('dev-fullscreen-open'); }
    var button = document.querySelector('.nav button[data-page="' + name + '"]');
    if (button && button.style.display !== 'none') button.click();
  }
  function createWorkflow() { var name = window.prompt('工作流名称'); if (!name) return; request('/workflows', { method: 'POST', body: JSON.stringify({ name: name, description: '', nodes: [{ name: 'SQL 任务', nodeType: 'SQL', fileVersionId: null, configJson: '{}', x: 180, y: 120, nodeCode: 'sql_' + Date.now() }], edges: [] }) }).then(function () { notify('工作流已创建'); loadWorkflow(); }).catch(function (error) { notify(error.message, true); }); }
  function closeDevelopmentDrawer() {
    var drawer = document.getElementById('dev-file-drawer');
    if (drawer) { drawer.classList.remove('open'); drawer.setAttribute('aria-hidden', 'true'); delete drawer.dataset.projectId; delete drawer.dataset.folderId; delete drawer.dataset.mode; }
  }
  function openDevelopmentDrawer(fileType, projectId, folderId) {
    var drawer = document.getElementById('dev-file-drawer');
    var form = document.getElementById('dev-file-form');
    if (!drawer || !form) return;
    form.reset();
    drawer.dataset.mode = 'create';
    drawer.dataset.projectId = projectId || liveState.selectedProjectId || '';
    drawer.dataset.folderId = folderId || '';
    var projectSelect = document.getElementById('dev-file-project');
    var newProject = document.getElementById('dev-file-new-project');
    var type = document.getElementById('dev-file-type');
    var content = document.getElementById('dev-file-content');
    var description = document.getElementById('dev-file-description');
    var error = document.getElementById('dev-file-error');
    var folderSelect = document.getElementById('dev-file-folder');
    var title = document.getElementById('dev-drawer-title');
    var submit = drawer.querySelector('.dev-drawer-submit');
    var projectField = drawer.querySelector('.dev-drawer-project-field');
    var typeField = document.getElementById('dev-file-type') && document.getElementById('dev-file-type').closest('.dev-drawer-field');
    var descriptionField = document.getElementById('dev-file-description') && document.getElementById('dev-file-description').closest('.dev-drawer-field');
    var contentField = document.getElementById('dev-file-content') && document.getElementById('dev-file-content').closest('.dev-drawer-field');
    [typeField, descriptionField, contentField].forEach(function (field) { if (field) field.style.display = ''; });
    if (title) title.textContent = '新建开发文件';
    if (submit) submit.textContent = '创建文件';
    if (projectField) { projectField.style.display = 'none'; projectField.setAttribute('aria-hidden', 'true'); }
    if (projectSelect) {
      projectSelect.disabled = false;
      projectSelect.innerHTML = liveState.projects.length ? liveState.projects.map(function (project) { return '<option value="' + project.id + '">' + escapeHtml(project.name) + '</option>'; }).join('') : '<option value="">暂无项目</option>';
      if (projectId || liveState.selectedProjectId) projectSelect.value = String(projectId || liveState.selectedProjectId);
    }
    if (newProject) newProject.style.display = liveState.projects.length ? 'none' : '';
    if (folderSelect) {
      folderSelect.innerHTML = '<option value="">根目录</option>' + (liveState.folders || []).map(function (item) { return '<option value="' + item.id + '">' + escapeHtml(item.name) + '</option>'; }).join('');
      folderSelect.value = folderId == null ? '' : String(folderId);
    }
    var hint = drawer.querySelector('.dev-drawer-hint');
    var folder = (liveState.folders || []).find(function (item) { return String(item.id) === String(folderId); });
    if (hint) hint.textContent = folder ? '文件将创建在文件夹“' + folder.name + '”中。' : '文件会保存到选中的真实项目中。';
    if (type) type.value = fileType || 'SQL';
    if (content) {
      var templateType = String(fileType || 'SQL').toUpperCase();
      content.value = templateType === 'PYTHON' ? '# Python 任务示例\nfrom datetime import datetime\n\nprint("hello data platform")\n' : templateType === 'SHELL' ? '#!/bin/bash\n# Shell 任务示例\necho "hello data platform"\n' : '-- SQL 脚本示例\nSELECT 1 AS example;\n';
    }
    if (description) description.value = '';
    if (error) error.textContent = '';
    drawer.classList.add('open'); drawer.setAttribute('aria-hidden', 'false');
    setTimeout(function () { var name = document.getElementById('dev-file-name'); if (name) name.focus(); }, 120);
  }
  function submitDevelopmentDrawer() {
    var form = document.getElementById('dev-file-form');
    var error = document.getElementById('dev-file-error');
    if (!form) return;
    var name = String((document.getElementById('dev-file-name') || {}).value || '').trim();
    var type = String((document.getElementById('dev-file-type') || {}).value || 'SQL').toUpperCase();
    var content = String((document.getElementById('dev-file-content') || {}).value || '');
    var description = String((document.getElementById('dev-file-description') || {}).value || '').trim();
    var projectId = String((document.getElementById('dev-file-project') || {}).value || '');
    var drawer = document.getElementById('dev-file-drawer');
    var folderValue = String((document.getElementById('dev-file-folder') || {}).value || '');
    var folderId = folderValue ? Number(folderValue) : null;
    var mode = drawer && drawer.dataset.mode || 'create';
    var saveMode = mode === 'save';
    var saveToProjectMode = mode === 'save-to-project';
    if (!name) { if (error) error.textContent = '请输入文件名称'; return; }
    var project = liveState.projects.find(function (item) { return String(item.id) === projectId; }) || (saveToProjectMode && projectId ? { id: Number(projectId) } : null);
    var createFile = function (target) {
      if (!target) { if (error) error.textContent = '请选择或创建项目'; return; }
      var existing = saveMode && liveState.selectedFile && liveState.selectedFile.id;
      var endpoint = existing ? '/development/files/' + liveState.selectedFile.id : '/development/files';
      var method = existing ? 'PUT' : 'POST';
      var payload = existing ? { name: name, content: content, description: description, folderId: folderId, moveToRoot: !folderValue } : { projectId: target.id, folderId: folderId, name: name, fileType: type, content: content, description: description };
      request(endpoint, { method: method, body: JSON.stringify(payload) }).then(function (file) {
        if (saveMode && liveState.selectedFile && !liveState.selectedFile.id) liveState.openFiles = liveState.openFiles.filter(function (item) { return developmentTabKey(item) !== developmentTabKey(liveState.selectedFile); });
        liveState.selectedProjectId = target.id; closeDevelopmentDrawer(); if (saveMode) { liveState.developmentSavedNotice = Date.now(); flashDevelopmentSaveButton(); } notify(saveToProjectMode ? '文件已保存到项目空间' : saveMode ? '保存成功' : '文件已创建'); return loadDevelopmentFiles(target.id, file.id);
      }).catch(function (requestError) { if (error) error.textContent = requestError.message; });
    };
    if (project) return createFile(project);
    var projectName = String(liveState.developmentScope || 'mine') === 'all' ? '数仓' : '我的开发';
    var projectDescription = String(liveState.developmentScope || 'mine') === 'all' ? '团队公共开发空间' : '个人工作区';
    request('/development/projects', { method: 'POST', body: JSON.stringify({ name: projectName, description: projectDescription }) }).then(function (created) { liveState.projects.push(created); return createFile(created); }).catch(function (requestError) { if (error) error.textContent = requestError.message; });
  }
  function nextDevelopmentFileName(fileType, projectId, folderId) {
    var type = String(fileType || 'SQL').toUpperCase();
    var extension = type === 'PYTHON' ? '.py' : type === 'SHELL' ? '.sh' : '.sql';
    var used = {};
    var matchesLocation = function (file) {
      if (!file) return false;
      if (projectId == null && file.id != null && file.projectId != null) return false;
      if (projectId != null && file.projectId != null && String(file.projectId) !== String(projectId)) return false;
      var fileFolder = file.folderId == null ? null : String(file.folderId);
      var targetFolder = folderId == null ? null : String(folderId);
      return fileFolder === targetFolder;
    };
    (liveState.projectFiles || []).filter(matchesLocation).forEach(function (file) { used[String(file.name || '').toLowerCase()] = true; });
    (liveState.openFiles || []).filter(matchesLocation).forEach(function (file) { used[String(file.name || '').toLowerCase()] = true; });
    var index = 0;
    var candidate = '未命名' + extension;
    while (used[candidate.toLowerCase()]) { index += 1; candidate = '未命名' + index + extension; }
    return candidate;
  }
  function createDevelopmentDraft(fileType, projectId, folderId, showInTree) {
    if (String(liveState.developmentScope || 'mine') === 'favorites') return notify('收藏页仅支持查看，不能新建文件', true);
    var type = String(fileType || 'SQL').toUpperCase();
    var current = liveState.selectedFile || {};
    var targetProjectId = projectId == null ? (liveState.selectedProjectId == null ? null : liveState.selectedProjectId) : Number(projectId);
    var targetFolderId = folderId === undefined ? (current.folderId == null ? null : current.folderId) : (folderId == null || folderId === '' ? null : Number(folderId));
    var draft = { id: null, tabId: 'draft-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7), projectId: targetProjectId, folderId: targetFolderId, name: nextDevelopmentFileName(type, targetProjectId, targetFolderId), fileType: type, content: '', description: '', status: 'DRAFT', currentVersion: 0 };
    if (targetProjectId != null) {
      // Unsaved drafts live only in editor tabs; the resource tree contains persisted files.
      liveState.projectFiles = (liveState.projectFiles || []).filter(function (file) { return file && file.id != null; });
      if (showInTree === true) liveState.projectFiles.push(draft);
      renderDevelopmentTree(targetProjectId, liveState.folders, liveState.projectFiles);
    }
    renderDevelopmentEditor(draft);
  }
  function createDevelopmentFile(fileType, projectId, folderId) { createDevelopmentDraft(fileType || 'SQL', projectId, folderId); }
  function closeDevelopmentSaveNameModal() {
    var host = document.getElementById('dev-save-name-modal');
    if (host) { host.classList.remove('open'); host.setAttribute('aria-hidden', 'true'); }
  }
  function openDevelopmentSaveNameModal() {
    var file = liveState.selectedFile;
    var host = document.getElementById('dev-save-name-modal');
    var form = document.getElementById('dev-save-name-form');
    var input = document.getElementById('dev-save-name-input');
    var folderSelect = document.getElementById('dev-save-folder');
    var currentFile = document.getElementById('dev-save-name-current-file');
    var error = document.getElementById('dev-save-name-error');
    if (!file || !host || !form || !input) return notify('请先创建或选择一个文件');
    form.reset();
    input.value = file.name && file.name !== '未命名.sql' ? file.name : '';
    if (currentFile) currentFile.textContent = file.name || '未命名.sql';
    if (folderSelect) {
      var folderList = Array.isArray(liveState.folders) ? liveState.folders : [];
      var renderFolders = function (list) {
        folderSelect.innerHTML = '<option value="__root__">根目录</option>' + (list || []).map(function (folder) { return '<option value="' + folder.id + '">' + escapeHtml(folder.name) + '</option>'; }).join('');
        folderSelect.value = file.folderId == null || !(list || []).some(function (folder) { return String(folder.id) === String(file.folderId); }) ? '__root__' : String(file.folderId);
        folderSelect.disabled = false;
      };
      folderSelect.disabled = true;
      renderFolders(folderList);
      var projectId = file.projectId != null ? file.projectId : liveState.selectedProjectId;
      if (projectId != null) request('/development/folders?projectId=' + encodeURIComponent(projectId)).then(function (folders) {
        if (liveState.selectedFile === file && host.classList.contains('open')) renderFolders(Array.isArray(folders) ? folders : []);
      }).catch(function () { folderSelect.disabled = false; });
    }
    if (error) error.textContent = '';
    host.classList.add('open'); host.setAttribute('aria-hidden', 'false');
    window.setTimeout(function () { input.focus(); input.select(); }, 0);
  }
  function submitDevelopmentSaveName() {
    var file = liveState.selectedFile;
    var input = document.getElementById('dev-save-name-input');
    var folderSelect = document.getElementById('dev-save-folder');
    var error = document.getElementById('dev-save-name-error');
    var name = String(input && input.value || '').trim();
    if (!file || file.id) return closeDevelopmentSaveNameModal();
    if (!name) { if (error) error.textContent = '请输入文件名称'; return; }
    var content = developmentEditorValue();
    var folderValue = String(folderSelect && folderSelect.value || '__root__');
    var folderId = folderValue === '__root__' ? null : Number(folderValue);
    var username = String(liveState.currentUsername || 'admin').toLowerCase();
    var known = (liveState.projects || []).filter(function (project) { return String(project.ownerName || '').toLowerCase() === username; });
    var activeProject = (liveState.projects || []).find(function (project) { return String(project.id) === String(liveState.selectedProjectId); });
    if (!known.length && activeProject && String(activeProject.ownerName || '').toLowerCase() === username) known.push(activeProject);
    if (String(liveState.developmentScope || 'mine') !== 'all' && file.projectId != null) {
      known.unshift(liveState.projects.find(function (project) { return String(project.id) === String(file.projectId); }) || { id: file.projectId, ownerName: liveState.currentUsername });
    }
    var projectPromise = known[0] ? Promise.resolve(known[0]) : request('/development/projects?scope=mine').then(function (projects) {
      var list = Array.isArray(projects) ? projects : [];
      return list.find(function (project) { return String(project.ownerName || '').toLowerCase() === username; }) || list[0] || null;
    }).then(function (project) {
      if (project) return project;
      return request('/development/projects', { method: 'POST', body: JSON.stringify({ name: '我的开发', description: '个人工作区' }) });
    });
    var submit = document.querySelector('.dev-save-name-submit');
    if (submit) { submit.disabled = true; submit.textContent = '保存中…'; }
    projectPromise.then(function (project) {
      if (!project || project.id == null) throw new Error('无法确定我的开发空间');
      return request('/development/files', { method: 'POST', body: JSON.stringify({ projectId: project.id, folderId: folderId, name: name, fileType: String(file.fileType || 'SQL').toUpperCase(), content: content, description: '' }) }).then(function (saved) {
        liveState.openFiles = liveState.openFiles.filter(function (item) { return developmentTabKey(item) !== developmentTabKey(file); });
        liveState.selectedFile = saved;
        liveState.selectedProjectId = saved.projectId;
        liveState.developmentSavedNotice = Date.now();
        closeDevelopmentSaveNameModal();
        if (submit) { submit.disabled = false; submit.textContent = '保存'; }
        liveState.developmentScope = 'mine';
        document.querySelectorAll('.dev-segment').forEach(function (segment) { segment.classList.toggle('on', (segment.dataset.devScope || 'mine') === 'mine'); });
        notify('保存成功');
        return loadDevelopment('mine').then(function () { return loadDevelopmentFiles(saved.projectId, saved.id); });
      });
    }).catch(function (requestError) {
      if (submit) { submit.disabled = false; submit.textContent = '保存'; }
      if (error) error.textContent = requestError.message || '保存失败';
    });
  }
  function closeDevelopmentSaveToProjectModal() {
    var host = document.getElementById('dev-project-save-modal');
    if (host) { host.classList.remove('open'); host.setAttribute('aria-hidden', 'true'); }
  }
  function openDevelopmentSaveToProjectModal() {
    var file = liveState.selectedFile;
    var host = document.getElementById('dev-project-save-modal');
    var form = document.getElementById('dev-project-save-form');
    var folderSelect = document.getElementById('dev-project-save-folder');
    var nameInput = document.getElementById('dev-project-save-name');
    var descriptionInput = document.getElementById('dev-project-save-description');
    var error = document.getElementById('dev-project-save-error');
    if (!file || !host || !form || !folderSelect || !nameInput) return notify('请先选择或新建一个个人 SQL');
    form.reset();
    nameInput.value = file.name || '未命名.sql';
    descriptionInput.value = file.description || '';
    folderSelect.innerHTML = '<option value="">加载目录…</option>';
    folderSelect.disabled = true;
    if (error) error.textContent = '';
    var sharedProjects = (liveState.projects || []).filter(function (project) { return !isPersonalDevelopmentProject(project); });
    var target = sharedProjects.find(function (project) { return String(project.id) === String(liveState.selectedProjectId); }) || sharedProjects[0] || null;
    host.dataset.targetProjectId = target ? String(target.id) : '';
    var loadFolders = function (project) {
      if (!project) { folderSelect.innerHTML = '<option value="__root__">根目录</option>'; folderSelect.disabled = false; if (error) error.textContent = '暂无项目空间，请先创建一个项目'; return; }
      request('/development/folders?projectId=' + encodeURIComponent(project.id)).then(function (folders) {
        var list = Array.isArray(folders) ? folders : [];
        folderSelect.innerHTML = list.length ? '<option value="">请选择目录</option>' + list.map(function (item) { return '<option value="' + item.id + '">' + escapeHtml(item.name) + '</option>'; }).join('') + '<option value="__root__">根目录</option>' : '<option value="__root__">根目录</option>';
        folderSelect.disabled = false;
        if (file.folderId != null && list.some(function (item) { return String(item.id) === String(file.folderId); })) folderSelect.value = String(file.folderId);
        else folderSelect.value = '__root__';
      }).catch(function (requestError) { folderSelect.innerHTML = '<option value="__root__">根目录</option>'; folderSelect.disabled = false; if (error) error.textContent = requestError.message || '目录加载失败'; });
    };
    host.classList.add('open'); host.setAttribute('aria-hidden', 'false');
    if (target) loadFolders(target);
    else request('/development/projects?scope=all').then(function (projects) {
      var list = (Array.isArray(projects) ? projects : []).filter(function (project) { return !isPersonalDevelopmentProject(project); });
      target = list.find(function (project) { return String(project.id) === String(liveState.selectedProjectId); }) || list[0] || null;
      host.dataset.targetProjectId = target ? String(target.id) : '';
      loadFolders(target);
    }).catch(function () { loadFolders(null); });
    window.setTimeout(function () { folderSelect.focus(); }, 0);
  }
  function submitDevelopmentSaveToProject() {
    var file = liveState.selectedFile;
    var host = document.getElementById('dev-project-save-modal');
    var folderSelect = document.getElementById('dev-project-save-folder');
    var nameInput = document.getElementById('dev-project-save-name');
    var descriptionInput = document.getElementById('dev-project-save-description');
    var error = document.getElementById('dev-project-save-error');
    var name = String(nameInput && nameInput.value || '').trim();
    var projectId = String(host && host.dataset.targetProjectId || '');
    var folderValue = String(folderSelect && folderSelect.value || '');
    if (!file) return closeDevelopmentSaveToProjectModal();
    if (!projectId) { if (error) error.textContent = '暂无项目空间，请先创建一个项目'; return; }
    if (!folderValue) { if (error) error.textContent = '请选择目标目录'; return; }
    if (!name) { if (error) error.textContent = '请输入文件名称'; return; }
    var content = developmentEditorValue();
    var folderId = folderValue === '__root__' ? null : Number(folderValue);
    var submit = document.querySelector('.dev-project-save-submit');
    if (submit) { submit.disabled = true; submit.textContent = '保存中…'; }
    request('/development/files', { method: 'POST', body: JSON.stringify({ projectId: Number(projectId), folderId: folderId, name: name, fileType: String(file.fileType || 'SQL').toUpperCase(), content: content, description: String(descriptionInput && descriptionInput.value || '').trim() }) }).then(function (saved) {
      closeDevelopmentSaveToProjectModal();
      if (submit) { submit.disabled = false; submit.textContent = '保存到项目'; }
      liveState.selectedProjectId = saved.projectId;
      liveState.developmentScope = 'all';
      liveState.folderOpen[saved.folderId] = true;
      document.querySelectorAll('.dev-segment').forEach(function (segment) { segment.classList.toggle('on', (segment.dataset.devScope || 'mine') === 'all'); });
      notify('已保存到项目');
      return request('/development/projects?scope=all').then(function (projects) { liveState.selectedProjectId = saved.projectId; renderDevelopment(projects); return loadDevelopmentFiles(saved.projectId, saved.id); });
    }).catch(function (requestError) { if (submit) { submit.disabled = false; submit.textContent = '保存到项目'; } if (error) error.textContent = requestError.message || '保存失败'; });
  }
  function openDevelopmentPublishDialog(file, devLabel, onlineLabel) {
    var host = document.getElementById('dev-publish-dialog');
    if (!host) return false;
    var project = liveState.projects.find(function (item) { return String(item.id) === String(file.projectId); });
    var nextSchedule = new Date(); nextSchedule.setDate(nextSchedule.getDate() + 1); nextSchedule.setHours(2, 0, 0, 0);
    var nextLabel = nextSchedule.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).replace(/\//g, '-');
    var task = liveState.selectedWorkflow && liveState.selectedWorkflow.name ? liveState.selectedWorkflow.name : '未绑定工作流';
    var schedule = task === '未绑定工作流' ? '未设置' : '每天 02:00';
    host.innerHTML = '<section class="dev-publish-card" role="dialog" aria-modal="true" aria-labelledby="dev-publish-title"><div class="dev-publish-head"><div><h3 id="dev-publish-title">发布上线</h3><p>将项目开发版本切换为线上正式运行版本</p></div><button type="button" class="dev-publish-close" aria-label="关闭">×</button></div><div class="dev-publish-body"><div class="dev-publish-file-name">' + escapeHtml(file.name || '未命名文件') + '</div><div class="dev-publish-version-flow"><div><small>当前线上版本</small><strong>' + escapeHtml(onlineLabel || '—') + '</strong></div><i class="ri-arrow-right-line"></i><div class="next"><small>待发布开发版本</small><strong>' + escapeHtml(devLabel) + '</strong></div></div><div class="dev-publish-detail-grid"><span>负责人</span><b>' + escapeHtml((project && project.ownerName) || liveState.currentUsername || '—') + '</b><span>最近修改</span><b>刚刚</b><span>绑定工作流</span><b>' + escapeHtml(task) + '</b><span>调度周期</span><b>' + escapeHtml(schedule) + '</b><span>下次调度</span><b>' + escapeHtml(nextLabel) + '</b></div><div class="dev-publish-warning"><i class="ri-error-warning-fill"></i><span>发布后，后续线上调度将执行 ' + escapeHtml(devLabel) + '，当前正在运行的实例不受影响。</span></div><label class="dev-publish-note">发布备注（可选）<textarea placeholder="例如：修复订单金额统计逻辑"></textarea></label></div><div class="dev-publish-foot"><button type="button" class="dev-publish-cancel">取消</button><button type="button" class="primary dev-publish-confirm">确认发布上线</button></div></section>';
    var close = function () { host.classList.remove('open'); host.setAttribute('aria-hidden', 'true'); };
    host.querySelector('.dev-publish-close').onclick = close;
    host.querySelector('.dev-publish-cancel').onclick = close;
    host.onclick = function (event) { if (event.target === host) close(); };
    host.querySelector('.dev-publish-confirm').onclick = function () {
      var button = this; button.disabled = true; button.textContent = '发布中…'; notify('发布中…');
      request('/development/files/' + file.id + '/publish', { method: 'POST' }).then(function (published) {
        close(); liveState.selectedFile = published; notify('发布成功，' + devLabel + ' 已成为线上版本'); return loadDevelopmentFiles(published.projectId, published.id);
      }).catch(function (error) { button.disabled = false; notify(error.message || '发布失败', true); });
    };
    host.classList.add('open'); host.setAttribute('aria-hidden', 'false');
    return true;
  }
  function publishDevelopmentFile() {
    var file = liveState.selectedFile;
    if (!file || !file.id) return notify('请先保存后再发布', true);
    if (file.dirty) return notify('请先保存后再发布', true);
    var development = page('page-development');
    request('/development/files/' + file.id + '/versions').then(function (versions) {
      var items = Array.isArray(versions) ? versions : [];
      var published = items.find(function (version) { return version.publishFlag === true; });
      var devLabel = 'V' + (file.currentVersion || 1);
      var onlineLabel = published ? 'V' + published.versionNo : '—';
      if (published && Number(published.versionNo) === Number(file.currentVersion)) { notify('当前开发版本已是线上版本，无需重复发布', true); return; }
      openDevelopmentPublishDialog(file, devLabel, onlineLabel);
    }).catch(function (error) { notify(error.message || '读取版本信息失败', true); });
  }
  function bindDevelopmentDividers() {
    var pageRoot = document.getElementById('page-development');
    if (!pageRoot) return;
    var shell = pageRoot.querySelector('.dev-shell');
    var left = pageRoot.querySelector('.dev-left');
    var right = pageRoot.querySelector('.dev-right');
    var editor = pageRoot.querySelector('.dev-editor');
    var code = pageRoot.querySelector('.code-shell');
    var codeView = pageRoot.querySelector('.codeview');
    var result = pageRoot.querySelector('.result-shell');
    if (!shell || !editor || !code || !result) return;
    if (!window.platformMonaco) bindDevelopmentAutocomplete(editor, codeView);
    var collapseLeft = pageRoot.querySelector('.dev-collapse-left');
    var collapseRight = pageRoot.querySelector('.dev-collapse-right');
    var reopenLeft = pageRoot.querySelector('.dev-reopen-left');
    var reopenRight = pageRoot.querySelector('.dev-reopen-right');
    var setPane = function (pane, button, reopen, collapsed, side) {
      if (!pane) return;
      pane.classList.toggle('is-collapsed', collapsed);
      if (button) { button.setAttribute('aria-expanded', collapsed ? 'false' : 'true'); button.title = collapsed ? '展开' : '收起'; }
      if (reopen) reopen.hidden = !collapsed;
      if (side) shell.dataset[side + 'Collapsed'] = collapsed ? 'true' : 'false';
    };
    if (collapseLeft) collapseLeft.onclick = function () { setPane(left, collapseLeft, reopenLeft, true, 'left'); };
    if (collapseRight) collapseRight.onclick = function () { setPane(right, collapseRight, reopenRight, true, 'right'); };
    if (reopenLeft) reopenLeft.onclick = function () { setPane(left, collapseLeft, reopenLeft, false, 'left'); };
    if (reopenRight) reopenRight.onclick = function () { setPane(right, collapseRight, reopenRight, false, 'right'); };
    pageRoot.querySelectorAll('.dev-divider').forEach(function (divider) {
      divider.onpointerdown = function (event) {
        if (event.button !== undefined && event.button !== 0) return;
        event.preventDefault();
        divider.classList.add('dragging');
        var type = divider.dataset.divider;
        var startX = event.clientX;
        var startY = event.clientY;
        var startLeft = left ? left.getBoundingClientRect().width : 0;
        var startRight = right ? right.getBoundingClientRect().width : 0;
        var startCode = code.getBoundingClientRect().height;
        var move = function (current) {
          if (type === 'left' && left) {
            var nextLeft = Math.min(520, Math.max(180, startLeft + current.clientX - startX));
            left.style.flex = '0 0 ' + nextLeft + 'px';
            left.style.width = nextLeft + 'px';
          } else if (type === 'right' && right) {
            var nextRight = Math.min(520, Math.max(220, startRight - (current.clientX - startX)));
            right.style.flex = '0 0 ' + nextRight + 'px';
            right.style.width = nextRight + 'px';
          } else if (type === 'bottom') {
            var available = Math.max(360, editor.clientHeight - 32 - 47 - divider.offsetHeight);
            var nextCode = Math.min(available - 140, Math.max(140, startCode + current.clientY - startY));
            var nextResult = Math.max(140, available - nextCode);
            code.style.flex = '0 0 ' + nextCode + 'px';
            code.style.height = nextCode + 'px';
            result.style.flex = '0 0 ' + nextResult + 'px';
            result.style.height = nextResult + 'px';
          }
        };
        var stop = function () {
          divider.classList.remove('dragging');
          window.removeEventListener('pointermove', move);
          window.removeEventListener('pointerup', stop);
          window.removeEventListener('pointercancel', stop);
          document.body.style.userSelect = '';
          document.body.style.cursor = '';
        };
        document.body.style.userSelect = 'none';
        document.body.style.cursor = type === 'bottom' ? 'row-resize' : 'col-resize';
        window.addEventListener('pointermove', move);
        window.addEventListener('pointerup', stop);
        window.addEventListener('pointercancel', stop);
      };
    });
  }

  function editorCompletionToken(code) {
    var selection = window.getSelection && window.getSelection();
    if (!selection || !selection.rangeCount || !code.contains(selection.anchorNode)) return /([A-Za-z_][A-Za-z0-9_.]*)$/.exec(code.textContent || '')?.[1] || '';
    var range = selection.getRangeAt(0); var before = document.createRange(); before.selectNodeContents(code); before.setEnd(range.startContainer, range.startOffset);
    return /([A-Za-z_][A-Za-z0-9_.]*)$/.exec(before.toString())?.[1] || '';
  }
  function refreshDevelopmentCompletionItems() {
    var source = activeDevelopmentSource();
    if (!source || !source.id || !source.databaseName) return Promise.resolve([]);
    var key = source.id + ':' + source.databaseName;
    if (liveState.completionKey === key && Array.isArray(liveState.completionItems)) return Promise.resolve(liveState.completionItems);
    return request('/metadata/tables?dataSourceId=' + encodeURIComponent(source.id) + '&database=' + encodeURIComponent(source.databaseName)).then(function (tables) {
      liveState.completionKey = key;
      liveState.completionItems = (tables || []).map(function (table) { return { label: table.name, detail: '表 · ' + source.databaseName, icon: 'ri-table-2', kind: 'table' }; });
      if (window.platformMonaco) window.platformMonaco.setCompletionItems(liveState.completionItems);
      return liveState.completionItems;
    }).catch(function () { return []; });
  }
  function bindDevelopmentAutocomplete(editor, code) {
    if (!editor || !code || code.dataset.autocompleteBound === 'true') return;
    code.dataset.autocompleteBound = 'true';
    var popup = document.createElement('div');
    popup.className = 'dev-autocomplete'; popup.hidden = true; editor.appendChild(popup);
    var selectedIndex = 0; var visibleItems = [];
    var baseSuggestions = function () {
      var type = String((liveState.selectedFile && liveState.selectedFile.fileType) || 'SQL').toUpperCase();
      var words = type === 'PYTHON' ? ['def', 'import', 'from', 'return', 'if', 'elif', 'else', 'for', 'while', 'print'] : type === 'SHELL' ? ['echo', 'export', 'if', 'then', 'else', 'fi', 'for', 'do', 'done', 'grep'] : ['SELECT', 'FROM', 'WHERE', 'JOIN', 'GROUP BY', 'ORDER BY', 'INSERT INTO', 'UPDATE', 'DELETE', 'CREATE TABLE'];
      return words.map(function (word) { return { label: word, detail: type === 'SQL' ? '关键字' : type === 'PYTHON' ? 'Python' : 'Shell', icon: 'ri-key-2-line', kind: 'keyword' }; });
    };
    var hide = function () { popup.hidden = true; visibleItems = []; };
    var draw = function (prefix) {
      var token = String(prefix || '').toLowerCase();
      var items = baseSuggestions();
      if (String((liveState.selectedFile || {}).fileType || 'SQL').toUpperCase() === 'SQL') items = items.concat(liveState.completionItems || []);
      visibleItems = items.filter(function (item) { return !token || item.label.toLowerCase().indexOf(token) === 0; }).slice(0, 9);
      if (!visibleItems.length) return hide();
      selectedIndex = Math.min(selectedIndex, visibleItems.length - 1);
      popup.innerHTML = '<div class="suggest-head">建议</div>' + visibleItems.map(function (item, index) { return '<button type="button" class="' + (index === selectedIndex ? 'on' : '') + '" data-suggest-index="' + index + '"><i class="' + item.icon + '"></i><span>' + escapeHtml(item.label) + '</span><small>' + escapeHtml(item.detail) + '</small></button>'; }).join('');
      popup.hidden = false;
    };
    var show = function (prefix) { refreshDevelopmentCompletionItems().finally(function () { draw(prefix); }); };
    var insert = function (item) {
      if (!item) return; var token = editorCompletionToken(code); code.focus();
      document.execCommand('insertText', false, token ? item.label.slice(token.length) : item.label); hide();
    };
    code.addEventListener('input', function () { var token = editorCompletionToken(code); if (!token) return hide(); selectedIndex = 0; show(token); });
    code.addEventListener('focus', function () { refreshDevelopmentCompletionItems(); });
    code.addEventListener('keydown', function (event) {
      if ((event.ctrlKey || event.metaKey) && event.code === 'Space') { event.preventDefault(); selectedIndex = 0; show(editorCompletionToken(code)); return; }
      if (popup.hidden) return;
      if (event.key === 'ArrowDown') { event.preventDefault(); selectedIndex = (selectedIndex + 1) % visibleItems.length; draw(editorCompletionToken(code)); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); selectedIndex = (selectedIndex - 1 + visibleItems.length) % visibleItems.length; draw(editorCompletionToken(code)); }
      else if (event.key === 'Enter' || event.key === 'Tab') { event.preventDefault(); insert(visibleItems[selectedIndex]); }
      else if (event.key === 'Escape') hide();
    });
    code.addEventListener('click', function (event) { loadEditorTableFields(event, code); });
    popup.addEventListener('mousedown', function (event) { var button = event.target.closest('[data-suggest-index]'); if (!button) return; event.preventDefault(); insert(visibleItems[Number(button.dataset.suggestIndex)]); });
    document.addEventListener('click', function (event) { if (!popup.contains(event.target) && event.target !== code) hide(); });
    var sourceSelect = document.getElementById('dev-development-source');
    if (sourceSelect && sourceSelect.dataset.completionBound !== 'true') { sourceSelect.dataset.completionBound = 'true'; sourceSelect.addEventListener('change', function () { liveState.completionKey = ''; refreshDevelopmentCompletionItems(); }); }
  }
  function sourceCatalog() { return liveState.sourceCatalog.length ? liveState.sourceCatalog : liveState.sources; }
  function testAllSources() { Promise.all(sourceCatalog().map(function (item) { return request('/data-sources/' + item.id + '/test'); })).then(function () { notify('全部数据源连接检测完成'); loadSources(); }).catch(function (error) { notify(error.message, true); }); }
  function viewSourceMetadata() {
    var source = sourceCatalog().find(function (item) { return String(item.type).toUpperCase() === 'STARROCKS' && item.metadataVisible !== false; }) || sourceCatalog().find(function (item) { return item.metadataVisible !== false; });
    if (!source) return notify('暂无可查看元数据的数据源', true);
    window.localStorage.setItem('metadataDataSourceId', String(source.id));
    window.location.href = '/metadata.html';
  }
  function batchRunTasks() { if (!liveState.tasks.length) return notify('暂无可启动的真实任务'); Promise.all(liveState.tasks.map(function (task) { return request('/integration/tasks/' + task.id + '/run', { method: 'POST' }); })).then(function () { notify('批量任务已提交'); loadIntegration(); loadOverview(); }).catch(function (error) { notify(error.message, true); }); }
  function createIntegrationTask() {
    var source = sourceCatalog().find(function (item) { return String(item.type).toUpperCase() === 'MYSQL' && item.databaseName; });
    var target = sourceCatalog().find(function (item) { return String(item.type).toUpperCase() === 'STARROCKS'; });
    if (!source || !target) return notify('需要先登记一个 MySQL 和一个 StarRocks 数据源', true);
    var name = window.prompt('同步任务名称'); if (!name) return;
    var sourceTable = window.prompt('源表名', 'yzl_order'); if (!sourceTable) return;
    var targetDb = window.prompt('目标数据库', 'ods') || 'ods';
    var targetTable = window.prompt('目标表名', sourceTable) || sourceTable;
    var body = { name: name, sourceType: 'MYSQL', targetType: 'STARROCKS', syncMode: 'FULL', sourceDataSourceId: source.id, targetDataSourceId: target.id, source: { host: source.host, port: source.port, database: source.databaseName, username: source.username, password: '', table: sourceTable }, target: { host: target.host, port: target.port, database: targetDb, username: target.username, password: '', table: targetTable }, mappings: [], options: {}, tables: [{ sourceDatabase: source.databaseName, sourceTable: sourceTable, targetDatabase: targetDb, targetTable: targetTable }] };
    request('/integration/tasks', { method: 'POST', body: JSON.stringify(body) }).then(function () { notify('同步任务已创建'); loadIntegration(); loadOverview(); }).catch(function (error) { notify(error.message, true); });
  }
  function operateInstance(action) { var id = window.prompt('请输入 DolphinScheduler 实例 ID'); if (!id) return; request('/operations/process-instances/' + encodeURIComponent(id) + '/' + action, { method: 'POST' }).then(function () { notify(action === 'stop' ? '实例已终止' : '实例已重跑'); loadOperations(); }).catch(function (error) { notify(error.message, true); }); }
  function viewTaskLog(id) { id = id || window.prompt('请输入 DolphinScheduler 任务实例 ID'); if (!id) return; request('/operations/task-instances/' + encodeURIComponent(id) + '/log').then(function (log) { window.alert(log || '暂无任务日志'); }).catch(function (error) { notify(error.message, true); }); }
  function saveWorkflow() { var workflow = liveState.selectedWorkflow; if (!workflow) return notify('暂无可保存的真实工作流'); var nodes = (workflow.nodes || []).map(function (node) { return { name: node.name, nodeType: node.nodeType, fileVersionId: node.fileVersionId, configJson: node.configJson, x: node.x, y: node.y, nodeCode: node.nodeCode }; }); var edges = (workflow.edges || []).map(function (edge) { return { sourceNodeId: edge.sourceNodeId, targetNodeId: edge.targetNodeId }; }); request('/workflows/' + workflow.id + '/graph', { method: 'PUT', body: JSON.stringify({ name: workflow.name, description: workflow.description || '', nodes: nodes, edges: edges }) }).then(function (item) { liveState.selectedWorkflow = item; notify('工作流已保存'); loadWorkflow(); }).catch(function (error) { notify(error.message, true); }); }
  function runWorkflow() { if (!liveState.selectedWorkflow) return notify('暂无可运行的真实工作流'); request('/workflows/' + liveState.selectedWorkflow.id + '/run', { method: 'POST' }).then(function () { notify('工作流已提交到调度服务'); loadOperations(); loadOverview(); }).catch(function (error) { notify(error.message, true); }); }
  function publishWorkflow() { if (!liveState.selectedWorkflow) return notify('暂无可发布的真实工作流'); request('/workflows/' + liveState.selectedWorkflow.id + '/publish', { method: 'POST' }).then(function () { notify('工作流已发布到 DolphinScheduler'); loadWorkflow(); }).catch(function (error) { notify(error.message, true); }); }
  function setDevelopmentRunState(running, label) {
    var development = page('page-development');
    var button = development && development.querySelector('.dev-run-button');
    var status = development && development.querySelector('.result-status');
    var statusLabel = development && development.querySelector('.result-status-label');
    var statusbar = development && development.querySelector('.result-statusbar');
    var runningMeta = development && development.querySelector('.result-running-meta');
    if (button) { button.classList.toggle('is-running', !!running); button.querySelector('span').textContent = running ? '停止运行' : '运行'; button.querySelector('i').className = running ? 'ri-stop-fill' : 'ri-play-fill'; }
    if (statusbar) statusbar.hidden = false;
    if (status) status.className = 'result-status' + (running ? ' running' : '');
    var icon = status && status.querySelector('i'); if (icon) icon.className = running ? 'ri-loader-4-line ri-spin' : 'ri-checkbox-circle-fill';
    if (statusLabel) statusLabel.textContent = label || (running ? '正在执行' : '尚未执行');
    if (runningMeta) { runningMeta.hidden = !running; runningMeta.textContent = running ? (liveState.developmentRunMeta || '') : ''; }
  }
  function currentStatement(text, offset) {
    var source = String(text || ''), start = Math.max(0, source.lastIndexOf(';', Math.max(0, offset - 1)) + 1), end = source.indexOf(';', offset);
    if (end < 0) end = source.length;
    return source.slice(start, end + (end < source.length ? 1 : 0)).trim();
  }
  function editorCaretOffset(code) {
    if (window.platformMonaco && window.platformMonaco.getEditor()) return window.platformMonaco.getCaretOffset();
    var selection = window.getSelection && window.getSelection();
    if (!selection || !selection.rangeCount || !code.contains(selection.anchorNode)) return 0;
    var before = document.createRange(); before.selectNodeContents(code); before.setEnd(selection.anchorNode, selection.anchorOffset); return before.toString().length;
  }
  function stopCurrentFile() {
    if (!liveState.developmentRunning) return;
    liveState.developmentRunning = false; liveState.developmentRunToken += 1; setDevelopmentRunState(false, '已停止'); notify('已停止运行');
  }
  function runCurrentFile() {
    var code = page('page-development') && page('page-development').querySelector('.codeview');
    if (!code || !liveState.selectedFile) return notify('请先从项目中选择一个真实文件');
    var fileType = String(liveState.selectedFile.fileType || 'SQL').toUpperCase();
    if (fileType !== 'SQL') return notify('当前仅支持运行 SQL 文件', true);
    if (liveState.developmentRunning) return stopCurrentFile();
    var selection = window.platformMonaco && window.platformMonaco.getEditor() ? window.platformMonaco.getEditor().getSelection() : (window.getSelection ? window.getSelection() : null);
    var selectedSql = window.platformMonaco && window.platformMonaco.getEditor() ? window.platformMonaco.getSelectedText().trim() : selection && selection.toString ? selection.toString().trim() : '';
    var hasSelection = !!selectedSql;
    var allSql = developmentEditorValue();
    var sql = hasSelection ? selectedSql : currentStatement(allSql, editorCaretOffset(code)) || allSql;
    sql = sql.replace(/^--[^\n]*\n/, '').trim();
    if (!sql) return notify('请输入或选中要运行的 SQL', true);
    liveState.lastExecutedSql = sql;
    var sourceSelect = document.getElementById('dev-development-source');
    var sourceId = sourceSelect && sourceSelect.value;
    if (!sourceId) return notify('请先选择数据源后再执行 SQL', true);
    var source = sourceCatalog().find(function (item) { return String(item.id) === String(sourceId); });
    if (!source) return notify('所选数据源不可用，请刷新后重试', true);
    var startedAt = Date.now();
    var token = ++liveState.developmentRunToken; liveState.developmentRunning = true; liveState.developmentRunMeta = '开始时间：' + new Date(startedAt).toLocaleTimeString('zh-CN', { hour12: false }) + ' · 数据源：' + source.name + (source.databaseName ? ' / ' + source.databaseName : ''); setDevelopmentRunState(true, '正在执行 SQL…'); toggleDevelopmentHistory(false);
    request('/query/execute', { method: 'POST', body: JSON.stringify({ sql: sql, selected: hasSelection, dataSourceId: source.id, databaseName: source.databaseName || 'ods' }) }).then(function (result) { if (token !== liveState.developmentRunToken || !liveState.developmentRunning) return; if (result && (result.elapsedMs == null || Number(result.elapsedMs) < 0)) result.elapsedMs = Date.now() - startedAt; liveState.developmentRunning = false; setDevelopmentRunState(false); renderQueryResult(result); }).catch(function (error) { if (token !== liveState.developmentRunToken) return; liveState.developmentRunning = false; setDevelopmentRunState(false, '执行失败'); renderQueryFailure(error, Date.now() - startedAt); notify(error.message, true); });
  }
  function renderQueryFailure(error, elapsedMs) {
    var shell = document.querySelector('#page-development .result-shell');
    if (!shell) return;
    var elapsed = shell.querySelector('.result-elapsed'); if (elapsed) elapsed.textContent = '耗时 ' + (Math.max(0, Number(elapsedMs) || 0) / 1000).toFixed(2) + 's';
    var count = shell.querySelector('.result-count'); if (count) count.textContent = '';
    var head = shell.querySelector('.result-table-wrap thead'); if (head) head.innerHTML = '<tr><th>SQL Error</th></tr>';
    var body = shell.querySelector('.result-table-wrap tbody');
    if (body) body.innerHTML = '<tr><td class="query-error">' + escapeHtml(error && error.message ? error.message : 'SQL 执行失败') + '</td></tr>';
  }
  function renderQueryResult(result) {
    var shell = document.querySelector('#page-development .result-shell');
    if (!shell) return;
    var columns = result.columns || [];
    var comments = result.columnComments || {};
    var head = shell.querySelector('.result-table-wrap thead');
    if (head) head.innerHTML = '<tr>' + (columns.length ? columns.map(function (column) {
      var comment = String(comments[column] || '').trim();
      var label = /[\u3400-\u9fff]/.test(comment) ? comment : column;
      var title = comment && comment !== label ? comment + '（' + column + '）' : column;
      return '<th title="' + escapeHtml(title) + '">' + escapeHtml(label) + '</th>';
    }).join('') : '<th>结果</th>') + '</tr>';
    var body = shell.querySelector('.result-table-wrap tbody');
    if (!body) return;
    var rows = result.rows || [];
    body.innerHTML = rows.length ? rows.map(function (row) { return '<tr>' + (columns.length ? columns : Object.keys(row)).map(function (column) { return '<td>' + escapeHtml(row[column]) + '</td>'; }).join('') + '</tr>'; }).join('') : emptyRow(columns.length || 1, '查询无结果');
    var statusCode = String(result.status || '').toUpperCase();
    var succeeded = statusCode === 'SUCCESS';
    var status = shell.querySelector('.result-status');
    if (status) { status.className = 'result-status ' + (succeeded ? 'success' : 'failed'); }
    var statusIcon = status && status.querySelector('i');
    if (statusIcon) statusIcon.className = succeeded ? 'ri-checkbox-circle-fill' : 'ri-close-circle-fill';
    var statusLabel = shell.querySelector('.result-status-label');
    if (statusLabel) statusLabel.textContent = succeeded ? '执行成功' : (statusCode === 'CANCELED' ? '执行已取消' : '执行失败');
    var elapsed = Number(result.elapsedMs);
    var elapsedNode = shell.querySelector('.result-elapsed');
    if (elapsedNode) elapsedNode.textContent = '耗时 ' + (Number.isFinite(elapsed) && elapsed >= 0 ? (elapsed / 1000).toFixed(2) + 's' : '—');
    var countNode = shell.querySelector('.result-count');
    if (countNode) countNode.textContent = '返回 ' + number(result.rowCount) + ' 条数据';
    var sql = result.sql || (liveState.lastExecutedSql || '');
    liveState.executionHistory.unshift({ sql: sql, status: result.status || 'SUCCESS', username: liveState.currentUsername || 'admin', rowCount: result.rowCount || 0, elapsedMs: result.elapsedMs || 0, finishedAt: result.finishedAt || new Date().toISOString() });
    liveState.executionHistory = liveState.executionHistory.slice(0, 100);
    renderDevelopmentHistory();
    notify('查询完成，共 ' + number(result.rowCount) + ' 行');
  }

  function renderDevelopmentHistory(query) {
    var shell = document.querySelector('#page-development .result-shell');
    var list = shell && shell.querySelector('.result-history-list');
    if (!list) return;
    var keyword = String(query || '').trim().toLowerCase();
    var items = liveState.executionHistory.map(function (item, index) { return { item: item, index: index }; }).filter(function (entry) { return !keyword || [entry.item.sql, entry.item.username].some(function (value) { return String(value || '').toLowerCase().indexOf(keyword) >= 0; }); });
    list.innerHTML = '<table class="data-table execution-history-table"><thead><tr><th>执行时间</th><th>执行人</th><th>状态</th><th>耗时</th><th>执行 SQL</th><th>操作</th></tr></thead><tbody>' + (items.length ? items.map(function (entry) { var item = entry.item; var code = String(item.status || '').toUpperCase(); var label = code === 'SUCCESS' ? '执行成功' : code === 'CANCELED' ? '已取消' : code === 'FAILED' ? '执行失败' : (item.status || '—'); var cls = code === 'SUCCESS' ? 'success' : 'failed'; var ms = Number(item.elapsedMs); var sql = String(item.sql || '—'); var shortSql = sql.replace(/\s+/g, ' ').trim(); if (shortSql.length > 42) shortSql = shortSql.slice(0, 42) + '…'; return '<tr><td>' + escapeHtml(when(item.finishedAt)) + '</td><td>' + escapeHtml(item.username || '—') + '</td><td><span class="state ' + cls + '">' + escapeHtml(label) + '</span></td><td>' + escapeHtml(Number.isFinite(ms) && ms >= 0 ? (ms / 1000).toFixed(2) + 's' : '—') + '</td><td class="history-sql" title="' + escapeHtml(sql) + '">' + escapeHtml(shortSql) + '</td><td><span class="history-action"><button type="button" data-history-detail="' + entry.index + '">详情</button><button type="button" data-history-copy="' + entry.index + '">复制</button></span></td></tr>'; }).join('') : '<tr><td colspan="6" class="muted" style="padding:20px;text-align:center">暂无匹配的执行记录</td></tr>') + '</tbody></table>';
  }

  function ensureSqlDetailDialog() {
    var host = document.getElementById('sql-detail-dialog');
    if (!host || host.dataset.bound === 'true') return host;
    host.dataset.bound = 'true';
    host.innerHTML = '<section class="sql-detail-card" role="dialog" aria-modal="true" aria-label="执行 SQL 详情"><div class="sql-detail-head"><h3>执行 SQL</h3><button type="button" class="sql-detail-copy">复制 SQL</button><button type="button" class="sql-detail-close" aria-label="关闭">×</button></div><pre class="sql-detail-code"></pre></section>';
    host.querySelector('.sql-detail-close').onclick = function () { host.classList.remove('open'); };
    host.onclick = function (event) { if (event.target === host) host.classList.remove('open'); };
    host.querySelector('.sql-detail-copy').onclick = function () { copySql(host.dataset.sql || ''); };
    return host;
  }
  function copySql(sql) {
    var done = function () { notify('SQL 已复制'); };
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(sql || '').then(done).catch(function () { notify('复制失败，请手动复制', true); });
    else { var input = document.createElement('textarea'); input.value = sql || ''; document.body.appendChild(input); input.select(); document.execCommand('copy'); input.remove(); done(); }
  }
  function showSqlDetail(index) {
    var item = liveState.executionHistory[index]; if (!item) return;
    var host = ensureSqlDetailDialog(); if (!host) return;
    host.dataset.sql = item.sql || '';
    host.querySelector('.sql-detail-code').textContent = item.sql || '—';
    host.classList.add('open');
  }

  function loadDevelopmentHistory() {
    return request('/query/history').then(function (items) {
      liveState.executionHistory = (items || []).map(function (item) { return { sql: item.sql, status: item.status, username: item.username || 'admin', rowCount: item.rowCount || 0, elapsedMs: item.elapsedMs || 0, finishedAt: item.finishedAt || item.startedAt || '' }; });
      renderDevelopmentHistory((document.querySelector('.result-history-search') || {}).value || '');
    });
  }

  function toggleDevelopmentHistory(show) {
    var shell = document.querySelector('#page-development .result-shell');
    if (!shell) return;
    var panel = shell.querySelector('.result-history-panel');
    var table = shell.querySelector('.result-table-wrap');
    var tab = shell.querySelector('.result-history-tab');
    var outputTab = shell.querySelector('.result-output-tab');
    var statusbar = shell.querySelector('.result-statusbar');
    if (panel) panel.hidden = !show;
    if (table) table.hidden = show;
    if (tab) tab.classList.toggle('on', show);
    if (outputTab) outputTab.classList.toggle('on', !show);
    if (statusbar) statusbar.hidden = show;
    if (show) loadDevelopmentHistory().catch(function (error) { notify(error.message, true); });
  }
  function toggleResultFullscreen(force) {
    var shell = document.querySelector('#page-development .result-shell');
    if (!shell) return;
    var enabled = force == null ? !shell.classList.contains('result-fullscreen-mode') : !!force;
    shell.classList.toggle('result-fullscreen-mode', enabled);
    document.body.classList.toggle('result-fullscreen-open', enabled);
    var button = shell.querySelector('.result-fullscreen');
    if (button) {
      button.title = enabled ? '退出全屏' : '全屏查看结果';
      button.setAttribute('aria-label', button.title);
      button.innerHTML = '<i class="' + (enabled ? 'ri-fullscreen-exit-line' : 'ri-fullscreen-line') + '"></i>';
    }
  }
  function exportDevelopmentResult() {
    var table = document.querySelector('#page-development .result-table-wrap table');
    if (!table) return notify('暂无可导出的结果', true);
    var rows = Array.from(table.querySelectorAll('tr')).map(function (row) {
      return Array.from(row.children).map(function (cell) { return '"' + String(cell.textContent || '').replace(/"/g, '""').replace(/\s+/g, ' ').trim() + '"'; }).join(',');
    }).filter(Boolean);
    if (rows.length < 2) return notify('暂无可导出的结果', true);
    var blob = new Blob(['\ufeff' + rows.join('\r\n')], { type: 'text/csv;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a'); link.href = url; link.download = 'query-result-' + new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-') + '.csv';
    document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
    notify('结果已导出');
  }
  function saveCurrentFile() {
    if (!liveState.selectedFile) return notify('请先选择或新建一个文件');
    var file = liveState.selectedFile;
    if (!file.id) return openDevelopmentSaveNameModal();
    var content = developmentEditorValue();
    request('/development/files/' + file.id, { method: 'PUT', body: JSON.stringify({ content: String(content || file.content || ''), name: file.name, description: file.description || '', folderId: file.folderId == null ? null : Number(file.folderId), moveToRoot: false }) }).then(function (saved) {
      saved.dirty = false; liveState.selectedFile = saved; liveState.developmentSavedNotice = Date.now();
      liveState.openFiles = liveState.openFiles.map(function (item) { return developmentTabKey(item) === developmentTabKey(file) ? Object.assign(item, saved, { dirty: false }) : item; });
      renderDevelopmentTabs(); renderDevelopmentVersionState(saved); flashDevelopmentSaveButton(); notify('保存成功');
      return loadDevelopmentFiles(saved.projectId, saved.id);
    }).catch(function (error) { notify(error.message || '保存失败', true); });
  }
  function flashDevelopmentSaveButton() {
    var button = document.querySelector('#page-development .dev-save-file');
    if (!button) return;
    window.clearTimeout(flashDevelopmentSaveButton.timer);
    button.innerHTML = '<i class="ri-checkbox-circle-fill"></i>已保存'; button.classList.add('saved');
    flashDevelopmentSaveButton.timer = window.setTimeout(function () { button.innerHTML = '<i class="ri-save-line"></i>保存'; button.classList.remove('saved'); }, 1800);
    window.setTimeout(function () { if (liveState.selectedFile) renderDevelopmentVersionState(liveState.selectedFile); }, 3600);
  }
  function formatDevelopmentFile() {
    var development = page('page-development');
    var code = development && development.querySelector('.codeview');
    var file = liveState.selectedFile;
    if (!code || !file) return notify('请先选择或新建一个文件', true);
    var raw = developmentEditorValue();
    var type = String(file.fileType || 'SQL').toUpperCase();
    var formatted = raw.replace(/[ \t]+\n/g, '\n').trim();
    if (type === 'SQL') {
      formatted = formatted.replace(/\s+/g, ' ').replace(/\s*,\s*/g, ', ')
        .replace(/\b(select|from|where|join|left join|right join|group by|order by|having|limit|insert into|insert overwrite|update|delete from)\b/gi, function (keyword) { return '\n' + keyword.toUpperCase(); })
        .replace(/^\n/, '').replace(/\b(and|or)\b/gi, function (keyword) { return '\n  ' + keyword.toUpperCase(); });
    }
    setDevelopmentEditorValue(formatted, true);
    syncDevelopmentEditor(true);
    notify(type === 'SQL' ? 'SQL 格式化完成，当前有未保存修改' : '文件格式化完成，当前有未保存修改');
  }

  function clearStaticPlaceholders() {
    document.querySelectorAll('.kpi-value, #page-workbench .metric .mv').forEach(function (node) { text(node, '—'); });
    document.querySelectorAll('.kpi-trend, .kpi-bars, #page-workbench .metric .trend, #page-workbench .metric .bars').forEach(function (node) { node.remove(); });
    var workbench = page('page-workbench');
    if (workbench) {
      var status = workbench.querySelector('.summary .status');
      if (status) { text(status.querySelector('.online'), '暂无系统状态'); text(status.querySelector('.count strong'), '—'); text(status.querySelector('.count small'), '暂无数据'); status.querySelectorAll('.service em').forEach(function (node) { text(node, '—'); }); }
      var statusList = workbench.querySelector('.status-list'); if (statusList) statusList.innerHTML = '<div class="muted" style="padding:24px;text-align:center">暂无数据</div>';
    }
    clearChart('trend'); clearChart('donut');
    document.querySelectorAll('.data-table tbody, #page-workbench .table tbody').forEach(function (body) {
      var columns = body.parentElement.querySelectorAll('thead th').length || 1;
      body.innerHTML = emptyRow(columns, '暂无数据');
    });
    var favourites = document.querySelector('.favs'); if (favourites) favourites.innerHTML = '<div style="padding:28px;color:#8190a8;text-align:center">暂无收藏</div>';
    var sourcePage = page('page-source');
    if (sourcePage) {
      var sourceSide = sourcePage.querySelector('.side-list'); if (sourceSide) sourceSide.innerHTML = '<div class="side-row muted">暂无数据</div>';
      var sourceDonut = sourcePage.querySelector('.source-donut'); if (sourceDonut) { sourceDonut.style.background = '#e5ebf3'; var sourceCenter = sourceDonut.querySelector('b'); if (sourceCenter) text(sourceCenter, '—'); }
      var sourceLegend = sourcePage.querySelector('.source-donut + .legend-list'); if (sourceLegend) sourceLegend.innerHTML = '<div class="muted">暂无数据</div>';
      var health = sourcePage.querySelector('.health-bar i'); if (health) health.style.width = '0%';
      sourcePage.querySelectorAll('.health-bar + div span').forEach(function (node) { text(node, '暂无数据'); });
      var sourceFooter = sourcePage.querySelector('.source-footer > span'); if (sourceFooter) text(sourceFooter, '共 0 条');
    }
    var modalResults = document.querySelector('.modal .results'); if (modalResults) modalResults.innerHTML = '<div class="result muted">请输入关键词搜索平台数据</div>';
    document.querySelectorAll('.date').forEach(function (node) { text(node, new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())); });
    document.querySelectorAll('.pager').forEach(function (node) { node.style.display = 'none'; });
    document.querySelectorAll('.module-note').forEach(function (node) { node.innerHTML = ''; });
    var badge = document.querySelector('.badge'); if (badge) badge.style.display = 'none';
    var development = page('page-development');
    if (development) {
      var code = development.querySelector('.codeview'); if (code) { code.textContent = ''; code.dataset.placeholder = '暂无项目文件'; }
      var resultMeta = development.querySelector('.result-meta'); if (resultMeta) resultMeta.innerHTML = '<span class="muted">暂无执行结果</span>';
      var fieldInformation = development.querySelector('.dev-field-info'); if (fieldInformation) fieldInformation.innerHTML = '<h4>字段信息</h4><div class="muted">点击编辑器中的表名，即可查看对应字段。</div>';
    }
    var assets = page('page-assets');
    if (assets) {
      var assetSide = assets.querySelector('.side-list'); if (assetSide) assetSide.innerHTML = '<div class="side-row muted">暂无已登记资产</div>';
      var detail = assets.querySelector('.asset-detail');
      if (detail) detail.innerHTML = '<div class="asset-title"><i class="ri-table-line ico-blue"></i><div><div>未选择资产</div><div class="muted" style="font-size:12px;font-weight:400;margin-top:3px">请选择有真实血缘关系的资产</div></div></div>';
    }
    var workflow = page('page-workflow');
    if (workflow) {
      var workflowTitle = workflow.querySelector('.workflow-head strong'); if (workflowTitle) text(workflowTitle, '暂无工作流');
      var workflowStatus = workflow.querySelector('.workflow-head .tag'); if (workflowStatus) { text(workflowStatus, '暂无数据'); workflowStatus.className = 'tag purple'; }
      var workflowSaved = workflow.querySelector('.workflow-head .muted'); if (workflowSaved) text(workflowSaved, '请先新建工作流');
      var workflowEdit = workflow.querySelector('.workflow-head > i'); if (workflowEdit) workflowEdit.remove();
      workflow.querySelectorAll('.wf-node').forEach(function (node) { node.remove(); });
      workflow.querySelectorAll('.wf-lines,.minimap').forEach(function (node) { node.remove(); });
      var inspector = workflow.querySelector('.workflow-inspector .formbox'); if (inspector) inspector.innerHTML = '<h3 style="margin:4px 0">暂无工作流</h3><p class="muted">暂无可展示的工作流数据</p>';
    }
    var operations = page('page-operations');
    if (operations) {
      operations.querySelectorAll('.alert-item').forEach(function (item) { item.remove(); });
      var system = operations.querySelector('.sys-card .online'); if (system) text(system, '暂无系统状态');
      var opsChart = operations.querySelector('.chart-card .static-line'); if (opsChart) opsChart.outerHTML = '<div class="live-chart-empty" style="height:190px;display:grid;place-items:center;color:#8190a8">暂无数据</div>';
      var opsDonut = operations.querySelector('.ops-donut'); if (opsDonut) opsDonut.innerHTML = '<div><b>—</b><span>暂无数据</span></div>';
      var opsLegend = operations.querySelector('.donut-layout .legend-list'); if (opsLegend) opsLegend.innerHTML = '<div class="muted">暂无数据</div>';
    }
    var integration = page('page-integration');
    if (integration) {
      var integrationSide = integration.querySelector('.integration-sidebar .side-list'); if (integrationSide) integrationSide.innerHTML = '<div class="side-row muted">暂无数据</div>';
      var intDonut = integration.querySelector('.int-donut'); if (intDonut) intDonut.innerHTML = '<div><b>—</b><span>暂无数据</span></div>';
      var intLegend = integration.querySelector('.compact-legend'); if (intLegend) intLegend.innerHTML = '<div class="muted">暂无数据</div>';
      var intFooter = integration.querySelector('.table-footer'); if (intFooter) text(intFooter.querySelector('span'), '共 0 条记录');
    }
    document.querySelectorAll('[data-a="系统设置"], .support, .support-title, #page-operations .help-strip, #page-assets .filter-row .mbtn.primary').forEach(function (node) { node.remove(); });
    document.querySelectorAll('#page-workbench .tabs .tab:not(.on), #page-operations .module-tabs, #page-assets .filter-row select, #page-assets .filter-row input, #page-development .right-tabs button:not(.on):not(.dev-collapse-right), #page-development .result-tabs span, #page-workflow .right-tabs button:not(.on)').forEach(function (node) { node.remove(); });
    document.querySelectorAll('#page-operations .quickbox, #page-development .dev-toolbar .mbtn, #page-workflow .workflow-head .mbtn').forEach(function (node) {
      var label = node.textContent.trim();
      if (['暂停调度', '告警配置', '停止', '调度', '版本', '更多'].indexOf(label) >= 0) node.remove();
    });
  }

  function startPlatformStatusTicker(statusCard) {
    var track = statusCard && statusCard.querySelector('.service-track');
    var items = track ? track.querySelectorAll('.service') : [];
    if (!track || items.length < 2) return;
    window.clearInterval(statusCard._serviceTicker);
    var index = 0;
    var timer;
    function update() {
      track.style.transform = 'translateY(-' + (index * 28) + 'px)';
    }
    timer = window.setInterval(function () { index = (index + 1) % items.length; update(); }, 3000);
    statusCard._serviceTicker = timer;
    statusCard.onmouseenter = function () { window.clearInterval(timer); };
    statusCard.onmouseleave = function () { window.clearInterval(timer); timer = window.setInterval(function () { index = (index + 1) % items.length; update(); }, 3000); statusCard._serviceTicker = timer; };
  }

  function loadIdentity() {
    return request('/auth/me').then(function (identity) {
      var username = identity.authenticated ? identity.username : '未登录';
      liveState.currentUsername = username;
      var roleCode = String(identity.roleCode || identity.role || '').toUpperCase();
      var isAdmin = !!(identity.authenticated && (identity.superAdmin === true || roleCode === 'ADMIN' || roleCode === 'SUPER_ADMIN'));
      updateAdminControls(isAdmin);
      document.querySelectorAll('.pname').forEach(function (node) { text(node, username); });
      document.querySelectorAll('.avatar').forEach(function (node) { text(node, username.slice(0, 1).toUpperCase() || '—'); });
      document.querySelectorAll('.prole').forEach(function (node) { text(node, identity.authenticated ? (identity.role || (isAdmin ? '管理员' : '普通用户')) : '未启用认证'); });
      var welcome = document.querySelector('#page-workbench .welcome h1');
      if (welcome) text(welcome, identity.authenticated ? '你好，' + username + ' 👋' : '你好 👋');
      if (!isAdmin && location.hash === '#settings') showPage('workbench');
    }).catch(function () {
      updateAdminControls(false);
      if (location.hash === '#settings') showPage('workbench');
    });
  }

  function renderDevelopmentDataSources() {
    var select = document.getElementById('dev-development-source');
    if (!select) return;
    var current = select.value;
    select.innerHTML = '<option value="">选择数据源</option>' + (liveState.sources || []).map(function (source) { return '<option value="' + source.id + '">' + escapeHtml(source.name) + '</option>'; }).join('');
    if ((liveState.sources || []).some(function (source) { return String(source.id) === String(current); })) select.value = current;
  }

  function loadOverview() { return request('/dashboard/overview').then(renderOverview); }
  function loadSources() { return request('/dashboard/sources').then(function (sources) { renderSources(sources, true); renderDevelopmentDataSources(); }); }
  function loadIntegration() { var trendRange = (document.getElementById('integrationTrendRange') || {}).value || 'seven'; var statusRange = (document.getElementById('integrationStatusRange') || {}).value || 'seven'; return Promise.all([request('/dashboard/integration?range=' + encodeURIComponent(trendRange)), request('/integration/tasks'), request('/dashboard/sources'), request('/dashboard/integration?range=' + encodeURIComponent(statusRange))]).then(function (values) { renderIntegration(values[0], values[1], values[2], values[3]); }); }
  function loadOperations() { return request('/dashboard/operations').then(renderOperations); }
  function loadAssets() { return Promise.all([request('/dashboard/assets'), request('/lineage')]).then(function (values) { renderAssets(values[0], values[1]); }); }
  function loadWorkflow() { return request('/workflows').then(renderWorkflow); }
  function loadDevelopment(scope) {
    var currentScope = scope || liveState.developmentScope || 'mine';
    return request('/development/projects?scope=' + encodeURIComponent(currentScope)).then(function (projects) {
      if (String(liveState.developmentScope || 'mine') !== String(currentScope)) return projects;
      renderDevelopment(projects);
      return projects;
    });
  }

  function loadAll() {
    var jobs = [loadIdentity(), loadOverview(), loadSources(), loadIntegration(), loadOperations(), loadAssets(), loadWorkflow(), loadDevelopment()];
    return Promise.all(jobs).then(function () { document.documentElement.dataset.liveData = 'ready'; }).catch(function (error) { notify(error.message || '无法加载平台数据', true); });
  }

  try { clearStaticPlaceholders(); }
  finally { document.documentElement.classList.remove('live-boot'); }
  bindActions();
  window.addEventListener('platform:data-source-changed', function () {
    loadSources().then(function () { loadOverview(); loadIntegration(); notify('数据源已保存并刷新列表'); }).catch(function (error) { notify(error.message || '数据源已保存，但列表刷新失败', true); });
  });
  loadAll();
})();
