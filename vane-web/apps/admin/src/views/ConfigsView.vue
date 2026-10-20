<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage } from 'element-plus'
import { http, problemOf } from '@vane/shared'

interface Config {
  key: string
  valueJson: string
  valueType: string
  description: string | null
  editable: boolean
}

const { t } = useI18n()
const items = ref<Config[]>([])
const filters = reactive({ keyword: '', onlyEditable: false })
const loading = ref(false)
const dialogVisible = ref(false)
const current = ref<Config | null>(null)
const form = reactive({ valueJson: '' })

const filtered = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return items.value.filter((item) => {
    if (filters.onlyEditable && !item.editable) {
      return false
    }
    if (!keyword) {
      return true
    }
    return (
      item.key.toLowerCase().includes(keyword) ||
      (item.description ?? '').toLowerCase().includes(keyword)
    )
  })
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<Config[]>('/admin/configs')
    items.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openEdit(item: Config): void {
  if (!item.editable) {
    ElMessage.warning(t('configs.readonlyWarning'))
    return
  }
  current.value = item
  form.valueJson = item.valueJson
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!current.value) {
    return
  }
  try {
    JSON.parse(form.valueJson)
  } catch {
    ElMessage.error(t('configs.invalidJson'))
    return
  }
  try {
    await http.patch(`/admin/configs/${current.value.key}`, { valueJson: form.valueJson })
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('configs.title') }}<HelpTip page="configs" :title="t('configs.title')" /></h3>
      <span class="meta">{{ t('common.total') }} {{ items.length }}</span>
    </div>
    <div class="card">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :placeholder="t('configs.filterHint')"
          clearable
          style="width: 280px"
        />
        <el-checkbox v-model="filters.onlyEditable">{{ t('configs.onlyEditable') }}</el-checkbox>
        <span class="meta">{{ filtered.length }} / {{ items.length }}</span>
      </div>

      <el-table v-loading="loading" :data="filtered" stripe>
        <el-table-column prop="key" :label="t('configs.key')" min-width="240" />
        <el-table-column prop="valueJson" :label="t('configs.value')" min-width="180" />
        <el-table-column prop="valueType" :label="t('configs.type')" width="110" />
        <el-table-column prop="description" :label="t('configs.description')" min-width="220" />
        <el-table-column :label="t('configs.editable')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.editable ? 'success' : 'info'">{{ row.editable ? t('common.yes') : t('configs.readonly') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!row.editable" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && filtered.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="t('configs.editTitle', { key: current?.key ?? '' })" width="520px">
      <el-form label-width="100px">
        <el-form-item><template #label>{{ t('configs.type') }}<FieldHelp help="field.configs.type.help" /></template>
          <el-tag>{{ current?.valueType }}</el-tag>
        </el-form-item>
        <el-form-item><template #label>{{ t('configs.value') }}<FieldHelp help="field.configs.value.help" /></template>
          <el-input v-model="form.valueJson" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submit">{{ t('common.save') }}</el-button>
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




