import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, seoMock } = vi.hoisted(() => ({ getMock: vi.fn(), seoMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

vi.mock('../seo', () => ({
  applyContactSeo: seoMock,
  applyHomeSeo: vi.fn(),
  applyAboutSeo: vi.fn(),
  applyProductsSeo: vi.fn(),
  applyProductSeo: vi.fn(),
  applyPageSeo: vi.fn(),
  applyNotFoundSeo: vi.fn(),
  localizeHref: (_locale: string, target: string | null) => target ?? '#'
}))

import ContactView from './ContactView.vue'

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ContactView, { global: { plugins: [i18n] } })
}

describe('portal ContactView', () => {
  it('展示站点联系方式，缺失字段降级为占位文案', async () => {
    getMock.mockResolvedValue({
      data: {
        siteName: '示例公司',
        description: '',
        baseUrl: 'https://example.com',
        ogImage: '',
        locales: ['zh-CN'],
        defaultLocale: 'zh-CN',
        contactEmail: 'hi@example.com',
        contactPhone: '010-12345678',
        contactAddress: '',
        contactHours: '  '
      }
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('联系我们')
    expect(wrapper.text()).toContain('邮箱')
    expect(wrapper.text()).toContain('hi@example.com')
    expect(wrapper.text()).toContain('电话')
    expect(wrapper.text()).toContain('010-12345678')
    expect(wrapper.text()).toContain('地址')
    expect(wrapper.text()).toContain('服务时间')
    // 地址与服务时间未下发：降级为占位文案，页面不崩
    expect(wrapper.findAll('.contact-value--empty')).toHaveLength(2)
    expect(seoMock).toHaveBeenCalledWith('zh-CN')
    wrapper.unmount()
  })

  it('接口失败展示错误态，重试后恢复', async () => {
    getMock
      .mockRejectedValueOnce({ isAxiosError: true, response: { status: 500, data: { status: 500, detail: '服务异常', traceId: 'trace-3' } } })
      .mockResolvedValueOnce({ data: { siteName: '示例公司', contactEmail: 'hi@example.com' } })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('服务异常')
    expect(wrapper.text()).toContain('trace-3')

    await wrapper.get('.state__retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('hi@example.com')
    wrapper.unmount()
  })
})
