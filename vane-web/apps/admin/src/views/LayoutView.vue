<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { menuMessageKey, problemOf } from '@vane/shared'
import { useAuthStore } from '../stores/auth'
import LanguageSelect from '../components/LanguageSelect.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()

const groups = computed(() => {
  const menus = auth.menus
  const roots = menus.filter((m) => !m.parentCode)
  return roots
    .map((root) => ({ root, children: menus.filter((m) => m.parentCode === root.code) }))
    .filter((group) => group.children.length > 0)
})

const currentPath = computed(() => router.currentRoute.value.path)

async function ensureMe(): Promise<void> {
  if (auth.me) {
    return
  }
  try {
    await auth.loadMe()
  } catch (e) {
    const problem = problemOf(e)
    ElMessage.error(problem?.detail ?? t('app.userInfoFailed'))
  }
}

async function logout(): Promise<void> {
  await auth.logout()
  await router.replace('/login')
}

onMounted(ensureMe)
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">vane</div>
      <el-menu class="menu" :default-active="currentPath" router>
        <el-sub-menu v-for="group in groups" :key="group.root.code" :index="group.root.code">
          <template #title>{{ t(menuMessageKey(group.root.nameKey)) }}</template>
          <el-menu-item v-for="item in group.children" :key="item.code" :index="item.path ?? '/'">
            {{ t(menuMessageKey(item.nameKey)) }}
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="workspace">
      <el-header class="header" height="56px">
        <span class="title">{{ t('app.adminTitle') }}</span>
        <span class="spacer" />
        <LanguageSelect />
        <span class="user">{{ auth.me?.username ?? '—' }}</span>
        <el-button link type="primary" @click="logout">{{ t('app.logout') }}</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
/* 后台外壳固定为一屏：只有侧边栏与主内容区各自滚动，页面本身不滚动。 */
.layout {
  height: 100vh;
  overflow: hidden;
}

.aside {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border-inline-end: 1px solid #e5e7eb;
}

.brand {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  align-items: center;
  padding-inline-start: 20px;
  font-weight: 700;
  font-size: 18px;
  color: var(--vane-color-brand);
}

/* 菜单展开超出视口时只滚动侧边栏 */
.menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-inline-end: none;
}

.workspace {
  min-width: 0;
  height: 100%;
}

.header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.title {
  font-weight: 600;
}

.spacer {
  flex: 1;
}

.user {
  color: #4b5563;
}

/* 主内容区是唯一纵向滚动容器；内边距交给页面级 .page */
.main {
  min-height: 0;
  padding: 0;
  overflow: auto;
}
</style>
