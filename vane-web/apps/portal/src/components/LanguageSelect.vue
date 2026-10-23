<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { localeLabels, locales, type Locale } from '@vane/shared'

const { locale } = useI18n()
const route = useRoute()
const router = useRouter()

/** 语言是 URL 的一部分（SEO 需要每语言独立地址），切换即导航并保留当前页面与查询串。 */
function change(event: Event): void {
  const next = (event.target as HTMLSelectElement).value as Locale
  const name = typeof route.name === 'string' ? route.name : 'home'
  void router.push({
    name,
    params: { ...route.params, locale: next },
    query: route.query
  })
}
</script>

<template>
  <select class="language-select" :value="locale" :aria-label="localeLabels[locale as Locale]" @change="change">
    <option v-for="item in locales" :key="item" :value="item">{{ localeLabels[item] }}</option>
  </select>
</template>

<style scoped>
.language-select {
  border: 1px solid var(--vane-color-border-strong);
  background: var(--vane-color-surface);
  border-radius: var(--vane-radius-sm);
  padding: var(--vane-space-2xs) var(--vane-space-xs);
  font-size: var(--vane-font-size-sm);
  color: var(--vane-color-text);
  cursor: pointer;
}
</style>
