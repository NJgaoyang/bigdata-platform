import { createRouter, createWebHistory } from "vue-router";
import OverviewView from "./views/OverviewView.vue";
import DevelopmentView from "./views/DevelopmentView.vue";
import IntegrationView from "./views/IntegrationView.vue";
import ExploreView from "./views/ExploreView.vue";
import LineageView from "./views/LineageView.vue";
import WorkflowView from "./views/WorkflowView.vue";
import OperationsView from "./views/OperationsView.vue";
import SettingsView from "./views/SettingsView.vue";
import LoginView from "./views/LoginView.vue";

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", component: LoginView },
    { path: "/", redirect: "/development" },
    { path: "/overview", component: OverviewView },
    { path: "/integration", component: IntegrationView },
    { path: "/development", component: DevelopmentView },
    { path: "/explore", component: ExploreView },
    { path: "/lineage", component: LineageView },
    { path: "/workflow", component: WorkflowView },
    { path: "/operations", component: OperationsView },
    { path: "/settings", component: SettingsView },
  ],
});
