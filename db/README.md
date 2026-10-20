# 数据库迁移脚本

vane 模板的 PostgreSQL 18 建库脚本，按 Flyway 命名规范组织。

## 脚本顺序

| 脚本 | 内容 |
|---|---|
| `V1__extensions_and_common.sql` | `citext` 扩展；主键使用 PG 18 内置 `uuidv7()` |
| `V2__iam_schema.sql` | 身份与权限：组织、用户、角色、权限点、关联表、refresh 令牌族、MFA、登录日志 |
| `V3__sys_schema.sql` | 系统与平台：参数、字典、消息模板、编号规则、幂等键、日志、定时任务、附件 |
| `V4__cms_schema.sql` | 内容：内容页、公告、导航、轮播（含多语言表） |
| `V5__seed_reference.sql` | 参考数据：角色、权限点、角色授权、系统参数、字典、消息模板、编号规则 |

> 初始管理员账号**不由 SQL 创建**。应用启动时若检测到无超级管理员，生成随机密码并在日志中一次性输出，同时标记 `must_change_password = true`。

## 执行方式

### 方式一：Flyway（推荐，随应用启动）

将本目录挂载或复制为应用的迁移目录：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

要求数据库为 PostgreSQL 18（`uuidv7()` 为内置函数）。

### 方式二：手动验证（Docker）

```powershell
powershell -ExecutionPolicy Bypass -File .\db\verify.ps1
```

脚本会启动临时 `postgres:18` 容器（端口 55432），依次执行迁移并输出表数量、索引、外键、部分唯一索引与种子数据统计；成功后自动删除容器。加 `-KeepContainer` 可保留容器排查。

## 验证状态

已在 PostgreSQL 18 容器中完整执行通过：29 张表、索引与外键创建成功，种子数据（角色 4、权限点 61、角色授权 143、系统参数 16、字典项 20、模板译文 6）校验通过。

## 约定

- 迁移**只前进不回滚**；破坏性变更走「加列 → 双写 → 迁移数据 → 删旧列」的多版本流程。
- `updated_at` 由应用层维护，不使用数据库触发器。
- 日志类表（`iam_login_log`、`sys_oper_log`、`sys_job_log`）只增不改不删，应用账号不授予 UPDATE / DELETE。
- 关联表（`iam_user_role`、`iam_role_permission`、`iam_role_dept`）物理删除，不设 `deleted_at`。
