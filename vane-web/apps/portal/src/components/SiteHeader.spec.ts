import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import SiteHeader from './SiteHeader.vue'

const NAVS = [
  {
    id: 'n1',
    labelKey: 'nav.home',
    target: '/',
    openInNewTab: false,
    children: [
      { id: 'n2', labelKey: 'nav.about', target: '/about', openInNewTab: false, children: [] }
    ]
  }
]

function mountHeader() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return {
    i18n,
    wrapper: mount(SiteHeader, {
      global: {
        plugins: [i18n],
        components: { 'router-link': { props: ['to'], template: '<a :href="to"><slot /></a>' } }
      }
    })
  }
}

describe('portal SiteHeader', () => {
  it('渲染品牌链接、导航项与 20 种语言下拉', async () => {
    getMock.mockResolvedValue({ data: NAVS })
    const { wrapper } = mountHeader()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/public/navs', { params: { position: 'HEADER' } })
    expect(wrapper.find('.brand').attributes('href')).toBe('/zh-CN')
    expect(wrapper.find('a[href="/zh-CN/about"]').exists()).toBe(true)
    expect(wrapper.findAll('select option')).toHaveLength(20)
    wrapper.unmount()
  })

  it('切换语言后重新拉取导航，并把链接换成新语言前缀', async () => {
    getMock.mockResolvedValue({ data: NAVS })
    const { i18n, wrapper } = mountHeader()
    await flushPromises()
    const callsBefore = getMock.mock.calls.length

    i18n.global.locale.value = 'en'
    await flushPromises()

    expect(getMock.mock.calls.length).toBeGreaterThan(callsBefore)
    expect(wrapper.find('a[href="/en/about"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('未配置翻译的导航 key 退化为最后一段，不显示原始 i18n 标识', async () => {
    getMock.mockResolvedValue({
      data: [{ id: 'n9', labelKey: 'nav.archive-old', target: '/archive-old', openInNewTab: false, children: [] }]
    })
    const { wrapper } = mountHeader()
    await flushPromises()

    const link = wrapper.find('a[href="/zh-CN/archive-old"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toBe('archive-old')
    expect(link.text()).not.toContain('portal.nav.archive-old')
    wrapper.unmount()
  })
  it('导航接口失败时不阻塞页面（保持空导航）', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const { wrapper } = mountHeader()
    await flushPromises()

    expect(wrapper.find('.brand').exists()).toBe(true)
    expect(wrapper.findAll('.nav a')).toHaveLength(0)
    wrapper.unmount()
  })
})

