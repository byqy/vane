import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PortalLayout from './PortalLayout.vue'

describe('portal PortalLayout', () => {
  it('渲染页头、主内容区与页脚，内容由路由视图填充', () => {
    const wrapper = mount(PortalLayout, {
      global: {
        stubs: {
          SiteHeader: { template: '<header class="site-header-stub" />' },
          SiteFooter: { template: '<footer class="site-footer-stub" />' },
          RouterView: { template: '<div class="page-stub" />' }
        }
      }
    })

    expect(wrapper.find('.site-header-stub').exists()).toBe(true)
    expect(wrapper.find('.site-footer-stub').exists()).toBe(true)
    expect(wrapper.find('main.portal-main .page-stub').exists()).toBe(true)
    wrapper.unmount()
  })
})
