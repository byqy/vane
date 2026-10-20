import { createRouter, createWebHistory } from 'vue-router'
import { tokenStore, http } from '@vane/shared'
import { useAuthStore } from './stores/auth'
import { createAuthGuard } from './router-guard'
import LoginView from './views/LoginView.vue'
import ChangePasswordView from './views/ChangePasswordView.vue'
import LayoutView from './views/LayoutView.vue'
import UsersView from './views/UsersView.vue'
import NoticesView from './views/NoticesView.vue'
import RolesView from './views/RolesView.vue'
import DeptsView from './views/DeptsView.vue'
import OperLogsView from './views/OperLogsView.vue'
import LoginLogsView from './views/LoginLogsView.vue'
import ConfigsView from './views/ConfigsView.vue'
import DictsView from './views/DictsView.vue'
import PagesView from './views/PagesView.vue'
import JobsView from './views/JobsView.vue'
import NavsView from './views/NavsView.vue'
import BannersView from './views/BannersView.vue'
import PermissionsView from './views/PermissionsView.vue'
import HealthView from './views/HealthView.vue'
import FilesView from './views/FilesView.vue'
import MailView from './views/MailView.vue'
import PlaceholderView from './views/PlaceholderView.vue'
import SmsView from './views/SmsView.vue'

export const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/change-password', name: 'changePassword', component: ChangePasswordView },
    {
      path: '/',
      component: LayoutView,
      children: [
        { path: '', redirect: '/system/user' },
        { path: 'system/user', name: 'users', component: UsersView },
        { path: 'system/role', name: 'roles', component: RolesView },
        { path: 'system/dept', name: 'depts', component: DeptsView },
        { path: 'system/config', name: 'configs', component: ConfigsView },
        { path: 'system/dict', name: 'dicts', component: DictsView },
        { path: 'audit/oper-log', name: 'operLogs', component: OperLogsView },
        { path: 'audit/login-log', name: 'loginLogs', component: LoginLogsView },
        { path: 'content/notice', name: 'notices', component: NoticesView },
        { path: 'content/page', name: 'pages', component: PagesView },
        {
          path: 'content/article',
          name: 'articles',
          component: () => import('./views/ArticlesView.vue')
        },
        {
          path: 'content/category',
          name: 'categories',
          component: () => import('./views/CategoriesView.vue')
        },
        {
          path: 'content/tag',
          name: 'tags',
          component: () => import('./views/TagsView.vue')
        },
        { path: 'content/nav', name: 'navs', component: NavsView },
        { path: 'content/banner', name: 'banners', component: BannersView },
        { path: 'system/permission', name: 'permissions', component: PermissionsView },
        { path: 'ops/job', name: 'jobs', component: JobsView },
        { path: 'ops/file', name: 'files', component: FilesView },
        { path: 'ops/health', name: 'health', component: HealthView },
        { path: 'ops/mail', name: 'mail', component: MailView },
  { path: 'ops/sms', name: 'sms', component: SmsView },
        { path: ':pathMatch(.*)*', component: PlaceholderView }
      ]
    }
  ]
})

const guard = createAuthGuard({
  tokenStore,
  refresh: async () => {
    const { data } = await http.post('/auth/refresh')
    return data.accessToken
  },
  getMe: () => useAuthStore().me,
  loadMe: () => useAuthStore().loadMe()
})

router.beforeEach(guard)


