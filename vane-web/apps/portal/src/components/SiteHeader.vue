<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchNavs, type PublicNavNode } from '../api'
import { portalLabel } from '../labels'
import { localizeHref } from '../seo'
import LanguageSelect from './LanguageSelect.vue'

const { t, locale } = useI18n()
const navs = ref<PublicNavNode[]>([])

/** 页头只渲染一级项的直达链接（含其子项），保持单行导航的可用性。 */
const flatNavs = computed(() => navs.value.flatMap((nav) => [nav, ...(nav.children ?? [])]))

/** ADR-025：优先用后端下发的本地化 label，留空回退 labelKey → portal.* 文案。 */
function navLabel(nav: PublicNavNode): string {
  return nav.label?.trim() || portalLabel(t, nav.labelKey)
}

function isInternal(target: string | null): boolean {
  return Boolean(target && target.startsWith('/'))
}

async function load(): Promise<void> {
  try {
    navs.value = await fetchNavs('HEADER')
  } catch {
    // 导航失败不阻塞页面内容；保持空导航
    navs.value = []
  }
}

watch(locale, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <header class="site-header">
    <div class="container header-inner">
      <router-link class="brand" :to="`/${locale}`">{{ t('portal.site') }}</router-link>
      <nav class="nav">
        <template v-for="nav in flatNavs" :key="nav.id">
          <router-link v-if="isInternal(nav.target)" class="nav-link" :to="localizeHref(locale, nav.target)">
            {{ navLabel(nav) }}
          </router-link>
          <a
            v-else
            class="nav-link"
            :href="localizeHref(locale, nav.target)"
            :target="nav.openInNewTab ? '_blank' : undefined"
            rel="noopener"
          >
            {{ navLabel(nav) }}
          </a>
        </template>
      </nav>
      <LanguageSelect />
    </div>
  </header>
</template>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--vane-color-surface);
  border-bottom: 1px solid var(--vane-color-border);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: var(--vane-space-md);
  padding-block: var(--vane-space-sm);
}

.brand {
  font-size: var(--vane-font-size-lg);
  font-weight: var(--vane-font-weight-bold);
  color: var(--vane-color-text);
  text-decoration: none;
  white-space: nowrap;
}

.nav {
  display: flex;
  gap: var(--vane-space-md);
  flex: 1;
  flex-wrap: wrap;
}

.nav-link {
  color: var(--vane-color-text-secondary);
  text-decoration: none;
  font-size: var(--vane-font-size-base);
}

.nav-link:hover {
  color: var(--vane-color-brand);
}
</style>
