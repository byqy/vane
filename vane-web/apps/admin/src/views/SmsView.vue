<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { downloadFile, formatDateTime, http, localeLabels, locales, problemOf } from '@vane/shared'
import type {
  MessageTemplateView,
  SmsBindingView,
  SmsCampaignPage,
  SmsCampaignView,
  SmsImportResult,
  SmsInboundPage,
  SmsLogPage,
  SmsLogView,
  SmsOptoutPage,
  SmsOptoutView,
  SmsProviderView,
  SmsReceiptView,
  SmsRecipientPage,
  SmsSignatureView
} from '@vane/shared'

interface SmsTemplate {
  id: string
  code: string
  channel: string
}

const { t } = useI18n()
const tab = ref('providers')
const countries = ['CN', 'HK', 'MO', 'TW', 'SG', 'MY', 'TH', 'VN', 'ID', 'PH']
const providerTypes = ['ALIYUN', 'TENCENT', 'GENERIC_HTTP']
const providerFields: Record<string, string[]> = {
  ALIYUN: ['accessKeyId', 'accessKeySecret', 'regionId'],
  TENCENT: ['secretId', 'secretKey', 'sdkAppId', 'region'],
  GENERIC_HTTP: ['url']
}
const statuses = ['ACTIVE', 'DISABLED']

function showError(e: unknown, fallback: string): void {
  ElMessage.error(problemOf(e)?.detail ?? fallback)
}

// ---------- 服务商 ----------
const providers = ref<SmsProviderView[]>([])
const providersLoading = ref(false)
const providerDialog = ref(false)
const providerEditing = ref(false)
const providerForm = reactive({
  code: '',
  type: 'ALIYUN',
  name: '',
  countries: ['CN'] as string[],
  enabled: true,
  isDefault: false,
  config: {} as Record<string, string>
})

async function loadProviders(): Promise<void> {
  providersLoading.value = true
  try {
    providers.value = (await http.get<SmsProviderView[]>('/admin/sms/providers')).data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    providersLoading.value = false
  }
}

function openProviderCreate(): void {
  providerEditing.value = false
  Object.assign(providerForm, {
    code: '', type: 'ALIYUN', name: '', countries: ['CN'], enabled: true, isDefault: false, config: {}
  })
  providerDialog.value = true
}

function openProviderEdit(row: SmsProviderView): void {
  providerEditing.value = true
  const config: Record<string, string> = {}
  for (const field of providerFields[row.type] ?? []) {
    config[field] = ''
  }
  Object.assign(providerForm, {
    code: row.code,
    type: row.type,
    name: row.name,
    countries: [...row.countries],
    enabled: row.enabled,
    isDefault: row.isDefault,
    config
  })
  providerDialog.value = true
}

async function submitProvider(): Promise<void> {
  if (!providerForm.code.trim() || !providerForm.name.trim()) {
    ElMessage.error(t('common.saveFailed'))
    return
  }
  const touched = Object.values(providerForm.config).some((value) => (value ?? '').trim())
  const payload: Record<string, unknown> = {
    name: providerForm.name,
    countries: providerForm.countries,
    enabled: providerForm.enabled,
    isDefault: providerForm.isDefault
  }
  if (touched) {
    payload.config = providerForm.config
  }
  try {
    if (providerEditing.value) {
      await http.patch(`/admin/sms/providers/${providerForm.code}`, payload)
    } else {
      await http.post('/admin/sms/providers', {
        code: providerForm.code,
        type: providerForm.type,
        ...payload
      })
    }
    ElMessage.success(t('common.saveSuccess'))
    providerDialog.value = false
    await loadProviders()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

const testDialog = ref(false)
const testingProvider = ref('')
const testForm = reactive({ phone: '', signature: '', templateCode: '', variables: '{"code":"123456"}' })

function openTest(code: string): void {
  testingProvider.value = code
  Object.assign(testForm, { phone: '', signature: '', templateCode: '', variables: '{"code":"123456"}' })
  testDialog.value = true
}

async function submitTest(): Promise<void> {
  let variables: Record<string, string> = {}
  try {
    variables = JSON.parse(testForm.variables || '{}')
  } catch {
    ElMessage.error(t('sms.variables'))
    return
  }
  try {
    const { data } = await http.post<{ success: boolean; providerStatus: string }>(
      `/admin/sms/providers/${testingProvider.value}/test`,
      { phone: testForm.phone, signature: testForm.signature, templateCode: testForm.templateCode, variables }
    )
    if (data.success) {
      ElMessage.success(t('sms.testSuccess'))
    } else {
      ElMessage.error(t('sms.failed'))
    }
    testDialog.value = false
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

// ---------- 签名 ----------
const signatures = ref<SmsSignatureView[]>([])
const signaturesLoading = ref(false)
const signatureDialog = ref(false)
const signatureEditing = ref(false)
const signatureForm = reactive({
  id: '',
  providerCode: '',
  signName: '',
  countryCode: 'CN',
  locale: 'zh-CN',
  status: 'ACTIVE',
  approvalStatus: ''
})

async function loadSignatures(): Promise<void> {
  signaturesLoading.value = true
  try {
    signatures.value = (await http.get<SmsSignatureView[]>('/admin/sms/signatures')).data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    signaturesLoading.value = false
  }
}

function openSignatureCreate(): void {
  signatureEditing.value = false
  Object.assign(signatureForm, {
    id: '',
    providerCode: providers.value[0]?.code ?? '',
    signName: '',
    countryCode: 'CN',
    locale: 'zh-CN',
    status: 'ACTIVE',
    approvalStatus: ''
  })
  signatureDialog.value = true
}

function openSignatureEdit(row: SmsSignatureView): void {
  signatureEditing.value = true
  Object.assign(signatureForm, {
    id: row.id,
    providerCode: row.providerCode,
    signName: row.signName,
    countryCode: row.countryCode,
    locale: row.locale,
    status: row.status,
    approvalStatus: row.approvalStatus ?? ''
  })
  signatureDialog.value = true
}

async function submitSignature(): Promise<void> {
  if (!signatureForm.providerCode || !signatureForm.signName.trim()) {
    ElMessage.error(t('common.saveFailed'))
    return
  }
  const payload: Record<string, unknown> = {
    signName: signatureForm.signName,
    countryCode: signatureForm.countryCode,
    locale: signatureForm.locale,
    status: signatureForm.status,
    approvalStatus: signatureForm.approvalStatus || null
  }
  try {
    if (signatureEditing.value) {
      await http.patch(`/admin/sms/signatures/${signatureForm.id}`, payload)
    } else {
      await http.post('/admin/sms/signatures', { providerCode: signatureForm.providerCode, ...payload })
    }
    ElMessage.success(t('common.saveSuccess'))
    signatureDialog.value = false
    await loadSignatures()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

// ---------- 模板绑定 ----------
const bindings = ref<SmsBindingView[]>([])
const bindingsLoading = ref(false)
const templates = ref<SmsTemplate[]>([])
const bindingDialog = ref(false)
const bindingTemplateId = ref('')
const bindingRows = ref<Array<Record<string, string>>>([])

async function loadBindings(): Promise<void> {
  bindingsLoading.value = true
  try {
    bindings.value = (await http.get<SmsBindingView[]>('/admin/sms/template-bindings')).data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    bindingsLoading.value = false
  }
}

async function openBindings(): Promise<void> {
  if (templates.value.length === 0) {
    try {
      templates.value = (await http.get<SmsTemplate[]>('/admin/message-templates?channel=SMS')).data
    } catch (e) {
      showError(e, t('common.loadFailed'))
    }
  }
  bindingTemplateId.value = templates.value[0]?.id ?? ''
  bindingRows.value = []
  bindingDialog.value = true
  if (bindingTemplateId.value) {
    await loadBindingRows(bindingTemplateId.value)
  }
}

async function loadBindingRows(templateId: string): Promise<void> {
  bindingTemplateId.value = templateId
  try {
    const existing = (await http.get<SmsBindingView[]>(`/admin/sms/template-bindings/${templateId}`)).data
    bindingRows.value = existing.map((row) => ({
      providerCode: row.providerCode,
      providerTemplateCode: row.providerTemplateCode,
      signatureId: row.signatureId ?? '',
      countryCode: row.countryCode,
      locale: row.locale,
      paramMapping: row.paramMappingJson || '{}'
    }))
  } catch (e) {
    showError(e, t('common.loadFailed'))
  }
}

function addBindingRow(): void {
  bindingRows.value.push({
    providerCode: providers.value[0]?.code ?? '',
    providerTemplateCode: '',
    signatureId: '',
    countryCode: 'CN',
    locale: 'zh-CN',
    paramMapping: '{}'
  })
}

async function submitBindings(): Promise<void> {
  if (!bindingTemplateId.value) {
    ElMessage.error(t('sms.selectTemplate'))
    return
  }
  const rows = []
  for (const row of bindingRows.value) {
    let paramMapping: Record<string, string> = {}
    try {
      paramMapping = JSON.parse(row.paramMapping || '{}')
    } catch {
      ElMessage.error(t('sms.paramMapping'))
      return
    }
    rows.push({
      providerCode: row.providerCode,
      providerTemplateCode: row.providerTemplateCode,
      signatureId: row.signatureId || null,
      countryCode: row.countryCode,
      locale: row.locale,
      paramMapping
    })
  }
  try {
    await http.put(`/admin/sms/template-bindings/${bindingTemplateId.value}`, { bindings: rows })
    ElMessage.success(t('sms.bindingsSaved'))
    bindingDialog.value = false
    await loadBindings()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

// ---------- 发送日志 ----------
const logs = ref<SmsLogPage>({ items: [], page: { number: 0, size: 20, total: 0 } })
const logsLoading = ref(false)
const logFilters = reactive({ templateCode: '', providerCode: '', result: '' })

async function loadLogs(): Promise<void> {
  logsLoading.value = true
  try {
    const { data } = await http.get<SmsLogPage>('/admin/sms/logs', {
      params: {
        'page[number]': logs.value.page.number,
        'page[size]': logs.value.page.size,
        templateCode: logFilters.templateCode || undefined,
        providerCode: logFilters.providerCode || undefined,
        result: logFilters.result || undefined
      }
    })
    logs.value = data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    logsLoading.value = false
  }
}

function changeLogPage(page: number): void {
  logs.value.page.number = page - 1
  void loadLogs()
}

// ---------- 群发活动 ----------
const campaigns = ref<SmsCampaignPage>({ items: [], page: { number: 0, size: 20, total: 0 } })
const campaignsLoading = ref(false)
const campaignDialog = ref(false)
const campaignForm = reactive({
  name: '',
  templateId: '',
  providerCode: '',
  locale: 'zh-CN',
  throttlePerSecond: 100,
  dailyLimit: undefined as number | undefined,
  budgetAmount: undefined as number | undefined
})

async function loadCampaigns(): Promise<void> {
  campaignsLoading.value = true
  try {
    const { data } = await http.get<SmsCampaignPage>('/admin/sms/campaigns', {
      params: { 'page[number]': campaigns.value.page.number, 'page[size]': campaigns.value.page.size }
    })
    campaigns.value = data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    campaignsLoading.value = false
  }
}

function openCampaignCreate(): void {
  Object.assign(campaignForm, {
    name: '',
    templateId: templates.value[0]?.id ?? '',
    providerCode: providers.value[0]?.code ?? '',
    locale: 'zh-CN',
    throttlePerSecond: 100,
    dailyLimit: undefined,
    budgetAmount: undefined
  })
  campaignDialog.value = true
}

async function submitCampaign(): Promise<void> {
  if (!campaignForm.name.trim() || !campaignForm.templateId || !campaignForm.providerCode) {
    ElMessage.error(t('common.saveFailed'))
    return
  }
  try {
    await http.post('/admin/sms/campaigns', {
      name: campaignForm.name,
      templateId: campaignForm.templateId,
      providerCode: campaignForm.providerCode,
      locale: campaignForm.locale,
      throttlePerSecond: campaignForm.throttlePerSecond,
      dailyLimit: campaignForm.dailyLimit ?? null,
      budgetAmount: campaignForm.budgetAmount ?? null
    })
    ElMessage.success(t('sms.campaignCreated'))
    campaignDialog.value = false
    await loadCampaigns()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

async function campaignAction(row: SmsCampaignView, action: 'start' | 'pause' | 'cancel'): Promise<void> {
  try {
    await http.post(`/admin/sms/campaigns/${row.id}/${action}`, {})
    ElMessage.success(
      action === 'start' ? t('sms.campaignStarted') : action === 'pause' ? t('sms.campaignPaused') : t('sms.campaignCancelled')
    )
    await loadCampaigns()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

const importDialog = ref(false)
const importCampaign = ref<SmsCampaignView | null>(null)
const importFile = ref<File | null>(null)
const importPreview = ref<SmsImportResult | null>(null)

function openImport(row: SmsCampaignView): void {
  importCampaign.value = row
  importFile.value = null
  importPreview.value = null
  importDialog.value = true
}

function onImportFile(event: Event): void {
  const input = event.target as HTMLInputElement
  importFile.value = input.files?.[0] ?? null
  importPreview.value = null
}

async function postCampaignFile(path: string): Promise<SmsImportResult> {
  const form = new FormData()
  form.append('file', importFile.value as File)
  const { data } = await http.post<SmsImportResult>(path, form)
  return data
}

async function previewImport(): Promise<void> {
  if (!importFile.value) {
    ElMessage.error(t('sms.selectFile'))
    return
  }
  try {
    importPreview.value = await postCampaignFile('/admin/sms/campaigns/import/preview')
  } catch (e) {
    showError(e, t('common.loadFailed'))
  }
}

async function confirmImport(): Promise<void> {
  if (!importCampaign.value || !importFile.value) {
    ElMessage.error(t('sms.selectFile'))
    return
  }
  try {
    importPreview.value = await postCampaignFile(
      `/admin/sms/campaigns/${importCampaign.value.id}/recipients/import`
    )
    ElMessage.success(t('sms.importDone'))
    await loadCampaigns()
  } catch (e) {
    showError(e, t('sms.importDone'))
  }
}

async function exportCampaignErrors(row: SmsCampaignView): Promise<void> {
  try {
    await downloadFile(`/admin/sms/campaigns/${row.id}/errors`, `sms-campaign-errors-${row.id}.csv`)
  } catch (e) {
    showError(e, t('common.loadFailed'))
  }
}

const recipientsDialog = ref(false)
const recipients = ref<SmsRecipientPage>({ items: [], page: { number: 0, size: 20, total: 0 } })
const recipientsLoading = ref(false)

async function openRecipients(row: SmsCampaignView): Promise<void> {
  recipientsDialog.value = true
  recipients.value = { items: [], page: { number: 0, size: 20, total: 0 } }
  recipientsLoading.value = true
  try {
    const { data } = await http.get<SmsRecipientPage>(`/admin/sms/campaigns/${row.id}/recipients`, {
      params: { 'page[number]': 0, 'page[size]': 20 }
    })
    recipients.value = data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    recipientsLoading.value = false
  }
}

function changeCampaignTab(): void {
  if (templates.value.length === 0) {
    http
      .get<SmsTemplate[]>('/admin/message-templates?channel=SMS')
      .then(({ data }) => {
        templates.value = data
      })
      .catch((e) => showError(e, t('common.loadFailed')))
  }
  void loadCampaigns()
}

// ---------- 回执 ----------
const receiptDialog = ref(false)
const receiptLoading = ref(false)
const receipts = ref<SmsReceiptView[]>([])
const receiptLog = ref<SmsLogView | null>(null)

async function openReceipts(row: SmsLogView): Promise<void> {
  receiptLog.value = row
  receiptDialog.value = true
  receiptLoading.value = true
  receipts.value = []
  try {
    const { data } = await http.get<SmsReceiptView[]>(`/admin/sms/logs/${row.id}/receipts`)
    receipts.value = data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    receiptLoading.value = false
  }
}

// ---------- 退订名单 ----------
const optouts = ref<SmsOptoutPage>({ items: [], page: { number: 0, size: 20, total: 0 } })
const optoutsLoading = ref(false)
const optoutDialog = ref(false)
const optoutForm = reactive({ phone: '', scope: 'MARKETING', reason: '' })

async function loadOptouts(): Promise<void> {
  optoutsLoading.value = true
  try {
    const { data } = await http.get<SmsOptoutPage>('/admin/sms/optouts', {
      params: { 'page[number]': optouts.value.page.number, 'page[size]': optouts.value.page.size }
    })
    optouts.value = data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    optoutsLoading.value = false
  }
}

function openOptoutCreate(): void {
  Object.assign(optoutForm, { phone: '', scope: 'MARKETING', reason: '' })
  optoutDialog.value = true
}

async function submitOptout(): Promise<void> {
  if (!optoutForm.phone.trim()) {
    ElMessage.error(t('common.saveFailed'))
    return
  }
  try {
    await http.post('/admin/sms/optouts', { ...optoutForm })
    ElMessage.success(t('sms.optoutCreated'))
    optoutDialog.value = false
    await loadOptouts()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

async function deleteOptout(row: SmsOptoutView): Promise<void> {
  try {
    await ElMessageBox.confirm(t('sms.deleteConfirm'), t('common.actions'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/sms/optouts/${row.id}`)
    ElMessage.success(t('common.saveSuccess'))
    await loadOptouts()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

// ---------- 上行消息 ----------
const inbounds = ref<SmsInboundPage>({ items: [], page: { number: 0, size: 20, total: 0 } })
const inboundsLoading = ref(false)

async function loadInbounds(): Promise<void> {
  inboundsLoading.value = true
  try {
    const { data } = await http.get<SmsInboundPage>('/admin/sms/inbounds', {
      params: { 'page[number]': inbounds.value.page.number, 'page[size]': inbounds.value.page.size }
    })
    inbounds.value = data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    inboundsLoading.value = false
  }
}

function changeTab(name: string): void {
  if (name === 'templates') {
    void loadSmsTemplates()
  } else if (name === 'signatures') {
    void loadSignatures()
  } else if (name === 'bindings') {
    void loadBindings()
  } else if (name === 'logs') {
    void loadLogs()
  } else if (name === 'campaigns') {
    changeCampaignTab()
  } else if (name === 'optouts') {
    void loadOptouts()
  } else if (name === 'inbounds') {
    void loadInbounds()
  }
}

// ---------- 短信模板 ----------
const smsTemplates = ref<MessageTemplateView[]>([])
const smsTemplatesLoading = ref(false)
const templateVisible = ref(false)
const editingTemplateId = ref('')
const templateForm = reactive({ translations: [] as Array<{ locale: string; subject: string; body: string }> })

async function loadSmsTemplates(): Promise<void> {
  smsTemplatesLoading.value = true
  try {
    smsTemplates.value = (await http.get<MessageTemplateView[]>('/admin/message-templates?channel=SMS')).data
  } catch (e) {
    showError(e, t('common.loadFailed'))
  } finally {
    smsTemplatesLoading.value = false
  }
}

function openTemplateEdit(template: MessageTemplateView): void {
  editingTemplateId.value = template.id
  templateForm.translations = template.translations.map((item) => ({
    locale: item.locale,
    subject: item.subject ?? '',
    body: item.body
  }))
  templateVisible.value = true
}

async function submitTemplateEdit(): Promise<void> {
  try {
    await http.patch(`/admin/message-templates/${editingTemplateId.value}`, {
      translations: templateForm.translations
    })
    ElMessage.success(t('common.save'))
    templateVisible.value = false
    await loadSmsTemplates()
  } catch (e) {
    showError(e, t('common.saveFailed'))
  }
}

onMounted(loadProviders)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('sms.title') }}</h3>
    </div>

    <div class="card">
      <el-tabs v-model="tab" @tab-change="changeTab">
        <el-tab-pane :label="t('sms.tabTemplates')" name="templates">
          <el-table v-loading="smsTemplatesLoading" :data="smsTemplates" stripe>
            <el-table-column prop="code" :label="t('mail.code')" min-width="200" />
            <el-table-column :label="t('common.status')" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('mail.locales')" width="110">
              <template #default="{ row }">{{ row.translations.length }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTemplateEdit(row)">
                  {{ t('mail.editTemplate') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!smsTemplatesLoading && smsTemplates.length === 0" :description="t('common.empty')" />
        </el-tab-pane>
        <el-tab-pane :label="t('sms.providers')" name="providers">
          <div class="toolbar">
            <el-button type="primary" @click="openProviderCreate">{{ t('sms.createProvider') }}</el-button>
            <span class="meta">{{ t('common.total') }} {{ providers.length }}</span>
          </div>
          <el-table v-loading="providersLoading" :data="providers" stripe>
            <el-table-column prop="code" :label="t('sms.code')" min-width="150" />
            <el-table-column prop="type" :label="t('sms.type')" width="130" />
            <el-table-column prop="name" :label="t('sms.name')" min-width="150" />
            <el-table-column :label="t('sms.countries')" min-width="160">
              <template #default="{ row }">{{ row.countries.join(', ') }}</template>
            </el-table-column>
            <el-table-column :label="t('sms.default')" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" type="success">{{ t('common.yes') }}</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('sms.enabled')" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.yes') : t('common.no') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openProviderEdit(row)">{{ t('common.edit') }}</el-button>
                <el-button link type="primary" @click="openTest(row.code)">{{ t('sms.testTitle') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!providersLoading && providers.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

        <el-tab-pane :label="t('sms.signatures')" name="signatures">
          <div class="toolbar">
            <el-button type="primary" @click="openSignatureCreate">{{ t('sms.createSignature') }}</el-button>
            <span class="meta">{{ t('common.total') }} {{ signatures.length }}</span>
          </div>
          <el-table v-loading="signaturesLoading" :data="signatures" stripe>
            <el-table-column prop="signName" :label="t('sms.signName')" min-width="150" />
            <el-table-column prop="providerCode" :label="t('sms.provider')" min-width="140" />
            <el-table-column prop="countryCode" :label="t('sms.country')" width="110" />
            <el-table-column prop="locale" :label="t('sms.locale')" width="110" />
            <el-table-column :label="t('sms.status')" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approvalStatus" :label="t('sms.approvalStatus')" min-width="140" />
            <el-table-column :label="t('common.actions')" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSignatureEdit(row)">{{ t('common.edit') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!signaturesLoading && signatures.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

        <el-tab-pane :label="t('sms.bindings')" name="bindings">
          <div class="toolbar">
            <el-button type="primary" @click="openBindings">{{ t('sms.saveBindings') }}</el-button>
            <span class="meta">{{ t('common.total') }} {{ bindings.length }}</span>
          </div>
          <el-alert type="info" :closable="false" :title="t('sms.bindingsHint')" style="margin-bottom: 12px" />
          <el-table v-loading="bindingsLoading" :data="bindings" stripe>
            <el-table-column prop="templateCode" :label="t('sms.template')" min-width="160" />
            <el-table-column prop="providerCode" :label="t('sms.provider')" min-width="140" />
            <el-table-column prop="providerTemplateCode" :label="t('sms.providerTemplateCode')" min-width="170" />
            <el-table-column prop="countryCode" :label="t('sms.country')" width="110" />
            <el-table-column prop="locale" :label="t('sms.locale')" width="110" />
          </el-table>
          <el-empty v-if="!bindingsLoading && bindings.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

        <el-tab-pane :label="t('sms.logs')" name="logs">
          <div class="toolbar">
            <el-input v-model="logFilters.templateCode" :placeholder="t('sms.filterTemplate')" clearable style="width: 180px" />
            <el-input v-model="logFilters.providerCode" :placeholder="t('sms.filterProvider')" clearable style="width: 180px" />
            <el-select v-model="logFilters.result" :placeholder="t('sms.filterResult')" clearable style="width: 140px">
              <el-option :label="t('sms.success')" value="SUCCESS" />
              <el-option :label="t('sms.failed')" value="FAIL" />
            </el-select>
            <el-button type="primary" @click="loadLogs">{{ t('common.search') }}</el-button>
          </div>
          <el-table v-loading="logsLoading" :data="logs.items" stripe>
            <el-table-column prop="createdAt" :label="t('sms.time')" min-width="170" />
            <el-table-column prop="templateCode" :label="t('sms.templateCode')" min-width="150" />
            <el-table-column prop="phoneMasked" :label="t('sms.phone')" min-width="140" />
            <el-table-column prop="providerCode" :label="t('sms.provider')" min-width="130" />
            <el-table-column prop="result" :label="t('sms.result')" width="100">
              <template #default="{ row }">
                <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
                  {{ row.result === 'SUCCESS' ? t('sms.success') : t('sms.failed') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('sms.deliveryStatus')" width="120">
              <template #default="{ row }">{{ row.deliveryStatus ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="errorCode" :label="t('sms.errorCode')" min-width="140" />
            <el-table-column :label="t('sms.receipts')" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="openReceipts(row)">{{ t('sms.receipts') }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" :label="t('sms.duration')" width="110" />
            <el-table-column prop="traceId" :label="t('sms.traceId')" min-width="180" />
          </el-table>
          <el-empty v-if="!logsLoading && logs.items.length === 0" :description="t('sms.emptyLogs')" />
          <el-pagination
            v-if="logs.page.total > logs.page.size"
            :current-page="logs.page.number + 1"
            :page-size="logs.page.size"
            :total="logs.page.total"
            layout="prev, pager, next"
            style="margin-top: 12px"
            @current-change="changeLogPage"
          />
        </el-tab-pane>
        <el-tab-pane :label="t('sms.campaigns')" name="campaigns">
          <div class="toolbar">
            <el-button type="primary" @click="openCampaignCreate">{{ t('sms.createCampaign') }}</el-button>
            <span class="meta">{{ t('common.total') }} {{ campaigns.page.total }}</span>
          </div>
          <el-table v-loading="campaignsLoading" :data="campaigns.items" stripe>
            <el-table-column prop="name" :label="t('sms.campaignName')" min-width="170" />
            <el-table-column prop="templateCode" :label="t('sms.templateCode')" min-width="150" />
            <el-table-column :label="t('sms.campaignStatus')" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'DONE' ? 'success' : row.status === 'CANCELLED' ? 'info' : 'warning'">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('sms.progress')" width="140">
              <template #default="{ row }">{{ row.sent }} / {{ row.total }}（{{ row.failed }}）</template>
            </el-table-column>
            <el-table-column :label="t('sms.time')" min-width="170">
              <template #default="{ row }">{{ row.createdAt ? formatDateTime(row.createdAt) : '—' }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="330" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="!['DRAFT', 'SCHEDULED', 'PAUSED'].includes(row.status)" @click="campaignAction(row, 'start')">
                  {{ t('sms.start') }}
                </el-button>
                <el-button link type="primary" :disabled="!['SCHEDULED', 'RUNNING'].includes(row.status)" @click="campaignAction(row, 'pause')">
                  {{ t('sms.pause') }}
                </el-button>
                <el-button link type="danger" :disabled="['DONE', 'CANCELLED'].includes(row.status)" @click="campaignAction(row, 'cancel')">
                  {{ t('sms.cancel') }}
                </el-button>
                <el-button link type="primary" @click="openImport(row)">{{ t('sms.import') }}</el-button>
                <el-button link type="primary" @click="openRecipients(row)">{{ t('sms.recipients') }}</el-button>
                <el-button link type="primary" @click="exportCampaignErrors(row)">{{ t('sms.downloadErrors') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!campaignsLoading && campaigns.items.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

        <el-tab-pane :label="t('sms.optouts')" name="optouts">
          <div class="toolbar">
            <el-button type="primary" @click="openOptoutCreate">{{ t('sms.createOptout') }}</el-button>
            <span class="meta">{{ t('common.total') }} {{ optouts.page.total }}</span>
          </div>
          <el-table v-loading="optoutsLoading" :data="optouts.items" stripe>
            <el-table-column :label="t('sms.hash')" min-width="200">
              <template #default="{ row }">{{ row.phoneHash.slice(0, 16) }}…</template>
            </el-table-column>
            <el-table-column prop="scope" :label="t('sms.scope')" width="120" />
            <el-table-column prop="source" :label="t('sms.source')" width="120" />
            <el-table-column prop="reason" :label="t('sms.reason')" min-width="180" />
            <el-table-column :label="t('sms.time')" min-width="170">
              <template #default="{ row }">{{ row.createdAt ? formatDateTime(row.createdAt) : '—' }}</template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="100">
              <template #default="{ row }">
                <el-button link type="danger" @click="deleteOptout(row)">{{ t('sms.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!optoutsLoading && optouts.items.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

        <el-tab-pane :label="t('sms.inbounds')" name="inbounds">
          <div class="toolbar">
            <span class="meta">{{ t('common.total') }} {{ inbounds.page.total }}</span>
          </div>
          <el-table v-loading="inboundsLoading" :data="inbounds.items" stripe>
            <el-table-column prop="providerCode" :label="t('sms.provider')" min-width="130" />
            <el-table-column prop="phoneMasked" :label="t('sms.phone')" min-width="140" />
            <el-table-column prop="content" :label="t('sms.content')" min-width="200" />
            <el-table-column :label="t('sms.receivedAt')" min-width="170">
              <template #default="{ row }">{{ row.receivedAt ? formatDateTime(row.receivedAt) : '—' }}</template>
            </el-table-column>
            <el-table-column :label="t('sms.processed')" width="100">
              <template #default="{ row }">{{ row.processed ? t('common.yes') : t('common.no') }}</template>
            </el-table-column>
            <el-table-column prop="matchedKeyword" :label="t('sms.matchedKeyword')" min-width="140" />
          </el-table>
          <el-empty v-if="!inboundsLoading && inbounds.items.length === 0" :description="t('common.empty')" />
        </el-tab-pane>

      </el-tabs>
    </div>

    <el-dialog v-model="providerDialog" :title="providerEditing ? t('sms.editProvider') : t('sms.createProvider')" width="620px">
      <el-form label-width="130px">
        <el-form-item :label="t('sms.code')">
          <el-input v-model="providerForm.code" :disabled="providerEditing" placeholder="aliyun-cn" />
        </el-form-item>
        <el-form-item :label="t('sms.type')">
          <el-select v-model="providerForm.type" :disabled="providerEditing" style="width: 220px">
            <el-option v-for="item in providerTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('sms.name')"><el-input v-model="providerForm.name" /></el-form-item>
        <el-form-item :label="t('sms.countries')">
          <el-select v-model="providerForm.countries" multiple style="width: 100%">
            <el-option v-for="item in countries" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item v-for="field in providerFields[providerForm.type] ?? []" :key="field" :label="field">
          <el-input v-model="providerForm.config[field]" :placeholder="t('sms.secretHint')" show-password />
        </el-form-item>
        <el-form-item :label="t('sms.enabled')"><el-switch v-model="providerForm.enabled" /></el-form-item>
        <el-form-item :label="t('sms.default')"><el-switch v-model="providerForm.isDefault" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitProvider">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="signatureDialog" :title="signatureEditing ? t('sms.editSignature') : t('sms.createSignature')" width="560px">
      <el-form label-width="130px">
        <el-form-item :label="t('sms.provider')">
          <el-select v-model="signatureForm.providerCode" :disabled="signatureEditing" style="width: 240px">
            <el-option v-for="item in providers" :key="item.code" :label="item.code" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('sms.signName')"><el-input v-model="signatureForm.signName" /></el-form-item>
        <el-form-item :label="t('sms.country')">
          <el-select v-model="signatureForm.countryCode" style="width: 200px">
            <el-option v-for="item in countries" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('sms.locale')">
          <el-select v-model="signatureForm.locale" style="width: 200px">
            <el-option v-for="item in locales" :key="item" :label="localeLabels[item]" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('sms.status')">
          <el-select v-model="signatureForm.status" style="width: 200px">
            <el-option v-for="item in statuses" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('sms.approvalStatus')"><el-input v-model="signatureForm.approvalStatus" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signatureDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitSignature">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindingDialog" :title="t('sms.bindings')" width="900px">
      <el-form label-width="110px">
        <el-form-item :label="t('sms.template')">
          <el-select v-model="bindingTemplateId" style="width: 320px" @change="loadBindingRows">
            <el-option v-for="item in templates" :key="item.id" :label="item.code" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="bindingRows" size="small">
        <el-table-column :label="t('sms.provider')" min-width="140">
          <template #default="{ row }">
            <el-select v-model="row.providerCode" size="small">
              <el-option v-for="item in providers" :key="item.code" :label="item.code" :value="item.code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('sms.providerTemplateCode')" min-width="150">
          <template #default="{ row }"><el-input v-model="row.providerTemplateCode" size="small" /></template>
        </el-table-column>
        <el-table-column :label="t('sms.signature')" min-width="140">
          <template #default="{ row }">
            <el-select v-model="row.signatureId" size="small" clearable>
              <el-option v-for="item in signatures" :key="item.id" :label="item.signName" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('sms.country')" width="110">
          <template #default="{ row }">
            <el-select v-model="row.countryCode" size="small">
              <el-option v-for="item in countries" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('sms.locale')" width="120">
          <template #default="{ row }">
            <el-select v-model="row.locale" size="small">
              <el-option v-for="item in locales" :key="item" :label="item" :value="item" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="t('sms.paramMapping')" min-width="150">
          <template #default="{ row }"><el-input v-model="row.paramMapping" size="small" /></template>
        </el-table-column>
        <el-table-column width="70">
          <template #default="{ $index }">
            <el-button link type="danger" @click="bindingRows.splice($index, 1)">×</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button style="margin-top: 8px" @click="addBindingRow">+ {{ t('sms.addRow') }}</el-button>
      <template #footer>
        <el-button @click="bindingDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitBindings">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDialog" :title="t('sms.testTitle')" width="560px">
      <el-form label-width="120px">
        <el-form-item :label="t('sms.phone')"><el-input v-model="testForm.phone" placeholder="+8613800138000" /></el-form-item>
        <el-form-item :label="t('sms.signature')"><el-input v-model="testForm.signature" /></el-form-item>
        <el-form-item :label="t('sms.templateCode')"><el-input v-model="testForm.templateCode" placeholder="SMS_123456" /></el-form-item>
        <el-form-item :label="t('sms.variables')"><el-input v-model="testForm.variables" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitTest">{{ t('sms.testTitle') }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="receiptDialog" :title="t('sms.receipts')" width="720px">
      <el-table v-loading="receiptLoading" :data="receipts" size="small">
        <el-table-column prop="status" :label="t('sms.receiptStatus')" width="120" />
        <el-table-column prop="providerStatus" :label="t('sms.providerStatus')" min-width="140" />
        <el-table-column :label="t('sms.time')" min-width="170">
          <template #default="{ row }">
            {{ row.occurredAt ? formatDateTime(row.occurredAt) : row.createdAt ? formatDateTime(row.createdAt) : '—' }}
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!receiptLoading && receipts.length === 0" :description="t('sms.noReceipts')" />
    </el-dialog>

    <el-dialog v-model="optoutDialog" :title="t('sms.createOptout')" width="520px">
      <el-form label-width="110px">
        <el-form-item :label="t('sms.phone')"><el-input v-model="optoutForm.phone" placeholder="+8613800138000" /></el-form-item>
        <el-form-item :label="t('sms.scope')">
          <el-select v-model="optoutForm.scope" style="width: 200px">
            <el-option label="MARKETING" value="MARKETING" />
            <el-option label="ALL" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('sms.reason')"><el-input v-model="optoutForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="optoutDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitOptout">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateVisible" :title="t('mail.editTemplate')" width="680px">
      <el-collapse>
        <el-collapse-item v-for="(row, index) in templateForm.translations" :key="row.locale" :title="row.locale">
          <el-form label-position="top">
            <el-form-item :label="t('mail.body')">
              <el-input v-model="templateForm.translations[index].body" type="textarea" :rows="4" />
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>
      <template #footer>
        <el-button @click="templateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitTemplateEdit">{{ t('common.save') }}</el-button>
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
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
</style>
