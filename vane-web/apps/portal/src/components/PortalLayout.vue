<script setup lang="ts">
import SiteHeader from './SiteHeader.vue'
import SiteFooter from './SiteFooter.vue'
</script>

<template>
  <div class="portal-layout">
    <SiteHeader />
    <main class="portal-main">
      <router-view />
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.portal-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--vane-color-bg);
}

.portal-main {
  flex: 1;
}
</style>
