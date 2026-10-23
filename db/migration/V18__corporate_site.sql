-- =====================================================================
-- V18: 官网内容模型（ADR-025）
-- 说明：
--   * 产品沿用「主体 + _i18n」模式：cms_product 主体，cms_product_i18n 多语言；
--   * 产品媒体集独立成表 cms_product_media，与多语言解耦；
--   * 导航与轮播新增自由文案表 cms_nav_i18n / cms_banner_i18n，
--     既有 label_key / title_key 保留不变（ADR-025 决策 2）；
--   * 权限点定义在代码 PermissionCatalog（ADR-021），本迁移只补角色授权。
-- =====================================================================

-- ---------- 产品主体 ----------
CREATE TABLE cms_product (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    code          text        NOT NULL,
    slug          text        NOT NULL,
    group_code    text,
    cover_file_id uuid REFERENCES sys_file (id),
    video_file_id uuid REFERENCES sys_file (id),
    status        text        NOT NULL DEFAULT 'DRAFT',
    sort_no       integer     NOT NULL DEFAULT 0,
    published_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    deleted_at    timestamptz,
    CONSTRAINT ck_cms_product_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
COMMENT ON TABLE cms_product IS '产品主体；名称、摘要、正文与特性按语言存 cms_product_i18n';

CREATE UNIQUE INDEX uk_cms_product_code ON cms_product (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_cms_product_slug ON cms_product (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_cms_product_status_sort ON cms_product (status, sort_no, published_at DESC);
CREATE INDEX idx_cms_product_group ON cms_product (group_code);

-- ---------- 产品多语言 ----------
CREATE TABLE cms_product_i18n (
    product_id uuid NOT NULL REFERENCES cms_product (id) ON DELETE CASCADE,
    locale     text NOT NULL,
    name       text NOT NULL,
    summary    text,
    body       text,
    features   jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, locale)
);
COMMENT ON TABLE cms_product_i18n IS '产品多语言名称、摘要、正文与特性（features 为 [{"title","description"}] 数组）';

-- ---------- 产品媒体集 ----------
CREATE TABLE cms_product_media (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    product_id uuid NOT NULL REFERENCES cms_product (id) ON DELETE CASCADE,
    file_id    uuid NOT NULL REFERENCES sys_file (id),
    kind       text        NOT NULL,
    sort_no    integer     NOT NULL DEFAULT 0,
    caption    text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_cms_product_media_kind CHECK (kind IN ('IMAGE', 'VIDEO'))
);
COMMENT ON TABLE cms_product_media IS '产品图集与视频；媒体本身无语言属性，caption 为运营备注';

CREATE INDEX idx_cms_product_media_product ON cms_product_media (product_id, sort_no);

-- ---------- 导航自由文案 ----------
CREATE TABLE cms_nav_i18n (
    nav_id uuid NOT NULL REFERENCES cms_nav (id) ON DELETE CASCADE,
    locale text NOT NULL,
    label  text NOT NULL,
    PRIMARY KEY (nav_id, locale)
);
COMMENT ON TABLE cms_nav_i18n IS '导航自由文案；缺失时回退 cms_nav.label_key';

-- ---------- 轮播自由文案 ----------
CREATE TABLE cms_banner_i18n (
    banner_id uuid NOT NULL REFERENCES cms_banner (id) ON DELETE CASCADE,
    locale    text NOT NULL,
    title     text NOT NULL,
    PRIMARY KEY (banner_id, locale)
);
COMMENT ON TABLE cms_banner_i18n IS '轮播自由文案；缺失时回退 cms_banner.title_key';

-- ---------- 权限授权 ----------
-- 权限点定义见代码 PermissionCatalog：
--   content:product        菜单「内容运营 / 产品」
--   content:product:media  产品媒体集维护
INSERT INTO iam_role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM iam_role r
 CROSS JOIN (VALUES ('content:product'), ('content:product:media')) AS p(code)
 WHERE r.code IN ('SUPER_ADMIN', 'ADMIN') AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;
