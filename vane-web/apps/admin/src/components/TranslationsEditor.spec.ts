import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { ElSelect } from 'element-plus'
import { messages } from '@vane/shared'
import TranslationsEditor from './TranslationsEditor.vue'
import type { TranslationRow } from './translation-types'

const FIELDS = [
  { key: 'title', labelKey: 'notices.subject' },
  { key: 'body', labelKey: 'notices.body', textarea: true }
]

function mountEditor(rows: TranslationRow[]) {
  const i18n = createI18n({ legacy: false, locale: 'zh-CN', fallbackLocale: 'zh-CN', messages })
  return mount(TranslationsEditor, {
    props: { modelValue: rows, fields: FIELDS },
    global: { plugins: [i18n] }
  })
}

function buttonByText(wrapper: ReturnType<typeof mountEditor>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('TranslationsEditor', () => {
  it('按语言渲染译文表单，并显示母语名称', () => {
    const wrapper = mountEditor([
      { locale: 'zh-CN', title: '甲', body: '正文' },
      { locale: 'ja', title: '甲（ja）', body: 'ほんぶん' }
    ])
    const text = wrapper.text()
    expect(text).toContain('中文')
    expect(text).toContain('日语')
    expect(wrapper.findAll('input').length).toBeGreaterThanOrEqual(2)
  })

  it('修改字段时向上抛出更新后的译文行', async () => {
    const wrapper = mountEditor([{ locale: 'zh-CN', title: '旧标题', body: '旧正文' }])
    const titleInput = wrapper.find('.el-collapse input')
    await titleInput.setValue('新标题')

    const emitted = wrapper.emitted('update:modelValue') as TranslationRow[][][]
    const rows = emitted.at(-1)?.[0] as TranslationRow[]
    expect(rows[0]).toMatchObject({ locale: 'zh-CN', title: '新标题', body: '旧正文' })
  })

  it('可以添加一种新语言，初始字段为空', async () => {
    const wrapper = mountEditor([{ locale: 'zh-CN', title: '甲', body: '' }])
    wrapper.findComponent(ElSelect).vm.$emit('update:modelValue', 'de')
    await wrapper.vm.$nextTick()
    await buttonByText(wrapper, '添加语言')!.trigger('click')

    const emitted = wrapper.emitted('update:modelValue') as TranslationRow[][][]
    const rows = emitted.at(-1)?.[0] as TranslationRow[]
    expect(rows.map((row) => row.locale)).toEqual(['zh-CN', 'de'])
    expect(rows[1]).toMatchObject({ locale: 'de', title: '', body: '' })
  })

  it('删除语言后不再提交该语言', async () => {
    const wrapper = mountEditor([
      { locale: 'zh-CN', title: '甲', body: '' },
      { locale: 'en', title: 'A', body: '' }
    ])
    // 第二个折叠面板（en）的删除按钮：只删该语言，保留 zh-CN
    const deleteButtons = wrapper.findAll('button').filter((button) => button.text().trim() === '删除')
    await deleteButtons.at(-1)!.trigger('click')

    const emitted = wrapper.emitted('update:modelValue') as TranslationRow[][][]
    const rows = emitted.at(-1)?.[0] as TranslationRow[]
    expect(rows.map((row) => row.locale)).toEqual(['zh-CN'])
  })
})



