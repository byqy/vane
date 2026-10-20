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

import DeptsView from './DeptsView.vue'

const M = messages['zh-CN'] as Record<string, string>

const TREE = [
  {
    id: 'd1',
    parentId: null,
    code: 'HQ',
    name: '总部',
    sortNo: 1,
    status: 'ACTIVE',
    children: []
  }
]

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(DeptsView, { attachTo: document.body, global: { plugins: [i18n] } })
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

describe('DeptsView', () => {
  it('渲染组织树', async () => {
    getMock.mockResolvedValue({ data: TREE })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/depts/tree')
    expect(wrapper.text()).toContain('总部')
    expect(wrapper.text()).toContain('HQ')
    wrapper.unmount()
  })

  it('新建根组织提交 POST', async () => {
    getMock.mockResolvedValue({ data: TREE })
    postMock.mockResolvedValue({ data: { id: 'd2' } })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['depts.createRoot'])!.trigger('click')
    await flushPromises()
    await setDialogInput(0, 'BRANCH')
    await setDialogInput(1, '分公司')
    footerButton(M['common.save'])!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/depts', {
      parentId: null,
      code: 'BRANCH',
      name: '分公司',
      sortNo: 0
    })
    wrapper.unmount()
  })

  it('编辑组织提交 PATCH', async () => {
    getMock.mockResolvedValue({ data: TREE })
    patchMock.mockResolvedValue({ data: null })
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['common.edit'])!.trigger('click')
    await flushPromises()
    await setDialogInput(1, '总部（改名）')
    footerButton(M['common.save'])!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledWith('/admin/depts/d1', {
      parentId: null,
      name: '总部（改名）',
      status: 'ACTIVE',
      sortNo: 1
    })
    wrapper.unmount()
  })

  it('删除组织需确认后调用 DELETE', async () => {
    getMock.mockResolvedValue({ data: TREE })
    deleteMock.mockResolvedValue({ data: null })
    confirmMock.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['common.delete'])!.trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    expect(deleteMock).toHaveBeenCalledWith('/admin/depts/d1')
    wrapper.unmount()
  })

  it('取消确认时不删除', async () => {
    getMock.mockResolvedValue({ data: TREE })
    deleteMock.mockClear()
    confirmMock.mockRejectedValue(new Error('cancel'))
    const wrapper = mountView()
    await flushPromises()

    await buttonByText(wrapper, M['common.delete'])!.trigger('click')
    await flushPromises()

    expect(deleteMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
