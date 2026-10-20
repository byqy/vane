<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { formatDate, http, problemOf } from '@vane/shared'
import SiteHeader from '../components/SiteHeader.vue'
import { applyNotFoundSeo, applyPageSeo } from '../seo'

interface PublicPage {
  id: string
  slug: string
  title: string | null
  summary: string | null
  body: string | null
  coverFileId: string | null
  publishedAt: string | null
}

const { t, locale } = useI18n()
const route = useRoute()
const page = ref<PublicPage | null>(null)
const loading = ref(false)
const notFound = ref(false)

async function load(): Promise<void> {
  loading.value = true
  notFound.value = false
  try {
    const { data } = await http.get<PublicPage>(`/public/pages/${String(route.params.slug)}`)
    page.value = data
    void applyPageSeo(locale.value, String(route.params.slug), data)
  } catch (e) {
    const problem = problemOf(e)
    if (problem?.status === 404) {
      notFound.value = true
        void applyNotFoundSeo(locale.value)
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
    <SiteHeader />
    <p v-if="loading" class="muted">…</p>

    <div v-else-if="notFound" class="card">
      <h2>{{ t('portal.pageNotFound') }}</h2>
      <router-link :to="`/${locale}`">{{ t('portal.backHome') }}</router-link>
    </div>

    <article v-else-if="page" class="card">
      <h1>{{ page.title ?? page.slug }}</h1>
      <p v-if="page.summary" class="summary">{{ page.summary }}</p>
      <!-- 正文由后台编辑，属于受控内容；此处直接渲染 HTML -->
      <div v-if="page.body" class="body" v-html="page.body" />
      <p v-if="page.publishedAt" class="meta">{{ formatDate(page.publishedAt, locale) }}</p>
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
  font-size: 24px;
}

.summary {
  color: #4b5563;
  margin: 0 0 16px;
}

.body :deep(img) {
  max-width: 100%;
}

.meta {
  color: #9ca3af;
  font-size: 13px;
  margin-top: 16px;
}

.muted {
  color: #6b7280;
}
</style>






