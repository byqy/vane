import { applySeo, http, type Locale, type PublicProductDetail, type SeoAlternate } from '@vane/shared'
import { i18n } from './i18n'

export interface SiteSeo {
  siteName: string
  description: string
  baseUrl: string
  ogImage: string
  locales: string[]
  defaultLocale: string
  /** 联系方式由系统参数下发，未配置时为空串；结构化数据只在有值时写入。 */
  contactEmail?: string | null
  contactPhone?: string | null
  sameAs?: string[] | null
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

export interface ProductListSeoOptions {
  group?: string | null
  page?: number
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

/** 公开文件的绝对地址：结构化数据与 OG 需要绝对 URL。 */
export function absoluteFileUrl(baseUrl: string, fileId: string): string {
  return `${baseUrl}/api/v1/public/files/${fileId}`
}

let jsonLdScript: HTMLScriptElement | null = null

/** 写入（或清除）结构化数据；路由切换时避免残留上一个页面的 JSON-LD。 */
function writeJsonLd(data: Record<string, unknown> | null): void {
  if (jsonLdScript && !jsonLdScript.isConnected) {
    // head 被外部清理过（测试或页面重置），重新创建
    jsonLdScript = null
  }
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

function t(key: string): string {
  return i18n.global.t(key)
}

/**
 * 首页：站点标题 + Organization 结构化数据（name/url/logo/sameAs/contactPoint）。
 * sameAs、email、telephone 只在后端下发时写入，不编造。
 */
export async function applyHomeSeo(locale: Locale): Promise<void> {
  const site = await loadSite()
  const canonical = `${site.baseUrl}/${locale}`
  applySeo({
    title: site.siteName,
    description: site.description || undefined,
    canonical,
    locale,
    ogImage: site.ogImage || undefined,
    alternates: alternatesFor(site.baseUrl, '', site.locales)
  })

  const contactPoint: Record<string, unknown> = {
    '@type': 'ContactPoint',
    contactType: 'customer service',
    availableLanguage: site.locales
  }
  if (site.contactEmail) {
    contactPoint.email = site.contactEmail
  }
  if (site.contactPhone) {
    contactPoint.telephone = site.contactPhone
  }

  writeJsonLd({
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: site.siteName,
    url: canonical,
    ...(site.ogImage ? { logo: site.ogImage } : {}),
    ...(site.sameAs && site.sameAs.length > 0 ? { sameAs: site.sameAs } : {}),
    contactPoint: [contactPoint]
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

/** 公司介绍页：未发布时用标题兜底并 noindex。 */
export async function applyAboutSeo(locale: Locale, page: { title: string | null; summary: string | null } | null): Promise<void> {
  const site = await loadSite()
  writeJsonLd(null)
  applySeo({
    title: `${page?.title || t('portal.about.title')} - ${site.siteName}`,
    description: page?.summary || site.description || undefined,
    canonical: `${site.baseUrl}/${locale}/about`,
    locale,
    ogImage: site.ogImage || undefined,
    ogType: 'article',
    robots: page ? 'index,follow' : 'noindex,follow',
    alternates: alternatesFor(site.baseUrl, '/about', site.locales)
  })
}

/** 联系我们页。 */
export async function applyContactSeo(locale: Locale): Promise<void> {
  const site = await loadSite()
  writeJsonLd(null)
  applySeo({
    title: `${t('portal.contact.title')} - ${site.siteName}`,
    description: site.description || undefined,
    canonical: `${site.baseUrl}/${locale}/contact`,
    locale,
    ogImage: site.ogImage || undefined,
    alternates: alternatesFor(site.baseUrl, '/contact', site.locales)
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

/**
 * 产品中心：产品线与分页属于筛选态，按 ADR-018/025 用 noindex,follow，
 * canonical 固定指向无条件列表页，避免筛选参数产生重复内容。
 */
export async function applyProductsSeo(locale: Locale, options: ProductListSeoOptions = {}): Promise<void> {
  const site = await loadSite()
  const filtered = Boolean(options.group?.trim()) || (options.page ?? 1) > 1
  writeJsonLd(null)
  applySeo({
    title: `${t('portal.products.title')} - ${site.siteName}`,
    description: t('portal.products.subtitle') || site.description || undefined,
    canonical: `${site.baseUrl}/${locale}/products`,
    locale,
    ogImage: site.ogImage || undefined,
    robots: filtered ? 'noindex,follow' : 'index,follow',
    alternates: alternatesFor(site.baseUrl, '/products', site.locales)
  })
}

/** 产品详情：Product + BreadcrumbList；不存在时 noindex 并清空结构化数据。 */
export async function applyProductSeo(locale: Locale, product: PublicProductDetail | null): Promise<void> {
  const site = await loadSite()
  const listPath = '/products'
  if (!product) {
    writeJsonLd(null)
    applySeo({
      title: `${t('portal.products.notFound')} - ${site.siteName}`,
      description: site.description || undefined,
      canonical: `${site.baseUrl}/${locale}${listPath}`,
      locale,
      ogImage: site.ogImage || undefined,
      robots: 'noindex,follow',
      alternates: alternatesFor(site.baseUrl, listPath, site.locales)
    })
    return
  }

  const canonical = `${site.baseUrl}/${locale}/products/${product.slug}`
  const images = [product.coverFileId, ...product.media.filter((item) => item.kind?.toUpperCase() === 'IMAGE').map((item) => item.fileId)]
    .filter((fileId): fileId is string => Boolean(fileId))
    .map((fileId) => absoluteFileUrl(site.baseUrl, fileId))

  applySeo({
    title: `${product.name} - ${site.siteName}`,
    description: product.summary || site.description || undefined,
    canonical,
    locale,
    ogImage: images[0] ?? site.ogImage ?? undefined,
    alternates: alternatesFor(site.baseUrl, `/products/${product.slug}`, site.locales)
  })

  writeJsonLd({
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'Product',
        name: product.name,
        description: product.summary || undefined,
        sku: product.code,
        url: canonical,
        ...(images.length > 0 ? { image: images } : {}),
        ...(product.group ? { category: product.group } : {}),
        brand: { '@type': 'Brand', name: site.siteName }
      },
      {
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: t('portal.nav.home'), item: `${site.baseUrl}/${locale}` },
          { '@type': 'ListItem', position: 2, name: t('portal.products.title'), item: `${site.baseUrl}/${locale}${listPath}` },
          { '@type': 'ListItem', position: 3, name: product.name, item: canonical }
        ]
      }
    ]
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
