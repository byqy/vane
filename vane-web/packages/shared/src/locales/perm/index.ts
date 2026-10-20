// 权限点显示名称索引：按语言聚合 locales/perm/<locale>.ts，合并规则见 ../i18n.ts。
// 键由 PermissionCatalog 的 code 派生（ADR-021），20 种语言必须齐全（permission.test.ts 强制校验）。

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

export const permMessages: Record<string, Record<string, string>> = {
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
