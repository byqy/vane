import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'
import { nextTick } from 'vue'
import BannerCarousel from './BannerCarousel.vue'

function banners(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    id: `b${index}`,
    imageFileId: `file-${index}`,
    title: `横幅 ${index + 1}`,
    linkUrl: null
  }))
}

function mountCarousel(items = banners(3)) {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(BannerCarousel, {
    props: { items },
    global: { plugins: [i18n] }
  })
}

function currentImage(wrapper: ReturnType<typeof mountCarousel>): string | undefined {
  return wrapper.find('img').attributes('src')
}

beforeEach(() => {
  vi.useRealTimers()
})

describe('portal BannerCarousel', () => {
  it('渲染第一张、上一张/下一张按钮带无障碍标签且可切换', async () => {
    const wrapper = mountCarousel()
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-0')
    expect(wrapper.text()).toContain('横幅 1')

    const next = wrapper.get('button.control--next')
    expect(next.attributes('aria-label')).toBe('下一张')
    await next.trigger('click')
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-1')

    const prev = wrapper.get('button.control--prev')
    expect(prev.attributes('aria-label')).toBe('上一张')
    await prev.trigger('click')
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-0')
    wrapper.unmount()
  })

  it('循环切换：从最后一张点下一张回到第一张', async () => {
    const wrapper = mountCarousel(banners(2))
    await wrapper.get('button.control--next').trigger('click')
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-1')
    await wrapper.get('button.control--next').trigger('click')
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-0')
    wrapper.unmount()
  })

  it('键盘左右方向键可操作轮播', async () => {
    const wrapper = mountCarousel()
    await wrapper.get('.carousel').trigger('keydown', { key: 'ArrowRight' })
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-1')
    await wrapper.get('.carousel').trigger('keydown', { key: 'ArrowLeft' })
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-0')
    wrapper.unmount()
  })

  it('自动播放按间隔轮播，且只有一张时不显示切换按钮', async () => {
    vi.useFakeTimers()
    const wrapper = mountCarousel()
    vi.advanceTimersByTime(6000)
    await nextTick()
    expect(currentImage(wrapper)).toBe('/api/v1/public/files/file-1')

    const single = mountCarousel(banners(1))
    expect(single.find('button.control--next').exists()).toBe(false)
    expect(single.find('button.control--prev').exists()).toBe(false)
    wrapper.unmount()
    single.unmount()
    vi.useRealTimers()
  })
})
