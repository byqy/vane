import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { loginMock, replaceMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  replaceMock: vi.fn()
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ login: loginMock })
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: replaceMock })
}))

import LoginView from './LoginView.vue'

function mountLogin(locale: 'zh-CN' | 'en' = 'zh-CN') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'zh-CN', messages })
  return mount(LoginView, { global: { plugins: [i18n] } })
}

describe('LoginView', () => {
  it('按语言渲染标题与按钮文案', () => {
    expect(mountLogin('zh-CN').text()).toContain('vane 管理后台')
    expect(mountLogin('en').text()).toContain('vane Admin')
  })

  it('登录前可切换语言：渲染语言下拉并随 i18n 更新', async () => {
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })
    expect(wrapper.find('.el-select').exists()).toBe(true)

    i18n.global.locale.value = 'ja'
    await flushPromises()
    expect(wrapper.text()).toContain('vane 管理画面')
  })

  it('登录按钮是原生 submit，表单提交可触发登录（回车生效）', async () => {
    loginMock.mockResolvedValue(undefined)
    const wrapper = mountLogin()
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('登'))
    expect(submitButton!.attributes('type')).toBe('submit')

    await wrapper.find('input[autocomplete="username"]').setValue('admin')
    await wrapper.find('input[autocomplete="current-password"]').setValue('Secret@123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(loginMock).toHaveBeenCalledWith('admin', 'Secret@123')
  })

  it('提交成功后跳转', async () => {
    loginMock.mockResolvedValue(undefined)
    const wrapper = mountLogin()
    await wrapper.find('input[autocomplete="username"]').setValue('admin')
      await wrapper.find('input[autocomplete="current-password"]').setValue('Secret@123')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(loginMock).toHaveBeenCalledWith('admin', 'Secret@123')
    expect(replaceMock).toHaveBeenCalled()
  })

  it('失败时展示 Problem Details 与错误码', async () => {
    loginMock.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: { type: '/problems/auth-required', title: '需要登录', status: 401, detail: '用户名或密码错误', code: 'AUTH.LOGIN.FAILED' }
      }
    })
    const wrapper = mountLogin()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('用户名或密码错误')
    expect(text).toContain('AUTH.LOGIN.FAILED')
  })
})







