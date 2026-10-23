<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { formatDate } from '@vane/shared'
import { fetchPage, type PublicPageContent } from '../api'
import { isNotFound, problemText } from '../problem'
import { applyNotFoundSeo, applyPageSeo } from '../seo'

const { t, locale } = useI18n()
const route = useRoute()
const page = ref<PublicPageContent | null>(null)
const loading = ref(false)
const notFound = ref(false)
const error = ref<string | null>(null)

async function load(): Promise<void> {
  loading.value = true
  notFound.value = false
  error.value = null
  try {
    page.value = await fetchPage(String(route.params.slug))
    void applyPageSeo(locale.value, String(route.params.slug), page.value)
  } catch (e) {
    page.value = null
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

// 内容页正文同样按 Accept-Language 返回，切换语言后重载
watch(locale, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <div class="container">
    <p v-if="loading" class="state">{{ t('portal.common.loading') }}</p>

    <div v-else-if="error" class="state state--error">
      <p>{{ error }}</p>
      <button type="button" class="state__retry" @click="load">{{ t('portal.common.retry') }}</button>
    </div>

    <div v-else-if="notFound" class="card">
      <h2>{{ t('portal.pageNotFound') }}</h2>
      <router-link :to="`/${locale}`">{{ t('portal.backHome') }}</router-link>
    </div>

    <article v-else-if="page" class="card">
      <h1>{{ page.title ?? page.slug }}</h1>
      <p v-if="page.summary" class="summary">{{ page.summary }}</p>
      <!-- 正文由后台编辑，属于受控内容；此处直接渲染 HTML -->
      <div v-if="page.body" class="rich-text" v-html="page.body" />
      <p v-if="page.publishedAt" class="meta">{{ formatDate(page.publishedAt, locale) }}</p>
    </article>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 var(--vane-space-sm);
  font-size: var(--vane-font-size-xl);
}

.summary {
  color: var(--vane-color-text-secondary);
  margin: 0 0 var(--vane-space-md);
}

.meta {
  color: var(--vane-color-text-subtle);
  font-size: var(--vane-font-size-sm);
  margin-top: var(--vane-space-md);
}
</style>
