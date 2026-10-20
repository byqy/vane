import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  deleteMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, delete: deleteMock } }
})

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ can: () => true })
}))

import TagsView from './TagsView.vue'

const TAG = { id: 't1', code: 'release', articleCount: 3, createdAt: '2026-10-19T00:00:00Z' }

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(TagsView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

afterEach(() => {
  document.body.innerHTML = ''
})

describe('TagsView', () => {
  it('渲染标签列表：编码与文章数', async () => {
    getMock.mockResolvedValue({ data: [TAG] })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/tags')
    expect(wrapper.text()).toContain('release')
    expect(wrapper.text()).toContain('3')
    wrapper.unmount()
  })

  it('新建标签提交编码', async () => {
    getMock.mockResolvedValue({ data: [TAG] })
    postMock.mockResolvedValue({ data: { ...TAG, id: 't2' } })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '新建标签')!.trigger('click')
    await flushPromises()

    const codeInput = document.querySelector('.el-dialog input') as HTMLInputElement
    await setInputValue(codeInput, 'update')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/tags', { code: 'update' })
    wrapper.unmount()
  })

  it('删除标签调用删除接口', async () => {
    getMock.mockResolvedValue({ data: [TAG] })
    deleteMock.mockResolvedValue({ data: {} })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '删除')!.trigger('click')
    await flushPromises()
    const confirm = document.querySelector('.el-message-box__btns .el-button--primary') as HTMLButtonElement | null
    confirm!.click()
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith('/admin/tags/t1')
    wrapper.unmount()
  })
})
