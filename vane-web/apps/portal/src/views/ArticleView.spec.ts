import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, articleSeoMock, notFoundSeoMock, routeState } = vi.hoisted(() => ({
  getMock: vi.fn(),
  articleSeoMock: vi.fn(),
  notFoundSeoMock: vi.fn(),
  routeState: {
    params: { locale: 'zh-CN', slug: 'hello' } as Record<string, string>,
    query: {} as Record<string, string>,
    fullPath: '/zh-CN/articles/hello'
  }
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('../seo', () => ({
  applyArticleSeo: articleSeoMock,
  applyNotFoundSeo: notFoundSeoMock,
  applyArticleListSeo: vi.fn(),
  applyPageSeo: vi.fn(),
  applyHomeSeo: vi.fn(),
  localizeHref: (_locale: string, target: string | null) => target ?? '#'
}))

import ArticleView from './ArticleView.vue'

const DETAIL = {
  id: 'a1',
  slug: 'hello',
  title: '你好世界',
  summary: '简介',
  body: '<p>正文内容</p>',
  coverFileId: null,
  publishedAt: '2026-10-19T00:00:00Z',
  categoryCode: 'news',
  categoryName: '新闻',
  tags: ['release']
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return {
    i18n,
    wrapper: mount(ArticleView, {
      global: {
        plugins: [i18n], stubs: { SiteHeader: true },
        components: { 'router-link': { props: ['to'], template: '<a><slot /></a>' } }
      }
    })
  }
}

beforeEach(() => {
  routeState.params = { locale: 'zh-CN', slug: 'hello' }
  routeState.query = {}
  routeState.fullPath = '/zh-CN/articles/hello'
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('portal ArticleView', () => {
  it('渲染文章标题、摘要、正文与发布时间', async () => {
    getMock.mockResolvedValue({ data: DETAIL })
    const { wrapper } = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/public/articles/hello')
    expect(wrapper.text()).toContain('你好世界')
    expect(wrapper.text()).toContain('简介')
    expect(wrapper.html()).toContain('正文内容')
    expect(wrapper.text()).toContain('返回文章列表')
    expect(articleSeoMock).toHaveBeenCalledWith(
      'zh-CN',
      expect.objectContaining({ slug: 'hello', title: '你好世界', summary: '简介' })
    )
    wrapper.unmount()
  })

  it('404 时展示不存在态并写 noindex SEO', async () => {
    getMock.mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { status: 404, detail: '不存在' } }
    })
    const { wrapper } = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('文章不存在')
    expect(notFoundSeoMock).toHaveBeenCalledWith('zh-CN')
    wrapper.unmount()
  })

  it('切换语言后重新拉取文章', async () => {
    let call = 0
    getMock.mockImplementation(() => {
      call += 1
      return Promise.resolve({ data: call <= 1 ? DETAIL : { ...DETAIL, title: 'Hello world' } })
    })
    const { i18n, wrapper } = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('你好世界')

    i18n.global.locale.value = 'en'
    await flushPromises()

    expect(wrapper.text()).toContain('Hello world')
    wrapper.unmount()
  })
})


