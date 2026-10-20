<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, problemOf, type PageResult, type UserSummary } from '@vane/shared'

interface DeptNode {
  id: string
  name: string
  children: DeptNode[]
}

interface RoleItem {
  id: string
  code: string
  name: string
}

interface FlatDept {
  id: string
  label: string
}

const { t, locale } = useI18n()

const items = ref<UserSummary[]>([])
const total = ref(0)
const loading = ref(false)
const traceId = ref<string | null>(null)
const page = reactive({ number: 0, size: 20 })
const filters = reactive({ username: '', status: '' })

const depts = ref<FlatDept[]>([])
const roles = ref<RoleItem[]>([])

const dialogVisible = ref(false)
const editing = ref<UserSummary | null>(null)
const submitting = ref(false)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  deptId: '',
  status: 'ACTIVE',
  roleIds: [] as string[]
})

const passwordVisible = ref(false)
const resetResult = reactive({ username: '', password: '' })

const statusOptions = computed(() => [
  { value: 'ACTIVE', label: t('common.enabled') },
  { value: 'DISABLED', label: t('common.disabled') }
])

async function load(): Promise<void> {
  loading.value = true
  traceId.value = null
  try {
    const { data } = await http.get<PageResult<UserSummary>>('/admin/users', {
      params: {
        'page[number]': page.number,
        'page[size]': page.size,
        username: filters.username || undefined,
        status: filters.status || undefined
      }
    })
    items.value = data.items
    total.value = data.page.total
  } catch (e) {
    const problem = problemOf(e)
    traceId.value = problem?.traceId ?? null
    ElMessage.error(problem?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function flatten(nodes: DeptNode[], depth = 0): FlatDept[] {
  return nodes.flatMap((node) => [
    { id: node.id, label: `${'　'.repeat(depth)}${node.name}` },
    ...flatten(node.children ?? [], depth + 1)
  ])
}

async function loadReference(): Promise<void> {
  try {
    const [deptResponse, roleResponse] = await Promise.all([
      http.get<DeptNode[]>('/admin/depts/tree'),
      http.get<PageResult<RoleItem>>('/admin/roles', { params: { 'page[size]': 100 } })
    ])
    depts.value = flatten(deptResponse.data)
    roles.value = roleResponse.data.items
  } catch {
    // 参考数据失败不阻塞主列表
  }
}

async function loadRoleIds(userId: string): Promise<string[]> {
  try {
    const { data } = await http.get<{ roleIds: string[] }>(`/admin/users/${userId}/roles`)
    return data.roleIds
  } catch {
    return []
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    username: '',
    password: '',
    nickname: '',
    deptId: depts.value[0]?.id ?? '',
    status: 'ACTIVE',
    roleIds: []
  })
  dialogVisible.value = true
}

async function openEdit(row: UserSummary): Promise<void> {
  editing.value = row
  Object.assign(form, {
    username: row.username,
    password: '',
    nickname: row.nickname ?? '',
    deptId: row.deptId,
    status: row.status,
    roleIds: await loadRoleIds(row.id)
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  submitting.value = true
  try {
    if (editing.value) {
      await http.patch(
        `/admin/users/${editing.value.id}`,
        { nickname: form.nickname, deptId: form.deptId, status: form.status },
        { headers: { 'If-Match': `"${editing.value.version}"` } }
      )
      await http.put(`/admin/users/${editing.value.id}/roles`, { roleIds: form.roleIds })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      const { data } = await http.post<UserSummary>('/admin/users', {
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        deptId: form.deptId
      })
      if (form.roleIds.length > 0) {
        await http.put(`/admin/users/${data.id}/roles`, { roleIds: form.roleIds })
      }
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    submitting.value = false
  }
}

async function remove(row: UserSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('users.deleteConfirm', { username: row.username }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/users/${row.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

async function resetPassword(row: UserSummary): Promise<void> {
  try {
    const { data } = await http.post<{ password: string }>(`/admin/users/${row.id}/reset-password`)
    resetResult.username = row.username
    resetResult.password = data.password
    passwordVisible.value = true
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function copyPassword(): Promise<void> {
  try {
    await navigator.clipboard.writeText(resetResult.password)
    ElMessage.success(t('common.copied'))
  } catch {
    ElMessage.warning(t('common.copy'))
  }
}

function formatTime(value: string | null): string {
  return formatDateTime(value, locale.value)
}

function search(): void {
  page.number = 0
  void load()
}

onMounted(async () => {
  await loadReference()
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('users.title') }}<HelpTip page="users" :title="t('users.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button type="primary" style="margin-inline-start: 12px" @click="openCreate">
          {{ t('users.create') }}
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-form inline>
        <el-form-item :label="t('users.filterUsername')">
          <el-input v-model="filters.username" style="width: 160px" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="t('users.filterStatus')">
          <el-select v-model="filters.status" clearable style="width: 140px">
            <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="username" :label="t('users.username')" width="150" />
        <el-table-column prop="nickname" :label="t('users.nickname')" width="140" />
        <el-table-column prop="deptName" :label="t('users.dept')" width="150" />
        <el-table-column :label="t('users.roles')" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" size="small" style="margin-inline-end: 4px">
              {{ role }}
            </el-tag>
            <span v-if="row.roles.length === 0">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('users.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('users.lastLogin')" min-width="170">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="primary" @click="resetPassword(row)">{{ t('users.resetPassword') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && items.length === 0" :description="t('common.empty')" />

      <el-alert
        v-if="traceId"
        type="error"
        :closable="false"
        style="margin-top: 12px"
        :title="t('common.requestFailed', { traceId })"
      />

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="page.size"
        :current-page="page.number + 1"
        @current-change="(p: number) => { page.number = p - 1; void load() }"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('users.edit') : t('users.create')" width="560px">
      <el-form label-width="100px">
        <el-form-item><template #label>{{ t('users.username') }}<FieldHelp help="field.users.username.help" /></template>
          <el-input v-model="form.username" :disabled="!!editing" />
        </el-form-item>
        <el-form-item v-if="!editing"><template #label>{{ t('users.password') }}<FieldHelp help="field.users.password.help" /></template>
          <el-input v-model="form.password" type="password" show-password :placeholder="t('users.passwordHint')" />
        </el-form-item>
        <el-form-item :label="t('users.nickname')">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item><template #label>{{ t('users.dept') }}<FieldHelp help="field.users.dept.help" /></template>
          <el-select v-model="form.deptId" filterable style="width: 100%">
            <el-option v-for="dept in depts" :key="dept.id" :label="dept.label" :value="dept.id" />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>{{ t('users.roles') }}<FieldHelp help="field.users.roles.help" /></template>
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.name}（${role.code}）`" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editing"><template #label>{{ t('users.status') }}<FieldHelp help="field.users.status.help" /></template>
          <el-select v-model="form.status" style="width: 180px">
            <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ editing ? t('common.save') : t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" :title="t('users.resetPasswordTitle', { username: resetResult.username })" width="460px">
      <el-alert type="warning" :closable="false" :title="t('users.resetPasswordHint')" />
      <div class="password-box">{{ resetResult.password }}</div>
      <template #footer>
        <el-button @click="copyPassword">{{ t('common.copy') }}</el-button>
        <el-button type="primary" @click="passwordVisible = false">{{ t('common.cancel') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}

.password-box {
  margin-top: 12px;
  padding: 12px;
  background: #f3f4f6;
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 16px;
  word-break: break-all;
}
</style>





