<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import { integrationApi, type IntegrationAttempt, type IntegrationBatch, type IntegrationCursor, type IntegrationInstance, type IntegrationTask, type IntegrationTaskPayload, type IntegrationTaskSummary, type IntegrationTaskSchedule } from '../../api/domain'

const tasks = ref<IntegrationTask[]>([])
const sources = ref<DataSourceView[]>([])
const latest = ref<Record<number, IntegrationInstance | undefined>>({})
const latestBatch = ref<Record<number, IntegrationBatch | undefined>>({})
const taskSummaries = ref<Record<number, IntegrationTaskSummary | undefined>>({})
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTask = ref<IntegrationTask | null>(null)
const detailBatches = ref<IntegrationBatch[]>([])
const detailInstances = ref<IntegrationInstance[]>([])
const detailSchedule = ref<IntegrationTaskSchedule | null>(null)
const detailTab = ref('overview')
const editorStep = ref(0)
const editorMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const keyword = ref('')
const modeFilter = ref('')
const tableKeyword = ref('')
const sourceDbs = ref<string[]>([])
const targetDbs = ref<string[]>([])
const sourceTables = ref<Array<{ name: string; comment?: string }>>([])
const targetTables = reactive<Record<string, string>>({})
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyTask = ref<IntegrationTask | null>(null)
const history = ref<IntegrationInstance[]>([])
const batches = ref<IntegrationBatch[]>([])
const historyAttempts = ref<Record<number, IntegrationAttempt[]>>({})
const historyLogs = ref<Record<string, string>>({})
const selectedHistoryLogKey = ref('')
const selectedHistoryLogTitle = ref('')
const historyLogMaximized = ref(false)
const seatunnelPreview = ref('')
const previewLoading = ref(false)
const scheduleConfigMode = ref<'quick'|'custom'>('custom')
const scheduleTab = ref<'minute'|'hour'|'day'|'month'|'week'>('minute')
const schedulePreviewTimes = ref<string[]>([])
const quickPreset = ref('DAILY_0200')
let historyPollTimer: ReturnType<typeof setInterval> | null = null
const backfillVisible = ref(false)
const backfillTask = ref<IntegrationTask | null>(null)
const backfillSaving = ref(false)
const cursorVisible = ref(false)
const cursorTask = ref<IntegrationTask | null>(null)
const cursorSaving = ref(false)
const cursorState = reactive<IntegrationCursor>({ taskId: 0, cursorColumn: '', cursorValue: '' })
const backfillForm = reactive({ where: '', startLabel: '', endLabel: '' })

const form = reactive({
  name: '',
  description: '',
  sourceDataSourceId: 0,
  targetDataSourceId: 0,
  sourceDatabase: '',
  targetDatabase: 'ods',
  selectedTables: [] as string[],
  targetPrefix: '',
  where: '',
  targetStrategy: 'AUTO_EVOLVE',
  scheduleEnabled: true,
  cronExpression: '0 0 2 * * ?',
  scheduleMinute: '0',
  scheduleHour: '2',
  scheduleDay: '*',
  scheduleMonth: '*',
  scheduleWeek: '?',
  timezone: 'Asia/Shanghai'
})

const mysql = computed(() => sources.value.filter(source => source.type === 'MYSQL'))
const starrocks = computed(() => sources.value.filter(source => source.type === 'STARROCKS'))
const filteredSourceTables = computed(() => {
  const q = tableKeyword.value.trim().toLowerCase()
  return q ? sourceTables.value.filter(table => `${table.name} ${table.comment || ''}`.toLowerCase().includes(q)) : sourceTables.value
})
const minuteOptions = Array.from({ length: 60 }, (_, value) => String(value))
const hourOptions = Array.from({ length: 24 }, (_, value) => String(value))
const dayOptions = ['*', ...Array.from({ length: 31 }, (_, value) => String(value + 1))]
const monthOptions = ['*', ...Array.from({ length: 12 }, (_, value) => String(value + 1))]
const weekOptions = [
  { label:'不指定', value:'?' }, { label:'周日', value:'1' }, { label:'周一', value:'2' },
  { label:'周二', value:'3' }, { label:'周三', value:'4' }, { label:'周四', value:'5' },
  { label:'周五', value:'6' }, { label:'周六', value:'7' }
]

function syncCronFromParts() {
  const day = form.scheduleWeek !== '?' ? '?' : form.scheduleDay
  form.cronExpression = `0 ${form.scheduleMinute} ${form.scheduleHour} ${day} ${form.scheduleMonth} ${form.scheduleWeek}`
  void refreshSchedulePreview()
}

function selectScheduleDay(value: string) { form.scheduleDay = value; form.scheduleWeek = '?'; syncCronFromParts() }
function selectScheduleWeek(value: string) { form.scheduleWeek = value; if (value !== '?') form.scheduleDay = '*'; syncCronFromParts() }

function applyQuickPreset() {
  if (quickPreset.value === 'HOURLY') { form.scheduleMinute='0'; form.scheduleHour='*'; form.scheduleDay='*'; form.scheduleMonth='*'; form.scheduleWeek='?' }
  else if (quickPreset.value === 'DAILY_0000') { form.scheduleMinute='0'; form.scheduleHour='0'; form.scheduleDay='*'; form.scheduleMonth='*'; form.scheduleWeek='?' }
  else { form.scheduleMinute='0'; form.scheduleHour='2'; form.scheduleDay='*'; form.scheduleMonth='*'; form.scheduleWeek='?' }
  syncCronFromParts()
}

async function refreshSchedulePreview() {
  if (!form.scheduleEnabled || !form.cronExpression) { schedulePreviewTimes.value = []; return }
  try { schedulePreviewTimes.value = await integrationApi.previewSchedule({ cronExpression: form.cronExpression, timezone: form.timezone, enabled: true }) }
  catch { schedulePreviewTimes.value = [] }
}

function parseCronToParts(cron?: string) {
  const parts = (cron || '').trim().split(/\s+/)
  if (parts.length >= 6) {
    form.scheduleMinute = minuteOptions.includes(parts[1]) ? parts[1] : '0'
    form.scheduleHour = [...hourOptions, '*'].includes(parts[2]) ? parts[2] : '2'
    form.scheduleDay = dayOptions.includes(parts[3]) ? parts[3] : '*'
    form.scheduleMonth = monthOptions.includes(parts[4]) ? parts[4] : '*'
    form.scheduleWeek = ['?','1','2','3','4','5','6','7'].includes(parts[5]) ? parts[5] : '?'
    if (form.scheduleDay === '?') form.scheduleDay = '*'
  } else {
    form.scheduleMinute = '0'
    form.scheduleHour = '2'
    form.scheduleDay = '*'
    form.scheduleMonth = '*'
    form.scheduleWeek = '?'
  }
  syncCronFromParts()
}

function scheduleTextFromCron(cron?: string) {
  const parts = (cron || '').trim().split(/\s+/)
  if (parts.length < 6) return '未配置'
  const minute = parts[1] || '0'
  const hourValue = parts[2] || '*'
  const dayValue = parts[3] || '*'
  const monthValue = parts[4] || '*'
  const weekValue = parts[5] || '?'
  const month = monthValue === '*' ? '每月' : `${monthValue}月`
  const day = dayValue === '*' ? '每日' : dayValue === '?' ? '' : `${dayValue}日`
  const hour = hourValue === '*' ? '每小时' : `${hourValue.padStart(2, '0')}时`
  const week = weekValue === '?' || weekValue === '*' ? '' : ` ${weekOptions.find(v => v.value === weekValue)?.label || ''}`
  return [month, day, hour, `${minute.padStart(2, '0')}分${week}`].filter(Boolean).join(' ')
}

function scheduleText() {
  return form.scheduleEnabled ? scheduleTextFromCron(form.cronExpression) : '仅手动执行'
}

const filteredTasks = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return tasks.value.filter(task => {
    const textMatch = !q || `${task.name} ${task.tables?.map(table => table.sourceTable).join(' ') || ''}`.toLowerCase().includes(q)
    const modeMatch = !modeFilter.value || task.syncMode === modeFilter.value
    return textMatch && modeMatch
  })
})

async function load() {
  loading.value = true
  try {
    const [taskRows, sourceRows] = await Promise.all([integrationApi.list(), dataSourceApi.list()])
    tasks.value = taskRows.filter(task => task.syncMode !== 'REALTIME')
    sources.value = sourceRows
    const states = await Promise.all(tasks.value.map(async task => {
      const [batchRows, instanceRows, summary] = await Promise.all([integrationApi.batches(task.id), integrationApi.instances(task.id), integrationApi.summary(task.id)])
      return { taskId: task.id, batch: batchRows[0], instance: instanceRows[0], summary }
    }))
    latestBatch.value = Object.fromEntries(states.map(item => [item.taskId, item.batch]))
    latest.value = Object.fromEntries(states.map(item => [item.taskId, item.instance]))
    taskSummaries.value = Object.fromEntries(states.map(item => [item.taskId, item.summary]))
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    loading.value = false
  }
}

function resetEditor() {
  Object.assign(form, {
    name: '',
    description: '',
    sourceDataSourceId: mysql.value[0]?.id || 0,
    targetDataSourceId: starrocks.value[0]?.id || 0,
    sourceDatabase: '',
    targetDatabase: 'ods',
    selectedTables: [],
    targetPrefix: '',
    where: '',
    targetStrategy: 'AUTO_EVOLVE',
    scheduleEnabled: true,
    cronExpression: '0 0 2 * * ?',
    scheduleMinute: '0',
    scheduleHour: '2',
    scheduleDay: '*',
    scheduleMonth: '*',
    scheduleWeek: '?',
    timezone: 'Asia/Shanghai'
  })
  sourceDbs.value = []
  targetDbs.value = []
  sourceTables.value = []
  tableKeyword.value = ''
  editorStep.value = 0
  seatunnelPreview.value = ''
  schedulePreviewTimes.value = []
  scheduleConfigMode.value = 'custom'
  scheduleTab.value = 'minute'
  editingId.value = null
  for (const key of Object.keys(targetTables)) delete targetTables[key]
}

async function openCreate() {
  editorMode.value = 'create'
  resetEditor()
  editorVisible.value = true
  await Promise.all([
    form.sourceDataSourceId ? loadSourceDatabases(true) : Promise.resolve(),
    form.targetDataSourceId ? loadTargetDatabases(true) : Promise.resolve()
  ])
  await refreshSchedulePreview()
}

async function openDetail(task: IntegrationTask) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'overview'
  detailTask.value = task
  detailBatches.value = []
  detailInstances.value = []
  detailSchedule.value = null
  try {
    const [fullTask, batchRows, instanceRows, schedule] = await Promise.all([
      integrationApi.get(task.id),
      integrationApi.batches(task.id),
      integrationApi.instances(task.id),
      integrationApi.schedule(task.id)
    ])
    detailTask.value = fullTask
    detailBatches.value = batchRows
    detailInstances.value = instanceRows
    detailSchedule.value = schedule
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    detailLoading.value = false
  }
}

async function editFromDetail() {
  if (!detailTask.value) return
  const task = detailTask.value
  detailVisible.value = false
  await openEdit(task)
}

async function openEdit(task: IntegrationTask) {
  if (isOnline(task)) return ElMessage.warning('任务已上线，请先下线后再编辑')
  editorMode.value = 'edit'
  resetEditor()
  editingId.value = task.id
  form.name = task.name
  const source = safeJson(task.sourceConfigJson)
  const target = safeJson(task.targetConfigJson)
  const transform = safeJson(task.transformConfigJson)
  const options = objectValue(transform.options)
  form.description = stringValue(options.description)
  form.sourceDataSourceId = matchDataSource('MYSQL', source)
  form.targetDataSourceId = matchDataSource('STARROCKS', target)
  form.sourceDatabase = task.tables?.[0]?.sourceDatabase || stringValue(source.database)
  form.targetDatabase = task.tables?.[0]?.targetDatabase || stringValue(target.database) || 'ods'
  form.where = stringValue(options.where)
  form.targetStrategy = strategyFromOptions(options)
  editorVisible.value = true
  await Promise.all([
    form.sourceDataSourceId ? loadSourceDatabases(false) : Promise.resolve(),
    form.targetDataSourceId ? loadTargetDatabases(false) : Promise.resolve()
  ])
  try {
    const schedule = await integrationApi.schedule(task.id)
    form.scheduleEnabled = schedule.enabled
    form.cronExpression = schedule.cronExpression || '0 0 2 * * ?'
    parseCronToParts(form.cronExpression)
    form.timezone = schedule.timezone || 'Asia/Shanghai'
  } catch {
    form.scheduleEnabled = true
    form.cronExpression = '0 0 2 * * ?'
    parseCronToParts(form.cronExpression)
    form.timezone = 'Asia/Shanghai'
  }
  form.selectedTables = (task.tables || []).map(table => table.sourceTable)
  for (const table of task.tables || []) targetTables[table.sourceTable] = table.targetTable
  form.targetPrefix = inferTargetPrefix(task)
  ensureTargetMappings()
  if (!form.sourceDataSourceId || !form.targetDataSourceId) {
    ElMessage.warning('原任务的数据源无法唯一匹配，请重新选择来源和目标数据源后保存')
  }
}

async function loadSourceDatabases(resetSelection: boolean) {
  if (!form.sourceDataSourceId) {
    sourceDbs.value = []
    sourceTables.value = []
    return
  }
  try {
    sourceDbs.value = (await integrationApi.sourceDatabases(form.sourceDataSourceId)).map(item => item.name)
    if (resetSelection || !sourceDbs.value.includes(form.sourceDatabase)) form.sourceDatabase = sourceDbs.value[0] || ''
    await loadSourceTables(resetSelection)
  } catch (error) {
    ElMessage.error(messageOf(error))
  }
}

async function loadTargetDatabases(resetSelection: boolean) {
  if (!form.targetDataSourceId) {
    targetDbs.value = []
    return
  }
  try {
    targetDbs.value = (await integrationApi.targetDatabases(form.targetDataSourceId)).map(item => item.name)
    if (resetSelection || !targetDbs.value.includes(form.targetDatabase)) {
      const preferred = targetDbs.value.includes('ods') ? 'ods' : targetDbs.value[0]
      form.targetDatabase = preferred || starrocks.value.find(item => item.id === form.targetDataSourceId)?.databaseName || 'ods'
    }
  } catch (error) {
    ElMessage.error(messageOf(error))
  }
}

async function loadSourceTables(resetSelection: boolean) {
  if (!form.sourceDataSourceId || !form.sourceDatabase) {
    sourceTables.value = []
    return
  }
  try {
    sourceTables.value = await integrationApi.sourceTables(form.sourceDataSourceId, form.sourceDatabase)
    if (resetSelection) {
      form.selectedTables = []
      for (const key of Object.keys(targetTables)) delete targetTables[key]
    }
    ensureTargetMappings()
  } catch (error) {
    ElMessage.error(messageOf(error))
  }
}

function ensureTargetMappings() {
  for (const table of form.selectedTables) {
    if (!targetTables[table]) targetTables[table] = `${form.targetPrefix}${table}`
  }
}

function applyTargetPrefix() {
  for (const table of form.selectedTables) targetTables[table] = `${form.targetPrefix}${table}`
}

function selectAllVisible() {
  const names = filteredSourceTables.value.map(table => table.name)
  form.selectedTables = Array.from(new Set([...form.selectedTables, ...names]))
  ensureTargetMappings()
}

function clearSelectedTables() {
  form.selectedTables = []
}

function removeSelectedTable(table: string) {
  form.selectedTables = form.selectedTables.filter(item => item !== table)
  delete targetTables[table]
}

function validateStep(step: number) {
  if (step === 0) {
    if (!form.name.trim()) return '请输入任务名称'
    if (!form.sourceDataSourceId || !form.sourceDatabase) return '请选择 MySQL 来源和数据库'
    if (!form.targetDataSourceId || !form.targetDatabase) return '请选择 StarRocks 目标和数据库'
    if (!form.targetStrategy) return '请选择目标策略'
  }
  if (step === 1) {
    if (!form.selectedTables.length) return '请至少选择一张来源表'
    if (form.selectedTables.some(table => !targetTables[table]?.trim())) return '请为所有来源表配置目标表名'
  }
  if (step === 2 && form.scheduleEnabled) {
    if (!form.cronExpression.trim()) return '请输入 Cron 表达式'
    if (!form.timezone.trim()) return '请选择时区'
  }
  return ''
}

async function nextStep() {
  const error = validateStep(editorStep.value)
  if (error) return ElMessage.warning(error)
  if (editorStep.value === 2) {
    const ok = await loadSeaTunnelPreview()
    if (!ok) return
  }
  editorStep.value = Math.min(3, editorStep.value + 1)
}

function previousStep() {
  editorStep.value = Math.max(0, editorStep.value - 1)
}

function targetPolicyOptions() {
  switch (form.targetStrategy) {
    case 'RECREATE': return { targetPolicy: 'RECREATE', schemaSaveMode: 'RECREATE_SCHEMA', dataSaveMode: 'DROP_DATA' }
    case 'FULL_OVERWRITE': return { targetPolicy: 'FULL_OVERWRITE', schemaSaveMode: 'CREATE_SCHEMA_WHEN_NOT_EXIST', dataSaveMode: 'DROP_DATA' }
    case 'APPEND_ONLY': return { targetPolicy: 'APPEND_ONLY', schemaSaveMode: 'IGNORE', dataSaveMode: 'APPEND_DATA' }
    default: return { targetPolicy: 'AUTO_EVOLVE', schemaSaveMode: 'CREATE_SCHEMA_WHEN_NOT_EXIST', dataSaveMode: 'APPEND_DATA' }
  }
}

function buildPayload(): IntegrationTaskPayload {
  const source = sources.value.find(item => item.id === form.sourceDataSourceId)
  const target = sources.value.find(item => item.id === form.targetDataSourceId)
  if (!source || !target) throw new Error('数据源不存在或已被删除')
  const firstSourceTable = form.selectedTables[0]
  const where = form.where.trim()
  const policy = targetPolicyOptions()
  return {
    name: form.name.trim(),
    sourceType: 'MYSQL',
    targetType: 'STARROCKS',
    syncMode: where ? 'INCREMENTAL' : 'FULL',
    sourceDataSourceId: source.id,
    targetDataSourceId: target.id,
    source: {
      host: source.host,
      port: source.port,
      database: form.sourceDatabase,
      username: source.username,
      password: '',
      table: firstSourceTable
    },
    target: {
      host: target.host,
      port: target.port,
      database: form.targetDatabase.trim(),
      username: target.username,
      password: '',
      table: targetTables[firstSourceTable]
    },
    mappings: [],
    options: {
      description: form.description.trim(),
      where,
      ...policy
    },
    tables: form.selectedTables.map(table => ({
      sourceDatabase: form.sourceDatabase,
      sourceTable: table,
      targetDatabase: form.targetDatabase.trim(),
      targetTable: targetTables[table].trim()
    }))
  }
}

async function loadSeaTunnelPreview() {
  previewLoading.value = true
  try {
    seatunnelPreview.value = await integrationApi.previewConfig(buildPayload())
    return true
  } catch (error) {
    seatunnelPreview.value = ''
    ElMessage.error(messageOf(error))
    return false
  } finally {
    previewLoading.value = false
  }
}

async function copySeaTunnelConfig() {
  const text = seatunnelPreview.value.trim()
  if (!text) return ElMessage.warning('暂无可复制的 SeaTunnel 配置')
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('SeaTunnel 配置已复制到剪贴板')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand('copy')
    textarea.remove()
    copied ? ElMessage.success('SeaTunnel 配置已复制到剪贴板') : ElMessage.error('复制失败，请手动选择配置内容')
  }
}

async function saveTask() {
  for (let step = 0; step <= 2; step += 1) {
    const error = validateStep(step)
    if (error) {
      editorStep.value = step
      return ElMessage.warning(error)
    }
  }
  saving.value = true
  try {
    const payload = buildPayload()
    let savedTask: IntegrationTask
    if (editorMode.value === 'edit' && editingId.value) {
      savedTask = await integrationApi.update(editingId.value, payload)
      ElMessage.success('离线同步任务已更新')
    } else {
      savedTask = await integrationApi.create(payload)
      ElMessage.success('离线同步任务已创建')
    }
    await integrationApi.saveSchedule(savedTask.id, {
      cronExpression: form.cronExpression.trim(),
      timezone: form.timezone.trim(),
      enabled: form.scheduleEnabled
    })
    editorVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    saving.value = false
  }
}

async function run(task: IntegrationTask) {
  if (!isOnline(task)) return ElMessage.warning('任务已下线，请先上线后再运行')
  try {
    const result = await integrationApi.validate(task.id)
    if (!result.valid) return ElMessage.error(result.message || 'SeaTunnel 配置校验失败')
    const previous = latestBatch.value[task.id]
    if ((previous?.status || '').toUpperCase() === 'UNKNOWN') {
      await ElMessageBox.confirm(
        '上一批次执行结果未知，可能已经写入部分或全部数据。建议先进入运行记录点击“核对状态”。如果仍确认要重新执行，将创建一个新的批次，存在重复写入风险。',
        '确认重新运行？',
        { type: 'warning', confirmButtonText: '确认重新运行', cancelButtonText: '取消' }
      )
      await integrationApi.runConfirmed(task.id)
    } else {
      await integrationApi.run(task.id)
    }
    ElMessage.success('离线同步任务已提交')
    setTimeout(load, 800)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function stop(task: IntegrationTask) {
  try {
    await integrationApi.stop(task.id)
    ElMessage.success('停止请求已提交')
    await load()
  } catch (error) {
    ElMessage.error(messageOf(error))
  }
}

async function remove(task: IntegrationTask) {
  if (isOnline(task)) return ElMessage.warning('任务已上线，请先下线后再删除')
  try {
    await ElMessageBox.confirm(`删除“${task.name}”？删除后无法恢复。`, '删除离线同步任务', { type: 'warning' })
    await integrationApi.remove(task.id)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function onlineTask(task: IntegrationTask) {
  try {
    await ElMessageBox.confirm(`上线“${task.name}”？上线后任务配置将只读，并允许手工运行和工作流调度。`, '上线离线同步任务', { type: 'warning' })
    await integrationApi.online(task.id)
    ElMessage.success('任务已上线')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function offlineTask(task: IntegrationTask) {
  try {
    await ElMessageBox.confirm(`下线“${task.name}”？下线后将禁止手工运行和工作流调度，允许重新编辑配置。`, '下线离线同步任务', { type: 'warning' })
    await integrationApi.offline(task.id)
    ElMessage.success('任务已下线')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function refreshHistory(showLoading = false) {
  if (!historyTask.value) return
  if (showLoading) historyLoading.value = true
  try {
    const [batchRows, instanceRows] = await Promise.all([
      integrationApi.batches(historyTask.value.id),
      integrationApi.instances(historyTask.value.id)
    ])
    batches.value = batchRows
    history.value = instanceRows
    const nextLogs: Record<string, string> = { ...historyLogs.value }
    if (batchRows.length) {
      const attemptPairs = await Promise.all(batchRows.map(async batch => [batch.id, await integrationApi.attempts(batch.id)] as const))
      historyAttempts.value = Object.fromEntries(attemptPairs)
      for (const [batchId, rows] of attemptPairs) {
        const batch = batchRows.find(item => item.id === batchId)
        for (const attempt of rows) {
          const key = `attempt-${attempt.id}`
          if (!selectedHistoryLogKey.value && attempt.executionId) {
            selectedHistoryLogKey.value = key
            selectedHistoryLogTitle.value = `${batch?.batchCode || 'Batch'} / Attempt #${attempt.attemptNo}`
          }
          if (!attempt.executionId) {
            nextLogs[key] = attempt.errorMessage || batch?.errorMessage || '尚未生成执行日志'
            continue
          }
          try { nextLogs[key] = await integrationApi.log(attempt.executionId) }
          catch (error) { nextLogs[key] = messageOf(error) }
        }
      }
    } else {
      historyAttempts.value = {}
      for (const row of instanceRows) {
        const key = `instance-${row.id}`
        if (!selectedHistoryLogKey.value && row.executionId) {
          selectedHistoryLogKey.value = key
          selectedHistoryLogTitle.value = row.executionId
        }
        if (!row.executionId) {
          nextLogs[key] = row.message || '暂无日志'
          continue
        }
        try { nextLogs[key] = await integrationApi.log(row.executionId) }
        catch (error) { nextLogs[key] = messageOf(error) }
      }
    }
    historyLogs.value = nextLogs
  } catch (error) {
    if (showLoading) ElMessage.error(messageOf(error))
  } finally {
    if (showLoading) historyLoading.value = false
  }
}

function startHistoryPolling() {
  stopHistoryPolling()
  historyPollTimer = setInterval(() => {
    if (historyVisible.value) void refreshHistory(false)
  }, 2000)
}

function stopHistoryPolling() {
  if (historyPollTimer) clearInterval(historyPollTimer)
  historyPollTimer = null
}

async function showHistory(task: IntegrationTask) {
  historyTask.value = task
  historyVisible.value = true
  selectedHistoryLogKey.value = ''
  selectedHistoryLogTitle.value = ''
  historyLogs.value = {}
  await refreshHistory(true)
  startHistoryPolling()
}

function selectAttempt(batch: IntegrationBatch, attempt: IntegrationAttempt) {
  selectedHistoryLogKey.value = `attempt-${attempt.id}`
  selectedHistoryLogTitle.value = `${batch.batchCode} / Attempt #${attempt.attemptNo}`
}

function selectLegacyInstance(row: IntegrationInstance) {
  selectedHistoryLogKey.value = `instance-${row.id}`
  selectedHistoryLogTitle.value = row.executionId || `历史执行 #${row.id}`
}

function selectedHistoryLog() {
  return selectedHistoryLogKey.value ? (historyLogs.value[selectedHistoryLogKey.value] || '暂无日志') : '暂无日志'
}

function closeHistory() {
  stopHistoryPolling()
  historyLogMaximized.value = false
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && historyLogMaximized.value) {
    event.preventDefault()
    historyLogMaximized.value = false
  }
}

async function retryBatch(batch: IntegrationBatch) {
  const task = tasks.value.find(item => item.id === batch.taskId)
  if (task && !isOnline(task)) return ElMessage.warning('任务已下线，请先上线后再重试')
  try {
    await ElMessageBox.confirm('重试会复用该批次创建时固化的运行快照，不使用任务当前的新配置。确认重试？', '重试离线批次', { type: 'warning' })
    await integrationApi.retryBatch(batch.id)
    ElMessage.success('重试已提交')
    if (historyTask.value) await showHistory(historyTask.value)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function reconcileBatch(batch: IntegrationBatch) {
  try {
    await integrationApi.reconcileBatch(batch.id)
    ElMessage.success('已向 SeaTunnel 重新核对运行状态')
    if (historyTask.value) await showHistory(historyTask.value)
  } catch (error) { ElMessage.error(messageOf(error)) }
}

function openBackfill(task: IntegrationTask) {
  if (!isOnline(task)) return ElMessage.warning('任务已下线，请先上线后再补数')
  backfillTask.value = task
  backfillForm.where = task.syncMode === 'INCREMENTAL' ? formWhere(task) : ''
  backfillForm.startLabel = ''
  backfillForm.endLabel = ''
  backfillVisible.value = true
}

async function submitBackfill() {
  if (!backfillTask.value || !backfillForm.where.trim()) return ElMessage.warning('请填写本次补数的 WHERE 条件')
  backfillSaving.value = true
  try {
    await integrationApi.backfill(backfillTask.value.id, { ...backfillForm, where: backfillForm.where.trim() })
    ElMessage.success('补数批次已提交')
    backfillVisible.value = false
    await load()
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { backfillSaving.value = false }
}

async function openCursor(task: IntegrationTask) {
  cursorTask.value = task
  try { Object.assign(cursorState, await integrationApi.cursor(task.id)); cursorVisible.value = true }
  catch (error) { ElMessage.error(messageOf(error)) }
}

async function saveCursor() {
  if (!cursorTask.value) return
  cursorSaving.value = true
  try {
    Object.assign(cursorState, await integrationApi.saveCursor(cursorTask.value.id, { cursorColumn: cursorState.cursorColumn, cursorValue: cursorState.cursorValue }))
    ElMessage.success('增量游标已保存')
    cursorVisible.value = false
  } catch (error) { ElMessage.error(messageOf(error)) }
  finally { cursorSaving.value = false }
}

function sourceLabel(task: IntegrationTask) {
  const config = safeJson(task.sourceConfigJson)
  const source = sources.value.find(item => item.type === 'MYSQL'
    && item.host === stringValue(config.host)
    && item.port === Number(config.port || 0))
  return source?.name || `${stringValue(config.host) || task.sourceType}:${Number(config.port || 0) || '—'}`
}

function targetLabel(task: IntegrationTask) {
  const config = safeJson(task.targetConfigJson)
  const source = sources.value.find(item => item.type === 'STARROCKS'
    && item.host === stringValue(config.host)
    && item.port === Number(config.port || 0))
  return source?.name || `${stringValue(config.host) || task.targetType}:${Number(config.port || 0) || '—'}`
}

function schemaModeLabel(task: IntegrationTask) {
  const options = objectValue(safeJson(task.transformConfigJson).options)
  return targetStrategyLabel(strategyFromOptions(options))
}

function strategyFromOptions(options: Record<string, unknown>) {
  const policy = stringValue(options.targetPolicy).toUpperCase()
  if (['AUTO_EVOLVE', 'RECREATE', 'FULL_OVERWRITE', 'APPEND_ONLY'].includes(policy)) return policy
  const schema = stringValue(options.schemaSaveMode).toUpperCase()
  const data = stringValue(options.dataSaveMode).toUpperCase()
  if (schema === 'RECREATE_SCHEMA') return 'RECREATE'
  if (data === 'DROP_DATA') return 'FULL_OVERWRITE'
  if (schema === 'IGNORE') return 'APPEND_ONLY'
  return 'AUTO_EVOLVE'
}

function targetStrategyLabel(value: string) {
  const labels: Record<string, string> = {
    AUTO_EVOLVE: '自动建表/补字段，保留已有数据',
    RECREATE: '表存在则删除重建',
    FULL_OVERWRITE: '全量覆盖已有数据',
    APPEND_ONLY: '仅追加，不处理表结构'
  }
  return labels[value] || labels.AUTO_EVOLVE
}

function inferTargetPrefix(task: IntegrationTask) {
  const mappings = task.tables || []
  if (!mappings.length) return ''
  const prefixes = mappings.map(item => item.targetTable.endsWith(item.sourceTable)
    ? item.targetTable.slice(0, item.targetTable.length - item.sourceTable.length)
    : null)
  return prefixes.every(item => item !== null && item === prefixes[0]) ? String(prefixes[0] || '') : ''
}

function detailWhere(task: IntegrationTask) {
  const value = formWhere(task)
  return value || '—'
}

function latestDetailBatch() {
  return detailBatches.value[0]
}

function commandHandler(task: IntegrationTask) {
  return (command: string | number | object) => handleTaskCommand(String(command), task)
}

function handleTaskCommand(command: string, task: IntegrationTask) {
  if (command === 'backfill') return openBackfill(task)
  if (command === 'cursor') return openCursor(task)
  if (command === 'history') return showHistory(task)
  if (command === 'delete') return remove(task)
}

function latestStatus(task: IntegrationTask) {
  return latestBatch.value[task.id]?.status || latest.value[task.id]?.status || '未运行'
}

function latestStartedAt(task: IntegrationTask) {
  return formatDateTime(latestBatch.value[task.id]?.startedAt || latestBatch.value[task.id]?.createdAt || latest.value[task.id]?.startedAt)
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19)
}

function formatCount(value?: number) {
  if (value === undefined || value === null) return '—'
  return new Intl.NumberFormat('zh-CN').format(value)
}

function formatDuration(ms?: number) {
  if (ms === undefined || ms === null) return '—'
  const seconds = Math.max(0, Math.round(ms / 1000))
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  if (minutes < 60) return `${minutes}分${rest ? `${rest}秒` : ''}`
  const hours = Math.floor(minutes / 60)
  const min = minutes % 60
  return `${hours}小时${min ? `${min}分` : ''}`
}

function taskSummary(task: IntegrationTask) { return taskSummaries.value[task.id] }
function taskCreatedAt(task: IntegrationTask) { return formatDateTime(taskSummary(task)?.createdAt) }
function taskCreatedBy(task: IntegrationTask) { return taskSummary(task)?.createdBy || 'platform' }

function statusLabel(status?: string) {
  const value = (status || '').toUpperCase()
  if (!value || value === '未运行' || value === 'UNKNOWN') return value === '未运行' ? '未运行' : '未知'
  if (value.includes('FINISHED') || value.includes('SUCCESS')) return '成功'
  if (value.includes('RUNNING')) return '运行中'
  if (value.includes('START') || value.includes('SUBMIT') || value.includes('QUEUED') || value.includes('PENDING') || value.includes('WAIT')) return '等待运行'
  if (value.includes('FAIL') || value.includes('ERROR') || value.includes('LOST')) return '失败'
  if (value.includes('STOP') || value.includes('CANCEL')) return '已停止'
  return status || '未知'
}

function isOnline(task: IntegrationTask) {
  return (task.lifecycleStatus || 'ONLINE').toUpperCase() === 'ONLINE'
}

function canStop(task: IntegrationTask) {
  return ['RUNNING', 'STARTING', 'SUBMITTED'].includes((latest.value[task.id]?.status || '').toUpperCase())
}

function path(task: IntegrationTask) {
  const table = task.tables?.[0]
  return table ? `${table.sourceDatabase} → ${table.targetDatabase}` : `${task.sourceType} → ${task.targetType}`
}

function modeLabel(mode: string) {
  return mode === 'INCREMENTAL' ? '条件增量' : '全量同步'
}

function canRetryBatch(batch: IntegrationBatch) {
  return !['QUEUED', 'SUBMITTED', 'STARTING', 'RUNNING'].includes((batch.status || '').toUpperCase())
}

function triggerLabel(value: string) {
  const labels: Record<string, string> = { MANUAL: '手动', SCHEDULED: '调度', WORKFLOW: '工作流', BACKFILL: '补数' }
  return labels[value] || value
}

function formWhere(task: IntegrationTask) {
  const transform = safeJson(task.transformConfigJson)
  return stringValue(objectValue(transform.options).where)
}

function safeJson(value?: string) {
  try { return value ? JSON.parse(value) as Record<string, unknown> : {} } catch { return {} }
}

function objectValue(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function matchDataSource(type: string, config: Record<string, unknown>) {
  const candidates = sources.value.filter(source => source.type === type)
  const exact = candidates.find(source => source.host === stringValue(config.host)
    && source.port === Number(config.port || 0)
    && source.username === stringValue(config.username))
  return exact?.id || (candidates.length === 1 ? candidates[0].id : 0)
}

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : '操作失败'
}

onMounted(() => {
  void load()
  window.addEventListener('keydown', handleGlobalKeydown)
})
onBeforeUnmount(() => {
  stopHistoryPolling()
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<template>
  <div class="ds-page ds-integration-page">
    <PageHeader title="离线同步" subtitle="MySQL → StarRocks 批式同步。用户只配置业务来源和目标，SeaTunnel 执行环境由平台统一管理。">
      <template #actions><el-button type="primary" @click="openCreate">+ 新建离线同步</el-button></template>
    </PageHeader>

    <div class="ds-card">
      <div class="ds-toolbar toolbar-wrap">
        <el-input v-model="keyword" clearable placeholder="搜索任务或来源表" style="width:260px" />
        <el-select v-model="modeFilter" clearable placeholder="同步方式" style="width:140px">
          <el-option label="全量同步" value="FULL" />
          <el-option label="条件增量" value="INCREMENTAL" />
        </el-select>
        <div class="ds-spacer" />
        <span class="toolbar-count">共 {{ filteredTasks.length }} 个任务</span>
        <el-button @click="load" :loading="loading">刷新</el-button>
      </div>

      <el-table :data="filteredTasks" v-loading="loading" row-class-name="offline-task-row" @row-click="openDetail">
        <el-table-column label="任务名称" width="170" show-overflow-tooltip>
          <template #default="scope">
            <button class="task-name-link" @click.stop="openDetail(scope.row)">{{ scope.row.name }}</button>
          </template>
        </el-table-column>
        <el-table-column label="执行概况" min-width="165">
          <template #default="scope"><div class="runtime-summary"><span>数据量：<strong>{{ formatCount(taskSummary(scope.row)?.dataCount) }}</strong></span><span>耗时：{{ formatDuration(taskSummary(scope.row)?.durationMs) }}</span></div></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope"><strong :class="isOnline(scope.row) ? 'schedule-online' : 'schedule-offline'">{{ isOnline(scope.row) ? '已上线' : '已下线' }}</strong></template>
        </el-table-column>
        <el-table-column label="调度" min-width="205">
          <template #default="scope"><div class="schedule-summary"><span>上次：{{ formatDateTime(taskSummary(scope.row)?.lastRunAt) }}</span><span>下次：{{ formatDateTime(taskSummary(scope.row)?.nextRunAt) }}</span></div></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="175"><template #default="scope"><span class="time-cell">{{ taskCreatedAt(scope.row) }}</span></template></el-table-column>
        <el-table-column label="创建人" min-width="100"><template #default="scope">{{ taskCreatedBy(scope.row) }}</template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <template v-if="isOnline(scope.row)">
              <el-button link type="primary" @click.stop="run(scope.row)">运行</el-button>
              <el-button link type="primary" @click.stop="openDetail(scope.row)">查看</el-button>
              <el-button link type="warning" @click.stop="offlineTask(scope.row)">下线</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click.stop="openEdit(scope.row)">编辑</el-button>
              <el-button link type="success" @click.stop="onlineTask(scope.row)">上线</el-button>
              <el-button link type="danger" @click.stop="remove(scope.row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" size="60%" destroy-on-close class="task-detail-drawer">
      <template #header>
        <div class="detail-head">
          <div>
            <div class="detail-eyebrow">离线同步任务</div>
            <h3>{{ detailTask?.name || '任务详情' }}</h3>
            <div class="detail-subtitle">点击任务默认进入只读详情；只有进入编辑向导后才可以修改任务配置。</div>
          </div>
          <div class="detail-actions" v-if="detailTask">
            <el-button @click="detailTab = 'runs'">运行记录</el-button>
            <el-button v-if="!isOnline(detailTask)" type="primary" @click="editFromDetail">编辑任务</el-button>
          </div>
        </div>
      </template>

      <div class="task-detail" v-loading="detailLoading" v-if="detailTask">
        <div class="detail-summary">
          <div class="summary-item"><span>当前状态</span><StatusBadge :status="latestDetailBatch()?.status || latestStatus(detailTask)" :label="statusLabel(latestDetailBatch()?.status || latestStatus(detailTask))" /></div>
          <div class="summary-item"><span>同步方式</span><strong>{{ modeLabel(detailTask.syncMode) }}</strong></div>
          <div class="summary-item"><span>同步表数</span><strong>{{ detailTask.tables?.length || 0 }} 张</strong></div>
          <div class="summary-item"><span>最近运行</span><strong>{{ latestDetailBatch()?.startedAt ? formatDateTime(latestDetailBatch()?.startedAt) : latestStartedAt(detailTask) }}</strong></div>
        </div>

        <el-tabs v-model="detailTab" class="detail-tabs">
          <el-tab-pane label="任务信息" name="overview">
            <section class="detail-section">
              <div class="detail-section-title">基本信息</div>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="任务名称">{{ detailTask.name }}</el-descriptions-item>
                <el-descriptions-item label="任务 ID">{{ detailTask.id }}</el-descriptions-item>
                <el-descriptions-item label="来源数据源">{{ sourceLabel(detailTask) }}</el-descriptions-item>
                <el-descriptions-item label="目标数据源">{{ targetLabel(detailTask) }}</el-descriptions-item>
                <el-descriptions-item label="来源 → 目标">{{ path(detailTask) }}</el-descriptions-item>
                <el-descriptions-item label="执行引擎">SeaTunnel（平台默认集群）</el-descriptions-item>
                <el-descriptions-item label="同步方式">{{ modeLabel(detailTask.syncMode) }}</el-descriptions-item>
                <el-descriptions-item label="目标表策略">{{ schemaModeLabel(detailTask) }}</el-descriptions-item>
                <el-descriptions-item label="WHERE 条件" :span="2"><code class="readonly-code">{{ detailWhere(detailTask) }}</code></el-descriptions-item>
              </el-descriptions>
            </section>
            <section class="detail-section">
              <div class="detail-section-title">调度配置</div>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="任务上线状态"><strong :class="isOnline(detailTask) ? 'schedule-online' : 'schedule-offline'">{{ isOnline(detailTask) ? '已上线' : '已下线' }}</strong></el-descriptions-item>
                <el-descriptions-item label="调度状态">{{ detailSchedule?.enabled ? '已启用' : '未启用' }}</el-descriptions-item>
                <el-descriptions-item label="调度策略">{{ detailSchedule?.enabled ? scheduleTextFromCron(detailSchedule?.cronExpression) : '仅手动执行' }}</el-descriptions-item>
                <el-descriptions-item label="时区">{{ detailSchedule?.timezone || 'Asia/Shanghai' }}</el-descriptions-item>
                <el-descriptions-item label="下次执行">{{ formatDateTime(taskSummary(detailTask)?.nextRunAt) }}</el-descriptions-item>
                <el-descriptions-item label="上线行为" :span="2">上线只启用任务和调度，不会立即执行；手动运行或到达调度时间才会执行。</el-descriptions-item>
              </el-descriptions>
            </section>
            <section class="detail-section">
              <div class="detail-section-title">最近运行</div>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="最近状态"><StatusBadge :status="latestDetailBatch()?.status || latestStatus(detailTask)" :label="statusLabel(latestDetailBatch()?.status || latestStatus(detailTask))" /></el-descriptions-item>
                <el-descriptions-item label="触发方式">{{ latestDetailBatch() ? triggerLabel(latestDetailBatch()?.triggerType || '') : '—' }}</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ formatDateTime(latestDetailBatch()?.startedAt) }}</el-descriptions-item>
                <el-descriptions-item label="结束时间">{{ formatDateTime(latestDetailBatch()?.finishedAt) }}</el-descriptions-item>
              </el-descriptions>
            </section>
          </el-tab-pane>

          <el-tab-pane :label="`表映射 (${detailTask.tables?.length || 0})`" name="tables">
            <section class="detail-section no-top">
              <el-table :data="detailTask.tables || []" border>
                <el-table-column label="来源表" min-width="260"><template #default="scope"><span class="object-name">{{ scope.row.sourceDatabase }}.{{ scope.row.sourceTable }}</span></template></el-table-column>
                <el-table-column label="目标表" min-width="260"><template #default="scope"><span class="object-name target">{{ scope.row.targetDatabase }}.{{ scope.row.targetTable }}</span></template></el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane :label="`运行记录 (${detailBatches.length || detailInstances.length})`" name="runs">
            <div class="detail-run-toolbar"><el-button type="primary" plain @click="showHistory(detailTask!)">查看完整运行记录与日志</el-button></div>
            <section class="detail-section no-top">
              <el-table v-if="detailBatches.length" :data="detailBatches" border>
                <el-table-column prop="batchCode" label="批次" min-width="190" show-overflow-tooltip />
                <el-table-column label="触发方式" width="100"><template #default="scope">{{ triggerLabel(scope.row.triggerType) }}</template></el-table-column>
                <el-table-column label="状态" width="110"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
                <el-table-column prop="startedAt" label="开始时间" width="175" />
                <el-table-column prop="finishedAt" label="结束时间" width="175" />
                <el-table-column label="日志" width="110"><template #default><span class="muted-inline">见运行记录</span></template></el-table-column>
              </el-table>
              <el-table v-else :data="detailInstances" border>
                <el-table-column prop="executionId" label="执行 ID" min-width="220" show-overflow-tooltip />
                <el-table-column label="状态" width="110"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
                <el-table-column prop="startedAt" label="开始时间" width="175" />
                <el-table-column prop="finishedAt" label="结束时间" width="175" />
                <el-table-column label="日志" width="100"><template #default><span class="muted-inline">见运行记录</span></template></el-table-column>
              </el-table>
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-drawer v-model="editorVisible" :title="editorMode === 'edit' ? '编辑离线同步任务' : '新建离线同步任务'" size="60%" destroy-on-close>
      <div class="editor-shell">
        <el-steps :active="editorStep" finish-status="success" simple class="editor-steps editor-steps-4">
          <el-step title="基本配置" />
          <el-step title="选择表" />
          <el-step title="调度配置" />
          <el-step title="确认配置" />
        </el-steps>

        <div class="editor-body">
          <template v-if="editorStep === 0">
            <div class="section-title">基本配置</div>
            <div class="section-tip">增量条件为空即按全量同步；目标策略决定 StarRocks 表结构和已有数据的处理方式。</div>
            <el-form label-position="top">
              <div class="form-grid">
                <el-form-item label="任务名称"><el-input v-model="form.name" placeholder="输入同步任务名称" /></el-form-item>
                <el-form-item label="描述"><el-input v-model="form.description" placeholder="任务描述" /></el-form-item>
                <el-form-item label="增量条件" class="span-2">
                  <el-input v-model="form.where" placeholder="为空则全量同步；增量建议使用 [start,end) 条件，例如 update_time >= :start AND update_time < :end" />
                </el-form-item>
                <el-form-item label="目标策略" class="span-2">
                  <el-select v-model="form.targetStrategy" style="width:100%">
                    <el-option label="自动建表/补字段，保留已有数据" value="AUTO_EVOLVE" />
                    <el-option label="表存在则删除重建" value="RECREATE" />
                    <el-option label="全量覆盖已有数据" value="FULL_OVERWRITE" />
                    <el-option label="仅追加，不处理表结构" value="APPEND_ONLY" />
                  </el-select>
                </el-form-item>
              </div>

              <div class="database-pair">
                <section class="database-card source-card">
                  <div class="database-card-title">源数据库</div>
                  <el-form-item label="数据源">
                    <el-select v-model="form.sourceDataSourceId" filterable style="width:100%" placeholder="选择 MySQL 源" @change="loadSourceDatabases(true)">
                      <el-option v-for="source in mysql" :key="source.id" :label="source.name" :value="source.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="数据库">
                    <el-select v-model="form.sourceDatabase" filterable style="width:100%" placeholder="选择源数据库" @change="loadSourceTables(true)">
                      <el-option v-for="database in sourceDbs" :key="database" :label="database" :value="database" />
                    </el-select>
                  </el-form-item>
                </section>
                <div class="database-arrow">→</div>
                <section class="database-card target-card">
                  <div class="database-card-title">目标数据库</div>
                  <el-form-item label="数据源">
                    <el-select v-model="form.targetDataSourceId" filterable style="width:100%" placeholder="选择 StarRocks 目标" @change="loadTargetDatabases(true)">
                      <el-option v-for="source in starrocks" :key="source.id" :label="source.name" :value="source.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="数据库">
                    <el-select v-model="form.targetDatabase" filterable style="width:100%" placeholder="选择目标数据库">
                      <el-option v-for="database in targetDbs" :key="database" :label="database" :value="database" />
                    </el-select>
                  </el-form-item>
                </section>
              </div>
            </el-form>
          </template>

          <template v-else-if="editorStep === 1">
            <div class="section-title table-step-title">
              <span>选择数据表</span>
              <div class="target-prefix-toolbar"><span>目标表前缀</span><el-input v-model="form.targetPrefix" clearable placeholder="可选，例如 ods_" @change="applyTargetPrefix" /><el-button @click="applyTargetPrefix">应用</el-button></div>
            </div>
            <div class="section-tip">从左侧选择 MySQL 源表，右侧确认 StarRocks 目标表名；目标表前缀可以一键应用到所有已选表。</div>
            <div class="table-selector">
              <div class="table-source-pane">
                <div class="pane-heading">源头表</div>
                <div class="pane-toolbar">
                  <el-input v-model="tableKeyword" clearable placeholder="搜索源表" />
                  <el-button @click="selectAllVisible">全选当前</el-button>
                </div>
                <el-checkbox-group v-model="form.selectedTables" class="table-check-list" @change="ensureTargetMappings">
                  <el-checkbox v-for="table in filteredSourceTables" :key="table.name" :value="table.name" class="table-check-item">
                    <span class="table-name">{{ table.name }}</span><span v-if="table.comment" class="table-comment">{{ table.comment }}</span>
                  </el-checkbox>
                </el-checkbox-group>
              </div>
              <div class="table-selected-pane">
                <div class="pane-title"><strong>目标表（{{ form.selectedTables.length }}）</strong><el-button link @click="clearSelectedTables">清空</el-button></div>
                <div v-if="!form.selectedTables.length" class="empty-selection">从左侧勾选需要同步的表</div>
                <div v-for="table in form.selectedTables" :key="table" class="selected-row target-mapping-row">
                  <span class="source-table-name">{{ table }}</span><span class="arrow">→</span>
                  <el-input v-model="targetTables[table]" size="small" />
                  <el-button link type="danger" @click="removeSelectedTable(table)">移除</el-button>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="editorStep === 2">
            <div class="section-title">调度配置</div>
            <div class="section-tip">上线只启用任务和调度，不会立即执行。任务仅在手动点击“运行”或到达调度时间时执行。</div>
            <el-form label-position="top" class="schedule-form">
              <el-form-item label="启用调度">
                <el-switch v-model="form.scheduleEnabled" active-text="启用" inactive-text="关闭" @change="refreshSchedulePreview" />
              </el-form-item>
              <el-form-item label="执行时间">
                <div class="schedule-config-box" :class="{ disabled: !form.scheduleEnabled }">
                  <div class="schedule-mode-tabs">
                    <button type="button" :class="{ active: scheduleConfigMode === 'quick' }" @click="scheduleConfigMode='quick'">快速配置</button>
                    <button type="button" :class="{ active: scheduleConfigMode === 'custom' }" @click="scheduleConfigMode='custom'">定时配置</button>
                  </div>
                  <div v-if="scheduleConfigMode === 'quick'" class="schedule-quick">
                    <el-select v-model="quickPreset" :disabled="!form.scheduleEnabled" style="width:100%" @change="applyQuickPreset">
                      <el-option label="每天 02:00" value="DAILY_0200" />
                      <el-option label="每天 00:00" value="DAILY_0000" />
                      <el-option label="每小时整点" value="HOURLY" />
                    </el-select>
                  </div>
                  <div v-else class="schedule-custom">
                    <div class="schedule-unit-tabs">
                      <button v-for="item in [{k:'minute',t:'分'},{k:'hour',t:'时'},{k:'day',t:'日'},{k:'month',t:'月'},{k:'week',t:'周'}]" :key="item.k" type="button" :class="{ active: scheduleTab === item.k }" @click="scheduleTab = item.k as any">{{ item.t }}</button>
                    </div>
                    <div class="schedule-unit-picker">
                      <el-select v-if="scheduleTab==='minute'" v-model="form.scheduleMinute" :disabled="!form.scheduleEnabled" style="width:100%" @change="syncCronFromParts"><el-option v-for="item in minuteOptions" :key="`m-${item}`" :label="`${item.padStart(2,'0')}分`" :value="item" /></el-select>
                      <el-select v-else-if="scheduleTab==='hour'" v-model="form.scheduleHour" :disabled="!form.scheduleEnabled" style="width:100%" @change="syncCronFromParts"><el-option label="每小时" value="*" /><el-option v-for="item in hourOptions" :key="`h-${item}`" :label="`${item.padStart(2,'0')}时`" :value="item" /></el-select>
                      <el-select v-else-if="scheduleTab==='day'" :model-value="form.scheduleDay" :disabled="!form.scheduleEnabled" style="width:100%" @change="selectScheduleDay"><el-option label="每日" value="*" /><el-option v-for="item in dayOptions.filter(v=>v!=='*')" :key="`d-${item}`" :label="`${item}日`" :value="item" /></el-select>
                      <el-select v-else-if="scheduleTab==='month'" v-model="form.scheduleMonth" :disabled="!form.scheduleEnabled" style="width:100%" @change="syncCronFromParts"><el-option label="每月" value="*" /><el-option v-for="item in monthOptions.filter(v=>v!=='*')" :key="`mo-${item}`" :label="`${item}月`" :value="item" /></el-select>
                      <el-select v-else :model-value="form.scheduleWeek" :disabled="!form.scheduleEnabled" style="width:100%" @change="selectScheduleWeek"><el-option v-for="item in weekOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
                    </div>
                  </div>
                  <div class="schedule-preview-card">
                    <div class="schedule-preview-title">预计下次执行时间（前2次）</div>
                    <div v-if="!form.scheduleEnabled" class="schedule-preview-empty">调度已关闭</div>
                    <div v-else-if="!schedulePreviewTimes.length" class="schedule-preview-empty">正在计算…</div>
                    <div v-for="(time,index) in schedulePreviewTimes" :key="time" class="schedule-preview-row"><i>{{ index + 1 }}</i><span>{{ formatDateTime(time).replace(/-/g,'/') }}</span></div>
                  </div>
                </div>
                <div class="field-tip">{{ scheduleText() }}<span v-if="form.scheduleEnabled"> · 系统表达式：{{ form.cronExpression }}</span></div>
              </el-form-item>
              <el-form-item label="时区">
                <el-select v-model="form.timezone" :disabled="!form.scheduleEnabled" style="width:100%" @change="refreshSchedulePreview">
                  <el-option label="Asia/Shanghai" value="Asia/Shanghai" />
                  <el-option label="Asia/Singapore" value="Asia/Singapore" />
                  <el-option label="UTC" value="UTC" />
                </el-select>
              </el-form-item>
              <div class="schedule-behavior-note">
                <strong>执行规则</strong>
                <span>保存任务后仍为下线状态；上线时只恢复调度，不会立即执行 SeaTunnel。</span>
                <span>手动运行：MANUAL ｜ 定时触发：SCHEDULED</span>
              </div>
            </el-form>
          </template>

          <template v-else>
            <div class="section-title">确认配置</div>
            <el-descriptions :column="2" border class="confirm-overview">
              <el-descriptions-item label="任务名称">{{ form.name }}</el-descriptions-item>
              <el-descriptions-item label="同步方式">{{ form.where.trim() ? '条件增量' : '全量同步' }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ mysql.find(item => item.id === form.sourceDataSourceId)?.name || '—' }} / {{ form.sourceDatabase }}</el-descriptions-item>
              <el-descriptions-item label="目标">{{ starrocks.find(item => item.id === form.targetDataSourceId)?.name || '—' }} / {{ form.targetDatabase }}</el-descriptions-item>
              <el-descriptions-item label="目标策略">{{ targetStrategyLabel(form.targetStrategy) }}</el-descriptions-item>
              <el-descriptions-item label="同步表数">{{ form.selectedTables.length }} 张</el-descriptions-item>
              <el-descriptions-item label="任务上线状态">{{ editorMode === 'create' ? '已下线' : (editingId && tasks.find(t=>t.id===editingId)?.lifecycleStatus==='ONLINE' ? '已上线' : '已下线') }}</el-descriptions-item>
              <el-descriptions-item label="调度状态">{{ form.scheduleEnabled ? '已启用' : '未启用' }}</el-descriptions-item>
              <el-descriptions-item label="调度策略">{{ form.scheduleEnabled ? scheduleText() : '仅手动执行' }}</el-descriptions-item>
              <el-descriptions-item label="调度时区">{{ form.scheduleEnabled ? form.timezone : '—' }}</el-descriptions-item>
              <el-descriptions-item label="上线行为">上线后等待手动运行或调度时间，不会立即执行</el-descriptions-item>
              <el-descriptions-item label="增量条件" :span="2">{{ form.where.trim() || '空（全量同步）' }}</el-descriptions-item>
            </el-descriptions>

            <div class="confirm-grid">
              <section class="confirm-panel">
                <div class="confirm-panel-title">表映射</div>
                <div class="confirm-mapping-list">
                  <div v-for="table in form.selectedTables" :key="table" class="confirm-row">
                    <span>{{ form.sourceDatabase }}.{{ table }}</span><span>→</span><span>{{ form.targetDatabase }}.{{ targetTables[table] }}</span>
                  </div>
                </div>
              </section>
              <section class="confirm-panel seatunnel-panel">
                <div class="confirm-panel-title"><span>SeaTunnel 配置</span><div><el-button size="small" @click="copySeaTunnelConfig">复制</el-button><el-button size="small" :loading="previewLoading" @click="loadSeaTunnelPreview">刷新配置</el-button></div></div>
                <div class="seatunnel-config-viewer" v-loading="previewLoading"><pre>{{ seatunnelPreview || '正在生成 SeaTunnel 配置…' }}</pre></div>
                <div class="seatunnel-config-tip">密码已脱敏；其余配置结构与实际提交执行文件一致。</div>
              </section>
            </div>
          </template>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="editorVisible = false">取消</el-button>
          <div class="ds-spacer" />
          <el-button v-if="editorStep > 0" @click="previousStep">上一步</el-button>
          <el-button v-if="editorStep < 3" type="primary" @click="nextStep">下一步</el-button>
          <el-button v-else type="primary" :loading="saving" @click="saveTask">{{ editorMode === 'edit' ? '保存修改' : '创建任务' }}</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="historyVisible" :title="`运行记录 · ${historyTask?.name || ''}`" size="1040px" :close-on-press-escape="!historyLogMaximized" @closed="closeHistory">
      <div class="history-shell" v-loading="historyLoading">
        <div class="runtime-note">点击运行记录后直接展示日志；任务执行中每 2 秒自动刷新，无需等待任务结束。</div>
        <div class="history-records">
          <template v-if="batches.length">
            <section v-for="batch in batches" :key="batch.id" class="history-batch-card">
              <div class="history-batch-head">
                <div><strong>{{ batch.batchCode }}</strong><span>{{ triggerLabel(batch.triggerType) }}</span></div>
                <StatusBadge :status="batch.status" :label="statusLabel(batch.status)" />
                <span>{{ formatDateTime(batch.startedAt || batch.createdAt) }} → {{ formatDateTime(batch.finishedAt) }}</span>
                <div class="history-batch-actions">
                  <el-button v-if="isOnline(historyTask!)" link :disabled="!canRetryBatch(batch)" @click.stop="retryBatch(batch)">重试</el-button>
                  <el-button link @click.stop="reconcileBatch(batch)">核对状态</el-button>
                </div>
              </div>
              <div v-if="historyAttempts[batch.id]?.length" class="history-attempts">
                <button v-for="attempt in historyAttempts[batch.id]" :key="attempt.id" type="button"
                  :class="['history-attempt-row', { selected: selectedHistoryLogKey === `attempt-${attempt.id}` }]"
                  @click="selectAttempt(batch, attempt)">
                  <strong>Attempt #{{ attempt.attemptNo }}</strong>
                  <StatusBadge :status="attempt.status" :label="statusLabel(attempt.status)" />
                  <span class="mono">{{ attempt.executionId || '尚未生成执行 ID' }}</span>
                  <span>{{ formatDateTime(attempt.startedAt || attempt.createdAt) }}</span>
                  <span class="view-log-text">查看日志</span>
                </button>
              </div>
              <div v-else class="runtime-note compact-note">该批次没有 Attempt 记录。{{ batch.errorMessage || '' }}</div>
            </section>
          </template>
          <template v-else>
            <div class="runtime-note compact-note">以下为升级批次模型之前的历史执行记录。</div>
            <button v-for="row in history" :key="row.id" type="button"
              :class="['legacy-run-row', { selected: selectedHistoryLogKey === `instance-${row.id}` }]"
              @click="selectLegacyInstance(row)">
              <strong>{{ row.executionId || `历史执行 #${row.id}` }}</strong>
              <StatusBadge :status="row.status" :label="statusLabel(row.status)" />
              <span>{{ formatDateTime(row.startedAt) }} → {{ formatDateTime(row.finishedAt) }}</span>
              <span class="view-log-text">查看日志</span>
            </button>
          </template>
        </div>

        <div :class="['history-log-panel', { 'is-maximized': historyLogMaximized }]">
          <div class="history-log-toolbar">
            <div>
              <strong>执行日志</strong>
              <span>{{ selectedHistoryLogTitle || '选择一条 Attempt 查看日志' }}</span>
              <em>运行中自动刷新</em>
            </div>
            <div class="history-log-actions">
              <el-button size="small" @click="refreshHistory(false)">刷新</el-button>
              <el-button v-if="!historyLogMaximized" size="small" type="primary" plain @click="historyLogMaximized = true">放大</el-button>
              <el-button v-else size="small" type="primary" @click="historyLogMaximized = false">还原（Esc）</el-button>
            </div>
          </div>
          <div class="history-log-viewer"><pre>{{ selectedHistoryLog() }}</pre></div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="backfillVisible" :title="`补数 · ${backfillTask?.name || ''}`" width="680px">
      <div class="runtime-note">补数会创建独立 BACKFILL Batch，不修改任务原有同步条件。</div>
      <el-form label-position="top">
        <el-form-item label="本次补数 WHERE 条件"><el-input v-model="backfillForm.where" type="textarea" :rows="4" placeholder="例如 biz_date BETWEEN '2026-09-01' AND '2026-09-07'" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="范围说明（开始，可选）"><el-input v-model="backfillForm.startLabel" placeholder="2026-09-01" /></el-form-item>
          <el-form-item label="范围说明（结束，可选）"><el-input v-model="backfillForm.endLabel" placeholder="2026-09-07" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="backfillVisible=false">取消</el-button><el-button type="primary" :loading="backfillSaving" @click="submitBackfill">提交补数</el-button></template>
    </el-dialog>

    <el-dialog v-model="cursorVisible" :title="`增量游标 · ${cursorTask?.name || ''}`" width="560px">
      <div class="runtime-note">游标用于记录增量任务已处理到的位置。本轮先作为运行状态元数据保存，不会自动改写你配置的 WHERE 条件。</div>
      <el-form label-position="top">
        <el-form-item label="游标字段"><el-input v-model="cursorState.cursorColumn" placeholder="例如 updated_at / id" /></el-form-item>
        <el-form-item label="当前游标值"><el-input v-model="cursorState.cursorValue" placeholder="例如 2026-09-13 14:00:00 / 123456" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="cursorVisible=false">取消</el-button><el-button type="primary" :loading="cursorSaving" @click="saveCursor">保存游标</el-button></template>
    </el-dialog>

  </div>
</template>

<style scoped>
.toolbar-wrap{gap:10px}.toolbar-count{font-size:12px;color:var(--ds-text-secondary)}
.editor-shell{display:flex;flex-direction:column;min-height:620px}.editor-steps{margin-bottom:20px}.editor-body{flex:1;padding:0 4px}.editor-steps-3 :deep(.el-step){padding:0 28px}
.section-title{font-size:16px;font-weight:600;color:var(--ds-text-primary);margin-bottom:8px}.section-tip{font-size:13px;color:var(--ds-text-secondary);margin-bottom:22px;line-height:1.7}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.span-2{grid-column:1 / -1}.prefix-input{display:flex;gap:8px;width:100%}.database-pair{display:grid;grid-template-columns:1fr 48px 1fr;align-items:center;gap:12px;margin-top:8px}.database-card{border:1px solid var(--ds-border);border-radius:6px;padding:16px 16px 2px;background:#fafbfc}.database-card-title{font-size:14px;font-weight:600;margin-bottom:12px}.source-card .database-card-title{color:var(--el-color-primary)}.target-card .database-card-title{color:#079455}.database-arrow{text-align:center;color:var(--el-color-primary);font-size:28px;font-weight:700}.pane-heading{font-size:13px;font-weight:600;margin-bottom:10px}.table-step-title{display:flex;justify-content:space-between;align-items:center;gap:20px}.target-prefix-toolbar{display:flex;align-items:center;gap:8px;font-size:12px;font-weight:400;color:var(--ds-text-secondary)}.target-prefix-toolbar .el-input{width:220px}
.table-selector{display:grid;grid-template-columns:1.1fr .9fr;border:1px solid var(--ds-border);border-radius:4px;min-height:470px;overflow:hidden}.table-source-pane{border-right:1px solid var(--ds-border);padding:16px}.table-selected-pane{padding:16px;background:#fafbfc}.pane-toolbar{display:flex;gap:8px;margin-bottom:12px}.pane-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.table-check-list{display:flex;flex-direction:column;max-height:405px;overflow:auto}.table-check-item{margin-right:0!important;padding:9px 8px;border-bottom:1px solid #f1f3f5}.table-name{display:inline-block;min-width:180px;color:var(--ds-text-primary)}.table-comment{color:var(--ds-text-secondary);font-size:12px}.empty-selection{padding:48px 0;text-align:center;color:var(--ds-text-secondary)}.selected-row{display:grid;grid-template-columns:1fr 24px 1fr;align-items:center;padding:9px 0;border-bottom:1px solid var(--ds-border);font-size:13px}.selected-row-editable{grid-template-columns:minmax(0,1fr) 24px minmax(0,1fr) 52px;gap:6px}.target-mapping-row{grid-template-columns:minmax(120px,1fr) 24px minmax(180px,1.2fr) 52px;gap:6px}.source-table-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.selected-row-editable>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.arrow{text-align:center;color:var(--el-color-primary)}
.mapping-table{width:100%;border:1px solid var(--ds-border);border-radius:4px;overflow:hidden}.mapping-head,.mapping-row{display:grid;grid-template-columns:1fr 1fr;gap:18px;align-items:center;padding:10px 14px}.mapping-head{background:#f7f8fa;color:var(--ds-text-secondary);font-size:12px}.mapping-row{border-top:1px solid var(--ds-border)}.mapping-source{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}
.confirm-mappings{margin-top:20px;border:1px solid var(--ds-border);border-radius:4px}.confirm-overview{margin-bottom:16px}.confirm-grid{display:grid;grid-template-columns:.9fr 1.1fr;gap:14px}.confirm-panel{border:1px solid var(--ds-border);border-radius:4px;overflow:hidden;background:#fff}.confirm-panel-title{min-height:42px;padding:8px 12px;background:#f7f8fa;display:flex;align-items:center;justify-content:space-between;font-weight:600}.confirm-mapping-list{max-height:360px;overflow:auto}.seatunnel-panel{min-width:0}.seatunnel-config-viewer{height:360px;overflow:auto;background:#111827;padding:12px 14px}.seatunnel-config-viewer pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:11px;line-height:1.55}.seatunnel-config-tip{padding:8px 12px;font-size:11px;color:var(--ds-text-secondary);background:#fafbfc;border-top:1px solid var(--ds-border)}.confirm-title{padding:10px 14px;background:#f7f8fa;font-weight:600}.confirm-row{display:grid;grid-template-columns:1fr 36px 1fr;padding:9px 14px;border-top:1px solid var(--ds-border);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}.drawer-footer{display:flex;align-items:center;width:100%}.log-box{min-height:300px;max-height:520px;overflow:auto;background:#111827;border-radius:4px;padding:14px}.log-box pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}

.runtime-summary,.schedule-summary{display:flex;flex-direction:column;gap:4px;font-size:12px;line-height:1.45;color:var(--ds-text-secondary)}.runtime-summary strong{color:var(--ds-text-primary);font-weight:600}.schedule-online{color:var(--ds-success)}.schedule-offline{color:#98a2b3}.task-name-link{border:0;background:transparent;padding:0;color:var(--el-color-primary);font:inherit;cursor:pointer;text-align:left}.task-name-link:hover{text-decoration:underline}.time-cell{white-space:nowrap;font-size:13px;color:var(--ds-text-primary)}.task-more{display:inline-flex;margin-left:12px}.danger-menu-item{color:var(--el-color-danger)}
:deep(.offline-task-row){cursor:pointer}:deep(.offline-task-row td.el-table__cell){padding:7px 0}:deep(.offline-task-row .cell){padding-left:7px;padding-right:7px}:deep(.el-table th.el-table__cell){padding:8px 0}:deep(.el-table th .cell){padding-left:7px;padding-right:7px}:deep(.offline-task-row:hover>td.el-table__cell){background:#f5f8ff!important}
.detail-head{width:100%;display:flex;align-items:flex-start;justify-content:space-between;gap:24px;padding-right:8px}.detail-head h3{margin:4px 0 6px;font-size:20px;color:var(--ds-text-primary)}.detail-eyebrow{font-size:12px;color:var(--el-color-primary);font-weight:600}.detail-subtitle{font-size:12px;color:var(--ds-text-secondary)}.detail-actions{display:flex;gap:8px;flex:0 0 auto}.task-detail{padding:0 2px 24px}.detail-summary{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid var(--ds-border);background:#fff;margin:2px 0 18px}.summary-item{min-height:72px;padding:12px 16px;border-right:1px solid var(--ds-border);display:flex;flex-direction:column;justify-content:center;gap:8px}.summary-item:last-child{border-right:0}.summary-item>span{font-size:12px;color:var(--ds-text-secondary)}.summary-item>strong{font-size:14px;color:var(--ds-text-primary);font-weight:600}.detail-tabs{margin-top:4px}.detail-section{margin-top:14px}.detail-section.no-top{margin-top:0}.detail-section-title{font-size:14px;font-weight:600;color:var(--ds-text-primary);margin:0 0 10px}.readonly-code{display:block;white-space:pre-wrap;word-break:break-all;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;color:#475467;background:#f8fafc;padding:2px 6px;border-radius:3px}.object-name{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:#344054}.object-name.target{color:var(--el-color-primary)}

.editor-steps :deep(.el-step__title){white-space:nowrap!important;font-size:14px!important}.editor-steps :deep(.el-step.is-simple .el-step__main){min-width:max-content}.editor-steps :deep(.el-step.is-simple){min-width:0;padding:0 14px}
.history-shell{height:calc(100vh - 104px);display:flex;flex-direction:column;gap:10px;min-height:520px}.history-records{flex:0 0 auto;max-height:255px;overflow:auto;display:flex;flex-direction:column;gap:10px;padding-right:2px}.history-batch-card{border:1px solid var(--ds-border);background:#fff}.history-batch-head{min-height:48px;padding:8px 12px;display:grid;grid-template-columns:minmax(220px,1fr) 100px 285px auto;align-items:center;gap:12px;background:#f8fafc;border-bottom:1px solid var(--ds-border);font-size:12px;color:var(--ds-text-secondary)}.history-batch-head>div:first-child{display:flex;align-items:center;gap:10px;min-width:0}.history-batch-head strong{color:var(--ds-text-primary)}.history-batch-head span{white-space:nowrap}.history-batch-actions{display:flex;justify-content:flex-end}.history-attempts{display:flex;flex-direction:column}.history-attempt-row,.legacy-run-row{width:100%;border:0;border-bottom:1px solid #eef0f2;background:#fff;padding:9px 12px;display:grid;grid-template-columns:100px 100px minmax(220px,1fr) 170px 72px;align-items:center;gap:10px;text-align:left;font:inherit;color:var(--ds-text-secondary);cursor:pointer}.history-attempt-row:last-child{border-bottom:0}.history-attempt-row:hover,.legacy-run-row:hover,.history-attempt-row.selected,.legacy-run-row.selected{background:#f5f8ff}.history-attempt-row.selected,.legacy-run-row.selected{box-shadow:inset 3px 0 0 var(--el-color-primary)}.history-attempt-row strong,.legacy-run-row strong{color:var(--ds-text-primary)}.legacy-run-row{grid-template-columns:minmax(260px,1fr) 110px 320px 72px;border:1px solid var(--ds-border);margin-bottom:8px}.view-log-text{color:var(--el-color-primary);white-space:nowrap}.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.compact-note{margin:0;padding:9px 12px}.history-log-panel{flex:1;min-height:0;display:flex;flex-direction:column;border:1px solid var(--ds-border);background:#fff}.history-log-toolbar{min-height:52px;padding:8px 12px;border-bottom:1px solid var(--ds-border);display:flex;align-items:center;justify-content:space-between;gap:16px}.history-log-toolbar>div:first-child{display:flex;align-items:center;gap:10px;min-width:0}.history-log-toolbar strong{color:var(--ds-text-primary)}.history-log-toolbar span{font-size:12px;color:var(--ds-text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-log-toolbar em{font-style:normal;font-size:12px;color:var(--ds-success);white-space:nowrap}.history-log-actions{display:flex;gap:6px;flex:0 0 auto}.history-log-viewer{flex:1;min-height:260px;overflow:auto;background:#111827;padding:14px 16px}.history-log-viewer pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}.history-log-panel.is-maximized{position:fixed;z-index:4000;inset:18px;background:#fff;border:1px solid #cfd4dc;box-shadow:0 16px 48px rgba(0,0,0,.24)}.history-log-panel.is-maximized .history-log-viewer{min-height:0}.muted-inline{font-size:12px;color:var(--ds-text-tertiary)}.detail-run-toolbar{display:flex;justify-content:flex-end;margin:0 0 10px}
@media (max-width:1000px){.form-grid,.table-selector,.confirm-grid,.database-pair{grid-template-columns:1fr}.database-arrow{transform:rotate(90deg)}.span-2{grid-column:auto}.table-source-pane{border-right:0;border-bottom:1px solid var(--ds-border)}}
.schedule-form{max-width:720px}.field-tip{margin-top:6px;font-size:12px;color:var(--ds-text-secondary)}.schedule-behavior-note{display:flex;flex-direction:column;gap:8px;margin-top:10px;padding:14px 16px;border:1px solid var(--ds-border);background:var(--ds-fill-lighter);border-radius:6px;font-size:13px;color:var(--ds-text-secondary)}.schedule-behavior-note strong{color:var(--ds-text-primary)}

.schedule-wheel-grid{display:grid;grid-template-columns:repeat(4,minmax(96px,1fr));gap:12px;width:100%}.schedule-wheel{display:flex;flex-direction:column;gap:6px}.schedule-wheel>span{font-size:12px;color:var(--ds-text-secondary);text-align:center}.schedule-wheel :deep(.el-select){width:100%}.schedule-wheel-grid.disabled{opacity:.65}@media(max-width:1100px){.schedule-wheel-grid{grid-template-columns:repeat(2,minmax(110px,1fr))}}
.schedule-config-box{width:100%;border:1px solid var(--ds-border);border-radius:6px;background:#fff;overflow:hidden}.schedule-config-box.disabled{opacity:.65}.schedule-mode-tabs{display:flex;border-bottom:1px solid var(--ds-border);background:#fafbfc}.schedule-mode-tabs button{height:38px;padding:0 20px;border:0;border-right:1px solid var(--ds-border);background:transparent;color:var(--ds-text-secondary);cursor:pointer;font-weight:600}.schedule-mode-tabs button.active{background:#fff;color:var(--el-color-primary);box-shadow:inset 0 -2px 0 var(--el-color-primary)}.schedule-quick{padding:14px}.schedule-custom{padding:0 14px 14px}.schedule-unit-tabs{display:grid;grid-template-columns:repeat(5,1fr);border:1px solid var(--ds-border);border-top:0;background:#f7f8fa}.schedule-unit-tabs button{height:38px;border:0;border-right:1px solid var(--ds-border);background:transparent;color:var(--ds-text-secondary);cursor:pointer;font-weight:600}.schedule-unit-tabs button:last-child{border-right:0}.schedule-unit-tabs button.active{background:#fff;color:var(--el-color-primary)}.schedule-unit-picker{padding:14px 0 0}.schedule-preview-card{margin:0 14px 14px;padding:12px 14px;border:1px solid #d8e5f5;border-radius:6px;background:#f7fbff}.schedule-preview-title{font-size:12px;color:#8793a5;margin-bottom:8px}.schedule-preview-row{display:flex;align-items:center;gap:8px;height:28px;font-size:13px;color:#344054}.schedule-preview-row i{width:20px;height:20px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;background:var(--el-color-primary);color:#fff;font-style:normal;font-size:11px}.schedule-preview-empty{font-size:12px;color:var(--ds-text-tertiary)}
</style>
