# ADR-016：Element Plus 按需引入

| 项 | 值 |
|---|---|
| 状态 | 已接受 |
| 日期 | 2026-10-18 |
| 决策人 | 项目负责人 |
| 影响范围 | 前端构建、包体、依赖清单 |

## 背景

后台端使用 Element Plus 2.14.7，此前通过 `app.use(ElementPlus)` 整包注册。构建产物单 JS 文件 1.16 MB（gzip 约 377 KB），而实际只用到约 20 个组件与 4 个 API（`ElMessage`、`ElMessageBox`、`v-loading`、`ElConfigProvider`）。

`docs/08` 要求首屏体积可控；整包引入使首屏加载与冷启动明显偏重，且不利于后续按页拆包。

## 决策

采用 **按需引入**，用两个构建期插件：

| 依赖 | 作用 |
|---|---|
| `unplugin-vue-components` | 模板里的 `el-*` 组件与 `ElConfigProvider` 自动解析并注册 |
| `unplugin-auto-import` | 自动生成组件解析所需的导入声明（含 resolver 提供的样式导入） |

配以 `ElementPlusResolver`。以下内容仍需显式处理：

- **指令**：`v-loading` 由 `ElLoading.directive` 手动注册（插件不解析指令）。
- **函数式组件**：`ElMessage`、`ElMessageBox` 保持显式 `import`，并显式引入其样式，避免丢失样式。

不引入 `unplugin-element-plus`（与本方案重复，且只解决样式不解决组件解析）。

## 影响

- 新增 **构建期** 依赖两项，不进入运行时产物。
- `main.ts` 不再 `app.use(ElementPlus)` 与整包 CSS；改为注册 `v-loading` 指令 + 引入必要基础样式。
- 组件使用方式不变（模板仍写 `el-*`），业务代码零改动。
- 保持 Element Plus 版本不变（2.14.7），不涉及运行时依赖升级。

## 备选方案

| 方案 | 优点 | 不可选原因 |
|---|---|---|
| 维持整包引入 | 零配置、无样式丢失风险 | 包体过大，与 `docs/08` 冲突 |
| 手动逐组件 import | 无新依赖 | 组件量大（20+），样式遗漏风险高，维护成本高 |
| 换用其他组件库 | — | 违反 01 §2 版本基线 |
