/**
 * 前后台共用的界面文案。
 *
 * 语言清单以 docs/04 为唯一权威；新增语言时必须同时提供 locales/<locale>.ts
 * 且键集合与 zh-CN 完全一致（i18n.test.ts 强制校验）。
 * 缺键时 vue-i18n 回退到 fallbackLocale（zh-CN），便于发现遗漏。
 */
import ar from './locales/ar'
import de from './locales/de'
import en from './locales/en'
import es from './locales/es'
import fr from './locales/fr'
import hi from './locales/hi'
import id from './locales/id'
import it from './locales/it'
import ja from './locales/ja'
import ko from './locales/ko'
import nl from './locales/nl'
import pl from './locales/pl'
import ptBR from './locales/pt-BR'
import ru from './locales/ru'
import th from './locales/th'
import tr from './locales/tr'
import uk from './locales/uk'
import vi from './locales/vi'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import { helpMessages } from './locales/help'
import { permMessages } from './locales/perm'

export const locales = [
  'zh-CN',
  'zh-TW',
  'en',
  'ja',
  'ko',
  'ru',
  'de',
  'fr',
  'es',
  'pt-BR',
  'it',
  'nl',
  'pl',
  'tr',
  'uk',
  'vi',
  'th',
  'id',
  'ar',
  'hi'
] as const

export type Locale = (typeof locales)[number]

/** 语言切换器显示的母语名称，不随界面语言变化。 */
export const localeLabels: Record<Locale, string> = {
  'zh-CN': '简体中文',
  'zh-TW': '繁體中文',
  en: 'English',
  ja: '日本語',
  ko: '한국어',
  ru: 'Русский',
  de: 'Deutsch',
  fr: 'Français',
  es: 'Español',
  'pt-BR': 'Português (Brasil)',
  it: 'Italiano',
  nl: 'Nederlands',
  pl: 'Polski',
  tr: 'Türkçe',
  uk: 'Українська',
  vi: 'Tiếng Việt',
  th: 'ไทย',
  id: 'Bahasa Indonesia',
  ar: 'العربية',
  hi: 'हिन्दी'
}

/** 从右往左书写的语言（需要设置 dir="rtl"）。 */
export const rtlLocales: readonly Locale[] = ['ar']

export function isLocale(value: string): value is Locale {
  return (locales as readonly string[]).includes(value)
}

export function isRtl(locale: string): boolean {
  return (rtlLocales as readonly string[]).includes(locale)
}

/** Accept-Language 匹配：先精确匹配，再退到同语言的基础标签（如 pt 匹配 pt-BR）。 */
export function resolveLocale(tag: string | null | undefined, fallback: Locale = 'zh-CN'): Locale {
  if (!tag) {
    return fallback
  }
  const normalized = tag.trim()
  if (isLocale(normalized)) {
    return normalized
  }
  const base = normalized.split('-')[0].toLowerCase()
  const exactBase = locales.find((locale) => locale.toLowerCase() === base)
  if (exactBase) {
    return exactBase
  }
  const regional = locales.find((locale) => locale.toLowerCase().startsWith(`${base}-`))
  return regional ?? fallback
}

const baseMessages: Record<Locale, Record<string, string>> = {
  'zh-CN': zhCN,
  'zh-TW': zhTW,
  en,
  ja,
  ko,
  ru,
  de,
  fr,
  es,
  'pt-BR': ptBR,
  it,
  nl,
  pl,
  tr,
  uk,
  vi,
  th,
  id,
  ar,
  hi
}

/**
 * 界面文案 = 基础文案 + 页面内帮助文案 + 权限点名称。
 *
 * 帮助与权限点名称单独放在 locales/help、locales/perm 下：内容成组、体积大，
 * 与基础文案分文件维护，合并后仍满足「各语言键集合一致」的门禁。
 */
export const messages: Record<Locale, Record<string, string>> = Object.fromEntries(
  locales.map((locale) => [
    locale,
    { ...baseMessages[locale], ...(helpMessages[locale] ?? {}), ...(permMessages[locale] ?? {}) }
  ])
) as Record<Locale, Record<string, string>>

/** 数据库里的菜单/权限 nameKey 形如 menu.system.user，这里映射到文案键。 */
export function menuMessageKey(nameKey: string): string {
  return nameKey.startsWith('menu.') ? nameKey : `menu.${nameKey}`
}


