# Agent 工作手册

| 项 | 值 |
|---|---|
| 版本 | v0.1（草案） |
| 状态 | 待评审 |
| 效力 | 在本仓库动手时的操作约定：构建、验证、契约快照、自检与常见坑；与 [01 技术栈与规范基线](01-技术栈与规范基线.md)、[09 测试与质量门禁](09-测试与质量门禁.md) 冲突时以那两册为准 |
| 上游 | [01 技术栈与规范基线](01-技术栈与规范基线.md)、[09 测试与质量门禁](09-测试与质量门禁.md)、[需求设计总纲](需求设计总纲.md) |

---

## 1. 动手前的三条

1. 先读根目录 `AGENTS.md`、`vane-server/AGENTS.md` 或 `vane-web/AGENTS.md`（按改动范围），再读本文。
2. 任何决策性改动先看 `docs/10-ADR/`；已接受 ADR 的结论不可修改，变更另立 ADR。
3. 起手先跑一次只读自检 `scripts/agent-check.ps1`，确认环境可用、契约未漂移。

## 2. 环境准备

```powershell
$env:JAVA_HOME = 'C:\Users\byqy\.jdks\temurin-25.0.3'   # JDK 25，缺省会误用 JDK 21
cd D:\ProgramData\Code\byqy\tby
```

本地依赖：PostgreSQL 18（`vane-pg`）、Valkey（`vane-valkey`），均为容器；`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 默认 `jdbc:postgresql://localhost:5432/vane`、`postgres`。

## 3. 常用命令

| 目的 | 命令 |
|---|---|
| 后端测试 | `cd vane-server; .\mvnw.cmd -B test` |
| 覆盖率门禁（行 ≥ 70%） | `powershell -File .\scripts\verify-coverage.ps1` |
| 前端三门禁 | `cd vane-web; pnpm -r typecheck; pnpm -r test; pnpm -r build` |
| 契约一致性 | `powershell -File .\scripts\verify-contracts.ps1 -BaseUrl http://127.0.0.1:8080 -Token <token>` |
| 重新生成契约快照 | `powershell -File .\scripts\update-contracts.ps1 -BaseUrl ... -Token ...` |
| 文档索引一致性 | `pwsh -File .\scripts\gen-doc-catalog.ps1 -Check` |
| 代理只读自检 | `powershell -File .\scripts\agent-check.ps1 -BaseUrl ... -CredentialsFile ...` |
| CLI 自检 | `node tools\vane-cli\vane.mjs check` |
| MCP 冒烟 | `node tools\vane-mcp\smoke.mjs` |

**单模块测试**要带 `-am`，否则会因为兄弟模块未安装而失败：

```powershell
.\mvnw.cmd -B -pl vane-iam -am test
```

## 4. 改了接口要做什么

1. 改 Controller / DTO；
2. 重新生成两份快照：`scripts\update-contracts.ps1`（产出 `openapi/vane.json`、`openapi/capabilities.json`）；
3. 同步 `docs/03` 中受影响的分页 / 错误 / 端点说明；
4. 跑 `.\mvnw.cmd -B test`，确认 `openApiContractMatchesRepositorySnapshot` 与 `capabilityManifestMatchesRepositorySnapshot` 通过。

能力清单**不要手写**：它由 `CapabilityManifestBuilder` 从 Spring 映射、`PermissionCatalog`、`@PreAuthorize` 与 `ErrorCodeRegistry` 运行时生成。

## 5. 错误与幂等

- 新增错误码：`CommonErrors` 加枚举 → 21 个 `i18n/messages*.properties` 补文案 → 声明 `ErrorAction`。`ErrorActionContractTest` 会拦截漏填。
- 幂等键由 `IdempotencyFilter` + `IdempotencyInterceptor` 统一处理，**新增创建类接口不用改幂等代码**；要调整适用范围就改 `IdempotencySupport.isEligible` 并同步 `docs/03` §8。
- 服务账号删除需二次确认（`POST /api/v1/agent/confirmations` → `X-Vane-Confirm`）；演练用 `X-Vane-Dry-Run: true`（数据库回滚，外部副作用不回滚）。

## 6. 常见坑

| 现象 | 原因与处置 |
|---|---|
| `.\mvnw.cmd package` 报无法重命名 jar | 有 java 进程占用 jar。按命令行匹配后停止：`Get-CimInstance Win32_Process -Filter "Name='java.exe'" \| ? { $_.CommandLine -match 'vane-bootstrap.*\.jar' } \| % { Stop-Process -Id $_.ProcessId -Force }`。**不要** `Get-Process java \| Stop-Process`，会误杀 IDE |
| `.ps1` 里有中文就乱码 / 解析报错 | 必须存成 UTF-8 **带 BOM**；编辑工具常把 BOM 去掉 |
| PowerShell 5.1 读 UTF-8 文档中文变乱码 | `Get-Content` 默认按 ANSI；用 `[System.IO.File]::ReadAllLines($p, [Text.Encoding]::UTF8)` |
| 生成类脚本在不同机器产出不同 JSON | `ConvertTo-Json` 在 PS 5.1 与 7 下格式不同；生成文件用手写确定性序列化（见 `scripts/gen-doc-catalog.ps1`） |
| 5.1 报 `SkipHttpErrorCheck` 不存在 | 该参数是 PS 7 才有；从 `$_.ErrorDetails.Message` 或 `$_.Exception.Response` 取错误体 |
| HTTP 请求拿到 HTML 400 | URI 里出现未编码的 `[` `]`（分页参数）；服务端已放开查询字符集，仍建议编码为 `%5B` `%5D` |
| 列表测试「通过」但没校验到东西 | 断言要同时确认状态码 200，否则 400 的 HTML 体也会满足 `doesNotContain` |
| `git push` 报 22 端口超时 | `~/.ssh/config` 已配 `github.com → ssh.github.com:443`；若恢复原通道删掉该段 |
| 前端改了 `packages/shared` 后页面显示原始 key | workspace 包在应用 root 之外，重启对应 dev server |

## 7. 角色与分派

- **主代理**：澄清、设计、拆解、评审 diff、跑最终验证、决定不可逆操作。
- **执行代理**：只做分派范围，交付带证据（改了哪、验证命令与真实输出、剩余风险）。
- 并行任务的写域必须互斥；有依赖就排序；汇合时由主代理统一验收。

## 8. 会话交接

临时交接写 `.agent/handover.md`（已 gitignore，不进版本历史）；新会话读完后**删除该文件**，把仍有效的内容并回本文。长期有效的约定才进本文件。
