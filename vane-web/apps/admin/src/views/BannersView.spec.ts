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

import BannersView from './BannersView.vue'

const BANNER = {
  id: 'b1',
  position: 'home.top',
  imageFileId: 'f1',
  titleKey: 'banner.welcome',
  linkUrl: '/about',
  sortNo: 1,
  status: 'ACTIVE',
  startAt: null,
  endAt: null
}

function mountBanners() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(BannersView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountBanners>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

function inputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll('.el-dialog input')) as HTMLInputElement[]
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('BannersView', () => {
  it('渲染轮播列表与状态', async () => {
    getMock.mockResolvedValue({ data: [BANNER] })
    const wrapper = mountBanners()
    await flushPromises()

    expect(wrapper.text()).toContain('home.top')
    expect(wrapper.text()).toContain('banner.welcome')
    expect(wrapper.text()).toContain('ACTIVE')
    wrapper.unmount()
  })

  it('编辑轮播时预填并提交 PATCH', async () => {
    getMock.mockResolvedValue({ data: [BANNER] })
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountBanners()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    const titleKeyInput = inputs().find((input) => input.value === 'banner.welcome')!
    await setInputValue(titleKeyInput, 'banner.new')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledTimes(1)
    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/banners/b1')
    expect(payload.titleKey).toBe('banner.new')
    expect(payload.status).toBe('ACTIVE')
    wrapper.unmount()
  })

  it('编辑轮播时时间按本地时区显示并以 UTC 提交（往返不偏移）', async () => {
    const withTimes = { ...BANNER, startAt: '2026-10-19T02:00:00Z', endAt: '2026-11-01T16:30:00Z' }
    getMock.mockResolvedValue({ data: [withTimes] })
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountBanners()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    footerButtons().at(-1)!.click()
    await flushPromises()

    const [, payload] = patchMock.mock.calls[0]
    expect(new Date(payload.startAt).toISOString()).toBe('2026-10-19T02:00:00.000Z')
    expect(new Date(payload.endAt).toISOString()).toBe('2026-11-01T16:30:00.000Z')
    wrapper.unmount()
  })
  it('上传图片后调用文件接口并回填 fileId', async () => {
    getMock.mockResolvedValue({ data: [BANNER] })
    postMock.mockResolvedValue({ data: { id: 'f9' } })

    const wrapper = mountBanners()
    await flushPromises()
    await buttonByText(wrapper, '新建轮播')!.trigger('click')
    await flushPromises()

    const fileInput = document.querySelector('.el-dialog input[type="file"]') as HTMLInputElement
    const file = new File(['png'], 'banner.png', { type: 'image/png' })
    Object.defineProperty(fileInput, 'files', { value: [file], configurable: true })
    fileInput.dispatchEvent(new Event('change'))
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, form] = postMock.mock.calls[0]
    expect(url).toBe('/files')
    expect(form).toBeInstanceOf(FormData)
    expect(wrapper.text()).toContain('f9')
    wrapper.unmount()
  })
})

