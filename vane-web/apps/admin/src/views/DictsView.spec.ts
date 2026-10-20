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

import DictsView from './DictsView.vue'

const TYPES = [
  { code: 'common_status', nameKey: 'dict.commonStatus', status: 'ACTIVE', remark: '通用状态', itemCount: 2 }
]
const ITEMS = [
  { value: 'ACTIVE', label: '启用', color: 'success', sortNo: 1, status: 'ACTIVE', builtin: true, labels: { 'zh-CN': '启用', en: 'Active' } }
]

function mountDicts() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  const wrapper = mount(DictsView, { attachTo: document.body, global: { plugins: [i18n] } })
  return Object.assign(wrapper, { i18n })
}

function routeGet(url: string) {
  if (url === '/admin/dicts') {
    return Promise.resolve({ data: TYPES })
  }
  return Promise.resolve({ data: ITEMS })
}

function buttonByText(wrapper: ReturnType<typeof mountDicts>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function dialogInputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll('.el-dialog input')) as HTMLInputElement[]
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('DictsView', () => {
  it('渲染字典类型与字典项', async () => {
    getMock.mockImplementation(routeGet)
    const wrapper = mountDicts()
    await flushPromises()

    expect(wrapper.text()).toContain('common_status')
    expect(wrapper.text()).toContain('启用')
    wrapper.unmount()
  })

  it('新建字典类型提交 typeCode 与 nameKey', async () => {
    getMock.mockImplementation(routeGet)
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountDicts()
    await flushPromises()
    await buttonByText(wrapper, '新建字典类型')!.trigger('click')
    await flushPromises()

    const inputs = dialogInputs()
    await setInputValue(inputs[0], 'it_status')
    await setInputValue(inputs[1], 'dict.itStatus')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/dicts', {
      typeCode: 'it_status',
      nameKey: 'dict.itStatus',
      remark: ''
    })
    wrapper.unmount()
  })

  it('新建字典项提交值与中英标签', async () => {
    getMock.mockImplementation(routeGet)
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountDicts()
    await flushPromises()
    await buttonByText(wrapper, '新建字典项')!.trigger('click')
    await flushPromises()

    const inputs = dialogInputs()
    await setInputValue(inputs[0], 'PAUSED')
    await setInputValue(inputs[1], '暂停')
    await setInputValue(inputs[2], 'Paused')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]
    expect(url).toBe('/admin/dicts/common_status/items')
    expect(payload.value).toBe('PAUSED')
    expect(payload.translations).toEqual([
      { locale: 'zh-CN', label: '暂停' },
      { locale: 'en', label: 'Paused' }
    ])
    wrapper.unmount()
  })

  it('切换语言后重新拉取字典项（标签由后端按 Accept-Language 返回）', async () => {
    getMock.mockImplementation(routeGet)
    const wrapper = mountDicts()
    await flushPromises()

    const itemsCallsBefore = getMock.mock.calls.filter((call) => String(call[0]).includes('/items')).length
    wrapper.i18n.global.locale.value = 'en'
    await flushPromises()

    const itemsCalls = getMock.mock.calls.filter((call) => String(call[0]).includes('/items'))
    expect(itemsCalls.length).toBeGreaterThan(itemsCallsBefore)
    const lastConfig = itemsCalls.at(-1)![1] as { headers?: Record<string, string> }
    expect(lastConfig.headers?.['Accept-Language']).toBe('en')
    wrapper.unmount()
  })

  it('编辑字典项走 PATCH', async () => {
    getMock.mockImplementation(routeGet)
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountDicts()
    await flushPromises()

    // 字典项表格中第一行「编辑」按钮
    const editButtons = wrapper.findAll('button').filter((button) => button.text().trim() === '编辑')
    await editButtons.at(-1)!.trigger('click')
    await flushPromises()

    const inputs = dialogInputs()
    await setInputValue(inputs[1], '已启用')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledTimes(1)
    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/dicts/common_status/items/ACTIVE')
    expect(payload.translations[0]).toEqual({ locale: 'zh-CN', label: '已启用' })
    wrapper.unmount()
  })
})

