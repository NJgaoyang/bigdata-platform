<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import { assetApi, type AssetItem } from '../../api/domain'
import { assetDetailPath, assetTypeClass, assetTypeLabel } from './asset-utils'

const router = useRouter()
const loading = ref(false)
const assets = ref<AssetItem[]>([])
const favorites = ref<AssetItem[]>([])
const keyword = ref('')

const tables = computed(() => assets.value.filter(item => item.type === 'TABLE'))
const datasets = computed(() => assets.value.filter(item => item.type === 'DATASET'))
const metrics = computed(() => assets.value.filter(item => item.type === 'METRIC'))
const described = computed(() => assets.value.filter(item => item.description?.trim()).length)
const descriptionCoverage = computed(() => assets.value.length ? Math.round(described.value / assets.value.length * 100) : 0)
const sourceCount = computed(() => new Set(tables.value.map(item => item.source).filter(Boolean)).size)
const commonAssets = computed(() => {
  const favRefs = new Set(favorites.value.map(item => item.ref))
  return [...favorites.value, ...assets.value.filter(item => !favRefs.has(item.ref))].slice(0, 6)
})
const sourceDistribution = computed(() => {
  const map = new Map<string, number>()
  tables.value.forEach(item => map.set(item.source || '未标记来源', (map.get(item.source || '未标记来源') || 0) + 1))
  return [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6)
})
const missingDescription = computed(() => assets.value.filter(item => !item.description?.trim()).length)

async function load() {
  loading.value = true
  try {
    const [catalog, fav] = await Promise.all([assetApi.catalog(), assetApi.favorites()])
    assets.value = catalog
    favorites.value = fav
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '资产加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  router.push({ path: '/assets/catalog', query: keyword.value.trim() ? { q: keyword.value.trim() } : {} })
}

function open(asset: AssetItem) {
  router.push(assetDetailPath(asset))
}

onMounted(load)
</script>

<template>
  <div class="ds-page asset-home" v-loading="loading">
    <PageHeader title="数据资产" subtitle="统一发现、理解和使用企业数据资产。资产目录直接连接当前平台真实元数据、数据集和认证指标。" />

    <section class="search-hero ds-card">
      <div class="hero-copy">
        <strong>搜索企业数据资产</strong>
        <span>支持数据表、数据集、认证指标、来源和描述</span>
      </div>
      <div class="hero-search">
        <el-input v-model="keyword" size="large" clearable placeholder="输入资产名称、描述、数据库或数据源" @keyup.enter="search" />
        <el-button type="primary" size="large" @click="search">搜索</el-button>
      </div>
      <div class="quick-search">
        <span>快速入口</span>
        <button @click="router.push({path:'/assets/catalog',query:{type:'TABLE'}})">数据表</button>
        <button @click="router.push({path:'/assets/catalog',query:{type:'DATASET'}})">数据集</button>
        <button @click="router.push({path:'/assets/catalog',query:{type:'METRIC'}})">认证指标</button>
        <button @click="router.push('/assets/favorites')">我的收藏</button>
      </div>
    </section>

    <section class="metric-grid">
      <div class="metric-card ds-card"><span>全部资产</span><strong>{{ assets.length.toLocaleString() }}</strong><small>统一资产目录</small></div>
      <div class="metric-card ds-card"><span>数据表</span><strong>{{ tables.length.toLocaleString() }}</strong><small>{{ sourceCount }} 个可见数据源</small></div>
      <div class="metric-card ds-card"><span>数据集</span><strong>{{ datasets.length.toLocaleString() }}</strong><small>状态正常的数据集</small></div>
      <div class="metric-card ds-card"><span>认证指标</span><strong>{{ metrics.length.toLocaleString() }}</strong><small>已认证指标</small></div>
      <div class="metric-card ds-card"><span>描述覆盖率</span><strong>{{ descriptionCoverage }}%</strong><small>{{ missingDescription }} 项待完善</small></div>
    </section>

    <section class="home-grid">
      <div class="ds-card section-card">
        <div class="section-head"><div><strong>常用资产</strong><span>收藏优先，随后展示当前可访问资产</span></div><el-button link type="primary" @click="router.push('/assets/catalog')">查看全部</el-button></div>
        <div v-if="commonAssets.length" class="asset-list">
          <button v-for="item in commonAssets" :key="item.ref" class="asset-row" @click="open(item)">
            <span :class="['asset-icon',assetTypeClass(item.type)]">{{ assetTypeLabel(item.type).slice(0,1) }}</span>
            <span class="asset-main"><strong>{{ item.name }}</strong><small>{{ item.description || item.detail || '暂无描述' }}</small></span>
            <span class="asset-source">{{ item.source || '—' }}</span>
            <span class="asset-type">{{ assetTypeLabel(item.type) }}</span>
          </button>
        </div>
        <div v-else class="empty-block">暂无可访问资产</div>
      </div>

      <div class="ds-card section-card">
        <div class="section-head"><div><strong>数据来源</strong><span>按当前可见 StarRocks 数据源统计</span></div><el-button link type="primary" @click="router.push('/assets/catalog')">进入目录</el-button></div>
        <div v-if="sourceDistribution.length" class="source-list">
          <button v-for="([name,count],index) in sourceDistribution" :key="name" class="source-row" @click="router.push({path:'/assets/catalog',query:{source:name}})">
            <span class="source-rank">{{ index + 1 }}</span>
            <span class="source-name"><strong>{{ name }}</strong><small>{{ count }} 张数据表</small></span>
            <span class="source-bar"><i :style="{width:`${Math.max(8,Math.round(count/(sourceDistribution[0]?.[1]||1)*100))}%`}" /></span>
          </button>
        </div>
        <div v-else class="empty-block">暂无数据来源</div>
      </div>

      <div class="ds-card section-card governance-card">
        <div class="section-head"><div><strong>资产基础治理</strong><span>基于当前目录可可靠计算的元数据完整度</span></div><el-button link type="primary" @click="router.push('/assets/governance')">查看治理项</el-button></div>
        <div class="governance-summary">
          <div><span>已有描述</span><strong>{{ described }}</strong><small>覆盖率 {{ descriptionCoverage }}%</small></div>
          <div><span>缺少描述</span><strong class="warning">{{ missingDescription }}</strong><small>建议补齐业务含义</small></div>
          <div><span>我的收藏</span><strong>{{ favorites.length }}</strong><small>个人常用资产</small></div>
        </div>
        <div class="governance-note">数据质量规则、查询热度和标签治理需要对应后端采集能力；本页不会用 Mock 数字冒充生产指标。</div>
      </div>

      <div class="ds-card section-card map-entry">
        <div class="map-copy"><span class="map-icon">⌘</span><div><strong>数据地图</strong><p>从真实表级血缘查看上下游依赖关系，定位数据从哪里来、流向哪里。</p></div></div>
        <el-button type="primary" plain @click="router.push('/assets/map')">打开数据地图</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.asset-home{display:flex;flex-direction:column;gap:14px}.search-hero{padding:22px 24px;background:linear-gradient(135deg,#fff 0%,#f7fbff 100%)}.hero-copy{display:flex;align-items:baseline;gap:12px}.hero-copy strong{font-size:18px;color:#252b3a}.hero-copy span{font-size:12px;color:#8a8e99}.hero-search{display:flex;gap:10px;max-width:780px;margin-top:16px}.hero-search .el-input{flex:1}.quick-search{display:flex;align-items:center;gap:8px;margin-top:12px;font-size:12px;color:#98a2b3}.quick-search button{border:0;background:#f3f6fa;color:#52606d;padding:5px 9px;border-radius:4px;cursor:pointer}.quick-search button:hover{background:#eaf3ff;color:#1677ff}.metric-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.metric-card{padding:15px 16px}.metric-card span{display:block;font-size:12px;color:#667085}.metric-card strong{display:block;margin-top:7px;font-size:24px;line-height:1.1;color:#252b3a}.metric-card small{display:block;margin-top:6px;color:#98a2b3;font-size:11px}.home-grid{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(360px,.65fr);gap:14px}.section-card{overflow:hidden}.section-head{min-height:48px;padding:10px 14px;border-bottom:1px solid #edf0f3;display:flex;align-items:center;justify-content:space-between}.section-head>div{display:flex;align-items:baseline;gap:9px}.section-head strong{font-size:14px}.section-head span{font-size:11px;color:#98a2b3}.asset-list{display:flex;flex-direction:column}.asset-row{border:0;border-bottom:1px solid #f1f3f5;background:#fff;display:grid;grid-template-columns:34px minmax(0,1fr) 130px 78px;align-items:center;gap:10px;padding:10px 14px;text-align:left;cursor:pointer}.asset-row:last-child{border-bottom:0}.asset-row:hover{background:#f8fbff}.asset-icon{width:30px;height:30px;border-radius:5px;display:grid;place-items:center;font-size:12px;font-weight:700}.asset-icon.table{background:#eaf3ff;color:#1677ff}.asset-icon.dataset{background:#eefbf3;color:#16a34a}.asset-icon.metric{background:#fff6e8;color:#d97706}.asset-main{min-width:0}.asset-main strong,.asset-main small{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.asset-main strong{font-size:12px;color:#252b3a}.asset-main small{margin-top:3px;font-size:10px;color:#98a2b3}.asset-source,.asset-type{font-size:11px;color:#667085;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.asset-type{text-align:right}.source-list{padding:4px 0}.source-row{width:100%;border:0;background:#fff;padding:10px 14px;display:grid;grid-template-columns:24px 130px minmax(0,1fr);gap:10px;align-items:center;text-align:left;cursor:pointer}.source-row:hover{background:#fafcff}.source-rank{width:20px;height:20px;border-radius:4px;background:#f2f4f7;display:grid;place-items:center;color:#667085;font-size:10px}.source-name strong,.source-name small{display:block}.source-name strong{font-size:12px}.source-name small{margin-top:2px;font-size:10px;color:#98a2b3}.source-bar{height:6px;background:#f0f2f5;border-radius:99px;overflow:hidden}.source-bar i{display:block;height:100%;background:#93bfff;border-radius:99px}.governance-card{grid-column:1/2}.governance-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:16px}.governance-summary>div{padding:12px;background:#f8fafc;border:1px solid #eef1f4;border-radius:5px}.governance-summary span,.governance-summary small{display:block;font-size:10px;color:#98a2b3}.governance-summary strong{display:block;margin:5px 0;font-size:20px}.governance-summary .warning{color:#d97706}.governance-note{margin:0 16px 16px;padding:10px 12px;background:#fff9ed;border:1px solid #fde6bb;color:#8a6116;font-size:11px;line-height:1.6}.map-entry{grid-column:2/3;padding:18px;display:flex;align-items:center;justify-content:space-between;gap:16px}.map-copy{display:flex;gap:12px;align-items:flex-start}.map-icon{width:36px;height:36px;border-radius:6px;background:#eaf3ff;color:#1677ff;display:grid;place-items:center;font-size:18px}.map-copy strong{font-size:14px}.map-copy p{margin:5px 0 0;color:#8a8e99;font-size:11px;line-height:1.6}.empty-block{padding:38px;text-align:center;color:#98a2b3;font-size:12px}@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(3,1fr)}.home-grid{grid-template-columns:1fr}.governance-card,.map-entry{grid-column:auto}}
</style>
