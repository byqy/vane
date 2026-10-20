/**
 * 后台表单「字段级」帮助的键约定。
 *
 * 与页面级帮助（`help.ts` 的 `helpPages` / `help.<page>.what`）分工：
 * 页面级讲这个页面整体怎么用，字段级讲单个字段填什么、有什么坑。
 * 字段键为 `field.<page>.<field>.help`，文案在 `locales/help/field/<locale>.ts`，
 * 清单由本文件的 `fieldHelpKeys` 约束，键集合由 `field-help.test.ts` 强制校验。
 */
export const fieldHelpKeys = [
  'field.navs.labelKey.help',
  'field.navs.target.help',
  'field.navs.sortNo.help',
  'field.pages.slug.help',
  'field.articles.slug.help',
  'field.articles.category.help',
  'field.articles.tags.help',
  'field.articles.newTag.help',
  'field.categories.code.help',
  'field.tags.code.help',
  'field.users.username.help',
  'field.users.password.help',
  'field.users.dept.help',
  'field.users.roles.help',
  'field.users.status.help',
  'field.roles.code.help',
  'field.roles.dataScope.help',
  'field.configs.type.help',
  'field.configs.value.help',
  'field.dicts.typeCode.help',
  'field.dicts.nameKey.help',
  'field.dicts.itemValue.help',
  'field.dicts.labelZh.help',
  'field.jobs.cron.help',
  'field.jobs.params.help',
  'field.jobs.concurrency.help',
  'field.banners.image.help',
  'field.banners.link.help',
  'field.banners.startAt.help',
  'field.banners.endAt.help',
  'field.mail.variables.help',
  'field.notices.publishAt.help',
  'field.notices.expireAt.help'
] as const

export type FieldHelpKey = (typeof fieldHelpKeys)[number]

/** 字段级帮助入口（「?」）的无障碍名称。 */
export const fieldHelpOpenKey = 'fieldHelp.open' as const
