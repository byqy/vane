<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { formatDate, http, problemOf, type PublicArticleDetail } from '@vane/shared'
import SiteHeader from '../components/SiteHeader.vue'
import { applyArticleSeo, applyNotFoundSeo } from '../seo'

const { t, locale } = useI18n()
const route = useRoute()
const article = ref<PublicArticleDetail | null>(null)
const loading = ref(false)
const notFound = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  const slug = String(route.params.slug ?? '')
  loading.value = true
  notFound.value = false
  error.value = null
  try {
    const { data } = await http.get<PublicArticleDetail>(`/public/articles/${slug}`)
    article.value = data
    void applyArticleSeo(locale.value, {
      slug: data.slug,
      title: data.title,
      summary: data.summary,
      publishedAt: data.publishedAt,
      categoryName: data.categoryName
    })
  } catch (e) {
    article.value = null
    const problem = problemOf(e)
    if (problem?.status === 404) {
      notFound.value = true
      void applyNotFoundSeo(locale.value)
    } else {
      error.value = problem?.detail ?? t('common.loadFailed')
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(
  () => route.fullPath,
  () => {
    void load()
  }
)

watch(locale, () => {
  void load()
})
</script>

<template>
  <div class="container">
    <SiteHeader />

    <p v-if="loading" class="muted">…</p>

    <div v-else-if="notFound" class="card">
      <h1>{{ t('portal.articles.notFound') }}</h1>
      <router-link :to="`/${locale}/articles`">{{ t('portal.articles.backToList') }}</router-link>
    </div>

    <p v-else-if="error" class="error">{{ error }}</p>

    <article v-else-if="article" class="card">
      <h1>{{ article.title ?? article.slug }}</h1>
      <div class="meta">
        <router-link
          v-if="article.categoryCode"
          :to="`/${locale}/categories/${article.categoryCode}`"
          class="category"
        >
          {{ article.categoryName ?? article.categoryCode }}
        </router-link>
        <span v-for="tag in article.tags" :key="tag" class="tag">{{ tag }}</span>
        <span v-if="article.publishedAt">{{ formatDate(article.publishedAt, locale) }}</span>
      </div>
      <p v-if="article.summary" class="summary">{{ article.summary }}</p>
      <!-- 正文由后台编辑，属于受控内容；此处直接渲染 HTML -->
      <div v-if="article.body" class="body" v-html="article.body" />
      <router-link class="back" :to="`/${locale}/articles`">{{ t('portal.articles.backToList') }}</router-link>
    </article>
  </div>
</template>

<style scoped>



.card {
  background: #fff;
  border-radius: var(--vane-radius-card);
  border: 1px solid #eceff3;
  padding: 24px;
}

h1 {
  margin: 0 0 12px;
  font-size: 26px;
}

.meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 16px;
}

.category {
  color: var(--vane-color-brand);
  text-decoration: none;
}

.tag {
  background: #eef2ff;
  color: #3b5bdb;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
}

.summary {
  color: #4b5563;
  margin: 0 0 16px;
}

.body :deep(img) {
  max-width: 100%;
}

.back {
  display: inline-block;
  margin-top: 24px;
  color: var(--vane-color-brand);
}

.muted {
  color: #6b7280;
}

.error {
  color: #c92a2a;
}
</style>
