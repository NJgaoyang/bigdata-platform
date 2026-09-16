import { api } from './http'

export interface AuthSession { token:string; username:string; expiresAt:string }
export interface CurrentUser { username:string; displayName:string; authenticated:boolean; permissions:string[]; roleCode:string; superAdmin:boolean }

export const authApi = {
  login: (username:string,password:string) => api.post<AuthSession>('/auth/login',{username,password}),
  me: () => api.get<CurrentUser>('/auth/me'),
  logout: () => api.post<void>('/auth/logout')
}
