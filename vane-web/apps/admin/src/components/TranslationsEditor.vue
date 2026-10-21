<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { locales, languageName, type Locale } from '@vane/shared'

import type { TranslationField, TranslationRow } from './translation-types'

const props = defineProps<{
  modelValue: TranslationRow[]
  fields: TranslationField[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: TranslationRow[]]
}>()

const { t, locale } = useI18n()
const adding = ref<Locale | ''>('')

const rows = computed(() => props.modelValue)

const available = computed(() =>
  locales.filter((locale) => !rows.value.some((row) => row.locale === locale))
)

function labelOf(code: string): string {
  return languageName(code, locale.value)
}

function addLocale(): void {
  if (!adding.value) {
    return
  }
  const row: TranslationRow = { locale: adding.value }
  for (const field of props.fields) {
    row[field.key] = ''
  }
  emit('update:modelValue', [...rows.value, row])
  adding.value = ''
}

function removeLocale(locale: string): void {
  emit('update:modelValue', rows.value.filter((row) => row.locale !== locale))
}

function updateField(locale: string, field: string, value: string): void {
  emit(
    'update:modelValue',
    rows.value.map((row) => (row.locale === locale ? { ...row, [field]: value } : row))
  )
}
</script>

<template>
  <div class="translations-editor">
    <el-collapse>
      <el-collapse-item v-for="row in rows" :key="row.locale" :name="row.locale">
        <template #title>
          <span class="locale-title">{{ labelOf(row.locale) }}</span>
          <el-button
            v-if="rows.length > 1"
            link
            type="danger"
            size="small"
            style="margin-inline-start: 12px"
            @click.stop="removeLocale(row.locale)"
          >
            {{ t('common.delete') }}
          </el-button>
        </template>
        <el-form label-width="110px">
          <el-form-item v-for="field in fields" :key="field.key" :label="t(field.labelKey)">
            <el-input
              v-if="field.textarea"
              :model-value="row[field.key] ?? ''"
              type="textarea"
              :rows="3"
              @update:model-value="(value: string) => updateField(row.locale, field.key, value)"
            />
            <el-input
              v-else
              :model-value="row[field.key] ?? ''"
              @update:model-value="(value: string) => updateField(row.locale, field.key, value)"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>

    <div class="add-row">
      <el-select v-model="adding" :placeholder="t('common.addLanguage')" style="width: 200px">
        <el-option v-for="locale in available" :key="locale" :label="labelOf(locale)" :value="locale" />
      </el-select>
      <el-button :disabled="!adding" @click="addLocale">{{ t('common.addLanguage') }}</el-button>
    </div>
  </div>
</template>

<style scoped>
.locale-title {
  font-weight: 600;
}

.add-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>


