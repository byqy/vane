import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'
import HelpTip from './HelpTip.vue'

const M = messages['zh-CN'] as Record<string, string>

function mountTip(locale: 'zh-CN' | 'en' = 'zh-CN') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'zh-CN', messages })
  return mount(HelpTip, {
    attachTo: document.body,
    props: { page: 'navs', title: locale === 'zh-CN' ? '导航管理' : 'Navigation' },
    global: { plugins: [i18n] }
  })
}

function drawerText(): string {
  return document.body.textContent ?? ''
}

function drawerItems(): string[] {
  return Array.from(document.querySelectorAll('.el-drawer li')).map((item) => (item.textContent ?? '').trim())
}

describe('HelpTip', () => {
  it('渲染问号按钮且可访问名称来自文案', () => {
    const wrapper = mountTip()
    const button = wrapper.get('button.help-tip')
    expect(button.text()).toBe('?')
    expect(button.attributes('aria-label')).toBe(M['help.open'])
    expect(button.attributes('title')).toBe(M['help.open'])
    wrapper.unmount()
  })

  it('默认不展开抽屉', () => {
    const wrapper = mountTip()
    // el-drawer 关闭时只保留容器，正文不渲染
    expect(document.querySelector('.el-drawer__body')).toBeNull()
    expect(drawerText()).not.toContain(M['help.what'])
    wrapper.unmount()
  })

  it('点击后展开抽屉，展示用途与分条用法', async () => {
    const wrapper = mountTip()
    await wrapper.get('button.help-tip').trigger('click')
    await flushPromises()

    expect(document.querySelector('.el-drawer__body')).not.toBeNull()
    expect(drawerText()).toContain('使用帮助 · 导航管理')
    expect(drawerText()).toContain(M['help.navs.what'])
    expect(drawerText()).toContain(M['help.what'])
    expect(drawerText()).toContain(M['help.steps'])

    const items = drawerItems()
    expect(items.length).toBeGreaterThanOrEqual(2)
    expect(items.every((item) => item.length > 0 && !item.startsWith('·'))).toBe(true)
    wrapper.unmount()
  })

  it('切换到英文后展示英文帮助文案', async () => {
    const wrapper = mountTip('en')
    await wrapper.get('button.help-tip').trigger('click')
    await flushPromises()

    const text = drawerText()
    expect(text).toContain('Help · Navigation')
    expect(text).toContain(messages.en['help.navs.what'])
    expect(text).not.toContain(M['help.navs.what'])
    wrapper.unmount()
  })
})
