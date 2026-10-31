#!/usr/bin/env node
/**
 * vane MCP server（ADR-030 P2）：把 vane 的能力清单暴露成 MCP tools。
 *
 * 设计约束：
 *  - 零依赖：只用 Node 标准库实现 MCP 的 stdio JSON-RPC，避免引入未登记依赖。
 *  - 工具由 `GET /api/v1/agent/capabilities` 动态生成，不手写清单，避免与后端漂移。
 *  - 只暴露当前账号**有权限**的操作；权限不足的操作不出现，而不是出现后报 403。
 *
 * 环境变量：
 *  VANE_BASE_URL       默认 http://127.0.0.1:8080
 *  VANE_USERNAME / VANE_PASSWORD  服务账号凭据
 *  VANE_TOKEN          也可直接给 access token（优先于用户名密码）
 */
import process from 'node:process'
import readline from 'node:readline'

const BASE = (process.env.VANE_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
const SERVER_INFO = { name: 'vane-mcp', version: '0.1.0' }
const PROTOCOL_VERSION = '2025-06-18'

let token = process.env.VANE_TOKEN || null
/** 工具调用串行化，保证响应顺序稳定。 */
let queue = Promise.resolve()
let permissions = new Set()
let operations = []
let errorCatalog = []

function log(message) {
  // MCP 用 stdout 传协议帧，日志必须走 stderr
  process.stderr.write(`[vane-mcp] ${message}\n`)
}

async function api(method, path, { query, body, headers } = {}) {
  const url = new URL(BASE + path)
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) url.searchParams.set(key, String(value))
    }
  }
  const init = { method, headers: { Accept: 'application/json', ...(headers || {}) } }
  if (token) init.headers.Authorization = `Bearer ${token}`
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  let response
  try {
    response = await fetch(url, init)
  } catch (error) {
    return { status: 0, body: { code: 'NETWORK', action: 'RETRY', detail: String(error) } }
  }
  const text = await response.text()
  let parsed = null
  if (text) {
    try {
      parsed = JSON.parse(text)
    } catch {
      parsed = text
    }
  }
  return { status: response.status, body: parsed, headers: response.headers }
}

async function login() {
  if (token) return
  const username = process.env.VANE_USERNAME
  const password = process.env.VANE_PASSWORD
  if (!username || !password) {
    throw new Error('缺少凭据：设置 VANE_TOKEN，或 VANE_USERNAME + VANE_PASSWORD')
  }
  const result = await api('POST', '/api/v1/auth/login', { body: { username, password } })
  if (result.status !== 200 || !result.body?.accessToken) {
    throw new Error(`登录失败：HTTP ${result.status} ${JSON.stringify(result.body)}`)
  }
  token = result.body.accessToken
}

async function refreshIfNeeded(result) {
  if (result.status !== 401) return result
  // access 令牌过期：重新登录一次再重试
  token = null
  await login()
  return result
}

async function loadCapabilities() {
  const me = await api('GET', '/api/v1/auth/me')
  permissions = new Set(me.body?.permissions || [])
  const cap = await api('GET', '/api/v1/agent/capabilities')
  if (cap.status !== 200) throw new Error(`读取能力清单失败：HTTP ${cap.status}`)
  operations = cap.body.operations || []
  errorCatalog = cap.body.errorCatalog || []
  log(`已加载 ${operations.length} 个操作 / ${errorCatalog.length} 个错误码 / 权限 ${permissions.size} 个`)
}

/** 把路径与方法转成 MCP 工具名（只允许 [a-zA-Z0-9_-]）。 */
function toolName(op) {
  const path = op.path
    .replace(/^\/api\/v1\//, '')
    .replace(/\{([^}]+)\}/g, 'by_$1')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
  return `${op.method.toLowerCase()}_${path}`.slice(0, 64)
}

function canCall(op) {
  return !op.permissionCode || permissions.has(op.permissionCode)
}

/** 由路径模板生成 path 参数说明。 */
function pathParams(op) {
  return [...op.path.matchAll(/\{([^}]+)\}/g)].map((m) => m[1])
}

function buildTools() {
  const tools = [
    {
      name: 'vane_capabilities',
      description: '读取 vane 能力清单：可用操作、所需权限码、副作用、幂等性与错误码目录。开工前先调用它。',
      inputSchema: { type: 'object', properties: {}, additionalProperties: false },
    },
    {
      name: 'vane_events',
      description:
        '读取审计事件投影（谁在何时做了什么）。支持 since/until 时间窗与游标分页；只含排障字段，不含请求参数。',
      inputSchema: {
        type: 'object',
        properties: {
          since: { type: 'string', description: '起始时间，ISO 8601（含）' },
          until: { type: 'string', description: '结束时间，ISO 8601（含）' },
          after: { type: 'string', description: '上一页返回的 page.after' },
          limit: { type: 'integer', description: '每页条数，默认 50，最大 200' },
        },
        additionalProperties: false,
      },
    },
    {
      name: 'vane_batch',
      description:
        '批量提交写操作（最多 20 项），逐项返回 { id, status, body, action }。适合一次创建多条内容；单项失败不影响其余项。',
      inputSchema: {
        type: 'object',
        properties: {
          operations: {
            type: 'array',
            description: '操作列表',
            items: {
              type: 'object',
              properties: {
                id: { type: 'string' },
                method: { type: 'string', enum: ['POST', 'PATCH', 'PUT', 'DELETE'] },
                path: { type: 'string', description: '以 /api/v1/ 开头' },
                body: { type: 'object' },
                headers: { type: 'object', additionalProperties: { type: 'string' } },
              },
              required: ['method', 'path'],
            },
          },
        },
        required: ['operations'],
        additionalProperties: false,
      },
    },
    {
      name: 'vane_request',
      description:
        '直接调用任意 vane 接口。请求与响应结构查能力清单里的 openApiRef 或 /v3/api-docs。写操作自动带 Idempotency-Key（可用 idempotencyKey 指定）。',
      inputSchema: {
        type: 'object',
        properties: {
          method: { type: 'string', enum: ['GET', 'POST', 'PATCH', 'PUT', 'DELETE'] },
          path: { type: 'string', description: '例如 /api/v1/admin/notices' },
          query: { type: 'object', description: '查询参数' },
          body: { type: 'object', description: '请求体' },
          idempotencyKey: { type: 'string', description: '写操作的幂等键（8-128 位）' },
        },
        required: ['method', 'path'],
        additionalProperties: false,
      },
    },
  ]

  for (const op of operations) {
    if (!canCall(op) || op.path.startsWith('/api/v1/agent/')) continue
    const params = pathParams(op)
    const schema = {
      type: 'object',
      properties: {
        body: { type: 'object', description: '请求体（结构见 OpenAPI）' },
        query: { type: 'object', description: '查询参数' },
        idempotencyKey: { type: 'string', description: '幂等键；POST 建议必填' },
        headers: { type: 'object', additionalProperties: { type: 'string' } },
      },
      additionalProperties: false,
    }
    for (const name of params) {
      schema.properties[name] = { type: 'string', description: `路径参数 ${name}` }
    }
    const permission = op.permissionCode ? `权限 ${op.permissionCode}` : '无需权限码（需登录）'
    tools.push({
      name: toolName(op),
      description: `${op.method} ${op.path} · ${permission} · 副作��� ${op.sideEffect} · 幂等 ${op.idempotency}`,
      inputSchema: schema,
    })
  }
  return tools
}

function fillPath(template, args) {
  return template.replace(/\{([^}]+)\}/g, (_, name) => {
    const value = args[name]
    if (value === undefined) throw new Error(`缺少路径参数 ${name}`)
    return encodeURIComponent(String(value))
  })
}

function formatResult(status, body, extra = {}) {
  const payload = { status, ...extra, body }
  const isError = status >= 400
  let hint = ''
  if (isError && body && typeof body === 'object' && body.action) {
    hint = `\n\n恢复指令 action=${body.action}（${body.code || '未知错误码'}）`
    if (body.action === 'RETRY') hint += '：原样重试即可'
    else if (body.action === 'FIX_PARAMS') hint += `：修改参数后重发${body.field ? `，问题字段 ${body.field}` : ''}`
    else if (body.action === 'ASK_USER') hint += '：停止并转人工确认'
    else if (body.action === 'REFRESH_TOKEN') hint += '：令牌失效，本服务已尝试重新登录'
    else if (body.action === 'CONFLICT') hint += '：重新读取当前状态后再决策'
  }
  return {
    content: [{ type: 'text', text: JSON.stringify(payload, null, 2) + hint }],
    isError,
  }
}

async function callTool(name, args = {}) {
  if (name === 'vane_capabilities') {
    return formatResult(200, { operations, errorCatalog, permissions: [...permissions] })
  }
  if (name === 'vane_events') {
    const result = await api('GET', '/api/v1/agent/events', {
      query: { since: args.since, until: args.until, cursor: args.after, limit: args.limit },
    })
    return formatResult(result.status, result.body)
  }
  if (name === 'vane_batch') {
    const result = await api('POST', '/api/v1/agent/batch', { body: { operations: args.operations || [] } })
    return formatResult(result.status, result.body)
  }
  if (name === 'vane_request') {
    const needsKey = args.method !== 'GET' && args.method !== 'DELETE'
    const headers = needsKey
      ? { 'Idempotency-Key': args.idempotencyKey || `mcp-${crypto.randomUUID()}` }
      : args.idempotencyKey
        ? { 'Idempotency-Key': args.idempotencyKey }
        : undefined
    const result = await api(args.method, fillPath(args.path, args), {
      query: args.query,
      body: args.body,
      headers: needsKey && !args.idempotencyKey ? undefined : headers,
    })
    return formatResult(result.status, result.body)
  }
  const op = operations.find((candidate) => toolName(candidate) === name)
  if (!op) throw new Error(`未知工具 ${name}`)
  if (!canCall(op)) throw new Error(`当前账号缺少权限码 ${op.permissionCode}`)
  const headers = { ...(args.headers || {}) }
  if (op.idempotency === 'KEY') {
    headers['Idempotency-Key'] = args.idempotencyKey || `mcp-${crypto.randomUUID()}`
  }
  const result = await api(op.method, fillPath(op.path, args), {
    query: args.query,
    body: args.body,
    headers,
  })
  return formatResult(result.status, result.body, { operation: `${op.method} ${op.path}` })
}

/** 工具层失败（未知工具、缺少权限、参数不合法）：作为错误结果返回，不中断会话。 */
function failureResult(error) {
  return {
    content: [{ type: 'text', text: `工具调用失败：${error?.message || error}` }],
    isError: true,
  }
}

function send(message) {
  process.stdout.write(JSON.stringify(message) + '\n')
}

function handleRequest(request) {
  const { id, method, params } = request
  try {
    if (method === 'initialize') {
      send({
        jsonrpc: '2.0',
        id,
        result: {
          protocolVersion: params?.protocolVersion || PROTOCOL_VERSION,
          capabilities: { tools: {} },
          serverInfo: SERVER_INFO,
        },
      })
      return
    }
    if (method === 'notifications/initialized') return
    if (method === 'ping') {
      send({ jsonrpc: '2.0', id, result: {} })
      return
    }
    if (method === 'tools/list') {
      send({ jsonrpc: '2.0', id, result: { tools: buildTools() } })
      return
    }
    if (method === 'tools/call') {
      // 串行处理：保持响应顺序与请求一致，便于人工与脚本排查
      queue = queue.then(async () => {
        try {
          const result = await callTool(params?.name, params?.arguments)
          send({ jsonrpc: '2.0', id, result })
        } catch (error) {
          send({ jsonrpc: '2.0', id, result: failureResult(error) })
        }
      })
      return
    }
    send({ jsonrpc: '2.0', id, error: { code: -32601, message: `不支持的方法 ${method}` } })
  } catch (error) {
    send({ jsonrpc: '2.0', id, error: { code: -32603, message: String(error) } })
  }
}

async function main() {
  await login()
  await loadCapabilities()
  const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity })
  rl.on('line', (line) => {
    const trimmed = line.trim()
    if (!trimmed) return
    let request
    try {
      request = JSON.parse(trimmed)
    } catch {
      log(`无法解析的输入：${trimmed.slice(0, 120)}`)
      return
    }
    handleRequest(request)
  })
}

main().catch((error) => {
  log(`启动失败：${error?.message || error}`)
  process.exit(1)
})
