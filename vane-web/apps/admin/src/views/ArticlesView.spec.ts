import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock, putMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return {
    ...actual,
    http: { get: getMock, post: postMock, patch: patchMock, put: putMock, delete: deleteMock }
  }
})

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ can: () => true })
}))

import ArticlesView from './ArticlesView.vue'

const TAG = { id: 't1', code: 'release', articleCount: 1, createdAt: '2026-10-19T00:00:00Z' }

const CATEGORY = {
  id: 'c1',
  parentId: null,
  code: 'news',
  sortNo: 10,
  status: 'ACTIVE',
  translations: [{ locale: 'zh-CN', name: '新闻', description: null }],
  children: []
}

const ARTICLE = {
  id: 'a1',
  slug: 'hello',
  status: 'DRAFT',
  categoryId: 'c1',
  coverFileId: null,
  publishedAt: null,
  tags: [TAG],
  translations: [{ locale: 'zh-CN', title: '你好世界', summary: '摘要', body: '<p>正文</p>' }]
}

function mockLoads(): void {
  getMock.mockImplementation((url: string) => {
    if (url === '/admin/articles') {
      return Promise.resolve({ data: { items: [ARTICLE], page: { number: 0, size: 20, total: 1 } } })
    }
    if (url === '/admin/categories') {
      return Promise.resolve({ data: [CATEGORY] })
    }
    if (url === '/admin/tags') {
      return Promise.resolve({ data: [TAG] })
    }
    return Promise.resolve({ data: [] })
  })
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ArticlesView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function dialogInputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll('.el-dialog .el-collapse input')) as HTMLInputElement[]
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

describe('ArticlesView', () => {
  it('渲染文章列表：标题、分类、标签与状态', async () => {
    mockLoads()
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('你好世界')
    expect(wrapper.text()).toContain('新闻 (news)')
    expect(wrapper.text()).toContain('release')
    expect(wrapper.text()).toContain('草稿')
    wrapper.unmount()
  })

  it('新建文章提交 slug 与多语言标题', async () => {
    mockLoads()
    postMock.mockResolvedValue({ data: { ...ARTICLE, id: 'a2' } })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '新建文章')!.trigger('click')
    await flushPromises()

    const slugInput = document.querySelector('.el-dialog input') as HTMLInputElement
    await setInputValue(slugInput, 'about-us')
    await setInputValue(dialogInputs()[0]!, '关于我们')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]!
    expect(url).toBe('/admin/articles')
    expect(payload.slug).toBe('about-us')
    expect(payload.translations[0]).toMatchObject({ locale: 'zh-CN', title: '关于我们' })
    wrapper.unmount()
  })

  it('编辑文章时整体替换标签', async () => {
    mockLoads()
    patchMock.mockResolvedValue({ data: ARTICLE })
    putMock.mockResolvedValue({ data: {} })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(patchMock.mock.calls[0]![0]).toBe('/admin/articles/a1')
    expect(putMock).toHaveBeenCalledWith('/admin/articles/a1/tags', { tagIds: ['t1'] })
    wrapper.unmount()
  })

  it('发布与删除分别调用发布接口和删除接口', async () => {
    mockLoads()
    postMock.mockResolvedValue({ data: { ...ARTICLE, status: 'PUBLISHED' } })
    deleteMock.mockResolvedValue({ data: {} })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, '发布')!.trigger('click')
    await flushPromises()
    expect(postMock).toHaveBeenCalledWith('/admin/articles/a1/publish')
    expect(getMock.mock.calls.filter(([url]) => url === '/admin/articles').length).toBe(2)

    await buttonByText(wrapper, '删除')!.trigger('click')
    await flushPromises()
    const confirm = document.querySelector('.el-message-box__btns .el-button--primary') as HTMLButtonElement | null
    expect(confirm).not.toBeNull()
    confirm!.click()
    await flushPromises()
    expect(deleteMock).toHaveBeenCalledWith('/admin/articles/a1')
    wrapper.unmount()
  })

  it('加载失败时展示错误态', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.find('.el-alert').exists()).toBe(true)
    wrapper.unmount()
  })
})
