-- V17: 邮件服务（SMTP，ADR-024）：服务商配置 + 异步发送队列/日志 + 任务与权限。
CREATE TABLE sys_mail_provider (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    code            text NOT NULL,
    name            text NOT NULL,
    host            text NOT NULL,
    port            integer NOT NULL,
    tls_mode        text NOT NULL DEFAULT 'STARTTLS',
    username        text,
    password_cipher text,
    key_version     text,
    from_address    citext NOT NULL,
    from_name       text,
    enabled         boolean NOT NULL DEFAULT true,
    is_default      boolean NOT NULL DEFAULT false,
    daily_limit     integer NOT NULL DEFAULT 500,
    timeout_ms      integer NOT NULL DEFAULT 10000,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid,
    CONSTRAINT uk_sys_mail_provider_code UNIQUE (code),
    CONSTRAINT ck_sys_mail_provider_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_sys_mail_provider_tls CHECK (tls_mode IN ('NONE', 'STARTTLS', 'SSL')),
    CONSTRAINT ck_sys_mail_provider_limit CHECK (daily_limit > 0),
    CONSTRAINT ck_sys_mail_provider_timeout CHECK (timeout_ms BETWEEN 1000 AND 120000)
);
CREATE UNIQUE INDEX uk_sys_mail_provider_default ON sys_mail_provider (is_default) WHERE is_default;

CREATE TABLE sys_mail_log (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    provider_code   text NOT NULL,
    template_code   text NOT NULL,
    locale          text,
    to_masked       text NOT NULL,
    to_domain       text,
    to_cipher       text,
    to_key_version  text,
    to_hash         text,
    variables       jsonb,
    subject         text,
    status          text NOT NULL,
    error_code      text,
    error_detail    text,
    attempts        integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    duration_ms     integer,
    trace_id        text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    CONSTRAINT ck_sys_mail_log_status CHECK (status IN ('QUEUED', 'SENT', 'FAILED'))
);
CREATE INDEX idx_sys_mail_log_time ON sys_mail_log (created_at DESC);
CREATE INDEX idx_sys_mail_log_status ON sys_mail_log (status, next_attempt_at);
CREATE INDEX idx_sys_mail_log_template ON sys_mail_log (template_code, created_at DESC);
CREATE INDEX idx_sys_mail_log_provider ON sys_mail_log (provider_code, created_at DESC);
CREATE INDEX idx_sys_mail_log_to_hash ON sys_mail_log (to_hash, created_at DESC);

INSERT INTO sys_job (job_code, name_key, handler, cron, params, status, concurrency)
VALUES ('mail-send-worker', 'job.mailSendWorker', 'mail-send-worker', '*/30 * * * * *', NULL, 'ACTIVE', 'FORBID'),
       ('mail-log-retention', 'job.mailLogRetention', 'mail-log-retention', '0 40 3 * * *', NULL, 'ACTIVE', 'FORBID');

INSERT INTO iam_role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM iam_role r
CROSS JOIN (VALUES ('ops:mail:provider'), ('ops:mail:log'), ('ops:mail:log:export')) AS p(code)
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN') AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;
