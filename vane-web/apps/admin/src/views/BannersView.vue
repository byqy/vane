<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime, http, problemOf, toDateTimeInput, toIsoInstant } from '@vane/shared'

interface BannerView {
  id: string
  position: string
  imageFileId: string
  titleKey: string | null
  linkUrl: string | null
  sortNo: number
  status: string
  startAt: string | null
  endAt: string | null
}

const { t, locale } = useI18n()
const items = ref<BannerView[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = reactive({ position: 'home.top', imageFileId: '', titleKey: '', linkUrl: '', sortNo: 0, status: 'ACTIVE', startAt: '', endAt: '' })
const uploading = ref(false)
const editing = ref<BannerView | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<BannerView[]>('/admin/banners')
    items.value = data
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    position: 'home.top',
    imageFileId: '',
    titleKey: '',
    linkUrl: '',
    sortNo: 0,
    status: 'ACTIVE',
    startAt: '',
    endAt: ''
  })
  dialogVisible.value = true
}

function openEdit(banner: BannerView): void {
  editing.value = banner
  Object.assign(form, {
    position: banner.position,
    imageFileId: banner.imageFileId,
    titleKey: banner.titleKey ?? '',
    linkUrl: banner.linkUrl ?? '',
    sortNo: banner.sortNo,
    status: banner.status,
    startAt: toDateTimeInput(banner.startAt),
    endAt: toDateTimeInput(banner.endAt)
  })
  dialogVisible.value = true
}

async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('visibility', 'PUBLIC')
    const response = await http.post<{ id: string }>('/files', data)
    form.imageFileId = response.data.id
    ElMessage.success(t('banners.uploadSuccess'))
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function submit(): Promise<void> {
  if (!form.imageFileId) {
    ElMessage.error(t('banners.uploadFirst'))
    return
  }
  try {
    if (editing.value) {
      await http.patch(`/admin/banners/${editing.value.id}`, {
        imageFileId: form.imageFileId,
        titleKey: form.titleKey,
        linkUrl: form.linkUrl,
        sortNo: form.sortNo,
        status: form.status,
        startAt: toIsoInstant(form.startAt),
        endAt: toIsoInstant(form.endAt)
      })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      await http.post('/admin/banners', {
        position: form.position,
        imageFileId: form.imageFileId,
        titleKey: form.titleKey,
        linkUrl: form.linkUrl,
        sortNo: form.sortNo,
        startAt: toIsoInstant(form.startAt),
        endAt: toIsoInstant(form.endAt)
      })
      ElMessage.success(t('common.createSuccess'))
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(banner: BannerView): Promise<void> {
  try {
    await ElMessageBox.confirm(t('banners.deleteConfirm'), t('common.confirmTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await http.delete(`/admin/banners/${banner.id}`)
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
      <h3 style="margin: 0">{{ t('banners.title') }}<HelpTip page="banners" :title="t('banners.title')" /></h3>
      <el-button type="primary" @click="openCreate">{{ t('banners.create') }}</el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column prop="position" :label="t('navs.position')" width="140" />
        <el-table-column prop="imageFileId" :label="t('banners.imageFileId')" min-width="280" />
        <el-table-column prop="titleKey" :label="t('banners.titleKey')" width="180" />
        <el-table-column prop="linkUrl" :label="t('banners.link')" min-width="160" />
        <el-table-column prop="sortNo" :label="t('common.sort')" width="90" />
        <el-table-column prop="status" :label="t('common.status')" width="100" />
        <el-table-column :label="t('banners.startAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.startAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('banners.endAt')" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.endAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && items.length === 0" :description="t('common.empty')" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? t('banners.edit') : t('banners.create')" width="540px">
      <el-form label-width="100px">
        <el-form-item :label="t('navs.position')">
          <el-input v-model="form.position" :disabled="!!editing" />
        </el-form-item>
        <el-form-item><template #label>{{ t('banners.image') }}<FieldHelp help="field.banners.image.help" /></template>
          <input type="file" accept="image/*" @change="upload" />
          <div v-if="form.imageFileId" style="margin-inline-start: 12px; color: #6b7280">{{ form.imageFileId }}</div>
        </el-form-item>
        <el-form-item :label="t('banners.titleKey')"><el-input v-model="form.titleKey" :placeholder="t('banners.titleKeyHint')" /></el-form-item>
        <el-form-item><template #label>{{ t('banners.link') }}<FieldHelp help="field.banners.link.help" /></template><el-input v-model="form.linkUrl" /></el-form-item>
        <el-form-item :label="t('common.sort')"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
        <el-form-item v-if="editing" :label="t('common.status')">
          <el-select v-model="form.status" style="width: 160px">
            <el-option :label="t('common.enabled')" value="ACTIVE" />
            <el-option :label="t('common.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>{{ t('banners.startAt') }}<FieldHelp help="field.banners.startAt.help" /></template>
          <el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item><template #label>{{ t('banners.endAt') }}<FieldHelp help="field.banners.endAt.help" /></template>
          <el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="uploading" @click="submit">
          {{ editing ? t('common.save') : t('common.create') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>







