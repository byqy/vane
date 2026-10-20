/** Problem Details（03 §2）。 */
export interface ProblemDetails {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  code?: string
  traceId?: string
  errors?: Array<{ pointer: string; code: string }>
}

export interface PageMeta {
  number: number
  size: number
  total: number
}

export interface PageResult<T> {
  items: T[]
  page: PageMeta
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface MenuItem {
  code: string
  parentCode: string | null
  nameKey: string
  path: string | null
  component: string | null
  icon: string | null
  sortNo: number
}

export interface MeResponse {
  mustChangePassword: boolean
  id: string
  username: string
  deptId: string
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
  /** 用户保存的界面语言偏好（BCP-47，未设置时为空）。 */
  locale?: string | null
  /** 用户时区（IANA ID，如 Asia/Shanghai；供异步场景格式化时间）。 */
  timezone?: string | null
}

/** 文章/分类的多语言译文（后台管理端）。 */
export interface ContentTranslation {
  locale: string
  title: string
  summary: string | null
  body: string | null
}

export interface CategoryTranslation {
  locale: string
  name: string
  description: string | null
}

/** 后台标签视图（带文章使用计数）。 */
export interface TagView {
  id: string
  code: string
  articleCount: number
  createdAt: string | null
}

/** 后台文章视图（含全部语言译文与标签）。 */
export interface ArticleView {
  id: string
  slug: string
  status: string
  categoryId: string | null
  coverFileId: string | null
  publishedAt: string | null
  tags: TagView[]
  translations: ContentTranslation[]
}

/** 后台分类视图。 */
export interface CategoryView {
  id: string
  parentId: string | null
  code: string
  sortNo: number
  status: string
  translations: CategoryTranslation[]
}

/** 后台分类树节点（业务上只有一级，children 恒为空）。 */
export interface CategoryNodeView extends CategoryView {
  children: CategoryNodeView[]
}

/** 门户文章列表项（不含正文）。 */
export interface PublicArticleView {
  id: string
  slug: string
  title: string | null
  summary: string | null
  coverFileId: string | null
  publishedAt: string | null
  categoryCode: string | null
  categoryName: string | null
  tags: string[]
}

/** 门户文章详情（正文 HTML）。 */
export interface PublicArticleDetail extends PublicArticleView {
  body: string | null
}

/** 门户分类（含已发布文章数与子节点）。 */
export interface PublicCategoryView {
  id: string
  code: string
  name: string
  description: string | null
  articleCount: number
  children: PublicCategoryView[]
}

/** 门户标签（含已发布文章数）。 */
export interface PublicTagView {
  id: string
  code: string
  articleCount: number
}

export interface UserSummary {
  id: string
  username: string
  nickname: string | null
  deptId: string
  deptName: string | null
  status: string
  roles: string[]
  createdAt: string | null
  lastLoginAt: string | null
  version: number
}

/** 短信服务商配置视图（密钥类字段为掩码）。 */
export interface SmsProviderView {
  code: string
  type: string
  name: string
  config: Record<string, string>
  countries: string[]
  optoutKeywords: string[]
  enabled: boolean
  isDefault: boolean
  keyVersion: string
}

/** 短信签名。 */
export interface SmsSignatureView {
  id: string
  providerCode: string
  signName: string
  countryCode: string
  locale: string
  status: string
  approvalStatus: string | null
}

/** 短信模板绑定。 */
export interface SmsBindingView {
  id: string | null
  templateId: string
  templateCode: string
  providerCode: string
  providerTemplateCode: string
  signatureId: string | null
  countryCode: string
  locale: string
  paramMappingJson: string
}

/** 短信发送日志。 */
export interface SmsLogView {
  id: string
  templateCode: string
  phoneMasked: string
  countryCode: string | null
  providerCode: string
  providerMessageId: string | null
  campaignId: string | null
  result: string
  errorCode: string | null
  durationMs: number | null
  traceId: string | null
  createdAt: string | null
}

/** 短信发送日志分页。 */
export interface SmsLogPage {
  items: SmsLogView[]
  page: { number: number; size: number; total: number }
}
