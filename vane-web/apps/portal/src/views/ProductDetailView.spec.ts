import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, seoMock, routeState } = vi.hoisted(() => ({
  getMock: vi.fn(),
  seoMock: vi.fn(),
  routeState: {
    params: { locale: 'zh-CN', slug: 'alpha' } as Record<string, string>,
    query: {} as Record<string, string>,
    fullPath: '/zh-CN/products/alpha'
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
  applyProductSeo: seoMock,
  applyProductsSeo: vi.fn(),
  applyHomeSeo: vi.fn(),
  applyAboutSeo: vi.fn(),
  applyContactSeo: vi.fn(),
  applyPageSeo: vi.fn(),
  applyNotFoundSeo: vi.fn(),
  applyArticleListSeo: vi.fn(),
  localizeHref: (_locale: string, target: string | null) => target ?? '#'
}))

import ProductDetailView from './ProductDetailView.vue'

const DETAIL = {
  id: 'pr1',
  code: 'ALPHA',
  slug: 'alpha',
  group: 'software',
  coverFileId: 'cover-1',
  videoFileId: 'video-1',
  name: 'Alpha 产品',
  summary: '产品摘要',
  body: '<p>详情正文</p>',
  features: [
    { title: '高可用', description: '双活部署' },
    { title: '安全', description: null }
  ],
  media: [
    { fileId: 'img-1', kind: 'IMAGE', sortNo: 0, caption: '外观' },
    { fileId: 'img-2', kind: 'IMAGE', sortNo: 1, caption: null }
  ],
  sortNo: 0,
  publishedAt: null
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ProductDetailView, {
    global: {
      plugins: [i18n],
      components: { 'router-link': { props: ['to'], template: '<a :href="to"><slot /></a>' } }
    }
  })
}

beforeEach(() => {
  routeState.params = { locale: 'zh-CN', slug: 'alpha' }
  routeState.fullPath = '/zh-CN/products/alpha'
})

describe('portal ProductDetailView', () => {
  it('渲染封面、图集、视频、特性、正文与咨询入口', async () => {
    getMock.mockResolvedValue({ data: DETAIL })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/public/products/alpha')
    expect(wrapper.text()).toContain('Alpha 产品')
    expect(wrapper.text()).toContain('产品摘要')
    expect(wrapper.text()).toContain('software')
    expect(wrapper.find('img.cover').attributes('src')).toBe('/api/v1/public/files/cover-1')
    expect(wrapper.text()).toContain('产品图集')
    expect(wrapper.findAll('.thumb')).toHaveLength(2)
    expect(wrapper.text()).toContain('演示视频')
    expect(wrapper.find('video').attributes('src')).toBe('/api/v1/public/files/video-1')
    expect(wrapper.text()).toContain('产品特性')
    expect(wrapper.text()).toContain('高可用')
    expect(wrapper.text()).toContain('双活部署')
    expect(wrapper.html()).toContain('详情正文')
    expect(wrapper.find('a[href="/zh-CN/contact"]').text()).toBe('咨询此产品')
    expect(seoMock).toHaveBeenCalledWith('zh-CN', expect.objectContaining({ slug: 'alpha' }))
    wrapper.unmount()
  })

  it('产品不存在时展示不存在态并写 noindex SEO', async () => {
    getMock.mockRejectedValue({ isAxiosError: true, response: { status: 404, data: { status: 404 } } })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('产品不存在')
    expect(seoMock).toHaveBeenCalledWith('zh-CN', null)
    wrapper.unmount()
  })

  it('接口失败展示错误态，重试后恢复详情', async () => {
    getMock
      .mockRejectedValueOnce({ isAxiosError: true, response: { status: 500, data: { status: 500, detail: '服务异常', traceId: 'trace-2' } } })
      .mockResolvedValueOnce({ data: DETAIL })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('服务异常')
    expect(wrapper.text()).toContain('trace-2')

    await wrapper.get('.state__retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Alpha 产品')
    wrapper.unmount()
  })
})
