import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { http, newIdempotencyKey, problemOf, setLocale, tokenStore } from './http'

const PROBLEM = {
  type: '/problems/unauthorized',
  title: 'Unauthorized',
  status: 401,
  detail: '令牌无效',
  instance: '/api/v1/admin/users',
  code: 'AUTH.TOKEN.INVALID',
  traceId: 'abc123'
}

function ok(config: AxiosRequestConfig, data: unknown = { ok: true }): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config } as AxiosResponse
}

function unauthorized(config: AxiosRequestConfig): never {
  const response = {
    data: PROBLEM,
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config
  } as AxiosResponse
  throw new AxiosError('Request failed with status code 401', 'ERR_BAD_REQUEST', config as never, null, response)
}

beforeEach(() => {
  tokenStore.set(null)
  setLocale('zh-CN')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('请求头注入', () => {
  it('带上 Authorization、Accept-Language 与 X-Request-Id', async () => {
    tokenStore.set('token-1')
    setLocale('en')
    let captured: AxiosRequestConfig | null = null
    http.defaults.adapter = async (config) => {
      captured = config
      return ok(config)
    }

    await http.get('/admin/users')

    expect(captured).not.toBeNull()
    expect((captured as unknown as AxiosRequestConfig).headers?.Authorization).toBe('Bearer token-1')
    expect((captured as unknown as AxiosRequestConfig).headers?.['Accept-Language']).toBe('en')
    expect((captured as unknown as AxiosRequestConfig).headers?.['X-Request-Id']).toMatch(/^[0-9a-f]{32}$/)
  })

  it('未登录时不携带 Authorization', async () => {
    let captured: AxiosRequestConfig | null = null
    http.defaults.adapter = async (config) => {
      captured = config
      return ok(config)
    }

    await http.get('/public/notices')

    expect((captured as unknown as AxiosRequestConfig).headers?.Authorization).toBeUndefined()
  })
})

describe('401 单飞刷新', () => {
  it('刷新一次后重试原请求', async () => {
    tokenStore.set('expired')
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({ data: { accessToken: 'fresh' } } as never)
    let attempts = 0
    http.defaults.adapter = async (config) => {
      attempts += 1
      if (!(config as { __retried?: boolean }).__retried) {
        unauthorized(config)
      }
      return ok(config, { items: [] })
    }

    const response = await http.get('/admin/users')

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(attempts).toBe(2)
    expect(tokenStore.get()).toBe('fresh')
    expect(response.data).toEqual({ items: [] })
  })

  it('并发 401 只刷新一次（单飞）', async () => {
    tokenStore.set('expired')
    let refreshCount = 0
    vi.spyOn(axios, 'post').mockImplementation(async () => {
      refreshCount += 1
      await new Promise((resolve) => setTimeout(resolve, 10))
      return { data: { accessToken: 'fresh' } } as never
    })
    http.defaults.adapter = async (config) => {
      if (!(config as { __retried?: boolean }).__retried) {
        unauthorized(config)
      }
      return ok(config)
    }

    const results = await Promise.all([http.get('/admin/users'), http.get('/admin/roles')])

    expect(results).toHaveLength(2)
    expect(refreshCount).toBe(1)
  })

  it('登录接口的 401 不触发刷新', async () => {
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({ data: {} } as never)
    http.defaults.adapter = async (config) => unauthorized(config)

    await expect(http.post('/auth/login', { username: 'x' })).rejects.toBeInstanceOf(AxiosError)
    expect(refresh).not.toHaveBeenCalled()
  })
})

describe('problemOf', () => {
  it('从 AxiosError 中取出 Problem Details', async () => {
    http.defaults.adapter = async (config) => unauthorized(config)

    const error = await http.get('/admin/users').catch((e: unknown) => e)

    expect(problemOf(error)).toMatchObject({ code: 'AUTH.TOKEN.INVALID', traceId: 'abc123', status: 401 })
  })

  it('普通错误返回 null', () => {
    expect(problemOf(new Error('boom'))).toBeNull()
    expect(problemOf(undefined)).toBeNull()
  })
})

describe('newIdempotencyKey', () => {
  it('生成合法 UUID', () => {
    expect(newIdempotencyKey()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)
  })

  it('每次不同', () => {
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey())
  })
})
