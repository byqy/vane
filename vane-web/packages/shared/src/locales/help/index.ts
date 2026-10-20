// 页面内帮助文案索引：按语言聚合 locales/help/<locale>.ts（页面级）与 locales/help/field/<locale>.ts（字段级）。
// 合并进 messages 的规则见 ../i18n.ts；键集合由 ../help.test.ts 与 ../field-help.test.ts 强制校验。

import ar from './ar'
import de from './de'
import en from './en'
import es from './es'
import fr from './fr'
import hi from './hi'
import id from './id'
import it from './it'
import ja from './ja'
import ko from './ko'
import nl from './nl'
import pl from './pl'
import ptBR from './pt-BR'
import ru from './ru'
import th from './th'
import tr from './tr'
import uk from './uk'
import vi from './vi'
import zhCN from './zh-CN'
import zhTW from './zh-TW'
import { fieldMessages } from './field'

const pageMessages: Record<string, Record<string, string>> = {
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

/** 页面级 + 字段级帮助文案，按语言合并后交给 i18n。 */
export const helpMessages: Record<string, Record<string, string>> = Object.fromEntries(
  Object.keys(pageMessages).map((locale) => [
    locale,
    { ...pageMessages[locale], ...(fieldMessages[locale] ?? {}) }
  ])
)
