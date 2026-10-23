<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchSite, type PublicSiteInfo } from '../api'
import { useAsyncSection } from '../section'
import { applyContactSeo } from '../seo'

const { t, locale } = useI18n()

const site = useAsyncSection<PublicSiteInfo | null>(null)

interface ContactRow {
  key: string
  label: string
  value: string | null
}

/** 系统参数未配置时后端回空串，这里统一归一化为「无值」。 */
function valueOf(value: string | null | undefined): string | null {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}

/** 联系方式字段由后端按需下发；缺失时降级为占位文案，不隐藏整行、不造数据。 */
const rows = computed<ContactRow[]>(() => {
  const info = site.data
  return [
    { key: 'email', label: t('portal.contact.email'), value: valueOf(info?.contactEmail) },
    { key: 'phone', label: t('portal.contact.phone'), value: valueOf(info?.contactPhone) },
    { key: 'address', label: t('portal.contact.address'), value: valueOf(info?.contactAddress) },
    { key: 'hours', label: t('portal.contact.hours'), value: valueOf(info?.contactHours) }
  ]
})

async function load(): Promise<void> {
  await site.load(() => fetchSite())
  void applyContactSeo(locale.value)
}

onMounted(() => {
  void load()
})

watch(locale, () => {
  void load()
})
</script>

<template>
  <div class="container container--narrow">
    <h1 class="page-title">{{ t('portal.contact.title') }}</h1>

    <p v-if="site.loading" class="state">{{ t('portal.common.loading') }}</p>

    <div v-else-if="site.error" class="state state--error">
      <p>{{ site.error }}</p>
      <button type="button" class="state__retry" @click="load">{{ t('portal.common.retry') }}</button>
    </div>

    <dl v-else class="contact-list">
      <div v-for="row in rows" :key="row.key" class="contact-row">
        <dt class="contact-label">{{ row.label }}</dt>
        <dd class="contact-value" :class="{ 'contact-value--empty': !row.value }">
          {{ row.value || t('portal.section.empty') }}
        </dd>
      </div>
    </dl>
  </div>
</template>

<style scoped>
.page-title {
  margin: var(--vane-space-xl) 0 var(--vane-space-lg);
  font-size: var(--vane-font-size-2xl);
}

.contact-list {
  margin: 0;
  background: var(--vane-color-surface);
  border: 1px solid var(--vane-color-border);
  border-radius: var(--vane-radius-card);
  padding: var(--vane-space-lg);
}

.contact-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--vane-space-sm);
  padding-block: var(--vane-space-sm);
  border-bottom: 1px solid var(--vane-color-border);
}

.contact-row:last-child {
  border-bottom: none;
}

.contact-label {
  min-width: 96px;
  margin: 0;
  color: var(--vane-color-text-muted);
  font-size: var(--vane-font-size-base);
}

.contact-value {
  margin: 0;
  color: var(--vane-color-text);
  font-size: var(--vane-font-size-md);
}

.contact-value--empty {
  color: var(--vane-color-text-subtle);
  font-size: var(--vane-font-size-base);
}
</style>
