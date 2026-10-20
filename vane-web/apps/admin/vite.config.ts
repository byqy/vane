import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  base: '/admin/',
  plugins: [
    vue(),
    // 按需引入 Element Plus（ADR-016）：模板里的 el-* 与 resolver 提供的 API 自动解析
    AutoImport({ resolvers: [ElementPlusResolver()], dts: false }),
    Components({ resolvers: [ElementPlusResolver()], dts: false })
  ],
  server: {
    port: 5174,
    // 启动即预转换入口与页面/组件，避免首次进入某个页面时才触发依赖发现与整页重载
    warmup: {
      clientFiles: [
        './src/main.ts',
        './src/App.vue',
        './src/router.ts',
        './src/views/*.vue',
        './src/components/*.vue'
      ]
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
