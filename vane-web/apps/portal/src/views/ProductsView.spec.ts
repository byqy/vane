import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, pushMock, seoMock, routeState } = vi.hoisted(() => ({
  getMock: vi.fn(),
  pushMock: vi.fn(),
  seoMock: vi.fn(),
  routeState: {
    params: { locale: 'zh-CN' } as Record<string, string>,
    query: {} as Record<string, string>,
    fullPath: '/zh-CN/products'
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
  applyProductsSeo: seoMock,
  applyHomeSeo: vi.fn(),
  applyAboutSeo: vi.fn(),
  applyContactSeo: vi.fn(),
  applyProductSeo: vi.fn(),
  applyPageSeo: vi.fn(),
  applyNotFoundSeo: vi.fn(),
  applyArticleListSeo: vi.fn(),
  localizeHref: (_locale: string, target: string | null) => target ?? '#'
}))

import ProductsView from './ProductsView.vue'

function product(slug: string, name: string, group: string | null) {
  return {
    id: `id-${slug}`,
    code: slug.toUpperCase(),
    slug,
    group,
    coverFileId: null,
    videoFileId: null,
    name,
    summary: `${name} 摘要`,
    sortNo: 0,
    publishedAt: null
  }
}

const PRODUCTS = [product('alpha', 'Alpha 产品', 'software'), product('beta', 'Beta 产品', 'hardware')]

function mockApi(options: { items?: unknown[]; total?: number } = {}): void {
  getMock.mockImplementation((url: string, config?: { params?: Record<string, unknown> }) => {
    if (url === '/public/products') {
      const params = config?.params ?? {}
      const size = Number(params['page[size]'] ?? 9)
      if (size >= 100) {
        return Promise.resolve({
          data: {
            items: PRODUCTS,
            page: { number: 0, size, total: PRODUCTS.length }
          }
        })
      }
      return Promise.resolve({
        data: {
          items: options.items ?? PRODUCTS,
          page: { number: Number(params['page[number]'] ?? 0), size, total: options.total ?? PRODUCTS.length }
        }
      })
    }
    return Promise.resolve({ data: [] })
  })
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ProductsView, {
    global: {
      plugins: [i18n],
      components: { 'router-link': { props: ['to'], template: '<a :href="to"><slot /></a>' } }
    }
  })
}

beforeEach(() => {
  routeState.params = { locale: 'zh-CN' }
  routeState.query = {}
  routeState.fullPath = '/zh-CN/products'
  pushMock.mockReset()
})

describe('portal ProductsView', () => {
  it('渲染产品卡片、产品线筛选与总数，并写产品中心 SEO', async () => {
    mockApi()
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('产品中心')
    expect(wrapper.text()).toContain('Alpha 产品')
    expect(wrapper.text()).toContain('Beta 产品')
    expect(wrapper.find('a[href="/zh-CN/products/alpha"]').exists()).toBe(true)
    expect(wrapper.findAll('.filter-select option')).toHaveLength(3)
    expect(seoMock).toHaveBeenCalledWith('zh-CN', { group: '', page: 1 })
    wrapper.unmount()
  })

  it('选择产品线后按 group 导航', async () => {
    mockApi()
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('.filter-select').setValue('hardware')

    expect(pushMock).toHaveBeenCalledWith({
      name: 'products',
      params: { locale: 'zh-CN' },
      query: { group: 'hardware' }
    })
    wrapper.unmount()
  })

  it('带 group 查询串进入时按筛选态请求并透传参数', async () => {
    routeState.query = { group: 'software' }
    routeState.fullPath = '/zh-CN/products?group=software'
    mockApi()
    const wrapper = mountView()
    await flushPromises()

    const listCall = getMock.mock.calls.find(([, config]) => Number(config?.params?.['page[size]'] ?? 9) === 9)
    expect(listCall?.[1]?.params).toMatchObject({ group: 'software', 'page[number]': 0 })
    expect(seoMock).toHaveBeenCalledWith('zh-CN', { group: 'software', page: 1 })
    wrapper.unmount()
  })

  it('空列表展示空态', async () => {
    mockApi({ items: [], total: 0 })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无产品')
    wrapper.unmount()
  })

  it('接口失败展示错误态与 traceId，重试后恢复列表', async () => {
    getMock
      .mockRejectedValueOnce({ isAxiosError: true, response: { status: 500, data: { status: 500, detail: '服务异常', traceId: 'trace-1' } } })
      .mockImplementation(() => Promise.resolve({ data: { items: PRODUCTS, page: { number: 0, size: 9, total: 2 } } }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('服务异常')
    expect(wrapper.text()).toContain('trace-1')

    await wrapper.get('.state__retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Alpha 产品')
    wrapper.unmount()
  })
})
