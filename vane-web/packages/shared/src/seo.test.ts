/** @vitest-environment jsdom */
import { describe, expect, it, beforeEach } from 'vitest'
import { applySeo } from './seo'

function headMeta(selector: string): HTMLMetaElement | null {
  return document.head.querySelector<HTMLMetaElement>(selector)
}

describe('applySeo', () => {
  beforeEach(() => {
    document.head.innerHTML = ''
    document.title = ''
  })

  it('写入标题、描述、canonical 与 Open Graph 标签', () => {
    applySeo({
      title: '关于我们 - vane',
      description: '机构简介',
      canonical: 'https://example.com/zh-CN/about',
      locale: 'zh-CN',
      ogImage: 'https://example.com/og.png'
    })

    expect(document.title).toBe('关于我们 - vane')
    expect(headMeta('meta[name="description"]')?.content).toBe('机构简介')
    expect(headMeta('meta[property="og:title"]')?.content).toBe('关于我们 - vane')
    expect(headMeta('meta[property="og:url"]')?.content).toBe('https://example.com/zh-CN/about')
    expect(headMeta('meta[property="og:image"]')?.content).toBe('https://example.com/og.png')
    expect(headMeta('meta[name="robots"]')?.content).toBe('index,follow')
    expect(document.head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.com/zh-CN/about'
    )
    expect(document.documentElement.lang).toBe('zh-CN')
  })

  it('写入并替换多语言备用链接（不重复累积）', () => {
    applySeo({
      title: 'Home',
      canonical: 'https://example.com/zh-CN',
      alternates: [
        { hreflang: 'zh-CN', href: 'https://example.com/zh-CN' },
        { hreflang: 'en', href: 'https://example.com/en' }
      ]
    })
    applySeo({
      title: 'Home',
      canonical: 'https://example.com/zh-CN',
      alternates: [{ hreflang: 'ja', href: 'https://example.com/ja' }]
    })

    const alternates = document.head.querySelectorAll('link[rel="alternate"]')
    expect(alternates).toHaveLength(1)
    expect(alternates[0]?.getAttribute('hreflang')).toBe('ja')
    expect(alternates[0]?.getAttribute('href')).toBe('https://example.com/ja')
  })

  it('无描述时移除对应标签，文章页使用 og:type=article', () => {
    applySeo({ title: 'A', description: 'desc' })
    expect(headMeta('meta[name="description"]')).not.toBeNull()

    applySeo({ title: 'B', ogType: 'article' })

    expect(headMeta('meta[name="description"]')).toBeNull()
    expect(headMeta('meta[property="og:type"]')?.content).toBe('article')
    expect(headMeta('meta[name="twitter:card"]')?.content).toBe('summary')
  })
})

