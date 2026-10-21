-- V16: 审计日志精细化（ADR-023）：目标级 + 字段级变更 + 上下文。
ALTER TABLE sys_oper_log
    ADD COLUMN source text NOT NULL DEFAULT 'API',
    ADD COLUMN target_type text,
    ADD COLUMN target_id text,
    ADD COLUMN target_label text,
    ADD COLUMN http_status smallint,
    ADD COLUMN user_agent text,
    ADD COLUMN dept_id uuid,
    ADD COLUMN affected_count integer,
    ADD COLUMN changes jsonb,
    ADD COLUMN changes_version smallint NOT NULL DEFAULT 1,
    ADD COLUMN changes_truncated boolean NOT NULL DEFAULT false;

ALTER TABLE sys_oper_log ADD CONSTRAINT ck_sys_oper_log_source CHECK (source IN ('API', 'JOB', 'SYSTEM'));
CREATE INDEX idx_sys_oper_log_target ON sys_oper_log (target_type, target_id, created_at DESC);
CREATE INDEX idx_sys_oper_log_trace ON sys_oper_log (trace_id);

-- 详情（params/changes）单独权限点：SUPER_ADMIN / ADMIN / AUDITOR。
INSERT INTO iam_role_permission (role_id, permission_code)
SELECT r.id, 'audit:oper-log:detail'
FROM iam_role r
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'AUDITOR') AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;
