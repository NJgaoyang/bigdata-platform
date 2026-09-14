<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import { dashboardApi, type IntegrationSummary } from '../../api/platform'
import { integrationApi, realtimeApi, type IntegrationTask, type RealtimeJob } from '../../api/domain'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const data = ref<IntegrationSummary | null>(null)
const batch=ref<IntegrationTask[]>([])
const realtime=ref<RealtimeJob[]>([])
const successRate = computed(() => {
  const success = data.value?.success || 0; const failed = data.value?.failed || 0; const total = success + failed
  return total ? `${((success / total) * 100).toFixed(1)}%` : '—'
})
async function load() { loading.value = true; error.value=''; try { [data.value,batch.value,realtime.value] = await Promise.all([dashboardApi.integration(),integrationApi.list(),realtimeApi.list()]) } catch(e) { error.value=e instanceof Error?e.message:'加载失败' } finally { loading.value=false } }
function go(path:string){ void router.push(path) }
onMounted(load)
</script>
<template>
  <div class="ds-page ds-integration-page">
    <PageHeader title="集成概览" subtitle="查看同步任务运行状态和异常情况。" />
    <div v-if="error" class="ds-error">{{ error }} <span class="ds-link" @click="load">重新加载</span></div>
    <el-skeleton v-if="loading" :rows="7" animated />
    <template v-else-if="data">
      <div class="ds-grid-4">
        <div class="ds-card ds-metric"><div class="ds-metric__label">离线任务</div><div class="ds-metric__value">{{ batch.length }}</div><div class="ds-metric__hint">SeaTunnel</div></div>
        <div class="ds-card ds-metric"><div class="ds-metric__label">实时任务</div><div class="ds-metric__value">{{ realtime.length }}</div><div class="ds-metric__hint">Flink CDC</div></div>
        <div class="ds-card ds-metric"><div class="ds-metric__label">运行中</div><div class="ds-metric__value">{{ data.running + realtime.filter(x=>x.observedState==='RUNNING').length }}</div><div class="ds-metric__hint">离线实例 + 实时作业</div></div>
        <div class="ds-card ds-metric"><div class="ds-metric__label">异常 / 失败</div><div class="ds-metric__value danger">{{ data.failed + realtime.filter(x=>x.observedState==='FAILED').length }}</div><div class="ds-metric__hint">需要处理</div></div>
      </div>
      <div class="ds-grid-2 ds-section-gap">
        <div class="ds-card"><div class="ds-card__head"><div><div class="ds-card__title">异常任务</div><div class="ds-card__sub">只展示需要人工处理的同步异常</div></div><div class="ds-spacer"/><el-button size="small" @click="go('/operations/failures')">查看失败任务</el-button></div><div v-if="data.failed===0" class="ds-empty"><div><div class="ds-empty__title">当前无同步异常</div><div>失败实例会进入运维中心统一处理。</div></div></div><div v-else class="summary-line"><span><i class="ds-dot ds-dot--danger"/>失败实例</span><strong>{{ data.failed }}</strong><span class="ds-link" @click="go('/operations/failures')">处理</span></div></div>
        <div class="ds-card"><div class="ds-card__head"><div><div class="ds-card__title">执行统计</div><div class="ds-card__sub">来自真实同步实例</div></div><div class="ds-spacer"/><el-button size="small" @click="go('/integration/instances')">运行实例</el-button></div><div class="stat-list"><div><span>成功</span><strong>{{ data.success }}</strong></div><div><span>运行中</span><strong>{{ data.running }}</strong></div><div><span>等待</span><strong>{{ data.pending }}</strong></div><div><span>总执行数</span><strong>{{ data.total }}</strong></div></div></div>
      </div>
    </template>
  </div>
</template>
<style scoped>
.danger { color:#b42318; }
.summary-line { height:58px; display:grid; grid-template-columns:1fr 80px 60px; align-items:center; padding:0 16px; border-top:1px solid var(--ds-border-soft); }
.stat-list { display:grid; grid-template-columns:repeat(4,1fr); min-height:136px; align-items:center; }
.stat-list>div { padding:16px; border-right:1px solid var(--ds-border-soft); }
.stat-list>div:last-child { border-right:0; }
.stat-list span { display:block; color:var(--ds-text-tertiary); font-size:11px; }
.stat-list strong { display:block; margin-top:8px; font-size:20px; }
</style>
