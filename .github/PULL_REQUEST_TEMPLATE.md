## 改了什么

<!-- 一句话说明变更内容与动机 -->

## 类型

- [ ] 新功能（feat）
- [ ] 缺陷修复（fix）
- [ ] 文档（docs）
- [ ] 重构 / 工程（refactor / chore）
- [ ] 破坏性变更（breaking，请在下方说明）

## 关联

<!-- 关联 Issue / ADR，例如 Closes #12、ADR-025 -->

## 验证

<!-- 贴出门禁命令与结果 -->

```powershell
cd vane-server; .\mvnw.cmd -B test
powershell -ExecutionPolicy Bypass -File .\scripts\verify-coverage.ps1
cd ..\vane-web; pnpm -r typecheck; pnpm -r test; pnpm -r build
```

## 检查清单

- [ ] 涉及接口 / 表结构 / 错误码的改动已先更新文档并 bump 版本
- [ ] 新增数据库变更只新增了 Flyway 脚本，未修改已执行脚本
- [ ] 新增逻辑有对应测试，未放宽或删除既有断言
- [ ] 新增错误码已补 20 种语言文案
- [ ] 未提交 `target/`、`node_modules/`、`.env`、密钥或本地配置
- [ ] 破坏性变更已在描述中说明迁移方式
