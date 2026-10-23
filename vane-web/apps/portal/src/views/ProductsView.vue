<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import type { PublicProductView } from '@vane/shared'
import { fetchProducts, PAGE_SIZE_MAX } from '../api'
import { problemText } from '../problem'
import { applyProductsSeo } from '../seo'
import ProductCard from '../components/ProductCard.vue'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

const products = ref<PublicProductView[]>([])
const groups = ref<string[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

const group = ref('')
const page = ref(1)
const pageSize = 9

const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function syncFromRoute(): void {
  group.value = String(route.query.group ?? '')
  const raw = Number(route.query.page ?? 1)
  page.value = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 1
}

function navigate(query: Record<string, string>): void {
  void router.push({ name: 'products', params: { locale: locale.value }, query })
}

function filter(): void {
  page.value = 1
  navigate(group.value ? { group: group.value } : {})
}

/** 产品线选项没有独立接口，用一页上限数据汇总；失败时只保留「全部产品线」。 */
async function loadGroups(): Promise<void> {
  try {
    const result = await fetchProducts({ page: 1, size: PAGE_SIZE_MAX })
    groups.value = [...new Set(result.items.map((item) => item.group).filter((value): value is string => Boolean(value)))]
  } catch {
    groups.value = []
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const result = await fetchProducts({ page: page.value, size: pageSize, group: group.value || null })
    products.value = result.items
    total.value = result.page.total
    void applyProductsSeo(locale.value, { group: group.value, page: page.value })
  } catch (e) {
    products.value = []
    total.value = 0
    error.value = problemText(e, t('portal.common.error'))
  } finally {
    loading.value = false
  }
}

async function reloadFromRoute(): Promise<void> {
  syncFromRoute()
  await load()
}

function goPage(target: number): void {
  if (target < 1 || target > pages.value) {
    return
  }
  const query: Record<string, string> = {}
  if (group.value) {
    query.group = group.value
  }
  if (target > 1) {
    query.page = String(target)
  }
  navigate(query)
}

onMounted(() => {
  syncFromRoute()
  void Promise.all([load(), loadGroups()])
})

watch(
  () => route.fullPath,
  () => {
    void reloadFromRoute()
  }
)

watch(locale, () => {
  void Promise.all([load(), loadGroups()])
})
</script>

<template>
  <div class="container">
    <header class="page-head">
      <h1 class="page-title">{{ t('portal.products.title') }}</h1>
      <p class="page-subtitle">{{ t('portal.products.subtitle') }}</p>
    </header>

    <div class="filters">
      <select
        v-model="group"
        class="filter-select"
        :aria-label="t('portal.products.allGroups')"
        @change="filter"
      >
        <option value="">{{ t('portal.products.allGroups') }}</option>
        <option v-for="item in groups" :key="item" :value="item">{{ item }}</option>
      </select>
      <p v-if="!loading && !error" class="state total">{{ total }}</p>
    </div>

    <p v-if="loading" class="state">{{ t('portal.common.loading') }}</p>

    <div v-else-if="error" class="state state--error">
      <p>{{ error }}</p>
      <button type="button" class="state__retry" @click="load">{{ t('portal.common.retry') }}</button>
    </div>

    <p v-else-if="products.length === 0" class="state">{{ t('portal.products.empty') }}</p>

    <div v-else class="product-grid">
      <ProductCard v-for="product in products" :key="product.id" :product="product" />
    </div>

    <div v-if="!loading && !error && total > pageSize" class="pager">
      <button type="button" class="button button--ghost" :disabled="page <= 1" @click="goPage(page - 1)">
        {{ t('portal.articles.prev') }}
      </button>
      <span class="state">{{ t('portal.articles.pageInfo', { page, pages }) }}</span>
      <button type="button" class="button button--ghost" :disabled="page >= pages" @click="goPage(page + 1)">
        {{ t('portal.articles.next') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.page-head {
  padding-block: var(--vane-space-xl) var(--vane-space-md);
}

.page-title {
  margin: 0 0 var(--vane-space-xs);
  font-size: var(--vane-font-size-2xl);
}

.page-subtitle {
  margin: 0;
  color: var(--vane-color-text-secondary);
  font-size: var(--vane-font-size-md);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--vane-space-md);
  margin-bottom: var(--vane-space-lg);
}

.filter-select {
  border: 1px solid var(--vane-color-border-strong);
  background: var(--vane-color-surface);
  border-radius: var(--vane-radius-sm);
  padding: var(--vane-space-xs) var(--vane-space-sm);
  font-size: var(--vane-font-size-base);
  color: var(--vane-color-text);
}

.total {
  color: var(--vane-color-text-muted);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--vane-space-lg);
}

.pager {
  display: flex;
  align-items: center;
  gap: var(--vane-space-sm);
  margin-top: var(--vane-space-xl);
}
</style>
