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

import JobsView from './JobsView.vue'

const JOB = {
  id: 'j1',
  jobCode: 'cleanup-idempotency',
  nameKey: 'job.cleanup',
  handler: 'cleanup-idempotency',
  cron: '0 0 3 * * *',
  params: null,
  status: 'ACTIVE',
  concurrency: 'FORBID',
  lastRunAt: null,
  nextRunAt: null
}

const LOGS = {
  items: [
    { id: 'l1', startedAt: '2026-10-19T01:00:00Z', finishedAt: '2026-10-19T01:00:01Z', success: true, message: 'cleaned 3', traceId: 'abc' }
  ],
  page: { number: 0, size: 20, total: 1 }
}

function mountJobs() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(JobsView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function routeGet(url: string) {
  if (url.includes('/logs')) {
    return Promise.resolve({ data: LOGS })
  }
  return Promise.resolve({ data: [JOB] })
}

function buttonByText(wrapper: ReturnType<typeof mountJobs>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function inputs(): HTMLInputElement[] {
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

describe('JobsView', () => {
  it('渲染任务列表', async () => {
    getMock.mockImplementation(routeGet)
    const wrapper = mountJobs()
    await flushPromises()

    expect(wrapper.text()).toContain('cleanup-idempotency')
    expect(wrapper.text()).toContain('0 0 3 * * *')
    wrapper.unmount()
  })

  it('启停按钮按当前状态提交相反状态', async () => {
    getMock.mockImplementation(routeGet)
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountJobs()
    await flushPromises()
    // ACTIVE 任务的按钮文案是「暂停」
    await buttonByText(wrapper, '暂停')!.trigger('click')
    await flushPromises()

    expect(patchMock).toHaveBeenCalledTimes(1)
    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/jobs/j1')
    expect(payload.status).toBe('PAUSED')
    wrapper.unmount()
  })

  it('编辑任务提交 cron、参数与并发策略', async () => {
    getMock.mockImplementation(routeGet)
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountJobs()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    const cronInput = inputs().find((input) => (input as HTMLInputElement).value === '0 0 3 * * *')!
    await setInputValue(cronInput, '0 0 4 * * *')
    footerButtons().at(-1)!.click()
    await flushPromises()

    const [, payload] = patchMock.mock.calls[0]
    expect(payload.cron).toBe('0 0 4 * * *')
    expect(payload.concurrency).toBe('FORBID')
    wrapper.unmount()
  })

  it('打开执行日志抽屉并按任务查询', async () => {
    getMock.mockImplementation(routeGet)

    const wrapper = mountJobs()
    await flushPromises()
    await buttonByText(wrapper, '执行日志')!.trigger('click')
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/jobs/j1/logs', {
      params: { 'page[number]': 0, 'page[size]': 20 }
    })
    expect(wrapper.text()).toContain('cleaned 3')
    wrapper.unmount()
  })

  it('立即执行调用 run 接口', async () => {
    getMock.mockImplementation(routeGet)
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountJobs()
    await flushPromises()
    await buttonByText(wrapper, '立即执行')!.trigger('click')
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/jobs/j1/run')
    wrapper.unmount()
  })
})
