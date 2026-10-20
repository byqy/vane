import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return { ...actual, http: { get: getMock } }
})

import PermissionsView from './PermissionsView.vue'

const TREE = [
  {
    id: 'system',
    code: 'system',
    parentCode: null,
    nameKey: 'menu.system',
    type: 'MENU',
    path: '/system',
    children: [
      {
        id: 'system:user',
        code: 'system:user',
        parentCode: 'system',
        nameKey: 'menu.system.user',
        type: 'MENU',
        path: '/system/user',
        children: [
          {
            id: 'system:user:create',
            code: 'system:user:create',
            parentCode: 'system:user',
            nameKey: 'perm.system.user.create',
            type: 'BUTTON',
            path: null,
            children: []
          }
        ]
      }
    ]
  }
]

function mountView() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(PermissionsView, { global: { plugins: [i18n] } })
}

describe('PermissionsView', () => {
  it('渲染权限点树并显示中文名称、编码与类型', async () => {
    getMock.mockResolvedValue({ data: TREE })
    const wrapper = mountView()
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/permissions/tree')
    expect(wrapper.text()).toContain('系统管理')
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('新建用户')
    expect(wrapper.text()).toContain('system:user:create')
    expect(wrapper.text()).toContain('按钮')
    expect(wrapper.text()).toContain('只读')
    wrapper.unmount()
  })

  it('缺失译文时回退原始 key 并标记', async () => {
    getMock.mockResolvedValue({
      data: [
        {
          id: 'system:unknown',
          code: 'system:unknown',
          parentCode: null,
          nameKey: 'perm.system.unknown',
          type: 'BUTTON',
          path: null,
          children: []
        }
      ]
    })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('perm.system.unknown')
    expect(wrapper.find('.key-fallback').exists()).toBe(true)
    wrapper.unmount()
  })

  it('加载失败时显示空态而不是崩溃', async () => {
    getMock.mockRejectedValue(new Error('boom'))
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('权限点')
    wrapper.unmount()
  })
})
