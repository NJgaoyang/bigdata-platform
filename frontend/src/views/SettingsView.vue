<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { Clock, Coin, Connection, Lock, Search, UserFilled } from "@element-plus/icons-vue";
import { platformApi, type DataSource, type SeaTunnelCluster } from "../api";

type Section = "users" | "permissions" | "dataSources" | "audits" | "clusters";
type User = Record<string, any>;
const section = ref<Section>("users");
const navItems = [
  { key: "users" as Section, label: "用户管理", icon: UserFilled },
  { key: "permissions" as Section, label: "权限中心", icon: Lock },
  { key: "dataSources" as Section, label: "数据源", icon: Coin },
  { key: "audits" as Section, label: "审计日志", icon: Clock },
  { key: "clusters" as Section, label: "集群配置", icon: Connection },
];

const users = ref<User[]>([]);
const userKeyword = ref("");
const userLoading = ref(false);
const userDialog = ref(false);
const editingUserId = ref<number | null>(null);
const userForm = ref({ username: "", displayName: "", password: "", roleCode: "USER", status: "ACTIVE" });
const userDialogTitle = computed(() => editingUserId.value ? "编辑用户" : "新增用户");

const permissionOptions = [
  { code: "DATA_INTEGRATION", label: "数据集成", description: "创建、编辑和运行同步任务" },
  { code: "DATA_DEVELOPMENT", label: "数据开发", description: "项目、文件和 SQL 执行" },
  { code: "DATA_EXPLORE", label: "数据探查", description: "仅允许访问 StarRocks 元数据和查询" },
  { code: "DATA_LINEAGE", label: "数据血缘", description: "查看和分析表级血缘" },
  { code: "SCHEDULER", label: "调度中心", description: "工作流和调度配置" },
  { code: "OPERATIONS", label: "运维中心", description: "实例、日志、重跑和停止" },
  { code: "SYSTEM_SETTINGS", label: "系统设置", description: "用户、权限、数据源和审计" },
];
const permissionUserId = ref<number | null>(null);
const selectedPermissions = ref<string[]>([]);
const permissionLoading = ref(false);

const sources = ref<DataSource[]>([]);
const sourceDialog = ref(false);
const editingSourceId = ref<number | null>(null);
const sourceForm = ref({ name: "", type: "STARROCKS", host: "", port: 9030, databaseName: "", username: "", password: "" });
const sourceDialogTitle = computed(() => editingSourceId.value ? "编辑数据源" : "新增数据源");

const audits = ref<Record<string, any>[]>([]);
const auditKeyword = ref("");
const auditTimeRange = ref<string[]>([]);
const filteredAudits = computed(() => {
  const query = auditKeyword.value.trim().toLowerCase();
  const [startValue, endValue] = auditTimeRange.value;
  const start = startValue ? new Date(startValue.replace(" ", "T")).getTime() : Number.NEGATIVE_INFINITY;
  const end = endValue ? new Date(endValue.replace(" ", "T")).getTime() : Number.POSITIVE_INFINITY;
  return audits.value.filter(item => {
    const recordedAt = new Date(String(item.createdAt || "")).getTime();
    const matchesTime = Number.isNaN(recordedAt) ? !startValue && !endValue : recordedAt >= start && recordedAt <= end;
    const matchesKeyword = !query || [item.operatorName, item.action, item.resourceType, item.detail, auditAction(item.action), auditObject(item), auditDetail(item)].some(value => String(value || "").toLowerCase().includes(query));
    return matchesTime && matchesKeyword;
  });
});
const selectedUser = computed(() => users.value.find(user => Number(user.id) === permissionUserId.value));
const clusters = ref<SeaTunnelCluster[]>([]);
const clusterDialog = ref(false);
const clusterDetailVisible = ref(false);
const editingClusterId = ref<number | null>(null);
const selectedCluster = ref<SeaTunnelCluster | null>(null);
const clusterForm = ref({ name: "", host: "", port: 5801, sshUsername: "", sshPort: 22, sshPassword: "", seatunnelHome: "/data/software/seatunnel", description: "" });
const clusterDialogTitle = computed(() => editingClusterId.value ? "编辑客户端" : "新增客户端");
function errorMessage(error: unknown, fallback: string) {
  const value = error as { response?: { data?: { message?: unknown } }; message?: unknown };
  const message = value?.response?.data?.message || value?.message;
  return typeof message === "string" && message.trim() ? message : fallback;
}
function roleLabel(user: User) { return String(user.roleCode || (String(user.username).toLowerCase() === "admin" ? "ADMIN" : "USER")).toUpperCase() === "ADMIN" ? "管理员" : "普通用户"; }
function isBuiltInAdmin(user: User) { return String(user.username || "").trim().toLowerCase() === "admin"; }
function formatDateTime(value: unknown) {
  if (!value) return "—";
  const date = new Date(String(value));
  if (Number.isNaN(date.getTime())) return String(value).replace("T", " ").replace(/\.\d+$/, "");
  const pad = (item: number) => String(item).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
function auditResult(item: Record<string, any>) { return /FAILED|ERROR|DENIED|失败|拒绝/i.test(`${item.action || ""} ${item.detail || ""}`) ? "失败" : "成功"; }
const auditResourceLabels: Record<string, string> = {
  AUTH: "登录认证", HTTP: "系统接口", USER: "用户", ROLE: "角色", PROJECT: "项目", DATASOURCE: "数据源",
  QUERY: "数据查询", ALERT_CHANNEL: "告警通道", SEATUNNEL_CLUSTER: "SeaTunnel 客户端", INTEGRATION_TASK: "同步任务",
  WORKFLOW: "工作流", SCHEDULE: "调度任务", TASK: "任务",
};
const auditActionLabels: Record<string, string> = {
  API_REQUEST: "系统操作", ACCESS_DENIED: "访问拒绝", LOGIN: "用户登录", LOGOUT: "退出登录", LOGIN_FAILED: "登录失败", CHANGE_PASSWORD: "修改密码",
  CREATE_USER: "新增用户", UPDATE_USER: "编辑用户", DELETE_USER: "删除用户", UPDATE_USER_STATUS: "用户状态切换",
  SET_USER_PERMISSIONS: "更新用户权限", CREATE_ROLE: "新增角色", GRANT_PERMISSION: "授予角色权限",
  GRANT_PROJECT_PERMISSION: "授予项目权限", GRANT_DATASOURCE_PERMISSION: "授予数据源权限", CREATE_ALERT_CHANNEL: "新增告警通道",
  QUERY_EXECUTE: "执行查询", CREATE_CLUSTER: "新增客户端", UPDATE_CLUSTER: "编辑客户端", DELETE_CLUSTER: "删除客户端", CHECK_CLUSTER: "检查客户端",
  RUN_TASK: "执行任务", EXECUTE_TASK: "执行任务", TOGGLE_TASK_STATUS: "上下线切换", PUBLISH_WORKFLOW: "发布工作流", RUN_WORKFLOW: "执行工作流",
};
function auditResource(value: unknown) { return auditResourceLabels[String(value || "").toUpperCase()] || String(value || "其他对象"); }
function auditAction(value: unknown) {
  const raw = String(value || "").toUpperCase();
  if (auditActionLabels[raw]) return auditActionLabels[raw];
  if (raw.startsWith("CREATE_")) return `新增${auditResource(raw.slice(7))}`;
  if (raw.startsWith("UPDATE_")) return `更新${auditResource(raw.slice(7))}`;
  if (raw.startsWith("DELETE_")) return `删除${auditResource(raw.slice(7))}`;
  if (raw.startsWith("RUN_") || raw.startsWith("EXECUTE_")) return "执行任务";
  return String(value || "系统操作");
}
function auditObject(item: Record<string, any>) { const resource = auditResource(item.resourceType); return item.resourceId ? `${resource}#${item.resourceId}` : resource; }
function apiOperationDetail(value: string) {
  const match = value.match(/^(GET|POST|PUT|PATCH|DELETE)\s+([^?\s]+)/i);
  if (!match) return value;
  const method = match[1].toUpperCase();
  const path = match[2].replace(/\/\d+(?=\/|$)/g, "/:id");
  const routes: Record<string, string> = {
    "/api/system/users": "用户列表", "/api/system/users/:id/disable": "用户状态", "/api/system/users/:id/enable": "用户状态", "/api/system/users/:id/permissions": "用户权限",
    "/api/system/audit-logs": "审计日志", "/api/system/clusters": "集群配置", "/api/data-sources": "数据源", "/api/integration/tasks": "同步任务",
    "/api/development/files": "开发文件", "/api/development/folders": "开发目录", "/api/development/projects": "开发项目",
    "/api/metadata/databases": "元数据目录", "/api/metadata/tables": "元数据表", "/api/lineage": "数据血缘", "/api/workflows": "工作流",
    "/api/operations/process-instances": "流程实例", "/api/operations/task-instances": "任务实例", "/api/operations/failed-tasks": "失败任务",
  };
  const label = routes[path] || routes[path.replace(/\/:id$/, "")] || "系统资源";
  const verbs: Record<string, string> = { GET: "查看", POST: "新增", PUT: "更新", PATCH: "更新", DELETE: "删除" };
  return `${verbs[method] || "访问"}${label}`;
}
function auditDetail(item: Record<string, any>) {
  const raw = String(item.detail || "");
  const action = String(item.action || "").toUpperCase();
  if (!raw) return "—";
  if (action === "API_REQUEST") return apiOperationDetail(raw);
  if (action === "QUERY_EXECUTE") { const rows = raw.match(/rows=(\d+)/i)?.[1]; return rows ? `查询成功，返回 ${rows} 行数据` : "执行数据查询"; }
  if (action === "LOGIN") return `用户 ${raw} 登录平台`;
  if (action === "LOGOUT") return `用户 ${raw} 退出平台`;
  if (action === "LOGIN_FAILED") return `用户 ${raw} 登录失败`;
  if (action === "UPDATE_USER_STATUS") return raw.toUpperCase() === "DISABLED" ? "已禁用用户" : raw.toUpperCase() === "ACTIVE" ? "已启用用户" : raw;
  if (action === "CHECK_CLUSTER") return raw.toUpperCase() === "HEALTHY" ? "客户端连通性正常" : raw.toUpperCase() === "UNREACHABLE" ? "客户端暂不可达" : raw;
  if (action === "SET_USER_PERMISSIONS") return `已更新模块权限：${raw.split(",").map(item => ({ DATA_INTEGRATION: "数据集成", DATA_DEVELOPMENT: "数据开发", DATA_EXPLORE: "数据探查", DATA_LINEAGE: "数据血缘", SCHEDULER: "调度中心", OPERATIONS: "运维中心", SYSTEM_SETTINGS: "系统设置" }[item.trim()] || item.trim())).join("、")}`;
  return /^[\u4e00-\u9fa5]/.test(raw) ? raw : `${auditAction(item.action)}：${raw}`;
}

async function loadUsers() {
  userLoading.value = true;
  try {
    users.value = (await platformApi.systemUsersSearch(userKeyword.value)).data.data || [];
    if (permissionUserId.value && !users.value.some(user => Number(user.id) === permissionUserId.value)) permissionUserId.value = null;
  } catch (error) {
    users.value = [];
    ElMessage.error(error instanceof Error ? error.message : "用户列表加载失败");
  } finally {
    userLoading.value = false;
  }
}
function openCreateUser() {
  editingUserId.value = null;
  userForm.value = { username: "", displayName: "", password: "", roleCode: "USER", status: "ACTIVE" };
  userDialog.value = true;
}
function openEditUser(user: User) {
  editingUserId.value = Number(user.id);
  userForm.value = { username: String(user.username || ""), displayName: String(user.displayName || ""), password: "", roleCode: String(user.roleCode || (String(user.username).toLowerCase() === "admin" ? "ADMIN" : "USER")), status: String(user.status || "ACTIVE") };
  userDialog.value = true;
}
async function saveUser() {
  if (!userForm.value.username.trim() || !userForm.value.displayName.trim()) {
    ElMessage.warning("请填写用户名和显示名");
    return;
  }
  if (!editingUserId.value && !userForm.value.password) {
    ElMessage.warning("新用户必须设置登录密码");
    return;
  }
  try {
    if (editingUserId.value) await platformApi.updateSystemUser(editingUserId.value, userForm.value);
    else await platformApi.createSystemUser(userForm.value);
    userDialog.value = false;
    await loadUsers();
    ElMessage.success(editingUserId.value ? "用户已更新" : "用户已创建");
  } catch (error) {
    ElMessage.error(errorMessage(error, "用户保存失败"));
  }
}
async function toggleUser(user: User) {
  const id = Number(user.id);
  try {
    if (String(user.status).toUpperCase() === "DISABLED") await platformApi.enableSystemUser(id);
    else await platformApi.disableSystemUser(id);
    await loadUsers();
    ElMessage.success(String(user.status).toUpperCase() === "DISABLED" ? "用户已启用" : "用户已禁用");
  } catch (error: any) {
    ElMessage.error(errorMessage(error, "用户状态更新失败"));
  }
}
async function deleteUser(user: User) {
  if (!window.confirm(`确认删除用户“${user.username}”吗？`)) return;
  try {
    await platformApi.deleteSystemUser(Number(user.id));
    await loadUsers();
    ElMessage.success("用户已删除");
  } catch (error: any) {
    ElMessage.error(errorMessage(error, "用户删除失败"));
  }
}
async function selectPermissionUser(userId: number) {
  permissionUserId.value = userId;
  const user = users.value.find(item => Number(item.id) === userId);
  if (user && isBuiltInAdmin(user)) {
    selectedPermissions.value = permissionOptions.map(item => item.code);
    return;
  }
  permissionLoading.value = true;
  try {
    selectedPermissions.value = (await platformApi.userPermissions(userId)).data.data || [];
  } catch (error: any) {
    selectedPermissions.value = [];
    ElMessage.error(error?.response?.data?.message || "用户权限加载失败");
  } finally {
    permissionLoading.value = false;
  }
}
async function savePermissions() {
  if (!permissionUserId.value) {
    ElMessage.warning("请先选择用户");
    return;
  }
  if (selectedUser.value && isBuiltInAdmin(selectedUser.value)) {
    ElMessage.info("内置 admin 始终拥有全部权限");
    return;
  }
  try {
    await platformApi.setUserPermissions(permissionUserId.value, selectedPermissions.value);
    ElMessage.success("用户权限已更新");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "用户权限保存失败");
  }
}
function openCreateSource() {
  editingSourceId.value = null;
  sourceForm.value = { name: "", type: "STARROCKS", host: "", port: 9030, databaseName: "", username: "", password: "" };
  sourceDialog.value = true;
}
function openEditSource(source: DataSource) {
  editingSourceId.value = source.id;
  sourceForm.value = { name: source.name, type: source.type, host: source.host, port: source.port, databaseName: source.databaseName, username: source.username, password: "" };
  sourceDialog.value = true;
}
async function saveSource() {
  const form = sourceForm.value;
  if (![form.name, form.host, form.username].every(item => item.trim())) {
    ElMessage.warning("请填写数据源名称、主机和用户名");
    return;
  }
  if (!editingSourceId.value && !form.password) {
    ElMessage.warning("新建数据源必须填写密码");
    return;
  }
  try {
    const saved = editingSourceId.value
      ? await platformApi.updateDataSource(editingSourceId.value, sourceForm.value)
      : await platformApi.createDataSource(sourceForm.value);
    sourceDialog.value = false;
    sources.value = (await platformApi.dataSources()).data.data || [];
    ElMessage.success(editingSourceId.value ? "数据源已更新" : "数据源已创建");
    const tested = await platformApi.testDataSource(saved.data.data.id);
    if (!tested.data.data.success) ElMessage.warning(tested.data.data.message);
  } catch (error) {
    ElMessage.error(errorMessage(error, "数据源保存失败"));
  }
}
async function setMetadataVisibility(source: DataSource, value: string | number | boolean) {
  const visible = Boolean(value);
  try {
    const response = await platformApi.setDataSourceMetadataVisibility(source.id, visible);
    const index = sources.value.findIndex(item => item.id === source.id);
    if (index >= 0) sources.value[index] = response.data.data;
    ElMessage.success(visible ? "已在元数据中展示" : "已从元数据中隐藏");
  } catch (error) {
    ElMessage.error(errorMessage(error, "元数据展示设置失败"));
  }
}
async function deleteSource(source: DataSource) {
  if (!window.confirm(`确认删除数据源“${source.name}”吗？`)) return;
  try {
    await platformApi.deleteDataSource(source.id);
    sources.value = sources.value.filter(item => item.id !== source.id);
    ElMessage.success("数据源已删除");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "数据源删除失败");
  }
}
async function testSource(source: DataSource) {
  try {
    const result = await platformApi.testDataSource(source.id);
    result.data.data.success ? ElMessage.success(result.data.data.message) : ElMessage.warning(result.data.data.message);
  } catch (error) {
    ElMessage.error(errorMessage(error, "数据源连接测试失败"));
  }
}
async function loadAudits() {
  try {
    audits.value = (await platformApi.systemAuditLogs()).data.data || [];
  } catch (error: any) {
    audits.value = [];
    ElMessage.error(error?.response?.data?.message || "审计日志加载失败");
  }
}
async function loadClusters() {
  try {
    clusters.value = (await platformApi.clusters()).data.data || [];
  } catch (error: any) {
    clusters.value = [];
    ElMessage.error(error?.response?.data?.message || "集群配置加载失败");
  }
}
function openCreateCluster() {
  editingClusterId.value = null;
  clusterForm.value = { name: "", host: "", port: 5801, sshUsername: "", sshPort: 22, sshPassword: "", seatunnelHome: "/data/software/seatunnel", description: "" };
  clusterDialog.value = true;
}
function openEditCluster(cluster: SeaTunnelCluster) {
  editingClusterId.value = cluster.id;
  clusterForm.value = { name: cluster.name, host: cluster.host, port: cluster.port, sshUsername: cluster.sshUsername || "", sshPort: cluster.sshPort || 22, sshPassword: "", seatunnelHome: cluster.seatunnelHome, description: cluster.description || "" };
  clusterDialog.value = true;
}
async function saveCluster() {
  const form = clusterForm.value;
  if (![form.name, form.host, form.seatunnelHome].every(item => item.trim())) {
    ElMessage.warning("请填写客户端名称、主机地址和 SeaTunnel 根目录");
    return;
  }
  if (!Number.isInteger(form.port) || form.port < 1 || form.port > 65535 || !Number.isInteger(form.sshPort) || form.sshPort < 1 || form.sshPort > 65535) {
    ElMessage.warning("端口必须是 1 到 65535 的整数");
    return;
  }
  try {
    if (editingClusterId.value) await platformApi.updateCluster(editingClusterId.value, clusterForm.value);
    else await platformApi.createCluster(clusterForm.value);
    clusterDialog.value = false;
    await loadClusters();
    ElMessage.success(editingClusterId.value ? "客户端已更新" : "客户端已创建");
  } catch (error) {
    ElMessage.error(errorMessage(error, "客户端保存失败"));
  }
}
async function checkCluster(cluster: SeaTunnelCluster) {
  try {
    const result = await platformApi.checkCluster(cluster.id);
    const updated = result.data.data;
    clusters.value = clusters.value.map(item => item.id === updated.id ? updated : item);
    ElMessage.success(updated.healthStatus === "HEALTHY" ? "客户端连接正常" : "客户端暂不可达");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "客户端检查失败");
  }
}
async function checkAllClusters() {
  try {
    clusters.value = (await platformApi.checkAllClusters()).data.data || [];
    ElMessage.success("集群检查已完成");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "集群检查失败");
  }
}
async function deleteCluster(cluster: SeaTunnelCluster) {
  if (!window.confirm(`确认删除客户端“${cluster.name}”吗？`)) return;
  try {
    await platformApi.deleteCluster(cluster.id);
    clusters.value = clusters.value.filter(item => item.id !== cluster.id);
    ElMessage.success("客户端已删除");
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "客户端删除失败");
  }
}
function clusterHealthLabel(value: string) { return value === "HEALTHY" ? "健康" : value === "UNREACHABLE" ? "不可达" : "未检查"; }
async function changeSection(next: Section) {
  section.value = next;
  if (next === "users" || next === "permissions") await loadUsers();
  if (next === "dataSources") sources.value = (await platformApi.dataSources()).data.data || [];
  if (next === "audits") await loadAudits();
  if (next === "clusters") await loadClusters();
}
onMounted(async () => {
  await Promise.allSettled([loadUsers(), loadAudits(), loadClusters(), platformApi.dataSources().then(result => { sources.value = result.data.data || []; })]);
});
</script>

<template>
  <section class="page">
    <div class="module-bar"><div class="module-title">系统设置 <span class="crumb">/ 管理中心</span></div><div class="module-actions"><span class="muted">用户、权限、数据源和审计统一管理</span></div></div>
    <div class="settings-layout">
      <aside class="settings-nav">
        <button v-for="item in navItems" :key="item.key" class="settings-nav-item" :class="{ active: section === item.key }" @click="changeSection(item.key)"><span class="settings-nav-icon"><el-icon><component :is="item.icon" /></el-icon></span><span class="settings-nav-label">{{ item.label }}</span></button>
      </aside>
      <main class="settings-content">
        <template v-if="section === 'users'">
          <div class="section-heading"><div><h2>用户管理</h2><p>管理平台登录用户、显示名和账号状态。</p></div><button class="btn-primary" @click="openCreateUser">＋ 新增用户</button></div>
          <div class="card user-table-card"><div class="filterbar user-filterbar"><input v-model="userKeyword" placeholder="搜索用户名或姓名" @keyup.enter="loadUsers"><button class="btn-primary" :disabled="userLoading" @click="loadUsers">查询</button><button class="btn-default" @click="userKeyword = ''; loadUsers()">重置</button></div><table class="data-table user-table"><thead><tr><th>ID</th><th>用户名</th><th>姓名</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="user in users" :key="String(user.id)"><td>{{ user.id }}</td><td>{{ user.username }}</td><td>{{ user.displayName }}</td><td>{{ roleLabel(user) }}</td><td><span class="user-status" :class="String(user.status).toUpperCase() === 'ACTIVE' ? 'enabled' : 'disabled'">{{ String(user.status).toUpperCase() === 'ACTIVE' ? '启用' : '禁用' }}</span></td><td>{{ formatDateTime(user.createdAt) }}</td><td class="user-actions"><button class="user-action edit" @click="openEditUser(user)">编辑</button><template v-if="!isBuiltInAdmin(user)"><button class="user-action" @click="toggleUser(user)">{{ String(user.status).toUpperCase() === 'DISABLED' ? '启用' : '禁用' }}</button><button class="user-action delete" @click="deleteUser(user)">删除</button></template><span v-else class="protected-user">内置账号</span></td></tr><tr v-if="!users.length"><td colspan="7" class="empty-state">暂无用户</td></tr></tbody></table></div>
        </template>
        <template v-else-if="section === 'permissions'">
          <div class="section-heading"><div><h2>权限中心</h2><p>按用户单独授予平台模块访问权限，未勾选的模块将拒绝访问。</p></div></div>
          <div class="permission-layout"><div class="card permission-users"><div class="card-head"><span>选择用户</span><span class="muted">{{ users.length }} 个用户</span></div><button v-for="user in users" :key="String(user.id)" class="permission-user" :class="{ active: permissionUserId === Number(user.id) }" @click="selectPermissionUser(Number(user.id))"><span>{{ user.displayName }}</span><small>{{ user.username }}</small></button><div v-if="!users.length" class="empty-state">暂无用户</div></div><div class="card permission-editor"><div class="card-head"><span>{{ selectedUser ? `${selectedUser.displayName} 的模块权限` : '请先选择用户' }}</span><button class="btn-primary" :disabled="permissionLoading || !permissionUserId || Boolean(selectedUser && isBuiltInAdmin(selectedUser))" @click="savePermissions">保存权限</button></div><div v-if="permissionUserId" class="permission-options"><label v-for="item in permissionOptions" :key="item.code" class="permission-option"><input v-model="selectedPermissions" type="checkbox" :value="item.code" :disabled="Boolean(selectedUser && isBuiltInAdmin(selectedUser))"><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span></label></div><div v-else class="empty-state">从左侧选择用户后配置权限</div></div></div>
        </template>
        <template v-else-if="section === 'dataSources'">
          <div class="section-heading"><div><h2>数据源</h2><p>配置数据连接，并控制是否在数据探查的元数据列表中展示。</p></div><button class="btn-primary" @click="openCreateSource">＋ 新增数据源</button></div>
          <div class="card"><table class="data-table"><thead><tr><th>名称</th><th>类型</th><th>连接地址</th><th>数据库</th><th>元数据展示</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="source in sources" :key="source.id"><td>{{ source.name }}</td><td>{{ source.type }}</td><td class="mono">{{ source.host }}:{{ source.port }}</td><td>{{ source.databaseName || '—' }}</td><td><el-switch :model-value="source.metadataVisible !== false" active-text="展示" inactive-text="隐藏" inline-prompt @change="setMetadataVisibility(source, $event)" /></td><td><span class="status-dot ok"></span>{{ source.status }}</td><td class="actions"><button @click="testSource(source)">测试连接</button><button @click="openEditSource(source)">编辑</button><button class="danger-text" @click="deleteSource(source)">删除</button></td></tr><tr v-if="!sources.length"><td colspan="7" class="empty-state">暂无数据源，请新增 MySQL 或 StarRocks 连接</td></tr></tbody></table></div>
        </template>
        <template v-else-if="section === 'audits'">
          <div class="section-heading audit-heading"><div><h2>操作记录</h2><p>查看所有用户的操作日志</p></div><button class="btn-default" @click="loadAudits">刷新</button></div>
          <div class="card audit-searchbar"><el-icon><Search /></el-icon><input v-model="auditKeyword" class="audit-keyword" placeholder="搜索用户、操作、对象或详情"><div class="audit-time-range"><label>时间</label><el-date-picker v-model="auditTimeRange" class="audit-time-picker" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" format="YYYY年MM月DD日 HH:mm" value-format="YYYY-MM-DD HH:mm:ss" :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]" /></div><button v-if="auditKeyword || auditTimeRange.length" class="audit-search-clear" @click="auditKeyword = ''; auditTimeRange = []">清除</button><span class="audit-count">{{ filteredAudits.length }} 条记录</span></div>
          <div class="card audit-table-card"><table class="data-table audit-table"><thead><tr><th>ID</th><th>用户</th><th>操作</th><th>对象</th><th>详情</th><th>结果</th><th>时间</th></tr></thead><tbody><tr v-for="item in filteredAudits" :key="String(item.id)"><td>{{ item.id }}</td><td>{{ item.operatorName || '—' }}</td><td>{{ auditAction(item.action) }}</td><td>{{ auditObject(item) }}</td><td class="audit-detail">{{ auditDetail(item) }}</td><td><span class="audit-result" :class="auditResult(item) === '成功' ? 'success' : 'failure'">{{ auditResult(item) }}</span></td><td>{{ formatDateTime(item.createdAt) }}</td></tr><tr v-if="!filteredAudits.length"><td colspan="7" class="empty-state">{{ auditKeyword ? '未找到匹配的操作记录' : '暂无操作记录' }}</td></tr></tbody></table></div>
        </template>
        <template v-else>
          <div class="section-heading cluster-heading"><div><h2>集群配置</h2><p>管理 SeaTunnel 集群客户端节点</p></div></div>
          <div class="cluster-toolbar"><button class="btn-primary" @click="openCreateCluster">＋ 新增客户端</button><button class="btn-default" @click="checkAllClusters">⟳ 检查全部</button></div>
          <div class="card cluster-table-card"><table class="data-table cluster-table"><thead><tr><th>名称</th><th>主机:端口</th><th>节点健康</th><th>描述</th><th>操作</th></tr></thead><tbody><tr v-for="cluster in clusters" :key="cluster.id"><td class="cluster-name">{{ cluster.name }}</td><td>{{ cluster.host }}:{{ cluster.port }}</td><td><span class="cluster-health" :class="cluster.healthStatus.toLowerCase()">{{ clusterHealthLabel(cluster.healthStatus) }}</span></td><td>{{ cluster.description || '—' }}</td><td class="cluster-actions"><button class="user-action" @click="checkCluster(cluster)">检查</button><button class="user-action" @click="openEditCluster(cluster)">编辑</button><button class="user-action delete" @click="deleteCluster(cluster)">删除</button><button class="user-action" @click="selectedCluster = cluster; clusterDetailVisible = true">查看</button></td></tr><tr v-if="!clusters.length"><td colspan="5" class="empty-state">暂无 SeaTunnel 客户端，请新增客户端</td></tr></tbody></table></div>
        </template>
      </main>
    </div>
    <div v-if="userDialog" class="modal-mask user-modal-mask" @click.self="userDialog = false"><div class="modal user-modal"><div class="modal-head user-modal-head"><span>{{ userDialogTitle }}</span><button class="modal-close" aria-label="关闭" @click="userDialog = false">×</button></div><div class="modal-body user-modal-body"><div class="user-form-row"><label>用户名</label><input v-model="userForm.username" :disabled="Boolean(editingUserId)"></div><div class="user-form-row"><label>密码</label><input v-model="userForm.password" type="password" :placeholder="editingUserId ? '留空表示保留原密码' : '请输入登录密码'"></div><div class="user-form-row"><label>姓名</label><input v-model="userForm.displayName"></div><div class="user-form-row"><label>角色</label><select v-model="userForm.roleCode" :disabled="userForm.username.toLowerCase() === 'admin'"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></div><div class="user-form-row user-status-row"><label>状态</label><span>禁用</span><el-switch v-model="userForm.status" active-value="ACTIVE" inactive-value="DISABLED" :disabled="userForm.username.toLowerCase() === 'admin'" /><span>启用</span></div></div><div class="modal-foot user-modal-foot"><button class="btn-default" @click="userDialog = false">取消</button><button class="btn-primary" @click="saveUser">保存</button></div></div></div>
    <div v-if="sourceDialog" class="modal-mask" @click.self="sourceDialog = false"><div class="modal"><div class="modal-head"><span>{{ sourceDialogTitle }}</span><button class="modal-close" @click="sourceDialog = false">×</button></div><div class="modal-body"><div class="form-item"><label>名称</label><input v-model="sourceForm.name"></div><div class="form-row"><div class="form-item"><label>类型</label><select v-model="sourceForm.type"><option>STARROCKS</option><option>MYSQL</option></select></div><div class="form-item"><label>端口</label><input v-model.number="sourceForm.port" type="number"></div></div><div class="form-item"><label>主机</label><input v-model="sourceForm.host"></div><div class="form-item"><label>数据库（可选）</label><input v-model="sourceForm.databaseName" placeholder="不填写时连接服务器并浏览全部数据库"></div><div class="form-row"><div class="form-item"><label>用户名</label><input v-model="sourceForm.username"></div><div class="form-item"><label>密码</label><input v-model="sourceForm.password" type="password" placeholder="编辑时留空表示保留原密码"></div></div></div><div class="modal-foot"><button class="btn-default" @click="sourceDialog = false">取消</button><button class="btn-primary" @click="saveSource">保存</button></div></div></div>
    <div v-if="clusterDialog" class="modal-mask cluster-modal-mask" @click.self="clusterDialog = false"><div class="modal cluster-modal"><div class="modal-head cluster-modal-head"><span>{{ clusterDialogTitle }}</span><button class="modal-close" aria-label="关闭" @click="clusterDialog = false">×</button></div><div class="modal-body cluster-modal-body"><div class="cluster-form-item"><label>客户端名称</label><input v-model="clusterForm.name" placeholder="请输入客户端名称"></div><div class="cluster-form-grid"><div class="cluster-form-item"><label>主机地址</label><input v-model="clusterForm.host" placeholder="例如 172.16.0.99"></div><div class="cluster-form-item"><label>端口</label><input v-model.number="clusterForm.port" type="number"></div></div><div class="cluster-form-grid"><div class="cluster-form-item"><label>SSH 用户名</label><input v-model="clusterForm.sshUsername" placeholder="可选"></div><div class="cluster-form-item"><label>SSH 端口</label><input v-model.number="clusterForm.sshPort" type="number"></div></div><div class="cluster-form-grid uneven"><div class="cluster-form-item"><label>SSH 密码 <small>编辑时留空表示保留原密码</small></label><input v-model="clusterForm.sshPassword" type="password" placeholder="SSH 登录密码"></div><div class="cluster-form-item"><label>SeaTunnel 根目录</label><input v-model="clusterForm.seatunnelHome"></div></div><div class="cluster-form-item"><label>描述</label><textarea v-model="clusterForm.description" rows="3" placeholder="补充客户端用途或环境说明"></textarea></div></div><div class="modal-foot cluster-modal-foot"><button class="btn-default" @click="clusterDialog = false">取消</button><button class="btn-primary" @click="saveCluster">保存</button></div></div></div>
    <el-dialog v-model="clusterDetailVisible" title="客户端详情" width="min(560px, 92vw)"><div v-if="selectedCluster" class="cluster-detail"><div><span>名称</span><b>{{ selectedCluster.name }}</b></div><div><span>服务地址</span><b>{{ selectedCluster.host }}:{{ selectedCluster.port }}</b></div><div><span>SSH</span><b>{{ selectedCluster.sshUsername || '未配置' }} : {{ selectedCluster.sshPort }}</b></div><div><span>SeaTunnel 根目录</span><b>{{ selectedCluster.seatunnelHome }}</b></div><div><span>节点健康</span><b>{{ clusterHealthLabel(selectedCluster.healthStatus) }}</b></div><div><span>描述</span><b>{{ selectedCluster.description || '—' }}</b></div></div></el-dialog>
  </section>
</template>

<style scoped>
.settings-layout { display:flex; min-height:calc(100vh - var(--nav-h) - var(--bar-h)); background:#f7f9fc; }
.settings-nav { width:236px; flex:0 0 236px; padding:10px 14px 20px; border-right:1px solid #dfe7f0; background:#fff; }
.settings-nav-item { display:flex; align-items:center; gap:12px; width:100%; min-height:52px; margin:2px 0; padding:8px 14px; border:1px solid transparent; border-radius:8px; text-align:left; color:#667085; background:transparent; font-family:"Microsoft YaHei","PingFang SC",-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif; font-size:14px; font-weight:400; line-height:20px; letter-spacing:normal; cursor:pointer; transition:background .18s ease, color .18s ease, border-color .18s ease; }
.settings-nav-item:hover { color:#344054; background:#f8fafc; }
.settings-nav-item.active { color:#344054; border-color:#d0d5dd; background:#f2f4f7; box-shadow:none; }
.settings-nav-label { font-family:"Microsoft YaHei","PingFang SC",-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif; font-size:14px; font-weight:400; line-height:20px; letter-spacing:normal; color:inherit; }
.settings-nav-icon { display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; border-radius:7px; color:#667085; background:#f2f4f7; font-size:18px; flex:0 0 32px; }
.settings-nav-item.active .settings-nav-icon { color:#344054; background:#e4e7ec; }
.settings-content { flex:1; min-width:0; padding:14px 24px 24px; }
.section-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:18px; }
.section-heading h2 { margin:0; color:#1d2939; font-size:20px; }
.section-heading p { margin:6px 0 0; color:#667085; font-size:13px; }
.permission-layout { display:grid; grid-template-columns:260px minmax(420px, 1fr); gap:18px; }
.permission-users { padding:0 10px 12px; }
.permission-user { display:flex; justify-content:space-between; width:100%; padding:11px 10px; border:0; border-radius:7px; background:transparent; text-align:left; cursor:pointer; color:#344054; }
.permission-user:hover, .permission-user.active { color:#1677ff; background:#edf5ff; }
.permission-user small { color:#98a2b3; }
.permission-editor { min-height:300px; }
.permission-options { display:grid; grid-template-columns:repeat(2, minmax(220px, 1fr)); gap:10px; padding:16px; }
.permission-option { display:flex; align-items:flex-start; gap:10px; padding:13px; border:1px solid #e4eaf2; border-radius:8px; cursor:pointer; }
.permission-option:has(input:checked) { border-color:#8dbdff; background:#f4f8ff; }
.permission-option input { margin-top:3px; accent-color:#1677ff; }
.permission-option span { display:flex; flex-direction:column; gap:5px; }
.permission-option small { color:#98a2b3; line-height:1.4; }
.audit-detail { max-width:420px; white-space:pre-wrap; word-break:break-word; }
.danger-text { color:#d64545; }
.user-table-card,.audit-table-card { overflow:hidden; border-radius:10px; }
.user-filterbar { justify-content:flex-end; }
.user-filterbar input { margin-right:auto; }
.user-table,.audit-table { table-layout:fixed; }
.user-table th,.audit-table th { height:52px; padding:0 20px; color:#526987; background:#f4f7fb; font-size:14px; font-weight:600; }
.user-table td,.audit-table td { height:58px; padding:0 20px; color:#4d6383; font-size:14px; border-color:#e6edf5; }
.user-table th:nth-child(1) { width:7%; }.user-table th:nth-child(2) { width:16%; }.user-table th:nth-child(3) { width:13%; }.user-table th:nth-child(4) { width:13%; }.user-table th:nth-child(5) { width:10%; }.user-table th:nth-child(6) { width:23%; }.user-table th:nth-child(7) { width:18%; }
.user-status { font-weight:500; }.user-status.enabled,.audit-result.success { color:#09a66d; }.user-status.disabled,.audit-result.failure { color:#e5484d; }
.user-actions { display:flex; align-items:center; gap:10px; white-space:nowrap; }
.protected-user { color:#98a2b3; font-size:12px; }
.user-action { min-width:60px; height:32px; padding:0 12px; border:1px solid #d7dee8; border-radius:8px; background:#fff; color:#536176; font-size:13px; font-weight:600; }
.user-action:hover { border-color:#a9c7f5; color:#1677ff; background:#f8fbff; }.user-action.edit { color:#1677ff; border-color:#bfd9ff; background:#f3f8ff; }.user-action.delete { color:#fff; border-color:#e8292f; background:#e8292f; }.user-action.delete:hover { border-color:#c61e24; background:#c61e24; color:#fff; }
.audit-heading { margin-bottom:16px; }.audit-heading h2 { font-size:20px; font-weight:700; letter-spacing:-.01em; }.audit-heading p { margin-top:6px; font-size:13px; color:#667085; }
.audit-searchbar { display:flex; align-items:center; gap:10px; min-height:58px; margin-bottom:16px; padding:10px 14px; border-color:#dbe5f0; border-radius:10px; }.audit-searchbar :deep(.el-icon) { color:#8294ad; font-size:17px; }.audit-searchbar .audit-keyword { flex:0 1 270px; width:270px; min-width:150px; height:32px; border:1px solid #d9e3ef; border-radius:6px; padding:0 9px; outline:0; color:#344054; background:#fff; font:13px inherit; }.audit-searchbar .audit-keyword::placeholder { color:#98a7bb; }.audit-time-range { display:flex; align-items:center; gap:7px; color:#667085; font-size:12px; white-space:nowrap; }.audit-time-range label { font-weight:600; }.audit-time-range :deep(.audit-time-picker.el-date-editor) { width:350px; height:32px; }.audit-time-range :deep(.audit-time-picker .el-range-input) { font-size:12px; }.audit-time-range :deep(.audit-time-picker .el-range-separator) { width:24px; font-size:12px; }.audit-search-clear { border:0; color:#667085; background:transparent; font:inherit; font-size:12px; cursor:pointer; }.audit-search-clear:hover { color:#1677ff; }.audit-count { margin-left:auto; padding-left:12px; border-left:1px solid #e5ebf2; color:#8493a7; font-size:12px; white-space:nowrap; }
.audit-table th { height:42px; padding:0 10px; font-size:12px; }.audit-table td { height:48px; padding:0 10px; font-size:12px; }.audit-table th:nth-child(1) { width:7%; }.audit-table th:nth-child(2) { width:10%; }.audit-table th:nth-child(3) { width:13%; }.audit-table th:nth-child(4) { width:23%; }.audit-table th:nth-child(5) { width:20%; }.audit-table th:nth-child(6) { width:8%; }.audit-table th:nth-child(7) { width:19%; }
.audit-table th,.audit-table td { border-right:0; }.audit-table .audit-detail { max-width:none; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.audit-result { font-weight:500; }
.cluster-heading { margin-bottom:16px; }.cluster-heading h2 { font-size:20px; font-weight:700; letter-spacing:-.01em; }.cluster-heading p { margin-top:6px; font-size:13px; color:#667085; }.cluster-toolbar { display:flex; gap:12px; margin-bottom:18px; }.cluster-toolbar .btn-primary,.cluster-toolbar .btn-default { height:38px; padding:0 16px; border-radius:9px; font-size:14px; font-weight:700; }.cluster-table-card { overflow:hidden; border-radius:12px; }.cluster-table th { height:52px; padding:0 24px; color:#526987; background:#f4f7fb; font-size:14px; font-weight:600; }.cluster-table td { height:68px; padding:0 24px; color:#536783; font-size:14px; border-color:#e6edf5; }.cluster-table th:nth-child(1) { width:17%; }.cluster-table th:nth-child(2) { width:19%; }.cluster-table th:nth-child(3) { width:15%; }.cluster-table th:nth-child(4) { width:25%; }.cluster-table th:nth-child(5) { width:24%; }.cluster-name { color:#1f2d42 !important; font-weight:700; }.cluster-health { display:inline-flex; align-items:center; min-width:64px; justify-content:center; height:28px; padding:0 10px; border-radius:14px; background:#f1f5f9; color:#64748b; font-size:12px; font-weight:700; }.cluster-health.healthy { background:#e8f8f0; color:#158f55; }.cluster-health.unreachable { background:#fff0f0; color:#d44343; }.cluster-actions { display:flex; gap:9px; align-items:center; white-space:nowrap; }.cluster-modal-mask { background:rgba(30,41,59,.48); }.cluster-modal { width:min(560px, calc(100vw - 32px)); border-radius:16px; }.cluster-modal-head { height:64px; padding:0 24px; color:#1f2d42; font-size:18px; font-weight:700; }.cluster-modal-head .modal-close { font-size:27px; font-weight:300; }.cluster-modal-body { padding:24px 36px 18px; }.cluster-form-item { margin-bottom:16px; }.cluster-form-item label { display:block; margin-bottom:7px; color:#526987; font-size:14px; font-weight:600; }.cluster-form-item label small { color:#8b9ab0; font-size:12px; font-weight:400; }.cluster-form-item input,.cluster-form-item textarea { width:100%; border:1px solid #d8e3f2; border-radius:10px; outline:0; color:#26364d; background:#f8fafc; padding:0 12px; font:inherit; }.cluster-form-item input { height:40px; }.cluster-form-item textarea { min-height:82px; padding:10px 12px; resize:vertical; }.cluster-form-item input:focus,.cluster-form-item textarea:focus { border-color:#88b9fa; box-shadow:0 0 0 2px rgba(22,119,255,.09); }.cluster-form-grid { display:grid; grid-template-columns:1fr 180px; gap:18px; }.cluster-form-grid.uneven { grid-template-columns:1fr 1fr; }.cluster-modal-foot { min-height:72px; padding:14px 28px; background:#f8fafc; }.cluster-modal-foot .btn-default,.cluster-modal-foot .btn-primary { min-width:76px; height:36px; border-radius:9px; font-weight:700; }.cluster-detail { display:grid; grid-template-columns:1fr 1fr; gap:12px; }.cluster-detail div { min-height:68px; padding:12px; border:1px solid #e3eaf3; border-radius:8px; display:flex; flex-direction:column; gap:7px; }.cluster-detail span { color:#8392a8; font-size:12px; }.cluster-detail b { color:#28374d; font-size:14px; word-break:break-word; }
.user-modal-mask { background:rgba(30,41,59,.48); }.user-modal { width:min(520px, calc(100vw - 32px)); border-radius:16px; }.user-modal-head { height:64px; padding:0 24px; color:#1f2d42; font-size:18px; font-weight:700; }.user-modal-head .modal-close { font-size:27px; font-weight:300; }.user-modal-body { padding:24px 34px 18px; }.user-form-row { display:grid; grid-template-columns:82px minmax(0, 1fr); align-items:center; gap:14px; margin-bottom:18px; }.user-form-row label { color:#526987; font-size:15px; font-weight:600; text-align:right; }.user-form-row input,.user-form-row select { width:100%; height:40px; padding:0 12px; border:1px solid #d8e3f2; border-radius:10px; outline:0; color:#26364d; background:#f8fafc; }.user-form-row input:focus,.user-form-row select:focus { border-color:#88b9fa; box-shadow:0 0 0 2px rgba(22,119,255,.09); }.user-status-row { grid-template-columns:82px auto 48px auto; justify-content:start; gap:12px; margin-bottom:6px; }.user-status-row span { color:#526987; font-size:14px; font-weight:600; }.user-status-row :deep(.el-switch) { --el-switch-on-color:#409eff; --el-switch-off-color:#cbd5e1; }.user-modal-foot { min-height:72px; padding:14px 28px; background:#f8fafc; }.user-modal-foot .btn-default,.user-modal-foot .btn-primary { min-width:76px; height:36px; border-radius:9px; font-weight:700; }
@media (max-width: 900px) { .settings-layout { display:block; } .settings-nav { width:auto; border-right:0; border-bottom:1px solid #e4eaf2; display:flex; overflow:auto; padding:12px; } .settings-nav-item { min-width:170px; min-height:52px; margin:0 4px; } .settings-content { padding:18px; } .permission-layout,.cluster-form-grid,.cluster-form-grid.uneven,.cluster-detail { grid-template-columns:1fr; } .cluster-actions { flex-wrap:wrap; }.audit-searchbar { flex-wrap:wrap; }.audit-count { margin-left:0; }.audit-time-range { width:100%; }.audit-time-range :deep(.audit-time-picker.el-date-editor) { width:min(350px, calc(100vw - 100px)); } }
</style>
