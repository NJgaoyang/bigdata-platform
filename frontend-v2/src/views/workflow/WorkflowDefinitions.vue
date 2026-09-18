<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Graph } from '@antv/x6'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '../../components/PageHeader.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import { developmentApi, releaseApi, workflowApi, type DevFile, type DevelopmentSchedule, type WorkflowNode, type WorkflowView } from '../../api/domain'

type NodeKind='SQL'|'PYTHON'|'SHELL'|'SEATUNNEL'|'CONDITION'
type NodeData={name:string;nodeType:NodeKind;devFileId?:number;configJson:string;taskVersion?:number;lifecycleStatus?:string;ownerName?:string}

const route=useRoute(),router=useRouter()
const loading=ref(true),rows=ref<WorkflowView[]>([]),editorOpen=ref(false),saving=ref(false),dirty=ref(false)
const current=ref<WorkflowView|null>(null),files=ref<DevFile[]>([]),selectedCode=ref(''),selectedSchedule=ref<DevelopmentSchedule>()
const taskSearch=ref(''),canvasRef=ref<HTMLDivElement>(),selectedEdgeId=ref('')
const form=reactive({name:'',description:''})
const nodeDraft=reactive<NodeData>({name:'',nodeType:'SQL',configJson:'{}'})
const stats=reactive({nodes:0,edges:0,roots:0,leaves:0})
let graph:Graph|undefined

const sqlFiles=computed(()=>files.value.filter(f=>f.fileType?.toUpperCase()==='SQL').filter(f=>{
  const q=taskSearch.value.trim().toLowerCase();return !q||f.name.toLowerCase().includes(q)||(f.ownerName||'').toLowerCase().includes(q)
}))
const selectedFile=computed(()=>nodeDraft.devFileId?files.value.find(f=>f.id===nodeDraft.devFileId):undefined)
const selectedIsTask=computed(()=>Boolean(nodeDraft.devFileId))
const workflowVersion=computed(()=>current.value?.publishedVersion||0)

function stripExt(v:string){return v.replace(/\.(sql|sh|py)$/i,'')}
function msg(e:unknown){return e instanceof Error?e.message:'操作失败'}
function typeLabel(v?:string){return ({SQL:'SQL',PYTHON:'Python',SHELL:'Shell',SEATUNNEL:'SeaTunnel',CONDITION:'条件'} as Record<string,string>)[v||'']||v||'任务'}
function lifecycleLabel(v?:string){return v==='ONLINE'?'已上线':'未上线'}
function taskMeta(file?:DevFile){return file?`${typeLabel(file.fileType)} · V${file.currentVersion} · ${lifecycleLabel(file.lifecycleStatus)}`:'开发任务'}
function cycleLabel(v?:string){return ({DAILY:'每日',HOURLY:'每小时',WEEKLY:'每周',MONTHLY:'每月',CRON:'Cron'} as Record<string,string>)[v||'']||v||'—'}

async function load(){loading.value=true;try{rows.value=await workflowApi.list()}catch(e){ElMessage.error(msg(e))}finally{loading.value=false}}
async function loadFiles(){try{const ps=await developmentApi.projects();files.value=(await Promise.all(ps.map(p=>developmentApi.files(p.id)))).flat()}catch(e){ElMessage.error('开发任务加载失败：'+msg(e))}}
async function boot(){await Promise.all([load(),loadFiles()]);const focus=Number(route.query.fileId||0);if(focus){const match=rows.value.find(w=>w.nodes.some(n=>n.devFileId===focus));if(match)await edit(match,focus);else ElMessage.info('当前任务尚未加入任何工作流')}}

function newWorkflow(){current.value=null;form.name='';form.description='';selectedCode.value='';selectedSchedule.value=undefined;editorOpen.value=true;dirty.value=false;void nextTick(()=>initGraph())}
async function edit(row:WorkflowView,focusFileId?:number){try{current.value=await workflowApi.get(row.id);form.name=current.value.name;form.description=current.value.description||'';selectedCode.value='';selectedSchedule.value=undefined;editorOpen.value=true;dirty.value=false;await nextTick();initGraph(current.value);if(focusFileId){const target=graph?.getNodes().find(n=>(n.getData() as NodeData)?.devFileId===focusFileId);if(target)await selectNode(String(target.id))}}catch(e){ElMessage.error(msg(e))}}
function closeEditor(){graph?.dispose();graph=undefined;editorOpen.value=false;selectedCode.value='';selectedSchedule.value=undefined;selectedEdgeId.value='';if(route.query.fileId)void router.replace({path:'/workflow/definitions'})}

function initGraph(w?:WorkflowView|null){
  graph?.dispose();if(!canvasRef.value)return
  graph=new Graph({container:canvasRef.value,autoResize:true,background:{color:'#f8fafc'},grid:{visible:true,size:20,type:'dot',args:{color:'#dfe5ec'}},panning:true,mousewheel:{enabled:true,modifiers:['ctrl','meta'],minScale:.5,maxScale:1.5},connecting:{router:'orth',connector:'rounded',anchor:'center',connectionPoint:'boundary',allowBlank:false,allowLoop:false,allowMulti:false,snap:true,createEdge(){return graph!.createEdge({zIndex:0,attrs:{line:{stroke:'#8492a6',strokeWidth:1.35,targetMarker:{name:'block',width:7,height:7}}}})}}})
  if(w){const idToCode=new Map(w.nodes.map(n=>[n.id,n.nodeCode]));w.nodes.forEach(n=>addGraphNode(n,false));w.edges.forEach(e=>{const s=idToCode.get(e.sourceNodeId),t=idToCode.get(e.targetNodeId);if(s&&t)graph?.addEdge({source:{cell:s,port:'out'},target:{cell:t,port:'in'},zIndex:0,attrs:{line:{stroke:'#8492a6',strokeWidth:1.35,targetMarker:{name:'block',width:7,height:7}}}})})}
  graph.on('node:click',({node})=>void selectNode(String(node.id)))
  graph.on('blank:click',()=>{selectedCode.value='';selectedSchedule.value=undefined;selectedEdgeId.value='';refreshSelection()})
  graph.on('node:moved',()=>{dirty.value=true;updateStats()})
  graph.on('edge:connected',({edge})=>{const target=graph?.getCellById(String(edge.getTargetCellId()));const data=target?.isNode()?(target.getData() as NodeData):undefined;const file=data?.devFileId?files.value.find(f=>f.id===data.devFileId):undefined;if(file?.lifecycleStatus==='ONLINE'){graph?.removeCell(edge);ElMessage.warning(`下游任务“${stripExt(file.name)}”已上线，请先下线后再修改依赖`);return}dirty.value=true;updateStats()})
  graph.on('edge:click',({edge})=>{selectedEdgeId.value=String(edge.id);selectedCode.value='';selectedSchedule.value=undefined;refreshSelection()})
  graph.on('edge:dblclick',({edge})=>{graph?.removeCell(edge);selectedEdgeId.value='';dirty.value=true;updateStats()})
  graph.on('edge:removed',()=>updateStats())
  dirty.value=false;updateStats();setTimeout(()=>graph?.zoomToFit({padding:54,maxScale:1}),0)
}

function addGraphNode(n?:Partial<WorkflowNode>,select=true){if(!graph)return;const devFile=n?.devFileId?files.value.find(f=>f.id===n.devFileId):undefined;const code=n?.nodeCode||`node_${Date.now()}_${Math.random().toString(16).slice(2,7)}`;const nodeType=(n?.nodeType||devFile?.fileType||'SQL') as NodeKind;const data:NodeData={name:n?.name||stripExt(devFile?.name||'新节点'),nodeType,devFileId:n?.devFileId,configJson:n?.configJson||'{}',taskVersion:devFile?.currentVersion,lifecycleStatus:devFile?.lifecycleStatus,ownerName:devFile?.ownerName};const task=Boolean(data.devFileId);const meta=task?taskMeta(devFile):typeLabel(nodeType)
  graph.addNode({id:code,x:n?.x??100+(graph.getNodes().length%3)*245,y:n?.y??90+Math.floor(graph.getNodes().length/3)*120,width:210,height:72,data,markup:[{tagName:'rect',selector:'body'},{tagName:'text',selector:'title'},{tagName:'text',selector:'meta'}],attrs:{body:{fill:'#fff',stroke:nodeType==='CONDITION'?'#b7bdc8':'#b8c8df',strokeWidth:1,rx:7,ry:7,strokeDasharray:nodeType==='CONDITION'?'4 3':''},title:{text:data.name,refX:14,refY:25,fontSize:13,fontWeight:600,fill:'#27364a',textAnchor:'start'},meta:{text:meta,refX:14,refY:49,fontSize:11,fill:'#7b8798',textAnchor:'start'}},ports:{groups:{in:{position:'left',attrs:{circle:{r:4,magnet:true,stroke:'#8294ae',strokeWidth:1.2,fill:'#fff'}}},out:{position:'right',attrs:{circle:{r:4,magnet:true,stroke:'#8294ae',strokeWidth:1.2,fill:'#fff'}}}},items:[{id:'in',group:'in'},{id:'out',group:'out'}]}})
  if(select)void selectNode(code);updateStats()
}
function addTask(file:DevFile){if(!graph)return;const existing=graph.getNodes().find(n=>(n.getData() as NodeData)?.devFileId===file.id);if(existing){void selectNode(String(existing.id));ElMessage.info('该任务已在当前工作流中');return}addGraphNode({name:stripExt(file.name),nodeType:'SQL',devFileId:file.id,nodeCode:`task_${file.id}`});dirty.value=true}
function addKind(type:'SEATUNNEL'|'CONDITION'){addGraphNode({name:type==='CONDITION'?'条件分支':'SeaTunnel 节点',nodeType:type});dirty.value=true}

async function selectNode(code:string){selectedCode.value=code;selectedEdgeId.value='';const n=graph?.getCellById(code);if(!n||!n.isNode())return;const d=n.getData() as NodeData;Object.assign(nodeDraft,{name:d.name||'',nodeType:d.nodeType||'SQL',devFileId:d.devFileId,configJson:d.configJson||'{}',taskVersion:d.taskVersion,lifecycleStatus:d.lifecycleStatus,ownerName:d.ownerName});refreshSelection();selectedSchedule.value=undefined;if(d.devFileId){const key=code;try{const sc=await developmentApi.schedule(d.devFileId);if(selectedCode.value===key)selectedSchedule.value=sc}catch{}}}
function refreshSelection(){graph?.getNodes().forEach(n=>n.attr('body/stroke',String(n.id)===selectedCode.value?'#4f7fc8':((n.getData() as NodeData)?.nodeType==='CONDITION'?'#b7bdc8':'#b8c8df')));graph?.getEdges().forEach(e=>e.attr('line/stroke',String(e.id)===selectedEdgeId.value?'#4f7fc8':'#8492a6'))}
function applyNode(){const n=graph?.getCellById(selectedCode.value);if(!n||!n.isNode()||selectedIsTask.value)return;const d={...(n.getData() as NodeData),name:nodeDraft.name,nodeType:nodeDraft.nodeType,configJson:nodeDraft.configJson};n.setData(d);n.attr('title/text',d.name);n.attr('meta/text',typeLabel(d.nodeType));dirty.value=true;ElMessage.success('节点配置已更新')}
function removeNode(){const n=graph?.getCellById(selectedCode.value);if(!n)return;graph?.removeCell(n);selectedCode.value='';selectedSchedule.value=undefined;dirty.value=true;updateStats()}
function removeSelectedEdge(){const e=selectedEdgeId.value&&graph?.getCellById(selectedEdgeId.value);if(e&&e.isEdge()){graph?.removeCell(e);selectedEdgeId.value='';dirty.value=true;updateStats()}}

function payload(){const nodes=(graph?.getNodes()||[]).map(n=>{const p=n.getPosition(),d=n.getData() as NodeData;return{name:d.name,nodeType:d.nodeType,devFileId:d.devFileId,configJson:d.configJson||'{}',x:Math.round(p.x),y:Math.round(p.y),nodeCode:String(n.id)}});const edges=(graph?.getEdges()||[]).map(e=>({sourceNodeCode:String(e.getSourceCellId()),targetNodeCode:String(e.getTargetCellId())}));return{name:form.name.trim(),description:form.description.trim(),nodes,edges}}
async function save(){if(!form.name.trim())return ElMessage.warning('请输入工作流名称');if(!(graph?.getNodes().length))return ElMessage.warning('请至少添加一个任务节点');saving.value=true;try{const saved=current.value?await workflowApi.update(current.value.id,payload()):await workflowApi.create(payload());current.value=saved;await Promise.all([load(),loadFiles()]);current.value=await workflowApi.get(saved.id);form.name=current.value.name;form.description=current.value.description||'';initGraph(current.value);dirty.value=false;ElMessage.success('工作流已保存，任务依赖已同步')}catch(e){ElMessage.error(msg(e))}finally{saving.value=false}}
async function publish(){if(dirty.value||!current.value){await save();if(!current.value||dirty.value)return}try{const req=await releaseApi.request({resourceType:'WORKFLOW',resourceId:current.value.id,resourceName:form.name,requestedVersion:current.value.publishedVersion+1});if(req.status==='RELEASED')ElMessage.success('工作流已发布');else if(req.status==='PENDING_APPROVAL')ElMessage.info('发布申请已提交，等待审批');else ElMessage.info(`发布状态：${req.status}`);await load();current.value=await workflowApi.get(current.value.id);initGraph(current.value)}catch(e){ElMessage.error(msg(e))}}
async function run(){if(!current.value)return ElMessage.warning('请先保存工作流');try{await workflowApi.run(current.value.id);ElMessage.success('运行实例已提交')}catch(e){ElMessage.error(msg(e))}}
async function remove(r:WorkflowView){try{await ElMessageBox.confirm(`删除“${r.name}”？`,'删除工作流',{type:'warning'});await workflowApi.remove(r.id);await load()}catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error(msg(e))}}

function autoLayout(){if(!graph)return;const nodes=graph.getNodes(),edges=graph.getEdges();const indegree=new Map<string,number>(),out=new Map<string,string[]>();nodes.forEach(n=>{indegree.set(String(n.id),0);out.set(String(n.id),[])});edges.forEach(e=>{const s=String(e.getSourceCellId()),t=String(e.getTargetCellId());if(indegree.has(t)&&out.has(s)){indegree.set(t,(indegree.get(t)||0)+1);out.get(s)!.push(t)}});const queue=[...indegree.entries()].filter(([,v])=>v===0).map(([k])=>k),level=new Map<string,number>();queue.forEach(k=>level.set(k,0));while(queue.length){const id=queue.shift()!,l=level.get(id)||0;for(const n of out.get(id)||[]){level.set(n,Math.max(level.get(n)||0,l+1));indegree.set(n,(indegree.get(n)||1)-1);if(indegree.get(n)===0)queue.push(n)}}nodes.forEach(n=>{if(!level.has(String(n.id)))level.set(String(n.id),0)});const layers=new Map<number,string[]>();for(const [id,l] of level)layers.set(l,[...(layers.get(l)||[]),id]);for(const [l,ids] of layers)ids.forEach((id,i)=>{const cell=graph?.getCellById(id);if(cell?.isNode())cell.setPosition(100+l*270,80+i*118)});dirty.value=true;updateStats();setTimeout(()=>graph?.zoomToFit({padding:60,maxScale:1}),0)}
function updateStats(){if(!graph){Object.assign(stats,{nodes:0,edges:0,roots:0,leaves:0});return}const nodes=graph.getNodes(),edges=graph.getEdges(),inSet=new Set(edges.map(e=>String(e.getTargetCellId()))),outSet=new Set(edges.map(e=>String(e.getSourceCellId())));Object.assign(stats,{nodes:nodes.length,edges:edges.length,roots:nodes.filter(n=>!inSet.has(String(n.id))).length,leaves:nodes.filter(n=>!outSet.has(String(n.id))).length})}
function zoom(delta:number){graph?.zoom(delta)}
function fit(){graph?.zoomToFit({padding:60,maxScale:1})}
function openDevelopment(){if(nodeDraft.devFileId)void router.push({path:'/development/workspace',query:{fileId:String(nodeDraft.devFileId)}})}

onMounted(boot);onBeforeUnmount(()=>graph?.dispose())
</script>

<template>
<div v-if="!editorOpen" class="ds-page workflow-list-page">
  <PageHeader title="工作流" subtitle="统一编排开发任务依赖；SQL 任务依赖与数据开发实时共用同一份配置。"><template #actions><el-button type="primary" @click="newWorkflow">+ 新建工作流</el-button></template></PageHeader>
  <div class="ds-card workflow-table-card">
    <div class="ds-toolbar"><strong>工作流定义</strong><span class="toolbar-note">任务版本统一为 Vx，工作流不再绑定单独 SQL 快照</span><div class="ds-spacer"/><el-button @click="load">刷新</el-button></div>
    <el-skeleton v-if="loading" :rows="6" animated/>
    <div v-else-if="!rows.length" class="ds-empty"><div><div class="ds-empty__title">暂无工作流</div><div>创建工作流后，从开发任务库添加任务并建立依赖关系。</div></div></div>
    <table v-else class="ds-table"><thead><tr><th>工作流名称</th><th>状态</th><th>工作流版本</th><th>任务数</th><th>依赖数</th><th>编码</th><th>操作</th></tr></thead><tbody><tr v-for="r in rows" :key="r.id"><td class="ds-resource"><span class="workflow-mark">WF</span><span class="workflow-name" @click="edit(r)">{{r.name}}</span></td><td><StatusBadge :status="r.status"/></td><td>{{r.publishedVersion?`V${r.publishedVersion}`:'未发布'}}</td><td>{{r.nodes.filter(n=>n.devFileId).length}}</td><td>{{r.edges.length}}</td><td class="muted-code">{{r.workflowCode}}</td><td><span class="ds-link" @click="edit(r)">打开</span><span class="sep">·</span><span class="ds-link" @click="workflowApi.run(r.id).then(()=>ElMessage.success('运行实例已提交')).catch(e=>ElMessage.error(msg(e)))">运行</span><span class="sep">·</span><span class="danger" @click="remove(r)">删除</span></td></tr></tbody></table>
  </div>
</div>

<div v-else class="workflow-editor">
  <header class="editor-head">
    <button class="back" @click="closeEditor">‹</button>
    <div class="editor-title"><div class="title-line"><strong>{{current?'编辑工作流':'新建工作流'}}</strong><span v-if="current" class="workflow-code">{{current.workflowCode}}</span><span v-if="dirty" class="dirty-dot">未保存</span></div><div class="title-sub">开发任务依赖在此处修改后，会同步回数据开发的“上游依赖 / 调度依赖”</div></div>
    <div class="editor-version"><span>工作流版本</span><b>{{workflowVersion?`V${workflowVersion}`:'草稿'}}</b></div>
    <div class="editor-actions"><el-button @click="autoLayout">自动布局</el-button><el-button :loading="saving" @click="save">保存</el-button><el-button v-if="current" @click="run">运行</el-button><el-button type="primary" @click="publish">发布</el-button></div>
  </header>
  <div class="workflow-meta"><el-input v-model="form.name" placeholder="工作流名称"/><el-input v-model="form.description" placeholder="工作流描述（可选）"/><div class="sync-hint"><span class="sync-dot"></span>任务依赖已与数据开发共享</div></div>
  <div class="editor-grid">
    <aside class="task-library">
      <div class="panel-head"><strong>开发任务</strong><span>{{sqlFiles.length}}</span></div>
      <div class="task-search"><el-input v-model="taskSearch" size="small" placeholder="搜索任务" :prefix-icon="Search" clearable/></div>
      <div class="library-tip">点击任务加入画布。任务只绑定自身，不再选择文件版本。</div>
      <div class="task-list"><button v-for="f in sqlFiles" :key="f.id" class="task-row" @click="addTask(f)"><span class="task-type">SQL</span><span class="task-main"><b>{{stripExt(f.name)}}</b><small>V{{f.currentVersion}} · {{f.ownerName||'—'}}</small></span><i :class="['life-dot',f.lifecycleStatus==='ONLINE'?'online':'offline']"></i></button><div v-if="!sqlFiles.length" class="empty-list">没有匹配的 SQL 任务</div></div>
      <div class="control-library"><div class="control-title">编排节点</div><button class="control-row" @click="addKind('CONDITION')"><span class="diamond">◇</span><div><b>条件分支</b><small>仅属于当前工作流</small></div></button><button class="control-row" @click="addKind('SEATUNNEL')"><span class="control-ico">⇄</span><div><b>SeaTunnel 节点</b><small>保留现有特殊编排能力</small></div></button></div>
    </aside>

    <main class="canvas-shell">
      <div class="canvas-tools"><button @click="zoom(-.1)">−</button><button @click="zoom(.1)">＋</button><button class="fit" @click="fit">适应画布</button><button v-if="selectedEdgeId" class="delete-edge" @click="removeSelectedEdge">删除连线</button></div>
      <div class="canvas-help">从节点右侧连接点拖到下游左侧建立依赖 · 双击连线可删除</div>
      <div ref="canvasRef" class="canvas"></div>
      <div class="canvas-status"><span>{{stats.nodes}} 个节点</span><span>{{stats.edges}} 条依赖</span><span>{{stats.roots}} 个起点</span><span>{{stats.leaves}} 个末端</span></div>
    </main>

    <aside class="inspector">
      <div class="panel-head"><strong>节点详情</strong></div>
      <div v-if="!selectedCode&&!selectedEdgeId" class="empty-inspector"><div class="empty-ico">⌁</div><b>选择一个节点</b><span>查看任务版本、调度与依赖信息</span></div>
      <div v-else-if="selectedEdgeId" class="edge-inspector"><div class="section-title">依赖连线</div><p>当前选中一条依赖关系。删除后保存，若两端都是开发 SQL 任务，会同步更新下游任务的上游依赖。</p><el-button type="danger" plain @click="removeSelectedEdge">删除依赖</el-button></div>
      <template v-else-if="selectedIsTask">
        <div class="task-detail-head"><span class="task-type large">{{typeLabel(selectedFile?.fileType)}}</span><div><strong>{{stripExt(selectedFile?.name||nodeDraft.name)}}</strong><small>{{selectedFile?.description||'开发任务'}}</small></div></div>
        <dl class="detail-kv"><dt>任务版本</dt><dd><b>V{{selectedFile?.currentVersion||nodeDraft.taskVersion||0}}</b></dd><dt>生命周期</dt><dd><span :class="['life-text',selectedFile?.lifecycleStatus==='ONLINE'?'online':'offline']">{{lifecycleLabel(selectedFile?.lifecycleStatus)}}</span></dd><dt>负责人</dt><dd>{{selectedFile?.ownerName||'—'}}</dd><dt>任务类型</dt><dd>{{typeLabel(selectedFile?.fileType)}}</dd></dl>
        <div v-if="selectedFile?.lifecycleStatus==='ONLINE'" class="readonly-note">任务已上线。可以查看依赖，但修改指向该任务的上游依赖前需要先在数据开发下线。</div>
        <div class="divider"></div>
        <div class="section-title"><span>调度配置</span><small>与数据开发共享</small></div>
        <template v-if="selectedSchedule"><dl class="detail-kv compact"><dt>状态</dt><dd>{{selectedSchedule.enabled?'已启用':'未启用'}}</dd><dt>周期</dt><dd>{{cycleLabel(selectedSchedule.cycleType)}} {{selectedSchedule.executionTime||''}}</dd><dt>Cron</dt><dd class="mono">{{selectedSchedule.cronExpression}}</dd><dt>Database</dt><dd>{{selectedSchedule.databaseName||'—'}}</dd></dl><div class="dependency-block"><div class="dep-title">上游依赖 <b>{{selectedSchedule.dependencies.length}}</b></div><div v-if="selectedSchedule.dependencies.length" class="dep-chips"><span v-for="d in selectedSchedule.dependencies" :key="d.fileId">{{stripExt(d.name)}}</span></div><div v-else class="dep-empty">暂无上游任务</div></div><div class="dependency-block"><div class="dep-title">下游任务 <b>{{selectedSchedule.downstream.length}}</b></div><div v-if="selectedSchedule.downstream.length" class="dep-chips"><span v-for="d in selectedSchedule.downstream" :key="d.fileId">{{stripExt(d.name)}}</span></div><div v-else class="dep-empty">暂无下游任务</div></div></template><div v-else class="loading-line">正在读取调度信息…</div>
        <div class="inspector-actions"><el-button type="primary" plain @click="openDevelopment">在数据开发中打开</el-button><el-button type="danger" text @click="removeNode">从工作流移除</el-button></div>
      </template>
      <template v-else>
        <div class="section-title">编排节点</div><el-form label-position="top"><el-form-item label="节点名称"><el-input v-model="nodeDraft.name"/></el-form-item><el-form-item label="节点类型"><el-select v-model="nodeDraft.nodeType" style="width:100%"><el-option label="条件分支" value="CONDITION"/><el-option label="SeaTunnel" value="SEATUNNEL"/></el-select></el-form-item><el-form-item label="节点配置 JSON"><el-input v-model="nodeDraft.configJson" type="textarea" :rows="8"/></el-form-item><el-button type="primary" @click="applyNode">应用配置</el-button><el-button type="danger" plain @click="removeNode">删除节点</el-button></el-form>
      </template>
    </aside>
  </div>
</div>
</template>

<style scoped>
.workflow-list-page{min-height:calc(100vh - var(--ds-topbar));}.workflow-table-card{overflow:hidden}.toolbar-note{margin-left:12px;color:#98a2b3;font-size:12px;font-weight:400}.workflow-mark{display:inline-grid;place-items:center;width:28px;height:22px;margin-right:9px;border:1px solid #cad5e4;border-radius:5px;background:#f6f8fb;color:#52657c;font-size:9px;font-weight:700}.workflow-name{cursor:pointer}.workflow-name:hover{color:#2f6fed}.muted-code{color:#8a94a5;font:11px Consolas,monospace}.sep{margin:0 7px;color:#d0d5dd}.danger{color:#d92d20;cursor:pointer}
.workflow-editor{height:calc(100vh - var(--ds-topbar));min-height:650px;display:flex;flex-direction:column;background:#f5f7fa;color:#243244;overflow:hidden}.editor-head{height:66px;flex:0 0 66px;display:flex;align-items:center;padding:0 18px;background:#fff;border-bottom:1px solid #e3e8ef}.back{width:32px;height:32px;border:1px solid #d9e0e8;border-radius:6px;background:#fff;color:#526174;font-size:25px;line-height:24px;cursor:pointer}.back:hover{background:#f7f9fc}.editor-title{margin-left:12px;min-width:0}.title-line{display:flex;align-items:center;gap:9px}.title-line strong{font-size:16px;color:#1f2d3d}.workflow-code{font:10px Consolas,monospace;color:#8491a3;background:#f3f5f8;padding:3px 6px;border-radius:4px}.dirty-dot{font-size:11px;color:#a35f09}.title-sub{margin-top:4px;color:#8a94a5;font-size:11px}.editor-version{margin-left:auto;padding-right:18px;text-align:right}.editor-version span{display:block;color:#98a2b3;font-size:10px}.editor-version b{display:block;margin-top:2px;color:#344054;font-size:13px}.editor-actions{display:flex;gap:8px;padding-left:18px;border-left:1px solid #e7ebf0}.workflow-meta{height:54px;flex:0 0 54px;display:grid;grid-template-columns:240px minmax(260px,480px) 1fr;gap:10px;align-items:center;padding:8px 14px;background:#fff;border-bottom:1px solid #e5e9ef}.sync-hint{justify-self:end;display:flex;align-items:center;gap:7px;color:#667085;font-size:11px}.sync-dot{width:7px;height:7px;border-radius:50%;background:#2d9b69}
.editor-grid{flex:1;min-height:0;display:grid;grid-template-columns:242px minmax(0,1fr) 318px}.task-library,.inspector{background:#fff;min-height:0;overflow:auto}.task-library{border-right:1px solid #e3e8ef}.inspector{border-left:1px solid #e3e8ef}.panel-head{height:44px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid #edf0f3;position:sticky;top:0;background:#fff;z-index:2}.panel-head strong{font-size:13px}.panel-head span{color:#98a2b3;font-size:11px}.task-search{padding:10px 12px 6px}.library-tip{padding:0 12px 10px;color:#98a2b3;font-size:10px;line-height:1.5}.task-list{padding:0 7px 10px}.task-row{width:100%;height:50px;border:0;border-radius:6px;background:#fff;display:flex;align-items:center;gap:9px;padding:0 8px;text-align:left;cursor:pointer}.task-row:hover{background:#f3f6fa}.task-type{width:31px;height:22px;border:1px solid #b8c8df;border-radius:4px;background:#f6f9fd;color:#3667a7;display:inline-grid;place-items:center;font-size:9px;font-weight:700;flex:none}.task-type.large{width:38px;height:28px}.task-main{min-width:0;flex:1}.task-main b,.task-main small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.task-main b{font-size:12px;font-weight:600;color:#344054}.task-main small{margin-top:3px;color:#98a2b3;font-size:9px}.life-dot{width:7px;height:7px;border-radius:50%;flex:none}.life-dot.online{background:#26a269}.life-dot.offline{background:#b7c0cc}.empty-list{padding:28px 8px;text-align:center;color:#98a2b3;font-size:11px}.control-library{border-top:1px solid #edf0f3;padding:12px 8px}.control-title{padding:0 4px 8px;color:#7b8796;font-size:11px;font-weight:600}.control-row{width:100%;min-height:44px;border:1px solid #e2e7ed;border-radius:6px;background:#fff;display:flex;align-items:center;gap:9px;padding:7px 9px;text-align:left;cursor:pointer;margin-bottom:7px}.control-row:hover{background:#f8fafc;border-color:#c9d4e1}.control-row b,.control-row small{display:block}.control-row b{font-size:11px;color:#344054}.control-row small{margin-top:2px;font-size:9px;color:#98a2b3}.diamond,.control-ico{width:28px;text-align:center;color:#667085;font-size:18px}
.canvas-shell{position:relative;min-width:0;min-height:0;background:#f8fafc;overflow:hidden}.canvas{position:absolute;inset:0 0 29px 0}.canvas-tools{position:absolute;right:14px;top:12px;z-index:5;display:flex;border:1px solid #d9e0e8;border-radius:6px;background:#fff;box-shadow:0 3px 10px rgba(30,45,65,.06);overflow:hidden}.canvas-tools button{height:30px;min-width:34px;border:0;border-right:1px solid #e7ebf0;background:#fff;color:#526174;cursor:pointer}.canvas-tools button:hover{background:#f5f8fb}.canvas-tools .fit{padding:0 10px}.canvas-tools .delete-edge{border-right:0;padding:0 10px;color:#c23b35}.canvas-help{position:absolute;left:14px;top:13px;z-index:4;color:#8995a5;font-size:10px;background:rgba(248,250,252,.92);padding:5px 7px;border-radius:4px}.canvas-status{height:29px;position:absolute;left:0;right:0;bottom:0;border-top:1px solid #e4e8ee;background:#fff;display:flex;align-items:center;gap:16px;padding:0 12px;color:#7b8796;font-size:10px}
.inspector{padding-bottom:16px}.empty-inspector{height:260px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#98a2b3}.empty-inspector .empty-ico{font-size:30px;color:#c4ccd6}.empty-inspector b{margin-top:8px;color:#667085;font-size:12px}.empty-inspector span{margin-top:5px;font-size:10px}.task-detail-head{display:flex;gap:10px;align-items:flex-start;padding:15px 14px 12px}.task-detail-head>div{min-width:0}.task-detail-head strong,.task-detail-head small{display:block}.task-detail-head strong{font-size:14px;color:#27364a}.task-detail-head small{margin-top:4px;color:#98a2b3;font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.detail-kv{display:grid;grid-template-columns:76px 1fr;gap:8px 10px;margin:0;padding:0 14px;font-size:11px}.detail-kv dt{color:#8c97a6}.detail-kv dd{margin:0;color:#344054;min-width:0;overflow:hidden;text-overflow:ellipsis}.detail-kv.compact{padding:0}.life-text.online{color:#1f8f5f}.life-text.offline{color:#7b8796}.readonly-note{margin:12px 14px 0;padding:9px 10px;border:1px solid #e4e8ed;border-radius:6px;background:#f8fafc;color:#667085;font-size:10px;line-height:1.55}.divider{height:1px;background:#edf0f3;margin:14px}.section-title{display:flex;align-items:center;justify-content:space-between;margin:0 14px 10px;font-size:12px;font-weight:600;color:#344054}.section-title small{color:#98a2b3;font-size:9px;font-weight:400}.inspector>.detail-kv.compact{margin:0 14px}.mono{font:10px Consolas,monospace;word-break:break-all}.dependency-block{margin:14px}.dep-title{display:flex;align-items:center;justify-content:space-between;color:#667085;font-size:10px;margin-bottom:7px}.dep-title b{min-width:18px;height:18px;padding:0 5px;border-radius:9px;background:#eef2f6;color:#526174;display:grid;place-items:center;font-size:9px}.dep-chips{display:flex;gap:5px;flex-wrap:wrap}.dep-chips span{padding:4px 6px;border:1px solid #dde4ec;border-radius:4px;background:#f8fafc;color:#526174;font-size:9px}.dep-empty,.loading-line{color:#a1aab7;font-size:10px;padding:6px 0}.loading-line{padding:8px 14px}.inspector-actions{display:flex;align-items:center;justify-content:space-between;gap:6px;padding:4px 14px}.edge-inspector{padding:15px 14px}.edge-inspector .section-title{margin:0 0 9px}.edge-inspector p{color:#667085;font-size:11px;line-height:1.7;margin:0 0 14px}.inspector :deep(.el-form){padding:0 14px}.inspector :deep(.el-form-item__label){font-size:11px}.inspector :deep(.el-form-item){margin-bottom:13px}
@media(max-width:1250px){.editor-grid{grid-template-columns:220px minmax(0,1fr) 286px}.workflow-meta{grid-template-columns:220px minmax(220px,360px) 1fr}.title-sub{display:none}}
</style>
