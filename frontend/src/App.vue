<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Bell, QuestionFilled, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { platformApi } from "./api";

const route = useRoute();
const router = useRouter();
const navItems = [
  { path: "/integration", label: "数据集成", permission: "DATA_INTEGRATION" },
  { path: "/development", label: "数据开发", permission: "DATA_DEVELOPMENT" },
  { path: "/explore", label: "数据探查", permission: "DATA_EXPLORE" },
  { path: "/lineage", label: "数据血缘", permission: "DATA_LINEAGE" },
  { path: "/workflow", label: "调度中心", permission: "SCHEDULER" },
  { path: "/operations", label: "运维中心", permission: "OPERATIONS" },
  { path: "/settings", label: "系统设置", permission: "SYSTEM_SETTINGS" },
];
const permissions = ref<string[]>(navItems.map(item => item.permission));
const visibleNavItems = computed(() => navItems.filter(item => permissions.value.includes(item.permission)));
const currentLabel = computed(
  () =>
    navItems.find((item) => route.path.startsWith(item.path))?.label ||
    "工作台",
);
const searchVisible = ref(false);
const helpVisible = ref(false);
const noticeVisible = ref(false);
const userVisible = ref(false);
const authenticated = ref(
  Boolean(localStorage.getItem("platform_access_token")),
);
const username = ref(localStorage.getItem("platform_username") || "admin");
const globalKeyword = ref("");
const health = ref<Record<string, unknown>>({});
async function refreshSession() {
  const token = localStorage.getItem("platform_access_token");
  if (!token) {
    authenticated.value = false;
    permissions.value = navItems.map(item => item.permission);
    return;
  }
  try {
    const session = (await platformApi.me()).data.data;
    authenticated.value = Boolean(session.authenticated);
    username.value = session.username || username.value;
    permissions.value = session.permissions || [];
  } catch {
    authenticated.value = false;
    permissions.value = [];
  }
}
onMounted(async () => {
  await refreshSession();
  try {
    health.value = (await platformApi.health()).data.data || {};
  } catch {
    health.value = { status: "DOWN" };
  }
  router.afterEach(refreshSession);
});
function runGlobalSearch() {
  const keyword = globalKeyword.value.trim().toLowerCase();
  const target =
    navItems.find((item) => item.label.includes(keyword)) ||
    (keyword.includes("sql")
      ? navItems.find((item) => item.path === "/development")
      : undefined) ||
    (keyword.includes("任务")
      ? navItems.find((item) => item.path === "/operations")
      : undefined);
  if (!target) {
    ElMessage.warning("请输入模块名称，例如“数据开发”或“运维中心”");
    return;
  }
  searchVisible.value = false;
  router.push(target.path);
}
async function logout() {
  try {
    await platformApi.logout();
  } catch {
    /* session may already be expired */
  }
  localStorage.removeItem("platform_access_token");
  localStorage.removeItem("platform_username");
  permissions.value = [];
  authenticated.value = false;
  userVisible.value = false;
  await router.replace("/login");
}
</script>

<template>
  <div v-if="route.path !== '/login'" class="app-shell">
    <header class="top-nav">
      <div class="logo" @click="router.push('/development')">
        <div class="logo-mark">D</div>
        <span>DataWorks Studio</span>
      </div>
      <div class="nav-items">
        <button
          v-for="item in visibleNavItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: route.path.startsWith(item.path) }"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="top-spacer"></div>
      <div class="top-actions">
        <button class="icon-btn" title="全局搜索" @click="searchVisible = true">
          <el-icon><Search /></el-icon></button
        ><button class="icon-btn" title="使用帮助" @click="helpVisible = true">
          <el-icon><QuestionFilled /></el-icon></button
        ><button class="icon-btn" title="通知" @click="noticeVisible = true">
          <el-icon><Bell /></el-icon></button
        ><button class="user user-button" @click="userVisible = true">
          <div class="avatar">A</div>
          <span>{{ username }}⌄</span>
        </button>
      </div>
    </header>
    <main class="app-main"><router-view /></main>
    <el-dialog v-model="searchVisible" title="全局搜索" width="min(520px, 90vw)"
      ><div class="global-search">
        <input
          v-model="globalKeyword"
          autofocus
          placeholder="输入模块名称，例如：数据开发"
          @keyup.enter="runGlobalSearch"
        /><button class="btn-primary" @click="runGlobalSearch">打开</button>
      </div></el-dialog
    >
    <el-dialog v-model="helpVisible" title="使用帮助" width="min(620px, 90vw)"
      ><div class="help-list">
        <p>
          <b>{{ currentLabel }}</b> 页面帮助
        </p>
        <p>
          数据开发用于编写和保存 SQL、Python、Shell；数据集成用于创建并运行
          SeaTunnel 同步任务；调度中心用于配置 DolphinScheduler 工作流。
        </p>
        <p>运行记录和日志请前往“运维中心”。数据探查仅开放 StarRocks 数据源。</p>
      </div></el-dialog
    >
    <el-dialog v-model="noticeVisible" title="通知中心" width="min(620px, 90vw)"
      ><div class="notice-item">
        <b>平台服务正常</b
        ><span>{{ health.status === "UP" ? `后端已连接 · ${health.mode === "real" ? "真实调度器模式" : "开发模式"}` : "后端连接异常" }}</span>
      </div>
      <div class="notice-item">
        <b>数据探查安全策略</b><span>仅允许查询 StarRocks</span>
      </div></el-dialog
    >
    <el-dialog v-model="userVisible" title="当前用户" width="min(420px, 90vw)"
      ><div class="user-card">
        <div class="avatar">A</div>
        <div>
          <b>{{ username }}</b>
          <p>平台管理员</p>
        </div>
      </div>
      <button
        class="btn-default"
        @click="
          userVisible = false;
          router.push('/settings');
        "
      >
        打开系统配置
      </button>
      <button
        v-if="authenticated"
        class="btn-default logout-button"
        @click="logout"
      >
        退出登录
      </button></el-dialog
    >
  </div>
  <router-view v-else />
</template>

<style scoped>
.user-button {
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 7px;
}
.user-button:hover {
  background: #f2f5f9;
}
.logout-button {
  margin-left: 8px;
}
.global-search {
  display: flex;
  gap: 8px;
}
.global-search input {
  flex: 1;
  height: 36px;
  border: 1px solid #d9e0e8;
  border-radius: 6px;
  padding: 0 10px;
  outline: none;
}
.help-list {
  color: #475467;
  line-height: 1.8;
}
.notice-item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px 0;
  border-bottom: 1px solid #eef1f5;
}
.notice-item span,
.user-card p {
  color: #8b95a5;
  font-size: 12px;
  margin: 0;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
