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

/* ---------------------------------- 产品（ADR-025） ---------------------------------- */

/** 门户产品列表项。 */
export interface PublicProductView {
  id: string
  code: string
  slug: string
  group: string | null
  coverFileId: string | null
  videoFileId: string | null
  name: string
  summary: string | null
  sortNo: number
  publishedAt: string | null
}

/** 产品媒体项（图集）。 */
export interface PublicProductMedia {
  fileId: string
  kind: string
  sortNo: number
  caption: string | null
}

/** 产品特性条目。 */
export interface PublicProductFeature {
  title: string
  description: string | null
}

/** 门户产品详情。 */
export interface PublicProductDetail {
  id: string
  code: string
  slug: string
  group: string | null
  coverFileId: string | null
  videoFileId: string | null
  name: string
  summary: string | null
  body: string | null
  features: PublicProductFeature[]
  media: PublicProductMedia[]
  sortNo: number
  publishedAt: string | null
}

/** 后台产品多语言译文。 */
export interface ProductTranslation {
  locale: string
  name: string
  summary: string | null
  body: string | null
  features: PublicProductFeature[]
}

/** 后台产品列表项。 */
export interface ProductAdminView {
  id: string
  code: string
  slug: string
  group: string | null
  coverFileId: string | null
  videoFileId: string | null
  status: string
  sortNo: number
  publishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
  translations: ProductTranslation[]
}

/** 后台产品媒体项。 */
export interface ProductMediaItem {
  id: string
  productId: string
  fileId: string
  kind: string
  sortNo: number
  caption: string | null
  updatedAt: string | null
}

/** 后台产品特性条目（编辑态）。 */
export interface ProductFeatureItem {
  title: string
  description: string | null
}

/** 后台产品详情。 */
export interface ProductAdminDetail {
  id: string
  code: string
  slug: string
  group: string | null
  coverFileId: string | null
  videoFileId: string | null
  status: string
  sortNo: number
  publishedAt: string | null
  createdAt: string | null
  updatedAt: string | null
  translations: ProductTranslation[]
  media: ProductMediaItem[]
}

/** 后台产品分页结果。 */
export interface ProductAdminPage extends PageResult<ProductAdminView> {}

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
  deliveryStatus: string | null
}

/** 短信发送日志分页。 */
export interface SmsLogPage {
  items: SmsLogView[]
  page: { number: number; size: number; total: number }
}


/** 短信群发活动。 */
export interface SmsCampaignView {
  id: string
  name: string
  templateId: string
  templateCode: string
  providerCode: string
  signatureId: string | null
  status: string
  scheduledAt: string | null
  throttlePerSecond: number
  dailyLimit: number | null
  budgetAmount: number | null
  locale: string
  total: number
  sent: number
  failed: number
  createdAt: string | null
}

/** 群发 CSV 预校验/导入结果。 */
export interface SmsImportResult {
  total: number
  valid: number
  invalid: number
  duplicates: number
  optedOut: number
  errors: Array<{ line: number; phone: string | null; errorCode: string }>
}

/** 群发收件人。 */
export interface SmsRecipientView {
  id: string
  phoneE164: string | null
  countryCode: string | null
  paramsJson: string
  status: string
  errorCode: string | null
  attempts: number
  sentAt: string | null
}

export interface SmsCampaignPage {
  items: SmsCampaignView[]
  page: { number: number; size: number; total: number }
}

export interface SmsRecipientPage {
  items: SmsRecipientView[]
  page: { number: number; size: number; total: number }
}


/** 短信回执明细。 */
export interface SmsReceiptView {
  id: string
  logId: string
  providerMessageId: string | null
  status: string
  providerStatus: string | null
  occurredAt: string | null
  rawJson: string | null
  createdAt: string | null
}

/** 短信退订记录。 */
export interface SmsOptoutView {
  id: string
  phoneHash: string
  scope: string
  source: string
  reason: string | null
  createdAt: string | null
}

/** 短信上行消息。 */
export interface SmsInboundView {
  id: string
  providerCode: string
  providerMessageId: string | null
  phoneMasked: string
  content: string
  receivedAt: string | null
  processed: boolean
  matchedKeyword: string | null
}

export interface SmsOptoutPage {
  items: SmsOptoutView[]
  page: { number: number; size: number; total: number }
}

export interface SmsInboundPage {
  items: SmsInboundView[]
  page: { number: number; size: number; total: number }
}

/** 操作日志列表项（03 §19.1）：不含 params 与 changes。 */
export interface OperLogSummary {
  id: string
  username: string | null
  module: string
  action: string
  source: string
  targetType: string | null
  targetId: string | null
  targetLabel: string | null
  httpMethod: string | null
  path: string | null
  httpStatus: number | null
  result: string
  errorCode: string | null
  durationMs: number | null
  affectedCount: number | null
  ip: string | null
  userAgent: string | null
  deptId: string | null
  traceId: string | null
  createdAt: string
}

/** 字段级变更项。 */
export interface OperLogChange {
  field: string
  before: unknown
  after: unknown
}

/** 操作日志详情（03 §19.1）：含 params 与 changes。 */
export interface OperLogDetail extends OperLogSummary {
  params: string | null
  changes: OperLogChange[] | null
  changesVersion: number | null
  changesTruncated: boolean
}

/** 邮件服务商（ADR-024）：password 恒为掩码。 */
export interface MailProviderView {
  code: string
  name: string
  host: string
  port: number
  tlsMode: string
  username: string | null
  password: string | null
  fromAddress: string
  fromName: string | null
  enabled: boolean
  isDefault: boolean
  dailyLimit: number
  timeoutMs: number
}

/** 邮件发送日志（ADR-024）。 */
export interface MailLogView {
  id: string
  providerCode: string
  templateCode: string
  locale: string | null
  toMasked: string
  toDomain: string | null
  subject: string | null
  status: string
  errorCode: string | null
  errorDetail: string | null
  attempts: number
  durationMs: number | null
  traceId: string | null
  createdAt: string | null
  sentAt: string | null
}

export interface MailLogPage {
  items: MailLogView[]
  page: { number: number; size: number; total: number }
}

/** 消息模板（EMAIL / SMS / INAPP）。 */
export interface MessageTemplateTranslation {
  locale: string
  subject: string | null
  body: string
}

export interface MessageTemplateView {
  id: string
  code: string
  channel: string
  status: string
  remark: string | null
  translations: MessageTemplateTranslation[]
}
