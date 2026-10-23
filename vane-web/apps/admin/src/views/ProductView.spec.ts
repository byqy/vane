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

import ProductView from './ProductView.vue'

const PRODUCT = {
  id: 'p1',
  code: 'P-001',
  slug: 'alpha',
  group: 'line-a',
  status: 'DRAFT',
  sortNo: 10,
  coverFileId: null,
  videoFileId: null,
  publishedAt: null,
  createdAt: '2026-10-23T00:00:00Z',
  name: 'Alpha',
  summary: 'Alpha summary'
}

const DETAIL = {
  ...PRODUCT,
  translations: [
    {
      locale: 'zh-CN',
      name: 'Alpha',
      summary: 'Alpha summary',
      body: '<p>正文</p>',
      features: [{ title: 'F1', description: 'D1' }]
    }
  ],
  media: [{ fileId: 'f1', kind: 'IMAGE', sortNo: 1, caption: 'cover' }]
}

function mockLoads(): void {
  getMock.mockImplementation((url: string) => {
    if (url === '/admin/products') {
      return Promise.resolve({ data: { items: [PRODUCT], page: { number: 0, size: 20, total: 1 } } })
    }
    if (url === '/admin/products/p1') {
      return Promise.resolve({ data: DETAIL })
    }
    if (url === '/admin/files') {
      return Promise.resolve({
        data: { items: [{ id: 'f1', name: 'a.png', mime: 'image/png', size: 10, visibility: 'PUBLIC', createdAt: '2026-10-23T00:00:00Z' }], page: { number: 0, size: 20, total: 1 } }
      })
    }
    return Promise.resolve({ data: { items: [], page: { number: 0, size: 20, total: 0 } } })
  })
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(ProductView, { attachTo: document.body, global: { plugins: [i18n] } })
}

async function setInputValue(input: HTMLInputElement | null, value: string): Promise<void> {
  expect(input).not.toBeNull()
  input!.value = value
  input!.dispatchEvent(new Event('input'))
  await flushPromises()
}

function dialogInput(selector: string): HTMLInputElement | null {
  return document.querySelector(selector) as HTMLInputElement | null
}

function langInput(locale: string, field: string): HTMLInputElement | null {
  return document.querySelector(`.lang-row[data-locale="${locale}"] .${field} input`) as HTMLInputElement | null
}

function submitButton(): HTMLButtonElement {
  const buttons = Array.from(
    document.querySelectorAll('.el-dialog__footer button')
  ) as HTMLButtonElement[]
  return buttons.at(-1)!
}

afterEach(() => {
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

describe('ProductView', () => {
  it('渲染产品列表：编码、名称、产品线与排序', async () => {
    mockLoads()
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('P-001')
    expect(wrapper.text()).toContain('Alpha')
    expect(wrapper.text()).toContain('line-a')
    expect(wrapper.text()).toContain('10')
    wrapper.unmount()
  })

  it('筛选请求带上 status 与 keyword 参数', async () => {
    mockLoads()
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as { filters: { status: string; keyword: string } }
    vm.filters.status = 'PUBLISHED'
    await setInputValue(dialogInput('.filters .el-input input'), 'alpha')
    await wrapper.find('.btn-search').trigger('click')
    await flushPromises()

    const calls = getMock.mock.calls.filter(([url]) => url === '/admin/products')
    expect(calls.length).toBeGreaterThan(1)
    const call = calls.at(-1)!
    const params = (call[1] as { params: Record<string, unknown> }).params
    expect(params.status).toBe('PUBLISHED')
    expect(params.keyword).toBe('alpha')
    expect(params['page[number]']).toBe(0)
    expect(params['page[size]']).toBe(20)
    wrapper.unmount()
  })

  it('新建产品提交多语言内容与特性列表', async () => {
    mockLoads()
    postMock.mockResolvedValue({ data: { ...PRODUCT, id: 'p2' } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.btn-create').trigger('click')
    await flushPromises()

    await setInputValue(dialogInput('.el-dialog .f-code input'), 'P-002')
    await setInputValue(dialogInput('.el-dialog .f-slug input'), 'beta')
    await setInputValue(langInput('zh-CN', 'f-name'), 'Beta')
    await setInputValue(langInput('zh-CN', 'f-summary'), 'Beta summary')

    const addFeature = document.querySelector('.lang-row[data-locale="zh-CN"] .f-feature-add') as HTMLButtonElement
    addFeature.click()
    await flushPromises()
    await setInputValue(langInput('zh-CN', 'f-feature-title'), 'Feature 1')

    submitButton().click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]! as [string, Record<string, unknown>]
    expect(url).toBe('/admin/products')
    expect(payload.code).toBe('P-002')
    expect(payload.slug).toBe('beta')
    expect(payload.translations).toEqual([
      {
        locale: 'zh-CN',
        name: 'Beta',
        summary: 'Beta summary',
        body: '',
        features: [{ title: 'Feature 1', description: '' }]
      }
    ])
    wrapper.unmount()
  })

  it('编辑时整体替换媒体集', async () => {
    mockLoads()
    patchMock.mockResolvedValue({ data: DETAIL })
    putMock.mockResolvedValue({ data: {} })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.act-edit').trigger('click')
    await flushPromises()

    submitButton().click()
    await flushPromises()

    expect(patchMock.mock.calls[0]![0]).toBe('/admin/products/p1')
    expect(putMock).toHaveBeenCalledWith('/admin/products/p1/media', {
      media: [{ fileId: 'f1', kind: 'IMAGE', sortNo: 1, caption: 'cover' }]
    })
    wrapper.unmount()
  })

  it('上线动作调用发布接口', async () => {
    mockLoads()
    postMock.mockResolvedValue({ data: { ...PRODUCT, status: 'PUBLISHED' } })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.act-publish').trigger('click')
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/products/p1/publish')
    wrapper.unmount()
  })

  it('删除前二次确认并调用删除接口', async () => {
    mockLoads()
    deleteMock.mockResolvedValue({ data: {} })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.act-delete').trigger('click')
    await flushPromises()

    const confirm = document.querySelector(
      '.el-message-box__btns .el-button--primary'
    ) as HTMLButtonElement | null
    expect(confirm).not.toBeNull()
    confirm!.click()
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith('/admin/products/p1')
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
