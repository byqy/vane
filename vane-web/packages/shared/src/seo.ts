/**
 * 门户 SEO：把页面元数据写入 document head。
 * SPA 下由各页面在数据加载后调用；爬虫若执行 JS 可读到，非 JS 爬虫仍有静态兜底。
 */

export interface SeoAlternate {
  hreflang: string
  href: string
}

export interface SeoInput {
  title: string
  description?: string
  canonical?: string
  locale?: string
  ogImage?: string
  ogType?: 'website' | 'article'
  alternates?: SeoAlternate[]
  robots?: string
}

function meta(selector: string, attr: 'name' | 'property', key: string, content: string | undefined): void {
  let element = document.head.querySelector<HTMLMetaElement>(selector)
  if (!content) {
    element?.remove()
    return
  }
  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attr, key)
    element.dataset.seo = 'true'
    document.head.appendChild(element)
  }
  element.setAttribute('content', content)
}

function link(rel: string, href: string | undefined, extra?: Record<string, string>): void {
  const selector = `link[rel="${rel}"]${extra ? `[hreflang="${extra.hreflang}"]` : ''}`
  let element = document.head.querySelector<HTMLLinkElement>(selector)
  if (!href) {
    element?.remove()
    return
  }
  if (!element) {
    element = document.createElement('link')
    element.rel = rel
    if (extra) {
      for (const [key, value] of Object.entries(extra)) {
        element.setAttribute(key, value)
      }
    }
    element.dataset.seo = 'true'
    document.head.appendChild(element)
  }
  element.href = href
}

/** 清掉上一次由 JS 写入的多语言备用链接，避免路由切换后残留。 */
function clearAlternates(): void {
  document.head.querySelectorAll('link[rel="alternate"][data-seo]').forEach((element) => element.remove())
}

export function applySeo(input: SeoInput): void {
  document.title = input.title

  meta('meta[name="description"]', 'name', 'description', input.description)
  meta('meta[name="robots"]', 'name', 'robots', input.robots ?? 'index,follow')

  meta('meta[property="og:title"]', 'property', 'og:title', input.title)
  meta('meta[property="og:description"]', 'property', 'og:description', input.description)
  meta('meta[property="og:type"]', 'property', 'og:type', input.ogType ?? 'website')
  meta('meta[property="og:url"]', 'property', 'og:url', input.canonical)
  meta('meta[property="og:image"]', 'property', 'og:image', input.ogImage)

  meta('meta[name="twitter:card"]', 'name', 'twitter:card', input.ogImage ? 'summary_large_image' : 'summary')
  meta('meta[name="twitter:title"]', 'name', 'twitter:title', input.title)
  meta('meta[name="twitter:description"]', 'name', 'twitter:description', input.description)

  link('canonical', input.canonical)

  clearAlternates()
  for (const alternate of input.alternates ?? []) {
    link('alternate', alternate.href, { hreflang: alternate.hreflang })
  }

  if (input.locale) {
    document.documentElement.lang = input.locale
  }
}
