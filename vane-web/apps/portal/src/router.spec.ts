import { describe, expect, it, vi } from 'vitest'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import { i18n } from './i18n'
import { router } from './router'

describe('portal router', () => {
  it('解析官网新增路由，且语言前缀进入 params', () => {
    expect(router.resolve('/zh-CN').name).toBe('home')
    expect(router.resolve('/zh-CN/about').name).toBe('about')
    expect(router.resolve('/zh-CN/products').name).toBe('products')
    expect(router.resolve('/zh-CN/products/alpha')).toMatchObject({
      name: 'product',
      params: { locale: 'zh-CN', slug: 'alpha' }
    })
    expect(router.resolve('/en/contact').name).toBe('contact')
    // 既有路由保持可用
    expect(router.resolve('/zh-CN/articles').name).toBe('articles')
    expect(router.resolve('/zh-CN/articles/hello').name).toBe('article')
    expect(router.resolve('/zh-CN/categories/news').name).toBe('category')
    expect(router.resolve('/zh-CN/legacy-page')).toMatchObject({
      name: 'page',
      params: { slug: 'legacy-page' }
    })
  })

  it('没有语言前缀的旧链接补上当前语言', async () => {
    const locale = i18n.global.locale.value
    await router.push('/about')
    await router.isReady()
    expect(router.currentRoute.value.fullPath).toBe(`/${locale}/about`)
    expect(router.currentRoute.value.name).toBe('about')

    await router.push('/products/alpha')
    expect(router.currentRoute.value.fullPath).toBe(`/${locale}/products/alpha`)
    expect(router.currentRoute.value.name).toBe('product')
  })
})
