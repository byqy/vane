import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'

export interface GuardDeps {
  /** access 令牌（只存内存，刷新页面靠 refresh Cookie 恢复）。 */
  tokenStore: {
    get: () => string | null
    set: (token: string | null) => void
  }
  /** 用 refresh Cookie 换新 access；失败表示会话不存在。 */
  refresh: () => Promise<string>
  /** 当前用户信息（含 mustChangePassword）。 */
  getMe: () => { mustChangePassword?: boolean } | null
  loadMe: () => Promise<unknown>
}

/**
 * 路由守卫：
 * 1. 公开页直接放行；
 * 2. 首次导航尝试用 refresh Cookie 恢复会话；
 * 3. 无令牌 → 登录页（带 redirect）；
 * 4. 首登/重置后强制改密：其他页面一律跳改密页；已改密的用户访问改密页跳用户页。
 */
export function createAuthGuard(deps: GuardDeps) {
  let bootstrapped = false

  return async (to: RouteLocationNormalized): Promise<boolean | RouteLocationRaw> => {
    if (to.meta.public) {
      // 已登录还去登录页：直接送回业务页，避免重复登录
      if (to.name === 'login' && deps.tokenStore.get()) {
        return { name: 'users' }
      }
      return true
    }

    if (!bootstrapped) {
      bootstrapped = true
      if (!deps.tokenStore.get()) {
        try {
          deps.tokenStore.set(await deps.refresh())
        } catch {
          return { name: 'login', query: { redirect: to.fullPath } }
        }
      }
    }

    if (!deps.tokenStore.get()) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }

    if (!deps.getMe()) {
      try {
        await deps.loadMe()
      } catch {
        deps.tokenStore.set(null)
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }

    const mustChange = deps.getMe()?.mustChangePassword === true
    if (mustChange && to.name !== 'changePassword') {
      return { name: 'changePassword' }
    }
    if (!mustChange && to.name === 'changePassword') {
      return { name: 'users' }
    }
    return true
  }
}

