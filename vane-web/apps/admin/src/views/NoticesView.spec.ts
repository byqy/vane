import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, patch: patchMock, delete: deleteMock } }
})

import NoticesView from './NoticesView.vue'

const NOTICE = {
  id: 'n1',
  level: 'INFO',
  status: 'DRAFT',
  pinned: false,
  publishAt: null,
  expireAt: null,
  translations: [
    { locale: 'zh-CN', title: '系统维护', body: '今晚维护' },
    { locale: 'ja', title: 'メンテナンス', body: '本日保守' }
  ]
}

function mountNotices() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(NoticesView, {
    attachTo: document.body,
    global: { plugins: [i18n] }
  })
}

/** el-dialog 通过 teleport 挂到 body，直接查 DOM。 */
function dialogTitleInput(): HTMLInputElement {
  return document.querySelector('.el-dialog .el-collapse input') as HTMLInputElement
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

function buttonByText(wrapper: ReturnType<typeof mountNotices>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('NoticesView', () => {
  it('加载后渲染译文标题', async () => {
    getMock.mockResolvedValue({ data: { items: [NOTICE], page: { number: 0, size: 20, total: 1 } } })
    const wrapper = mountNotices()
    await flushPromises()

    expect(wrapper.text()).toContain('系统维护')
    expect(getMock).toHaveBeenCalledWith('/admin/notices', { params: { 'page[number]': 0, 'page[size]': 20 } })
    wrapper.unmount()
  })

  it('新建公告提交多语言译文', async () => {
    getMock.mockResolvedValue({ data: { items: [NOTICE], page: { number: 0, size: 20, total: 1 } } })
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountNotices()
    await flushPromises()
    await buttonByText(wrapper, '新建公告')!.trigger('click')
    await flushPromises()

    await setInputValue(dialogTitleInput(), '新公告')
    const buttons = footerButtons()
    buttons.at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/notices', {
      level: 'INFO',
      pinned: false,
      publishAt: null,
      expireAt: null,
      translations: [{ locale: 'zh-CN', title: '新公告', body: '' }]
    })
    wrapper.unmount()
  })

  it('编辑公告走 PATCH 并保留其它语言', async () => {
    getMock.mockResolvedValue({ data: { items: [NOTICE], page: { number: 0, size: 20, total: 1 } } })
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountNotices()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    await setInputValue(dialogTitleInput(), '系统维护（改）')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledTimes(1)
    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/notices/n1')
    expect(payload.translations.map((row: { locale: string }) => row.locale)).toEqual(['zh-CN', 'ja'])
    expect(payload.translations[0].title).toBe('系统维护（改）')
    wrapper.unmount()
  })

  it('没有任何译文标题时本地拦截，不发请求', async () => {
    getMock.mockResolvedValue({ data: { items: [NOTICE], page: { number: 0, size: 20, total: 1 } } })

    const wrapper = mountNotices()
    await flushPromises()
    await buttonByText(wrapper, '新建公告')!.trigger('click')
    await flushPromises()
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).not.toHaveBeenCalled()
    expect(patchMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
