<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { PublicProductView } from '@vane/shared'
import { fileUrl } from '../api'

defineProps<{ product: PublicProductView }>()

const { locale } = useI18n()
</script>

<template>
  <router-link class="product-card" :to="`/${locale}/products/${product.slug}`">
    <div class="cover">
      <img v-if="product.coverFileId" :src="fileUrl(product.coverFileId)" :alt="product.name" loading="lazy" />
      <span v-else class="cover-placeholder" aria-hidden="true">{{ product.name.slice(0, 1) }}</span>
    </div>
    <div class="body">
      <p v-if="product.group" class="group">{{ product.group }}</p>
      <h3 class="name">{{ product.name }}</h3>
      <p v-if="product.summary" class="summary">{{ product.summary }}</p>
    </div>
  </router-link>
</template>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--vane-color-surface);
  border: 1px solid var(--vane-color-border);
  border-radius: var(--vane-radius-card);
  overflow: hidden;
  text-decoration: none;
  color: var(--vane-color-text);
  box-shadow: var(--vane-shadow-sm);
  transition: transform var(--vane-transition-fast), box-shadow var(--vane-transition-fast);
}

.product-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--vane-shadow-card);
}

.cover {
  aspect-ratio: 16 / 9;
  background: var(--vane-color-surface-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-placeholder {
  font-size: var(--vane-font-size-2xl);
  font-weight: var(--vane-font-weight-bold);
  color: var(--vane-color-brand);
}

.body {
  padding: var(--vane-space-md) var(--vane-space-lg) var(--vane-space-lg);
}

.group {
  margin: 0 0 var(--vane-space-2xs);
  font-size: var(--vane-font-size-xs);
  color: var(--vane-color-brand);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.name {
  margin: 0 0 var(--vane-space-xs);
  font-size: var(--vane-font-size-md);
  font-weight: var(--vane-font-weight-bold);
}

.summary {
  margin: 0;
  color: var(--vane-color-text-secondary);
  font-size: var(--vane-font-size-base);
}
</style>
