# 变更记录

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。0.x 阶段接口与表结构可能变动，升级前请阅读对应版本说明。

## [Unreleased]

### 变更

- **依赖基线批量升级**（ADR-028）：Spring Boot 4.1.2、JaCoCo 0.8.15、libphonenumber 9.0.42、Vite 8.3.5、vue-i18n 11.4.16、jsdom 30.1.3、unplugin-vue-components 32.1.0、unplugin-auto-import 21.1.0；CI 动作升级至 actions/checkout@v7、actions/setup-java@v6、actions/setup-node@v7、pnpm/action-setup@v6。
- **任务锁降级**：Valkey 不可用时定时任务跳过本轮并限频告警，不再抛出连接异常堆栈。
- **CI 修复**：Maven wrapper 的分发地址由 `maven.aliyun.com` 改为中央仓库 `repo.maven.apache.org`，解决 GitHub Actions 后端 job 因镜像在境外不可达而持续失败的问题。
- **默认语言修复**：服务端默认语言固定为 `zh-CN`（`spring.web.locale`），不再随宿主机 locale 变化；新增默认语言回归测试，集成测试 46 → 47。
- **依赖分组**：Dependabot 的补丁与次版本升级合并为单个 PR，主版本升级仍单独提 PR。
- **前端测试工具链升级**（ADR-029）：Vitest 3.2.7 → 5.0.5、`@types/node` 24.19.2 → 26.6.5；248 个前端测试、typecheck 与构建全部通过。
- **Agent 可操作性契约**（ADR-030，P0-1）：引入 `springdoc-openapi 3.1.1`，输出 OpenAPI 3.1.0 契约（`/v3/api-docs`，需认证），仓库快照 `openapi/vane.json`，并由集成测试逐字节比对拦截契约漂移；新增 `scripts/verify-openapi.ps1` 与 `scripts/update-openapi.ps1`。集成测试 47 → 48。
- **错误恢复指令**（ADR-030，P0-2）：错误体新增 `action`（五档恢复指令）与 `retryable`，字段级错误另带 `field`（JSON Pointer）；82 个错误码逐个声明 `ErrorAction`，前端按未知成员忽略处理，既有 `code` 与状态码映射不变。
- **能力清单**（ADR-030，P0-3）：新增 `GET /api/v1/agent/capabilities`（需认证），由运行时元数据生成 167 个操作（方法、路径、权限码、副作用、幂等性、OpenAPI 锚点）与 82 条错误码目录；快照 `openapi/capabilities.json` 由集成测试锁定。契约脚本合并为 `scripts/verify-contracts.ps1` 与 `scripts/update-contracts.ps1`。
- **幂等执行**（ADR-030，P0-4）：支持 `Idempotency-Key` 请求头——同键同体重放首次响应（带 `Idempotent-Replayed: true`），同键异体或处理中返回 `409 REQUEST.IDEMPOTENCY_CONFLICT`（处理中带 `Retry-After`）；键存 Valkey，窗口 24 小时（`vane.idempotency.ttl`）。
- **Agent 自检脚本**（ADR-030，P0-5）：新增 `scripts/agent-check.ps1`——一条命令只读体检健康、登录、能力清单、两份契约快照、错误恢复指令、权限边界与幂等协议，输出 JSON 报告。
- **文档目录**（ADR-030，P1）：新增 `docs/catalog.json`（id / 权威主题 / 适用任务 / 版本 / 依赖 / 锚点 六字段），由 `scripts/gen-doc-catalog.ps1` 从文档头部生成，CI 增加一致性校验；同时修复后台列表泄漏软删除记录（公告 / 内容页 / 文章 / 角色 / 部门）。
- **审计事件投影**（ADR-030，P1）：新增 `GET /api/v1/agent/events`（需 `audit:oper-log`），按 `since`/`until` 时间窗与游标分页读取审计事件，仅暴露排障字段；游标保留微秒精度，避免同一条记录在下一页重复。
- **批量端点**（ADR-030，P1）：新增 `POST /api/v1/agent/batch`——最多 20 个写操作一次提交，逐项返回 `{ id, status, body, action, replayed }`，全部成功 `200`、存在失败 `207`；通过本机回环转发复用既有校验、权限、审计与幂等。
- **服务角色**（ADR-030，P1）：`iam_role` 新增 `service` 列（迁移 V20），角色创建/修改支持标记服务角色，列表返回该标志；权限码仍取自 `PermissionCatalog` 既有码，不新增通配权限。
- **游标分页统一**（ADR-030，P1）：审计日志、文章后台列表、审计事件投影统一支持 `page[after]` + `page.after/hasNext`（共享 `CursorToken`，保留微秒精度）；另外放开 Tomcat 查询字符集，未编码的 `page[size]` 不再返回 HTML 400。
- **CLI 与 MCP server**（ADR-030，P2）：新增 `tools/vane-cli`（命令行：能力清单、审计事件、任意请求、批量、自检）与 `tools/vane-mcp`（stdio MCP server，工具由能力清单动态生成、按账号权限过滤），均零依赖；`tools/README.md` 为使用说明。
- **高危操作二次确认**（ADR-030，P2）：服务账号执行 `DELETE` 前须先 `POST /api/v1/agent/confirmations` 取令牌（5 分钟、一次性、绑定操作者/方法/路径），再以 `X-Vane-Confirm` 回传，缺失返回 `428 REQUEST.CONFIRMATION.REQUIRED`；人工账号与后台界面不受影响。
- **演练模式**（ADR-030，P2）：请求头 `X-Vane-Dry-Run: true` 时整请求在恒回滚事务中执行，校验/权限/审计照跑、响应照回但数据库不落库；外部副作用（短信/邮件/文件）不回滚，已在文档中标注。

## [0.1.0] - 2026-10-27

首个公开版本（0.x 草案）。

### 包含内容

- **后端**：Spring Boot 4 / Java 25 多模块工程（9 个模块），认证与刷新令牌轮换、MFA（TOTP）、RBAC + 五级数据范围、用户/角色/权限点/组织、系统参数与字典、审计（操作日志 + 登录日志 + 字段级变更）、文件存储、内容体系（公告 / 内容页 / 导航 / 轮播 / 文章 / 分类 / 标签 / 搜索）、官网产品（多语言 + 媒体集 + 视频）、定时任务、邮件服务（SMTP + 异步队列）、短信服务（服务商、模板绑定、群发、回执与上行）。
- **前端**：pnpm workspace（`packages/shared` + `apps/admin` + `apps/portal`）；管理端 18 个菜单、页面内帮助与字段级帮助；门户为企业官网形态（首页 / 公司介绍 / 产品中心 / 产品详情 / 联系我们 / 新闻）。
- **国际化**：20 种语言，界面文案入仓库、运营内容入数据库。
- **数据模型**：50 张表，Flyway V1–V19 单一建库来源，含示例官网内容种子。
- **文档**：11 份设计分册 + 25 篇 ADR + 术语表。

### 质量基线

- 后端 274 个测试（228 单元 + 46 集成），行覆盖率 84.3%。
- 前端 248 个测试（shared 40 + portal 53 + admin 155），typecheck 与 build 通过。

### 已知限制

- 0.x 草案：接口、表结构、内部模块可能调整，不保证向后兼容。
- Playwright E2E 未接入；示例官网内容不含图片/视频素材（需在后台上传）。
- 视频以原文件播放，未做转码与 CDN；文件存储默认本地磁盘（S3 抽象预留未实现）。
