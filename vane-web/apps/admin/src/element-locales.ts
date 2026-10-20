import type { Locale } from '@vane/shared'

type ElementLocaleModule = { default: Record<string, unknown> }

/**
 * Element Plus 内置文案（分页、日期选择器、表格空态等）的语言包。
 * 按需动态加载：切换语言时才拉取对应语言包，避免首屏带 20 份。
 */
const loaders: Record<Locale, () => Promise<ElementLocaleModule>> = {
  'zh-CN': () => import('element-plus/es/locale/lang/zh-cn'),
  'zh-TW': () => import('element-plus/es/locale/lang/zh-tw'),
  en: () => import('element-plus/es/locale/lang/en'),
  ja: () => import('element-plus/es/locale/lang/ja'),
  ko: () => import('element-plus/es/locale/lang/ko'),
  ru: () => import('element-plus/es/locale/lang/ru'),
  de: () => import('element-plus/es/locale/lang/de'),
  fr: () => import('element-plus/es/locale/lang/fr'),
  es: () => import('element-plus/es/locale/lang/es'),
  'pt-BR': () => import('element-plus/es/locale/lang/pt-br'),
  it: () => import('element-plus/es/locale/lang/it'),
  nl: () => import('element-plus/es/locale/lang/nl'),
  pl: () => import('element-plus/es/locale/lang/pl'),
  tr: () => import('element-plus/es/locale/lang/tr'),
  uk: () => import('element-plus/es/locale/lang/uk'),
  vi: () => import('element-plus/es/locale/lang/vi'),
  th: () => import('element-plus/es/locale/lang/th'),
  id: () => import('element-plus/es/locale/lang/id'),
  ar: () => import('element-plus/es/locale/lang/ar'),
  hi: () => import('element-plus/es/locale/lang/hi')
}

const cache = new Map<Locale, Record<string, unknown>>()

export async function loadElementLocale(locale: Locale): Promise<Record<string, unknown>> {
  const cached = cache.get(locale)
  if (cached) {
    return cached
  }
  try {
    const module = await loaders[locale]()
    cache.set(locale, module.default)
    return module.default
  } catch {
    const fallback = await loaders.en()
    return fallback.default
  }
}
