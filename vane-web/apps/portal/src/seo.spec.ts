import { beforeEach, describe, expect, it, vi } from 'vitest'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import { i18n } from './i18n'
import {
  applyArticleListSeo,
  applyArticleSeo,
  applyHomeSeo,
  applyNotFoundSeo,
  applyProductSeo,
  applyProductsSeo
} from './seo'

const SITE = {
  siteName: 'vane 门户',
  description: '机构门户',
  baseUrl: 'https://example.com',
  ogImage: '',
  locales: ['zh-CN', 'en'],
  defaultLocale: 'zh-CN'
}

function metaContent(selector: string): string | null {
  return document.head.querySelector<HTMLMetaElement>(selector)?.content ?? null
}

function jsonLd(): Record<string, unknown> | null {
  const script = document.head.querySelector<HTMLScriptElement>('script[type="application/ld+json"][data-seo]')
  return script?.textContent ? JSON.parse(script.textContent) : null
}

beforeEach(() => {
  document.head.innerHTML = ''
  document.title = ''
  i18n.global.locale.value = 'zh-CN'
  getMock.mockResolvedValue({ data: SITE })
})

describe('portal seo', () => {
  it('文章详情写入标题、canonical、og:type 与 Article JSON-LD', async () => {
    await applyArticleSeo('zh-CN', {
      slug: 'hello',
      title: '你好世界',
      summary: '简介',
      publishedAt: '2026-10-19T00:00:00Z',
      categoryName: '新闻'
    })

    expect(document.title).toBe('你好世界 - vane 门户')
    expect(metaContent('meta[name="description"]')).toBe('简介')
    expect(metaContent('meta[property="og:type"]')).toBe('article')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/articles/hello'
    )
    expect(document.head.querySelectorAll('link[rel="alternate"]')).toHaveLength(2)
    expect(jsonLd()).toMatchObject({
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: '你好世界',
      description: '简介',
      inLanguage: 'zh-CN',
      articleSection: '新闻'
    })
  })

  it('关键词搜索页 robots=noindex,follow，普通列表页 index,follow', async () => {
    await applyArticleListSeo('zh-CN', { keyword: '安全' })
    expect(metaContent('meta[name="robots"]')).toBe('noindex,follow')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/articles'
    )

    await applyArticleListSeo('zh-CN')
    expect(metaContent('meta[name="robots"]')).toBe('index,follow')
    expect(jsonLd()).toBeNull()
  })

  it('分类页 canonical 指向分类路径，404 页使用本地化标题', async () => {
    await applyArticleListSeo('zh-CN', { categoryCode: 'news' })
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/categories/news'
    )

    await applyNotFoundSeo('zh-CN')
    expect(document.title).toContain('页面不存在')
    expect(metaContent('meta[name="robots"]')).toBe('noindex,follow')
  })

  it('首页写入 Organization 结构化数据（name/url/contactPoint，未提供的数据不编造）', async () => {
    await applyHomeSeo('zh-CN')

    expect(document.title).toBe('vane 门户')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN'
    )
    const data = jsonLd() as Record<string, unknown>
    expect(data).toMatchObject({
      '@context': 'https://schema.org',
      '@type': 'Organization',
      name: 'vane 门户',
      url: 'https://example.com/zh-CN'
    })
    expect(data.sameAs).toBeUndefined()
    const contactPoint = (data.contactPoint as Array<Record<string, unknown>>)[0]!
    expect(contactPoint).toMatchObject({ '@type': 'ContactPoint', contactType: 'customer service' })
    expect(contactPoint.availableLanguage).toEqual(['zh-CN', 'en'])
  })

  it('产品详情写入 Product 与 BreadcrumbList，canonical 指向产品页', async () => {
    await applyProductSeo('zh-CN', {
      id: 'p1',
      code: 'ALPHA',
      slug: 'alpha',
      group: 'software',
      coverFileId: 'cover-1',
      videoFileId: null,
      name: 'Alpha 产品',
      summary: '产品摘要',
      body: null,
      features: [],
      media: [{ fileId: 'img-1', kind: 'IMAGE', sortNo: 0, caption: null }],
      sortNo: 0,
      publishedAt: null
    })

    expect(document.title).toBe('Alpha 产品 - vane 门户')
    expect(metaContent('meta[name="description"]')).toBe('产品摘要')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/products/alpha'
    )
    const graph = (jsonLd() as Record<string, unknown>)['@graph'] as Array<Record<string, unknown>>
    expect(graph[0]).toMatchObject({
      '@type': 'Product',
      name: 'Alpha 产品',
      sku: 'ALPHA',
      category: 'software',
      url: 'https://example.com/zh-CN/products/alpha'
    })
    expect(graph[0]!.image).toEqual([
      'https://example.com/api/v1/public/files/cover-1',
      'https://example.com/api/v1/public/files/img-1'
    ])
    expect(graph[1]!['@type']).toBe('BreadcrumbList')
    expect((graph[1]!.itemListElement as unknown[]).length).toBe(3)
  })

  it('产品列表筛选态与大分页 noindex,follow，无条件列表 index,follow', async () => {
    await applyProductsSeo('zh-CN', { group: 'software' })
    expect(metaContent('meta[name="robots"]')).toBe('noindex,follow')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/products'
    )

    await applyProductsSeo('zh-CN', { page: 2 })
    expect(metaContent('meta[name="robots"]')).toBe('noindex,follow')

    await applyProductsSeo('zh-CN')
    expect(metaContent('meta[name="robots"]')).toBe('index,follow')
  })

  it('产品不存在时写 noindex 且清空结构化数据', async () => {
    await applyProductSeo('zh-CN', null)

    expect(document.title).toContain('产品不存在')
    expect(metaContent('meta[name="robots"]')).toBe('noindex,follow')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/products'
    )
    expect(jsonLd()).toBeNull()
  })
})
