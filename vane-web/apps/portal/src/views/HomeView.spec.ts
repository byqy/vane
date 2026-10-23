import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import HomeView from './HomeView.vue'

function noticesOf(title: string, body: string) {
  return {
    items: [
      {
        id: 'n1',
        level: 'INFO',
        pinned: false,
        publishAt: null,
        title,
        body
      }
    ],
    page: { number: 0, size: 20, total: 1 }
  }
}

function mountHome() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return {
    i18n,
    wrapper: mount(HomeView, {
      global: {
        plugins: [i18n],
        stubs: { SiteHeader: true },
        components: {
          'router-link': { props: ['to'], template: '<a :href="to"><slot /></a>' }
        }
      }
    })
  }
}

describe('portal HomeView', () => {
  it('首页展示最新文章并链接到文章详情', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/public/articles') {
        return Promise.resolve({
          data: {
            items: [
              { id: 'a1', slug: 'welcome-content', title: '欢迎使用 vane 内容体系', summary: '演示文章摘要', publishedAt: '2026-10-19T00:00:00Z' }
            ],
            page: { number: 0, size: 5, total: 1 }
          }
        })
      }
      return Promise.resolve({ data: url === '/public/notices' ? noticesOf('公告标题', '公告正文') : [] })
    })

    const { wrapper } = mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('最新动态')
    expect(wrapper.text()).toContain('欢迎使用 vane 内容体系')
    expect(wrapper.text()).toContain('演示文章摘要')
    const link = wrapper.findAll('a').find((item) => item.text().includes('欢迎使用 vane 内容体系'))
    expect(link?.attributes('href')).toBe('/zh-CN/articles/welcome-content')
    wrapper.unmount()
  })
  it('切换语言后重新拉取公告，标题与正文随之切换', async () => {
    let call = 0
    getMock.mockImplementation((url: string) => {
      if (url === '/public/notices') {
        call++
        return Promise.resolve({ data: call <= 1 ? noticesOf('系统维护', '今晚维护') : noticesOf('Maintenance', 'Tonight') })
      }
      if (url === '/public/articles') {
        return Promise.resolve({ data: { items: [], page: { number: 0, size: 5, total: 0 } } })
      }
      return Promise.resolve({ data: [] })
    })

    const { i18n, wrapper } = mountHome()
    await flushPromises()
    expect(wrapper.text()).toContain('系统维护')
    const callsBefore = getMock.mock.calls.length

    i18n.global.locale.value = 'en'
    await flushPromises()

    expect(getMock.mock.calls.length).toBeGreaterThan(callsBefore)
    expect(wrapper.text()).toContain('Maintenance')
    expect(wrapper.text()).toContain('Tonight')
    expect(wrapper.text()).not.toContain('系统维护')
  })

  it('渲染首屏、轮播、公司介绍、产品矩阵与核心优势；无数据区块整块隐藏', async () => {
    getMock.mockImplementation((url: string) => {
      switch (url) {
        case '/public/site':
          return Promise.resolve({
            data: {
              siteName: '示例公司',
              description: '示例公司描述',
              baseUrl: 'https://example.com',
              ogImage: '',
              locales: ['zh-CN'],
              defaultLocale: 'zh-CN'
            }
          })
        case '/public/banners':
          return Promise.resolve({
            data: [{ id: 'b1', imageFileId: 'banner-1', title: '首页横幅', linkUrl: null }]
          })
        case '/public/pages/about':
          return Promise.resolve({
            data: { id: 'p1', slug: 'about', title: '公司介绍', summary: '我们做产品', body: '<p>正文</p>', coverFileId: null, publishedAt: null }
          })
        case '/public/products':
          return Promise.resolve({
            data: {
              items: [
                {
                  id: 'pr1',
                  code: 'ALPHA',
                  slug: 'alpha',
                  group: 'software',
                  coverFileId: null,
                  videoFileId: null,
                  name: 'Alpha 产品',
                  summary: '产品摘要',
                  sortNo: 0,
                  publishedAt: null
                }
              ],
              page: { number: 0, size: 6, total: 1 }
            }
          })
        default:
          return Promise.resolve({ data: { items: [], page: { number: 0, size: 5, total: 0 } } })
      }
    })

    const { wrapper } = mountHome()
    await flushPromises()

    expect(wrapper.find('.hero-title').text()).toBe('示例公司')
    expect(wrapper.text()).toContain('咨询与合作')
    expect(wrapper.text()).toContain('浏览产品')
    expect(wrapper.find('img[src="/api/v1/public/files/banner-1"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('首页横幅')
    expect(wrapper.text()).toContain('我们做产品')
    expect(wrapper.text()).toContain('Alpha 产品')
    expect(wrapper.find('a[href="/zh-CN/products/alpha"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('核心优势')
    expect(wrapper.text()).toContain('稳定可靠')
    // 文章与公告都没有数据：整块隐藏，不留空标题
    expect(wrapper.text()).not.toContain('最新动态')
    expect(wrapper.text()).not.toContain('公告')
    wrapper.unmount()
  })

  it('区块接口失败时展示错误态与 traceId，重试后恢复该区块', async () => {
    let failArticles = true
    getMock.mockImplementation((url: string) => {
      if (url === '/public/articles') {
        return failArticles
          ? Promise.reject({
              isAxiosError: true,
              response: { status: 500, data: { status: 500, detail: '文章接口异常', traceId: 'trace-a' } }
            })
          : Promise.resolve({
              data: {
                items: [{ id: 'a9', slug: 'recovered', title: '恢复的文章', summary: null, publishedAt: null }],
                page: { number: 0, size: 5, total: 1 }
              }
            })
      }
      return Promise.resolve({ data: url === '/public/notices' ? { items: [], page: { number: 0, size: 20, total: 0 } } : [] })
    })

    const { wrapper } = mountHome()
    await flushPromises()

    expect(wrapper.text()).toContain('文章接口异常')
    expect(wrapper.text()).toContain('trace-a')

    failArticles = false
    await wrapper.get('.state__retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('恢复的文章')
    expect(wrapper.text()).not.toContain('文章接口异常')
    wrapper.unmount()
  })
})




