<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { formatDateTime } from '../../utils/display'
import { workbenchApi, type WorkbenchIssue, type WorkbenchRun, type WorkbenchSummary } from '../../api/platform'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const summary = ref<WorkbenchSummary | null>(null)
const issues = ref<WorkbenchIssue[]>([])
const recentRuns = ref<WorkbenchRun[]>([])

const successRate = computed(() => summary.value?.successRate24h == null ? '—' : `${summary.value.successRate24h.toFixed(2)}%`)
const visibleIssues = computed(() => issues.value.slice(0, 6))
const visibleRuns = computed(() => recentRuns.value.slice(0, 8))

function go(path?: string) { if (path) void router.push(path) }
function formatTime(value?: string) { return formatDateTime(value) }

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
onMounted(load)
</script>

<template>
  <div class="ds-page ds-page--wide workbench">
    <PageHeader title="工作台" subtitle="查看当前发生的事情、需要处理的问题和最近运行。">
      <template #actions><el-button @click="load" :loading="loading">刷新</el-button></template>
    </PageHeader>
    <div v-if="error" class="ds-error">{{ error }} <span class="ds-link" @click="load">重新加载</span></div>
    <el-skeleton v-if="loading" :rows="8" animated />
    <template v-else-if="summary">
      <section class="ds-grid-4">
        <div class="ds-card ds-metric metric-attention">
          <div class="ds-metric__label">需要处理</div><div class="ds-metric__value">{{ summary.issues }}</div>
          <div class="ds-metric__hint">失败实例 {{ summary.failed24h }} · 异常数据源 {{ summary.unhealthySources }}</div>
        </div>
        <div class="ds-card ds-metric">
          <div class="ds-metric__label">运行中</div><div class="ds-metric__value">{{ summary.running }}</div>
          <div class="ds-metric__hint">工作流、离线与实时运行实例</div>
        </div>
        <div class="ds-card ds-metric">
          <div class="ds-metric__label">未发布</div><div class="ds-metric__value">{{ summary.unpublished }}</div>
          <div class="ds-metric__hint">开发文件、工作流和实时任务草稿</div>
        </div>
        <div class="ds-card ds-metric">
          <div class="ds-metric__label">24h 成功率</div><div class="ds-metric__value">{{ successRate }}</div>
          <div class="ds-metric__hint">成功 {{ summary.successful24h }} · 失败 {{ summary.failed24h }}</div>
        </div>
      </section>

      <section class="workbench-main ds-section-gap">
        <div class="ds-card">
          <div class="ds-card__head">
            <div><div class="ds-card__title">需要处理</div><div class="ds-card__sub">真实失败实例与异常数据源，最多展示 6 条</div></div>
            <div class="ds-spacer"/><el-button size="small" @click="go('/operations/failures')">查看运维</el-button>
          </div>
          <div v-if="!visibleIssues.length" class="ds-empty"><div><div class="ds-empty__title">当前没有需要处理的异常</div><div>最近 24 小时没有失败实例，数据源状态正常。</div></div></div>
          <div v-for="item in visibleIssues" :key="item.id" class="attention-row">
            <span class="attention-row__type danger">{{ item.type }}</span>
            <div><strong>{{ item.name }}</strong><small>{{ item.detail }}</small></div>
            <StatusBadge :status="item.status"/>
            <span class="ds-link" @click="go(item.path)">处理</span>
          </div>
        </div>

        <div class="ds-card">
          <div class="ds-card__head">
            <div><div class="ds-card__title">最近运行</div><div class="ds-card__sub">工作流、离线同步和实时同步的真实执行记录</div></div>
            <div class="ds-spacer"/><el-button size="small" @click="go('/operations/instances')">运行实例</el-button>
          </div>
          <div v-if="!visibleRuns.length" class="ds-empty"><div><div class="ds-empty__title">暂无运行记录</div><div>实际执行任务后会显示在这里。</div></div></div>
          <table v-else class="ds-table">
            <thead><tr><th style="width:34%">任务</th><th style="width:19%">类型</th><th style="width:20%">状态</th><th>时间</th></tr></thead>
            <tbody><tr v-for="item in visibleRuns" :key="item.id" class="run-row" @click="go(item.path)">
              <td class="ds-resource">{{ item.name }}</td><td>{{ item.type }}</td><td><StatusBadge :status="item.status" /></td><td>{{ formatTime(item.finishedAt || item.startedAt || item.sortTime) }}</td>
            </tr></tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.workbench { padding-top: 28px; }
.workbench-main { display: grid; grid-template-columns: .92fr 1.08fr; gap: 12px; align-items: start; }
.metric-attention .ds-metric__value { color: #b42318; }
.attention-row { min-height: 59px; display: grid; grid-template-columns: 72px minmax(0, 1fr) 100px 42px; align-items: center; gap: 12px; padding: 8px 14px; border-bottom: 1px solid var(--ds-border-soft); }
.attention-row:last-child { border-bottom: 0; }
.attention-row__type { overflow: hidden; font-size: 11px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.attention-row__type.danger { color: #d92d20; }
.attention-row strong { display: block; overflow: hidden; color: #344054; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.attention-row small { display: block; margin-top: 4px; overflow: hidden; color: var(--ds-text-tertiary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.run-row { cursor: pointer; }
.run-row:hover td { background: #fafbfc; }
@media (max-width: 1280px) { .workbench-main { grid-template-columns: 1fr; } }
</style>
