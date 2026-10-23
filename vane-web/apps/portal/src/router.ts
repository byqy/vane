import { createRouter, createWebHistory } from 'vue-router'
import { isLocale, type Locale } from '@vane/shared'
import { i18n, setLocale } from './i18n'
import PortalLayout from './components/PortalLayout.vue'

/**
 * 门户路由：/{locale}/…（ADR-017：语言是 URL 的唯一来源）。
 * 所有页面挂在 PortalLayout 下，共享页头与页脚；页面组件懒加载，首屏只带布局与首页。
 */
export const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    { path: '/', redirect: () => `/${i18n.global.locale.value}` },
    {
      path: '/:locale',
      component: PortalLayout,
      children: [
        { path: '', name: 'home', component: () => import('./views/HomeView.vue') },
        { path: 'about', name: 'about', component: () => import('./views/AboutView.vue') },
        { path: 'products', name: 'products', component: () => import('./views/ProductsView.vue') },
        { path: 'products/:slug', name: 'product', component: () => import('./views/ProductDetailView.vue') },
        { path: 'contact', name: 'contact', component: () => import('./views/ContactView.vue') },
        // 文章路由必须排在 :slug 之前，避免 "articles" 被当成内容页 slug
        { path: 'articles', name: 'articles', component: () => import('./views/ArticlesView.vue') },
        { path: 'articles/:slug', name: 'article', component: () => import('./views/ArticleView.vue') },
        { path: 'categories/:code', name: 'category', component: () => import('./views/ArticlesView.vue') },
        { path: ':slug', name: 'page', component: () => import('./views/PageView.vue') }
      ]
    }
  ]
})

/** URL 是语言的唯一来源：进入路由时把 :locale 同步到界面语言。 */
router.beforeEach((to) => {
  const param = String(to.params.locale ?? '')
  if (isLocale(param)) {
    setLocale(param as Locale)
    return true
  }
  // 兼容没有语言前缀的旧链接（/about、/products/alpha 等）：补上当前语言后重试
  const path = to.fullPath.startsWith('/') ? to.fullPath : `/${to.fullPath}`
  return `/${i18n.global.locale.value}${path}`
})

export type { Locale }
