<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { platformApi, type DataSource } from "../api";
import * as monaco from "monaco-editor";

const query = ref("");
const selectedTable = ref("");
const metaSearch = ref("");
const runMessage = ref("运行查询后显示真实结果");
const executionId = ref("");
const metadata = ref<Record<string, string[]>>({});
const fieldMap = ref<Record<string, string[][]>>({});
const activeDataSourceId = ref<number>();
const sources = ref<DataSource[]>([]);
const activeDatabase = ref("");
const editorContainer = ref<HTMLElement>();
const resultColumns = ref<string[]>([]);
const resultRows = ref<Record<string, unknown>[]>([]);
const historyVisible = ref(false);
const queryHistory = ref<Record<string, unknown>[]>([]);
let queryTimer: number | undefined;
let monacoEditor: monaco.editor.IStandaloneCodeEditor | null = null;
let editorChangeDisposable: monaco.IDisposable | null = null;
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
const fields = computed(
  () => fieldMap.value[selectedTable.value] || [],
);
onMounted(async () => {
  try {
    sources.value = ((await platformApi.dataSources()).data.data || []).filter(
      (source) => source.type === "STARROCKS",
    );
    const source = sources.value[0];
    if (!source) {
      metadata.value = {};
      selectedTable.value = "";
      activeDatabase.value = "";
      runMessage.value = "请先配置 StarRocks 数据源";
      initEditor();
      return;
    }
    await loadMetadata(source);
  } catch {
    activeDataSourceId.value = undefined;
    metadata.value = {};
    selectedTable.value = "";
    activeDatabase.value = "";
    runMessage.value = "StarRocks 元数据暂不可用";
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
    suggest: { showKeywords: true, showFunctions: true },
  });
  editorChangeDisposable = monacoEditor.onDidChangeModelContent(() => {
    query.value = monacoEditor?.getValue() || "";
  });
  monaco.languages.registerCompletionItemProvider("sql", {
    provideCompletionItems(model, position) {
      const word = model.getWordUntilPosition(position);
      const range = new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn);
      const items = ["SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "LIMIT", "COUNT(*)", "SUM()", ...Object.values(metadata.value).flat()];
      return { suggestions: items.map(label => ({ label, kind: monaco.languages.CompletionItemKind.Keyword, insertText: label, range })) };
    },
  });
}
async function loadMetadata(source: DataSource) {
  activeDataSourceId.value = source.id;
  const databases =
    (await platformApi.metadataDatabases(source.id, source.type)).data.data ||
    [];
  const next: Record<string, string[]> = {};
  for (const database of databases.slice(0, 8)) {
    const tables =
      (await platformApi.metadataTables(source.id, database.name)).data.data ||
      [];
    next[database.name] = tables.map((table) => table.name);
  }
  metadata.value = next;
  if (Object.keys(next).length) {
    const database = Object.keys(next)[0];
    const table = next[database]?.[0];
    activeDatabase.value = database;
    selectedTable.value = table || "";
    if (table)
      query.value = `-- ${source.name} · ${database}.${table}\nSELECT *\nFROM ${database}.${table}\nLIMIT 100;`;
  }
}
async function changeDataSource() {
  const source = sources.value.find(
    (item) => item.id === activeDataSourceId.value,
  );
  if (source)
    await loadMetadata(source).catch(() => ElMessage.error("元数据加载失败"));
}
async function openHistory() {
  try {
    queryHistory.value = (await platformApi.queryHistory()).data.data || [];
    historyVisible.value = true;
  } catch {
    ElMessage.error("查询历史加载失败");
  }
}
async function showFields(table: string, database = activeDatabase.value) {
  selectedTable.value = table;
  activeDatabase.value = database;
  if (activeDataSourceId.value && database) {
    try {
      const result = await platformApi.metadataColumns(
        activeDataSourceId.value,
        database,
        table,
      );
      fieldMap.value[table] = (result.data.data || []).map((column) => [
        column.name,
        column.dataType,
        column.comment || "",
      ]);
    } catch {
      ElMessage.error("字段结构加载失败");
      return;
    }
  }
  ElMessage.success(`${table} 字段已加载`);
}
async function runExplore() {
  if (!activeDataSourceId.value) {
    runMessage.value = "未连接 StarRocks 数据源";
    ElMessage.warning("数据探查仅允许使用 StarRocks 数据源");
    return;
  }
  runMessage.value = "执行中...";
  const selection = monacoEditor?.getSelection();
  const model = monacoEditor?.getModel();
  const hasSelection = Boolean(selection && model && !selection.isEmpty());
  const start = hasSelection && selection && model ? model.getOffsetAt(selection.getStartPosition()) : 0;
  const end = hasSelection && selection && model ? model.getOffsetAt(selection.getEndPosition()) : 0;
  const sql = start !== end ? query.value.slice(start, end) : query.value;
  try {
    const submitted = await platformApi.querySubmit(
      sql,
      start !== end,
      activeDataSourceId.value,
      activeDatabase.value,
    );
    executionId.value = String(submitted.data.data?.executionId || "");
    runMessage.value = "执行中...";
    if (executionId.value) pollQuery(executionId.value);
  } catch {
    runMessage.value = "执行失败";
    ElMessage.error("SQL 执行失败");
  }
}
async function explainQuery() {
  if (!activeDataSourceId.value) {
    ElMessage.warning("数据探查仅允许使用 StarRocks 数据源");
    return;
  }
  const sql = query.value.trim();
  if (!sql) {
    ElMessage.warning("请输入 SQL 后再生成执行计划");
    return;
  }
  runMessage.value = "执行计划生成中...";
  try {
    const explainSql = /^explain\b/i.test(sql) ? sql : `EXPLAIN ${sql}`;
    const submitted = await platformApi.querySubmit(
      explainSql,
      false,
      activeDataSourceId.value,
      activeDatabase.value,
    );
    executionId.value = String(submitted.data.data?.executionId || "");
    if (executionId.value) pollQuery(executionId.value);
  } catch {
    runMessage.value = "执行计划生成失败";
    ElMessage.error("执行计划生成失败");
  }
}
function pollQuery(id: string) {
  if (queryTimer) window.clearInterval(queryTimer);
  queryTimer = window.setInterval(async () => {
    try {
      const result = await platformApi.queryStatus(id);
      const data = result.data.data || {};
      const status = String(data.status || "RUNNING");
      if (status === "RUNNING") return;
      resultColumns.value = Array.isArray(data.columns) ? data.columns.map(String) : [];
      resultRows.value = Array.isArray(data.rows) ? (data.rows as Record<string, unknown>[]) : [];
      runMessage.value = `执行${status === "SUCCESS" ? "完成" : status === "CANCELED" ? "已停止" : "失败"} · 返回 ${data.rowCount || 0} 行`;
      window.clearInterval(queryTimer);
      queryTimer = undefined;
      if (status === "SUCCESS") ElMessage.success("SQL 执行成功");
    } catch { window.clearInterval(queryTimer); queryTimer = undefined; runMessage.value = "查询状态获取失败"; }
  }, 500);
}
async function stopExplore() {
  if (!executionId.value) {
    ElMessage.warning("当前没有运行中的查询");
    return;
  }
  await platformApi.cancelQuery(executionId.value).catch(() => undefined);
  executionId.value = "";
  runMessage.value = "查询已停止";
  ElMessage.info("查询已停止");
}
function newQueryTab() {
  query.value = activeDatabase.value && selectedTable.value
    ? `SELECT *\nFROM ${activeDatabase.value}.${selectedTable.value}\nLIMIT 100;`
    : "";
  monacoEditor?.setValue(query.value);
  resultColumns.value = [];
  resultRows.value = [];
  runMessage.value = "新查询已创建";
}
function closeQueryTab() {
  query.value = "";
  resultColumns.value = [];
  resultRows.value = [];
  runMessage.value = "查询页签已关闭";
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
function highlight(value: string) {
  const escaped = value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
  return (
    escaped
      .replace(/(--[^\n]*)/g, '<span class="comment">$1</span>')
      .replace(/('[^'\n]*')/g, '<span class="str">$1</span>')
      .replace(
        /\b(SELECT|FROM|WHERE|GROUP|BY|ORDER|DESC|LIMIT|AS|AND|OR|JOIN|ON)\b/gi,
        '<span class="kw">$1</span>',
      )
      .replace(
        /\b(COUNT|SUM|AVG|DATE_FORMAT|DATE_SUB)\b/gi,
        '<span class="fn">$1</span>',
      ) + "\n"
  );
}
onBeforeUnmount(() => {
  if (queryTimer) window.clearInterval(queryTimer);
  editorChangeDisposable?.dispose();
  monacoEditor?.dispose();
  monacoEditor = null;
});
</script>

<template>
  <section class="page">
    <div class="module-bar">
      <div class="module-title">数据探查</div>
      <span class="crumb">/ SQL 查询</span>
      <div class="module-actions">
        <select
          v-model="activeDataSourceId"
          class="btn"
          @change="changeDataSource"
        >
          <option v-for="source in sources" :key="source.id" :value="source.id">
            {{ source.name }}
          </option>
          <option v-if="!sources.length">暂无数据源</option></select
        ><span style="font-size: 12px; color: #8b95a5">{{
          activeDataSourceId ? "连接正常 ●" : "未连接"
        }}</span>
      </div>
    </div>
    <div class="split">
      <aside class="left-panel" style="width: 280px">
        <div class="panel-head">
          <div class="panel-title">元数据</div>
          <div class="panel-actions">
            <button class="btn small icononly" @click="changeDataSource">
              ↻
            </button>
          </div>
        </div>
        <div class="metadata-search">
          <input v-model="metaSearch" class="search" placeholder="搜索表名" />
        </div>
        <div class="tree">
          <div v-for="item in shownMeta" :key="item.db">
            <div class="tree-row">
              <span class="tree-arrow">▾</span
              ><span class="tree-icon db">◉</span><b>{{ item.db }}</b
              ><span class="count">{{ item.tables.length }}</span>
            </div>
            <div
              v-for="table in item.tables"
              :key="table"
              class="tree-row indent-1"
              :class="{ selected: selectedTable === table }"
              @click="showFields(table, item.db)"
            >
              <span class="tree-arrow"></span
              ><span class="tree-icon tableico">▤</span><span>{{ table }}</span>
            </div>
          </div>
        </div>
      </aside>
      <div class="explore-layout">
        <section class="explore-main">
          <div class="tabbar">
            <div class="tab active">
              <span class="sql">SQL</span
              ><span class="tab-name">查询窗口 1</span
              ><button class="close tab-control" @click="closeQueryTab">×</button>
            </div>
            <button class="tab tab-control" style="min-width: 38px" @click="newQueryTab">＋</button>
          </div>
          <div class="editor-toolbar">
            <button class="btn small primary" @click="runExplore">▶ 运行</button
            ><button class="btn small" @click="stopExplore">■ 停止</button>
            <div class="toolbar-sep"></div>
            <button class="btn small" @click="formatCode">{ } 格式化</button
            ><button
              class="btn small"
              @click="explainQuery"
            >
              执行计划</button
            ><button class="btn small" @click="openHistory">历史记录</button
            ><span class="toolbar-meta"
              >提示：选中 SQL 后点击“运行”可仅执行选中部分</span
            >
          </div>
          <div class="editor-shell">
            <div ref="editorContainer" class="code-area monaco-code-area explore-monaco"></div>
            <div class="result-panel" style="height: 235px">
              <div class="result-head">
                <div class="result-tab">结果集 1</div>
                <div>消息</div>
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
                <div v-else class="empty-state">运行查询后显示真实结果</div>
              </div>
            </div>
          </div>
        </section>
        <aside class="field-side">
          <div class="field-title">表结构</div>
          <div class="field-sub">
            <template v-if="selectedTable"
              ><b>{{ activeDatabase }}.{{ selectedTable }}</b
              ><br />字段 {{ fields.length }} 个 · 已加载真实结构</template
            ><template v-else>点击左侧表名查看字段及中文注释</template>
          </div>
          <div class="field-list">
            <template v-if="selectedTable"
              ><div class="field-row head">
                <div>字段名</div>
                <div>类型</div>
                <div>中文注释</div>
              </div>
              <div v-for="field in fields" :key="field[0]" class="field-row">
                <div class="mono">{{ field[0] }}</div>
                <div>{{ field[1] }}</div>
                <div>{{ field[2] }}</div>
              </div></template
            >
            <div v-else class="empty-visual" style="height: 220px">
              <div style="text-align: center">
                <div style="font-size: 34px">▤</div>
                <div>尚未选择数据表</div>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </section>
  <el-dialog v-model="historyVisible" title="查询历史记录" width="min(980px, 92vw)">
    <div class="query-history">
      <table class="data-table" v-if="queryHistory.length">
        <thead><tr><th>执行时间</th><th>数据源</th><th>状态</th><th>耗时</th><th>SQL</th><th>错误</th></tr></thead>
        <tbody><tr v-for="item in queryHistory" :key="String(item.queryId)">
          <td>{{ item.startedAt || '-' }}</td><td>{{ item.databaseName || 'StarRocks' }}</td>
          <td>{{ item.status }}</td><td>{{ item.elapsedMs || 0 }} ms</td>
          <td class="history-sql">{{ item.sql }}</td><td class="history-error">{{ item.errorMessage || '-' }}</td>
        </tr></tbody>
      </table>
      <div v-else class="empty-state">暂无持久化查询记录</div>
    </div>
  </el-dialog>
</template>

<style scoped>
.tab-control { border: 0; cursor: pointer; }
.query-history { max-height: 60vh; overflow: auto; }
.history-sql { max-width: 360px; white-space: pre-wrap; word-break: break-word; font: 12px/1.5 Consolas, Monaco, monospace; }
.history-error { max-width: 220px; color: #c45656; white-space: pre-wrap; word-break: break-word; }
</style>
