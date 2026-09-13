<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import { integrationApi, type IntegrationInstance, type IntegrationTask, type IntegrationTaskPayload } from '../../api/domain'

const tasks = ref<IntegrationTask[]>([])
const sources = ref<DataSourceView[]>([])
const latest = ref<Record<number, IntegrationInstance | undefined>>({})
const loading = ref(false)
const saving = ref(false)
const editorVisible = ref(false)
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
const logVisible = ref(false)
const logLoading = ref(false)
const logTitle = ref('运行日志')
const logContent = ref('')

const form = reactive({
  name: '',
  sourceDataSourceId: 0,
  targetDataSourceId: 0,
  sourceDatabase: '',
  targetDatabase: 'ods',
  selectedTables: [] as string[],
  targetPrefix: 'ods_',
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
    const pairs: Array<[number, IntegrationInstance | undefined]> = await Promise.all(
      tasks.value.map(async task => [task.id, (await integrationApi.instances(task.id))[0]])
    )
    latest.value = Object.fromEntries(pairs)
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
    targetPrefix: 'ods_',
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

async function openEdit(task: IntegrationTask) {
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
  try {
    await ElMessageBox.confirm(`删除“${task.name}”？删除后无法恢复。`, '删除离线同步任务', { type: 'warning' })
    await integrationApi.remove(task.id)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(messageOf(error))
  }
}

async function showHistory(task: IntegrationTask) {
  historyTask.value = task
  historyVisible.value = true
  historyLoading.value = true
  try {
    history.value = await integrationApi.instances(task.id)
  } catch (error) {
    ElMessage.error(messageOf(error))
  } finally {
    historyLoading.value = false
  }
}

async function showLog(instance: IntegrationInstance) {
  logVisible.value = true
  logLoading.value = true
  logTitle.value = `运行日志 · ${instance.executionId}`
  logContent.value = ''
  try {
    logContent.value = await integrationApi.log(instance.executionId)
  } catch (error) {
    logContent.value = messageOf(error)
  } finally {
    logLoading.value = false
  }
}

function latestStatus(task: IntegrationTask) {
  return latest.value[task.id]?.status || '未运行'
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

onMounted(load)
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

      <el-table :data="filteredTasks" v-loading="loading">
        <el-table-column prop="name" label="任务名称" min-width="180" />
        <el-table-column label="来源 → 目标" min-width="190"><template #default="scope">{{ path(scope.row) }}</template></el-table-column>
        <el-table-column label="表数" width="80"><template #default="scope">{{ scope.row.tables?.length || 0 }}</template></el-table-column>
        <el-table-column label="同步方式" width="110"><template #default="scope">{{ modeLabel(scope.row.syncMode) }}</template></el-table-column>
        <el-table-column label="最近状态" width="130"><template #default="scope"><StatusBadge :status="latestStatus(scope.row)" /></template></el-table-column>
        <el-table-column label="最近运行" min-width="165"><template #default="scope">{{ latest[scope.row.id]?.startedAt || '—' }}</template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="primary" @click="run(scope.row)">运行</el-button>
            <el-button link :disabled="!canStop(scope.row)" @click="stop(scope.row)">停止</el-button>
            <el-button link @click="showHistory(scope.row)">运行记录</el-button>
            <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

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
                <div v-for="table in form.selectedTables" :key="table" class="selected-row">
                  <span>{{ table }}</span><span class="arrow">→</span><span>{{ targetTables[table] }}</span>
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
                  <div class="prefix-input"><el-input v-model="form.targetPrefix" placeholder="ods_" /><el-button @click="applyTargetPrefix">应用到全部</el-button></div>
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

    <el-drawer v-model="historyVisible" :title="`运行记录 · ${historyTask?.name || ''}`" size="760px">
      <el-table :data="history" v-loading="historyLoading">
        <el-table-column prop="executionId" label="执行 ID" min-width="210" show-overflow-tooltip />
        <el-table-column label="状态" width="120"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
        <el-table-column prop="startedAt" label="开始时间" min-width="165" />
        <el-table-column prop="finishedAt" label="结束时间" min-width="165" />
        <el-table-column label="操作" width="80"><template #default="scope"><el-button link type="primary" @click="showLog(scope.row)">日志</el-button></template></el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="logVisible" :title="logTitle" width="820px">
      <div v-loading="logLoading" class="log-box"><pre>{{ logContent || '暂无日志' }}</pre></div>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-wrap{gap:10px}.toolbar-count{font-size:12px;color:var(--ds-text-secondary)}
.editor-shell{display:flex;flex-direction:column;min-height:620px}.editor-steps{margin-bottom:24px}.editor-body{flex:1;padding:0 4px}
.section-title{font-size:16px;font-weight:600;color:var(--ds-text-primary);margin-bottom:8px}.section-tip{font-size:13px;color:var(--ds-text-secondary);margin-bottom:22px;line-height:1.7}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.span-2{grid-column:1 / -1}.prefix-input{display:flex;gap:8px;width:100%}
.table-selector{display:grid;grid-template-columns:1.1fr .9fr;border:1px solid var(--ds-border);border-radius:4px;min-height:470px;overflow:hidden}.table-source-pane{border-right:1px solid var(--ds-border);padding:16px}.table-selected-pane{padding:16px;background:#fafbfc}.pane-toolbar{display:flex;gap:8px;margin-bottom:12px}.pane-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.table-check-list{display:flex;flex-direction:column;max-height:405px;overflow:auto}.table-check-item{margin-right:0!important;padding:9px 8px;border-bottom:1px solid #f1f3f5}.table-name{display:inline-block;min-width:180px;color:var(--ds-text-primary)}.table-comment{color:var(--ds-text-secondary);font-size:12px}.empty-selection{padding:48px 0;text-align:center;color:var(--ds-text-secondary)}.selected-row{display:grid;grid-template-columns:1fr 24px 1fr;align-items:center;padding:9px 0;border-bottom:1px solid var(--ds-border);font-size:13px}.arrow{text-align:center;color:var(--el-color-primary)}
.mapping-table{width:100%;border:1px solid var(--ds-border);border-radius:4px;overflow:hidden}.mapping-head,.mapping-row{display:grid;grid-template-columns:1fr 1fr;gap:18px;align-items:center;padding:10px 14px}.mapping-head{background:#f7f8fa;color:var(--ds-text-secondary);font-size:12px}.mapping-row{border-top:1px solid var(--ds-border)}.mapping-source{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}
.confirm-mappings{margin-top:20px;border:1px solid var(--ds-border);border-radius:4px}.confirm-title{padding:10px 14px;background:#f7f8fa;font-weight:600}.confirm-row{display:grid;grid-template-columns:1fr 36px 1fr;padding:9px 14px;border-top:1px solid var(--ds-border);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13px}.drawer-footer{display:flex;align-items:center;width:100%}.log-box{min-height:300px;max-height:520px;overflow:auto;background:#111827;border-radius:4px;padding:14px}.log-box pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}
@media (max-width:1000px){.form-grid,.table-selector{grid-template-columns:1fr}.span-2{grid-column:auto}.table-source-pane{border-right:0;border-bottom:1px solid var(--ds-border)}}
</style>
