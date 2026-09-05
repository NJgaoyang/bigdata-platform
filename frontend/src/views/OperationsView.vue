<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { platformApi } from '../api'

const search = ref('')
const typeFilter = ref('')
const statusFilter = ref('')
const health = ref<Record<string, unknown>>({})
type Operation = { id: string; taskId: string; processInstanceId: string; name: string; type: string; start: string; duration: string; status: string; cls: string }
const instances = ref<Operation[]>([])
const taskInstances = ref<Record<string, unknown>[]>([])
const failedInstances = ref<Record<string, unknown>[]>([])
const logVisible = ref(false)
const logLoading = ref(false)
const logTitle = ref('任务日志')
const logContent = ref('')
const panelVisible = ref(false)
const panelMode = ref<'alerts' | 'search'>('alerts')
const logKeyword = ref('')
const searchedTasks = computed(() => taskInstances.value.filter(item => !logKeyword.value || String(item.name || '').toLowerCase().includes(logKeyword.value.toLowerCase())))
async function loadInstances() {
  try {
    const [healthResponse, response, taskResponse, failedResponse] = await Promise.all([
      platformApi.health(), platformApi.operationInstances(), platformApi.operationTasks(), platformApi.failedOperations()
    ])
    health.value = healthResponse.data.data || {}
    const tasks = taskResponse.data.data || []
    taskInstances.value = tasks
    failedInstances.value = failedResponse.data.data || []
    const firstTaskByProcess = new Map<string, Record<string, unknown>>()
    tasks.forEach(task => {
      const processId = String(task.processInstanceId || '')
      if (processId && !firstTaskByProcess.has(processId)) firstTaskByProcess.set(processId, task)
    })
    instances.value = (response.data.data || []).map(item => {
      const status = String(item.status || 'UNKNOWN')
      const normalized = status === 'SUCCESS' ? '成功' : status === 'RUNNING_EXECUTION' ? '运行中' : status === 'FAILURE' ? '失败' : status
      const processInstanceId = String(item.processInstanceId || item.id || '')
      const firstTask = firstTaskByProcess.get(processInstanceId)
      return { id: String(item.id || item.name || ''), taskId: String(firstTask?.id || ''), processInstanceId, name: String(item.name || '未命名实例'), type: '工作流', start: String(item.startTime || '—'), duration: '—', status: normalized, cls: normalized === '成功' ? 'ok' : normalized === '失败' ? 'bad' : 'run' }
    })
  } catch (error) { health.value = { status: 'DOWN' }; ElMessage.error(error instanceof Error ? error.message : '运维数据加载失败'); }
}
onMounted(loadInstances)
const successCount = computed(() => instances.value.filter(item => item.status === '成功').length)
const runningCount = computed(() => instances.value.filter(item => item.status === '运行中').length)
const failedCount = computed(() => failedInstances.value.length || instances.value.filter(item => item.status === '失败').length)
const filteredInstances = computed(() => instances.value.filter(item =>
  (!search.value || item.name.includes(search.value)) &&
  (!typeFilter.value || item.type === typeFilter.value) &&
  (!statusFilter.value || item.status === statusFilter.value)
))
function action(message: string) { ElMessage.success(message) }
function applyFilters() {
  search.value = search.value.trim()
  void loadInstances()
}
function openPanel(mode: 'alerts' | 'search') { panelMode.value = mode; panelVisible.value = true }
async function openLog(item: Operation) {
  logTitle.value = `${item.name} · 任务日志`
  logContent.value = ''
  logVisible.value = true
  logLoading.value = true
  try {
    if (!item.taskId) { logContent.value = '该流程暂未生成可查询的任务实例'; return }
    logContent.value = (await platformApi.operationLog(item.taskId)).data.data || '暂无运行日志'
  } catch { logContent.value = '日志加载失败，请确认后端已启用真实调度器模式' }
  finally { logLoading.value = false }
}
async function rerun(item: Operation) {
  try { await platformApi.rerunOperation(item.id); action('实例已重新提交'); await loadInstances() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '实例重跑失败') }
}
async function stop(item: Operation) {
  try { await platformApi.stopOperation(item.id); action('停止请求已提交'); await loadInstances() } catch (error) { ElMessage.error(error instanceof Error ? error.message : '实例停止失败') }
}
function openTaskLog(task: Record<string, unknown>) {
  openLog({ id: String(task.id || ''), taskId: String(task.id || ''), processInstanceId: String(task.processInstanceId || ''), name: String(task.name || '任务'), type: '任务', start: '', duration: '', status: String(task.status || ''), cls: 'ok' })
}
</script>

<template>
  <section class="page">
    <div class="module-bar"><div class="module-title">运维中心 <span class="crumb">/ 任务实例</span></div><div class="module-actions"><button class="btn-default" @click="openPanel('alerts')">告警记录</button><button class="btn-default" @click="openPanel('search')">日志检索</button></div></div>
    <div class="page-body">
      <div class="metrics"><div class="metric"><div class="metric-icon blue">◷</div><div><div class="metric-label">今日实例</div><div class="metric-value">{{ instances.length }}</div></div></div><div class="metric"><div class="metric-icon green">✓</div><div><div class="metric-label">成功</div><div class="metric-value">{{ successCount }}</div></div></div><div class="metric"><div class="metric-icon orange">!</div><div><div class="metric-label">失败</div><div class="metric-value">{{ failedCount }}</div></div></div><div class="metric"><div class="metric-icon purple">↗</div><div><div class="metric-label">运行中</div><div class="metric-value">{{ runningCount }}</div></div></div></div>
      <div class="card"><div class="card-head"><span>流程实例</span><span class="muted">API 状态：<b class="api-up">{{ health.status || 'UP' }}</b> · 模式：{{ health.mode === 'real' ? '真实调度器' : '开发模式' }}</span></div><div class="filterbar"><input v-model="search" placeholder="搜索实例名称..."/><select v-model="typeFilter"><option value="">全部类型</option><option value="工作流">工作流</option><option value="同步任务">同步任务</option></select><select v-model="statusFilter"><option value="">全部状态</option><option value="成功">成功</option><option value="运行中">运行中</option><option value="失败">失败</option></select><button class="btn-primary" @click="applyFilters">查询</button><button class="btn-default" @click="search = ''; typeFilter = ''; statusFilter = ''">重置</button></div><table class="data-table"><thead><tr><th>实例名称</th><th>类型</th><th>开始时间</th><th>耗时</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="instance in filteredInstances" :key="instance.id"><td class="mono">{{ instance.name }}</td><td>{{ instance.type }}</td><td class="muted">{{ instance.start }}</td><td>{{ instance.duration }}</td><td><span class="status-dot" :class="instance.cls"></span>{{ instance.status }}</td><td class="actions"><button @click="openLog(instance)">日志</button><button @click="rerun(instance)">重跑</button><button @click="stop(instance)">停止</button></td></tr><tr v-if="!filteredInstances.length"><td colspan="6" class="empty-state">暂无符合条件的实例记录</td></tr></tbody></table><div class="subsection-title">任务实例明细</div><table class="data-table task-table"><thead><tr><th>任务名称</th><th>类型</th><th>流程实例</th><th>开始时间</th><th>状态</th><th>日志</th></tr></thead><tbody><tr v-for="task in taskInstances" :key="String(task.id)"><td class="mono">{{ task.name }}</td><td>{{ task.taskType || '—' }}</td><td>{{ task.processInstanceId || '—' }}</td><td class="muted">{{ task.startTime || '—' }}</td><td>{{ task.status || '—' }}</td><td><button class="link-action" @click="openTaskLog(task)">查看</button></td></tr><tr v-if="!taskInstances.length"><td colspan="6" class="empty-state">暂无任务明细</td></tr></tbody></table></div>
    <el-dialog v-model="logVisible" :title="logTitle" width="min(900px, 88vw)" class="log-dialog"><div v-loading="logLoading" class="log-content"><pre>{{ logContent }}</pre></div></el-dialog>
    <el-dialog v-model="panelVisible" :title="panelMode === 'alerts' ? '告警记录' : '日志检索'" width="min(820px, 90vw)"><template v-if="panelMode === 'alerts'"><table class="data-table"><thead><tr><th>任务</th><th>状态</th><th>开始时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in failedInstances" :key="String(item.id)"><td>{{ item.name || item.taskName || '失败任务' }}</td><td>{{ item.status || '失败' }}</td><td>{{ item.startTime || '—' }}</td><td><button class="link-action" @click="openTaskLog(item)">查看日志</button></td></tr><tr v-if="!failedInstances.length"><td colspan="4" class="empty-state">当前没有失败告警</td></tr></tbody></table></template><template v-else><input v-model="logKeyword" class="dialog-search" placeholder="输入任务名称检索"><table class="data-table"><thead><tr><th>任务</th><th>类型</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in searchedTasks" :key="String(item.id)"><td>{{ item.name }}</td><td>{{ item.taskType || '—' }}</td><td>{{ item.status || '—' }}</td><td><button class="link-action" @click="openTaskLog(item)">查看日志</button></td></tr><tr v-if="!searchedTasks.length"><td colspan="4" class="empty-state">没有匹配的任务</td></tr></tbody></table></template></el-dialog>
    </div>
  </section>
</template>

<style scoped>
.subsection-title { padding: 14px 12px 8px; color: #344054; font-size: 13px; font-weight: 700; border-top: 1px solid var(--line2); }
.task-table { margin-bottom: 8px; }
.log-content { min-height: 180px; max-height: 62vh; overflow: auto; background: #101722; border-radius: 6px; padding: 14px; color: #d7e4f5; }
.log-content pre { margin: 0; white-space: pre-wrap; word-break: break-word; font: 12px/1.6 Consolas, Monaco, monospace; }
.dialog-search { width:100%; height:34px; border:1px solid #d9e0e8; border-radius:6px; padding:0 10px; margin-bottom:12px; }
</style>
