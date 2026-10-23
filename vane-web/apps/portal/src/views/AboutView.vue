<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDate } from '@vane/shared'
import { fetchPage, type PublicPageContent } from '../api'
import { isNotFound, problemText } from '../problem'
import { applyAboutSeo } from '../seo'

const { t, locale } = useI18n()

const page = ref<PublicPageContent | null>(null)
const loading = ref(false)
const notFound = ref(false)
const error = ref<string | null>(null)

/** 运营在后台维护了标题就用它，未发布时用固定的页面标题兜底。 */
const heading = computed(() => page.value?.title || t('portal.about.title'))

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  notFound.value = false
  try {
    page.value = await fetchPage('about')
    void applyAboutSeo(locale.value, page.value)
  } catch (e) {
    page.value = null
    if (isNotFound(e)) {
      notFound.value = true
      void applyAboutSeo(locale.value, null)
    } else {
      error.value = problemText(e, t('portal.common.error'))
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)

watch(locale, () => {
  void load()
})
</script>

<template>
  <div class="container">
    <h1 class="page-title">{{ heading }}</h1>

    <p v-if="loading" class="state">{{ t('portal.common.loading') }}</p>

    <div v-else-if="error" class="state state--error">
      <p>{{ error }}</p>
      <button type="button" class="state__retry" @click="load">{{ t('portal.common.retry') }}</button>
    </div>

    <div v-else-if="notFound" class="card">
      <p class="state">{{ t('portal.about.notFound') }}</p>
    </div>

    <article v-else-if="page" class="card">
      <p v-if="page.summary" class="about-summary">{{ page.summary }}</p>
      <!-- 正文由后台编辑，属于受控内容；此处直接渲染 HTML -->
      <div v-if="page.body" class="rich-text" v-html="page.body" />
      <p v-if="page.publishedAt" class="about-date">{{ formatDate(page.publishedAt, locale) }}</p>
    </article>
  </div>
</template>

<style scoped>
.page-title {
  font-size: var(--vane-font-size-xl);
  margin: var(--vane-space-xl) 0 var(--vane-space-md);
}

.about-summary {
  margin: 0 0 var(--vane-space-md);
  color: var(--vane-color-text-secondary);
  font-size: var(--vane-font-size-md);
}

.about-date {
  margin: var(--vane-space-lg) 0 0;
  color: var(--vane-color-text-subtle);
  font-size: var(--vane-font-size-sm);
}
</style>
