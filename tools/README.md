# tools —— 面向 Agent 的本地工具

两个零依赖的 Node 脚本，直接调用 vane 的 HTTP API（ADR-030 P1/P2 交付物）：

| 工具 | 用途 |
|---|---|
| `vane-cli/` | 命令行：查能力清单、读审计事件、发任意请求、批量提交、自检 |
| `vane-mcp/` | MCP server（stdio）：把能力清单动态暴露成 MCP tools，供任何 MCP 客户端使用 |

两者都**不写死接口清单**：工具列表与操作说明来自运行时的 `GET /api/v1/agent/capabilities`，权限不足的操作不会出现，避免与后端漂移。

## 共同配置

| 环境变量 | 说明 |
|---|---|
| `VANE_BASE_URL` | 后端地址，默认 `http://127.0.0.1:8080` |
| `VANE_USERNAME` / `VANE_PASSWORD` | 服务账号凭据（推荐，最小权限） |
| `VANE_TOKEN` | 或直接给 access token（优先于用户名密码） |

服务账号建议使用**服务角色**（`iam_role.service = true`），权限码只给内容运营所需，**不含任何删除权限**。

```powershell
$env:VANE_BASE_URL='http://127.0.0.1:8080'
$env:VANE_USERNAME='agent-ops'
$env:VANE_PASSWORD='<从凭据文件读取>'
```

## CLI

```powershell
node tools/vane-cli/vane.mjs check                                  # 只读自检（健康 / 登录 / 能力清单 / 事件）
node tools/vane-cli/vane.mjs capabilities --compact                 # 能力清单统计
node tools/vane-cli/vane.mjs events --limit 20                      # 最近审计事件（游标分页）
node tools/vane-cli/vane.mjs events --since 2026-10-31T00:00:00Z    # 时间窗
node tools/vane-cli/vane.mjs get /api/v1/admin/notices --query 'page[size]=5'
node tools/vane-cli/vane.mjs post /api/v1/admin/notices --body '{"level":"INFO","pinned":false,"translations":[{"locale":"zh-CN","title":"标题","body":"正文"}]}'
node tools/vane-cli/vane.mjs batch --file ops.json                  # 逐项结果，失败项带 action
```

要点：

- 写操作默认自动生成 `Idempotency-Key`，重试不会重复创建；可用 `--key` 指定以复用同一次操作。
- 失败时输出 `status`、`body`（Problem Details）、`action` 与中文 `hint`，并返回非零退出码。
- `events` 支持 `--after` 传上一页的 `page.after`。

## MCP server

```powershell
node tools/vane-mcp/smoke.mjs        # 冒烟：握手 + 工具生成 + 一次只读调用
node tools/vane-mcp/server.mjs       # 由 MCP 客户端以 stdio 方式拉起
```

MCP 客户端配置示例（以通用 JSON 配置为例，字段名随客户端而变）：

```json
{
  "mcpServers": {
    "vane": {
      "command": "node",
      "args": ["D:/path/to/vane/tools/vane-mcp/server.mjs"],
      "env": {
        "VANE_BASE_URL": "http://127.0.0.1:8080",
        "VANE_USERNAME": "agent-ops",
        "VANE_PASSWORD": "<password>"
      }
    }
  }
}
```

暴露的工具：

| 工具 | 说明 |
|---|---|
| `vane_capabilities` | 读取完整能力清单（操作、权限码、副作用、幂等性、错误码目录） |
| `vane_events` | 审计事件投影（`since`/`until`/`after`/`limit`） |
| `vane_batch` | 批量写操作，逐项结果 |
| `vane_request` | 任意请求（自动带幂等键） |
| `<method>_<path>` | 由能力清单生成的逐操作工具，只暴露当前账号**有权限**的那些 |

设计约束：

- **零依赖**：只用 Node 标准库实现 MCP 的 stdio JSON-RPC，不引入未登记依赖。
- **stdout 只走协议帧**，日志一律写 stderr，否则会破坏客户端解析。
- **工具调用串行**：响应顺序与请求一致，便于排查。
- **权限前置**：无权限的操作不会出现在工具列表里，调用时才失败的情形只可能是权限在会话期间被回收。

## 安全边界

- 服务账号**没有删除权限**；删除需人工用管理员账号操作。
- `publish` / `unpublish` 直接影响门户展示，工具描述里已标注，执行前应确认。
- 凭据只放环境变量或本地凭据文件，不要提交进仓库。
