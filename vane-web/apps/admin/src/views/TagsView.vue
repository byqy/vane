<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, problemOf, type TagView } from '@vane/shared'
import { useAuthStore } from '../stores/auth'

const { t, locale } = useI18n()
const auth = useAuthStore()

const items = ref<TagView[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ code: '' })

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,63}$/

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const { data } = await http.get<TagView[]>('/admin/tags')
    items.value = data
  } catch (e) {
    items.value = []
    loadError.value = problemOf(e)?.detail ?? t('common.loadFailed')
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  form.code = ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!SLUG_PATTERN.test(form.code)) {
    ElMessage.error(t('content.tag.codeHint'))
    return
  }
  saving.value = true
  try {
    await http.post('/admin/tags', { code: form.code })
    ElMessage.success(t('common.createSuccess'))
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function remove(tag: TagView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('content.tag.deleteConfirm', { name: tag.code }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/tags/${tag.id}`)
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
      <h3 style="margin: 0">{{ t('content.tag.title') }}<HelpTip page="tags" :title="t('content.tag.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ items.length }}</span>
        <el-button
          v-if="auth.can('content:tag:create')"
          type="primary"
          style="margin-inline-start: 12px"
          @click="openCreate"
        >
          {{ t('content.tag.create') }}
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="code" :label="t('content.tag.code')" min-width="200" />
        <el-table-column prop="articleCount" :label="t('content.tag.articleCount')" width="140" />
        <el-table-column :label="t('content.tag.createdAt')" width="190">
          <template #default="{ row }">{{ formatDateTime(row.createdAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="140">
          <template #default="{ row }">
            <el-button
              v-if="auth.can('content:tag:delete')"
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
        v-if="!loading && !loadError && items.length === 0"
        :description="t('content.tag.empty')"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="t('content.tag.create')" width="480px">
      <el-form label-width="100px">
        <el-form-item><template #label>{{ t('content.tag.code') }}<FieldHelp help="field.tags.code.help" /></template>
          <el-input
            v-model="form.code"
            :placeholder="t('content.tag.codeHint')"
            @keyup.enter="submit"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ t('common.create') }}
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
