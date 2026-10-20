import { createRouter, createWebHistory } from 'vue-router'
import { isLocale, type Locale } from '@vane/shared'
import { i18n, setLocale } from './i18n'
import HomeView from './views/HomeView.vue'
import PageView from './views/PageView.vue'
import ArticlesView from './views/ArticlesView.vue'
import ArticleView from './views/ArticleView.vue'

export const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    { path: '/', redirect: () => `/${i18n.global.locale.value}` },
    { path: '/:locale', name: 'home', component: HomeView },
    // 文章路由必须排在 /:locale/:slug 之前，避免 "articles" 被当成内容页 slug
    { path: '/:locale/articles', name: 'articles', component: ArticlesView },
    { path: '/:locale/articles/:slug', name: 'article', component: ArticleView },
    { path: '/:locale/categories/:code', name: 'category', component: ArticlesView },
    { path: '/:locale/:slug', name: 'page', component: PageView }
  ]
})

/** URL 是语言的唯一来源：进入路由时把 :locale 同步到界面语言。 */
router.beforeEach((to) => {
  const param = String(to.params.locale ?? '')
  if (isLocale(param)) {
    setLocale(param as Locale)
    return true
  }
  // 兼容没有语言前缀的旧链接：/about、/articles 等统一补上当前语言后重试
  const rest = to.fullPath.replace(/^\/[^/]+/, '')
  return `/${i18n.global.locale.value}${rest}`
})

export type { Locale }
