import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, pushMock, routeState, seoMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  pushMock: vi.fn(),
  seoMock: vi.fn(),
  routeState: {
    params: {} as Record<string, string>,
    query: {} as Record<string, string>,
    fullPath: '/zh-CN/articles'
  }
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: pushMock })
}))

vi.mock('../seo', () => ({
  applyArticleListSeo: seoMock,
  applyArticleSeo: vi.fn(),
  applyNotFoundSeo: vi.fn()
}))

import ArticlesView from './ArticlesView.vue'

const ARTICLE = {
  id: 'a1',
  slug: 'security-notice',
  title: '安全公告',
  summary: '重要更新',
  coverFileId: null,
  publishedAt: '2026-10-19T00:00:00Z',
  categoryCode: 'news',
  categoryName: '新闻',
  tags: ['release']
}

const CATEGORY = { id: 'c1', code: 'news', name: '新闻', description: null, articleCount: 1, children: [] }
const TAG = { id: 't1', code: 'release', articleCount: 1 }

function mockList(options: { items?: unknown[]; total?: number } = {}): void {
  getMock.mockImplementation((url: string) => {
    if (url === '/public/articles') {
      return Promise.resolve({
        data: {
          items: options.items ?? [ARTICLE],
          page: { number: 0, size: 10, total: options.total ?? 1 }
        }
      })
    }
    if (url === '/public/categories') {
      return Promise.resolve({ data: [CATEGORY] })
    }
    if (url === '/public/tags') {
      return Promise.resolve({ data: [TAG] })
    }
    return Promise.resolve({ data: [] })
  })
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ArticlesView, {
    global: {
      plugins: [i18n], stubs: { SiteHeader: true },
      components: { 'router-link': { props: ['to'], template: '<a><slot /></a>' } }
    }
  })
}

function articlesCallParams(): Record<string, unknown> {
  const call = getMock.mock.calls.find(([url]) => url === '/public/articles')
  return (call?.[1] as { params: Record<string, unknown> } | undefined)?.params ?? {}
}

beforeEach(() => {
  routeState.params = { locale: 'zh-CN' }
  routeState.query = {}
  routeState.fullPath = '/zh-CN/articles'
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('portal ArticlesView', () => {
  it('渲染文章列表并带上关键词参数', async () => {
    routeState.query = { keyword: '安全' }
    routeState.fullPath = '/zh-CN/articles?keyword=安全'
    mockList()

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('安全公告')
    expect(wrapper.text()).toContain('新闻')
    expect(wrapper.text()).toContain('release')
    expect(wrapper.text()).toContain('共 1 篇')
    expect(articlesCallParams()).toMatchObject({ keyword: '安全', 'page[number]': 0, 'page[size]': 10 })
    expect(seoMock).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('按分类路由筛选并透传 category 参数', async () => {
    routeState.params = { locale: 'zh-CN', code: 'news' }
    routeState.fullPath = '/zh-CN/categories/news'
    mockList()

    const wrapper = mountView()
    await flushPromises()

    expect(articlesCallParams()).toMatchObject({ category: 'news' })
    wrapper.unmount()
  })

  it('输入关键词并搜索时更新查询参数', async () => {
    mockList()
    const wrapper = mountView()
    await flushPromises()

    const input = wrapper.find('input[type="search"]')
    await input.setValue('更新')
    const searchButton = wrapper.findAll('button').find((button) => button.text().includes('搜索'))
    await searchButton!.trigger('click')
    await flushPromises()

    expect(pushMock).toHaveBeenCalledWith({
      name: 'articles',
      params: { locale: 'zh-CN' },
      query: { keyword: '更新' }
    })
    wrapper.unmount()
  })

  it('空列表展示空态', async () => {
    mockList({ items: [], total: 0 })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无文章')
    wrapper.unmount()
  })

  it('接口失败展示错误态', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败')
    wrapper.unmount()
  })
})


