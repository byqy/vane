# vane

面向小微企业的**前后台管理模板**：后台管理端 + 前台门户端共用一套后端与账号体系。技术栈、接口契约、国际化、数据模型、安全基线均按现行开放标准锁定，可作为新项目起点整体复制到新机构使用。

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](docs/01-技术栈与规范基线.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f.svg)](docs/01-技术栈与规范基线.md)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](docs/01-技术栈与规范基线.md)
[![CI](https://github.com/byqy/vane/actions/workflows/ci.yml/badge.svg)](https://github.com/byqy/vane/actions/workflows/ci.yml)

> **主仓库在 GitHub，Gitee 为只读镜像**。Issue 与 Pull Request 请提交到 [GitHub](https://github.com/byqy/vane)；Gitee 镜像（[gitee.com/byqy/vane](https://gitee.com/byqy/vane)）由平台镜像自动同步，可能略有延迟，请以 GitHub 为准。

## 特性

- **后台 + 门户双端**：Vue 3 + Element Plus 管理端、纯 SPA 门户端，共用一套 `packages/shared`
- **真实 HTTP 语义**：资源式响应、RFC 9457 Problem Details、错误码 `域.资源.原因`、traceId 贯穿
- **权限与数据范围**：RBAC + 五级数据范围（全部 / 本组织及以下 / 本组织 / 仅本人 / 自定义），查询层统一注入
- **20 种语言**：界面、校验消息、错误码、模板、字典全链路多语言；语言前缀路由 + 内容按语言分行存储
- **内容体系**：公告、内容页、导航、轮播、文章/分类/标签/搜索，以及官网产品（图集 + 视频 + 特性）
- **消息能力**：邮件（SMTP 服务商、模板、异步队列与重试）、短信（服务商、模板绑定、群发、回执与上行）
- **审计**：操作日志与登录日志、字段级变更前后值、CSV 导出、保留期清理
- **可复制**：文档先行（11 份分册 + 25 篇 ADR），表结构由 Flyway 单一来源管理

## 架构

```text
┌──────────────┐   ┌──────────────┐
│  apps/admin  │   │ apps/portal  │   Vue 3 + Vite，共用 packages/shared
└──────┬───────┘   └──────┬───────┘
       │        /api/v1   │
       └────────┬─────────┘
        ┌───────▼────────┐
        │  vane-server   │   Spring Boot 4 / Java 25，9 个 Maven 模块
        └───┬────────┬───┘
      ┌─────▼──┐  ┌──▼──────┐
      │Postgres│  │ Valkey  │
      └────────┘  └─────────┘
```

```text
vane/
├─ docs/             设计文档（11 份分册 + 25 篇 ADR）
├─ db/migration/     Flyway 脚本（唯一建库来源）
├─ vane-server/      后端（详见 vane-server/README.md）
├─ vane-web/         前端（详见 vane-web/README.md）
├─ Dockerfile / vane-web/Dockerfile / nginx.conf
└─ docker-compose.yml   postgres + valkey + app + web
```

| 入口 | 地址（本地开发） |
|---|---|
| 后台管理端 | `http://localhost:5174/admin/` |
| 前台门户端 | `http://localhost:5175/` |
| 后端接口 | `http://localhost:8080/api/v1` |

## 环境要求

| 项 | 版本 |
|---|---|
| JDK | 25（Temurin） |
| Maven | 用仓库自带 `vane-server/mvnw`（3.10.0），无需安装 |
| Node / pnpm | Node 26 LTS、pnpm 12 |
| 数据库 | PostgreSQL 18、Valkey 9（Docker 建议） |

## 快速开始

```powershell
# 1. 依赖容器
docker run -d --name vane-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=vane -p 55432:5432 postgres:18
docker run -d --name vane-valkey -p 6379:6379 valkey/valkey:9

# 2. 后端（先设置 JAVA_HOME 指向 JDK 25；数据库连接见 vane-server/README.md）
cd vane-server
.\mvnw.cmd -B -DskipTests package
java -jar vane-bootstrap\target\vane-bootstrap-0.1.0-SNAPSHOT.jar

# 3. 前端
cd ..\vane-web
pnpm install
pnpm dev:admin     # 5174
pnpm dev:portal    # 5175
```

或用 Compose 一次拉起（`web` 暴露在 `${HTTP_PORT:-8080}`）：

```powershell
Copy-Item .env.example .env    # 按需改密码与 MFA 密钥
docker compose up -d --build
```

**初始账号**：库中无超级管理员时，应用创建 `admin`，密码取 `vane.iam.bootstrap-admin.password`；未配置则随机生成并只在启动日志打印一次，首次登录强制改密。本地开发库中的 `admin / Admin@12345` 只是示例数据，请勿用于任何真实环境。

## 文档

文档先行是本项目的硬性规则：接口、表结构、错误码都以 `docs/` 为准，实现与文档冲突时改实现。

| 文档 | 作用 |
|---|---|
| [`docs/README.md`](docs/README.md) | 文档索引与阅读顺序 |
| [`需求设计总纲`](docs/需求设计总纲.md) | 范围、文档体系、决策、里程碑 |
| [`docs/01`](docs/01-技术栈与规范基线.md) | 版本基线、工程结构、编码规范 |
| [`docs/02`](docs/02-需求规格说明.md) | 角色、权限模型、功能需求与优先级 |
| [`docs/03`](docs/03-HTTP语义与错误模型规范.md) | HTTP 契约：状态码、Problem Details、错误码 |
| [`docs/05`](docs/05-数据模型与数据字典.md) | 表结构、索引、约束、迁移 |
| [`docs/10-ADR/`](docs/10-ADR/README.md) | 25 篇架构决策记录 |
| [`vane-server/README.md`](vane-server/README.md) | 后端：模块、构建运行、配置、迁移、测试 |
| [`vane-web/README.md`](vane-web/README.md) | 前端：结构、开发构建、测试、编码约定 |

## 质量门禁

| 范围 | 要求 |
|---|---|
| 后端 | `mvnw test` 全绿；行覆盖率 ≥ 70%（聚合报告），新增代码 ≥ 80% |
| 前端 | `pnpm -r typecheck` / `pnpm -r test` / `pnpm -r build` 全绿 |
| 契约 | 错误码「一码一状态」、每码 20 语言文案齐备；不破坏 `docs/03` 契约 |

## 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。安全问题请按 [SECURITY.md](SECURITY.md) 私下报告，不要开公开 Issue。

## 版本说明

**当前为 0.x 草案阶段**：文档版本为 `v0.x`，接口、表结构与内部模块可能在不另行通知的情况下调整；不建议在未锁版本的生产项目中直接跟随 `main`。

## 许可证

[MIT](LICENSE) © 2026 byqy
