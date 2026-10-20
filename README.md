# vane

面向小微企业的**前后台管理模板**：后台管理端 + 前台门户端共用一套后端与账号体系。技术栈、接口契约、国际化、数据模型、安全基线均按现行开放标准锁定，可作为新项目起点整体复制到新机构使用。

| 入口 | 地址 |
|---|---|
| 后台管理端 | `http://localhost:5174/admin/` |
| 前台门户端 | `http://localhost:5175/` |
| 后端接口 | `http://localhost:8080/api/v1` |

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
tby/
├─ docs/             设计文档（需求、契约、数据模型、ADR 等 11 个分册）
├─ db/migration/     Flyway 脚本（唯一建库来源）
├─ vane-server/      后端（详见 vane-server/README.md）
├─ vane-web/         前端（详见 vane-web/README.md）
├─ Dockerfile / vane-web/Dockerfile / nginx.conf
└─ docker-compose.yml   postgres + valkey + app + web
```

## 快速开始

```powershell
# 1. 依赖容器
docker run -d --name vane-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=vane -p 55432:5432 postgres:18
docker run -d --name vane-valkey -p 6379:6379 valkey/valkey:9

# 2. 后端（需设 DB_URL / DB_USERNAME / DB_PASSWORD / AUTH_COOKIE_SECURE=false，详见 vane-server/README.md）
cd vane-server
$env:JAVA_HOME = 'C:\Users\byqy\.jdks\temurin-25.0.3'
.\mvnw.cmd -B -DskipTests package
java -jar vane-bootstrap\target\vane-bootstrap-0.1.0-SNAPSHOT.jar

# 3. 前端
cd ..\vane-web
$env:PATH = 'C:\nvm4w\nodejs;' + $env:PATH
pnpm install
pnpm dev:admin     # 5174
pnpm dev:portal    # 5175
```

或用 Compose 一次拉起（`web` 暴露在 `${HTTP_PORT:-8080}`）：

```powershell
Copy-Item .env.example .env    # 按需改密码与 MFA 密钥
docker compose up -d --build
```

默认账号：库中无超级管理员时，应用创建 `admin`；密码取 `vane.iam.bootstrap-admin.password`，未配置则随机生成并只在启动日志打印一次，首次登录强制改密。本地开发库中已有账号 `admin / Admin@12345`。

## 文档

| 文档 | 作用 |
|---|---|
| [`docs/README.md`](docs/README.md) | 文档索引与阅读顺序 |
| [`需求设计总纲`](docs/需求设计总纲.md) | 范围、文档体系、决策、里程碑 |
| [`docs/02`](docs/02-需求规格说明.md) | 角色、权限模型、功能需求与优先级 |
| [`docs/03`](docs/03-HTTP语义与错误模型规范.md) | HTTP 契约：状态码、Problem Details、错误码 |
| [`docs/01`](docs/01-技术栈与规范基线.md) | 版本基线、工程结构、编码规范 |
| [`vane-server/README.md`](vane-server/README.md) | 后端：模块、构建运行、配置、迁移、测试 |
| [`vane-web/README.md`](vane-web/README.md) | 前端：结构、开发构建、测试、编码约定 |
| [`docs/10-ADR/`](docs/10-ADR/README.md) | 16 条架构决策 |

## 已知限制

| 项 | 状态 |
|---|---|
| Playwright E2E | 未接入（浏览器二进制下载受网络限制） |
| Docker 镜像实际构建 | 本机访问 Docker Hub 受限，镜像未实测构建；`docker compose config` 已通过 |
| 许可证 | 仓库未附 `LICENSE`，复用前按机构要求补充 |
