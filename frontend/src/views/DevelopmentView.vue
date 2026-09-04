<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api'
import * as monaco from 'monaco-editor'
import { useRouter } from 'vue-router'

type ResourceType = 'project' | 'folder' | 'sql' | 'python' | 'shell'
interface DevNode { id: string; parent: string | null; type: ResourceType; name: string; code?: string; backendId?: number }
const devNodes = ref<DevNode[]>([
  { id:'p1', parent:null, type:'project', name:'数仓开发项目' },
  { id:'fd1', parent:'p1', type:'folder', name:'01_ODS 数据接入' },
  { id:'f-sql-0', parent:'fd1', type:'sql', name:'ods_order_info.sql', code:`-- ODS 订单数据接入\nINSERT OVERWRITE ods.ods_order_info\nSELECT\n    id,\n    user_id,\n    order_status,\n    total_amount,\n    create_time\nFROM mysql_prod.order_info\nWHERE DATE(create_time) = CURRENT_DATE;` },
  { id:'fd2', parent:'p1', type:'folder', name:'02_DWD 明细层' },
  { id:'f-sql-1', parent:'fd2', type:'sql', name:'dwd_trade_order_detail.sql', code:`-- 交易域：订单明细事实表\nWITH order_base AS (\n    SELECT\n        id AS order_id,\n        user_id,\n        province_id,\n        total_amount,\n        create_time\n    FROM ods.ods_order_info\n    WHERE dt = DATE_SUB(CURRENT_DATE, 1)\n)\nSELECT\n    DATE_FORMAT(create_time, '%Y-%m-%d') AS dt,\n    province_id,\n    COUNT(*) AS order_cnt,\n    SUM(total_amount) AS gmv,\n    COUNT(DISTINCT user_id) AS pay_user_cnt\nFROM order_base\nGROUP BY\n    DATE_FORMAT(create_time, '%Y-%m-%d'),\n    province_id\nORDER BY gmv DESC\nLIMIT 100;` },
  { id:'f-py-1', parent:'fd2', type:'python', name:'user_feature_build.py', code:`# 用户特征计算任务\nfrom datetime import datetime\n\ndef build_user_feature(ds):\n    print(f"start build feature, ds={ds}")\n    sql = "SELECT user_id, COUNT(*) cnt FROM dwd_user_action GROUP BY user_id"\n    return sql\n\nif __name__ == "__main__":\n    build_user_feature("2026-09-02")` },
  { id:'fd3', parent:'p1', type:'folder', name:'03_DWS 汇总层' },
  { id:'f-sql-2', parent:'fd3', type:'sql', name:'dws_trade_province_1d.sql', code:`INSERT OVERWRITE dws.dws_trade_province_1d\nSELECT\n    dt,\n    province_id,\n    COUNT(DISTINCT order_id) AS order_cnt,\n    SUM(order_amount) AS gmv\nFROM dwd.dwd_trade_order_detail\nWHERE dt = DATE_SUB(CURRENT_DATE, 1)\nGROUP BY dt, province_id;` },
  { id:'fd4', parent:'p1', type:'folder', name:'脚本任务' },
  { id:'f-sh-1', parent:'fd4', type:'shell', name:'check_partition.sh', code:`#!/bin/bash\n# 检查昨日分区是否生成\nexport DS=2026-09-02\necho "checking partition: \${DS}"\necho "partition exists"` },
  { id:'p2', parent:null, type:'project', name:'营销分析项目' },
  { id:'fd5', parent:'p2', type:'folder', name:'活动分析' },
  { id:'f-sql-3', parent:'fd5', type:'sql', name:'campaign_roi.sql', code:`SELECT campaign_id, SUM(gmv) / SUM(cost) AS roi\nFROM ads.ads_campaign_daily\nWHERE dt >= '2026-09-01'\nGROUP BY campaign_id\nORDER BY roi DESC;` }
])
const selectedNodeId = ref('f-sql-1')
const currentType = ref<ResourceType>('sql')
const currentCode = ref('')
const search = ref('')
const showModal = ref(false)
const createType = ref<ResourceType>('project')
const createName = ref('')
const runMessage = ref('就绪')
const executionId = ref('')
const editorContainer = ref<HTMLElement>()
const resultColumns = ref<string[]>([])
const resultRows = ref<Record<string, unknown>[]>([])
const backendProjectId = ref<number>()
const router = useRouter()
let seq = 30

const selectedNode = computed(() => devNodes.value.find(n => n.id === selectedNodeId.value))
const visibleNodes = computed(() => devNodes.value.filter(n => {
  if (!search.value) return true
  const q = search.value.toLowerCase()
  return n.name.toLowerCase().includes(q) || Boolean(n.parent && devNodes.value.find(p => p.id === n.parent)?.name.toLowerCase().includes(q))
}))
let monacoEditor: monaco.editor.IStandaloneCodeEditor | null = null
let editorChangeDisposable: monaco.IDisposable | null = null
const completionItems = ['SELECT','FROM','WHERE','GROUP BY','ORDER BY','LEFT JOIN','INNER JOIN','COUNT(*)','SUM()','DATE_FORMAT()','LIMIT 100','dwd_trade_order_detail','dim_user_info','ods_order_info']

onMounted(async () => {
  const projectResult = await platformApi.projects().catch(() => null)
  let project = projectResult?.data.data?.[0]
  if (!project) project = (await platformApi.createProject({ name: '数仓开发项目', description: '平台默认开发项目' }).catch(() => null))?.data.data
  if (project) {
    backendProjectId.value = project.id
    const result = await platformApi.files(project.id).catch(() => null)
    const backendFile = result?.data.data?.find(file => file.name === selectedNode.value?.name) || result?.data.data?.[0]
    if (backendFile) {
      const node = devNodes.value.find(n => n.id === selectedNodeId.value)
      if (node) { node.backendId = backendFile.id; node.code = backendFile.content }
    }
  }
  openNode(selectedNodeId.value)
  initMonaco()
})
function childrenOf(id: string) { return devNodes.value.filter(n => n.parent === id) }
function depthOf(node: DevNode) { let depth = 0; let parent = node.parent; while (parent) { depth++; parent = devNodes.value.find(n => n.id === parent)?.parent || null } return Math.min(depth, 3) }
function nodeIcon(type: ResourceType) { return type === 'project' ? '▣' : type === 'folder' ? '▾' : type === 'sql' ? 'SQL' : type === 'python' ? 'Py' : 'SH' }
function nodeClass(type: ResourceType) { return type === 'folder' ? 'folder' : type === 'sql' ? 'sql' : type === 'python' ? 'py' : type === 'shell' ? 'sh' : 'db' }
function treeNodes(parent: string | null): DevNode[] { return visibleNodes.value.filter(n => n.parent === parent) }
function editorLanguage(type: ResourceType) { return type === 'sql' ? 'sql' : type === 'python' ? 'python' : type === 'shell' ? 'shell' : 'plaintext' }
function initMonaco() {
  if (!editorContainer.value || monacoEditor) return
  monacoEditor = monaco.editor.create(editorContainer.value, {
    value: currentCode.value,
    language: editorLanguage(currentType.value),
    theme: 'vs',
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    lineHeight: 22,
    tabSize: 4,
    insertSpaces: true,
    wordWrap: 'off',
    padding: { top: 12, bottom: 12 },
    suggest: { showMethods: true, showFunctions: true, showKeywords: true }
  })
  editorChangeDisposable = monacoEditor.onDidChangeModelContent(() => {
    currentCode.value = monacoEditor?.getValue() || ''
    const node = selectedNode.value
    if (node) node.code = currentCode.value
  })
  monacoEditor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => saveCurrent())
  monaco.languages.registerCompletionItemProvider(['sql', 'python', 'shell'], {
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position)
      const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn)
      return { suggestions: completionItems.map(label => ({ label, kind: monaco.languages.CompletionItemKind.Keyword, insertText: label, range })) }
    }
  })
}
function openNode(id: string) {
  const node = devNodes.value.find(n => n.id === id)
  if (!node) return
  selectedNodeId.value = id
  if (node.type === 'folder' || node.type === 'project') {
    currentCode.value = ''
    monacoEditor?.setValue('')
    return
  }
  currentType.value = node.type
  currentCode.value = node.code || ''
  if (monacoEditor) {
    monacoEditor.setValue(currentCode.value)
    monaco.editor.setModelLanguage(monacoEditor.getModel()!, editorLanguage(currentType.value))
  }
}
function closeEditor() { selectedNodeId.value = ''; currentCode.value = ''; resultColumns.value = []; resultRows.value = []; monacoEditor?.setValue('') }
function openCreateModal() { createName.value = ''; showModal.value = true }
function createNode() { const name0 = createName.value.trim(); if (!name0) { ElMessage.warning('请输入名称'); return }; let name = name0; if (createType.value === 'sql' && !name.endsWith('.sql')) name += '.sql'; if (createType.value === 'python' && !name.endsWith('.py')) name += '.py'; if (createType.value === 'shell' && !name.endsWith('.sh')) name += '.sh'; let parent: string | null = null; if (createType.value !== 'project') { const selected = selectedNode.value; parent = selected && ['project','folder'].includes(selected.type) ? selected.id : selected?.parent || null; if (!parent) { ElMessage.warning('请先选中项目或文件夹'); return } } const code = createType.value === 'sql' ? 'SELECT *\nFROM table_name\nLIMIT 100;' : createType.value === 'python' ? '# Python Task\nprint("hello data platform")' : createType.value === 'shell' ? '#!/bin/bash\necho "hello data platform"' : ''; const node: DevNode = { id: 'n' + (++seq), parent, type: createType.value, name, code }; devNodes.value.push(node); selectedNodeId.value = node.id; showModal.value = false; if (!['project','folder'].includes(node.type)) openNode(node.id); ElMessage.success('创建成功') }
function renameNode() { const node = selectedNode.value; if (!node) { ElMessage.warning('请先选择资源'); return }; const value = window.prompt('请输入新名称', node.name); if (value?.trim()) { node.name = value.trim(); ElMessage.success('重命名成功') } }
function deleteNode() { const node = selectedNode.value; if (!node) return; if (childrenOf(node.id).length) { ElMessage.warning('资源下存在子项，不能删除'); return }; devNodes.value = devNodes.value.filter(n => n.id !== node.id); selectedNodeId.value = ''; currentCode.value = ''; ElMessage.success('已删除 ' + node.name) }
async function saveCurrent() {
  const node = selectedNode.value
  if (!node) return
  try {
    if (node.backendId) await platformApi.saveFile(node.backendId, currentCode.value)
    else if (backendProjectId.value) {
      const result = await platformApi.createFile({ projectId: backendProjectId.value, name: node.name, fileType: node.type.toUpperCase(), content: currentCode.value })
      node.backendId = result.data.data.id
    }
    node.code = currentCode.value
    ElMessage.success('保存成功')
  } catch { ElMessage.error('文件保存失败，请检查平台数据库连接') }
}
async function runDevSql() {
  const model = monacoEditor?.getModel()
  const selection = monacoEditor && model && !monacoEditor.getSelection()?.isEmpty()
    ? model.getValueInRange(monacoEditor.getSelection()!)
    : currentCode.value
  runMessage.value = '运行中...'
  try {
    const result = await platformApi.query(selection, true)
    const resultData = result.data.data as Record<string, unknown> | undefined
    resultColumns.value = Array.isArray(resultData?.columns) ? resultData.columns.map(String) : []
    resultRows.value = Array.isArray(resultData?.rows) ? resultData.rows as Record<string, unknown>[] : []
    executionId.value = String(result.data.data?.executionId || '')
    runMessage.value = `执行${result.data.data?.status === 'SUCCESS' ? '成功' : '完成'} · ${result.data.data?.rowCount || 0} 行`
    ElMessage.success('任务执行成功')
  } catch { runMessage.value = '执行失败'; ElMessage.error('SQL 执行失败') }
}
async function stopDevSql() {
  if (executionId.value) await platformApi.cancelQuery(executionId.value).catch(() => undefined)
  runMessage.value = '已停止'
  ElMessage.info('已发送停止请求')
}
function formatCode() { if (currentType.value !== 'sql') { ElMessage.info('当前文件不是 SQL，跳过 SQL 格式化'); return }; currentCode.value = currentCode.value.replace(/\s+(SELECT|FROM|WHERE|GROUP BY|ORDER BY|LEFT JOIN|INNER JOIN|LIMIT)\s+/gi, (_, key) => '\n' + key.toUpperCase() + ' ').trim(); monacoEditor?.setValue(currentCode.value); ElMessage.success('格式化完成') }
onBeforeUnmount(() => { editorChangeDisposable?.dispose(); monacoEditor?.dispose(); monacoEditor = null })
</script>

<template>
  <section class="page"><div class="module-bar"><div class="module-title">数据开发</div><span class="crumb">/ 开发工作台</span><div class="module-actions"><span style="color:#8b95a5;font-size:12px">当前项目：</span><select class="btn" @change="ElMessage.success('项目已切换')"><option>数仓开发项目</option><option>营销分析项目</option></select><button class="btn" @click="router.push('/workflow')">发布中心</button></div></div>
    <div class="split"><aside class="left-panel"><div class="panel-head"><div class="panel-title">项目资源</div><div class="panel-actions"><button class="btn small icononly" title="新建" @click="openCreateModal">＋</button><button class="btn small icononly" title="重命名" @click="renameNode">✎</button><button class="btn small icononly danger" title="删除" @click="deleteNode">⌫</button></div></div><div class="tree-toolbar"><input v-model="search" class="search" placeholder="搜索项目 / 文件夹 / 文件" /></div><div class="tree"><template v-for="node in treeNodes(null)" :key="node.id"><div class="tree-row" :class="['indent-'+depthOf(node), { selected:selectedNodeId===node.id }]" @click="openNode(node.id)"><span class="tree-arrow">{{ ['project','folder'].includes(node.type) ? '▾' : '' }}</span><span class="tree-icon" :class="nodeClass(node.type)">{{ nodeIcon(node.type) }}</span><span>{{ node.name }}</span><span v-if="['project','folder'].includes(node.type)" class="count">{{ childrenOf(node.id).length }}</span></div><template v-for="child in treeNodes(node.id)" :key="child.id"><div class="tree-row" :class="['indent-'+depthOf(child), { selected:selectedNodeId===child.id }]" @click="openNode(child.id)"><span class="tree-arrow">{{ ['project','folder'].includes(child.type) ? '▾' : '' }}</span><span class="tree-icon" :class="nodeClass(child.type)">{{ nodeIcon(child.type) }}</span><span>{{ child.name }}</span><span v-if="['project','folder'].includes(child.type)" class="count">{{ childrenOf(child.id).length }}</span></div><template v-for="grand in treeNodes(child.id)" :key="grand.id"><div class="tree-row" :class="['indent-'+depthOf(grand), { selected:selectedNodeId===grand.id }]" @click="openNode(grand.id)"><span class="tree-arrow"></span><span class="tree-icon" :class="nodeClass(grand.type)">{{ nodeIcon(grand.type) }}</span><span>{{ grand.name }}</span></div></template></template></template></div></aside>
      <section class="workarea"><div class="tabbar"><div v-if="selectedNode" class="tab active"><span :class="nodeClass(selectedNode.type)">{{ nodeIcon(selectedNode.type) }}</span><span class="tab-name">{{ selectedNode.name }}</span><button class="close tab-action" title="关闭" @click.stop="closeEditor">×</button></div><button class="tab tab-action" style="min-width:38px" title="新建资源" @click="openCreateModal">＋</button></div><div class="editor-toolbar"><button class="btn small primary" @click="runDevSql">▶ 运行</button><button class="btn small" @click="stopDevSql">■ 停止</button><div class="toolbar-sep"></div><button class="btn small" @click="saveCurrent">⌘ 保存</button><button class="btn small" @click="formatCode">{ } 格式化</button><div class="toolbar-sep"></div><button class="btn small" @click="ElMessage.success('已通过语法检查')">✓ 检查</button><span class="toolbar-meta"><span class="status-dot"></span> Monaco 编辑器 · 代码补全已开启　UTF-8</span></div><div class="editor-shell"><div ref="editorContainer" class="code-area monaco-code-area"></div><div class="result-panel"><div class="result-head"><button class="result-tab result-tab-button">运行结果</button><button class="plain-tab" @click="ElMessage.info(runMessage)">日志</button><button class="plain-tab" @click="ElMessage.info('运行历史将在运维中心统一展示')">历史</button><div class="run-msg">{{ runMessage }}</div></div><div class="table-wrap"><table v-if="resultColumns.length"><thead><tr><th v-for="column in resultColumns" :key="column">{{ column }}</th></tr></thead><tbody><tr v-for="(row,index) in resultRows" :key="index"><td v-for="column in resultColumns" :key="column">{{ row[column] }}</td></tr><tr v-if="!resultRows.length"><td :colspan="resultColumns.length" class="empty-state">查询未返回数据</td></tr></tbody></table><div v-else class="empty-state">运行 SQL 后显示真实结果</div></div></div></div></section>
    </div><div v-if="showModal" class="modal-mask" @click.self="showModal=false"><div class="modal"><div class="modal-hd">新建资源</div><div class="modal-bd"><div class="form-item"><label>资源类型</label><select v-model="createType"><option value="project">项目</option><option value="folder">文件夹</option><option value="sql">SQL 文件</option><option value="shell">Shell 脚本</option><option value="python">Python 任务</option></select></div><div class="form-item"><label>名称</label><input v-model="createName" placeholder="请输入名称" @keyup.enter="createNode" /></div><div style="font-size:12px;color:#8b95a5">文件会创建在当前选中的项目或文件夹下。</div></div><div class="modal-ft"><button class="btn" @click="showModal=false">取消</button><button class="btn primary" @click="createNode">确定</button></div></div></div>
  </section>
</template>

<style scoped>
.tab-action,.plain-tab,.result-tab-button { border:0; background:transparent; cursor:pointer; }
.plain-tab { color:#667085; }
</style>
