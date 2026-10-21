# AGENTS.md — vane-server

后端工程的代理说明。仓库级规则见上级 [`AGENTS.md`](../AGENTS.md)，本文只写后端特有的约束。

## 模块与依赖方向

```text
vane-common        信封无关的公共件：错误模型、Problem Details、traceId、i18n
vane-iam           身份与权限：用户、角色、权限点、令牌、MFA
vane-system        系统：参数、字典、模板、日志、任务、附件
vane-content       内容：内容页、公告、导航、轮播
vane-file          文件存储抽象
vane-audit         审计查询
vane-api-admin     后台 API（controller + DTO）
vane-api-portal    门户 API（controller + DTO）
vane-bootstrap     启动装配、配置、Flyway、打包
```

**依赖规则（可被架构测试验证）**：

1. `vane-common` 不得依赖任何业务模块。
2. `api-*` 只被 `vane-bootstrap` 依赖，不得被业务模块依赖。
3. Controller 不得直接依赖 Repository 或实体，只能依赖 application 层用例。
4. 实体与 DTO 必须分离，转换用 MapStruct。
5. 包名：`xyz.byqy.vane.<module>.<layer>`，层为 `api / application / domain / infrastructure`。

## 构建

```powershell
$env:JAVA_HOME = 'C:\Users\byqy\.jdks\temurin-25.0.3'
.\mvnw.cmd -B -DskipTests package     # 构建
.\mvnw.cmd -B test                    # 测试 + JaCoCo 覆盖率
powershell -ExecutionPolicy Bypass -File .\scripts\verify-coverage.ps1   # 覆盖率门禁（行 ≥ 70%）
.\mvnw.cmd -B -pl vane-common test    # 单模块测试
```

- 一律用 `mvnw`，不要用系统 `mvn`，不要改 wrapper 里的 Maven 版本（3.10.0）。
- 编译目标 Java **25**，不要降级语言级别。

## Spring Boot 4 的坑（会导致静默失败）

| 场景 | 正确依赖 |
|---|---|
| 数据库迁移 | `org.springframework.boot:spring-boot-starter-flyway`（只加 `flyway-core` 时**应用照常启动但迁移不执行**） |
| JSON | `spring-boot-starter-jackson`（Web starter 不再传递 Jackson） |
| 校验 | `spring-boot-starter-validation` |

Jackson 已升级到 **3.x**：`ObjectMapper` 在 `tools.jackson.databind`，`com.fasterxml.jackson.databind` 不存在。仅注解包仍沿用 `com.fasterxml.jackson.annotation`。

## 错误与响应

- 抛业务错误：`throw new ApiException(CommonErrors.XXX)`，或模块自定义 `ErrorCode` 并 `ErrorCodeRegistry.register(...)`。
- 不要自己拼错误 JSON，也不要捕获异常后返回 `{code,msg}`——统一由 `ApiExceptionHandler` / `ProblemDetailWriter` 输出。
- 成功响应直接返回资源或 `{ items, page }`，不包 `data`。
- 服务器错误**不得**返回堆栈、SQL、内部类名，只回 `traceId`。
- 新增错误码必须：加枚举 → 加各支持语言文案（`vane-common/src/main/resources/i18n/messages*.properties`，20 份）→ 保持 code 唯一。

## Web 约定

- 路径前缀：`/api/v1/auth/**`、`/api/v1/admin/**`、`/api/v1/portal/**`、`/api/v1/public/**`、`/api/v1/files/**`。
- 状态码严格按 `docs/03` §3 契约（201 创建 + `Location`、204 无体、422 校验、429 限流等）。
- 分页参数 `page[size]` + `page[after]` 或 `page[number]`，单页上限 100。
- `traceId` 由 `TraceIdFilter` 自动写入 MDC、响应头 `X-Request-Id` 与错误体，业务代码不要自己生成。
- 语言由 `Accept-Language` 决定，文案经 `MessageResolver` 解析，不要硬编码中文。

## 安全

- `SecurityConfig` 已设定：无状态、无 CSRF、无 Basic/Form 登录；`/actuator/health/**`、`/actuator/info`、`/api/v1/auth/**`、`/api/v1/public/**` 放行，其余需认证。
- 权限判定只在服务端进行；前端隐藏按钮不是安全边界。
- 数据范围在查询层统一注入，受控表须在 `docs/05` §7 登记并补越权测试。
- 不得把密码、令牌、完整证件号写入日志或错误 detail。

## 配置

- 配置只放 `vane-bootstrap/src/main/resources/application.yaml`，敏感值经环境变量注入。
- 关键环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
- `spring.jpa.hibernate.ddl-auto: validate`——**禁止**改成 `update/create`，表结构只由迁移脚本决定。
- Flyway 迁移脚本的唯一来源是仓库根 `db/migration/`，通过资源目录 `../../db/migration` 打包进 jar，不要在 `src/main/resources` 下复制副本。

## 本地数据库

```powershell
docker run -d --name vane-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=vane -p 55432:5432 postgres:18
$env:DB_URL='jdbc:postgresql://localhost:55432/vane'; $env:DB_USERNAME='postgres'; $env:DB_PASSWORD='postgres'
```

PostgreSQL 18 内置 `uuidv7()`；`citext` 扩展由 `V1` 创建。

若本机 55432 落在 Windows 保留端口段（`netsh int ipv4 show excludedportrange protocol=tcp` 可见），可改用 5432 等空闲端口，并同步 IDEA 运行配置里的 `DB_URL`。

## 测试要求

### 覆盖率门槛（与 `docs/09` §4 一致）

| 范围 | 阈值 |
|---|---|
| 后端行覆盖率 | ≥ 70% |
| 新增/变更代码覆盖率 | ≥ 80% |
| 排除项 | 生成的 DTO / MapStruct Mapper、配置类、启动类 |

覆盖率下降必须在 PR 说明原因；**禁止**用无断言的测试凑数。

### 测试维度（每类都要有，不是只写单元测试）

| 维度 | 位置 | 必测内容 |
|---|---|---|
| 单元测试 | 同包 `src/test/java` | 领域规则、工具类、错误码映射；不启动 Spring |
| 集成测试 | `@SpringBootTest` + Testcontainers（`vane-bootstrap` 的 `VaneIntegrationTest`） | Repository、事务、数据范围注入、JPA 审计（created_by/updated_by 自动填当前操作者）、Flyway 结果 |
| 接口测试 | MockMvc / WebTestClient | 状态码契约、Problem Details 字段、鉴权、分页边界 |
| 契约测试 | CI 校验 | 错误码「一码一状态」、`type` 稳定、每码各支持语言文案齐全 |
| 越权测试 | 参数化集成测试 | 每个受控表的列表 / 详情 / 导出 / 统计四类接口 |
| 认证测试 | 集成测试 | 令牌缺失/过期/撤销、refresh 轮换与复用检测、MFA 绑定与校验、登录锁定 |

### 必测清单

- 新接口：走一遍 03 §3 的状态码契约（成功、校验失败、未认证、无权限、不存在）。
- 新增错误码：断言 `code` 唯一、`type` 符合 `/problems/*`、各支持语言文案均存在。
- 新增受控表：越权矩阵三类身份（本组织 / 跨组织 / 无权限角色）× 四类接口。
- 涉及时区、金额、枚举序列化的改动：断言输出格式符合 03 §5。
- 超过 100 条的集合查询：断言分页上限生效，而不是静默截断。

### 约定

- 纯逻辑测试不启动 Spring 上下文（保持秒级）。
- 需要数据库/缓存的集成测试用 Testcontainers，**禁止**连本地库或依赖固定端口。
- 时间相关断言注入 `Clock`，不依赖 `now()` 的真实时间。
- 测试数据用构造器生成，不依赖生产数据；敏感字段用明显假值。
- 不新增逻辑却没有测试；不为通过而放宽断言或删除用例。

### 覆盖率

- `./mvnw -B test` 会生成 JaCoCo 报告；**判断门禁只看聚合报告** `vane-bootstrap/target/site/jacoco-aggregate/`，单模块报告不含集成测试对其他模块的覆盖。
- 门禁脚本：`scripts/verify-coverage.ps1`（行 ≥ 70%，低于阈值退出码 1）。
- 当前基线：行 83.6%、分支 61.5%；265 个测试 = 222 单元 + 43 集成（`VaneIntegrationTest`）。

## 当前进度

已实现：错误模型与 Problem Details、traceId、i18n、安全基线、Flyway、认证（登录 / 刷新 / 登出 / me / 失败锁定 / 登录日志）、MFA（TOTP）、权限点校验（权限码目录 `PermissionCatalog`，ADR-021）、数据范围注入、JPA 审计（created_by/updated_by 自动填当前操作者）、用户管理（CRUD + 角色分配 + 重置密码 + 用户名/状态过滤，列表含组织名与角色码）/ 角色 / 权限点 / 组织管理、系统参数与字典（类型/字典项 CRUD + 中英标签）、审计查询（操作日志目标级 + 字段级变更、登录日志、CSV 导出；ADR-023：目标对象、HTTP 状态、UA、组织、影响条数、字段级 before/after、递归脱敏、详情权限 `audit:oper-log:detail`、导出/下载纳入审计；保留期清理走 `db/maintenance/audit-retention.ps1`）、用户列表 CSV 导出、文件上传下载与管理、内容模块（公告 / 内容页 / 导航 / 轮播 / 文章 / 分类 / 标签 / 站内搜索）、定时任务（含执行日志查询）、聚合健康检查、消息模板与邮件发送（SMTP 未配置时降级为日志，模板页挂 `ops:mail` 菜单）、短信服务 P1 基础（服务商配置加密、测试发送、号码白名单，迁移 V12）、初始管理员引导（无超级管理员时创建 admin + 根组织，密码一次性输出）、自助改密（首登/重置后强制改密，最小长度取系统参数）。
短信服务 P1：服务商配置（密钥环加密）、签名、模板绑定、发送主链路（渲染 + 场景频控 + 日志）、验证码（Valkey 哈希存储 + 防刷 + `/auth/sms-code`）、日志查询、测试发送、号码白名单、错误码 20 语言、阿里云/腾讯云 SDK 适配（`VANE_SMS_MODE=vendor`）、群发活动（CSV 预校验/导入、worker 限速与预算/日限额、退订过滤，迁移 V13）、回执（独立只增表 + 最新状态）、上行与关键词自动退订、退订名单维护、保留期清理任务（迁移 V14）、按国家路由服务商、群发发送时段（按国家时区 8–21 点）、发送统计与成本估算、回执轮询任务（迁移 V15）。
短信服务范围内未实现项：无。
邮件服务 P1（ADR-024）：SMTP 服务商配置（密钥环加密、唯一默认、日限额、连通性校验）、模板渲染入队、`mail-send-worker` 异步发送与重试分类、发送日志查询/导出、保留期清理；`spring.mail.*` 配置不再读取。

## 不要做

- 不要引入 `docs/01` §2.3 列出的范围外技术（MyBatis、Redis、MySQL、Nacos、Kafka、代码生成器等）。
- 不要新增未在 `docs/01` 版本基线中的依赖；确需新增先提 ADR。
- 不要修改已执行过的迁移脚本，也不要使用 `ddl-auto` 建表。
- 不要为了让测试通过而放宽断言或跳过用例。






















