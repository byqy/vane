# HTTP 语义与错误模型规范

| 项 | 值 |
|---|---|
| 版本 | v0.21（草案） |
| 状态 | 待评审 |
| 效力 | HTTP 语义、响应模型、错误码、分页与序列化的唯一详细规范；摘要见 [01 基线](01-技术栈与规范基线.md) §8 |
| 上游 | [01 技术栈与规范基线](01-技术栈与规范基线.md)、[需求设计总纲](需求设计总纲.md) |

---

本文规定成功响应的表示方式、状态码契约、错误响应体、错误码注册、序列化约定、请求头、条件请求、幂等、分页、版本策略与契约管理。

## 1. 基本形态：body 只装数据，成败由状态码表达

**路径结构**

| 前缀 | 用途 |
|---|---|
| `/api/v1/auth/*` | 两端共用：登录、刷新、登出 |
| `/api/v1/admin/*` | 后台管理端 |
| `/api/v1/portal/*` | 前台门户 |
| `/api/v1/public/*` | 匿名只读 |
| `/api/v1/files/*` | 附件下载与预览（需鉴权） |
**取单个资源**：

```http
GET /api/v1/admin/users/0199a3f2-8c41-7d3e-9b21-5f0c2a7e4d18 HTTP/1.1
Accept: application/json
```

```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "8f3c1a2b"
Cache-Control: private, no-cache
X-Request-Id: 4bf92f3577b34da6a3ce929d0e0e4736
```
```json
{
  "id": "0199a3f2-8c41-7d3e-9b21-5f0c2a7e4d18",
  "username": "alice",
  "email": "alice@example.com",
  "version": 3,
  "createdAt": "2026-10-16T09:12:31.428Z"
}
```

**创建资源**——`201` + `Location`；重复提交用 `Idempotency-Key` 防重（见 §8），不要用 `If-None-Match` 冒充幂等：

```http
POST /api/v1/admin/users HTTP/1.1
Content-Type: application/json
Idempotency-Key: 6f1c2b8e-4a7d-4c1e-9f30-2b7a5c8d1e44
```
```http
HTTP/1.1 201 Created
Location: /api/v1/admin/users/0199a3f2-8c41-7d3e-9b21-5f0c2a7e4d18
Content-Type: application/json
```
响应体为创建后的完整资源表示。

> 若某个创建接口采用 `PUT /{id}` 的语义（客户端生成 id），则可用 `If-None-Match: *` 表达「不存在才创建」。

**删除资源**——`204`，**无响应体**：

```http
DELETE /api/v1/admin/users/0199a3f2-8c41-7d3e-9b21-5f0c2a7e4d18 HTTP/1.1
```
```http
HTTP/1.1 204 No Content
```

**集合**——`items` + `page` 是集合自身的表示，不是 `code/msg` 式外层包装。支持两种分页：

```http
GET /api/v1/admin/users?page%5Bsize%5D=20&page%5Bafter%5D=eyJpZCI6IjAxOTlhM2YyIn0 HTTP/1.1
```
```http
HTTP/1.1 200 OK
Content-Type: application/json
Link: </api/v1/admin/users?page%5Bsize%5D=20&page%5Bafter%5D=eyJpZCI6IjAxOTlhM2Y0In0>; rel="next"
```
```json
{
  "items": [
    { "id": "0199a3f2-…", "username": "alice" },
    { "id": "0199a3f3-…", "username": "bob" }
  ],
  "page": { "size": 20, "after": "eyJpZCI6IjAxOTlhM2YyIn0", "hasNext": true }
}
```

## 2. 错误：一律 `application/problem+json`

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json
Content-Language: zh-CN
```
```json
{
  "type": "/problems/validation-failed",
  "title": "请求参数校验失败",
  "status": 422,
  "detail": "邮箱已被占用",
  "instance": "/api/v1/admin/users",
  "code": "IAM.USER.EMAIL_TAKEN",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [
    { "pointer": "/email", "code": "VALIDATION.NOT_UNIQUE" }
  ]
}
```

要点：

- `type` 是**稳定的、语言无关的**问题类型标识，使用相对 URI `/problems/<短横线编码>`，避免依赖未定域名；若需对外文档页再改为绝对 URI。客户端按 `type` + `code` 分支，**不要**按 `title` 文案匹配。
- `title` / `detail` 按请求语言本地化，并用 `Content-Language` 声明语言；`type` / `code` / `pointer` 永远不翻译。
- `errors[]` 用于字段级错误：`pointer` 是 RFC 6901 JSON Pointer，`code` 指向错误码注册表。顶层 `code` 与字段级 `code` 可不同。
- `traceId` 是 RFC 9457 §3.2 允许的**扩展成员**，与响应头 `X-Request-Id` 同值，用于日志关联。
- **JSON 媒体类型不得带 `charset` 参数**。RFC 8259 §8.1 要求联网传输的 JSON 强制 UTF-8；§11 的 IANA 注册说明原文是 *“No 'charset' parameter is defined for this registration. Adding one really has no effect on compliant recipients.”* 正确写法是 `application/json` / `application/problem+json`。
- 服务器错误（`500`/`503`）**不得**返回堆栈、SQL、内部类名，只回 `traceId`。

## 3. 状态码契约表（全站唯一口径）

| 场景 | 状态码 | 关键响应头 / 说明 |
|---|---|---|
| GET 单资源 / 集合成功 | `200` | `ETag`、`Cache-Control` |
| GET 命中协商缓存 | `304` | 无 body；配合 `If-None-Match` |
| POST 创建成功 | `201` | `Location` |
| POST 受理异步任务 | `202` | `Location` 指向任务资源；`Retry-After` 可选 |
| PUT / PATCH 成功且有返回体 | `200` | 新 `ETag` |
| DELETE 成功 / 无返回体 | `204` | 无 body |
| 批量操作 | `200` | body 内逐项给结果 |
| 请求语法错误、JSON 无法解析 | `400` | 不用于「字段语义不合法」 |
| 未认证 / 凭据失效 | `401` | `WWW-Authenticate` |
| 已认证但无权限 | `403` | 与 `401` 严格区分 |
| 资源不存在 | `404` | 不泄露「存在但无权」的差异 |
| 方法不支持 | `405` | `Allow` |
| 资源已永久移除 | `410` | 可选，用于已注销/已删除的公开资源 |
| 状态冲突（重复创建、状态机非法跳转） | `409` | |
| `If-Match` 版本冲突 | `412` | 配合 `ETag` 做乐观锁 |
| 要求条件请求但客户端未带 | `428` | 高风险写操作可强制 `If-Match` |
| 语义校验失败（能读懂但内容不合法） | `422` | 与 `400` 的分工见下 |
| 请求体过大 | `413` | 上传上限 |
| 媒体类型不支持 | `415` | |
| 无法满足 `Accept` | `406` | |
| 限流 | `429` | `Retry-After` |
| 服务端异常 | `500` | 不泄露堆栈/SQL，只回 `traceId` |
| 依赖不可用（数据库/缓存/下游） | `503` | `Retry-After` |

**`400` 与 `422` 的分工**：`400` = 请求本身不成立（报文格式错、JSON 解析失败、必填结构缺失、类型无法转换）；`422` = 请求结构合法但业务语义不通过（字段格式对但值非法、唯一性冲突、状态不允许）。两者都返回 Problem Details，仅 `status` 不同。

**批量操作**：统一返回 `200`，body 为逐项结果数组（每项含 `id` / `status` / `code`）；部分失败不回滚时需在响应中明确标识。

## 4. 错误码注册表

- 命名：`域.模块.原因`，全大写下划线分段（如 `IAM.ROLE.NAME_DUPLICATE`、`SYS.FILE.TYPE_INVALID`）。
- **`code` 决定 `httpStatus`**：注册表是唯一权威（后端枚举 + 前端常量 + OpenAPI 枚举由同一份源生成），每条含 `code`、`httpStatus`、`type` URI、i18n 文案 key、是否可重试、是否记审计。
- 业务异常只抛 `BusinessException(code)`，由统一异常处理器查注册表得到状态码与 `type`，**不得**在业务代码里手写 `ResponseEntity` 状态码。

**域清单**（一期，新增域需 ADR）：

| 域 | 覆盖范围 |
|---|---|
| `AUTH` | 登录、登出、令牌、会话、验证码 |
| `IAM` | 用户、角色、权限点、组织、数据范围 |
| `VALIDATION` | 通用入参校验（非业务语义） |
| `SYS` | 系统参数、字典、编号、定时任务 |
| `FILE` | 上传、下载、存储、类型与大小 |
| `CONTENT` | 公告、内容页、导航 |

- 越权访问：返回 `403` + `IAM.SCOPE.DENIED`；资源不存在一律 `404`，不区分「无权」与「不存在」。

**一致性校验进 CI**：同一 `code` 不得映射到不同 `httpStatus`；每个 `code` 必须有 `zh-CN` 与 `en` 两条文案，缺失即构建失败。

## 5. 字段与序列化约定

| 项 | 约定 |
|---|---|
| 命名 | JSON 字段 `camelCase`；数据库列 `snake_case` |
| 标识 | UUIDv7 小写带连字符字符串，如 `0199a3f2-8c41-7d3e-9b21-5f0c2a7e4d18` |
| 时间 | RFC 3339、UTC、`Z` 结尾、毫秒精度：`2026-10-16T09:12:31.428Z`；纯日期 `2026-10-16` |
| 金额 | 十进制**字符串** + 币种字段，避免二进制浮点精度损失；如 `{"amount": "199.00", "currency": "CNY"}` |
| 布尔 | `true` / `false`，不使用 `0/1` |
| 枚举 | 大写下划线字符串，如 `"PENDING_REVIEW"`；不使用数字 |
| 空值 | 未赋值字段**省略**；`null` 仅用于显式清空（`PATCH` 语义） |
| 数组 | 永不返回 `null`，空数组返回 `[]` |
| 未知字段 | 服务端默认忽略；契约比对在 CI 中完成 |
| 响应体顶层 | 单资源为对象，集合为 `{items, page}`；成功响应不包 `code`/`msg` |

## 6. 请求头约定

| 头 | 何时使用 | 说明 |
|---|---|---|
| `Accept` | 所有请求 | 默认 `application/json` |
| `Content-Type` | 有请求体时 | `application/json` 或 `application/merge-patch+json` |
| `Accept-Language` | 所有请求 | BCP 47；缺失时用用户偏好或默认语言 |
| `Authorization` | 除公开接口外所有请求 | `Bearer <access token>`；见 §14 |
| `X-Request-Id` | 可选 | 客户端生成则回显，否则服务端生成 |
| `traceparent` | 可选 | W3C Trace Context，与 `X-Request-Id` 并行使用 |
| `If-Match` | 更新/删除 | 携带读到的 `ETag`，防止并发覆盖 |
| `If-None-Match` | GET 缓存 / PUT 创建 | `304` 或「不存在才创建」 |
| `Idempotency-Key` | POST 创建类 | 见 §8 |

## 7. 条件请求与缓存

- **`ETag` 生成**：单个资源用强 `ETag`，取资源表示内容哈希（必须包含 `version` 与 `updatedAt`）；集合在内容稳定时可用弱 `ETag`（`W/`）。
- **读缓存**：`GET` 带 `If-None-Match` 且命中 → `304`，无 body。
- **写并发**：`PUT` / `PATCH` / `DELETE` 必须支持 `If-Match`，不匹配 → `412`；高风险操作可强制要求，缺失 → `428`。
- **缓存策略**：用户私有数据 `Cache-Control: private, no-cache`；公开只读资源可 `public, max-age=<秒>`；带语言的响应必须 `Vary: Accept-Language`。

## 8. 幂等

- 适用范围：所有「创建」类 `POST`（用户、角色、内容、上传登记等）。
- `Idempotency-Key` 为客户端生成的 UUID；服务端保存 **24 小时**。
- 相同 key 的重放请求：返回**首次**的响应与状态码，并带响应头 `Idempotent-Replayed: true`。
- 相同 key 但请求体不同：返回 `409`，`code = SYS.IDEMPOTENCY.KEY_CONFLICT`。
- 同一 key 正在处理中：返回 `409`，`code = SYS.IDEMPOTENCY.IN_PROGRESS`，响应带 `Retry-After`。

## 9. 分页、过滤与排序

| 能力 | 游标分页（默认） | 页号分页（管理端表格） |
|---|---|---|
| 参数 | `page[size]`、`page[after]` | `page[number]`、`page[size]` |
| 响应 | `page.hasNext`、`page.after` | `page.total`、`page.number` |
| 适用 | 信息流、时间线、大数据量 | 后台表格、需要跳页 |
| 上限 | `page[size]` ≤ 100，默认 20 | 同左 |

- 过滤：`filter[field]=value`，多值重复参数 `filter[status]=ACTIVE&filter[status]=LOCKED`。
- 排序：`sort=-createdAt,username`，`-` 表示降序；只允许对已建索引字段排序。
- 游标与页号不得在同一请求中混用。
- **URL 编码**：`[` `]` 在 RFC 3986 中不属于 query 允许字符，出现在 `Link` 头或跳转 URL 中时**必须**编码为 `%5B` `%5D`；客户端发送未编码形式时服务端也应当兼容解析。

## 10. API 版本与废弃

- 路径主版本：`/api/v1/...`；只有**不兼容变更**才升主版本，兼容变更（加字段、加接口）不升。
- 废弃流程：先在响应头声明 `Deprecation: true` 与 `Sunset: <HTTP-date>`（RFC 8594），并在文档标记；并行支持期**不少于 3 个月**。
- 主版本下线前需提供迁移说明与至少一次公告。

**变更分类**

| 不兼容（必须升主版本） | 兼容（不升版本） |
|---|---|
| 删字段、改字段名 | 加可选字段 |
| 改字段类型或语义 | 加新端点 |
| 可选改为必填 | 放宽校验 |
| 删除或修改枚举值 | 新增枚举值（客户端须容忍未知值） |
| 改路径或 HTTP 方法 | 新增可选请求参数 |
| 改变状态码语义 | 新增响应头 |

客户端**必须**容忍未知字段与未知枚举值，否则无法做兼容扩展。

## 11. OpenAPI 契约

- 接口描述由 `springdoc-openapi` 从代码与注解生成，禁止手写维护。
- 产物路径：`/v3/api-docs`（JSON）与 `/v3/api-docs.yaml`。
- 规范版本：遵循 OpenAPI 3.x（当前规范版本为 3.2.1）；实际输出的小版本以 `springdoc-openapi` 实现为准，升级时记录。
- CI 门禁：构建后与主干快照做 diff，**breaking change 必须显式声明**，否则失败。

## 12. 关联与可观测

- 入站接受 W3C Trace Context `traceparent` 与 `X-Request-Id`，缺省则生成；出站**回显**同一值。
- 日志、审计、Problem Details、响应头使用**同一个** `traceId`。
- 前端错误上报携带 `traceId`，与后端日志可对齐。

## 13. 前端消费约定

- 拦截器按 **HTTP 状态码**分支，不按 `code` 判断成败：`401` → 按 §14 刷新（单飞，多个并发 401 只触发一次）；刷新失败则清理状态并跳登录；`403` → 无权限页；`404` → 空态页；`409` / `412` → 提示数据已变更并刷新；`422` → 按 `errors[].pointer` 落到表单字段；`429` → 按 `Retry-After` 退避；`5xx` → 兜底错误页 + `traceId`。
- 统一在请求层完成 Problem Details 解析，业务代码拿到的永远是「强类型结果或强类型错误」，不直接碰原始响应体。
- 列表请求的游标与页号由请求层统一拼装并编码，业务层不拼字符串。

## 14. 认证令牌

**令牌类型**

| 令牌 | 传输方式 | 有效期 | 服务端存储 |
|---|---|---|---|
| access | `Authorization: Bearer <token>` | 15 分钟 | Valkey 存哈希（TTL 15 分钟），撤销即时生效 |
| refresh | `__Host-REFRESH` HttpOnly Cookie（本地 http 开发降级为 `REFRESH`，见 §14） | 7 天 | Valkey 存哈希与族状态 |

access 令牌只存内存，**不得**写入 `localStorage`、`sessionStorage` 或 Cookie。

**接口**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/login` | 校验凭据；未开启 MFA 时返回 access 并 `Set-Cookie: __Host-REFRESH`；开启 MFA 时返回 `mfaToken`（见下） |
| POST | `/api/v1/auth/refresh` | 用 refresh 换新 access，同时轮换 refresh |
| POST | `/api/v1/auth/logout` | 撤销当前令牌族并清除 Cookie |
| GET | `/api/v1/auth/me` | 当前用户与权限 |
| POST | `/api/v1/auth/mfa/bind` | 绑定 TOTP：**需当前密码或短期 step-up 令牌**；返回密钥与二维码，首次校验通过后启用 |
| POST | `/api/v1/auth/mfa/verify` | 校验 TOTP 或恢复码，换取 access 与 refresh |

登录响应体：

```json
{ "accessToken": "eyJ…", "tokenType": "Bearer", "expiresIn": 900 }
```

**刷新与轮换**

- refresh 一次性使用：刷新成功后旧值立即失效，同时下发新值。
- 一次登录对应一个 `familyId`，该会话内所有 refresh 属于同一族。
- **复用检测**：已使用过的 refresh 再次出现，判定为泄露，撤销整个族并返回 `401` + `AUTH.TOKEN.REUSED`。
- **并发宽限**：同一 refresh 的并发刷新允许 10 秒宽限窗口，窗口内重放返回同一结果，窗口外按复用处理。
- 登出撤销当前族；管理员强制下线可撤销指定用户全部族。

**多因素认证（TOTP）**

- 账号开启 MFA 时，`POST /api/v1/auth/login` 校验密码成功后**不直接发 access**，返回 `200` 与一次性 `mfaToken`（有效期 5 分钟）。
- 客户端再调用 `POST /api/v1/auth/mfa/verify` 提交 `{ "mfaToken": "…", "code": "123456" }`，通过后才下发 access 与 refresh。
- 恢复码与 TOTP 走同一校验端点；每个恢复码只能使用一次。
- 连续失败 5 次锁定 15 分钟，返回 `429` + `Retry-After`。
- 错误码：`AUTH.MFA.REQUIRED`、`AUTH.MFA.INVALID`、`AUTH.MFA.LOCKED`。
- `mfaToken`：256 bit 随机串（base64url），服务端存哈希于 Valkey；TTL 5 分钟；一次性（校验成功即删）；绑定 `userId`，不携带任何权限。
- TOTP 密钥：AES-256-GCM 加密后入库，主密钥来自环境变量或 KMS；密文携带密钥版本号，支持轮换。

**Cookie 与请求校验**

- `__Host-REFRESH`：`Secure` + `Path=/` + `HttpOnly` + `SameSite=Strict`，不带 `Domain`（`__Host-` 前缀要求 `Path=/`）。
- **用户偏好**：`PATCH /api/v1/auth/me/preferences`，body 可含 `locale`（BCP-47）与 `timezone`（IANA ID，如 `Asia/Shanghai`），至少提供一个字段，成功返回 204。`locale` 不在支持清单、`timezone` 不是合法 IANA ID 或两者皆空时返回 422。偏好写入 `iam_user.locale` / `iam_user.timezone`，由 `/auth/me` 同名同义字段回读。
- **本地开发例外**：`vane.auth.cookie-secure=false`（http 环境）时 Cookie 名降级为 `REFRESH`。浏览器会丢弃「带 `__Host-` 前缀但缺少 `Secure`」的 Cookie，不降级会导致刷新页面后丢失会话；生产必须恢复 `AUTH_COOKIE_SECURE=true` 并使用 `__Host-REFRESH`。
- `login`、`refresh`、`logout` 三个接口必须校验 `Origin` 与站点同源。
- 采用 Bearer 传输，其余接口**不需要** CSRF 令牌。

**401 错误码**

| code | 含义 |
|---|---|
| `AUTH.TOKEN.MISSING` | 缺少 `Authorization` 头 |
| `AUTH.TOKEN.EXPIRED` | access 已过期，应刷新 |
| `AUTH.TOKEN.INVALID` | 令牌无效 |
| `AUTH.TOKEN.REVOKED` | 令牌已被撤销 |
| `AUTH.TOKEN.REUSED` | refresh 复用，整族已撤销，必须重新登录 |

**修改密码**

- `POST /api/v1/auth/password`，请求体 `{"currentPassword":"…","newPassword":"…"}`；成功返回 `204`。
- 密码最小长度取系统参数 `security.password.min-length`（默认 6）。
- 首登或管理员重置密码后，`GET /api/v1/auth/me` 返回 `mustChangePassword: true`，客户端须强制先改密。
- 校验失败返回 `422`，错误码：

| code | 含义 |
|---|---|
| `AUTH.PASSWORD.MISMATCH` | 当前密码不正确 |
| `AUTH.PASSWORD.POLICY` | 新密码不满足策略 |
| `AUTH.PASSWORD.UNCHANGED` | 新密码与当前密码相同 |

**多端接入**

- App 端复用同一组接口；refresh 存客户端安全存储（Keychain / Keystore）。
- App 端刷新时通过请求体提交 `{"refreshToken": "…"}`，服务端仅在 `X-Client-Type: app` 时接受该方式；Web 端只从 Cookie 读取。
- 所有客户端共用同一套 access 校验与错误码。

## 15. 文件访问

- 私有附件：`GET /api/v1/files/{id}`，需登录、校验权限点；若附件归属业务数据，同时适用数据范围。
- 公开附件：`GET /api/v1/public/files/{id}`，仅限白名单用途（如站点图标），不承载个人数据。
- **不得**在响应中暴露存储路径或真实文件名；对外只使用文件 ID。
- 下载强制 `Content-Disposition: attachment`；仅图片预览类白名单 MIME 允许 `inline`。
- 上传文件名需过滤路径分隔符与 `..`，防止路径穿越。





## 16. 门户 SEO 接口

| 方法 | 路径 | 说明 | 缓存 |
|---|---|---|---|
| GET | `/api/v1/public/site` | 站点 SEO 配置：`siteName`、`description`、`baseUrl`、`ogImage`、`locales`、`defaultLocale` | 不缓存 |
| GET | `/api/v1/public/pages` | 已发布内容页清单（`slug`、`publishedAt`），供导航与 sitemap 使用 | 不缓存 |
| GET | `/sitemap.xml`（同 `/api/v1/public/sitemap.xml`） | 多语言 sitemap：首页与内容页 × 全部支持语言，带 `xhtml:link` hreflang 互指与 `lastmod` | `public, max-age=3600` |
| GET | `/robots.txt`（同 `/api/v1/public/robots.txt`） | 允许抓取、禁止 `/admin/`、声明 sitemap 地址 | `public, max-age=3600` |

- 四个接口均允许匿名访问（`/api/v1/public/**` 与根路径 `/sitemap.xml`、`/robots.txt`）。
- `sitemap.xml` 的 `loc` 与 hreflang 链接基于系统参数 `seo.base-url`；部署到正式域名时必须配置。
- `sitemap.xml` 只包含已发布内容页；草稿与软删内容不出现。

## 17. 内容体系接口

### 17.1 后台（权限点见括号）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/admin/categories` | 分类列表（含文章数） | `content:category` |
| POST | `/api/v1/admin/categories` | 创建分类（一级，`parentId` 必须为空） | `content:category:create` |
| PATCH | `/api/v1/admin/categories/{id}` | 修改分类 | `content:category:update` |
| DELETE | `/api/v1/admin/categories/{id}` | 删除分类（有文章时 409） | `content:category:delete` |
| GET | `/api/v1/admin/articles` | 文章分页列表，过滤 `categoryId` / `status` / `keyword` | `content:article` |
| POST | `/api/v1/admin/articles` | 创建文章（多语言译文、分类、可选标签） | `content:article:create` |
| PATCH | `/api/v1/admin/articles/{id}` | 修改文章 | `content:article:update` |
| DELETE | `/api/v1/admin/articles/{id}` | 删除文章（软删） | `content:article:delete` |
| POST | `/api/v1/admin/articles/{id}/publish` | 发布 | `content:article:publish` |
| POST | `/api/v1/admin/articles/{id}/unpublish` | 下线 | `content:article:publish` |
| GET/PUT | `/api/v1/admin/articles/{id}/tags` | 读取/整体替换标签 | `content:article` / `:update` |
| GET | `/api/v1/admin/tags` | 标签列表（含使用计数） | `content:tag` |
| POST | `/api/v1/admin/tags` | 创建标签 | `content:tag:create` |
| DELETE | `/api/v1/admin/tags/{id}` | 删除未被使用的标签（使用中 409） | `content:tag:delete` |

### 17.2 门户

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/public/articles` | 已发布文章分页：`page[number]`、`page[size]`、`category`（分类 code）、`tag`（标签 code）、`keyword` |
| GET | `/api/v1/public/articles/{slug}` | 文章详情（按 `Accept-Language` 本地化，缺失回退 `zh-CN`） |
| GET | `/api/v1/public/categories` | 分类列表（含已发布文章数） |
| GET | `/api/v1/public/tags` | 标签列表（含已发布文章数） |

- 公开接口只返回 `PUBLISHED` 且未删除的文章；列表默认按 `published_at DESC` 排序。
- 搜索为标题/摘要的大小写不敏感子串匹配（`ILIKE`），单页上限 100。
- 文章 URL 约定：门户为 `/{locale}/articles/{slug}`，sitemap 与 hreflang 遵循 ADR-017。
## 18. 短信服务接口

### 18.1 后台接口

路径前缀 `/api/v1/admin/sms`；成功响应遵循 §1，错误按 §2；权限点见括号。

| 方法 | 路径 | 成功 | 说明 |
|---|---|---|---|
| GET | `/providers` | 200 | 服务商列表，密钥只回显掩码（`ops:sms:config`） |
| POST | `/providers` | 201 + `Location` | 新建服务商（`ops:sms:config`），配置按 `provider_type` 校验 |
| PATCH | `/providers/{code}` | 200 | 修改/启停/设默认；密钥留空表示不修改 |
| POST | `/providers/{code}/test` | 202 | 测试发送（`ops:sms:send`），返回 `traceId` 供日志关联 |
| POST | `/providers/{code}/reencrypt` | 204 | 按当前 active key 重新加密该配置（`ops:sms:config`） |
| GET | `/signatures` | 200 | 签名列表，含厂商审核状态（`ops:sms:config`） |
| POST | `/signatures` | 201 + `Location` | 新建签名 |
| PATCH | `/signatures/{id}` | 200 | 修改/启停 |
| GET | `/template-bindings` | 200 | 模板绑定列表（`ops:sms:template`） |
| PUT | `/template-bindings/{templateId}` | 204 | 整体替换绑定（按 provider/country/locale） |
| GET | `/logs` | 200 | 分页；过滤：模板、服务商、结果、送达状态（取最新回执）、号码掩码、时间范围（`ops:sms:log`） |
| GET | `/logs/export` | 200（`text/csv`） | 导出，字段脱敏（`ops:sms:log`） |
| GET | `/stats` | 200 | 发送量与成本估算：`from`/`to`（默认近 30 天）、按服务商汇总（`ops:sms:log`） |
| POST | `/logs/{id}/resend` | 202 | 手动重发，原记录不变（`ops:sms:send`） |
| GET | `/campaigns` | 200 | 群发活动分页（`ops:sms:campaign`） |
| POST | `/campaigns` | 201 + `Location` | 创建草稿 |
| PATCH | `/campaigns/{id}` | 200 | 修改草稿（模板、计划时间、限速、预算） |
| POST | `/campaigns/import/preview` | 200 | CSV 预校验（`multipart/form-data`），返回有效/无效/重复/已退订统计与错误行样例 |
| POST | `/campaigns/{id}/recipients/import` | 201 | 确认导入，幂等（唯一约束去重） |
| GET | `/campaigns/{id}/errors` | 200（`text/csv`） | 错误行导出（无效号码、缺失变量、已退订） |
| POST | `/campaigns/{id}/start` | 202 | 开始或恢复；状态或预算非法返回 409 |
| POST | `/campaigns/{id}/pause`、`/cancel` | 204 | 暂停 / 取消（已发部分保留） |
| GET | `/campaigns/{id}/recipients` | 200 | 收件人分页与结果 |
| GET/POST/DELETE | `/optouts` | 200/201/204 | 退订名单维护（`ops:sms:optout`） |

约定：

- 密钥类字段响应一律掩码；请求传空字符串表示「不修改」，传新值则加密覆盖。
- `start` 幂等：已在 `RUNNING` 返回 202；`DONE`/`CANCELLED` 返回 409 Problem Details；
  超出活动预算或全局日限额同样返回 409（`SMS.CAMPAIGN_BUDGET_EXCEEDED`）。
- 导入超过单次上限（默认 5 万行）按 §3 返回 422；预校验结果不落库，确认导入才写收件人。
- 发送日志保持只增不改；送达状态由 `sys_sms_receipt` 的最新一行决定。

### 18.2 公开回调

| 方法 | 路径 | 认证 | 说明 |
|---|---|---|---|
| POST | `/api/v1/public/sms/receipt/{provider}` | 厂商签名 + 时间戳窗口 + IP 白名单 + 限流 | 送达回执；按 `provider_message_id` 幂等追加 |
| POST | `/api/v1/public/sms/inbound/{provider}` | 同上 | 上行消息；写入 `sys_sms_inbound` 并触发退订匹配 |

- 鉴权失败 403、请求体非法 422、超限 429；厂商重试依赖幂等键去重。
- 回调不携带任何密钥；原始报文保存在 `sys_sms_receipt.raw` 与 `sys_sms_inbound.content`。

### 18.3 验证码接口

路径前缀 `/api/v1/auth/sms-code`；匿名可访问但按 IP / 号码频控（限流失败 429，Problem Details 附 `retryAfter`）。

| 方法 | 路径 | 成功 | 说明 |
|---|---|---|---|
| POST | `/send` | 202 | 请求体 `{ scene, phone, locale? }`；场景：`LOGIN`/`RESET_PASSWORD`/`BIND_PHONE`/`SENSITIVE_OPERATION` |
| POST | `/verify` | 204 | 请求体 `{ scene, phone, code }`；错误：`SMS.CODE_INVALID`、`SMS.CODE_EXPIRED`、`SMS.CODE_ATTEMPTS_EXCEEDED` |

- 验证码 6 位、5 分钟有效、最多尝试 5 次；发送冷却 60 秒。
- 响应不返回验证码；成功发送只返回 202 与 `traceId`。
- 场景与业务接口（找回密码、绑定手机）的衔接在实现时接入，不额外暴露验证码明文。
## 19. 审计日志接口

### 19.1 操作日志

路径前缀 `/api/v1/admin/oper-logs`；只读，权限点 `audit:oper-log`。

| 方法 | 路径 | 成功 | 说明 |
|---|---|---|---|
| GET | `/` | 200 | 分页；过滤：`module`、`action`、`result`、`username`、`source`、`targetType`、`targetId`、`traceId`、`ip`、`httpStatus`、`from`、`to`；`targetId` 必须与 `targetType` 同时使用，单独传 `targetId` 返回 422 `VALIDATION_FAILED` |
| GET | `/{id}` | 200 / 404 | 详情：含完整 `params` 与 `changes`；权限点 `audit:oper-log:detail`；不存在 404 |
| GET | `/export` | 200（`text/csv`） | 导出，权限点 `audit:oper-log:export`，上限 5000 行 |

列表项字段：`id`、`username`、`module`、`action`、`targetType`、`targetId`、`targetLabel`、
`source`、`httpMethod`、`path`、`httpStatus`、`result`、`errorCode`、`durationMs`、`affectedCount`、
`ip`、`userAgent`、`deptId`、`traceId`、`createdAt`；**列表不返回 `params` 与 `changes`**。
详情在上述字段外增加 `params`（JSON 字符串）与 `changes`（字段级前后值数组）。

变更项格式：`[{"field":"status","before":"ACTIVE","after":"DISABLED"}]`；敏感字段值为 `***`；
超限时 `changesTruncated=true`、`changesVersion=1`；`changes=[]` 表示已采集且无变化，`null` 表示未采集。

导出 CSV 列：时间、用户、模块、动作、目标类型、目标 ID、目标名称、方法、路径、结果、
错误码、HTTP 状态、影响条数、耗时、IP、UA、变更、traceId。

### 19.2 登录日志

路径 `/api/v1/admin/login-logs`，权限点 `audit:login-log`（导出 `audit:login-log:export`）；
过滤：`username`、`result`、`from`、`to`；返回登录结果、IP、UA、traceId、时间。

### 19.3 审计的审计

导出操作日志、登录日志、用户列表、短信错误行，以及文件下载，本身也写操作日志：
`module` 对应业务模块、`action=export|download`、`affectedCount` 为行数或 1；
不记录导出内容。

### 19.4 保留期

操作日志与登录日志保留期默认 730 天，由部署侧维护脚本清理（见 07 §10）；应用账号对两类日志仅 INSERT / SELECT。

## 20. 邮件服务接口

### 20.1 服务商

路径前缀 `/api/v1/admin/mail/providers`；权限点 `ops:mail:provider`。

| 方法 | 路径 | 成功 | 说明 |
|---|---|---|---|
| GET | `/` | 200 | 列表；`password` 恒为 `***` |
| POST | `/` | 201 + Location | 创建；`code` 唯一 |
| PATCH | `/{code}` | 200 | 更新；`password` 为空表示保持原值 |
| DELETE | `/{code}` | 204 | 删除；默认服务商返回 409 `MAIL.PROVIDER.IN_USE`；首个服务商自动成为默认 |
| POST | `/{code}/verify` | 200 | SMTP 连通性与认证校验；不可达 422 `MAIL.PROVIDER.UNREACHABLE` |

服务商字段：`code`、`name`、`host`、`port`、`tlsMode`(`NONE`/`STARTTLS`/`SSL`)、`username`、
`fromAddress`、`fromName`、`enabled`、`isDefault`、`dailyLimit`、`timeoutMs`。

### 20.2 发送

`POST /api/v1/admin/mail/send`，权限点 `ops:mail:send`：

```json
{ "templateCode": "user.welcome", "to": "alice@example.com", "locale": "zh-CN",
  "variables": { "siteName": "vane" }, "providerCode": null }
```

成功返回 **202**：`{ "logId": "...", "status": "QUEUED", "subject": "...", "body": "..." }`。
渲染后的主题/正文随响应返回供预览，实际投递由 worker 异步完成。

| 场景 | 状态 |
|---|---|
| 模板不存在或未启用 | 404 `MAIL.TEMPLATE.NOT_FOUND` |
| 模板变量缺失 | 422 `MAIL.TEMPLATE.VARIABLE_MISSING` |
| 无启用服务商（`vane.mail.mode=smtp`） | 409 `MAIL.NOT_CONFIGURED` |
| 收件人非法 | 422 `MAIL.RECIPIENT.INVALID` |
| 超过服务商日限额 | 429 `MAIL.QUOTA.EXCEEDED` |

### 20.3 发送日志

路径前缀 `/api/v1/admin/mail/logs`；权限点 `ops:mail:log`，导出 `ops:mail:log:export`。

| 方法 | 路径 | 成功 | 说明 |
|---|---|---|---|
| GET | `/` | 200 | 过滤：`status`、`templateCode`、`to`（收件人：精确地址按 HMAC 匹配，或按 `@domain` 后缀）、`timeFrom`、`timeTo` |
| GET | `/{id}` | 200 / 404 | 详情 |
| GET | `/export` | 200（`text/csv`） | 导出，上限 5000 行 |

列表字段：`id`、`providerCode`、`templateCode`、`locale`、`toMasked`、`toDomain`、`subject`、
`status`、`errorCode`、`attempts`、`durationMs`、`traceId`、`createdAt`、`sentAt`。
不返回完整收件人地址、收件人密文、渲染变量与任何凭据。

### 20.5 兼容调整

- `POST /admin/message-templates/{code}/send` 改为调用邮件服务入队，成功返回 **202**（不再是 200）。
- `GET /admin/message-templates` 权限放宽为 `ops:mail` 或 `ops:sms`（短信页绑定模板需要读取列表）。

### 20.4 保留期

发送日志默认保留 180 天，由 `mail-log-retention` 任务清理（07 §10）。

