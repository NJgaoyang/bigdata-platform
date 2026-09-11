<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowDownBold, ArrowRightBold, ArrowUpBold, DocumentChecked, FullScreen, RefreshRight, Tickets } from "@element-plus/icons-vue";
import { platformApi, type DataSource } from "../api";
import * as monaco from "monaco-editor";

type BottomPanel = "result" | "history";
interface QueryTab {
  id: number;
  name: string;
  sql: string;
  columns: string[];
  rows: Record<string, unknown>[];
  message: string;
  executionId: string;
  errorMessage: string;
  fileId?: number;
  savedSql?: string;
  savedName?: string;
}
let nextTabId = 1;
const queryTabs = ref<QueryTab[]>([{
  id: nextTabId,
  name: "查询窗口 1",
  sql: "",
  columns: [],
  rows: [],
  message: "",
  executionId: "",
  errorMessage: "",
}]);
const activeQueryTabId = ref(nextTabId);
const activeBottomPanel = ref<BottomPanel>("result");
const activeQueryTab = computed<QueryTab>(() => queryTabs.value.find(tab => tab.id === activeQueryTabId.value) || queryTabs.value[0]!);
const query = computed({ get: () => activeQueryTab.value.sql, set: value => { activeQueryTab.value.sql = value; } });
const selectedTable = ref("");
const metaSearch = ref("");
const runMessage = computed({ get: () => activeQueryTab.value.message, set: value => { activeQueryTab.value.message = value; } });
const executionId = computed({ get: () => activeQueryTab.value.executionId, set: value => { activeQueryTab.value.executionId = value; } });
const metadata = ref<Record<string, string[]>>({});
const fieldMap = ref<Record<string, string[][]>>({});
const activeDataSourceId = ref<number>();
const sources = ref<DataSource[]>([]);
const activeDatabase = ref("");
const editorContainer = ref<HTMLElement>();
const pageContainer = ref<HTMLElement>();
const editorShell = ref<HTMLElement>();
const leftPanelWidth = ref(248);
const resultPanelHeight = ref(235);
const editorCollapsed = ref(false);
const expandedDatabases = ref<Set<string>>(new Set());
const expandedTables = ref<Set<string>>(new Set());
const isFullscreen = ref(false);
const resultColumns = computed({ get: () => activeQueryTab.value.columns, set: value => { activeQueryTab.value.columns = value; } });
const resultRows = computed({ get: () => activeQueryTab.value.rows, set: value => { activeQueryTab.value.rows = value; } });
const queryHistory = ref<Record<string, unknown>[]>([]);
const queryTimers = new Map<number, number>();
let monacoEditor: monaco.editor.IStandaloneCodeEditor | null = null;
let editorChangeDisposable: monaco.IDisposable | null = null;
let completionDisposable: monaco.IDisposable | null = null;
let stopActiveResize: (() => void) | null = null;
let tableClickTimer: number | undefined;
const shownMeta = computed(() =>
  Object.entries(metadata.value)
    .map(([db, tables]) => ({
      db,
      tables: tables.filter(
        (t) => !metaSearch.value || t.includes(metaSearch.value.toLowerCase()),
      ),
    }))
    .filter((item) => item.tables.length),
);
function fieldsFor(database: string, table: string) {
  return fieldMap.value[`${database}.${table}`] || [];
}
function isTableExpanded(database: string, table: string) {
  return expandedTables.value.has(`${database}.${table}`);
}
function isDatabaseExpanded(database: string) {
  return expandedDatabases.value.has(database);
}
function toggleDatabase(database: string) {
  const next = new Set(expandedDatabases.value);
  if (next.has(database)) next.delete(database);
  else next.add(database);
  expandedDatabases.value = next;
}
onMounted(async () => {
  try {
    sources.value = ((await platformApi.dataSources()).data.data || []).filter(
      (source) => source.metadataVisible !== false,
    );
    const source = sources.value[0];
    if (!source) {
      metadata.value = {};
      selectedTable.value = "";
      activeDatabase.value = "";
      runMessage.value = "请先配置数据源";
      initEditor();
      return;
    }
    await loadMetadata(source);
  } catch {
    activeDataSourceId.value = undefined;
    metadata.value = {};
    selectedTable.value = "";
    activeDatabase.value = "";
    runMessage.value = "数据源元数据暂不可用";
  }
  initEditor();
});
function initEditor() {
  if (!editorContainer.value || monacoEditor) return;
  monacoEditor = monaco.editor.create(editorContainer.value, {
    value: query.value,
    language: "sql",
    theme: "vs",
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    lineHeight: 22,
    tabSize: 4,
    insertSpaces: true,
    wordWrap: "off",
    padding: { top: 12, bottom: 12 },
    fixedOverflowWidgets: true,
    suggest: { showKeywords: true, showFunctions: true },
  });
  editorChangeDisposable = monacoEditor.onDidChangeModelContent(() => {
    query.value = monacoEditor?.getValue() || "";
  });
  completionDisposable?.dispose();
  completionDisposable = monaco.languages.registerCompletionItemProvider("sql", {
    triggerCharacters: ["."],
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position);
      const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
      const keywords = ["SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "LIMIT", "JOIN", "LEFT JOIN", "ON", "AS", "AND", "OR"];
      const functions = ["COUNT(*)", "SUM()", "AVG()", "MAX()", "MIN()"];
      const suggestions: monaco.languages.CompletionItem[] = [
        ...Object.keys(metadata.value).map((database) => ({
          label: database,
          kind: monaco.languages.CompletionItemKind.Module,
          insertText: database,
          detail: "数据库",
          sortText: `0-${database}`,
          range,
        })),
        ...Object.entries(metadata.value).flatMap(([database, tables]) => tables.map((table) => ({
          label: table,
          kind: monaco.languages.CompletionItemKind.Class,
          insertText: table,
          detail: `数据表 · ${database}.${table}`,
          sortText: `1-${table}`,
          range,
        }))),
        ...keywords.map((keyword) => ({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: keyword,
          detail: "SQL 关键字",
          sortText: `2-${keyword}`,
          range,
        })),
        ...functions.map((name) => ({
          label: name,
          kind: monaco.languages.CompletionItemKind.Function,
          insertText: name,
          detail: "SQL 函数",
          sortText: `3-${name}`,
          range,
        })),
      ];
      return { suggestions };
    },
  });
  monacoEditor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => saveQueryTab());
}
async function loadMetadata(source: DataSource) {
  activeDataSourceId.value = source.id;
  metadata.value = {};
  fieldMap.value = {};
  expandedTables.value = new Set();
  selectedTable.value = "";
  activeDatabase.value = "";
  const databases =
    (await platformApi.metadataDatabases(source.id, source.type)).data.data ||
    [];
  const next: Record<string, string[]> = {};
  const orderedDatabases = [...databases].sort((left, right) => {
    if (left.name === source.databaseName) return -1;
    if (right.name === source.databaseName) return 1;
    const leftSystem = left.name.toLowerCase() === "information_schema";
    const rightSystem = right.name.toLowerCase() === "information_schema";
    return Number(leftSystem) - Number(rightSystem);
  });
  const tableResults = await Promise.allSettled(
    orderedDatabases.map(async (database) => ({
      database: database.name,
      tables: (await platformApi.metadataTables(source.id, database.name)).data.data || [],
    })),
  );
  for (const result of tableResults) {
    if (result.status === "fulfilled") {
      next[result.value.database] = result.value.tables.map((table) => table.name);
    }
  }
  metadata.value = next;
  expandedDatabases.value = new Set(Object.keys(next));
  if (Object.keys(next).length) {
    const database = Object.prototype.hasOwnProperty.call(next, source.databaseName)
      ? source.databaseName
      : Object.keys(next).find((name) => name.toLowerCase() !== "information_schema") || Object.keys(next)[0];
    const table = next[database]?.[0];
    activeDatabase.value = database;
    selectedTable.value = table || "";
  }
}
async function changeDataSource() {
  const source = sources.value.find(
    (item) => item.id === activeDataSourceId.value,
  );
  if (source) {
    await loadMetadata(source).catch(() => ElMessage.error("元数据加载失败"));
    monacoEditor?.focus();
  }
}
async function openHistory() {
  activeBottomPanel.value = "history";
  try {
    queryHistory.value = (await platformApi.queryHistory()).data.data || [];
  } catch {
    ElMessage.error("查询历史加载失败");
  }
}
async function showFields(table: string, database = activeDatabase.value) {
  selectedTable.value = table;
  activeDatabase.value = database;
  const fieldKey = `${database}.${table}`;
  if (activeDataSourceId.value && database && !fieldMap.value[fieldKey]) {
    try {
      const result = await platformApi.metadataColumns(
        activeDataSourceId.value,
        database,
        table,
      );
      fieldMap.value[fieldKey] = (result.data.data || []).map((column) => [
        column.name,
        column.dataType,
        column.comment || "",
      ]);
    } catch {
      ElMessage.error("字段结构加载失败");
      return;
    }
  }
}
async function toggleTableFields(table: string, database: string) {
  const key = `${database}.${table}`;
  if (expandedTables.value.has(key)) {
    expandedTables.value.delete(key);
    return;
  }
  await showFields(table, database);
  if (Object.prototype.hasOwnProperty.call(fieldMap.value, key)) expandedTables.value.add(key);
}
function selectTableWithDelay(table: string, database: string) {
  if (tableClickTimer) window.clearTimeout(tableClickTimer);
  tableClickTimer = window.setTimeout(() => {
    tableClickTimer = undefined;
    void toggleTableFields(table, database);
  }, 220);
}
async function insertTableFromTree(table: string, database: string) {
  if (tableClickTimer) window.clearTimeout(tableClickTimer);
  tableClickTimer = undefined;
  await showFields(table, database);
  const key = `${database}.${table}`;
  if (Object.prototype.hasOwnProperty.call(fieldMap.value, key)) expandedTables.value.add(key);
  insertTableName(table);
}
function insertEditorText(value: string) {
  if (!value || !monacoEditor) return;
  if (editorCollapsed.value) editorCollapsed.value = false;
  const selection = monacoEditor.getSelection();
  if (!selection) return;
  monacoEditor.pushUndoStop();
  monacoEditor.executeEdits("metadata-double-click", [{ range: selection, text: value, forceMoveMarkers: true }]);
  monacoEditor.pushUndoStop();
  monacoEditor.focus();
  window.setTimeout(() => monacoEditor?.layout(), 0);
}
function insertTableName(table: string) {
  insertEditorText(table);
}
function insertFieldName(field: string) {
  insertEditorText(field);
}
function resolveQueryDatabase(sql: string) {
  const references = [...sql.matchAll(/\b(?:from|join)\s+((?:`[^`]+`|[a-zA-Z_][\w$]*)(?:\s*\.\s*(?:`[^`]+`|[a-zA-Z_][\w$]*))?)/gi)]
    .map((match) => match[1].replace(/`/g, "").replace(/\s+/g, ""));
  const qualified = references.find((reference) => reference.includes("."));
  if (qualified) return qualified.split(".")[0];

  const tableNames = references.map((reference) => reference.toLowerCase());
  const containsAllTables = (database: string) => tableNames.every((table) =>
    (metadata.value[database] || []).some((candidate) => candidate.toLowerCase() === table),
  );
  if (tableNames.length && activeDatabase.value && containsAllTables(activeDatabase.value)) {
    return activeDatabase.value;
  }
  const sourceDatabase = sources.value.find((source) => source.id === activeDataSourceId.value)?.databaseName;
  if (tableNames.length && sourceDatabase && containsAllTables(sourceDatabase)) return sourceDatabase;
  const metadataDatabase = Object.keys(metadata.value).find(containsAllTables);
  return metadataDatabase || sourceDatabase || activeDatabase.value;
}
async function runExplore() {
  if (!activeDataSourceId.value) {
    runMessage.value = "未选择数据源";
    ElMessage.warning("请先在左侧元数据区域选择数据源");
    return;
  }
  runMessage.value = "执行中...";
  const selection = monacoEditor?.getSelection();
  const model = monacoEditor?.getModel();
  const hasSelection = Boolean(selection && model && !selection.isEmpty());
  const start = hasSelection && selection && model ? model.getOffsetAt(selection.getStartPosition()) : 0;
  const end = hasSelection && selection && model ? model.getOffsetAt(selection.getEndPosition()) : 0;
  const sql = start !== end ? query.value.slice(start, end) : query.value;
  if (!sql.trim()) {
    runMessage.value = "请输入 SQL 后再执行";
    activeBottomPanel.value = "result";
    ElMessage.warning("请输入 SQL 后再执行");
    return;
  }
  const tabId = activeQueryTabId.value;
  const queryDatabase = resolveQueryDatabase(sql);
  if (queryDatabase) activeDatabase.value = queryDatabase;
  activeQueryTab.value.errorMessage = "";
  try {
    const submitted = await platformApi.querySubmit(
      sql,
      start !== end,
      activeDataSourceId.value,
      queryDatabase,
    );
    executionId.value = String(submitted.data.data?.executionId || "");
    runMessage.value = "执行中...";
    if (executionId.value) pollQuery(executionId.value, tabId);
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || "SQL 执行失败";
    activeQueryTab.value.errorMessage = message;
    runMessage.value = `执行失败：${message}`;
    activeBottomPanel.value = "result";
    ElMessage.error(message);
  }
}
async function explainQuery() {
  if (!activeDataSourceId.value) {
    ElMessage.warning("请先在左侧元数据区域选择数据源");
    return;
  }
  const sql = query.value.trim();
  if (!sql) {
    ElMessage.warning("请输入 SQL 后再生成执行计划");
    return;
  }
  runMessage.value = "执行计划生成中...";
  const queryDatabase = resolveQueryDatabase(sql);
  if (queryDatabase) activeDatabase.value = queryDatabase;
  try {
    const explainSql = /^explain\b/i.test(sql) ? sql : `EXPLAIN ${sql}`;
    const submitted = await platformApi.querySubmit(
      explainSql,
      false,
      activeDataSourceId.value,
      queryDatabase,
    );
    executionId.value = String(submitted.data.data?.executionId || "");
    if (executionId.value) pollQuery(executionId.value, activeQueryTabId.value);
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || "执行计划生成失败";
    activeQueryTab.value.errorMessage = message;
    runMessage.value = `执行计划生成失败：${message}`;
    activeBottomPanel.value = "result";
    ElMessage.error(message);
  }
}
function pollQuery(id: string, tabId: number) {
  const existingTimer = queryTimers.get(tabId);
  if (existingTimer) window.clearInterval(existingTimer);
  const timer = window.setInterval(async () => {
    try {
      const result = await platformApi.queryStatus(id);
      const data = result.data.data || {};
      const status = String(data.status || "RUNNING");
      if (status === "RUNNING") return;
      const tab = queryTabs.value.find(item => item.id === tabId);
      if (!tab) {
        window.clearInterval(timer);
        queryTimers.delete(tabId);
        return;
      }
      tab.columns = Array.isArray(data.columns) ? data.columns.map(String) : [];
      tab.rows = Array.isArray(data.rows) ? (data.rows as Record<string, unknown>[]) : [];
      tab.errorMessage = String(data.errorMessage || "");
      tab.message = `执行${status === "SUCCESS" ? "完成" : status === "CANCELED" ? "已停止" : "失败"} · 返回 ${data.rowCount || 0} 行${tab.errorMessage ? ` · ${tab.errorMessage}` : ""}`;
      window.clearInterval(timer);
      queryTimers.delete(tabId);
      if (status !== "SUCCESS" && activeQueryTabId.value === tabId) activeBottomPanel.value = "result";
      if (status === "SUCCESS") ElMessage.success("SQL 执行成功");
    } catch (error: any) {
      window.clearInterval(timer);
      queryTimers.delete(tabId);
      const tab = queryTabs.value.find(item => item.id === tabId);
      if (tab) {
        tab.errorMessage = error?.response?.data?.message || error?.message || "查询状态获取失败";
        tab.message = tab.errorMessage;
      }
    }
  }, 500);
  queryTimers.set(tabId, timer);
}
async function stopExplore() {
  if (!executionId.value) {
    ElMessage.warning("当前没有运行中的查询");
    return;
  }
  await platformApi.cancelQuery(executionId.value).catch(() => undefined);
  const timer = queryTimers.get(activeQueryTabId.value);
  if (timer) window.clearInterval(timer);
  queryTimers.delete(activeQueryTabId.value);
  executionId.value = "";
  runMessage.value = "查询已停止";
  ElMessage.info("查询已停止");
}
function newQueryTab() {
  nextTabId += 1;
  queryTabs.value.push({ id: nextTabId, name: `查询窗口 ${nextTabId}`, sql: "", columns: [], rows: [], message: "", executionId: "", errorMessage: "" });
  switchQueryTab(nextTabId);
}
function switchQueryTab(id: number) {
  activeQueryTabId.value = id;
  monacoEditor?.setValue(activeQueryTab.value.sql);
}
function closeQueryTab(id: number) {
  if (queryTabs.value.length === 1) {
    Object.assign(queryTabs.value[0], { sql: "", columns: [], rows: [], message: "", executionId: "", errorMessage: "" });
    monacoEditor?.setValue("");
    return;
  }
  const index = queryTabs.value.findIndex(tab => tab.id === id);
  const timer = queryTimers.get(id);
  if (timer) window.clearInterval(timer);
  queryTimers.delete(id);
  queryTabs.value.splice(index, 1);
  if (activeQueryTabId.value === id) switchQueryTab(queryTabs.value[Math.max(0, index - 1)].id);
}
function formatCode() {
  query.value = query.value
    .replace(
      /\s+(SELECT|FROM|WHERE|GROUP BY|ORDER BY|LIMIT)\s+/gi,
      (_, key) => "\n" + key.toUpperCase() + " ",
    )
    .trim();
  monacoEditor?.setValue(query.value);
  ElMessage.success("格式化完成");
}
async function renameQueryTab(tab: QueryTab = activeQueryTab.value) {
  const previousName = tab.name;
  try {
    const result = await ElMessageBox.prompt("请输入新的查询窗口名称", "重命名查询窗口", {
      inputValue: tab.name,
      inputPlaceholder: "例如：订单分析",
      inputPattern: /\S+/,
      inputErrorMessage: "名称不能为空",
      confirmButtonText: "确定",
      cancelButtonText: "取消",
    });
    tab.name = String(result.value).trim().replace(/\.sql$/i, "");
    if (tab.fileId) {
      await platformApi.saveFile(tab.fileId, tab.sql, `${tab.name}.sql`);
      tab.savedName = tab.name;
    }
    ElMessage.success("查询窗口已重命名");
  } catch (error: any) {
    tab.name = previousName;
    if (error !== "cancel" && error !== "close") ElMessage.error(error?.message || "重命名失败");
  }
}
async function saveQueryTab() {
  const tab = activeQueryTab.value;
  if (!tab.sql.trim()) {
    ElMessage.warning("请输入 SQL 后再保存");
    return;
  }
  try {
    const fileName = `${tab.name.replace(/\.sql$/i, "")}.sql`;
    if (tab.fileId) {
      await platformApi.saveFile(tab.fileId, tab.sql, fileName);
    } else {
      let project = (await platformApi.projects()).data.data?.[0];
      if (!project) {
        project = (await platformApi.createProject({ name: "数仓开发项目", description: "平台默认开发工作区" })).data.data;
      }
      const saved = await platformApi.createFile({
        projectId: project.id,
        folderId: null,
        name: fileName,
        fileType: "SQL",
        content: tab.sql,
      });
      tab.fileId = saved.data.data.id;
    }
    tab.savedSql = tab.sql;
    tab.savedName = tab.name;
    ElMessage.success("SQL 已保存到数据开发工作区");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || "SQL 保存失败");
  }
}
function toggleEditor() {
  editorCollapsed.value = !editorCollapsed.value;
  window.setTimeout(() => monacoEditor?.layout(), 180);
}
function startResize(
  event: PointerEvent,
  cursor: "col-resize" | "row-resize",
  onMove: (event: PointerEvent) => void,
) {
  event.preventDefault();
  stopActiveResize?.();
  const previousCursor = document.body.style.cursor;
  const previousUserSelect = document.body.style.userSelect;
  document.body.style.cursor = cursor;
  document.body.style.userSelect = "none";
  const finish = () => {
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerup", finish);
    document.body.style.cursor = previousCursor;
    document.body.style.userSelect = previousUserSelect;
    stopActiveResize = null;
    monacoEditor?.layout();
  };
  stopActiveResize = finish;
  window.addEventListener("pointermove", onMove);
  window.addEventListener("pointerup", finish, { once: true });
}
function resizeLeftPanel(event: PointerEvent) {
  const startX = event.clientX;
  const startWidth = leftPanelWidth.value;
  startResize(event, "col-resize", (moveEvent) => {
    leftPanelWidth.value = Math.min(380, Math.max(180, startWidth + moveEvent.clientX - startX));
    monacoEditor?.layout();
  });
}
function resizeResultPanel(event: PointerEvent) {
  const startY = event.clientY;
  const startHeight = resultPanelHeight.value;
  startResize(event, "row-resize", (moveEvent) => {
    const shellHeight = editorShell.value?.clientHeight || 600;
    const maximum = Math.max(180, shellHeight - 140);
    resultPanelHeight.value = Math.min(maximum, Math.max(120, startHeight + startY - moveEvent.clientY));
    monacoEditor?.layout();
  });
}
async function toggleFullscreen() {
  const element = pageContainer.value;
  if (!element) return;
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await element.requestFullscreen();
  } catch {
    ElMessage.error("浏览器无法进入全屏模式");
  }
}
function syncFullscreenState() {
  isFullscreen.value = document.fullscreenElement === pageContainer.value;
  window.setTimeout(() => monacoEditor?.layout(), 60);
}
onBeforeUnmount(() => {
  stopActiveResize?.();
  if (tableClickTimer) window.clearTimeout(tableClickTimer);
  document.removeEventListener("fullscreenchange", syncFullscreenState);
  queryTimers.forEach(timer => window.clearInterval(timer));
  queryTimers.clear();
  editorChangeDisposable?.dispose();
  completionDisposable?.dispose();
  monacoEditor?.dispose();
  monacoEditor = null;
});
onMounted(() => document.addEventListener("fullscreenchange", syncFullscreenState));
</script>

<template>
  <section ref="pageContainer" class="page explore-page" :class="{ 'is-fullscreen': isFullscreen }">
    <div class="module-bar">
      <div class="module-title">数据探查</div>
      <span class="crumb">/ SQL 查询</span>
    </div>
    <div class="split">
      <aside class="left-panel explore-left-panel" :style="{ width: `${leftPanelWidth}px` }">
        <div class="panel-head">
          <div class="panel-title">元数据</div>
          <div class="panel-actions">
            <button class="btn small icononly" title="刷新元数据" aria-label="刷新元数据" @click="changeDataSource">
              <RefreshRight />
            </button>
          </div>
        </div>
        <div class="metadata-source-picker">
          <label for="metadata-source">数据源</label>
          <select id="metadata-source" v-model="activeDataSourceId" @change="changeDataSource">
            <option v-for="source in sources" :key="source.id" :value="source.id">{{ source.name }}（{{ source.type }}）</option>
            <option v-if="!sources.length" disabled>暂无数据源</option>
          </select>
        </div>
        <div class="metadata-search">
          <input v-model="metaSearch" class="search" placeholder="搜索表名" />
        </div>
        <div class="workspace-resource-tree">
          <div v-for="item in shownMeta" :key="item.db">
            <div class="tree-row database-tree-row" :title="isDatabaseExpanded(item.db) ? '收起全部表' : '展开全部表'" @click="toggleDatabase(item.db)">
              <button class="tree-toggle" :aria-label="isDatabaseExpanded(item.db) ? `收起 ${item.db} 下的全部表` : `展开 ${item.db} 下的全部表`" @click.stop="toggleDatabase(item.db)">
                <ArrowDownBold v-if="isDatabaseExpanded(item.db)" />
                <ArrowRightBold v-else />
              </button>
              <span class="tree-icon db">◉</span><b>{{ item.db }}</b
              ><span class="count">{{ item.tables.length }}</span>
            </div>
            <div v-for="table in (isDatabaseExpanded(item.db) ? item.tables : [])" :key="table" class="table-tree-node">
              <div
                class="tree-row indent-1"
                :class="{ selected: selectedTable === table && activeDatabase === item.db }"
                title="单击展开字段，双击插入表名"
                @click="selectTableWithDelay(table, item.db)"
                @dblclick.stop="insertTableFromTree(table, item.db)"
              >
                <button class="tree-toggle" :title="isTableExpanded(item.db, table) ? '收起字段' : '展开字段'" @click.stop="toggleTableFields(table, item.db)">
                  <ArrowDownBold v-if="isTableExpanded(item.db, table)" />
                  <ArrowRightBold v-else />
                </button>
                <span class="tree-icon tableico">▤</span><span>{{ table }}</span>
              </div>
              <div v-if="isTableExpanded(item.db, table)" class="field-tree">
                <el-tooltip
                  v-for="field in fieldsFor(item.db, table)"
                  :key="field[0]"
                  :content="field[2] || '暂无中文注释'"
                  placement="right"
                  :show-after="300"
                >
                  <div
                    class="tree-row indent-2 field-tree-row"
                    :aria-label="`${field[0]}，${field[2] || '暂无中文注释'}，双击插入字段名`"
                    @dblclick.stop="insertFieldName(field[0])"
                  >
                    <span class="tree-arrow"></span><span class="tree-icon field-icon"><Tickets /></span><span>{{ field[0] }}</span>
                  </div>
                </el-tooltip>
                <div v-if="!fieldsFor(item.db, table).length" class="tree-empty indent-2">暂无字段</div>
              </div>
            </div>
          </div>
        </div>
      </aside>
      <div class="horizontal-resizer" title="左右拖动调整元数据区域宽度" @pointerdown="resizeLeftPanel"></div>
      <div class="explore-layout">
        <section class="explore-main">
          <div class="tabbar">
            <div v-for="tab in queryTabs" :key="tab.id" class="tab" :class="{ active: activeQueryTabId === tab.id }" @click="switchQueryTab(tab.id)">
              <span class="sql">SQL</span
              ><span class="tab-name" :title="tab.name" @dblclick.stop="renameQueryTab(tab)">{{ tab.name }}</span
              ><span v-if="tab.fileId && (tab.savedSql !== tab.sql || tab.savedName !== tab.name)" class="tab-dirty" title="有未保存的修改"></span
              ><button class="close tab-control" @click.stop="closeQueryTab(tab.id)">×</button>
            </div>
            <button class="tab tab-control" style="min-width: 38px" @click="newQueryTab">＋</button>
          </div>
          <div class="editor-toolbar">
            <button class="btn small primary" @click="runExplore">▶ 运行</button
            ><button class="btn small" @click="stopExplore">■ 停止</button>
            <div class="toolbar-sep"></div>
            <button class="btn small" @click="formatCode">{ } 格式化</button
            ><button
              class="btn small explain-action"
              @click="explainQuery"
            >
              执行计划</button>
            <button class="btn small editor-action" title="保存到数据开发工作区（Ctrl/Cmd + S）" @click="saveQueryTab"><DocumentChecked /><span>保存</span></button>
            <button class="btn small editor-action editor-collapse-button" :title="editorCollapsed ? '展开 SQL 编辑器' : '收起 SQL 编辑器'" @click="toggleEditor">
              <ArrowDownBold v-if="editorCollapsed" />
              <ArrowUpBold v-else />
              <span>{{ editorCollapsed ? "展开编辑器" : "收起编辑器" }}</span>
            </button>
            <div class="toolbar-view-actions">
              <button class="btn small fullscreen-button" :title="isFullscreen ? '退出全屏' : '全屏编辑'" @click="toggleFullscreen">
                <FullScreen /><span>{{ isFullscreen ? "退出全屏" : "全屏" }}</span>
              </button>
            </div>
          </div>
          <div ref="editorShell" class="editor-shell explore-editor-shell" :class="{ 'editor-collapsed': editorCollapsed }">
            <div v-show="!editorCollapsed" class="editor-region">
              <div ref="editorContainer" class="code-area monaco-code-area explore-monaco"></div>
              <div v-if="!query.trim()" class="editor-empty-hint">提示：选中 SQL 后点击“运行”可仅执行选中部分</div>
            </div>
            <div v-show="!editorCollapsed" class="vertical-resizer" title="上下拖动调整结果区域高度" @pointerdown="resizeResultPanel"><span></span></div>
            <div class="result-panel explore-result-panel" :style="{ height: `${resultPanelHeight}px` }">
              <div class="result-head">
                <button class="result-tab-button" :class="{ active: activeBottomPanel === 'result' }" @click="activeBottomPanel = 'result'">结果集 1</button>
                <button class="result-tab-button" :class="{ active: activeBottomPanel === 'history' }" @click="openHistory">历史记录</button>
                <div class="run-msg">{{ runMessage }}</div>
              </div>
              <div v-if="activeBottomPanel === 'result'" class="table-wrap">
                <table v-if="resultColumns.length" class="scrollable-result-table">
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
                <div v-else class="result-empty" aria-hidden="true"></div>
              </div>
              <div v-else class="table-wrap query-history">
                <table v-if="queryHistory.length" class="data-table">
                  <thead><tr><th>执行时间</th><th>数据源</th><th>状态</th><th>耗时</th><th>SQL</th><th>错误</th></tr></thead>
                  <tbody><tr v-for="item in queryHistory" :key="String(item.queryId)"><td>{{ item.startedAt || '-' }}</td><td>{{ item.databaseName || '默认数据库' }}</td><td>{{ item.status }}</td><td>{{ item.elapsedMs || 0 }} ms</td><td class="history-sql">{{ item.sql }}</td><td class="history-error">{{ item.errorMessage || '-' }}</td></tr></tbody>
                </table>
                <div v-else class="empty-state">暂无持久化查询记录</div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.explore-page { background:#fff; overflow:hidden; }
.explore-page:fullscreen,
.explore-page.is-fullscreen { width:100vw; height:100vh; background:#fff; }
.explore-left-panel { min-width:180px; max-width:380px; flex:0 0 auto; }
.horizontal-resizer { width:5px; flex:0 0 5px; margin-left:-1px; cursor:col-resize; position:relative; z-index:5; background:transparent; }
.horizontal-resizer::after { content:""; position:absolute; inset:0 2px; background:#e5e9f0; transition:background .15s; }
.horizontal-resizer:hover::after { background:#7eb4ff; }
.panel-actions svg { width:15px; height:15px; }
.editor-toolbar { min-width:0; overflow:hidden; }
.toolbar-view-actions { margin-left:auto; display:flex; align-items:center; padding-left:10px; }
.metadata-source-picker { padding:9px 10px; border-bottom:1px solid var(--line2); }
.metadata-source-picker label { display:block; margin-bottom:5px; color:#667085; font-size:12px; }
.metadata-source-picker select { width:100%; height:31px; padding:0 8px; border:1px solid #d9e0e8; border-radius:6px; outline:none; background:#fff; color:#344054; font-size:12px; }
.metadata-source-picker select:focus { border-color:#7eb4ff; box-shadow:0 0 0 2px rgba(22,119,255,.08); }
.editor-action svg { width:13px; height:13px; }
.tab-dirty { width:6px; height:6px; flex:0 0 6px; border-radius:50%; background:#fa8c16; }
.fullscreen-button svg { width:14px; height:14px; }
.tree-toggle { width:12px; height:24px; flex:0 0 12px; padding:0; border:0; background:transparent; color:#667085; display:grid; place-items:center; }
.tree-toggle svg { width:9px; height:9px; }
.database-tree-row { cursor:pointer; }
.field-icon { color:#77a9dc; }
.field-icon svg { width:14px; height:14px; vertical-align:middle; }
.field-tree-row { cursor:copy; color:#344054; }
.field-tree-row:hover { color:#1677ff; }
.tree-empty { padding:5px 8px; color:#98a2b3; font-size:12px; }
.explore-editor-shell { min-width:0; }
.editor-region { position:relative; flex:1 1 auto; min-height:120px; overflow:hidden; }
.editor-region .code-area { width:100%; height:100%; min-height:0; }
.editor-empty-hint { position:absolute; left:65px; top:16px; z-index:5; pointer-events:none; color:#98a2b3; font-size:12px; }
.explore-monaco :deep(.suggest-widget) { border:1px solid #cfd8e3; border-radius:6px; box-shadow:0 8px 22px rgba(31,45,61,.14); }
.vertical-resizer { height:7px; flex:0 0 7px; cursor:row-resize; background:#fff; border-top:1px solid #e5e9f0; border-bottom:1px solid #eef1f5; display:flex; align-items:center; justify-content:center; position:relative; z-index:6; }
.vertical-resizer span { width:34px; height:2px; border-radius:2px; background:#cbd5e1; }
.vertical-resizer:hover { background:#f3f8ff; border-color:#b5d2fa; }
.vertical-resizer:hover span { background:#1677ff; }
.explore-result-panel { flex:0 0 auto; min-height:120px; max-height:calc(100% - 120px); margin:0 8px 8px; border:1px solid #dfe6ee; border-top:0; border-radius:0 0 8px 8px; overflow:hidden; box-shadow:0 2px 6px rgba(31,45,61,.04); }
.explore-result-panel .result-head { background:#fbfcfe; }
.explore-result-panel .table-wrap { min-width:0; min-height:0; overflow:auto; overscroll-behavior:contain; }
.explore-result-panel .table-wrap::-webkit-scrollbar { width:9px; height:9px; }
.explore-result-panel .table-wrap::-webkit-scrollbar-thumb { border:2px solid transparent; border-radius:8px; background:#b8c2cf; background-clip:padding-box; }
.explore-result-panel .table-wrap::-webkit-scrollbar-track { background:#f6f8fb; }
.editor-collapsed .explore-result-panel { flex:1 1 auto; height:auto !important; max-height:none; }
.scrollable-result-table { width:max-content; min-width:100%; }
.scrollable-result-table th,
.scrollable-result-table td { min-width:120px; max-width:420px; overflow:hidden; text-overflow:ellipsis; }
.tab-control { border: 0; cursor: pointer; }
.result-tab-button { align-self:stretch; border:0; border-bottom:2px solid transparent; background:transparent; color:#667085; cursor:pointer; }
.result-tab-button.active { border-bottom-color:#1677ff; color:#1677ff; font-weight:600; }
.query-history { overflow: auto; }
.history-sql { max-width: 360px; white-space: pre-wrap; word-break: break-word; font: 12px/1.5 Consolas, Monaco, monospace; }
.history-error { max-width: 220px; color: #c45656; white-space: pre-wrap; word-break: break-word; }
@media (max-width: 1100px) {
  .fullscreen-button span,
  .editor-collapse-button span { display:none; }
}
@media (max-width: 1400px) {
  .editor-action span { display:none; }
  .editor-action { width:28px; padding:0; }
}
@media (max-width: 1100px) {
  .explain-action { display:none; }
}
</style>
