<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import { downloadFile, formatDateTime, http, problemOf, type PageResult } from '@vane/shared'

interface OperLog {
  id: string
  username: string | null
  module: string
  action: string
  path: string | null
  result: string
  errorCode: string | null
  durationMs: number | null
  ip: string | null
  traceId: string | null
  httpMethod: string | null
  path: string | null
  params: string | null
  createdAt: string
}

const { t, locale } = useI18n()
const items = ref<OperLog[]>([])
const total = ref(0)
const loading = ref(false)
const page = reactive({ number: 0, size: 20 })
const filters = reactive({ module: '', action: '', result: '', username: '' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<PageResult<OperLog>>('/admin/oper-logs', {
      params: {
        'page[number]': page.number,
        'page[size]': page.size,
        module: filters.module || undefined,
        action: filters.action || undefined,
        result: filters.result || undefined,
        username: filters.username || undefined
      }
    })
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

async function exportCsv(): Promise<void> {
  try {
    await downloadFile('/admin/oper-logs/export', 'oper-logs.csv', {
      module: filters.module || undefined,
      action: filters.action || undefined,
      result: filters.result || undefined,
      username: filters.username || undefined
    })
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
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
          <el-input v-model="filters.module" :placeholder="'user'" style="width: 140px" />
        </el-form-item>
        <el-form-item :label="t('logs.action')">
          <el-input v-model="filters.action" :placeholder="'create'" style="width: 140px" />
        </el-form-item>
        <el-form-item :label="t('logs.result')">
          <el-select v-model="filters.result" clearable style="width: 120px">
            <el-option :label="t('logs.success')" value="SUCCESS" />
            <el-option :label="t('logs.fail')" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('logs.user')">
          <el-input v-model="filters.username" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="exportCsv">{{ t('common.export') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="items" stripe>
          <el-table-column type="expand">
            <template #default="{ row }">
              <pre class="params">{{ prettyParams(row.params) }}</pre>
            </template>
          </el-table-column>
        <el-table-column prop="module" :label="t('logs.module')" width="100" />
        <el-table-column prop="action" :label="t('logs.action')" width="120" />
          <el-table-column :label="t('logs.method')" width="100">
            <template #default="{ row }">
              <span class="method">{{ row.httpMethod }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('logs.path')" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="path">{{ row.path }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('logs.params')" width="110">
            <template #default="{ row }">
              <span class="meta">{{ row.params ? 'JSON' : '—' }}</span>
            </template>
          </el-table-column>
        <el-table-column prop="username" :label="t('logs.user')" width="120" />
        <el-table-column :label="t('logs.result')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorCode" :label="t('logs.errorCode')" width="200" />
        <el-table-column prop="durationMs" :label="t('logs.duration')" width="100" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column :label="t('logs.time')" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.createdAt, locale) }}</template>
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
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}
</style>





