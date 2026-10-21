import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock, putMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  putMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock, post: postMock, patch: patchMock, put: putMock } }
})

import SmsView from './SmsView.vue'

const PROVIDER = {
  code: 'aliyun-cn',
  type: 'ALIYUN',
  name: '阿里云',
  config: { accessKeyId: 'ak', accessKeySecret: 'sk****', regionId: 'cn-hangzhou' },
  countries: ['CN'],
  optoutKeywords: [],
  enabled: true,
  isDefault: true,
  keyVersion: '1'
}

const LOG_PAGE = {
  items: [{
    id: 'l1',
    templateCode: 'password.reset',
    phoneMasked: '+8613****8000',
    countryCode: 'CN',
    providerCode: 'aliyun-cn',
    providerMessageId: null,
    campaignId: null,
    result: 'SUCCESS',
    errorCode: null,
    durationMs: 12,
    traceId: 'trace-1',
    createdAt: '2026-10-20T10:00:00Z'
  }],
  page: { number: 0, size: 20, total: 1 }
}

function mountSms() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(SmsView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function buttonByText(wrapper: ReturnType<typeof mountSms>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function tabByText(wrapper: ReturnType<typeof mountSms>, text: string) {
  return wrapper.findAll('.el-tabs__item').find((tab) => tab.text().includes(text))
}

async function setInputValue(input: HTMLInputElement, value: string): Promise<void> {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

describe('SmsView', () => {
  it('渲染服务商列表', async () => {
    getMock.mockResolvedValue({ data: [PROVIDER] })
    const wrapper = mountSms()
    await flushPromises()

    expect(wrapper.text()).toContain('aliyun-cn')
    expect(wrapper.text()).toContain('ALIYUN')
    wrapper.unmount()
  })

  it('短信模板在短信服务页维护', async () => {
    const TEMPLATES = [{
      id: 't1',
      code: 'password.reset',
      channel: 'SMS',
      status: 'ACTIVE',
      remark: null,
      translations: [{ locale: 'zh-CN', subject: null, body: '验证码 {code}' }]
    }]
    getMock.mockImplementation((url: string) => Promise.resolve({
      data: url.includes('/admin/message-templates') ? TEMPLATES : []
    }))
    const wrapper = mountSms()
    await flushPromises()

    await tabByText(wrapper, '短信模板')!.trigger('click')
    await flushPromises()

    expect(getMock.mock.calls.some((call) => call[0] === '/admin/message-templates?channel=SMS')).toBe(true)
    expect(wrapper.text()).toContain('password.reset')
    wrapper.unmount()
  })

  it('切换到发送日志页签并加载日志', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/sms/logs') {
        return Promise.resolve({ data: LOG_PAGE })
      }
      return Promise.resolve({ data: [] })
    })
    const wrapper = mountSms()
    await flushPromises()

    await tabByText(wrapper, '发送日志')!.trigger('click')
    await flushPromises()

    const logCall = getMock.mock.calls.find((call) => call[0] === '/admin/sms/logs')
    expect(logCall).toBeTruthy()
    expect(logCall![1].params).toMatchObject({ 'page[number]': 0, 'page[size]': 20 })
    expect(wrapper.text()).toContain('+8613****8000')
    wrapper.unmount()
  })

  it('测试发送提交手机号、签名与变量', async () => {
    getMock.mockResolvedValue({ data: [PROVIDER] })
    postMock.mockResolvedValue({ data: { success: true, providerStatus: 'OK' } })

    const wrapper = mountSms()
    await flushPromises()
    await buttonByText(wrapper, '测试发送')!.trigger('click')
    await flushPromises()

    const inputs = Array.from(document.querySelectorAll('.el-dialog input')) as HTMLInputElement[]
    await setInputValue(inputs[0], '+8613800138000')
    await setInputValue(inputs[1], 'vane')
    await setInputValue(inputs[2], 'SMS_1')
    const textarea = document.querySelector('.el-dialog textarea') as HTMLTextAreaElement
    textarea.value = '{"code":"123456"}'
    textarea.dispatchEvent(new Event('input'))
    await flushPromises()

    const footer = Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
    footer.at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledTimes(1)
    const [url, payload] = postMock.mock.calls[0]
    expect(url).toBe('/admin/sms/providers/aliyun-cn/test')
    expect(payload).toMatchObject({ phone: '+8613800138000', signature: 'vane', templateCode: 'SMS_1' })
    expect(payload.variables).toEqual({ code: '123456' })
    wrapper.unmount()
  })

  it('日志失败状态渲染为失败标签', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/sms/logs') {
        return Promise.resolve({ data: { ...LOG_PAGE, items: [{ ...LOG_PAGE.items[0], result: 'FAIL', errorCode: 'SMS.SEND.FAILED' }] } })
      }
      return Promise.resolve({ data: [] })
    })
    const wrapper = mountSms()
    await flushPromises()
    await tabByText(wrapper, '发送日志')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('失败')
    expect(wrapper.text()).toContain('SMS.SEND.FAILED')
    wrapper.unmount()
  })
  it('切换到群发页签并开始活动', async () => {
    const campaign = {
      id: 'c1',
      name: '双十一通知',
      templateId: 't1',
      templateCode: 'password.reset',
      providerCode: 'aliyun-cn',
      signatureId: null,
      status: 'DRAFT',
      scheduledAt: null,
      throttlePerSecond: 100,
      dailyLimit: null,
      budgetAmount: null,
      locale: 'zh-CN',
      total: 2,
      sent: 0,
      failed: 0,
      createdAt: '2026-10-20T10:00:00Z'
    }
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/sms/campaigns') {
        return Promise.resolve({ data: { items: [campaign], page: { number: 0, size: 20, total: 1 } } })
      }
      if (url.startsWith('/admin/message-templates')) {
        return Promise.resolve({ data: [] })
      }
      return Promise.resolve({ data: [] })
    })
    postMock.mockResolvedValue({ data: campaign })

    const wrapper = mountSms()
    await flushPromises()
    await tabByText(wrapper, '群发任务')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('双十一通知')
    await buttonByText(wrapper, '开始')!.trigger('click')
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/sms/campaigns/c1/start', {})
    wrapper.unmount()
  })

  it('切换退订名单页签并新增退订', async () => {
    getMock.mockImplementation((url: string) => {
      if (url === '/admin/sms/optouts') {
        return Promise.resolve({
          data: {
            items: [{
              id: 'o1',
              phoneHash: 'abcdef0123456789abcdef',
              scope: 'MARKETING',
              source: 'INBOUND',
              reason: 'TD',
              createdAt: '2026-10-20T10:00:00Z'
            }],
            page: { number: 0, size: 20, total: 1 }
          }
        })
      }
      return Promise.resolve({ data: [] })
    })
    postMock.mockResolvedValue({ data: { created: true } })

    const wrapper = mountSms()
    await flushPromises()
    await tabByText(wrapper, '退订名单')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('MARKETING')
    await buttonByText(wrapper, '新增退订')!.trigger('click')
    await flushPromises()

    const inputs = Array.from(document.querySelectorAll('.el-dialog input')) as HTMLInputElement[]
    await setInputValue(inputs[0], '+8613700137000')
    const footer = Array.from(document.querySelectorAll('.el-dialog__footer button')) as HTMLButtonElement[]
    footer.at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/sms/optouts', expect.objectContaining({ phone: '+8613700137000' }))
    wrapper.unmount()
  })

})
