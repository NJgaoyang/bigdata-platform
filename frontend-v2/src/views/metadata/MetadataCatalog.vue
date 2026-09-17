<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dataSourceApi } from '../../api/platform'
import { metadataApi, type ColumnView, type DataSourceView, type DatabaseView, type LineageView, type TablePreview, type TableProfile, type TableView } from '../../api/domain'
import { formatDateTime } from '../../utils/display'

const sources=ref<DataSourceView[]>([]), databases=ref<DatabaseView[]>([]), tables=ref<TableView[]>([])
const columns=ref<ColumnView[]>([]), profile=ref<TableProfile>(), lineage=ref<LineageView[]>([]), preview=ref<TablePreview>()
const sourceId=ref<number>(), database=ref(''), table=ref<TableView>()
const databaseKeyword=ref(''), tableKeyword=ref('')
const activeTab=ref<'overview'|'fields'|'preview'|'lineage'>('overview'), previewLimit=ref(10)
const loading=ref(false), tableLoading=ref(false), detailLoading=ref(false), previewLoading=ref(false)
const error=ref(''), previewError=ref(''), drawerVisible=ref(false)
const connectionState=ref<'idle'|'ok'|'bad'>('idle')

const source=computed(()=>sources.value.find(item=>item.id===sourceId.value))
const currentDatabase=computed(()=>databases.value.find(item=>item.name===database.value))
const filteredDatabases=computed(()=>{
  const q=databaseKeyword.value.trim().toLowerCase()
  return q?databases.value.filter(item=>`${item.name} ${item.comment||''}`.toLowerCase().includes(q)):databases.value
})
const filteredTables=computed(()=>{
  const q=tableKeyword.value.trim().toLowerCase()
  return q?tables.value.filter(item=>`${item.name} ${item.comment||''} ${item.type||''}`.toLowerCase().includes(q)):tables.value
})
function fmtSize(v?:number){if(v==null)return '—';if(v>=1024**3)return `${(v/1024**3).toFixed(2)} GB`;if(v>=1024**2)return `${(v/1024**2).toFixed(1)} MB`;if(v>=1024)return `${(v/1024).toFixed(1)} KB`;return `${v} B`}
function fmtTime(v?:string){return formatDateTime(v)}

async function loadSources(){
  error.value='';loading.value=true
  try{
    sources.value=(await dataSourceApi.list()).filter(item=>item.metadataVisible)
    const saved=Number(localStorage.getItem('metadataDataSourceId')||0)
    const first=sources.value.find(item=>item.id===saved)||sources.value.find(item=>item.type==='STARROCKS')||sources.value[0]
    if(first)await selectSource(first.id)
  }catch(e){error.value=e instanceof Error?e.message:'数据源加载失败'}finally{loading.value=false}
}
async function selectSource(id:number){
  sourceId.value=id;localStorage.setItem('metadataDataSourceId',String(id));connectionState.value='idle'
  database.value='';table.value=undefined;tables.value=[];databases.value=[];drawerVisible.value=false
  try{
    const selected=sources.value.find(item=>item.id===id)
    databases.value=await metadataApi.databases(id,selected?.type||'STARROCKS')
    const saved=localStorage.getItem('metadataDatabase')||''
    const first=databases.value.find(item=>item.name===saved)||databases.value[0]
    if(first)await selectDatabase(first.name)
  }catch(e){error.value=e instanceof Error?e.message:'数据库加载失败'}
}
async function selectDatabase(name:string){
  if(!sourceId.value)return
  database.value=name;localStorage.setItem('metadataDatabase',name);table.value=undefined;drawerVisible.value=false;tableKeyword.value=''
  tableLoading.value=true;error.value=''
  try{tables.value=await metadataApi.tables(sourceId.value,name)}
  catch(e){tables.value=[];error.value=e instanceof Error?e.message:'数据表加载失败'}finally{tableLoading.value=false}
}
async function openTable(value:TableView){
  if(!sourceId.value||!database.value)return
  table.value=value;drawerVisible.value=true;activeTab.value='overview';preview.value=undefined;previewError.value='';detailLoading.value=true
  try{
    const [cols,prof,lin]=await Promise.allSettled([
      metadataApi.columns(sourceId.value,database.value,value.name),
      metadataApi.profile(sourceId.value,database.value,value.name),
      metadataApi.lineage(`${database.value}.${value.name}`)
    ])
    columns.value=cols.status==='fulfilled'?cols.value:[]
    profile.value=prof.status==='fulfilled'?prof.value:undefined
    lineage.value=lin.status==='fulfilled'?lin.value:[]
  }finally{detailLoading.value=false}
}
async function switchTab(tab:'overview'|'fields'|'preview'|'lineage'){
  activeTab.value=tab
  if(tab==='preview'&&!preview.value&&!previewLoading.value)await loadPreview()
}
async function loadPreview(){
  if(!sourceId.value||!table.value)return
  previewLoading.value=true;previewError.value=''
  try{preview.value=await metadataApi.preview(sourceId.value,database.value,table.value.name,previewLimit.value)}
  catch(e){preview.value=undefined;previewError.value=e instanceof Error?e.message:'数据预览失败'}finally{previewLoading.value=false}
}
async function testConnection(){
  if(!sourceId.value)return
  try{await dataSourceApi.test(sourceId.value);connectionState.value='ok';ElMessage.success('连接正常')}
  catch(e){connectionState.value='bad';ElMessage.error(e instanceof Error?e.message:'连接失败')}
}
async function refreshMetadata(){
  if(!sourceId.value)return
  const keep=database.value
  await selectSource(sourceId.value)
  if(keep&&databases.value.some(item=>item.name===keep))await selectDatabase(keep)
  ElMessage.success('元数据已刷新')
}
async function editOwner(){
  if(!profile.value?.ownerEditable||!sourceId.value||!table.value)return
  try{
    const {value}=await ElMessageBox.prompt('请输入数据表负责人','设置负责人',{inputValue:profile.value.owner||'',confirmButtonText:'保存',cancelButtonText:'取消'})
    profile.value=await metadataApi.updateOwner(sourceId.value,database.value,table.value.name,value||'')
    ElMessage.success('负责人已更新')
  }catch{}
}
onMounted(loadSources)
</script>

<template>
  <div class="metadata-page" v-loading="loading">
    <header class="page-toolbar">
      <div class="page-title"><strong>元数据</strong><span>浏览数据源中的数据库和数据表</span></div>
      <div class="toolbar-spacer" />
      <span :class="['connection-state',connectionState]"><i />{{connectionState==='ok'?'连接正常':connectionState==='bad'?'连接失败':'未检测'}}</span>
      <el-select v-model="sourceId" class="source-select" placeholder="选择数据源" @change="selectSource">
        <el-option v-for="item in sources" :key="item.id" :label="`${item.name} (${item.type})`" :value="item.id" />
      </el-select>
      <el-button :disabled="!sourceId" @click="testConnection">检测连接</el-button>
      <el-button type="primary" :disabled="!sourceId" @click="refreshMetadata">刷新元数据</el-button>
    </header>

    <div v-if="error" class="ds-error">{{error}}</div>

    <section class="governance-shell">
      <aside class="database-panel">
        <div class="source-summary" v-if="source">
          <div class="source-mark"><span></span></div>
          <div class="source-summary-copy">
            <div class="source-summary-title"><strong>{{source.name}}</strong><span>{{source.type}}</span></div>
            <small>{{databases.length}} 个数据库 · {{connectionState==='ok'?'连接正常':connectionState==='bad'?'连接异常':'元数据目录'}}</small>
          </div>
        </div>
        <div class="panel-head"><div><strong>数据库目录</strong><span>{{databases.length}}</span></div></div>
        <div class="panel-search"><el-input v-model="databaseKeyword" placeholder="搜索数据库" clearable /></div>
        <div class="database-list">
          <button v-for="item in filteredDatabases" :key="item.name" :class="['database-row',{active:database===item.name}]" @click="selectDatabase(item.name)">
            <span class="database-icon"><i/><i/></span>
            <span class="database-copy"><strong>{{item.name}}</strong><small>{{item.comment||'数据库'}}</small></span>
            <span class="database-arrow">›</span>
          </button>
          <div v-if="!filteredDatabases.length" class="empty-small">暂无数据库</div>
        </div>
      </aside>

      <main class="table-panel">
        <template v-if="database">
          <div class="table-panel-head">
            <div class="db-title"><strong>{{database}}</strong><span>{{source?.name}} / {{source?.type}}</span></div>
            <div class="table-count">共 {{filteredTables.length}} 张表</div>
          </div>
          <div class="table-toolbar">
            <el-input v-model="tableKeyword" placeholder="搜索表名或描述" clearable class="table-search" />
          </div>
          <div class="table-content" v-loading="tableLoading">
            <el-table :data="filteredTables" height="100%" row-class-name="metadata-table-row" @row-click="openTable">
              <el-table-column prop="name" label="表名" min-width="280">
                <template #default="scope"><button class="table-name" @click.stop="openTable(scope.row)">{{scope.row.name}}</button></template>
              </el-table-column>
              <el-table-column prop="type" label="类型" width="150"><template #default="scope"><span class="type-text">{{scope.row.type||'TABLE'}}</span></template></el-table-column>
              <el-table-column prop="comment" label="描述" min-width="360" show-overflow-tooltip><template #default="scope">{{scope.row.comment||'—'}}</template></el-table-column>
              <el-table-column label="操作" width="110" fixed="right"><template #default="scope"><el-button link type="primary" @click.stop="openTable(scope.row)">查看详情</el-button></template></el-table-column>
              <template #empty><div class="table-empty">当前数据库暂无数据表</div></template>
            </el-table>
          </div>
        </template>
        <div v-else class="main-empty"><strong>请选择数据库</strong><span>从左侧选择数据库后查看数据表</span></div>
      </main>
    </section>

    <el-drawer v-model="drawerVisible" size="760px" :with-header="false" class="metadata-drawer">
      <div v-if="table" class="drawer-shell" v-loading="detailLoading">
        <div class="drawer-head">
          <div><div class="drawer-path">{{source?.name}} / {{database}}</div><h2>{{table.name}}</h2><p>{{table.comment||'暂无表描述'}}</p></div>
          <el-button text @click="drawerVisible=false">关闭</el-button>
        </div>
        <div class="drawer-tabs">
          <button :class="{active:activeTab==='overview'}" @click="switchTab('overview')">概览</button>
          <button :class="{active:activeTab==='fields'}" @click="switchTab('fields')">字段</button>
          <button :class="{active:activeTab==='preview'}" @click="switchTab('preview')">数据预览</button>
          <button :class="{active:activeTab==='lineage'}" @click="switchTab('lineage')">血缘</button>
        </div>
        <div class="drawer-body">
          <section v-show="activeTab==='overview'" class="overview-panel">
            <div class="overview-grid">
              <div><label>数据源</label><strong>{{source?.name||'—'}}</strong></div><div><label>数据库</label><strong>{{database}}</strong></div>
              <div><label>类型</label><strong>{{table.type||'TABLE'}}</strong></div><div><label>字段数</label><strong>{{columns.length}}</strong></div>
              <div><label>负责人</label><div class="owner-value"><strong>{{profile?.owner||'未设置'}}</strong><el-button v-if="profile?.ownerEditable" link type="primary" @click="editOwner">编辑</el-button></div></div>
              <div><label>行数</label><strong>{{profile?.rowCount?.toLocaleString()||'—'}}</strong></div>
              <div><label>估算大小</label><strong>{{fmtSize(profile?.estimatedSizeBytes)}}</strong></div><div><label>更新时间</label><strong>{{fmtTime(profile?.updateTime)}}</strong></div>
            </div>
            <div class="info-section"><div class="section-title">连接信息</div><dl><dt>地址</dt><dd>{{source?.host}}:{{source?.port}}</dd><dt>数据库</dt><dd>{{database}}</dd><dt>数据表</dt><dd>{{table.name}}</dd><dt>建表时间</dt><dd>{{fmtTime(profile?.createTime)}}</dd></dl></div>
          </section>

          <section v-show="activeTab==='fields'" class="drawer-table-section">
            <el-table :data="columns" height="calc(100vh - 190px)"><el-table-column prop="name" label="字段" min-width="210"/><el-table-column prop="dataType" label="类型" width="170"/><el-table-column prop="nullable" label="可空" width="90"><template #default="scope">{{scope.row.nullable===false?'否':'是'}}</template></el-table-column><el-table-column prop="comment" label="说明" min-width="240" show-overflow-tooltip/></el-table>
          </section>

          <section v-show="activeTab==='preview'" class="drawer-table-section">
            <div class="preview-toolbar"><span>仅抽样预览</span><el-select v-model="previewLimit" style="width:100px" @change="loadPreview"><el-option :value="10" label="10 行"/><el-option :value="50" label="50 行"/><el-option :value="100" label="100 行"/><el-option :value="500" label="500 行"/><el-option :value="1000" label="1000 行"/></el-select><el-button @click="loadPreview">刷新</el-button></div>
            <div v-if="previewError" class="preview-error">{{previewError}}</div>
            <div v-else class="preview-table-shell" v-loading="previewLoading"><table v-if="preview?.columns.length" class="preview-table"><thead><tr><th v-for="c in preview.columns" :key="c">{{c}}</th></tr></thead><tbody><tr v-for="(r,ri) in preview.rows" :key="ri"><td v-for="(c,ci) in preview.columns" :key="c">{{r[ci]??'NULL'}}</td></tr></tbody></table><div v-else-if="!previewLoading" class="table-empty">暂无预览数据</div></div>
          </section>

          <section v-show="activeTab==='lineage'" class="drawer-table-section"><el-table v-if="lineage.length" :data="lineage"><el-table-column prop="sourceTable" label="上游" min-width="250"/><el-table-column label="关系" width="80"><template #default>→</template></el-table-column><el-table-column prop="targetTable" label="下游" min-width="250"/><el-table-column prop="relationType" label="类型" width="130"/></el-table><div v-else class="table-empty">暂无已解析血缘</div></section>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.metadata-page{height:calc(100vh - var(--ds-topbar));padding:16px 18px 18px;background:#f5f6f7;display:flex;flex-direction:column;gap:12px;overflow:hidden}.page-toolbar{height:48px;flex:0 0 48px;display:flex;align-items:center;gap:10px}.page-title{display:flex;align-items:baseline;gap:10px}.page-title strong{font-size:18px;color:#252b3a}.page-title span{font-size:12px;color:#8a8e99}.toolbar-spacer{flex:1}.source-select{width:250px}.connection-state{height:24px;padding:0 8px;display:inline-flex;align-items:center;gap:6px;border:1px solid #dfe1e6;background:#fff;color:#7a7f8a;font-size:11px}.connection-state i{width:6px;height:6px;border-radius:50%;background:#c4c7ce}.connection-state.ok{color:#278a57;border-color:#b7e3ca;background:#f2fbf5}.connection-state.ok i{background:#36a269}.connection-state.bad{color:#c84b4b;border-color:#efc2c2;background:#fff6f6}.connection-state.bad i{background:#e05b5b}.governance-shell{flex:1;min-height:0;display:grid;grid-template-columns:260px minmax(0,1fr);background:#fff;border:1px solid #dfe1e6;overflow:hidden}.database-panel{min-height:0;display:flex;flex-direction:column;border-right:1px solid #dfe1e6;background:#fafafa}.panel-head{height:46px;flex:0 0 46px;padding:0 14px;display:flex;align-items:center;border-bottom:1px solid #e8eaed;background:#fff}.panel-head>div{display:flex;align-items:center;gap:8px}.panel-head strong{font-size:14px;color:#252b3a}.panel-head span{font-size:11px;color:#9aa0aa}.panel-search{padding:10px;border-bottom:1px solid #e8eaed;background:#fff}.database-list{flex:1;min-height:0;overflow:auto;padding:6px 0}.database-row{position:relative;width:100%;min-height:40px;padding:6px 12px 6px 14px;border:0;background:transparent;display:flex;align-items:center;gap:9px;text-align:left;cursor:pointer}.database-row:hover{background:#f1f3f5}.database-row.active{background:#e9f2ff;color:#1f5fbf}.database-row.active:before{content:'';position:absolute;left:0;top:0;bottom:0;width:3px;background:#2d78d4}.database-icon{width:16px;height:13px;border:1px solid #7b8796;border-radius:50%;position:relative;flex:0 0 16px}.database-icon:before,.database-icon:after{content:'';position:absolute;left:-1px;width:14px;height:5px;border-left:1px solid #7b8796;border-right:1px solid #7b8796;border-bottom:1px solid #7b8796;border-radius:0 0 50% 50%}.database-icon:before{top:3px}.database-icon:after{top:7px}.database-row.active .database-icon,.database-row.active .database-icon:before,.database-row.active .database-icon:after{border-color:#2d78d4}.database-copy{min-width:0;display:flex;flex-direction:column}.database-copy strong{font-size:12px;font-weight:600;color:#3b4250;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.database-row.active .database-copy strong{color:#1f5fbf}.database-copy small{margin-top:2px;font-size:10px;color:#9aa0aa;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.table-panel{min-width:0;min-height:0;display:flex;flex-direction:column;background:#fff}.table-panel-head{height:62px;flex:0 0 62px;padding:0 18px;display:flex;align-items:center;border-bottom:1px solid #e8eaed}.db-title{min-width:0;display:flex;flex-direction:column}.db-title strong{font-size:16px;color:#252b3a}.db-title span{margin-top:4px;font-size:11px;color:#8a8e99}.table-count{margin-left:auto;font-size:12px;color:#8a8e99}.table-toolbar{height:52px;flex:0 0 52px;padding:9px 14px;display:flex;align-items:center;border-bottom:1px solid #eef0f2}.table-search{width:280px}.table-content{flex:1;min-height:0;padding:0 14px 14px}.table-name{padding:0;border:0;background:transparent;color:#1f5fbf;font-size:12px;cursor:pointer}.type-text{color:#5f6673;font-size:11px}.table-empty,.empty-small{padding:36px 16px;text-align:center;color:#9aa0aa;font-size:12px}.main-empty{margin:auto;display:flex;flex-direction:column;align-items:center;gap:8px;color:#9aa0aa}.main-empty strong{font-size:15px;color:#5f6673}.drawer-shell{height:100%;display:flex;flex-direction:column}.drawer-head{min-height:108px;padding:22px 24px 16px;border-bottom:1px solid #e8eaed;display:flex;gap:12px}.drawer-head>div:first-child{min-width:0;flex:1}.drawer-path{font-size:11px;color:#8a8e99}.drawer-head h2{margin:7px 0 5px;font-size:20px;color:#252b3a}.drawer-head p{margin:0;color:#7a7f8a;font-size:12px}.drawer-tabs{height:44px;flex:0 0 44px;display:flex;gap:24px;padding:0 24px;border-bottom:1px solid #e8eaed}.drawer-tabs button{position:relative;border:0;background:transparent;color:#5f6673;cursor:pointer}.drawer-tabs button.active{color:#1f5fbf;font-weight:600}.drawer-tabs button.active:after{content:'';position:absolute;left:0;right:0;bottom:0;height:2px;background:#2d78d4}.drawer-body{flex:1;min-height:0;overflow:auto}.overview-panel{padding:20px 24px}.overview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border-top:1px solid #e8eaed;border-left:1px solid #e8eaed}.overview-grid>div{min-height:66px;padding:10px 12px;border-right:1px solid #e8eaed;border-bottom:1px solid #e8eaed}.overview-grid label,.overview-grid strong{display:block}.overview-grid label{font-size:11px;color:#8a8e99}.overview-grid strong{margin-top:7px;font-size:12px;color:#3b4250}.owner-value{display:flex;align-items:center;gap:8px}.owner-value strong{margin-top:7px}.info-section{margin-top:18px;border:1px solid #e8eaed}.section-title{height:38px;padding:0 12px;display:flex;align-items:center;background:#f7f8fa;border-bottom:1px solid #e8eaed;font-size:12px;font-weight:600;color:#3b4250}.info-section dl{display:grid;grid-template-columns:110px 1fr 110px 1fr;margin:0}.info-section dt,.info-section dd{margin:0;padding:10px 12px;border-bottom:1px solid #eef0f2;font-size:11px}.info-section dt{background:#fafafa;color:#8a8e99}.info-section dd{color:#4e5562}.drawer-table-section{padding:14px 20px}.preview-toolbar{height:42px;display:flex;align-items:center;justify-content:flex-end;gap:8px;color:#8a8e99;font-size:11px}.preview-toolbar span{margin-right:auto}.preview-error{padding:22px;border:1px solid #edc7c7;background:#fff7f7;color:#b54b4b;text-align:center}.preview-table-shell{overflow:auto;border:1px solid #e8eaed}.preview-table{min-width:100%;border-collapse:collapse;font-size:11px}.preview-table th,.preview-table td{height:38px;padding:0 10px;border-bottom:1px solid #eef0f2;white-space:nowrap;text-align:left}.preview-table th{background:#f7f8fa;color:#5f6673;font-weight:600}.preview-table td{color:#4e5562}@media(max-width:1280px){.governance-shell{grid-template-columns:230px minmax(0,1fr)}.source-select{width:220px}}


/* DataSphere light product theme — visual overrides only */
.metadata-page{padding:24px 26px 28px;gap:14px;background:linear-gradient(180deg,#fbfdff 0%,#f7faff 100%);color:#102847}
.page-toolbar{height:54px;flex-basis:54px}.page-title{gap:12px}.page-title strong{font-size:22px;letter-spacing:-.2px;color:#102847}.page-title span{font-size:12px;color:#7e8da3}
.source-select :deep(.el-select__wrapper),.page-toolbar :deep(.el-button),.panel-search :deep(.el-input__wrapper),.table-search :deep(.el-input__wrapper){border-radius:9px}.source-select :deep(.el-select__wrapper),.panel-search :deep(.el-input__wrapper),.table-search :deep(.el-input__wrapper){box-shadow:0 0 0 1px #dfe8f3 inset;background:#fff}
.connection-state{height:28px;padding:0 10px;border-radius:999px;border-color:#e1e8f2;background:#fff;color:#78889f}.connection-state.ok{border-color:#cdebd9;background:#f3fbf6}.connection-state.bad{border-color:#f1d2d2;background:#fff7f7}
.governance-shell{border:1px solid #e2e9f3;border-radius:18px;box-shadow:0 12px 34px rgba(42,83,163,.05);overflow:hidden;background:#fff}
.database-panel{border-right-color:#e8eef6;background:#fbfdff}.panel-head{height:50px;flex-basis:50px;border-bottom-color:#e8eef6;background:#fff}.panel-head strong{color:#17304f;font-weight:700}.panel-head span{min-width:22px;height:20px;padding:0 7px;border-radius:999px;background:#edf4ff;color:#2f6fed;display:inline-flex;align-items:center;justify-content:center}
.panel-search{padding:11px 10px;border-bottom-color:#edf1f6;background:#fbfdff}.database-list{padding:7px}.database-row{min-height:42px;padding:7px 10px;border-radius:9px}.database-row:hover{background:#f1f6fb}.database-row.active{background:#eaf2ff;color:#2468d8}.database-row.active:before{left:0;top:7px;bottom:7px;width:3px;border-radius:2px;background:#3b82f6}.database-copy strong{color:#40536c}.database-row.active .database-copy strong{color:#2468d8}
.table-panel-head{height:66px;flex-basis:66px;padding:0 20px;border-bottom-color:#e8eef6}.db-title strong{font-size:17px;color:#17304f}.db-title span,.table-count{color:#8190a5}.table-toolbar{height:54px;flex-basis:54px;padding:10px 16px;border-bottom-color:#edf1f6;background:#fbfdff}.table-content{padding:0 16px 16px}
.table-content :deep(.el-table){--el-table-header-bg-color:#fbfcfe;--el-table-row-hover-bg-color:#f6faff;--el-table-border-color:#eef2f7}.table-content :deep(.el-table th.el-table__cell){height:44px;background:#fbfcfe;color:#78889f;font-size:11px;font-weight:650}.table-content :deep(.el-table td.el-table__cell){border-bottom-color:#f0f3f7}.table-name{color:#2468d8;font-weight:650}.type-text{display:inline-flex;padding:3px 8px;border-radius:999px;background:#f1f5fa;color:#5f7188}
.drawer-head{min-height:116px;padding:24px 26px 18px;border-bottom-color:#e7edf5;background:#fff}.drawer-path{color:#8493a8}.drawer-head h2{color:#102847;font-size:21px}.drawer-head p{color:#74849a}.drawer-tabs{height:46px;flex-basis:46px;padding:0 26px;border-bottom-color:#e7edf5;background:#fbfdff}.drawer-tabs button{color:#687a92}.drawer-tabs button.active{color:#2468d8}.drawer-tabs button.active:after{background:#3b82f6}.drawer-body{background:#fbfdff}.overview-panel{padding:22px 26px}.overview-grid{border-color:#e2e9f3;border-radius:12px;overflow:hidden;background:#fff}.overview-grid>div{border-color:#edf1f6;padding:12px 14px}.overview-grid label{color:#8190a5}.overview-grid strong{color:#334a66}.info-section{border-color:#e2e9f3;border-radius:12px;overflow:hidden;background:#fff}.section-title{height:42px;background:#f7faff;border-bottom-color:#e7edf5;color:#31506f}.info-section dt,.info-section dd{border-bottom-color:#eef2f7}.info-section dt{background:#fbfcfe;color:#8392a7}.drawer-table-section{padding:16px 22px}.preview-table-shell{border-color:#e2e9f3;border-radius:10px;background:#fff}.preview-table th{background:#f8fbff;color:#63758d}.preview-table th,.preview-table td{border-bottom-color:#eef2f7}
:deep(.metadata-drawer .el-drawer__body){padding:0;background:#fbfdff}
/* Metadata database navigator */
.governance-shell{grid-template-columns:286px minmax(0,1fr)}
.database-panel{background:linear-gradient(180deg,#f9fbff 0%,#fbfdff 100%)}
.source-summary{margin:12px 12px 8px;padding:13px 12px;display:flex;align-items:center;gap:11px;border:1px solid #deE8f5;border-radius:12px;background:linear-gradient(135deg,#ffffff 0%,#f3f8ff 100%);box-shadow:0 6px 18px rgba(51,99,168,.055)}
.source-mark{width:34px;height:34px;flex:0 0 34px;border-radius:10px;display:grid;place-items:center;background:linear-gradient(145deg,#e8f2ff,#f6faff);box-shadow:inset 0 0 0 1px #dbe9fb}.source-mark span{position:relative;width:17px;height:13px;border:1.5px solid #3b82f6;border-radius:50%}.source-mark span:before,.source-mark span:after{content:'';position:absolute;left:-1.5px;width:16px;height:5px;border-left:1.5px solid #3b82f6;border-right:1.5px solid #3b82f6;border-bottom:1.5px solid #3b82f6;border-radius:0 0 50% 50%}.source-mark span:before{top:3px}.source-mark span:after{top:7px}.source-summary-copy{min-width:0;flex:1}.source-summary-title{display:flex;align-items:center;gap:7px;min-width:0}.source-summary-title strong{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#17304f;font-size:13px}.source-summary-title span{flex:none;padding:2px 6px;border-radius:999px;background:#eaf2ff;color:#2f6fed;font-size:9px;font-weight:650}.source-summary-copy small{display:block;margin-top:5px;color:#8392a7;font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.panel-head{margin-top:1px;padding:0 13px;background:transparent;border-bottom:0}.panel-head strong{font-size:12px;letter-spacing:.1px}.panel-head span{height:18px;min-width:20px;padding:0 6px;font-size:10px}
.panel-search{padding:0 11px 10px;border-bottom:0;background:transparent}.panel-search :deep(.el-input__wrapper){min-height:34px;background:rgba(255,255,255,.92);box-shadow:0 0 0 1px #e1eaf5 inset}
.database-list{padding:2px 9px 10px}.database-row{min-height:54px;padding:8px 9px 8px 10px;gap:10px;margin:3px 0;border:1px solid transparent;border-radius:11px;transition:background .16s ease,border-color .16s ease,box-shadow .16s ease,transform .16s ease}.database-row:hover{background:#fff;border-color:#e5edf7;box-shadow:0 5px 16px rgba(45,91,155,.05);transform:translateY(-1px)}.database-row.active{background:linear-gradient(90deg,#eaf3ff 0%,#f5f9ff 100%);border-color:#cfe0fb;box-shadow:0 6px 18px rgba(59,130,246,.08)}.database-row.active:before{left:-1px;top:9px;bottom:9px;width:3px;border-radius:0 3px 3px 0;background:#3b82f6}
.database-icon{width:30px;height:30px;flex:0 0 30px;border:0!important;border-radius:9px!important;background:#f0f5fb;display:grid;place-items:center}.database-icon:before{content:'';position:absolute;left:7px!important;top:8px!important;width:14px!important;height:10px!important;border:1.4px solid #70849f!important;border-radius:50%!important;background:transparent}.database-icon:after{content:'';position:absolute;left:7px!important;top:12px!important;width:14px!important;height:9px!important;border-left:1.4px solid #70849f!important;border-right:1.4px solid #70849f!important;border-bottom:1.4px solid #70849f!important;border-radius:0 0 50% 50%!important}.database-icon i{display:none}.database-row.active .database-icon{background:#dceaff}.database-row.active .database-icon:before,.database-row.active .database-icon:after{border-color:#347cf0!important}
.database-copy{flex:1;min-width:0}.database-copy strong{font-size:12px;font-weight:650;color:#344b66}.database-copy small{margin-top:4px;font-size:10px;color:#95a1b2}.database-row.active .database-copy strong{color:#1f63cb}.database-row.active .database-copy small{color:#7189a8}.database-arrow{flex:none;color:#b2bdca;font-size:19px;line-height:1;transform:translateX(-1px);transition:transform .16s ease,color .16s ease}.database-row:hover .database-arrow{color:#7a91ad;transform:translateX(1px)}.database-row.active .database-arrow{color:#3b82f6;transform:translateX(1px)}
@media(max-width:1280px){.metadata-page{padding:20px}.governance-shell{grid-template-columns:250px minmax(0,1fr)}.source-summary{margin:10px 9px 7px}}

</style>
