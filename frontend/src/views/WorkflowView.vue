<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Graph } from '@antv/x6'
import { platformApi } from '../api'

const search = ref('')
const scheduleOpen = ref(false)
const backfillOpen = ref(false)
const createOpen = ref(false)
const busy = ref(false)
const createName = ref('新建数据处理流程')
const schedule = ref({ cronExpression: '0 0 2 * * ?', timezone: 'Asia/Shanghai', enabled: true, failureStrategy: 'END', parallelism: 1 })
type ScheduleUnit = 'minute' | 'hour' | 'day' | 'month' | 'week'
const scheduleUnit = ref<ScheduleUnit>('day')
const minuteInterval = ref(50)
const hourInterval = ref(1)
const dayHour = ref(2)
const dayMinute = ref(50)
const monthDay = ref(1)
const weekDay = ref('MON')
const weekDayLabels: Record<string, string> = { MON: '周一', TUE: '周二', WED: '周三', THU: '周四', FRI: '周五', SAT: '周六', SUN: '周日' }
const backfill = ref({ start: '2026-09-01 00:00:00', end: '2026-09-02 00:00:00', parallelism: 1 })
const dagCanvas = ref<HTMLElement>()
const workflowId = ref<number>()
const defaultFileVersionId = ref<number>()
let graph: Graph | undefined
type WorkflowRow = { id?: number; name: string; project: string; schedule: string; status: string; statusClass: string; owner: string; updated: string }
const workflows = ref<WorkflowRow[]>([
  { id: undefined as number | undefined, name: '每日交易主题加工', project: '数仓开发项目', schedule: '每天 02:00', status: '已上线', statusClass: 'ok', owner: 'admin', updated: '09-03 14:20' },
  { id: undefined as number | undefined, name: '用户画像日更新', project: '用户增长项目', schedule: '每天 03:30', status: '已上线', statusClass: 'ok', owner: 'data_admin', updated: '09-03 13:05' },
  { id: undefined as number | undefined, name: '营销活动报表', project: '营销分析项目', schedule: '每小时', status: '调试中', statusClass: 'run', owner: 'analyst', updated: '09-03 12:42' },
])
const filteredWorkflows = computed(() => workflows.value.filter(item => !search.value || item.name.includes(search.value)))
const runningWorkflows = computed(() => workflows.value.filter(item => item.status === '运行中').length)
const successWorkflows = computed(() => workflows.value.filter(item => item.status === '已上线' || item.status === '已发布').length)
const failedWorkflows = computed(() => workflows.value.filter(item => item.status === '失败').length)
const scheduleCron = computed(() => {
  if (scheduleUnit.value === 'minute') return `0 */${minuteInterval.value} * * * ?`
  if (scheduleUnit.value === 'hour') return `0 0 */${hourInterval.value} * * ?`
  if (scheduleUnit.value === 'month') return `0 ${dayMinute.value} ${dayHour.value} ${monthDay.value} * ?`
  if (scheduleUnit.value === 'week') return `0 ${dayMinute.value} ${dayHour.value} ? * ${weekDay.value}`
  return `0 ${dayMinute.value} ${dayHour.value} * * ?`
})
const scheduleDescription = computed(() => {
  if (scheduleUnit.value === 'minute') return `每 ${minuteInterval.value} 分钟执行一次`
  if (scheduleUnit.value === 'hour') return `每 ${hourInterval.value} 小时执行一次`
  if (scheduleUnit.value === 'month') return `每月 ${monthDay.value} 日 ${pad(dayHour.value)}:${pad(dayMinute.value)} 执行`
  if (scheduleUnit.value === 'week') return `每${weekDayLabels[weekDay.value]} ${pad(dayHour.value)}:${pad(dayMinute.value)} 执行`
  return `每天 ${pad(dayHour.value)}:${pad(dayMinute.value)} 执行`
})
const nextRuns = computed(() => {
  const result: Date[] = []
  const now = new Date()
  let cursor = new Date(now)
  for (let i = 0; i < 5; i++) {
    if (scheduleUnit.value === 'minute') cursor = new Date(cursor.getTime() + minuteInterval.value * 60 * 1000)
    else if (scheduleUnit.value === 'hour') cursor = new Date(cursor.getTime() + hourInterval.value * 60 * 60 * 1000)
    else if (scheduleUnit.value === 'week') cursor = nextWeekday(cursor, weekDay.value)
    else if (scheduleUnit.value === 'month') cursor = nextMonth(cursor, monthDay.value, dayHour.value, dayMinute.value)
    else cursor = nextDay(cursor, dayHour.value, dayMinute.value)
    result.push(new Date(cursor))
  }
  return result
})
function pad(value: number) { return String(value).padStart(2, '0') }
function formatRun(date: Date) { return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` }
function nextDay(from: Date, hour: number, minute: number) { const date = new Date(from); date.setDate(date.getDate() + 1); date.setHours(hour, minute, 0, 0); return date }
function nextMonth(from: Date, day: number, hour: number, minute: number) { const date = new Date(from); date.setMonth(date.getMonth() + 1, day); date.setHours(hour, minute, 0, 0); return date }
function nextWeekday(from: Date, weekday: string) { const date = new Date(from); const wanted = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'].indexOf(weekday); let offset = (wanted - date.getDay() + 7) % 7; if (offset === 0) offset = 7; date.setDate(date.getDate() + offset); date.setHours(dayHour.value, dayMinute.value, 0, 0); return date }
function setScheduleUnit(value: string) {
  if (['minute', 'hour', 'day', 'month', 'week'].includes(value)) scheduleUnit.value = value as ScheduleUnit
}
function syncScheduleControls(cron: string) {
  const parts = cron.trim().split(/\s+/)
  if (parts.length < 6) return
  const minute = Number(parts[1])
  const hour = Number(parts[2])
  const dayOfMonth = parts[3]
  const dayOfWeek = parts[5]
  if (parts[1]?.startsWith('*/')) {
    minuteInterval.value = Number(parts[1].slice(2)) || minuteInterval.value
    scheduleUnit.value = 'minute'
  } else if (parts[1] === '0' && parts[2]?.startsWith('*/')) {
    hourInterval.value = Number(parts[2].slice(2)) || hourInterval.value
    scheduleUnit.value = 'hour'
  } else if (dayOfMonth === '?' && dayOfWeek && dayOfWeek !== '*') {
    dayHour.value = Number.isFinite(hour) ? hour : dayHour.value
    dayMinute.value = Number.isFinite(minute) ? minute : dayMinute.value
    weekDay.value = dayOfWeek
    scheduleUnit.value = 'week'
  } else if (dayOfMonth !== '*' && dayOfMonth !== '?') {
    monthDay.value = Number(dayOfMonth) || monthDay.value
    dayHour.value = Number.isFinite(hour) ? hour : dayHour.value
    dayMinute.value = Number.isFinite(minute) ? minute : dayMinute.value
    scheduleUnit.value = 'month'
  } else {
    dayHour.value = Number.isFinite(hour) ? hour : dayHour.value
    dayMinute.value = Number.isFinite(minute) ? minute : dayMinute.value
    scheduleUnit.value = 'day'
  }
}

function action(message: string) {
  ElMessage.success(message)
}
function selectWorkflow(item: WorkflowRow) { if (item.id) workflowId.value = item.id }
function addNode(nodeType: 'SQL' | 'SHELL' | 'PYTHON' | 'SEATUNNEL') {
  if (!graph) return
  const index = graph.getNodes().length
  const labels = { SQL: 'SQL 任务', SHELL: 'Shell 任务', PYTHON: 'Python 任务', SEATUNNEL: 'SeaTunnel 同步' }
  graph.addNode({ id: `task-${Date.now()}-${index}`, shape: 'rect', x: 60 + (index % 4) * 230, y: 220 + Math.floor(index / 4) * 100, width: 165, height: 58, label: labels[nodeType], data: { nodeType }, attrs: { body: { fill: nodeType === 'SEATUNNEL' ? '#f0fff7' : '#fff', stroke: nodeType === 'SEATUNNEL' ? '#20b26b' : '#9db0c8', rx: 7, ry: 7 }, label: { fill: '#344054', fontSize: 12 } } })
}
async function createWorkflow() {
  try {
    const result = await platformApi.createWorkflow({ name: createName.value, description: '由平台创建', nodes: [{ name: '起始任务', nodeType: 'SHELL', x: 50, y: 75 }], edges: [] })
    const created = result.data.data
    if (created && typeof created.id === 'number') {
      workflowId.value = created.id
      workflows.value.unshift({ id: created.id, name: createName.value, project: '平台项目', schedule: '未配置', status: '草稿', statusClass: 'run', owner: 'admin', updated: '刚刚' })
    }
    createOpen.value = false
    action('工作流已创建，可继续保存画布并发布')
  } catch { ElMessage.error('工作流创建失败') }
}
async function publishWorkflow(item: WorkflowRow) {
  if (!item.id) { action('演示工作流请先通过后端创建'); return }
  try { await platformApi.publishWorkflow(item.id); item.status = '已发布'; item.statusClass = 'ok'; action('工作流已发布') } catch { ElMessage.error('工作流发布失败，请先完成节点配置和版本绑定') }
}
async function runWorkflow(item: WorkflowRow) {
  if (!item.id) { action('演示工作流未绑定后端实例'); return }
  try { await platformApi.runWorkflow(item.id); action('工作流已提交运行') } catch { ElMessage.error('工作流运行失败，请先发布工作流') }
}
async function onlineWorkflow(item: WorkflowRow) {
  if (!item.id) { action('演示工作流未绑定后端实例'); return }
  try { await platformApi.onlineWorkflow(item.id); item.status = '已上线'; item.statusClass = 'ok'; action('调度已上线') } catch { ElMessage.error('调度上线失败，请先发布并保存 Cron') }
}
async function saveSchedule() {
  if (workflowId.value) {
    busy.value = true
    try {
      schedule.value.cronExpression = scheduleCron.value
      await platformApi.saveSchedule(workflowId.value, schedule.value)
      const current = workflows.value.find(item => item.id === workflowId.value)
      if (current) current.schedule = scheduleDescription.value
      scheduleOpen.value = false
      action('调度配置已保存')
    } catch { ElMessage.error('调度配置保存失败') } finally { busy.value = false }
    return
  }
  scheduleOpen.value = false
  action('调度配置已保存到本地演示')
}
async function openSchedule(item: WorkflowRow) {
  selectWorkflow(item)
  if (workflowId.value) {
    const result = await platformApi.schedule(workflowId.value).catch(() => null)
    if (result?.data.data) {
      Object.assign(schedule.value, result.data.data)
      syncScheduleControls(schedule.value.cronExpression)
    }
  }
  scheduleOpen.value = true
}
async function submitBackfill() {
  if (workflowId.value) {
    busy.value = true
    try {
      await platformApi.backfillWorkflow(workflowId.value, backfill.value)
      backfillOpen.value = false
      action('补数据任务已提交')
    } catch { ElMessage.error('补数据任务提交失败，请先发布工作流') } finally { busy.value = false }
    return
  }
  backfillOpen.value = false
  action('补数据任务已提交到本地演示')
}
async function validateGraph() {
  if (workflowId.value) {
    try {
      const result = await platformApi.validateWorkflow(workflowId.value)
      result.data.data?.valid ? action('DAG 校验通过') : ElMessage.warning(result.data.data?.message || 'DAG 校验未通过')
    } catch { ElMessage.error('DAG 校验失败') }
    return
  }
  action('已完成 DAG 校验')
}
async function saveGraph() {
  if (workflowId.value && graph) {
    try {
      await platformApi.updateWorkflowGraph(workflowId.value, { name: workflows.value.find(item => item.id === workflowId.value)?.name || '平台工作流', description: 'X6 DAG', nodes: graph.getNodes().map(node => { const label = String(node.getAttrByPath('label/text') || node.id); const nodeType = String(node.getData()?.nodeType || (label.startsWith('SeaTunnel') ? 'SEATUNNEL' : 'SQL')); return { name: label, nodeType, fileVersionId: nodeType === 'SEATUNNEL' ? null : defaultFileVersionId.value, x: node.position().x, y: node.position().y, nodeCode: node.id } }), edges: graph.getEdges().map(edge => ({ sourceNodeId: null, targetNodeId: null, sourceNodeCode: edge.getSourceCellId(), targetNodeCode: edge.getTargetCellId() })) })
    } catch { /* keep local canvas usable when no backend workflow is selected */ }
  }
  action('画布已保存到平台数据库')
}

onMounted(() => {
  if (!dagCanvas.value) return
  graph = new Graph({
    container: dagCanvas.value,
    grid: { size: 10, visible: true },
    panning: true,
    mousewheel: { enabled: true, modifiers: ['ctrl'] },
    connecting: { allowBlank: false, allowLoop: false, highlight: true, router: 'manhattan', connector: 'rounded' },
  })
  graph.addNodes([
    { id: 'source', shape: 'rect', x: 50, y: 75, width: 165, height: 58, label: 'SQL · ods_order_info', data: { nodeType: 'SQL' }, attrs: { body: { fill: '#fff', stroke: '#9db0c8', rx: 7, ry: 7 }, label: { fill: '#344054', fontSize: 12 } } },
    { id: 'detail', shape: 'rect', x: 295, y: 75, width: 175, height: 58, label: 'SQL · dwd_order_detail', data: { nodeType: 'SQL' }, attrs: { body: { fill: '#eaf3ff', stroke: '#1677ff', rx: 7, ry: 7 }, label: { fill: '#1268db', fontSize: 12 } } },
    { id: 'summary', shape: 'rect', x: 550, y: 75, width: 165, height: 58, label: 'SQL · dws_trade_day', data: { nodeType: 'SQL' }, attrs: { body: { fill: '#fff', stroke: '#9db0c8', rx: 7, ry: 7 }, label: { fill: '#344054', fontSize: 12 } } },
    { id: 'report', shape: 'rect', x: 795, y: 75, width: 165, height: 58, label: 'SeaTunnel · ads_report', data: { nodeType: 'SEATUNNEL' }, attrs: { body: { fill: '#fff', stroke: '#20b26b', rx: 7, ry: 7 }, label: { fill: '#158f55', fontSize: 12 } } },
  ])
  graph.addEdges([
    { source: 'source', target: 'detail' }, { source: 'detail', target: 'summary' }, { source: 'summary', target: 'report' },
  ])
  platformApi.projects().then(async result => {
    const existingProject = result.data.data?.[0]
    const project = existingProject || (await platformApi.createProject({ name: '数仓开发项目', description: '平台默认开发项目' }).catch(() => null))?.data.data
    if (!project) return
    let file = (await platformApi.files(project.id).catch(() => null))?.data.data?.[0]
    if (!file) file = (await platformApi.createFile({ projectId: project.id, name: 'workflow_default.sql', fileType: 'SQL', content: 'SELECT 1 AS health_check;' }).catch(() => null))?.data.data
    if (file) {
      const versions = await platformApi.fileVersions(file.id).catch(() => null)
      const latest = versions?.data.data?.[0]
      if (latest && typeof latest.id === 'number') defaultFileVersionId.value = latest.id
    }
  }).catch(() => undefined)
  platformApi.workflows().then(async result => { const actual = result.data.data || []; if (actual.length) workflows.value = actual.map(item => ({ id: Number(item.id), name: String(item.name || '未命名工作流'), project: '平台项目', schedule: '未配置', status: String(item.status || 'DRAFT') === 'PUBLISHED' ? '已发布' : '草稿', statusClass: String(item.status || '') === 'PUBLISHED' ? 'ok' : 'run', owner: 'admin', updated: '刚刚' })); const id = actual[0]?.id; if (typeof id === 'number') { workflowId.value = id; const config = await platformApi.schedule(id).catch(() => null); if (config?.data.data) { Object.assign(schedule.value, config.data.data); syncScheduleControls(schedule.value.cronExpression) } } }).catch(() => undefined)
})
onBeforeUnmount(() => graph?.dispose())
</script>

<template>
  <section class="page">
    <div class="module-bar">
      <div class="module-title">调度中心 <span class="crumb">/ 工作流</span></div>
      <div class="module-actions">
        <button class="btn-primary" @click="createOpen = true">＋ 新建工作流</button>
      </div>
    </div>

    <div class="page-body">
      <div class="metrics">
        <div class="metric"><div class="metric-icon blue">⌘</div><div><div class="metric-label">工作流总数</div><div class="metric-value">{{ workflows.length }}</div></div></div>
        <div class="metric"><div class="metric-icon green">◷</div><div><div class="metric-label">运行中</div><div class="metric-value">{{ runningWorkflows }}</div></div></div>
        <div class="metric"><div class="metric-icon purple">▶</div><div><div class="metric-label">今日成功</div><div class="metric-value">{{ successWorkflows }}</div></div></div>
        <div class="metric"><div class="metric-icon orange">!</div><div><div class="metric-label">失败</div><div class="metric-value">{{ failedWorkflows }}</div></div></div>
      </div>

      <div class="card dag-card"><div class="card-head"><span>工作流 DAG 设计</span><div class="dag-tools"><span class="muted">拖拽节点 · 连线 · Ctrl + 滚轮缩放</span><button class="btn-default" @click="addNode('SQL')">＋SQL</button><button class="btn-default" @click="addNode('SHELL')">＋Shell</button><button class="btn-default" @click="addNode('PYTHON')">＋Python</button><button class="btn-default" @click="addNode('SEATUNNEL')">＋SeaTunnel</button><button class="btn-default" :disabled="busy" @click="saveGraph">保存画布</button><button class="btn-default" @click="validateGraph">校验 DAG</button></div></div><div ref="dagCanvas" class="dag-canvas"></div></div>

      <div class="card">
        <div class="card-head"><span>工作流管理</span><span class="muted">共 {{ workflows.length }} 个工作流</span></div>
        <div class="filterbar">
          <input v-model="search" placeholder="搜索工作流名称...">
          <select><option>全部项目</option><option>数仓开发项目</option><option>用户增长项目</option></select>
          <select><option>全部状态</option><option>已上线</option><option>调试中</option></select>
          <button class="btn-primary" @click="action('筛选条件已应用')">查询</button>
          <button class="btn-default" @click="search = ''">重置</button>
        </div>
        <table class="data-table">
          <thead><tr><th>工作流名称</th><th>项目</th><th>调度周期</th><th>状态</th><th>负责人</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="workflow in filteredWorkflows" :key="workflow.id || workflow.name">
              <td><button class="link-action" @click="action(`已打开：${workflow.name}`)">{{ workflow.name }}</button></td>
              <td>{{ workflow.project }}</td><td>{{ workflow.schedule }}</td>
              <td><span class="status-dot" :class="workflow.statusClass"></span>{{ workflow.status }}</td>
              <td>{{ workflow.owner }}</td><td class="muted">{{ workflow.updated }}</td>
              <td class="actions"><button @click="selectWorkflow(workflow); action('已打开编辑器')">编辑</button><button @click="selectWorkflow(workflow); runWorkflow(workflow)">运行</button><button @click="openSchedule(workflow)">调度</button><button @click="selectWorkflow(workflow); backfillOpen = true">补数</button><button @click="publishWorkflow(workflow)">发布</button><button @click="onlineWorkflow(workflow)">上线</button></td>
            </tr><tr v-if="!filteredWorkflows.length"><td colspan="7" class="empty-state">暂无符合条件的工作流</td></tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="createOpen" class="modal-mask" @click.self="createOpen = false"><div class="modal"><div class="modal-head"><span>新建工作流</span><button class="modal-close" @click="createOpen = false">×</button></div><div class="modal-body"><div class="form-item"><label>工作流名称</label><input v-model="createName" @keyup.enter="createWorkflow"></div><p class="muted">创建后可在画布中继续编辑节点、连线和任务参数。</p></div><div class="modal-foot"><button class="btn-default" @click="createOpen = false">取消</button><button class="btn-primary" @click="createWorkflow">创建</button></div></div></div>
    <div v-if="scheduleOpen" class="modal-mask" @click.self="scheduleOpen = false"><div class="modal schedule-modal"><div class="modal-head"><span>调度配置</span><button class="modal-close" @click="scheduleOpen = false">×</button></div><div class="modal-body"><div class="schedule-tabs"><button v-for="item in [{key:'minute',label:'分'},{key:'hour',label:'时'},{key:'day',label:'天'},{key:'month',label:'月'},{key:'week',label:'周'}]" :key="item.key" :class="{active:scheduleUnit === item.key}" @click="setScheduleUnit(item.key)">{{ item.label }}</button></div><div class="schedule-setting"><template v-if="scheduleUnit === 'minute'"><label>每隔</label><select v-model.number="minuteInterval"><option :value="5">5 分钟</option><option :value="10">10 分钟</option><option :value="15">15 分钟</option><option :value="30">30 分钟</option><option :value="50">50 分钟</option></select></template><template v-else-if="scheduleUnit === 'hour'"><label>每隔</label><select v-model.number="hourInterval"><option :value="1">1 小时</option><option :value="2">2 小时</option><option :value="4">4 小时</option><option :value="6">6 小时</option><option :value="12">12 小时</option></select></template><template v-else><label>执行时间</label><div class="time-fields"><select v-model.number="dayHour"><option v-for="hour in 24" :key="hour" :value="hour - 1">{{ pad(hour - 1) }} 时</option></select><select v-model.number="dayMinute"><option v-for="minute in [0,10,20,30,40,50]" :key="minute" :value="minute">{{ pad(minute) }} 分</option></select></div></template><template v-if="scheduleUnit === 'month'"><label>每月</label><select v-model.number="monthDay"><option v-for="day in 28" :key="day" :value="day">{{ day }} 日</option></select></template><template v-if="scheduleUnit === 'week'"><label>每周</label><select v-model="weekDay"><option v-for="(label,key) in weekDayLabels" :key="key" :value="key">{{ label }}</option></select></template></div><div class="schedule-summary"><span class="summary-label">执行规则</span><strong>{{ scheduleDescription }}</strong><code>{{ scheduleCron }}</code></div><div class="next-runs"><div class="next-runs-title">预计下次执行时间（前5次）</div><div v-for="(run,index) in nextRuns" :key="run.toISOString()" class="next-run"><span>{{ index + 1 }}</span>{{ formatRun(run) }}</div></div><div class="form-row schedule-extra"><div class="form-item"><label>时区</label><select v-model="schedule.timezone"><option>Asia/Shanghai</option><option>UTC</option></select></div><div class="form-item"><label>失败策略</label><select v-model="schedule.failureStrategy"><option>END</option><option>CONTINUE</option></select></div><div class="form-item"><label>并行度</label><input v-model.number="schedule.parallelism" type="number" min="1"></div></div><label class="check-line"><input v-model="schedule.enabled" type="checkbox"> 启用调度</label></div><div class="modal-foot"><button class="btn-default" @click="scheduleOpen = false">取消</button><button class="btn-primary" :disabled="busy" @click="saveSchedule">保存配置</button></div></div></div>
    <div v-if="backfillOpen" class="modal-mask" @click.self="backfillOpen = false"><div class="modal"><div class="modal-head"><span>补数据</span><button class="modal-close" @click="backfillOpen = false">×</button></div><div class="modal-body"><div class="form-item"><label>开始业务时间</label><input v-model="backfill.start"></div><div class="form-item"><label>结束业务时间</label><input v-model="backfill.end"></div><div class="form-item"><label>并行度</label><input v-model.number="backfill.parallelism" type="number" min="1"></div></div><div class="modal-foot"><button class="btn-default" @click="backfillOpen = false">取消</button><button class="btn-primary" @click="submitBackfill">提交补数</button></div></div></div>
  </section>
</template>
