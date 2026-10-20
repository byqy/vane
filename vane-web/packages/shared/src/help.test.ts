import { describe, expect, it } from 'vitest'
import { messages, locales } from './i18n'
import { helpCommonKeys, helpKeys, helpLines, helpPages } from './help'

const zh = messages['zh-CN']

describe('页面内帮助文案', () => {
  it('每个页面都有用途与用法两条文案', () => {
    for (const page of helpPages) {
      const { what, steps } = helpKeys(page)
      expect(zh[what], what).toBeTruthy()
      expect(zh[steps], steps).toBeTruthy()
    }
  })

  it('20 种语言的帮助键集合一致', () => {
    const expected = helpPages.flatMap((page) => Object.values(helpKeys(page))).sort()
    for (const locale of locales) {
      const actual = expected.filter((key) => typeof messages[locale][key] === 'string')
      expect(actual, locale).toEqual(expected)
    }
  })

  it('用法按行分条且每条以项目符号开头', () => {
    for (const page of helpPages) {
      const { steps } = helpKeys(page)
      const source = zh[steps]
      expect(source.split('\n').length, page).toBeGreaterThanOrEqual(2)
      for (const line of source.split('\n')) {
        expect(line.trim().startsWith('·'), `${page}: ${line}`).toBe(true)
      }
      expect(helpLines(source).every((line) => !line.startsWith('·')), page).toBe(true)
    }
  })

  it('翻译里的分条数量与中文一致', () => {
    for (const page of helpPages) {
      const { steps } = helpKeys(page)
      const expected = helpLines(zh[steps]).length
      for (const locale of locales) {
        expect(helpLines(messages[locale][steps]).length, `${locale}/${steps}`).toBe(expected)
      }
    }
  })

  it('通用文案齐全，标题带 {name} 占位符', () => {
    for (const locale of locales) {
      const dict = messages[locale]
      expect(dict[helpCommonKeys.what], locale).toBeTruthy()
      expect(dict[helpCommonKeys.steps], locale).toBeTruthy()
      expect(dict[helpCommonKeys.open], locale).toBeTruthy()
      expect(dict[helpCommonKeys.title], locale).toContain('{name}')
    }
  })

  it('页面标识唯一', () => {
    expect(new Set(helpPages).size).toBe(helpPages.length)
    expect(helpPages.length).toBe(19)
  })
})
