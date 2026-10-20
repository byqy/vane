-- 门户 SEO 基础配置：站点名称、描述、对外访问根地址、默认分享图
INSERT INTO sys_config (config_key, value_json, value_type, description, editable) VALUES
  ('seo.site-name',    '"vane 门户"'::jsonb,  'STRING', '门户站点名称（浏览器标题与分享卡片）', true),
  ('seo.description',  '"面向机构的门户站点，发布公告与内容页"'::jsonb, 'STRING', '门户默认描述（未设置页面描述时使用）', true),
  ('seo.base-url',     '"http://localhost:5175"'::jsonb, 'STRING', '门户对外访问根地址（生成 canonical 与 sitemap 用，部署时必须改）', true),
  ('seo.og-image',     '""'::jsonb, 'STRING', '默认分享图 URL（可留空）', true)
ON CONFLICT (config_key) DO NOTHING;
