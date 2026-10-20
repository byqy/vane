import { describe, expect, it, vi } from 'vitest'
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { createAuthGuard, type GuardDeps } from './router-guard'

type GuardResult = boolean | RouteLocationRaw

function route(name: string | undefined, fullPath = '/system/user', isPublic = false) {
  return { name, fullPath, meta: isPublic ? { public: true } : {} } as unknown as RouteLocationNormalized
}

function deps(overrides: Partial<GuardDeps> = {}): GuardDeps {
  return {
    tokenStore: { get: () => null, set: () => undefined },
    refresh: async () => 'access-token',
    getMe: () => null,
    loadMe: async () => undefined,
    ...overrides
  }
}

describe('createAuthGuard', () => {

  it('公开页（登录页）无需令牌直接放行', async () => {
    const guard = createAuthGuard(deps())
    const result = (await guard(route('login', '/login', true))) as GuardResult
    expect(result).toBe(true)
  })

  it('已登录再访问登录页：送回业务页', async () => {
    const guard = createAuthGuard(deps({ tokenStore: { get: () => 'token', set: () => undefined } }))
    const result = (await guard(route('login', '/login', true))) as RouteLocationRaw
    expect(result).toMatchObject({ name: 'users' })
  })

  it('无令牌且 refresh 失败：跳登录页并带 redirect', async () => {
    const guard = createAuthGuard(
      deps({
        refresh: async () => {
          throw new Error('no cookie')
        }
      })
    )
    const result = (await guard(route('users', '/system/user'))) as RouteLocationRaw
    expect(result).toMatchObject({ name: 'login', query: { redirect: '/system/user' } })
  })

  it('无令牌但 refresh 成功：恢复会话后放行', async () => {
    let token: string | null = null
    const guard = createAuthGuard(
      deps({
        tokenStore: {
          get: () => token,
          set: (value) => {
            token = value
          }
        },
        getMe: () => ({ mustChangePassword: false })
      })
    )
    const result = (await guard(route('users'))) as GuardResult
    expect(result).toBe(true)
    expect(token).toBe('access-token')
  })

  it('必须改密时，访问其他页面一律跳改密页', async () => {
    const guard = createAuthGuard(
      deps({
        tokenStore: { get: () => 'token', set: () => undefined },
        getMe: () => ({ mustChangePassword: true })
      })
    )

    expect((await guard(route('users'))) as RouteLocationRaw).toMatchObject({ name: 'changePassword' })
    // 改密页自身放行，避免重定向死循环
    expect((await guard(route('changePassword', '/change-password'))) as GuardResult).toBe(true)
  })

  it('已完成改密的用户访问改密页时跳回业务页', async () => {
    const guard = createAuthGuard(
      deps({
        tokenStore: { get: () => 'token', set: () => undefined },
        getMe: () => ({ mustChangePassword: false })
      })
    )
    expect((await guard(route('changePassword', '/change-password'))) as RouteLocationRaw).toMatchObject({
      name: 'users'
    })
  })

  it('加载用户信息失败：清空令牌并跳登录页', async () => {
    let token: string | null = 'token'
    const guard = createAuthGuard(
      deps({
        tokenStore: {
          get: () => token,
          set: (value) => {
            token = value
          }
        },
        getMe: () => null,
        loadMe: async () => {
          throw new Error('401')
        }
      })
    )
    const result = (await guard(route('users', '/system/role'))) as RouteLocationRaw
    expect(result).toMatchObject({ name: 'login', query: { redirect: '/system/role' } })
    expect(token).toBeNull()
  })

  it('首次导航只尝试一次 refresh，不重复请求', async () => {
    const refresh = vi.fn(async () => 'access-token')
    let token: string | null = null
    const guard = createAuthGuard(
      deps({
        tokenStore: {
          get: () => token,
          set: (value) => {
            token = value
          }
        },
        refresh,
        getMe: () => ({ mustChangePassword: false })
      })
    )

    await guard(route('users'))
    await guard(route('roles', '/system/role'))
    expect(refresh).toHaveBeenCalledTimes(1)
  })
})


