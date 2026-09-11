(function () {
  'use strict';

  var state = {
    dashboard: {}, processes: [], tasks: [], failed: [], tab: 'process',
    search: '', status: 'all', page: 1, pageSize: 10,
    selectedProcessId: '', selectedTaskId: '', poll: null, loading: false
  };
  var page;

  function esc(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c];
    });
  }
  function request(path, options) {
    options = options || {};
    var token = localStorage.getItem('platform_access_token');
    options.headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    if (token) options.headers.Authorization = 'Bearer ' + token;
    return fetch('/api' + path, options).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (payload) {
        if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
        return payload.data;
      });
    });
  }
  function notify(message, error) {
    var toast = document.getElementById('toast');
    if (!toast) return;
    var icon = toast.querySelector('i');
    var label = toast.querySelector('span');
    if (icon) icon.className = error ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';
    if (label) label.textContent = message;
    toast.classList.add('show');
    clearTimeout(notify.timer);
    notify.timer = setTimeout(function () { toast.classList.remove('show'); }, 2600);
  }
  function kind(status) {
    var value = String(status || '').toUpperCase();
    if (/SUCCESS|FINISH/.test(value)) return 'success';
    if (/FAIL|ERROR|KILL|STOP/.test(value)) return 'failed';
    if (/RUNNING|SUBMITTED|READY|DISPATCH|DELAY|WAITING/.test(value)) return 'running';
    return 'pending';
  }
  function label(status) {
    var k = kind(status);
    if (k === 'success') return '成功';
    if (k === 'failed') return /STOP|KILL/.test(String(status || '').toUpperCase()) ? '已终止' : '失败';
    if (k === 'running') return '运行中';
    return '等待';
  }
  function badge(status) {
    return '<span class="ops-v2-status ' + kind(status) + '"><i></i>' + label(status) + '</span>';
  }
  function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—'; }
  function stamp(value) {
    var n = Date.parse(String(value || '').replace(' ', 'T'));
    return Number.isFinite(n) ? n : 0;
  }
  function duration(row) {
    if (row && row.duration && String(row.duration) !== '0') return String(row.duration);
    var start = stamp(row && row.startTime);
    var end = stamp(row && row.endTime) || (kind(row && row.status) === 'running' ? Date.now() : 0);
    if (!start || !end || end < start) return '—';
    var sec = Math.floor((end - start) / 1000), h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    return h ? h + '时' + m + '分' + s + '秒' : m ? m + '分' + s + '秒' : s + '秒';
  }
  function processId(row) { return String((row && (row.id || row.processInstanceId)) || ''); }
  function active(row) { return kind(row && row.status) === 'running'; }
  function newest(a, b) { return (stamp(b.startTime || b.submitTime) || Number(b.id) || 0) - (stamp(a.startTime || a.submitTime) || Number(a.id) || 0); }

  function mountLayout() {
    page = document.getElementById('page-operations');
    if (!page || page.dataset.opsV2Mounted === 'true') return;
    page.dataset.opsV2Mounted = 'true';
    page.innerHTML = '<section class="ops-v2-shell">' +
      '<div class="ops-v2-heading"><div><h1>调度运维</h1><p>实时监控和管理 DolphinScheduler 工作流运行状态，查看实例、任务执行过程和真实 Worker 日志。</p></div>' +
      '<div class="ops-v2-heading-actions"><span class="ops-v2-health"><i></i><b data-ops-scheduler-state>检测中</b></span><span class="ops-v2-updated" data-ops-updated>—</span><button type="button" class="ops-v2-refresh" data-ops-refresh><i class="ri-refresh-line"></i>刷新</button></div></div>' +
      '<div class="ops-v2-kpis">' +
      '<article class="ops-v2-kpi"><span class="ops-v2-kpi-icon blue"><i class="ri-file-list-3-line"></i></span><div><b data-ops-kpi="total">0</b><span>总实例数</span><small>真实流程实例</small></div></article>' +
      '<article class="ops-v2-kpi"><span class="ops-v2-kpi-icon green"><i class="ri-checkbox-circle-line"></i></span><div><b data-ops-kpi="success">0</b><span>成功实例</span><small data-ops-rate="success">成功率 —</small></div></article>' +
      '<article class="ops-v2-kpi"><span class="ops-v2-kpi-icon red"><i class="ri-close-circle-line"></i></span><div><b data-ops-kpi="failed">0</b><span>失败实例</span><small data-ops-rate="failed">失败率 —</small></div></article>' +
      '<article class="ops-v2-kpi"><span class="ops-v2-kpi-icon blue"><i class="ri-time-line"></i></span><div><b data-ops-kpi="running">0</b><span>运行中</span><small>实时状态</small></div></article>' +
      '<article class="ops-v2-kpi"><span class="ops-v2-kpi-icon gray"><i class="ri-hourglass-line"></i></span><div><b data-ops-kpi="pending">0</b><span>待运行</span><small>等待调度执行</small></div></article></div>' +
      '<div class="ops-v2-workspace"><section class="ops-v2-list-pane">' +
      '<div class="ops-v2-tabs"><button class="on" data-ops-tab="process">流程实例</button><button data-ops-tab="task">任务实例</button><button data-ops-tab="failed">失败告警 <span class="ops-v2-tab-count" data-ops-failed-count>0</span></button></div>' +
      '<div class="ops-v2-filters"><label class="ops-v2-search"><i class="ri-search-line"></i><input type="search" data-ops-search placeholder="请输入工作流名称或实例 ID"></label><select data-ops-status><option value="all">全部状态</option><option value="running">运行中</option><option value="success">成功</option><option value="failed">失败/终止</option><option value="pending">等待</option></select><button type="button" class="ops-v2-reset" data-ops-reset>重置</button></div>' +
      '<div class="ops-v2-table-wrap"><table class="ops-v2-table"><thead data-ops-table-head></thead><tbody data-ops-table-body><tr><td class="ops-v2-empty">正在读取真实调度实例…</td></tr></tbody></table></div>' +
      '<div class="ops-v2-pagination"><span data-ops-total>共 0 条</span><div><button type="button" data-ops-prev><i class="ri-arrow-left-s-line"></i></button><span data-ops-page>1 / 1</span><button type="button" data-ops-next><i class="ri-arrow-right-s-line"></i></button><select data-ops-page-size><option value="10">10 条/页</option><option value="20">20 条/页</option><option value="50">50 条/页</option></select></div></div></section>' +
      '<aside class="ops-v2-detail-pane"><div class="ops-v2-detail-empty" data-ops-detail-empty><i class="ri-pulse-line"></i><b>选择一个流程实例查看详情</b><span>展示真实 Task Instance 执行过程和 Worker 日志</span></div>' +
      '<div class="ops-v2-detail-content" data-ops-detail hidden><div class="ops-v2-detail-head"><div><span>实例详情</span><h2 data-ops-detail-name>—</h2></div><div class="ops-v2-detail-actions"><button type="button" data-ops-detail-action="refresh"><i class="ri-refresh-line"></i>刷新</button><button type="button" data-ops-detail-action="rerun">重跑</button><button type="button" class="danger" data-ops-detail-action="stop">终止</button></div></div>' +
      '<div class="ops-v2-info-grid" data-ops-info></div><section class="ops-v2-section"><div class="ops-v2-section-title"><b>任务执行过程</b><span data-ops-task-count>0 个任务</span></div><div class="ops-v2-progress" data-ops-progress></div></section>' +
      '<section class="ops-v2-section"><div class="ops-v2-section-title"><b>任务列表</b></div><div class="ops-v2-task-table-wrap"><table class="ops-v2-task-table"><thead><tr><th>任务名称</th><th>类型</th><th>状态</th><th>开始时间</th><th>耗时</th><th>操作</th></tr></thead><tbody data-ops-task-body></tbody></table></div></section>' +
      '<section class="ops-v2-section ops-v2-log-section"><div class="ops-v2-section-title"><div><b data-ops-log-title>实时日志</b><span data-ops-log-subtitle>请选择任务</span></div><div class="ops-v2-log-actions"><label><input type="checkbox" data-ops-auto-log checked> 自动刷新</label><button type="button" data-ops-log-action="refresh"><i class="ri-refresh-line"></i>刷新</button><button type="button" data-ops-log-action="copy"><i class="ri-file-copy-line"></i>复制</button><button type="button" data-ops-log-action="fullscreen"><i class="ri-fullscreen-line"></i>全屏</button></div></div><pre class="ops-v2-log" data-ops-log>请选择任务查看真实 Worker 日志</pre></section></div></aside></div></section>';
  }

  function counts() {
    var result = { total: state.processes.length, success: 0, failed: 0, running: 0, pending: 0 };
    state.processes.forEach(function (row) { result[kind(row.status)] += 1; });
    return result;
  }
  function renderKpis() {
    var c = counts();
    Object.keys(c).forEach(function (key) { var el = page.querySelector('[data-ops-kpi="' + key + '"]'); if (el) el.textContent = c[key].toLocaleString(); });
    var successRate = page.querySelector('[data-ops-rate="success"]');
    var failedRate = page.querySelector('[data-ops-rate="failed"]');
    if (successRate) successRate.textContent = '成功率 ' + (c.total ? (c.success * 100 / c.total).toFixed(1) + '%' : '—');
    if (failedRate) failedRate.textContent = '失败率 ' + (c.total ? (c.failed * 100 / c.total).toFixed(1) + '%' : '—');
    var available = state.dashboard.schedulerAvailable !== false;
    var health = page.querySelector('.ops-v2-health');
    if (health) health.classList.toggle('down', !available);
    var healthText = page.querySelector('[data-ops-scheduler-state]');
    if (healthText) healthText.textContent = available ? 'DolphinScheduler 正常' : 'DolphinScheduler 不可用';
    var updated = page.querySelector('[data-ops-updated]');
    if (updated) updated.textContent = '最后更新：' + time(new Date().toISOString());
    var failedCount = page.querySelector('[data-ops-failed-count]');
    if (failedCount) failedCount.textContent = state.failed.length;
  }
  function sourceRows() {
    var rows = state.tab === 'task' ? state.tasks : state.tab === 'failed' ? state.failed : state.processes;
    var q = state.search.trim().toLowerCase(), filter = state.status;
    return rows.filter(function (row) {
      var hay = [row.id, row.processInstanceId, row.name, row.taskType, row.processDefinitionCode].join(' ').toLowerCase();
      return (!q || hay.indexOf(q) >= 0) && (filter === 'all' || kind(row.status) === filter);
    }).sort(newest);
  }
  function renderTable() {
    var head = page.querySelector('[data-ops-table-head]'), body = page.querySelector('[data-ops-table-body]');
    if (!head || !body) return;
    var processMode = state.tab === 'process';
    head.innerHTML = processMode ? '<tr><th>实例 ID</th><th>工作流名称</th><th>状态</th><th>开始时间</th><th>结束时间</th><th>运行时长</th><th>操作</th></tr>' : '<tr><th>任务实例 ID</th><th>任务名称</th><th>任务类型</th><th>状态</th><th>开始时间</th><th>运行时长</th><th>操作</th></tr>';
    var all = sourceRows(), maxPage = Math.max(1, Math.ceil(all.length / state.pageSize));
    if (state.page > maxPage) state.page = maxPage;
    var rows = all.slice((state.page - 1) * state.pageSize, state.page * state.pageSize);
    if (!rows.length) body.innerHTML = '<tr><td colspan="7" class="ops-v2-empty">暂无符合条件的真实调度数据</td></tr>';
    else if (processMode) body.innerHTML = rows.map(function (row) {
      var id = processId(row), selected = id === state.selectedProcessId;
      return '<tr class="' + (selected ? 'selected' : '') + '"><td class="ops-v2-id">#' + esc(id) + '</td><td><b>' + esc(row.name || '—') + '</b></td><td>' + badge(row.status) + '</td><td>' + time(row.startTime) + '</td><td>' + time(row.endTime) + '</td><td>' + esc(duration(row)) + '</td><td><div class="ops-v2-row-actions"><button data-ops-action="detail" data-instance-id="' + esc(id) + '">详情</button><button data-ops-action="log" data-instance-id="' + esc(id) + '">日志</button>' + (active(row) ? '<button class="danger" data-ops-action="stop" data-instance-id="' + esc(id) + '">终止</button>' : '<button data-ops-action="rerun" data-instance-id="' + esc(id) + '">重跑</button>') + '</div></td></tr>';
    }).join('');
    else body.innerHTML = rows.map(function (row) {
      var id = String(row.id || ''), pid = String(row.processInstanceId || '');
      return '<tr><td class="ops-v2-id">#' + esc(id) + '</td><td><b>' + esc(row.name || '—') + '</b><small class="ops-v2-sub">流程实例 #' + esc(pid || '—') + '</small></td><td>' + esc(row.taskType || '—') + '</td><td>' + badge(row.status) + '</td><td>' + time(row.startTime) + '</td><td>' + esc(duration(row)) + '</td><td><button class="ops-v2-link" data-ops-task-log="' + esc(id) + '" data-process-id="' + esc(pid) + '">查看日志</button></td></tr>';
    }).join('');
    page.querySelector('[data-ops-total]').textContent = '共 ' + all.length + ' 条';
    page.querySelector('[data-ops-page]').textContent = state.page + ' / ' + maxPage;
    page.querySelector('[data-ops-prev]').disabled = state.page <= 1;
    page.querySelector('[data-ops-next]').disabled = state.page >= maxPage;
  }
  function selectedProcess() {
    return state.processes.find(function (row) { return processId(row) === state.selectedProcessId; }) || null;
  }
  function selectedTasks() {
    return state.tasks.filter(function (task) { return String(task.processInstanceId || '') === state.selectedProcessId; }).sort(function (a, b) { return newest(b, a); });
  }
  function renderDetail() {
    var empty = page.querySelector('[data-ops-detail-empty]'), content = page.querySelector('[data-ops-detail]'), process = selectedProcess();
    var has = !!process;
    empty.hidden = has; content.hidden = !has;
    if (!has) return;
    page.querySelector('[data-ops-detail-name]').innerHTML = esc(process.name || ('流程实例 #' + state.selectedProcessId)) + ' ' + badge(process.status);
    page.querySelector('[data-ops-info]').innerHTML = '<div><span>实例 ID</span><b>#' + esc(state.selectedProcessId) + '</b></div><div><span>状态</span><b>' + badge(process.status) + '</b></div><div><span>开始时间</span><b>' + time(process.startTime) + '</b></div><div><span>结束时间</span><b>' + time(process.endTime) + '</b></div><div><span>运行时长</span><b>' + esc(duration(process)) + '</b></div><div><span>流程定义 Code</span><b>' + esc(process.processDefinitionCode || '—') + '</b></div>';
    var stop = page.querySelector('[data-ops-detail-action="stop"]'), rerun = page.querySelector('[data-ops-detail-action="rerun"]');
    stop.hidden = !active(process); rerun.hidden = active(process);
    var tasks = selectedTasks();
    page.querySelector('[data-ops-task-count]').textContent = tasks.length + ' 个任务';
    var progress = page.querySelector('[data-ops-progress]'), tbody = page.querySelector('[data-ops-task-body]');
    if (!tasks.length) {
      progress.innerHTML = '<div class="ops-v2-empty-inline">暂未生成 Task Instance</div>';
      tbody.innerHTML = '<tr><td colspan="6" class="ops-v2-empty">暂无任务实例</td></tr>';
      return;
    }
    progress.innerHTML = tasks.map(function (task, index) {
      return '<button class="ops-v2-step ' + kind(task.status) + ' ' + (String(task.id) === state.selectedTaskId ? 'on' : '') + '" data-ops-task-select="' + esc(task.id) + '"><span class="ops-v2-step-icon">' + (kind(task.status) === 'success' ? '✓' : kind(task.status) === 'failed' ? '×' : kind(task.status) === 'running' ? '▶' : '○') + '</span><b>' + esc(task.name || ('Task #' + task.id)) + '</b><small>' + esc(task.taskType || '任务') + '</small><em>' + label(task.status) + ' · ' + esc(duration(task)) + '</em></button>' + (index < tasks.length - 1 ? '<span class="ops-v2-step-line"></span>' : '');
    }).join('');
    tbody.innerHTML = tasks.map(function (task) {
      return '<tr class="' + (String(task.id) === state.selectedTaskId ? 'selected' : '') + '"><td><b>' + esc(task.name || ('Task #' + task.id)) + '</b></td><td>' + esc(task.taskType || '—') + '</td><td>' + badge(task.status) + '</td><td>' + time(task.startTime) + '</td><td>' + esc(duration(task)) + '</td><td><button class="ops-v2-link" data-ops-task-select="' + esc(task.id) + '">查看日志</button></td></tr>';
    }).join('');
  }
  function loadLog(taskId, scrollBottom) {
    if (!taskId) return Promise.resolve();
    state.selectedTaskId = String(taskId); renderDetail();
    var task = state.tasks.find(function (row) { return String(row.id) === state.selectedTaskId; }) || {};
    page.querySelector('[data-ops-log-title]').textContent = '实时日志 · ' + (task.name || ('Task #' + taskId));
    page.querySelector('[data-ops-log-subtitle]').textContent = 'Task Instance ID: ' + taskId + ' · ' + label(task.status);
    var pre = page.querySelector('[data-ops-log]'); pre.textContent = '正在读取真实 Worker 日志…';
    return request('/operations/task-instances/' + encodeURIComponent(taskId) + '/log').then(function (log) {
      pre.textContent = log || '暂无任务日志'; if (scrollBottom) pre.scrollTop = pre.scrollHeight;
    }).catch(function (error) { pre.textContent = error.message || '日志读取失败'; });
  }
  function refreshAll() {
    if (state.loading) return Promise.resolve();
    state.loading = true;
    return Promise.all([request('/dashboard/operations'), request('/operations/process-instances'), request('/operations/task-instances'), request('/operations/failed-tasks')]).then(function (values) {
      state.dashboard = values[0] || {}; state.processes = values[1] || []; state.tasks = values[2] || []; state.failed = values[3] || [];
      renderKpis(); renderTable(); renderDetail(); schedulePoll();
    }).catch(function (error) { notify(error.message || '调度数据刷新失败', true); }).finally(function () { state.loading = false; });
  }
  function openProcess(id, autoLog) {
    state.selectedProcessId = String(id || ''); state.selectedTaskId = '';
    renderTable(); renderDetail();
    var tasks = selectedTasks();
    if (autoLog && tasks.length) loadLog(tasks[tasks.length - 1].id, true);
    schedulePoll();
  }
  function mutate(action, id) {
    var text = action === 'stop' ? '终止' : '重跑';
    if (!confirm('确定' + text + ' DolphinScheduler 流程实例 #' + id + ' 吗？')) return;
    request('/operations/process-instances/' + encodeURIComponent(id) + '/' + action, { method: 'POST' }).then(function () {
      notify(text + '指令已提交'); setTimeout(refreshAll, 600);
    }).catch(function (error) { notify(error.message || text + '失败', true); });
  }
  function schedulePoll() {
    clearTimeout(state.poll); state.poll = null;
    var process = selectedProcess(), tasks = selectedTasks();
    if (!process || (!active(process) && !tasks.some(active))) return;
    state.poll = setTimeout(function () {
      refreshAll().then(function () {
        if (state.selectedTaskId && page.querySelector('[data-ops-auto-log]').checked) loadLog(state.selectedTaskId, true);
      });
    }, 3000);
  }
  function copyLog() {
    var value = page.querySelector('[data-ops-log]').textContent || '';
    if (!value) return;
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(value).then(function () { notify('日志已复制'); });
  }
  function bind() {
    page.addEventListener('click', function (event) {
      var tab = event.target.closest('[data-ops-tab]');
      if (tab) {
        state.tab = tab.dataset.opsTab; state.page = 1;
        page.querySelectorAll('[data-ops-tab]').forEach(function (button) { button.classList.toggle('on', button === tab); });
        renderTable(); return;
      }
      var action = event.target.closest('[data-ops-action]');
      if (action) {
        var id = action.dataset.instanceId, type = action.dataset.opsAction;
        if (type === 'detail' || type === 'log') openProcess(id, type === 'log'); else mutate(type, id); return;
      }
      var task = event.target.closest('[data-ops-task-select]'); if (task) { loadLog(task.dataset.opsTaskSelect, true); return; }
      var taskLog = event.target.closest('[data-ops-task-log]'); if (taskLog) { openProcess(taskLog.dataset.processId, false); loadLog(taskLog.dataset.opsTaskLog, true); return; }
      var detail = event.target.closest('[data-ops-detail-action]');
      if (detail) { var type2 = detail.dataset.opsDetailAction; if (type2 === 'refresh') refreshAll(); else mutate(type2, state.selectedProcessId); return; }
      var logAction = event.target.closest('[data-ops-log-action]');
      if (logAction) {
        var type3 = logAction.dataset.opsLogAction;
        if (type3 === 'refresh' && state.selectedTaskId) loadLog(state.selectedTaskId, true);
        else if (type3 === 'copy') copyLog();
        else if (type3 === 'fullscreen') page.classList.toggle('ops-log-fullscreen');
        return;
      }
      if (event.target.closest('[data-ops-refresh]')) refreshAll();
      else if (event.target.closest('[data-ops-reset]')) { state.search = ''; state.status = 'all'; state.page = 1; page.querySelector('[data-ops-search]').value = ''; page.querySelector('[data-ops-status]').value = 'all'; renderTable(); }
      else if (event.target.closest('[data-ops-prev]')) { state.page = Math.max(1, state.page - 1); renderTable(); }
      else if (event.target.closest('[data-ops-next]')) { state.page += 1; renderTable(); }
    });
    page.querySelector('[data-ops-search]').addEventListener('input', function (event) { state.search = event.target.value; state.page = 1; renderTable(); });
    page.querySelector('[data-ops-status]').addEventListener('change', function (event) { state.status = event.target.value; state.page = 1; renderTable(); });
    page.querySelector('[data-ops-page-size]').addEventListener('change', function (event) { state.pageSize = Number(event.target.value) || 10; state.page = 1; renderTable(); });
  }
  function boot() {
    mountLayout(); if (!page) return; bind(); refreshAll();
  }
  window.platformOperationsEnhance = function (dashboard) { state.dashboard = dashboard || state.dashboard; if (page) renderKpis(); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot); else boot();
})();
