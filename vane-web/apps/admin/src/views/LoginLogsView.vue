<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import { downloadFile, formatDateTime, http, problemOf, type PageResult } from '@vane/shared'

interface LoginLog {
  id: string
  username: string
  result: string
  detail: string | null
  ip: string | null
  traceId: string | null
  createdAt: string
}

const { t, locale } = useI18n()
const items = ref<LoginLog[]>([])
const total = ref(0)
const loading = ref(false)
const page = reactive({ number: 0, size: 20 })
const filters = reactive({ username: '', result: '' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<PageResult<LoginLog>>('/admin/login-logs', {
      params: {
        'page[number]': page.number,
        'page[size]': page.size,
        username: filters.username || undefined,
        result: filters.result || undefined
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
    await downloadFile('/admin/login-logs/export', 'login-logs.csv', {
      username: filters.username || undefined,
      result: filters.result || undefined
    })
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('logs.login.title') }}<HelpTip page="loginLogs" :title="t('logs.login.title')" /></h3>
      <span class="meta">{{ t('common.total') }} {{ total }}</span>
    </div>
    <div class="card">
      <el-form inline>
        <el-form-item :label="t('logs.user')">
          <el-input v-model="filters.username" style="width: 160px" />
        </el-form-item>
        <el-form-item :label="t('logs.result')">
          <el-select v-model="filters.result" clearable style="width: 160px">
            <el-option :label="t('logs.result.SUCCESS')" value="SUCCESS" />
            <el-option :label="t('logs.result.FAIL_PASSWORD')" value="FAIL_PASSWORD" />
            <el-option :label="t('logs.result.FAIL_LOCKED')" value="FAIL_LOCKED" />
            <el-option :label="t('logs.result.FAIL_MFA')" value="FAIL_MFA" />
            <el-option :label="t('logs.result.FAIL_DISABLED')" value="FAIL_DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="exportCsv">{{ t('common.export') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="username" :label="t('logs.user')" width="160" />
        <el-table-column :label="t('logs.result')" width="160">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" :label="t('logs.detail')" min-width="160" />
        <el-table-column prop="ip" label="IP" width="150" />
        <el-table-column prop="traceId" label="traceId" min-width="200" />
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





