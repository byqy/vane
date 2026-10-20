<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, problemOf, type PageResult } from '@vane/shared'
import TranslationsEditor from '../components/TranslationsEditor.vue'
import type { TranslationRow } from '../components/translation-types'

interface Translation {
  locale: string
  title: string
  summary: string | null
  body: string | null
}

interface ContentPage {
  id: string
  slug: string
  status: string
  publishedAt: string | null
  translations: Translation[]
}

const { t } = useI18n()
const items = ref<ContentPage[]>([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<ContentPage | null>(null)
const form = reactive({
  slug: '',
  translations: [{ locale: 'zh-CN', title: '', summary: '', body: '' }] as TranslationRow[]
})

const translationFields = [
  { key: 'title', labelKey: 'notices.subject' },
  { key: 'summary', labelKey: 'pages.summary' },
  { key: 'body', labelKey: 'notices.body', textarea: true }
]

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<PageResult<ContentPage>>('/admin/pages', {
      params: { 'page[number]': 0, 'page[size]': 20 }
    })
    items.value = data.items
    total.value = data.page.total
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    slug: '',
    translations: [{ locale: 'zh-CN', title: '', summary: '', body: '' }]
  })
  dialogVisible.value = true
}

function openEdit(page: ContentPage): void {
  editing.value = page
  Object.assign(form, {
    slug: page.slug,
    translations: page.translations.map((item) => ({
      locale: item.locale,
      title: item.title,
      summary: item.summary ?? '',
      body: item.body ?? ''
    }))
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!/^[a-z0-9][a-z0-9-]{0,63}$/.test(form.slug)) {
    ElMessage.error(t('pages.slugInvalid'))
    return
  }
  const translations = form.translations
    .filter((row) => (row.title ?? '').trim())
    .map((row) => ({
      locale: row.locale,
      title: row.title,
      summary: row.summary ?? '',
      body: row.body ?? ''
    }))
  try {
    if (editing.value) {
      await http.patch(`/admin/pages/${editing.value.id}`, { translations })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/pages', { slug: form.slug, translations })
      ElMessage.success(t('notices.createDraft'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function publish(page: ContentPage): Promise<void> {
  try {
    await http.post(`/admin/pages/${page.id}/publish`)
    ElMessage.success(t('notices.published'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(page: ContentPage): Promise<void> {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm', { name: page.slug }), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/pages/${page.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

function titleOf(page: ContentPage, locale: string): string {
  return page.translations.find((t) => t.locale === locale)?.title ?? '—'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('pages.title') }}<HelpTip page="pages" :title="t('pages.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button type="primary" style="margin-inline-start: 12px" @click="openCreate">{{ t('pages.create') }}</el-button>
      </div>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="slug" label="slug" width="180" />
        <el-table-column :label="t('pages.zhTitle')" min-width="180">
          <template #default="{ row }">{{ titleOf(row, 'zh-CN') }}</template>
        </el-table-column>
        <el-table-column label="English" min-width="180">
          <template #default="{ row }">{{ titleOf(row, 'en') }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="120" />
        <el-table-column :label="t('common.actions')" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" :disabled="row.status === 'PUBLISHED'" @click="publish(row)">{{ t('pages.publish') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && items.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('pages.edit') : t('pages.create')" width="680px">
      <el-form label-width="90px">
        <el-form-item><template #label>slug<FieldHelp help="field.pages.slug.help" /></template>
          <el-input v-model="form.slug" :disabled="!!editing" :placeholder="t('pages.slugHint')" />
        </el-form-item>
        <el-divider content-position="left">{{ t('notices.translations') }}</el-divider>
        <TranslationsEditor v-model="form.translations" :fields="translationFields" />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submit">{{ editing ? t('common.save') : t('common.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}
</style>







