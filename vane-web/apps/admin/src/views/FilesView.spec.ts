import { describe, expect, it, vi } from 'vitest'
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
  return { ...actual, http: { get: getMock, post: postMock, patch: vi.fn(), delete: deleteMock } }
})

import FilesView from './FilesView.vue'

const FILE = {
  id: 'f1',
  name: 'logo.png',
  mime: 'image/png',
  size: 2048,
  visibility: 'PUBLIC',
  createdAt: '2026-10-19T02:00:00Z'
}

const PAGE = { items: [FILE], page: { number: 0, size: 20, total: 1 } }

function mountFiles() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(FilesView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountFiles>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

describe('FilesView', () => {
  it('渲染文件列表与可见性', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    const wrapper = mountFiles()
    await flushPromises()

    expect(wrapper.text()).toContain('logo.png')
    expect(wrapper.text()).toContain('2.0 KB')
    expect(wrapper.text()).toContain('公开')
    wrapper.unmount()
  })

  it('上传文件提交 multipart 与可见性', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountFiles()
    await flushPromises()
    await buttonByText(wrapper, '上传文件')!.trigger('click')
    await flushPromises()

    const fileInput = document.querySelector('.el-dialog input[type="file"]') as HTMLInputElement
    const file = new File(['hello'], 'readme.txt', { type: 'text/plain' })
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    fileInput.dispatchEvent(new Event('change'))
    await flushPromises()
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, body] = postMock.mock.calls[0]
    expect(url).toBe('/files')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('visibility')).toBe('PRIVATE')
    expect((body as FormData).get('file')).toBeTruthy()
    wrapper.unmount()
  })

  it('预览图片时用鉴权下载的 blob 生成临时地址', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/files') {
        return Promise.resolve({ data: PAGE })
      }
      return Promise.resolve({ data: new Blob(['png'], { type: 'image/png' }) })
    })
    const createObjectURL = vi.fn(() => 'blob:preview')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL })

    const wrapper = mountFiles()
    await flushPromises()
    await buttonByText(wrapper, '预览')!.trigger('click')
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/files/f1', { responseType: 'blob' })
    expect(createObjectURL).toHaveBeenCalled()
    expect(document.querySelector('.el-dialog img')).toBeTruthy()
    vi.unstubAllGlobals()
    wrapper.unmount()
  })

  it('删除文件调用 DELETE 并刷新列表', async () => {
    getMock.mockResolvedValue({ data: PAGE })
    deleteMock.mockResolvedValue({ data: null })

    const wrapper = mountFiles()
    await flushPromises()
    await buttonByText(wrapper, '删除')!.trigger('click')
    await flushPromises()

    // Element Plus MessageBox 通过 teleport 渲染确认按钮
    const confirmButton = Array.from(document.querySelectorAll('.el-message-box__btns button')).at(-1) as HTMLButtonElement
    confirmButton.click()
    await flushPromises()

    expect(deleteMock).toHaveBeenCalledWith('/admin/files/f1')
    wrapper.unmount()
  })
})
