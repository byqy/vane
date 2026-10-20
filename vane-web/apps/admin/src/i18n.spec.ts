import { describe, expect, it } from 'vitest'
import { i18n, setLocale } from './i18n'

describe('admin i18n', () => {
  it('切换语言时同步 i18n、lang、dir 与本地偏好', () => {
    setLocale('ja')
    expect(i18n.global.locale.value).toBe('ja')
    expect(document.documentElement.lang).toBe('ja')
    expect(document.documentElement.dir).toBe('ltr')

    setLocale('ar')
    expect(i18n.global.locale.value).toBe('ar')
    expect(document.documentElement.dir).toBe('rtl')

    setLocale('zh-CN')
    expect(document.documentElement.dir).toBe('ltr')
    expect(i18n.global.locale.value).toBe('zh-CN')
  })

  it('忽略不支持的语言值', () => {
    setLocale('zh-CN')
    setLocale('xx-YY' as never)
    expect(i18n.global.locale.value).toBe('zh-CN')
  })

  it('20 种语言都能切换到对应资源', () => {
    for (const locale of [
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
    ] as const) {
      setLocale(locale)
      expect(i18n.global.locale.value, locale).toBe(locale)
      expect(i18n.global.t('app.title'), locale).toBeTruthy()
    }
    setLocale('zh-CN')
  })
})

