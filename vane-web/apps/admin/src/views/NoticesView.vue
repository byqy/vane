<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, problemOf, toDateTimeInput, toIsoInstant, type PageResult } from '@vane/shared'
import TranslationsEditor from '../components/TranslationsEditor.vue'
import type { TranslationRow } from '../components/translation-types'

interface Translation {
  locale: string
  title: string
  body: string | null
}

interface Notice {
  id: string
  level: string
  status: string
  pinned: boolean
  publishAt: string | null
  expireAt: string | null
  translations: Translation[]
}

const { t, locale } = useI18n()
const items = ref<Notice[]>([])
const total = ref(0)
const loading = ref(false)
const traceId = ref<string | null>(null)

const dialogVisible = ref(false)
const editing = ref<Notice | null>(null)
const submitting = ref(false)
const form = reactive({
  level: 'INFO',
  pinned: false,
  publishAt: '',
  expireAt: '',
  translations: [{ locale: 'zh-CN', title: '', body: '' }] as TranslationRow[]
})

const translationFields = [
  { key: 'title', labelKey: 'notices.subject' },
  { key: 'body', labelKey: 'notices.body', textarea: true }
]

const levelOptions = [
  { value: 'INFO', key: 'notices.level.INFO' },
  { value: 'WARN', key: 'notices.level.WARN' },
  { value: 'URGENT', key: 'notices.level.URGENT' }
]

async function load(): Promise<void> {
  loading.value = true
  traceId.value = null
  try {
    const { data } = await http.get<PageResult<Notice>>('/admin/notices', {
      params: { 'page[number]': 0, 'page[size]': 20 }
    })
    items.value = data.items
    total.value = data.page.total
  } catch (e) {
    const problem = problemOf(e)
    traceId.value = problem?.traceId ?? null
    ElMessage.error(problem?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    level: 'INFO',
    pinned: false,
    publishAt: '',
    expireAt: '',
    translations: [{ locale: 'zh-CN', title: '', body: '' }]
  })
  dialogVisible.value = true
}

function openEdit(notice: Notice): void {
  editing.value = notice
  Object.assign(form, {
    level: notice.level,
    pinned: notice.pinned,
    publishAt: toDateTimeInput(notice.publishAt),
    expireAt: toDateTimeInput(notice.expireAt),
    translations: notice.translations.map((item) => ({
      locale: item.locale,
      title: item.title,
      body: item.body ?? ''
    }))
  })
  dialogVisible.value = true
}

function payload() {
  return {
    level: form.level,
    pinned: form.pinned,
    publishAt: toIsoInstant(form.publishAt),
    expireAt: toIsoInstant(form.expireAt),
    translations: form.translations
      .filter((row) => (row.title ?? '').trim())
      .map((row) => ({ locale: row.locale, title: row.title, body: row.body ?? '' }))
  }
}

async function submit(): Promise<void> {
  if (!form.translations.some((row) => (row.title ?? '').trim())) {
    ElMessage.error(t('notices.zhTitle'))
    return
  }
  submitting.value = true
  try {
    if (editing.value) {
      await http.patch(`/admin/notices/${editing.value.id}`, payload())
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/notices', payload())
      ElMessage.success(t('notices.createDraft'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function publish(notice: Notice): Promise<void> {
  try {
    await http.post(`/admin/notices/${notice.id}/publish`)
    ElMessage.success(t('notices.published'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(notice: Notice): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('common.deleteConfirm', { name: titleOf(notice, 'zh-CN') }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/notices/${notice.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

function titleOf(notice: Notice, locale: string): string {
  return notice.translations.find((item) => item.locale === locale)?.title ?? '—'
}

function formatTime(value: string | null): string {
  return formatDateTime(value, locale.value)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('notices.title') }}<HelpTip page="notices" :title="t('notices.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button type="primary" style="margin-inline-start: 12px" @click="openCreate">
          {{ t('notices.create') }}
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column :label="t('notices.zhTitle')" min-width="200">
          <template #default="{ row }">{{ titleOf(row, 'zh-CN') }}</template>
        </el-table-column>
        <el-table-column label="English" min-width="180">
          <template #default="{ row }">{{ titleOf(row, 'en') }}</template>
        </el-table-column>
        <el-table-column prop="level" :label="t('notices.level')" width="100" />
        <el-table-column prop="status" :label="t('common.status')" width="110" />
        <el-table-column :label="t('notices.pinned')" width="90">
          <template #default="{ row }">{{ row.pinned ? t('common.yes') : t('common.no') }}</template>
        </el-table-column>
        <el-table-column :label="t('notices.publishAt')" min-width="170">
          <template #default="{ row }">{{ formatTime(row.publishAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('notices.expireAt')" min-width="170">
          <template #default="{ row }">{{ formatTime(row.expireAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" :disabled="row.status === 'PUBLISHED'" @click="publish(row)">
              {{ t('notices.publish') }}
            </el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && items.length === 0" :description="t('common.empty')" />

      <el-alert
        v-if="traceId"
        type="error"
        :closable="false"
        style="margin-top: 12px"
        :title="t('common.requestFailed', { traceId })"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? t('notices.edit') : t('notices.create')"
      width="680px"
    >
      <el-form label-width="100px">
        <el-form-item :label="t('notices.level')">
          <el-select v-model="form.level" style="width: 160px">
            <el-option
              v-for="option in levelOptions"
              :key="option.value"
              :label="t(option.key)"
              :value="option.value"
            />
          </el-select>
          <el-checkbox v-model="form.pinned" style="margin-inline-start: 16px">
            {{ t('notices.pinned') }}
          </el-checkbox>
        </el-form-item>
        <el-form-item><template #label>{{ t('notices.publishAt') }}<FieldHelp help="field.notices.publishAt.help" /></template>
          <el-date-picker v-model="form.publishAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item><template #label>{{ t('notices.expireAt') }}<FieldHelp help="field.notices.expireAt.help" /></template>
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-divider content-position="left">{{ t('notices.translations') }}</el-divider>
        <TranslationsEditor v-model="form.translations" :fields="translationFields" />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editing ? t('common.save') : t('common.create') }}
        </el-button>
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










