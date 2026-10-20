<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import FieldHelp from '../components/FieldHelp.vue'
import HelpTip from '../components/HelpTip.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  formatDateTime,
  http,
  problemOf,
  type ArticleView,
  type CategoryNodeView,
  type PageResult,
  type TagView
} from '@vane/shared'
import TranslationsEditor from '../components/TranslationsEditor.vue'
import type { TranslationRow } from '../components/translation-types'
import { useAuthStore } from '../stores/auth'

const { t, locale } = useI18n()
const auth = useAuthStore()

const items = ref<ArticleView[]>([])
const categories = ref<CategoryNodeView[]>([])
const tags = ref<TagView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const loadError = ref<string | null>(null)

const filters = reactive({ status: '', categoryId: '', keyword: '' })

const dialogVisible = ref(false)
const editing = ref<ArticleView | null>(null)
const saving = ref(false)
const selectedTagIds = ref<string[]>([])
const newTagCode = ref('')

const form = reactive({
  slug: '',
  categoryId: '',
  translations: [] as TranslationRow[]
})

const translationFields = [
  { key: 'title', labelKey: 'content.article.titleField' },
  { key: 'summary', labelKey: 'content.article.summary' },
  { key: 'body', labelKey: 'content.article.body', textarea: true }
]

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,63}$/

function flatten(nodes: CategoryNodeView[]): CategoryNodeView[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children ?? [])])
}

const categoryOptions = computed(() => flatten(categories.value))

function categoryLabel(node: CategoryNodeView): string {
  const name =
    node.translations.find((item) => item.locale === 'zh-CN')?.name ?? node.translations[0]?.name
  return name ? `${name} (${node.code})` : node.code
}

function titleOf(article: ArticleView): string {
  const zh = article.translations.find((item) => item.locale === 'zh-CN')
  return zh?.title ?? article.translations[0]?.title ?? '—'
}

function categoryOf(categoryId: string | null): string {
  if (!categoryId) {
    return t('content.article.noCategory')
  }
  const node = categoryOptions.value.find((item) => item.id === categoryId)
  return node ? categoryLabel(node) : t('content.article.noCategory')
}

function statusOf(status: string | undefined): string {
  return status ? t(`content.article.status.${status}`) : '—'
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
    if (filters.categoryId) {
      params.categoryId = filters.categoryId
    }
    if (filters.keyword.trim()) {
      params.keyword = filters.keyword.trim()
    }
    const { data } = await http.get<PageResult<ArticleView>>('/admin/articles', { params })
    items.value = data.items
    total.value = data.page.total
  } catch (e) {
    items.value = []
    total.value = 0
    loadError.value = problemOf(e)?.detail ?? t('common.loadFailed')
  } finally {
    loading.value = false
  }
}

async function loadCategories(): Promise<void> {
  try {
    const { data } = await http.get<CategoryNodeView[]>('/admin/categories')
    categories.value = data
  } catch {
    categories.value = []
  }
}

async function loadTags(): Promise<void> {
  try {
    const { data } = await http.get<TagView[]>('/admin/tags')
    tags.value = data
  } catch {
    tags.value = []
  }
}

function search(): void {
  page.value = 1
  void load()
}

function reset(): void {
  filters.status = ''
  filters.categoryId = ''
  filters.keyword = ''
  page.value = 1
  void load()
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, {
    slug: '',
    categoryId: '',
    translations: [{ locale: 'zh-CN', title: '', summary: '', body: '' }]
  })
  selectedTagIds.value = []
  newTagCode.value = ''
  dialogVisible.value = true
}

function openEdit(article: ArticleView): void {
  editing.value = article
  Object.assign(form, {
    slug: article.slug,
    categoryId: article.categoryId ?? '',
    translations: article.translations.map((item) => ({
      locale: item.locale,
      title: item.title,
      summary: item.summary ?? '',
      body: item.body ?? ''
    }))
  })
  selectedTagIds.value = article.tags.map((tag) => tag.id)
  newTagCode.value = ''
  dialogVisible.value = true
}

async function createTag(): Promise<void> {
  const code = newTagCode.value.trim()
  if (!SLUG_PATTERN.test(code)) {
    ElMessage.error(t('content.tag.codeHint'))
    return
  }
  try {
    const { data } = await http.post<TagView>('/admin/tags', { code })
    tags.value = [...tags.value, data].sort((left, right) => left.code.localeCompare(right.code))
    selectedTagIds.value = [...selectedTagIds.value, data.id]
    newTagCode.value = ''
    ElMessage.success(t('common.createSuccess'))
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function submit(): Promise<void> {
  if (!SLUG_PATTERN.test(form.slug)) {
    ElMessage.error(t('content.article.slugHint'))
    return
  }
  const translations = form.translations
    .filter((row) => (row.title ?? '').trim())
    .map((row) => ({
      locale: row.locale,
      title: row.title,
      summary: row.summary ?? '',
      body: row.body ?? ''
    }))
  if (translations.length === 0) {
    ElMessage.error(t('content.article.titleField'))
    return
  }
  const payload = {
    slug: form.slug,
    categoryId: form.categoryId || null,
    translations
  }
  saving.value = true
  try {
    if (editing.value) {
      const id = editing.value.id
      await http.patch<ArticleView>(`/admin/articles/${id}`, payload)
      // 编辑时始终整体替换标签，允许清空
      await http.put(`/admin/articles/${id}/tags`, { tagIds: selectedTagIds.value })
      ElMessage.success(t('common.saveSuccess'))
    } else {
      const { data } = await http.post<ArticleView>('/admin/articles', payload)
      if (selectedTagIds.value.length > 0) {
        await http.put(`/admin/articles/${data.id}/tags`, { tagIds: selectedTagIds.value })
      }
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

async function setPublished(article: ArticleView, publish: boolean): Promise<void> {
  try {
    await http.post(`/admin/articles/${article.id}/${publish ? 'publish' : 'unpublish'}`)
    ElMessage.success(t(publish ? 'content.article.published' : 'content.article.unpublished'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.saveFailed'))
  }
}

async function remove(article: ArticleView): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('content.article.deleteConfirm', { name: article.slug }),
      t('common.confirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await http.delete(`/admin/articles/${article.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    await load()
  } catch (e) {
    ElMessage.error(problemOf(e)?.detail ?? t('common.deleteFailed'))
  }
}

onMounted(() => {
  void Promise.all([load(), loadCategories(), loadTags()])
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h3 style="margin: 0">{{ t('content.article.title') }}<HelpTip page="articles" :title="t('content.article.title')" /></h3>
      <div>
        <span class="meta">{{ t('common.total') }} {{ total }}</span>
        <el-button
          v-if="auth.can('content:article:create')"
          type="primary"
          style="margin-inline-start: 12px"
          @click="openCreate"
        >
          {{ t('content.article.create') }}
        </el-button>
      </div>
    </div>

    <div class="card filters">
      <el-select v-model="filters.status" :placeholder="t('content.article.status')" clearable style="width: 150px">
        <el-option :label="t('content.article.status.DRAFT')" value="DRAFT" />
        <el-option :label="t('content.article.status.PUBLISHED')" value="PUBLISHED" />
        <el-option :label="t('content.article.status.ARCHIVED')" value="ARCHIVED" />
      </el-select>
      <el-select
        v-model="filters.categoryId"
        :placeholder="t('content.article.category')"
        clearable
        style="width: 200px"
      >
        <el-option v-for="node in categoryOptions" :key="node.id" :label="categoryLabel(node)" :value="node.id" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        :placeholder="t('content.article.keywordHint')"
        clearable
        style="width: 260px"
        @keyup.enter="search"
      />
      <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
      <el-button @click="reset">{{ t('common.refresh') }}</el-button>
    </div>

    <div class="card">
      <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" />
      <el-table v-loading="loading" :data="items" stripe>
        <el-table-column :label="t('content.article.titleField')" min-width="200">
          <template #default="{ row }">{{ titleOf(row) }}</template>
        </el-table-column>
        <el-table-column prop="slug" :label="t('content.article.slug')" width="180" />
        <el-table-column :label="t('content.article.category')" min-width="160">
          <template #default="{ row }">{{ categoryOf(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column :label="t('content.article.tags')" min-width="140">
          <template #default="{ row }">{{ row.tags.map((tag: TagView) => tag.code).join(', ') || '—' }}</template>
        </el-table-column>
        <el-table-column :label="t('content.article.status')" width="120">
          <template #default="{ row }">{{ statusOf(row.status) }}</template>
        </el-table-column>
        <el-table-column :label="t('content.article.publishedAt')" width="170">
          <template #default="{ row }">{{ formatDateTime(row.publishedAt, locale) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="240">
          <template #default="{ row }">
            <el-button
              v-if="auth.can('content:article:update')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-if="auth.can('content:article:publish')"
              link
              type="primary"
              @click="setPublished(row, row.status !== 'PUBLISHED')"
            >
              {{ row.status === 'PUBLISHED' ? t('content.article.unpublish') : t('content.article.publish') }}
            </el-button>
            <el-button
              v-if="auth.can('content:article:delete')"
              link
              type="danger"
              @click="remove(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !loadError && items.length === 0" :description="t('content.article.empty')" />

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
      :title="editing ? t('content.article.edit') : t('content.article.create')"
      width="760px"
    >
      <el-form label-width="110px">
        <el-form-item><template #label>{{ t('content.article.slug') }}<FieldHelp help="field.articles.slug.help" /></template>
          <el-input v-model="form.slug" :placeholder="t('content.article.slugHint')" />
        </el-form-item>
        <el-form-item><template #label>{{ t('content.article.category') }}<FieldHelp help="field.articles.category.help" /></template>
          <el-select v-model="form.categoryId" clearable style="width: 100%">
            <el-option
              v-for="node in categoryOptions"
              :key="node.id"
              :label="categoryLabel(node)"
              :value="node.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>{{ t('content.article.tags') }}<FieldHelp help="field.articles.tags.help" /></template>
          <el-select v-model="selectedTagIds" multiple filterable style="width: 100%">
            <el-option v-for="tag in tags" :key="tag.id" :label="tag.code" :value="tag.id" />
          </el-select>
        </el-form-item>
        <el-form-item><template #label>{{ t('content.article.newTag') }}<FieldHelp help="field.articles.newTag.help" /></template>
          <div class="tag-create">
            <el-input
              v-model="newTagCode"
              :placeholder="t('content.tag.codeHint')"
              style="width: 240px"
              @keyup.enter="createTag"
            />
            <el-button @click="createTag">{{ t('content.article.newTag') }}</el-button>
          </div>
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
.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 12px;
}

.meta {
  color: #6b7280;
  font-size: 13px;
}

.tag-create {
  display: flex;
  gap: 8px;
}
</style>
