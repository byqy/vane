import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock, deleteMock, confirmMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn(),
  confirmMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, patch: patchMock, delete: deleteMock } }
})

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return { ...actual, ElMessageBox: { confirm: confirmMock } }
})

import NavsView from './NavsView.vue'

const M = messages['zh-CN'] as Record<string, string>

const TREE = [
  {
    id: 'n1',
    parentId: null,
    position: 'HEADER',
    labelKey: 'nav.home',
    target: '/',
    openInNewTab: false,
    sortNo: 0,
    status: 'ACTIVE',
    children: []
  }
]

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(NavsView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function footerButton(text: string): HTMLButtonElement | undefined {
  return (Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]).find(
    (button) => (button.textContent ?? '').includes(text)
  )
}

async function setDialogInput(index: number, value: string): Promise<void> {
  const input = document.querySelectorAll('.el-dialog input')[index] as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('NavsView', () => {
  it('渲染导航树', async () => {
    getMock.mockResolvedValue({ data: TREE })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/navs')
    expect(wrapper.text()).toContain('nav.home')
    wrapper.unmount()
  })

  it('新建根导航提交 POST', async () => {
    getMock.mockResolvedValue({ data: TREE })
    postMock.mockResolvedValue({ data: { id: 'n2' } })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['navs.createRoot'])!.trigger('click')
    await flushPromises()
    await setDialogInput(0, 'nav.about')
    footerButton(M['common.save'])!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/navs', {
      parentId: null,
      position: 'HEADER',
      labelKey: 'nav.about',
      target: '',
      openInNewTab: false,
      sortNo: 0
    })
    wrapper.unmount()
  })

  it('编辑导航提交 PATCH', async () => {
    getMock.mockResolvedValue({ data: TREE })
    patchMock.mockResolvedValue({ data: null })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['common.edit'])!.trigger('click')
    await flushPromises()
    await setDialogInput(0, 'nav.updated')
    footerButton(M['common.save'])!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledWith('/admin/navs/n1', {
      parentId: null,
      labelKey: 'nav.updated',
      target: '/',
      openInNewTab: false,
      sortNo: 0,
      status: 'ACTIVE'
    })
    wrapper.unmount()
  })

  it('删除导航需确认后调用 DELETE', async () => {
    getMock.mockResolvedValue({ data: TREE })
    deleteMock.mockResolvedValue({ data: null })
    confirmMock.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['common.delete'])!.trigger('click')
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith('/admin/navs/n1')
    wrapper.unmount()
  })
})
