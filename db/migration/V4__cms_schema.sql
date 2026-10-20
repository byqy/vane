-- =====================================================================
-- V4: CMS 模块（内容）
-- =====================================================================

-- ---------- 内容页 ----------
CREATE TABLE cms_page (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    slug          text        NOT NULL,
    status        text        NOT NULL DEFAULT 'DRAFT',
    cover_file_id uuid REFERENCES sys_file (id),
    published_at  timestamptz,
    author_id     uuid REFERENCES iam_user (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    deleted_at    timestamptz,
    CONSTRAINT ck_cms_page_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
COMMENT ON TABLE cms_page IS '内容页主体；正文按语言存 cms_page_i18n';

CREATE UNIQUE INDEX uk_cms_page_slug ON cms_page (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_cms_page_status ON cms_page (status);

CREATE TABLE cms_page_i18n (
    page_id    uuid        NOT NULL REFERENCES cms_page (id) ON DELETE CASCADE,
    locale     text        NOT NULL,
    title      text        NOT NULL,
    summary    text,
    body       text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (page_id, locale)
);
COMMENT ON TABLE cms_page_i18n IS '内容页多语言正文；body 为净化后的富文本';

-- ---------- 公告 ----------
CREATE TABLE cms_notice (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    level      text        NOT NULL DEFAULT 'INFO',
    status     text        NOT NULL DEFAULT 'DRAFT',
    pinned     boolean     NOT NULL DEFAULT false,
    publish_at timestamptz,
    expire_at  timestamptz,
    author_id  uuid REFERENCES iam_user (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ck_cms_notice_level CHECK (level IN ('INFO', 'WARN', 'URGENT')),
    CONSTRAINT ck_cms_notice_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'EXPIRED'))
);
COMMENT ON TABLE cms_notice IS '公告主体；正文按语言存 cms_notice_i18n';

CREATE INDEX idx_cms_notice_status_publish ON cms_notice (status, publish_at DESC);
CREATE INDEX idx_cms_notice_pinned ON cms_notice (pinned, publish_at DESC);

CREATE TABLE cms_notice_i18n (
    notice_id uuid NOT NULL REFERENCES cms_notice (id) ON DELETE CASCADE,
    locale    text NOT NULL,
    title     text NOT NULL,
    body      text,
    PRIMARY KEY (notice_id, locale)
);
COMMENT ON TABLE cms_notice_i18n IS '公告多语言正文';

-- ---------- 导航 ----------
CREATE TABLE cms_nav (
    id              uuid PRIMARY KEY DEFAULT uuidv7(),
    parent_id       uuid REFERENCES cms_nav (id),
    position        text        NOT NULL,
    label_key       text        NOT NULL,
    target          text,
    open_in_new_tab boolean     NOT NULL DEFAULT false,
    sort_no         integer     NOT NULL DEFAULT 0,
    status          text        NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid,
    deleted_at      timestamptz,
    CONSTRAINT ck_cms_nav_position CHECK (position IN ('HEADER', 'FOOTER')),
    CONSTRAINT ck_cms_nav_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE cms_nav IS '前台导航树；文案用 i18n key，不入库';

CREATE INDEX idx_cms_nav_position_sort ON cms_nav (position, sort_no);

-- ---------- 轮播 / 宣传位 ----------
CREATE TABLE cms_banner (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    position      text        NOT NULL,
    image_file_id uuid        NOT NULL REFERENCES sys_file (id),
    title_key     text,
    link_url      text,
    sort_no       integer     NOT NULL DEFAULT 0,
    status        text        NOT NULL DEFAULT 'ACTIVE',
    start_at      timestamptz,
    end_at        timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    deleted_at    timestamptz,
    CONSTRAINT ck_cms_banner_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE cms_banner IS '首页轮播与宣传位';

CREATE INDEX idx_cms_banner_position_sort ON cms_banner (position, sort_no);
