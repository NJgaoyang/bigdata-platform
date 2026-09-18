<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { dataSourceApi, type DataSourceView } from '../../api/platform'
import {
  metadataApi, metricApi, releaseApi,
  type DatabaseView, type TableView, type ColumnView,
  type DimensionView, type MetricDomainView, type MetricOverview,
  type MetricThemeView, type MetricVersionView, type MetricView
} from '../../api/domain'
import { formatDateTime } from '../../utils/display'

const route=useRoute(),router=useRouter()
const loading=ref(true),saving=ref(false),metricDrawer=ref(false),simpleDialog=ref(false),versionDialog=ref(false)
const overview=ref<MetricOverview|null>(null),rows=ref<MetricView[]>([]),domains=ref<MetricDomainView[]>([]),themes=ref<MetricThemeView[]>([])
const dimensions=ref<DimensionView[]>([]),sources=ref<DataSourceView[]>([]),versions=ref<MetricVersionView[]>([])
const sourceDatabases=ref<DatabaseView[]>([]),sourceTables=ref<TableView[]>([]),sourceColumns=ref<ColumnView[]>([])
const editingId=ref<number|null>(null),simpleId=ref<number|null>(null),simpleType=ref<'domain'|'theme'>('domain'),versionMetric=ref<MetricView|null>(null)
const keyword=ref(''),domainFilter=ref<number|undefined>(),themeFilter=ref<number|undefined>(),statusFilter=ref('')
const selectedThemeDomain=ref<number|undefined>(),themeKeyword=ref(''),themeStatus=ref(''),domainKeyword=ref(''),domainStatus=ref('')

const mode=computed(()=>route.path.split('/').pop()||'overview')
const pageMeta=computed(()=>({
  overview:['指标概览','查看指标规模、主题分布、待处理事项与最近变更。'],
  manage:['指标管理','统一维护业务指标定义、计算口径、主题归属、版本与发布状态。'],
  domains:['主题域管理','主题域是稳定的一级业务边界，可动态创建、停用和维护。'],
  themes:['主题管理','主题隶属于主题域，用于组织更具体的业务分析对象。']
} as Record<string,string[]>)[mode.value]||['指标中心','统一管理业务指标。'])

const form=reactive<any>({metricCode:'',metricName:'',metricType:'ATOMIC',description:'',businessDomain:'',domainId:undefined,themeId:undefined,ownerName:'',sourceDataSourceId:undefined,sourceDatabase:'',sourceTable:'',sourceField:'',aggregation:'COUNT',filterExpression:'',timeField:'',expressionText:'',dimensionIds:[],upstreamMetricCodes:[]})
const simpleForm=reactive<any>({code:'',name:'',description:'',ownerName:'',status:'ENABLED',sortOrder:10,domainId:undefined})

const enabledDomains=computed(()=>domains.value.filter(x=>x.status==='ENABLED'))
const metricThemes=computed(()=>themes.value.filter(x=>!form.domainId||x.domainId===form.domainId).filter(x=>x.status==='ENABLED'))
const filterThemes=computed(()=>themes.value.filter(x=>!domainFilter.value||x.domainId===domainFilter.value))
const selectedSource=computed(()=>sources.value.find(x=>x.id===form.sourceDataSourceId))
const formulaPreview=computed(()=>form.metricType==='ATOMIC'
  ? `${form.aggregation||'COUNT'}(${form.sourceField||'字段'})${form.filterExpression?`\nWHERE ${form.filterExpression}`:''}`
  : (form.expressionText||'请填写派生/复合指标表达式'))

const filteredMetrics=computed(()=>rows.value.filter(r=>{
  const q=keyword.value.trim().toLowerCase()
  const text=`${r.metricName} ${r.metricCode} ${r.ownerName||''}`.toLowerCase()
  const domainOk=!domainFilter.value||r.domainId===domainFilter.value
  const themeOk=!themeFilter.value||r.themeId===themeFilter.value
  const statusOk=!statusFilter.value||(statusFilter.value==='PUBLISHED'?['PUBLISHED','CERTIFIED'].includes(r.status):r.status===statusFilter.value)
  return (!q||text.includes(q))&&domainOk&&themeOk&&statusOk
}))
const filteredDomains=computed(()=>domains.value.filter(d=>{
  const q=domainKeyword.value.trim().toLowerCase()
  return (!q||`${d.domainName} ${d.domainCode} ${d.ownerName||''}`.toLowerCase().includes(q))&&(!domainStatus.value||d.status===domainStatus.value)
}))
const filteredThemes=computed(()=>themes.value.filter(t=>{
  const q=themeKeyword.value.trim().toLowerCase()
  return (!selectedThemeDomain.value||t.domainId===selectedThemeDomain.value)&&(!q||`${t.themeName} ${t.themeCode} ${t.ownerName||''}`.toLowerCase().includes(q))&&(!themeStatus.value||t.status===themeStatus.value)
}))
const recentMetrics=computed(()=>rows.value.slice().sort((a,b)=>String(b.updatedAt||'').localeCompare(String(a.updatedAt||''))).slice(0,5))
const maxDomainMetrics=computed(()=>Math.max(1,...domains.value.map(x=>x.metricCount)))

function statusText(v:string){return ({PUBLISHED:'已发布',CERTIFIED:'已发布',PENDING_APPROVAL:'待审批',DRAFT:'草稿',REJECTED:'已驳回',OFFLINE:'已下线'} as Record<string,string>)[v]||v}
function typeText(v:string){return ({ATOMIC:'原子指标',DERIVED:'派生指标',COMPOSITE:'复合指标'} as Record<string,string>)[v]||v}
function initial(v?:string){return (v||'—').trim().slice(0,1)}
function fmt(v?:string){return formatDateTime(v)}
function resetMetricFilter(){keyword.value='';domainFilter.value=undefined;themeFilter.value=undefined;statusFilter.value=''}
function domainChanged(){themeFilter.value=undefined}

async function load(){
  loading.value=true
  try{
    if(mode.value==='overview'){
      const [o,m,d,t]=await Promise.all([metricApi.overview(),metricApi.list(),metricApi.domains(),metricApi.themes()]);overview.value=o;rows.value=m;domains.value=d;themes.value=t
    }else if(mode.value==='manage'){
      const [m,d,t,s,ds]=await Promise.all([metricApi.list(),metricApi.domains(),metricApi.themes(),dataSourceApi.list(),metricApi.dimensions()]);rows.value=m;domains.value=d;themes.value=t;sources.value=s;dimensions.value=ds
    }else if(mode.value==='domains') domains.value=await metricApi.domains()
    else if(mode.value==='themes') {const [d,t]=await Promise.all([metricApi.domains(),metricApi.themes()]);domains.value=d;themes.value=t}
  }catch(e){ElMessage.error(e instanceof Error?e.message:'指标中心加载失败')}
  finally{loading.value=false}
}

async function openMetric(r?:MetricView){
  if(r?.status==='PENDING_APPROVAL') return ElMessage.warning('指标正在审批中，暂不能修改')
  editingId.value=r?.id||null
  Object.assign(form,r?{...r,domainId:r.domainId,themeId:r.themeId,dimensionIds:(r.dimensions||[]).map(x=>x.id),upstreamMetricCodes:[]}:{metricCode:'',metricName:'',metricType:'ATOMIC',description:'',businessDomain:'',domainId:undefined,themeId:undefined,ownerName:'',sourceDataSourceId:undefined,sourceDatabase:'',sourceTable:'',sourceField:'',aggregation:'COUNT',filterExpression:'',timeField:'',expressionText:'',dimensionIds:[],upstreamMetricCodes:[]})
  if(!form.domainId&&r?.businessDomain){const match=domains.value.find(x=>x.domainName===r.businessDomain);if(match)form.domainId=match.id}
  metricDrawer.value=true
  sourceDatabases.value=[];sourceTables.value=[];sourceColumns.value=[]
  if(form.sourceDataSourceId){await loadSourceDatabases(false);if(form.sourceDatabase){await loadSourceTables(false);if(form.sourceTable)await loadSourceColumns(false)}}
}

function validateMetric(){
  if(!form.metricCode||!form.metricName)return '请填写指标编码和名称'
  if(!form.domainId||!form.themeId)return '请选择主题域和主题'
  if(!form.ownerName)return '请填写负责人'
  if(form.metricType==='ATOMIC'&&(!form.sourceDataSourceId||!form.sourceDatabase||!form.sourceTable||!form.sourceField))return '原子指标必须选择完整的数据来源和统计字段'
  if(form.metricType!=='ATOMIC'&&!String(form.expressionText||'').trim())return '派生/复合指标必须填写计算表达式'
  return ''
}
async function persistMetric(){
  const msg=validateMetric();if(msg){ElMessage.warning(msg);return null}
  const payload={...form,businessDomain:domains.value.find(x=>x.id===form.domainId)?.domainName||form.businessDomain}
  return editingId.value?await metricApi.update(editingId.value,payload):await metricApi.create(payload)
}
async function saveDraft(){saving.value=true;try{const saved=await persistMetric();if(!saved)return;editingId.value=saved.id;ElMessage.success('指标草稿已保存');metricDrawer.value=false;await load()}catch(e){ElMessage.error(e instanceof Error?e.message:'保存失败')}finally{saving.value=false}}
async function saveAndSubmit(){
  saving.value=true
  try{
    const saved=await persistMetric();if(!saved)return
    editingId.value=saved.id
    const req=await releaseApi.request({resourceType:'METRIC',resourceId:saved.id,resourceName:saved.metricName,requestedVersion:saved.currentVersion,payload:{metricCode:saved.metricCode}})
    ElMessage.success(req.status==='PENDING_APPROVAL'?'指标已提交发布中心审批':'指标已发布')
    metricDrawer.value=false;await load()
  }catch(e){ElMessage.error(e instanceof Error?e.message:'提交审批失败')}finally{saving.value=false}
}
async function removeMetric(r:MetricView){try{await ElMessageBox.confirm(`删除指标“${r.metricName}”？`,'删除指标',{type:'warning'});await metricApi.remove(r.id);ElMessage.success('指标已删除');await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'删除失败')}}
async function openVersions(r:MetricView){versionMetric.value=r;versions.value=await metricApi.versions(r.id);versionDialog.value=true}
function versionFormula(v:MetricVersionView){try{const d=JSON.parse(v.definitionJson);return d.expressionText||`${d.aggregation||'COUNT'}(${d.sourceField||'字段'})${d.filterExpression?`\nWHERE ${d.filterExpression}`:''}`}catch{return v.definitionJson}}

function openSimple(type:'domain'|'theme',item?:MetricDomainView|MetricThemeView){
  simpleType.value=type;simpleId.value=item?.id||null
  if(type==='domain'){const d=item as MetricDomainView|undefined;Object.assign(simpleForm,d?{code:d.domainCode,name:d.domainName,description:d.description||'',ownerName:d.ownerName||'',status:d.status,sortOrder:d.sortOrder,domainId:undefined}:{code:'',name:'',description:'',ownerName:'',status:'ENABLED',sortOrder:10,domainId:undefined})}
  else{const t=item as MetricThemeView|undefined;Object.assign(simpleForm,t?{code:t.themeCode,name:t.themeName,description:t.description||'',ownerName:t.ownerName||'',status:t.status,sortOrder:t.sortOrder,domainId:t.domainId}:{code:'',name:'',description:'',ownerName:'',status:'ENABLED',sortOrder:10,domainId:selectedThemeDomain.value||enabledDomains.value[0]?.id})}
  simpleDialog.value=true
}
async function saveSimple(){
  if(!simpleForm.name||!simpleForm.code)return ElMessage.warning('请填写名称和编码')
  if(simpleType.value==='theme'&&!simpleForm.domainId)return ElMessage.warning('请选择所属主题域')
  saving.value=true
  try{
    if(simpleType.value==='domain'){
      const p={domainCode:simpleForm.code,domainName:simpleForm.name,description:simpleForm.description,ownerName:simpleForm.ownerName,status:simpleForm.status,sortOrder:simpleForm.sortOrder}
      simpleId.value?await metricApi.updateDomain(simpleId.value,p):await metricApi.createDomain(p)
    }else{
      const p={domainId:simpleForm.domainId,themeCode:simpleForm.code,themeName:simpleForm.name,description:simpleForm.description,ownerName:simpleForm.ownerName,status:simpleForm.status,sortOrder:simpleForm.sortOrder}
      simpleId.value?await metricApi.updateTheme(simpleId.value,p):await metricApi.createTheme(p)
    }
    ElMessage.success(simpleType.value==='domain'?'主题域已保存':'主题已保存');simpleDialog.value=false;await load()
  }catch(e){ElMessage.error(e instanceof Error?e.message:'保存失败')}finally{saving.value=false}
}
async function removeDomain(d:MetricDomainView){try{await ElMessageBox.confirm(`删除主题域“${d.domainName}”？`,'删除主题域',{type:'warning'});await metricApi.removeDomain(d.id);await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'删除失败')}}
async function removeTheme(t:MetricThemeView){try{await ElMessageBox.confirm(`删除主题“${t.themeName}”？`,'删除主题',{type:'warning'});await metricApi.removeTheme(t.id);await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(e instanceof Error?e.message:'删除失败')}}

async function loadSourceDatabases(clear=true){
  if(clear){form.sourceDatabase='';form.sourceTable='';form.sourceField=''};sourceDatabases.value=[];sourceTables.value=[];sourceColumns.value=[]
  if(!form.sourceDataSourceId||!selectedSource.value)return
  try{sourceDatabases.value=await metadataApi.databases(form.sourceDataSourceId,selectedSource.value.type)}catch(e){ElMessage.error(e instanceof Error?e.message:'数据库加载失败')}
}
async function loadSourceTables(clear=true){
  if(clear){form.sourceTable='';form.sourceField=''};sourceTables.value=[];sourceColumns.value=[]
  if(!form.sourceDataSourceId||!form.sourceDatabase)return
  try{sourceTables.value=await metadataApi.tables(form.sourceDataSourceId,form.sourceDatabase)}catch(e){ElMessage.error(e instanceof Error?e.message:'数据表加载失败')}
}
async function loadSourceColumns(clear=true){
  if(clear)form.sourceField='';sourceColumns.value=[]
  if(!form.sourceDataSourceId||!form.sourceDatabase||!form.sourceTable)return
  try{sourceColumns.value=await metadataApi.columns(form.sourceDataSourceId,form.sourceDatabase,form.sourceTable)}catch(e){ElMessage.error(e instanceof Error?e.message:'字段加载失败')}
}
async function createFromOverview(){await router.push('/metrics/manage');await load();await openMetric()}
function showVersionGuide(){ElMessageBox.alert('保存草稿会生成新的指标定义版本；提交发布后进入发布中心。自动审批直接发布，人工审批则进入待审批状态。审批中的版本不可继续修改。','指标版本规范',{confirmButtonText:'知道了'})}

watch(()=>route.path,load)
watch(()=>form.domainId,()=>{if(form.themeId&&!metricThemes.value.some(x=>x.id===form.themeId))form.themeId=undefined})
onMounted(load)
</script>

<template>
<div class="ds-page metric-center">
  <PageHeader :title="pageMeta[0]" :subtitle="pageMeta[1]">
    <template #actions>
      <template v-if="mode==='manage'"><el-button @click="showVersionGuide">版本规范</el-button><el-button type="primary" @click="openMetric()">+ 新建指标</el-button></template>
      <el-button v-else-if="mode==='overview'" type="primary" @click="createFromOverview">+ 新建指标</el-button>
      <el-button v-else-if="mode==='domains'" type="primary" @click="openSimple('domain')">+ 新建主题域</el-button>
      <el-button v-else-if="mode==='themes'" type="primary" @click="openSimple('theme')">+ 新建主题</el-button>
    </template>
  </PageHeader>

  <el-skeleton v-if="loading" :rows="8" animated/>

  <template v-else-if="mode==='overview'&&overview">
    <div class="metric-stats">
      <div class="stat-card"><div class="stat-icon blue">▤</div><div><span>指标总数</span><strong>{{overview.total}}</strong><small>当前平台全部业务指标</small></div></div>
      <div class="stat-card"><div class="stat-icon green">✓</div><div><span>已发布</span><strong>{{overview.published}}</strong><small>已进入生产并可被发现</small></div></div>
      <div class="stat-card"><div class="stat-icon orange">!</div><div><span>待审批</span><strong>{{overview.pendingApproval}}</strong><small>等待发布中心审批</small></div></div>
      <div class="stat-card"><div class="stat-icon purple">▥</div><div><span>草稿</span><strong>{{overview.draft}}</strong><small>尚未提交发布审批</small></div></div>
    </div>
    <div class="overview-grid">
      <section class="panel">
        <header class="panel-head"><div><b>主题域指标分布</b><span>按业务主题域统计当前指标数量</span></div><el-button link type="primary" @click="router.push('/metrics/domains')">管理主题域</el-button></header>
        <div v-if="!domains.length" class="mini-empty">暂无主题域</div>
        <div v-else class="distribution">
          <div v-for="d in domains" :key="d.id" class="dist-row"><span>{{d.domainName}}</span><div class="dist-bar"><i :style="{width:`${Math.max(4,d.metricCount/maxDomainMetrics*100)}%`}"></i></div><b>{{d.metricCount}}</b></div>
        </div>
      </section>
      <section class="panel">
        <header class="panel-head"><div><b>最近更新指标</b><span>按最近更新时间展示</span></div><el-button link type="primary" @click="router.push('/metrics/manage')">查看全部</el-button></header>
        <div v-if="!recentMetrics.length" class="mini-empty">暂无指标</div>
        <div v-else class="recent-list">
          <div v-for="r in recentMetrics" :key="r.id" class="recent-item"><div><b>{{r.metricName}}</b><span>{{r.domainName||r.businessDomain||'未分类'}} / {{r.themeName||'未归属主题'}} · {{r.ownerName||'—'}}</span></div><StatusBadge :status="r.status" :label="statusText(r.status)"/></div>
        </div>
      </section>
    </div>
  </template>

  <template v-else-if="mode==='manage'">
    <div class="metric-stats compact-stats">
      <div class="stat-card"><div class="stat-icon blue">▤</div><div><span>指标总数</span><strong>{{rows.length}}</strong><small>当前全部指标</small></div></div>
      <div class="stat-card"><div class="stat-icon green">✓</div><div><span>已发布</span><strong>{{rows.filter(x=>['PUBLISHED','CERTIFIED'].includes(x.status)).length}}</strong><small>生产可用指标</small></div></div>
      <div class="stat-card"><div class="stat-icon orange">!</div><div><span>待审批</span><strong>{{rows.filter(x=>x.status==='PENDING_APPROVAL').length}}</strong><small>等待发布中心处理</small></div></div>
      <div class="stat-card"><div class="stat-icon purple">▥</div><div><span>草稿</span><strong>{{rows.filter(x=>x.status==='DRAFT').length}}</strong><small>尚未提交审批</small></div></div>
    </div>
    <div class="panel filter-panel">
      <el-input v-model="keyword" clearable placeholder="搜索指标名称 / 编码 / 负责人" class="search-input"/>
      <el-select v-model="domainFilter" clearable placeholder="全部主题域" @change="domainChanged"><el-option v-for="d in domains" :key="d.id" :label="d.domainName" :value="d.id"/></el-select>
      <el-select v-model="themeFilter" clearable placeholder="全部主题"><el-option v-for="t in filterThemes" :key="t.id" :label="t.themeName" :value="t.id"/></el-select>
      <el-select v-model="statusFilter" clearable placeholder="全部状态"><el-option label="已发布" value="PUBLISHED"/><el-option label="待审批" value="PENDING_APPROVAL"/><el-option label="草稿" value="DRAFT"/><el-option label="已驳回" value="REJECTED"/></el-select>
      <el-button @click="resetMetricFilter">重置</el-button>
    </div>
    <div class="panel table-panel">
      <div v-if="!filteredMetrics.length" class="ds-empty"><div><div class="ds-empty__title">暂无匹配指标</div><div>可以调整筛选条件或新建指标。</div></div></div>
      <table v-else class="metric-table"><thead><tr><th>指标名称</th><th>主题域</th><th>主题</th><th>来源表</th><th>负责人</th><th>版本</th><th>状态</th><th>操作</th></tr></thead><tbody>
        <tr v-for="r in filteredMetrics" :key="r.id">
          <td><b class="metric-name">{{r.metricName}}</b><span class="metric-desc">{{r.metricCode}} · {{r.description||typeText(r.metricType)}}</span></td>
          <td>{{r.domainName||r.businessDomain||'—'}}</td><td>{{r.themeName||'—'}}</td><td>{{r.sourceTable||'—'}}</td>
          <td><span class="owner"><i>{{initial(r.ownerName)}}</i>{{r.ownerName||'—'}}</span></td><td>V{{r.currentVersion}}</td><td><StatusBadge :status="r.status" :label="statusText(r.status)"/></td>
          <td><span class="link" @click="openVersions(r)">版本</span><span class="link" :class="{disabled:r.status==='PENDING_APPROVAL'}" @click="openMetric(r)">编辑</span><el-dropdown trigger="click"><span class="link">更多</span><template #dropdown><el-dropdown-menu><el-dropdown-item :disabled="['PUBLISHED','CERTIFIED','PENDING_APPROVAL'].includes(r.status)" @click="removeMetric(r)">删除指标</el-dropdown-item></el-dropdown-menu></template></el-dropdown></td>
        </tr>
      </tbody></table>
      <div class="table-foot">共 {{filteredMetrics.length}} 条</div>
    </div>
  </template>

  <template v-else-if="mode==='domains'">
    <div class="panel filter-panel"><el-input v-model="domainKeyword" clearable placeholder="搜索主题域名称 / 编码 / 负责人" class="search-input"/><el-select v-model="domainStatus" clearable placeholder="全部状态"><el-option label="启用" value="ENABLED"/><el-option label="停用" value="DISABLED"/></el-select><el-button @click="domainKeyword='';domainStatus=''">重置</el-button></div>
    <div class="panel table-panel"><div v-if="!filteredDomains.length" class="ds-empty"><div><div class="ds-empty__title">暂无主题域</div><div>主题域可由管理员动态创建，不需要写死在代码中。</div></div></div><table v-else class="metric-table"><thead><tr><th>主题域名称</th><th>编码</th><th>主题数</th><th>指标数</th><th>负责人</th><th>状态</th><th>操作</th></tr></thead><tbody>
      <tr v-for="d in filteredDomains" :key="d.id"><td><b class="metric-name">{{d.domainName}}</b><span class="metric-desc">{{d.description||'暂无描述'}}</span></td><td>{{d.domainCode}}</td><td>{{d.themeCount}}</td><td>{{d.metricCount}}</td><td><span class="owner"><i>{{initial(d.ownerName)}}</i>{{d.ownerName||'—'}}</span></td><td><span class="config-status" :class="d.status.toLowerCase()">{{d.status==='ENABLED'?'启用':'停用'}}</span></td><td><span class="link" @click="openSimple('domain',d)">编辑</span><span class="link danger-link" @click="removeDomain(d)">删除</span></td></tr>
    </tbody></table><div class="table-foot">共 {{filteredDomains.length}} 条</div></div>
  </template>

  <template v-else-if="mode==='themes'">
    <div class="theme-layout">
      <aside class="panel domain-tree"><header><b>主题域</b><el-button link type="primary" @click="router.push('/metrics/domains')">管理</el-button></header><div class="domain-tree-item" :class="{active:!selectedThemeDomain}" @click="selectedThemeDomain=undefined"><i></i><span>全部主题</span><b>{{themes.length}}</b></div><div v-for="d in domains" :key="d.id" class="domain-tree-item" :class="{active:selectedThemeDomain===d.id}" @click="selectedThemeDomain=d.id"><i></i><span>{{d.domainName}}</span><b>{{d.themeCount}}</b></div></aside>
      <div class="theme-main"><div class="panel filter-panel"><el-input v-model="themeKeyword" clearable placeholder="搜索主题名称 / 编码 / 负责人" class="search-input"/><el-select v-model="themeStatus" clearable placeholder="全部状态"><el-option label="启用" value="ENABLED"/><el-option label="停用" value="DISABLED"/></el-select><el-button @click="themeKeyword='';themeStatus=''">重置</el-button></div>
        <div class="panel table-panel"><div v-if="!filteredThemes.length" class="ds-empty"><div><div class="ds-empty__title">暂无主题</div><div>请在所选主题域下创建主题。</div></div></div><table v-else class="metric-table"><thead><tr><th>主题名称</th><th>主题编码</th><th>所属主题域</th><th>指标数</th><th>负责人</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="t in filteredThemes" :key="t.id"><td><b class="metric-name">{{t.themeName}}</b><span class="metric-desc">{{t.description||'暂无描述'}}</span></td><td>{{t.themeCode}}</td><td>{{t.domainName}}</td><td>{{t.metricCount}}</td><td><span class="owner"><i>{{initial(t.ownerName)}}</i>{{t.ownerName||'—'}}</span></td><td><span class="config-status" :class="t.status.toLowerCase()">{{t.status==='ENABLED'?'启用':'停用'}}</span></td><td><span class="link" @click="openSimple('theme',t)">编辑</span><span class="link danger-link" @click="removeTheme(t)">删除</span></td></tr>
        </tbody></table><div class="table-foot">共 {{filteredThemes.length}} 条</div></div>
      </div>
    </div>
  </template>

  <el-drawer v-model="metricDrawer" :title="editingId?'编辑指标':'新建指标'" size="760px" class="metric-drawer" destroy-on-close>
    <div class="form-section"><h3>基本信息</h3><div class="form-grid">
      <el-form-item label="指标名称 *"><el-input v-model="form.metricName" placeholder="例如：支付订单数"/></el-form-item><el-form-item label="指标编码 *"><el-input v-model="form.metricCode" placeholder="例如：pay_order_count"/></el-form-item>
      <el-form-item label="主题域 *"><el-select v-model="form.domainId" style="width:100%" placeholder="请选择主题域"><el-option v-for="d in enabledDomains" :key="d.id" :label="d.domainName" :value="d.id"/></el-select></el-form-item><el-form-item label="主题 *"><el-select v-model="form.themeId" style="width:100%" placeholder="请选择主题"><el-option v-for="t in metricThemes" :key="t.id" :label="t.themeName" :value="t.id"/></el-select></el-form-item>
      <el-form-item label="指标类型 *"><el-select v-model="form.metricType" style="width:100%"><el-option label="原子指标" value="ATOMIC"/><el-option label="派生指标" value="DERIVED"/><el-option label="复合指标" value="COMPOSITE"/></el-select></el-form-item><el-form-item label="负责人 *"><el-input v-model="form.ownerName" placeholder="请输入负责人"/></el-form-item>
      <el-form-item class="full" label="业务定义 *"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="说明该指标在业务上的含义和统计范围"/></el-form-item>
    </div></div>
    <div class="form-section"><h3>数据来源</h3><div class="form-grid">
      <el-form-item label="数据源"><el-select v-model="form.sourceDataSourceId" clearable style="width:100%" @change="loadSourceDatabases(true)"><el-option v-for="s in sources" :key="s.id" :label="`${s.name} · ${s.type}`" :value="s.id"/></el-select></el-form-item><el-form-item label="数据库"><el-select v-model="form.sourceDatabase" filterable clearable style="width:100%" @change="loadSourceTables(true)"><el-option v-for="d in sourceDatabases" :key="d.name" :label="d.name" :value="d.name"/></el-select></el-form-item>
      <el-form-item label="来源表"><el-select v-model="form.sourceTable" filterable clearable style="width:100%" @change="loadSourceColumns(true)"><el-option v-for="t in sourceTables" :key="t.name" :label="t.comment?`${t.name} · ${t.comment}`:t.name" :value="t.name"/></el-select></el-form-item><el-form-item label="统计字段"><el-select v-model="form.sourceField" filterable clearable style="width:100%"><el-option v-for="c in sourceColumns" :key="c.name" :label="c.comment?`${c.name} · ${c.comment}`:c.name" :value="c.name"/></el-select></el-form-item>
      <div class="source-hint full">当前指标直接绑定物理数据源、数据库、表和字段，不额外引入数据集概念。</div>
    </div></div>
    <div class="form-section"><h3>计算口径</h3><div class="form-grid">
      <template v-if="form.metricType==='ATOMIC'"><el-form-item label="聚合方式 *"><el-select v-model="form.aggregation" style="width:100%"><el-option v-for="a in ['COUNT','SUM','AVG','MAX','MIN','COUNT DISTINCT']" :key="a" :label="a" :value="a"/></el-select></el-form-item><el-form-item label="时间字段"><el-select v-model="form.timeField" filterable clearable style="width:100%"><el-option v-for="c in sourceColumns" :key="c.name" :label="c.comment?`${c.name} · ${c.comment}`:c.name" :value="c.name"/></el-select></el-form-item><el-form-item class="full" label="过滤条件"><el-input v-model="form.filterExpression" type="textarea" :rows="2" placeholder="例如：status IN ('PAID','FINISHED')"/></el-form-item></template>
      <el-form-item v-else class="full" label="计算表达式 *"><el-input v-model="form.expressionText" type="textarea" :rows="3" placeholder="例如：pay_order_count / order_count"/></el-form-item>
      <el-form-item class="full" label="分析维度"><el-select v-model="form.dimensionIds" multiple clearable style="width:100%" placeholder="可选公共维度"><el-option v-for="d in dimensions" :key="d.id" :label="d.dimensionName" :value="d.id"/></el-select></el-form-item>
      <div class="full"><label class="preview-label">口径预览</label><pre class="formula-preview">{{formulaPreview}}</pre></div>
    </div></div>
    <template #footer><div class="drawer-footer"><el-button @click="metricDrawer=false">取消</el-button><el-button :loading="saving" @click="saveDraft">保存草稿</el-button><el-button type="primary" :loading="saving" @click="saveAndSubmit">保存并提交审批</el-button></div></template>
  </el-drawer>

  <el-dialog v-model="simpleDialog" :title="`${simpleId?'编辑':'新建'}${simpleType==='domain'?'主题域':'主题'}`" width="620px" destroy-on-close>
    <el-form label-position="top"><div class="dialog-grid"><el-form-item :label="`${simpleType==='domain'?'主题域':'主题'}名称 *`"><el-input v-model="simpleForm.name"/></el-form-item><el-form-item label="编码 *"><el-input v-model="simpleForm.code" placeholder="建议使用英文编码"/></el-form-item><el-form-item v-if="simpleType==='theme'" label="所属主题域 *"><el-select v-model="simpleForm.domainId" style="width:100%"><el-option v-for="d in enabledDomains" :key="d.id" :label="d.domainName" :value="d.id"/></el-select></el-form-item><el-form-item label="负责人"><el-input v-model="simpleForm.ownerName"/></el-form-item><el-form-item label="状态"><el-select v-model="simpleForm.status" style="width:100%"><el-option label="启用" value="ENABLED"/><el-option label="停用" value="DISABLED"/></el-select></el-form-item><el-form-item label="排序"><el-input-number v-model="simpleForm.sortOrder" :min="0" :max="999" style="width:100%"/></el-form-item><el-form-item class="full" label="描述"><el-input v-model="simpleForm.description" type="textarea" :rows="3"/></el-form-item></div></el-form>
    <template #footer><el-button @click="simpleDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSimple">确定</el-button></template>
  </el-dialog>

  <el-dialog v-model="versionDialog" width="720px" destroy-on-close><template #header><div><b class="version-title">指标版本记录</b><span class="version-sub">{{versionMetric?.metricName}} · {{versionMetric?.metricCode}}</span></div></template><div v-if="!versions.length" class="mini-empty">暂无版本记录</div><div v-else class="version-list"><div v-for="(v,i) in versions" :key="v.id" class="version-row"><div class="version-top"><div><b>V{{v.versionNo}}</b><span v-if="i===0" class="current-version">当前版本</span></div><span>{{fmt(v.createdAt)}} · {{v.createdBy}}</span></div><pre>{{versionFormula(v)}}</pre></div></div></el-dialog>
</div>
</template>

<style scoped>
.metric-center{padding-top:28px;padding-bottom:36px;background:linear-gradient(180deg,#fbfdff 0%,#f8fbff 100%)}
.panel{border:1px solid #e4ebf5;border-radius:14px;background:#fff;box-shadow:0 8px 26px rgba(42,83,163,.035);overflow:hidden}
.metric-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:14px}.stat-card{min-height:108px;padding:18px;display:flex;gap:14px;align-items:flex-start;border:1px solid #e4ebf5;border-radius:14px;background:#fff;box-shadow:0 8px 24px rgba(42,83,163,.035)}.stat-icon{width:40px;height:40px;border-radius:10px;display:grid;place-items:center;font-weight:800;flex:0 0 auto}.stat-icon.blue{background:#edf4ff;color:#2f6bff}.stat-icon.green{background:#ecfdf3;color:#16a34a}.stat-icon.orange{background:#fff7e6;color:#e59a00}.stat-icon.purple{background:#f3efff;color:#7c3aed}.stat-card span{display:block;color:#66788f;font-size:12px}.stat-card strong{display:block;margin-top:6px;color:#102847;font-size:27px;line-height:1}.stat-card small{display:block;margin-top:9px;color:#98a5b6;font-size:11px}.overview-grid{display:grid;grid-template-columns:1.2fr 1fr;gap:14px}.panel-head{height:54px;padding:0 16px;border-bottom:1px solid #edf2f7;display:flex;align-items:center;justify-content:space-between}.panel-head div{display:flex;flex-direction:column;gap:4px}.panel-head b{color:#17304f;font-size:13px}.panel-head span{color:#95a3b6;font-size:11px}.distribution{padding:10px 16px 16px}.dist-row{height:44px;display:grid;grid-template-columns:100px 1fr 42px;align-items:center;gap:12px;color:#53667d;font-size:12px}.dist-bar{height:7px;border-radius:99px;background:#eef3f8;overflow:hidden}.dist-bar i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#8bb0ff,#3b82f6)}.dist-row b{text-align:right;color:#66788f}.recent-list{padding:3px 16px 9px}.recent-item{min-height:58px;border-bottom:1px solid #eff3f7;display:flex;align-items:center;justify-content:space-between}.recent-item:last-child{border-bottom:0}.recent-item>div{display:flex;flex-direction:column;gap:4px}.recent-item b{font-size:12px;color:#304965}.recent-item span{font-size:11px;color:#97a4b5}.mini-empty{padding:54px 20px;text-align:center;color:#98a5b6;font-size:12px}
.filter-panel{min-height:62px;padding:12px 14px;display:flex;gap:10px;align-items:center;margin-bottom:14px}.filter-panel .search-input{width:min(360px,36%)}.filter-panel :deep(.el-select){width:156px}.filter-panel :deep(.el-input__wrapper),.filter-panel :deep(.el-select__wrapper){min-height:36px;border-radius:8px;box-shadow:0 0 0 1px #dfe7f2 inset}.table-panel{overflow:hidden}.metric-table{width:100%;border-collapse:collapse;table-layout:fixed}.metric-table th{height:44px;padding:0 14px;background:#fafcff;border-bottom:1px solid #e9eef5;color:#74859c;font-size:11px;text-align:left;font-weight:650}.metric-table td{height:62px;padding:9px 14px;border-bottom:1px solid #eef2f6;color:#4a5f78;font-size:12px;vertical-align:middle}.metric-table tbody tr:hover{background:#f9fbff}.metric-table tbody tr:last-child td{border-bottom:0}.metric-name{display:block;color:#2468d8;font-size:12px;font-weight:650;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.metric-desc{display:block;margin-top:4px;color:#98a5b5;font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.owner{display:flex;align-items:center;gap:7px}.owner i{width:26px;height:26px;border-radius:50%;display:grid;place-items:center;background:#eef3f8;color:#60738a;font-style:normal;font-size:11px;font-weight:650}.link{display:inline-flex;margin-right:10px;color:#2f6fed;cursor:pointer;font-weight:550}.link:hover{color:#1f5dcc}.link.disabled{color:#aab5c4;cursor:not-allowed}.danger-link{color:#d34a48}.table-foot{height:48px;padding:0 14px;border-top:1px solid #edf2f7;display:flex;align-items:center;color:#8a99ad;font-size:11px}.config-status{display:inline-flex;height:24px;padding:0 9px;border-radius:999px;align-items:center;font-size:11px}.config-status.enabled{background:#ecfdf3;color:#16824f}.config-status.disabled{background:#f2f4f7;color:#7d8999}
.theme-layout{display:grid;grid-template-columns:270px minmax(0,1fr);gap:14px}.domain-tree{padding:12px;min-height:560px}.domain-tree header{height:42px;padding:0 5px 8px;display:flex;align-items:center;justify-content:space-between;color:#304965}.domain-tree-item{height:40px;padding:0 10px;border-radius:8px;display:flex;align-items:center;gap:9px;color:#586b82;cursor:pointer;margin-bottom:3px}.domain-tree-item:hover{background:#f7faff}.domain-tree-item.active{background:#edf4ff;color:#2468d8;font-weight:650}.domain-tree-item i{width:8px;height:8px;border-radius:50%;background:#ccd6e5}.domain-tree-item.active i{background:#3b82f6}.domain-tree-item b{margin-left:auto;color:#97a5b7;font-size:11px}.theme-main{min-width:0}.theme-main .filter-panel{margin-top:0}
.form-section{margin-bottom:14px;border:1px solid #e4ebf4;border-radius:11px;overflow:hidden;background:#fff}.form-section h3{height:43px;margin:0;padding:0 14px;display:flex;align-items:center;border-bottom:1px solid #e9eef5;background:#fafcff;color:#344b65;font-size:12px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px;padding:14px}.form-grid .full,.dialog-grid .full{grid-column:1/-1}.source-hint{margin:-4px 0 2px;padding:9px 10px;border-radius:8px;background:#f6f9fd;color:#8695a8;font-size:11px}.preview-label{display:block;margin-bottom:7px;color:#52667e;font-size:12px}.formula-preview{margin:0;padding:12px 14px;border-radius:9px;background:#0f1d31;color:#d9e8ff;font:12px/1.7 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;white-space:pre-wrap}.drawer-footer{display:flex;justify-content:flex-end;gap:8px}.dialog-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.version-title{display:block;color:#233b58;font-size:15px}.version-sub{display:block;margin-top:4px;color:#94a1b3;font-size:11px}.version-list{display:flex;flex-direction:column;gap:10px}.version-row{padding:13px;border:1px solid #e4eaf3;border-radius:10px}.version-top{display:flex;justify-content:space-between;align-items:center;margin-bottom:9px}.version-top b{color:#2468d8}.version-top>span{color:#98a5b6;font-size:11px}.current-version{margin-left:7px;padding:2px 7px;border-radius:999px;background:#ecfdf3;color:#16824f;font-size:10px}.version-row pre{margin:0;padding:10px 12px;border-radius:8px;background:#f7f9fc;color:#51647c;font:11px/1.65 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;white-space:pre-wrap}
:deep(.metric-drawer .el-drawer__header){height:62px;margin:0;padding:0 20px;border-bottom:1px solid #e9eef5;color:#17304f;font-weight:700}:deep(.metric-drawer .el-drawer__body){padding:16px 20px;background:#fbfcfe}:deep(.metric-drawer .el-drawer__footer){height:64px;padding:0 20px;border-top:1px solid #e9eef5;background:#fff}:deep(.form-section .el-form-item){margin-bottom:14px}:deep(.form-section .el-form-item__label),:deep(.dialog-grid .el-form-item__label){color:#52667e;font-size:11px;font-weight:600}:deep(.el-dialog){border-radius:14px;overflow:hidden}:deep(.el-dialog__header){margin:0;padding:18px 20px;border-bottom:1px solid #edf1f6}:deep(.el-dialog__body){padding:18px 20px}:deep(.el-dialog__footer){padding:14px 20px;border-top:1px solid #edf1f6}
@media(max-width:1200px){.metric-stats{grid-template-columns:repeat(2,1fr)}.overview-grid{grid-template-columns:1fr}.theme-layout{grid-template-columns:230px minmax(0,1fr)}}
@media(max-width:900px){.theme-layout{grid-template-columns:1fr}.domain-tree{min-height:auto}.filter-panel{flex-wrap:wrap}.filter-panel .search-input{width:100%}}
</style>
