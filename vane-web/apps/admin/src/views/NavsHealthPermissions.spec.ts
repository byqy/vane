import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  deleteMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, patch: vi.fn(), delete: deleteMock } }
})

import NavsView from './NavsView.vue'
import HealthView from './HealthView.vue'
import PermissionsView from './PermissionsView.vue'

function mountView(component: typeof NavsView | typeof HealthView | typeof PermissionsView) {
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

describe('NavsView', () => {
  const NAVS = [
    { id: 'n1', parentId: null, position: 'HEADER', labelKey: 'nav.home', target: '/', openInNewTab: false, sortNo: 1, status: 'ACTIVE', children: [] }
  ]

  it('渲染导航树', async () => {
    getMock.mockResolvedValue({ data: NAVS })
    const wrapper = mountView(NavsView)
    await flushPromises()

    expect(wrapper.text()).toContain('nav.home')
    wrapper.unmount()
  })

  it('新建导航提交位置与标签 key', async () => {
    getMock.mockResolvedValue({ data: NAVS })
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountView(NavsView)
    await flushPromises()
    await buttonByText(wrapper, '新建')!.trigger('click')
    await flushPromises()

    const inputs = dialogInputs()
    await setInputValue(inputs[0], 'nav.about')
    await setInputValue(inputs[1], '/about')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]
    expect(url).toBe('/admin/navs')
    expect(payload.labelKey).toBe('nav.about')
    expect(payload.position).toBe('HEADER')
    wrapper.unmount()
  })
})

describe('HealthView', () => {
  it('展示健康检查聚合结果', async () => {
    getMock.mockResolvedValue({
      data: {
        status: 'UP',
        checks: { db: 'UP', valkey: 'DOWN' },
        checkedAt: '2026-10-19T02:00:00Z'
      }
    })
    const wrapper = mountView(HealthView)
    await flushPromises()

    expect(wrapper.text()).toContain('UP')
    expect(wrapper.text()).toContain('db')
    expect(wrapper.text()).toContain('valkey')
    wrapper.unmount()
  })
})

describe('PermissionsView', () => {
  it('渲染权限点树', async () => {
    getMock.mockResolvedValue({
      data: [
        { id: 'p1', code: 'system:user', type: 'MENU', nameKey: 'menu.system.user', path: '/system/user', children: [] }
      ]
    })
    const wrapper = mountView(PermissionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('system:user')
    wrapper.unmount()
  })
})

