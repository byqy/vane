import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { replaceMock, logoutMock, loadMeMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  logoutMock: vi.fn(),
  loadMeMock: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    currentRoute: { value: { path: '/admin/system/user' } },
    replace: replaceMock
  })
}))

const AUTH = {
  me: { username: 'admin' },
  menus: [
    { code: 'system', parentCode: null, nameKey: 'menu.system', path: '/system' },
    { code: 'system:user', parentCode: 'system', nameKey: 'menu.system.user', path: '/system/user' }
  ],
  loadMe: loadMeMock,
  logout: logoutMock
}

vi.mock('../stores/auth', () => ({
  useAuthStore: () => AUTH
}))

import LayoutView from './LayoutView.vue'

const M = messages['zh-CN'] as Record<string, string>

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(LayoutView, {
    global: {
      plugins: [i18n],
      stubs: { RouterView: { template: '<div class="router-stub" />' }, LanguageSelect: true }
    }
  })
}

describe('LayoutView', () => {
  it('按后端菜单渲染分组与子项，并显示当前用户', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain(M['menu.system'])
    expect(wrapper.text()).toContain(M['menu.system.user'])
    expect(wrapper.text()).toContain('admin')
    expect(loadMeMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('点击退出调用 logout 并跳转登录页', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text().includes(M['app.logout']))!.trigger('click')
    await flushPromises()

    expect(logoutMock).toHaveBeenCalled()
    expect(replaceMock).toHaveBeenCalledWith('/login')
    wrapper.unmount()
  })

  it('外壳为固定一屏：侧边栏菜单与主内容区各自滚动', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.layout').exists()).toBe(true)
    expect(wrapper.find('.aside .menu').exists()).toBe(true)
    expect(wrapper.find('.workspace .header').exists()).toBe(true)
    expect(wrapper.find('.workspace .main').exists()).toBe(true)
    expect(wrapper.find('.router-stub').exists()).toBe(true)
    wrapper.unmount()
  })

  it('没有 me 时加载用户信息', async () => {
    const original = AUTH.me
    AUTH.me = null as unknown as { username: string }
    loadMeMock.mockResolvedValue(undefined)
    const wrapper = mountView()
    await flushPromises()

    expect(loadMeMock).toHaveBeenCalled()
    AUTH.me = original
    wrapper.unmount()
  })
})
