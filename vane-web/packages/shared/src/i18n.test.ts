import { describe, expect, it } from 'vitest'
import { isLocale, isRtl, languageName, localeLabels, locales, menuMessageKey, messages, resolveLocale } from './i18n'

const zh = messages['zh-CN']
const zhKeys = Object.keys(zh).sort()

describe('i18n messages', () => {
  it('语言清单为 20 种，且每种语言都有资源与母语名称', () => {
    expect(locales).toHaveLength(20)
    for (const locale of locales) {
      expect(messages[locale], locale).toBeTruthy()
      expect(localeLabels[locale], locale).toBeTruthy()
    }
  })

  it('每种语言的键集合与 zh-CN 完全一致', () => {
    for (const locale of locales) {
      expect(Object.keys(messages[locale]).sort(), locale).toEqual(zhKeys)
    }
  })

  it('语言名称按当前界面语言翻译', () => {
    expect(languageName('zh-CN', 'zh-CN')).toBe('中文')
    expect(languageName('en', 'zh-CN')).toBe('英语')
    expect(languageName('zh-CN', 'en')).toBe('Chinese')
    expect(languageName('en', 'en')).toBe('English')
  })

  it('未知语言代码回退为代码本身', () => {
    expect(languageName('xx-YY', 'zh-CN')).toBe('xx-YY')
  })

  it('没有空文案', () => {
    for (const locale of locales) {
      for (const [key, value] of Object.entries(messages[locale])) {
        expect(value.trim(), `${locale} / ${key}`).not.toBe('')
      }
    }
  })

  it('每种语言都已真正翻译（不是英文占位）', () => {
    const english = messages.en
    for (const locale of locales) {
      if (locale === 'en') {
        continue
      }
      const sameAsEnglish = zhKeys.filter((key) => messages[locale][key] === english[key]).length
      expect(sameAsEnglish, `${locale} 未翻译的条目过多`).toBeLessThan(zhKeys.length * 0.2)
    }
  })

  it('所有键使用点分层级', () => {
    for (const key of zhKeys) {
      expect(key).toMatch(/^[a-z][a-zA-Z0-9]*(\.[A-Za-z0-9_]+)+$/)
    }
  })

  it('插值占位符在各语言中保持一致', () => {
    const placeholders = (value: string) => (value.match(/\{[a-zA-Z0-9_]+\}/g) ?? []).sort()
    for (const key of zhKeys) {
      const expected = placeholders(zh[key])
      if (expected.length === 0) {
        continue
      }
      for (const locale of locales) {
        expect(placeholders(messages[locale][key]), `${locale} / ${key}`).toEqual(expected)
      }
    }
  })

  it('menu.nameKey 映射为菜单文案键', () => {
    expect(menuMessageKey('menu.system.user')).toBe('menu.system.user')
    expect(menuMessageKey('system.user')).toBe('menu.system.user')
  })

  it('门户与后台共用同一份资源', () => {
    expect(messages['zh-CN']['portal.site']).toBe('vane 门户')
    expect(messages.en['portal.site']).toBe('vane Portal')
    expect(messages['zh-CN']['app.title']).toBe('vane 管理后台')
  })

  it('语言匹配与 RTL 判定', () => {
    expect(isLocale('ja')).toBe(true)
    expect(isLocale('xx')).toBe(false)
    expect(resolveLocale('pt')).toBe('pt-BR')
    expect(resolveLocale('fr-CA')).toBe('fr')
    expect(resolveLocale('de-AT')).toBe('de')
    expect(resolveLocale(null)).toBe('zh-CN')
    expect(resolveLocale('zz', 'en')).toBe('en')
    expect(isRtl('ar')).toBe(true)
    expect(isRtl('en')).toBe(false)
  })
})

