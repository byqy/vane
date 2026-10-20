import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'

const { getMock, postMock, patchMock, putMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  patchMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn()
}))

vi.mock('@vane/shared', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@vane/shared')>()
  return {
    ...actual,
    http: { get: getMock, post: postMock, patch: patchMock, put: putMock, delete: deleteMock }
  }
})

import RolesView from './RolesView.vue'

const ROLE = {
  id: 'r1',
  code: 'ADMIN',
  name: '管理员',
  dataScope: 'CUSTOM',
  status: 'ACTIVE',
  sortNo: 10,
  version: 0
}

function mountRoles() {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(RolesView, { attachTo: document.body, global: { plugins: [i18n] } })
}

function routeGet(url: string) {
  if (url === '/admin/depts/tree') {
    return Promise.resolve({ data: [{ id: 'd1', name: '总部', children: [] }] })
  }
  if (url === '/admin/permissions/tree') {
    return Promise.resolve({
      data: [
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
    })
  }
  if (url.includes('/permissions')) {
    return Promise.resolve({ data: ['system:user'] })
  }
  if (url.includes('/depts')) {
    return Promise.resolve({ data: { deptIds: ['d1'] } })
  }
  return Promise.resolve({ data: { items: [ROLE], page: { number: 0, size: 20, total: 1 } } })
}

function buttonByText(wrapper: ReturnType<typeof mountRoles>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function dialogInputs(): HTMLInputElement[] {
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

describe('RolesView', () => {
  it('渲染角色列表', async () => {
    getMock.mockImplementation(routeGet)
    const wrapper = mountRoles()
    await flushPromises()

    expect(wrapper.text()).toContain('ADMIN')
    expect(wrapper.text()).toContain('管理员')
    wrapper.unmount()
  })

  it('新建角色提交 code、name 与数据范围', async () => {
    getMock.mockImplementation(routeGet)
    postMock.mockResolvedValue({ data: {} })

    const wrapper = mountRoles()
    await flushPromises()
    await buttonByText(wrapper, '新建角色')!.trigger('click')
    await flushPromises()

    const inputs = dialogInputs()
    await setInputValue(inputs[0], 'DEPT_MGR')
    await setInputValue(inputs[1], '部门主管')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(postMock).toHaveBeenCalledWith('/admin/roles', {
      code: 'DEPT_MGR',
      name: '部门主管',
      dataScope: 'DEPT'
    })
    wrapper.unmount()
  })

  it('编辑角色走 PATCH', async () => {
    getMock.mockImplementation(routeGet)
    patchMock.mockResolvedValue({ data: {} })

    const wrapper = mountRoles()
    await flushPromises()
    await buttonByText(wrapper, '编辑')!.trigger('click')
    await flushPromises()

    await setInputValue(dialogInputs()[1], '系统管理员')
    footerButtons().at(-1)!.click()
    await flushPromises()

    expect(patchMock).toHaveBeenCalledTimes(1)
    const [url, payload] = patchMock.mock.calls[0]
    expect(url).toBe('/admin/roles/r1')
    expect(payload.name).toBe('系统管理员')
    wrapper.unmount()
  })

  it('点击组织授权打开组织树，保存提交 PUT', async () => {
    getMock.mockImplementation(routeGet)
    putMock.mockResolvedValue({ data: null })
    const wrapper = mountRoles()
    await flushPromises()

    const openButton = buttonByText(wrapper, '组织授权')
    expect(openButton).toBeTruthy()
    await openButton!.trigger('click')
    await flushPromises()

    const dialog = document.querySelector('.el-dialog')
    expect(dialog?.textContent).toContain('授权组织')
    expect(document.querySelector('.el-dialog .el-tree')).toBeTruthy()

    const saveButton = footerButtons().find((button) => (button.textContent ?? '').includes('保存'))
    expect(saveButton).toBeTruthy()
    saveButton!.click()
    await flushPromises()

    expect(putMock).toHaveBeenCalledWith('/admin/roles/r1/depts', { deptIds: ['d1'] })
    wrapper.unmount()
  })
  it('打开组织授权抽屉并加载组织树与已授权组织', async () => {
    getMock.mockImplementation(routeGet)

    const wrapper = mountRoles()
    await flushPromises()
    await buttonByText(wrapper, '组织授权')!.trigger('click')
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/depts/tree')
    expect(getMock).toHaveBeenCalledWith('/admin/roles/r1/depts')
    wrapper.unmount()
  })

  it('权限授权抽屉显示中文权限名，保存提交 permissionCodes', async () => {
    getMock.mockImplementation(routeGet)
    putMock.mockResolvedValue({ data: null })
    const wrapper = mountRoles()
    await flushPromises()

    // 角色列表里的「授权」按钮（"组织授权" 是另一个按钮，按文本精确匹配）
    const openButton = wrapper.findAll('button').find((button) => button.text().trim() === '授权')
    expect(openButton).toBeTruthy()
    await openButton!.trigger('click')
    await flushPromises()

    expect(getMock).toHaveBeenCalledWith('/admin/permissions/tree')
    expect(getMock).toHaveBeenCalledWith('/admin/roles/r1/permissions')

    // 页面上还有一个页面级帮助抽屉（HelpTip），权限授权抽屉里才有 el-tree
    const drawer = Array.from(document.querySelectorAll('.el-drawer')).find((element) =>
      element.querySelector('.el-tree')
    )
    expect(drawer).toBeTruthy()
    expect(drawer?.textContent).toContain('用户管理')
    expect(drawer?.textContent).toContain('新建用户')
    expect(drawer?.textContent).not.toContain('menu.system.user')

    const saveButton = Array.from(document.querySelectorAll('button')).find(
      (button) => (button.textContent ?? '').trim() === '保存授权'
    ) as HTMLButtonElement | undefined
    expect(saveButton).toBeTruthy()
    saveButton!.click()
    await flushPromises()

    expect(putMock).toHaveBeenCalledWith('/admin/roles/r1/permissions', {
      permissionCodes: ['system:user', 'system:user:create']
    })
    wrapper.unmount()
  })
})

