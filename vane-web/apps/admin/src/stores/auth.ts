import { defineStore } from 'pinia'
import { browserTimeZone, http, isLocale, tokenStore, type MeResponse, type TokenResponse } from '@vane/shared'
import { setLocale } from '../i18n'

interface AuthState {
  me: MeResponse | null
  loading: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({ me: null, loading: false }),
  getters: {
    permissions: (state): string[] => state.me?.permissions ?? [],
    menus: (state) => state.me?.menus ?? []
  },
  actions: {
    async login(username: string, password: string): Promise<void> {
      const { data } = await http.post<TokenResponse>('/auth/login', { username, password })
      tokenStore.set(data.accessToken)
      await this.loadMe()
    },
    async loadMe(): Promise<void> {
      this.loading = true
      try {
        const { data } = await http.get<MeResponse>('/auth/me')
        this.me = data
          // 服务端保存的语言偏好优先：登录后自动切到用户上次选择的语言
          if (data.locale && isLocale(data.locale)) {
            setLocale(data.locale)
          }
          // 浏览器时区变化时同步到服务端（异步场景用）；仅在不同才写
          const timezone = browserTimeZone()
          if (timezone && data.timezone !== timezone) {
            void http.patch('/auth/me/preferences', { timezone }).catch(() => undefined)
          }
      } finally {
        this.loading = false
      }
    },
    async logout(): Promise<void> {
      try {
        await http.post('/auth/logout')
      } finally {
        tokenStore.set(null)
        this.me = null
      }
    },
    can(permission: string): boolean {
      return this.permissions.includes(permission)
    }
  }
})



