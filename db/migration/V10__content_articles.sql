-- =====================================================================
-- V10: 内容体系（文章 / 分类 / 标签）
-- 说明：
--   * 分类业务上只有一级：parent_id 字段保留以兼容未来多级，但只允许 null；
--   * 文章与 cms_page 并存：page 用于单页（关于我们/帮助），article 用于列表型内容；
--   * 搜索使用 ILIKE + pg_trgm（中文子串匹配），不引入全文检索扩展。
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------- 文章分类 ----------
CREATE TABLE cms_category (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    parent_id  uuid REFERENCES cms_category (id),
    code       text        NOT NULL,
    sort_no    integer     NOT NULL DEFAULT 0,
    status     text        NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ck_cms_category_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
COMMENT ON TABLE cms_category IS '文章分类（业务上仅一级，parent_id 保留但必须为 null）';

CREATE UNIQUE INDEX uk_cms_category_code ON cms_category (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_cms_category_sort ON cms_category (sort_no);

CREATE TABLE cms_category_i18n (
    category_id uuid NOT NULL REFERENCES cms_category (id) ON DELETE CASCADE,
    locale      text NOT NULL,
    name        text NOT NULL,
    description text,
    PRIMARY KEY (category_id, locale)
);
COMMENT ON TABLE cms_category_i18n IS '文章分类多语言名称与描述';

-- ---------- 文章 ----------
CREATE TABLE cms_article (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    slug          text        NOT NULL,
    category_id   uuid REFERENCES cms_category (id),
    cover_file_id uuid REFERENCES sys_file (id),
    author_id     uuid REFERENCES iam_user (id),
    status        text        NOT NULL DEFAULT 'DRAFT',
    published_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    deleted_at    timestamptz,
    CONSTRAINT ck_cms_article_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
COMMENT ON TABLE cms_article IS '文章主体；正文按语言存 cms_article_i18n';

CREATE UNIQUE INDEX uk_cms_article_slug ON cms_article (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_cms_article_status_published ON cms_article (status, published_at DESC);
CREATE INDEX idx_cms_article_category ON cms_article (category_id);

CREATE TABLE cms_article_i18n (
    article_id uuid NOT NULL REFERENCES cms_article (id) ON DELETE CASCADE,
    locale     text NOT NULL,
    title      text NOT NULL,
    summary    text,
    body       text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (article_id, locale)
);
COMMENT ON TABLE cms_article_i18n IS '文章多语言标题、摘要与正文（正文为 HTML 直出，与 cms_page 一致）';

-- 中文子串搜索：列式 trigram GIN 索引，配合 ILIKE '%关键词%' 使用
CREATE INDEX idx_cms_article_i18n_title_trgm ON cms_article_i18n USING gin (title gin_trgm_ops);
CREATE INDEX idx_cms_article_i18n_summary_trgm ON cms_article_i18n USING gin (summary gin_trgm_ops);

-- ---------- 标签 ----------
CREATE TABLE cms_tag (
    id         uuid PRIMARY KEY DEFAULT uuidv7(),
    code       text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    deleted_at timestamptz
);
COMMENT ON TABLE cms_tag IS '文章标签；标签名直接存 code，界面文案由内容决定';

CREATE UNIQUE INDEX uk_cms_tag_code ON cms_tag (code) WHERE deleted_at IS NULL;

CREATE TABLE cms_article_tag (
    article_id uuid        NOT NULL REFERENCES cms_article (id) ON DELETE CASCADE,
    tag_id     uuid        NOT NULL REFERENCES cms_tag (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    PRIMARY KEY (article_id, tag_id)
);
COMMENT ON TABLE cms_article_tag IS '文章-标签关联（物理删除，整体替换）';

CREATE INDEX idx_cms_article_tag_tag ON cms_article_tag (tag_id);

-- ---------- 权限点：二级菜单 ----------
INSERT INTO iam_permission (id, parent_id, code, type, name_key, path, component, icon, sort_no, visible) VALUES
('20000000-0000-4000-8000-000000000095', '20000000-0000-4000-8000-000000000002', 'content:article',  'MENU', 'menu.content.article',  '/content/article',  'content/article/index',  'file-text', 50, true),
('20000000-0000-4000-8000-000000000096', '20000000-0000-4000-8000-000000000002', 'content:category', 'MENU', 'menu.content.category', '/content/category', 'content/category/index', 'folder',    60, true),
('20000000-0000-4000-8000-000000000097', '20000000-0000-4000-8000-000000000002', 'content:tag',      'MENU', 'menu.content.tag',      '/content/tag',      'content/tag/index',      'tag',       70, true);

-- ---------- 权限点：按钮级 ----------
INSERT INTO iam_permission (id, parent_id, code, type, name_key, sort_no) VALUES
('20000000-0000-4000-8000-000000000098', '20000000-0000-4000-8000-000000000095', 'content:article:create',   'BUTTON', 'perm.content.article.create',   10),
('20000000-0000-4000-8000-000000000099', '20000000-0000-4000-8000-000000000095', 'content:article:update',   'BUTTON', 'perm.content.article.update',   20),
('20000000-0000-4000-8000-000000000100', '20000000-0000-4000-8000-000000000095', 'content:article:delete',   'BUTTON', 'perm.content.article.delete',   30),
('20000000-0000-4000-8000-000000000101', '20000000-0000-4000-8000-000000000095', 'content:article:publish',  'BUTTON', 'perm.content.article.publish',  40),
('20000000-0000-4000-8000-000000000102', '20000000-0000-4000-8000-000000000096', 'content:category:create',  'BUTTON', 'perm.content.category.create',  10),
('20000000-0000-4000-8000-000000000103', '20000000-0000-4000-8000-000000000096', 'content:category:update',  'BUTTON', 'perm.content.category.update',  20),
('20000000-0000-4000-8000-000000000104', '20000000-0000-4000-8000-000000000096', 'content:category:delete',  'BUTTON', 'perm.content.category.delete',  30),
('20000000-0000-4000-8000-000000000105', '20000000-0000-4000-8000-000000000097', 'content:tag:create',       'BUTTON', 'perm.content.tag.create',       10),
('20000000-0000-4000-8000-000000000106', '20000000-0000-4000-8000-000000000097', 'content:tag:delete',       'BUTTON', 'perm.content.tag.delete',       20);

-- ---------- 角色授权 ----------
-- 超级管理员、管理员与运营：内容体系全部权限（与 V5 对 content:* 的授权口径一致）
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM iam_role r
JOIN iam_permission p ON p.deleted_at IS NULL
WHERE r.code IN ('SUPER_ADMIN', 'ADMIN', 'OPERATOR')
  AND (p.code = 'content:article' OR p.code LIKE 'content:article:%'
    OR p.code = 'content:category' OR p.code LIKE 'content:category:%'
    OR p.code = 'content:tag' OR p.code LIKE 'content:tag:%');
