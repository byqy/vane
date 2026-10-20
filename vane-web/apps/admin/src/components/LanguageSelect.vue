<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { http, localeLabels, locales, type Locale } from '@vane/shared'
import { setLocale } from '../i18n'
import { useAuthStore } from '../stores/auth'

const { locale } = useI18n()
const auth = useAuthStore()

function change(value: Locale): void {
  setLocale(value)
  // 已登录时把偏好写到服务端，供登录后与邮件等异步场景使用；失败不打断界面
  if (auth.me) {
    void http.patch('/auth/me/preferences', { locale: value }).catch(() => undefined)
  }
}
</script>

<template>
  <el-select
    :model-value="locale as Locale"
    size="small"
    class="language-select"
    filterable
    @change="change"
  >
    <el-option v-for="item in locales" :key="item" :label="localeLabels[item]" :value="item" />
  </el-select>
</template>

<style scoped>
.language-select {
  width: 170px;
}
</style>

