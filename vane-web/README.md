# vane-web

vane 的前端工程：pnpm workspace，包含「后台管理端 + 前台门户端」两个应用，以及两者共用的请求层与文案资源。

| 应用 | 包名 | 访问地址 | 说明 |
|---|---|---|---|
| 后台管理端 | `@vane/admin` | `http://localhost:5174/admin/` | Element Plus，18 个业务菜单，登录后由 `/auth/me` 下发菜单 |
| 前台门户端 | `@vane/portal` | `http://localhost:5175/` | 无 UI 框架，导航/轮播/公告/内容页/文章来自 `/api/v1/public/*` |
| 共享包 | `@vane/shared` | — | 请求封装、Problem Details 解析、类型、20 种语言文案 |

## 技术栈

| 依赖 | 版本 |
|---|---|
| Vue | 3.5.44 |
| Vite | 8.3.4（Rolldown） |
| TypeScript | 7.0.2 |
| Vue Router | 5.4.0 |
| Pinia | 4.1.0 |
| Element Plus（仅 admin） | 2.14.7 |
| vue-i18n | 11.4.14 |
| axios（仅 shared） | 1.20.0 |
| Vitest / jsdom / @vue/test-utils | 3.2.7 / 27 / 2.4.6 |

Node **26**、pnpm **12**（`packageManager: pnpm@12.14.0`）。版本基线以 `docs/01` 为准，新增依赖先提 ADR。

## 目录结构

```text
vane-web/
├─ packages/shared/
│  └─ src/
│     ├─ http.ts        axios 实例（Bearer / Accept-Language / X-Request-Id / 401 单飞刷新）
│     │                 与 Problem Details 解析（problemOf）、文件下载（downloadFile）
│     ├─ types.ts       接口类型（UserSummary、MeResponse、PageResult …）
│     ├─ i18n.ts        语言清单、母语名称、RTL 标记、Accept-Language 匹配
│     └─ index.ts       统一出口
├─ apps/admin/
│  ├─ src/
│  │  ├─ main.ts        createApp + Element Plus 按需样式 + 语言初始化
│  │  ├─ router.ts      路由表 + 「首登强制改密」守卫
│  │  ├─ stores/auth.ts 登录态（access 只存内存）
│  │  ├─ views/LayoutView.vue    侧边菜单与顶栏（菜单来自后端 menus）
│  │  └─ views/         业务页面（用户/角色/组织/参数/字典/日志/内容/文章/分类/标签/任务/文件/消息模板…）
│  └─ vite.config.ts    base=/admin/，端口 5174，代理 /api → 8080
└─ apps/portal/
   └─ src/
      ├─ router.ts      /、/:locale/articles、/:locale/articles/:slug、/:locale/categories/:code、/:locale/:slug（内容页）
      ├─ seo.ts         站点 / 内容页 / 文章（含 Article JSON-LD）/ 列表页 SEO
      └─ views/         HomeView、PageView、ArticlesView、ArticleView
```

## 前置条件

- Node 26、pnpm 12
- 后端已在 `http://localhost:8080` 运行（前端 `/api` 由 Vite 代理到它，不存在跨域问题）

```powershell
$env:PATH = '<Node 26 安装目录>;' + $env:PATH
node -v    # v26.x
pnpm -v    # 12.x
```

## 安装

```powershell
cd <仓库根>\vane-web
pnpm install
```

若提示构建脚本未批准（pnpm 12 默认拦截依赖的 postinstall），执行一次：

```powershell
pnpm approve-builds --all
```

本项目已把 `esbuild` 加入 `pnpm-workspace.yaml` 的 `allowBuilds`。

## 本地开发

```powershell
pnpm dev:admin     # 后台管理端  http://localhost:5174/admin/
pnpm dev:portal    # 门户端      http://localhost:5175/
pnpm dev:api       # 后端 jar（无 args；读 JAVA_HOME 与 DB_* 环境变量），供 IDE 运行配置调用
```

两个命令等价于 `pnpm --filter @vane/admin dev` / `--filter @vane/portal dev`，可同时运行。

### 在 WebStorm 中启动

1. **Settings → Node.js**：Node interpreter 选 `<Node 26 安装目录>\node.exe`，Package manager 选 `<Node 26 安装目录>\pnpm.cmd`。
2. **Run → Edit Configurations → + → npm**，建两个配置：

   | 字段 | `admin-dev` | `portal-dev` |
   |---|---|---|
   | package.json | `vane-web\package.json` | `vane-web\package.json` |
   | Command | `run` | `run` |
   | Scripts | `dev:admin` | `dev:portal` |

3. 两个配置可同时运行，端口不冲突，左侧运行面板可一键重启。

## 构建

```powershell
pnpm -r build
```

产物在 `apps/*/dist`，由 `vane-web/Dockerfile` 打进 Nginx 镜像：

| 应用 | JS（未压缩） | CSS（未压缩） |
|---|---|---|
| admin | ≈ 1.24 MB | ≈ 178 KB |
| portal | ≈ 521 KB | ≈ 5 KB |

部署形态（`nginx.conf`）：`/` → 门户、`/admin/` → 后台、`/api/` → 反代后端、`/actuator/` 返回 404。

## 测试与检查

```powershell
pnpm -r typecheck    # tsc --noEmit（.vue 模板不做类型检查，见「已知限制」）
pnpm -r test         # shared + portal + admin
pnpm --filter @vane/admin test
```

当前基线：**171** 个测试（shared 27 + portal 18 + admin 126）。新增页面需覆盖「加载态 / 空态 / 错误态」，组件测试优先验证交互与请求参数，不做快照。

## 编码约定（摘要）

完整规则见 [`AGENTS.md`](AGENTS.md)，以下几条约定的破坏性最强：

1. **所有请求走 `@vane/shared` 的 `http`**，禁止组件里直接用 axios，否则丢语言头、traceId 与刷新逻辑。
2. **access 令牌只存内存**，不得写 localStorage / sessionStorage / Cookie；刷新页面靠 refresh Cookie 恢复。
3. **文案入仓库**：所有界面文案加到 `packages/shared/src/i18n.ts` 的 zh/en 两份键集，模板里用 `t('...')`，不硬编码中文；两份键集有测试保证一致。
4. **菜单由后端下发**（`/auth/me` 的 `menus`），前端不硬编码导航；未实现的路径走占位页。
5. **错误提示用 `problemOf(e)`** 取 Problem Details，展示 `detail`，必要时附 `traceId`。
6. **接口类型集中在 `shared/src/types.ts`**，不在页面里重复定义。

## Element Plus 按需引入

admin 使用 `unplugin-vue-components` + `unplugin-auto-import` 的 `ElementPlusResolver`（见 [`../docs/10-ADR/ADR-016-element-plus-on-demand.md`](../docs/10-ADR/ADR-016-element-plus-on-demand.md)）：模板里直接写 `el-*` 即可，**不需要 import**；`ElMessage` / `ElMessageBox` / `ElLoading` 这类函数式 API 的样式需在 `main.ts` 显式引入，否则弹窗无样式。

## 多语言

- 语言清单（20 种）在 `packages/shared/src/i18n.ts`：`zh-CN`、`zh-TW`、`en`、`ja`、`ko`、`ru`、`de`、`fr`、`es`、`pt-BR`、`it`、`nl`、`pl`、`tr`、`uk`、`vi`、`th`、`id`、`ar`、`hi`。
- 每语言文案在 `src/locales/<locale>.ts`，键集合与 `zh-CN` 完全一致；`i18n.test.ts` 校验「键集合 / 占位符 / 无空值 / 是否真的翻译过」。
- 语言协商：localStorage 偏好 → `navigator.language` → 默认 `zh-CN`；请求语言通过 `Accept-Language` 传给后端。
- `ar` 为 RTL：`setLocale` 会设置 `document.documentElement.dir`。
- Element Plus 内置文案（分页、日期选择器）按语言**动态加载**语言包，不进入首屏包。
- 内容（公告 / 内容页 / 文章 / 分类 / 消息模板）通过 `TranslationsEditor` 组件按语言增删译文；字典项标签在字典页分别维护。

## 已知限制

| 限制 | 说明 |
|---|---|
| `vue-tsc` 不可用 | 与 TypeScript 7 不兼容，类型检查用 `tsc --noEmit`，`.vue` 模板暂不做类型检查 |
| Playwright E2E | 未接入（浏览器二进制下载受限） |
| 前端无权限判断的安全职责 | 前端只做展示层过滤，权限判定只认服务端 |
























