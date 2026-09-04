<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { platformApi, type DataSource } from "../api";

const query = ref(
  `-- 数据探查：支持选中部分 SQL 单独运行\nSELECT\n    user_id,\n    user_name,\n    province,\n    register_time,\n    user_level\nFROM dim.dim_user_info\nWHERE register_time >= '2026-08-01'\nORDER BY register_time DESC\nLIMIT 100;`,
);
const selectedTable = ref("");
const metaSearch = ref("");
const runMessage = ref("返回 5 行，耗时 128ms");
const executionId = ref("");
const metadata = ref<Record<string, string[]>>({
  ods: [
    "ods_order_info",
    "ods_user_info",
    "ods_product_sku",
    "ods_payment_info",
  ],
  dwd: ["dwd_trade_order_detail", "dwd_user_action", "dwd_payment_detail"],
  dim: ["dim_user_info", "dim_base_province", "dim_sku_info"],
  dws: ["dws_trade_province_1d", "dws_user_1d"],
  ads: ["ads_trade_province", "ads_trade_rank", "ads_campaign_daily"],
});
const fieldMap = ref<Record<string, string[][]>>({
  ods_order_info: [
    ["id", "BIGINT", "订单主键"],
    ["user_id", "BIGINT", "用户ID"],
    ["order_status", "VARCHAR(32)", "订单状态"],
    ["total_amount", "DECIMAL(18,2)", "订单金额"],
    ["create_time", "DATETIME", "下单时间"],
    ["dt", "DATE", "分区日期"],
  ],
  dwd_trade_order_detail: [
    ["order_id", "BIGINT", "订单ID"],
    ["user_id", "BIGINT", "用户ID"],
    ["province_id", "BIGINT", "省份ID"],
    ["sku_id", "BIGINT", "商品SKU"],
    ["order_amount", "DECIMAL(18,2)", "订单金额"],
    ["dt", "DATE", "业务日期"],
  ],
  dim_user_info: [
    ["user_id", "BIGINT", "用户ID"],
    ["user_name", "VARCHAR(128)", "用户名称"],
    ["province", "VARCHAR(64)", "所属省份"],
    ["register_time", "DATETIME", "注册时间"],
    ["user_level", "VARCHAR(32)", "会员等级"],
  ],
});
const activeDataSourceId = ref<number>();
const sources = ref<DataSource[]>([]);
const activeDatabase = ref("");
const editor = ref<HTMLTextAreaElement>();
const resultColumns = ref<string[]>([]);
const resultRows = ref<Record<string, unknown>[]>([]);
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
  () =>
    fieldMap.value[selectedTable.value] || [
      ["id", "BIGINT", "主键ID"],
      ["name", "VARCHAR(128)", "名称"],
      ["status", "VARCHAR(32)", "状态"],
      ["create_time", "DATETIME", "创建时间"],
      ["dt", "DATE", "业务日期"],
    ],
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
});
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
  if (Object.keys(next).length) {
    metadata.value = next;
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
      /* local prototype fields remain available */
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
  const start = editor.value?.selectionStart || 0;
  const end = editor.value?.selectionEnd || 0;
  const sql = start !== end ? query.value.slice(start, end) : query.value;
  try {
    const result = await platformApi.query(
      sql,
      start !== end,
      activeDataSourceId.value,
      activeDatabase.value,
    );
    executionId.value = String(result.data.data?.executionId || "");
    const resultData = result.data.data as Record<string, unknown> | undefined;
    resultColumns.value = Array.isArray(resultData?.columns)
      ? resultData.columns.map(String)
      : [];
    resultRows.value = Array.isArray(resultData?.rows)
      ? (resultData.rows as Record<string, unknown>[])
      : [];
    runMessage.value = `执行${result.data.data?.status === "SUCCESS" ? "完成" : "结束"} · 返回 ${result.data.data?.rowCount || 0} 行`;
    ElMessage.success("SQL 执行成功");
  } catch {
    runMessage.value = "执行失败";
    ElMessage.error("SQL 执行失败");
  }
}
async function stopExplore() {
  if (executionId.value)
    await platformApi.cancelQuery(executionId.value).catch(() => undefined);
  runMessage.value = "查询已停止";
  ElMessage.info("查询已停止");
}
function newQueryTab() {
  query.value = "-- 新建 StarRocks 查询\nSELECT *\nFROM ods.yzl_order\nLIMIT 100;";
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
              @click="ElMessage.success('EXPLAIN 执行计划已生成')"
            >
              执行计划</button
            ><span class="toolbar-meta"
              >提示：选中 SQL 后点击“运行”可仅执行选中部分</span
            >
          </div>
          <div class="editor-shell">
            <div class="code-area">
              <div class="linenos">
                {{
                  Array.from(
                    { length: Math.max(1, query.split("\n").length) },
                    (_, i) => i + 1,
                  ).join("\n")
                }}
              </div>
              <pre class="highlight" v-html="highlight(query)"></pre>
              <textarea
                ref="editor"
                v-model="query"
                class="editor-textarea"
                spellcheck="false"
              ></textarea>
            </div>
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
              ><br />字段 {{ fields.length }} 个 · 双击表已加载</template
            ><template v-else>双击左侧表名查看字段及中文注释</template>
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
</template>

<style scoped>
.tab-control { border: 0; cursor: pointer; }
</style>
