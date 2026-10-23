import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, aboutSeoMock } = vi.hoisted(() => ({ getMock: vi.fn(), aboutSeoMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

vi.mock('../seo', () => ({
  applyAboutSeo: aboutSeoMock,
  applyPageSeo: vi.fn(),
  applyHomeSeo: vi.fn(),
  applyContactSeo: vi.fn(),
  applyProductsSeo: vi.fn(),
  applyProductSeo: vi.fn(),
  applyNotFoundSeo: vi.fn(),
  localizeHref: (_locale: string, target: string | null) => target ?? '#'
}))

import AboutView from './AboutView.vue'

const PAGE = {
  id: 'p1',
  slug: 'about',
  title: '关于示例公司',
  summary: '我们提供示例服务。',
  body: '<p>公司正文</p>',
  coverFileId: null,
  publishedAt: '2026-10-19T00:00:00Z'
}

function mountAbout() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return { i18n, wrapper: mount(AboutView, { global: { plugins: [i18n] } }) }
}

describe('portal AboutView', () => {
  it('渲染公司介绍标题、摘要与正文，并写入 SEO', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    const { wrapper } = mountAbout()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/public/pages/about')
    expect(wrapper.get('.page-title').text()).toBe('关于示例公司')
    expect(wrapper.text()).toContain('我们提供示例服务。')
    expect(wrapper.html()).toContain('公司正文')
    expect(aboutSeoMock).toHaveBeenCalledWith('zh-CN', expect.objectContaining({ slug: 'about' }))
    wrapper.unmount()
  })

  it('未发布时展示未发布提示并写 noindex SEO', async () => {
    getMock.mockRejectedValue({ isAxiosError: true, response: { status: 404, data: { status: 404 } } })
    const { wrapper } = mountAbout()
    await flushPromises()

    expect(wrapper.text()).toContain('公司介绍尚未发布。')
    expect(aboutSeoMock).toHaveBeenCalledWith('zh-CN', null)
    wrapper.unmount()
  })

  it('接口失败展示错误态，重试后恢复内容', async () => {
    getMock
      .mockRejectedValueOnce({ isAxiosError: true, response: { status: 500, data: { status: 500, detail: '服务异常', traceId: 'trace-9' } } })
      .mockResolvedValueOnce({ data: PAGE })

    const { wrapper } = mountAbout()
    await flushPromises()

    expect(wrapper.text()).toContain('服务异常')
    expect(wrapper.text()).toContain('trace-9')

    await wrapper.get('.state__retry').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('关于示例公司')
    wrapper.unmount()
  })
})
