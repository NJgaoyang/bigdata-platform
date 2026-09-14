<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { operationsApi, type OperationInstance } from '../../api/domain'

const rows = ref<OperationInstance[]>([])
const loading = ref(false)
const type = ref('ALL')
const logVisible = ref(false)
const logLoading = ref(false)
const logMaximized = ref(false)
const logText = ref('')
const logRow = ref<OperationInstance | null>(null)
let logTimer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try { rows.value = (await operationsApi.instances()).filter(x => ['OFFLINE', 'REALTIME'].includes(x.type)) }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '加载失败') }
  finally { loading.value = false }
}

async function stop(row: OperationInstance) {
  try { await operationsApi.stop(row.type, row.id); ElMessage.success('停止请求已提交'); await load() }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '停止失败') }
}
function fmt(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19)
}
function statusLabel(status?: string) {
  const value = (status || '').toUpperCase()
  if (!value) return '未知'
  if (value.includes('FINISHED') || value.includes('SUCCESS')) return '成功'
  if (value.includes('RUNNING')) return '运行中'
  if (value.includes('START') || value.includes('SUBMIT') || value.includes('QUEUED') || value.includes('PENDING')) return '等待运行'
  if (value.includes('FAIL') || value.includes('ERROR') || value.includes('LOST') || value.includes('UNKNOWN')) return '失败'
  if (value.includes('STOP') || value.includes('CANCEL')) return '已停止'
  return status || '未知'
}
function running(row?: OperationInstance | null) {
  return !!row && ['RUNNING', 'STARTING', 'QUEUED', 'SUBMITTED'].some(x => (row.status || '').toUpperCase().includes(x))
}
async function refreshLog(silent = false) {
  if (!logRow.value) return
  if (!silent) logLoading.value = true
  try { logText.value = await operationsApi.log(logRow.value.type, logRow.value.id) || '暂无日志' }
  catch (e) { if (!silent) ElMessage.error(e instanceof Error ? e.message : '日志加载失败') }
  finally { if (!silent) logLoading.value = false }
}
function startLogPolling() {
  stopLogPolling()
  logTimer = setInterval(() => { if (logVisible.value && running(logRow.value)) void refreshLog(true) }, 2000)
}
function stopLogPolling() { if (logTimer) clearInterval(logTimer); logTimer = null }
async function openLog(row: OperationInstance) {
  logRow.value = row; logText.value = ''; logVisible.value = true
  await refreshLog(); startLogPolling()
}
function closeLog() { stopLogPolling(); logMaximized.value = false }
function keydown(e: KeyboardEvent) { if (e.key === 'Escape' && logMaximized.value) { e.preventDefault(); logMaximized.value = false } }
onMounted(() => { void load(); window.addEventListener('keydown', keydown) })
onBeforeUnmount(() => { stopLogPolling(); window.removeEventListener('keydown', keydown) })
</script>

<template>
  <div class="ds-page">
    <PageHeader title="运行实例" subtitle="统一查看 SeaTunnel 离线实例与 Flink CDC 实时实例。" />
    <div class="ds-card">
      <div class="ds-toolbar">
        <el-segmented v-model="type" :options="[{label:'全部',value:'ALL'},{label:'离线',value:'OFFLINE'},{label:'实时',value:'REALTIME'}]" />
        <div class="ds-spacer" />
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <el-table :data="rows.filter(r => type === 'ALL' || r.type === type)" v-loading="loading">
        <el-table-column prop="name" label="任务" width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="120"><template #default="s"><StatusBadge :status="s.row.status" :label="statusLabel(s.row.status)" /></template></el-table-column>
        <el-table-column prop="createdBy" label="创建人" min-width="120" show-overflow-tooltip><template #default="s">{{ s.row.createdBy || 'platform' }}</template></el-table-column>
        <el-table-column label="开始时间" min-width="180"><template #default="s">{{ fmt(s.row.startedAt || s.row.createdAt) }}</template></el-table-column>
        <el-table-column label="结束时间" min-width="180"><template #default="s">{{ fmt(s.row.finishedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="s">
            <el-button link type="primary" @click="openLog(s.row)">查看日志</el-button>
            <el-button v-if="running(s.row)" link type="danger" @click="stop(s.row)">停止</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="logVisible" :title="`运行日志 · ${logRow?.name || ''}`" size="900px" :close-on-press-escape="!logMaximized" @closed="closeLog">
      <div :class="['instance-log-panel', { maximized: logMaximized }]" v-loading="logLoading">
        <div class="instance-log-toolbar">
          <span>{{ logRow?.externalId || '—' }}<em v-if="running(logRow)">运行中自动刷新</em></span>
          <div><el-button size="small" @click="refreshLog(false)">刷新</el-button><el-button v-if="!logMaximized" size="small" type="primary" plain @click="logMaximized=true">放大</el-button><el-button v-else size="small" type="primary" @click="logMaximized=false">还原（Esc）</el-button></div>
        </div>
        <div class="instance-log-viewer"><pre>{{ logText || '暂无日志' }}</pre></div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.instance-log-panel{height:calc(100vh - 125px);display:flex;flex-direction:column;border:1px solid var(--ds-border);background:#fff}.instance-log-toolbar{min-height:52px;padding:8px 12px;border-bottom:1px solid var(--ds-border);display:flex;align-items:center;justify-content:space-between;gap:16px}.instance-log-toolbar span{font-size:12px;color:var(--ds-text-secondary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.instance-log-toolbar em{font-style:normal;color:var(--ds-success);margin-left:12px}.instance-log-viewer{flex:1;min-height:0;overflow:auto;background:#111827;padding:14px 16px}.instance-log-viewer pre{margin:0;color:#d1d5db;white-space:pre-wrap;word-break:break-word;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.65}.instance-log-panel.maximized{position:fixed;z-index:4000;inset:18px;height:auto;box-shadow:0 16px 48px rgba(0,0,0,.24)}
</style>
