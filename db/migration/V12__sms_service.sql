-- V12: 短信服务（ADR-022）：服务商、签名、模板绑定、发送日志、送达回执、群发、收件人、退订、上行。

CREATE TABLE sys_sms_provider (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    provider_code   text        NOT NULL,
    provider_type   text        NOT NULL,
    name            text        NOT NULL,
    config_cipher   text        NOT NULL,
    key_version     text        NOT NULL,
    countries       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    optout_keywords jsonb       NOT NULL DEFAULT '[]'::jsonb,
    enabled         boolean     NOT NULL DEFAULT true,
    is_default      boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid,
    CONSTRAINT uk_sys_sms_provider_code UNIQUE (provider_code),
    CONSTRAINT ck_sys_sms_provider_type CHECK (provider_type IN ('ALIYUN', 'TENCENT', 'GENERIC_HTTP'))
);
COMMENT ON TABLE sys_sms_provider IS '短信服务商配置，config_cipher 为 AES-GCM 密文（ADR-022）';
CREATE UNIQUE INDEX uk_sys_sms_provider_default ON sys_sms_provider ((is_default)) WHERE is_default;

CREATE TABLE sys_sms_signature (
    id                       uuid PRIMARY KEY DEFAULT uuidv7(),
    provider_code            text        NOT NULL,
    sign_name                text        NOT NULL,
    country_code             text        NOT NULL,
    locale                   text        NOT NULL,
    status                   text        NOT NULL DEFAULT 'ACTIVE',
    provider_approval_status text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_at               timestamptz NOT NULL DEFAULT now(),
    updated_by               uuid,
    deleted_at               timestamptz,
    CONSTRAINT ck_sys_sms_signature_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE sys_sms_signature IS '短信签名，按服务商 + 国家 + 语言维护';
CREATE INDEX idx_sys_sms_signature_provider ON sys_sms_signature (provider_code);

CREATE TABLE sys_sms_template_binding (
    id                     uuid PRIMARY KEY DEFAULT uuidv7(),
    template_id            uuid        NOT NULL REFERENCES sys_message_template (id) ON DELETE CASCADE,
    provider_code          text        NOT NULL,
    provider_template_code text        NOT NULL,
    signature_id           uuid        REFERENCES sys_sms_signature (id),
    country_code           text        NOT NULL,
    locale                 text        NOT NULL,
    param_mapping          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at             timestamptz NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz NOT NULL DEFAULT now(),
    updated_by             uuid,
    deleted_at             timestamptz
);
COMMENT ON TABLE sys_sms_template_binding IS '站内模板到厂商模板码、签名与变量映射的绑定';
CREATE UNIQUE INDEX uk_sys_sms_binding
    ON sys_sms_template_binding (template_id, provider_code, country_code, locale)
    WHERE deleted_at IS NULL;

CREATE TABLE sys_sms_log (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    template_code       text        NOT NULL,
    phone_masked        text        NOT NULL,
    phone_hash          text        NOT NULL,
    country_code        text,
    provider_code       text        NOT NULL,
    provider_message_id text,
    campaign_id         uuid,
    result              text        NOT NULL,
    error_code          text,
    duration_ms         integer,
    trace_id            text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sys_sms_log_result CHECK (result IN ('SUCCESS', 'FAIL'))
);
COMMENT ON TABLE sys_sms_log IS '短信发送记录，只增不改不删；送达状态见 sys_sms_receipt';
CREATE INDEX idx_sys_sms_log_pmid ON sys_sms_log (provider_message_id);
CREATE INDEX idx_sys_sms_log_time ON sys_sms_log (created_at DESC);

CREATE TABLE sys_sms_receipt (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    log_id              uuid        NOT NULL REFERENCES sys_sms_log (id) ON DELETE CASCADE,
    provider_message_id text,
    status              text        NOT NULL,
    provider_status     text,
    occurred_at         timestamptz,
    raw                 jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sys_sms_receipt_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'UNKNOWN'))
);
COMMENT ON TABLE sys_sms_receipt IS '短信送达回执，只增不改；最新一行为当前状态';
CREATE INDEX idx_sys_sms_receipt_log ON sys_sms_receipt (log_id, id);
CREATE INDEX idx_sys_sms_receipt_pmid ON sys_sms_receipt (provider_message_id);

CREATE TABLE sys_sms_campaign (
    id                   uuid PRIMARY KEY DEFAULT uuidv7(),
    name                 text        NOT NULL,
    template_id          uuid        NOT NULL REFERENCES sys_message_template (id),
    provider_code        text        NOT NULL,
    signature_id         uuid        REFERENCES sys_sms_signature (id),
    status               text        NOT NULL DEFAULT 'DRAFT',
    scheduled_at         timestamptz,
    throttle_per_second  integer     NOT NULL DEFAULT 100,
    daily_limit          integer,
    budget_amount        numeric(12, 2),
    total                integer     NOT NULL DEFAULT 0,
    sent                 integer     NOT NULL DEFAULT 0,
    failed               integer     NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT now(),
    updated_by           uuid,
    CONSTRAINT ck_sys_sms_campaign_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'DONE', 'CANCELLED'))
);
COMMENT ON TABLE sys_sms_campaign IS '短信群发活动';
CREATE INDEX idx_sys_sms_campaign_status ON sys_sms_campaign (status, scheduled_at);

CREATE TABLE sys_sms_recipient (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    campaign_id         uuid        NOT NULL REFERENCES sys_sms_campaign (id) ON DELETE CASCADE,
    phone_e164          text,
    country_code        text,
    params              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    source_line         integer,
    status              text        NOT NULL DEFAULT 'PENDING',
    provider_message_id text,
    error_code          text,
    attempts            integer     NOT NULL DEFAULT 0,
    sent_at             timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sys_sms_recipient_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'INVALID', 'OPTOUT'))
);
COMMENT ON TABLE sys_sms_recipient IS '群发收件人，含导入校验失败的行（status = INVALID）';
CREATE INDEX idx_sys_sms_recipient_queue ON sys_sms_recipient (campaign_id, status);
CREATE UNIQUE INDEX uk_sys_sms_recipient_phone ON sys_sms_recipient (campaign_id, phone_e164)
    WHERE phone_e164 IS NOT NULL;

CREATE TABLE sys_sms_optout (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    phone_hash  text        NOT NULL,
    scope       text        NOT NULL DEFAULT 'MARKETING',
    source      text        NOT NULL DEFAULT 'MANUAL',
    reason      text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sys_sms_optout_scope CHECK (scope IN ('ALL', 'MARKETING')),
    CONSTRAINT ck_sys_sms_optout_source CHECK (source IN ('MANUAL', 'INBOUND'))
);
COMMENT ON TABLE sys_sms_optout IS '短信退订与黑名单；发送前按 phone_hash 过滤';
CREATE UNIQUE INDEX uk_sys_sms_optout ON sys_sms_optout (phone_hash, scope);

CREATE TABLE sys_sms_inbound (
    id                  uuid PRIMARY KEY DEFAULT uuidv7(),
    provider_code       text        NOT NULL,
    provider_message_id text,
    phone_masked        text        NOT NULL,
    phone_hash          text        NOT NULL,
    content             text        NOT NULL,
    received_at         timestamptz NOT NULL DEFAULT now(),
    processed           boolean     NOT NULL DEFAULT false,
    matched_keyword     text
);
COMMENT ON TABLE sys_sms_inbound IS '短信上行消息（用户回复），退订关键词命中后写 sys_sms_optout';
CREATE INDEX idx_sys_sms_inbound_pending ON sys_sms_inbound (processed, received_at);

-- 权限点授权：短信服务菜单与按钮按 ADR-022 默认矩阵授予内置角色。
INSERT INTO iam_role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM iam_role r
CROSS JOIN (VALUES
    ('ops:sms'), ('ops:sms:config'), ('ops:sms:template'), ('ops:sms:log'),
    ('ops:sms:campaign'), ('ops:sms:send'), ('ops:sms:optout')
) AS p(code)
WHERE r.code = 'SUPER_ADMIN' AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO iam_role_permission (role_id, permission_code)
SELECT r.id, p.code
FROM iam_role r
CROSS JOIN (VALUES
    ('ops:sms'), ('ops:sms:template'), ('ops:sms:log'), ('ops:sms:campaign'),
    ('ops:sms:send'), ('ops:sms:optout')
) AS p(code)
WHERE r.code IN ('ADMIN', 'OPERATOR') AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;
