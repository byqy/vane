import { applySeo, http, type Locale, type SeoAlternate } from '@vane/shared'
import { i18n } from './i18n'

export interface SiteSeo {
  siteName: string
  description: string
  baseUrl: string
  ogImage: string
  locales: string[]
  defaultLocale: string
}

export interface ArticleSeoInput {
  slug: string
  title: string | null
  summary: string | null
  publishedAt: string | null
  categoryName?: string | null
}

export interface ArticleListSeoOptions {
  categoryCode?: string | null
  keyword?: string | null
}

const FALLBACK_LOCALES: string[] = [
  'zh-CN', 'zh-TW', 'en', 'ja', 'ko', 'ru', 'de', 'fr', 'es', 'pt-BR',
  'it', 'nl', 'pl', 'tr', 'uk', 'vi', 'th', 'id', 'ar', 'hi'
]

let cached: SiteSeo | null = null
let pending: Promise<SiteSeo> | null = null

/** 站点级 SEO 配置（名称、默认描述、对外根地址），进程内缓存。 */
export function loadSite(): Promise<SiteSeo> {
  if (cached) {
    return Promise.resolve(cached)
  }
  if (!pending) {
    pending = http
      .get<SiteSeo>('/public/site')
      .then(({ data }) => {
        cached = { ...data, locales: data.locales?.length ? data.locales : FALLBACK_LOCALES }
        return cached
      })
      .catch(() => {
        cached = { siteName: 'vane', description: '', baseUrl: '', ogImage: '', locales: FALLBACK_LOCALES, defaultLocale: 'zh-CN' }
        return cached
      })
      .finally(() => {
        pending = null
      })
  }
  return pending
}

/** 把站内链接（如 /about）补上语言前缀；外链与锚点原样返回。 */
export function localizeHref(locale: string, target: string | null | undefined): string {
  if (!target) {
    return '#'
  }
  if (!target.startsWith('/')) {
    return target
  }
  if (target === '/') {
    return `/${locale}`
  }
  return `/${locale}${target}`
}

/** 去掉了语言前缀的站内路径，用于生成各语言备用链接。 */
export function withoutLocale(path: string): string {
  const segments = path.split('/').filter(Boolean)
  if (segments.length > 0 && FALLBACK_LOCALES.includes(segments[0]!)) {
    segments.shift()
  }
  return segments.length === 0 ? '' : `/${segments.join('/')}`
}

export function alternatesFor(baseUrl: string, pathWithoutLocale: string, locales: string[]): SeoAlternate[] {
  return locales.map((locale) => ({
    hreflang: locale,
    href: `${baseUrl}/${locale}${pathWithoutLocale}`
  }))
}

let jsonLdScript: HTMLScriptElement | null = null

/** 写入（或清除）文章结构化数据；路由切换时避免残留上一篇文章。 */
function writeJsonLd(data: Record<string, unknown> | null): void {
  if (!data) {
    jsonLdScript?.remove()
    jsonLdScript = null
    return
  }
  if (!jsonLdScript) {
    jsonLdScript = document.createElement('script')
    jsonLdScript.type = 'application/ld+json'
    jsonLdScript.dataset.seo = 'true'
    document.head.appendChild(jsonLdScript)
  }
  jsonLdScript.textContent = JSON.stringify(data)
}

function articleTitleOf(site: SiteSeo, title: string | null): string {
  return title ? `${title} - ${site.siteName}` : site.siteName
}

export async function applyHomeSeo(locale: Locale): Promise<void> {
  const site = await loadSite()
  writeJsonLd(null)
  applySeo({
    title: site.siteName,
    description: site.description || undefined,
    canonical: `${site.baseUrl}/${locale}`,
    locale,
    ogImage: site.ogImage || undefined,
    alternates: alternatesFor(site.baseUrl, '', site.locales)
  })
}

export async function applyPageSeo(locale: Locale, slug: string, page: { title: string | null; summary: string | null } | null): Promise<void> {
  const site = await loadSite()
  writeJsonLd(null)
  const title = page?.title ? `${page.title} - ${site.siteName}` : site.siteName
  applySeo({
    title,
    description: page?.summary || site.description || undefined,
    canonical: `${site.baseUrl}/${locale}/${slug}`,
    locale,
    ogImage: site.ogImage || undefined,
    ogType: 'article',
    alternates: alternatesFor(site.baseUrl, `/${slug}`, site.locales)
  })
}

/** 文章详情：标题「文章标题 - 站点名」、description=summary、og:type=article、Article JSON-LD。 */
export async function applyArticleSeo(locale: Locale, article: ArticleSeoInput): Promise<void> {
  const site = await loadSite()
  const canonical = `${site.baseUrl}/${locale}/articles/${article.slug}`
  const title = articleTitleOf(site, article.title)
  applySeo({
    title,
    description: article.summary || site.description || undefined,
    canonical,
    locale,
    ogImage: site.ogImage || undefined,
    ogType: 'article',
    alternates: alternatesFor(site.baseUrl, `/articles/${article.slug}`, site.locales)
  })
  writeJsonLd({
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: article.title ?? article.slug,
    description: article.summary || undefined,
    datePublished: article.publishedAt || undefined,
    inLanguage: locale,
    mainEntityOfPage: canonical,
    url: canonical,
    articleSection: article.categoryName || undefined,
    publisher: { '@type': 'Organization', name: site.siteName }
  })
}

/** 文章列表与分类页：写 canonical 与 hreflang；关键词搜索页 robots=noindex,follow。 */
export async function applyArticleListSeo(
  locale: Locale,
  options: ArticleListSeoOptions = {}
): Promise<void> {
  const site = await loadSite()
  const searching = Boolean(options.keyword?.trim())
  const path = options.categoryCode ? `/categories/${options.categoryCode}` : '/articles'
  const title = `${i18n.global.t('portal.articles.title')} - ${site.siteName}`
  writeJsonLd(null)
  applySeo({
    title,
    description: site.description || undefined,
    canonical: `${site.baseUrl}/${locale}${path}`,
    locale,
    ogImage: site.ogImage || undefined,
    robots: searching ? 'noindex,follow' : 'index,follow',
    alternates: alternatesFor(site.baseUrl, path, site.locales)
  })
}

export async function applyNotFoundSeo(locale: Locale): Promise<void> {
  const site = await loadSite()
  writeJsonLd(null)
  applySeo({
    title: `${i18n.global.t('portal.pageNotFound')} - ${site.siteName}`,
    robots: 'noindex,follow',
    canonical: `${site.baseUrl}/${locale}`,
    locale
  })
}
