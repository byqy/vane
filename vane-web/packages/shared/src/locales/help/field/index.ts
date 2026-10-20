// 字段级帮助文案索引：按语言聚合 locales/help/field/<locale>.ts。
// 与页面级帮助（locales/help/<locale>.ts）分文件维护，合并规则见 ../index.ts。

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

export const fieldMessages: Record<string, Record<string, string>> = {
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
