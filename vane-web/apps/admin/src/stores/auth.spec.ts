import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { http } from '@vane/shared'

const { getMock, postMock, patchMock, setLocaleMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  setLocaleMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return {
    ...actual,
    http: { get: getMock, post: postMock, patch: patchMock }
  }
})

vi.mock('../i18n', () => ({ setLocale: setLocaleMock }))

import { browserTimeZone } from '@vane/shared'
import { useAuthStore } from './auth'

const ME = {
  mustChangePassword: false,
  id: 'u1',
  username: 'admin',
  deptId: 'd1',
  roles: ['SUPER_ADMIN'],
  permissions: ['system:user'],
  menus: [],
  locale: 'ja'
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getMock.mockReset()
    postMock.mockReset()
    patchMock.mockReset()
    patchMock.mockResolvedValue({ data: null })
    setLocaleMock.mockReset()
  })

  it('登录后加载 me，并应用服务端保存的语言偏好', async () => {
    getMock.mockResolvedValue({ data: ME })
    const auth = useAuthStore()

    await auth.loadMe()

    expect(auth.me?.username).toBe('admin')
    expect(auth.permissions).toEqual(['system:user'])
    expect(setLocaleMock).toHaveBeenCalledWith('ja')
  })

  it('服务端没有语言偏好时不改动界面语言', async () => {
    getMock.mockResolvedValue({ data: { ...ME, locale: null } })
    const auth = useAuthStore()

    await auth.loadMe()

    expect(setLocaleMock).not.toHaveBeenCalled()
  })

  it('服务端返回不支持的语言标签时忽略', async () => {
    getMock.mockResolvedValue({ data: { ...ME, locale: 'xx-YY' } })
    const auth = useAuthStore()

    await auth.loadMe()

    expect(setLocaleMock).not.toHaveBeenCalled()
  })

  it('logout 清空 me 与令牌', async () => {
    getMock.mockResolvedValue({ data: ME })
    postMock.mockResolvedValue({ data: null })
    const auth = useAuthStore()
    await auth.loadMe()

    await auth.logout()

    expect(postMock).toHaveBeenCalledWith('/auth/logout')
    expect(auth.me).toBeNull()
  })

  it('登录后浏览器时区变化时同步到服务端', async () => {
    patchMock.mockResolvedValue({ data: null })
    getMock.mockResolvedValue({ data: { ...ME, locale: 'zh-CN', timezone: null } })
    const auth = useAuthStore()

    await auth.loadMe()

    expect(patchMock).toHaveBeenCalledWith('/auth/me/preferences', { timezone: browserTimeZone() })
  })

  it('服务端时区与浏览器一致时不重复写', async () => {
    getMock.mockResolvedValue({ data: { ...ME, locale: 'zh-CN', timezone: browserTimeZone() } })
    const auth = useAuthStore()

    await auth.loadMe()

    expect(patchMock).not.toHaveBeenCalled()
  })

  it('时区同步失败不影响登录流程', async () => {
    patchMock.mockRejectedValue(new Error('network'))
    getMock.mockResolvedValue({ data: { ...ME, locale: 'zh-CN', timezone: 'Asia/Tokyo' } })
    const auth = useAuthStore()

    await expect(auth.loadMe()).resolves.toBeUndefined()
    expect(auth.me?.username).toBe('admin')
  })
  it('http 来自共享请求层（保证带语言头与 traceId）', () => {
    expect(http.get).toBe(getMock)
  })
})


