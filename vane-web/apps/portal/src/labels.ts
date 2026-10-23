import { portalMessageKey } from './i18n'

/**
 * 导航/轮播的 labelKey（nav.about、banner.welcome）映射到 portal.* 文案。
 * 资源里没有对应键时退化为 key 的最后一段，避免把内部标识直接显示给访客。
 */
export function portalLabel(t: (key: string) => string, labelKey: string | null | undefined): string {
  if (!labelKey) {
    return ''
  }
  const messageKey = portalMessageKey(labelKey)
  const translated = t(messageKey)
  if (translated !== messageKey) {
    return translated
  }
  return labelKey.split('.').pop() ?? labelKey
}
