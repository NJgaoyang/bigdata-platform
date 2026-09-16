import { api } from './http'

export interface PlatformUser { id:number; username:string; displayName:string; phone:string; roleCode:string; status:string; createdAt:string }
export interface PlatformRole { id:number; roleCode:string; roleName:string; permissions:string[] }
export interface DataSourcePermission { dataSourceId:number; dataSourceName:string; userId:number; username:string; displayName:string; permissionCode:'VIEW'|'QUERY'|'EDIT' }
export interface AuditLog { id:number; action:string; resourceType:string; resourceId?:number; detail?:string; operatorName:string; createdAt:string }
export interface AlertSetting { id:number; name:string; channelType:string; triggerEvent:string; webhookMasked:string; secretConfigured:boolean; keyword:string; customTemplate:string; enabled:boolean; createdAt?:string; updatedAt?:string }
export interface AlertSettingPayload { name:string; channelType:'DINGTALK'; triggerEvent:'FAILURE_ONLY'|'SUCCESS_AND_FAILURE'|'ALL'; webhook:string; secret:string; keyword:string; customTemplate:string; enabled:boolean }

export const accessApi = {
  users: () => api.get<PlatformUser[]>('/system/users'),
  roles: () => api.get<PlatformRole[]>('/system/roles'),
  auditLogs: () => api.get<AuditLog[]>('/system/audit-logs'),
  createUser: (payload:unknown) => api.post<PlatformUser>('/system/users', payload),
  updateUser: (id:number,payload:unknown) => api.put<PlatformUser>(`/system/users/${id}`, payload),
  createRole: (payload:{roleCode:string;roleName:string}) => api.post<PlatformRole>('/system/roles', payload),
  removeUser: (id:number) => api.delete<void>(`/system/users/${id}`),
  setUserStatus: (id:number, enabled:boolean) => api.post<PlatformUser>(`/system/users/${id}/${enabled?'enable':'disable'}`),
  resetUserPassword: (id:number,newPassword:string) => api.post<void>(`/system/users/${id}/reset-password`, { newPassword }),
  forceLogoutUser: (id:number) => api.post<void>(`/system/users/${id}/force-logout`),
  userPermissions: (id:number) => api.get<string[]>(`/system/users/${id}/permissions`),
  setUserPermissions: (id:number, permissions:string[]) => api.put<string[]>(`/system/users/${id}/permissions`, { permissions }),
  setRolePermissions: (id:number, permissions:string[]) => api.put<PlatformRole>(`/system/roles/${id}/permissions`, { permissions }),
  dataSourcePermissions: () => api.get<DataSourcePermission[]>('/system/data-source-permissions'),
  grantDataSource: (dataSourceId:number,userId:number,permissionCode:string) => api.post<string>(`/system/data-sources/${dataSourceId}/permissions`, { userId, permissionCode }),
  revokeDataSource: (binding:DataSourcePermission) => api.delete<void>(`/system/data-sources/${binding.dataSourceId}/permissions/${binding.userId}/${binding.permissionCode}`),
  alertSettings: () => api.get<AlertSetting[]>('/system/alert-settings'),
  createAlertSetting: (payload:AlertSettingPayload) => api.post<AlertSetting>('/system/alert-settings', payload),
  updateAlertSetting: (id:number,payload:AlertSettingPayload) => api.put<AlertSetting>(`/system/alert-settings/${id}`, payload),
  setAlertSettingEnabled: (id:number,enabled:boolean) => api.post<AlertSetting>(`/system/alert-settings/${id}/enabled`, { enabled }),
  testAlertSetting: (id:number) => api.post<string>(`/system/alert-settings/${id}/test`),
  deleteAlertSetting: (id:number) => api.delete<void>(`/system/alert-settings/${id}`)
}
