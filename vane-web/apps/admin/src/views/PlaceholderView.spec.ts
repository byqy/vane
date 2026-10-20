import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/system/permission' })
}))

import PlaceholderView from './PlaceholderView.vue'

function mountPlaceholder(locale: 'zh-CN' | 'en') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'zh-CN', messages })
  return mount(PlaceholderView, { global: { plugins: [i18n] } })
}

describe('PlaceholderView', () => {
  it('中文提示包含未实现路由', () => {
    const text = mountPlaceholder('zh-CN').text()
    expect(text).toContain('开发中')
    expect(text).toContain('/system/permission')
  })

  it('英文提示走同一份文案资源', () => {
    const text = mountPlaceholder('en').text()
    expect(text).toContain('Under construction')
    expect(text).toContain('/system/permission')
  })
})
