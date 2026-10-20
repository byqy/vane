import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, patchMock } = vi.hoisted(() => ({ getMock: vi.fn(), patchMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, patch: patchMock } }
})

import ConfigsView from './ConfigsView.vue'

const CONFIGS = [
  { key: 'security.password.min-length', valueJson: '6', valueType: 'NUMBER', description: '密码最小长度', editable: true },
  { key: 'site.name', valueJson: '"vane"', valueType: 'STRING', description: '站点名', editable: true },
  { key: 'system.build.version', valueJson: '"1.0"', valueType: 'STRING', description: '构建版本（只读）', editable: false }
]

function mountConfigs() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ConfigsView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountConfigs>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

function dialogTextarea(): HTMLTextAreaElement {
  return document.querySelector('.el-dialog textarea') as HTMLTextAreaElement
}

describe('ConfigsView', () => {
  it('渲染参数列表', async () => {
    getMock.mockResolvedValue({ data: CONFIGS })
    const wrapper = mountConfigs()
    await flushPromises()

    expect(wrapper.text()).toContain('security.password.min-length')
    expect(wrapper.text()).toContain('密码最小长度')
    wrapper.unmount()
  })

  it('按关键词过滤参数（匹配 key 或说明）', async () => {
    getMock.mockResolvedValue({ data: CONFIGS })
    const wrapper = mountConfigs()
    await flushPromises()
    expect(wrapper.text()).toContain('site.name')

    const searchInput = wrapper.find('input')
    await searchInput.setValue('min-length')
    await flushPromises()
    expect(wrapper.text()).toContain('security.password.min-length')
    expect(wrapper.text()).not.toContain('site.name')

    await searchInput.setValue('构建版本')
    await flushPromises()
    expect(wrapper.text()).toContain('system.build.version')
    expect(wrapper.text()).not.toContain('security.password.min-length')
    wrapper.unmount()
  })

  it('勾选「仅看可编辑」时过滤只读参数', async () => {
    getMock.mockResolvedValue({ data: CONFIGS })
    const wrapper = mountConfigs()
    await flushPromises()

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await flushPromises()

    expect(wrapper.text()).not.toContain('system.build.version')
    expect(wrapper.text()).toContain('site.name')
    wrapper.unmount()
  })

  it('编辑参数前预填 JSON 值，提交走 PATCH', async () => {
    getMock.mockResolvedValue({ data: CONFIGS })
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountConfigs()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    const textarea = dialogTextarea()
    expect(textarea.value).toBe('6')
    textarea.value = '8'
    textarea.dispatchEvent(new Event('input'))
    await flushPromises()
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledWith('/admin/configs/security.password.min-length', {
      valueJson: '8'
    })
    wrapper.unmount()
  })
})

