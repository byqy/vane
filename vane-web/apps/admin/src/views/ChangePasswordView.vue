<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LanguageSelect from '../components/LanguageSelect.vue'
import { http, problemOf } from '@vane/shared'
import { useAuthStore } from '../stores/auth'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  error.value = null
  if (form.newPassword !== form.confirmPassword) {
    error.value = t('chpass.mismatch')
    return
  }
  submitting.value = true
  try {
    await http.post('/auth/password', {
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    ElMessage.success(t('chpass.success'))
    await auth.logout()
    await router.replace({ name: 'login' })
  } catch (e) {
    const problem = problemOf(e)
    error.value = problem?.detail ?? t('chpass.failed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="chpass-wrap">
      <div class="lang-bar">
        <LanguageSelect />
      </div>
    <el-card class="chpass-card">
      <h2>{{ t('chpass.title') }}</h2>
      <p class="hint">{{ t('chpass.hint') }}</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item :label="t('chpass.current')">
          <el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item :label="t('chpass.new')">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item :label="t('chpass.confirm')">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting" style="width: 100%" @click="submit">
          {{ t('chpass.submit') }}
        </el-button>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-top: 12px" />
    </el-card>
  </div>
</template>

<style scoped>
.chpass-wrap {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
}

.chpass-card {
  width: 380px;
}

.hint {
  color: #6b7280;
  font-size: 13px;
  margin-top: -8px;
}

  .lang-bar {
    position: absolute;
    inset-block-start: 20px;
    inset-inline-end: 24px;
  }
</style>



