<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import type * as Monaco from 'monaco-editor/esm/vs/editor/editor.api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Close, Document, FolderOpened, Plus, Search, VideoPlay } from '@element-plus/icons-vue'
import { dataSourceApi } from '../../api/platform'
import { developmentApi, metadataApi, releaseApi, type DataSourceView, type DevFile, type DevFolder, type DevProject, type FileVersion, type LineageView, type QueryResult, type TableView } from '../../api/domain'

const router=useRouter()
const projects=ref<DevProject[]>([]),folders=ref<DevFolder[]>([]),files=ref<DevFile[]>([]),sources=ref<DataSourceView[]>([]),databases=ref<string[]>([]),tables=ref<TableView[]>([])
const projectId=ref<number>(),file=ref<DevFile>(),sourceId=ref<number>(),database=ref(''),selectedTableName=ref('')
const openFiles=ref<DevFile[]>([]),versions=ref<FileVersion[]>([]),lineage=ref<LineageView[]>([]),result=ref<QueryResult>(),output=ref('')
const resourceSearch=ref(''),groupMode=ref<'all'|'recent'|'mine'>('all'),activeBottom=ref<'result'|'log'|'lineage'|'versions'>('result')
const saving=ref(false),running=ref(false),loading=ref(false),filesLoading=ref(false),editorLoading=ref(false),taskDrawerOpen=ref(false)
const editorHost=ref<HTMLElement>(),editor=shallowRef<Monaco.editor.IStandaloneCodeEditor>()
const cursorLine=ref(1),cursorCol=ref(1),editorDirty=ref(false),dirtyFileIds=ref<Set<number>>(new Set())
const newDialog=ref(false),publishDialog=ref(false),compareDialog=ref(false),compareTarget=ref<FileVersion|null>(null),approvalRequired=ref(false)
const newForm=reactive({name:'',folderId:undefined as number|undefined,description:''}),taskDescription=ref('')
let monacoApi:typeof import('monaco-editor/esm/vs/editor/editor.api')|null=null
let switchingEditor=false
const savedContent=new Map<number,string>()
const restoring=ref(true),DEV_STATE_KEY='datasphere:development:state:v3'
type DevState={projectId?:number;fileId?:number;sourceId?:number;database?:string;activeBottom?:'result'|'log'|'lineage'|'versions'}

const currentProject=computed(()=>projects.value.find(x=>x.id===projectId.value))
const productionVersion=computed(()=>versions.value.filter(x=>x.publishFlag).reduce((m,x)=>Math.max(m,x.versionNo),0))
const currentVersion=computed(()=>file.value?.currentVersion||0)
const hasUnpublished=computed(()=>editorDirty.value||currentVersion.value>productionVersion.value)
const connectionLabel=computed(()=>{const source=sources.value.find(x=>x.id===sourceId.value);return source?`${source.name}${database.value?' / '+database.value:''}`:'未选择 StarRocks'})
const visibleFiles=computed(()=>{
  let rows=groupMode.value==='recent'?openFiles.value:files.value
  const q=resourceSearch.value.trim().toLowerCase()
  return q?rows.filter(x=>x.name.toLowerCase().includes(q)):rows
})
const groupedFiles=computed(()=>{
  const map=new Map(folders.value.map(x=>[x.id,x.name.toUpperCase()]))
  const order=['ODS','DWD','DWS','ADS','其他']
  const groups=new Map<string,DevFile[]>()
  for(const row of visibleFiles.value){
    const byFolder=row.folderId?map.get(row.folderId):undefined
    const prefix=row.name.replace(/\.sql$/i,'').split('_')[0]?.toUpperCase()
    const layer=['ODS','DWD','DWS','ADS'].includes(byFolder||'')?byFolder!:['ODS','DWD','DWS','ADS'].includes(prefix)?prefix:'其他'
    if(!groups.has(layer))groups.set(layer,[])
    groups.get(layer)!.push(row)
  }
  return order.filter(x=>groups.has(x)).map(name=>({name,files:groups.get(name)!}))
})

function readState():DevState{try{return JSON.parse(sessionStorage.getItem(DEV_STATE_KEY)||'{}')}catch{return {}}}
function persistState(){if(restoring.value)return;sessionStorage.setItem(DEV_STATE_KEY,JSON.stringify({projectId:projectId.value,fileId:file.value?.id,sourceId:sourceId.value,database:database.value,activeBottom:activeBottom.value}))}
function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
function scheduleEditorInit(){const start=()=>void initEditor();const idle=(window as Window & {requestIdleCallback?:(cb:()=>void,o?:{timeout:number})=>number}).requestIdleCallback;idle?idle(start,{timeout:500}):globalThis.setTimeout(start,0)}
function setDirty(id:number,value:boolean){const next=new Set(dirtyFileIds.value);value?next.add(id):next.delete(id);dirtyFileIds.value=next;if(file.value?.id===id)editorDirty.value=value}
function cacheActiveEditor(){if(!file.value||!editor.value)return;const content=editor.value.getValue();file.value={...file.value,content};const idx=openFiles.value.findIndex(x=>x.id===file.value!.id);if(idx>=0)openFiles.value[idx]=file.value;setDirty(file.value.id,content!==(savedContent.get(file.value.id)??''))}
async function initEditor(){
  if(!editorHost.value||editor.value||editorLoading.value)return
  editorLoading.value=true
  try{
    monacoApi??=await import('monaco-editor/esm/vs/editor/editor.api');await import('monaco-editor/esm/vs/basic-languages/sql/sql.contribution')
    if(!editorHost.value||editor.value)return
    editor.value=monacoApi.editor.create(editorHost.value,{value:file.value?.content||'-- 请选择或新建 SQL 任务\nSELECT 1;',language:'sql',theme:'vs',automaticLayout:true,minimap:{enabled:false},fontSize:13,lineHeight:24,scrollBeyondLastLine:false,padding:{top:14},renderLineHighlight:'line',wordWrap:'off'})
    editor.value.addCommand(monacoApi.KeyMod.CtrlCmd|monacoApi.KeyCode.Enter,()=>void run(false))
    editor.value.addCommand(monacoApi.KeyMod.CtrlCmd|monacoApi.KeyCode.KeyS,()=>void save())
    editor.value.onDidChangeModelContent(()=>{if(switchingEditor||!file.value)return;const content=editor.value?.getValue()||'';file.value={...file.value,content};const idx=openFiles.value.findIndex(x=>x.id===file.value!.id);if(idx>=0)openFiles.value[idx]=file.value;setDirty(file.value.id,content!==(savedContent.get(file.value.id)??''))})
    editor.value.onDidChangeCursorPosition(e=>{cursorLine.value=e.position.lineNumber;cursorCol.value=e.position.column})
  }catch(e){ElMessage.error('SQL 编辑器加载失败：'+msg(e))}finally{editorLoading.value=false}
}
async function boot(){
  loading.value=true;const state=readState()
  try{
    const [ps,ds]=await Promise.all([developmentApi.projects(),dataSourceApi.list()]);projects.value=ps;sources.value=ds.filter(x=>x.type==='STARROCKS')
    projectId.value=(ps.find(x=>x.id===state.projectId)||ps[0])?.id;sourceId.value=(sources.value.find(x=>x.id===state.sourceId)||sources.value[0])?.id
    if(state.activeBottom)activeBottom.value=state.activeBottom
    loading.value=false;await nextTick();scheduleEditorInit()
    const jobs:Promise<void>[]=[];if(projectId.value)jobs.push(loadDirectory(state.fileId));if(sourceId.value)jobs.push(loadDatabases(state.database));await Promise.all(jobs)
  }catch(e){ElMessage.error(msg(e))}finally{restoring.value=false;persistState();loading.value=false}
}
async function loadDirectory(preferred?:number){
  if(!projectId.value)return;filesLoading.value=true
  try{const [fs,dirs]=await Promise.all([developmentApi.files(projectId.value),developmentApi.folders(projectId.value)]);files.value=fs;folders.value=dirs;const target=fs.find(x=>x.id===preferred)||fs[0];if(target)await openFile(target)}finally{filesLoading.value=false}
}
async function openFile(row:DevFile){
  cacheActiveEditor();let detail=openFiles.value.find(x=>x.id===row.id)
  if(!detail){detail=await developmentApi.getFile(row.id);savedContent.set(detail.id,detail.content||'');openFiles.value.push(detail)}
  file.value={...detail};taskDescription.value=detail.description||''
  switchingEditor=true;editor.value?.setValue(detail.content||'');switchingEditor=false
  setDirty(detail.id,(detail.content||'')!==(savedContent.get(detail.id) ?? detail.content ?? ''));persistState();await loadVersions();inferTargetTable()
}
async function closeTab(row:DevFile,event:Event){
  event.stopPropagation();if(dirtyFileIds.value.has(row.id)){try{await ElMessageBox.confirm(`“${row.name}”有未保存修改，确认关闭？`,'关闭任务',{type:'warning'})}catch{return}}
  const idx=openFiles.value.findIndex(x=>x.id===row.id);if(idx<0)return;openFiles.value.splice(idx,1);setDirty(row.id,false)
  if(file.value?.id===row.id){const next=openFiles.value[Math.max(0,idx-1)];if(next)await openFile(next);else{file.value=undefined;versions.value=[];switchingEditor=true;editor.value?.setValue('-- 请选择或新建 SQL 任务\nSELECT 1;');switchingEditor=false}}
}
function openNew(){newForm.name='';newForm.description='';newForm.folderId=folders.value.find(x=>['DWD','ODS','DWS','ADS'].includes(x.name.toUpperCase()))?.id;newDialog.value=true}
async function createTask(){
  if(!projectId.value)return ElMessage.warning('暂无可用研发项目');let name=newForm.name.trim();if(!name)return ElMessage.warning('请输入任务名称');if(!name.toLowerCase().endsWith('.sql'))name+='.sql'
  try{const created=await developmentApi.createFile({projectId:projectId.value,folderId:newForm.folderId,name,fileType:'SQL',content:`-- ${name.replace(/\.sql$/i,'')}\n\nSELECT\n    *\nFROM \nWHERE 1 = 1;`,description:newForm.description.trim()||undefined});newDialog.value=false;await loadDirectory(created.id);ElMessage.success('开发任务已创建')}catch(e){ElMessage.error(msg(e))}
}
async function save(){
  if(!file.value||!editor.value)return;saving.value=true
  try{file.value=await developmentApi.saveFile(file.value.id,{content:editor.value.getValue(),name:file.value.name,description:taskDescription.value});savedContent.set(file.value.id,file.value.content||editor.value.getValue());const idx=openFiles.value.findIndex(x=>x.id===file.value!.id);if(idx>=0)openFiles.value[idx]=file.value;setDirty(file.value.id,false);await loadVersions();ElMessage.success(`已保存为开发版本 V${file.value.currentVersion}`)}catch(e){ElMessage.error(msg(e))}finally{saving.value=false}
}
async function openPublish(){if(!file.value)return ElMessage.warning('请选择开发任务');if(editorDirty.value)return ElMessage.warning('请先保存当前修改再提交发布');try{approvalRequired.value=(await releaseApi.policy()).approvalRequired}catch{approvalRequired.value=false}publishDialog.value=true}
async function confirmPublish(){if(!file.value)return;try{await releaseApi.request({resourceType:'DEVELOPMENT',resourceId:file.value.id,resourceName:file.value.name,requestedVersion:file.value.currentVersion});publishDialog.value=false;ElMessage.success(approvalRequired.value?'发布申请已提交，等待审批':'发布已提交');setTimeout(()=>void loadVersions(),500)}catch(e){ElMessage.error(msg(e))}}
async function run(explain=false){
  if(!sourceId.value||!editor.value)return ElMessage.warning('请先在“任务信息”中选择 StarRocks 数据源');running.value=true;activeBottom.value='log';const now=new Date().toLocaleTimeString();output.value=`[${now}] 提交 SQL 到 StarRocks\n[${now}] 正在解析并执行...`
  try{const model=editor.value.getModel(),selection=editor.value.getSelection();const sql=model&&(selection&&!selection.isEmpty()?model.getValueInRange(selection):model.getValue())||'';result.value=await developmentApi.query(explain?`EXPLAIN ${sql}`:sql,sourceId.value,database.value||undefined);output.value+=`\n[${new Date().toLocaleTimeString()}] ${result.value.status==='SUCCESS'?'执行成功':'执行完成'}，返回 ${result.value.rowCount} 行，总耗时 ${result.value.elapsedMs} ms`;activeBottom.value='result'}catch(e){output.value+=`\n[${new Date().toLocaleTimeString()}] 执行失败：${msg(e)}`;ElMessage.error(msg(e))}finally{running.value=false}
}
function formatSql(){if(!editor.value)return;const text=editor.value.getValue().replace(/\bselect\b/ig,'SELECT').replace(/\bfrom\b/ig,'FROM').replace(/\bwhere\b/ig,'WHERE').replace(/\bleft join\b/ig,'LEFT JOIN').replace(/\bjoin\b/ig,'JOIN').replace(/\binsert into\b/ig,'INSERT INTO').replace(/\bgroup by\b/ig,'GROUP BY').replace(/\border by\b/ig,'ORDER BY');editor.value.setValue(text);ElMessage.success('SQL 已格式化')}
async function loadDatabases(preferred?:string){if(!sourceId.value)return;try{const rows=await metadataApi.databases(sourceId.value,'STARROCKS');databases.value=rows.map(x=>x.name);database.value=preferred&&databases.value.includes(preferred)?preferred:(databases.value.includes(database.value)?database.value:databases.value[0]||'');await loadTables()}catch{databases.value=[]}}
async function loadTables(){if(!sourceId.value||!database.value){tables.value=[];return}try{tables.value=await metadataApi.tables(sourceId.value,database.value);inferTargetTable()}catch{tables.value=[]}}
function inferTargetTable(){if(!file.value||!tables.value.length)return;const sql=(editor.value?.getValue()||file.value.content||'');const m=sql.match(/insert\s+(?:overwrite\s+)?into\s+(?:table\s+)?(?:[`\w]+\.)?[`]?([\w]+)[`]?/i);if(m&&tables.value.some(x=>x.name===m[1]))selectedTableName.value=m[1];else if(!selectedTableName.value)selectedTableName.value=tables.value[0]?.name||''}
async function loadVersions(){if(!file.value){versions.value=[];return}try{versions.value=(await developmentApi.versions(file.value.id)).sort((a,b)=>b.versionNo-a.versionNo)}catch{versions.value=[]}}
async function loadLineage(){if(!selectedTableName.value){lineage.value=[];return}try{lineage.value=await metadataApi.lineage(selectedTableName.value)}catch{lineage.value=[]}}
function openCompare(version?:FileVersion){compareTarget.value=version||versions.value.find(x=>x.publishFlag)||versions.value.find(x=>x.versionNo<currentVersion.value)||null;if(!compareTarget.value)return ElMessage.info('暂无可比较的历史版本');compareDialog.value=true}
async function restoreVersion(v:FileVersion){if(!file.value)return;try{await ElMessageBox.confirm(`确认基于 V${v.versionNo} 恢复为新的开发版本？`,'恢复版本',{type:'warning'});file.value=await developmentApi.saveFile(file.value.id,{content:v.content,name:file.value.name,description:taskDescription.value});savedContent.set(file.value.id,file.value.content);switchingEditor=true;editor.value?.setValue(file.value.content);switchingEditor=false;setDirty(file.value.id,false);const idx=openFiles.value.findIndex(x=>x.id===file.value!.id);if(idx>=0)openFiles.value[idx]=file.value;await loadVersions();ElMessage.success(`已恢复为新的开发版本 V${file.value.currentVersion}`)}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}}
function versionState(v:FileVersion){if(v.versionNo===currentVersion.value)return '当前开发';if(v.publishFlag)return '生产版本';return '历史版本'}
function fmtTime(v?:string){return v?v.replace('T',' ').slice(0,19):'—'}
watch(projectId,()=>{if(!restoring.value)void loadDirectory()});watch(sourceId,()=>{if(!restoring.value)void loadDatabases()});watch(database,()=>{if(!restoring.value){void loadTables();persistState()}});watch(activeBottom,v=>{persistState();if(v==='versions')void loadVersions();if(v==='lineage')void loadLineage()});watch(()=>file.value?.id,persistState);watch(projectId,persistState);watch(sourceId,persistState)
onMounted(boot);onBeforeUnmount(()=>{cacheActiveEditor();persistState();editor.value?.dispose()})
</script>

<template>
<div class="dev-ide" v-loading="loading">
  <aside class="dev-sidebar" v-loading="filesLoading">
    <div class="side-head"><strong>开发目录</strong><el-button type="primary" size="small" @click="openNew"><el-icon><Plus/></el-icon>新建</el-button></div>
    <div class="side-search"><el-input v-model="resourceSearch" size="small" placeholder="搜索开发任务..." :prefix-icon="Search" clearable/></div>
    <div class="group-switch"><button :class="{active:groupMode==='all'}" @click="groupMode='all'">全部</button><button :class="{active:groupMode==='recent'}" @click="groupMode='recent'">最近打开</button><button :class="{active:groupMode==='mine'}" @click="groupMode='mine'">我的任务</button></div>
    <div class="task-tree">
      <template v-for="group in groupedFiles" :key="group.name">
        <div class="layer-row"><span class="chev">⌄</span><el-icon><FolderOpened/></el-icon><b>{{group.name}}</b></div>
        <button v-for="row in group.files" :key="row.id" class="task-row" :class="{selected:file?.id===row.id}" @click="openFile(row)"><span class="sql-icon">SQL</span><span>{{row.name.replace(/\.sql$/i,'')}}</span></button>
      </template>
      <div v-if="!groupedFiles.length" class="tree-empty">暂无匹配的开发任务</div>
    </div>
    <div class="side-foot">{{currentProject?.name||'数据开发'}} · 默认工作目录</div>
  </aside>

  <main class="ide-main">
    <div class="file-tabs"><button v-for="tab in openFiles" :key="tab.id" :class="['file-tab',{active:file?.id===tab.id,dirty:dirtyFileIds.has(tab.id)}]" @click="openFile(tab)"><span class="dot"></span><span>{{tab.name}}</span><el-icon class="tab-close" @click="closeTab(tab,$event)"><Close/></el-icon></button><button class="plus-tab" @click="openNew">＋</button></div>

    <div class="editor-toolbar">
      <div class="file-meta"><strong>{{file?.name?.replace(/\.sql$/i,'')||'未打开任务'}}</strong><span v-if="file" class="badge dev">开发 V{{currentVersion}}</span><span v-if="file&&productionVersion" class="badge prod">生产 V{{productionVersion}}</span><span v-if="file" :class="['badge',hasUnpublished?'dirty':'prod']">{{hasUnpublished?'● 有未发布变更':'✓ 已发布'}}</span></div>
      <div class="toolbar-actions"><el-button size="small" :loading="saving" @click="save">保存 <span class="shortcut">Ctrl+S</span></el-button><el-button size="small" class="run-btn" :loading="running" @click="run(false)"><el-icon><VideoPlay/></el-icon>运行 <span class="shortcut">Ctrl+Enter</span></el-button><el-button size="small" @click="formatSql">格式化</el-button><el-button size="small" @click="taskDrawerOpen=!taskDrawerOpen">任务信息</el-button><el-button size="small" @click="openCompare()">版本比较</el-button><el-button size="small" type="primary" @click="openPublish">提交发布</el-button></div>
    </div>

    <div class="editor-area">
      <div class="editor-wrap"><div ref="editorHost" class="editor-host"/><div v-if="editorLoading&&!editor" class="editor-loading">正在加载 SQL 编辑器…</div></div>
      <aside class="task-drawer" :class="{open:taskDrawerOpen}"><div class="drawer-head"><strong>任务信息</strong><button @click="taskDrawerOpen=false">×</button></div><div class="drawer-body">
        <dl class="kv"><dt>任务名称</dt><dd>{{file?.name?.replace(/\.sql$/i,'')||'—'}}</dd><dt>任务类型</dt><dd>StarRocks SQL</dd><dt>负责人</dt><dd>{{currentProject?.ownerName||'—'}}</dd><dt>开发版本</dt><dd>V{{currentVersion||'—'}}</dd><dt>生产版本</dt><dd>{{productionVersion?'V'+productionVersion:'尚未发布'}}</dd><dt>更新时间</dt><dd>{{fmtTime(file?.updatedAt)}}</dd></dl>
        <div class="divider"></div><label>任务描述</label><el-input v-model="taskDescription" type="textarea" :rows="3" placeholder="填写任务用途，便于后续维护"/>
        <div class="divider"></div><label>StarRocks 数据源</label><el-select v-model="sourceId" size="small" style="width:100%" placeholder="选择数据源"><el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id"/></el-select><label>Database</label><el-select v-model="database" size="small" style="width:100%" placeholder="选择 Database"><el-option v-for="d in databases" :key="d" :label="d" :value="d"/></el-select><label>血缘目标表</label><el-select v-model="selectedTableName" filterable size="small" style="width:100%" placeholder="自动识别或手动选择"><el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name"/></el-select>
        <div class="divider"></div><div class="schedule-title">调度配置</div><div class="muted">任务调度由工作流统一编排。</div><el-button link type="primary" @click="router.push('/workflow/definitions')">查看工作流配置 <el-icon><ArrowRight/></el-icon></el-button>
      </div></aside>
    </div>

    <section class="bottom-panel">
      <div class="bottom-head"><div class="bottom-tabs"><button :class="{active:activeBottom==='result'}" @click="activeBottom='result'">运行结果</button><button :class="{active:activeBottom==='log'}" @click="activeBottom='log'">执行日志</button><button :class="{active:activeBottom==='lineage'}" @click="activeBottom='lineage'">血缘</button><button :class="{active:activeBottom==='versions'}" @click="activeBottom='versions'">版本记录</button></div><div class="run-state"><template v-if="running">⏳ 正在执行...</template><template v-else-if="result">{{result.status==='SUCCESS'?'✓':'!'}} {{result.status==='SUCCESS'?'最近执行成功':result.status}} · {{result.elapsedMs}} ms · {{result.rowCount}} 行</template><template v-else>尚未执行</template></div></div>
      <div class="bottom-content">
        <div v-if="activeBottom==='result'" class="result-panel"><el-table v-if="result?.rows?.length" :data="result.rows" height="100%" size="small"><el-table-column v-for="c in result.columns" :key="c" :prop="c" :label="c" min-width="130" show-overflow-tooltip/></el-table><div v-else class="empty-panel">运行 SQL 后在这里查看结果</div></div>
        <pre v-else-if="activeBottom==='log'" class="log-panel">{{output||'暂无执行日志'}}</pre>
        <div v-else-if="activeBottom==='lineage'" class="lineage-panel"><template v-if="lineage.length"><div v-for="x in lineage" :key="x.id" class="lineage-flow"><div class="lineage-node">{{x.sourceTable}}<small>上游</small></div><span>→</span><div class="lineage-node active">{{x.targetTable}}<small>{{x.relationType}}</small></div></div></template><div v-else class="empty-panel">在“任务信息”中选择目标表后查看血缘</div></div>
        <div v-else class="versions-panel"><div class="version-row head"><span>版本</span><span>状态</span><span>时间</span><span>变更</span><span>操作</span></div><div v-for="v in versions" :key="v.id" class="version-row"><b>V{{v.versionNo}}</b><span><i :class="['vtag',v.publishFlag?'prod':v.versionNo===currentVersion?'current':'saved']">{{versionState(v)}}</i></span><span>—</span><span>{{v.checksum?.slice(0,12)||'已保存版本'}}</span><span><a @click="openCompare(v)">比较</a><a v-if="v.versionNo!==currentVersion" @click="restoreVersion(v)">恢复</a></span></div><div v-if="!versions.length" class="empty-panel">暂无版本记录</div></div>
      </div>
    </section>

    <div class="statusbar"><div><span><i :class="['conn-dot',{off:!sourceId}]"/>{{sourceId?'StarRocks 已连接':'StarRocks 未选择'}}</span><span>{{connectionLabel}}</span><span>Ln {{cursorLine}}, Col {{cursorCol}}</span></div><div><span>UTF-8</span><span>SQL</span><span>自动保存：关闭</span></div></div>
  </main>

  <el-dialog v-model="newDialog" title="新建开发任务" width="560px"><el-form label-position="top"><div class="form-grid"><el-form-item label="任务名称 *"><el-input v-model="newForm.name" placeholder="例如：dwd_order_item"/></el-form-item><el-form-item label="任务类型 *"><el-input value="StarRocks SQL" disabled/></el-form-item><el-form-item label="目录"><el-select v-model="newForm.folderId" style="width:100%" clearable placeholder="选择目录"><el-option v-for="f in folders" :key="f.id" :label="f.name" :value="f.id"/></el-select></el-form-item><el-form-item label="负责人"><el-input :value="currentProject?.ownerName||'当前用户'" disabled/></el-form-item></div><el-form-item label="任务描述"><el-input v-model="newForm.description" type="textarea" :rows="3" placeholder="填写任务用途，便于后续维护"/></el-form-item></el-form><template #footer><el-button @click="newDialog=false">取消</el-button><el-button type="primary" @click="createTask">创建并打开</el-button></template></el-dialog>

  <el-dialog v-model="publishDialog" title="提交发布" width="620px"><div class="publish-summary"><div><span>任务名称</span><b>{{file?.name||'—'}}</b></div><div><span>发布版本</span><b>V{{currentVersion}} → 生产</b></div></div><div class="publish-check"><strong>发布前确认</strong><p>当前开发版本已保存，提交后{{approvalRequired?'将进入发布中心等待审批':'将按照当前发布策略处理'}}。</p><p v-if="productionVersion">当前生产版本：V{{productionVersion}}</p><p v-else>该任务尚无生产版本。</p></div><template #footer><el-button @click="publishDialog=false">取消</el-button><el-button type="primary" @click="confirmPublish">确认发布</el-button></template></el-dialog>

  <el-dialog v-model="compareDialog" title="版本比较" width="860px"><div class="compare-tip">V{{compareTarget?.versionNo||'-'}} 与当前开发版本 V{{currentVersion}} 的 SQL 内容对比</div><div class="diff-grid"><div><div class="diff-head">历史 V{{compareTarget?.versionNo||'-'}}</div><pre>{{compareTarget?.content||''}}</pre></div><div><div class="diff-head">当前开发 V{{currentVersion}}</div><pre>{{editor?.getValue()||file?.content||''}}</pre></div></div><template #footer><el-button @click="compareDialog=false">关闭</el-button><el-button v-if="compareTarget" type="primary" @click="restoreVersion(compareTarget!);compareDialog=false">恢复此版本</el-button></template></el-dialog>
</div>
</template>

<style scoped>
.dev-ide{height:calc(100vh - var(--ds-topbar));min-height:650px;display:flex;background:#fff;color:#18202b;overflow:hidden}.dev-sidebar{width:272px;min-width:230px;display:flex;flex-direction:column;border-right:1px solid #e5e7eb;background:#fff}.side-head{height:48px;display:flex;align-items:center;justify-content:space-between;padding:0 12px 0 14px;border-bottom:1px solid #e5e7eb}.side-head strong{font-size:14px}.side-search{padding:10px 12px}.group-switch{display:flex;gap:4px;padding:0 12px 8px}.group-switch button{border:0;background:transparent;color:#667085;border-radius:5px;padding:6px 9px;font-size:12px;cursor:pointer}.group-switch button.active{background:#eef4ff;color:#2f6fed;font-weight:600}.task-tree{flex:1;min-height:0;overflow:auto;padding:4px 6px 14px}.layer-row,.task-row{height:30px;display:flex;align-items:center;gap:7px;border:0;border-radius:5px;background:transparent;color:#344054;font-size:12px}.layer-row{padding:0 8px}.layer-row .chev{width:12px;color:#8b95a5}.layer-row .el-icon{color:#4d8bf5}.task-row{width:100%;padding:0 8px 0 26px;text-align:left;cursor:pointer}.task-row:hover{background:#f5f7fa}.task-row.selected{background:#eaf1ff;color:#205ec9}.task-row span:last-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.sql-icon{width:22px;height:18px;display:grid;place-items:center;border:1px solid #b8c7e4;border-radius:4px;background:#f6f9ff;color:#2f6fed;font-size:8px}.tree-empty{padding:28px 12px;color:#98a2b3;font-size:12px;text-align:center}.side-foot{height:38px;display:flex;align-items:center;padding:0 12px;border-top:1px solid #e5e7eb;color:#8a93a0;font-size:11px}.ide-main{flex:1;min-width:0;display:flex;flex-direction:column;background:#fff}.file-tabs{height:38px;display:flex;align-items:stretch;border-bottom:1px solid #e5e7eb;background:#f8fafc;overflow-x:auto}.file-tab{min-width:190px;max-width:240px;display:flex;align-items:center;gap:7px;padding:0 10px;border:0;border-right:1px solid #e5e7eb;background:#f8fafc;color:#475467;font-size:12px;position:relative;cursor:pointer}.file-tab.active{background:#fff;color:#1d2939}.file-tab.active:after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:2px;background:#2f6fed}.file-tab .dot{width:6px;height:6px;border-radius:50%;background:#c9d1dd}.file-tab.dirty .dot{background:#f59e0b}.tab-close{margin-left:auto;color:#98a2b3;font-size:12px}.plus-tab{width:38px;border:0;background:transparent;color:#667085;font-size:18px}.plus-tab:hover{background:#eef2f6}.editor-toolbar{height:46px;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:0 12px;border-bottom:1px solid #e5e7eb}.file-meta{display:flex;align-items:center;gap:8px;min-width:0}.file-meta strong{max-width:230px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:13px}.badge{padding:3px 7px;border:1px solid;border-radius:999px;font-size:11px;white-space:nowrap}.badge.dev{color:#245fcb;background:#eef4ff;border-color:#cfe0ff}.badge.prod{color:#23724e;background:#edf8f2;border-color:#caecd9}.badge.dirty{color:#9a5a08;background:#fff5e8;border-color:#f6d7ae}.toolbar-actions{display:flex;align-items:center;gap:7px;white-space:nowrap}.toolbar-actions :deep(.el-button + .el-button){margin-left:0}.run-btn{color:#245fcb!important;border-color:#b9ccef!important;background:#f7faff!important}.shortcut{margin-left:4px;color:#98a2b3;font-size:10px}.editor-area{flex:1;min-height:0;display:flex}.editor-wrap{position:relative;flex:1;min-width:0;background:#fbfcfe}.editor-host{height:100%}.editor-loading{position:absolute;inset:0;display:grid;place-items:center;background:#fff;color:#98a2b3;font-size:12px}.task-drawer{width:0;overflow:hidden;border-left:0 solid #e5e7eb;background:#fff;transition:width .18s ease;display:flex;flex-direction:column}.task-drawer.open{width:330px;border-left-width:1px}.drawer-head{height:44px;flex:0 0 44px;display:flex;align-items:center;justify-content:space-between;padding:0 13px;border-bottom:1px solid #e5e7eb}.drawer-head strong{font-size:13px}.drawer-head button{border:0;background:transparent;color:#98a2b3;font-size:18px}.drawer-body{min-width:329px;overflow:auto;padding:14px}.drawer-body label{display:block;margin:12px 0 6px;color:#667085;font-size:11px}.kv{display:grid;grid-template-columns:88px 1fr;gap:8px 10px;font-size:12px;margin:0}.kv dt{color:#8a93a0}.kv dd{margin:0;color:#344054;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.divider{height:1px;background:#e5e7eb;margin:14px 0}.schedule-title{font-size:12px;font-weight:600;margin-bottom:7px}.muted{color:#8a93a0;font-size:11px}.bottom-panel{height:220px;flex:0 0 220px;display:flex;flex-direction:column;border-top:1px solid #e5e7eb;background:#fff}.bottom-head{height:36px;flex:0 0 36px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e5e7eb}.bottom-tabs{height:100%;display:flex}.bottom-tabs button{height:100%;padding:0 14px;border:0;background:#fff;color:#667085;font-size:12px;position:relative;cursor:pointer}.bottom-tabs button.active{color:#245fcb;font-weight:600}.bottom-tabs button.active:after{content:'';position:absolute;left:10px;right:10px;bottom:-1px;height:2px;background:#2f6fed}.run-state{padding-right:12px;color:#667085;font-size:11px}.bottom-content{flex:1;min-height:0;overflow:hidden}.result-panel,.lineage-panel,.versions-panel{height:100%;overflow:auto}.empty-panel{height:100%;display:grid;place-items:center;color:#98a2b3;font-size:12px}.log-panel{height:100%;margin:0;overflow:auto;padding:10px 14px;color:#455468;background:#fbfcfe;font:12px/22px Consolas,monospace;white-space:pre-wrap}.lineage-panel{display:flex;align-items:center;justify-content:center;padding:14px}.lineage-flow{display:flex;align-items:center;gap:14px}.lineage-node{min-width:150px;padding:11px 12px;border:1px solid #d7dce3;border-radius:8px;background:#fff;font-size:12px}.lineage-node.active{border-color:#b9ccef;background:#f7faff}.lineage-node small{display:block;margin-top:5px;color:#8a93a0;font-size:10px}.version-row{display:grid;grid-template-columns:72px 105px 150px minmax(200px,1fr) 130px;align-items:center;gap:8px;min-height:36px;padding:0 12px;border-bottom:1px solid #eef1f4;font-size:12px}.version-row.head{background:#fafbfc;color:#8a93a0}.vtag{display:inline-block;padding:2px 6px;border-radius:999px;font-style:normal;font-size:10px}.vtag.current{background:#fff5e8;color:#9a5a08}.vtag.prod{background:#edf8f2;color:#23724e}.vtag.saved{background:#eef4ff;color:#245fcb}.version-row a{margin-right:10px;color:#2f6fed;cursor:pointer}.statusbar{height:28px;flex:0 0 28px;display:flex;align-items:center;justify-content:space-between;padding:0 10px;border-top:1px solid #e5e7eb;background:#f8fafc;color:#667085;font-size:11px}.statusbar>div{display:flex;align-items:center;gap:16px}.conn-dot{width:7px;height:7px;display:inline-block;margin-right:5px;border-radius:50%;background:#26a269}.conn-dot.off{background:#98a2b3}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.publish-summary{display:grid;grid-template-columns:1fr 1fr;gap:12px}.publish-summary>div{padding:12px;border:1px solid #e5e7eb;border-radius:7px;background:#fafbfc}.publish-summary span,.publish-summary b{display:block}.publish-summary span{margin-bottom:5px;color:#8a93a0;font-size:11px}.publish-summary b{font-size:13px}.publish-check{margin-top:14px;padding:12px;border:1px solid #e5e7eb;border-radius:7px}.publish-check p{margin:7px 0 0;color:#667085;font-size:12px}.compare-tip{margin-bottom:10px;color:#667085;font-size:12px}.diff-grid{display:grid;grid-template-columns:1fr 1fr;border:1px solid #e5e7eb;border-radius:7px;overflow:hidden}.diff-grid>div+div{border-left:1px solid #e5e7eb}.diff-head{height:32px;padding:8px 10px;border-bottom:1px solid #e5e7eb;background:#fafbfc;color:#667085;font-size:11px}.diff-grid pre{height:360px;margin:0;overflow:auto;padding:10px;background:#fbfcfe;color:#344054;font:11px/22px Consolas,monospace;white-space:pre-wrap}@media(max-width:1200px){.dev-sidebar{width:235px}.toolbar-actions .shortcut{display:none}.task-drawer.open{width:300px}.drawer-body{min-width:299px}}
</style>
