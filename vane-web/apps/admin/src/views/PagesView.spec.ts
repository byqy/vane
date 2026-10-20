import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, patch: patchMock, delete: vi.fn() } }
})

import PagesView from './PagesView.vue'

const PAGE = {
  id: 'p1',
  slug: 'about',
  status: 'DRAFT',
  coverFileId: null,
  translations: [
    { locale: 'zh-CN', title: '关于我们', summary: '简介', body: '<p>正文</p>' },
    { locale: 'en', title: 'About us', summary: 'Intro', body: '<p>Body</p>' }
  ]
}

function mountPages() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(PagesView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function dialogInputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll('.el-dialog .el-collapse input')) as HTMLInputElement[]
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

function buttonByText(wrapper: ReturnType<typeof mountPages>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('PagesView', () => {
  it('渲染内容页标题', async () => {
    getMock.mockResolvedValue({ data: { items: [PAGE], page: { number: 0, size: 20, total: 1 } } })
    const wrapper = mountPages()
    await flushPromises()

    expect(wrapper.text()).toContain('关于我们')
    wrapper.unmount()
  })

  it('新建内容页提交 slug 与多语言译文', async () => {
    getMock.mockResolvedValue({ data: { items: [PAGE], page: { number: 0, size: 20, total: 1 } } })
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountPages()
    await flushPromises()
    await buttonByText(wrapper, '新建')!.trigger('click')
    await flushPromises()

    const slugInput = document.querySelector('.el-dialog input') as HTMLInputElement
    await setInputValue(slugInput, 'contact')
    await setInputValue(dialogInputs()[0], '联系我们')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]
    expect(url).toBe('/admin/pages')
    expect(payload.slug).toBe('contact')
    expect(payload.translations[0]).toMatchObject({ locale: 'zh-CN', title: '联系我们' })
    wrapper.unmount()
  })

  it('编辑内容页走 PATCH 并保留已有语言', async () => {
    getMock.mockResolvedValue({ data: { items: [PAGE], page: { number: 0, size: 20, total: 1 } } })
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountPages()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    await setInputValue(dialogInputs()[0], '关于我们（改）')
    footerButtons().at(-1)!.click()
    await flushPromises()

    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/pages/p1')
    expect(payload.translations.map((row: { locale: string }) => row.locale)).toEqual(['zh-CN', 'en'])
    expect(payload.translations[0].title).toBe('关于我们（改）')
    wrapper.unmount()
  })

  it('slug 非法时本地拦截', async () => {
    getMock.mockResolvedValue({ data: { items: [PAGE], page: { number: 0, size: 20, total: 1 } } })

    const wrapper = mountPages()
    await flushPromises()
    await buttonByText(wrapper, '新建')!.trigger('click')
    await flushPromises()

    const slugInput = document.querySelector('.el-dialog input') as HTMLInputElement
    await setInputValue(slugInput, 'Bad Slug!')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
