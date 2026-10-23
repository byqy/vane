<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { formatDate, http, type PublicArticleDetail } from '@vane/shared'
import { isNotFound, problemText } from '../problem'
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
    if (isNotFound(e)) {
      notFound.value = true
      void applyNotFoundSeo(locale.value)
    } else {
      error.value = problemText(e, t('portal.common.error'))
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
  <div class="container container--narrow">
    <p v-if="loading" class="state">{{ t('portal.common.loading') }}</p>

    <div v-else-if="error" class="state state--error">
      <p>{{ error }}</p>
      <button type="button" class="state__retry" @click="load">{{ t('portal.common.retry') }}</button>
    </div>

    <div v-else-if="notFound" class="card">
      <h1>{{ t('portal.articles.notFound') }}</h1>
      <router-link :to="`/${locale}/articles`">{{ t('portal.articles.backToList') }}</router-link>
    </div>

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
      <div v-if="article.body" class="rich-text" v-html="article.body" />
      <router-link class="back" :to="`/${locale}/articles`">{{ t('portal.articles.backToList') }}</router-link>
    </article>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 var(--vane-space-sm);
  font-size: var(--vane-font-size-xl);
}

.meta {
  display: flex;
  gap: var(--vane-space-xs);
  flex-wrap: wrap;
  align-items: center;
  color: var(--vane-color-text-muted);
  font-size: var(--vane-font-size-sm);
  margin-bottom: var(--vane-space-md);
}

.category {
  color: var(--vane-color-brand);
  text-decoration: none;
}

.tag {
  background: var(--vane-color-surface-brand);
  color: var(--vane-color-brand-strong);
  border-radius: var(--vane-radius-pill);
  padding: var(--vane-space-2xs) var(--vane-space-xs);
  font-size: var(--vane-font-size-xs);
}

.summary {
  color: var(--vane-color-text-secondary);
  margin: 0 0 var(--vane-space-md);
}

.back {
  display: inline-block;
  margin-top: var(--vane-space-lg);
  color: var(--vane-color-brand);
  text-decoration: none;
}
</style>
