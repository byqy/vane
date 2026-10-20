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

    expect(wrapper.text()).toContain('最新文章')
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
})




