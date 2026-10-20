import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import UsersView from './UsersView.vue'

const USERS = {
  items: [
    {
      id: 'u1',
      username: 'alice',
      nickname: 'Alice',
      deptId: 'd1',
      deptName: '总部',
      status: 'ACTIVE',
      roles: ['SUPER_ADMIN'],
      createdAt: '2026-01-01T00:00:00Z',
      lastLoginAt: '2026-10-18T10:00:00Z',
      version: 0
    },
    {
      id: 'u2',
      username: 'bob',
      nickname: 'Bob',
      deptId: 'd1',
      deptName: '总部',
      status: 'DISABLED',
      roles: [],
      createdAt: null,
      lastLoginAt: null,
      version: 2
    }
  ],
  page: { number: 0, size: 20, total: 2 }
}

function routeGet(url: string) {
  if (url === '/admin/depts/tree') {
    return Promise.resolve({ data: [{ id: 'd1', name: '总部', children: [] }] })
  }
  if (url === '/admin/roles') {
    return Promise.resolve({ data: { items: [{ id: 'r1', code: 'ADMIN', name: '管理员' }], page: { number: 0, size: 100, total: 1 } } })
  }
  return Promise.resolve({ data: USERS })
}

function mountUsers(locale: 'zh-CN' | 'en' = 'zh-CN') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'zh-CN', messages })
  return mount(UsersView, { global: { plugins: [i18n] } })
}

describe('UsersView', () => {
  it('加载成功后渲染用户行、组织与角色', async () => {
    getMock.mockImplementation(routeGet)

    const wrapper = mountUsers()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('alice')
    expect(text).toContain('bob')
    expect(text).toContain('总部')
    expect(text).toContain('SUPER_ADMIN')
    expect(text).toContain('2')
  })

  it('错误态展示 traceId 便于排查', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/users') {
        return Promise.reject({
          isAxiosError: true,
          response: {
            data: {
              type: '/problems/internal-error',
              title: '服务器内部错误',
              status: 500,
              detail: '服务器出错',
              code: 'SYS.INTERNAL_ERROR',
              traceId: 'trace-abc-123'
            }
          }
        })
      }
      return routeGet(url)
    })

    const wrapper = mountUsers()
    await flushPromises()

    expect(wrapper.text()).toContain('trace-abc-123')
  })

  it('空列表展示空态', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/users') {
        return Promise.resolve({ data: { items: [], page: { number: 0, size: 20, total: 0 } } })
      }
      return routeGet(url)
    })

    const wrapper = mountUsers()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无数据')
  })

  it('英文环境使用英文表头', async () => {
    getMock.mockImplementation(routeGet)

    const wrapper = mountUsers('en')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Users')
    expect(text).toContain('Username')
  })
})
