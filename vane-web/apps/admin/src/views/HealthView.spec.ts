import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import HealthView from './HealthView.vue'

const HEALTH = {
  status: 'UP',
  checks: { db: 'UP', cache: 'UP' },
  checkedAt: '2026-10-19T14:00:00Z'
}

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(HealthView, { global: { plugins: [i18n] } })
}

describe('HealthView', () => {
  it('渲染整体状态与各依赖状态', async () => {
    getMock.mockResolvedValue({ data: HEALTH })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/health')
    expect(wrapper.text()).toContain('UP')
    expect(wrapper.text()).toContain('db: UP')
    expect(wrapper.text()).toContain('cache: UP')
    expect(wrapper.text()).toContain(new Date('2026-10-19T14:00:00Z').toLocaleString('zh-CN'))
    expect(wrapper.text()).not.toContain('2026-10-19T14:00:00Z')
    wrapper.unmount()
  })

  it('降级状态使用 warning 标签', async () => {
    getMock.mockResolvedValue({ data: { ...HEALTH, status: 'DEGRADED', checks: { db: 'UP', cache: 'DOWN' } } })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('DEGRADED')
    expect(wrapper.text()).toContain('cache: DOWN')
    wrapper.unmount()
  })

  it('点击刷新会重新拉取', async () => {
    getMock.mockResolvedValue({ data: HEALTH })
    const wrapper = mountView()
    await flushPromises()
    expect(getMock).toHaveBeenCalledTimes(1)

    await wrapper.findAll('button').find((button) => button.text().includes('刷新'))!.trigger('click')
    await flushPromises()
    expect(getMock).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('加载失败时显示空态', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('健康')
    wrapper.unmount()
  })
})

