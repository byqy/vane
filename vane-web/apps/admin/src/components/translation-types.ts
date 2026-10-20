/** 多语言译文编辑器的数据类型（从 .vue 抽出，便于测试与跨文件复用）。 */

export interface TranslationField {
  key: string
  labelKey: string
  textarea?: boolean
}

export type TranslationRow = Record<string, string> & { locale: string }
