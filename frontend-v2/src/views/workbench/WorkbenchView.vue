<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime } from '../../utils/display'
import { workbenchApi, type WorkbenchIssue, type WorkbenchRun, type WorkbenchSummary } from '../../api/platform'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const summary = ref<WorkbenchSummary | null>(null)
const safeSummary = computed<WorkbenchSummary>(() => summary.value ?? { issues: 0, running: 0, unpublished: 0, successRate24h: undefined, successful24h: 0, failed24h: 0, unhealthySources: 0, generatedAt: '' })
const issues = ref<WorkbenchIssue[]>([])
const recentRuns = ref<WorkbenchRun[]>([])
const now = ref(new Date())

const successRateNumber = computed(() => Math.max(0, Math.min(100, summary.value?.successRate24h ?? 0)))
const successRate = computed(() => summary.value?.successRate24h == null ? '—' : `${summary.value.successRate24h.toFixed(2)}%`)
const runTotal24h = computed(() => (summary.value?.successful24h ?? 0) + (summary.value?.failed24h ?? 0))
const qualityRingStyle = computed(() => ({ '--success-angle': `${successRateNumber.value * 3.6}deg` }))
const visibleIssues = computed(() => issues.value.slice(0, 6))
const visibleRuns = computed(() => recentRuns.value.slice(0, 8))

function go(path?: string) { if (path) void router.push(path) }
function formatTime(value?: string) { return formatDateTime(value) }
const currentDateTime = computed(() => {
  const d = now.value
  const pad = (v: number) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [nextSummary, nextIssues, nextRuns] = await Promise.all([
      workbenchApi.summary(), workbenchApi.issues(), workbenchApi.recentRuns()
    ])
    summary.value = nextSummary
    issues.value = nextIssues
    recentRuns.value = nextRuns
  } catch (e) {
    error.value = e instanceof Error ? e.message : '工作台加载失败'
  } finally {
    loading.value = false
  }
}
let clockTimer: number | undefined
onMounted(() => { void load(); clockTimer = window.setInterval(() => { now.value = new Date() }, 1000) })
onUnmounted(() => { if (clockTimer) window.clearInterval(clockTimer) })
</script>

<template>
  <div class="ds-page ds-page--wide workbench">
    <PageHeader title="工作台" subtitle="聚焦今天需要关注的事项、运行状态与最近执行。">
      <template #actions>
        <div class="header-actions"><span class="datetime-chip">▣ {{ currentDateTime }}</span><el-button class="refresh-btn" @click="load" :loading="loading">↻ 刷新</el-button></div>
      </template>
    </PageHeader>

    <div v-if="error" class="ds-error">{{ error }} <span class="ds-link" @click="load">重新加载</span></div>
    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="summary">
      <section class="metric-grid">
        <article class="metric-card metric-card--danger">
          <div class="metric-card__top"><span class="metric-icon"><i class="i-alert"/></span><span class="metric-label">需要处理</span></div>
          <div class="metric-value">{{ safeSummary.issues }}</div>
          <div class="metric-hint">失败实例 {{ safeSummary.failed24h }} · 异常数据源 {{ safeSummary.unhealthySources }}</div>
        </article>
        <article class="metric-card">
          <div class="metric-card__top"><span class="metric-icon"><i class="i-play"/></span><span class="metric-label">运行中</span></div>
          <div class="metric-value">{{ safeSummary.running }}</div>
          <div class="metric-hint">工作流、离线与实时运行实例</div>
        </article>
        <article class="metric-card">
          <div class="metric-card__top"><span class="metric-icon"><i class="i-edit"/></span><span class="metric-label">未发布</span></div>
          <div class="metric-value">{{ safeSummary.unpublished }}</div>
          <div class="metric-hint">开发文件、工作流和实时任务草稿</div>
        </article>
        <article class="metric-card metric-card--success">
          <div class="metric-card__top"><span class="metric-icon"><i class="i-check"/></span><span class="metric-label">24h 成功率</span></div>
          <div class="metric-value">{{ successRate }}</div>
          <div class="metric-hint">成功 {{ safeSummary.successful24h }} · 失败 {{ safeSummary.failed24h }}</div>
        </article>
      </section>

      <section class="overview-grid">
        <article class="surface-card quality-card">
          <div class="section-head">
            <div><h2>24h 运行质量</h2><p>基于最近 24 小时真实执行结果</p></div>
            <span class="soft-tag">{{ runTotal24h }} 次执行</span>
          </div>
          <div class="quality-content">
            <div class="quality-ring" :style="qualityRingStyle">
              <div class="quality-ring__inner"><strong>{{ successRate }}</strong><span>成功率</span></div>
            </div>
            <div class="quality-legend">
              <div><span class="legend-dot success"/><div><strong>{{ safeSummary.successful24h }}</strong><small>成功</small></div></div>
              <div><span class="legend-dot danger"/><div><strong>{{ safeSummary.failed24h }}</strong><small>失败</small></div></div>
            </div>
          </div>
          <div class="quality-note">统计口径与现有工作台接口保持一致。</div>
        </article>

        <article class="surface-card attention-card">
          <div class="section-head">
            <div><h2>需要处理</h2><p>真实失败实例与异常数据源，最多展示 6 条</p></div>
            <el-button text class="text-action" @click="go('/operations/failures')">查看运维</el-button>
          </div>
          <div v-if="!visibleIssues.length" class="clean-empty">
            <span class="empty-icon"><i class="i-check"/></span>
            <div><strong>当前没有需要处理的异常</strong><small>最近 24 小时没有失败实例，数据源状态正常。</small></div>
          </div>
          <div v-else class="attention-list">
            <div v-for="item in visibleIssues" :key="item.id" class="attention-item">
              <span class="issue-type">{{ item.type }}</span>
              <div class="attention-main"><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></div>
              <StatusBadge :status="item.status"/>
              <button class="row-action" @click="go(item.path)">处理</button>
            </div>
          </div>
        </article>
      </section>

      <section class="surface-card runs-card">
        <div class="section-head section-head--runs">
          <div><h2>最近运行</h2><p>工作流、离线同步和实时同步的真实执行记录</p></div>
          <el-button text class="text-action" @click="go('/operations/instances')">查看全部实例</el-button>
        </div>
        <div v-if="!visibleRuns.length" class="clean-empty clean-empty--wide">
          <span class="empty-icon"><i class="i-play"/></span>
          <div><strong>暂无运行记录</strong><small>实际执行任务后会显示在这里。</small></div>
        </div>
        <div v-else class="runs-table-wrap">
          <table class="runs-table">
            <thead><tr><th>任务</th><th>类型</th><th>状态</th><th>完成时间</th><th></th></tr></thead>
            <tbody>
              <tr v-for="item in visibleRuns" :key="item.id" @click="go(item.path)">
                <td><div class="run-name"><span class="run-mark"/><strong>{{ item.name }}</strong></div></td>
                <td><span class="type-pill">{{ item.type }}</span></td>
                <td><StatusBadge :status="item.status" /></td>
                <td class="run-time">{{ formatTime(item.finishedAt || item.startedAt || item.sortTime) }}</td>
                <td><span class="chevron">›</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.workbench{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}
.header-actions{display:flex;align-items:center;gap:9px}.datetime-chip{height:36px;display:inline-flex;align-items:center;padding:0 13px;border:1px solid #dfe7f2;border-radius:8px;background:#fff;color:#52647d;font-size:12px;white-space:nowrap}.refresh-btn{height:36px!important;border-color:#d8e4f4!important;color:#31506f!important;background:#fff!important}
.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.metric-card{position:relative;min-height:148px;padding:20px 22px;border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.94);box-shadow:0 10px 30px rgba(42,83,163,.045);overflow:hidden}.metric-card:after{content:"";position:absolute;right:-28px;top:-28px;width:92px;height:92px;border-radius:50%;background:radial-gradient(circle,rgba(59,130,246,.09),rgba(59,130,246,0) 70%)}.metric-card__top{display:flex;align-items:center;gap:10px}.metric-icon{width:34px;height:34px;border-radius:10px;display:grid;place-items:center;background:#edf5ff;color:#347ff3}.metric-label{color:#61718a;font-size:13px;font-weight:600}.metric-value{margin-top:17px;color:#0a2142;font-size:32px;line-height:1;font-weight:760;letter-spacing:-.7px}.metric-hint{margin-top:12px;color:#8b99ad;font-size:11px;line-height:1.5}.metric-card--danger .metric-icon{background:#fff2f0;color:#d94d3f}.metric-card--danger .metric-value{color:#bd3529}.metric-card--success .metric-icon{background:#edf9f4;color:#19a66a}
.metric-icon i,.empty-icon i{position:relative;display:block;width:16px;height:16px}.i-alert:before{content:"!";position:absolute;inset:0;border:1.6px solid currentColor;border-radius:50%;font:700 11px/14px Arial;text-align:center}.i-play:before{content:"";position:absolute;left:4px;top:2px;border-left:9px solid currentColor;border-top:6px solid transparent;border-bottom:6px solid transparent}.i-edit:before{content:"";position:absolute;left:3px;top:3px;width:9px;height:9px;border:1.6px solid currentColor;border-radius:2px}.i-edit:after{content:"";position:absolute;right:0;top:1px;width:8px;height:2px;background:currentColor;transform:rotate(-45deg);transform-origin:right center}.i-check:before{content:"";position:absolute;left:3px;top:3px;width:10px;height:6px;border-left:2px solid currentColor;border-bottom:2px solid currentColor;transform:rotate(-45deg)}
.overview-grid{display:grid;grid-template-columns:.72fr 1.28fr;gap:14px;margin-top:14px}.surface-card{border:1px solid #e4ebf5;border-radius:16px;background:rgba(255,255,255,.96);box-shadow:0 10px 30px rgba(42,83,163,.04)}.quality-card,.attention-card{min-height:292px;padding:20px 22px}.section-head{display:flex;align-items:flex-start;gap:14px}.section-head>div:first-child{min-width:0}.section-head h2{margin:0;color:#102847;font-size:16px;font-weight:700;letter-spacing:-.1px}.section-head p{margin:6px 0 0;color:#8b99ad;font-size:11px}.soft-tag{margin-left:auto;padding:5px 9px;border-radius:999px;background:#f2f7fd;color:#66809f;font-size:10px}.text-action{margin-left:auto!important;padding:2px 0!important;color:#2877eb!important;font-size:12px!important}
.quality-content{display:flex;align-items:center;gap:34px;padding:24px 8px 16px}.quality-ring{--success-angle:0deg;width:126px;height:126px;flex:0 0 126px;border-radius:50%;display:grid;place-items:center;background:conic-gradient(#3b82f6 0 var(--success-angle),#edf2f8 var(--success-angle) 360deg);position:relative}.quality-ring:before{content:"";position:absolute;inset:10px;border-radius:50%;background:#fff;box-shadow:inset 0 0 0 1px #eef3f8}.quality-ring__inner{position:relative;z-index:1;text-align:center}.quality-ring__inner strong{display:block;color:#0d2445;font-size:23px;font-weight:760}.quality-ring__inner span{display:block;margin-top:5px;color:#8b99ad;font-size:10px}.quality-legend{display:grid;gap:18px;min-width:100px}.quality-legend>div{display:flex;align-items:center;gap:10px}.legend-dot{width:8px;height:8px;border-radius:50%}.legend-dot.success{background:#3b82f6}.legend-dot.danger{background:#e36a5d}.quality-legend strong{display:block;color:#18304f;font-size:19px}.quality-legend small{display:block;margin-top:2px;color:#8b99ad;font-size:10px}.quality-note{padding-top:13px;border-top:1px solid #eef2f7;color:#9aa6b8;font-size:10px;line-height:1.5}
.attention-list{margin-top:15px}.attention-item{min-height:52px;display:grid;grid-template-columns:70px minmax(0,1fr) 88px 38px;align-items:center;gap:12px;border-top:1px solid #eef2f7}.issue-type{overflow:hidden;color:#d55246;font-size:10px;font-weight:650;text-overflow:ellipsis;white-space:nowrap}.attention-main{min-width:0}.attention-main strong{display:block;overflow:hidden;color:#2b3f5c;font-size:12px;font-weight:650;text-overflow:ellipsis;white-space:nowrap}.attention-main small{display:block;margin-top:3px;overflow:hidden;color:#97a3b5;font-size:10px;text-overflow:ellipsis;white-space:nowrap}.row-action{border:0;background:none;color:#2877eb;font-size:11px;cursor:pointer;padding:4px 0}.clean-empty{min-height:210px;display:flex;align-items:center;justify-content:center;gap:12px;color:#8492a7}.empty-icon{width:34px;height:34px;border-radius:50%;display:grid;place-items:center;background:#edf7f2;color:#19a66a}.clean-empty strong{display:block;color:#52647e;font-size:12px}.clean-empty small{display:block;margin-top:5px;color:#99a5b5;font-size:10px}
.runs-card{margin-top:14px;overflow:hidden}.section-head--runs{padding:20px 22px 14px}.runs-table-wrap{overflow:auto}.runs-table{width:100%;border-collapse:collapse;table-layout:fixed}.runs-table th{height:38px;padding:0 18px;border-top:1px solid #eef2f7;border-bottom:1px solid #eef2f7;background:#fbfcfe;color:#8492a6;font-size:10px;font-weight:600;text-align:left}.runs-table th:nth-child(1){width:34%}.runs-table th:nth-child(2){width:18%}.runs-table th:nth-child(3){width:18%}.runs-table th:nth-child(4){width:25%}.runs-table th:last-child{width:5%}.runs-table td{height:52px;padding:0 18px;border-bottom:1px solid #f0f3f7;color:#52627a;font-size:11px}.runs-table tbody tr{cursor:pointer;transition:background .16s ease}.runs-table tbody tr:hover{background:#f8fbff}.runs-table tbody tr:last-child td{border-bottom:0}.run-name{display:flex;align-items:center;gap:10px;min-width:0}.run-mark{width:7px;height:7px;border-radius:50%;background:#70a9ff;box-shadow:0 0 0 4px #edf5ff}.run-name strong{overflow:hidden;color:#2a3e5b;font-size:12px;font-weight:650;text-overflow:ellipsis;white-space:nowrap}.type-pill{display:inline-flex;padding:4px 8px;border-radius:999px;background:#f1f6fc;color:#617895;font-size:10px}.run-time{color:#8c99ac!important}.chevron{color:#9aa8ba;font-size:18px}.clean-empty--wide{min-height:150px}
@media(max-width:1280px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.overview-grid{grid-template-columns:1fr}}
@media(max-width:760px){.metric-grid{grid-template-columns:1fr}.quality-content{gap:22px}.attention-item{grid-template-columns:60px minmax(0,1fr) 72px}.attention-item .row-action{display:none}.runs-table th:nth-child(2),.runs-table td:nth-child(2){display:none}.datetime-chip{display:none}}
</style>