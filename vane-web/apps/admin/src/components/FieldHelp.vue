<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { fieldHelpOpenKey, type FieldHelpKey } from '@vane/shared'

/**
 * 表单字段旁的「?」：点击弹出气泡，说明这个字段填什么、有什么坑。
 *
 * 与页面级 HelpTip 分工：HelpTip 讲整个页面怎么用，FieldHelp 只讲单个字段。
 * 文案键由 @vane/shared 的 fieldHelpKeys 约束，20 种语言在 locales/help/field/ 下维护。
 */
defineProps<{ help: FieldHelpKey }>()

const { t } = useI18n()
</script>

<template>
  <el-popover trigger="click" placement="top" :width="300">
    <template #reference>
      <button class="field-help" type="button" :aria-label="t(fieldHelpOpenKey)" :title="t(fieldHelpOpenKey)">
        ?
      </button>
    </template>
    <p class="field-help-text">{{ t(help) }}</p>
  </el-popover>
</template>

<style scoped>
.field-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  margin-inline-start: 4px;
  padding: 0;
  border: 1px solid #9ca3af;
  border-radius: 50%;
  background: transparent;
  color: #6b7280;
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  vertical-align: middle;
}

.field-help:hover,
.field-help:focus-visible {
  border-color: var(--vane-color-brand);
  background: var(--vane-color-brand);
  color: #fff;
  outline: none;
}

.field-help-text {
  margin: 0;
  color: #374151;
  font-size: 13px;
  line-height: 1.7;
}
</style>
