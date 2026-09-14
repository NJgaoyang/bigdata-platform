<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import { integrationApi, type IntegrationAttempt, type IntegrationBatch, type IntegrationCursor, type IntegrationInstance, type IntegrationTask, type IntegrationTaskPayload, type IntegrationTaskSummary } from '../../api/domain'

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
const detailTab = ref('overview')
const editorStep = ref(0)
const editorMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const keyword = ref('')
const modeFilter = ref('')
const tableKeyword = ref('')
const sourceDbs = ref<string[]>([])
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
  sourceDataSourceId: 0,
  targetDataSourceId: 0,
  sourceDatabase: '',
  targetDatabase: 'ods',
  selectedTables: [] as string[],
  targetPrefix: '',
  syncMode: 'FULL',
  where: '',
  schemaSaveMode: 'CREATE_SCHEMA_WHEN_NOT_EXIST'
})

const mysql = computed(() => sources.value.filter(source => source.type === 'MYSQL'))
const starrocks = computed(() => sources.value.filter(source => source.type === 'STARROCKS'))
const filteredSourceTables = computed(() => {
  const q = tableKeyword.value.trim().toLowerCase()
  return q ? sourceTables.value.filter(table => `${table.name} ${table.comment || ''}`.toLowerCase().includes(q)) : sourceTables.value
})
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
    sourceDataSourceId: mysql.value[0]?.id || 0,
    targetDataSourceId: starrocks.value[0]?.id || 0,
    sourceDatabase: '',
    targetDatabase: 'ods',
    selectedTables: [],
    targetPrefix: '',
    syncMode: 'FULL',
    where: '',
    schemaSaveMode: 'CREATE_SCHEMA_WHEN_NOT_EXIST'
  })
  sourceDbs.value = []
  sourceTables.value = []
  tableKeyword.value = ''
  editorStep.value = 0
  editingId.value = null
  for (const key of Object.keys(targetTables)) delete targetTables[key]
}

async function openCreate() {
  editorMode.value = 'create'
  resetEditor()
  editorVisible.value = true
  if (form.sourceDataSourceId) await loadSourceDatabases(true)
}

async function openDetail(task: IntegrationTask) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'overview'
  detailTask.value = task
  detailBatches.value = []
  detailInstances.value = []
  try {
    const [fullTask, batchRows, instanceRows] = await Promise.all([
      integrationApi.get(task.id),
      integrationApi.batches(task.id),
      integrationApi.instances(task.id)
    ])
    detailTask.value = fullTask
    detailBatches.value = batchRows
    detailInstances.value = instanceRows
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
  form.syncMode = task.syncMode || 'FULL'
  const source = safeJson(task.sourceConfigJson)
  const target = safeJson(task.targetConfigJson)
  const transform = safeJson(task.transformConfigJson)
  form.sourceDataSourceId = matchDataSource('MYSQL', source)
  form.targetDataSourceId = matchDataSource('STARROCKS', target)
  form.sourceDatabase = task.tables?.[0]?.sourceDatabase || stringValue(source.database)
  form.targetDatabase = task.tables?.[0]?.targetDatabase || stringValue(target.database) || 'ods'
  form.where = stringValue(objectValue(transform.options).where)
  form.schemaSaveMode = stringValue(objectValue(transform.options).schemaSaveMode) || 'CREATE_SCHEMA_WHEN_NOT_EXIST'
  editorVisible.value = true
  if (form.sourceDataSourceId) await loadSourceDatabases(false)
  form.selectedTables = (task.tables || []).map(table => table.sourceTable)
  for (const table of task.tables || []) targetTables[table.sourceTable] = table.targetTable
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
    if (!form.targetDataSourceId) return '请选择 StarRocks 目标数据源'
  }
  if (step === 1 && !form.selectedTables.length) return '请至少选择一张来源表'
  if (step === 2) {
    if (!form.targetDatabase.trim()) return '请输入目标数据库'
    if (form.selectedTables.some(table => !targetTables[table]?.trim())) return '请为所有来源表配置目标表名'
  }
  if (step === 3 && form.syncMode === 'INCREMENTAL' && !form.where.trim()) return '条件增量同步需要配置 WHERE 条件'
  return ''
}

function nextStep() {
  const error = validateStep(editorStep.value)
  if (error) return ElMessage.warning(error)
  editorStep.value = Math.min(4, editorStep.value + 1)
}

function previousStep() {
  editorStep.value = Math.max(0, editorStep.value - 1)
}

function buildPayload(): IntegrationTaskPayload {
  const source = sources.value.find(item => item.id === form.sourceDataSourceId)
  const target = sources.value.find(item => item.id === form.targetDataSourceId)
  if (!source || !target) throw new Error('数据源不存在或已被删除')
  const firstSourceTable = form.selectedTables[0]
  return {
    name: form.name.trim(),
    sourceType: 'MYSQL',
    targetType: 'STARROCKS',
    syncMode: form.syncMode,
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
      where: form.syncMode === 'INCREMENTAL' ? form.where.trim() : '',
      schemaSaveMode: form.schemaSaveMode
    },
    tables: form.selectedTables.map(table => ({
      sourceDatabase: form.sourceDatabase,
      sourceTable: table,
      targetDatabase: form.targetDatabase.trim(),
      targetTable: targetTables[table].trim()
    }))
  }
}

async function saveTask() {
  for (let step = 0; step <= 3; step += 1) {
    const error = validateStep(step)
    if (error) {
      editorStep.value = step
      return ElMessage.warning(error)
    }
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (editorMode.value === 'edit' && editingId.value) {
      await integrationApi.update(editingId.value, payload)
      ElMessage.success('离线同步任务已更新')
    } else {
      await integrationApi.create(payload)
      ElMessage.success('离线同步任务已创建')
    }
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
    await integrationApi.run(task.id)
    ElMessage.success('离线同步任务已提交')
    setTimeout(load, 800)
  } catch (error) {
    ElMessage.error(messageOf(error))
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
  const transform = safeJson(task.transformConfigJson)
  const mode = stringValue(objectValue(transform.options).schemaSaveMode)
  return mode === 'RECREATE_SCHEMA' ? '运行前重建目标表' : '目标表不存在时自动创建'
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
  const labels: Record<string, string> = { MANUAL: '手动', WORKFLOW: '工作流', BACKFILL: '补数' }
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
  <div class="ds-page">
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
        <el-table-column label="任务名称" min-width="210">
          <template #default="scope">
            <button class="task-name-link" @click.stop="openDetail(scope.row)">{{ scope.row.name }}</button>
          </template>
        </el-table-column>
        <el-table-column label="表数" width="72"><template #default="scope">{{ scope.row.tables?.length || 0 }}</template></el-table-column>
        <el-table-column label="同步方式" width="105"><template #default="scope">{{ modeLabel(scope.row.syncMode) }}</template></el-table-column>
        <el-table-column label="最近状态" width="120"><template #default="scope"><StatusBadge :status="latestStatus(scope.row)" :label="statusLabel(latestStatus(scope.row))" /></template></el-table-column>
        <el-table-column label="执行概况" min-width="150">
          <template #default="scope"><div class="runtime-summary"><span>数据量：<strong>{{ formatCount(taskSummary(scope.row)?.dataCount) }}</strong></span><span>耗时：{{ formatDuration(taskSummary(scope.row)?.durationMs) }}</span></div></template>
        </el-table-column>
        <el-table-column label="调度" min-width="210">
          <template #default="scope"><div class="schedule-summary"><span>状态：<strong :class="isOnline(scope.row) ? 'schedule-online' : 'schedule-offline'">{{ isOnline(scope.row) ? '已开启' : '已下线' }}</strong></span><span>上次：{{ formatDateTime(taskSummary(scope.row)?.lastRunAt) }}</span><span>下次：{{ formatDateTime(taskSummary(scope.row)?.nextRunAt) }}</span></div></template>
        </el-table-column>
        <el-table-column label="创建时间" width="175"><template #default="scope"><span class="time-cell">{{ taskCreatedAt(scope.row) }}</span></template></el-table-column>
        <el-table-column label="创建人" width="110"><template #default="scope">{{ taskCreatedBy(scope.row) }}</template></el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
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

    <el-drawer v-model="detailVisible" size="960px" destroy-on-close class="task-detail-drawer">
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

    <el-drawer v-model="editorVisible" :title="editorMode === 'edit' ? '编辑离线同步任务' : '新建离线同步任务'" size="920px" destroy-on-close>
      <div class="editor-shell">
        <el-steps :active="editorStep" finish-status="success" simple class="editor-steps">
          <el-step title="选择数据源" />
          <el-step title="选择数据表" />
          <el-step title="目标配置" />
          <el-step title="同步配置" />
          <el-step title="确认配置" />
        </el-steps>

        <div class="editor-body">
          <template v-if="editorStep === 0">
            <div class="section-title">任务与数据源</div>
            <div class="section-tip">离线同步固定使用 MySQL 作为来源、StarRocks 作为目标；SeaTunnel 执行环境由系统设置统一维护。</div>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="任务名称" class="span-2"><el-input v-model="form.name" placeholder="例如 ods_order_full" /></el-form-item>
              <el-form-item label="MySQL 来源">
                <el-select v-model="form.sourceDataSourceId" filterable style="width:100%" @change="loadSourceDatabases(true)">
                  <el-option v-for="source in mysql" :key="source.id" :label="source.name" :value="source.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="源数据库">
                <el-select v-model="form.sourceDatabase" filterable style="width:100%" @change="loadSourceTables(true)">
                  <el-option v-for="database in sourceDbs" :key="database" :label="database" :value="database" />
                </el-select>
              </el-form-item>
              <el-form-item label="StarRocks 目标" class="span-2">
                <el-select v-model="form.targetDataSourceId" filterable style="width:100%">
                  <el-option v-for="source in starrocks" :key="source.id" :label="source.name" :value="source.id" />
                </el-select>
              </el-form-item>
            </el-form>
          </template>

          <template v-else-if="editorStep === 1">
            <div class="section-title">选择来源表</div>
            <div class="table-selector">
              <div class="table-source-pane">
                <div class="pane-toolbar">
                  <el-input v-model="tableKeyword" clearable placeholder="搜索表名或备注" />
                  <el-button @click="selectAllVisible">全选当前</el-button>
                </div>
                <el-checkbox-group v-model="form.selectedTables" class="table-check-list" @change="ensureTargetMappings">
                  <el-checkbox v-for="table in filteredSourceTables" :key="table.name" :value="table.name" class="table-check-item">
                    <span class="table-name">{{ table.name }}</span><span v-if="table.comment" class="table-comment">{{ table.comment }}</span>
                  </el-checkbox>
                </el-checkbox-group>
              </div>
              <div class="table-selected-pane">
                <div class="pane-title"><strong>已选择 {{ form.selectedTables.length }} 张表</strong><el-button link @click="clearSelectedTables">清空</el-button></div>
                <div v-if="!form.selectedTables.length" class="empty-selection">从左侧勾选需要同步的表</div>
                <div v-for="table in form.selectedTables" :key="table" class="selected-row selected-row-editable">
                  <span>{{ table }}</span><span class="arrow">→</span><span>{{ targetTables[table] }}</span>
                  <el-button link type="danger" @click="removeSelectedTable(table)">移除</el-button>
                </div>
              </div>
            </div>
          </template>

          <template v-else-if="editorStep === 2">
            <div class="section-title">StarRocks 目标配置</div>
            <el-form label-position="top">
              <div class="form-grid">
                <el-form-item label="目标数据库"><el-input v-model="form.targetDatabase" placeholder="ods" /></el-form-item>
                <el-form-item label="目标表前缀">
                  <div class="prefix-input"><el-input v-model="form.targetPrefix" placeholder="可选，例如 ods_" /><el-button @click="applyTargetPrefix">应用到全部</el-button></div>
                </el-form-item>
              </div>
              <el-form-item label="表映射">
                <div class="mapping-table">
                  <div class="mapping-head"><span>MySQL 来源表</span><span>StarRocks 目标表</span></div>
                  <div v-for="table in form.selectedTables" :key="table" class="mapping-row">
                    <span class="mapping-source">{{ form.sourceDatabase }}.{{ table }}</span>
                    <el-input v-model="targetTables[table]" />
                  </div>
                </div>
              </el-form-item>
            </el-form>
          </template>

          <template v-else-if="editorStep === 3">
            <div class="section-title">同步策略</div>
            <div class="section-tip">调度策略不在离线任务中重复配置。需要定时运行时，在工作流中引用该离线同步任务。</div>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="同步方式">
                <el-radio-group v-model="form.syncMode">
                  <el-radio-button value="FULL">全量同步</el-radio-button>
                  <el-radio-button value="INCREMENTAL">条件增量</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="目标表处理">
                <el-select v-model="form.schemaSaveMode" style="width:100%">
                  <el-option label="不存在时自动建表" value="CREATE_SCHEMA_WHEN_NOT_EXIST" />
                  <el-option label="每次运行前重建目标表" value="RECREATE_SCHEMA" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="form.syncMode === 'INCREMENTAL'" label="WHERE 条件" class="span-2">
                <el-input v-model="form.where" type="textarea" :rows="4" placeholder="例如 updated_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)" />
              </el-form-item>
            </el-form>
          </template>

          <template v-else>
            <div class="section-title">确认配置</div>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="任务名称">{{ form.name }}</el-descriptions-item>
              <el-descriptions-item label="同步方式">{{ modeLabel(form.syncMode) }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ mysql.find(item => item.id === form.sourceDataSourceId)?.name || '—' }} / {{ form.sourceDatabase }}</el-descriptions-item>
              <el-descriptions-item label="目标">{{ starrocks.find(item => item.id === form.targetDataSourceId)?.name || '—' }} / {{ form.targetDatabase }}</el-descriptions-item>
              <el-descriptions-item label="同步表数">{{ form.selectedTables.length }} 张</el-descriptions-item>
              <el-descriptions-item label="执行引擎">SeaTunnel（平台统一执行环境）</el-descriptions-item>
            </el-descriptions>
            <div class="confirm-mappings">
              <div class="confirm-title">表映射</div>
              <div v-for="table in form.selectedTables" :key="table" class="confirm-row">
                <span>{{ form.sourceDatabase }}.{{ table }}</span><span>→</span><span>{{ form.targetDatabase }}.{{ targetTables[table] }}</span>
              </div>
            </div>
          </template>
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="editorVisible = false">取消</el-button>
          <div class="ds-spacer" />
          <el-button v-if="editorStep > 0" @click="previousStep">上一步</el-button>
          <el-button v-if="editorStep < 4" type="primary" @click="nextStep">下一步</el-button>
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
.editor-shell{display:flex;flex-direction:column;min-height:620px}.editor-steps{margin-bottom:24px}.editor-body{flex:1;padding:0 4px}
.section-title{font-size:16px;font-weight:600;color:var(--ds-text-primary);margin-bottom:8px}.section-tip{font-size:13px;color:var(--ds-text-secondary);margin-bottom:22px;line-height:1.7}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.span-2{grid-column:1 / -1}.prefix-input{display:flex;gap:8px;width:100%}
.table-selector{display:grid;grid-template-columns:1.1fr .9fr;border:1px solid var(--ds-border);border-radius:4px;min-height:470px;overflow:hidden}.table-source-pane{border-right:1px solid var(--ds-border);padding:16px}.table-selected-pane{padding:16px;background:#fafbfc}.pane-toolbar{display:flex;gap:8px;margin-bottom:12px}.pane-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.table-check-list{display:flex;flex-direction:column;max-height:405px;overflow:auto}.table-check-item{margin-right:0!important;padding:9px 8px;border-bottom:1px solid #f1f3f5}.table-name{display:inline-block;min-width:180px;color:var(--ds-text-primary)}.table-comment{color:var(--ds-text-secondary);font-size:12px}.empty-selection{padding:48px 0;text-align:center;color:var(--ds-text-secondary)}.selected-row{display:grid;grid-template-columns:1fr 24px 1fr;align-items:center;padding:9px 0;border-bottom:1px solid var(--ds-border);font-size:13px}.selected-row-editable{grid-template-columns:minmax(0,1fr) 24px minmax(0,1fr) 52px;gap:6px}.selected-row-editable>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.arrow{text-align:center;color:var(--el-color-primary)}
.mapping-table{width:100%;border:1px solid var(--ds-border);border-radius:4px;overflow:hidden}.mapping-head,.mapping-row{display:grid;grid-template-columns:1fr 1fr;gap:18px;align-items:center;padding:10px 14px}.mapping-head{background:#f7f8fa;color:var(--ds-text-secondary);font-size:12px}.mapping-row{border-top:1px solid var(--ds-border)}.mapping-source{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}
.confirm-mappings{margin-top:20px;border:1px solid var(--ds-border);border-radius:4px}.confirm-title{padding:10px 14px;background:#f7f8fa;font-weight:600}.confirm-row{display:grid;grid-template-columns:1fr 36px 1fr;padding:9px 14px;border-top:1px solid var(--ds-border);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}.drawer-footer{display:flex;align-items:center;width:100%}.log-box{min-height:300px;max-height:520px;overflow:auto;background:#111827;border-radius:4px;padding:14px}.log-box pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}

.runtime-summary,.schedule-summary{display:flex;flex-direction:column;gap:4px;font-size:12px;line-height:1.45;color:var(--ds-text-secondary)}.runtime-summary strong{color:var(--ds-text-primary);font-weight:600}.schedule-online{color:var(--ds-success)}.schedule-offline{color:#98a2b3}.task-name-link{border:0;background:transparent;padding:0;color:var(--el-color-primary);font:inherit;cursor:pointer;text-align:left}.task-name-link:hover{text-decoration:underline}.time-cell{white-space:nowrap;font-size:13px;color:var(--ds-text-primary)}.task-more{display:inline-flex;margin-left:12px}.danger-menu-item{color:var(--el-color-danger)}
:deep(.offline-task-row){cursor:pointer}:deep(.offline-task-row:hover>td.el-table__cell){background:#f5f8ff!important}
.detail-head{width:100%;display:flex;align-items:flex-start;justify-content:space-between;gap:24px;padding-right:8px}.detail-head h3{margin:4px 0 6px;font-size:20px;color:var(--ds-text-primary)}.detail-eyebrow{font-size:12px;color:var(--el-color-primary);font-weight:600}.detail-subtitle{font-size:12px;color:var(--ds-text-secondary)}.detail-actions{display:flex;gap:8px;flex:0 0 auto}.task-detail{padding:0 2px 24px}.detail-summary{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid var(--ds-border);background:#fff;margin:2px 0 18px}.summary-item{min-height:72px;padding:12px 16px;border-right:1px solid var(--ds-border);display:flex;flex-direction:column;justify-content:center;gap:8px}.summary-item:last-child{border-right:0}.summary-item>span{font-size:12px;color:var(--ds-text-secondary)}.summary-item>strong{font-size:14px;color:var(--ds-text-primary);font-weight:600}.detail-tabs{margin-top:4px}.detail-section{margin-top:14px}.detail-section.no-top{margin-top:0}.detail-section-title{font-size:14px;font-weight:600;color:var(--ds-text-primary);margin:0 0 10px}.readonly-code{display:block;white-space:pre-wrap;word-break:break-all;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;color:#475467;background:#f8fafc;padding:2px 6px;border-radius:3px}.object-name{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:#344054}.object-name.target{color:var(--el-color-primary)}

.editor-steps :deep(.el-step__title){white-space:nowrap!important;font-size:14px!important}.editor-steps :deep(.el-step.is-simple .el-step__main){min-width:max-content}.editor-steps :deep(.el-step.is-simple){min-width:0;padding:0 14px}
.history-shell{height:calc(100vh - 104px);display:flex;flex-direction:column;gap:10px;min-height:520px}.history-records{flex:0 0 auto;max-height:255px;overflow:auto;display:flex;flex-direction:column;gap:10px;padding-right:2px}.history-batch-card{border:1px solid var(--ds-border);background:#fff}.history-batch-head{min-height:48px;padding:8px 12px;display:grid;grid-template-columns:minmax(220px,1fr) 100px 285px auto;align-items:center;gap:12px;background:#f8fafc;border-bottom:1px solid var(--ds-border);font-size:12px;color:var(--ds-text-secondary)}.history-batch-head>div:first-child{display:flex;align-items:center;gap:10px;min-width:0}.history-batch-head strong{color:var(--ds-text-primary)}.history-batch-head span{white-space:nowrap}.history-batch-actions{display:flex;justify-content:flex-end}.history-attempts{display:flex;flex-direction:column}.history-attempt-row,.legacy-run-row{width:100%;border:0;border-bottom:1px solid #eef0f2;background:#fff;padding:9px 12px;display:grid;grid-template-columns:100px 100px minmax(220px,1fr) 170px 72px;align-items:center;gap:10px;text-align:left;font:inherit;color:var(--ds-text-secondary);cursor:pointer}.history-attempt-row:last-child{border-bottom:0}.history-attempt-row:hover,.legacy-run-row:hover,.history-attempt-row.selected,.legacy-run-row.selected{background:#f5f8ff}.history-attempt-row.selected,.legacy-run-row.selected{box-shadow:inset 3px 0 0 var(--el-color-primary)}.history-attempt-row strong,.legacy-run-row strong{color:var(--ds-text-primary)}.legacy-run-row{grid-template-columns:minmax(260px,1fr) 110px 320px 72px;border:1px solid var(--ds-border);margin-bottom:8px}.view-log-text{color:var(--el-color-primary);white-space:nowrap}.mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.compact-note{margin:0;padding:9px 12px}.history-log-panel{flex:1;min-height:0;display:flex;flex-direction:column;border:1px solid var(--ds-border);background:#fff}.history-log-toolbar{min-height:52px;padding:8px 12px;border-bottom:1px solid var(--ds-border);display:flex;align-items:center;justify-content:space-between;gap:16px}.history-log-toolbar>div:first-child{display:flex;align-items:center;gap:10px;min-width:0}.history-log-toolbar strong{color:var(--ds-text-primary)}.history-log-toolbar span{font-size:12px;color:var(--ds-text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-log-toolbar em{font-style:normal;font-size:12px;color:var(--ds-success);white-space:nowrap}.history-log-actions{display:flex;gap:6px;flex:0 0 auto}.history-log-viewer{flex:1;min-height:260px;overflow:auto;background:#111827;padding:14px 16px}.history-log-viewer pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}.history-log-panel.is-maximized{position:fixed;z-index:4000;inset:18px;background:#fff;border:1px solid #cfd4dc;box-shadow:0 16px 48px rgba(0,0,0,.24)}.history-log-panel.is-maximized .history-log-viewer{min-height:0}.muted-inline{font-size:12px;color:var(--ds-text-tertiary)}.detail-run-toolbar{display:flex;justify-content:flex-end;margin:0 0 10px}
@media (max-width:1000px){.form-grid,.table-selector{grid-template-columns:1fr}.span-2{grid-column:auto}.table-source-pane{border-right:0;border-bottom:1px solid var(--ds-border)}}
</style>
