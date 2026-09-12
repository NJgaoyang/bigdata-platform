<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { dashboardApi, type WorkbenchOverview, type RecentTask } from '../../api/platform'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const data = ref<WorkbenchOverview | null>(null)

const attention = computed(() => (data.value?.tasks.failed || 0) + (data.value?.sources.unhealthy || 0))
const successRate = computed(() => {
  const success = data.value?.tasks.success || 0
  const failed = data.value?.tasks.failed || 0
  const total = success + failed
  return total ? `${((success / total) * 100).toFixed(2)}%` : '—'
})
const failedTasks = computed(() => (data.value?.recentTasks || []).filter(item => isFailed(item.status)).slice(0, 5))
const unhealthySources = computed(() => (data.value?.sources.items || []).filter(item => !item.healthy).slice(0, Math.max(0, 5 - failedTasks.value.length)))
const recent = computed(() => (data.value?.recentTasks || []).slice(0, 7))

function isFailed(status?: string) { return /FAIL|ERROR|失败/i.test(status || '') }
function go(path: string) { void router.push(path) }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(5, 16) : '—' }

async function load() {
  loading.value = true; error.value = ''
  try { data.value = await dashboardApi.overview() }
  catch (e) { error.value = e instanceof Error ? e.message : '工作台加载失败' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="ds-page ds-page--wide workbench">
    <PageHeader title="工作台" subtitle="查看当前运行状态和需要处理的事项。" />
    <div v-if="error" class="ds-error">{{ error }} <span class="ds-link" @click="load">重新加载</span></div>
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="data">
      <section class="ds-grid-4">
        <div class="ds-card ds-metric metric-attention"><div class="ds-metric__label">需要处理</div><div class="ds-metric__value">{{ attention }}</div><div class="ds-metric__hint">失败任务 {{ data.tasks.failed }} · 异常数据源 {{ data.sources.unhealthy }}</div></div>
        <div class="ds-card ds-metric"><div class="ds-metric__label">运行中</div><div class="ds-metric__value">{{ data.tasks.running }}</div><div class="ds-metric__hint">当前平台运行实例</div></div>
        <div class="ds-card ds-metric"><div class="ds-metric__label">待执行</div><div class="ds-metric__value">{{ data.tasks.pending }}</div><div class="ds-metric__hint">等待调度或提交的任务</div></div>
        <div class="ds-card ds-metric"><div class="ds-metric__label">执行成功率</div><div class="ds-metric__value">{{ successRate }}</div><div class="ds-metric__hint">基于当前可用执行记录</div></div>
      </section>

      <section class="workbench-main ds-section-gap">
        <div class="ds-card">
          <div class="ds-card__head"><div><div class="ds-card__title">需要处理</div><div class="ds-card__sub">仅展示需要人工介入的异常</div></div><div class="ds-spacer"/><el-button size="small" @click="go('/operations/failures')">失败任务</el-button></div>
          <div v-if="!failedTasks.length && !unhealthySources.length" class="ds-empty"><div><div class="ds-empty__title">当前没有需要处理的异常</div><div>任务和数据源运行正常。</div></div></div>
          <div v-for="item in failedTasks" :key="`task-${item.id}`" class="attention-row">
            <span class="attention-row__type danger">失败任务</span><div><strong>{{ item.name }}</strong><small>{{ item.detail || item.type }}</small></div><StatusBadge :status="item.status"/><span class="ds-link" @click="go('/operations/failures')">处理</span>
          </div>
          <div v-for="item in unhealthySources" :key="`source-${item.id}`" class="attention-row">
            <span class="attention-row__type danger">数据源</span><div><strong>{{ item.name }}</strong><small>{{ item.lastCheckMessage || `${item.host}:${item.port}` }}</small></div><StatusBadge :status="item.status"/><span class="ds-link" @click="go('/integration/datasources')">处理</span>
          </div>
        </div>

        <div class="ds-card">
          <div class="ds-card__head"><div><div class="ds-card__title">最近运行</div><div class="ds-card__sub">最近产生状态变化的任务和工作流</div></div><div class="ds-spacer"/><el-button size="small" @click="go('/operations/instances')">运行实例</el-button></div>
          <div v-if="!recent.length" class="ds-empty"><div><div class="ds-empty__title">暂无运行记录</div><div>任务开始执行后会显示在这里。</div></div></div>
          <table v-else class="ds-table">
            <thead><tr><th style="width:34%">任务</th><th style="width:20%">类型</th><th style="width:22%">状态</th><th>时间</th></tr></thead>
            <tbody><tr v-for="item in recent" :key="item.id"><td class="ds-resource">{{ item.name }}</td><td>{{ item.type }}</td><td><StatusBadge :status="item.status" /></td><td>{{ formatTime(item.finishedAt || item.startedAt) }}</td></tr></tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.workbench { padding-top: 28px; }
.workbench-main { display: grid; grid-template-columns: .9fr 1.1fr; gap: 12px; align-items: start; }
.metric-attention .ds-metric__value { color: #b42318; }
.attention-row { min-height: 59px; display: grid; grid-template-columns: 72px minmax(0, 1fr) 100px 42px; align-items: center; gap: 12px; padding: 8px 14px; border-bottom: 1px solid var(--ds-border-soft); }
.attention-row:last-child { border-bottom: 0; }
.attention-row__type { font-size: 11px; font-weight: 650; }
.attention-row__type.danger { color: #d92d20; }
.attention-row strong { display: block; overflow: hidden; color: #344054; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.attention-row small { display: block; margin-top: 4px; overflow: hidden; color: var(--ds-text-tertiary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 1280px) { .workbench-main { grid-template-columns: 1fr; } }
</style>
