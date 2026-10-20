<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import { formatDateTime, http, problemOf } from '@vane/shared'

interface HealthView {
  status: string
  checks: Record<string, string>
  checkedAt: string
}

const { t, locale } = useI18n()
const health = ref<HealthView | null>(null)
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<HealthView>('/admin/health')
    health.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('health.unavailable'))
  } finally {
    loading.value = false
  }
}

function tagType(status: string): 'success' | 'danger' | 'warning' {
  if (status === 'UP') {
    return 'success'
  }
  return status === 'DEGRADED' ? 'warning' : 'danger'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('health.title') }}<HelpTip page="health" :title="t('health.title')" /></h3>
      <el-button :loading="loading" @click="load">{{ t('common.refresh') }}</el-button>
    </div>
    <div class="card">
      <el-descriptions v-if="health" :column="1" border>
        <el-descriptions-item :label="t('health.overall')">
          <el-tag :type="tagType(health.status)">{{ health.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('health.deps')">
          <el-tag
            v-for="(value, name) in health.checks"
            :key="name"
            :type="value === 'UP' ? 'success' : 'danger'"
            style="margin-inline-end: 8px"
          >
            {{ name }}: {{ value }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('health.checkedAt')">{{ formatDateTime(health.checkedAt, locale) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else :description="t('health.noData')" />
    </div>
  </div>
</template>



