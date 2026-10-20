import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, downloadMock, postMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  downloadMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return {
    ...actual,
    http: { get: getMock, post: postMock, patch: patchMock, delete: deleteMock },
    downloadFile: downloadMock
  }
})

import LoginLogsView from './LoginLogsView.vue'
import DeptsView from './DeptsView.vue'

const LOGS = {
  items: [
    {
      id: 'l1',
      userId: 'u1',
      username: 'admin',
      result: 'SUCCESS',
      detail: null,
      ip: '127.0.0.1',
      traceId: 't1',
      createdAt: '2026-10-19T02:00:00Z'
    }
  ],
  page: { number: 0, size: 20, total: 1 }
}

const TREE = [{ id: 'd1', code: 'HQ', name: '总部', parentId: null, sortNo: 1, status: 'ACTIVE', children: [] }]

function mountView(component: typeof LoginLogsView | typeof DeptsView) {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(component, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function dialogInputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll('.el-dialog input')) as HTMLInputElement[]
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('LoginLogsView', () => {
  it('加载登录日志并支持导出', async () => {
    getMock.mockResolvedValue({ data: LOGS })
    downloadMock.mockResolvedValue(undefined)

    const wrapper = mountView(LoginLogsView)
    await flushPromises()

    expect(wrapper.text()).toContain('admin')
    await buttonByText(wrapper, '导出')!.trigger('click')
    await flushPromises()

    expect(downloadMock).toHaveBeenCalledWith('/admin/login-logs/export', 'login-logs.csv', {
      username: undefined,
      result: undefined
    })
    wrapper.unmount()
  })
})

describe('DeptsView', () => {
  it('渲染组织树', async () => {
    getMock.mockResolvedValue({ data: TREE })
    const wrapper = mountView(DeptsView)
    await flushPromises()

    expect(wrapper.text()).toContain('总部')
    wrapper.unmount()
  })

  it('新建组织提交 code、name 与上级', async () => {
    getMock.mockResolvedValue({ data: TREE })
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountView(DeptsView)
    await flushPromises()
    await buttonByText(wrapper, '新建')!.trigger('click')
    await flushPromises()

    const inputs = dialogInputs()
    await setInputValue(inputs[0], 'BRANCH')
    await setInputValue(inputs[1], '分公司')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]
    expect(url).toBe('/admin/depts')
    expect(payload.code).toBe('BRANCH')
    expect(payload.name).toBe('分公司')
    wrapper.unmount()
  })
})
