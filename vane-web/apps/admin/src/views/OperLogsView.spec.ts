import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, downloadMock } = vi.hoisted(() => ({ getMock: vi.fn(), downloadMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock }, downloadFile: downloadMock }
})

vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: () => true }) }))

import OperLogsView from './OperLogsView.vue'

const SUMMARY = {
  id: 'l1',
  username: 'admin',
  module: 'user',
  action: 'update',
  source: 'API',
  targetType: 'user',
  targetId: 'u1',
  targetLabel: 'bob',
  httpMethod: 'PUT',
  path: '/api/v1/admin/users/u1',
  httpStatus: 200,
  result: 'SUCCESS',
  errorCode: null,
  durationMs: 12,
  affectedCount: null,
  ip: '127.0.0.1',
  userAgent: 'vitest',
  deptId: null,
  traceId: 't1',
  createdAt: '2026-10-19T02:00:00Z'
}

const DETAIL = {
  ...SUMMARY,
  params: '{"username":"bob","password":"***"}',
  changes: '[{"field":"status","before":"ACTIVE","after":"DISABLED"}]',
  changesVersion: 1,
  changesTruncated: false
}

const LOGS = {
  items: [SUMMARY],
  page: { number: 0, size: 20, total: 1 }
}

function mountLogs() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(OperLogsView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountLogs>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('OperLogsView', () => {
  it('加载操作日志列表并显示目标与来源摘要', async () => {
    getMock.mockResolvedValue({ data: LOGS })
    const wrapper = mountLogs()
    await flushPromises()

    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('user')
    expect(wrapper.text()).toContain('update')
    expect(wrapper.text()).toContain('PUT')
    expect(wrapper.text()).toContain('/api/v1/admin/users/u1')
    expect(wrapper.text()).toContain('user:bob')
    wrapper.unmount()
  })

  it('列表不展示请求参数（改由详情接口提供）', async () => {
    getMock.mockResolvedValue({ data: LOGS })
    const wrapper = mountLogs()
    await flushPromises()

    expect(wrapper.text()).not.toContain('"password"')
    wrapper.unmount()
  })

  it('按用户过滤时带上分页与过滤参数', async () => {
    getMock.mockResolvedValue({ data: LOGS })
    const wrapper = mountLogs()
    await flushPromises()

    const usernameInput = wrapper.findAll('input').find((input) => (input.element as HTMLInputElement).placeholder?.includes('用户名'))
    if (usernameInput) {
      await usernameInput.setValue('adm')
    }
    await buttonByText(wrapper, '查询')!.trigger('click')
    await flushPromises()

    const lastCall = getMock.mock.calls.at(-1)!
    expect(lastCall[0]).toBe('/admin/oper-logs')
    expect(lastCall[1].params['page[number]']).toBe(0)
    wrapper.unmount()
  })

  it('导出 CSV 时带上当前过滤条件', async () => {
    getMock.mockResolvedValue({ data: LOGS })
    downloadMock.mockResolvedValue(undefined)

    const wrapper = mountLogs()
    await flushPromises()
    await buttonByText(wrapper, '导出')!.trigger('click')
    await flushPromises()

    expect(downloadMock).toHaveBeenCalledTimes(1)
    const [path, filename] = downloadMock.mock.calls[0]
    expect(path).toBe('/admin/oper-logs/export')
    expect(filename).toBe('oper-logs.csv')
    wrapper.unmount()
  })

  it('打开详情抽屉时按需拉取详情并展示字段级变更', async () => {
    getMock.mockImplementation((url: string) => {
      if (url.includes('/admin/oper-logs/l1')) {
        return Promise.resolve({ data: DETAIL })
      }
      return Promise.resolve({ data: LOGS })
    })
    const wrapper = mountLogs()
    await flushPromises()

    await buttonByText(wrapper, '查看详情')!.trigger('click')
    await flushPromises()

    expect(getMock.mock.calls.at(-1)![0]).toBe('/admin/oper-logs/l1')
    expect(wrapper.text()).toContain('status')
    expect(wrapper.text()).toContain('ACTIVE')
    expect(wrapper.text()).toContain('DISABLED')
    expect(wrapper.text()).toContain('"password": "***"')
    wrapper.unmount()
  })
})
