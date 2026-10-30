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
