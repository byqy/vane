import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { postMock, logoutMock, replaceMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  logoutMock: vi.fn(),
  replaceMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { post: postMock } }
})

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({ logout: logoutMock })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: replaceMock })
}))

import ChangePasswordView from './ChangePasswordView.vue'

function mountView(locale: 'zh-CN' | 'en' = 'zh-CN') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'zh-CN', messages })
  return mount(ChangePasswordView, { global: { plugins: [i18n] } })
}

async function fill(wrapper: ReturnType<typeof mountView>, current: string, next: string, confirm: string) {
  await wrapper.find('input[autocomplete="current-password"]').setValue(current)
    const newInputs = wrapper.findAll('input[autocomplete="new-password"]')
    await newInputs[0].setValue(next)
    await newInputs[1].setValue(confirm)
}

describe('ChangePasswordView', () => {
  it('改密页（首登必经）也提供语言切换', async () => {
    const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
    const wrapper = mount(ChangePasswordView, { global: { plugins: [i18n] } })
    expect(wrapper.find('.el-select').exists()).toBe(true)

    i18n.global.locale.value = 'en'
    await flushPromises()
    expect(wrapper.text()).toContain('Change password')
  })

  it('渲染中英文标题', () => {
    expect(mountView('zh-CN').text()).toContain('修改密码')
    expect(mountView('en').text()).toContain('Change password')
  })

  it('两次新密码不一致时本地拦截', async () => {
    const wrapper = mountView()
    await fill(wrapper, 'Old@12345', 'New@12345', 'Other@12345')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('两次输入的新密码不一致')
    expect(postMock).not.toHaveBeenCalled()
  })

  it('改密按钮是原生 submit，表单提交可触发（回车生效）', async () => {
    postMock.mockResolvedValue({ data: null })
    const wrapper = mountView()
    const submitButton = wrapper.findAll('button').find((button) => button.text().includes('保存'))
    expect(submitButton!.attributes('type')).toBe('submit')

    await fill(wrapper, 'Old@12345', 'New@12345', 'New@12345')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/auth/password', {
      currentPassword: 'Old@12345',
      newPassword: 'New@12345'
    })
  })

  it('成功后登出并跳转登录页', async () => {
    postMock.mockResolvedValue({ data: null })
    const wrapper = mountView()
    await fill(wrapper, 'Old@12345', 'New@12345', 'New@12345')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/auth/password', {
      currentPassword: 'Old@12345',
      newPassword: 'New@12345'
    })
    expect(logoutMock).toHaveBeenCalled()
    expect(replaceMock).toHaveBeenCalledWith({ name: 'login' })
  })

  it('失败时展示后端 detail', async () => {
    postMock.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          type: '/problems/validation-failed',
          title: '参数校验失败',
          status: 422,
          detail: '当前密码不正确',
          code: 'AUTH.PASSWORD.MISMATCH'
        }
      }
    })
    const wrapper = mountView()
    await fill(wrapper, 'Wrong@123', 'New@12345', 'New@12345')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('当前密码不正确')
  })
})





