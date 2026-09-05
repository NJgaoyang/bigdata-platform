<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { platformApi, type DataSource } from "../api";
import * as monaco from "monaco-editor";
import { useRouter } from "vue-router";

type ResourceType = "project" | "folder" | "sql" | "python" | "shell";
interface DevNode {
  id: string;
  parent: string | null;
  type: ResourceType;
  name: string;
  code?: string;
  backendId?: number;
}
const devNodes = ref<DevNode[]>([]);
const selectedNodeId = ref("");
const currentType = ref<ResourceType>("sql");
const currentCode = ref("");
const search = ref("");
const showModal = ref(false);
const createType = ref<ResourceType>("folder");
const createName = ref("");
const runMessage = ref("就绪");
const executionId = ref("");
const editorContainer = ref<HTMLElement>();
const resultColumns = ref<string[]>([]);
const resultRows = ref<Record<string, unknown>[]>([]);
const historyVisible = ref(false);
const queryHistory = ref<Record<string, unknown>[]>([]);
const backendProjectId = ref<number>();
const currentProjectId = ref<number>();
const router = useRouter();
const dataSources = ref<DataSource[]>([]);
const activeDataSourceId = ref<number>();
let seq = 30;

const selectedNode = computed(() =>
  devNodes.value.find((n) => n.id === selectedNodeId.value),
);
const backendProjects = ref<{ id: number; name: string }[]>([]);
const visibleNodes = computed(() =>
  devNodes.value.filter((n) => {
    if (!search.value) return true;
    const q = search.value.toLowerCase();
    return (
      n.name.toLowerCase().includes(q) ||
      Boolean(
        n.parent &&
        devNodes.value
          .find((p) => p.id === n.parent)
          ?.name.toLowerCase()
          .includes(q),
      )
    );
  }),
);
let monacoEditor: monaco.editor.IStandaloneCodeEditor | null = null;
let editorChangeDisposable: monaco.IDisposable | null = null;
const completionItems = [
  "SELECT",
  "FROM",
  "WHERE",
  "GROUP BY",
  "ORDER BY",
  "LEFT JOIN",
  "INNER JOIN",
  "COUNT(*)",
  "SUM()",
  "DATE_FORMAT()",
  "LIMIT 100",
];

async function loadBackendTree(
  projects: { id: number; name: string; description?: string }[],
) {
  const loaded: DevNode[] = [];
  for (const project of projects) {
    const projectNode: DevNode = {
      id: `project-${project.id}`,
      parent: null,
      type: "project",
      name: project.name,
      backendId: project.id,
    };
    loaded.push(projectNode);
    const [foldersResult, filesResult] = await Promise.all([
      platformApi
        .folders(project.id)
        .catch(() => ({ data: { data: [] } }) as any),
      platformApi
        .files(project.id)
        .catch(() => ({ data: { data: [] } }) as any),
    ]);
    for (const folder of foldersResult.data.data || []) {
      loaded.push({
        id: `folder-${folder.id}`,
        parent: folder.parentId ? `folder-${folder.parentId}` : projectNode.id,
        type: "folder",
        name: String(folder.name),
        backendId: Number(folder.id),
      });
    }
    for (const file of filesResult.data.data || []) {
      loaded.push({
        id: `file-${file.id}`,
        parent: file.folderId ? `folder-${file.folderId}` : projectNode.id,
        type: String(file.fileType).toLowerCase() as ResourceType,
        name: file.name,
        code: file.content,
        backendId: file.id,
      });
    }
  }
  if (!loaded.length) return false;
  devNodes.value = loaded;
  const selected = loaded.find((node) => node.type !== "project") || loaded[0];
  selectedNodeId.value = selected?.id || "";
  currentProjectId.value = selected
    ? rootProjectOf(selected)?.backendId || projects[0]?.id
    : projects[0]?.id;
  return true;
}

function rootProjectOf(node?: DevNode): DevNode | undefined {
  let current = node;
  while (current && current.type !== "project")
    current = current.parent
      ? devNodes.value.find((item) => item.id === current?.parent)
      : undefined;
  return current;
}
function switchProject() {
  const project = devNodes.value.find(
    (node) =>
      node.type === "project" && node.backendId === currentProjectId.value,
  );
  const firstFile =
    project &&
    devNodes.value.find(
      (node) =>
        node.type !== "project" && rootProjectOf(node)?.id === project.id,
    );
  if (firstFile) openNode(firstFile.id);
  else if (project) openNode(project.id);
}

onMounted(async () => {
  dataSources.value = (
    (
      await platformApi
        .dataSources()
        .catch(() => ({ data: { data: [] } }) as any)
    ).data.data || []
  ).filter((source: DataSource) => source.type === "STARROCKS");
  activeDataSourceId.value = dataSources.value[0]?.id;
  const projectResult = await platformApi.projects().catch(() => null);
  backendProjects.value = (projectResult?.data.data || []).map((item) => ({
    id: item.id,
    name: item.name,
  }));
  let project = projectResult?.data.data?.[0];
  if (!project)
    project = (
      await platformApi
        .createProject({
          name: "数仓开发项目",
          description: "平台默认开发项目",
        })
        .catch(() => null)
    )?.data.data;
  if (project) {
    if (!backendProjects.value.some((item) => item.id === project?.id))
      backendProjects.value.push({ id: project.id, name: project.name });
    backendProjectId.value = project.id;
    currentProjectId.value = project.id;
    await loadBackendTree(
      projectResult?.data.data?.length ? projectResult.data.data : [project],
    );
  }
  if (selectedNodeId.value) openNode(selectedNodeId.value);
  initMonaco();
});
function childrenOf(id: string) {
  return devNodes.value.filter((n) => n.parent === id);
}
function depthOf(node: DevNode) {
  let depth = 0;
  let parent = node.parent;
  while (parent) {
    depth++;
    parent = devNodes.value.find((n) => n.id === parent)?.parent || null;
  }
  return Math.min(depth, 3);
}
function nodeIcon(type: ResourceType) {
  return type === "project"
    ? "▣"
    : type === "folder"
      ? "▾"
      : type === "sql"
        ? "SQL"
        : type === "python"
          ? "Py"
          : "SH";
}
function nodeClass(type: ResourceType) {
  return type === "folder"
    ? "folder"
    : type === "sql"
      ? "sql"
      : type === "python"
        ? "py"
        : type === "shell"
          ? "sh"
          : "db";
}
function treeNodes(parent: string | null): DevNode[] {
  if (parent === null) {
    // Projects remain an internal backend root for compatibility, but the
    // product organizes resources by folders rather than project partitions.
    return visibleNodes.value.filter(
      (n) => n.type !== "project" && n.parent?.startsWith("project-"),
    );
  }
  return visibleNodes.value.filter((n) => n.parent === parent);
}
function editorLanguage(type: ResourceType) {
  return type === "sql"
    ? "sql"
    : type === "python"
      ? "python"
      : type === "shell"
        ? "shell"
        : "plaintext";
}
function initMonaco() {
  if (!editorContainer.value || monacoEditor) return;
  monacoEditor = monaco.editor.create(editorContainer.value, {
    value: currentCode.value,
    language: editorLanguage(currentType.value),
    theme: "vs",
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    lineHeight: 22,
    tabSize: 4,
    insertSpaces: true,
    wordWrap: "off",
    padding: { top: 12, bottom: 12 },
    suggest: { showMethods: true, showFunctions: true, showKeywords: true },
  });
  editorChangeDisposable = monacoEditor.onDidChangeModelContent(() => {
    currentCode.value = monacoEditor?.getValue() || "";
    const node = selectedNode.value;
    if (node) node.code = currentCode.value;
  });
  monacoEditor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () =>
    saveCurrent(),
  );
  monaco.languages.registerCompletionItemProvider(["sql", "python", "shell"], {
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position);
      const range = new monaco.Range(
        position.lineNumber,
        word.startColumn,
        position.lineNumber,
        word.endColumn,
      );
      return {
        suggestions: completionItems.map((label) => ({
          label,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: label,
          range,
        })),
      };
    },
  });
}
function openNode(id: string) {
  const node = devNodes.value.find((n) => n.id === id);
  if (!node) return;
  selectedNodeId.value = id;
  if (node.type === "folder" || node.type === "project") {
    currentCode.value = "";
    monacoEditor?.setValue("");
    return;
  }
  currentType.value = node.type;
  currentCode.value = node.code || "";
  if (monacoEditor) {
    monacoEditor.setValue(currentCode.value);
    monaco.editor.setModelLanguage(
      monacoEditor.getModel()!,
      editorLanguage(currentType.value),
    );
  }
}
function closeEditor() {
  selectedNodeId.value = "";
  currentCode.value = "";
  resultColumns.value = [];
  resultRows.value = [];
  monacoEditor?.setValue("");
}
function openCreateModal() {
  createType.value = "folder";
  createName.value = "";
  showModal.value = true;
}
async function createNode() {
  const name0 = createName.value.trim();
  if (!name0) {
    ElMessage.warning("请输入名称");
    return;
  }
  let name = name0;
  if (createType.value === "sql" && !name.endsWith(".sql")) name += ".sql";
  if (createType.value === "python" && !name.endsWith(".py")) name += ".py";
  if (createType.value === "shell" && !name.endsWith(".sh")) name += ".sh";
  const selected = selectedNode.value;
  const parent =
    selected && ["project", "folder"].includes(selected.type)
      ? selected
      : selected?.parent
        ? devNodes.value.find((node) => node.id === selected.parent)
        : undefined;
  const project =
    parent?.type === "project"
      ? parent
      : parent
        ? rootProjectOf(parent)
        : selected && selected.type === "project"
          ? selected
          : devNodes.value.find((node) => node.type === "project");
  const projectId = project?.backendId || currentProjectId.value;
  if (!projectId) {
    ElMessage.warning("请先选中文件夹或资源目录");
    return;
  }
  try {
    let node: DevNode;
    if (createType.value === "folder") {
      const result = await platformApi.createFolder({
        projectId,
        parentId: parent?.type === "folder" ? parent.backendId : null,
        name,
      });
      node = {
        id: `folder-${result.data.data.id}`,
        parent: parent?.id || `project-${projectId}`,
        type: "folder",
        name,
        backendId: Number(result.data.data.id),
      };
    } else {
      const code =
        createType.value === "sql"
          ? "SELECT *\nFROM table_name\nLIMIT 100;"
          : createType.value === "python"
            ? '# Python Task\nprint("hello data platform")'
            : '#!/bin/bash\necho "hello data platform"';
      const result = await platformApi.createFile({
        projectId,
        folderId: parent?.type === "folder" ? parent.backendId : null,
        name,
        fileType: createType.value.toUpperCase(),
        content: code,
      });
      node = {
        id: `file-${result.data.data.id}`,
        parent: parent?.id || `project-${projectId}`,
        type: createType.value,
        name,
        code,
        backendId: result.data.data.id,
      };
    }
    devNodes.value.push(node);
    selectedNodeId.value = node.id;
    showModal.value = false;
    if (!["project", "folder"].includes(node.type)) openNode(node.id);
    ElMessage.success("创建成功");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "创建失败");
  }
}
async function renameNode() {
  const node = selectedNode.value;
  if (!node) {
    ElMessage.warning("请先选择资源");
    return;
  }
  const value = window.prompt("请输入新名称", node.name)?.trim();
  if (!value || !node.backendId) return;
  try {
    if (node.type === "project")
      await platformApi.updateProject(node.backendId, {
        name: value,
        description: "",
      });
    else if (node.type === "folder")
      await platformApi.updateFolder(node.backendId, { name: value });
    else
      await platformApi.saveFile(
        node.backendId,
        currentCode.value || node.code || "",
        value,
      );
    node.name = value;
    ElMessage.success("重命名成功");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "重命名失败");
  }
}
async function deleteNode() {
  const node = selectedNode.value;
  if (!node) return;
  if (childrenOf(node.id).length) {
    ElMessage.warning("资源下存在子项，不能删除");
    return;
  }
  try {
    if (node.backendId) {
      if (node.type === "project")
        await platformApi.deleteProject(node.backendId);
      else if (node.type === "folder")
        await platformApi.deleteFolder(node.backendId);
      else await platformApi.deleteFile(node.backendId);
    }
    devNodes.value = devNodes.value.filter((n) => n.id !== node.id);
    selectedNodeId.value = "";
    currentCode.value = "";
    ElMessage.success("已删除 " + node.name);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "删除失败");
  }
}
async function saveCurrent() {
  const node = selectedNode.value;
  if (!node) return;
  try {
    if (node.backendId)
      await platformApi.saveFile(node.backendId, currentCode.value, node.name);
    else if (backendProjectId.value) {
      const result = await platformApi.createFile({
        projectId: backendProjectId.value,
        folderId: node.parent?.startsWith("folder-")
          ? Number(node.parent.slice(7))
          : null,
        name: node.name,
        fileType: node.type.toUpperCase(),
        content: currentCode.value,
      });
      node.backendId = result.data.data.id;
    }
    node.code = currentCode.value;
    ElMessage.success("保存成功");
  } catch {
    ElMessage.error("文件保存失败，请检查平台数据库连接");
  }
}
async function runDevSql() {
  const model = monacoEditor?.getModel();
  const selection =
    monacoEditor && model && !monacoEditor.getSelection()?.isEmpty()
      ? model.getValueInRange(monacoEditor.getSelection()!)
      : currentCode.value;
  runMessage.value = "运行中...";
  try {
    if (!activeDataSourceId.value) {
      runMessage.value = "未配置 StarRocks 数据源";
      ElMessage.warning("数据开发 SQL 运行需要先配置 StarRocks 数据源");
      return;
    }
    const source = dataSources.value.find(
      (item) => item.id === activeDataSourceId.value,
    );
    const result = await platformApi.query(
      selection,
      true,
      activeDataSourceId.value,
      source?.databaseName,
    );
    const resultData = result.data.data as Record<string, unknown> | undefined;
    resultColumns.value = Array.isArray(resultData?.columns)
      ? resultData.columns.map(String)
      : [];
    resultRows.value = Array.isArray(resultData?.rows)
      ? (resultData.rows as Record<string, unknown>[])
      : [];
    executionId.value = String(result.data.data?.executionId || "");
    runMessage.value = `执行${result.data.data?.status === "SUCCESS" ? "成功" : "完成"} · ${result.data.data?.rowCount || 0} 行`;
    ElMessage.success("任务执行成功");
  } catch {
    runMessage.value = "执行失败";
    ElMessage.error("SQL 执行失败");
  }
}
async function stopDevSql() {
  if (!executionId.value) {
    ElMessage.warning("当前没有运行中的 SQL");
    return;
  }
  await platformApi.cancelQuery(executionId.value).catch(() => undefined);
  executionId.value = "";
  runMessage.value = "已停止";
  ElMessage.info("已发送停止请求");
}
async function openQueryHistory() {
  try {
    queryHistory.value = (await platformApi.queryHistory()).data.data || [];
    historyVisible.value = true;
  } catch {
    ElMessage.error("查询历史加载失败");
  }
}
function formatCode() {
  if (currentType.value !== "sql") {
    ElMessage.info("当前文件不是 SQL，跳过 SQL 格式化");
    return;
  }
  currentCode.value = currentCode.value
    .replace(
      /\s+(SELECT|FROM|WHERE|GROUP BY|ORDER BY|LEFT JOIN|INNER JOIN|LIMIT)\s+/gi,
      (_, key) => "\n" + key.toUpperCase() + " ",
    )
    .trim();
  monacoEditor?.setValue(currentCode.value);
  ElMessage.success("格式化完成");
}
function validateCurrent() {
  if (currentType.value !== "sql") {
    ElMessage.info("当前资源不是 SQL，暂不提供脚本语法检查");
    return;
  }
  const code = currentCode.value.trim();
  if (!code) {
    ElMessage.warning("当前 SQL 为空");
    return;
  }
  const balanced = (code.match(/\(/g) || []).length === (code.match(/\)/g) || []).length;
  const statement = /^(--[^\n]*\n\s*)?(select|with|insert|update|delete|create|alter|truncate)\b/i.test(code);
  if (!balanced || !statement) {
    ElMessage.error("基础语法检查未通过，请检查括号和 SQL 起始关键字");
    return;
  }
  ElMessage.success("基础语法检查通过");
}
onBeforeUnmount(() => {
  editorChangeDisposable?.dispose();
  monacoEditor?.dispose();
  monacoEditor = null;
});
</script>

<template>
  <section class="page">
    <div class="module-bar">
      <div class="module-title">数据开发</div>
      <span class="crumb">/ 开发工作台</span>
      <div class="module-actions">
        <span style="color: #8b95a5; font-size: 12px">数据源：</span
        ><select v-model="activeDataSourceId" class="btn">
          <option
            v-for="source in dataSources"
            :key="source.id"
            :value="source.id"
          >
            {{ source.name }} / {{ source.databaseName }}
          </option>
          <option v-if="!dataSources.length">未配置 StarRocks</option></select
        ><button class="btn" @click="router.push('/workflow')">发布中心</button>
      </div>
    </div>
    <div class="split">
      <aside class="left-panel">
        <div class="panel-head">
          <div class="panel-title">资源目录</div>
          <div class="panel-actions">
            <button
              class="btn small icononly"
              title="新建文件夹或文件"
              @click="openCreateModal"
            >
              ＋</button
            ><button
              class="btn small icononly"
              title="重命名"
              @click="renameNode"
            >
              ✎</button
            ><button
              class="btn small icononly danger"
              title="删除"
              @click="deleteNode"
            >
              ⌫
            </button>
          </div>
        </div>
        <div class="tree-toolbar">
          <input
            v-model="search"
            class="search"
            placeholder="搜索文件夹 / 文件"
          />
        </div>
        <div class="workspace-resource-tree">
          <template v-for="node in treeNodes(null)" :key="node.id"
            ><div
              class="tree-row"
              :class="[
                'indent-' + depthOf(node),
                { selected: selectedNodeId === node.id },
              ]"
              @click="openNode(node.id)"
            >
              <span class="tree-arrow">{{
                ["project", "folder"].includes(node.type) ? "▾" : ""
              }}</span
              ><span class="tree-icon" :class="nodeClass(node.type)">{{
                nodeIcon(node.type)
              }}</span
              ><span>{{ node.name }}</span
              ><span
                v-if="['project', 'folder'].includes(node.type)"
                class="count"
                >{{ childrenOf(node.id).length }}</span
              >
            </div>
            <template v-for="child in treeNodes(node.id)" :key="child.id"
              ><div
                class="tree-row"
                :class="[
                  'indent-' + depthOf(child),
                  { selected: selectedNodeId === child.id },
                ]"
                @click="openNode(child.id)"
              >
                <span class="tree-arrow">{{
                  ["project", "folder"].includes(child.type) ? "▾" : ""
                }}</span
                ><span class="tree-icon" :class="nodeClass(child.type)">{{
                  nodeIcon(child.type)
                }}</span
                ><span>{{ child.name }}</span
                ><span
                  v-if="['project', 'folder'].includes(child.type)"
                  class="count"
                  >{{ childrenOf(child.id).length }}</span
                >
              </div>
              <template v-for="grand in treeNodes(child.id)" :key="grand.id"
                ><div
                  class="tree-row"
                  :class="[
                    'indent-' + depthOf(grand),
                    { selected: selectedNodeId === grand.id },
                  ]"
                  @click="openNode(grand.id)"
                >
                  <span class="tree-arrow"></span
                  ><span class="tree-icon" :class="nodeClass(grand.type)">{{
                    nodeIcon(grand.type)
                  }}</span
                  ><span>{{ grand.name }}</span>
                </div></template
              ></template
            ></template
          >
        </div>
      </aside>
      <section class="workarea">
        <div class="tabbar">
          <div
            v-if="
              selectedNode && !['project', 'folder'].includes(selectedNode.type)
            "
            class="tab active"
          >
            <span :class="nodeClass(selectedNode.type)">{{
              nodeIcon(selectedNode.type)
            }}</span
            ><span class="tab-name">{{ selectedNode.name }}</span
            ><button
              class="close tab-action"
              title="关闭"
              @click.stop="closeEditor"
            >
              ×
            </button>
          </div>
          <button
            class="tab tab-action"
            style="min-width: 38px"
            title="新建文件夹或文件"
            @click="openCreateModal"
          >
            ＋
          </button>
        </div>
        <div class="editor-toolbar">
          <button class="btn small primary" @click="runDevSql">▶ 运行</button
          ><button class="btn small" @click="stopDevSql">■ 停止</button>
          <div class="toolbar-sep"></div>
          <button class="btn small" @click="saveCurrent">⌘ 保存</button
          ><button class="btn small" @click="formatCode">{ } 格式化</button>
          <div class="toolbar-sep"></div>
          <button
            class="btn small"
            @click="validateCurrent"
          >
            ✓ 检查</button
          ><span class="toolbar-meta"
            ><span class="status-dot"></span> Monaco 编辑器 ·
            代码补全已开启　UTF-8</span
          >
        </div>
        <div class="editor-shell">
          <div ref="editorContainer" class="code-area monaco-code-area"></div>
          <div class="result-panel">
            <div class="result-head">
              <button class="result-tab result-tab-button">运行结果</button
              ><button class="plain-tab" @click="ElMessage.info(runMessage)">
                日志</button
              ><button
                class="plain-tab"
                @click="openQueryHistory"
              >
                历史
              </button>
              <div class="run-msg">{{ runMessage }}</div>
            </div>
            <div class="table-wrap">
              <table v-if="resultColumns.length">
                <thead>
                  <tr>
                    <th v-for="column in resultColumns" :key="column">
                      {{ column }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in resultRows" :key="index">
                    <td v-for="column in resultColumns" :key="column">
                      {{ row[column] }}
                    </td>
                  </tr>
                  <tr v-if="!resultRows.length">
                    <td :colspan="resultColumns.length" class="empty-state">
                      查询未返回数据
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-else class="empty-state">运行 SQL 后显示真实结果</div>
            </div>
          </div>
        </div>
      </section>
    </div>
    <div v-if="showModal" class="modal-mask" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-hd">新建文件夹或文件</div>
        <div class="modal-bd">
          <div class="form-item">
            <label>资源类型</label
            ><select v-model="createType">
              <option value="folder">文件夹</option>
              <option value="sql">SQL 文件</option>
              <option value="shell">Shell 脚本</option>
              <option value="python">Python 任务</option>
            </select>
          </div>
          <div class="form-item">
            <label>名称</label
            ><input
              v-model="createName"
              placeholder="请输入名称"
              @keyup.enter="createNode"
            />
          </div>
          <div style="font-size: 12px; color: #8b95a5">
            文件会创建在当前选中的文件夹下，未选中文件夹时创建在根目录。
          </div>
        </div>
        <div class="modal-ft">
          <button class="btn" @click="showModal = false">取消</button
          ><button class="btn primary" @click="createNode">确定</button>
        </div>
      </div>
    </div>
  </section>
  <el-dialog v-model="historyVisible" title="查询历史记录" width="min(980px, 92vw)">
    <div class="query-history">
      <table class="data-table" v-if="queryHistory.length">
        <thead><tr><th>执行时间</th><th>数据源</th><th>状态</th><th>耗时</th><th>SQL</th><th>错误</th></tr></thead>
        <tbody><tr v-for="item in queryHistory" :key="String(item.queryId)">
          <td>{{ item.startedAt || '-' }}</td><td>{{ item.databaseName || 'StarRocks' }}</td><td>{{ item.status }}</td>
          <td>{{ item.elapsedMs || 0 }} ms</td><td class="history-sql">{{ item.sql }}</td><td class="history-error">{{ item.errorMessage || '-' }}</td>
        </tr></tbody>
      </table>
      <div v-else class="empty-state">暂无持久化查询记录</div>
    </div>
  </el-dialog>
</template>

<style scoped>
.tab-action,
.plain-tab,
.result-tab-button {
  border: 0;
  background: transparent;
  cursor: pointer;
}
.plain-tab {
  color: #667085;
}
.query-history { max-height: 60vh; overflow: auto; }
.history-sql { max-width: 360px; white-space: pre-wrap; word-break: break-word; font: 12px/1.5 Consolas, Monaco, monospace; }
.history-error { max-width: 220px; color: #c45656; white-space: pre-wrap; word-break: break-word; }
</style>
