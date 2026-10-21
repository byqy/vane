-- 审计日志保留期清理（ADR-023 §8、docs/07 §10）。
--
-- 应用账号对 sys_oper_log / iam_login_log 只有 INSERT / SELECT，清理必须由部署侧
-- 维护账号执行。执行前请按备份策略归档。
--
-- 用法：
--   psql "$DB_URL" -v retention_days=730 -f db/maintenance/audit-retention.sql
-- 或使用包装脚本：
--   powershell -ExecutionPolicy Bypass -File .\db\maintenance\audit-retention.ps1

DELETE FROM sys_oper_log
WHERE created_at < now() - make_interval(days => :retention_days);

DELETE FROM iam_login_log
WHERE created_at < now() - make_interval(days => :retention_days);
