#!/usr/bin/env node
/**
 * 本地启动后端 jar（供 IDE 运行配置调用）。
 *
 * 用途：让后端也能像前端一样通过 IDE/MCP 运行配置启动，环境变量集中在这里，
 * 不依赖终端手工 export。生产部署不走这个脚本。
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(here, '..')
const serverRoot = resolve(webRoot, '..', 'vane-server')
const jar = join(serverRoot, 'vane-bootstrap', 'target', 'vane-bootstrap-0.1.0-SNAPSHOT.jar')

if (!existsSync(jar)) {
  console.error(`[dev:api] 未找到 jar：${jar}`)
  console.error('[dev:api] 先执行：cd vane-server; .\\mvnw.cmd -B -DskipTests package')
  process.exit(1)
}

const javaHome = process.env.JAVA_HOME ?? 'C:\\Users\\byqy\\.jdks\\temurin-25.0.3'
const java = join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')

const env = {
  ...process.env,
  DB_URL: process.env.DB_URL ?? 'jdbc:postgresql://localhost:55432/vane',
  DB_USERNAME: process.env.DB_USERNAME ?? 'postgres',
  DB_PASSWORD: process.env.DB_PASSWORD ?? 'postgres',
  AUTH_COOKIE_SECURE: process.env.AUTH_COOKIE_SECURE ?? 'false',
  FILE_ROOT: process.env.FILE_ROOT ?? join(process.env.TEMP ?? '.', 'vane-files')
}

console.log(`[dev:api] java: ${java}`)
console.log(`[dev:api] jar : ${jar}`)
console.log(`[dev:api] db  : ${env.DB_URL}`)
console.log('[dev:api] 健康检查: http://localhost:8080/actuator/health')

const child = spawn(java, ['-jar', jar], { cwd: serverRoot, env, stdio: 'inherit' })

child.on('exit', (code, signal) => {
  if (signal) {
    console.log(`[dev:api] 已退出（signal=${signal}）`)
  } else {
    console.log(`[dev:api] 已退出（code=${code}）`)
  }
  process.exit(code ?? 0)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal))
}
