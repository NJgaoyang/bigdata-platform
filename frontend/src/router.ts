import { createRouter, createWebHistory } from "vue-router";
import OverviewView from "./views/OverviewView.vue";
import DevelopmentView from "./views/DevelopmentView.vue";
import IntegrationView from "./views/IntegrationProductionView.vue";
import ExploreView from "./views/ExploreView.vue";
import LineageView from "./views/LineageView.vue";
import WorkflowView from "./views/WorkflowView.vue";
import OperationsView from "./views/OperationsView.vue";
import SettingsView from "./views/SettingsView.vue";
import LoginView from "./views/LoginView.vue";
import { platformApi } from "./api";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView },
    { path: "/", redirect: "/overview" },
    { path: "/overview", component: OverviewView },
    { path: "/integration", component: IntegrationView, meta: { permission: "DATA_INTEGRATION" } },
    { path: "/development", component: DevelopmentView, meta: { permission: "DATA_DEVELOPMENT" } },
    { path: "/explore", component: ExploreView, meta: { permission: "DATA_EXPLORE" } },
    { path: "/lineage", component: LineageView, meta: { permission: "DATA_LINEAGE" } },
    { path: "/workflow", component: WorkflowView, meta: { permission: "SCHEDULER" } },
    { path: "/operations", component: OperationsView, meta: { permission: "OPERATIONS" } },
    { path: "/settings", component: SettingsView, meta: { permission: "SYSTEM_SETTINGS" } },
  ],
});

router.beforeEach(async (to) => {
  const token = localStorage.getItem("platform_access_token");
  const permission = to.meta.permission as string | undefined;
  if (!token || !permission) return true;
  try {
    const session = (await platformApi.me()).data.data;
    if (session.permissions?.includes(permission) || session.superAdmin) return true;
    return { path: "/overview", query: { reason: "permission" } };
  } catch {
    localStorage.removeItem("platform_access_token");
    localStorage.removeItem("platform_username");
    return { path: "/login" };
  }
});

export default router;
