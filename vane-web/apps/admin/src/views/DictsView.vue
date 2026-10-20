<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, problemOf } from '@vane/shared'

interface DictType {
  code: string
  nameKey: string
  status: string
  remark: string | null
  itemCount: number
}

interface DictItem {
  value: string
  label: string
  color: string | null
  sortNo: number
  status: string
  builtin: boolean
  labels: Record<string, string>
}

const { t, locale } = useI18n()
const types = ref<DictType[]>([])
const items = ref<DictItem[]>([])
const selected = ref<string>('')
const loading = ref(false)
const traceId = ref<string | null>(null)

const typeDialog = ref(false)
const editingType = ref<DictType | null>(null)
const typeForm = reactive({ typeCode: '', nameKey: '', remark: '', status: 'ACTIVE' })

const itemDialog = ref(false)
const editingItem = ref<DictItem | null>(null)
const itemForm = reactive({ value: '', color: '', sortNo: 0, status: 'ACTIVE', zhLabel: '', enLabel: '' })

async function loadTypes(): Promise<void> {
  loading.value = true
  traceId.value = null
  try {
    const { data } = await http.get<DictType[]>('/admin/dicts')
    types.value = data
    if (data.length > 0 && !data.some((type) => type.code === selected.value)) {
      selected.value = data[0].code
    }
    await loadItems()
  } catch (e) {
    const problem = problemOf(e)
    traceId.value = problem?.traceId ?? null
    ElMessage.error(problem?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function loadItems(): Promise<void> {
  if (!selected.value) {
    items.value = []
    return
  }
  try {
    const { data } = await http.get<DictItem[]>(`/admin/dicts/${selected.value}/items`, {
      headers: { 'Accept-Language': locale.value }
    })
    items.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

function selectType(type: DictType): void {
  selected.value = type.code
  void loadItems()
}


function openCreateType(): void {
  editingType.value = null
  Object.assign(typeForm, { typeCode: '', nameKey: '', remark: '', status: 'ACTIVE' })
  typeDialog.value = true
}

function openEditType(type: DictType): void {
  editingType.value = type
  Object.assign(typeForm, {
    typeCode: type.code,
    nameKey: type.nameKey,
    remark: type.remark ?? '',
    status: type.status
  })
  typeDialog.value = true
}

async function submitType(): Promise<void> {
  try {
    if (editingType.value) {
      await http.patch(`/admin/dicts/${editingType.value.code}`, {
        nameKey: typeForm.nameKey,
        status: typeForm.status,
        remark: typeForm.remark
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/dicts', {
        typeCode: typeForm.typeCode,
        nameKey: typeForm.nameKey,
        remark: typeForm.remark
      })
      selected.value = typeForm.typeCode
      ElMessage.success(t('common.createSuccess'))
    }
    typeDialog.value = false
    await loadTypes()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function removeType(type: DictType): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('common.deleteConfirm', { name: type.code }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/dicts/${type.code}`)
    ElMessage.success(t('common.deleteSuccess'))
    if (selected.value === type.code) {
      selected.value = ''
    }
    await loadTypes()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

function openCreateItem(): void {
  editingItem.value = null
  Object.assign(itemForm, { value: '', color: '', sortNo: 0, status: 'ACTIVE', zhLabel: '', enLabel: '' })
  itemDialog.value = true
}

function openEditItem(item: DictItem): void {
  editingItem.value = item
  Object.assign(itemForm, {
    value: item.value,
    color: item.color ?? '',
    sortNo: item.sortNo,
    status: item.status,
    zhLabel: item.labels['zh-CN'] ?? (locale.value === 'zh-CN' ? item.label : ''),
    enLabel: item.labels.en ?? (locale.value === 'en' ? item.label : '')
  })
  itemDialog.value = true
}

function translations() {
  return [
    ...(itemForm.zhLabel ? [{ locale: 'zh-CN', label: itemForm.zhLabel }] : []),
    ...(itemForm.enLabel ? [{ locale: 'en', label: itemForm.enLabel }] : [])
  ]
}

async function submitItem(): Promise<void> {
  if (!selected.value) {
    return
  }
  try {
    if (editingItem.value) {
      await http.patch(`/admin/dicts/${selected.value}/items/${editingItem.value.value}`, {
        color: itemForm.color,
        sortNo: itemForm.sortNo,
        status: itemForm.status,
        translations: translations()
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post(`/admin/dicts/${selected.value}/items`, {
        value: itemForm.value,
        color: itemForm.color,
        sortNo: itemForm.sortNo,
        translations: translations()
      })
      ElMessage.success(t('common.createSuccess'))
    }
    itemDialog.value = false
    await loadTypes()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function removeItem(item: DictItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('common.deleteConfirm', { name: item.value }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/dicts/${selected.value}/items/${item.value}`)
    ElMessage.success(t('common.deleteSuccess'))
    await loadTypes()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

// 字典项标签由后端按 Accept-Language 返回，切语言后必须重拉
watch(locale, () => {
  void loadItems()
})

onMounted(loadTypes)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('dicts.title') }}<HelpTip page="dicts" :title="t('dicts.title')" /></h3>
      <div>

        <el-button type="primary" style="margin-inline-start: 8px" @click="openCreateType">
          {{ t('dicts.createType') }}
        </el-button>
      </div>
    </div>

    <div class="card" style="display: flex; gap: 16px">
      <div style="width: 340px">
        <el-table v-loading="loading" :data="types" row-key="code" highlight-current-row @row-click="selectType">
          <el-table-column prop="code" :label="t('dicts.typeCode')" min-width="150" />
          <el-table-column prop="itemCount" :label="t('dicts.itemCount')" width="90" />
          <el-table-column :label="t('common.actions')" width="150">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEditType(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="danger" @click.stop="removeType(row)">{{ t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!loading && types.length === 0" :description="t('common.empty')" />
      </div>

      <div style="flex: 1">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
          <span class="meta">{{ selected || t('dicts.selectType') }}</span>
          <el-button type="primary" size="small" :disabled="!selected" @click="openCreateItem">
            {{ t('dicts.createItem') }}
          </el-button>
        </div>

        <el-table :data="items" stripe>
          <el-table-column prop="value" :label="t('dicts.itemValue')" width="170" />
          <el-table-column prop="label" :label="t('dicts.label')" min-width="160" />
          <el-table-column prop="color" :label="t('dicts.color')" width="120" />
          <el-table-column prop="sortNo" :label="t('dicts.sort')" width="90" />
          <el-table-column prop="status" :label="t('common.status')" width="110" />
          <el-table-column :label="t('dicts.builtin')" width="90">
            <template #default="{ row }">{{ row.builtin ? t('common.yes') : t('common.no') }}</template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="150">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditItem(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="danger" :disabled="row.builtin" @click="removeItem(row)">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="selected && items.length === 0" :description="t('common.empty')" />
      </div>
    </div>

    <el-alert
      v-if="traceId"
      type="error"
      :closable="false"
      style="margin-top: 12px"
      :title="t('common.requestFailed', { traceId })"
    />

    <el-dialog
      v-model="typeDialog"
      :title="editingType ? t('dicts.editType') : t('dicts.createType')"
      width="480px"
    >
      <el-form label-width="110px">
        <el-form-item><template #label>{{ t('dicts.typeCode') }}<FieldHelp help="field.dicts.typeCode.help" /></template>
          <el-input v-model="typeForm.typeCode" :disabled="!!editingType" placeholder="lower_case" />
        </el-form-item>
        <el-form-item><template #label>{{ t('dicts.nameKey') }}<FieldHelp help="field.dicts.nameKey.help" /></template>
          <el-input v-model="typeForm.nameKey" placeholder="dict.someName" />
        </el-form-item>
        <el-form-item :label="t('dicts.remark')">
          <el-input v-model="typeForm.remark" />
        </el-form-item>
        <el-form-item v-if="editingType" :label="t('common.status')">
          <el-select v-model="typeForm.status" style="width: 160px">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitType">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="itemDialog"
      :title="editingItem ? t('dicts.editItem') : t('dicts.createItem')"
      width="520px"
    >
      <el-form label-width="110px">
        <el-form-item><template #label>{{ t('dicts.itemValue') }}<FieldHelp help="field.dicts.itemValue.help" /></template>
          <el-input v-model="itemForm.value" :disabled="!!editingItem" />
        </el-form-item>
        <el-form-item><template #label>{{ t('dicts.labelZh') }}<FieldHelp help="field.dicts.labelZh.help" /></template>
          <el-input v-model="itemForm.zhLabel" />
        </el-form-item>
        <el-form-item :label="t('dicts.labelEn')">
          <el-input v-model="itemForm.enLabel" />
        </el-form-item>
        <el-form-item :label="t('dicts.color')">
          <el-input v-model="itemForm.color" placeholder="success / #3b5bdb" />
        </el-form-item>
        <el-form-item :label="t('dicts.sort')">
          <el-input-number v-model="itemForm.sortNo" :min="0" />
        </el-form-item>
        <el-form-item v-if="editingItem" :label="t('common.status')">
          <el-select v-model="itemForm.status" style="width: 160px">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitItem">{{ t('common.save') }}</el-button>
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




