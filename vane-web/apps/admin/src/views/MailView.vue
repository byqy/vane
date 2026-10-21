<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import HelpTip from '../components/HelpTip.vue'
import {
  downloadFile,
  formatDateTime,
  http,
  languageName,
  locales,
  problemOf,
  toIsoInstant,
  type MailLogPage,
  type MailLogView,
  type MailProviderView
} from '@vane/shared'
import { useAuthStore } from '../stores/auth'

interface TemplateView {
  id: string
  code: string
  channel: string
  status: string
  remark: string | null
  translations: Translation[]
}

interface Translation {
  locale: string
  subject: string | null
  body: string
}

interface TranslationRow {
  locale: string
  subject: string
  body: string
}

const { t, locale } = useI18n()
const auth = useAuthStore()
const tab = ref('templates')
const canProviders = auth.can('ops:mail:provider')
const canLogs = auth.can('ops:mail:log')

// ---------- 模板 ----------
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
    const { data } = await http.get<TemplateView[]>('/admin/message-templates', {
      params: { channel: 'EMAIL' }
    })
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
  const existing = new Map(template.translations.map((item) => [item.locale, item]))
  editForm.translations = locales.map((code) => {
    const found = existing.get(code)
    return { locale: code, subject: found?.subject ?? '', body: found?.body ?? '' }
  })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editing.value) {
    return
  }
  const translations = editForm.translations
    .filter((row) => row.body.trim() !== '')
    .map((row) => ({
      locale: row.locale,
      subject: row.subject.trim() === '' ? null : row.subject,
      body: row.body
    }))
  if (translations.length === 0) {
    ElMessage.error(t('common.localeRequired'))
    return
  }
  try {
    await http.patch(`/admin/message-templates/${editing.value.id}`, { translations })
    ElMessage.success(t('common.save'))
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
    if (canLogs) {
      await loadLogs()
    }
  } catch (e) {
    rendered.error = problemOf(e)?.detail ?? t('mail.sendFailed')
  }
}

// ---------- 服务商 ----------
const providers = ref<MailProviderView[]>([])
const providersLoading = ref(false)
const providerVisible = ref(false)
const editingCode = ref<string | null>(null)
const providerForm = reactive({
  code: '',
  name: '',
  host: '',
  port: 587,
  tlsMode: 'STARTTLS',
  username: '',
  password: '',
  fromAddress: '',
  fromName: '',
  enabled: true,
  isDefault: false,
  dailyLimit: 500,
  timeoutMs: 10000
})

async function loadProviders(): Promise<void> {
  if (!canProviders) {
    return
  }
  providersLoading.value = true
  try {
    providers.value = (await http.get<MailProviderView[]>('/admin/mail/providers')).data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    providersLoading.value = false
  }
}

function openCreateProvider(): void {
  editingCode.value = null
  Object.assign(providerForm, {
    code: '',
    name: '',
    host: '',
    port: 587,
    tlsMode: 'STARTTLS',
    username: '',
    password: '',
    fromAddress: '',
    fromName: '',
    enabled: true,
    isDefault: false,
    dailyLimit: 500,
    timeoutMs: 10000
  })
  providerVisible.value = true
}

function openEditProvider(provider: MailProviderView): void {
  editingCode.value = provider.code
  Object.assign(providerForm, {
    code: provider.code,
    name: provider.name,
    host: provider.host,
    port: provider.port,
    tlsMode: provider.tlsMode,
    username: provider.username ?? '',
    password: '',
    fromAddress: provider.fromAddress,
    fromName: provider.fromName ?? '',
    enabled: provider.enabled,
    isDefault: provider.isDefault,
    dailyLimit: provider.dailyLimit,
    timeoutMs: provider.timeoutMs
  })
  providerVisible.value = true
}

async function submitProvider(): Promise<void> {
  const payload = {
    ...providerForm,
    password: providerForm.password === '' ? null : providerForm.password
  }
  try {
    if (editingCode.value) {
      await http.patch(`/admin/mail/providers/${editingCode.value}`, payload)
    } else {
      await http.post('/admin/mail/providers', payload)
    }
    ElMessage.success(t('common.saved'))
    providerVisible.value = false
    await loadProviders()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function verifyProvider(provider: MailProviderView): Promise<void> {
  try {
    await http.post(`/admin/mail/providers/${provider.code}/verify`, {})
    ElMessage.success(t('mail.provider.verifyOk'))
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

async function removeProvider(provider: MailProviderView): Promise<void> {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm', { name: provider.code }), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/mail/providers/${provider.code}`)
    ElMessage.success(t('common.delete'))
    await loadProviders()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

// ---------- 发送日志 ----------
const mailLogs = ref<MailLogView[]>([])
const logsTotal = ref(0)
const logsLoading = ref(false)
const logPage = reactive({ number: 0, size: 20 })
const logFilters = reactive({ status: '', templateCode: '', to: '', timeFrom: '', timeTo: '' })

async function loadLogs(): Promise<void> {
  if (!canLogs) {
    return
  }
  logsLoading.value = true
  try {
    const { data } = await http.get<MailLogPage>('/admin/mail/logs', {
      params: {
        'page[number]': logPage.number,
        'page[size]': logPage.size,
        status: logFilters.status || undefined,
        templateCode: logFilters.templateCode || undefined,
        to: logFilters.to || undefined,
        timeFrom: logFilters.timeFrom ? toIsoInstant(logFilters.timeFrom) : undefined,
        timeTo: logFilters.timeTo ? toIsoInstant(logFilters.timeTo) : undefined
      }
    })
    mailLogs.value = data.items ?? []
    logsTotal.value = data.page?.total ?? 0
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    logsLoading.value = false
  }
}

function searchLogs(): void {
  logPage.number = 0
  void loadLogs()
}

async function exportLogs(): Promise<void> {
  try {
    await downloadFile('/admin/mail/logs/export', 'mail-logs.csv', {
      status: logFilters.status || undefined,
      templateCode: logFilters.templateCode || undefined,
      to: logFilters.to || undefined,
      timeFrom: logFilters.timeFrom ? toIsoInstant(logFilters.timeFrom) : undefined,
      timeTo: logFilters.timeTo ? toIsoInstant(logFilters.timeTo) : undefined
    })
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

function onTab(name: string | number): void {
  if (name === 'providers') {
    void loadProviders()
  } else if (name === 'logs') {
    void loadLogs()
  }
}

function localeName(code: string): string {
  return languageName(code, locale.value)
}

function statusTag(status: string): 'success' | 'danger' | 'info' {
  if (status === 'SENT') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'danger'
  }
  return 'info'
}

function timeText(value: string | null): string {
  return value ? formatDateTime(value, locale) : '—'
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
      <el-tabs v-model="tab" @tab-change="onTab">
        <el-tab-pane name="templates" :label="t('mail.tabTemplates')">
          <el-table v-loading="loading" :data="items" stripe>
            <el-table-column prop="code" :label="t('mail.code')" min-width="200" />
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
        </el-tab-pane>

        <el-tab-pane v-if="canProviders" name="providers" :label="t('mail.tabProviders')">
          <div class="toolbar">
            <el-button type="primary" @click="openCreateProvider">{{ t('common.create') }}</el-button>
          </div>
          <el-table v-loading="providersLoading" :data="providers" stripe>
            <el-table-column prop="code" :label="t('mail.provider.code')" min-width="140" />
            <el-table-column prop="name" :label="t('mail.provider.name')" min-width="140" />
            <el-table-column :label="t('mail.provider.host')" min-width="180">
              <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
            </el-table-column>
            <el-table-column prop="tlsMode" :label="t('mail.provider.tls')" width="120" />
            <el-table-column prop="fromAddress" :label="t('mail.provider.fromAddress')" min-width="200" />
            <el-table-column :label="t('mail.provider.isDefault')" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" type="success">{{ t('common.yes') }}</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.enabled')" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? t('common.enabled') : t('common.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditProvider(row)">{{ t('common.edit') }}</el-button>
                <el-button link type="primary" @click="verifyProvider(row)">{{ t('mail.provider.verify') }}</el-button>
                <el-button link type="danger" @click="removeProvider(row)">{{ t('common.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!providersLoading && providers.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

        <el-tab-pane v-if="canLogs" name="logs" :label="t('mail.tabLogs')">
          <el-form inline>
            <el-form-item :label="t('mail.status')">
              <el-select v-model="logFilters.status" clearable style="width: 130px">
                <el-option :label="t('mail.status.QUEUED')" value="QUEUED" />
                <el-option :label="t('mail.status.SENT')" value="SENT" />
                <el-option :label="t('mail.status.FAILED')" value="FAILED" />
              </el-select>
            </el-form-item>
            <el-form-item :label="t('mail.log.template')">
              <el-input v-model="logFilters.templateCode" style="width: 170px" />
            </el-form-item>
            <el-form-item :label="t('mail.to')">
              <el-input v-model="logFilters.to" :placeholder="t('mail.log.toHint')" style="width: 220px" />
            </el-form-item>
            <el-form-item :label="t('logs.time')">
              <el-date-picker
                v-model="logFilters.timeFrom"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 190px"
              />
              <span style="margin: 0 6px">–</span>
              <el-date-picker
                v-model="logFilters.timeTo"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 190px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="searchLogs">{{ t('common.search') }}</el-button>
              <el-button @click="exportLogs">{{ t('common.export') }}</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="logsLoading" :data="mailLogs" stripe>
            <el-table-column :label="t('logs.time')" min-width="170">
              <template #default="{ row }">{{ timeText(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="providerCode" :label="t('mail.provider.code')" width="130" />
            <el-table-column prop="templateCode" :label="t('mail.log.template')" min-width="160" />
            <el-table-column prop="toMasked" :label="t('mail.to')" min-width="180" />
            <el-table-column prop="subject" :label="t('mail.subject')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="t('mail.status')" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ t(`mail.status.${row.status}`) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorCode" :label="t('mail.log.error')" min-width="170" />
            <el-table-column prop="attempts" :label="t('mail.log.attempts')" width="100" />
            <el-table-column prop="traceId" label="traceId" min-width="180" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!logsLoading && mailLogs.length === 0" :description="t('common.empty')" />
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="logsTotal"
            :page-size="logPage.size"
            :current-page="logPage.number + 1"
            @current-change="(p: number) => { logPage.number = p - 1; void loadLogs() }"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="editVisible" :title="t('mail.editTemplate')" width="680px">
      <p class="meta">{{ t('common.localeHint') }}</p>
      <el-collapse>
        <el-collapse-item v-for="(row, index) in editForm.translations" :key="row.locale" :title="localeName(row.locale)">
          <el-form label-position="top">
            <el-form-item v-for="field in translationFields" :key="field.key" :label="t(field.labelKey)">
              <el-input v-if="field.textarea" v-model="row.body" type="textarea" :rows="4" />
              <el-input v-else v-model="row.subject" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitEdit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sendVisible" :title="t('mail.sendTitle')" width="620px">
      <el-form label-position="top">
        <el-form-item :label="t('mail.to')"><el-input v-model="sendForm.to" /></el-form-item>
        <el-form-item :label="t('mail.locale')">
          <el-select v-model="sendForm.locale" filterable style="width: 260px">
            <el-option v-for="code in locales" :key="code" :label="localeName(code)" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('mail.variables')">
          <el-input v-model="sendForm.variables" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <el-alert v-if="rendered.error" type="error" :closable="false" :title="rendered.error" />
      <template v-if="rendered.body">
        <h4>{{ t('mail.rendered') }}</h4>
        <p>{{ rendered.subject }}</p>
        <pre class="body">{{ rendered.body }}</pre>
      </template>
      <template #footer>
        <el-button @click="sendVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitSend">{{ t('mail.send') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="providerVisible"
      :title="editingCode ? t('mail.provider.name') : t('common.create')"
      width="680px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('mail.provider.code')">
          <el-input v-model="providerForm.code" :disabled="!!editingCode" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.name')"><el-input v-model="providerForm.name" /></el-form-item>
        <el-form-item :label="t('mail.provider.host')"><el-input v-model="providerForm.host" /></el-form-item>
        <el-form-item :label="t('mail.provider.port')">
          <el-input-number v-model="providerForm.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.tls')">
          <el-select v-model="providerForm.tlsMode">
            <el-option label="STARTTLS" value="STARTTLS" />
            <el-option label="SSL" value="SSL" />
            <el-option label="NONE" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('mail.provider.username')">
          <el-input v-model="providerForm.username" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.password')">
          <el-input
            v-model="providerForm.password"
            type="password"
            show-password
            :placeholder="editingCode ? t('mail.provider.passwordKeep') : ''"
          />
        </el-form-item>
        <el-form-item :label="t('mail.provider.fromAddress')">
          <el-input v-model="providerForm.fromAddress" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.fromName')">
          <el-input v-model="providerForm.fromName" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.dailyLimit')">
          <el-input-number v-model="providerForm.dailyLimit" :min="1" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.timeout')">
          <el-input-number v-model="providerForm.timeoutMs" :min="1000" :max="120000" />
        </el-form-item>
        <el-form-item :label="t('mail.provider.isDefault')">
          <el-switch v-model="providerForm.isDefault" />
        </el-form-item>
        <el-form-item :label="t('common.enabled')">
          <el-switch v-model="providerForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitProvider">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}

.toolbar {
  margin-bottom: 12px;
}

.body {
  margin: 0;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
