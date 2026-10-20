<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import en from 'element-plus/es/locale/lang/en'
import type { Locale } from '@vane/shared'
import { loadElementLocale } from './element-locales'

const { locale } = useI18n()

/** 先用英文占位，语言包异步加载完成后替换，避免切换时阻塞渲染。 */
const elementLocale = ref<Record<string, unknown>>(en)

async function sync(value: string): Promise<void> {
  elementLocale.value = await loadElementLocale(value as Locale)
}

void sync(locale.value)
watch(locale, (value) => void sync(value))
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>
