<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api'

type Task = { id: string | number; name: string; project: string; source: string; target: string; mode: string; status: string; updated: string; executionId?: string }
const search = ref('')
const show = ref(false)
const statusFilter = ref('')
const logVisible = ref(false)
const logTitle = ref('同步任务日志')
const logContent = ref('')
const detailVisible = ref(false)
const selectedTask = ref<Task | null>(null)
const importInput = ref<HTMLInputElement>()
const polling = new Map<string, number>()
const form = ref({ name: 'ods_to_dw_customer', sourceType: 'MYSQL', targetType: 'STARROCKS', syncMode: 'FULL', sourceHost: '81.69.15.136', sourcePort: 3306, sourceDatabase: 'yzl_prd', sourceUsername: 'root', sourcePassword: '', sourceTable: 'yzl_order', targetHost: '81.69.15.136', targetPort: 9030, targetDatabase: 'ods', targetUsername: 'dev_0904', targetPassword: '', targetTable: 'yzl_order', where: '', parallelism: 1, batchSize: 1000, mappings: 'order_id:order_id' })
const tasks = ref<Task[]>([
  { id: '10001', name: 'ods_order_to_dwd', project: '数仓开发项目', source: 'MySQL · ods_order_info', target: 'StarRocks · dwd_order_info', mode: '全量', status: '运行中', updated: '09-03 14:26' },
  { id: '10002', name: 'ods_customer_to_dwd', project: '数仓开发项目', source: 'MySQL · ods_customer', target: 'StarRocks · dwd_customer', mode: '增量', status: '成功', updated: '09-03 13:00' },
  { id: '10003', name: 'dim_province_sync', project: '基础数据项目', source: 'MySQL · base_province', target: 'StarRocks · dim_base_province', mode: '全量', status: '成功', updated: '09-03 12:30' },
  { id: '10004', name: 'ads_trade_report', project: '营销分析项目', source: 'StarRocks · dws_trade', target: 'MySQL · ads_trade_report', mode: '全量', status: '失败', updated: '09-03 11:08' },
])
const filteredTasks = computed(() => tasks.value.filter(item => (!search.value || item.name.includes(search.value)) && (!statusFilter.value || item.status === statusFilter.value)))
const runningCount = computed(() => tasks.value.filter(item => item.status === '运行中').length)
const successCount = computed(() => tasks.value.filter(item => item.status === '成功').length)
const failedCount = computed(() => tasks.value.filter(item => item.status === '失败').length)

onMounted(async () => {
  try {
    const data = (await platformApi.integrations()).data.data || []
    if (data.length) tasks.value = data.map((item: Record<string, unknown>, index: number) => ({
      id: item.id as string | number || index, name: String(item.name || '未命名任务'), project: '数仓开发项目',
      source: `${item.sourceType || 'MYSQL'} · source`, target: `${item.targetType || 'STARROCKS'} · target`,
      mode: String(item.syncMode || '全量'), status: String(item.status || '待运行'), updated: '尚未运行',
    }))
  } catch { /* development mock keeps the prototype rows visible */ }
})

async function create() {
  try {
    const mappings = form.value.mappings.split(/[,\n]/).map(item => item.trim()).filter(Boolean).map(item => { const [source, target] = item.split(':').map(value => value.trim()); return { source, target: target || source } })
    const result = await platformApi.createIntegration({ name: form.value.name, sourceType: form.value.sourceType, targetType: form.value.targetType, syncMode: form.value.syncMode, source: { host: form.value.sourceHost, port: form.value.sourcePort, database: form.value.sourceDatabase, username: form.value.sourceUsername, password: form.value.sourcePassword, table: form.value.sourceTable }, target: { host: form.value.targetHost, port: form.value.targetPort, database: form.value.targetDatabase, username: form.value.targetUsername, password: form.value.targetPassword, table: form.value.targetTable }, mappings, options: { where: form.value.where, parallelism: form.value.parallelism, batchSize: form.value.batchSize } })
    const item = result.data.data || {}
    tasks.value.unshift({ id: String(item.id || Date.now()), name: form.value.name, project: '数仓开发项目', source: `${form.value.sourceType} · source`, target: `${form.value.targetType} · target`, mode: form.value.syncMode === 'FULL' ? '全量' : '增量', status: '待运行', updated: '刚刚' })
    show.value = false
    ElMessage.success('同步任务已创建，配置已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建同步任务失败')
  }
}

async function runTask(task: Task) {
  const id = Number(task.id)
  if (Number.isFinite(id)) {
    try {
      const result = await platformApi.runIntegration(id)
      task.status = '运行中'
      task.updated = '刚刚'
      task.executionId = String(result.data.data?.executionId || '')
      if (task.executionId) pollStatus(task)
      ElMessage.success('同步任务已提交运行')
      return
    } catch { /* keep prototype task actions available when the mock row has no backend record */ }
  }
  task.status = '运行中'
  task.updated = '刚刚'
  ElMessage.success('同步任务已提交运行')
}

async function openLog(task: Task) {
  const id = Number(task.id)
  if (Number.isFinite(id)) {
    try {
      const instances = (await platformApi.integrationInstances(id)).data.data || []
      const executionId = String(instances[0]?.executionId || '')
      if (executionId) {
        logTitle.value = `${task.name} · 运行日志`
        logContent.value = (await platformApi.integrationLog(executionId)).data.data || '暂无运行日志'
        logVisible.value = true
        return
      }
    } catch { /* fallback to the prototype interaction */ }
  }
  ElMessage.info('暂无运行日志')
}
async function pollStatus(task: Task) {
  if (!task.executionId || polling.has(task.executionId)) return
  const timer = window.setInterval(async () => {
    try {
      const result = await platformApi.integrationStatus(task.executionId!)
      const status = result.data.data?.status || ''
      task.status = status === 'FINISHED' ? '成功' : status === 'FAILED' ? '失败' : status === 'RUNNING' ? '运行中' : status
      task.updated = new Date().toLocaleTimeString()
      if (status === 'FINISHED' || status === 'FAILED' || status === 'NOT_FOUND') {
        window.clearInterval(timer); polling.delete(task.executionId!)
      }
    } catch { /* keep the latest known state */ }
  }, 2000)
  polling.set(task.executionId, timer)
}
onBeforeUnmount(() => polling.forEach(timer => window.clearInterval(timer)))

function action(message: string) { ElMessage.success(message) }
function openTask(task: Task) { selectedTask.value = task; detailVisible.value = true }
function chooseImport() { importInput.value?.click() }
async function importTask(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const data = JSON.parse(await file.text()) as Record<string, any>
    form.value.name = String(data.name || file.name.replace(/\.json$/i, ''))
    form.value.sourceType = String(data.sourceType || 'MYSQL')
    form.value.targetType = String(data.targetType || 'STARROCKS')
    form.value.syncMode = String(data.syncMode || 'FULL')
    Object.assign(form.value, { sourceHost: data.source?.host || form.value.sourceHost, sourcePort: Number(data.source?.port || form.value.sourcePort), sourceDatabase: data.source?.database || form.value.sourceDatabase, sourceTable: data.source?.table || form.value.sourceTable, sourceUsername: data.source?.username || form.value.sourceUsername, targetHost: data.target?.host || form.value.targetHost, targetPort: Number(data.target?.port || form.value.targetPort), targetDatabase: data.target?.database || form.value.targetDatabase, targetTable: data.target?.table || form.value.targetTable, targetUsername: data.target?.username || form.value.targetUsername })
    form.value.mappings = Array.isArray(data.mappings) ? data.mappings.map((item: any) => `${item.source}:${item.target || item.source}`).join(',') : form.value.mappings
    show.value = true
    ElMessage.success('任务配置已导入，请确认后保存')
  } catch { ElMessage.error('导入失败，请选择正确的 JSON 任务配置') }
  ;(event.target as HTMLInputElement).value = ''
}
</script>

<template>
  <section class="page">
    <div class="module-bar"><div class="module-title">数据集成 <span class="crumb">/ 离线同步</span></div><div class="module-actions"><input ref="importInput" type="file" accept="application/json,.json" hidden @change="importTask"><button class="btn-default" @click="chooseImport">导入任务</button><button class="btn-primary" @click="show = true">＋ 新建同步任务</button></div></div>
    <div class="page-body">
      <div class="metrics"><div class="metric"><div class="metric-icon blue">⇄</div><div><div class="metric-label">同步任务</div><div class="metric-value">{{ tasks.length }}</div></div></div><div class="metric"><div class="metric-icon green">✓</div><div><div class="metric-label">运行中</div><div class="metric-value">{{ runningCount }}</div></div></div><div class="metric"><div class="metric-icon purple">▣</div><div><div class="metric-label">今日成功</div><div class="metric-value">{{ successCount }}</div></div></div><div class="metric"><div class="metric-icon orange">!</div><div><div class="metric-label">失败</div><div class="metric-value">{{ failedCount }}</div></div></div></div>
      <div class="card">
        <div class="card-head"><span>离线同步任务</span><span class="muted">支持 MySQL、StarRocks、Hive、Kafka</span></div>
        <div class="filterbar"><input v-model="search" placeholder="搜索任务名称..."/><select><option>全部项目</option><option>数仓开发项目</option></select><select v-model="statusFilter"><option value="">全部状态</option><option value="运行中">运行中</option><option value="成功">成功</option><option value="失败">失败</option><option value="待运行">待运行</option></select><button class="btn-primary" @click="action('筛选条件已应用')">查询</button><button class="btn-default" @click="search = ''; statusFilter = ''">重置</button></div>
        <table class="data-table"><thead><tr><th>任务名称</th><th>项目</th><th>来源 → 目标</th><th>同步模式</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody><tr v-for="task in filteredTasks" :key="task.id"><td><button class="link-action" @click="openTask(task)">{{ task.name }}</button><div class="muted mono">TASK-{{ task.id }}</div></td><td>{{ task.project }}</td><td><span class="tag blue">{{ task.source }}</span><span class="arrow">→</span><span class="tag">{{ task.target }}</span></td><td>{{ task.mode }}</td><td><span class="status-dot" :class="task.status === '失败' ? 'bad' : task.status === '运行中' ? 'run' : 'ok'"></span>{{ task.status }}</td><td class="muted">{{ task.updated }}</td><td class="actions"><button @click="openTask(task)">编辑</button><button @click="runTask(task)">运行</button><button @click="openLog(task)">日志</button></td></tr><tr v-if="!filteredTasks.length"><td colspan="7" class="empty-state">暂无符合条件的同步任务</td></tr></tbody></table>
      </div>
    </div>
    <div v-if="show" class="modal-mask" @click.self="show = false"><div class="modal integration-modal"><div class="modal-head"><span>新建离线同步任务</span><button class="modal-close" @click="show = false">×</button></div><div class="modal-body"><div class="form-item"><label>任务名称</label><input v-model="form.name"></div><div class="form-row"><div class="form-item"><label>来源类型</label><select v-model="form.sourceType"><option>MYSQL</option><option>STARROCKS</option></select></div><div class="form-item"><label>目标类型</label><select v-model="form.targetType"><option>STARROCKS</option><option>MYSQL</option></select></div></div><div class="form-row"><div class="form-item"><label>来源主机</label><input v-model="form.sourceHost"></div><div class="form-item"><label>端口</label><input v-model.number="form.sourcePort" type="number"></div><div class="form-item"><label>数据库</label><input v-model="form.sourceDatabase"></div><div class="form-item"><label>表</label><input v-model="form.sourceTable"></div></div><div class="form-row"><div class="form-item"><label>目标主机</label><input v-model="form.targetHost"></div><div class="form-item"><label>端口</label><input v-model.number="form.targetPort" type="number"></div><div class="form-item"><label>数据库</label><input v-model="form.targetDatabase"></div><div class="form-item"><label>表</label><input v-model="form.targetTable"></div></div><div class="form-row"><div class="form-item"><label>来源用户</label><input v-model="form.sourceUsername"></div><div class="form-item"><label>来源密码</label><input v-model="form.sourcePassword" type="password"></div><div class="form-item"><label>目标用户</label><input v-model="form.targetUsername"></div><div class="form-item"><label>目标密码</label><input v-model="form.targetPassword" type="password"></div></div><div class="form-item"><label>字段映射（source:target，逗号或换行分隔）</label><textarea v-model="form.mappings" rows="2"></textarea></div><div class="form-row"><div class="form-item"><label>过滤条件（可选）</label><input v-model="form.where" placeholder="例如 order_status = 1"></div><div class="form-item"><label>并行度</label><input v-model.number="form.parallelism" type="number" min="1"></div><div class="form-item"><label>批量大小</label><input v-model.number="form.batchSize" type="number" min="1"></div></div><div class="form-item"><label>同步模式</label><select v-model="form.syncMode"><option>FULL</option><option>INCREMENTAL</option></select></div></div><div class="modal-foot"><button class="btn-default" @click="show = false">取消</button><button class="btn-primary" @click="create">生成配置</button></div></div></div>
    <el-dialog v-model="logVisible" :title="logTitle" width="min(900px, 88vw)"><div class="integration-log"><pre>{{ logContent }}</pre></div></el-dialog>
    <el-dialog v-model="detailVisible" title="同步任务配置" width="min(620px, 90vw)"><div v-if="selectedTask" class="task-detail"><div><span>任务名称</span><b>{{ selectedTask.name }}</b></div><div><span>来源</span><b>{{ selectedTask.source }}</b></div><div><span>目标</span><b>{{ selectedTask.target }}</b></div><div><span>同步模式</span><b>{{ selectedTask.mode }}</b></div><div><span>当前状态</span><b>{{ selectedTask.status }}</b></div></div><template #footer><button class="btn-default" @click="detailVisible = false">关闭</button><button class="btn-default" @click="selectedTask && openLog(selectedTask)">查看日志</button><button class="btn-primary" @click="selectedTask && runTask(selectedTask)">运行任务</button></template></el-dialog>
  </section>
</template>

<style scoped>
.integration-modal { width: min(980px, 92vw); }
.integration-modal textarea { width: 100%; resize: vertical; border: 1px solid var(--line); border-radius: 6px; padding: 8px; font: inherit; }
.integration-log { max-height: 62vh; overflow: auto; background: #101722; color: #d7e4f5; border-radius: 6px; padding: 14px; }
.integration-log pre { margin: 0; white-space: pre-wrap; word-break: break-word; font: 12px/1.6 Consolas, Monaco, monospace; }
.task-detail { display:grid; grid-template-columns:1fr 1fr; gap:12px; }
.task-detail div { padding:12px; border:1px solid var(--line); border-radius:7px; display:flex; flex-direction:column; gap:6px; }
.task-detail span { color:#8b95a5; font-size:12px; }
</style>
