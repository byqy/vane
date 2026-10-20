<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { localeLabels, locales, type Locale } from '@vane/shared'

const { locale } = useI18n()
const route = useRoute()
const router = useRouter()

/** 语言是 URL 的一部分（SEO 需要每语言独立地址），切换即导航。 */
function change(event: Event): void {
  const next = (event.target as HTMLSelectElement).value as Locale
  if (route.name === 'page' && route.params.slug) {
    void router.push({ name: 'page', params: { locale: next, slug: String(route.params.slug) } })
    return
  }
  if (route.name === 'article' && route.params.slug) {
    void router.push({ name: 'article', params: { locale: next, slug: String(route.params.slug) } })
    return
  }
  if (route.name === 'category' && route.params.code) {
    void router.push({ name: 'category', params: { locale: next, code: String(route.params.code) }, query: route.query })
    return
  }
  if (route.name === 'articles') {
    void router.push({ name: 'articles', params: { locale: next }, query: route.query })
    return
  }
  void router.push({ name: 'home', params: { locale: next } })
}
</script>

<template>
  <select class="language-select" :value="locale" :aria-label="localeLabels[locale as Locale]" @change="change">
    <option v-for="item in locales" :key="item" :value="item">{{ localeLabels[item] }}</option>
  </select>
</template>

<style scoped>
.language-select {
  border: 1px solid #d7dbe3;
  background: #fff;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
}
</style>
