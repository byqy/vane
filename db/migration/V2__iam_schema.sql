-- =====================================================================
-- V2: IAM 模块（身份与权限）
-- 约定：主键 uuid default uuidv7()；业务实体含审计四列 + deleted_at + version
-- =====================================================================

-- ---------- 组织 ----------
CREATE TABLE iam_dept (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    parent_id      uuid REFERENCES iam_dept (id),
    code           text        NOT NULL,
    name           text        NOT NULL,
    sort_no        integer     NOT NULL DEFAULT 0,
    leader_user_id uuid,
    status         text        NOT NULL DEFAULT 'ACTIVE',
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     uuid,
    deleted_at     timestamptz,
    CONSTRAINT ck_iam_dept_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE iam_dept IS '组织/部门树，数据范围的锚点';

CREATE UNIQUE INDEX uk_iam_dept_code ON iam_dept (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_iam_dept_parent ON iam_dept (parent_id);

-- ---------- 用户 ----------
CREATE TABLE iam_user (
    id                   uuid PRIMARY KEY DEFAULT uuidv7(),
    username             text        NOT NULL,
    password_hash        text        NOT NULL,
    nickname             text,
    email                citext,
    phone                text,
    dept_id              uuid        NOT NULL REFERENCES iam_dept (id),
    status               text        NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled          boolean     NOT NULL DEFAULT false,
    locked_until         timestamptz,
    password_updated_at  timestamptz,
    must_change_password boolean     NOT NULL DEFAULT false,
    locale               text,
    timezone             text,
    last_login_at        timestamptz,
    version              bigint      NOT NULL DEFAULT 0,
    created_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT now(),
    updated_by           uuid,
    deleted_at           timestamptz,
    CONSTRAINT ck_iam_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);
COMMENT ON TABLE iam_user IS '用户账号；注销使用 deleted_at 软删除';

CREATE UNIQUE INDEX uk_iam_user_username ON iam_user (username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_iam_user_email ON iam_user (email) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE INDEX idx_iam_user_dept ON iam_user (dept_id);

ALTER TABLE iam_dept
    ADD CONSTRAINT fk_iam_dept_leader FOREIGN KEY (leader_user_id) REFERENCES iam_user (id);

-- ---------- 角色 ----------
CREATE TABLE iam_role (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    code        text        NOT NULL,
    name        text        NOT NULL,
    data_scope  text        NOT NULL DEFAULT 'SELF',
    builtin     boolean     NOT NULL DEFAULT false,
    status      text        NOT NULL DEFAULT 'ACTIVE',
    sort_no     integer     NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT ck_iam_role_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_iam_role_data_scope CHECK (data_scope IN ('ALL', 'DEPT_AND_CHILD', 'DEPT', 'SELF', 'CUSTOM'))
);
COMMENT ON TABLE iam_role IS '角色与数据范围';

CREATE UNIQUE INDEX uk_iam_role_code ON iam_role (code) WHERE deleted_at IS NULL;

-- ---------- 权限点与菜单 ----------
CREATE TABLE iam_permission (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    parent_id  uuid REFERENCES iam_permission (id),
    code       text        NOT NULL,
    type       text        NOT NULL,
    name_key   text        NOT NULL,
    path       text,
    component  text,
    icon       text,
    sort_no    integer     NOT NULL DEFAULT 0,
    visible    boolean     NOT NULL DEFAULT true,
    status     text        NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ck_iam_permission_type CHECK (type IN ('MENU', 'BUTTON')),
    CONSTRAINT ck_iam_permission_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE iam_permission IS '权限点与菜单树；MENU 参与路由，BUTTON 仅鉴权';

CREATE UNIQUE INDEX uk_iam_permission_code ON iam_permission (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_iam_permission_parent ON iam_permission (parent_id);

-- ---------- 关联表（物理删除） ----------
CREATE TABLE iam_user_role (
    user_id    uuid        NOT NULL REFERENCES iam_user (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL REFERENCES iam_role (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    PRIMARY KEY (user_id, role_id)
);
COMMENT ON TABLE iam_user_role IS '用户-角色关联';
CREATE INDEX idx_iam_user_role_role ON iam_user_role (role_id);

CREATE TABLE iam_role_permission (
    role_id       uuid        NOT NULL REFERENCES iam_role (id) ON DELETE CASCADE,
    permission_id uuid        NOT NULL REFERENCES iam_permission (id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    PRIMARY KEY (role_id, permission_id)
);
COMMENT ON TABLE iam_role_permission IS '角色-权限点关联';
CREATE INDEX idx_iam_role_permission_perm ON iam_role_permission (permission_id);

CREATE TABLE iam_role_dept (
    role_id    uuid        NOT NULL REFERENCES iam_role (id) ON DELETE CASCADE,
    dept_id    uuid        NOT NULL REFERENCES iam_dept (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    PRIMARY KEY (role_id, dept_id)
);
COMMENT ON TABLE iam_role_dept IS '角色的自定义数据范围（仅 data_scope = CUSTOM 时有意义）';
CREATE INDEX idx_iam_role_dept_dept ON iam_role_dept (dept_id);

-- ---------- 刷新令牌族 ----------
CREATE TABLE iam_refresh_token (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    family_id      uuid        NOT NULL,
    user_id        uuid        NOT NULL REFERENCES iam_user (id),
    token_hash     text        NOT NULL,
    client_type    text        NOT NULL,
    device_id      text,
    user_agent     text,
    ip             inet,
    issued_at      timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    used_at        timestamptz,
    revoked_at     timestamptz,
    replaced_by_id uuid REFERENCES iam_refresh_token (id),
    CONSTRAINT ck_iam_refresh_token_client CHECK (client_type IN ('WEB', 'APP'))
);
COMMENT ON TABLE iam_refresh_token IS '刷新令牌族；一次性轮换与复用检测见 03 §14';

CREATE UNIQUE INDEX uk_iam_refresh_token_hash ON iam_refresh_token (token_hash);
CREATE INDEX idx_iam_refresh_token_family ON iam_refresh_token (family_id);
CREATE INDEX idx_iam_refresh_token_user ON iam_refresh_token (user_id);
CREATE INDEX idx_iam_refresh_token_expires ON iam_refresh_token (expires_at);

-- ---------- MFA ----------
CREATE TABLE iam_mfa_totp (
    id             uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id        uuid        NOT NULL REFERENCES iam_user (id) ON DELETE CASCADE,
    secret_cipher  text        NOT NULL,
    key_version    integer     NOT NULL DEFAULT 1,
    confirmed_at   timestamptz,
    last_used_step bigint,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE iam_mfa_totp IS 'TOTP 密钥，AES-256-GCM 密文存储';
CREATE UNIQUE INDEX uk_iam_mfa_totp_user ON iam_mfa_totp (user_id);

CREATE TABLE iam_mfa_recovery_code (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid        NOT NULL REFERENCES iam_user (id) ON DELETE CASCADE,
    code_hash  text        NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE iam_mfa_recovery_code IS 'MFA 恢复码，一次性使用';
CREATE INDEX idx_iam_mfa_recovery_user ON iam_mfa_recovery_code (user_id);

-- ---------- 登录日志（只增） ----------
CREATE TABLE iam_login_log (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    user_id    uuid,
    username   text        NOT NULL,
    result     text        NOT NULL,
    detail     text,
    ip         inet,
    user_agent text,
    trace_id   text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_iam_login_log_result CHECK (result IN (
        'SUCCESS', 'FAIL_PASSWORD', 'FAIL_MFA', 'FAIL_LOCKED', 'FAIL_DISABLED', 'FAIL_CAPTCHA'))
);
COMMENT ON TABLE iam_login_log IS '登录日志，只增不改不删';

CREATE INDEX idx_iam_login_log_time ON iam_login_log (created_at DESC);
CREATE INDEX idx_iam_login_log_user_time ON iam_login_log (user_id, created_at DESC);
