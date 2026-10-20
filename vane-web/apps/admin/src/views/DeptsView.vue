<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, problemOf } from '@vane/shared'

interface DeptNode {
  id: string
  parentId: string | null
  code: string
  name: string
  sortNo: number
  status: string
  children: DeptNode[]
}

const { t } = useI18n()
const tree = ref<DeptNode[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<DeptNode | null>(null)
const form = reactive({ parentId: '', code: '', name: '', sortNo: 0, status: 'ACTIVE' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<DeptNode[]>('/admin/depts/tree')
    tree.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(parent: DeptNode | null): void {
  editing.value = null
  form.parentId = parent?.id ?? ''
  form.code = ''
  form.name = ''
  form.sortNo = 0
  form.status = 'ACTIVE'
  dialogVisible.value = true
}

function openEdit(node: DeptNode): void {
  editing.value = node
  form.parentId = node.parentId ?? ''
  form.code = node.code
  form.name = node.name
  form.sortNo = node.sortNo
  form.status = node.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  try {
    if (editing.value) {
      await http.patch(`/admin/depts/${editing.value.id}`, {
        parentId: form.parentId || null,
        name: form.name,
        status: form.status,
        sortNo: form.sortNo
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/depts', {
        parentId: form.parentId || null,
        code: form.code,
        name: form.name,
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

async function remove(node: DeptNode): Promise<void> {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm', { name: node.name }), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/depts/${node.id}`)
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
      <h3 style="margin: 0">{{ t('depts.title') }}<HelpTip page="depts" :title="t('depts.title')" /></h3>
      <el-button type="primary" @click="openCreate(null)">{{ t('depts.createRoot') }}</el-button>
    </div>
    <div class="card">
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="name" :label="t('depts.name')" min-width="200" />
        <el-table-column prop="code" :label="t('depts.code')" width="160" />
        <el-table-column prop="sortNo" :label="t('depts.sort')" width="90" />
        <el-table-column prop="status" :label="t('common.status')" width="110" />
        <el-table-column :label="t('common.actions')" width="260">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCreate(row)">{{ t('depts.addChild') }}</el-button>
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tree.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('depts.edit') : t('depts.create')" width="480px">
      <el-form label-width="80px">
        <el-form-item :label="t('depts.code')">
          <el-input v-model="form.code" :disabled="!!editing" placeholder="如 BRANCH" />
        </el-form-item>
        <el-form-item :label="t('depts.name')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('depts.sort')">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
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



