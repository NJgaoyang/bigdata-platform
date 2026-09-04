<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, CircleCheckFilled, DataAnalysis, Document, Odometer, Timer } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { platformApi } from '../api'

const router = useRouter()
const health = ref<Record<string, unknown> | null>(null)
const fileCount = ref(0)
const runningCount = ref(0)
const successRate = ref('—')
const alertCount = ref(0)
const activities = ref<{ color: string; title: string; detail: string }[]>([])
const serviceLabel = computed(() => {
  if (health.value?.status !== 'UP') return '后端连接异常'
  return health.value?.mode === 'real' ? '真实服务已连接' : '开发 Mock Gateway'
})
onMounted(async () => {
  const [healthResult, projectsResult, integrationsResult, workflowsResult, instancesResult, failedResult] = await Promise.allSettled([
    platformApi.health(),
    platformApi.projects(),
    platformApi.integrations(),
    platformApi.workflows(),
    platformApi.operationInstances(),
    platformApi.failedOperations(),
  ])
  health.value = healthResult.status === 'fulfilled' ? healthResult.value.data.data : { status: 'OFFLINE' }
  const projects = projectsResult.status === 'fulfilled' ? projectsResult.value.data.data || [] : []
  const files = await Promise.allSettled(projects.map(project => platformApi.files(project.id)))
  fileCount.value = files.reduce((total, item) => total + (item.status === 'fulfilled' ? (item.value.data.data || []).length : 0), 0)
  const integrations = integrationsResult.status === 'fulfilled' ? integrationsResult.value.data.data || [] : []
  const workflows = workflowsResult.status === 'fulfilled' ? workflowsResult.value.data.data || [] : []
  const instances = instancesResult.status === 'fulfilled' ? instancesResult.value.data.data || [] : []
  const failed = failedResult.status === 'fulfilled' ? failedResult.value.data.data || [] : []
  runningCount.value = integrations.filter(item => ['RUNNING', '运行中'].includes(String(item.status))).length + instances.filter(item => String(item.status) === 'RUNNING_EXECUTION').length
  alertCount.value = failed.length
  const success = instances.filter(item => ['SUCCESS', '成功'].includes(String(item.status))).length
  successRate.value = instances.length ? `${((success / instances.length) * 100).toFixed(1)}%` : '—'
  activities.value = [
    ...integrations.slice(0, 2).map(item => ({ color: '#62d6a2', title: `${item.name || '同步任务'} · ${item.status || '待运行'}`, detail: '数据集成 · 来自平台接口' })),
    ...workflows.slice(0, 2).map(item => ({ color: '#bd8be7', title: `${item.name || '工作流'} · ${item.status || '草稿'}`, detail: '调度中心 · 来自平台接口' })),
  ]
})
</script>

<template>
  <section>
    <div class="page-heading"><div><div class="eyebrow">CONTROL CENTER / 01</div><h1>工作台</h1><p>统一管理数据开发、同步任务与生产调度。</p></div><div class="heading-actions"><button class="btn-ghost" @click="router.push('/settings')">环境设置</button><button class="btn-primary" @click="router.push('/development')">进入开发空间 <el-icon><ArrowRight /></el-icon></button></div></div>
    <div class="stat-grid">
      <div class="stat-card"><div class="stat-label">开发文件</div><div class="stat-number">{{ fileCount }}</div><div class="stat-note">来自平台数据库</div></div>
      <div class="stat-card"><div class="stat-label">运行中任务</div><div class="stat-number">{{ runningCount }}</div><div class="stat-note">来自实时实例状态</div></div>
      <div class="stat-card"><div class="stat-label">实例成功率</div><div class="stat-number">{{ successRate }}</div><div class="stat-note">按当前可查询实例计算</div></div>
      <div class="stat-card"><div class="stat-label">待处理告警</div><div class="stat-number">{{ alertCount }}</div><div class="stat-note" style="color:#e5ad67">失败实例</div></div>
    </div>
    <div class="grid-two">
      <div class="panel"><div class="panel-header"><h3>最近活动</h3><span class="muted">来自平台接口</span></div><div class="panel-body activity"><div v-for="activity in activities" :key="activity.title" class="activity-row"><div class="activity-dot" :style="{ background: activity.color, boxShadow: `0 0 9px ${activity.color}` }"></div><div><strong>{{ activity.title }}</strong><span>{{ activity.detail }}</span></div></div><div v-if="!activities.length" class="empty-state">暂无同步任务或工作流活动</div></div></div>
      <div class="panel"><div class="panel-header"><h3>快速入口</h3><el-icon class="muted"><Odometer /></el-icon></div><div class="panel-body quick-grid"><div class="quick-item" @click="router.push('/development')"><el-icon><Document /></el-icon><br>新建开发文件</div><div class="quick-item" @click="router.push('/explore')"><el-icon><DataAnalysis /></el-icon><br>探查数据表</div><div class="quick-item" @click="router.push('/workflow')"><el-icon><Timer /></el-icon><br>设计工作流</div><div class="quick-item" @click="router.push('/operations')"><el-icon><CircleCheckFilled /></el-icon><br>查看运行状态</div></div><div class="panel-body" style="padding-top:0"><div class="muted">当前连接模式</div><div style="margin-top:8px;color:#64d4a2;font-size:12px">● {{ serviceLabel }}</div></div></div>
    </div>
  </section>
</template>
