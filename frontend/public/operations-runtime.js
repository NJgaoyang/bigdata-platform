(function () {
  'use strict';

  var state = { instanceId: '', process: null, tasks: [], selectedTaskId: '', timer: null, open: false, loading: false };
  var escapeHtml = function (value) { return String(value == null ? '' : value).replace(/[&<>'"]/g, function (char) { return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]; }); };
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
  function notify(message, error) {
    var toast = document.getElementById('toast');
    if (!toast) return;
    var icon = toast.querySelector('i'); if (icon) icon.className = error ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';
    var label = toast.querySelector('span'); if (label) label.textContent = message;
    toast.classList.add('show'); clearTimeout(notify.timer); notify.timer = setTimeout(function () { toast.classList.remove('show'); }, 2800);
  }
  function statusKind(status) {
    var value = String(status || '').toUpperCase();
    if (/SUCCESS|FINISH/.test(value)) return 'success';
    if (/FAIL|ERROR|KILL|STOP/.test(value)) return 'failed';
    if (/RUNNING|SUBMITTED|WAITING|READY|DELAY|DISPATCH/.test(value)) return 'running';
    return 'pending';
  }
  function statusLabel(status) {
    var kind = statusKind(status);
    if (kind === 'success') return '成功';
    if (kind === 'failed') return /STOP|KILL/.test(String(status || '').toUpperCase()) ? '已终止' : '失败';
    if (kind === 'running') return '运行中';
    return '等待中';
  }
  function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—'; }
  function elapsed(start, end, fallback) {
    if (fallback) return String(fallback);
    var a = start ? new Date(String(start).replace(' ', 'T')).getTime() : NaN;
    var b = end ? new Date(String(end).replace(' ', 'T')).getTime() : Date.now();
    if (!Number.isFinite(a) || !Number.isFinite(b) || b < a) return '—';
    var seconds = Math.floor((b - a) / 1000), h = Math.floor(seconds / 3600), m = Math.floor((seconds % 3600) / 60), s = seconds % 60;
    return h ? h + 'h ' + m + 'm ' + s + 's' : m ? m + 'm ' + s + 's' : s + 's';
  }
  function ensureDrawer() {
    var host = document.getElementById('ops-instance-backdrop');
    if (host) return host;
    host = document.createElement('div');
    host.id = 'ops-instance-backdrop'; host.className = 'ops-instance-backdrop'; host.setAttribute('aria-hidden', 'true');
    host.innerHTML = '<aside class="ops-instance-drawer" role="dialog" aria-modal="true" aria-labelledby="ops-instance-title">' +
      '<div class="ops-instance-head"><div class="ops-instance-title"><h2 id="ops-instance-title">实例详情</h2><p>真实数据来自 DolphinScheduler</p></div><span class="spacer"></span><span class="ops-auto-refresh"><i></i>运行中每 3 秒刷新</span><button type="button" class="ops-instance-close" aria-label="关闭">×</button></div>' +
      '<div class="ops-instance-body"><div class="ops-instance-summary"></div><div class="ops-instance-toolbar"><span class="ops-toolbar-note">查看流程实例下真实 Task Instance 的执行状态与 Worker 日志</span><button type="button" data-ops-drawer-action="refresh"><i class="ri-refresh-line"></i> 刷新</button><button type="button" data-ops-drawer-action="rerun"><i class="ri-restart-line"></i> 重跑</button><button type="button" class="danger" data-ops-drawer-action="stop"><i class="ri-stop-fill"></i> 终止</button></div>' +
      '<section class="ops-section tasks"><div class="ops-section-head">任务执行过程 <small class="ops-task-count">0 个任务</small></div><div class="ops-task-list"><div class="ops-task-empty">正在读取任务实例…</div></div></section>' +
      '<section class="ops-section logs"><div class="ops-section-head">任务日志 <small>Worker Log</small></div><div class="ops-log-wrap"><div class="ops-log-toolbar"><div class="ops-log-task"><b>请选择任务节点</b><small>点击上方任务即可查看真实执行日志</small></div><button type="button" data-ops-log-action="refresh"><i class="ri-refresh-line"></i> 刷新</button><button type="button" data-ops-log-action="copy"><i class="ri-file-copy-line"></i> 复制</button><button type="button" data-ops-log-action="fullscreen"><i class="ri-fullscreen-line"></i> 全屏</button></div><div class="ops-log-empty">请选择一个任务节点查看日志</div></div></section></div></aside>';
    document.body.appendChild(host);
    host.querySelector('.ops-instance-close').onclick = closeDrawer;
    host.onclick = function (event) { if (event.target === host) closeDrawer(); };
    host.addEventListener('click', onDrawerClick);
    return host;
  }
  function closeDrawer() {
    clearTimeout(state.timer); state.timer = null; state.open = false;
    var host = ensureDrawer(); host.classList.remove('open', 'log-fullscreen'); host.setAttribute('aria-hidden', 'true');
  }
  function renderSummary() {
    var host = ensureDrawer(), box = host.querySelector('.ops-instance-summary'), p = state.process || {};
    box.innerHTML = '<div class="ops-summary-card primary"><span>流程实例</span><b title="' + escapeHtml(p.name || '调度实例') + '">' + escapeHtml(p.name || '调度实例') + '</b></div>' +
      '<div class="ops-summary-card"><span>实例 ID</span><b>#' + escapeHtml(state.instanceId || '—') + '</b></div>' +
      '<div class="ops-summary-card"><span>状态</span><b class="ops-state ' + statusKind(p.status) + '">' + statusLabel(p.status) + '</b></div>' +
      '<div class="ops-summary-card"><span>耗时</span><b>' + escapeHtml(elapsed(p.startTime, p.endTime, p.duration)) + '</b></div>';
    var stop = host.querySelector('[data-ops-drawer-action="stop"]'); if (stop) stop.hidden = statusKind(p.status) !== 'running';
    var rerun = host.querySelector('[data-ops-drawer-action="rerun"]'); if (rerun) rerun.hidden = statusKind(p.status) === 'running';
  }
  function renderTasks() {
    var host = ensureDrawer(), list = host.querySelector('.ops-task-list'), count = host.querySelector('.ops-task-count');
    if (count) count.textContent = state.tasks.length + ' 个任务';
    if (!state.tasks.length) { list.innerHTML = '<div class="ops-task-empty">该流程实例暂未生成 Task Instance</div>'; return; }
    list.innerHTML = state.tasks.map(function (task, index) {
      var id = String(task.id || ''); var on = id === String(state.selectedTaskId);
      var meta = [task.taskType || '任务', time(task.startTime), task.workerGroup ? 'Worker Group: ' + task.workerGroup : '', task.host ? 'Host: ' + task.host : ''].filter(Boolean).join(' · ');
      return '<button type="button" class="ops-task-row ' + (on ? 'on' : '') + '" data-ops-task-id="' + escapeHtml(id) + '"><span class="ops-task-index">' + (index + 1) + '</span><span class="ops-task-main"><span class="ops-task-name"><b>' + escapeHtml(task.name || ('Task #' + id)) + '</b></span><span class="ops-task-meta">' + escapeHtml(meta) + '</span></span><span class="ops-task-state"><b class="ops-state ' + statusKind(task.status) + '">' + statusLabel(task.status) + '</b><span class="ops-task-duration">' + escapeHtml(elapsed(task.startTime, task.endTime, task.duration)) + '</span></span></button>';
    }).join('');
  }
  function renderLoading() {
    var host = ensureDrawer();
    host.querySelector('.ops-instance-summary').innerHTML = '<div class="ops-summary-card primary"><span>流程实例</span><b>正在读取真实实例…</b></div>';
    host.querySelector('.ops-task-list').innerHTML = '<div class="ops-task-empty">正在读取 DolphinScheduler Task Instance…</div>';
  }
  function loadLog(taskId, forceBottom) {
    if (!taskId) return Promise.resolve();
    state.selectedTaskId = String(taskId); renderTasks();
    var host = ensureDrawer(), task = state.tasks.find(function (item) { return String(item.id) === String(taskId); }) || {};
    var label = host.querySelector('.ops-log-task');
    if (label) label.innerHTML = '<b>' + escapeHtml(task.name || ('Task #' + taskId)) + '</b><small>Task Instance #' + escapeHtml(taskId) + ' · ' + escapeHtml(task.taskType || '任务') + ' · ' + statusLabel(task.status) + '</small>';
    var wrap = host.querySelector('.ops-log-wrap'); var old = wrap.querySelector('.ops-log, .ops-log-empty');
    if (old) old.outerHTML = '<div class="ops-log-empty">正在读取 Worker 日志…</div>';
    return request('/operations/task-instances/' + encodeURIComponent(taskId) + '/log').then(function (log) {
      var current = wrap.querySelector('.ops-log, .ops-log-empty');
      if (current) current.outerHTML = '<pre class="ops-log"></pre>';
      var pre = wrap.querySelector('.ops-log'); pre.textContent = log || '暂无任务日志';
      if (forceBottom || pre.scrollHeight - pre.scrollTop - pre.clientHeight < 80) pre.scrollTop = pre.scrollHeight;
    }).catch(function (error) {
      var current = wrap.querySelector('.ops-log, .ops-log-empty');
      if (current) current.outerHTML = '<div class="ops-log-empty">' + escapeHtml(error.message || '日志读取失败') + '</div>';
    });
  }
  function scheduleRefresh() {
    clearTimeout(state.timer); state.timer = null;
    if (!state.open) return;
    var active = statusKind((state.process || {}).status) === 'running' || state.tasks.some(function (task) { return statusKind(task.status) === 'running'; });
    if (active) state.timer = setTimeout(function () { refresh(false); }, 3000);
  }
  function refresh(initial, autoLog) {
    if (!state.instanceId || state.loading) return Promise.resolve();
    state.loading = true; if (initial) renderLoading();
    return Promise.all([request('/operations/process-instances'), request('/operations/task-instances?processInstanceId=' + encodeURIComponent(state.instanceId))]).then(function (values) {
      state.process = (values[0] || []).find(function (item) { return String(item.id || item.processInstanceId) === String(state.instanceId); }) || { id: state.instanceId, name: '流程实例 #' + state.instanceId, status: 'UNKNOWN' };
      state.tasks = (values[1] || []).slice().sort(function (a, b) { return String(a.startTime || a.id || '').localeCompare(String(b.startTime || b.id || '')); });
      renderSummary(); renderTasks();
      var selectedExists = state.tasks.some(function (task) { return String(task.id) === String(state.selectedTaskId); });
      if (!selectedExists && autoLog && state.tasks.length) state.selectedTaskId = String(state.tasks[state.tasks.length - 1].id);
      if (state.selectedTaskId) return loadLog(state.selectedTaskId, !!initial);
    }).catch(function (error) { notify(error.message || '实例详情读取失败', true); }).finally(function () { state.loading = false; scheduleRefresh(); });
  }
  function openDrawer(instanceId, autoLog) {
    if (!instanceId) return;
    state.instanceId = String(instanceId); state.process = null; state.tasks = []; state.selectedTaskId = ''; state.open = true;
    var host = ensureDrawer(); host.classList.add('open'); host.classList.remove('log-fullscreen'); host.setAttribute('aria-hidden', 'false');
    refresh(true, !!autoLog);
  }
  function mutate(action, instanceId) {
    var label = action === 'stop' ? '终止' : '重跑';
    if (!window.confirm('确定' + label + ' DolphinScheduler 流程实例 #' + instanceId + ' 吗？')) return;
    request('/operations/process-instances/' + encodeURIComponent(instanceId) + '/' + action, { method: 'POST' }).then(function () {
      notify(label + '指令已提交'); window.dispatchEvent(new CustomEvent('platform:operations-refresh'));
      if (state.open && String(state.instanceId) === String(instanceId)) setTimeout(function () { refresh(false); }, 600);
    }).catch(function (error) { notify(error.message || label + '失败', true); });
  }
  function onDrawerClick(event) {
    var task = event.target.closest('[data-ops-task-id]'); if (task) { loadLog(task.dataset.opsTaskId, true); return; }
    var action = event.target.closest('[data-ops-drawer-action]');
    if (action) {
      var type = action.dataset.opsDrawerAction;
      if (type === 'refresh') refresh(false);
      else if (type === 'stop' || type === 'rerun') mutate(type, state.instanceId);
      return;
    }
    var logAction = event.target.closest('[data-ops-log-action]'); if (!logAction) return;
    var type = logAction.dataset.opsLogAction, host = ensureDrawer();
    if (type === 'refresh') { if (state.selectedTaskId) loadLog(state.selectedTaskId, true); }
    else if (type === 'copy') {
      var pre = host.querySelector('.ops-log'); if (!pre) return notify('暂无可复制的日志', true);
      var value = pre.textContent || '';
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(value).then(function () { notify('日志已复制'); });
      else { var area = document.createElement('textarea'); area.value = value; document.body.appendChild(area); area.select(); document.execCommand('copy'); area.remove(); notify('日志已复制'); }
    } else if (type === 'fullscreen') host.classList.toggle('log-fullscreen');
  }
  function bindRowActions() {
    var page = document.getElementById('page-operations'); if (!page || page.dataset.opsRuntimeBound === 'true') return;
    page.dataset.opsRuntimeBound = 'true';
    page.addEventListener('click', function (event) {
      var button = event.target.closest('[data-ops-action]'); if (!button) return;
      var id = button.dataset.instanceId, action = button.dataset.opsAction;
      if (action === 'detail') openDrawer(id, false);
      else if (action === 'log') openDrawer(id, true);
      else if (action === 'stop' || action === 'rerun') mutate(action, id);
    });
  }
  function bindQuickActions() {
    document.querySelectorAll('#page-operations .quickbox').forEach(function (button) {
      var label = button.textContent.trim();
      if (label === '实例搜索') button.onclick = function () { var id = window.prompt('请输入 DolphinScheduler Process Instance ID'); if (id) openDrawer(id, false); };
      else if (label === '查看日志') button.onclick = function () { var id = window.prompt('请输入 DolphinScheduler Process Instance ID'); if (id) openDrawer(id, true); };
      else if (label === '重跑实例') button.onclick = function () { var id = window.prompt('请输入 DolphinScheduler Process Instance ID'); if (id) mutate('rerun', id); };
      else if (label === '终止实例') button.onclick = function () { var id = window.prompt('请输入 DolphinScheduler Process Instance ID'); if (id) mutate('stop', id); };
    });
  }
  window.platformOperationsEnhance = function () { bindRowActions(); bindQuickActions(); };
  window.platformOperationsOpen = openDrawer;
  ensureDrawer(); bindRowActions(); bindQuickActions();
})();
