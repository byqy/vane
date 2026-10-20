import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/',
  plugins: [vue()],
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/sitemap.xml': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/robots.txt': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

