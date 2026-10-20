import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // 测试里也按需解析 el-*，保持与运行时一致
    Components({ resolvers: [ElementPlusResolver()], dts: false })
  ],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
    restoreMocks: true,
    // element-plus 组件样式是 .css 导入，必须走 vite 转换而不是 Node 原生加载
    server: { deps: { inline: ['element-plus'] } }
  }
})
