import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/',
  plugins: [vue()],
  server: {
    // Windows 下只监听 ::1 会让 127.0.0.1 的连接等待约 2 秒后失败；监听 :: 同时覆盖 IPv4/IPv6 回环
    host: '::',
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/sitemap.xml': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/robots.txt': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})

