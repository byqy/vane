<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
</script>

<template>
  <div class="page">
    <div class="card">
      <h3 style="margin-top: 0">{{ t('placeholder.title') }}</h3>
      <p style="color: #6b7280">{{ t('placeholder.body', { path: route.path }) }}</p>
    </div>
  </div>
</template>

