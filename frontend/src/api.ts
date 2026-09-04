import axios from "axios";

const http = axios.create({ baseURL: "/api", timeout: 10000 });
http.interceptors.request.use((config) => {
  const token = localStorage.getItem("platform_access_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error.response?.status === 401 &&
      window.location.pathname !== "/login"
    ) {
      window.location.assign("/login");
    }
    return Promise.reject(error);
  },
);
export interface ApiResult<T> {
  success: boolean;
  data: T;
  message: string;
}
export interface DevFile {
  id: number;
  projectId: number;
  folderId?: number;
  name: string;
  fileType: string;
  content: string;
  status: string;
  currentVersion: number;
}
export interface Project {
  id: number;
  name: string;
  description?: string;
  status: string;
}
export interface IntegrationRequest {
  name: string;
  sourceType: string;
  targetType: string;
  syncMode: string;
  source: Record<string, unknown>;
  target: Record<string, unknown>;
  mappings: { source: string; target: string }[];
  options: Record<string, unknown>;
}
export interface DataSource {
  id: number;
  name: string;
  type: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  status: string;
}

export interface SeaTunnelCluster {
  id: number;
  name: string;
  host: string;
  port: number;
  sshUsername?: string;
  sshPort: number;
  seatunnelHome: string;
  description?: string;
  healthStatus: string;
  createdAt?: string;
}

export const platformApi = {
  health: () => http.get<ApiResult<Record<string, unknown>>>("/health"),
  login: (username: string, password: string) =>
    http.post<
      ApiResult<{ token: string; username: string; expiresAt: string }>
    >("/auth/login", { username, password }),
  logout: () => http.post<ApiResult<void>>("/auth/logout"),
  me: () => http.get<ApiResult<{ username: string; authenticated: boolean; permissions: string[] }>>("/auth/me"),
  projects: () => http.get<ApiResult<Project[]>>("/development/projects"),
  createProject: (request: Record<string, unknown>) =>
    http.post<ApiResult<Project>>("/development/projects", request),
  updateProject: (id: number, request: Record<string, unknown>) =>
    http.put<ApiResult<Project>>(`/development/projects/${id}`, request),
  deleteProject: (id: number) =>
    http.delete<ApiResult<void>>(`/development/projects/${id}`),
  folders: (projectId: number) =>
    http.get<ApiResult<Record<string, unknown>[]>>("/development/folders", {
      params: { projectId },
    }),
  createFolder: (request: Record<string, unknown>) =>
    http.post<ApiResult<Record<string, unknown>>>(
      "/development/folders",
      request,
    ),
  updateFolder: (id: number, request: Record<string, unknown>) =>
    http.put<ApiResult<Record<string, unknown>>>(
      `/development/folders/${id}`,
      request,
    ),
  deleteFolder: (id: number) =>
    http.delete<ApiResult<void>>(`/development/folders/${id}`),
  files: (projectId: number) =>
    http.get<ApiResult<DevFile[]>>("/development/files", {
      params: { projectId },
    }),
  createFile: (request: Record<string, unknown>) =>
    http.post<ApiResult<DevFile>>("/development/files", request),
  fileVersions: (id: number) =>
    http.get<ApiResult<Record<string, unknown>[]>>(
      `/development/files/${id}/versions`,
    ),
  saveFile: (id: number, content: string, name?: string) =>
    http.put<ApiResult<DevFile>>(`/development/files/${id}`, { content, name }),
  deleteFile: (id: number) =>
    http.delete<ApiResult<void>>(`/development/files/${id}`),
  query: (
    sql: string,
    selected = false,
    dataSourceId?: number,
    databaseName?: string,
  ) =>
    http.post<ApiResult<Record<string, unknown>>>("/query/execute", {
      sql,
      selected,
      dataSourceId,
      databaseName,
    }),
  querySubmit: (
    sql: string,
    selected = false,
    dataSourceId?: number,
    databaseName?: string,
  ) =>
    http.post<ApiResult<{ executionId: string; status: string }>>(
      "/query/submit",
      { sql, selected, dataSourceId, databaseName },
    ),
  queryStatus: (executionId: string) =>
    http.get<ApiResult<Record<string, unknown>>>(`/query/${executionId}`),
  queryHistory: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/query/history"),
  cancelQuery: (executionId: string) =>
    http.post<ApiResult<void>>(`/query/${executionId}/cancel`),
  dataSources: () => http.get<ApiResult<DataSource[]>>("/data-sources"),
  createDataSource: (request: Record<string, unknown>) =>
    http.post<ApiResult<DataSource>>("/data-sources", request),
  updateDataSource: (id: number, request: Record<string, unknown>) =>
    http.put<ApiResult<DataSource>>(`/data-sources/${id}`, request),
  deleteDataSource: (id: number) =>
    http.delete<ApiResult<void>>(`/data-sources/${id}`),
  testDataSource: (id: number) =>
    http.post<ApiResult<{ success: boolean; message: string }>>(
      `/data-sources/${id}/test`,
    ),
  metadataDatabases: (dataSourceId: number, type: string) =>
    http.get<ApiResult<{ name: string; comment: string }[]>>(
      "/metadata/databases",
      { params: { dataSourceId, type } },
    ),
  metadataTables: (dataSourceId: number, database: string) =>
    http.get<ApiResult<{ name: string; comment: string; type: string }[]>>(
      "/metadata/tables",
      { params: { dataSourceId, database } },
    ),
  metadataColumns: (dataSourceId: number, database: string, table: string) =>
    http.get<
      ApiResult<
        { name: string; dataType: string; nullable: boolean; comment: string }[]
      >
    >("/metadata/columns", { params: { dataSourceId, database, table } }),
  workflows: () => http.get<ApiResult<Record<string, unknown>[]>>("/workflows"),
  createWorkflow: (request: Record<string, unknown>) =>
    http.post<ApiResult<Record<string, unknown>>>("/workflows", request),
  deleteWorkflow: (id: number) =>
    http.delete<ApiResult<void>>(`/workflows/${id}`),
  workflow: (id: number) =>
    http.get<ApiResult<Record<string, unknown>>>(`/workflows/${id}`),
  updateWorkflow: (id: number, request: Record<string, unknown>) =>
    http.put<ApiResult<Record<string, unknown>>>(`/workflows/${id}`, request),
  publishWorkflow: (id: number) =>
    http.post<ApiResult<Record<string, unknown>>>(`/workflows/${id}/publish`),
  runWorkflow: (id: number) =>
    http.post<ApiResult<Record<string, unknown>>>(`/workflows/${id}/run`),
  schedule: (workflowId: number) =>
    http.get<ApiResult<Record<string, unknown>>>(
      `/scheduler/workflows/${workflowId}/schedule`,
    ),
  saveSchedule: (workflowId: number, request: Record<string, unknown>) =>
    http.put<ApiResult<Record<string, unknown>>>(
      `/scheduler/workflows/${workflowId}/schedule`,
      request,
    ),
  onlineWorkflow: (workflowId: number) =>
    http.post<ApiResult<Record<string, unknown>>>(
      `/scheduler/workflows/${workflowId}/online`,
    ),
  offlineWorkflow: (workflowId: number) =>
    http.post<ApiResult<Record<string, unknown>>>(
      `/scheduler/workflows/${workflowId}/offline`,
    ),
  backfillWorkflow: (workflowId: number, request: Record<string, unknown>) =>
    http.post<ApiResult<Record<string, unknown>>>(
      `/scheduler/workflows/${workflowId}/backfill`,
      request,
    ),
  updateWorkflowGraph: (workflowId: number, request: Record<string, unknown>) =>
    http.put<ApiResult<Record<string, unknown>>>(
      `/workflows/${workflowId}/graph`,
      request,
    ),
  validateWorkflow: (workflowId: number) =>
    http.post<ApiResult<{ valid: boolean; message: string }>>(
      `/workflows/${workflowId}/validate`,
    ),
  systemUsers: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/system/users"),
  systemUsersSearch: (keyword: string) =>
    http.get<ApiResult<Record<string, unknown>[]>>("/system/users", { params: { keyword } }),
  createSystemUser: (request: Record<string, unknown>) =>
    http.post<ApiResult<Record<string, unknown>>>("/system/users", request),
  updateSystemUser: (id: number, request: Record<string, unknown>) =>
    http.put<ApiResult<Record<string, unknown>>>(`/system/users/${id}`, request),
  deleteSystemUser: (id: number) =>
    http.delete<ApiResult<void>>(`/system/users/${id}`),
  disableSystemUser: (id: number) =>
    http.post<ApiResult<Record<string, unknown>>>(`/system/users/${id}/disable`),
  enableSystemUser: (id: number) =>
    http.post<ApiResult<Record<string, unknown>>>(`/system/users/${id}/enable`),
  userPermissions: (id: number) =>
    http.get<ApiResult<string[]>>(`/system/users/${id}/permissions`),
  setUserPermissions: (id: number, permissions: string[]) =>
    http.put<ApiResult<string[]>>(`/system/users/${id}/permissions`, { permissions }),
  systemRoles: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/system/roles"),
  systemAuditLogs: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/system/audit-logs"),
  clusters: () => http.get<ApiResult<SeaTunnelCluster[]>>("/system/clusters"),
  createCluster: (request: Record<string, unknown>) => http.post<ApiResult<SeaTunnelCluster>>("/system/clusters", request),
  updateCluster: (id: number, request: Record<string, unknown>) => http.put<ApiResult<SeaTunnelCluster>>(`/system/clusters/${id}`, request),
  deleteCluster: (id: number) => http.delete<ApiResult<void>>(`/system/clusters/${id}`),
  checkCluster: (id: number) => http.post<ApiResult<SeaTunnelCluster>>(`/system/clusters/${id}/check`),
  checkAllClusters: () => http.post<ApiResult<SeaTunnelCluster[]>>("/system/clusters/check-all"),
  systemAlertChannels: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/system/alert-channels"),
  systemFeatures: () =>
    http.get<ApiResult<Record<string, boolean>>>("/system/features"),
  systemOperationLogs: () =>
    http.get<ApiResult<string[]>>("/system/operation-logs"),
  integrations: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/integration/tasks"),
  integrationSourceTables: (dataSourceId: number) =>
    http.get<ApiResult<{ name: string; comment?: string }[]>>("/integration/tasks/source-tables", { params: { dataSourceId } }),
  createIntegration: (request: IntegrationRequest) =>
    http.post<ApiResult<Record<string, unknown>>>(
      "/integration/tasks",
      request,
    ),
  deleteIntegration: (id: number) =>
    http.delete<ApiResult<void>>(`/integration/tasks/${id}`),
  getIntegration: (id: number) =>
    http.get<ApiResult<Record<string, unknown>>>(`/integration/tasks/${id}`),
  integrationTables: (id: number) =>
    http.get<ApiResult<Record<string, unknown>[]>>(
      `/integration/tasks/${id}/tables`,
    ),
  deleteIntegrationTable: (taskId: number, tableId: number) =>
    http.delete<ApiResult<Record<string, unknown>>>(
      `/integration/tasks/${taskId}/tables/${tableId}`,
    ),
  updateIntegration: (id: number, request: IntegrationRequest) =>
    http.put<ApiResult<Record<string, unknown>>>(
      `/integration/tasks/${id}`,
      request,
    ),
  runIntegration: (id: number) =>
    http.post<ApiResult<Record<string, unknown>>>(
      `/integration/tasks/${id}/run`,
    ),
  integrationInstances: (id: number) =>
    http.get<ApiResult<Record<string, unknown>[]>>(
      `/integration/tasks/${id}/instances`,
    ),
  integrationLog: (executionId: string) =>
    http.get<ApiResult<string>>(
      `/integration/tasks/executions/${executionId}/log`,
    ),
  integrationStatus: (executionId: string) =>
    http.get<
      ApiResult<{ executionId: string; status: string; message: string }>
    >(`/integration/tasks/executions/${executionId}`),
  lineage: () => http.get<ApiResult<Record<string, unknown>[]>>("/lineage"),
  operationInstances: () =>
    http.get<ApiResult<Record<string, unknown>[]>>(
      "/operations/process-instances",
    ),
  operationTasks: () =>
    http.get<ApiResult<Record<string, unknown>[]>>(
      "/operations/task-instances",
    ),
  failedOperations: () =>
    http.get<ApiResult<Record<string, unknown>[]>>("/operations/failed-tasks"),
  operationLog: (id: string) =>
    http.get<ApiResult<string>>(`/operations/task-instances/${id}/log`),
  stopOperation: (id: string) =>
    http.post<ApiResult<void>>(`/operations/process-instances/${id}/stop`),
  rerunOperation: (id: string) =>
    http.post<ApiResult<Record<string, unknown>>>(
      `/operations/process-instances/${id}/rerun`,
    ),
};
