import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, downloadMock } = vi.hoisted(() => ({ getMock: vi.fn(), downloadMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock }, downloadFile: downloadMock }
})

import LoginLogsView from './LoginLogsView.vue'

const M = messages['zh-CN'] as Record<string, string>

const PAGE = {
  items: [
    {
      id: 'l1',
      username: 'admin',
      result: 'SUCCESS',
      detail: '登录成功',
      ip: '127.0.0.1',
      traceId: 't1',
      createdAt: '2026-10-19T14:00:00Z'
    }
  ],
  page: { number: 0, size: 20, total: 1 }
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(LoginLogsView, { global: { plugins: [i18n] } })
}

describe('LoginLogsView', () => {
  it('渲染登录日志与总数', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('SUCCESS')
    expect(wrapper.text()).toContain('登录成功')
    wrapper.unmount()
  })

  it('按用户名搜索会带过滤参数并重置到第一页', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input').setValue('admin')
    await wrapper.findAll('button').find((button) => button.text().includes(M['common.search']))!.trigger('click')
    await flushPromises()

    const params = getMock.mock.calls.at(-1)![1].params
    expect(params['page[number]']).toBe(0)
    expect(params.username).toBe('admin')
    wrapper.unmount()
  })

  it('导出 CSV 携带当前过滤条件', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    downloadMock.mockResolvedValue(undefined)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('input').setValue('admin')
    await wrapper.findAll('button').find((button) => button.text().includes(M['common.export']))!.trigger('click')
    await flushPromises()

    expect(downloadMock).toHaveBeenCalledWith('/admin/login-logs/export', 'login-logs.csv', { username: 'admin', result: undefined })
    wrapper.unmount()
  })

  it('加载失败时显示空态', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain(M['logs.login.title'])
    wrapper.unmount()
  })
})
