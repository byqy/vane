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

import CategoriesView from './CategoriesView.vue'

const CATEGORY = {
  id: 'c1',
  parentId: null,
  code: 'news',
  sortNo: 10,
  status: 'ACTIVE',
  translations: [{ locale: 'zh-CN', name: '新闻', description: '机构新闻' }],
  children: []
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(CategoriesView, { attachTo: document.body, global: { plugins: [i18n] } })
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

describe('CategoriesView', () => {
  it('渲染分类列表：编码、名称与状态', async () => {
    getMock.mockResolvedValue({ data: [CATEGORY] })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/categories')
    expect(wrapper.text()).toContain('news')
    expect(wrapper.text()).toContain('新闻')
    expect(wrapper.text()).toContain('启用')
    wrapper.unmount()
  })

  it('新建分类提交编码与多语言名称', async () => {
    getMock.mockResolvedValue({ data: [CATEGORY] })
    postMock.mockResolvedValue({ data: { ...CATEGORY, id: 'c2' } })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '新建分类')!.trigger('click')
    await flushPromises()

    const codeInput = document.querySelector('.el-dialog input') as HTMLInputElement
    await setInputValue(codeInput, 'notice')
    const nameInput = document.querySelector('.el-dialog .el-collapse input') as HTMLInputElement
    await setInputValue(nameInput, '公告')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]!
    expect(url).toBe('/admin/categories')
    expect(payload).toMatchObject({ code: 'notice', status: 'ACTIVE' })
    expect(payload.translations[0]).toMatchObject({ locale: 'zh-CN', name: '公告' })
    wrapper.unmount()
  })

  it('删除被拒绝时展示后端 detail', async () => {
    getMock.mockResolvedValue({ data: [CATEGORY] })
    deleteMock.mockRejectedValue({
      isAxiosError: true,
      response: { data: { status: 409, detail: '分类下仍有文章，无法删除' } }
    })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '删除')!.trigger('click')
    await flushPromises()
    const confirm = document.querySelector('.el-message-box__btns .el-button--primary') as HTMLButtonElement | null
    confirm!.click()
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith('/admin/categories/c1')
    expect(document.body.textContent).toContain('分类下仍有文章，无法删除')
    wrapper.unmount()
  })
})
