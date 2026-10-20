import { createI18n } from 'vue-i18n'
import { isLocale, isRtl, messages, resolveLocale, setLocale as setRequestLocale, type Locale } from '@vane/shared'

/** localStorage 在 jsdom / SSR 环境可能不可用，统一入口避免启动即崩。 */
function storage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

const STORAGE_KEY = 'vane.portal.locale'

function initialLocale(): Locale {
  const stored = storage()?.getItem(STORAGE_KEY) ?? null
  if (stored && isLocale(stored)) {
    return stored
  }
  return resolveLocale(navigator.language, 'zh-CN')
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'zh-CN',
  messages
})

export function setLocale(locale: Locale): void {
  if (!isLocale(locale)) {
    return
  }
  i18n.global.locale.value = locale
  storage()?.setItem(STORAGE_KEY, locale)
  setRequestLocale(locale)
  document.documentElement.lang = locale
  document.documentElement.dir = isRtl(locale) ? 'rtl' : 'ltr'
}

/** 数据库里的 nameKey（nav.home / banner.welcome）映射到 portal.* 文案键。 */
export function portalMessageKey(nameKey: string): string {
  return nameKey.startsWith('portal.') ? nameKey : `portal.${nameKey}`
}

setLocale(initialLocale())



