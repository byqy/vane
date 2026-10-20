<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  formatDate,
  http,
  problemOf,
  type PageResult,
  type PublicArticleView,
  type PublicCategoryView,
  type PublicTagView
} from '@vane/shared'
import SiteHeader from '../components/SiteHeader.vue'
import { applyArticleListSeo } from '../seo'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

const items = ref<PublicArticleView[]>([])
const categories = ref<PublicCategoryView[]>([])
const tags = ref<PublicTagView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

const keyword = ref('')
const categoryCode = ref('')
const tagCode = ref('')
const page = ref(1)
const pageSize = 10

const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function flattenCategories(nodes: PublicCategoryView[]): PublicCategoryView[] {
  return nodes.flatMap((node) => [node, ...flattenCategories(node.children ?? [])])
}

function syncFromRoute(): void {
  keyword.value = String(route.query.keyword ?? '')
  categoryCode.value = String(route.params.code ?? route.query.category ?? '')
  tagCode.value = String(route.query.tag ?? '')
  const raw = Number(route.query.page ?? 1)
  page.value = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1
}

function filterQuery(): Record<string, string> {
  const query: Record<string, string> = {}
  if (keyword.value.trim()) {
    query.keyword = keyword.value.trim()
  }
  if (categoryCode.value) {
    query.category = categoryCode.value
  }
  if (tagCode.value) {
    query.tag = tagCode.value
  }
  return query
}

function navigate(query: Record<string, string>): void {
  void router.push({ name: 'articles', params: { locale: locale.value }, query })
}

async function loadMeta(): Promise<void> {
  try {
    const [categoryResponse, tagResponse] = await Promise.all([
      http.get<PublicCategoryView[]>('/public/categories'),
      http.get<PublicTagView[]>('/public/tags')
    ])
    categories.value = flattenCategories(categoryResponse.data)
    tags.value = tagResponse.data
  } catch {
    categories.value = []
    tags.value = []
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const params: Record<string, unknown> = {
      'page[number]': page.value - 1,
      'page[size]': pageSize
    }
    if (categoryCode.value) {
      params.category = categoryCode.value
    }
    if (tagCode.value) {
      params.tag = tagCode.value
    }
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
    }
    const { data } = await http.get<PageResult<PublicArticleView>>('/public/articles', { params })
    items.value = data.items
    total.value = data.page.total
    void applyArticleListSeo(locale.value, {
      categoryCode: categoryCode.value || null,
      keyword: keyword.value
    })
  } catch (e) {
    items.value = []
    total.value = 0
    const problem = problemOf(e)
    error.value = problem
      ? `${problem.detail ?? problem.title}（${problem.code ?? problem.status}）`
      : t('common.loadFailed')
  } finally {
    loading.value = false
  }
}

async function reloadFromRoute(): Promise<void> {
  syncFromRoute()
  await load()
}

function search(): void {
  page.value = 1
  navigate(filterQuery())
}

function clearFilters(): void {
  keyword.value = ''
  categoryCode.value = ''
  tagCode.value = ''
  page.value = 1
  navigate({})
}

function goPage(target: number): void {
  if (target < 1 || target > pages.value) {
    return
  }
  const query = filterQuery()
  if (target > 1) {
    query.page = String(target)
  }
  navigate(query)
}

function articleHref(article: PublicArticleView): string {
  return `/${locale.value}/articles/${article.slug}`
}

onMounted(() => {
  syncFromRoute()
  void Promise.all([load(), loadMeta()])
})

watch(
  () => route.fullPath,
  () => {
    void reloadFromRoute()
  }
)

watch(locale, () => {
  void loadMeta()
})
</script>

<template>
  <div class="container">
    <SiteHeader />

    <h1 class="page-title">{{ t('portal.articles.title') }}</h1>

    <div class="filters">
      <select v-model="categoryCode" :aria-label="t('portal.articles.category')" @change="search">
        <option value="">{{ t('common.all') }}</option>
        <option v-for="category in categories" :key="category.id" :value="category.code">
          {{ category.name }} ({{ category.articleCount }})
        </option>
      </select>
      <select v-model="tagCode" :aria-label="t('portal.articles.tag')" @change="search">
        <option value="">{{ t('common.all') }}</option>
        <option v-for="tag in tags" :key="tag.id" :value="tag.code">{{ tag.code }} ({{ tag.articleCount }})</option>
      </select>
      <input
        v-model="keyword"
        type="search"
        :placeholder="t('portal.articles.searchPlaceholder')"
        @keyup.enter="search"
      />
      <button type="button" class="primary" @click="search">{{ t('portal.articles.search') }}</button>
      <button type="button" class="ghost" @click="clearFilters">{{ t('common.refresh') }}</button>
    </div>

    <p v-if="!loading && !error" class="muted">{{ t('portal.articles.total', { count: total }) }}</p>
    <p v-if="loading" class="muted">…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="items.length === 0" class="muted">{{ t('portal.articles.empty') }}</p>

    <ul v-else class="list">
      <li v-for="article in items" :key="article.id" class="card">
        <h2>
          <router-link :to="articleHref(article)">{{ article.title ?? article.slug }}</router-link>
        </h2>
        <p v-if="article.summary" class="summary">{{ article.summary }}</p>
        <div class="meta">
          <span v-if="article.categoryName" class="category">
            {{ t('portal.articles.category') }}<span class="category-name">{{ article.categoryName }}</span>
          </span>
          <span v-for="tag in article.tags" :key="tag" class="tag">{{ tag }}</span>
          <span v-if="article.publishedAt">{{ formatDate(article.publishedAt, locale) }}</span>
        </div>
        <router-link class="more" :to="articleHref(article)">{{ t('portal.articles.readMore') }}</router-link>
      </li>
    </ul>

    <div v-if="!loading && !error && total > pageSize" class="pager">
      <button type="button" class="ghost" :disabled="page <= 1" @click="goPage(page - 1)">
        {{ t('portal.articles.prev') }}
      </button>
      <span class="muted">{{ t('portal.articles.pageInfo', { page, pages }) }}</span>
      <button type="button" class="ghost" :disabled="page >= pages" @click="goPage(page + 1)">
        {{ t('portal.articles.next') }}
      </button>
    </div>
  </div>
</template>

<style scoped>



.page-title {
  font-size: 24px;
  margin: 12px 0 16px;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 16px;
}

.filters select,
.filters input {
  border: 1px solid #d7dbe3;
  background: #fff;
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 14px;
  color: #374151;
}

.filters input {
  min-width: 220px;
}

.primary {
  border: 1px solid var(--vane-color-brand);
  background: var(--vane-color-brand);
  color: #fff;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
}

.ghost {
  border: 1px solid #d0d5dd;
  background: #fff;
  border-radius: 6px;
  padding: 6px 14px;
  cursor: pointer;
}

.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}

.card {
  background: #fff;
  border-radius: var(--vane-radius-card);
  padding: 16px 18px;
  border: 1px solid #eceff3;
}

.card h2 {
  margin: 0 0 8px;
  font-size: 18px;
}

.card h2 a {
  color: inherit;
  text-decoration: none;
}

.summary {
  margin: 0 0 10px;
  color: #4b5563;
}

.meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  color: #6b7280;
  font-size: 13px;
}

.category-name {
  margin-inline-start: 6px;
}

.tag {
  background: #eef2ff;
  color: #3b5bdb;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}

.more {
  display: inline-block;
  margin-top: 10px;
  color: var(--vane-color-brand);
  font-size: 14px;
}

.pager {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.muted {
  color: #6b7280;
}

.error {
  color: #c92a2a;
}
</style>
