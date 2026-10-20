<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, problemOf } from '@vane/shared'

interface FileAdminView {
  id: string
  name: string
  mime: string
  size: number
  visibility: string
  createdAt: string
}

interface PageResult {
  items: FileAdminView[]
  page: { number: number; size: number; total: number }
}

const { t, locale } = useI18n()
const items = ref<FileAdminView[]>([])
const total = ref(0)
const loading = ref(false)
const page = reactive({ number: 0, size: 20 })
const traceId = ref<string | null>(null)

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadForm = reactive({ visibility: 'PRIVATE', file: null as File | null })

const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')
const previewMime = ref('')

async function load(): Promise<void> {
  loading.value = true
  traceId.value = null
  try {
    const { data } = await http.get<PageResult>('/admin/files', {
      params: { 'page[number]': page.number, 'page[size]': page.size }
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

function openUpload(): void {
  uploadForm.visibility = 'PRIVATE'
  uploadForm.file = null
  uploadVisible.value = true
}

function pickFile(event: Event): void {
  const input = event.target as HTMLInputElement
  uploadForm.file = input.files?.[0] ?? null
}

async function submitUpload(): Promise<void> {
  if (!uploadForm.file) {
    ElMessage.error(t('files.upload'))
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', uploadForm.file)
    data.append('visibility', uploadForm.visibility)
    await http.post('/files', data)
    ElMessage.success(t('files.uploadSuccess'))
    uploadVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    uploading.value = false
  }
}

async function blobOf(file: FileAdminView): Promise<Blob> {
  const response = await http.get<Blob>(`/files/${file.id}`, { responseType: 'blob' })
  return response.data
}

async function preview(file: FileAdminView): Promise<void> {
  if (!file.mime.startsWith('image/')) {
    ElMessage.warning(file.mime)
    return
  }
  try {
    const blob = await blobOf(file)
    if (previewUrl.value) {
      URL.revokeObjectURL(previewUrl.value)
    }
    previewUrl.value = URL.createObjectURL(blob)
    previewName.value = file.name
    previewMime.value = file.mime
    previewVisible.value = true
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

async function download(file: FileAdminView): Promise<void> {
  try {
    const blob = await blobOf(file)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = file.name
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  }
}

async function remove(file: FileAdminView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('common.deleteConfirm', { name: file.name }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/files/${file.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('files.title') }}<HelpTip page="files" :title="t('files.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button type="primary" style="margin-inline-start: 12px" @click="openUpload">{{ t('files.upload') }}</el-button>
      </div>
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="name" :label="t('files.name')" min-width="220" />
        <el-table-column prop="mime" :label="t('files.mime')" width="170" />
        <el-table-column :label="t('files.size')" width="110">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column :label="t('files.visibility')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.visibility === 'PUBLIC' ? 'success' : 'info'">
              {{ row.visibility === 'PUBLIC' ? t('files.visibilityPublic') : t('files.visibilityPrivate') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('files.uploadedAt')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row)">{{ t('files.preview') }}</el-button>
            <el-button link type="primary" @click="download(row)">{{ t('files.download') }}</el-button>
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

    <el-dialog v-model="uploadVisible" :title="t('files.upload')" width="480px">
      <el-form label-width="100px">
        <el-form-item :label="t('files.name')">
          <input type="file" @change="pickFile" />
        </el-form-item>
        <el-form-item :label="t('files.visibility')">
          <el-radio-group v-model="uploadForm.visibility">
            <el-radio value="PRIVATE">{{ t('files.visibilityPrivate') }}</el-radio>
            <el-radio value="PUBLIC">{{ t('files.visibilityPublic') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">{{ t('files.upload') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="t('files.previewTitle', { name: previewName })" width="720px">
      <img v-if="previewMime.startsWith('image/')" :src="previewUrl" style="max-width: 100%" :alt="previewName" />
    </el-dialog>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}
</style>




