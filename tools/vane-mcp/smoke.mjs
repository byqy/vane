#!/usr/bin/env node
/**
 * vane MCP server 冒烟测试：验证协议握手、工具生成与一次只读调用。
 *
 * 用法（需要后端在跑、且环境变量已配好凭据）：
 *   node tools/vane-mcp/smoke.mjs
 */
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'

const here = path.dirname(fileURLToPath(import.meta.url))
const serverPath = path.join(here, 'server.mjs')

function fail(message) {
  process.stderr.write(`❌ ${message}\n`)
  process.exit(1)
}

const child = spawn(process.execPath, [serverPath], {
  stdio: ['pipe', 'pipe', 'inherit'],
  env: process.env,
})

const responses = new Map()
let buffer = ''
child.stdout.on('data', (chunk) => {
  buffer += chunk.toString('utf8')
  let index
  while ((index = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, index).trim()
    buffer = buffer.slice(index + 1)
    if (!line) continue
    try {
      const message = JSON.parse(line)
      responses.set(message.id, message)
    } catch {
      fail(`无法解析服务端响应：${line.slice(0, 160)}`)
    }
  }
})

function send(message) {
  child.stdin.write(JSON.stringify(message) + '\n')
}

function waitFor(id, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const started = Date.now()
    const timer = setInterval(() => {
      if (responses.has(id)) {
        clearInterval(timer)
        resolve(responses.get(id))
      } else if (Date.now() - started > timeoutMs) {
        clearInterval(timer)
        reject(new Error(`等待响应 ${id} 超时`))
      }
    }, 30)
  })
}

async function main() {
  send({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2025-06-18' } })
  const init = await waitFor(1)
  if (init.result?.serverInfo?.name !== 'vane-mcp') fail('initialize 响应缺少 serverInfo')

  send({ jsonrpc: '2.0', method: 'notifications/initialized' })
  send({ jsonrpc: '2.0', id: 2, method: 'tools/list' })
  const list = await waitFor(2)
  const tools = list.result?.tools || []
  if (tools.length === 0) fail('工具列表为空')
  for (const required of ['vane_capabilities', 'vane_request', 'vane_events', 'vane_batch']) {
    if (!tools.some((tool) => tool.name === required)) fail(`缺少内置工具 ${required}`)
  }
  for (const tool of tools) {
    if (!tool.inputSchema || tool.inputSchema.type !== 'object') fail(`工具 ${tool.name} 缺少 inputSchema`)
  }

  send({ jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'vane_capabilities', arguments: {} } })
  const call = await waitFor(3)
  if (call.result?.isError) fail(`vane_capabilities 返回错误：${call.result.content?.[0]?.text}`)
  const payload = JSON.parse(call.result.content[0].text)
  const manifest = payload.body || {}
  if (!(manifest.operations?.length > 0)) fail('能力清单里没有操作')
  if (!(manifest.errorCatalog?.length > 0)) fail('错误码目录为空')

  process.stdout.write(
    `✅ MCP 冒烟通过：工具 ${tools.length} 个，操作 ${manifest.operations.length} 个，错误码 ${manifest.errorCatalog.length} 个\n`,
  )
  child.stdin.end()
  child.kill()
}

main().catch((error) => {
  child.kill()
  fail(error?.message || String(error))
})
