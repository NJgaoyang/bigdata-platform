<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, watch, type PropType } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '../../components/PageHeader.vue'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import { assetApi, metadataApi, metricApi, type AssetItem, type DatasetView, type MetricView, type TableProfile, type ColumnView } from '../../api/domain'

type CatalogTab='ALL'|'TABLE'|'METRIC'|'DATASET'
type ViewMode='list'|'directory'
type DirectoryMode='business'|'technical'
type DirectoryChild={name:string;label:string;count:number}
type DirectoryRoot={name:string;label:string;count:number;children:DirectoryChild[]}

const route=useRoute(),router=useRouter()
const loading=ref(true),rows=ref<AssetItem[]>([]),metrics=ref<MetricView[]>([]),datasets=ref<DatasetView[]>([]),sources=ref<DataSourceView[]>([])
const keyword=ref(''),catalogTab=ref<CatalogTab>('ALL'),viewMode=ref<ViewMode>('list'),directoryMode=ref<DirectoryMode>('business')
const sourceFilter=ref(''),domainFilter=ref(''),ownerFilter=ref(''),databaseFilter=ref('')
const dirRoot=ref(''),dirNode=ref(''),treeKeyword=ref(''),dirKeyword=ref(''),dirTab=ref<CatalogTab>('ALL')
const drawerOpen=ref(false),detailTab=ref<'overview'|'definition'|'source'|'version'>('overview'),selected=ref<AssetItem>()
const detailLoading=ref(false),tableProfile=ref<TableProfile>(),tableColumns=ref<ColumnView[]>([])
const currentPage=ref(1),pageSize=12
const mode=computed(()=>route.path.endsWith('/favorites')?'favorites':'catalog')
const favoriteRows=computed(()=>rows.value.filter(r=>r.favorite))
const currentRows=computed(()=>mode.value==='favorites'?favoriteRows.value:rows.value)
const metricMap=computed(()=>new Map(metrics.value.map(m=>[m.id,m])))
const datasetMap=computed(()=>new Map(datasets.value.map(d=>[d.id,d])))
const sourceMap=computed(()=>new Map(sources.value.map(s=>[s.id,s])))
const countBy=(type:string)=>rows.value.filter(r=>r.type===type).length
const summary=computed(()=>({all:rows.value.length,table:countBy('TABLE'),metric:countBy('METRIC'),favorite:favoriteRows.value.length}))
const domains=computed(()=>[...new Set(metrics.value.map(m=>m.businessDomain).filter(Boolean) as string[])].sort((a,b)=>a.localeCompare(b,'zh-CN')))
const owners=computed(()=>[...new Set(rows.value.map(r=>r.owner).filter(Boolean) as string[])].sort((a,b)=>a.localeCompare(b,'zh-CN')))
const sourceNames=computed(()=>[...new Set(rows.value.filter(r=>r.type==='TABLE').map(r=>r.source).filter(Boolean) as string[])].sort((a,b)=>a.localeCompare(b,'zh-CN')))
const databases=computed(()=>[...new Set(rows.value.filter(r=>r.type==='TABLE').map(r=>parseTable(r)?.database).filter(Boolean) as string[])].sort((a,b)=>a.localeCompare(b,'zh-CN')))

function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
function typeLabel(type:string){return ({TABLE:'数据表',METRIC:'业务指标',DATASET:'数据集'} as Record<string,string>)[type]||type}
function ownerAvatar(v?:string){return (v||'—').slice(0,1)}
function fmt(v?:string){return v?v.replace('T',' ').slice(0,16):'—'}
function parseId(ref:string,prefix:string){const m=ref.match(new RegExp(`^${prefix}:(\\d+)`));return m?Number(m[1]):undefined}
function parseTable(a:AssetItem){const m=a.ref.match(/^TABLE:(\d+):(.+)\.([^.]+)$/);return m?{dataSourceId:Number(m[1]),database:m[2],table:m[3]}:undefined}
function metricOf(a?:AssetItem){const id=a&&parseId(a.ref,'METRIC');return id?metricMap.value.get(id):undefined}
function datasetOf(a?:AssetItem){const id=a&&parseId(a.ref,'DATASET');return id?datasetMap.value.get(id):undefined}
function assetCode(a:AssetItem){if(a.type==='METRIC')return metricOf(a)?.metricCode||a.detail||a.ref;if(a.type==='DATASET')return datasetOf(a)?.datasetCode||a.ref;return a.description||a.detail||a.ref}
function assetOrigin(a:AssetItem){
  if(a.type==='METRIC'){const m=metricOf(a);return{main:m?.businessDomain||a.source||'未归类',sub:m?.sourceTable?`来源表：${m.sourceDatabase?m.sourceDatabase+'.':''}${m.sourceTable}`:''}}
  if(a.type==='DATASET'){const d=datasetOf(a),src=d?.sourceDataSourceId?sourceMap.value.get(d.sourceDataSourceId)?.name:'';return{main:src||'数据集',sub:[d?.sourceDatabase,d?.sourceTable].filter(Boolean).join('.')||a.detail||''}}
  const t=parseTable(a);return{main:a.source||'StarRocks',sub:t?[t.database,t.table].join('.'):a.detail||''}
}
function updatedAt(a:AssetItem){return a.type==='METRIC'?metricOf(a)?.updatedAt:a.type==='DATASET'?datasetOf(a)?.updatedAt:undefined}
function searchHay(a:AssetItem){const m=metricOf(a),d=datasetOf(a);return[a.name,a.ref,a.description,a.source,a.detail,a.owner,m?.metricCode,m?.businessDomain,m?.sourceDatabase,m?.sourceTable,d?.datasetCode,d?.sourceDatabase,d?.sourceTable].filter(Boolean).join(' ').toLowerCase()}
function resetFilters(){keyword.value='';sourceFilter.value='';domainFilter.value='';ownerFilter.value='';databaseFilter.value='';currentPage.value=1}

const filtered=computed(()=>currentRows.value.filter(a=>{
  if(catalogTab.value!=='ALL'&&a.type!==catalogTab.value)return false
  const q=keyword.value.trim().toLowerCase();if(q&&!searchHay(a).includes(q))return false
  if(ownerFilter.value&&a.owner!==ownerFilter.value)return false
  if(sourceFilter.value&&a.source!==sourceFilter.value)return false
  if(databaseFilter.value&&parseTable(a)?.database!==databaseFilter.value)return false
  if(domainFilter.value&&metricOf(a)?.businessDomain!==domainFilter.value)return false
  return true
}))
const totalPages=computed(()=>Math.max(1,Math.ceil(filtered.value.length/pageSize)))
const pagedFiltered=computed(()=>{const start=(currentPage.value-1)*pageSize;return filtered.value.slice(start,start+pageSize)})
const pageNumbers=computed(()=>Array.from({length:totalPages.value},(_,i)=>i+1).filter(p=>Math.abs(p-currentPage.value)<=2))
function changePage(page:number){currentPage.value=Math.min(totalPages.value,Math.max(1,page))}
const businessRoots=computed<DirectoryRoot[]>(()=>{
  const roots:DirectoryRoot[]=[]
  for(const domain of domains.value){
    const ms=rows.value.filter(a=>a.type==='METRIC'&&metricOf(a)?.businessDomain===domain)
    const sourceKeys=new Set(ms.map(a=>{const m=metricOf(a);return m?.sourceTable?`${m.sourceDatabase||''}.${m.sourceTable}`:''}).filter(Boolean))
    const ts=rows.value.filter(a=>{const t=parseTable(a);return a.type==='TABLE'&&t&&sourceKeys.has(`${t.database}.${t.table}`)})
    roots.push({name:domain,label:domain,count:new Set([...ms,...ts].map(x=>x.ref)).size,children:[{name:'METRIC',label:'业务指标',count:ms.length},{name:'TABLE',label:'关联数据表',count:ts.length}]})
  }
  if(rows.value.some(a=>a.type==='DATASET'))roots.push({name:'__DATASET__',label:'数据集',count:countBy('DATASET'),children:[{name:'DATASET',label:'全部数据集',count:countBy('DATASET')}]})
  return roots
})
const technicalRoots=computed<DirectoryRoot[]>(()=>{
  const groups=new Map<string,Map<string,number>>()
  for(const a of rows.value.filter(x=>x.type==='TABLE')){
    const t=parseTable(a);if(!t)continue
    const src=a.source||sourceMap.value.get(t.dataSourceId)?.name||`数据源 ${t.dataSourceId}`
    if(!groups.has(src))groups.set(src,new Map())
    const dbs=groups.get(src)!;dbs.set(t.database,(dbs.get(t.database)||0)+1)
  }
  return[...groups].map(([src,dbs])=>({name:src,label:src,count:[...dbs.values()].reduce((s,n)=>s+n,0),children:[...dbs].map(([db,count])=>({name:db,label:db,count}))}))
})
const directoryRoots=computed(()=>directoryMode.value==='business'?businessRoots.value:technicalRoots.value)
const visibleDirectoryRoots=computed(()=>{const q=treeKeyword.value.trim().toLowerCase();return !q?directoryRoots.value:directoryRoots.value.filter(r=>`${r.label} ${r.children.map(c=>c.label).join(' ')}`.toLowerCase().includes(q))})
function ensureDirectorySelection(){
  const roots=directoryRoots.value;if(!roots.length){dirRoot.value='';dirNode.value='';return}
  let root=roots.find(r=>r.name===dirRoot.value)
  if(!root){root=roots[0];dirRoot.value=root.name}
  if(dirNode.value!==root.name&&!root.children.some(c=>c.name===dirNode.value))dirNode.value=root.name
}
function switchDirectoryMode(v:DirectoryMode){directoryMode.value=v;dirRoot.value='';dirNode.value='';dirTab.value='ALL';treeKeyword.value='';dirKeyword.value='';ensureDirectorySelection()}
function selectDirectory(root:string,node:string){dirRoot.value=root;dirNode.value=node;dirTab.value='ALL'}
const selectedRoot=computed(()=>directoryRoots.value.find(r=>r.name===dirRoot.value))
const selectedChild=computed(()=>selectedRoot.value?.children.find(c=>c.name===dirNode.value))
const directoryAssets=computed(()=>{
  let out:AssetItem[]=[]
  if(directoryMode.value==='business'){
    if(dirRoot.value==='__DATASET__')out=rows.value.filter(a=>a.type==='DATASET')
    else{
      const ms=rows.value.filter(a=>a.type==='METRIC'&&metricOf(a)?.businessDomain===dirRoot.value)
      const keys=new Set(ms.map(a=>{const m=metricOf(a);return m?.sourceTable?`${m.sourceDatabase||''}.${m.sourceTable}`:''}).filter(Boolean))
      const ts=rows.value.filter(a=>{const t=parseTable(a);return a.type==='TABLE'&&t&&keys.has(`${t.database}.${t.table}`)})
      out=dirNode.value==='METRIC'?ms:dirNode.value==='TABLE'?ts:[...ms,...ts]
    }
  }else out=rows.value.filter(a=>{const t=parseTable(a);return a.type==='TABLE'&&a.source===dirRoot.value&&(!dirNode.value||dirNode.value===dirRoot.value||t?.database===dirNode.value)})
  if(dirTab.value!=='ALL')out=out.filter(a=>a.type===dirTab.value)
  const q=dirKeyword.value.trim().toLowerCase();if(q)out=out.filter(a=>searchHay(a).includes(q))
  return[...new Map(out.map(a=>[a.ref,a])).values()]
})
const directoryTitle=computed(()=>selectedChild.value?.label||selectedRoot.value?.label||'资产目录')
const directoryDesc=computed(()=>directoryMode.value==='business'
  ?(dirRoot.value==='__DATASET__'?'平台沉淀的数据集资产。':dirNode.value==='METRIC'?`${selectedRoot.value?.label||''}下已认证的业务指标。`:dirNode.value==='TABLE'?`由${selectedRoot.value?.label||''}指标实际引用的数据表。`:`${selectedRoot.value?.label||''}业务域的数据资产。`)
  :`${selectedRoot.value?.label||''}${selectedChild.value?` / ${selectedChild.value.label}`:''} 下可发现的数据表资产。`)

async function load(){loading.value=true;try{const[catalog,ms,ds,srcs]=await Promise.all([assetApi.catalog(),metricApi.list(),assetApi.datasets(),dataSourceApi.list()]);rows.value=catalog;metrics.value=ms;datasets.value=ds;sources.value=srcs;ensureDirectorySelection();currentPage.value=1}catch(e){ElMessage.error(msg(e))}finally{loading.value=false}}
async function toggle(a:AssetItem){try{a.favorite?await assetApi.unfavorite(a.ref):await assetApi.favorite(a.type,a.ref);a.favorite=!a.favorite;ElMessage.success(a.favorite?'已收藏':'已取消收藏')}catch(e){ElMessage.error(msg(e))}}
function selectSummary(kind:'ALL'|'TABLE'|'METRIC'|'favorite'){if(kind==='favorite'){void router.push('/assets/favorites');return}if(mode.value==='favorites')void router.push('/assets/catalog');viewMode.value='list';catalogTab.value=kind;resetFilters()}
function listTab(v:CatalogTab){catalogTab.value=v;resetFilters()}

async function openDetail(a:AssetItem){
  selected.value=a;detailTab.value='overview';tableProfile.value=undefined;tableColumns.value=[];drawerOpen.value=true
  if(a.type==='TABLE'){const t=parseTable(a);if(t){detailLoading.value=true;try{const[profile,cols]=await Promise.all([metadataApi.profile(t.dataSourceId,t.database,t.table),metadataApi.columns(t.dataSourceId,t.database,t.table)]);if(selected.value?.ref===a.ref){tableProfile.value=profile;tableColumns.value=cols}}catch{}finally{detailLoading.value=false}}}
}
function relatedAssets(a?:AssetItem){
  if(!a)return[] as AssetItem[]
  if(a.type==='METRIC'){const m=metricOf(a);return rows.value.filter(x=>{const t=parseTable(x);return x.type==='TABLE'&&t&&t.database===(m?.sourceDatabase||'')&&t.table===m?.sourceTable})}
  if(a.type==='TABLE'){const t=parseTable(a);return rows.value.filter(x=>{const m=metricOf(x);return x.type==='METRIC'&&m?.sourceDatabase===t?.database&&m?.sourceTable===t?.table})}
  if(a.type==='DATASET'){const d=datasetOf(a);return rows.value.filter(x=>{const t=parseTable(x);return x.type==='TABLE'&&!!t&&t.dataSourceId===d?.sourceDataSourceId&&t.database===d?.sourceDatabase&&t.table===d?.sourceTable})}
  return[]
}
function openPrimaryRelated(){const a=relatedAssets(selected.value)[0];if(a)void openDetail(a);else ElMessage.info(selected.value?.type==='TABLE'?'暂无关联业务指标':'暂无可跳转的来源资产')}
const AssetTable=defineComponent({
  name:'AssetTable',props:{items:{type:Array as PropType<AssetItem[]>,required:true},showUpdated:{type:Boolean,default:false}},emits:['open','favorite'],
  setup(props,{emit}){return()=>h('div',{class:'asset-table-wrap'},[
    props.items.length?h('table',{class:'asset-table'},[
      h('colgroup',{},props.showUpdated
        ?[h('col',{style:'width:31%'}),h('col',{style:'width:13%'}),h('col',{style:'width:24%'}),h('col',{style:'width:13%'}),h('col',{style:'width:12%'}),h('col',{style:'width:7%'})]
        :[h('col',{style:'width:34%'}),h('col',{style:'width:14%'}),h('col',{style:'width:23%'}),h('col',{style:'width:14%'}),h('col',{style:'width:15%'})]),
      h('thead',{},h('tr',{},[h('th',{},'资产名称'),h('th',{},'类型'),h('th',{},'归属 / 来源'),h('th',{},'负责人'),...(props.showUpdated?[h('th',{},'更新时间')]:[]),h('th',{},'操作')])),
      h('tbody',{},props.items.map(a=>{const origin=assetOrigin(a);return h('tr',{key:a.ref},[
        h('td',{},[h('button',{class:'asset-name',onClick:()=>emit('open',a)},a.name),h('div',{class:'asset-desc'},assetCode(a))]),
        h('td',{},h('span',{class:['type-badge',a.type.toLowerCase()]},typeLabel(a.type))),
        h('td',{},[h('div',{},origin.main),h('div',{class:'asset-desc'},origin.sub)]),
        h('td',{},a.owner?h('div',{class:'owner'},[h('span',{class:'owner-avatar'},ownerAvatar(a.owner)),a.owner]):'—'),
        ...(props.showUpdated?[h('td',{class:'muted-cell'},fmt(updatedAt(a)))]:[]),
        h('td',{},h('div',{class:'row-actions'},[h('button',{class:['star',{on:a.favorite}],title:a.favorite?'取消收藏':'收藏',onClick:()=>emit('favorite',a)},a.favorite?'★':'☆'),h('button',{class:'text-action',onClick:()=>emit('open',a)},'查看')]))
      ])}))
    ]):h('div',{class:'asset-empty'},'暂无匹配资产')
  ])}
})

watch(()=>route.path,()=>{catalogTab.value='ALL';resetFilters();drawerOpen.value=false})
watch([keyword,catalogTab,sourceFilter,domainFilter,ownerFilter,databaseFilter],()=>{currentPage.value=1})
watch(totalPages,v=>{if(currentPage.value>v)currentPage.value=v})
watch(directoryRoots,ensureDirectorySelection)
onMounted(load)
</script>
<template>
<div class="ds-page asset-page">
  <PageHeader :title="mode==='catalog'?'资产目录':'我的收藏'" :subtitle="mode==='catalog'?'统一检索、浏览和发现平台数据资产。':'快速查看你收藏的数据表、业务指标与数据集。'">
    <template #actions>
      <div v-if="mode==='catalog'" class="view-switch">
        <button :class="{active:viewMode==='list'}" @click="viewMode='list'">列表视图</button>
        <button :class="{active:viewMode==='directory'}" @click="viewMode='directory';ensureDirectorySelection()">目录视图</button>
      </div>
    </template>
  </PageHeader>

  <div v-if="mode==='catalog'" class="asset-summary">
    <button :class="['summary-item',{active:viewMode==='list'&&catalogTab==='ALL'}]" @click="selectSummary('ALL')"><b>{{summary.all}}</b><span>资产总数</span></button>
    <button :class="['summary-item',{active:viewMode==='list'&&catalogTab==='TABLE'}]" @click="selectSummary('TABLE')"><b>{{summary.table}}</b><span>数据表</span></button>
    <button :class="['summary-item',{active:viewMode==='list'&&catalogTab==='METRIC'}]" @click="selectSummary('METRIC')"><b>{{summary.metric}}</b><span>业务指标</span></button>
    <button class="summary-item" @click="selectSummary('favorite')"><b>{{summary.favorite}}</b><span>我的收藏</span></button>
    <div class="summary-note">业务指标仅统计已认证指标 · 数据集计入资产总数</div>
  </div>

  <el-skeleton v-if="loading" :rows="8" animated class="loading-card"/>

  <template v-else-if="mode==='catalog'&&viewMode==='directory'">
    <div class="directory-card">
      <aside class="directory-left">
        <div class="directory-head">
          <strong>浏览维度</strong>
          <div class="segment"><button :class="{active:directoryMode==='business'}" @click="switchDirectoryMode('business')">业务视角</button><button :class="{active:directoryMode==='technical'}" @click="switchDirectoryMode('technical')">技术视角</button></div>
          <el-input v-model="treeKeyword" size="small" :placeholder="directoryMode==='business'?'搜索业务域':'搜索数据源 / Database'" :prefix-icon="Search" clearable/>
        </div>
        <div class="directory-tree">
          <div v-for="root in visibleDirectoryRoots" :key="root.name" class="tree-group">
            <button :class="['tree-row','root',{active:dirRoot===root.name&&dirNode===root.name}]" @click="selectDirectory(root.name,root.name)"><span class="tree-arrow">⌄</span><span>{{root.label}}</span><em>{{root.count}}</em></button>
            <button v-for="child in root.children" :key="root.name+child.name" :class="['tree-row','child',{active:dirRoot===root.name&&dirNode===child.name}]" @click="selectDirectory(root.name,child.name)"><span>{{child.label}}</span><em>{{child.count}}</em></button>
          </div>
          <div v-if="!visibleDirectoryRoots.length" class="tree-empty">没有匹配目录</div>
        </div>
      </aside>
      <section class="directory-right">
        <div class="directory-context">
          <div class="breadcrumb">{{directoryMode==='business'?'业务视角':'技术视角'}} / <span>{{selectedRoot?.label}}<template v-if="selectedChild"> / {{selectedChild.label}}</template></span></div>
          <h2>{{directoryTitle}}</h2><p>{{directoryDesc}}</p>
          <div class="directory-meta"><span><b>{{directoryAssets.filter(a=>a.type==='METRIC').length}}</b>业务指标</span><span><b>{{directoryAssets.filter(a=>a.type==='TABLE').length}}</b>数据表</span><span v-if="directoryAssets.some(a=>a.type==='DATASET')"><b>{{directoryAssets.filter(a=>a.type==='DATASET').length}}</b>数据集</span></div>
          <el-input v-model="dirKeyword" :prefix-icon="Search" placeholder="搜索当前目录资产" clearable/>
        </div>
        <div class="catalog-tabs">
          <button :class="{active:dirTab==='ALL'}" @click="dirTab='ALL'">全部资产</button>
          <button v-if="directoryMode==='business'" :class="{active:dirTab==='METRIC'}" @click="dirTab='METRIC'">业务指标</button>
          <button :class="{active:dirTab==='TABLE'}" @click="dirTab='TABLE'">数据表</button>
          <button v-if="dirRoot==='__DATASET__'" :class="{active:dirTab==='DATASET'}" @click="dirTab='DATASET'">数据集</button>
        </div>
        <AssetTable :items="directoryAssets" @open="openDetail" @favorite="toggle"/>
      </section>
    </div>
  </template>

  <template v-else>
    <div class="catalog-card">
      <div v-if="mode==='catalog'" class="search-toolbar"><el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索资产名称 / 编码 / 描述 / 来源表" clearable/><el-button type="primary">搜索</el-button></div>
      <div class="catalog-tabs">
        <button :class="{active:catalogTab==='ALL'}" @click="listTab('ALL')">全部资产 <span>({{currentRows.length}})</span></button>
        <button :class="{active:catalogTab==='TABLE'}" @click="listTab('TABLE')">数据表 <span>({{currentRows.filter(a=>a.type==='TABLE').length}})</span></button>
        <button :class="{active:catalogTab==='METRIC'}" @click="listTab('METRIC')">业务指标 <span>({{currentRows.filter(a=>a.type==='METRIC').length}})</span></button>
        <button :class="{active:catalogTab==='DATASET'}" @click="listTab('DATASET')">数据集 <span>({{currentRows.filter(a=>a.type==='DATASET').length}})</span></button>
      </div>
      <div class="filter-row">
        <span>筛选</span>
        <el-select v-if="catalogTab==='ALL'||catalogTab==='TABLE'" v-model="sourceFilter" clearable :placeholder="catalogTab==='TABLE'?'全部数据源':'全部来源'" style="width:160px"><el-option v-for="x in sourceNames" :key="x" :label="x" :value="x"/></el-select>
        <el-select v-if="catalogTab==='TABLE'" v-model="databaseFilter" clearable placeholder="全部 Database" style="width:150px"><el-option v-for="x in databases" :key="x" :label="x" :value="x"/></el-select>
        <el-select v-if="catalogTab==='METRIC'||catalogTab==='ALL'" v-model="domainFilter" clearable placeholder="全部业务域" style="width:150px"><el-option v-for="x in domains" :key="x" :label="x" :value="x"/></el-select>
        <el-select v-model="ownerFilter" clearable placeholder="全部负责人" style="width:150px"><el-option v-for="x in owners" :key="x" :label="x" :value="x"/></el-select>
        <el-button @click="resetFilters">重置</el-button><div class="filter-spacer"/><el-button :loading="loading" @click="load">刷新</el-button>
      </div>
      <AssetTable :items="pagedFiltered" :show-updated="true" @open="openDetail" @favorite="toggle"/>
      <div class="pagination">
        <span>共 {{filtered.length}} 条</span>
        <div class="pager">
          <button class="page-btn" :disabled="currentPage<=1" @click="changePage(currentPage-1)">‹</button>
          <button v-for="p in pageNumbers" :key="p" :class="['page-btn',{active:currentPage===p}]" @click="changePage(p)">{{p}}</button>
          <button class="page-btn" :disabled="currentPage>=totalPages" @click="changePage(currentPage+1)">›</button>
        </div>
      </div>
    </div>
  </template>

  <el-drawer v-model="drawerOpen" size="620px" :with-header="false" class="asset-drawer">
    <template v-if="selected">
      <div class="drawer-head"><div><h3>{{selected.name}}</h3><p>{{typeLabel(selected.type)}} · {{assetCode(selected)}}</p></div><button class="drawer-close" @click="drawerOpen=false">×</button></div>
      <div class="drawer-tabs"><button :class="{active:detailTab==='overview'}" @click="detailTab='overview'">资产概览</button><button v-if="selected.type==='METRIC'" :class="{active:detailTab==='definition'}" @click="detailTab='definition'">指标口径</button><button :class="{active:detailTab==='source'}" @click="detailTab='source'">数据来源</button><button v-if="selected.type==='METRIC'" :class="{active:detailTab==='version'}" @click="detailTab='version'">版本信息</button></div>
      <div class="drawer-body">
        <template v-if="detailTab==='overview'">
          <div class="detail-title">基本信息</div>
          <div v-if="selected.type==='METRIC'" class="detail-grid"><div><label>指标编码</label><span>{{metricOf(selected)?.metricCode||'—'}}</span></div><div><label>当前版本</label><span>V{{metricOf(selected)?.currentVersion||1}}</span></div><div><label>业务域</label><span>{{metricOf(selected)?.businessDomain||'未归类'}}</span></div><div><label>负责人</label><span>{{selected.owner||'—'}}</span></div><div><label>状态</label><span>已认证</span></div><div><label>更新时间</label><span>{{fmt(metricOf(selected)?.updatedAt)}}</span></div></div>
          <div v-else-if="selected.type==='TABLE'" class="detail-grid"><div><label>表名称</label><span>{{selected.name}}</span></div><div><label>数据源</label><span>{{assetOrigin(selected).main}}</span></div><div><label>Database</label><span>{{parseTable(selected)?.database||'—'}}</span></div><div><label>负责人</label><span>{{tableProfile?.owner||selected.owner||'—'}}</span></div><div><label>数据量</label><span>{{tableProfile?.rowCount?.toLocaleString()||'—'}}</span></div><div><label>字段数</label><span>{{detailLoading?'读取中':tableColumns.length||'—'}}</span></div></div>
          <div v-else class="detail-grid"><div><label>数据集编码</label><span>{{datasetOf(selected)?.datasetCode||'—'}}</span></div><div><label>状态</label><span>{{datasetOf(selected)?.status||selected.status}}</span></div><div><label>负责人</label><span>{{selected.owner||'—'}}</span></div><div><label>更新时间</label><span>{{fmt(datasetOf(selected)?.updatedAt)}}</span></div></div>
          <div class="detail-title spaced">资产说明</div><div class="detail-desc">{{selected.description||'暂无说明'}}</div>
        </template>
        <template v-else-if="detailTab==='definition'">
          <div class="detail-title">计算口径</div>
          <div class="detail-grid"><div><label>指标类型</label><span>{{metricOf(selected)?.metricType||'—'}}</span></div><div><label>聚合方式</label><span>{{metricOf(selected)?.aggregation||'—'}}</span></div><div><label>统计字段</label><span>{{metricOf(selected)?.sourceField||'—'}}</span></div><div><label>时间字段</label><span>{{metricOf(selected)?.timeField||'—'}}</span></div><div class="wide"><label>过滤条件</label><span>{{metricOf(selected)?.filterExpression||'—'}}</span></div><div class="wide"><label>分析维度</label><span>{{metricOf(selected)?.dimensions?.map(d=>d.dimensionName).join('、')||'—'}}</span></div></div>
          <div class="detail-title spaced">公式</div><div class="detail-desc mono">{{metricOf(selected)?.expressionText||'—'}}</div>
        </template>
        <template v-else-if="detailTab==='source'">
          <div class="detail-title">来源 / 关联资产</div>
          <div v-if="relatedAssets(selected).length" class="related-list"><button v-for="a in relatedAssets(selected)" :key="a.ref" @click="openDetail(a)"><span :class="['type-badge',a.type.toLowerCase()]">{{typeLabel(a.type)}}</span><div><b>{{a.name}}</b><small>{{assetOrigin(a).main}}<template v-if="assetOrigin(a).sub"> · {{assetOrigin(a).sub}}</template></small></div><span class="go">›</span></button></div>
          <div v-else class="detail-desc">当前暂无可关联资产。</div>
        </template>
        <template v-else>
          <div class="detail-title">当前版本</div>
          <div class="version-row"><b>V{{metricOf(selected)?.currentVersion||1}} · 当前版本</b><span>{{fmt(metricOf(selected)?.updatedAt)}} · {{selected.owner||'—'}}</span></div>
          <div class="detail-tip">当前指标接口仅提供当前任务版本；不展示虚构的历史版本记录。</div>
        </template>
      </div>
      <div class="drawer-foot"><el-button @click="toggle(selected)">{{selected.favorite?'★ 已收藏':'☆ 收藏'}}</el-button><el-button type="primary" @click="openPrimaryRelated">{{selected.type==='TABLE'?'查看关联指标':'查看来源资产'}}</el-button></div>
    </template>
  </el-drawer>
</div>
</template>

<style scoped>
.asset-page{--line:#e7ebf2;--line2:#dfe5ee;--blue:#2f6bff;--blue-soft:#eef4ff;color:#1f2937}
.view-switch{display:flex;gap:4px;padding:4px;background:#eef1f5;border-radius:9px;align-items:center}.view-switch button{height:32px;border:0;background:transparent;border-radius:7px;color:#667085;padding:0 13px;cursor:pointer}.view-switch button.active{background:#fff;color:#245fd8;font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.08)}
.asset-summary{background:#fff;border:1px solid var(--line);border-radius:10px;margin-bottom:14px;padding:0 18px;min-height:64px;display:flex;align-items:center}.summary-item{position:relative;display:flex;align-items:baseline;gap:7px;padding:0 26px;min-width:150px;height:40px;border:0;background:transparent;cursor:pointer}.summary-item:first-child{padding-left:0}.summary-item:not(:last-of-type):after{content:"";position:absolute;right:0;top:6px;width:1px;height:28px;background:#edf0f4}.summary-item b{font-size:22px;line-height:1;font-weight:700;color:#25324b;font-variant-numeric:tabular-nums}.summary-item span{font-size:12px;color:#667085}.summary-item:hover b,.summary-item.active b{color:var(--blue)}.summary-item:hover span,.summary-item.active span{color:#344054}.summary-note{margin-left:auto;color:#98a2b3;font-size:12px}
.loading-card,.catalog-card,.directory-card{background:#fff;border:1px solid var(--line);border-radius:10px;overflow:hidden}.loading-card{padding:18px}.search-toolbar{display:flex;gap:10px;padding:14px 16px;border-bottom:1px solid var(--line)}.search-toolbar .el-input{flex:1}.catalog-tabs{height:46px;display:flex;gap:22px;align-items:flex-end;padding:0 16px;border-bottom:1px solid var(--line)}.catalog-tabs button{height:46px;border:0;background:transparent;color:#667085;position:relative;cursor:pointer;padding:0 1px}.catalog-tabs button.active{color:#245fd8;font-weight:600}.catalog-tabs button.active:after{content:"";position:absolute;left:0;right:0;bottom:-1px;height:2px;background:var(--blue)}.catalog-tabs span{color:#98a2b3;font-size:11px;font-weight:400}
.filter-row{display:flex;align-items:center;gap:10px;padding:12px 16px;border-bottom:1px solid var(--line)}.filter-row>span{color:#98a2b3;font-size:12px;margin-right:2px}.filter-spacer{flex:1}.asset-table-wrap{overflow:auto;padding:0 16px 16px}.asset-table{width:100%;border-collapse:collapse;table-layout:fixed}.asset-table :deep(th){height:42px;background:#fafbfc;color:#667085;font-size:12px;font-weight:600;text-align:left;padding:0 12px;border-bottom:1px solid var(--line)}.asset-table :deep(td){height:62px;padding:10px 12px;border-bottom:1px solid #eef1f5;color:#344054;font-size:12px;vertical-align:middle}.asset-table :deep(tr:hover td){background:#fcfdff}
.asset-name{border:0;background:transparent;padding:0;color:#245fd8;font-weight:600;cursor:pointer}.asset-desc{margin-top:4px;color:#98a2b3;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.type-badge{height:23px;padding:0 8px;border-radius:999px;background:#eef4ff;color:#275fc2;font-size:11px;display:inline-flex;align-items:center}.type-badge.table{background:#eefaf6;color:#16825f}.type-badge.dataset{background:#f4f1ff;color:#6c55bd}.owner{display:flex;align-items:center;gap:7px}.owner-avatar{width:25px;height:25px;border-radius:50%;background:#eef2f7;display:grid;place-items:center;color:#667085;font-size:11px;font-weight:600}.row-actions{display:flex;align-items:center;gap:4px;white-space:nowrap}.star{border:0;background:transparent;color:#98a2b3;font-size:18px;cursor:pointer;padding:4px}.star.on{color:#2f6bff}.text-action{border:0;background:transparent;color:#245fd8;cursor:pointer;padding:4px}.muted-cell{color:#98a2b3!important}.asset-empty{height:180px;display:grid;place-items:center;color:#98a2b3;font-size:12px}
.pagination{height:52px;border-top:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;padding:0 16px;color:#667085;font-size:12px}.pager{display:flex;align-items:center;gap:6px}.page-btn{width:30px;height:30px;border:1px solid var(--line);background:#fff;border-radius:7px;color:#475467;cursor:pointer}.page-btn:hover:not(:disabled){border-color:#c9d8ff;color:#245fd8}.page-btn.active{background:#edf4ff;color:#245fd8;border-color:#c9d8ff}.page-btn:disabled{opacity:.45;cursor:not-allowed}
.directory-card{display:grid;grid-template-columns:270px minmax(0,1fr);min-height:650px}.directory-left{border-right:1px solid var(--line);display:flex;flex-direction:column}.directory-head{padding:14px;border-bottom:1px solid var(--line)}.directory-head>strong{display:block;margin-bottom:10px;font-size:13px;color:#344054}.segment{display:flex;padding:3px;margin-bottom:10px;background:#f2f4f7;border-radius:8px}.segment button{flex:1;height:30px;border:0;background:transparent;color:#667085;border-radius:6px;cursor:pointer}.segment button.active{background:#fff;color:#245fd8;font-weight:600;box-shadow:0 1px 3px rgba(16,24,40,.08)}.directory-tree{padding:9px 8px 16px;overflow:auto;flex:1}.tree-group{margin-bottom:4px}.tree-row{width:100%;height:38px;border:0;border-radius:7px;background:#fff;color:#475467;display:flex;align-items:center;gap:6px;padding:0 9px;text-align:left;cursor:pointer;margin-bottom:2px}.tree-row:hover{background:#f7f9fc}.tree-row.active{background:#eef4ff;color:#245fd8;font-weight:600}.tree-row.child{padding-left:31px}.tree-row em{margin-left:auto;color:#98a2b3;font-size:11px;font-style:normal;font-weight:400}.tree-arrow{width:16px;text-align:center;color:#98a2b3}.tree-empty{padding:30px 10px;text-align:center;color:#98a2b3;font-size:11px}
.directory-right{min-width:0;display:flex;flex-direction:column}.directory-context{padding:16px 18px 0}.breadcrumb{font-size:12px;color:#98a2b3;margin-bottom:10px}.breadcrumb span{color:#667085}.directory-context h2{margin:0;font-size:20px;font-weight:700}.directory-context p{margin:6px 0 0;color:#667085;font-size:12px;line-height:1.6}.directory-meta{display:flex;gap:18px;margin-top:11px;color:#98a2b3;font-size:12px}.directory-meta b{color:#475467;margin-right:4px;font-weight:600}.directory-context .el-input{margin-top:16px;padding:12px 0;border-top:1px solid #eef1f5;max-width:none}.directory-right>.asset-table-wrap{flex:1;padding:0 18px 18px}
.asset-drawer :deep(.el-drawer__body){padding:0;display:flex;flex-direction:column}.drawer-head{height:68px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 20px;border-bottom:1px solid var(--line)}.drawer-head h3{margin:0;font-size:18px}.drawer-head p{margin:4px 0 0;color:#98a2b3;font-size:12px}.drawer-close{width:32px;height:32px;border:0;border-radius:8px;background:transparent;color:#667085;font-size:22px;cursor:pointer}.drawer-close:hover{background:#f3f4f6}.drawer-tabs{height:46px;flex:none;display:flex;gap:22px;padding:0 20px;border-bottom:1px solid var(--line);align-items:flex-end}.drawer-tabs button{height:46px;border:0;background:transparent;color:#667085;position:relative;cursor:pointer;padding:0 1px}.drawer-tabs button.active{color:#245fd8;font-weight:600}.drawer-tabs button.active:after{content:"";position:absolute;left:0;right:0;bottom:-1px;height:2px;background:var(--blue)}.drawer-body{flex:1;overflow:auto;padding:18px 20px}.detail-title{margin-bottom:12px;color:#344054;font-size:13px;font-weight:600}.detail-title.spaced{margin-top:22px}.detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px 20px}.detail-grid>div{min-width:0}.detail-grid .wide{grid-column:1/-1}.detail-grid label{display:block;margin-bottom:4px;color:#98a2b3;font-size:12px}.detail-grid span{color:#344054;line-height:1.55;word-break:break-word}.detail-desc{padding:12px;border:1px solid var(--line);border-radius:8px;background:#fafbfc;color:#475467;line-height:1.8}.mono{font-family:Consolas,monospace}.related-list{border-top:1px solid #eef1f5}.related-list button{width:100%;min-height:58px;border:0;border-bottom:1px solid #eef1f5;background:#fff;display:flex;align-items:center;gap:10px;text-align:left;cursor:pointer}.related-list button:hover{background:#fafcff}.related-list button>div{flex:1}.related-list b,.related-list small{display:block}.related-list b{color:#245fd8;font-size:12px}.related-list small{margin-top:4px;color:#98a2b3;font-size:10px}.related-list .go{color:#98a2b3;font-size:20px}.version-row{padding:12px 0;border-bottom:1px solid #eef1f5}.version-row b,.version-row span{display:block}.version-row span{margin-top:5px;color:#98a2b3;font-size:11px}.detail-tip{margin-top:14px;padding:10px;border-radius:6px;background:#f8fafc;color:#667085;font-size:11px}.drawer-foot{height:62px;flex:none;display:flex;justify-content:flex-end;align-items:center;gap:10px;padding:0 20px;border-top:1px solid var(--line)}
@media(max-width:1180px){.summary-item{min-width:125px;padding:0 18px}.summary-note{display:none}.directory-card{grid-template-columns:235px minmax(0,1fr)}}
</style>
