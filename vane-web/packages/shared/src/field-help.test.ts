import { describe, expect, it } from 'vitest'
import { messages, locales } from './i18n'
import { fieldHelpKeys, fieldHelpOpenKey } from './field-help'

const zh = messages['zh-CN']

describe('表单字段级帮助文案', () => {
  it('每个字段都有说明文案', () => {
    for (const key of fieldHelpKeys) {
      expect(zh[key], key).toBeTruthy()
      expect(zh[key].trim(), key).not.toBe('')
    }
  })

  it('20 种语言都覆盖全部字段键', () => {
    for (const locale of locales) {
      for (const key of [fieldHelpOpenKey, ...fieldHelpKeys]) {
        expect(typeof messages[locale][key], `${locale}/${key}`).toBe('string')
        expect(messages[locale][key].trim(), `${locale}/${key}`).not.toBe('')
      }
    }
  })

  it('键命名符合 field.<page>.<field>.help', () => {
    for (const key of fieldHelpKeys) {
      expect(key, key).toMatch(/^field\.[a-z][a-zA-Z]*\.[a-zA-Z]+\.[a-z]+$/)
      expect(key.endsWith('.help'), key).toBe(true)
    }
    expect(fieldHelpOpenKey).toBe('fieldHelp.open')
  })

  it('字段键唯一，且与页面级帮助键不重叠', () => {
    expect(new Set(fieldHelpKeys).size).toBe(fieldHelpKeys.length)
    for (const key of fieldHelpKeys) {
      expect(typeof zh[`help.${key.split('.')[1]}.what`], key).toBe('string')
    }
  })

  it('插值占位符在各语言中保持一致', () => {
    const placeholders = (value: string) => (value.match(/\{[a-zA-Z0-9_]+\}/g) ?? []).sort()
    for (const key of [fieldHelpOpenKey, ...fieldHelpKeys]) {
      const expected = placeholders(zh[key])
      for (const locale of locales) {
        expect(placeholders(messages[locale][key]), `${locale}/${key}`).toEqual(expected)
      }
    }
  })
})
