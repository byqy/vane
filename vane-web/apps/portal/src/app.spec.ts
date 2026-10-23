import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import App from './App.vue'
import { i18n } from './i18n'
import { router } from './router'

const SITE = {
  siteName: '示例公司',
  description: '示例公司描述',
  baseUrl: 'https://example.com',
  ogImage: '',
  locales: ['zh-CN', 'en'],
  defaultLocale: 'zh-CN',
  email: 'hi@example.com'
}

const PAGE = {
  id: 'p1',
  slug: 'about',
  title: '关于示例公司',
  summary: '我们提供示例服务。',
  body: '<p>公司正文</p>',
  coverFileId: null,
  publishedAt: null
}

describe('portal app shell', () => {
  it('所有页面共用页头与页脚，路由切换后渲染对应页面', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/public/site') {
        return Promise.resolve({ data: SITE })
      }
      if (url === '/public/navs') {
        return Promise.resolve({
          data: [{ id: 'n1', label: '公司介绍', labelKey: 'nav.about', target: '/about', openInNewTab: false, children: [] }]
        })
      }
      if (url === '/public/pages/about') {
        return Promise.resolve({ data: PAGE })
      }
      return Promise.resolve({ data: [] })
    })

    await router.push('/zh-CN/about')
    await router.isReady()

    const wrapper = mount(App, { global: { plugins: [i18n, router] } })
    await flushPromises()

    expect(wrapper.find('header.site-header').exists()).toBe(true)
    expect(wrapper.find('main.portal-main').exists()).toBe(true)
    expect(wrapper.find('footer.site-footer').exists()).toBe(true)
    // 页脚导航取 FOOTER 位置
    expect(getMock).toHaveBeenCalledWith('/public/navs', { params: { position: 'FOOTER' } })
    expect(wrapper.text()).toContain('关于示例公司')
    expect(wrapper.text()).toContain('版权所有')
    wrapper.unmount()
  })
})
