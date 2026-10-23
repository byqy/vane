import { problemOf } from '@vane/shared'

/**
 * 把接口错误转成可展示文本：优先 Problem Details 的 detail，再 title，
 * 并附上 code 与 traceId，便于与后端日志串联（03 §2）。
 */
export function problemText(error: unknown, fallback: string): string {
  const problem = problemOf(error)
  if (!problem) {
    return fallback
  }
  const base = problem.detail || problem.title || fallback
  const meta = [problem.code, problem.traceId].filter((value): value is string => Boolean(value))
  return meta.length > 0 ? `${base} (${meta.join(' · ')})` : base
}

/** 是否是「资源不存在」：内容未发布、产品归档都走 404。 */
export function isNotFound(error: unknown): boolean {
  return problemOf(error)?.status === 404
}
