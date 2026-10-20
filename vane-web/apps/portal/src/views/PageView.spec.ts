import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { slug: 'about' } }),
  useRouter: () => ({ push: vi.fn() }),
  RouterLink: { template: '<a><slot /></a>' }
}))

vi.mock('../seo', () => ({
  applyPageSeo: vi.fn(),
  applyNotFoundSeo: vi.fn(),
  applyHomeSeo: vi.fn(),
  localizeHref: (_locale: string, target: string | null) => target ?? '#'
}))

import PageView from './PageView.vue'

function pageOf(title: string, body: string) {
  return {
    id: 'p1',
    slug: 'about',
    title,
    summary: null,
    body,
    coverFileId: null,
    publishedAt: '2026-10-19T00:00:00Z'
  }
}

function mountPage() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return { i18n, wrapper: mount(PageView, {
    global: {
      plugins: [i18n],
        stubs: { SiteHeader: true },
      components: { 'router-link': { template: '<a><slot /></a>' } }
    }
  }) }
}

describe('portal PageView', () => {

  it('切换语言后重新拉取内容页正文', async () => {
    let call = 0
    getMock.mockImplementation(() => {
      call++
      return Promise.resolve({
        data: call <= 1 ? pageOf('关于我们', '<p>中文正文</p>') : pageOf('About us', '<p>English body</p>')
      })
    })

    const { i18n, wrapper } = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('关于我们')
    const before = getMock.mock.calls.length

    i18n.global.locale.value = 'en'
    await flushPromises()

    expect(getMock.mock.calls.length).toBeGreaterThan(before)
    expect(wrapper.text()).toContain('About us')
    expect(wrapper.html()).toContain('English body')
  })
})



