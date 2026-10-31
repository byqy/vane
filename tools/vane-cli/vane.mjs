#!/usr/bin/env node
/**
 * vane CLI（ADR-030 P2）：给人与 Agent 共用的命令行入口，零依赖。
 *
 * 用法：
 *   node tools/vane-cli/vane.mjs capabilities
 *   node tools/vane-cli/vane.mjs events --since 2026-10-31T00:00:00Z --limit 20
 *   node tools/vane-cli/vane.mjs get /api/v1/admin/notices --query 'page[size]=5'
 *   node tools/vane-cli/vane.mjs post /api/v1/admin/notices --body '{"level":"INFO","pinned":false,...}'
 *   node tools/vane-cli/vane.mjs batch --file ops.json
 *   node tools/vane-cli/vane.mjs check
 *
 * 凭据：VANE_BASE_URL + VANE_USERNAME/VANE_PASSWORD，或直接 VANE_TOKEN。
 * 所有写操作默认自动生成 Idempotency-Key。
 */
import process from 'node:process'
import { readFileSync } from 'node:fs'

const BASE = (process.env.VANE_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
let token = process.env.VANE_TOKEN || null

function out(value) {
  process.stdout.write(JSON.stringify(value, null, 2) + '\n')
}

function die(message, code = 1) {
  process.stderr.write(`错误：${message}\n`)
  process.exit(code)
}

async function api(method, path, { query, body, headers } = {}) {
  const url = new URL(BASE + path)
  for (const [key, value] of Object.entries(query || {})) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value))
  }
  const init = { method, headers: { Accept: 'application/json', ...(headers || {}) } }
  if (token) init.headers.Authorization = `Bearer ${token}`
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  const response = await fetch(url, init)
  const text = await response.text()
  let parsed = text
  try {
    parsed = text ? JSON.parse(text) : null
  } catch {
    /* 保持原文 */
  }
  return { status: response.status, body: parsed }
}

async function login() {
  if (token) return
  const username = process.env.VANE_USERNAME
  const password = process.env.VANE_PASSWORD
  if (!username || !password) die('缺少凭据：设置 VANE_TOKEN 或 VANE_USERNAME/VANE_PASSWORD')
  const result = await api('POST', '/api/v1/auth/login', { body: { username, password } })
  if (result.status !== 200 || !result.body?.accessToken) die(`登录失败：${JSON.stringify(result.body)}`)
  token = result.body.accessToken
}

function parseArgs(argv) {
  const args = { _: [] }
  for (let i = 0; i < argv.length; i++) {
    const item = argv[i]
    if (item.startsWith('--')) {
      const [key, inline] = item.slice(2).split('=')
      if (inline !== undefined) args[key] = inline
      else if (argv[i + 1] && !argv[i + 1].startsWith('--')) args[key] = argv[++i]
      else args[key] = true
    } else {
      args._.push(item)
    }
  }
  return args
}

function explainProblem(body) {
  if (!body || typeof body !== 'object') return null
  if (!body.code) return null
  const hints = {
    RETRY: '原样重试即可',
    FIX_PARAMS: `修改参数后重发${body.field ? `（问题字段 ${body.field}）` : ''}`,
    ASK_USER: '停止并转人工确认',
    REFRESH_TOKEN: '令牌失效，重新登录后重试',
    CONFLICT: '重新读取当前状态后再决策',
  }
  return { code: body.code, action: body.action, hint: hints[body.action] || '未定义', traceId: body.traceId }
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const command = args._[0]
  await login()

  if (command === 'capabilities') {
    const result = await api('GET', '/api/v1/agent/capabilities')
    if (args.compact) {
      out({
        operations: result.body.operations.length,
        errorCatalog: result.body.errorCatalog.length,
        bySideEffect: result.body.operations.reduce((acc, op) => {
          acc[op.sideEffect] = (acc[op.sideEffect] || 0) + 1
          return acc
        }, {}),
      })
    } else {
      out(result.body)
    }
    return
  }

  if (command === 'events') {
    const result = await api('GET', '/api/v1/agent/events', {
      query: { since: args.since, until: args.until, cursor: args.after, limit: args.limit },
    })
    out({ status: result.status, ...result.body })
    if (result.status >= 400) process.exitCode = 1
    return
  }

  if (command === 'get' || command === 'post' || command === 'patch' || command === 'put' || command === 'delete') {
    const path = args._[1]
    if (!path) die('缺少路径，例如：get /api/v1/admin/notices')
    const query = {}
    if (args.query) {
      for (const pair of String(args.query).split('&')) {
        const [key, value] = pair.split('=')
        if (key) query[key] = value ?? ''
      }
    }
    const body = args.body ? JSON.parse(args.body) : args.file ? JSON.parse(readFileSync(args.file, 'utf8')) : undefined
    const needsKey = command !== 'get' && command !== 'delete'
    const headers = needsKey
      ? { 'Idempotency-Key': args.key || `cli-${crypto.randomUUID()}` }
      : undefined
    const result = await api(command.toUpperCase(), path, { query, body, headers })
    const payload = { status: result.status, body: result.body }
    if (result.status >= 400) {
      payload.action = result.body?.action
      payload.hint = explainProblem(result.body)
      process.exitCode = 1
    }
    out(payload)
    return
  }

  if (command === 'batch') {
    if (!args.file) die('需要 --file 指定批量请求 JSON（形如 {"operations":[...]}）')
    const payload = JSON.parse(readFileSync(args.file, 'utf8'))
    const result = await api('POST', '/api/v1/agent/batch', { body: payload })
    out({ status: result.status, ...result.body })
    if (result.status >= 400 || (result.body?.failed ?? 0) > 0) process.exitCode = 1
    return
  }

  if (command === 'check') {
    const checks = []
    const health = await api('GET', '/actuator/health')
    checks.push({ name: 'health', ok: health.status === 200 && health.body?.status === 'UP' })
    const me = await api('GET', '/api/v1/auth/me')
    checks.push({ name: 'auth', ok: me.status === 200, permissions: me.body?.permissions?.length ?? 0 })
    const cap = await api('GET', '/api/v1/agent/capabilities')
    checks.push({
      name: 'capabilities',
      ok: cap.status === 200 && (cap.body?.operations?.length ?? 0) > 0,
      operations: cap.body?.operations?.length ?? 0,
    })
    const events = await api('GET', '/api/v1/agent/events?limit=1')
    checks.push({ name: 'events', ok: events.status === 200 })
    const ok = checks.every((item) => item.ok)
    out({ base: BASE, ok, checks })
    if (!ok) process.exitCode = 1
    return
  }

  die(
    [
      '可用命令：',
      '  capabilities [--compact]',
      '  events [--since ISO] [--until ISO] [--after CURSOR] [--limit N]',
      '  get|post|patch|put|delete <path> [--query "a=1&b=2"] [--body JSON] [--file JSON] [--key IDEMPOTENCY]',
      '  batch --file ops.json',
      '  check',
    ].join('\n'),
  )
}

main().catch((error) => die(error?.stack || String(error)))
