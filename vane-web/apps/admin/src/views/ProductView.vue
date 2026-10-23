<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, languageName, locales, problemOf, type PageResult } from '@vane/shared'
import { useAuthStore } from '../stores/auth'

interface ProductListItem {
  id: string
  code: string
  slug: string
  group: string | null
  status: string
  sortNo: number
  coverFileId: string | null
  videoFileId: string | null
  publishedAt: string | null
  createdAt: string | null
  name: string | null
  summary: string | null
}

interface ProductFeature {
  title: string
  description: string
}

interface ProductTranslation {
  locale: string
  name: string
  summary: string
  body: string
  features: ProductFeature[]
}

interface ProductMedia {
  fileId: string
  kind: 'IMAGE' | 'VIDEO'
  sortNo: number
  caption: string
}

interface ProductDetail extends ProductListItem {
  translations: ProductTranslation[]
  media: ProductMedia[]
}

interface AdminFileView {
  id: string
  name: string
  mime: string
  size: number
  visibility: string
  createdAt: string
}

const { t, locale } = useI18n()
const auth = useAuthStore()
const canProduct = auth.can('content:product')
const canMedia = auth.can('content:product:media')

const STATUSES = ['DRAFT', 'PUBLISHED', 'ARCHIVED'] as const
const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,63}$/

const items = ref<ProductListItem[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref<string | null>(null)
const page = ref(1)
const pageSize = 20
const filters = reactive({ status: '', keyword: '' })

const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const saving = ref(false)

const form = reactive({
  code: '',
  slug: '',
  group: '',
  status: 'DRAFT',
  sortNo: 0,
  coverFileId: '',
  videoFileId: ''
})

const translations = ref<ProductTranslation[]>([])
const media = ref<ProductMedia[]>([])

// ---------- 文件选择 / 上传 ----------
const fileDialogVisible = ref(false)
const fileLoading = ref(false)
const files = ref<AdminFileView[]>([])
const fileKeyword = ref('')
/** 目标：cover | video | media:<index> */
const fileTarget = ref<string>('cover')
const fileKind = ref<'IMAGE' | 'VIDEO'>('IMAGE')
const uploading = ref(false)

function localeName(code: string): string {
  return languageName(code, locale.value)
}

function statusText(status: string): string {
  return t(`product.status.${status}`)
}

function statusTag(status: string): 'success' | 'info' | 'warning' {
  if (status === 'PUBLISHED') {
    return 'success'
  }
  if (status === 'ARCHIVED') {
    return 'warning'
  }
  return 'info'
}

function coverUrl(fileId: string | null | undefined): string {
  return fileId ? `/api/v1/public/files/${fileId}` : ''
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const params: Record<string, unknown> = {
      'page[number]': page.value - 1,
      'page[size]': pageSize
    }
    if (filters.status) {
      params.status = filters.status
    }
    if (filters.keyword.trim()) {
      params.keyword = filters.keyword.trim()
    }
    const { data } = await http.get<PageResult<ProductListItem>>('/admin/products', { params })
    items.value = data.items ?? []
    total.value = data.page?.total ?? 0
  } catch (e) {
    items.value = []
    total.value = 0
    loadError.value = problemOf(e)?.detail ?? t('common.loadFailed')
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function refresh(): void {
  filters.status = ''
  filters.keyword = ''
  page.value = 1
  void load()
}

function emptyTranslation(code: string): ProductTranslation {
  return { locale: code, name: '', summary: '', body: '', features: [] }
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    code: '',
    slug: '',
    group: '',
    status: 'DRAFT',
    sortNo: 0,
    coverFileId: '',
    videoFileId: ''
  })
  translations.value = locales.map((code) => emptyTranslation(code))
  media.value = []
  dialogVisible.value = true
}

async function openEdit(row: ProductListItem): Promise<void> {
  editingId.value = row.id
  Object.assign(form, {
    code: row.code,
    slug: row.slug,
    group: row.group ?? '',
    status: row.status,
    sortNo: row.sortNo,
    coverFileId: row.coverFileId ?? '',
    videoFileId: row.videoFileId ?? ''
  })
  const existing = new Map<string, ProductTranslation>()
  let detail: ProductDetail | null = null
  try {
    const { data } = await http.get<ProductDetail>(`/admin/products/${row.id}`)
    detail = data
  } catch {
    detail = null
  }
  const source = detail?.translations ?? []
  for (const item of source) {
    existing.set(
      item.locale,
      {
        locale: item.locale,
        name: item.name ?? '',
        summary: item.summary ?? '',
        body: item.body ?? '',
        features: (item.features ?? []).map((feature) => ({
          title: feature.title ?? '',
          description: feature.description ?? ''
        }))
      }
    )
  }
  if (existing.size === 0 && row.name) {
    existing.set('zh-CN', { ...emptyTranslation('zh-CN'), name: row.name, summary: row.summary ?? '' })
  }
  translations.value = locales.map((code) => existing.get(code) ?? emptyTranslation(code))
  media.value = (detail?.media ?? []).map((item) => ({
    fileId: item.fileId,
    kind: item.kind,
    sortNo: item.sortNo,
    caption: item.caption ?? ''
  }))
  dialogVisible.value = true
}

function addFeature(translation: ProductTranslation): void {
  translation.features.push({ title: '', description: '' })
}

function removeFeature(translation: ProductTranslation, index: number): void {
  translation.features.splice(index, 1)
}

function addMedia(): void {
  media.value.push({ fileId: '', kind: 'IMAGE', sortNo: media.value.length + 1, caption: '' })
}

function removeMedia(index: number): void {
  media.value.splice(index, 1)
}

function moveMedia(index: number, delta: number): void {
  const target = index + delta
  if (target < 0 || target >= media.value.length) {
    return
  }
  const list = [...media.value]
  const current = list[index]!
  list[index] = list[target]!
  list[target] = current
  media.value = list.map((item, position) => ({ ...item, sortNo: position + 1 }))
}

function openFilePicker(target: string, kind: 'IMAGE' | 'VIDEO'): void {
  fileTarget.value = target
  fileKind.value = kind
  fileKeyword.value = ''
  fileDialogVisible.value = true
  void loadFiles()
}

async function loadFiles(): Promise<void> {
  fileLoading.value = true
  try {
    const { data } = await http.get<PageResult<AdminFileView>>('/admin/files', {
      params: { 'page[number]': 0, 'page[size]': 50 }
    })
    files.value = data.items ?? []
  } catch (e) {
    files.value = []
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    fileLoading.value = false
  }
}

function pickExistingFile(file: AdminFileView): void {
  const target = fileTarget.value
  if (target === 'cover') {
    form.coverFileId = file.id
  } else if (target === 'video') {
    form.videoFileId = file.id
  } else if (target.startsWith('media:')) {
    const index = Number(target.slice('media:'.length))
    const entry = media.value[index]
    if (entry) {
      entry.fileId = file.id
      entry.kind = file.mime.startsWith('video/') ? 'VIDEO' : 'IMAGE'
    }
  }
  fileDialogVisible.value = false
}

async function uploadTo(target: string, event: Event): Promise<void> {
  fileTarget.value = target
  fileKind.value = target === 'video' ? 'VIDEO' : 'IMAGE'
  await uploadFile(event)
}

async function uploadFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('visibility', 'PUBLIC')
    const response = await http.post<{ id?: string; fileId?: string }>('/files', data)
    const id = response.data?.id ?? response.data?.fileId ?? ''
    if (!id) {
      ElMessage.error(t('common.saveFailed'))
      return
    }
    const kind: 'IMAGE' | 'VIDEO' =
      fileTarget.value === 'video' || file.type.startsWith('video/') ? 'VIDEO' : 'IMAGE'
    if (fileTarget.value === 'cover') {
      form.coverFileId = id
    } else if (fileTarget.value === 'video') {
      form.videoFileId = id
    } else if (fileTarget.value.startsWith('media:')) {
      const index = Number(fileTarget.value.slice('media:'.length))
      const entry = media.value[index]
      if (entry) {
        entry.fileId = id
        entry.kind = kind
      }
    }
    ElMessage.success(t('files.uploadSuccess'))
    fileDialogVisible.value = false
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    uploading.value = false
  }
}

async function submit(): Promise<void> {
  if (!form.code.trim()) {
    ElMessage.error(t('product.code'))
    return
  }
  if (!SLUG_PATTERN.test(form.slug.trim())) {
    ElMessage.error(t('product.slug'))
    return
  }
  const translationPayload = translations.value
    .filter((row) => row.name.trim() !== '')
    .map((row) => ({
      locale: row.locale,
      name: row.name.trim(),
      summary: row.summary,
      body: row.body,
      features: row.features
        .filter((feature) => feature.title.trim() !== '')
        .map((feature) => ({ title: feature.title.trim(), description: feature.description }))
    }))
  if (translationPayload.length === 0) {
    ElMessage.error(t('product.name'))
    return
  }
  const mediaPayload = media.value
    .filter((entry) => entry.fileId.trim() !== '')
    .map((entry, index) => ({
      fileId: entry.fileId.trim(),
      kind: entry.kind,
      sortNo: index + 1,
      caption: entry.caption
    }))
  const payload = {
    code: form.code.trim(),
    slug: form.slug.trim(),
    group: form.group.trim() || null,
    coverFileId: form.coverFileId || null,
    videoFileId: form.videoFileId || null,
    status: form.status,
    sortNo: form.sortNo,
    translations: translationPayload
  }
  saving.value = true
  try {
    if (editingId.value) {
      await http.patch(`/admin/products/${editingId.value}`, payload)
      if (canMedia) {
        await http.put(`/admin/products/${editingId.value}/media`, { media: mediaPayload })
      }
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/products', { ...payload, media: mediaPayload })
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

async function setPublished(row: ProductListItem, publish: boolean): Promise<void> {
  try {
    await http.post(`/admin/products/${row.id}/${publish ? 'publish' : 'unpublish'}`)
    ElMessage.success(t('common.saveSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(row: ProductListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(t('product.deleteConfirm'), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/products/${row.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('product.title') }}</h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button
          v-if="canProduct"
          type="primary"
          class="btn-create"
          style="margin-inline-start: 12px"
          @click="openCreate"
        >
          {{ t('product.create') }}
        </el-button>
      </div>
    </div>

    <div class="card filters">
      <el-select v-model="filters.status" :placeholder="t('product.status')" clearable style="width: 150px">
        <el-option v-for="status in STATUSES" :key="status" :label="statusText(status)" :value="status" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        :placeholder="t('product.keyword')"
        clearable
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-button type="primary" class="btn-search" @click="search">{{ t('common.search') }}</el-button>
      <el-button @click="refresh">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="card">
      <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="code" :label="t('product.code')" min-width="140" />
        <el-table-column prop="name" :label="t('product.name')" min-width="160">
          <template #default="{ row }">{{ row.name || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('product.cover')" width="110">
          <template #default="{ row }">
            <img
              v-if="row.coverFileId"
              class="thumb"
              :src="coverUrl(row.coverFileId)"
              :alt="t('product.cover')"
            />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="group" :label="t('product.group')" min-width="140">
          <template #default="{ row }">{{ row.group || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('product.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" :label="t('product.sortNo')" width="90" />
        <el-table-column :label="t('product.publishedAt')" width="180">
          <template #default="{ row }">
            {{ row.publishedAt ? formatDateTime(row.publishedAt, locale) : '—' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canProduct"
              link
              type="primary"
              class="act-edit"
              @click="openEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="canProduct"
              link
              type="primary"
              class="act-publish"
              @click="setPublished(row, row.status !== 'PUBLISHED')"
            >
              {{ row.status === 'PUBLISHED' ? t('product.unpublish') : t('product.publish') }}
            </el-button>
            <el-button
              v-if="canProduct"
              link
              type="danger"
              class="act-delete"
              @click="remove(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !loadError && items.length === 0" :description="t('product.empty')" />

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        style="margin-top: 12px"
        @current-change="load"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('product.edit') : t('product.create')"
      width="900px"
    >
      <div class="dialog-body">
        <el-form label-width="120px">
          <el-form-item :label="t('product.code')">
            <el-input v-model="form.code" class="f-code" />
          </el-form-item>
          <el-form-item :label="t('product.slug')">
            <el-input v-model="form.slug" class="f-slug" />
          </el-form-item>
          <el-form-item :label="t('product.group')">
            <el-input v-model="form.group" class="f-group" />
          </el-form-item>
          <el-form-item :label="t('product.sortNo')">
            <el-input-number v-model="form.sortNo" :min="0" class="f-sort" />
          </el-form-item>
          <el-form-item :label="t('product.status')">
            <el-select v-model="form.status" class="f-status" style="width: 100%">
              <el-option v-for="status in STATUSES" :key="status" :label="statusText(status)" :value="status" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('product.cover')">
            <div class="media-row">
              <img v-if="form.coverFileId" class="thumb" :src="coverUrl(form.coverFileId)" :alt="t('product.cover')" />
              <span v-else>—</span>
              <el-button @click="openFilePicker('cover', 'IMAGE')">{{ t('product.chooseFile') }}</el-button>
              <label v-if="canMedia" class="upload-label">
                {{ t('product.uploadImage') }}
                <input type="file" accept="image/*" @change="uploadTo('cover', $event)" />
              </label>
            </div>
          </el-form-item>
          <el-form-item :label="t('product.video')">
            <div class="media-row">
              <span>{{ form.videoFileId || '—' }}</span>
              <el-button @click="openFilePicker('video', 'VIDEO')">{{ t('product.chooseFile') }}</el-button>
              <label v-if="canMedia" class="upload-label">
                {{ t('product.uploadVideo') }}
                <input type="file" accept="video/*" @change="uploadTo('video', $event)" />
              </label>
            </div>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">{{ t('product.translations') }}</el-divider>
        <p class="meta">{{ t('common.localeHint') }}</p>
        <div
          v-for="row in translations"
          :key="row.locale"
          class="lang-row"
          :data-locale="row.locale"
        >
          <div class="lang-head">{{ localeName(row.locale) }}</div>
          <el-form label-width="120px">
            <el-form-item :label="t('product.name')">
              <el-input v-model="row.name" class="f-name" />
            </el-form-item>
            <el-form-item :label="t('product.summary')">
              <el-input v-model="row.summary" class="f-summary" />
            </el-form-item>
            <el-form-item :label="t('product.body')">
              <el-input v-model="row.body" type="textarea" :rows="3" class="f-body" />
            </el-form-item>
            <el-form-item :label="t('product.features')">
              <div class="features">
                <div v-for="(feature, index) in row.features" :key="index" class="feature-row">
                  <el-input v-model="feature.title" :placeholder="t('product.featureTitle')" class="f-feature-title" />
                  <el-input v-model="feature.description" :placeholder="t('product.featureDescription')" class="f-feature-desc" />
                  <el-button link type="danger" @click="removeFeature(row, index)">
                    {{ t('product.featureRemove') }}
                  </el-button>
                </div>
                <el-button size="small" class="f-feature-add" @click="addFeature(row)">{{ t('product.addFeature') }}</el-button>
              </div>
            </el-form-item>
          </el-form>
        </div>

        <el-divider content-position="left">{{ t('product.media') }}</el-divider>
        <div v-if="canMedia">
          <div v-for="(entry, index) in media" :key="index" class="media-entry">
            <el-input v-model="entry.fileId" class="f-media-file" style="width: 260px" />
            <el-select v-model="entry.kind" style="width: 130px">
              <el-option :label="t('product.mediaImage')" value="IMAGE" />
              <el-option :label="t('product.mediaVideo')" value="VIDEO" />
            </el-select>
            <el-input v-model="entry.caption" :placeholder="t('product.caption')" class="f-media-caption" style="width: 220px" />
            <el-button @click="openFilePicker(`media:${index}`, 'IMAGE')">{{ t('product.chooseFile') }}</el-button>
            <el-button link @click="moveMedia(index, -1)">↑</el-button>
            <el-button link @click="moveMedia(index, 1)">↓</el-button>
            <el-button link type="danger" @click="removeMedia(index)">{{ t('product.mediaRemove') }}</el-button>
          </div>
          <el-button size="small" class="f-media-add" @click="addMedia">{{ t('product.addMedia') }}</el-button>
        </div>
        <p v-else class="meta">{{ t('product.media') }}</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" class="btn-submit" :loading="saving" @click="submit">
          {{ editingId ? t('common.save') : t('common.create') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fileDialogVisible" :title="t('files.title')" width="720px">
      <div class="media-row">
        <el-input v-model="fileKeyword" :placeholder="t('common.search')" style="width: 220px" />
        <el-button @click="loadFiles">{{ t('common.refresh') }}</el-button>
        <label class="upload-label">
          {{ t('files.upload') }}
          <input type="file" :accept="fileKind === 'VIDEO' ? 'video/*' : 'image/*'" @change="uploadFile" />
        </label>
      </div>
      <el-table v-loading="fileLoading" :data="files" stripe height="320">
        <el-table-column prop="name" :label="t('files.name')" min-width="200" />
        <el-table-column prop="mime" :label="t('files.mime')" width="180" />
        <el-table-column :label="t('common.actions')" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="pickExistingFile(row)">{{ t('product.chooseFile') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!fileLoading && files.length === 0" :description="t('common.empty')" />
    </el-dialog>
  </div>
</template>

<style scoped>
.meta {
  color: #6b7280;
  font-size: 13px;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 12px;
}

.dialog-body {
  max-height: 60vh;
  overflow-y: auto;
  padding-inline-end: 8px;
}

.thumb {
  width: 56px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #e5e7eb;
}

.lang-row {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}

.lang-head {
  font-weight: 600;
  margin-bottom: 8px;
}

.features {
  width: 100%;
}

.feature-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.media-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.media-entry {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.upload-label {
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
}
</style>
