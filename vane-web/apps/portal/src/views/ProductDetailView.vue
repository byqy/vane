<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import type { PublicProductDetail } from '@vane/shared'
import { fetchProduct, fileUrl } from '../api'
import { isNotFound, problemText } from '../problem'
import { applyProductSeo } from '../seo'
import MediaGallery from '../components/MediaGallery.vue'

const { t, locale } = useI18n()
const route = useRoute()

const product = ref<PublicProductDetail | null>(null)
const loading = ref(false)
const notFound = ref(false)
const error = ref<string | null>(null)

const features = computed(() => product.value?.features ?? [])

async function load(): Promise<void> {
  const slug = String(route.params.slug ?? '')
  loading.value = true
  error.value = null
  notFound.value = false
  try {
    product.value = await fetchProduct(slug)
    void applyProductSeo(locale.value, product.value)
  } catch (e) {
    product.value = null
    if (isNotFound(e)) {
      notFound.value = true
      void applyProductSeo(locale.value, null)
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
  <div class="container">
    <nav class="breadcrumb" :aria-label="t('portal.products.title')">
      <router-link :to="`/${locale}`">{{ t('portal.nav.home') }}</router-link>
      <span class="separator" aria-hidden="true">/</span>
      <router-link :to="`/${locale}/products`">{{ t('portal.products.title') }}</router-link>
      <template v-if="product">
        <span class="separator" aria-hidden="true">/</span>
        <span class="current">{{ product.name }}</span>
      </template>
    </nav>

    <p v-if="loading" class="state">{{ t('portal.common.loading') }}</p>

    <div v-else-if="error" class="state state--error">
      <p>{{ error }}</p>
      <button type="button" class="state__retry" @click="load">{{ t('portal.common.retry') }}</button>
    </div>

    <div v-else-if="notFound" class="card">
      <h1 class="not-found-title">{{ t('portal.products.notFound') }}</h1>
      <router-link class="back" :to="`/${locale}/products`">{{ t('portal.section.viewAll') }}</router-link>
    </div>

    <article v-else-if="product" class="product">
      <header class="product-head">
        <div v-if="product.group" class="product-group">{{ product.group }}</div>
        <h1 class="product-name">{{ product.name }}</h1>
        <p v-if="product.summary" class="product-summary">{{ product.summary }}</p>
        <router-link class="button button--primary inquiry" :to="`/${locale}/contact`">
          {{ t('portal.products.inquiry') }}
        </router-link>
      </header>

      <img
        v-if="product.coverFileId"
        class="cover"
        :src="fileUrl(product.coverFileId)"
        :alt="product.name"
      />

      <MediaGallery :media="product.media" :video-file-id="product.videoFileId" />

      <section v-if="features.length > 0" class="features">
        <h2 class="block-title">{{ t('portal.products.features') }}</h2>
        <ul class="feature-list">
          <li v-for="(feature, index) in features" :key="`${feature.title}-${index}`" class="feature">
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p v-if="feature.description" class="feature-description">{{ feature.description }}</p>
          </li>
        </ul>
      </section>

      <section v-if="product.body" class="detail">
        <div class="rich-text" v-html="product.body" />
      </section>

      <router-link class="back" :to="`/${locale}/products`">{{ t('portal.section.viewAll') }}</router-link>
    </article>
  </div>
</template>

<style scoped>
.breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--vane-space-xs);
  padding-block: var(--vane-space-lg) var(--vane-space-md);
  font-size: var(--vane-font-size-sm);
  color: var(--vane-color-text-muted);
}

.breadcrumb a {
  color: var(--vane-color-text-muted);
  text-decoration: none;
}

.breadcrumb a:hover {
  color: var(--vane-color-brand);
}

.separator {
  color: var(--vane-color-text-subtle);
}

.current {
  color: var(--vane-color-text);
}

.not-found-title {
  margin: 0 0 var(--vane-space-md);
  font-size: var(--vane-font-size-xl);
}

.product-head {
  margin-bottom: var(--vane-space-lg);
}

.product-group {
  font-size: var(--vane-font-size-xs);
  color: var(--vane-color-brand);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: var(--vane-space-2xs);
}

.product-name {
  margin: 0 0 var(--vane-space-xs);
  font-size: var(--vane-font-size-2xl);
  line-height: var(--vane-line-height-tight);
}

.product-summary {
  margin: 0 0 var(--vane-space-md);
  max-width: 760px;
  color: var(--vane-color-text-secondary);
  font-size: var(--vane-font-size-md);
}

.inquiry {
  margin-bottom: var(--vane-space-lg);
}

.cover {
  display: block;
  width: 100%;
  max-height: 480px;
  object-fit: cover;
  border-radius: var(--vane-radius-card);
  margin-bottom: var(--vane-space-lg);
}

.block-title {
  margin: 0 0 var(--vane-space-sm);
  font-size: var(--vane-font-size-lg);
  font-weight: var(--vane-font-weight-bold);
}

.features {
  margin-top: var(--vane-space-xl);
}

.feature-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--vane-space-md);
  list-style: none;
  margin: 0;
  padding: 0;
}

.feature {
  background: var(--vane-color-surface);
  border: 1px solid var(--vane-color-border);
  border-radius: var(--vane-radius-card);
  padding: var(--vane-space-md) var(--vane-space-lg);
}

.feature-title {
  margin: 0 0 var(--vane-space-2xs);
  font-size: var(--vane-font-size-md);
  font-weight: var(--vane-font-weight-medium);
}

.feature-description {
  margin: 0;
  color: var(--vane-color-text-secondary);
}

.detail {
  margin-top: var(--vane-space-xl);
}

.back {
  display: inline-block;
  margin-top: var(--vane-space-xl);
  color: var(--vane-color-brand);
  text-decoration: none;
}
</style>
