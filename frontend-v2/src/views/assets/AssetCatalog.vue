<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusBadge from '../../components/StatusBadge.vue'
import { assetApi, type AssetItem } from '../../api/domain'

const route=useRoute(),router=useRouter()
const loading=ref(true),rows=ref<AssetItem[]>([])
const keyword=ref(''),typeFilter=ref(''),sourceFilter=ref(''),statusFilter=ref('')
const mode=computed(()=>route.path.endsWith('/favorites')?'favorites':'catalog')
const certifiedStatus=(v?:string)=>/CERTIFIED|PUBLISHED|ONLINE|ACTIVE|ENABLED/i.test(v||'')
const pendingStatus=(v?:string)=>/DRAFT|PENDING|UNPUBLISHED|OFFLINE|DISABLED|REVIEW/i.test(v||'')
const assetTypeLabel=(v:string)=>({TABLE:'数据表',DATASET:'数据集',METRIC:'认证指标'} as Record<string,string>)[v]||v
const sources=computed(()=>Array.from(new Set(rows.value.map(r=>r.source).filter(Boolean) as string[])).sort())
const totals=computed(()=>({
  total:rows.value.length,
  certified:rows.value.filter(r=>certifiedStatus(r.status)).length,
  pending:rows.value.filter(r=>pendingStatus(r.status)).length,
  favorite:rows.value.filter(r=>r.favorite).length
}))
const typeCounts=computed(()=>({
  TABLE:rows.value.filter(r=>r.type==='TABLE').length,
  DATASET:rows.value.filter(r=>r.type==='DATASET').length,
  METRIC:rows.value.filter(r=>r.type==='METRIC').length
}))
const filtered=computed(()=>rows.value.filter(r=>{
  if(typeFilter.value&&r.type!==typeFilter.value)return false
  if(sourceFilter.value&&r.source!==sourceFilter.value)return false
  if(statusFilter.value&&String(r.status).toUpperCase()!==statusFilter.value)return false
  const q=keyword.value.trim().toLowerCase()
  return !q||`${r.name} ${r.description||''} ${r.source||''} ${r.owner||''}`.toLowerCase().includes(q)
}))
const statusOptions=computed(()=>Array.from(new Set(rows.value.map(r=>String(r.status||'')).filter(Boolean))).sort())

function selectDirectory(key:'all'|'TABLE'|'DATASET'|'METRIC'|'favorites'){
  if(key==='favorites'){void router.push('/assets/favorites');return}
  if(mode.value==='favorites')void router.push('/assets/catalog')
  typeFilter.value=key==='all'?'':key
}
function resetFilters(){keyword.value='';typeFilter.value='';sourceFilter.value='';statusFilter.value=''}
async function showGuide(){await ElMessageBox.alert('资产目录统一展示当前平台可发现的数据表、数据集和已认证指标。可使用左侧目录与顶部筛选快速定位资产，并通过收藏建立个人常用资产清单。','数据资产使用指南',{confirmButtonText:'知道了'})}
async function load(){loading.value=true;try{rows.value=mode.value==='favorites'?await assetApi.favorites():await assetApi.catalog()}catch(e){ElMessage.error(e instanceof Error?e.message:'资产加载失败')}finally{loading.value=false}}
async function toggle(r:AssetItem){try{r.favorite?await assetApi.unfavorite(r.ref):await assetApi.favorite(r.type,r.ref);ElMessage.success(r.favorite?'已取消收藏':'已收藏');await load()}catch(e){ElMessage.error(e instanceof Error?e.message:'操作失败')}}
watch(()=>route.path,()=>{resetFilters();void load()});onMounted(load)
</script>

<template>
  <div class="asset-page">
    <header class="asset-header">
      <div><div class="asset-title-row"><h1>{{mode==='favorites'?'我的收藏':'数据资产'}}</h1><p>{{mode==='favorites'?'快速访问自己收藏的数据资产。':'统一管理数据表、数据集和认证指标，提升资产可发现性与复用效率。'}}</p></div></div>
      <el-button class="guide-btn" @click="showGuide">使用指南</el-button>
    </header>

    <section class="filter-card">
      <el-input v-model="keyword" clearable placeholder="搜索资产名称 / 负责人 / 来源" class="search-input"/>
      <div class="filter-item"><span>资产类型</span><el-select v-model="typeFilter" clearable placeholder="全部"><el-option label="数据表" value="TABLE"/><el-option label="数据集" value="DATASET"/><el-option label="认证指标" value="METRIC"/></el-select></div>
      <div class="filter-item"><span>来源</span><el-select v-model="sourceFilter" clearable placeholder="全部"><el-option v-for="s in sources" :key="s" :label="s" :value="s"/></el-select></div>
      <div class="filter-item"><span>状态</span><el-select v-model="statusFilter" clearable placeholder="全部"><el-option v-for="s in statusOptions" :key="s" :label="s" :value="s"/></el-select></div>
      <div class="filter-actions"><el-button @click="resetFilters">重置</el-button><el-button type="primary" @click="load" :loading="loading">刷新</el-button></div>
    </section>

    <section class="asset-metrics" v-if="mode==='catalog'">
      <article class="metric-card"><span class="metric-icon blue">◉</span><div><label>资产总数</label><strong>{{totals.total}}</strong><small>平台当前可发现资产</small></div></article>
      <article class="metric-card"><span class="metric-icon green">▦</span><div><label>数据表</label><strong>{{typeCounts.TABLE}}</strong><small>当前可发现数据表</small></div></article>
      <article class="metric-card"><span class="metric-icon amber">▤</span><div><label>数据集</label><strong>{{typeCounts.DATASET}}</strong><small>当前有效数据集</small></div></article>
      <article class="metric-card"><span class="metric-icon violet">⌁</span><div><label>认证指标</label><strong>{{typeCounts.METRIC}}</strong><small>已认证业务指标</small></div></article>
    </section>

    <section class="asset-shell">
      <aside class="asset-directory">
        <div class="directory-title">资产目录</div>
        <div class="directory-search"><el-input v-model="keyword" clearable placeholder="搜索目录 / 资产"/></div>
        <nav class="directory-list">
          <button :class="{active:mode==='catalog'&&!typeFilter}" @click="selectDirectory('all')"><span class="dir-icon database">◉</span><strong>全部资产</strong><em>{{totals.total}}</em></button>
          <button :class="{active:typeFilter==='TABLE'}" @click="selectDirectory('TABLE')"><span class="dir-icon">▦</span><strong>数据表</strong><em>{{typeCounts.TABLE}}</em></button>
          <button :class="{active:typeFilter==='DATASET'}" @click="selectDirectory('DATASET')"><span class="dir-icon">▤</span><strong>数据集</strong><em>{{typeCounts.DATASET}}</em></button>
          <button :class="{active:typeFilter==='METRIC'}" @click="selectDirectory('METRIC')"><span class="dir-icon">⌁</span><strong>认证指标</strong><em>{{typeCounts.METRIC}}</em></button>
          <div class="directory-sep"></div>
          <button :class="{active:mode==='favorites'}" @click="selectDirectory('favorites')"><span class="dir-icon">☆</span><strong>我的收藏</strong><em>{{totals.favorite}}</em></button>
        </nav>
      </aside>

      <main class="asset-content">
        <div class="asset-tabs">
          <div class="tab-list">
            <button :class="{active:mode==='catalog'}" @click="router.push('/assets/catalog')">资产列表</button>
            <button :class="{active:mode==='favorites'}" @click="router.push('/assets/favorites')">我的收藏</button>
          </div>
          <div class="asset-summary">共 <strong>{{filtered.length}}</strong> 项 <span>·</span> 收藏 {{totals.favorite}}</div>
        </div>

        <el-skeleton v-if="loading" :rows="8" animated class="asset-skeleton"/>
        <div v-else-if="!filtered.length" class="asset-empty"><div class="empty-mark">⌕</div><strong>{{mode==='favorites'?'暂无收藏资产':'暂无匹配资产'}}</strong><span>{{mode==='favorites'?'可在资产目录收藏常用资源。':'调整筛选条件后再试。'}}</span></div>
        <div v-else class="asset-table-wrap">
          <table class="asset-table">
            <thead><tr><th>资产名称</th><th>类型</th><th>来源</th><th>负责人</th><th>状态</th><th>详情</th><th class="op-col">操作</th></tr></thead>
            <tbody><tr v-for="r in filtered" :key="r.ref">
              <td><div class="asset-name-cell"><span :class="['asset-kind-icon',String(r.type).toLowerCase()]">{{r.type==='TABLE'?'▦':r.type==='DATASET'?'▤':'⌁'}}</span><div><strong>{{r.name}}</strong><small>{{r.description||r.ref}}</small></div></div></td>
              <td><span :class="['type-tag',String(r.type).toLowerCase()]">{{assetTypeLabel(r.type)}}</span></td>
              <td>{{r.source||'—'}}</td><td>{{r.owner||'—'}}</td>
              <td><StatusBadge :status="r.status"/></td><td class="detail-cell" :title="r.detail">{{r.detail||'—'}}</td>
              <td><button class="favorite-btn" :class="{active:r.favorite}" @click="toggle(r)">{{r.favorite?'★ 已收藏':'☆ 收藏'}}</button></td>
            </tr></tbody>
          </table>
        </div>
      </main>
    </section>
  </div>
</template>

<style scoped>
.asset-page{min-height:calc(100vh - var(--ds-topbar));padding:22px 26px 28px;background:linear-gradient(180deg,#fbfdff 0%,#f7faff 100%);color:#102847}
.asset-header{height:58px;display:flex;align-items:flex-start;gap:18px}.asset-title-row{display:flex;align-items:baseline;gap:18px}.asset-title-row h1{margin:0;font-size:26px;line-height:1.2;letter-spacing:-.5px}.asset-title-row p{margin:0;color:#7d8da4;font-size:12px}.guide-btn{margin-left:auto;height:36px;border-radius:8px;border-color:#dce6f3;color:#536982;background:#fff}
.filter-card{min-height:64px;padding:11px 13px;display:grid;grid-template-columns:minmax(260px,1.35fr) repeat(3,minmax(180px,.78fr)) auto;align-items:center;gap:12px;border:1px solid #e3eaf4;border-radius:13px;background:#fff;box-shadow:0 8px 26px rgba(42,83,163,.035)}.search-input{min-width:0}.filter-item{display:grid;grid-template-columns:auto minmax(0,1fr);align-items:center;gap:8px;color:#52657d;font-size:12px;font-weight:600}.filter-item :deep(.el-select){width:100%}.filter-card :deep(.el-input__wrapper),.filter-card :deep(.el-select__wrapper){min-height:38px;border-radius:8px;box-shadow:0 0 0 1px #dfe7f2 inset}.filter-actions{display:flex;gap:8px}.filter-actions :deep(.el-button){height:38px;border-radius:8px}
.asset-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-top:12px}.metric-card{min-height:96px;padding:15px 18px;display:flex;align-items:center;gap:15px;border:1px solid #e4ebf5;border-radius:14px;background:#fff;box-shadow:0 8px 28px rgba(42,83,163,.035)}.metric-icon{width:42px;height:42px;display:grid;place-items:center;border-radius:11px;font-size:19px;font-weight:700}.metric-icon.blue{background:#edf4ff;color:#2f6fed}.metric-icon.green{background:#edf9f4;color:#18a66b}.metric-icon.amber{background:#fff6e9;color:#ec9418}.metric-icon.violet{background:#f3f0ff;color:#7659da}.metric-card label,.metric-card small{display:block}.metric-card label{color:#718198;font-size:11px}.metric-card strong{display:block;margin-top:3px;color:#102847;font-size:25px;line-height:1;font-weight:760}.metric-card small{margin-top:7px;color:#97a4b5;font-size:9px}
.asset-shell{min-height:520px;margin-top:12px;display:grid;grid-template-columns:250px minmax(0,1fr);border:1px solid #e3eaf4;border-radius:14px;background:#fff;box-shadow:0 8px 28px rgba(42,83,163,.035);overflow:hidden}.asset-directory{border-right:1px solid #e7edf5;background:#fbfdff}.directory-title{height:48px;padding:0 14px;display:flex;align-items:center;border-bottom:1px solid #e8eef6;background:#fff;color:#17304f;font-size:14px;font-weight:700}.directory-search{padding:10px;border-bottom:1px solid #edf1f6}.directory-search :deep(.el-input__wrapper){border-radius:8px;box-shadow:0 0 0 1px #dfe7f2 inset}.directory-list{padding:7px}.directory-list button{width:100%;min-height:39px;padding:0 9px;display:grid;grid-template-columns:22px minmax(0,1fr) auto;align-items:center;gap:8px;border:0;border-radius:8px;background:transparent;color:#52657d;text-align:left;cursor:pointer}.directory-list button:hover{background:#f2f6fb}.directory-list button.active{background:linear-gradient(90deg,#eaf2ff 0%,#f0f6ff 100%);color:#2468d8;box-shadow:inset 3px 0 0 #3b82f6}.directory-list strong{font-size:12px;font-weight:600}.directory-list em{font-style:normal;color:#8b99ad;font-size:10px}.directory-list button.active em{color:#5d84bd}.dir-icon{width:22px;height:22px;display:grid;place-items:center;border-radius:6px;background:#f2f5f9;color:#6b7e96;font-size:12px}.directory-list button.active .dir-icon{background:#dceaff;color:#2f6fed}.directory-sep{height:1px;margin:7px 6px;background:#e9eef5}
.asset-content{min-width:0}.asset-tabs{height:58px;padding:0 15px;display:flex;align-items:center;border-bottom:1px solid #e8eef6}.tab-list{height:100%;display:flex;gap:28px}.tab-list button{position:relative;border:0;background:transparent;color:#52657d;font-size:13px;cursor:pointer}.tab-list button.active{color:#2468d8;font-weight:700}.tab-list button.active:after{content:"";position:absolute;left:0;right:0;bottom:0;height:2px;background:#3b82f6}.asset-summary{margin-left:auto;color:#8a98ac;font-size:11px}.asset-summary strong{color:#344b66}.asset-summary span{margin:0 8px;color:#c3ccd7}
.asset-skeleton{padding:18px}.asset-table-wrap{overflow:auto;padding:12px 14px 16px}.asset-table{width:100%;border-collapse:separate;border-spacing:0;table-layout:fixed}.asset-table th{height:42px;padding:0 12px;background:#f8fbff;border-bottom:1px solid #e8eef6;color:#65778f;font-size:11px;font-weight:650;text-align:left}.asset-table th:first-child{width:30%}.asset-table th:nth-child(2){width:10%}.asset-table th:nth-child(3){width:14%}.asset-table th:nth-child(4){width:12%}.asset-table th:nth-child(5){width:12%}.asset-table th:nth-child(6){width:14%}.asset-table th:last-child{width:8%}.asset-table td{height:58px;padding:0 12px;border-bottom:1px solid #eef2f7;color:#4d6079;font-size:11px}.asset-table tbody tr:hover td{background:#f8fbff}.asset-name-cell{display:flex;align-items:center;gap:10px;min-width:0}.asset-kind-icon{width:30px;height:30px;flex:0 0 30px;display:grid;place-items:center;border-radius:8px;background:#edf4ff;color:#2f6fed}.asset-kind-icon.dataset{background:#eef8f5;color:#1c9b68}.asset-kind-icon.metric{background:#f4efff;color:#7659da}.asset-name-cell>div{min-width:0}.asset-name-cell strong{display:block;overflow:hidden;color:#2468d8;font-size:12px;font-weight:650;text-overflow:ellipsis;white-space:nowrap}.asset-name-cell small{display:block;margin-top:4px;overflow:hidden;color:#94a1b2;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.type-tag{display:inline-flex;padding:3px 8px;border-radius:6px;background:#edf4ff;color:#315f9d;font-size:10px}.type-tag.dataset{background:#edf8f3;color:#26805b}.type-tag.metric{background:#f4efff;color:#7659da}.detail-cell{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.favorite-btn{border:0;background:transparent;color:#2f6fed;font-size:11px;cursor:pointer}.favorite-btn.active{color:#d68f18}.asset-empty{min-height:360px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#98a5b6}.empty-mark{width:42px;height:42px;border-radius:50%;display:grid;place-items:center;background:#edf4fb;color:#6d87a8;font-size:19px}.asset-empty strong{margin-top:9px;color:#52657d;font-size:13px}.asset-empty span{margin-top:5px;font-size:10px}
@media(max-width:1280px){.filter-card{grid-template-columns:1fr 1fr 1fr}.search-input{grid-column:span 2}.filter-actions{justify-content:flex-end}.asset-shell{grid-template-columns:220px minmax(0,1fr)}}
@media(max-width:900px){.asset-metrics{grid-template-columns:repeat(2,1fr)}.asset-shell{grid-template-columns:1fr}.asset-directory{display:none}.filter-card{grid-template-columns:1fr}.search-input{grid-column:auto}.filter-actions{justify-content:flex-start}.asset-title-row{display:block}.asset-title-row p{margin-top:5px}}
</style>
