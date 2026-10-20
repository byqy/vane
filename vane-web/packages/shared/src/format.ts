/** 按界面语言与浏览器本地时区格式化日期时间；空值或非法值返回占位符。 */
export function formatDateTime(value: string | null | undefined, locale: string, fallback = '—'): string {
  if (!value) {
    return fallback
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return fallback
  }
  return date.toLocaleString(locale)
}

/** 仅格式化日期部分（浏览器本地时区）。 */
export function formatDate(value: string | null | undefined, locale: string, fallback = '—'): string {
  if (!value) {
    return fallback
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return fallback
  }
  return date.toLocaleDateString(locale)
}

/** 浏览器当前 IANA 时区（如 Asia/Shanghai），取不到时回退 UTC。 */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

function pad2(value: number): string {
  return String(value).padStart(2, '0')
}

/**
 * UTC 时间串 → 日期选择器用的本地时间串 `YYYY-MM-DDTHH:mm:ss`（不带时区）。
 * 用于把接口返回的 UTC 值回填到 el-date-picker。
 */
export function toDateTimeInput(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return (
    `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}` +
    `T${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
  )
}

/**
 * 日期选择器的本地时间串 → UTC ISO-8601（带 Z），接口只接受 UTC 时间。
 * 空值返回 null；非法值同样返回 null，避免把无效时间提交到服务端。
 */
export function toIsoInstant(local: string | null | undefined): string | null {
  if (!local) {
    return null
  }
  const date = new Date(local)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date.toISOString()
}
