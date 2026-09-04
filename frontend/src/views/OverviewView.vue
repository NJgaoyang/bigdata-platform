<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, CircleCheckFilled, DataAnalysis, Document, Odometer, Timer } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { platformApi } from '../api'

const router = useRouter()
const health = ref<Record<string, unknown> | null>(null)
onMounted(async () => { try { health.value = (await platformApi.health()).data.data } catch { health.value = { status: 'OFFLINE' } } })
</script>

<template>
  <section>
    <div class="page-heading"><div><div class="eyebrow">CONTROL CENTER / 01</div><h1>工作台</h1><p>统一管理数据开发、同步任务与生产调度。</p></div><div class="heading-actions"><button class="btn-ghost" @click="router.push('/settings')">环境设置</button><button class="btn-primary" @click="router.push('/development')">进入开发空间 <el-icon><ArrowRight /></el-icon></button></div></div>
    <div class="stat-grid">
      <div class="stat-card"><div class="stat-label">活跃开发文件</div><div class="stat-number">24</div><div class="stat-note">↑ 12% 本周</div></div>
      <div class="stat-card"><div class="stat-label">运行中任务</div><div class="stat-number">08</div><div class="stat-note">全部运行正常</div></div>
      <div class="stat-card"><div class="stat-label">今日成功率</div><div class="stat-number">99.2<span style="font-size:16px">%</span></div><div class="stat-note">↑ 0.8% 较昨日</div></div>
      <div class="stat-card"><div class="stat-label">待处理告警</div><div class="stat-number">03</div><div class="stat-note" style="color:#e5ad67">需要关注</div></div>
    </div>
    <div class="grid-two">
      <div class="panel"><div class="panel-header"><h3>最近活动</h3><span class="muted">实时更新</span></div><div class="panel-body activity"><div class="activity-row"><div class="activity-dot"></div><div><strong>daily_sales.sql 已保存新版本</strong><span>数据开发 · 2 分钟前</span></div></div><div class="activity-row"><div class="activity-dot" style="background:#62d6a2;box-shadow:0 0 9px #62d6a2"></div><div><strong>ods_to_dw_customer 同步成功</strong><span>数据集成 · 18 分钟前</span></div></div><div class="activity-row"><div class="activity-dot" style="background:#bd8be7;box-shadow:0 0 9px #bd8be7"></div><div><strong>sales_daily 工作流已上线</strong><span>调度中心 · 42 分钟前</span></div></div><div class="activity-row"><div class="activity-dot" style="background:#e6ad65;box-shadow:0 0 9px #e6ad65"></div><div><strong>customer_dim 运行耗时较长</strong><span>运维中心 · 1 小时前</span></div></div></div></div>
      <div class="panel"><div class="panel-header"><h3>快速入口</h3><el-icon class="muted"><Odometer /></el-icon></div><div class="panel-body quick-grid"><div class="quick-item" @click="router.push('/development')"><el-icon><Document /></el-icon><br>新建开发文件</div><div class="quick-item" @click="router.push('/explore')"><el-icon><DataAnalysis /></el-icon><br>探查数据表</div><div class="quick-item" @click="router.push('/workflow')"><el-icon><Timer /></el-icon><br>设计工作流</div><div class="quick-item" @click="router.push('/operations')"><el-icon><CircleCheckFilled /></el-icon><br>查看运行状态</div></div><div class="panel-body" style="padding-top:0"><div class="muted">当前连接模式</div><div style="margin-top:8px;color:#64d4a2;font-size:12px">● {{ health?.status === 'UP' ? '开发环境 · Mock Gateway 已就绪' : '等待后端连接' }}</div></div></div>
    </div>
  </section>
</template>
