<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dataSourceApi } from '../../api/platform'
import { metadataApi, type ColumnView, type DataSourceView, type DatabaseView, type LineageView, type TablePreview, type TableProfile, type TableView } from '../../api/domain'

const sources=ref<DataSourceView[]>([]), databases=ref<DatabaseView[]>([])
const tableCache=ref<Record<string,TableView[]>>({}), expanded=ref<Set<string>>(new Set())
const sourceId=ref<number>(), database=ref(''), table=ref<TableView>()
const columns=ref<ColumnView[]>([]), profile=ref<TableProfile>(), lineage=ref<LineageView[]>([]), preview=ref<TablePreview>()
const keyword=ref(''), activeTab=ref<'overview'|'fields'|'preview'|'lineage'>('overview'), previewLimit=ref(50)
const loading=ref(false), detailLoading=ref(false), previewLoading=ref(false), error=ref(''), previewError=ref('')
const connectionState=ref<'idle'|'ok'|'bad'>('idle')

const source=computed(()=>sources.value.find(item=>item.id===sourceId.value))
const currentDatabase=computed(()=>databases.value.find(item=>item.name===database.value))
const currentTables=computed(()=>tableCache.value[database.value]||[])
const visibleDatabases=computed(()=>{
  const q=keyword.value.trim().toLowerCase(); if(!q)return databases.value
  return databases.value.filter(db=>db.name.toLowerCase().includes(q)||(tableCache.value[db.name]||[]).some(t=>`${t.name} ${t.comment||''}`.toLowerCase().includes(q)))
})
function visibleTables(db:string){const q=keyword.value.trim().toLowerCase();const rows=tableCache.value[db]||[];return q?rows.filter(t=>`${t.name} ${t.comment||''}`.toLowerCase().includes(q)):rows}
function fmtSize(v?:number){if(v==null)return '—';if(v>=1024**3)return `${(v/1024**3).toFixed(2)} GB`;if(v>=1024**2)return `${(v/1024**2).toFixed(1)} MB`;if(v>=1024)return `${(v/1024).toFixed(1)} KB`;return `${v} B`}
function fmtTime(v?:string){return v?String(v).replace('T',' ').slice(0,19):'—'}

async function loadSources(){
  error.value=''
  try{
    sources.value=(await dataSourceApi.list()).filter(s=>s.metadataVisible)
    const saved=Number(localStorage.getItem('metadataDataSourceId')||0)
    const first=sources.value.find(s=>s.id===saved)||sources.value.find(s=>s.type==='STARROCKS')||sources.value[0]
    if(first)await selectSource(first.id)
  }catch(e){error.value=e instanceof Error?e.message:'数据源加载失败'}
}
async function selectSource(id:number){
  sourceId.value=id; localStorage.setItem('metadataDataSourceId',String(id)); database.value='';table.value=undefined
  databases.value=[];tableCache.value={};expanded.value=new Set();columns.value=[];profile.value=undefined;lineage.value=[];preview.value=undefined
  activeTab.value='overview';connectionState.value='idle';loading.value=true;error.value=''
  try{
    const selected=sources.value.find(s=>s.id===id)
    databases.value=await metadataApi.databases(id,selected?.type||'STARROCKS')
    const saved=localStorage.getItem('metadataDatabase')||''
    if(saved&&databases.value.some(d=>d.name===saved))await selectDatabase(saved)
  }catch(e){error.value=e instanceof Error?e.message:'数据库加载失败'}finally{loading.value=false}
}
async function loadTables(db:string){
  if(!sourceId.value||tableCache.value[db])return
  const rows=await metadataApi.tables(sourceId.value,db)
  tableCache.value={...tableCache.value,[db]:rows}
}
async function selectDatabase(db:string){
  database.value=db;table.value=undefined;columns.value=[];profile.value=undefined;lineage.value=[];preview.value=undefined;activeTab.value='overview'
  localStorage.setItem('metadataDatabase',db)
  expanded.value=new Set([...expanded.value,db]); detailLoading.value=true
  try{await loadTables(db)}catch(e){error.value=e instanceof Error?e.message:'数据表加载失败'}finally{detailLoading.value=false}
}
async function toggleDatabase(db:string){
  if(expanded.value.has(db)){const next=new Set(expanded.value);next.delete(db);expanded.value=next;return}
  expanded.value=new Set([...expanded.value,db]);await selectDatabase(db)
}
async function selectTable(db:string,value:TableView){
  database.value=db;table.value=value;activeTab.value='overview';preview.value=undefined;previewError.value='';detailLoading.value=true
  localStorage.setItem('metadataDatabase',db)
  if(!sourceId.value)return
  try{
    const [cols,prof,lin]=await Promise.allSettled([
      metadataApi.columns(sourceId.value,db,value.name),metadataApi.profile(sourceId.value,db,value.name),metadataApi.lineage(`${db}.${value.name}`)
    ])
    columns.value=cols.status==='fulfilled'?cols.value:[]
    profile.value=prof.status==='fulfilled'?prof.value:undefined
    lineage.value=lin.status==='fulfilled'?lin.value:[]
  }catch(e){error.value=e instanceof Error?e.message:'表详情加载失败'}finally{detailLoading.value=false}
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
  const keepDb=database.value,keepTable=table.value?.name
  await selectSource(sourceId.value)
  if(keepDb&&databases.value.some(d=>d.name===keepDb)){
    await selectDatabase(keepDb)
    const found=(tableCache.value[keepDb]||[]).find(t=>t.name===keepTable)
    if(found)await selectTable(keepDb,found)
  }
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
  <div class="metadata-page">
    <div class="page-head">
      <div class="breadcrumb">元数据 / <b>{{ source?.name||'数据源' }}</b> / <span>{{ table?.name||database||'浏览' }}</span></div>
      <div class="head-spacer" />
      <span :class="['source-state',connectionState]"><i />{{connectionState==='ok'?'连接正常':connectionState==='bad'?'连接失败':'未检测'}}</span>
      <el-select v-model="sourceId" class="source-select" placeholder="选择数据源" @change="selectSource">
        <el-option v-for="s in sources" :key="s.id" :label="`${s.name} · ${s.type}`" :value="s.id" />
      </el-select>
      <el-button @click="testConnection">检测连接</el-button>
      <el-button type="primary" @click="refreshMetadata">刷新元数据</el-button>
    </div>

    <div v-if="error" class="ds-error">{{error}}</div>
    <section class="metadata-shell" v-loading="loading">
      <aside class="pane catalog-pane">
        <div class="pane-head"><strong>元数据目录</strong><span class="count">{{databases.length}} 个数据库</span></div>
        <div class="pane-search"><el-input v-model="keyword" placeholder="搜索数据库或数据表" clearable /></div>
        <div class="tree">
          <div v-for="db in visibleDatabases" :key="db.name" :class="['tree-db',{expanded:expanded.has(db.name)}]">
            <button :class="['tree-db-row',{on:database===db.name&&!table}]" @click="toggleDatabase(db.name)">
              <span class="tree-chevron">›</span>
              <span class="navicat-db-icon" aria-hidden="true" />
              <span class="tree-title">{{db.name}}</span>
            </button>
            <div v-if="expanded.has(db.name)" class="tree-children">
              <button v-for="t in visibleTables(db.name)" :key="t.name" :class="['tree-table-row',{on:database===db.name&&table?.name===t.name}]" @click="selectTable(db.name,t)">
                <span class="tree-branch" aria-hidden="true" />
                <span class="navicat-table-icon" aria-hidden="true"><i/><i/><i/><i/></span>
                <span class="tree-title">{{t.name}}</span>
              </button>
              <div v-if="!tableCache[db.name]" class="tree-loading">正在加载数据表…</div>
              <div v-else-if="!visibleTables(db.name).length" class="tree-loading">当前数据库暂无匹配数据表</div>
            </div>
          </div>
          <div v-if="!visibleDatabases.length" class="empty">暂无数据库或没有匹配结果</div>
        </div>
      </aside>

      <section class="pane detail" v-loading="detailLoading">
        <template v-if="database">
          <div class="detail-head">
            <div class="detail-symbol">{{table?'T':'DB'}}</div>
            <div class="detail-copy"><div class="detail-title">{{table?.name||database}}</div><div class="detail-path">{{source?.name}} / {{database}}<template v-if="table"> / {{table.name}}</template></div></div>
            <div class="detail-tags"><span class="tag">{{table?.type||'DATABASE'}}</span><span class="tag">{{table?`${columns.length} 字段`:`${currentTables.length} 表`}}</span></div>
          </div>

          <template v-if="!table">
            <div class="db-browser">
              <div class="facts">
                <div class="fact"><label>数据源</label><strong>{{source?.name||'—'}}</strong></div>
                <div class="fact"><label>数据库</label><strong>{{database}}</strong></div>
                <div class="fact"><label>数据表</label><strong>{{currentTables.length}}</strong></div>
                <div class="fact"><label>数据库说明</label><strong>{{currentDatabase?.comment||'—'}}</strong></div>
              </div>
              <div class="db-browser-head"><strong>数据表</strong><span>{{currentTables.length}} 项</span></div>
              <el-table :data="currentTables" height="calc(100vh - 345px)" @row-click="(row:TableView)=>selectTable(database,row)">
                <el-table-column prop="name" label="表名" min-width="260"><template #default="s"><span class="field-name">{{s.row.name}}</span></template></el-table-column>
                <el-table-column prop="type" label="类型" width="140" />
                <el-table-column prop="comment" label="说明" min-width="320" show-overflow-tooltip />
              </el-table>
            </div>
          </template>

          <template v-else>
            <div class="tabs">
              <button :class="['tab',{on:activeTab==='overview'}]" @click="switchTab('overview')">概览</button>
              <button :class="['tab',{on:activeTab==='fields'}]" @click="switchTab('fields')">字段</button>
              <button :class="['tab',{on:activeTab==='preview'}]" @click="switchTab('preview')">数据预览</button>
              <button :class="['tab',{on:activeTab==='lineage'}]" @click="switchTab('lineage')">血缘</button>
            </div>
            <div class="detail-body">
              <div v-show="activeTab==='overview'" class="overview">
                <div class="facts table-profile-facts">
                  <div class="fact owner-fact"><label>负责人</label><div class="fact-value"><strong>{{profile?.owner||'未设置'}}</strong><button v-if="profile?.ownerEditable" class="fact-action" @click="editOwner">编辑</button></div></div>
                  <div class="fact"><label>行数</label><strong>{{profile?.rowCount?.toLocaleString()||'—'}}</strong></div>
                  <div class="fact"><label>估算大小</label><strong>{{fmtSize(profile?.estimatedSizeBytes)}}</strong></div>
                  <div class="fact"><label>更新时间</label><strong>{{fmtTime(profile?.updateTime)}}</strong></div>
                </div>
                <div class="section"><div class="section-title">对象信息</div><div class="source-grid"><span>数据源</span><b>{{source?.name}}</b><span>类型</span><b>{{source?.type}}</b><span>数据库</span><b>{{database}}</b><span>数据表</span><b>{{table.name}}</b><span>地址</span><b>{{source?.host}}:{{source?.port}}</b><span>建表时间</span><b>{{fmtTime(profile?.createTime)}}</b></div></div>
                <div class="section"><div class="section-title">表说明</div><div class="section-body">{{table.comment||'暂无表说明'}}</div></div>
              </div>

              <div v-show="activeTab==='fields'" class="content-pad">
                <el-table :data="columns" height="calc(100vh - 300px)"><el-table-column prop="name" label="字段" min-width="220"><template #default="s"><span class="field-name">{{s.row.name}}</span></template></el-table-column><el-table-column prop="dataType" label="类型" width="190"/><el-table-column prop="nullable" label="可空" width="100"><template #default="s"><span :class="['nullable',{no:s.row.nullable===false}]">{{s.row.nullable===false?'否':'是'}}</span></template></el-table-column><el-table-column prop="comment" label="说明" min-width="300" show-overflow-tooltip/></el-table>
              </div>

              <div v-show="activeTab==='preview'" class="preview-wrap">
                <div class="preview-toolbar"><span>数据预览仅用于抽样查看，不执行全表查询。</span><div class="head-spacer"/><el-select v-model="previewLimit" style="width:110px" @change="loadPreview"><el-option :value="20" label="20 行"/><el-option :value="50" label="50 行"/><el-option :value="100" label="100 行"/></el-select><el-button @click="loadPreview">刷新</el-button></div>
                <div v-if="previewError" class="preview-error">{{previewError}}</div>
                <div v-else class="preview-table-shell" v-loading="previewLoading">
                  <table v-if="preview?.columns.length" class="preview-table"><thead><tr><th v-for="c in preview.columns" :key="c">{{c}}</th></tr></thead><tbody><tr v-for="(r,ri) in preview.rows" :key="ri"><td v-for="(c,ci) in preview.columns" :key="c">{{r[ci]??'NULL'}}</td></tr></tbody></table>
                  <div v-else-if="!previewLoading" class="empty">暂无预览数据</div>
                </div>
              </div>

              <div v-show="activeTab==='lineage'" class="lineage-wrap">
                <div class="lineage-tip">展示平台已经解析到的当前数据表上下游关系。</div>
                <el-table v-if="lineage.length" :data="lineage"><el-table-column prop="sourceTable" label="上游" min-width="260"/><el-table-column label="关系" width="100"><template #default>→</template></el-table-column><el-table-column prop="targetTable" label="下游" min-width="260"/><el-table-column prop="relationType" label="类型" width="150"/></el-table>
                <div v-else class="empty">暂无已解析血缘</div>
              </div>
            </div>
          </template>
        </template>
        <div v-else class="empty detail-empty"><strong>请选择数据库</strong><span>从左侧元数据目录选择数据库或数据表</span></div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.metadata-page{height:calc(100vh - var(--ds-topbar));padding:16px 24px 22px;display:flex;flex-direction:column;gap:12px;overflow:hidden}.page-head{height:44px;flex:0 0 44px;display:flex;align-items:center;gap:10px}.breadcrumb{font-size:12px;color:#8491a4}.breadcrumb b{color:#2c405c;font-weight:650}.head-spacer{flex:1}.source-select{width:250px}.source-state{height:26px;padding:0 9px;border-radius:999px;background:#f1f4f8;color:#758296;font-size:11px;display:inline-flex;align-items:center;gap:6px}.source-state i{width:6px;height:6px;border-radius:50%;background:#98a2b3}.source-state.ok{background:#e9f8f2;color:#148a64}.source-state.ok i{background:#16a36f}.source-state.bad{background:#fff0ef;color:#c84e49}.source-state.bad i{background:#d75550}.metadata-shell{flex:1;min-height:0;display:grid;grid-template-columns:300px minmax(0,1fr);gap:12px}.pane{min-height:0;background:#fff;border:1px solid var(--ds-border);border-radius:9px;overflow:hidden;display:flex;flex-direction:column}.pane-head{height:50px;flex:0 0 50px;border-bottom:1px solid var(--ds-border-soft);display:flex;align-items:center;padding:0 14px}.pane-head strong{font-size:14px;color:#263b56}.count{margin-left:auto;color:#8d99a9;font-size:11px}.pane-search{padding:10px;border-bottom:1px solid var(--ds-border-soft)}.tree{flex:1;min-height:0;overflow:auto;padding:5px 7px 10px}.tree-db{margin:0}.tree-db-row,.tree-table-row{width:100%;display:flex;align-items:center;border:0;background:transparent;border-radius:4px;cursor:pointer;color:#344054;text-align:left;white-space:nowrap}.tree-db-row{height:30px;padding:0 7px 0 3px;gap:6px}.tree-table-row{height:29px;padding:0 7px 0 23px;gap:6px;font-size:12px;position:relative}.tree-db-row:hover,.tree-table-row:hover{background:#f3f6fa}.tree-db-row.on,.tree-table-row.on{background:#dcecff;color:#155eef}.tree-db-row.on .tree-title,.tree-table-row.on .tree-title{color:#155eef}.tree-chevron{width:14px;height:14px;display:grid;place-items:center;color:#667085;font-size:16px;line-height:1;transition:transform .12s;flex:0 0 14px}.tree-db.expanded>.tree-db-row .tree-chevron{transform:rotate(90deg)}.tree-title{min-width:0;overflow:hidden;text-overflow:ellipsis;font-size:12px;font-weight:500;line-height:29px}.tree-db-row .tree-title{font-weight:600}.tree-children{padding:0}.tree-branch{width:9px;height:29px;flex:0 0 9px;position:relative}.tree-branch:before{content:"";position:absolute;left:2px;top:-1px;bottom:14px;border-left:1px solid #d6dde8}.tree-branch:after{content:"";position:absolute;left:2px;top:14px;width:7px;border-top:1px solid #d6dde8}.navicat-db-icon{width:15px;height:12px;position:relative;display:inline-block;flex:0 0 15px;border:1px solid #5b86c5;border-radius:50% / 26%;background:linear-gradient(#eef5ff,#dceaff)}.navicat-db-icon:before,.navicat-db-icon:after{content:"";position:absolute;left:-1px;width:15px;height:5px;border:1px solid #5b86c5;border-radius:50%;background:#eef5ff}.navicat-db-icon:before{top:-2px}.navicat-db-icon:after{bottom:-2px;background:transparent;border-top-color:transparent}.navicat-table-icon{width:14px;height:14px;display:grid;grid-template-columns:repeat(2,1fr);grid-template-rows:repeat(2,1fr);gap:1px;padding:2px;border:1px solid #7d8da6;border-radius:2px;background:#fff;flex:0 0 14px}.navicat-table-icon i{display:block;background:#b6c3d5}.tree-table-row.on .navicat-table-icon{border-color:#4f7fe8}.tree-table-row.on .navicat-table-icon i{background:#6f9cf2}.tree-loading{padding:6px 10px 6px 43px;color:#98a2b3;font-size:11px}.detail{min-width:0}.detail-head{height:76px;flex:0 0 76px;border-bottom:1px solid var(--ds-border-soft);display:flex;align-items:center;padding:0 18px;gap:12px}.detail-symbol{width:40px;height:40px;border-radius:10px;background:#eaf2ff;color:#2c72dd;display:grid;place-items:center;font-size:11px;font-weight:750}.detail-copy{min-width:0}.detail-title{font-size:17px;font-weight:700;color:#253b57}.detail-path{margin-top:5px;font-size:11px;color:#8996a7}.detail-tags{margin-left:auto;display:flex;gap:7px}.tag{height:23px;padding:0 8px;border-radius:6px;background:#f1f4f8;color:#718095;font-size:10px;display:inline-flex;align-items:center}.tabs{height:44px;flex:0 0 44px;border-bottom:1px solid var(--ds-border-soft);display:flex;align-items:flex-end;padding-left:18px;gap:24px}.tab{height:44px;border:0;background:none;color:#68788f;padding:0 2px;position:relative;cursor:pointer}.tab.on{color:#286ed7;font-weight:650}.tab.on:after{content:"";position:absolute;left:0;right:0;bottom:0;height:2px;background:#3478f6}.detail-body{flex:1;min-height:0;overflow:auto}.overview,.db-browser{padding:16px 18px 22px}.facts{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.fact{min-height:70px;border:1px solid #e8edf3;border-radius:8px;background:#fbfcfe;padding:11px 12px}.fact label{display:block;color:#8995a5;font-size:10px;margin-bottom:7px}.fact strong{display:block;color:#304863;font-size:12px;font-weight:650;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.fact-value{display:flex;align-items:center;gap:7px}.fact-value strong{flex:1}.fact-action{border:0;border-radius:5px;padding:4px 7px;background:#edf3ff;color:#3478f6;cursor:pointer;font-size:10px}.section{margin-top:15px;border:1px solid #e8edf3;border-radius:8px;overflow:hidden}.section-title{height:38px;background:#fafbfd;border-bottom:1px solid #edf1f5;display:flex;align-items:center;padding:0 12px;font-size:12px;font-weight:650;color:#3e5571}.section-body{padding:12px;color:#65758b;font-size:12px;line-height:1.7}.source-grid{display:grid;grid-template-columns:120px 1fr 120px 1fr}.source-grid span,.source-grid b{padding:10px 12px;border-bottom:1px solid #edf1f5;font-size:11px}.source-grid span{color:#8995a5;background:#fbfcfe}.source-grid b{font-weight:550;color:#405772;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.db-browser-head{height:44px;display:flex;align-items:center;gap:10px;margin-top:12px;border-bottom:1px solid #edf1f5}.db-browser-head span{margin-left:auto;color:#8d99a9;font-size:11px}.content-pad,.preview-wrap,.lineage-wrap{padding:14px 18px 20px}.field-name{font-family:Consolas,Monaco,monospace;color:#2168c9}.nullable{font-size:9px;border-radius:5px;padding:2px 6px;background:#edf7f2;color:#16845f}.nullable.no{background:#fff1e9;color:#bf6a16}.preview-toolbar{height:40px;display:flex;align-items:center;gap:10px;margin-bottom:10px;color:#8190a4;font-size:11px}.preview-table-shell{width:100%;overflow:auto;border:1px solid #e6ebf1;border-radius:8px}.preview-table{min-width:100%;border-collapse:collapse;font-size:11px}.preview-table th{height:38px;background:#f7f9fb;color:#65758b;text-align:left;padding:0 12px;white-space:nowrap}.preview-table td{height:38px;border-top:1px solid #edf1f5;padding:0 12px;color:#3d536f;white-space:nowrap}.preview-error{margin:26px auto;max-width:520px;padding:22px;border:1px solid #f1d9d6;border-radius:9px;background:#fff8f7;color:#a95550;text-align:center;font-size:12px}.lineage-tip{margin-bottom:12px;padding:9px 11px;border:1px solid #dce8f8;border-radius:7px;background:#f6f9fe;color:#617994;font-size:11px}.empty{padding:34px 18px;text-align:center;color:#95a1b0;font-size:12px;line-height:1.7}.detail-empty{margin:auto;display:flex;flex-direction:column;gap:6px}.detail-empty strong{font-size:15px;color:#53677f}@media(max-width:1360px){.metadata-page{padding-left:16px;padding-right:16px}.metadata-shell{grid-template-columns:270px minmax(0,1fr)}.facts{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
