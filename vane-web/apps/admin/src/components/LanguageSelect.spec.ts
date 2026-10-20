import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { patchMock, setLocaleMock, authState } = vi.hoisted(() => ({
  patchMock: vi.fn(),
  setLocaleMock: vi.fn(),
  authState: { me: null as { username: string } | null }
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { patch: patchMock } }
})

vi.mock('../i18n', () => ({ setLocale: setLocaleMock }))
vi.mock('../stores/auth', () => ({ useAuthStore: () => authState }))

import LanguageSelect from './LanguageSelect.vue'

function mountSelect() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(LanguageSelect, { global: { plugins: [i18n] } })
}

describe('LanguageSelect', () => {
  it('切换语言立即生效；未登录时不同步到服务端', async () => {
    authState.me = null
    patchMock.mockClear()
    const wrapper = mountSelect()

    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', 'en')
    await wrapper.vm.$nextTick()

    expect(setLocaleMock).toHaveBeenCalledWith('en')
    expect(patchMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('已登录时把语言偏好写回服务端', async () => {
    authState.me = { username: 'admin' }
    patchMock.mockClear()
    patchMock.mockResolvedValue({ data: null })
    const wrapper = mountSelect()

    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', 'ja')
    await wrapper.vm.$nextTick()

    expect(setLocaleMock).toHaveBeenCalledWith('ja')
    expect(patchMock).toHaveBeenCalledWith('/auth/me/preferences', { locale: 'ja' })
    wrapper.unmount()
  })

  it('写回失败不打断界面切换', async () => {
    authState.me = { username: 'admin' }
    patchMock.mockRejectedValue(new Error('network'))
    const wrapper = mountSelect()

    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', 'de')
    await wrapper.vm.$nextTick()

    expect(setLocaleMock).toHaveBeenCalledWith('de')
    wrapper.unmount()
  })
})
