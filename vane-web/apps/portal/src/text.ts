/** 富文本摘要：去掉 HTML 标签并压缩空白，用于列表/区块摘要（正文仍以 v-html 渲染）。 */
export function stripHtml(html: string | null | undefined, maxLength = 240): string {
  if (!html) {
    return ''
  }
  const text = html
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > maxLength ? `${text.slice(0, maxLength).trim()}…` : text
}
