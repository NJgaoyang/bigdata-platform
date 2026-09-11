<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi, type DataSource, type SeaTunnelCluster } from '../api'

type TableOption = { name: string; comment?: string }
type IntegrationTable = {
  id?: number
  taskId?: number
  sourceDatabase: string
  sourceTable: string
  targetDatabase: string
  targetTable: string
  partitionColumn?: string
}
type Instance = {
  id: number
  executionId?: string
  status?: string
  startedAt?: string
  finishedAt?: string
  message?: string
}
type TaskRow = {
  id: number
  name: string
  sourceType: string
  targetType: string
  syncMode: string
  status: string
  sourceConfigJson?: string
  targetConfigJson?: string
  transformConfigJson?: string
  seatunnelConfig?: string
  tables: IntegrationTable[]
  latest?: Instance
}

const loading = ref(false)
const tasks = ref<TaskRow[]>([])
const keyword = ref('')
const statusFilter = ref('')
const createVisible = ref(false)
const saving = ref(false)
const logVisible = ref(false)
const logTitle = ref('SeaTunnel 运行日志')
const logContent = ref('')
const configVisible = ref(false)
const configTitle = ref('SeaTunnel 配置')
const configContent = ref('')
const sources = ref<DataSource[]>([])
const targets = ref<DataSource[]>([])
const clusters = ref<SeaTunnelCluster[]>([])
const sourceDatabases = ref<TableOption[]>([])
const sourceTables = ref<TableOption[]>([])
const sourceDbLoading = ref(false)
const sourceTableLoading = ref(false)

const form = reactive({
  name: '',
  syncMode: 'FULL',
  clusterId: undefined as number | undefined,
  sourceDataSourceId: undefined as number | undefined,
  sourceDatabase: '',
  sourceTables: [] as string[],
  targetDataSourceId: undefined as number | undefined,
  targetDatabase: 'ods',
  targetTable: '',
  schemaSaveMode: 'CREATE_SCHEMA_WHEN_NOT_EXIST',
  dataSaveMode: 'APPEND_DATA',
  parallelism: 2,
  batchSize: 1000,
  starrocksHttpPort: 8030,
  where: '',
  startupMode: 'initial',
  startupTimestamp: '',
  checkpointSeconds: 10,
  serverId: '',
})

function authHeaders(json = false) {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = localStorage.getItem('platform_access_token')
  if (token) headers.Authorization = `Bearer ${token}`
  if (json) headers['Content-Type'] = 'application/json'
  return headers
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: { ...authHeaders(options.body != null), ...(options.headers || {}) },
  })
  let payload: any = {}
  try { payload = await response.json() } catch { payload = {} }
  if (!response.ok || payload.success === false) throw new Error(payload.message || `请求失败（HTTP ${response.status}）`)
  return payload.data as T
}

function normalizeStatus(value?: string) {
  const status = String(value || '').trim().toUpperCase()
  if (status.includes('RUNNING') || status.includes('SUBMITTED')) return 'RUNNING'
  if (status.includes('SUCCESS') || status.includes('FINISHED')) return 'SUCCESS'
  if (status.includes('FAIL') || status.includes('ERROR')) return 'FAILED'
  if (status.includes('CANCEL') || status.includes('STOP')) return 'STOPPED'
  if (status.includes('LOST')) return 'LOST'
  return 'PENDING'
}
function statusText(value?: string) {
  return ({ RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', STOPPED: '已停止', LOST: '状态丢失', PENDING: '待运行' } as Record<string, string>)[normalizeStatus(value)]
}
function modeText(mode?: string) {
  const value = String(mode || 'FULL').toUpperCase()
  if (value === 'REALTIME') return '实时 CDC'
  if (value === 'INCREMENTAL') return '增量'
  return '全量'
}
function tableText(task: TaskRow, side: 'source' | 'target') {
  const list = Array.isArray(task.tables) ? task.tables : []
  if (!list.length) return '—'
  if (list.length > 1) return `${list.length} 张表`
  const row = list[0]
  return side === 'source' ? `${row.sourceDatabase}.${row.sourceTable}` : `${row.targetDatabase}.${row.targetTable}`
}
function latestStatus(task: TaskRow) { return task.latest?.status || task.status }

const filteredTasks = computed(() => tasks.value.filter((task) => {
  const hitKeyword = !keyword.value.trim() || task.name.toLowerCase().includes(keyword.value.trim().toLowerCase()) || tableText(task, 'source').toLowerCase().includes(keyword.value.trim().toLowerCase())
  const hitStatus = !statusFilter.value || normalizeStatus(latestStatus(task)) === statusFilter.value
  return hitKeyword && hitStatus
}))
const runningCount = computed(() => tasks.value.filter((task) => normalizeStatus(latestStatus(task)) === 'RUNNING').length)
const successCount = computed(() => tasks.value.filter((task) => normalizeStatus(latestStatus(task)) === 'SUCCESS').length)
const failedCount = computed(() => tasks.value.filter((task) => ['FAILED', 'LOST'].includes(normalizeStatus(latestStatus(task)))).length)
const selectedSource = computed(() => sources.value.find((item) => item.id === form.sourceDataSourceId))
const selectedTarget = computed(() => targets.value.find((item) => item.id === form.targetDataSourceId))

async function loadTasks() {
  loading.value = true
  try {
    const raw = (await platformApi.integrations()).data.data || []
    const rows = raw.map((item: any) => ({ ...item, id: Number(item.id), tables: Array.isArray(item.tables) ? item.tables : [] })) as TaskRow[]
    await Promise.all(rows.map(async (task) => {
      try {
        const instances = (await platformApi.integrationInstances(task.id)).data.data || []
        task.latest = instances[0] as Instance | undefined
      } catch { task.latest = undefined }
    }))
    tasks.value = rows
  } catch (error: any) {
    tasks.value = []
    ElMessage.error(error?.message || '同步任务加载失败')
  } finally { loading.value = false }
}

async function loadReferences() {
  const [sourceResult, clusterResult] = await Promise.all([platformApi.dataSources(), platformApi.clusters()])
  const all = sourceResult.data.data || []
  sources.value = all.filter((item) => String(item.type).toUpperCase() === 'MYSQL')
  targets.value = all.filter((item) => String(item.type).toUpperCase() === 'STARROCKS')
  clusters.value = clusterResult.data.data || []
}

function resetForm() {
  Object.assign(form, {
    name: '', syncMode: 'FULL', clusterId: undefined,
    sourceDataSourceId: undefined, sourceDatabase: '', sourceTables: [],
    targetDataSourceId: undefined, targetDatabase: 'ods', targetTable: '',
    schemaSaveMode: 'CREATE_SCHEMA_WHEN_NOT_EXIST', dataSaveMode: 'APPEND_DATA',
    parallelism: 2, batchSize: 1000, starrocksHttpPort: 8030, where: '',
    startupMode: 'initial', startupTimestamp: '', checkpointSeconds: 10, serverId: '',
  })
  sourceDatabases.value = []
  sourceTables.value = []
}
async function openCreate() {
  resetForm()
  try {
    await loadReferences()
    if (sources.value.length === 1) {
      form.sourceDataSourceId = sources.value[0].id
      await changeSource()
    }
    if (targets.value.length === 1) {
      form.targetDataSourceId = targets.value[0].id
      form.targetDatabase = targets.value[0].databaseName || 'ods'
    }
    if (!sources.value.length || !targets.value.length) ElMessage.warning('请先在系统中配置至少一个 MySQL 数据源和一个 StarRocks 数据源')
    createVisible.value = true
  } catch (error: any) { ElMessage.error(error?.message || '加载同步任务创建数据失败') }
}

async function changeSource() {
  form.sourceDatabase = ''
  form.sourceTables = []
  sourceTables.value = []
  if (!form.sourceDataSourceId) return
  sourceDbLoading.value = true
  try {
    sourceDatabases.value = (await platformApi.integrationSourceDatabases(form.sourceDataSourceId)).data.data || []
    const configured = selectedSource.value?.databaseName
    if (configured && sourceDatabases.value.some((item) => item.name === configured)) form.sourceDatabase = configured
    else if (sourceDatabases.value.length === 1) form.sourceDatabase = sourceDatabases.value[0].name
    if (form.sourceDatabase) await changeSourceDatabase()
  } catch (error: any) { ElMessage.error(error?.message || '读取 MySQL 数据库失败') }
  finally { sourceDbLoading.value = false }
}
async function changeSourceDatabase() {
  form.sourceTables = []
  sourceTables.value = []
  if (!form.sourceDataSourceId || !form.sourceDatabase) return
  sourceTableLoading.value = true
  try { sourceTables.value = (await platformApi.integrationSourceTables(form.sourceDataSourceId, form.sourceDatabase)).data.data || [] }
  catch (error: any) { ElMessage.error(error?.message || '读取 MySQL 表失败') }
  finally { sourceTableLoading.value = false }
}
function changeTarget() {
  if (selectedTarget.value?.databaseName) form.targetDatabase = selectedTarget.value.databaseName
  else if (!form.targetDatabase) form.targetDatabase = 'ods'
}
watch(() => form.sourceTables, (tables) => {
  if (tables.length === 1 && !form.targetTable) form.targetTable = tables[0]
  if (tables.length !== 1) form.targetTable = ''
}, { deep: true })

function validateCreate() {
  if (!form.name.trim()) return '请填写任务名称'
  if (!selectedSource.value || !form.sourceDatabase || !form.sourceTables.length) return '请完整选择 MySQL 源数据'
  if (!selectedTarget.value || !form.targetDatabase.trim()) return '请完整选择 StarRocks 目标数据'
  if (form.syncMode === 'INCREMENTAL' && !form.where.trim()) return '增量同步必须填写 WHERE 条件'
  if (form.syncMode === 'REALTIME' && form.startupMode === 'timestamp' && !form.startupTimestamp.trim()) return 'timestamp 启动方式必须填写毫秒时间戳'
  return ''
}

async function createTask() {
  const validation = validateCreate()
  if (validation) { ElMessage.warning(validation); return }
  const source = selectedSource.value!
  const target = selectedTarget.value!
  const tables = form.sourceTables.map((sourceTable) => ({
    sourceDatabase: form.sourceDatabase,
    sourceTable,
    targetDatabase: form.targetDatabase.trim(),
    targetTable: form.sourceTables.length === 1 ? (form.targetTable.trim() || sourceTable) : sourceTable,
    partitionColumn: '',
  }))
  const options: Record<string, unknown> = {
    clusterId: form.clusterId || null,
    schemaSaveMode: form.schemaSaveMode,
    dataSaveMode: form.dataSaveMode,
    parallelism: Number(form.parallelism) || 2,
    batchSize: Number(form.batchSize) || 1000,
    starrocksHttpPort: Number(form.starrocksHttpPort) || 8030,
  }
  if (form.syncMode === 'INCREMENTAL') options.where = form.where.trim()
  if (form.syncMode === 'REALTIME') {
    options.startupMode = form.startupMode
    options.startupTimestamp = form.startupTimestamp.trim()
    options.checkpointSeconds = Number(form.checkpointSeconds) || 10
    options.serverId = form.serverId.trim()
  }
  const body = {
    name: form.name.trim(), sourceType: 'MYSQL', targetType: 'STARROCKS', syncMode: form.syncMode,
    sourceDataSourceId: source.id, targetDataSourceId: target.id,
    source: { host: source.host, port: source.port, database: form.sourceDatabase, username: source.username || 'root', password: '', table: tables[0].sourceTable },
    target: { host: target.host, port: target.port, database: form.targetDatabase.trim(), username: target.username || 'root', password: '', table: tables[0].targetTable },
    mappings: [], options, tables,
  }
  saving.value = true
  try {
    await request('/integration/tasks', { method: 'POST', body: JSON.stringify(body) })
    createVisible.value = false
    ElMessage.success('同步任务已创建，SeaTunnel 2.3.12 配置已生成')
    await loadTasks()
  } catch (error: any) { ElMessage.error(error?.message || '同步任务创建失败') }
  finally { saving.value = false }
}

async function runTask(task: TaskRow) {
  try {
    const result = await request<{ executionId?: string; status?: string }>(`/integration/tasks/${task.id}/run`, { method: 'POST' })
    ElMessage.success(`任务已提交${result?.executionId ? `：${result.executionId}` : ''}`)
    await loadTasks()
  } catch (error: any) { ElMessage.error(error?.message || '任务运行失败') }
}
async function stopTask(task: TaskRow) {
  try {
    await ElMessageBox.confirm(`确认停止“${task.name}”的最新执行实例吗？`, '停止同步任务', { type: 'warning' })
    await request(`/integration/tasks/${task.id}/stop`, { method: 'POST' })
    ElMessage.success('停止请求已发送')
    await loadTasks()
  } catch (error: any) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '停止失败') }
}
async function syncSchema(task: TaskRow, recreate = false) {
  try {
    if (recreate) await ElMessageBox.confirm('重建会删除并重新创建目标表结构，是否继续？', '重建目标表', { type: 'warning' })
    const rows = await request<any[]>(`/integration/tasks/${task.id}/sync-schema?recreate=${recreate}`, { method: 'POST' })
    ElMessage.success(`目标表结构处理完成${rows?.length ? `，${rows.length} 张表` : ''}`)
  } catch (error: any) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '表结构同步失败') }
}
async function openLog(task: TaskRow) {
  try {
    const instances = await request<Instance[]>(`/integration/tasks/${task.id}/instances`)
    const executionId = instances?.[0]?.executionId
    if (!executionId) { ElMessage.warning('该任务还没有运行实例'); return }
    logTitle.value = `${task.name} · ${executionId}`
    logContent.value = await request<string>(`/integration/tasks/executions/${encodeURIComponent(executionId)}/log`) || '暂无日志'
    logVisible.value = true
  } catch (error: any) { ElMessage.error(error?.message || '运行日志加载失败') }
}
function openConfig(task: TaskRow) {
  configTitle.value = `${task.name} · SeaTunnel 2.3.12 配置`
  configContent.value = task.seatunnelConfig || '当前任务暂无可显示配置'
  configVisible.value = true
}
async function deleteTask(task: TaskRow) {
  try {
    await ElMessageBox.confirm(`确认删除同步任务“${task.name}”吗？`, '删除同步任务', { type: 'warning' })
    await request(`/integration/tasks/${task.id}`, { method: 'DELETE' })
    ElMessage.success('同步任务已删除')
    await loadTasks()
  } catch (error: any) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '删除失败') }
}

onMounted(loadTasks)
</script>

<template>
  <section class="integration-page">
    <div class="page-head">
      <div><div class="eyebrow">SEATUNNEL 2.3.12</div><h1>数据集成</h1><p>MySQL → StarRocks 的全量、增量与实时 CDC 同步任务。</p></div>
      <div class="head-actions"><button class="btn-default" :disabled="loading" @click="loadTasks">刷新</button><button class="btn-primary" @click="openCreate">＋ 新建同步任务</button></div>
    </div>

    <div class="stats">
      <div class="stat"><span>同步任务</span><b>{{ tasks.length }}</b></div>
      <div class="stat"><span>运行中</span><b>{{ runningCount }}</b></div>
      <div class="stat"><span>成功</span><b>{{ successCount }}</b></div>
      <div class="stat"><span>失败 / 丢失</span><b>{{ failedCount }}</b></div>
    </div>

    <div class="panel">
      <div class="toolbar"><input v-model="keyword" placeholder="搜索任务名称或源表"><select v-model="statusFilter"><option value="">全部状态</option><option value="RUNNING">运行中</option><option value="SUCCESS">成功</option><option value="FAILED">失败</option><option value="LOST">状态丢失</option><option value="PENDING">待运行</option></select></div>
      <div class="table-wrap">
        <table class="task-table">
          <thead><tr><th>任务名称</th><th>数据来源</th><th>数据目标</th><th>同步模式</th><th>运行状态</th><th>最近执行</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="task in filteredTasks" :key="task.id">
              <td><b>{{ task.name }}</b><small>TASK-{{ task.id }}</small></td>
              <td>MYSQL · {{ tableText(task, 'source') }}</td>
              <td>STARROCKS · {{ tableText(task, 'target') }}</td>
              <td><span class="mode-tag">{{ modeText(task.syncMode) }}</span></td>
              <td><span class="status" :class="normalizeStatus(latestStatus(task)).toLowerCase()">{{ statusText(latestStatus(task)) }}</span></td>
              <td class="muted">{{ task.latest?.startedAt || '—' }}</td>
              <td class="actions"><button @click="runTask(task)">运行</button><button v-if="normalizeStatus(latestStatus(task)) === 'RUNNING'" @click="stopTask(task)">停止</button><button @click="syncSchema(task)">同步表结构</button><button @click="openLog(task)">日志</button><button @click="openConfig(task)">配置</button><button class="danger" @click="deleteTask(task)">删除</button></td>
            </tr>
            <tr v-if="!filteredTasks.length"><td colspan="7" class="empty">{{ loading ? '正在加载...' : '暂无同步任务' }}</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建 SeaTunnel 同步任务" width="min(920px, 94vw)" destroy-on-close>
      <div class="create-form">
        <div class="section-title">基本信息</div>
        <label><span>任务名称</span><input v-model="form.name" placeholder="例如：订单同步"></label>
        <label><span>同步模式</span><select v-model="form.syncMode"><option value="FULL">全量 FULL</option><option value="INCREMENTAL">增量 INCREMENTAL</option><option value="REALTIME">实时 REALTIME / MySQL CDC</option></select></label>
        <label class="full"><span>SeaTunnel 运行集群</span><select v-model="form.clusterId"><option :value="undefined">本机 SeaTunnel</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }} · {{ cluster.host }}:{{ cluster.sshPort || cluster.port }}</option></select></label>

        <div class="section-title">MySQL 数据来源</div>
        <label><span>源数据源</span><select v-model="form.sourceDataSourceId" @change="changeSource"><option :value="undefined">请选择 MySQL 数据源</option><option v-for="source in sources" :key="source.id" :value="source.id">{{ source.name }}</option></select></label>
        <label><span>源数据库</span><select v-model="form.sourceDatabase" :disabled="!form.sourceDataSourceId || sourceDbLoading" @change="changeSourceDatabase"><option value="">{{ sourceDbLoading ? '正在读取...' : '请选择数据库' }}</option><option v-for="db in sourceDatabases" :key="db.name" :value="db.name">{{ db.name }}</option></select></label>
        <label class="full"><span>源表（支持多选）</span><el-select v-model="form.sourceTables" multiple filterable :loading="sourceTableLoading" placeholder="请选择一张或多张表" style="width:100%"><el-option v-for="table in sourceTables" :key="table.name" :label="table.comment ? `${table.name} · ${table.comment}` : table.name" :value="table.name" /></el-select></label>

        <div class="section-title">StarRocks 数据目标</div>
        <label><span>目标数据源</span><select v-model="form.targetDataSourceId" @change="changeTarget"><option :value="undefined">请选择 StarRocks 数据源</option><option v-for="target in targets" :key="target.id" :value="target.id">{{ target.name }}</option></select></label>
        <label><span>目标数据库</span><input v-model="form.targetDatabase" placeholder="ods"></label>
        <label class="full"><span>单表目标表名（可选）</span><input v-model="form.targetTable" :disabled="form.sourceTables.length !== 1" placeholder="单表同步时可改名；多表默认同名"></label>

        <div class="section-title">目标表与写入策略</div>
        <label><span>Schema 保存策略</span><select v-model="form.schemaSaveMode"><option value="CREATE_SCHEMA_WHEN_NOT_EXIST">不存在时自动创建</option><option value="RECREATE_SCHEMA">重建目标表</option><option value="ERROR_WHEN_SCHEMA_NOT_EXIST">目标表不存在时报错</option><option value="IGNORE">忽略表结构处理</option></select></label>
        <label><span>数据保存策略</span><select v-model="form.dataSaveMode"><option value="APPEND_DATA">追加数据</option><option value="DROP_DATA">清空后写入</option><option value="ERROR_WHEN_DATA_EXISTS">已有数据时报错</option></select></label>

        <div class="section-title">SeaTunnel 2.3.12 参数</div>
        <label><span>并行度</span><input v-model.number="form.parallelism" type="number" min="1" max="128"></label>
        <label><span>StarRocks HTTP 端口</span><input v-model.number="form.starrocksHttpPort" type="number" min="1" max="65535"></label>
        <label v-if="form.syncMode !== 'REALTIME'"><span>JDBC Fetch Size</span><input v-model.number="form.batchSize" type="number" min="1"></label>
        <label v-if="form.syncMode === 'INCREMENTAL'"><span>WHERE 条件</span><input v-model="form.where" placeholder="update_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)"></label>
        <template v-if="form.syncMode === 'REALTIME'">
          <label><span>CDC 启动方式</span><select v-model="form.startupMode"><option value="initial">initial：快照 + binlog</option><option value="earliest">earliest</option><option value="latest">latest</option><option value="timestamp">timestamp</option></select></label>
          <label><span>Checkpoint（秒）</span><input v-model.number="form.checkpointSeconds" type="number" min="1"></label>
          <label v-if="form.startupMode === 'timestamp'"><span>启动时间戳（毫秒）</span><input v-model="form.startupTimestamp" placeholder="例如：1757462400000"></label>
          <label><span>CDC Server ID（可选）</span><input v-model="form.serverId" placeholder="例如：5656-5660"></label>
        </template>
      </div>
      <template #footer><button class="btn-default" @click="createVisible = false">取消</button><button class="btn-primary" :disabled="saving" @click="createTask">{{ saving ? '创建中...' : '创建任务' }}</button></template>
    </el-dialog>

    <el-dialog v-model="logVisible" :title="logTitle" width="min(980px, 95vw)"><pre class="code-box">{{ logContent }}</pre></el-dialog>
    <el-dialog v-model="configVisible" :title="configTitle" width="min(980px, 95vw)"><pre class="code-box">{{ configContent }}</pre></el-dialog>
  </section>
</template>

<style scoped>
.integration-page{padding:24px;min-height:100%;background:#f5f7fb;color:#26364d}.page-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;margin-bottom:18px}.page-head h1{margin:3px 0 6px;font-size:24px}.page-head p{margin:0;color:#74839a}.eyebrow{font-size:11px;letter-spacing:1.4px;color:#3478f6;font-weight:700}.head-actions{display:flex;gap:9px}.btn-primary,.btn-default{height:36px;padding:0 15px;border-radius:7px;border:1px solid #d7e0ec;background:#fff;color:#465a73;cursor:pointer}.btn-primary{background:#3478f6;border-color:#3478f6;color:#fff}.btn-primary:disabled,.btn-default:disabled{opacity:.55;cursor:not-allowed}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:14px}.stat{background:#fff;border:1px solid #e4eaf2;border-radius:10px;padding:16px 18px}.stat span{display:block;color:#718197;font-size:12px}.stat b{display:block;margin-top:8px;font-size:24px;color:#1d2d44}.panel{background:#fff;border:1px solid #e4eaf2;border-radius:10px;overflow:hidden}.toolbar{display:flex;gap:10px;padding:13px 15px;border-bottom:1px solid #edf1f5}.toolbar input{width:320px}.toolbar input,.toolbar select,.create-form input,.create-form select{height:36px;border:1px solid #d8e1ec;border-radius:6px;padding:0 10px;background:#fff;color:#31445e;outline:none}.toolbar input:focus,.create-form input:focus,.create-form select:focus{border-color:#6d9cf2;box-shadow:0 0 0 2px #eef4ff}.table-wrap{overflow:auto}.task-table{width:100%;border-collapse:collapse;min-width:1100px}.task-table th,.task-table td{padding:12px 13px;border-bottom:1px solid #edf1f5;text-align:left;font-size:12px}.task-table th{background:#fafbfd;color:#718197;font-weight:600}.task-table td b{display:block;color:#26364d}.task-table td small{display:block;color:#9aa6b6;margin-top:3px}.muted{color:#8492a5}.mode-tag{display:inline-flex;padding:3px 8px;border-radius:5px;background:#edf4ff;color:#3478f6}.status{display:inline-flex;padding:3px 8px;border-radius:999px;background:#f2f4f7;color:#667085}.status.running{background:#eaf3ff;color:#2875e3}.status.success{background:#e9f8f1;color:#14966b}.status.failed,.status.lost{background:#fff0ef;color:#df4c4c}.status.stopped{background:#f3f4f6;color:#697386}.actions{white-space:nowrap}.actions button{border:0;background:transparent;color:#2875e3;cursor:pointer;padding:3px 5px;font-size:12px}.actions button.danger{color:#d74b4b}.empty{text-align:center!important;color:#98a4b5;padding:48px!important}.create-form{display:grid;grid-template-columns:1fr 1fr;gap:13px 16px}.section-title{grid-column:1/-1;padding-top:10px;margin-top:2px;border-top:1px solid #edf1f5;font-weight:700;color:#2b3d55}.section-title:first-child{border-top:0;padding-top:0}.create-form label{display:grid;gap:6px;font-size:12px;color:#5e7088}.create-form label.full{grid-column:1/-1}.create-form input:disabled{background:#f5f7fa;color:#98a4b5}.code-box{max-height:62vh;overflow:auto;margin:0;padding:16px;border-radius:8px;background:#0f1724;color:#d6e2f0;font:12px/1.65 Consolas,Monaco,monospace;white-space:pre-wrap}.el-dialog__footer .btn-default{margin-right:8px}@media(max-width:900px){.stats{grid-template-columns:1fr 1fr}.create-form{grid-template-columns:1fr}.create-form label.full,.section-title{grid-column:1}.page-head{flex-direction:column}}
</style>
