<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import { http, localeLabels, locales, problemOf } from '@vane/shared'
import TranslationsEditor from '../components/TranslationsEditor.vue'
import type { TranslationRow } from '../components/translation-types'

interface Translation {
  locale: string
  subject: string | null
  body: string
}

interface TemplateView {
  id: string
  code: string
  channel: string
  status: string
  remark: string | null
  translations: Translation[]
}

const { t } = useI18n()
const items = ref<TemplateView[]>([])
const loading = ref(false)
const traceId = ref<string | null>(null)

const editVisible = ref(false)
const editing = ref<TemplateView | null>(null)
const editForm = reactive({ translations: [] as TranslationRow[] })

const translationFields = [
  { key: 'subject', labelKey: 'mail.subject' },
  { key: 'body', labelKey: 'mail.body', textarea: true }
]

const sendVisible = ref(false)
const sending = ref<TemplateView | null>(null)
const sendForm = reactive({
  to: '',
  locale: 'zh-CN',
  variables: '{"siteName":"vane","username":"demo"}'
})
const rendered = reactive({ subject: '', body: '', error: '' })

async function load(): Promise<void> {
  loading.value = true
  traceId.value = null
  try {
    const { data } = await http.get<TemplateView[]>('/admin/message-templates')
    items.value = data
  } catch (e) {
    const problem = problemOf(e)
    traceId.value = problem?.traceId ?? null
    ElMessage.error(problem?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openEdit(template: TemplateView): void {
  editing.value = template
  editForm.translations = template.translations.map((item) => ({
    locale: item.locale,
    subject: item.subject ?? '',
    body: item.body
  }))
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editing.value) {
    return
  }
  const translations = editForm.translations
    .filter((row) => (row.body ?? '').trim())
    .map((row) => ({ locale: row.locale, subject: row.subject ?? '', body: row.body }))
  try {
    await http.patch(`/admin/message-templates/${editing.value.id}`, { translations })
    ElMessage.success(t('common.saveSuccess'))
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

function openSend(template: TemplateView): void {
  sending.value = template
  rendered.subject = ''
  rendered.body = ''
  rendered.error = ''
  sendVisible.value = true
}

async function submitSend(): Promise<void> {
  if (!sending.value) {
    return
  }
  let variables: Record<string, string>
  try {
    variables = JSON.parse(sendForm.variables || '{}')
  } catch {
    rendered.error = t('mail.variables')
    return
  }
  try {
    const { data } = await http.post<{ subject: string | null; body: string }>(
      `/admin/message-templates/${sending.value.code}/send`,
      { to: sendForm.to, locale: sendForm.locale, variables }
    )
    rendered.subject = data.subject ?? ''
    rendered.body = data.body
    rendered.error = ''
    ElMessage.success(t('mail.rendered'))
  } catch (e) {
    rendered.error = problemOf(e)?.detail ?? t('mail.sendFailed')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('mail.title') }}<HelpTip page="mail" :title="t('mail.title')" /></h3>
      <span class="meta">{{ t('common.total') }} {{ items.length }}</span>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="code" :label="t('mail.code')" min-width="200" />
        <el-table-column prop="channel" :label="t('mail.channel')" width="120" />
        <el-table-column :label="t('mail.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('mail.locales')" width="110">
          <template #default="{ row }">{{ row.translations.length }}</template>
        </el-table-column>
        <el-table-column prop="remark" :label="t('mail.remark')" min-width="160" />
        <el-table-column :label="t('common.actions')" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('mail.editTemplate') }}</el-button>
            <el-button link type="primary" @click="openSend(row)">{{ t('mail.sendTest') }}</el-button>
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

    <el-dialog v-model="editVisible" :title="t('mail.editTemplate')" width="680px">
      <el-form label-width="100px">
        <el-divider content-position="left">{{ t('notices.translations') }}</el-divider>
        <TranslationsEditor v-model="editForm.translations" :fields="translationFields" />
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitEdit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sendVisible" :title="t('mail.sendTitle')" width="620px">
      <el-form label-width="110px">
        <el-form-item :label="t('mail.to')"><el-input v-model="sendForm.to" placeholder="demo@example.com" /></el-form-item>
        <el-form-item :label="t('mail.locale')">
          <el-select v-model="sendForm.locale" style="width: 220px">
            <el-option v-for="item in locales" :key="item" :label="localeLabels[item]" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>{{ t('mail.variables') }}<FieldHelp help="field.mail.variables.help" /></template>
          <el-input v-model="sendForm.variables" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>

      <el-descriptions v-if="rendered.body" :column="1" border style="margin-top: 8px">
        <el-descriptions-item :label="t('mail.subject')">{{ rendered.subject || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="t('mail.body')">{{ rendered.body }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="rendered.error" type="error" :closable="false" :title="rendered.error" style="margin-top: 8px" />

      <template #footer>
        <el-button @click="sendVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitSend">{{ t('mail.send') }}</el-button>
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




