import { api } from './http'

export interface TaskSummary { total: number; running: number; success: number; failed: number; pending: number }
export interface PlatformStatus { status: string; mode: string; onlineDataSources: number; totalDataSources: number; schedulerAvailable: boolean }
export interface RecentTask { id: string; name: string; type: string; status: string; startedAt?: string; finishedAt?: string; detail?: string }
export interface SourceItem {
  id: number; name: string; type: string; status: string; host: string; port: number; databaseName: string;
  username: string; metadataVisible: boolean; healthy: boolean; lastCheckedAt?: string; lastCheckMessage?: string
}
export interface SourceDashboard { total: number; healthy: number; unhealthy: number; unchecked: number; metadataVisible: number; typeDistribution: Record<string, number>; items: SourceItem[] }
export interface WorkbenchOverview {
  platform: PlatformStatus
  tasks: TaskSummary
  sources: SourceDashboard
  recentTasks: RecentTask[]
  generatedAt: string
}
export interface WorkbenchSummary {
  issues:number; running:number; unpublished:number; successRate24h?:number; successful24h:number; failed24h:number; unhealthySources:number; generatedAt:string
}
export interface WorkbenchIssue { id:string; type:string; name:string; status:string; detail:string; occurredAt?:string; path:string }
export interface WorkbenchRun { id:string; name:string; type:string; status:string; startedAt?:string; finishedAt?:string; detail?:string; path:string; sortTime?:string }
export interface TrendPoint { date: string; count: number; success: number; failed: number; running: number }
export interface IntegrationSummary {
  taskTotal: number
  total: number
  running: number
  success: number
  failed: number
  pending: number
  sourceDistribution: Record<string, number>
  trend: TrendPoint[]
  generatedAt: string
}
export interface DataSourceView {
  id: number
  name: string
  type: 'MYSQL' | 'STARROCKS'
  host: string
  port: number
  databaseName: string
  timezone: string
  username: string
  status: string
  metadataVisible: boolean
  lastCheckedAt?: string
  lastCheckMessage?: string
}
export interface DataSourcePayload {
  name: string
  type: 'MYSQL' | 'STARROCKS'
  host: string
  port: number
  databaseName: string
  timezone: string
  username: string
  password?: string
  metadataVisible: boolean
}

export const dashboardApi = {
  overview: () => api.get<WorkbenchOverview>('/dashboard/overview'),
  integration: () => api.get<IntegrationSummary>('/dashboard/integration')
}
export const workbenchApi = {
  summary: () => api.get<WorkbenchSummary>('/workbench/summary'),
  issues: () => api.get<WorkbenchIssue[]>('/workbench/issues'),
  recentRuns: () => api.get<WorkbenchRun[]>('/workbench/recent-runs')
}
export const dataSourceApi = {
  list: () => api.get<DataSourceView[]>('/data-sources'),
  create: (payload: DataSourcePayload) => api.post<DataSourceView>('/data-sources', payload),
  update: (id: number, payload: DataSourcePayload) => api.put<DataSourceView>(`/data-sources/${id}`, payload),
  remove: (id: number) => api.delete<void>(`/data-sources/${id}`),
  test: (id: number) => api.post<unknown>(`/data-sources/${id}/test`),
  setMetadataVisible: (id:number, visible:boolean) => api.put<DataSourceView>(`/data-sources/${id}/metadata-visibility`, { visible })
}
