import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ProblemDetails, TokenResponse } from './types'

const API_BASE = '/api/v1'

/** access 令牌只存内存（D-07）：刷新页面靠 refresh Cookie 恢复。 */
let accessToken: string | null = null

export const tokenStore = {
  get: (): string | null => accessToken,
  set: (token: string | null): void => {
    accessToken = token
  }
}

let currentLocale = 'zh-CN'
export function setLocale(locale: string): void {
  currentLocale = locale
}

/** 非 /api/v1 前缀的请求（如 /actuator），同样携带凭据。 */
export const rawHttp = axios.create({
  withCredentials: true,
  timeout: 10_000
})

export const http = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  timeout: 20_000
})

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  config.headers['Accept-Language'] = currentLocale
  if (!config.headers['X-Request-Id']) {
    config.headers['X-Request-Id'] = crypto.randomUUID().replace(/-/g, '')
  }
  return config
})

/** 401 单飞刷新：同一时刻只发起一次 refresh，其余请求排队等待。 */
let refreshing: Promise<void> | null = null

async function refreshSession(): Promise<void> {
  const { data } = await axios.post<TokenResponse>(`${API_BASE}/auth/refresh`, null, {
    withCredentials: true,
    headers: { 'Accept-Language': currentLocale }
  })
  accessToken = data.accessToken
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetails>) => {
    const config = error.config as (AxiosRequestConfig & { __retried?: boolean }) | undefined
    const status = error.response?.status
    const isAuthCall = config?.url?.includes('/auth/login') || config?.url?.includes('/auth/refresh')

    if (status === 401 && config && !config.__retried && !isAuthCall) {
      config.__retried = true
      try {
        refreshing = refreshing ?? refreshSession().finally(() => {
          refreshing = null
        })
        await refreshing
        return http.request(config)
      } catch {
        accessToken = null
        if (typeof window !== 'undefined') {
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  }
)

/** 从任意错误中取出 Problem Details，供页面展示 detail 与 traceId。 */
export function problemOf(error: unknown): ProblemDetails | null {
  if (axios.isAxiosError<ProblemDetails>(error)) {
    return error.response?.data ?? null
  }
  return null
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID()
}


export async function downloadFile(path: string, filename: string, params?: Record<string, unknown>): Promise<void> {
  const response = await http.get<Blob>(path, { params, responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

