import axios, { type AxiosRequestConfig } from 'axios'

interface ApiEnvelope<T> {
  success: boolean
  data: T
  message: string
}

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('platform_auth_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(response => response, error => {
  if (error?.response?.status === 401 && window.location.pathname !== '/login') {
    localStorage.removeItem('platform_auth_token')
    const target = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.assign(`/login?redirect=${target}`)
  }
  const message = error?.response?.data?.message || error?.message || '请求失败'
  return Promise.reject(new Error(message))
})

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T>>(config)
  const envelope = response.data
  if (!envelope.success) throw new Error(envelope.message || '请求失败')
  return envelope.data
}

export const api = {
  get: <T>(url: string, config?: AxiosRequestConfig) => request<T>({ ...config, method: 'GET', url }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => request<T>({ ...config, method: 'POST', url, data }),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => request<T>({ ...config, method: 'PUT', url, data }),
  delete: <T>(url: string, config?: AxiosRequestConfig) => request<T>({ ...config, method: 'DELETE', url })
}
