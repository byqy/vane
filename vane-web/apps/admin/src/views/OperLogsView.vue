<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import {
  downloadFile,
  formatDateTime,
  http,
  problemOf,
  type OperLogChange,
  type OperLogDetail,
  type OperLogSummary,
  type PageResult
} from '@vane/shared'
import { useAuthStore } from '../stores/auth'

const { t, locale } = useI18n()
const auth = useAuthStore()

const items = ref<OperLogSummary[]>([])
const total = ref(0)
const loading = ref(false)
const page = reactive({ number: 0, size: 20 })
const filters = reactive({
  module: '',
  action: '',
  result: '',
  username: '',
  source: '',
  targetType: '',
  targetId: '',
  traceId: '',
  ip: '',
  httpStatus: '' as string
})

const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OperLogDetail | null>(null)
const detailChanges = ref<OperLogChange[]>([])

const canDetail = auth.can('audit:oper-log:detail')

function queryParams(): Record<string, string | number | undefined> {
  return {
    'page[number]': page.number,
    'page[size]': page.size,
    module: filters.module || undefined,
    action: filters.action || undefined,
    result: filters.result || undefined,
    username: filters.username || undefined,
    source: filters.source || undefined,
    targetType: filters.targetType || undefined,
    targetId: filters.targetId || undefined,
    traceId: filters.traceId || undefined,
    ip: filters.ip || undefined,
    httpStatus: filters.httpStatus ? Number(filters.httpStatus) : undefined
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<PageResult<OperLogSummary>>('/admin/oper-logs', { params: queryParams() })
    items.value = data.items
    total.value = data.page.total
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.number = 0
  void load()
}

function reset(): void {
  filters.module = ''
  filters.action = ''
  filters.result = ''
  filters.username = ''
  filters.source = ''
  filters.targetType = ''
  filters.targetId = ''
  filters.traceId = ''
  filters.ip = ''
  filters.httpStatus = ''
  search()
}

async function exportCsv(): Promise<void> {
  try {
    await downloadFile('/admin/oper-logs/export', 'oper-logs.csv', {
      module: filters.module || undefined,
      action: filters.action || undefined,
      result: filters.result || undefined,
      username: filters.username || undefined,
      source: filters.source || undefined,
      targetType: filters.targetType || undefined,
      targetId: filters.targetId || undefined,
      traceId: filters.traceId || undefined,
      ip: filters.ip || undefined,
      httpStatus: filters.httpStatus ? Number(filters.httpStatus) : undefined
    })
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

async function openDetail(row: OperLogSummary): Promise<void> {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  detailChanges.value = []
  try {
    const { data } = await http.get<OperLogDetail>(`/admin/oper-logs/${row.id}`)
    detail.value = data
    detailChanges.value = parseChanges(data.changes)
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    detailLoading.value = false
  }
}

function parseChanges(raw: string | OperLogChange[] | null): OperLogChange[] {
  if (!raw) {
    return []
  }
  if (Array.isArray(raw)) {
    return raw
  }
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as OperLogChange[]) : []
  } catch {
    return []
  }
}

function prettyParams(value: string | null): string {
  if (!value) {
    return '—'
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function changeValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'string') {
    return value
  }
  return JSON.stringify(value)
}

function sourceLabel(source: string | null): string {
  if (!source) {
    return '—'
  }
  const key = `logs.source.${source}`
  const label = t(key)
  return label === key ? source : label
}

function targetText(row: OperLogSummary): string {
  if (!row.targetType && !row.targetLabel) {
    return '—'
  }
  const type = row.targetType ?? '—'
  return row.targetLabel ? `${type}:${row.targetLabel}` : type
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('logs.oper.title') }}<HelpTip page="operLogs" :title="t('logs.oper.title')" /></h3>
      <span class="meta">{{ t('common.total') }} {{ total }}</span>
    </div>
    <div class="card">
      <el-form inline>
        <el-form-item :label="t('logs.module')">
          <el-input v-model="filters.module" :placeholder="'user'" style="width: 130px" />
        </el-form-item>
        <el-form-item :label="t('logs.action')">
          <el-input v-model="filters.action" :placeholder="'create'" style="width: 130px" />
        </el-form-item>
        <el-form-item :label="t('logs.result')">
          <el-select v-model="filters.result" clearable style="width: 120px">
            <el-option :label="t('logs.success')" value="SUCCESS" />
            <el-option :label="t('logs.fail')" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('logs.source')">
          <el-select v-model="filters.source" clearable style="width: 120px">
            <el-option :label="t('logs.source.API')" value="API" />
            <el-option :label="t('logs.source.JOB')" value="JOB" />
            <el-option :label="t('logs.source.SYSTEM')" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('logs.user')">
          <el-input v-model="filters.username" style="width: 130px" />
        </el-form-item>
        <el-form-item :label="t('logs.targetType')">
          <el-input v-model="filters.targetType" :placeholder="'user'" style="width: 120px" />
        </el-form-item>
        <el-form-item :label="t('logs.targetId')">
          <el-input v-model="filters.targetId" style="width: 190px" />
        </el-form-item>
        <el-form-item :label="t('logs.traceId')">
          <el-input v-model="filters.traceId" style="width: 170px" />
        </el-form-item>
        <el-form-item :label="t('logs.ip')">
          <el-input v-model="filters.ip" style="width: 130px" />
        </el-form-item>
        <el-form-item :label="t('logs.httpStatus')">
          <el-input v-model="filters.httpStatus" style="width: 100px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="reset">{{ t('common.reset') }}</el-button>
          <el-button @click="exportCsv">{{ t('common.export') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="module" :label="t('logs.module')" width="100" />
        <el-table-column prop="action" :label="t('logs.action')" width="130" />
        <el-table-column :label="t('logs.source')" width="90">
          <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
        </el-table-column>
        <el-table-column :label="t('logs.target')" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ targetText(row) }}</template>
        </el-table-column>
        <el-table-column :label="t('logs.method')" width="90">
          <template #default="{ row }">
            <span class="method">{{ row.httpMethod }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('logs.path')" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="path">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="t('logs.user')" width="110" />
        <el-table-column :label="t('logs.result')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('logs.httpStatus')" width="100">
          <template #default="{ row }">{{ row.httpStatus ?? '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('logs.affectedCount')" width="100">
          <template #default="{ row }">{{ row.affectedCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="durationMs" :label="t('logs.duration')" width="100" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column :label="t('logs.time')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt, locale) }}</template>
        </el-table-column>
        <el-table-column v-if="canDetail" :label="t('common.detail')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('logs.viewDetail') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && items.length === 0" :description="t('common.empty')" />

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="page.size"
        :current-page="page.number + 1"
        @current-change="(p: number) => { page.number = p - 1; void load() }"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="t('logs.detailTitle')" size="620px">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="t('logs.time')">
              {{ formatDateTime(detail.createdAt, locale) }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('logs.user')">{{ detail.username ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.module')">{{ detail.module }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.action')">{{ detail.action }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.source')">{{ sourceLabel(detail.source) }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.targetType')">{{ detail.targetType ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.targetId')">{{ detail.targetId ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.target')">{{ detail.targetLabel ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.method')">{{ detail.httpMethod ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.path')">{{ detail.path ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.result')">{{ detail.result }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.errorCode')">{{ detail.errorCode ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.httpStatus')">{{ detail.httpStatus ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.affectedCount')">{{ detail.affectedCount ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.dept')">{{ detail.deptId ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.duration')">{{ detail.durationMs ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.ip')">{{ detail.ip ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.userAgent')">{{ detail.userAgent ?? '—' }}</el-descriptions-item>
            <el-descriptions-item :label="t('logs.traceId')">{{ detail.traceId ?? '—' }}</el-descriptions-item>
          </el-descriptions>

          <h4 class="section">{{ t('logs.changes') }}</h4>
          <el-alert
            v-if="detail.changesTruncated"
            type="warning"
            :closable="false"
            :title="t('logs.changesTruncated')"
            style="margin-bottom: 8px"
          />
          <el-table v-if="detailChanges.length > 0" :data="detailChanges" size="small" border>
            <el-table-column prop="field" :label="t('logs.field')" min-width="160" />
            <el-table-column :label="t('logs.before')" min-width="160">
              <template #default="{ row }">{{ changeValue(row.before) }}</template>
            </el-table-column>
            <el-table-column :label="t('logs.after')" min-width="160">
              <template #default="{ row }">{{ changeValue(row.after) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else :description="t('logs.changesEmpty')" :image-size="80" />

          <h4 class="section">{{ t('logs.params') }}</h4>
          <pre class="params">{{ prettyParams(detail.params) }}</pre>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}

.section {
  margin: 18px 0 8px;
  font-size: 14px;
}

.drawer-body {
  min-height: 120px;
}

.params {
  margin: 0;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}
</style>
