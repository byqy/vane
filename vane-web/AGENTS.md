# AGENTS.md — vane-web

前端工程的代理说明。仓库级规则见上级 [`AGENTS.md`](../AGENTS.md)，本文只写前端特有约束。

## 技术栈

| 项 | 版本 |
|---|---|
| Vue | 3.5.44 |
| Vite | 8.3.4（Rolldown） |
| TypeScript | 7.0.2 |
| Vue Router | 5.4.0 |
| Pinia | 4.1.0 |
| Element Plus | 2.14.7 |
| axios | 1.20.0 |

Node **26**、pnpm **12**。不要引入未在 `docs/01` 版本基线中的依赖；确需新增先提 ADR。

## 结构

```text
vane-web/
├─ packages/shared/   请求层、类型
├─ apps/admin/        后台管理端，base=/admin/，端口 5174
└─ apps/portal/       门户端，base=/，端口 5175
```

## 命令

```powershell
$env:PATH = 'C:\nvm4w\nodejs;' + $env:PATH
pnpm install
pnpm -r typecheck
pnpm -r build
pnpm -r test   # shared + admin + portal
pnpm --filter @vane/admin dev
```

## 硬性规则

1. **所有请求走 `@vane/shared` 的 `http`**，不要在组件里直接用 axios。
2. **access 令牌只存内存**（D-07）。不得写入 localStorage / sessionStorage / Cookie；刷新页面靠 refresh Cookie 恢复。
3. **不要自己拼错误提示**：用 `problemOf(e)` 取 Problem Details，展示 `detail`，必要时带上 `traceId`。
4. **菜单由后端下发**（`/auth/me` 的 `menus`），前端不硬编码导航；未实现的路由走占位页。
5. **界面文案入仓库**（D-09）：文案统一加到 `@vane/shared` 的 `src/locales/<locale>.ts`（语言清单在 `i18n.ts`），用 `useI18n()` 取，不在模板里硬编码中文。
6. **状态处理要完整**：加载态、空态、错误态三态齐全，不能只写成功路径。
7. **类型**：接口类型集中放在 `shared/src/types.ts`，不要在各页面重复定义同名结构。
8. **时间显示与提交**：接口时间一律 UTC ISO；界面展示用 `formatDateTime` / `formatDate`，日期选择器回填用 `toDateTimeInput`、提交用 `toIsoInstant`；不得直接渲染 `createdAt` 等原始字符串，也不得在本地时间串后拼 `Z`。

## 已知限制

| 限制 | 说明 |
|---|---|
| `vue-tsc` 不可用 | 与 TypeScript 7 不兼容，类型检查用 `tsc --noEmit`；`.vue` 模板暂不做类型检查 |
| Element Plus 按需引入 | 已用 unplugin 按需（ADR-016）；新增组件直接写 `el-*` 即可，函数式组件（`ElMessage` 等）样式需在 `main.ts` 显式引入 |
| 前端测试 | Vitest：`packages/shared` 40 个（请求层、Problem 解析、20 种语言文案一致性、语言名称按界面语言翻译、页面内帮助与字段级帮助文案键、时区格式化与转换、SEO 元数据）+ `apps/portal` 18 个（公共页头导航与语言切换、未翻译导航 key 降级、语言切换后公告/内容页重新拉取、文章列表筛选与搜索参数、文章详情与 404、文章页语言切换重载、文章/列表/分类/404 的 SEO 与 Article JSON-LD、首页最新文章）+ `apps/admin` 146 个组件测试（含路由守卫、回车提交、页面内帮助抽屉、字段级帮助气泡）：登录/改密（含登录前语言切换）/占位页、用户/角色/组织/字典/参数/导航/公告/内容页/文章/分类/标签/轮播/任务/文件/消息模板/日志/健康/权限点各页面的渲染与写操作、20 种语言切换与 RTL 方向、多语言译文编辑器；Playwright E2E 尚未做 |
| 文案残留 | 视图中已无硬编码中文（仅剩代码注释与示例占位符；语言资源在 @vane/shared/src/locales/） |
| 门户语言在 URL | 门户语言由 URL 决定（`/{locale}/...`），不要只改 i18n 而不导航；SEO 元数据由 `applySeo` 写入 |
| 开发服务器监听 | Vite 的 `server.host` 设为 `::`：Windows 下 Vite 默认只监听 `::1`，浏览器访问 `127.0.0.1` 会等约 2 秒后失败；该配置只影响本机开发 |
| workspace 包热更新 | `packages/shared` 在应用 root 之外，修改其 locale/类型后 Vite 不一定热更新；页面出现原始 key 时重启对应 dev server |

## 当前进度

- `shared`：请求封装（Bearer、`Accept-Language`、`X-Request-Id`、401 单飞刷新）、Problem Details 解析、类型、**20 种语言文案资源（`src/locales/*.ts`、`src/locales/help/*.ts`、`src/locales/help/field/*.ts`、`src/locales/perm/*.ts`）**、页面内帮助与字段级帮助文案约定（`help.ts` 的 `helpPages`、`field-help.ts` 的 `fieldHelpKeys`）
- `admin`：Element Plus 按需引入（JS 1.16 MB → 687 KB）、18 个菜单全部实现；用户管理 CRUD/重置密码/角色分配/过滤；公告、内容页、轮播、任务均已支持编辑（含投放/到期时间、启停）；角色支持自定义组织授权；字典支持类型与字典项 CRUD（中英标签）；新增消息模板页（编辑译文 + 发送测试）；任务页可查看执行日志；文件页支持上传、预览、下载；操作日志页支持来源/目标/traceId/HTTP 状态筛选与按需拉取的详情抽屉（字段级变更前后表）、登录日志与用户列表支持导出 CSV；消息模板页升级为「邮件服务」三页签（模板 / SMTP 服务商 / 发送日志）；全部列表页有空态；新增自助改密页与「首登强制改密」路由守卫；新增文章/分类/标签三页（多语言标题/摘要/正文、发布/下线、标签多选与新建、分类与标签 CRUD）；19 个菜单页标题接入页面内帮助（`HelpTip` 打开的「?」抽屉）+ 13 个页面 33 个关键表单字段接入字段级帮助（`FieldHelp` 气泡），文案覆盖 20 种语言；权限点页与角色授权树改用权限码（`nameKey` 由 code 派生）并显示中文名称；新增「短信服务」页（服务商配置、签名、模板绑定、发送日志、群发任务、退订名单、上行消息七个页签；日志含送达状态与回执明细，群发含 CSV 预校验/导入与错误行下载）
- `portal`：页头导航（消费 `/public/navs`）、轮播位（消费 `/public/banners` + 公开图片）、公告列表、内容页（`/:slug` → `/public/pages/{slug}`）、文章列表（分类/标签筛选、关键词搜索、分页）、文章详情（HTML 正文 + Article JSON-LD）、分类页、20 种语言切换

## 不要做

- 不要在前端承担权限判断的安全职责——服务端才是边界，前端只做展示层过滤。
- 不要绕过 `http` 直接拼 `/api` 请求，否则会丢失语言头、traceId 与刷新逻辑。
- 不要提交 `node_modules/`、`dist/`、`.vite/`。







































