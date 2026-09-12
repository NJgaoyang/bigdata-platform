(function () {
  'use strict';

  var state = {
    dashboard: null, tab: 'process', processes: [], tasks: [], failed: [],
    selectedProcessId: '', selectedProcess: null, processTasks: [], selectedTaskId: '',
    search: '', status: 'all', page: 1, pageSize: 10, poll: null, loading: false
  };
  var page;
  function esc(value) { return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) { return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]; }); }
  function request(url, options) {
    options = options || {};
    var token = localStorage.getItem('platform_access_token');
    options.headers = Object.assign({ Accept: 'application/json' }, options.headers || {});
    if (token) options.headers.Authorization = 'Bearer ' + token;
    if (options.body) options.headers['Content-Type'] = 'application/json';
    return fetch('/api' + url, options).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (payload) {
        if (!response.ok || payload.success === false) throw new Error(payload.message || '接口请求失败');
        return payload.data;
      });
    });
  }
  function notify(message, isError) {
    var toast = document.getElementById('toast');
    if (!toast) return;
    var icon = toast.querySelector('i'); if (icon) icon.className = isError ? 'ri-error-warning-fill' : 'ri-checkbox-circle-fill';
    var label = toast.querySelector('span'); if (label) label.textContent = message;
    toast.classList.add('show'); clearTimeout(notify.timer); notify.timer = setTimeout(function () { toast.classList.remove('show'); }, 2800);
  }
  function kind(status) {
    var v = String(status || '').toUpperCase();
    if (/SUCCESS|FINISH/.test(v)) return 'success';
    if (/FAIL|ERROR|KILL|STOP/.test(v)) return 'failed';
    if (/RUNNING|SUBMITTED|READY|DISPATCH|DELAY/.test(v)) return 'running';
    return 'pending';
  }
  function label(status) {
    var k = kind(status), raw = String(status || '').toUpperCase();
    if (k === 'success') return '成功';
    if (k === 'failed') return /STOP|KILL/.test(raw) ? '已终止' : '失败';
    if (k === 'running') return '运行中';
    return '等待';
  }
  function badge(status) { return '<span class="ops-v2-status ' + kind(status) + '"><i></i>' + label(status) + '</span>'; }
  function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '—'; }
  function stamp(value) { var n = Date.parse(String(value || '').replace(' ', 'T')); return Number.isFinite(n) ? n : 0; }
  function duration(row) {
    if (row && row.duration && String(row.duration) !== '0') return String(row.duration);
    var a = stamp(row && row.startTime), b = stamp(row && row.endTime) || (kind(row && row.status) === 'running' ? Date.now() : 0);
    if (!a || !b || b < a) return '—';
    var sec = Math.floor((b-a)/1000), h=Math.floor(sec/3600), m=Math.floor((sec%3600)/60), s=sec%60;
    return h ? h+'时'+m+'分'+s+'秒' : m ? m+'分'+s+'秒' : s+'秒';
  }
  function byNewest(a,b) { return (stamp(b.startTime || b.submitTime) || Number(b.id)||0) - (stamp(a.startTime || a.submitTime) || Number(a.id)||0); }
  function processId(row) { return String((row && (row.id || row.processInstanceId)) || ''); }
  function active(row) { return kind(row && row.status) === 'running'; }
  function pct(part,total) { return total ? ((Number(part||0)/Number(total))*100).toFixed(1)+'%' : '—'; }

  function renderKpis() {
    var d=state.dashboard || {}, t=d.tasks || {};
    ['total','success','failed','running','pending'].forEach(function (key) { var el=page.querySelector('[data-ops-kpi="'+key+'"]'); if (el) el.textContent=Number(t[key]||0).toLocaleString(); });
    ['success','failed','running'].forEach(function (key) { var el=page.querySelector('[data-ops-rate="'+key+'"]'); if (el) el.textContent=(key==='running'?'运行中 ':'')+(key==='success'?'成功率 ':key==='failed'?'失败率 ':'')+pct(t[key],t.total); });
    var health=page.querySelector('.ops-v2-health'), healthText=page.querySelector('[data-ops-scheduler-state]');
    if (health) health.classList.toggle('down', d.schedulerAvailable === false);
    if (healthText) healthText.textContent=d.schedulerAvailable === false ? 'DolphinScheduler 不可用' : 'DolphinScheduler 正常';
    var updated=page.querySelector('[data-ops-updated]'); if (updated) updated.textContent='最后更新：'+time(d.generatedAt || new Date().toISOString());
    var failedCount=page.querySelector('[data-ops-failed-count]'); if (failedCount) failedCount.textContent=Number(t.failed||0);
  }
  function rowsForTab() {
    var rows = state.tab === 'task' ? state.tasks : state.tab === 'failed' ? state.failed : state.processes;
    var q=state.search.trim().toLowerCase(), sk=state.status;
    return rows.filter(function (row) {
      var hay=[row.id,row.processInstanceId,row.name,row.taskType,row.processDefinitionCode].join(' ').toLowerCase();
      return (!q || hay.indexOf(q)>=0) && (sk==='all' || kind(row.status)===sk);
    }).sort(byNewest);
  }
  function renderTable() {
    var head=page.querySelector('[data-ops-table-head]'), body=page.querySelector('[data-ops-table-body]'); if (!head||!body) return;
    var isProcess=state.tab==='process', cols=isProcess?7:7;
    head.innerHTML=isProcess?'<tr><th>实例 ID</th><th>工作流名称</th><th>状态</th><th>开始时间</th><th>结束时间</th><th>运行时长</th><th>操作</th></tr>':'<tr><th>任务实例 ID</th><th>任务名称</th><th>任务类型</th><th>状态</th><th>开始时间</th><th>运行时长</th><th>操作</th></tr>';
    var all=rowsForTab(), maxPage=Math.max(1,Math.ceil(all.length/state.pageSize)); if (state.page>maxPage) state.page=maxPage;
    var rows=all.slice((state.page-1)*state.pageSize,state.page*state.pageSize);
    if (!rows.length) body.innerHTML='<tr><td colspan="'+cols+'" class="ops-v2-empty">暂无符合条件的真实调度数据</td></tr>';
    else if (isProcess) body.innerHTML=rows.map(function (row) {
      var id=processId(row), selected=id===state.selectedProcessId;
      return '<tr class="'+(selected?'selected':'')+'" data-process-row="'+esc(id)+'"><td class="ops-v2-id">#'+esc(id)+'</td><td><b>'+esc(row.name||'—')+'</b></td><td>'+badge(row.status)+'</td><td>'+time(row.startTime)+'</td><td>'+time(row.endTime)+'</td><td>'+esc(duration(row))+'</td><td><div class="ops-v2-row-actions"><button data-ops-action="detail" data-instance-id="'+esc(id)+'">详情</button><button data-ops-action="log" data-instance-id="'+esc(id)+'">日志</button>'+(active(row)?'<button class="danger" data-ops-action="stop" data-instance-id="'+esc(id)+'">终止</button>':'<button data-ops-action="rerun" data-instance-id="'+esc(id)+'">重跑</button>')+'</div></td></tr>';
    }).join('');
    else body.innerHTML=rows.map(function (row) {
      var id=String(row.id||''), pid=String(row.processInstanceId||'');
      return '<tr><td class="ops-v2-id">#'+esc(id)+'</td><td><b>'+esc(row.name||'—')+'</b><small class="ops-v2-sub">流程实例 #'+esc(pid||'—')+'</small></td><td>'+esc(row.taskType||'—')+'</td><td>'+badge(row.status)+'</td><td>'+time(row.startTime)+'</td><td>'+esc(duration(row))+'</td><td><div class="ops-v2-row-actions"><button data-ops-task-log="'+esc(id)+'" data-process-id="'+esc(pid)+'">日志</button>'+(pid?'<button data-ops-action="detail" data-instance-id="'+esc(pid)+'">流程详情</button>':'')+'</div></td></tr>';
    }).join('');
    page.querySelector('[data-ops-total]').textContent='共 '+all.length+' 条';
    page.querySelector('[data-ops-page]').textContent=state.page+' / '+maxPage;
    page.querySelector('[data-ops-prev]').disabled=state.page<=1; page.querySelector('[data-ops-next]').disabled=state.page>=maxPage;
  }
  function renderInfo() {
    var p=state.selectedProcess || {}, info=page.querySelector('[data-ops-info]');
    page.querySelector('[data-ops-detail-name]').innerHTML=esc(p.name||('流程实例 #'+state.selectedProcessId))+' '+badge(p.status);
    info.innerHTML='<div><span>实例 ID</span><b>#'+esc(state.selectedProcessId||'—')+'</b></div><div><span>状态</span><b>'+badge(p.status)+'</b></div><div><span>开始时间</span><b>'+time(p.startTime)+'</b></div><div><span>结束时间</span><b>'+time(p.endTime)+'</b></div><div><span>运行时长</span><b>'+esc(duration(p))+'</b></div><div><span>流程定义 Code</span><b>'+esc(p.processDefinitionCode||'—')+'</b></div>';
    var stop=page.querySelector('[data-ops-detail-action="stop"]'), rerun=page.querySelector('[data-ops-detail-action="rerun"]');
    if (stop) stop.hidden=!active(p); if (rerun) rerun.hidden=active(p);
  }
  function taskOrder() { return state.processTasks.slice().sort(function(a,b){ return (stamp(a.startTime||a.submitTime)||Number(a.id)||0)-(stamp(b.startTime||b.submitTime)||Number(b.id)||0); }); }
  function renderProcessTasks() {
    var rows=taskOrder(), progress=page.querySelector('[data-ops-progress]'), body=page.querySelector('[data-ops-task-body]');
    page.querySelector('[data-ops-task-count]').textContent=rows.length+' 个任务';
    if (!rows.length) { progress.innerHTML='<div class="ops-v2-empty-inline">暂未生成 Task Instance</div>'; body.innerHTML='<tr><td colspan="6" class="ops-v2-empty">暂无任务实例</td></tr>'; return; }
    progress.innerHTML=rows.map(function (task,index) {
      return '<button class="ops-v2-step '+kind(task.status)+' '+(String(task.id)===state.selectedTaskId?'on':'')+'" data-ops-task-select="'+esc(task.id)+'"><span class="ops-v2-step-icon">'+(kind(task.status)==='success'?'✓':kind(task.status)==='failed'?'×':kind(task.status)==='running'?'▶':'○')+'</span><b>'+esc(task.name||('Task #'+task.id))+'</b><small>'+esc(task.taskType||'任务')+'</small><em>'+label(task.status)+' · '+esc(duration(task))+'</em></button>'+(index<rows.length-1?'<span class="ops-v2-step-line"></span>':'');
    }).join('');
    body.innerHTML=rows.map(function (task) { return '<tr class="'+(String(task.id)===state.selectedTaskId?'selected':'')+'"><td><b>'+esc(task.name||('Task #'+task.id))+'</b></td><td>'+esc(task.taskType||'—')+'</td><td>'+badge(task.status)+'</td><td>'+time(task.startTime)+'</td><td>'+esc(duration(task))+'</td><td><button class="ops-v2-link" data-ops-task-select="'+esc(task.id)+'">查看日志</button></td></tr>'; }).join('');
  }
  function renderDetailVisibility() {
    var empty=page.querySelector('[data-ops-detail-empty]'), content=page.querySelector('[data-ops-detail]'), has=!!state.selectedProcessId;
    empty.hidden=has; content.hidden=!has;
  }
  function loadLog(taskId, scrollBottom) {
    if (!taskId) return Promise.resolve();
    state.selectedTaskId=String(taskId); renderProcessTasks();
    var task=state.processTasks.find(function(t){return String(t.id)===state.selectedTaskId;})||{};
    page.querySelector('[data-ops-log-title]').textContent='实时日志 · '+(task.name||('Task #'+taskId));
    page.querySelector('[data-ops-log-subtitle]').textContent='Task Instance ID: '+taskId+' · '+label(task.status);
    var pre=page.querySelector('[data-ops-log]'); pre.textContent='正在读取真实 Worker 日志…';
    return request('/operations/task-instances/'+encodeURIComponent(taskId)+'/log').then(function(log){ pre.textContent=log||'暂无任务日志'; if(scrollBottom) pre.scrollTop=pre.scrollHeight; }).catch(function(e){pre.textContent=e.message||'日志读取失败';});
  }
  function loadDetail(instanceId, autoLog) {
    if (!instanceId) return Promise.resolve(); state.selectedProcessId=String(instanceId); renderDetailVisibility(); renderTable();
    return Promise.all([request('/operations/process-instances'),request('/operations/task-instances?processInstanceId='+encodeURIComponent(instanceId))]).then(function(values){
      state.processes=values[0]||state.processes; state.selectedProcess=state.processes.find(function(p){return processId(p)===state.selectedProcessId;})||{id:state.selectedProcessId,status:'UNKNOWN'}; state.processTasks=values[1]||[];
      renderInfo(); renderProcessTasks(); renderTable();
      if (state.selectedTaskId && !state.processTasks.some(function(t){return String(t.id)===state.selectedTaskId;})) state.selectedTaskId='';
      if (autoLog && !state.selectedTaskId && state.processTasks.length) state.selectedTaskId=String(taskOrder().slice(-1)[0].id);
      if (state.selectedTaskId) return loadLog(state.selectedTaskId, false);
    }).finally(schedulePoll);
  }
  function schedulePoll() {
    clearTimeout(state.poll); state.poll=null; if (!state.selectedProcessId) return;
    var should=active(state.selectedProcess)||state.processTasks.some(active); if (!should) return;
    state.poll=setTimeout(function(){ loadDetail(state.selectedProcessId, false).then(function(){ if(page.querySelector('[data-ops-auto-log]').checked && state.selectedTaskId) loadLog(state.selectedTaskId, true); }); },3000);
  }
  function loadTabData() {
    var url=state.tab==='task'?'/operations/task-instances':state.tab==='failed'?'/operations/failed-tasks':'/operations/process-instances';
    return request(url).then(function(rows){ if(state.tab==='task') state.tasks=rows||[]; else if(state.tab==='failed') state.failed=rows||[]; else state.processes=rows||[]; renderTable(); }).catch(function(e){notify(e.message||'调度数据加载失败',true);});
  }
  function refreshAll() {
    if (state.loading) return Promise.resolve(); state.loading=true;
    return Promise.all([request('/dashboard/operations'),request('/operations/process-instances')]).then(function(values){ state.dashboard=values[0]||{}; state.processes=values[1]||[]; renderKpis(); return loadTabData(); }).then(function(){ if(state.selectedProcessId) return loadDetail(state.selectedProcessId,false); }).catch(function(e){notify(e.message||'刷新失败',true);}).finally(function(){state.loading=false;});
  }
  function mutate(action,id) {
    var text=action==='stop'?'终止':'重跑'; if(!confirm('确定'+text+' DolphinScheduler 流程实例 #'+id+' 吗？')) return;
    request('/operations/process-instances/'+encodeURIComponent(id)+'/'+action,{method:'POST'}).then(function(){notify(text+'指令已提交'); setTimeout(refreshAll,600);}).catch(function(e){notify(e.message||text+'失败',true);});
  }
  function copyLog() {
    var value=page.querySelector('[data-ops-log]').textContent||''; if(!value) return;
    if(navigator.clipboard&&navigator.clipboard.writeText) navigator.clipboard.writeText(value).then(function(){notify('日志已复制');});
    else { var t=document.createElement('textarea');t.value=value;document.body.appendChild(t);t.select();document.execCommand('copy');t.remove();notify('日志已复制'); }
  }
  function mountLayout() {
    page=document.getElementById('page-operations');
    if (!page || page.querySelector('.ops-v2-shell')) return;
    page.innerHTML=`<section class="ops-v2-shell">
      <div class="ops-v2-heading"><div><h1>调度运维</h1><p>实时监控和管理 DolphinScheduler 工作流运行状态，支持查看实例详情、任务执行过程和真实 Worker 日志。</p></div><div class="ops-v2-heading-actions"><span class="ops-v2-health"><i></i><b data-ops-scheduler-state>检测中</b></span><span class="ops-v2-updated" data-ops-updated>—</span><button type="button" class="ops-v2-refresh" data-ops-refresh><i class="ri-refresh-line"></i>刷新</button></div></div>
      <div class="ops-v2-kpis">
        <article class="ops-v2-kpi"><span class="ops-v2-kpi-icon blue"><i class="ri-file-list-3-line"></i></span><div><b data-ops-kpi="total">0</b><span>总实例数</span><small>真实流程实例</small></div></article>
        <article class="ops-v2-kpi"><span class="ops-v2-kpi-icon green"><i class="ri-checkbox-circle-line"></i></span><div><b data-ops-kpi="success">0</b><span>成功实例</span><small data-ops-rate="success">成功率 —</small></div></article>
        <article class="ops-v2-kpi"><span class="ops-v2-kpi-icon red"><i class="ri-close-circle-line"></i></span><div><b data-ops-kpi="failed">0</b><span>失败实例</span><small data-ops-rate="failed">失败率 —</small></div></article>
        <article class="ops-v2-kpi"><span class="ops-v2-kpi-icon blue"><i class="ri-time-line"></i></span><div><b data-ops-kpi="running">0</b><span>运行中</span><small data-ops-rate="running">运行中 —</small></div></article>
        <article class="ops-v2-kpi"><span class="ops-v2-kpi-icon gray"><i class="ri-hourglass-line"></i></span><div><b data-ops-kpi="pending">0</b><span>待运行</span><small>等待调度执行</small></div></article>
      </div>
      <div class="ops-v2-workspace">
        <section class="ops-v2-list-pane"><div class="ops-v2-tabs"><button class="on" data-ops-tab="process">流程实例</button><button data-ops-tab="task">任务实例</button><button data-ops-tab="failed">失败告警 <span class="ops-v2-tab-count" data-ops-failed-count>0</span></button></div><div class="ops-v2-filters"><label class="ops-v2-search"><i class="ri-search-line"></i><input type="search" data-ops-search placeholder="请输入工作流名称或实例 ID"></label><select data-ops-status><option value="all">全部状态</option><option value="running">运行中</option><option value="success">成功</option><option value="failed">失败/终止</option><option value="pending">等待</option></select><button type="button" class="ops-v2-reset" data-ops-reset>重置</button></div><div class="ops-v2-table-wrap"><table class="ops-v2-table"><thead data-ops-table-head></thead><tbody data-ops-table-body><tr><td class="ops-v2-empty">正在读取真实调度实例…</td></tr></tbody></table></div><div class="ops-v2-pagination"><span data-ops-total>共 0 条</span><div><button type="button" data-ops-prev><i class="ri-arrow-left-s-line"></i></button><span data-ops-page>1 / 1</span><button type="button" data-ops-next><i class="ri-arrow-right-s-line"></i></button><select data-ops-page-size><option value="10">10 条/页</option><option value="20">20 条/页</option><option value="50">50 条/页</option></select></div></div></section>
        <aside class="ops-v2-detail-pane"><div class="ops-v2-detail-empty" data-ops-detail-empty><i class="ri-pulse-line"></i><b>选择一个流程实例查看详情</b><span>这里会展示真实 Task Instance 执行过程和 Worker 日志</span></div><div class="ops-v2-detail-content" data-ops-detail hidden><div class="ops-v2-detail-head"><div><span>实例详情</span><h2 data-ops-detail-name>—</h2></div><div class="ops-v2-detail-actions"><button type="button" data-ops-detail-action="refresh"><i class="ri-refresh-line"></i>刷新</button><button type="button" data-ops-detail-action="rerun">重跑</button><button type="button" class="danger" data-ops-detail-action="stop">终止</button></div></div><div class="ops-v2-info-grid" data-ops-info></div>
        <section class="ops-v2-section"><div class="ops-v2-section-title"><b>任务执行过程</b><span data-ops-task-count>0 个任务</span></div><div class="ops-v2-progress" data-ops-progress></div></section><section class="ops-v2-section"><div class="ops-v2-section-title"><b>任务列表</b></div><div class="ops-v2-task-table-wrap"><table class="ops-v2-task-table"><thead><tr><th>任务名称</th><th>类型</th><th>状态</th><th>开始时间</th><th>耗时</th><th>操作</th></tr></thead><tbody data-ops-task-body></tbody></table></div></section><section class="ops-v2-section ops-v2-log-section"><div class="ops-v2-section-title"><div><b data-ops-log-title>实时日志</b><span data-ops-log-subtitle>请选择任务</span></div><div class="ops-v2-log-actions"><label><input type="checkbox" data-ops-auto-log checked> 自动刷新</label><button type="button" data-ops-log-action="refresh"><i class="ri-refresh-line"></i>刷新</button><button type="button" data-ops-log-action="copy"><i class="ri-file-copy-line"></i>复制</button><button type="button" data-ops-log-action="fullscreen"><i class="ri-fullscreen-line"></i>全屏</button></div></div><pre class="ops-v2-log" data-ops-log>请选择任务查看真实 Worker 日志</pre></section></div></aside>
      </div></section>`;
  }
  function bind() {
    mountLayout(); if(!page||page.dataset.opsV2Bound==='true') return; page.dataset.opsV2Bound='true';
    page.addEventListener('click',function(e){
      var tab=e.target.closest('[data-ops-tab]'); if(tab){state.tab=tab.dataset.opsTab;state.page=1;page.querySelectorAll('[data-ops-tab]').forEach(function(x){x.classList.toggle('on',x===tab);});loadTabData();return;}
      var action=e.target.closest('[data-ops-action]'); if(action){var id=action.dataset.instanceId,a=action.dataset.opsAction;if(a==='detail'||a==='log')loadDetail(id,a==='log');else mutate(a,id);return;}
      var task=e.target.closest('[data-ops-task-select]'); if(task){loadLog(task.dataset.opsTaskSelect,true);return;}
      var globalTask=e.target.closest('[data-ops-task-log]'); if(globalTask){var pid=globalTask.dataset.processId,id=globalTask.dataset.opsTaskLog;if(pid)loadDetail(pid,false).then(function(){loadLog(id,true);});return;}
      var da=e.target.closest('[data-ops-detail-action]'); if(da){var a2=da.dataset.opsDetailAction;if(a2==='refresh')loadDetail(state.selectedProcessId,false);else mutate(a2,state.selectedProcessId);return;}
      var la=e.target.closest('[data-ops-log-action]'); if(la){var a3=la.dataset.opsLogAction;if(a3==='refresh'&&state.selectedTaskId)loadLog(state.selectedTaskId,true);else if(a3==='copy')copyLog();else if(a3==='fullscreen')page.classList.toggle('ops-log-fullscreen');return;}
      if(e.target.closest('[data-ops-refresh]'))refreshAll(); else if(e.target.closest('[data-ops-reset]')){state.search='';state.status='all';state.page=1;page.querySelector('[data-ops-search]').value='';page.querySelector('[data-ops-status]').value='all';renderTable();} else if(e.target.closest('[data-ops-prev]')){state.page=Math.max(1,state.page-1);renderTable();} else if(e.target.closest('[data-ops-next]')){state.page++;renderTable();}
    });
    page.querySelector('[data-ops-search]').addEventListener('input',function(e){state.search=e.target.value;state.page=1;renderTable();});
    page.querySelector('[data-ops-status]').addEventListener('change',function(e){state.status=e.target.value;state.page=1;renderTable();});
    page.querySelector('[data-ops-page-size]').addEventListener('change',function(e){state.pageSize=Number(e.target.value)||10;state.page=1;renderTable();});
  }
  window.platformOperationsEnhance=function(dashboard){ bind(); state.dashboard=dashboard||state.dashboard||{}; renderKpis(); if(!state.processes.length) refreshAll(); else renderTable(); };
  function boot(){ bind(); refreshAll(); }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',boot); else boot();
})();
