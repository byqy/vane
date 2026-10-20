import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
// 函数式组件与指令的样式需显式引入（组件与 resolver 解析的部分由插件自动带样式）
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'
import App from './App.vue'
import { router } from './router'
import { i18n } from './i18n'
import './style.css'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
app.use(router)
// 指令不会被组件解析器处理，v-loading 需要手动注册
app.directive('loading', ElLoading.directive)
app.mount('#app')
