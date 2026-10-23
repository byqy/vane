<script setup lang="ts">
defineProps<{
  title: string
  actionTo?: string | null
  actionText?: string | null
}>()
</script>

<template>
  <div class="section-heading">
    <h2 class="title">{{ title }}</h2>
    <router-link v-if="actionTo && actionText" class="action" :to="actionTo">{{ actionText }}</router-link>
  </div>
</template>

<style scoped>
.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--vane-space-md);
  margin-bottom: var(--vane-space-lg);
}

.title {
  margin: 0;
  font-size: var(--vane-font-size-xl);
  font-weight: var(--vane-font-weight-bold);
  color: var(--vane-color-text);
}

.action {
  color: var(--vane-color-brand);
  font-size: var(--vane-font-size-base);
  text-decoration: none;
  white-space: nowrap;
}

.action:hover {
  text-decoration: underline;
}
</style>
