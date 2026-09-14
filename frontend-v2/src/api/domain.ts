import { api } from './http'
import type { DataSourceView } from './platform'

export interface DatabaseView { name: string; comment?: string }
export interface TableView { database: string; name: string; comment?: string; type?: string }
export interface ColumnView { name: string; dataType: string; nullable?: boolean; comment?: string; ordinalPosition?: number }
export interface TableProfile { dataSourceId:number; database:string; table:string; rowCount?:number; estimatedSizeBytes?:number; owner?:string; createTime?:string; updateTime?:string; ownerEditable:boolean }
export interface LineageView { id:number; sourceTable:string; targetTable:string; relationType:string; fileId?:number; fileVersionId?:number }
export interface TablePreview { dataSourceId:number; database:string; table:string; columns:string[]; rows:string[][]; limit:number }

export const metadataApi = {
  databases: (dataSourceId:number, type='STARROCKS') => api.get<DatabaseView[]>('/metadata/databases', { params:{ dataSourceId, type } }),
  tables: (dataSourceId:number, database:string) => api.get<TableView[]>('/metadata/tables', { params:{ dataSourceId, database } }),
  columns: (dataSourceId:number, database:string, table:string) => api.get<ColumnView[]>('/metadata/columns', { params:{ dataSourceId, database, table } }),
  profile: (dataSourceId:number, database:string, table:string) => api.get<TableProfile>('/metadata/table-profile', { params:{ dataSourceId, database, table } }),
  preview: (dataSourceId:number, database:string, table:string, limit=50) => api.get<TablePreview>('/metadata/table-preview', { params:{ dataSourceId, database, table, limit } }),
  updateOwner: (dataSourceId:number, database:string, table:string, owner:string) => api.put<TableProfile>('/metadata/table-profile/owner', { dataSourceId, database, table, owner }),
  lineage: (name:string) => api.get<LineageView[]>('/lineage/table', { params:{ name } })
}

export interface DevProject { id:number; name:string; description?:string; status:string; ownerName?:string }
export interface DevFolder { id:number; projectId:number; parentId?:number; name:string; createdAt?:string }
export interface DevFile { id:number; projectId:number; folderId?:number; name:string; fileType:string; content:string; description?:string; status:string; currentVersion:number; updatedAt?:string }
export interface FileVersion { id:number; fileId:number; versionNo:number; content:string; checksum?:string; publishFlag:boolean }
export interface QueryResult { executionId:string; status:string; columns:string[]; rows:Array<Record<string,unknown>>; rowCount:number; elapsedMs:number; errorMessage?:string; columnComments?:Record<string,string> }
export interface QueryHistory { queryId:string; datasourceId?:number; databaseName?:string; sql:string; status:string; username:string; startedAt?:string; elapsedMs:number; errorMessage?:string }

export const developmentApi = {
  projects: () => api.get<DevProject[]>('/development/projects'),
  folders: (projectId:number) => api.get<DevFolder[]>('/development/folders', { params:{ projectId } }),
  files: (projectId:number) => api.get<DevFile[]>('/development/files', { params:{ projectId } }),
  getFile: (id:number) => api.get<DevFile>(`/development/files/${id}`),
  createFile: (payload:{projectId:number;folderId?:number;name:string;fileType:string;content:string;description?:string}) => api.post<DevFile>('/development/files',payload),
  saveFile: (id:number,payload:{content:string;name?:string;description?:string;folderId?:number}) => api.put<DevFile>(`/development/files/${id}`,payload),
  versions: (id:number) => api.get<FileVersion[]>(`/development/files/${id}/versions`),
  createVersion: (id:number,content:string) => api.post<FileVersion>(`/development/files/${id}/versions`,{content}),
  publish: (id:number) => api.post<DevFile>(`/development/files/${id}/publish`),
  query: (sql:string,dataSourceId:number,databaseName?:string) => api.post<QueryResult>('/query/execute',{sql,selected:false,dataSourceId,databaseName}),
  history: () => api.get<QueryHistory[]>('/query/history')
}

export interface IntegrationTable { id:number; taskId:number; sourceDatabase:string; sourceTable:string; targetDatabase:string; targetTable:string; partitionColumn?:string }
export interface IntegrationTask { id:number; name:string; sourceType:string; targetType:string; syncMode:string; status:string; lifecycleStatus:string; sourceConfigJson:string; targetConfigJson:string; transformConfigJson:string; seatunnelConfig:string; tables:IntegrationTable[] }
export interface IntegrationTaskSummary { createdAt?:string; createdBy:string; lastRunAt?:string; nextRunAt?:string; durationMs?:number; dataCount?:number }
export interface IntegrationTaskSchedule { taskId:number; cronExpression:string; timezone:string; enabled:boolean }
export interface IntegrationInstance { id:number; taskId:number; executionId:string; status:string; startedAt?:string; finishedAt?:string; message?:string }
export interface IntegrationBatch { id:number; taskId:number; batchCode:string; triggerType:string; status:string; clusterId?:number; parametersJson?:string; sourceBatchId?:number; createdBy:string; startedAt?:string; finishedAt?:string; errorMessage?:string; createdAt?:string }
export interface IntegrationAttempt { id:number; batchId:number; attemptNo:number; executionId?:string; status:string; startedAt?:string; finishedAt?:string; errorMessage?:string; createdAt?:string }
export interface IntegrationCursor { taskId:number; cursorColumn?:string; cursorValue?:string; updatedAt?:string }
export interface IntegrationTaskPayload {
  name:string; sourceType:string; targetType:string; syncMode:string; sourceDataSourceId:number; targetDataSourceId:number;
  source:{host:string;port:number;database:string;username:string;password:string;table:string};
  target:{host:string;port:number;database:string;username:string;password:string;table:string};
  mappings:Array<{source:string;target:string}>; options:Record<string,unknown>;
  tables:Array<{sourceDatabase:string;sourceTable:string;targetDatabase:string;targetTable:string;partitionColumn?:string}>
}
export const integrationApi = {
  list: () => api.get<IntegrationTask[]>('/integration/tasks'),
  get: (id:number) => api.get<IntegrationTask>(`/integration/tasks/${id}`),
  summary: (id:number) => api.get<IntegrationTaskSummary>(`/integration/tasks/${id}/summary`),
  schedule: (id:number) => api.get<IntegrationTaskSchedule>(`/integration/tasks/${id}/schedule`),
  previewSchedule: (payload:{cronExpression:string;timezone:string;enabled:boolean}) => api.post<string[]>('/integration/tasks/schedule/preview',payload),
  saveSchedule: (id:number,payload:{cronExpression:string;timezone:string;enabled:boolean}) => api.put<IntegrationTaskSchedule>(`/integration/tasks/${id}/schedule`,payload),
  create: (payload:IntegrationTaskPayload) => api.post<IntegrationTask>('/integration/tasks',payload),
  update: (id:number,payload:IntegrationTaskPayload) => api.put<IntegrationTask>(`/integration/tasks/${id}`,payload),
  online: (id:number) => api.post<IntegrationTask>(`/integration/tasks/${id}/online`),
  offline: (id:number) => api.post<IntegrationTask>(`/integration/tasks/${id}/offline`),
  remove: (id:number) => api.delete<void>(`/integration/tasks/${id}`),
  run: (id:number) => api.post<{executionId:string;status:string}>(`/integration/tasks/${id}/run`),
  runConfirmed: (id:number) => api.post<{executionId:string;status:string}>(`/integration/tasks/${id}/run-confirmed`),
  stop: (id:number) => api.post<void>(`/integration/tasks/${id}/stop`),
  validate: (id:number) => api.post<{valid:boolean;message:string}>(`/integration/tasks/${id}/validate`),
  instances: (id:number) => api.get<IntegrationInstance[]>(`/integration/tasks/${id}/instances`),
  batches: (id:number) => api.get<IntegrationBatch[]>(`/integration/tasks/${id}/batches`),
  attempts: (batchId:number) => api.get<IntegrationAttempt[]>(`/integration/tasks/batches/${batchId}/attempts`),
  retryBatch: (batchId:number) => api.post<IntegrationBatch>(`/integration/tasks/batches/${batchId}/retry`),
  reconcileBatch: (batchId:number) => api.post<IntegrationBatch>(`/integration/tasks/batches/${batchId}/reconcile`),
  backfill: (id:number,payload:{where:string;startLabel?:string;endLabel?:string}) => api.post<IntegrationBatch>(`/integration/tasks/${id}/backfill`,payload),
  cursor: (id:number) => api.get<IntegrationCursor>(`/integration/tasks/${id}/cursor`),
  saveCursor: (id:number,payload:{cursorColumn?:string;cursorValue?:string}) => api.put<IntegrationCursor>(`/integration/tasks/${id}/cursor`,payload),
  log: (executionId:string) => api.get<string>(`/integration/tasks/executions/${executionId}/log`),
  sourceDatabases: (dataSourceId:number) => api.get<Array<{name:string}>>('/integration/tasks/source-databases',{params:{dataSourceId}}),
  sourceTables: (dataSourceId:number,database:string) => api.get<Array<{name:string;comment?:string}>>('/integration/tasks/source-tables',{params:{dataSourceId,database}}),
  targetDatabases: (dataSourceId:number) => api.get<Array<{name:string;comment?:string}>>('/integration/tasks/target-databases',{params:{dataSourceId}}),
  previewConfig: (payload:IntegrationTaskPayload) => api.post<string>('/integration/tasks/preview-config',payload)
}

export interface WorkflowNode { id:number; name:string; nodeType:'SQL'|'SEATUNNEL'|'CONDITION'; fileVersionId?:number; configJson?:string; x:number; y:number; nodeCode:string }
export interface WorkflowEdge { id:number; sourceNodeId:number; targetNodeId:number }
export interface WorkflowView { id:number; name:string; workflowCode:string; description?:string; status:string; publishedVersion:number; nodes:WorkflowNode[]; edges:WorkflowEdge[]; dsProcessCode?:string }
export interface WorkflowPayload { name:string; description?:string; nodes:Array<{name:string;nodeType:string;fileVersionId?:number;configJson?:string;x:number;y:number;nodeCode:string}>; edges:Array<{sourceNodeCode:string;targetNodeCode:string}> }
export interface ScheduleConfig { id:number; workflowId:number; cronExpression:string; timezone:string; enabled:boolean; failureStrategy:string; parallelism:number; workerGroup?:string; alertGroup?:string }
export const workflowApi = {
  list: () => api.get<WorkflowView[]>('/workflows'),
  get: (id:number) => api.get<WorkflowView>(`/workflows/${id}`),
  create: (payload:WorkflowPayload) => api.post<WorkflowView>('/workflows',payload),
  update: (id:number,payload:WorkflowPayload) => api.put<WorkflowView>(`/workflows/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/workflows/${id}`),
  run: (id:number) => api.post<{instanceId:string;status:string;message:string}>(`/workflows/${id}/run`),
  schedule: (id:number) => api.get<ScheduleConfig>(`/workflows/${id}/schedule`),
  saveSchedule: (id:number,payload:ScheduleConfig) => api.put<ScheduleConfig>(`/workflows/${id}/schedule`,payload),
  online: (id:number) => api.post<ScheduleConfig>(`/workflows/${id}/schedule/online`),
  offline: (id:number) => api.post<ScheduleConfig>(`/workflows/${id}/schedule/offline`)
}

export interface OperationSummary { total:number; running:number; success:number; failed:number; stopped:number }
export interface OperationInstance { type:string; id:string; externalId?:string; name:string; status:string; engine:string; createdBy?:string; startedAt?:string; finishedAt?:string; errorMessage?:string; createdAt?:string }
export interface FailureItem { type:string; id:string; parentInstanceId?:string; name:string; engine:string; status:string; attemptNo:number; errorMessage?:string; startedAt?:string }
export interface AlertItem { alertType:string; resourceType:string; resourceId:string; name:string; status:string; message?:string; occurredAt?:string; handlingState:string }
export const operationsApi = {
  summary: () => api.get<OperationSummary>('/operations/summary'),
  instances: () => api.get<OperationInstance[]>('/operations/instances'),
  failures: () => api.get<FailureItem[]>('/operations/failures'),
  alerts: () => api.get<AlertItem[]>('/operations/alerts'),
  stop: (type:string,id:string) => api.post<void>(`/operations/instances/${type}/${id}/stop`),
  log: (type:string,id:string) => api.get<string>(`/operations/instances/${type}/${id}/log`),
  rerun: (instanceId:string) => api.post<{instanceId:string;status:string}>(`/operations/workflow-instances/${instanceId}/rerun`)
}

export interface FlinkEnvironment { id:number; name:string; engineType:string; deploymentMode:string; submitterType:string; restUrl?:string; flinkHome?:string; flinkCdcHome?:string; javaHome?:string; flinkVersion?:string; flinkCdcVersion?:string; sshHost?:string; sshPort:number; sshUsername?:string; enabled:boolean; defaultEnvironment:boolean }
export interface RealtimeJob { id:number; name:string; description?:string; runtimeEnvironmentId?:number; releaseState:string; desiredState:string; observedState:string; definitionVersion:number; publishedVersion?:number; spec:Record<string,unknown>; configDigest?:string; lastError?:string; createdBy:string; updatedAt?:string; publishedUpdateAvailable:boolean }
export interface RealtimeExecution { id:number; jobId:number; definitionVersion:number; engineJobId?:string; runtimeRevision?:string; status:string; errorMessage?:string; startedAt?:string; finishedAt?:string }
export interface RealtimeRuntime { job:RealtimeJob; execution?:RealtimeExecution; environment?:FlinkEnvironment }
export interface RealtimePreCheckItem { level:'ERROR'|'WARNING'|'INFO'; code:string; message:string; detail:string; blocking:boolean }
export const realtimeApi = {
  list: () => api.get<RealtimeJob[]>('/realtime/jobs'),
  get: (id:number) => api.get<RealtimeJob>(`/realtime/jobs/${id}`),
  create: (payload:{name:string;description?:string;runtimeEnvironmentId?:number;spec:Record<string,unknown>}) => api.post<RealtimeJob>('/realtime/jobs',payload),
  draft: (id:number,payload:{name:string;description?:string;runtimeEnvironmentId?:number;spec:Record<string,unknown>}) => api.put<RealtimeJob>(`/realtime/jobs/${id}/draft`,payload),
  validate: (id:number) => api.post<{valid:boolean;message:string;warnings:string[];yamlPreview:string;items:RealtimePreCheckItem[]}>(`/realtime/jobs/${id}/validate`),
  publish: (id:number) => api.post<RealtimeJob>(`/realtime/jobs/${id}/publish`),
  start: (id:number) => api.post<RealtimeRuntime>(`/realtime/jobs/${id}/start`),
  stop: (id:number) => api.post<RealtimeRuntime>(`/realtime/jobs/${id}/stop`),
  restart: (id:number) => api.post<RealtimeRuntime>(`/realtime/jobs/${id}/restart`),
  runtime: (id:number) => api.get<RealtimeRuntime>(`/realtime/jobs/${id}/runtime`),
  checkpoints: (id:number) => api.get<unknown>(`/realtime/jobs/${id}/checkpoints`),
  metrics: (id:number) => api.get<unknown>(`/realtime/jobs/${id}/metrics`),
  logs: (id:number) => api.get<unknown>(`/realtime/jobs/${id}/logs`),
  yaml: (id:number) => api.get<string>(`/realtime/jobs/${id}/yaml`)
}
export const flinkApi = {
  list: () => api.get<FlinkEnvironment[]>('/flink/environments'),
  create: (payload:Record<string,unknown>) => api.post<FlinkEnvironment>('/flink/environments',payload),
  update: (id:number,payload:Record<string,unknown>) => api.put<FlinkEnvironment>(`/flink/environments/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/flink/environments/${id}`),
  test: (id:number) => api.post<unknown>(`/flink/environments/${id}/test`)
}

export interface ReleasePolicy { id:number; policyKey:string; approvalRequired:boolean; updatedBy:string; updatedAt:string }
export interface ReleaseRequestView { id:number; resourceType:string; resourceId:number; resourceName?:string; requestedVersion?:number; status:string; requestedBy:string; reviewedBy?:string; reviewComment?:string; requestedAt:string; reviewedAt?:string }
export interface ReleaseRecord { id:number; requestId?:number; resourceType:string; resourceId:number; resourceName?:string; releasedVersion?:number; resultStatus:string; detail?:string; operatorName:string; releasedAt:string }
export const releaseApi = {
  policy: () => api.get<ReleasePolicy>('/release/policy'),
  updatePolicy: (approvalRequired:boolean) => api.put<ReleasePolicy>('/release/policy',{approvalRequired}),
  requests: (status?:string) => api.get<ReleaseRequestView[]>('/release/requests',{params:status?{status}:undefined}),
  request: (payload:{resourceType:string;resourceId:number;resourceName?:string;requestedVersion?:number}) => api.post<ReleaseRequestView>('/release/requests',payload),
  approve: (id:number,comment='') => api.post<ReleaseRequestView>(`/release/requests/${id}/approve`,{comment}),
  reject: (id:number,comment='') => api.post<ReleaseRequestView>(`/release/requests/${id}/reject`,{comment}),
  records: () => api.get<ReleaseRecord[]>('/release/records')
}

export interface MetricOverview { total:number; certified:number; draft:number; dimensions:number; lineageRelations:number }
export interface DimensionView { id:number; dimensionCode:string; dimensionName:string; description?:string; sourceDataSourceId?:number; sourceDatabase?:string; sourceTable?:string; sourceField?:string; ownerName?:string }
export interface MetricView { id:number; metricCode:string; metricName:string; metricType:string; description?:string; businessDomain?:string; ownerName?:string; status:string; currentVersion:number; sourceDataSourceId?:number; sourceDatabase?:string; sourceTable?:string; sourceField?:string; aggregation?:string; filterExpression?:string; timeField?:string; expressionText?:string; dimensions:DimensionView[]; updatedAt?:string }
export interface MetricLineage { id:number; metricId:number; metricCode:string; metricName:string; upstreamType:string; upstreamRef:string; downstreamType?:string; downstreamRef?:string }
export const metricApi = {
  overview: () => api.get<MetricOverview>('/metrics/overview'),
  list: () => api.get<MetricView[]>('/metrics'),
  create: (payload:Record<string,unknown>) => api.post<MetricView>('/metrics',payload),
  update: (id:number,payload:Record<string,unknown>) => api.put<MetricView>(`/metrics/${id}`,payload),
  remove: (id:number) => api.delete<void>(`/metrics/${id}`),
  certify: (id:number) => api.post<MetricView>(`/metrics/${id}/certify`),
  uncertify: (id:number) => api.post<MetricView>(`/metrics/${id}/uncertify`),
  dimensions: () => api.get<DimensionView[]>('/metrics/dimensions'),
  createDimension: (payload:Record<string,unknown>) => api.post<DimensionView>('/metrics/dimensions',payload),
  updateDimension: (id:number,payload:Record<string,unknown>) => api.put<DimensionView>(`/metrics/dimensions/${id}`,payload),
  removeDimension: (id:number) => api.delete<void>(`/metrics/dimensions/${id}`),
  lineage: () => api.get<MetricLineage[]>('/metrics/lineage')
}

export interface AssetItem { type:string; ref:string; name:string; description?:string; owner?:string; status:string; source?:string; detail?:string; favorite:boolean }
export interface DatasetView { id:number; datasetCode:string; datasetName:string; description?:string; sourceDataSourceId?:number; sourceDatabase?:string; sourceTable?:string; ownerName?:string; status:string }
export const assetApi = {
  catalog: () => api.get<AssetItem[]>('/assets/catalog'),
  favorites: () => api.get<AssetItem[]>('/assets/favorites'),
  favorite: (assetType:string,assetRef:string) => api.post<void>('/assets/favorites',{assetType,assetRef}),
  unfavorite: (assetRef:string) => api.delete<void>('/assets/favorites',{params:{assetRef}}),
  datasets: () => api.get<DatasetView[]>('/assets/datasets'),
  createDataset: (payload:Record<string,unknown>) => api.post<DatasetView>('/assets/datasets',payload)
}

export type { DataSourceView }
