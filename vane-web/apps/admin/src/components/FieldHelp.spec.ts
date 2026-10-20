import { beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { messages } from '@vane/shared'
import FieldHelp from './FieldHelp.vue'

const M = messages['zh-CN'] as Record<string, string>

function mountHelp(locale: 'zh-CN' | 'en' = 'zh-CN') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'zh-CN', messages })
  return mount(FieldHelp, {
    attachTo: document.body,
    props: { help: 'field.navs.labelKey.help' },
    global: { plugins: [i18n] }
  })
}

/** el-popover 展开有过渡帧，jsdom 里要等一拍 display 才会切换。 */
async function openPopover(wrapper: ReturnType<typeof mountHelp>): Promise<void> {
  await wrapper.get('button.field-help').trigger('click')
  await flushPromises()
  await new Promise((resolve) => setTimeout(resolve, 60))
  await flushPromises()
}

/** 只取当前可见（未带 aria-hidden）的气泡正文。 */
function visiblePopoverText(): string {
  return Array.from(document.querySelectorAll('.el-popover'))
    .filter((element) => element.getAttribute('aria-hidden') !== 'true')
    .map((element) => element.textContent ?? '')
    .join(' ')
}

describe('FieldHelp', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('渲染问号按钮，可访问名称来自文案', () => {
    const wrapper = mountHelp()
    const button = wrapper.get('button.field-help')
    expect(button.text()).toBe('?')
    expect(button.attributes('aria-label')).toBe(M['fieldHelp.open'])
    expect(button.attributes('title')).toBe(M['fieldHelp.open'])
    wrapper.unmount()
  })

  it('未点击时不展示说明正文', () => {
    const wrapper = mountHelp()
    expect(visiblePopoverText()).not.toContain(M['field.navs.labelKey.help'])
    wrapper.unmount()
  })

  it('点击后展示字段说明', async () => {
    const wrapper = mountHelp()
    await openPopover(wrapper)

    expect(visiblePopoverText()).toContain(M['field.navs.labelKey.help'])
    wrapper.unmount()
  })

  it('切换语言后展示对应语言的说明', async () => {
    const wrapper = mountHelp('en')
    await openPopover(wrapper)

    const text = visiblePopoverText()
    expect(text).toContain(messages.en['field.navs.labelKey.help'])
    expect(text).not.toContain(M['field.navs.labelKey.help'])
    wrapper.unmount()
  })
})
