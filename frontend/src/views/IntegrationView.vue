<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { Connection, DocumentChecked, Grid, Setting } from "@element-plus/icons-vue";
import { platformApi, type DataSource, type SeaTunnelCluster } from "../api";

type Task = {
  id: string | number;
  name: string;
  project: string;
  source: string;
  target: string;
  mode: string;
  status: string;
  updated: string;
  executionId?: string;
  tables?: IntegrationTable[];
};
type IntegrationTable = {
  id?: number;
  taskId?: number;
  sourceDatabase: string;
  sourceTable: string;
  targetDatabase: string;
  targetTable: string;
  partitionColumn?: string;
};
const search = ref("");
const show = ref(false);
const wizardStep = ref<1 | 2 | 3>(1);
const taskDescription = ref("");
const targetStrategy = ref("AUTO_CREATE");
const executionEngine = ref("SEATUNNEL_ZETA");
const deploymentMode = ref("CLUSTER");
const sourceDataSourceId = ref<number>();
const targetDataSourceId = ref<number>();
const sourceDataSources = ref<DataSource[]>([]);
const targetDataSources = ref<DataSource[]>([]);
const clusters = ref<SeaTunnelCluster[]>([]);
const selectedClusterId = ref<number>();
const sourceTables = ref<{ name: string; comment?: string }[]>([]);
const tableKeyword = ref("");
const statusFilter = ref("");
const logVisible = ref(false);
const logTitle = ref("同步任务日志");
const logContent = ref("");
const detailVisible = ref(false);
const selectedTask = ref<Task | null>(null);
const editingId = ref<number>();
const importInput = ref<HTMLInputElement>();
const polling = new Map<string, number>();
const form = ref({
  name: "ods_to_dw_customer",
  sourceType: "MYSQL",
  targetType: "STARROCKS",
  syncMode: "FULL",
  sourceHost: "81.69.15.136",
  sourcePort: 3306,
  sourceDatabase: "yzl_prd",
  sourceUsername: "root",
  sourcePassword: "",
  sourceTable: "yzl_order",
  targetHost: "81.69.15.136",
  targetPort: 9030,
  targetDatabase: "ods",
  targetUsername: "dev_0904",
  targetPassword: "",
  targetTable: "yzl_order",
  where: "",
  parallelism: 1,
  batchSize: 1000,
  mappings: "order_id:order_id",
  tables: [
    {
      sourceDatabase: "yzl_prd",
      sourceTable: "yzl_order",
      targetDatabase: "ods",
      targetTable: "yzl_order",
      partitionColumn: "",
    },
  ] as IntegrationTable[],
});
const tasks = ref<Task[]>([]);
const loading = ref(false);
const filteredTasks = computed(() =>
  tasks.value.filter(
    (item) =>
      (!search.value || item.name.includes(search.value)) &&
      (!statusFilter.value || item.status === statusFilter.value),
  ),
);
const runningCount = computed(
  () => tasks.value.filter((item) => item.status === "运行中").length,
);
const successCount = computed(
  () => tasks.value.filter((item) => item.status === "成功").length,
);
const failedCount = computed(
  () => tasks.value.filter((item) => item.status === "失败").length,
);
const filteredSourceTables = computed(() => sourceTables.value.filter(item => !tableKeyword.value.trim() || item.name.toLowerCase().includes(tableKeyword.value.trim().toLowerCase()) || String(item.comment || "").toLowerCase().includes(tableKeyword.value.trim().toLowerCase())));
const selectedTableNames = computed(() => new Set(form.value.tables.map(item => item.sourceTable)));
const allTablesSelected = computed(() => filteredSourceTables.value.length > 0 && filteredSourceTables.value.every(item => selectedTableNames.value.has(item.name)));
const selectedCluster = computed(() => clusters.value.find(item => item.id === selectedClusterId.value));
function displayStatus(value: unknown) {
  const status = String(value || "DRAFT").toUpperCase();
  if (["RUNNING", "RUNNING_EXECUTION"].includes(status)) return "运行中";
  if (["FINISHED", "SUCCESS"].includes(status)) return "成功";
  if (["FAILED", "FAILURE"].includes(status)) return "失败";
  if (["CANCELED", "STOPPED"].includes(status)) return "已停止";
  return "待运行";
}
function tableSummary(item: Record<string, unknown>, side: "source" | "target") {
  const tables = Array.isArray(item.tables) ? (item.tables as Record<string, unknown>[]) : [];
  const databaseKey = side === "source" ? "sourceDatabase" : "targetDatabase";
  const tableKey = side === "source" ? "sourceTable" : "targetTable";
  if (tables.length === 1) return `${tables[0]?.[databaseKey] || ""}.${tables[0]?.[tableKey] || side}`;
  return tables.length > 1 ? `${tables.length} 张表` : side;
}

async function loadTasks() {
  loading.value = true;
  try {
    const data = (await platformApi.integrations()).data.data || [];
    tasks.value = data.map(
      (item: Record<string, unknown>, index: number) => ({
        id: (item.id as string | number) || index,
        name: String(item.name || "未命名任务"),
        project: String(item.projectName || "平台项目"),
        source: `${item.sourceType || "MYSQL"} · ${tableSummary(item, "source")}`,
        target: `${item.targetType || "STARROCKS"} · ${tableSummary(item, "target")}`,
        mode: String(item.syncMode || "全量"),
        status: displayStatus(item.status),
        updated: String(item.updatedAt || "尚未运行"),
        tables: Array.isArray(item.tables)
          ? (item.tables as IntegrationTable[])
          : [],
      }),
    );
  } catch (error) {
    tasks.value = [];
    ElMessage.error(error instanceof Error ? error.message : "同步任务加载失败");
  } finally {
    loading.value = false;
  }
}
onMounted(loadTasks);

async function loadWizardReferences() {
  try {
    const [sourcesResult, clustersResult] = await Promise.all([platformApi.dataSources(), platformApi.clusters()]);
    const dataSources = sourcesResult.data.data || [];
    sourceDataSources.value = dataSources.filter(item => item.type === "MYSQL");
    targetDataSources.value = dataSources.filter(item => item.type === "STARROCKS");
    clusters.value = clustersResult.data.data || [];
    if (!sourceDataSourceId.value && sourceDataSources.value.length) await selectSourceDataSource(sourceDataSources.value[0].id);
    if (!targetDataSourceId.value && targetDataSources.value.length) selectTargetDataSource(targetDataSources.value[0].id);
    if (!selectedClusterId.value && clusters.value.length) selectedClusterId.value = clusters.value[0].id;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "加载数据源或集群配置失败");
  }
}
async function selectSourceDataSource(id?: number) {
  sourceDataSourceId.value = id;
  const source = sourceDataSources.value.find(item => item.id === id);
  if (!source) return;
  Object.assign(form.value, { sourceType: source.type, sourceHost: source.host, sourcePort: source.port, sourceDatabase: source.databaseName, sourceUsername: source.username });
  Object.assign(form.value, {
    name: "",
    sourceType: "MYSQL",
    targetType: "STARROCKS",
    syncMode: "FULL",
    sourceHost: "",
    sourcePort: 3306,
    sourceDatabase: "",
    sourceUsername: "",
    sourcePassword: "",
    sourceTable: "",
    targetHost: "",
    targetPort: 9030,
    targetDatabase: "",
    targetUsername: "",
    targetPassword: "",
    targetTable: "",
    where: "",
    parallelism: 1,
    batchSize: 1000,
    mappings: "",
    tables: [],
  });
  try {
    sourceTables.value = (await platformApi.integrationSourceTables(source.id)).data.data || [];
  } catch (error: any) {
    sourceTables.value = [];
    ElMessage.error(error?.response?.data?.message || "读取可同步表失败");
  }
}
function selectSourceDataSourceByEvent(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value);
  void selectSourceDataSource(Number.isFinite(value) && value > 0 ? value : undefined);
}
function selectTargetDataSource(id?: number) {
  targetDataSourceId.value = id;
  const target = targetDataSources.value.find(item => item.id === id);
  if (!target) return;
  Object.assign(form.value, { targetType: target.type, targetHost: target.host, targetPort: target.port, targetDatabase: target.databaseName, targetUsername: target.username });
}
function selectTargetDataSourceByEvent(event: Event) {
  const value = Number((event.target as HTMLSelectElement).value);
  selectTargetDataSource(Number.isFinite(value) && value > 0 ? value : undefined);
}
function toggleSourceTable(table: { name: string }) {
  const index = form.value.tables.findIndex(item => item.sourceTable === table.name);
  if (index >= 0) form.value.tables.splice(index, 1);
  else form.value.tables.push({ sourceDatabase: form.value.sourceDatabase, sourceTable: table.name, targetDatabase: form.value.targetDatabase, targetTable: table.name, partitionColumn: "" });
}
function toggleAllSourceTables() {
  if (allTablesSelected.value) form.value.tables = form.value.tables.filter(item => !filteredSourceTables.value.some(source => source.name === item.sourceTable));
  else filteredSourceTables.value.forEach(item => { if (!selectedTableNames.value.has(item.name)) form.value.tables.push({ sourceDatabase: form.value.sourceDatabase, sourceTable: item.name, targetDatabase: form.value.targetDatabase, targetTable: item.name, partitionColumn: "" }); });
}
async function nextFromBasic() {
  if (!form.value.name.trim()) { ElMessage.warning("请填写同步任务名称"); return; }
  if (!sourceDataSourceId.value || !targetDataSourceId.value) { ElMessage.warning("请选择 MySQL 源和 StarRocks 目标数据源"); return; }
  if (deploymentMode.value === "CLUSTER" && !selectedClusterId.value) { ElMessage.warning("请选择目标集群节点"); return; }
  await selectSourceDataSource(sourceDataSourceId.value);
  wizardStep.value = 2;
}
function nextFromTables() {
  if (!form.value.tables.length) { ElMessage.warning("请至少选择一张同步表"); return; }
  wizardStep.value = 3;
}
function goWizardStep(step: number) {
  if (step <= wizardStep.value && step >= 1 && step <= 3) wizardStep.value = step as 1 | 2 | 3;
}
function previewSeaTunnelConfig() {
  const clusterLine = selectedCluster.value ? `  cluster.name = \"${selectedCluster.value.name}\"\n  cluster.endpoint = \"${selectedCluster.value.host}:${selectedCluster.value.port}\"\n` : "";
  const tableLines = form.value.tables.map(item => `  source_table = \"${item.sourceDatabase}.${item.sourceTable}\"\n  target_table = \"${item.targetDatabase}.${item.targetTable}\"`).join("\n\n");
  return `env {\n  parallelism = ${form.value.parallelism}\n  job.mode = \"${form.value.syncMode === "FULL" ? "BATCH" : "CDC"}\"\n${clusterLine}}\n\n# ${executionEngine.value} · ${deploymentMode.value}\n${tableLines}`;
}
async function copyPreviewConfig() {
  if (!navigator.clipboard) { ElMessage.warning("当前浏览器不支持复制，请手动复制配置内容"); return; }
  await navigator.clipboard.writeText(previewSeaTunnelConfig());
  ElMessage.success("配置已复制");
}

async function create() {
  try {
    const tableRows = form.value.tables
      .map((table) => ({
        sourceDatabase: table.sourceDatabase || form.value.sourceDatabase,
        sourceTable: table.sourceTable.trim(),
        targetDatabase: table.targetDatabase || form.value.targetDatabase,
        targetTable: table.targetTable.trim(),
        partitionColumn: table.partitionColumn || "",
      }))
      .filter((table) => table.sourceTable && table.targetTable);
    if (!tableRows.length || tableRows.length !== form.value.tables.length) {
      ElMessage.warning("请完整填写每张源表和目标表");
      return;
    }
    const mappings = form.value.mappings
      .split(/[,\n]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => {
        const [source, target] = item.split(":").map((value) => value.trim());
        return { source, target: target || source };
      });
    const request = {
      name: form.value.name,
      sourceType: form.value.sourceType,
      targetType: form.value.targetType,
      syncMode: form.value.syncMode,
      source: {
        host: form.value.sourceHost,
        port: form.value.sourcePort,
        database: form.value.sourceDatabase,
        username: form.value.sourceUsername,
        password: form.value.sourcePassword,
        table: form.value.tables[0]?.sourceTable || form.value.sourceTable,
      },
      target: {
        host: form.value.targetHost,
        port: form.value.targetPort,
        database: form.value.targetDatabase,
        username: form.value.targetUsername,
        password: form.value.targetPassword,
        table: form.value.tables[0]?.targetTable || form.value.targetTable,
      },
      mappings,
      options: {
        where: form.value.where,
        parallelism: form.value.parallelism,
        batchSize: form.value.batchSize,
        description: taskDescription.value,
        targetStrategy: targetStrategy.value,
        executionEngine: executionEngine.value,
        deploymentMode: deploymentMode.value,
        clusterId: selectedClusterId.value || null,
      },
      tables: tableRows,
    };
    const result = editingId.value
      ? await platformApi.updateIntegration(editingId.value, request)
      : await platformApi.createIntegration(request);
    const item = result.data.data || {};
    const next: Task = {
      id: Number(item.id || editingId.value || Date.now()),
      name: form.value.name,
      project: "数仓开发项目",
      source: `${form.value.sourceType} · ${tableRows.length > 1 ? `${tableRows.length} 张表` : `${tableRows[0].sourceDatabase}.${tableRows[0].sourceTable}`}`,
      target: `${form.value.targetType} · ${tableRows.length > 1 ? `${tableRows.length} 张表` : `${tableRows[0].targetDatabase}.${tableRows[0].targetTable}`}`,
      mode: form.value.syncMode === "FULL" ? "全量" : "增量",
      status: "待运行",
      updated: "刚刚",
      tables: (item.tables as IntegrationTable[]) || form.value.tables,
    };
    const existing = tasks.value.findIndex(
      (task) => Number(task.id) === Number(next.id),
    );
    if (existing >= 0) tasks.value.splice(existing, 1, next);
    else tasks.value.unshift(next);
    show.value = false;
    const message = editingId.value
      ? "同步任务已更新"
      : "同步任务已创建，配置已生成";
    editingId.value = undefined;
    ElMessage.success(message);
  } catch (error) {
    ElMessage.error(
      error instanceof Error ? error.message : "创建同步任务失败",
    );
  }
}

async function editTask(task: Task) {
  try {
    const result = await platformApi.getIntegration(Number(task.id));
    const data = result.data.data || {};
    const source =
      typeof data.sourceConfigJson === "string"
        ? JSON.parse(data.sourceConfigJson)
        : {};
    const target =
      typeof data.targetConfigJson === "string"
        ? JSON.parse(data.targetConfigJson)
        : {};
    const transform =
      typeof data.transformConfigJson === "string"
        ? JSON.parse(data.transformConfigJson)
        : {};
    Object.assign(form.value, {
      name: data.name || task.name,
      sourceType: data.sourceType || "MYSQL",
      targetType: data.targetType || "STARROCKS",
      syncMode: data.syncMode || "FULL",
      sourceHost: source.host || form.value.sourceHost,
      sourcePort: Number(source.port || form.value.sourcePort),
      sourceDatabase: source.database || form.value.sourceDatabase,
      sourceUsername: source.username || form.value.sourceUsername,
      sourcePassword: "",
      sourceTable: source.table || form.value.sourceTable,
      targetHost: target.host || form.value.targetHost,
      targetPort: Number(target.port || form.value.targetPort),
      targetDatabase: target.database || form.value.targetDatabase,
      targetUsername: target.username || form.value.targetUsername,
      targetPassword: "",
      targetTable: target.table || form.value.targetTable,
      tables:
        Array.isArray(data.tables) && data.tables.length
          ? (data.tables as IntegrationTable[])
          : form.value.tables,
      where: transform.options?.where || "",
      parallelism: Number(transform.options?.parallelism || 1),
      batchSize: Number(transform.options?.batchSize || 1000),
      mappings: Array.isArray(transform.mappings)
        ? transform.mappings
            .map((item: any) => `${item.source}:${item.target || item.source}`)
            .join(",")
        : form.value.mappings,
    });
    editingId.value = Number(task.id);
    detailVisible.value = false;
    wizardStep.value = 1;
    await loadWizardReferences();
    sourceDataSourceId.value = sourceDataSources.value.find(item => item.host === form.value.sourceHost && item.port === form.value.sourcePort && item.databaseName === form.value.sourceDatabase)?.id;
    targetDataSourceId.value = targetDataSources.value.find(item => item.host === form.value.targetHost && item.port === form.value.targetPort && item.databaseName === form.value.targetDatabase)?.id;
    show.value = true;
  } catch {
    ElMessage.error("同步任务配置加载失败");
  }
}

function addTable() {
  form.value.tables.push({
    sourceDatabase: form.value.sourceDatabase,
    sourceTable: "",
    targetDatabase: form.value.targetDatabase,
    targetTable: "",
    partitionColumn: "",
  });
}
function removeTable(index: number) {
  if (form.value.tables.length <= 1) {
    ElMessage.warning("至少保留一张同步表");
    return;
  }
  form.value.tables.splice(index, 1);
}

async function runTask(task: Task) {
  const id = Number(task.id);
  if (Number.isFinite(id)) {
    try {
      const result = await platformApi.runIntegration(id);
      task.status = "运行中";
      task.updated = "刚刚";
      task.executionId = String(result.data.data?.executionId || "");
      if (task.executionId) pollStatus(task);
      ElMessage.success("同步任务已提交运行");
      return;
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : "同步任务运行失败");
      return;
    }
  }
  ElMessage.error("该任务没有有效的后端记录，无法运行");
}

async function openLog(task: Task) {
  const id = Number(task.id);
  if (Number.isFinite(id)) {
    try {
      const instances =
        (await platformApi.integrationInstances(id)).data.data || [];
      const executionId = String(instances[0]?.executionId || "");
      if (executionId) {
        logTitle.value = `${task.name} · 运行日志`;
        logContent.value =
          (await platformApi.integrationLog(executionId)).data.data ||
          "暂无运行日志";
        logVisible.value = true;
        return;
      }
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : "运行日志加载失败");
      return;
    }
  }
  ElMessage.warning("该任务没有有效的后端记录");
}
async function pollStatus(task: Task) {
  if (!task.executionId || polling.has(task.executionId)) return;
  const timer = window.setInterval(async () => {
    try {
      const result = await platformApi.integrationStatus(task.executionId!);
      const status = result.data.data?.status || "";
      task.status =
        status === "FINISHED"
          ? "成功"
          : status === "FAILED"
            ? "失败"
            : status === "RUNNING"
              ? "运行中"
              : status;
      task.updated = new Date().toLocaleTimeString();
      if (
        status === "FINISHED" ||
        status === "FAILED" ||
        status === "NOT_FOUND"
      ) {
        window.clearInterval(timer);
        polling.delete(task.executionId!);
      }
    } catch {
      /* keep the latest known state */
    }
  }, 2000);
  polling.set(task.executionId, timer);
}
onBeforeUnmount(() => polling.forEach((timer) => window.clearInterval(timer)));

async function deleteTask(task: Task) {
  const id = Number(task.id);
  if (!Number.isFinite(id)) {
    ElMessage.error("该任务没有有效的后端记录，无法删除");
    return;
  }
  if (!window.confirm(`确认删除同步任务“${task.name}”吗？`)) return;
  try {
    await platformApi.deleteIntegration(id);
    tasks.value = tasks.value.filter((item) => Number(item.id) !== id);
    if (selectedTask.value?.id === task.id) detailVisible.value = false;
    ElMessage.success("同步任务已删除");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "同步任务删除失败");
  }
}
function openCreate() {
  editingId.value = undefined;
  wizardStep.value = 1;
  taskDescription.value = "";
  targetStrategy.value = "AUTO_CREATE";
  executionEngine.value = "SEATUNNEL_ZETA";
  deploymentMode.value = "CLUSTER";
  sourceDataSourceId.value = undefined;
  targetDataSourceId.value = undefined;
  selectedClusterId.value = undefined;
  sourceTables.value = [];
  tableKeyword.value = "";
  form.value.tables = [];
  void loadWizardReferences();
  show.value = true;
}
function openTask(task: Task) {
  selectedTask.value = task;
  detailVisible.value = true;
  platformApi
    .getIntegration(Number(task.id))
    .then((result) => {
      const data = result.data.data || {};
      if (selectedTask.value?.id === task.id) {
        selectedTask.value.tables = Array.isArray(data.tables)
          ? (data.tables as IntegrationTable[])
          : task.tables;
      }
    })
    .catch(() => undefined);
}
async function deleteTaskTable(table: IntegrationTable) {
  if (!selectedTask.value || !table.id) return;
  try {
    const result = await platformApi.deleteIntegrationTable(
      Number(selectedTask.value.id),
      table.id,
    );
    const updated = result.data.data || {};
    const remaining = Array.isArray(updated.tables)
      ? (updated.tables as IntegrationTable[])
      : [];
    selectedTask.value.tables = remaining;
    const row = tasks.value.find((item) => item.id === selectedTask.value?.id);
    if (row) {
      row.tables = remaining;
      const sourceType = String(updated.sourceType || row.source.split(" · ")[0]);
      const targetType = String(updated.targetType || row.target.split(" · ")[0]);
      row.source = `${sourceType} · ${remaining.length > 1 ? `${remaining.length} 张表` : `${remaining[0]?.sourceDatabase || ""}.${remaining[0]?.sourceTable || "source"}`}`;
      row.target = `${targetType} · ${remaining.length > 1 ? `${remaining.length} 张表` : `${remaining[0]?.targetDatabase || ""}.${remaining[0]?.targetTable || "target"}`}`;
      row.status = "待运行";
    }
    ElMessage.success("已删除该表，任务配置已重新生成");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "删除任务表失败");
  }
}
function chooseImport() {
  importInput.value?.click();
}
async function importTask(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    const data = JSON.parse(await file.text()) as Record<string, any>;
    form.value.name = String(data.name || file.name.replace(/\.json$/i, ""));
    form.value.sourceType = String(data.sourceType || "MYSQL");
    form.value.targetType = String(data.targetType || "STARROCKS");
    form.value.syncMode = String(data.syncMode || "FULL");
    Object.assign(form.value, {
      sourceHost: data.source?.host || form.value.sourceHost,
      sourcePort: Number(data.source?.port || form.value.sourcePort),
      sourceDatabase: data.source?.database || form.value.sourceDatabase,
      sourceTable: data.source?.table || form.value.sourceTable,
      sourceUsername: data.source?.username || form.value.sourceUsername,
      targetHost: data.target?.host || form.value.targetHost,
      targetPort: Number(data.target?.port || form.value.targetPort),
      targetDatabase: data.target?.database || form.value.targetDatabase,
      targetTable: data.target?.table || form.value.targetTable,
      targetUsername: data.target?.username || form.value.targetUsername,
      tables:
        Array.isArray(data.tables) && data.tables.length
          ? (data.tables as IntegrationTable[])
          : [
              {
                sourceDatabase:
                  data.source?.database || form.value.sourceDatabase,
                sourceTable: data.source?.table || form.value.sourceTable,
                targetDatabase:
                  data.target?.database || form.value.targetDatabase,
                targetTable: data.target?.table || form.value.targetTable,
                partitionColumn: "",
              },
            ],
    });
    form.value.mappings = Array.isArray(data.mappings)
      ? data.mappings
          .map((item: any) => `${item.source}:${item.target || item.source}`)
          .join(",")
      : form.value.mappings;
    editingId.value = undefined;
    wizardStep.value = 1;
    void loadWizardReferences();
    show.value = true;
    ElMessage.success("任务配置已导入，请确认后保存");
  } catch {
    ElMessage.error("导入失败，请选择正确的 JSON 任务配置");
  }
  (event.target as HTMLInputElement).value = "";
}
</script>

<template>
  <section class="page">
    <div v-if="!show" class="module-bar">
      <div class="module-title">
        数据集成 <span class="crumb">/ 离线同步</span>
      </div>
      <div class="module-actions">
        <input
          ref="importInput"
          type="file"
          accept="application/json,.json"
          hidden
          @change="importTask"
        /><button class="btn-default" @click="chooseImport">导入任务</button
        ><button class="btn-primary" @click="openCreate">
          ＋ 新建同步任务
        </button>
      </div>
    </div>
    <div v-if="!show" class="page-body">
      <div class="metrics">
        <div class="metric">
          <div class="metric-icon blue">⇄</div>
          <div>
            <div class="metric-label">同步任务</div>
            <div class="metric-value">{{ tasks.length }}</div>
          </div>
        </div>
        <div class="metric">
          <div class="metric-icon green">✓</div>
          <div>
            <div class="metric-label">运行中</div>
            <div class="metric-value">{{ runningCount }}</div>
          </div>
        </div>
        <div class="metric">
          <div class="metric-icon purple">▣</div>
          <div>
            <div class="metric-label">今日成功</div>
            <div class="metric-value">{{ successCount }}</div>
          </div>
        </div>
        <div class="metric">
          <div class="metric-icon orange">!</div>
          <div>
            <div class="metric-label">失败</div>
            <div class="metric-value">{{ failedCount }}</div>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-head">
          <span>离线同步任务</span
          ><span class="muted">支持 MySQL、StarRocks、Hive、Kafka</span>
        </div>
        <div class="filterbar">
          <input v-model="search" placeholder="搜索任务名称..." /><select>
            <option>全部项目</option>
            <option>数仓开发项目</option></select
          ><select v-model="statusFilter">
            <option value="">全部状态</option>
            <option value="运行中">运行中</option>
            <option value="成功">成功</option>
            <option value="失败">失败</option>
            <option value="待运行">待运行</option></select
          ><button class="btn-primary" :disabled="loading" @click="loadTasks">
            查询</button
          ><button
            class="btn-default"
            @click="
              search = '';
              statusFilter = '';
            "
          >
            重置
          </button>
        </div>
        <table class="data-table">
          <thead>
            <tr>
              <th>任务名称</th>
              <th>项目</th>
              <th>来源 → 目标</th>
              <th>同步模式</th>
              <th>状态</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="task in filteredTasks" :key="task.id">
              <td>
                <button class="link-action" @click="openTask(task)">
                  {{ task.name }}
                </button>
                <div class="muted mono">TASK-{{ task.id }}</div>
              </td>
              <td>{{ task.project }}</td>
              <td>
                <span class="tag blue">{{ task.source }}</span
                ><span class="arrow">→</span
                ><span class="tag">{{ task.target }}</span>
              </td>
              <td>{{ task.mode }}</td>
              <td>
                <span
                  class="status-dot"
                  :class="
                    task.status === '失败'
                      ? 'bad'
                      : task.status === '运行中'
                        ? 'run'
                        : 'ok'
                  "
                ></span
                >{{ task.status }}
              </td>
              <td class="muted">{{ task.updated }}</td>
              <td class="actions">
                <button @click="editTask(task)">编辑</button
                ><button @click="runTask(task)">运行</button
                ><button @click="openLog(task)">日志</button
                ><button class="danger-text" @click="deleteTask(task)">删除</button>
              </td>
            </tr>
            <tr v-if="!filteredTasks.length">
              <td colspan="7" class="empty-state">暂无符合条件的同步任务</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <section v-if="show" class="integration-wizard page-body">
      <div class="wizard-titlebar"><div><h1>{{ editingId ? "编辑同步任务" : "创建同步任务" }}</h1><p>配置 MySQL 到 StarRocks 的数据同步任务</p></div><button class="btn-default" @click="show = false">返回任务列表</button></div>
      <div class="wizard-steps"><button v-for="step in [1, 2, 3]" :key="step" type="button" class="wizard-step" :class="{ active: wizardStep === step, done: wizardStep > step }" :disabled="step > wizardStep" @click="goWizardStep(step)"><span>{{ wizardStep > step ? '✓' : step }}</span>{{ step === 1 ? '基本配置' : step === 2 ? '选择表' : '预览确认' }}</button></div>
      <div v-if="wizardStep === 1" class="wizard-card">
        <div class="wizard-card-title"><span class="wizard-icon"><el-icon><Setting /></el-icon></span><h2>基本配置</h2></div>
        <div class="wizard-two-columns"><div class="wizard-field"><label>任务名称</label><input v-model="form.name" placeholder="输入同步任务名称"></div><div class="wizard-field"><label>描述</label><input v-model="taskDescription" placeholder="任务描述"></div></div>
        <div class="wizard-field inline"><label>同步类型</label><label class="radio-label"><input v-model="form.syncMode" type="radio" value="FULL">批量同步 (BATCH)</label><label class="radio-label"><input v-model="form.syncMode" type="radio" value="INCREMENTAL">实时同步 (CDC)</label></div>
        <div class="wizard-field inline-wide"><label>增量条件</label><input v-model="form.where" placeholder="为空则全量同步，例如：create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)"></div>
        <div class="wizard-field inline-wide"><label>目标策略</label><select v-model="targetStrategy"><option value="AUTO_CREATE">自动建表/补字段，保留已有数据</option><option value="TRUNCATE">清空目标表后全量写入</option><option value="APPEND">仅追加写入已有表</option></select></div>
        <div class="wizard-field inline-wide"><label>目标集群</label><select v-model="selectedClusterId"><option :value="undefined">选择集群节点</option><option v-for="cluster in clusters" :key="cluster.id" :value="cluster.id">{{ cluster.name }} · {{ cluster.host }}:{{ cluster.port }}</option></select></div>
        <div class="wizard-field inline"><label>执行引擎</label><label class="radio-label"><input v-model="executionEngine" type="radio" value="SEATUNNEL_ZETA">SeaTunnel Zeta</label><label class="radio-label"><input v-model="executionEngine" type="radio" value="FLINK">Flink</label><label class="radio-label"><input v-model="executionEngine" type="radio" value="SPARK">Spark</label></div>
        <div class="wizard-field inline"><label>部署模式</label><label class="radio-label"><input v-model="deploymentMode" type="radio" value="CLIENT">Client 本地</label><label class="radio-label"><input v-model="deploymentMode" type="radio" value="CLUSTER">Cluster 集群</label></div>
        <div class="wizard-endpoints"><div class="endpoint-card source"><h3>源数据库</h3><div class="wizard-field"><label>MySQL 源</label><select :value="sourceDataSourceId" @change="selectSourceDataSourceByEvent"><option value="">选择 MySQL 源</option><option v-for="source in sourceDataSources" :key="source.id" :value="source.id">{{ source.name }} · {{ source.databaseName }}</option></select></div><div class="endpoint-meta">{{ form.sourceHost || '—' }} · {{ form.sourceDatabase || '—' }}</div></div><div class="endpoint-arrow">→</div><div class="endpoint-card target"><h3>目标数据库</h3><div class="wizard-field"><label>StarRocks 目标</label><select :value="targetDataSourceId" @change="selectTargetDataSourceByEvent"><option value="">选择 StarRocks 目标</option><option v-for="target in targetDataSources" :key="target.id" :value="target.id">{{ target.name }} · {{ target.databaseName }}</option></select></div><div class="endpoint-meta">{{ form.targetHost || '—' }} · {{ form.targetDatabase || '—' }}</div></div></div>
        <div class="wizard-footer"><button class="btn-default" @click="show = false">取消</button><button class="btn-primary" @click="nextFromBasic">下一步：选择表 →</button></div>
      </div>
      <div v-else-if="wizardStep === 2" class="wizard-card table-selector-card"><div class="wizard-card-title"><span class="wizard-icon"><el-icon><Grid /></el-icon></span><h2>选择同步表</h2><span class="selection-count">{{ form.tables.length }} / {{ sourceTables.length }}</span></div><div class="table-selector-tools"><label class="check-label"><input :checked="allTablesSelected" type="checkbox" @change="toggleAllSourceTables">全选</label><input v-model="tableKeyword" class="table-search" placeholder="搜索表名"></div><div class="source-table-list"><label v-for="table in filteredSourceTables" :key="table.name" class="source-table-row"><input :checked="selectedTableNames.has(table.name)" type="checkbox" @change="toggleSourceTable(table)"><el-icon><Grid /></el-icon><code>{{ table.name }}</code><span>{{ table.comment || '—' }}</span></label><div v-if="!sourceTables.length" class="empty-state">当前 MySQL 数据源未返回可同步表，请检查数据源连接。</div></div><div class="wizard-footer"><button class="btn-default" @click="wizardStep = 1">← 上一步</button><button class="btn-primary" @click="nextFromTables">下一步：预览确认 →</button></div></div>
      <div v-else class="wizard-preview"><div class="wizard-card"><div class="wizard-card-title"><span class="wizard-icon"><el-icon><DocumentChecked /></el-icon></span><h2>任务概要</h2></div><div class="preview-grid"><div><span>任务名称</span><b>{{ form.name }}</b></div><div><span>同步类型</span><b>{{ form.syncMode === 'FULL' ? '批量同步(BATCH)' : '实时同步(CDC)' }}</b></div><div><span>源端</span><b>{{ form.sourceDatabase }} <small>({{ form.sourceType.toLowerCase() }})</small></b></div><div><span>目标</span><b>{{ form.targetDatabase }} <small>({{ form.targetType.toLowerCase() }})</small></b></div><div><span>目标策略</span><b>{{ targetStrategy === 'AUTO_CREATE' ? '自动建表/补字段，保留已有数据' : targetStrategy === 'TRUNCATE' ? '清空目标表后全量写入' : '仅追加写入已有表' }}</b></div><div><span>目标集群</span><b>{{ selectedCluster ? selectedCluster.name : '本地执行' }}</b></div></div><strong class="table-total">同步表数：{{ form.tables.length }} 张</strong></div><div class="wizard-card config-preview"><div class="wizard-card-title"><span class="wizard-icon"><el-icon><Connection /></el-icon></span><h2>SeaTunnel 配置</h2><button class="btn-default" @click="copyPreviewConfig">复制</button></div><pre>{{ previewSeaTunnelConfig() }}</pre></div><div class="wizard-footer"><button class="btn-default" @click="wizardStep = 2">← 上一步</button><button class="btn-primary" @click="create">{{ editingId ? '保存修改' : '创建同步任务' }} →</button></div></div>
    </section>
    <div v-if="false" class="modal-mask" @click.self="show = false">
      <div class="modal integration-modal">
        <div class="modal-head">
          <span>{{ editingId ? "编辑离线同步任务" : "新建离线同步任务" }}</span
          ><button class="modal-close" @click="show = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>任务名称</label><input v-model="form.name" />
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>来源类型</label
              ><select v-model="form.sourceType">
                <option>MYSQL</option>
                <option>STARROCKS</option>
              </select>
            </div>
            <div class="form-item">
              <label>目标类型</label
              ><select v-model="form.targetType">
                <option>STARROCKS</option>
                <option>MYSQL</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>来源主机</label><input v-model="form.sourceHost" />
            </div>
            <div class="form-item">
              <label>端口</label
              ><input v-model.number="form.sourcePort" type="number" />
            </div>
            <div class="form-item">
              <label>数据库</label><input v-model="form.sourceDatabase" />
            </div>
            <div class="form-item">
              <label>表</label><input v-model="form.sourceTable" />
            </div>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>目标主机</label><input v-model="form.targetHost" />
            </div>
            <div class="form-item">
              <label>端口</label
              ><input v-model.number="form.targetPort" type="number" />
            </div>
            <div class="form-item">
              <label>数据库</label><input v-model="form.targetDatabase" />
            </div>
            <div class="form-item">
              <label>表</label><input v-model="form.targetTable" />
            </div>
          </div>
          <div class="table-mapping-editor">
            <div class="table-mapping-head">
              <label>同步表配置</label
              ><span class="muted"
                >支持单表或多表，一个任务内可分别指定目标表</span
              ><button class="btn-default" type="button" @click="addTable">
                ＋ 添加表
              </button>
            </div>
            <div
              v-for="(table, index) in form.tables"
              :key="table.id || index"
              class="table-mapping-row"
            >
              <input v-model="table.sourceDatabase" placeholder="源数据库" />
              <input v-model="table.sourceTable" placeholder="源表名" />
              <span class="arrow">→</span>
              <input v-model="table.targetDatabase" placeholder="目标数据库" />
              <input v-model="table.targetTable" placeholder="目标表名" />
              <input
                v-model="table.partitionColumn"
                placeholder="分区字段（可选）"
              />
              <button
                class="link-action danger-link"
                type="button"
                @click="removeTable(index)"
              >
                删除
              </button>
            </div>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>来源用户</label><input v-model="form.sourceUsername" />
            </div>
            <div class="form-item">
              <label>来源密码</label
              ><input v-model="form.sourcePassword" type="password" />
            </div>
            <div class="form-item">
              <label>目标用户</label><input v-model="form.targetUsername" />
            </div>
            <div class="form-item">
              <label>目标密码</label
              ><input v-model="form.targetPassword" type="password" />
            </div>
          </div>
          <div class="form-item">
            <label>字段映射（source:target，逗号或换行分隔）</label
            ><textarea v-model="form.mappings" rows="2"></textarea>
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>过滤条件（可选）</label
              ><input
                v-model="form.where"
                placeholder="例如 order_status = 1"
              />
            </div>
            <div class="form-item">
              <label>并行度</label
              ><input v-model.number="form.parallelism" type="number" min="1" />
            </div>
            <div class="form-item">
              <label>批量大小</label
              ><input v-model.number="form.batchSize" type="number" min="1" />
            </div>
          </div>
          <div class="form-item">
            <label>同步模式</label
            ><select v-model="form.syncMode">
              <option>FULL</option>
              <option>INCREMENTAL</option>
            </select>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn-default" @click="show = false">取消</button
          ><button class="btn-primary" @click="create">
            {{ editingId ? "保存修改" : "生成配置" }}
          </button>
        </div>
      </div>
    </div>
    <el-dialog v-model="logVisible" :title="logTitle" width="min(900px, 88vw)"
      ><div class="integration-log">
        <pre>{{ logContent }}</pre>
      </div></el-dialog
    >
    <el-dialog
      v-model="detailVisible"
      title="同步任务配置"
      width="min(620px, 90vw)"
      ><div v-if="selectedTask" class="task-detail">
        <div>
          <span>任务名称</span><b>{{ selectedTask.name }}</b>
        </div>
        <div>
          <span>来源</span><b>{{ selectedTask.source }}</b>
        </div>
        <div>
          <span>目标</span><b>{{ selectedTask.target }}</b>
        </div>
        <div>
          <span>同步模式</span><b>{{ selectedTask.mode }}</b>
        </div>
        <div>
          <span>当前状态</span><b>{{ selectedTask.status }}</b>
        </div>
        <div class="task-detail-tables">
          <span>关联表（{{ selectedTask.tables?.length || 0 }}）</span>
          <table class="data-table compact-table">
            <thead>
              <tr>
                <th>源表</th>
                <th>目标表</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="table in selectedTask.tables"
                :key="table.id || table.sourceTable"
              >
                <td>{{ table.sourceDatabase }}.{{ table.sourceTable }}</td>
                <td>{{ table.targetDatabase }}.{{ table.targetTable }}</td>
                <td>
                  <button
                    class="link-action danger-link"
                    @click="deleteTaskTable(table)"
                  >
                    删除
                  </button>
                </td>
              </tr>
              <tr v-if="!selectedTask.tables?.length">
                <td colspan="3" class="empty-state">
                  暂无关联表，请编辑任务重新添加
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <template #footer
        ><button class="btn-default" @click="detailVisible = false">关闭</button
        ><button
          class="btn-default"
          @click="selectedTask && openLog(selectedTask)"
        >
          查看日志</button
        ><button
          class="btn-primary"
          @click="selectedTask && runTask(selectedTask)"
        >
          运行任务
        </button></template
      ></el-dialog
    >
  </section>
</template>

<style scoped>
.integration-wizard { max-width:1680px; margin:0 auto; background:linear-gradient(135deg,#f4f8fc 0%,#edf6f8 100%); }.wizard-titlebar { display:flex; align-items:center; justify-content:space-between; padding:8px 0 22px; }.wizard-titlebar h1 { margin:0; color:#18263b; font-size:30px; letter-spacing:-.03em; }.wizard-titlebar p { margin:7px 0 0; color:#546985; font-size:16px; }.wizard-steps { display:grid; grid-template-columns:repeat(3,1fr); gap:12px; padding:12px; border:1px solid #dbe7f3; border-radius:18px; background:#fff; box-shadow:0 10px 28px rgba(50,91,133,.08); }.wizard-step { height:68px; border:0; border-radius:14px; background:transparent; color:#8a97aa; font:600 16px inherit; cursor:pointer; }.wizard-step span { display:inline-grid; width:34px; height:34px; margin-right:12px; place-items:center; border-radius:50%; color:#fff; background:#98a3b6; }.wizard-step.active { color:#fff; background:linear-gradient(110deg,#2864eb,#0795b4); box-shadow:0 10px 20px rgba(31,99,225,.20); }.wizard-step.active span { background:rgba(255,255,255,.18); }.wizard-step.done { color:#049f76; background:#d9f7e9; }.wizard-step.done span { background:#a8edd3; color:#079e72; }.wizard-step:disabled { cursor:default; }.wizard-card { margin-top:22px; padding:32px; border:1px solid #dbe7f3; border-radius:18px; background:#fff; box-shadow:0 10px 28px rgba(50,91,133,.07); }.wizard-card-title { display:flex; align-items:center; gap:13px; margin-bottom:25px; }.wizard-card-title h2 { margin:0; color:#1d2a40; font-size:20px; }.wizard-card-title .btn-default { margin-left:auto; }.wizard-icon { display:grid; width:42px; height:42px; place-items:center; border-radius:11px; color:#2963dc; background:#e4efff; font-size:22px; }.wizard-two-columns { display:grid; grid-template-columns:1fr 1fr; gap:22px; }.wizard-field { min-width:0; }.wizard-field label { display:block; margin:0 0 8px; color:#536783; font-size:14px; font-weight:600; }.wizard-field input,.wizard-field select { width:100%; height:42px; border:1px solid #d8e4f2; border-radius:11px; outline:0; padding:0 13px; color:#26364d; background:#f8fafc; font:inherit; }.wizard-field input:focus,.wizard-field select:focus { border-color:#83b5fb; box-shadow:0 0 0 2px rgba(40,100,235,.1); }.wizard-field.inline,.wizard-field.inline-wide { display:flex; align-items:center; gap:26px; margin-top:22px; }.wizard-field.inline > label,.wizard-field.inline-wide > label { width:80px; margin:0; flex:0 0 80px; }.wizard-field.inline-wide input,.wizard-field.inline-wide select { flex:1; }.radio-label { margin:0 !important; display:inline-flex !important; align-items:center; gap:8px; color:#536783 !important; font-size:15px !important; font-weight:500 !important; }.radio-label input { width:18px; height:18px; accent-color:#2864eb; }.wizard-endpoints { display:grid; grid-template-columns:1fr 60px 1fr; gap:24px; align-items:center; margin-top:28px; }.endpoint-card { padding:20px; border:1px solid #dbe7f3; border-radius:14px; background:#f9fbfd; }.endpoint-card h3 { margin:0 0 18px; font-size:17px; }.endpoint-card.source h3 { color:#2864eb; }.endpoint-card.target h3 { color:#08a375; }.endpoint-meta { margin-top:12px; color:#8091a8; font-size:13px; }.endpoint-arrow { color:#2864eb; text-align:center; font-size:34px; font-weight:700; }.wizard-footer { display:flex; justify-content:space-between; margin-top:30px; }.wizard-footer .btn-default,.wizard-footer .btn-primary { min-width:130px; height:40px; border-radius:10px; font-weight:700; }.table-selector-card { min-height:650px; }.selection-count { margin-left:auto; padding:7px 13px; border-radius:16px; color:#2660dc; background:#e5efff; font-weight:700; }.table-selector-tools { display:flex; align-items:center; justify-content:space-between; margin-bottom:20px; }.check-label { display:flex; align-items:center; gap:10px; color:#536783; font-size:15px; font-weight:600; }.check-label input,.source-table-row input { width:19px; height:19px; accent-color:#2864eb; }.table-search { width:360px; height:42px; border:1px solid #d8e4f2; border-radius:11px; padding:0 13px; background:#f8fafc; color:#26364d; font:inherit; outline:0; }.source-table-list { min-height:480px; max-height:560px; overflow:auto; padding:8px; border:1px solid #dce7f4; border-radius:16px; background:#f9fbfd; }.source-table-row { display:grid; grid-template-columns:24px 24px minmax(240px,1fr) minmax(180px,.8fr); gap:12px; align-items:center; min-height:64px; padding:0 14px; border-bottom:1px solid #e8eef5; cursor:pointer; }.source-table-row:last-child { border-bottom:0; }.source-table-row:hover { background:#f3f8ff; }.source-table-row .el-icon { color:#2864eb; font-size:19px; }.source-table-row code { color:#27364d; font:15px Consolas,Monaco,monospace; }.source-table-row span { overflow:hidden; color:#8797ad; text-align:right; text-overflow:ellipsis; white-space:nowrap; }.wizard-preview { padding-bottom:20px; }.preview-grid { display:grid; grid-template-columns:1fr 1fr; gap:18px; }.preview-grid div { min-height:86px; padding:16px; border:1px solid #dbe7f3; border-radius:14px; display:flex; flex-direction:column; justify-content:center; gap:8px; }.preview-grid span { color:#8393a9; font-size:13px; }.preview-grid b { color:#1d2a40; font-size:16px; }.preview-grid b small { color:#8797ad; font-weight:600; }.table-total { display:block; margin-top:24px; color:#2864eb; font-size:17px; }.config-preview pre { max-height:360px; margin:0; overflow:auto; padding:22px; border-radius:14px; background:#111b30; color:#d5e4ff; font:14px/1.75 Consolas,Monaco,monospace; white-space:pre-wrap; }
.integration-modal {
  width: min(980px, 92vw);
}
.table-mapping-editor {
  margin: 4px 0 16px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: #fbfdff;
}
.table-mapping-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.table-mapping-head label {
  color: #596273;
  font-weight: 600;
}
.table-mapping-head .btn-default {
  margin-left: auto;
}
.table-mapping-row {
  display: grid;
  grid-template-columns: 1fr 1.1fr 24px 1fr 1.1fr 1fr 42px;
  gap: 6px;
  align-items: center;
  margin-top: 7px;
}
.table-mapping-row input {
  width: 100%;
  height: 32px;
  border: 1px solid #d9e0e8;
  border-radius: 6px;
  padding: 0 8px;
  outline: none;
}
.table-mapping-row input:focus {
  border-color: #7eb4ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.08);
}
.danger-link {
  color: #e5484d;
}
.compact-table {
  margin-top: 8px;
}
.task-detail-tables {
  grid-column: 1 / -1;
}
.task-detail-tables > span {
  display: block;
}
.integration-modal textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px;
  font: inherit;
}
.integration-log {
  max-height: 62vh;
  overflow: auto;
  background: #101722;
  color: #d7e4f5;
  border-radius: 6px;
  padding: 14px;
}
.integration-log pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font:
    12px/1.6 Consolas,
    Monaco,
    monospace;
}
.task-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.task-detail div {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 7px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.task-detail span {
  color: #8b95a5;
  font-size: 12px;
}
@media (max-width: 900px) { .integration-wizard { padding:14px; }.wizard-titlebar { align-items:flex-start; gap:14px; }.wizard-titlebar h1 { font-size:24px; }.wizard-steps { gap:6px; padding:7px; }.wizard-step { height:54px; font-size:13px; }.wizard-step span { width:25px; height:25px; margin-right:5px; }.wizard-two-columns,.wizard-endpoints,.preview-grid { grid-template-columns:1fr; }.endpoint-arrow { display:none; }.wizard-field.inline,.wizard-field.inline-wide { align-items:flex-start; flex-wrap:wrap; gap:13px; }.wizard-field.inline > label,.wizard-field.inline-wide > label { width:100%; flex-basis:100%; }.table-search { width:52%; }.source-table-row { grid-template-columns:22px 22px minmax(130px,1fr); }.source-table-row span { display:none; }.wizard-card { padding:20px; }.table-selector-card { min-height:0; }.source-table-list { min-height:280px; }.wizard-footer .btn-default,.wizard-footer .btn-primary { min-width:110px; } }
</style>
