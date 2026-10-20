<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox, ElTree } from 'element-plus'
import { http, problemOf, type PageResult } from '@vane/shared'

interface Role {
  id: string
  code: string
  name: string
  dataScope: string
  builtin: boolean
  status: string
  sortNo: number
}

interface PermissionNode {
  id: string
  code: string
  parentCode: string | null
  nameKey: string
  type: string
  children: PermissionNode[]
}

const { t } = useI18n()
const roles = ref<Role[]>([])
const total = ref(0)
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<Role | null>(null)
const form = reactive({ code: '', name: '', dataScope: 'DEPT' })
const dataScopes = ['ALL', 'DEPT_AND_CHILD', 'DEPT', 'SELF', 'CUSTOM']

const assignVisible = ref(false)
const assigning = ref<Role | null>(null)
const tree = ref<InstanceType<typeof ElTree> | null>(null)
const treeData = ref<PermissionNode[]>([])
const treeLoading = ref(false)

const treeProps = { children: 'children' }

interface DeptNode {
  id: string
  name: string
  children: DeptNode[]
}

const deptsVisible = ref(false)
const deptsLoading = ref(false)
const deptRole = ref<Role | null>(null)
const deptTree = ref<InstanceType<typeof ElTree> | null>(null)
const deptData = ref<DeptNode[]>([])
const deptProps = { label: 'name', children: 'children' }

async function openDepts(role: Role): Promise<void> {
  deptRole.value = role
  deptsVisible.value = true
  try {
    const [treeResponse, granted] = await Promise.all([
      http.get<DeptNode[]>('/admin/depts/tree'),
      http.get<{ deptIds: string[] }>(`/admin/roles/${role.id}/depts`)
    ])
    deptData.value = treeResponse.data
    await nextTick()
    deptTree.value?.setCheckedKeys(granted.data.deptIds, false)
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

async function saveDepts(): Promise<void> {
  if (!deptRole.value || !deptTree.value) {
    return
  }
  const checked = deptTree.value.getCheckedKeys(false) as string[]
  const half = deptTree.value.getHalfCheckedKeys() as string[]
  const deptIds = [...new Set([...checked, ...half])]
  try {
    await http.put(`/admin/roles/${deptRole.value.id}/depts`, { deptIds })
    ElMessage.success(t('roles.saveDepts'))
    deptsVisible.value = false
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<PageResult<Role>>('/admin/roles', {
      params: { 'page[number]': 0, 'page[size]': 20 }
    })
    roles.value = data.items
    total.value = data.page.total
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.code = ''
  form.name = ''
  form.dataScope = 'DEPT'
  dialogVisible.value = true
}

function openEdit(role: Role): void {
  editing.value = role
  form.code = role.code
  form.name = role.name
  form.dataScope = role.dataScope
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  try {
    if (editing.value) {
      await http.patch(`/admin/roles/${editing.value.id}`, {
        name: form.name,
        dataScope: form.dataScope
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/roles', { code: form.code, name: form.name, dataScope: form.dataScope })
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(role: Role): Promise<void> {
  try {
    await ElMessageBox.confirm(t('common.deleteConfirm', { name: role.name }), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/roles/${role.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

async function openAssign(role: Role): Promise<void> {
  assigning.value = role
  assignVisible.value = true
  treeLoading.value = true
  try {
    const [treeRes, grantedRes] = await Promise.all([
      http.get<PermissionNode[]>('/admin/permissions/tree'),
      http.get<string[]>(`/admin/roles/${role.id}/permissions`)
    ])
    treeData.value = treeRes.data
    await nextTick()
    tree.value?.setCheckedKeys(grantedRes.data, false)
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    treeLoading.value = false
  }
}

async function saveAssign(): Promise<void> {
  if (!assigning.value || !tree.value) {
    return
  }
  const checked = tree.value.getCheckedKeys(false) as string[]
  const half = tree.value.getHalfCheckedKeys() as string[]
  const permissionCodes = [...new Set([...checked, ...half])]
  try {
    await http.put(`/admin/roles/${assigning.value.id}/permissions`, { permissionCodes })
    ElMessage.success(t('roles.assignedCount', { count: permissionCodes.length }))
    assignVisible.value = false
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

const scopeLabel = (value: string): string => t(`roles.scope.${value}`)

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('roles.title') }}<HelpTip page="roles" :title="t('roles.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button type="primary" style="margin-inline-start: 12px" @click="openCreate">{{ t('roles.create') }}</el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="roles" stripe>
        <el-table-column prop="code" :label="t('roles.code')" width="160" />
        <el-table-column prop="name" :label="t('roles.name')" />
        <el-table-column :label="t('roles.dataScope')" width="160">
          <template #default="{ row }">{{ scopeLabel(row.dataScope) }}</template>
        </el-table-column>
        <el-table-column :label="t('roles.builtin')" width="80">
          <template #default="{ row }">{{ row.builtin ? t('common.yes') : t('common.no') }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('common.status')" width="100" />
        <el-table-column :label="t('common.actions')" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" @click="openAssign(row)">{{ t('roles.assign') }}</el-button>
            <el-button link type="primary" @click="openDepts(row)">{{ t('roles.depts') }}</el-button>
            <el-button link type="danger" :disabled="row.builtin" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && roles.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('roles.edit') : t('roles.create')" width="520px">
      <el-form label-width="90px">
        <el-form-item><template #label>{{ t('roles.code') }}<FieldHelp help="field.roles.code.help" /></template>
          <el-input v-model="form.code" :disabled="!!editing" :placeholder="'DEPT_MGR'" />
        </el-form-item>
        <el-form-item :label="t('roles.name')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item><template #label>{{ t('roles.dataScope') }}<FieldHelp help="field.roles.dataScope.help" /></template>
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="item in dataScopes" :key="item" :label="t(`roles.scope.${item}`)" :value="item" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submit">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="deptsVisible" :title="t('roles.deptsTitle', { name: deptRole?.name ?? '' })" width="480px">
      <el-alert
        v-if="deptRole && deptRole.dataScope !== 'CUSTOM'"
        type="warning"
        :title="t('roles.deptsHint')"
        :closable="false"
        style="margin-bottom: 12px"
      />
      <el-tree
        ref="deptTree"
        v-loading="deptsLoading"
        :data="deptData"
        :props="deptProps"
        node-key="id"
        show-checkbox
        default-expand-all
        style="min-height: 200px"
      />
      <template #footer>
        <el-button @click="deptsVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="deptRole?.dataScope !== 'CUSTOM'" @click="saveDepts">
          {{ t('roles.saveDepts') }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="assignVisible" :title="t('roles.assignTitle', { name: assigning?.name ?? '' })" size="420px">
      <el-tree
        ref="tree"
        v-loading="treeLoading"
        :data="treeData"
        :props="treeProps"
        node-key="id"
        show-checkbox
        default-expand-all
        style="min-height: 200px"
      >
        <template #default="{ data }">
          <span>{{ t(data.nameKey) }}</span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="assignVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveAssign">{{ t('roles.saveAssign') }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}
</style>







