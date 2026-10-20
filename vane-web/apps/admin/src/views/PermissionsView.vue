<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import { http, problemOf } from '@vane/shared'

interface PermissionNode {
  id: string
  code: string
  parentCode: string | null
  nameKey: string
  type: string
  path: string | null
  children: PermissionNode[]
}

const { t, te } = useI18n()
const tree = ref<PermissionNode[]>([])
const loading = ref(false)

/** 译文缺失时（新增权限点还没补文案）回退为原始文案键，并做视觉提示。 */
function isUntranslated(nameKey: string): boolean {
  return !te(nameKey)
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<PermissionNode[]>('/admin/permissions/tree')
    tree.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('perms.title') }}<HelpTip page="perms" :title="t('perms.title')" /></h3>
      <span class="meta">{{ t('perms.readonly') }}</span>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column :label="t('perms.title')" min-width="220">
          <template #default="{ row }">
            <span :class="{ 'key-fallback': isUntranslated(row.nameKey) }">{{ t(row.nameKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="code" :label="t('perms.code')" min-width="220" />
        <el-table-column :label="t('perms.type')" width="110">
          <template #default="{ row }">{{ t(`perms.type.${row.type}`) }}</template>
        </el-table-column>
        <el-table-column prop="path" :label="t('perms.path')" min-width="180" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.key-fallback {
  color: #b45309;
  font-family: monospace;
}

.meta {
  color: #6b7280;
  font-size: 13px;
}
</style>
