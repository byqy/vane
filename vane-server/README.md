# vane-server

vane 的后端工程：Java 25 + Spring Boot 4，Maven 多模块（9 个模块），单库多模块的经典分层结构——业务模块只依赖 `vane-common`，API 层只被启动模块依赖。

## 模块与依赖方向

```text
vane-common        错误模型、Problem Details、traceId、i18n（不依赖任何业务模块）
vane-iam           身份与权限：用户、角色、权限点、组织、令牌、MFA、登录日志
vane-system        系统：参数、字典、消息模板、操作日志、定时任务、附件
vane-content       内容：内容页、公告、导航、轮播（20 种语言）
vane-file          文件存储抽象与本地实现
vane-audit         审计查询（操作日志/登录日志）
vane-api-admin     后台 API（controller + DTO）
vane-api-portal    门户 API（controller + DTO）
vane-bootstrap     启动装配、配置、Flyway、打包（可执行 jar）
```

依赖规则：

1. `vane-common` 不得依赖任何业务模块；业务模块之间按需依赖，禁止循环。
2. `vane-api-*` 只被 `vane-bootstrap` 依赖，业务模块不得依赖 API 层。
3. Controller 只依赖 application 层用例，不直接碰 Repository 或实体。
4. 实体与 DTO 分离；组织名称等跨模块数据由 application 层组装。

包名统一为 `xyz.byqy.vane.<module>.<layer>`，层为 `api / application / domain / infrastructure`。

## 技术栈

| 依赖 | 版本 |
|---|---|
| Java | **25**（Temurin） |
| Spring Boot | 4.1.1 |
| Spring Framework / Security | 7.0.9 / 7.1.1 |
| Hibernate | 7.4.5 |
| PostgreSQL | 18（`uuidv7()`、`citext`） |
| Valkey（Redis 协议） | 9 |
| Flyway | 随 Boot 管理（`spring-boot-starter-flyway`） |
| Maven | wrapper 3.10.0（**不要**用系统 `mvn`） |

## 构建与运行

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25'   # 指向你的 JDK 25
cd <仓库根>\vane-server

.\mvnw.cmd -B -DskipTests package     # 打包（产物 vane-bootstrap\target\vane-bootstrap-0.1.0-SNAPSHOT.jar）
.\mvnw.cmd -B test                    # 全量测试 + JaCoCo
.\mvnw.cmd -B -pl vane-iam test       # 只跑某个模块的测试
.\mvnw.cmd -B -pl vane-bootstrap -am -DskipTests package   # 只重新打包启动模块及其依赖
```

运行（主类 `xyz.byqy.vane.bootstrap.VaneApplication`，或直接跑 jar）：

```powershell
$env:DB_URL='jdbc:postgresql://localhost:55432/vane'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='postgres'
$env:AUTH_COOKIE_SECURE='false'
$env:FILE_ROOT="$env:TEMP\vane-files"
java -jar vane-bootstrap\target\vane-bootstrap-0.1.0-SNAPSHOT.jar
```

启动后：`http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`。

打包前必须先停掉占用 jar 的进程，否则 Windows 会报 `Unable to rename ...jar`。

## 配置

唯一配置文件：`vane-bootstrap/src/main/resources/application.yaml`。**所有环境差异通过环境变量注入**，不使用时序 profile，也不把密码写进仓库。

| 变量 | 说明 | 本地开发值 |
|---|---|---|
| `DB_URL` | JDBC 连接串 | `jdbc:postgresql://localhost:55432/vane` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 | `postgres` / `postgres` |
| `REDIS_HOST` / `REDIS_PORT` | Valkey 地址 | `localhost` / `6379` |
| `AUTH_TOKEN_STORE` | 令牌存储：`redis` / `memory` | `redis`（集成测试用 `memory`） |
| `AUTH_COOKIE_SECURE` | refresh Cookie 是否要求 HTTPS | `false` |
| `MFA_SECRET_KEY` | MFA 主密钥（base64 32 字节） | 空 |
| `FILE_ROOT` | 本地文件根目录 | `./data/files` |
| `SPRING_MAIL_*` / `VANE_MAIL_FROM` | SMTP；未配置时邮件降级为日志 | 空 |

数据库迁移的唯一来源是仓库根 `db/migration`：构建时由 `vane-bootstrap/pom.xml` 的 resources 配置拷到 jar 内的 `db/migration`，**不要在 `src/main/resources` 下复制副本**。

## 本地数据库

```powershell
docker run -d --name vane-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=vane -p 55432:5432 postgres:18
docker run -d --name vane-valkey -p 6379:6379 valkey/valkey:9
```

`spring.jpa.hibernate.ddl-auto: validate`：表结构只由 Flyway 决定，禁止改成 `update/create`。

## 新增数据库迁移

1. 在 `db/migration/` 新建 `V{n}__snake_case_desc.sql`（当前最新为 `V7`）。
2. **不得修改已执行过的脚本**；破坏性变更走「加列 → 双写 → 迁移 → 删列」。
3. 应用启动时自动执行；已验证脚本的版本号记录在 `flyway_schema_history`。
4. 集成测试里断言迁移总数（`VaneIntegrationTest#migrationsRunAndAllSucceed`），新增脚本后同步更新期望值。
5. 可单独验证：`powershell -ExecutionPolicy Bypass -File ..\db\verify.ps1`。

## 测试

| 层次 | 位置 | 说明 |
|---|---|---|
| 单元测试 | 各模块 `src/test/java` | 不启动 Spring，秒级；领域规则、工具类、错误码映射 |
| 集成测试 | `vane-bootstrap/src/test/java/.../VaneIntegrationTest` | `@SpringBootTest` + Testcontainers（`postgres:18`），覆盖鉴权、CRUD、契约、导出等全链路 |
| 契约/越权 | 同上 | 断言状态码、Problem Details 字段、数据范围 |

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25'   # 指向你的 JDK 25
.\mvnw.cmd -B test
powershell -ExecutionPolicy Bypass -File .\scripts\verify-coverage.ps1   # 行覆盖率 ≥ 70%，低于阈值退出 1
```

- 覆盖率只认聚合报告：`vane-bootstrap/target/site/jacoco-aggregate/jacoco.csv`。
- 当前基线：**89** 个测试（64 单元 + 25 集成），聚合行覆盖率 **84.2%**、分支 56.9%。
- 集成测试依赖 Docker；Docker Desktop 未启动会报 `Could not find a valid Docker environment`（这是环境问题，不是代码问题）。

时间相关逻辑注入 `Clock`，不要依赖真实时间；测试数据用构造器生成，不依赖生产数据。

## 接口约定

- 前缀：`/api/v1/auth/**`、`/api/v1/admin/**`、`/api/v1/portal/**`、`/api/v1/public/**`、`/api/v1/files/**`。
- 成功响应直接返回资源或 `{ items, page }`，不包 `data`；创建返回 `201` + `Location`，删除返回 `204`。
- 错误一律 RFC 9457 Problem Details：`type / title / status / detail / instance / code / traceId`，`Content-Type: application/problem+json`；`X-Request-Id` 与 `traceId` 一致。
- 业务错误统一 `throw new ApiException(CommonErrors.XXX)`；新增错误码要注册、补各支持语言文案（`vane-common/src/main/resources/i18n/messages*.properties`，共 20 份）并保持「一码一状态」。
- 语言由 `Accept-Language` 决定，文案经 `MessageResolver` 解析，禁止硬编码中文。
- 分页参数 `page[number]` + `page[size]`，单页上限 100。

## Spring Boot 4 的坑

| 场景 | 正确做法 |
|---|---|
| 数据库迁移 | 依赖 `spring-boot-starter-flyway`；只加 `flyway-core` 时应用能启动但**迁移不执行** |
| JSON | Jackson 3：`ObjectMapper` 在 `tools.jackson.databind`；Web starter 不再传递 Jackson，需 `spring-boot-starter-jackson` |
| 校验 | 需显式 `spring-boot-starter-validation` |
| 状态码枚举 | 用 `HttpStatus.UNPROCESSABLE_CONTENT`（没有 `UNPROCESSABLE_ENTITY`） |
| JPA 自动配置 | `@EntityScan`、`@EnableJpaRepositories` 在 `org.springframework.boot.persistence.autoconfigure` |
| 测试 | `TestRestTemplate` 已移除，集成测试用 JDK `HttpClient` |

## 已实现能力

认证（登录 / 刷新 / 登出 / me / 失败锁定 / 登录日志）、MFA（TOTP）、RBAC 权限点与数据范围、用户管理、角色（含自定义组织授权）、权限点、组织、系统参数、字典 CRUD（中英标签）、操作/登录日志查询与 CSV 导出、用户导出、文件上传下载、内容模块（公告 / 内容页 / 导航 / 轮播）、定时任务与执行日志、消息模板与邮件、健康检查、初始管理员引导、自助改密与首登强制改密。

## 不要做

- 不引入 `docs/01` §2.3 列出的范围外技术（MyBatis、MySQL、Nacos、Kafka、代码生成器等）。
- 不新增未在版本基线中的依赖；确需新增先提 ADR。
- 不修改已执行过的迁移脚本，不使用 `ddl-auto` 建表。
- 不为让测试通过而放宽断言或跳过用例。

编码与提交约束见 [`AGENTS.md`](AGENTS.md)，接口契约以 [`../docs/03-HTTP语义与错误模型规范.md`](../docs/03-HTTP语义与错误模型规范.md) 为准，表结构以 [`../docs/05-数据模型与数据字典.md`](../docs/05-数据模型与数据字典.md) 为准。



