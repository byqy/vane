# ADR-030：Agent 可操作性契约

| 项 | 值 |
|---|---|
| 状态 | 已接受 |
| 实施状态 | P0（P0-1～P0-5）已实现并由 CI 与契约测试锁定；P1、P2 未开始 |
| 日期 | 2026-10-29 |
| 影响文档 | `docs/01-技术栈与规范基线.md`、`docs/03-HTTP语义与错误模型规范.md`、`docs/09-测试与质量门禁.md`、`docs/需求设计总纲.md` §3、`.github/workflows/ci.yml`、`vane-server/pom.xml` |
| 关联 | [ADR-021](ADR-021-权限码目录与校验.md)（权限码目录）、[ADR-023](ADR-023-审计日志与字段级变更.md)（操作留痕）、[ADR-027](ADR-027-仓库主从关系调整.md)（GitHub 为主仓，Agent 协作入口） |
| 评审结论 | 「待决问题」六项已于 2026-10-29 评审通过，结论见文末「评审决议」 |

## 背景

vane 是**面向小微企业的前后台管理模板**，同时也是**产品**：代码与文档要能整体复制到新机构使用，需要能被 AI Agent 读取和驱动。当前实现中已有一部分天然适合 Agent 的机制：

| 已有机制 | 位置 | 对 Agent 的价值 |
|---|---|---|
| RFC 9457 Problem Details | `vane-common/problem` | 错误体结构化，无需解析自然语言 |
| 错误码注册表 | `ErrorCodeRegistry` | code → 状态码/type 强类型映射，重复注册启动即失败 |
| 权限码目录（62 个） | `PermissionCatalog` | 权限是代码而非 UI 概念，可自我约束 |
| `If-Match` + `@Version` | `IamUser` 等 | 乐观锁，防覆盖他人修改 |
| 20 语言 + `Accept-Language` | `MessageResolver` | Agent 产出文案天然多语言 |
| 文档优先 + 三份 AGENTS.md | `docs/` | 已有上下文入口 |

但要让 Agent **可靠地自主完成任务**，当前仍缺三样东西：

1. **没有机器可读的接口契约**。`docs/01` §2.1 登记了 `springdoc-openapi 3.1.1`，但全部 `pom.xml` 中并无该依赖，服务端不输出 OpenAPI。Agent 只能读 Controller 源码猜参数，既慢又易错。
2. **错误只说「是什么错」，不说「该怎么办」**。`CommonErrors` 每个错误码第四位是布尔量（当前一律 `false`），且未暴露到响应体。Agent 拿到 409 无法判断该重试、改参数还是转问用户。
3. **写操作不可安全重试**。已有 `If-Match` 乐观锁能防覆盖，但无幂等键，Agent 在超时后重发会产生重复数据。

此外，工程门禁目前是四个命令、四种输出格式（`mvnw test`、`verify-coverage.ps1`、`pnpm -r typecheck/test/build`），人读没问题，Agent 需自行拼装判定。

## 决策

采纳**四层契约**作为 vane 对 Agent 的公开承诺面。四层互相独立、可分别落地。

### 第一层：契约层——OpenAPI 输出并冻结

1. 引入 `springdoc-openapi-starter-webmvc-ui` **3.1.1**（版本已在 `docs/01` §2.1 锁定，符合「不得擅自升降依赖版本」规则），对外输出 OpenAPI 3.1 文档（`/v3/api-docs`）。
2. **契约冻结**：仓库内保存快照 `openapi/vane.json`；CI 中比对，OpenAPI 输出与快照不一致即失败。变更契约必须同时更新快照与 `docs/03`，防止「文档说 A、代码是 B」。
3. OpenAPI 描述随 `Accept-Language` 本地化，与既有 i18n 机制一致。

### 第二层：能力层——能力清单（capability manifest）

4. 新增**能力清单**：每个可被 Agent 调用的操作一行，字段固定为
   `操作（方法 + 路径）`、`所需权限码`、`幂等支持`、`副作用描述`、`可重试错误码`、`示例请求与响应`、`所属文档锚点`。
5. 清单**由代码生成，不手写**：权限码取自 `PermissionCatalog`（62 个），错误码取自 `ErrorCodeRegistry`（启动期即完成注册与去重校验），操作与参数取自 Controller 注解与 OpenAPI。避免清单与实现漂移。
6. 清单**不对未认证方开放**；暴露方式见「待决问题 1」。

### 第三层：语义层——错误体的恢复指令

7. Problem Details 扩展成员（不破坏 RFC 9457 兼容）：`code`（既有）、`retryable`（布尔）、`action`（枚举）、`field`（违规字段的 JSON Pointer，可空）、`docs`（文档锚点，可空）。
8. `action` 取值固定五档，**语义稳定、不得新增同义值**：

   | 取值 | 含义 | Agent 应有行为 |
   |---|---|---|
   | `RETRY` | 瞬时失败 | 原样重试（配合幂等键） |
   | `FIX_PARAMS` | 请求内容有问题 | 改参数后重发 |
   | `ASK_USER` | 需要人的决策 | 停止并转人工 |
   | `REFRESH_TOKEN` | 令牌失效 | 先刷新令牌再重试 |
   | `CONFLICT` | 与他人/自身状态冲突 | 重新读取状态后再决策 |

9. `ErrorCode` 枚举扩展一个 `action` 字段，替换现有未使用的布尔位；既有错误码逐个补齐，不改变任何既有 `code` 与 HTTP 状态码映射。

### 第四层：执行层——幂等、归因与门禁

10. **幂等写**：写操作支持 `Idempotency-Key` 请求头；同一 Key 在窗口期内重放返回**首次执行的结果**，而非重复执行。窗口期与存储方案见「待决问题 2」。
11. **Agent 归因**：操作日志记录执行主体类型（人 / 服务账号 / Agent）与主体标识，使 Agent 的写操作在审计中可区分、可追责，沿用 ADR-023 的字段级变更与脱敏机制。
12. **机器可读门禁**：新增 `scripts/agent-check.ps1`，一次执行后端测试 + 覆盖率门禁 + 前端 typecheck/test/build，输出 `target/agent-check.json`（每项 status、计数、失败摘要）与非零退出码。Agent 一次调用即可判定是否可提交。
13. **服务身份**：为 Agent 用途提供最小权限服务角色，权限码由 `PermissionCatalog` 既有码组成，不新增粗粒度通配权限。

### 明确不做（反模式）

- ❌ 让 Agent 读管理端 HTML 反推接口参数——必须走 OpenAPI。
- ❌ 让 Agent 猜错误的处理方式——必须有 `action`。
- ❌ 让 Agent 自由重试写操作——必须有幂等键。
- ❌ 人工维护能力清单——必须由代码生成。
- ❌ 为 Agent 另开一套接口——Agent 走的必须是与人类相同的业务接口与权限判定。

### 实施顺序

| 阶段 | 内容 | 验收 |
|---|---|---|
| P0-1 | 引入 springdoc、输出 OpenAPI、建立 `openapi/vane.json` 快照与 CI 比对 | CI 能拦截契约漂移；`docs/03` 与快照同步 |
| P0-2 | Problem Details 扩展 `action`/`retryable`/`field`；`ErrorCode` 补 `action` | 全部错误码具备 `action`；契约测试断言每个码的 `action` 合法 |
| P0-3 | 能力清单端点/文件，由 `PermissionCatalog` + `ErrorCodeRegistry` + OpenAPI 生成 | 清单条目与实际端点数量一致（契约测试校验） |
| P0-4 | `Idempotency-Key` 支持 | 同一 Key 重放不产生重复数据；不同 Key 正常重复创建 |
| P0-5 | `scripts/agent-check.ps1` 输出 JSON | 一条命令可判定门禁通过与否 |
| P1 | 批量端点逐项结果、游标分页统一、`/agent/events?since=` 只读投影、`docs/catalog.json`、服务角色 | 各自补测试与文档 |
| P2 | 官方 CLI/SDK、把 vane 打包为 MCP server、演练沙箱、高危操作二次确认 | 独立发布说明 |

## 影响

| 项 | 影响 |
|---|---|
| 依赖 | 新增 `springdoc-openapi-starter-webmvc-ui 3.1.1`（版本已在 `docs/01` §2.1 登记，本 ADR 仅将其从「登记」变为「实际引入」） |
| 接口契约 | 错误体新增三个扩展成员；既有 `code`、`type`、HTTP 状态码映射**不变**，对现有客户端兼容 |
| 前端 | 前端 `problemOf(e)` 需忽略新增成员；不得因新增字段改变既有展示 |
| 权限 | 能力清单暴露权限码结构（本就属于公开契约的一部分），不新增权限模型 |
| 存储 | 幂等记录需要存储（Valkey 或 PG），带来一次容量与清理策略决策 |
| 审计 | 操作日志新增执行主体类型字段，沿用 ADR-023 的保留期清理 |
| CI | 新增 OpenAPI 快照比对步骤与 `agent-check` 汇总 |
| 文档 | `docs/03` 增补错误体扩展与幂等语义；`docs/09` 增补 `agent-check` 门禁；`docs/01` 同步 springdoc 状态 |

## 风险与取舍

- **契约冻结的维护成本**：OpenAPI 快照会让每次接口变更都要更新快照与 `docs/03`，短期内增加提交负担。取舍：接受这一负担——契约漂移对 Agent 的危害远大于同步成本。
- **错误体扩展的影响面**：`Problem Details` 增字段会影响所有客户端。取舍：只增不改，既有成员语义与取值一律不动；前端按「未知成员忽略」处理。
- **能力清单泄露内部结构**：清单暴露权限码与副作用描述。取舍：权限码本就是公开契约（前端权限点页面已用），且清单仅对已认证方开放，风险可控。
- **幂等的存储成本与语义歧义**：窗口期过短挡不住慢重试，过长会占用存储并让「同 Key 不同体」的语义复杂化。取舍：窗口期取 24 小时，Key 与请求体摘要绑定，冲突时返回 `REQUEST.IDEMPOTENCY_CONFLICT`（该码已存在）。
- **Agent 自动写数据的治理**：Agent 能改生产数据是能力也是风险。取舍：一期只做「契约 + 语义 + 幂等 + 归因 + 最小权限」，沙箱与高危操作二次确认放在 P2；在具备演练环境前，不把「Agent 可写生产库」作为交付目标。
- **与「不做内置代码生成器」的关系**（`docs/01` §2.3）：能力清单由现有元数据生成，不是通用代码生成器，不违反范围约束。

## 评审决议

评审于 2026-10-29 完成，六项待决问题的结论如下。自本 ADR 接受起，结论不可修改；变更须另立 ADR。

| # | 问题 | 结论 |
|---|---|---|
| 1 | 能力清单的暴露方式 | **两者都要**：登录后可访问的端点 `/api/v1/agent/capabilities`，同时随构建产出仓库内快照 `openapi/capabilities.json`；两者由同一份元数据生成，内容一致由契约测试保证 |
| 2 | 幂等记录存储 | **Valkey**，键 `vane:idempotency:{key}`，TTL **24 小时**；Key 与请求体摘要绑定，摘要不一致返回既有 `REQUEST.IDEMPOTENCY_CONFLICT` |
| 3 | OpenAPI 是否对外公开 | **仅登录后可见**（未认证返回 401），接口面不外露；公开集成需求由 P2 的 MCP server 承载 |
| 4 | Agent 身份形态 | **独立服务账号**（可单独撤销、单独审计、禁用 MFA），权限取自既有权限码目录，不新增通配权限 |
| 5 | 首期是否交付 MCP server | **P2 起步**；先做实四层契约，MCP 的 tool 定义由 OpenAPI 与能力清单生成，避免手工维护 |
| 6 | `docs/catalog.json` 字段粒度 | 固定为：文档 id、权威主题、适用任务、版本、依赖的其他分册、对应锚点 |
