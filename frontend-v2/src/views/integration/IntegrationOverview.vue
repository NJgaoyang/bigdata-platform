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
const successAngle = computed(() => {
  const success = data.value?.success || 0
  const failed = data.value?.failed || 0
  const total = success + failed
  return total ? `${(success / total) * 360}deg` : '0deg'
})
const realtimeRunning = computed(() => realtime.value.filter(x => x.observedState === 'RUNNING').length)
const realtimeFailed = computed(() => realtime.value.filter(x => x.observedState === 'FAILED').length)
const allRunning = computed(() => (data.value?.running || 0) + realtimeRunning.value)
const allFailed = computed(() => (data.value?.failed || 0) + realtimeFailed.value)
async function load() { loading.value = true; error.value=''; try { [data.value,batch.value,realtime.value] = await Promise.all([dashboardApi.integration(),integrationApi.list(),realtimeApi.list()]) } catch(e) { error.value=e instanceof Error?e.message:'加载失败' } finally { loading.value=false } }
function go(path:string){ void router.push(path) }
onMounted(load)
</script>
<template>
  <div class="ds-page ds-integration-page integration-overview">
    <PageHeader title="数据集成" subtitle="统一查看离线同步、实时同步和运行状态。">
      <template #actions><el-button class="refresh-btn" @click="load" :loading="loading">刷新</el-button></template>
    </PageHeader>
    <div v-if="error" class="ds-error">{{ error }} <span class="ds-link" @click="load">重新加载</span></div>
    <el-skeleton v-if="loading" :rows="7" animated />
    <template v-else-if="data">
      <section class="integration-metrics">
        <button class="metric-tile" type="button" @click="go('/integration/batch')">
          <span class="metric-icon icon-batch"><i/></span><span class="metric-copy"><em>离线任务</em><strong>{{ batch.length }}</strong><small>SeaTunnel 批式同步</small></span><b>›</b>
        </button>
        <button class="metric-tile" type="button" @click="go('/integration/realtime')">
          <span class="metric-icon icon-live"><i/></span><span class="metric-copy"><em>实时任务</em><strong>{{ realtime.length }}</strong><small>Flink CDC 实时同步</small></span><b>›</b>
        </button>
        <button class="metric-tile" type="button" @click="go('/integration/instances')">
          <span class="metric-icon icon-run"><i/></span><span class="metric-copy"><em>运行中</em><strong>{{ allRunning }}</strong><small>离线实例 + 实时作业</small></span><b>›</b>
        </button>
        <button class="metric-tile metric-tile--danger" type="button" @click="go('/operations/failures')">
          <span class="metric-icon icon-alert"><i>!</i></span><span class="metric-copy"><em>异常 / 失败</em><strong>{{ allFailed }}</strong><small>需要人工关注</small></span><b>›</b>
        </button>
      </section>

      <section class="integration-grid">
        <div class="surface-card quality-card">
          <div class="section-head"><div><h2>执行质量</h2><p>基于真实同步执行结果</p></div><span class="soft-tag">当前统计</span></div>
          <div class="quality-body">
            <div class="quality-ring" :style="{ '--success-angle': successAngle }"><div><strong>{{ successRate }}</strong><span>成功率</span></div></div>
            <div class="quality-stats">
              <div><span class="dot dot-success"/><p><em>成功</em><strong>{{ data.success }}</strong></p></div>
              <div><span class="dot dot-running"/><p><em>运行中</em><strong>{{ data.running }}</strong></p></div>
              <div><span class="dot dot-pending"/><p><em>等待</em><strong>{{ data.pending }}</strong></p></div>
              <div><span class="dot dot-total"/><p><em>总执行数</em><strong>{{ data.total }}</strong></p></div>
            </div>
          </div>
        </div>

        <div class="surface-card attention-card">
          <div class="section-head"><div><h2>异常任务</h2><p>只展示需要人工处理的同步异常</p></div><el-button text class="text-action" @click="go('/operations/failures')">查看失败任务</el-button></div>
          <div v-if="allFailed===0" class="clean-empty"><span class="empty-ok">✓</span><div><strong>当前无同步异常</strong><small>失败实例会进入运维中心统一处理。</small></div></div>
          <div v-else class="alert-summary"><span class="alert-mark">!</span><div><strong>当前有 {{ allFailed }} 个异常 / 失败任务</strong><small>离线失败 {{ data.failed }} · 实时失败 {{ realtimeFailed }}</small></div><button type="button" @click="go('/operations/failures')">立即处理</button></div>
        </div>
      </section>

      <section class="surface-card entry-card">
        <div class="section-head"><div><h2>集成任务</h2><p>按使用场景快速进入对应任务空间</p></div></div>
        <div class="entry-grid">
          <button type="button" @click="go('/integration/batch')"><span class="entry-icon batch-entry">B</span><div><strong>离线同步</strong><small>MySQL → StarRocks 批式同步</small></div><em>{{ batch.length }} 个任务</em><b>›</b></button>
          <button type="button" @click="go('/integration/realtime')"><span class="entry-icon live-entry">R</span><div><strong>实时同步</strong><small>MySQL CDC → Flink CDC → StarRocks</small></div><em>{{ realtime.length }} 个任务</em><b>›</b></button>
          <button type="button" @click="go('/integration/instances')"><span class="entry-icon instance-entry">I</span><div><strong>运行实例</strong><small>统一查看离线与实时执行实例</small></div><em>{{ allRunning }} 个运行中</em><b>›</b></button>
        </div>
      </section>
    </template>
  </div>
</template>
<style scoped>
.integration-overview{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}.refresh-btn{border-color:#d8e4f4!important;color:#31506f!important;background:#fff!important}
.integration-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.metric-tile{min-height:122px;display:grid;grid-template-columns:42px minmax(0,1fr) 18px;align-items:center;gap:14px;padding:18px 20px;border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.96);box-shadow:0 10px 30px rgba(42,83,163,.045);text-align:left;cursor:pointer;transition:.18s ease}.metric-tile:hover{transform:translateY(-1px);border-color:#ceddf1;box-shadow:0 14px 34px rgba(42,83,163,.075)}.metric-icon{width:42px;height:42px;border-radius:12px;display:grid;place-items:center;background:#edf5ff;color:#347ff3}.metric-icon i{font-style:normal;font-weight:800}.icon-batch i{width:16px;height:14px;border:1.8px solid currentColor;border-radius:3px;box-shadow:inset 0 -4px 0 rgba(52,127,243,.12)}.icon-live i{width:14px;height:14px;border-radius:50%;border:1.8px solid currentColor;box-shadow:0 0 0 4px rgba(52,127,243,.10)}.icon-run i{width:0;height:0;border-left:11px solid currentColor;border-top:7px solid transparent;border-bottom:7px solid transparent;margin-left:3px}.metric-copy{min-width:0}.metric-copy em,.metric-copy strong,.metric-copy small{display:block;font-style:normal}.metric-copy em{color:#61718a;font-size:12px;font-weight:650}.metric-copy strong{margin-top:7px;color:#0a2142;font-size:28px;line-height:1;font-weight:760}.metric-copy small{margin-top:7px;color:#8b99ad;font-size:10px}.metric-tile>b{color:#9aabc0;font-size:20px}.metric-tile--danger .metric-icon{background:#fff2f0;color:#d94d3f}.metric-tile--danger .metric-copy strong{color:#bd3529}
.integration-grid{display:grid;grid-template-columns:.82fr 1.18fr;gap:14px;margin-top:14px}.surface-card{border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.96);box-shadow:0 10px 30px rgba(42,83,163,.04)}.quality-card,.attention-card{min-height:268px;padding:20px 22px}.section-head{display:flex;align-items:flex-start;gap:12px}.section-head>div:first-child{min-width:0}.section-head h2{margin:0;color:#102847;font-size:16px;font-weight:700}.section-head p{margin:6px 0 0;color:#8b99ad;font-size:11px}.soft-tag{margin-left:auto;padding:5px 9px;border-radius:999px;background:#f2f7fd;color:#66809f;font-size:10px}.text-action{margin-left:auto!important;padding:2px 0!important;color:#2877eb!important;font-size:12px!important}.quality-body{display:flex;align-items:center;gap:30px;padding:23px 8px 10px}.quality-ring{--success-angle:0deg;width:122px;height:122px;flex:0 0 122px;border-radius:50%;display:grid;place-items:center;background:conic-gradient(#3b82f6 0 var(--success-angle),#edf2f8 var(--success-angle) 360deg);position:relative}.quality-ring:before{content:"";position:absolute;inset:10px;border-radius:50%;background:#fff;box-shadow:inset 0 0 0 1px #eef3f8}.quality-ring>div{position:relative;text-align:center}.quality-ring strong{display:block;color:#0d2445;font-size:22px}.quality-ring span{display:block;margin-top:4px;color:#8b99ad;font-size:10px}.quality-stats{display:grid;grid-template-columns:repeat(2,minmax(90px,1fr));gap:16px 24px;flex:1}.quality-stats>div{display:flex;align-items:center;gap:9px}.dot{width:8px;height:8px;border-radius:50%;flex:none}.dot-success{background:#3b82f6}.dot-running{background:#4c9aff}.dot-pending{background:#a5b4c8}.dot-total{background:#6b7f99}.quality-stats p{margin:0}.quality-stats em,.quality-stats strong{display:block;font-style:normal}.quality-stats em{color:#8b99ad;font-size:10px}.quality-stats strong{margin-top:3px;color:#18304f;font-size:17px}.clean-empty{min-height:190px;display:flex;align-items:center;justify-content:center;gap:12px}.empty-ok{width:34px;height:34px;border-radius:50%;display:grid;place-items:center;background:#edf9f4;color:#19a66a;font-weight:800}.clean-empty strong,.clean-empty small{display:block}.clean-empty strong{color:#52647e;font-size:12px}.clean-empty small{margin-top:5px;color:#99a5b5;font-size:10px}.alert-summary{min-height:190px;display:grid;grid-template-columns:38px minmax(0,1fr) auto;align-items:center;gap:14px}.alert-mark{width:34px;height:34px;border-radius:50%;display:grid;place-items:center;background:#fff2f0;color:#d94d3f;font-weight:800}.alert-summary strong,.alert-summary small{display:block}.alert-summary strong{color:#3c4d63;font-size:13px}.alert-summary small{margin-top:6px;color:#96a2b3;font-size:10px}.alert-summary button{border:0;background:none;color:#2877eb;font-size:11px;cursor:pointer}
.entry-card{margin-top:14px;padding:20px 22px}.entry-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:16px}.entry-grid button{display:grid;grid-template-columns:40px minmax(0,1fr) auto 14px;align-items:center;gap:12px;min-height:76px;padding:12px 14px;border:1px solid #e8eef6;border-radius:12px;background:#fbfdff;text-align:left;cursor:pointer}.entry-grid button:hover{border-color:#d2e0f2;background:#f8fbff}.entry-icon{width:38px;height:38px;border-radius:10px;display:grid;place-items:center;font-weight:750}.batch-entry{background:#edf5ff;color:#347ff3}.live-entry{background:#eef7ff;color:#3b82f6}.instance-entry{background:#f1f5f9;color:#667f9e}.entry-grid strong,.entry-grid small{display:block}.entry-grid strong{color:#273d5a;font-size:12px}.entry-grid small{margin-top:4px;color:#91a0b4;font-size:10px}.entry-grid em{font-style:normal;color:#71859f;font-size:10px;white-space:nowrap}.entry-grid b{color:#9aabc0;font-size:18px}
@media(max-width:1280px){.integration-metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.integration-grid{grid-template-columns:1fr}.entry-grid{grid-template-columns:1fr}}@media(max-width:720px){.integration-metrics{grid-template-columns:1fr}.quality-body{gap:20px}.quality-stats{grid-template-columns:1fr 1fr}}

/* login/workbench visual alignment */
.integration-overview{position:relative;min-height:calc(100vh - 72px);background:linear-gradient(180deg,#fbfdff 0%,#f7faff 100%)}
.integration-overview:before{content:"";position:absolute;left:-120px;bottom:-150px;width:360px;height:360px;border-radius:50%;background:radial-gradient(circle,rgba(59,130,246,.07),rgba(147,197,253,.035) 46%,transparent 68%);pointer-events:none}
.integration-overview :deep(.ds-page-header){position:relative;z-index:1;margin-bottom:22px}
.integration-overview :deep(.ds-page-header__title){color:#0b2142;font-size:26px;font-weight:760;letter-spacing:-.5px}
.integration-overview :deep(.ds-page-header__subtitle){margin-top:7px;color:#7b8ca5;font-size:12px}
.integration-overview .metric-tile,.integration-overview .surface-card{border-color:#e7edf6!important;border-radius:18px!important;background:rgba(255,255,255,.92)!important;box-shadow:0 12px 32px rgba(32,76,145,.04)!important}
.integration-overview .metric-tile{min-height:126px;padding:20px 22px}
.integration-overview .metric-tile:hover{transform:translateY(-2px);border-color:#d5e2f3!important;box-shadow:0 16px 38px rgba(32,76,145,.075)!important}
.integration-overview .metric-icon{border-radius:13px;background:linear-gradient(145deg,#eef6ff,#e8f2ff);box-shadow:inset 0 0 0 1px rgba(59,130,246,.05)}
.integration-overview .metric-copy em{color:#73849c}.integration-overview .metric-copy strong{color:#0b2142;font-size:30px}.integration-overview .metric-copy small{color:#95a2b5}
.integration-overview .quality-ring{box-shadow:0 10px 24px rgba(59,130,246,.08)}
.integration-overview .entry-grid button{border-color:#e7edf6;border-radius:14px;background:linear-gradient(180deg,#fff 0%,#fbfdff 100%);transition:.18s ease}
.integration-overview .entry-grid button:hover{transform:translateY(-1px);border-color:#d5e2f3;box-shadow:0 10px 24px rgba(32,76,145,.05)}

</style>
