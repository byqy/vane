<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchNavs, fetchSite, type PublicNavNode, type PublicSiteInfo } from '../api'
import { portalMessageKey } from '../i18n'
import { localizeHref } from '../seo'

const { t, locale } = useI18n()
const navs = ref<PublicNavNode[]>([])
const site = ref<PublicSiteInfo | null>(null)

const siteName = computed(() => site.value?.siteName || t('portal.site'))
const year = new Date().getFullYear()
const copyright = computed(() => t('portal.footer.copyright', { year, site: siteName.value }))

/** 数据库里的 labelKey（nav.about）映射到 portal.* 文案；未翻译时退化为最后一段。 */
function keyLabel(labelKey: string | null | undefined): string {
  if (!labelKey) {
    return ''
  }
  const messageKey = portalMessageKey(labelKey)
  const translated = t(messageKey)
  if (translated !== messageKey) {
    return translated
  }
  return labelKey.split('.').pop() ?? labelKey
}

/** ADR-025：优先展示后端下发的本地化 label，留空时回退 labelKey。 */
function navLabel(nav: PublicNavNode): string {
  return nav.label?.trim() || keyLabel(nav.labelKey)
}

async function load(): Promise<void> {
  // 页脚不是主内容，接口失败时保持可用的版权与标语，不阻塞页面
  const [siteResult, navResult] = await Promise.allSettled([fetchSite(), fetchNavs('FOOTER')])
  site.value = siteResult.status === 'fulfilled' ? siteResult.value : null
  navs.value = navResult.status === 'fulfilled' ? navResult.value : []
}

onMounted(load)

watch(locale, () => {
  void load()
})
</script>

<template>
  <footer class="site-footer">
    <div class="container footer-inner">
      <div class="brand">
        <p class="brand-name">{{ siteName }}</p>
        <p class="slogan">{{ t('portal.footer.slogan') }}</p>
      </div>

      <nav v-if="navs.length > 0" class="links" :aria-label="t('portal.footer.quickLinks')">
        <p class="links-title">{{ t('portal.footer.quickLinks') }}</p>
        <ul class="links-list">
          <li v-for="nav in navs" :key="nav.id">
            <a
              :href="localizeHref(locale, nav.target)"
              :target="nav.openInNewTab ? '_blank' : undefined"
              rel="noopener"
            >
              {{ navLabel(nav) }}
            </a>
          </li>
          <li v-for="child in navs.flatMap((nav) => nav.children ?? [])" :key="child.id">
            <a :href="localizeHref(locale, child.target)" :target="child.openInNewTab ? '_blank' : undefined" rel="noopener">
              {{ navLabel(child) }}
            </a>
          </li>
        </ul>
      </nav>
    </div>

    <div class="container copyright">
      <p>{{ copyright }}</p>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  margin-top: var(--vane-space-3xl);
  background: var(--vane-color-surface-inverse);
  color: var(--vane-color-text-inverse);
  padding-block: var(--vane-space-2xl) var(--vane-space-lg);
}

.footer-inner {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vane-space-xl);
  justify-content: space-between;
}

.brand-name {
  margin: 0 0 var(--vane-space-xs);
  font-size: var(--vane-font-size-lg);
  font-weight: var(--vane-font-weight-bold);
}

.slogan {
  margin: 0;
  max-width: 320px;
  color: var(--vane-color-border-strong);
  font-size: var(--vane-font-size-base);
}

.links-title {
  margin: 0 0 var(--vane-space-sm);
  font-size: var(--vane-font-size-base);
  font-weight: var(--vane-font-weight-medium);
}

.links-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vane-space-sm) var(--vane-space-lg);
  list-style: none;
  margin: 0;
  padding: 0;
}

.links-list a {
  color: var(--vane-color-border-strong);
  text-decoration: none;
  font-size: var(--vane-font-size-base);
}

.links-list a:hover {
  color: var(--vane-color-text-inverse);
}

.copyright {
  margin-top: var(--vane-space-xl);
  padding-top: var(--vane-space-md);
  border-top: 1px solid var(--vane-color-text-secondary);
  color: var(--vane-color-text-subtle);
  font-size: var(--vane-font-size-sm);
}

.copyright p {
  margin: 0;
}
</style>
