<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, problemOf } from '@vane/shared'

interface JobView {
  id: string
  jobCode: string
  nameKey: string
  handler: string
  cron: string
  status: string
  concurrency: string
  lastRunAt: string | null
  nextRunAt: string | null
}

const { t, locale } = useI18n()
const items = ref<JobView[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<JobView | null>(null)
const form = reactive({ jobCode: '', nameKey: '', handler: 'cleanup-idempotency', cron: '0 0 3 * * *', concurrency: 'FORBID' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<JobView[]>('/admin/jobs')
    items.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    jobCode: '',
    nameKey: '',
    handler: 'cleanup-idempotency',
    cron: '0 0 3 * * *',
    concurrency: 'FORBID',
    status: 'ACTIVE',
    params: ''
  })
  dialogVisible.value = true
}

function openEdit(job: JobView): void {
  editing.value = job
  Object.assign(form, {
    jobCode: job.jobCode,
    nameKey: job.nameKey,
    handler: job.handler,
    cron: job.cron,
    concurrency: job.concurrency,
    status: job.status,
    params: job.params ?? ''
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  try {
    if (editing.value) {
      await http.patch(`/admin/jobs/${editing.value.id}`, {
        nameKey: form.nameKey,
        cron: form.cron,
        params: form.params || null,
        status: form.status,
        concurrency: form.concurrency
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/jobs', { ...form })
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('jobs.createFailedCron'))
  }
}

async function toggle(job: JobView): Promise<void> {
  try {
    await http.patch(`/admin/jobs/${job.id}`, {
      nameKey: job.nameKey,
      cron: job.cron,
      params: job.params ?? null,
      status: job.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE',
      concurrency: job.concurrency
    })
    ElMessage.success(t('common.saveSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function run(job: JobView): Promise<void> {
  try {
    await http.post(`/admin/jobs/${job.id}/run`)
    ElMessage.success(t('jobs.triggered'))
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('jobs.runFailed'))
  }
}

async function remove(job: JobView): Promise<void> {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm', { name: job.jobCode }), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/jobs/${job.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

interface JobLog {
  id: string
  startedAt: string | null
  finishedAt: string | null
  success: boolean | null
  message: string | null
  traceId: string | null
}

const logsVisible = ref(false)
const logsJob = ref<JobView | null>(null)
const logs = ref<JobLog[]>([])
const logsLoading = ref(false)
const logsPage = reactive({ number: 0, size: 20, total: 0 })

async function openLogs(job: JobView): Promise<void> {
  logsJob.value = job
  logsPage.number = 0
  logsVisible.value = true
  await loadLogs()
}

async function loadLogs(): Promise<void> {
  if (!logsJob.value) {
    return
  }
  logsLoading.value = true
  try {
    const { data } = await http.get<{ items: JobLog[]; page: { total: number } }>(
      `/admin/jobs/${logsJob.value.id}/logs`,
      { params: { 'page[number]': logsPage.number, 'page[size]': logsPage.size } }
    )
    logs.value = data.items
    logsPage.total = data.page.total
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    logsLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('jobs.title') }}<HelpTip page="jobs" :title="t('jobs.title')" /></h3>
      <el-button type="primary" @click="openCreate">{{ t('jobs.create') }}</el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="jobCode" :label="t('jobs.code')" width="180" />
        <el-table-column prop="handler" :label="t('jobs.handler')" width="200" />
        <el-table-column prop="cron" label="cron" width="160" />
        <el-table-column prop="status" :label="t('common.status')" width="100" />
        <el-table-column :label="t('jobs.lastRun')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.lastRunAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('jobs.nextRun')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.nextRunAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" @click="toggle(row)">
              {{ row.status === 'ACTIVE' ? t('jobs.pause') : t('jobs.enable') }}
            </el-button>
            <el-button link type="primary" @click="openLogs(row)">{{ t('jobs.logs') }}</el-button>
            <el-button link type="primary" @click="run(row)">{{ t('jobs.runNow') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && items.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('jobs.edit') : t('jobs.create')" width="560px">
      <el-form label-width="100px">
        <el-form-item :label="t('jobs.code')">
          <el-input v-model="form.jobCode" :disabled="!!editing" :placeholder="t('jobs.codeHint')" />
        </el-form-item>
        <el-form-item :label="t('jobs.nameKey')"><el-input v-model="form.nameKey" :placeholder="t('jobs.nameHint')" /></el-form-item>
        <el-form-item v-if="!editing" :label="t('jobs.handler')">
          <el-select v-model="form.handler" style="width: 100%">
            <el-option label="cleanup-idempotency" value="cleanup-idempotency" />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>cron<FieldHelp help="field.jobs.cron.help" /></template>
          <el-input v-model="form.cron" :placeholder="t('jobs.cronHint')" />
        </el-form-item>
        <el-form-item><template #label>{{ t('jobs.params') }}<FieldHelp help="field.jobs.params.help" /></template>
          <el-input v-model="form.params" type="textarea" :rows="2" placeholder='{"key":"value"}' />
        </el-form-item>
        <el-form-item><template #label>{{ t('jobs.concurrency') }}<FieldHelp help="field.jobs.concurrency.help" /></template>
          <el-radio-group v-model="form.concurrency">
            <el-radio value="FORBID">{{ t('jobs.concurrency.FORBID') }}</el-radio>
            <el-radio value="ALLOW">{{ t('jobs.concurrency.ALLOW') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editing" :label="t('common.status')">
          <el-select v-model="form.status" style="width: 160px">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('jobs.pause')" value="PAUSED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submit">
          {{ editing ? t('common.save') : t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="logsVisible" :title="t('jobs.logsTitle', { code: logsJob?.jobCode ?? '' })" size="720px">
      <el-table v-loading="logsLoading" :data="logs" stripe>
        <el-table-column :label="t('jobs.startedAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.startedAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('jobs.finishedAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.finishedAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('jobs.result')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'">
              {{ row.success ? t('common.yes') : t('common.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" :label="t('jobs.message')" min-width="220" />
        <el-table-column prop="traceId" :label="t('jobs.traceId')" width="200" />
      </el-table>

      <el-empty v-if="!logsLoading && logs.length === 0" :description="t('jobs.noLogs')" />

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="logsPage.total"
        :page-size="logsPage.size"
        :current-page="logsPage.number + 1"
        @current-change="(p: number) => { logsPage.number = p - 1; void loadLogs() }"
      />
    </el-drawer>
  </div>
</template>







