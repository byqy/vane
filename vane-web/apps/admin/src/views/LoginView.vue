<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { problemOf } from '@vane/shared'
import { useAuthStore } from '../stores/auth'
import LanguageSelect from '../components/LanguageSelect.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({ username: 'admin', password: '' })
const submitting = ref(false)
const error = ref<string | null>(null)

async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    await auth.login(form.username, form.password)
    if (auth.me?.mustChangePassword) {
      await router.replace({ name: 'changePassword' })
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    const problem = problemOf(e)
    error.value = problem
      ? `${problem.detail ?? problem.title}（${problem.code ?? problem.status}）`
      : t('login.failed')
    ElMessage.error(error.value)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
      <div class="lang-bar">
        <LanguageSelect />
      </div>
    <el-card class="login-card">
      <h2>{{ t('app.title') }}</h2>
      <p class="hint">{{ t('app.hint') }}</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item :label="t('login.username')">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item :label="t('login.password')">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting" style="width: 100%" @click="submit">
          {{ t('login.submit') }}
        </el-button>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" style="margin-top: 12px" />
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 360px;
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




