import { describe, expect, it } from 'vitest'
import { browserTimeZone, formatDate, formatDateTime, toDateTimeInput, toIsoInstant } from './format'

describe('format', () => {
  it('browserTimeZone 返回 IANA 时区标识', () => {
    const zone = browserTimeZone()
    expect(typeof zone).toBe('string')
    expect(zone.length).toBeGreaterThan(0)
    expect(zone).not.toContain('+')
  })

  it('formatDateTime 按浏览器本地时区渲染', () => {
    // 02:00Z 在不同时区会渲染成不同的本地时间，这里用 Date 自己算期望值，避免依赖运行机器时区
    const iso = '2026-10-19T02:00:00Z'
    const expected = new Date(iso).toLocaleString('zh-CN')
    expect(formatDateTime(iso, 'zh-CN')).toBe(expected)
    expect(formatDateTime(iso, 'zh-CN')).not.toBe(iso)
  })

  it('formatDateTime / formatDate 对空值与非法值返回占位符', () => {
    expect(formatDateTime(null, 'zh-CN')).toBe('—')
    expect(formatDateTime('not-a-date', 'zh-CN')).toBe('—')
    expect(formatDate(undefined, 'zh-CN')).toBe('—')
    expect(formatDateTime('', 'zh-CN', '-')).toBe('-')
  })

  it('toDateTimeInput 把 UTC 转成选择器用的本地时间（无时区后缀）', () => {
    const iso = '2026-10-19T02:00:00Z'
    const local = new Date(iso)
    const pad = (value: number): string => String(value).padStart(2, '0')
    const expected =
      `${local.getFullYear()}-${pad(local.getMonth() + 1)}-${pad(local.getDate())}` +
      `T${pad(local.getHours())}:${pad(local.getMinutes())}:${pad(local.getSeconds())}`

    expect(toDateTimeInput(iso)).toBe(expected)
    expect(toDateTimeInput(iso)).not.toContain('Z')
    expect(toDateTimeInput(null)).toBe('')
    expect(toDateTimeInput('bad')).toBe('')
  })

  it('toIsoInstant 把本地时间转回 UTC，且与原始值可往返', () => {
    const iso = '2026-10-19T02:00:00Z'
    expect(toIsoInstant(toDateTimeInput(iso))).toBe(new Date(iso).toISOString())
    expect(toIsoInstant('')).toBeNull()
    expect(toIsoInstant(null)).toBeNull()
    expect(toIsoInstant('bad')).toBeNull()
  })

  it('本地时间不会被当成 UTC（时区偏移不是 0 时两者不同）', () => {
    const local = '2026-10-19T10:00:00'
    const asLocal = new Date(local).toISOString()
    const naiveUtc = new Date(local + 'Z').toISOString()
    expect(toIsoInstant(local)).toBe(asLocal)
    if (new Date().getTimezoneOffset() !== 0) {
      expect(toIsoInstant(local)).not.toBe(naiveUtc)
    }
  })
})

