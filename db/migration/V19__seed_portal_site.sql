-- =====================================================================
-- V19: 示例官网内容（ADR-025 决策 8）
-- 说明：
--   * 全部措辞使用通用「示例公司」示例文案，运营上线前可整体替换；
--   * 内容表此前为空表，本迁移提供开箱即用的官网骨架；
--   * 不写入任何二进制素材：封面、演示视频与图集留给运营在后台上传，
--     避免出现指向不存在存储对象的占位记录（图片会 404）。
-- =====================================================================

-- ---------- 站点联系方式（联系我们页读取 site.contact.*） ----------
INSERT INTO sys_config (config_key, value_json, value_type, description, editable) VALUES
  ('site.contact.email',   '"contact@example.com"'::jsonb,      'STRING', '官网联系邮箱', true),
  ('site.contact.phone',   '"+86 000-0000 0000"'::jsonb,        'STRING', '官网联系电话', true),
  ('site.contact.address', '"示例省示例市示例路 1 号"'::jsonb,   'STRING', '官网联系地址', true),
  ('site.contact.hours',   '"周一至周五 09:00-18:00"'::jsonb,    'STRING', '服务时间', true)
ON CONFLICT (config_key) DO NOTHING;

-- ---------- 页头导航 ----------
INSERT INTO cms_nav (id, position, label_key, target, open_in_new_tab, sort_no, status,
                     created_at, updated_at) VALUES
('30000000-0000-4000-8000-0000000000a1', 'HEADER', 'nav.home',     '/',         false, 10, 'ACTIVE', now(), now()),
('30000000-0000-4000-8000-0000000000a2', 'HEADER', 'nav.about',    '/about',    false, 20, 'ACTIVE', now(), now()),
('30000000-0000-4000-8000-0000000000a3', 'HEADER', 'nav.products', '/products', false, 30, 'ACTIVE', now(), now()),
('30000000-0000-4000-8000-0000000000a4', 'HEADER', 'nav.articles', '/articles', false, 40, 'ACTIVE', now(), now());

-- ---------- 页脚导航 ----------
INSERT INTO cms_nav (id, position, label_key, target, open_in_new_tab, sort_no, status,
                     created_at, updated_at) VALUES
('30000000-0000-4000-8000-0000000000b1', 'FOOTER', 'nav.about',    '/about',    false, 10, 'ACTIVE', now(), now()),
('30000000-0000-4000-8000-0000000000b2', 'FOOTER', 'nav.products', '/products', false, 20, 'ACTIVE', now(), now()),
('30000000-0000-4000-8000-0000000000b3', 'FOOTER', 'nav.contact',  '/contact',  false, 30, 'ACTIVE', now(), now());

-- ---------- 导航自由文案（缺失时回退 label_key） ----------
INSERT INTO cms_nav_i18n (nav_id, locale, label) VALUES
('30000000-0000-4000-8000-0000000000a1', 'zh-CN', '首页'),
('30000000-0000-4000-8000-0000000000a1', 'en',    'Home'),
('30000000-0000-4000-8000-0000000000a2', 'zh-CN', '关于我们'),
('30000000-0000-4000-8000-0000000000a2', 'en',    'About Us'),
('30000000-0000-4000-8000-0000000000a3', 'zh-CN', '产品中心'),
('30000000-0000-4000-8000-0000000000a3', 'en',    'Products'),
('30000000-0000-4000-8000-0000000000a4', 'zh-CN', '新闻中心'),
('30000000-0000-4000-8000-0000000000a4', 'en',    'News'),
('30000000-0000-4000-8000-0000000000b1', 'zh-CN', '公司简介'),
('30000000-0000-4000-8000-0000000000b1', 'en',    'Company Profile'),
('30000000-0000-4000-8000-0000000000b2', 'zh-CN', '产品与方案'),
('30000000-0000-4000-8000-0000000000b2', 'en',    'Products & Solutions'),
('30000000-0000-4000-8000-0000000000b3', 'zh-CN', '联系我们'),
('30000000-0000-4000-8000-0000000000b3', 'en',    'Contact');

-- ---------- 公司简介页 ----------
INSERT INTO cms_page (id, slug, status, published_at, created_at, updated_at) VALUES
('30000000-0000-4000-8000-0000000000e1', 'about', 'PUBLISHED', now(), now(), now());

INSERT INTO cms_page_i18n (page_id, locale, title, summary, body) VALUES
('30000000-0000-4000-8000-0000000000e1', 'zh-CN', '公司简介',
 '示例公司专注于为中小企业提供易用的数字化产品与实施服务。',
 '<h2>关于示例公司</h2><p>示例公司成立于示例年份，是一家专注于数字化产品与实施服务的企业。我们围绕「让数字化更简单」这一目标，提供软件产品、硬件设备与配套服务的一体化解决方案。</p><p>团队由产品、研发、交付与服务四个部分组成，服务覆盖从咨询、实施到运维的完整过程。</p>'),
('30000000-0000-4000-8000-0000000000e1', 'en',    'About Example Co.',
 'Example Co. provides easy-to-use digital products and delivery services for small and medium businesses.',
 '<h2>About Example Co.</h2><p>Founded in a sample year, Example Co. focuses on digital products and delivery services. We provide software, hardware and supporting services around one goal: making digitalisation simpler.</p><p>Our team covers product, engineering, delivery and service, serving customers from consulting to operations.</p>');

-- ---------- 文章分类 ----------
INSERT INTO cms_category (id, code, sort_no, status, created_at, updated_at) VALUES
('30000000-0000-4000-8000-000000000401', 'news',     10, 'ACTIVE', now(), now()),
('30000000-0000-4000-8000-000000000402', 'insights', 20, 'ACTIVE', now(), now());

INSERT INTO cms_category_i18n (category_id, locale, name, description) VALUES
('30000000-0000-4000-8000-000000000401', 'zh-CN', '公司动态', '示例公司的新闻与公告'),
('30000000-0000-4000-8000-000000000401', 'en',    'News', 'News and announcements of Example Co.'),
('30000000-0000-4000-8000-000000000402', 'zh-CN', '行业洞察', '示例公司对行业的观察与实践'),
('30000000-0000-4000-8000-000000000402', 'en',    'Insights', 'Observations and practices from Example Co.');

-- ---------- 文章 ----------
INSERT INTO cms_article (id, slug, category_id, status, published_at, created_at, updated_at) VALUES
('30000000-0000-4000-8000-000000000101', 'example-company-launches-new-product-line',
 '30000000-0000-4000-8000-000000000401', 'PUBLISHED', now() - interval '30 days', now() - interval '30 days', now()),
('30000000-0000-4000-8000-000000000102', 'example-company-wins-service-award',
 '30000000-0000-4000-8000-000000000401', 'PUBLISHED', now() - interval '20 days', now() - interval '20 days', now()),
('30000000-0000-4000-8000-000000000103', 'how-small-businesses-build-digital-capability',
 '30000000-0000-4000-8000-000000000402', 'PUBLISHED', now() - interval '10 days', now() - interval '10 days', now());

INSERT INTO cms_article_i18n (article_id, locale, title, summary, body) VALUES
('30000000-0000-4000-8000-000000000101', 'zh-CN', '示例公司发布全新产品线',
 '示例公司面向中小企业发布全新产品线，覆盖采集、分析与运维场景。',
 '<p>示例公司今日发布全新产品线，围绕数据采集、分析与智能运维三个方向提供模块化组合，支持按需订阅。</p>'),
('30000000-0000-4000-8000-000000000101', 'en',    'Example Co. Launches a New Product Line',
 'Example Co. releases a modular product line for data collection, analytics and operations.',
 '<p>Example Co. announced a new modular product line covering data collection, analytics and intelligent operations, available as a subscription.</p>'),
('30000000-0000-4000-8000-000000000102', 'zh-CN', '示例公司获年度服务奖',
 '示例公司的交付与服务团队获得年度服务奖。',
 '<p>凭借稳定的交付质量与响应速度，示例公司获得本年度服务类奖项。</p>'),
('30000000-0000-4000-8000-000000000102', 'en',    'Example Co. Wins Annual Service Award',
 'The delivery and service team of Example Co. received the annual service award.',
 '<p>For consistent delivery quality and response time, Example Co. received this year''s service award.</p>'),
('30000000-0000-4000-8000-000000000103', 'zh-CN', '中小企业如何构建数字化能力',
 '从流程梳理到工具落地，中小企业构建数字化能力的三个阶段。',
 '<p>数字化不是一次性项目，而是持续演进的过程。本文给出流程梳理、工具落地与度量改进三个阶段的实践要点。</p>'),
('30000000-0000-4000-8000-000000000103', 'en',    'How Small Businesses Build Digital Capability',
 'Three stages for small businesses: process mapping, tool adoption and measurement.',
 '<p>Digitalisation is not a one-off project but a continuous process. This article outlines three practical stages.</p>');

-- ---------- 标签 ----------
INSERT INTO cms_tag (id, code, created_at, updated_at) VALUES
('30000000-0000-4000-8000-0000000000a2', 'product-release', now(), now()),
('30000000-0000-4000-8000-0000000000a3', 'practice',       now(), now());

INSERT INTO cms_article_tag (article_id, tag_id, created_at) VALUES
('30000000-0000-4000-8000-000000000101', '30000000-0000-4000-8000-0000000000a2', now()),
('30000000-0000-4000-8000-000000000102', '30000000-0000-4000-8000-0000000000a3', now()),
('30000000-0000-4000-8000-000000000103', '30000000-0000-4000-8000-0000000000a3', now());

-- ---------- 产品 ----------
INSERT INTO cms_product (id, code, slug, group_code, status, sort_no,
                         published_at, created_at, updated_at) VALUES
('30000000-0000-4000-8000-000000000201', 'demo-analytics', 'analytics-platform', 'software', 'PUBLISHED', 10,
 now() - interval '15 days', now() - interval '15 days', now()),
('30000000-0000-4000-8000-000000000202', 'demo-collector', 'data-collector',     'hardware', 'PUBLISHED', 20,
 now() - interval '10 days', now() - interval '10 days', now()),
('30000000-0000-4000-8000-000000000203', 'demo-guard',     'operations-guard',   'service',  'PUBLISHED', 30,
 now() - interval '5 days',  now() - interval '5 days',  now());

INSERT INTO cms_product_i18n (product_id, locale, name, summary, body, features) VALUES
('30000000-0000-4000-8000-000000000201', 'zh-CN', '示例分析平台',
 '面向业务人员的数据分析平台，开箱即用。',
 '<p>示例分析平台提供自助式报表、看板与告警能力，业务人员无需编写代码即可完成日常分析。</p>',
 '[{"title":"自助报表","description":"拖拽式报表设计，支持多数据源接入。"},{"title":"实时看板","description":"秒级刷新的大屏看板，支持多端投放。"},{"title":"智能告警","description":"基于阈值的异常检测与分级通知。"}]'),
('30000000-0000-4000-8000-000000000201', 'en',    'Example Analytics Platform',
 'A ready-to-use analytics platform for business teams.',
 '<p>The example analytics platform provides self-service reports, dashboards and alerting so business teams can analyse data without writing code.</p>',
 '[{"title":"Self-service reports","description":"Drag-and-drop report design with multi-source connections."},{"title":"Live dashboards","description":"Dashboards refreshed per second and published to every screen."},{"title":"Smart alerting","description":"Threshold-based anomaly detection with graded notifications."}]'),
('30000000-0000-4000-8000-000000000202', 'zh-CN', '示例采集终端',
 '轻量级边缘采集终端，支持多种协议接入。',
 '<p>示例采集终端面向现场数据采集场景，支持多种工业与物联网协议，免配置即可稳定运行。</p>',
 '[{"title":"多协议接入","description":"内置常见工业与物联网协议采集能力。"},{"title":"离线存储","description":"断网续传，本地缓存可达七天。"},{"title":"低功耗设计","description":"典型场景下可数月免维护运行。"}]'),
('30000000-0000-4000-8000-000000000202', 'en',    'Example Collection Terminal',
 'A lightweight edge terminal for collecting data on site.',
 '<p>The example collection terminal supports common industrial and IoT protocols and runs reliably with zero configuration.</p>',
 '[{"title":"Multi-protocol","description":"Built-in support for common industrial and IoT protocols."},{"title":"Offline storage","description":"Store-and-forward with up to seven days of local cache."},{"title":"Low power","description":"Runs for months without maintenance in typical scenarios."}]'),
('30000000-0000-4000-8000-000000000203', 'zh-CN', '示例运维托管服务',
 '覆盖监控、巡检与应急响应的运维托管服务。',
 '<p>示例运维托管服务由示例公司专业团队提供，覆盖日常监控、定期巡检与应急响应，帮助客户保持业务连续。</p>',
 '[{"title":"7×24 监控","description":"全天候指标监控与异常通知。"},{"title":"定期巡检","description":"按周期执行的现场与系统巡检。"},{"title":"应急响应","description":"分级响应机制与复盘报告。"}]'),
('30000000-0000-4000-8000-000000000203', 'en',    'Example Managed Operations',
 'Managed operations covering monitoring, inspection and emergency response.',
 '<p>Delivered by the professional team of Example Co., the managed operations service covers daily monitoring, regular inspection and emergency response to keep customers running.</p>',
 '[{"title":"24/7 monitoring","description":"Round-the-clock metric monitoring and anomaly notification."},{"title":"Regular inspection","description":"Scheduled on-site and system inspections."},{"title":"Emergency response","description":"Tiered response process with review reports."}]');
