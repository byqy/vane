-- =====================================================================
-- V3: SYS 模块（系统与平台）
-- =====================================================================

-- ---------- 系统参数 ----------
CREATE TABLE sys_config (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    config_key  text        NOT NULL,
    value_json  jsonb       NOT NULL,
    value_type  text        NOT NULL,
    description text,
    editable    boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    CONSTRAINT uk_sys_config_key UNIQUE (config_key),
    CONSTRAINT ck_sys_config_type CHECK (value_type IN ('STRING', 'INT', 'BOOL', 'DECIMAL', 'JSON', 'URL'))
);
COMMENT ON TABLE sys_config IS '系统参数，按类型校验；内置只读参数 editable = false';

-- ---------- 字典 ----------
CREATE TABLE sys_dict_type (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    type_code  text        NOT NULL,
    name_key   text        NOT NULL,
    status     text        NOT NULL DEFAULT 'ACTIVE',
    remark     text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ck_sys_dict_type_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE sys_dict_type IS '字典类型；名称走 i18n key';
CREATE UNIQUE INDEX uk_sys_dict_type_code ON sys_dict_type (type_code) WHERE deleted_at IS NULL;

CREATE TABLE sys_dict_item (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    type_id    uuid        NOT NULL REFERENCES sys_dict_type (id),
    item_value text        NOT NULL,
    color      text,
    sort_no    integer     NOT NULL DEFAULT 0,
    status     text        NOT NULL DEFAULT 'ACTIVE',
    builtin    boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ck_sys_dict_item_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE sys_dict_item IS '字典项；标签为运营数据，按语言存 sys_dict_item_i18n';
CREATE UNIQUE INDEX uk_sys_dict_item_value ON sys_dict_item (type_id, item_value) WHERE deleted_at IS NULL;

CREATE TABLE sys_dict_item_i18n (
    item_id uuid NOT NULL REFERENCES sys_dict_item (id) ON DELETE CASCADE,
    locale  text NOT NULL,
    label   text NOT NULL,
    PRIMARY KEY (item_id, locale)
);
COMMENT ON TABLE sys_dict_item_i18n IS '字典项多语言标签，locale 为 BCP 47';

-- ---------- 消息模板 ----------
CREATE TABLE sys_message_template (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    code       text        NOT NULL,
    channel    text        NOT NULL,
    status     text        NOT NULL DEFAULT 'ACTIVE',
    remark     text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ck_sys_message_template_channel CHECK (channel IN ('EMAIL', 'SMS', 'INAPP')),
    CONSTRAINT ck_sys_message_template_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE sys_message_template IS '邮件/短信/站内信模板';
CREATE UNIQUE INDEX uk_sys_message_template_code_channel ON sys_message_template (code, channel) WHERE deleted_at IS NULL;

CREATE TABLE sys_message_template_i18n (
    template_id uuid        NOT NULL REFERENCES sys_message_template (id) ON DELETE CASCADE,
    locale      text        NOT NULL,
    subject     text,
    body        text        NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (template_id, locale)
);
COMMENT ON TABLE sys_message_template_i18n IS '模板多语言正文，body 支持 ICU 变量';

-- ---------- 编号规则 ----------
CREATE TABLE sys_serial_rule (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    rule_code     text        NOT NULL,
    pattern       text        NOT NULL,
    reset_period  text        NOT NULL DEFAULT 'NONE',
    last_value    bigint      NOT NULL DEFAULT 0,
    last_reset_at timestamptz,
    version       bigint      NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    CONSTRAINT uk_sys_serial_rule_code UNIQUE (rule_code),
    CONSTRAINT ck_sys_serial_rule_reset CHECK (reset_period IN ('NONE', 'DAY', 'MONTH', 'YEAR'))
);
COMMENT ON TABLE sys_serial_rule IS '业务编号规则；version 乐观锁防并发重号';

-- ---------- 幂等键 ----------
CREATE TABLE sys_idempotency_key (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    key_hash        text        NOT NULL,
    user_id         uuid,
    request_hash    text        NOT NULL,
    status          text        NOT NULL,
    response_status integer,
    response_body   jsonb,
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    CONSTRAINT uk_sys_idempotency_key_hash UNIQUE (key_hash),
    CONSTRAINT ck_sys_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);
COMMENT ON TABLE sys_idempotency_key IS '幂等键，默认保留 24 小时';
CREATE INDEX idx_sys_idempotency_expires ON sys_idempotency_key (expires_at);

-- ---------- 操作日志（只增） ----------
CREATE TABLE sys_oper_log (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id     uuid,
    username    text,
    module      text NOT NULL,
    action      text NOT NULL,
    http_method text,
    path        text,
    params      jsonb,
    result      text NOT NULL,
    error_code  text,
    duration_ms integer,
    ip          inet,
    trace_id    text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sys_oper_log_result CHECK (result IN ('SUCCESS', 'FAIL'))
);
COMMENT ON TABLE sys_oper_log IS '操作日志，只增不改不删；params 已脱敏';

CREATE INDEX idx_sys_oper_log_time ON sys_oper_log (created_at DESC);
CREATE INDEX idx_sys_oper_log_user_time ON sys_oper_log (user_id, created_at DESC);

-- ---------- 定时任务 ----------
CREATE TABLE sys_job (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    job_code    text        NOT NULL,
    name_key    text        NOT NULL,
    handler     text        NOT NULL,
    cron        text        NOT NULL,
    params      jsonb,
    status      text        NOT NULL DEFAULT 'ACTIVE',
    concurrency text        NOT NULL DEFAULT 'FORBID',
    last_run_at timestamptz,
    next_run_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT ck_sys_job_status CHECK (status IN ('ACTIVE', 'PAUSED')),
    CONSTRAINT ck_sys_job_concurrency CHECK (concurrency IN ('FORBID', 'ALLOW'))
);
COMMENT ON TABLE sys_job IS '定时任务定义';
CREATE UNIQUE INDEX uk_sys_job_code ON sys_job (job_code) WHERE deleted_at IS NULL;

CREATE TABLE sys_job_log (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    job_id      uuid        NOT NULL REFERENCES sys_job (id),
    started_at  timestamptz NOT NULL,
    finished_at timestamptz,
    success     boolean,
    message     text,
    trace_id    text
);
COMMENT ON TABLE sys_job_log IS '任务执行日志，只增';
CREATE INDEX idx_sys_job_log_job_time ON sys_job_log (job_id, started_at DESC);

-- ---------- 附件 ----------
CREATE TABLE sys_file (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    storage        text        NOT NULL,
    object_key     text        NOT NULL,
    original_name  text        NOT NULL,
    mime           text        NOT NULL,
    size_bytes     bigint      NOT NULL,
    sha256         char(64)    NOT NULL,
    width          integer,
    height         integer,
    visibility     text        NOT NULL DEFAULT 'PRIVATE',
    owner_user_id  uuid        REFERENCES iam_user (id),
    biz_type       text,
    biz_id         uuid,
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     uuid,
    deleted_at     timestamptz,
    CONSTRAINT ck_sys_file_storage CHECK (storage IN ('LOCAL', 'S3')),
    CONSTRAINT ck_sys_file_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC'))
);
COMMENT ON TABLE sys_file IS '附件与存储对象；object_key 不对外暴露，访问规则见 03 §15';

CREATE INDEX idx_sys_file_biz ON sys_file (biz_type, biz_id);
CREATE INDEX idx_sys_file_sha ON sys_file (sha256);
