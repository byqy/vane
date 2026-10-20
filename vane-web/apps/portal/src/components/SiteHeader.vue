<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { http, problemOf } from '@vane/shared'
import { portalMessageKey } from '../i18n'
import { localizeHref } from '../seo'
import LanguageSelect from './LanguageSelect.vue'

interface NavNode {
  id: string
  labelKey: string
  target: string | null
  openInNewTab: boolean
  children: NavNode[]
}

const { t, locale } = useI18n()
const navs = ref<NavNode[]>([])

const label = (key: string | null): string => {
  if (!key) {
    return ''
  }
  const messageKey = portalMessageKey(key)
  const translated = t(messageKey)
  if (translated !== messageKey) {
    return translated
  }
  // 未翻译的 key 不直接显示原始标识，退化为最后一段（如 nav.articles → articles）
  return key.split('.').pop() ?? key
}

async function load(): Promise<void> {
  try {
    const { data } = await http.get<NavNode[]>('/public/navs', { params: { position: 'HEADER' } })
    navs.value = data
  } catch (e) {
    // 导航失败不阻塞页面内容，仅记录状态；保持空导航
    void problemOf(e)
  }
}

watch(locale, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <header class="header">
    <router-link class="brand" :to="`/${locale}`">{{ t('portal.site') }}</router-link>
    <nav class="nav">
      <a
        v-for="nav in navs"
        :key="nav.id"
        :href="localizeHref(locale, nav.target)"
        :target="nav.openInNewTab ? '_blank' : undefined"
        rel="noopener"
      >
        {{ label(nav.labelKey) }}
      </a>
      <a v-for="child in navs.flatMap((nav) => nav.children)" :key="child.id" :href="localizeHref(locale, child.target)">
        {{ label(child.labelKey) }}
      </a>
    </nav>
    <LanguageSelect />
  </header>
</template>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
}

.brand {
  font-size: 20px;
  font-weight: 600;
  color: inherit;
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 16px;
  flex: 1;
  flex-wrap: wrap;
}

.nav a {
  color: #374151;
  text-decoration: none;
  font-size: 14px;
}

.nav a:hover {
  color: var(--vane-color-brand);
}
</style>

