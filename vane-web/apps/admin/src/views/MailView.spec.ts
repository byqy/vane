import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn()
}))

vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: () => true }) }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, patch: patchMock } }
})

import MailView from './MailView.vue'

const TEMPLATE = {
  id: 't1',
  code: 'mail.welcome',
  channel: 'EMAIL',
  status: 'ACTIVE',
  remark: null,
  translations: [
    { locale: 'zh-CN', subject: '欢迎', body: '你好 {username}' },
    { locale: 'en', subject: 'Welcome', body: 'Hello {username}' }
  ]
}

const SMS_TEMPLATE = {
  id: 't2',
  code: 'password.reset',
  channel: 'SMS',
  status: 'ACTIVE',
  remark: null,
  translations: [{ locale: 'zh-CN', subject: null, body: '验证码 {code}' }]
}

function mountMail() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(MailView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function footerButtons(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
}

function editorInputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll('.el-dialog .el-collapse input')) as HTMLInputElement[]
}

function buttonByText(wrapper: ReturnType<typeof mountMail>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('MailView', () => {
  it('渲染模板列表并区分渠道', async () => {
    getMock.mockResolvedValue({ data: [TEMPLATE, SMS_TEMPLATE] })
    const wrapper = mountMail()
    await flushPromises()

    expect(wrapper.text()).toContain('mail.welcome')
    expect(wrapper.text()).toContain('EMAIL')
    expect(wrapper.text()).toContain('SMS')
    wrapper.unmount()
  })

  it('短信渠道模板不提供邮件发送测试', async () => {
    getMock.mockResolvedValue({ data: [TEMPLATE, SMS_TEMPLATE] })
    const wrapper = mountMail()
    await flushPromises()

    const rows = wrapper.findAll('.el-table__body tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('发送测试')
    expect(rows[1].text()).not.toContain('发送测试')
    wrapper.unmount()
  })

  it('切换到服务商页签时加载服务商并隐藏密码', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/mail/providers') {
        return Promise.resolve({
          data: [
            {
              code: 'smtp',
              name: 'SMTP',
              host: 'smtp.example.com',
              port: 587,
              tlsMode: 'STARTTLS',
              username: 'user',
              password: '***',
              fromAddress: 'no-reply@example.com',
              fromName: 'vane',
              enabled: true,
              isDefault: true,
              dailyLimit: 500,
              timeoutMs: 10000
            }
          ]
        })
      }
      return Promise.resolve({ data: [TEMPLATE] })
    })
    const wrapper = mountMail()
    await flushPromises()

    const tab = wrapper.findAll('.el-tabs__item').find((item) => item.text().includes('服务商'))
    await tab!.trigger('click')
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/mail/providers')
    expect(wrapper.text()).toContain('smtp.example.com:587')
    expect(wrapper.text()).not.toContain('secret')
    wrapper.unmount()
  })

  it('编辑模板提交多语言主题与正文', async () => {
    getMock.mockResolvedValue({ data: [TEMPLATE] })
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountMail()
    await flushPromises()
    await buttonByText(wrapper, '编辑模板')!.trigger('click')
    await flushPromises()

    await setInputValue(editorInputs()[0], '欢迎（改）')
    footerButtons().at(-1)!.click()
    await flushPromises()

    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/message-templates/t1')
    expect(payload.translations.map((row: { locale: string }) => row.locale)).toEqual(['zh-CN', 'en'])
    expect(payload.translations[0]).toMatchObject({ locale: 'zh-CN', subject: '欢迎（改）' })
    wrapper.unmount()
  })

  it('发送测试邮件提交收件人、语言与变量', async () => {
    getMock.mockResolvedValue({ data: [TEMPLATE] })
    postMock.mockResolvedValue({ data: { subject: '欢迎', body: '你好 demo' } })

    const wrapper = mountMail()
    await flushPromises()
    await buttonByText(wrapper, '发送测试')!.trigger('click')
    await flushPromises()

    const inputs = Array.from(document.querySelectorAll('.el-dialog input')) as HTMLInputElement[]
    await setInputValue(inputs[0], 'demo@example.com')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]
    expect(url).toBe('/admin/message-templates/mail.welcome/send')
    expect(payload).toMatchObject({ to: 'demo@example.com', locale: 'zh-CN' })
    expect(typeof payload.variables).toBe('object')
    wrapper.unmount()
  })

  it('变量不是合法 JSON 时不发请求', async () => {
    getMock.mockResolvedValue({ data: [TEMPLATE] })

    const wrapper = mountMail()
    await flushPromises()
    await buttonByText(wrapper, '发送测试')!.trigger('click')
    await flushPromises()

    const textarea = document.querySelector('.el-dialog textarea') as HTMLTextAreaElement
    textarea.value = '{not json'
    textarea.dispatchEvent(new Event('input'))
    await flushPromises()
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
