# 贡献指南

感谢参与 vane。本项目是「文档先行」的模板工程，改动前请先了解以下约定。

## 主仓库与提交位置

- **主仓库：GitHub**（`https://github.com/byqy/vane`），Issue 与 Pull Request 都提交到这里。
- **Gitee 是只读镜像**（`https://gitee.com/byqy/vane`），由平台自动同步；镜像仓关闭了 Issue / PR，请勿在那里提交（不会被合并）。

维护者可用 [`scripts/check-mirror.ps1`](scripts/check-mirror.ps1) 自检两平台是否一致（分支指向、标签、工作区状态；默认会 fetch 两个远程，`-NoFetch` 可离线比较已有引用）：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-mirror.ps1
# 退出码：0 一致；1 有差异；2 远程未配置或读取失败
```

## 开发流程

1. Fork 主仓库，从 `main` 切出分支：`feature/<简短说明>`、`fix/<简短说明>` 或 `docs/<简短说明>`。
2. **涉及接口、表结构、错误码或决策的改动，先改文档**（`docs/`），再改实现。
   - 新的技术选型或与既有 ADR 冲突的决定，必须新增 ADR（`docs/10-ADR/ADR-0xx-名称.md`），不得修改已接受 ADR 的结论。
   - 修改文档要 bump 版本号，并同步 `docs/需求设计总纲.md` §3 的状态列。
3. 数据库变更只能**新增** Flyway 脚本（`db/migration/V{n}__描述.sql`），不得修改已执行过的脚本。
4. 提交前跑完门禁（见下）。
5. 提交 Pull Request，描述里写明：改了什么、为什么改、如何验证。

## 提交信息

提交信息用中文或英文均可，建议 `type(scope): 摘要` 形式：

```
feat(content): 产品媒体集支持视频
fix(auth): 修复锁定写库被事务回滚
docs: 同步 V19 示例内容与种子说明
```

## 质量门禁（提交前本地必须通过）

```powershell
# 后端
cd vane-server
.\mvnw.cmd -B test
powershell -ExecutionPolicy Bypass -File .\scripts\verify-coverage.ps1

# 前端
cd ..\vane-web
pnpm -r typecheck
pnpm -r test
pnpm -r build
```

- 后端行覆盖率 ≥ 70%（聚合报告），新增/变更代码 ≥ 80%。
- 新增接口要覆盖 `docs/03` §3 的状态码契约（成功 / 校验失败 / 未认证 / 无权限 / 不存在）。
- 新增错误码必须注册并补齐 20 种语言文案。
- 新增受控表必须在 `docs/05` §7 登记，并补越权测试。
- **禁止**为了让测试通过而放宽断言、跳过用例或用无断言测试凑覆盖率。

## 代码约定

- 后端：Java 25、构造器注入、`record` 优先、Controller 不直接依赖 Repository、错误统一抛 `ApiException`。
- 前端：所有请求走 `@vane/shared` 的 `http`；文案入 `packages/shared/src/locales/`（20 种语言键集合必须一致）；时间统一 UTC ISO；页面三态（加载/空/错误）齐全。
- 不新增未在 `docs/01` 版本基线中的依赖；确需新增先提 ADR。

## 文档写作

- 文档只写当前状态，不写变更历史与过程性描述（历史由 git 承担）。
- 不做竞品对比，只描述目标与约束。
- 表格必须闭合，代码围栏必须配对。

## 行为准则

参与本项目即表示同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。
