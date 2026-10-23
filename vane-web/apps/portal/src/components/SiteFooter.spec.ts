import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

vi.mock('../seo', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../seo')>()
  return { ...actual, localizeHref: (locale: string, target: string | null) => (target ? `/${locale}${target}` : '#') }
})

import SiteFooter from './SiteFooter.vue'

function mountFooter() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(SiteFooter, { global: { plugins: [i18n] } })
}

describe('portal SiteFooter', () => {
  it('渲染站点名、页脚导航与版权信息', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/public/site') {
        return Promise.resolve({
          data: {
            siteName: '示例公司',
            description: '',
            baseUrl: 'https://example.com',
            ogImage: '',
            locales: ['zh-CN'],
            defaultLocale: 'zh-CN'
          }
        })
      }
      return Promise.resolve({
        data: [
          { id: 'f1', label: '公司介绍', labelKey: 'nav.about', target: '/about', openInNewTab: false, children: [] }
        ]
      })
    })

    const wrapper = mountFooter()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/public/navs', { params: { position: 'FOOTER' } })
    expect(wrapper.text()).toContain('示例公司')
    expect(wrapper.text()).toContain('快速导航')
    expect(wrapper.text()).toContain('公司介绍')
    expect(wrapper.find('a[href="/zh-CN/about"]').exists()).toBe(true)
    expect(wrapper.text()).toContain(`© ${new Date().getFullYear()} 示例公司`)
    wrapper.unmount()
  })

  it('label 留空时回退 labelKey 的翻译文案', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/public/site') {
        return Promise.reject(new Error('boom'))
      }
      return Promise.resolve({
        data: [{ id: 'f2', label: null, labelKey: 'nav.about', target: '/about', openInNewTab: false, children: [] }]
      })
    })

    const wrapper = mountFooter()
    await flushPromises()

    // 站点名接口失败时回退到 portal.site，页脚仍然可用
    expect(wrapper.text()).toContain('关于我们')
    expect(wrapper.find('.brand-name').text()).toBe('vane 门户')
    expect(wrapper.text()).toContain(`© ${new Date().getFullYear()} vane 门户`)
    wrapper.unmount()
  })

  it('页脚导航接口失败时不渲染导航列表但保留版权', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const wrapper = mountFooter()
    await flushPromises()

    expect(wrapper.find('.links').exists()).toBe(false)
    expect(wrapper.text()).toContain('版权所有')
    wrapper.unmount()
  })
})
