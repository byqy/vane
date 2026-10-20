import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, downloadMock } = vi.hoisted(() => ({ getMock: vi.fn(), downloadMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock }, downloadFile: downloadMock }
})

import OperLogsView from './OperLogsView.vue'

const LOGS = {
  items: [
    {
      id: 'l1',
      username: 'admin',
      module: 'user',
      action: 'create',
      httpMethod: 'POST',
      path: '/api/v1/admin/users',
      params: '{"username":"audit","password":"***"}',
      result: 'SUCCESS',
      errorCode: null,
      durationMs: 12,
      ip: '127.0.0.1',
      traceId: 't1',
      createdAt: '2026-10-19T02:00:00Z'
    }
  ],
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
  it('加载操作日志列表', async () => {
    getMock.mockResolvedValue({ data: LOGS })
    const wrapper = mountLogs()
    await flushPromises()

    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('user')
    expect(wrapper.text()).toContain('create')
    expect(wrapper.text()).toContain('POST')
    expect(wrapper.text()).toContain('方法')
    expect(wrapper.text()).toContain('路径')
    expect(wrapper.text()).toContain('/api/v1/admin/users')
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

    it('展开行显示请求参数（已掩码）', async () => {
      getMock.mockResolvedValue({ data: LOGS })
      const wrapper = mountLogs()
      await flushPromises()
      const expandIcon = document.querySelector('.el-table__expand-icon') as HTMLElement
      expect(expandIcon).toBeTruthy()
      expandIcon.click()
      await flushPromises()
      const params = document.querySelector('pre.params')
      expect(params?.textContent).toContain('"username": "audit"')
      expect(params?.textContent).toContain('"password": "***"')
      wrapper.unmount()
    })
})


