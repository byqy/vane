<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, problemOf } from '@vane/shared'

interface NavNode {
  id: string
  parentId: string | null
  position: string
  labelKey: string
  target: string | null
  openInNewTab: boolean
  sortNo: number
  status: string
  children: NavNode[]
}

const { t } = useI18n()
const tree = ref<NavNode[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<NavNode | null>(null)
const form = reactive({ parentId: '', position: 'HEADER', labelKey: '', target: '', sortNo: 0, status: 'ACTIVE' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<NavNode[]>('/admin/navs')
    tree.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(parent: NavNode | null): void {
  editing.value = null
  Object.assign(form, {
    parentId: parent?.id ?? '',
    position: parent?.position ?? 'HEADER',
    labelKey: '',
    target: '',
    sortNo: 0,
    status: 'ACTIVE'
  })
  dialogVisible.value = true
}

function openEdit(node: NavNode): void {
  editing.value = node
  Object.assign(form, {
    parentId: node.parentId ?? '',
    position: node.position,
    labelKey: node.labelKey,
    target: node.target ?? '',
    sortNo: node.sortNo,
    status: node.status
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  try {
    if (editing.value) {
      await http.patch(`/admin/navs/${editing.value.id}`, {
        parentId: form.parentId || null,
        labelKey: form.labelKey,
        target: form.target,
        openInNewTab: false,
        sortNo: form.sortNo,
        status: form.status
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/navs', {
        parentId: form.parentId || null,
        position: form.position,
        labelKey: form.labelKey,
        target: form.target,
        openInNewTab: false,
        sortNo: form.sortNo
      })
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(node: NavNode): Promise<void> {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm', { name: node.labelKey }), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/navs/${node.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('navs.title') }}<HelpTip page="navs" :title="t('navs.title')" /></h3>
      <el-button type="primary" @click="openCreate(null)">{{ t('navs.createRoot') }}</el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="tree" row-key="id" default-expand-all :tree-props="{ children: 'children' }">
        <el-table-column prop="labelKey" :label="t('navs.labelKey')" min-width="200" />
        <el-table-column prop="position" :label="t('navs.position')" width="110" />
        <el-table-column prop="target" :label="t('navs.target')" min-width="180" />
        <el-table-column prop="sortNo" :label="t('common.sort')" width="90" />
        <el-table-column prop="status" :label="t('common.status')" width="110" />
        <el-table-column :label="t('common.actions')" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCreate(row)">{{ t('navs.addChild') }}</el-button>
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tree.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('navs.edit') : t('navs.create')" width="520px">
      <el-form label-width="100px">
        <el-form-item><template #label>{{ t('navs.labelKey') }}<FieldHelp help="field.navs.labelKey.help" /></template><el-input v-model="form.labelKey" :placeholder="t('navs.labelKeyHint')" /></el-form-item>
        <el-form-item v-if="!editing" :label="t('navs.position')">
          <el-select v-model="form.position" style="width: 160px">
            <el-option :label="t('navs.position.HEADER')" value="HEADER" />
            <el-option :label="t('navs.position.FOOTER')" value="FOOTER" />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>{{ t('navs.target') }}<FieldHelp help="field.navs.target.help" /></template><el-input v-model="form.target" :placeholder="t('navs.targetHint')" /></el-form-item>
        <el-form-item><template #label>{{ t('common.sort') }}<FieldHelp help="field.navs.sortNo.help" /></template><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
        <el-form-item v-if="editing" :label="t('common.status')">
          <el-select v-model="form.status" style="width: 160px">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>



