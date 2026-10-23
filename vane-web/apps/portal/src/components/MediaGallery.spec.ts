import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'
import MediaGallery from './MediaGallery.vue'

const MEDIA = [
  { fileId: 'img-1', kind: 'IMAGE', sortNo: 0, caption: '外观' },
  { fileId: 'img-2', kind: 'IMAGE', sortNo: 1, caption: null }
]

function mountGallery(props: Record<string, unknown>) {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(MediaGallery, { props, global: { plugins: [i18n] } })
}

describe('portal MediaGallery', () => {
  it('图集渲染大图与缩略图，点击缩略图切换大图', async () => {
    const wrapper = mountGallery({ media: MEDIA })
    expect(wrapper.text()).toContain('产品图集')
    expect(wrapper.find('.stage-image').attributes('src')).toBe('/api/v1/public/files/img-1')
    expect(wrapper.findAll('.thumb')).toHaveLength(2)

    await wrapper.findAll('.thumb')[1]!.trigger('click')
    expect(wrapper.find('.stage-image').attributes('src')).toBe('/api/v1/public/files/img-2')
    expect(wrapper.findAll('.thumb')[1]!.attributes('aria-pressed')).toBe('true')
    wrapper.unmount()
  })

  it('有视频时渲染 controls 播放器并显示视频标题', () => {
    const wrapper = mountGallery({ media: MEDIA, videoFileId: 'video-1' })
    expect(wrapper.text()).toContain('演示视频')
    const video = wrapper.find('video')
    expect(video.attributes('src')).toBe('/api/v1/public/files/video-1')
    expect(video.attributes('controls')).toBeDefined()
    wrapper.unmount()
  })

  it('只有视频没有图片时仍渲染视频，不渲染图集', () => {
    const wrapper = mountGallery({ media: [], videoFileId: 'video-1' })
    expect(wrapper.find('.stage').exists()).toBe(false)
    expect(wrapper.find('video').exists()).toBe(true)
    wrapper.unmount()
  })
})
