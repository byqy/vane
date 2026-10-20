<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { helpCommonKeys, helpKeys, helpLines, type HelpPage } from '@vane/shared'

/**
 * 页面标题旁的「?」入口：点击打开抽屉，说明这个页面是干什么的、怎么用。
 * 文案来自 @vane/shared 的 help.<page>.what / help.<page>.steps（20 种语言）。
 */
const props = defineProps<{ page: HelpPage; title: string }>()

const { t } = useI18n()
const visible = ref(false)
const keys = computed(() => helpKeys(props.page))
const what = computed(() => t(keys.value.what))
const steps = computed(() => helpLines(t(keys.value.steps)))
const openLabel = computed(() => t(helpCommonKeys.open))
</script>

<template>
  <button class="help-tip" type="button" :title="openLabel" :aria-label="openLabel" @click="visible = true">?</button>
  <el-drawer v-model="visible" :title="t(helpCommonKeys.title, { name: title })" size="420px">
    <section class="help-section">
      <h4>{{ t(helpCommonKeys.what) }}</h4>
      <p>{{ what }}</p>
    </section>
    <section class="help-section">
      <h4>{{ t(helpCommonKeys.steps) }}</h4>
      <ul>
        <li v-for="(line, index) in steps" :key="index">{{ line }}</li>
      </ul>
    </section>
  </el-drawer>
</template>

<style scoped>
.help-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  margin-inline-start: 6px;
  padding: 0;
  border: 1px solid var(--vane-color-brand);
  border-radius: 50%;
  background: transparent;
  color: var(--vane-color-brand);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  vertical-align: middle;
}

.help-tip:hover,
.help-tip:focus-visible {
  background: var(--vane-color-brand);
  color: #fff;
  outline: none;
}

.help-section + .help-section {
  margin-block-start: 20px;
}

.help-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #111827;
}

.help-section p {
  margin: 0;
  color: #374151;
  line-height: 1.7;
}

.help-section ul {
  margin: 0;
  padding-inline-start: 18px;
  color: #374151;
  line-height: 1.7;
}

.help-section li + li {
  margin-block-start: 6px;
}
</style>
