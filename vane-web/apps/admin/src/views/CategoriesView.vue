<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http, problemOf, type CategoryNodeView } from '@vane/shared'
import TranslationsEditor from '../components/TranslationsEditor.vue'
import type { TranslationRow } from '../components/translation-types'
import { useAuthStore } from '../stores/auth'

const { t } = useI18n()
const auth = useAuthStore()

const tree = ref<CategoryNodeView[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

const dialogVisible = ref(false)
const editing = ref<CategoryNodeView | null>(null)
const saving = ref(false)
const form = reactive({
  code: '',
  sortNo: 0,
  status: 'ACTIVE',
  translations: [] as TranslationRow[]
})

const translationFields = [
  { key: 'name', labelKey: 'content.category.name' },
  { key: 'description', labelKey: 'content.category.description', textarea: true }
]

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,63}$/

function nameOf(node: CategoryNodeView): string {
  return (
    node.translations.find((item) => item.locale === 'zh-CN')?.name ??
    node.translations[0]?.name ??
    node.code
  )
}

function statusOf(status: string | undefined): string {
  return status ? t(`content.category.status.${status}`) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const { data } = await http.get<CategoryNodeView[]>('/admin/categories')
    tree.value = data
  } catch (e) {
    tree.value = []
    loadError.value = problemOf(e)?.detail ?? t('common.loadFailed')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    code: '',
    sortNo: tree.value.length * 10,
    status: 'ACTIVE',
    translations: [{ locale: 'zh-CN', name: '', description: '' }]
  })
  dialogVisible.value = true
}

function openEdit(node: CategoryNodeView): void {
  editing.value = node
  Object.assign(form, {
    code: node.code,
    sortNo: node.sortNo,
    status: node.status,
    translations: node.translations.map((item) => ({
      locale: item.locale,
      name: item.name,
      description: item.description ?? ''
    }))
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!SLUG_PATTERN.test(form.code)) {
    ElMessage.error(t('content.category.codeHint'))
    return
  }
  const translations = form.translations
    .filter((row) => (row.name ?? '').trim())
    .map((row) => ({
      locale: row.locale,
      name: row.name,
      description: row.description ?? ''
    }))
  if (translations.length === 0) {
    ElMessage.error(t('content.category.name'))
    return
  }
  const payload = {
    code: form.code,
    sortNo: form.sortNo,
    status: form.status,
    translations
  }
  saving.value = true
  try {
    if (editing.value) {
      await http.patch(`/admin/categories/${editing.value.id}`, payload)
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/categories', payload)
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function remove(node: CategoryNodeView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('content.category.deleteConfirm', { name: nameOf(node) }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/categories/${node.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('content.category.hasArticles'))
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('content.category.title') }}<HelpTip page="categories" :title="t('content.category.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ tree.length }}</span>
        <el-button
          v-if="auth.can('content:category:create')"
          type="primary"
          style="margin-inline-start: 12px"
          @click="openCreate"
        >
          {{ t('content.category.create') }}
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        stripe
      >
        <el-table-column prop="code" :label="t('content.category.code')" min-width="180" />
        <el-table-column :label="t('content.category.name')" min-width="200">
          <template #default="{ row }">{{ nameOf(row) }}</template>
        </el-table-column>
        <el-table-column prop="sortNo" :label="t('content.category.sort')" width="110" />
        <el-table-column :label="t('content.category.status')" width="120">
          <template #default="{ row }">{{ statusOf(row.status) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="200">
          <template #default="{ row }">
            <el-button
              v-if="auth.can('content:category:update')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="auth.can('content:category:delete')"
              link
              type="danger"
              @click="remove(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && !loadError && tree.length === 0"
        :description="t('content.category.empty')"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? t('content.category.edit') : t('content.category.create')"
      width="680px"
    >
      <el-form label-width="110px">
        <el-form-item><template #label>{{ t('content.category.code') }}<FieldHelp help="field.categories.code.help" /></template>
          <el-input v-model="form.code" :placeholder="t('content.category.codeHint')" />
        </el-form-item>
        <el-form-item :label="t('content.category.sort')">
          <el-input-number v-model="form.sortNo" :min="0" />
        </el-form-item>
        <el-form-item :label="t('content.category.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="t('content.category.status.ACTIVE')" value="ACTIVE" />
            <el-option :label="t('content.category.status.DISABLED')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">{{ t('content.article.translations') }}</el-divider>
        <TranslationsEditor v-model="form.translations" :fields="translationFields" />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ editing ? t('common.save') : t('common.create') }}
        </el-button>
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
