import {
  http,
  type PageResult,
  type PublicArticleView,
  type PublicProductDetail,
  type PublicProductView
} from '@vane/shared'

/**
 * 门户公开接口的视图模型与请求函数。
 * 共享包已提供产品类型；导航、轮播、站点、内容页的公开 DTO 目前只在门户消费，
 * 因此在这里声明（不是后端契约的重复定义，而是门户侧的读取视图）。
 */

/** 公开导航节点（ADR-025 起同时可能带本地化 `label`，`labelKey` 保留兼容）。 */
export interface PublicNavNode {
  id: string
  labelKey?: string | null
  label?: string | null
  target: string | null
  openInNewTab: boolean
  children?: PublicNavNode[]
}

/** 公开轮播（ADR-025 起同时可能带本地化 `title`）。 */
export interface PublicBanner {
  id: string
  position?: string
  imageFileId: string
  titleKey?: string | null
  title?: string | null
  linkUrl: string | null
  sortNo?: number
}

/** 公开内容页（公司介绍等）。 */
export interface PublicPageContent {
  id: string
  slug: string
  title: string | null
  summary: string | null
  body: string | null
  coverFileId: string | null
  publishedAt: string | null
}

/** 公开站点信息；联系方式为可选字段，后端未下发时页面降级展示。 */
export interface PublicSiteInfo {
  siteName: string
  description: string
  baseUrl: string
  ogImage: string
  locales: string[]
  defaultLocale: string
  email?: string | null
  phone?: string | null
  address?: string | null
  hours?: string | null
  sameAs?: string[] | null
}

/** 列表接口单页上限（03 §5）。 */
export const PAGE_SIZE_MAX = 100

export async function fetchSite(): Promise<PublicSiteInfo> {
  const { data } = await http.get<PublicSiteInfo>('/public/site')
  return data
}

export async function fetchNavs(position: 'HEADER' | 'FOOTER'): Promise<PublicNavNode[]> {
  const { data } = await http.get<PublicNavNode[]>('/public/navs', { params: { position } })
  return data
}

export async function fetchBanners(position: string): Promise<PublicBanner[]> {
  const { data } = await http.get<PublicBanner[]>('/public/banners', { params: { position } })
  return data
}

export async function fetchPage(slug: string): Promise<PublicPageContent> {
  const { data } = await http.get<PublicPageContent>(`/public/pages/${slug}`)
  return data
}

export async function fetchArticles(size: number): Promise<PublicArticleView[]> {
  const { data } = await http.get<PageResult<PublicArticleView>>('/public/articles', {
    params: { 'page[number]': 0, 'page[size]': size }
  })
  return data.items
}

export interface ProductQuery {
  page?: number
  size?: number
  group?: string | null
}

export async function fetchProducts(query: ProductQuery = {}): Promise<PageResult<PublicProductView>> {
  const params: Record<string, unknown> = {
    'page[number]': Math.max(0, (query.page ?? 1) - 1),
    'page[size]': query.size ?? 9
  }
  if (query.group) {
    params.group = query.group
  }
  const { data } = await http.get<PageResult<PublicProductView>>('/public/products', { params })
  return data
}

export async function fetchProduct(slug: string): Promise<PublicProductDetail> {
  const { data } = await http.get<PublicProductDetail>(`/public/products/${slug}`)
  return data
}

/** 公开公告（首页展示用）。 */
export interface PublicNotice {
  id: string
  level: string
  pinned: boolean
  title: string
  body: string | null
  publishAt: string | null
}

export async function fetchNotices(size = 20): Promise<PublicNotice[]> {
  const { data } = await http.get<PageResult<PublicNotice>>('/public/notices', {
    params: { 'page[number]': 0, 'page[size]': size }
  })
  return data.items
}

/** 公开文件地址（图片与视频统一走这个前缀，要求文件 visibility=PUBLIC）。 */
export function fileUrl(fileId: string): string {
  return `/api/v1/public/files/${fileId}`
}
