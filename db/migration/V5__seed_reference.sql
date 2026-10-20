-- =====================================================================
-- V5: 参考数据种子（角色、权限点、系统参数、字典、消息模板）
-- 说明：初始管理员账号不在此脚本创建，由应用启动引导生成随机密码
-- =====================================================================

-- ---------- 角色 ----------
INSERT INTO iam_role (id, code, name, data_scope, builtin, sort_no) VALUES
('10000000-0000-4000-8000-000000000001', 'SUPER_ADMIN', '超级管理员', 'ALL',             true, 10),
('10000000-0000-4000-8000-000000000002', 'ADMIN',       '管理员',     'DEPT_AND_CHILD',  true, 20),
('10000000-0000-4000-8000-000000000003', 'OPERATOR',    '运营',       'DEPT',            true, 30),
('10000000-0000-4000-8000-000000000004', 'AUDITOR',     '审计员',     'ALL',             true, 40);

-- ---------- 权限点：一级菜单 ----------
INSERT INTO iam_permission (id, parent_id, code, type, name_key, path, component, icon, sort_no, visible) VALUES
('20000000-0000-4000-8000-000000000001', NULL, 'system',  'MENU', 'menu.system',  '/system',  'layout', 'setting',  100, true),
('20000000-0000-4000-8000-000000000002', NULL, 'content', 'MENU', 'menu.content', '/content', 'layout', 'document', 200, true),
('20000000-0000-4000-8000-000000000003', NULL, 'ops',     'MENU', 'menu.ops',     '/ops',     'layout', 'monitor',  300, true),
('20000000-0000-4000-8000-000000000004', NULL, 'audit',   'MENU', 'menu.audit',   '/audit',   'layout', 'file-text',400, true);

-- ---------- 权限点：二级菜单 ----------
INSERT INTO iam_permission (id, parent_id, code, type, name_key, path, component, icon, sort_no, visible) VALUES
('20000000-0000-4000-8000-000000000011', '20000000-0000-4000-8000-000000000001', 'system:user',       'MENU', 'menu.system.user',       '/system/user',       'system/user/index',       'user',    10, true),
('20000000-0000-4000-8000-000000000012', '20000000-0000-4000-8000-000000000001', 'system:role',       'MENU', 'menu.system.role',       '/system/role',       'system/role/index',       'team',    20, true),
('20000000-0000-4000-8000-000000000013', '20000000-0000-4000-8000-000000000001', 'system:dept',       'MENU', 'menu.system.dept',       '/system/dept',       'system/dept/index',       'apartment',30, true),
('20000000-0000-4000-8000-000000000014', '20000000-0000-4000-8000-000000000001', 'system:permission', 'MENU', 'menu.system.permission', '/system/permission', 'system/permission/index', 'key',      40, true),
('20000000-0000-4000-8000-000000000015', '20000000-0000-4000-8000-000000000001', 'system:config',     'MENU', 'menu.system.config',     '/system/config',     'system/config/index',     'setting',  50, true),
('20000000-0000-4000-8000-000000000016', '20000000-0000-4000-8000-000000000001', 'system:dict',       'MENU', 'menu.system.dict',       '/system/dict',       'system/dict/index',       'book',     60, true),
('20000000-0000-4000-8000-000000000021', '20000000-0000-4000-8000-000000000002', 'content:notice',    'MENU', 'menu.content.notice',    '/content/notice',    'content/notice/index',    'bell',     10, true),
('20000000-0000-4000-8000-000000000022', '20000000-0000-4000-8000-000000000002', 'content:page',      'MENU', 'menu.content.page',      '/content/page',      'content/page/index',      'document', 20, true),
('20000000-0000-4000-8000-000000000023', '20000000-0000-4000-8000-000000000002', 'content:nav',       'MENU', 'menu.content.nav',       '/content/nav',       'content/nav/index',       'menu',     30, true),
('20000000-0000-4000-8000-000000000024', '20000000-0000-4000-8000-000000000002', 'content:banner',    'MENU', 'menu.content.banner',    '/content/banner',    'content/banner/index',    'picture',  40, true),
('20000000-0000-4000-8000-000000000031', '20000000-0000-4000-8000-000000000003', 'ops:job',           'MENU', 'menu.ops.job',           '/ops/job',           'ops/job/index',           'clock',    10, true),
('20000000-0000-4000-8000-000000000032', '20000000-0000-4000-8000-000000000003', 'ops:file',          'MENU', 'menu.ops.file',          '/ops/file',          'ops/file/index',          'folder',   20, true),
('20000000-0000-4000-8000-000000000033', '20000000-0000-4000-8000-000000000003', 'ops:health',        'MENU', 'menu.ops.health',        '/ops/health',        'ops/health/index',        'heart',    30, true),
('20000000-0000-4000-8000-000000000041', '20000000-0000-4000-8000-000000000004', 'audit:oper-log',    'MENU', 'menu.audit.operLog',     '/audit/oper-log',    'audit/operlog/index',     'list',     10, true),
('20000000-0000-4000-8000-000000000042', '20000000-0000-4000-8000-000000000004', 'audit:login-log',   'MENU', 'menu.audit.loginLog',    '/audit/login-log',   'audit/loginlog/index',    'login',    20, true);

-- ---------- 权限点：按钮级 ----------
INSERT INTO iam_permission (id, parent_id, code, type, name_key, sort_no) VALUES
('20000000-0000-4000-8000-000000000051', '20000000-0000-4000-8000-000000000011', 'system:user:create',         'BUTTON', 'perm.system.user.create',         10),
('20000000-0000-4000-8000-000000000052', '20000000-0000-4000-8000-000000000011', 'system:user:update',         'BUTTON', 'perm.system.user.update',         20),
('20000000-0000-4000-8000-000000000053', '20000000-0000-4000-8000-000000000011', 'system:user:delete',         'BUTTON', 'perm.system.user.delete',         30),
('20000000-0000-4000-8000-000000000054', '20000000-0000-4000-8000-000000000011', 'system:user:reset-password', 'BUTTON', 'perm.system.user.resetPassword',  40),
('20000000-0000-4000-8000-000000000055', '20000000-0000-4000-8000-000000000011', 'system:user:export',         'BUTTON', 'perm.system.user.export',         50),
('20000000-0000-4000-8000-000000000056', '20000000-0000-4000-8000-000000000012', 'system:role:create',         'BUTTON', 'perm.system.role.create',         10),
('20000000-0000-4000-8000-000000000057', '20000000-0000-4000-8000-000000000012', 'system:role:update',         'BUTTON', 'perm.system.role.update',         20),
('20000000-0000-4000-8000-000000000058', '20000000-0000-4000-8000-000000000012', 'system:role:delete',         'BUTTON', 'perm.system.role.delete',         30),
('20000000-0000-4000-8000-000000000059', '20000000-0000-4000-8000-000000000012', 'system:role:assign',         'BUTTON', 'perm.system.role.assign',         40),
('20000000-0000-4000-8000-000000000060', '20000000-0000-4000-8000-000000000013', 'system:dept:create',         'BUTTON', 'perm.system.dept.create',         10),
('20000000-0000-4000-8000-000000000061', '20000000-0000-4000-8000-000000000013', 'system:dept:update',         'BUTTON', 'perm.system.dept.update',         20),
('20000000-0000-4000-8000-000000000062', '20000000-0000-4000-8000-000000000013', 'system:dept:delete',         'BUTTON', 'perm.system.dept.delete',         30),
('20000000-0000-4000-8000-000000000063', '20000000-0000-4000-8000-000000000014', 'system:permission:create',   'BUTTON', 'perm.system.permission.create',   10),
('20000000-0000-4000-8000-000000000064', '20000000-0000-4000-8000-000000000014', 'system:permission:update',   'BUTTON', 'perm.system.permission.update',   20),
('20000000-0000-4000-8000-000000000065', '20000000-0000-4000-8000-000000000014', 'system:permission:delete',   'BUTTON', 'perm.system.permission.delete',   30),
('20000000-0000-4000-8000-000000000066', '20000000-0000-4000-8000-000000000015', 'system:config:update',       'BUTTON', 'perm.system.config.update',       10),
('20000000-0000-4000-8000-000000000067', '20000000-0000-4000-8000-000000000016', 'system:dict:create',         'BUTTON', 'perm.system.dict.create',         10),
('20000000-0000-4000-8000-000000000068', '20000000-0000-4000-8000-000000000016', 'system:dict:update',         'BUTTON', 'perm.system.dict.update',         20),
('20000000-0000-4000-8000-000000000069', '20000000-0000-4000-8000-000000000016', 'system:dict:delete',         'BUTTON', 'perm.system.dict.delete',         30),
('20000000-0000-4000-8000-000000000070', '20000000-0000-4000-8000-000000000021', 'content:notice:create',      'BUTTON', 'perm.content.notice.create',      10),
('20000000-0000-4000-8000-000000000071', '20000000-0000-4000-8000-000000000021', 'content:notice:update',      'BUTTON', 'perm.content.notice.update',      20),
('20000000-0000-4000-8000-000000000072', '20000000-0000-4000-8000-000000000021', 'content:notice:delete',      'BUTTON', 'perm.content.notice.delete',      30),
('20000000-0000-4000-8000-000000000073', '20000000-0000-4000-8000-000000000021', 'content:notice:publish',     'BUTTON', 'perm.content.notice.publish',     40),
('20000000-0000-4000-8000-000000000074', '20000000-0000-4000-8000-000000000022', 'content:page:create',        'BUTTON', 'perm.content.page.create',        10),
('20000000-0000-4000-8000-000000000075', '20000000-0000-4000-8000-000000000022', 'content:page:update',        'BUTTON', 'perm.content.page.update',        20),
('20000000-0000-4000-8000-000000000076', '20000000-0000-4000-8000-000000000022', 'content:page:delete',        'BUTTON', 'perm.content.page.delete',        30),
('20000000-0000-4000-8000-000000000077', '20000000-0000-4000-8000-000000000022', 'content:page:publish',       'BUTTON', 'perm.content.page.publish',       40),
('20000000-0000-4000-8000-000000000078', '20000000-0000-4000-8000-000000000023', 'content:nav:create',         'BUTTON', 'perm.content.nav.create',         10),
('20000000-0000-4000-8000-000000000079', '20000000-0000-4000-8000-000000000023', 'content:nav:update',         'BUTTON', 'perm.content.nav.update',         20),
('20000000-0000-4000-8000-000000000080', '20000000-0000-4000-8000-000000000023', 'content:nav:delete',         'BUTTON', 'perm.content.nav.delete',         30),
('20000000-0000-4000-8000-000000000081', '20000000-0000-4000-8000-000000000024', 'content:banner:create',      'BUTTON', 'perm.content.banner.create',      10),
('20000000-0000-4000-8000-000000000082', '20000000-0000-4000-8000-000000000024', 'content:banner:update',      'BUTTON', 'perm.content.banner.update',      20),
('20000000-0000-4000-8000-000000000083', '20000000-0000-4000-8000-000000000024', 'content:banner:delete',      'BUTTON', 'perm.content.banner.delete',      30),
('20000000-0000-4000-8000-000000000084', '20000000-0000-4000-8000-000000000031', 'ops:job:create',             'BUTTON', 'perm.ops.job.create',             10),
('20000000-0000-4000-8000-000000000085', '20000000-0000-4000-8000-000000000031', 'ops:job:update',             'BUTTON', 'perm.ops.job.update',             20),
('20000000-0000-4000-8000-000000000086', '20000000-0000-4000-8000-000000000031', 'ops:job:delete',             'BUTTON', 'perm.ops.job.delete',             30),
('20000000-0000-4000-8000-000000000087', '20000000-0000-4000-8000-000000000031', 'ops:job:run',                'BUTTON', 'perm.ops.job.run',                40),
('20000000-0000-4000-8000-000000000088', '20000000-0000-4000-8000-000000000032', 'ops:file:upload',            'BUTTON', 'perm.ops.file.upload',            10),
('20000000-0000-4000-8000-000000000089', '20000000-0000-4000-8000-000000000032', 'ops:file:delete',            'BUTTON', 'perm.ops.file.delete',            20),
('20000000-0000-4000-8000-000000000090', '20000000-0000-4000-8000-000000000032', 'ops:file:download',          'BUTTON', 'perm.ops.file.download',          30),
('20000000-0000-4000-8000-000000000091', '20000000-0000-4000-8000-000000000041', 'audit:oper-log:export',      'BUTTON', 'perm.audit.operLog.export',       10),
('20000000-0000-4000-8000-000000000092', '20000000-0000-4000-8000-000000000042', 'audit:login-log:export',     'BUTTON', 'perm.audit.loginLog.export',      10);

-- ---------- 角色授权 ----------
-- 超级管理员：全部权限
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT '10000000-0000-4000-8000-000000000001', p.id FROM iam_permission p WHERE p.deleted_at IS NULL;

-- 管理员：除「权限点维护」与「审计导出」外的全部权限
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT '10000000-0000-4000-8000-000000000002', p.id
FROM iam_permission p
WHERE p.deleted_at IS NULL
  AND p.code NOT IN ('system:permission', 'system:permission:create', 'system:permission:update', 'system:permission:delete')
  AND p.code NOT LIKE 'audit:%:export';

-- 运营：内容菜单与按钮 + 文件
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT '10000000-0000-4000-8000-000000000003', p.id
FROM iam_permission p
WHERE p.deleted_at IS NULL
  AND (p.code LIKE 'content:%' OR p.code LIKE 'ops:file:%' OR p.code = 'content');

-- 审计员：审计菜单与导出
INSERT INTO iam_role_permission (role_id, permission_id)
SELECT '10000000-0000-4000-8000-000000000004', p.id
FROM iam_permission p
WHERE p.deleted_at IS NULL
  AND (p.code LIKE 'audit%');

-- ---------- 系统参数 ----------
INSERT INTO sys_config (config_key, value_json, value_type, description, editable) VALUES
('security.password.min-length',        '6'::jsonb,                  'INT',    '密码最小长度（合规场景建议 ≥ 12）', true),
('security.login.fail-threshold',       '5'::jsonb,                  'INT',    '登录失败锁定阈值', true),
('security.login.lock-minutes',         '15'::jsonb,                 'INT',    '锁定时长（分钟）', true),
('security.token.access-ttl-seconds',   '900'::jsonb,                'INT',    'access 令牌有效期（秒）', true),
('security.token.refresh-ttl-days',     '7'::jsonb,                  'INT',    'refresh 令牌有效期（天）', true),
('security.mfa.recovery-code-count',    '10'::jsonb,                 'INT',    'MFA 恢复码数量', true),
('api.idempotency.ttl-hours',           '24'::jsonb,                 'INT',    '幂等键保留时长（小时）', true),
('api.page.default-size',               '20'::jsonb,                 'INT',    '分页默认条数', true),
('api.page.max-size',                   '100'::jsonb,                'INT',    '分页最大条数', true),
('i18n.default-locale',                 '"zh-CN"'::jsonb,            'STRING', '默认语言', true),
('i18n.supported-locales',              '["zh-CN","en"]'::jsonb,     'JSON',   '可用语言列表', true),
('file.upload.max-size-mb',             '20'::jsonb,                 'INT',    '上传大小上限（MB）', true),
('file.image.allowed-types',            '["png","jpg","jpeg","webp","svg"]'::jsonb, 'JSON', '允许的图片类型', true),
('file.svg.sanitize',                   'true'::jsonb,               'BOOL',   '上传 SVG 是否净化', false),
('ui.theme.default',                    '"light"'::jsonb,            'STRING', '默认主题模式', true),
('system.demo-mode',                    'false'::jsonb,              'BOOL',   '演示模式（禁写）', true);

-- ---------- 字典 ----------
INSERT INTO sys_dict_type (id, type_code, name_key, remark) VALUES
('30000000-0000-4000-8000-000000000001', 'common_status',  'dict.commonStatus',  '通用状态'),
('30000000-0000-4000-8000-000000000002', 'notice_level',   'dict.noticeLevel',   '公告级别'),
('30000000-0000-4000-8000-000000000003', 'publish_status', 'dict.publishStatus', '发布状态'),
('30000000-0000-4000-8000-000000000004', 'data_scope',     'dict.dataScope',     '数据范围'),
('30000000-0000-4000-8000-000000000005', 'client_type',    'dict.clientType',    '客户端类型'),
('30000000-0000-4000-8000-000000000006', 'message_channel','dict.messageChannel','消息渠道');

INSERT INTO sys_dict_item (id, type_id, item_value, color, sort_no, builtin) VALUES
('31000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', 'ACTIVE',   'success', 10, true),
('31000000-0000-4000-8000-000000000002', '30000000-0000-4000-8000-000000000001', 'DISABLED', 'info',    20, true),
('31000000-0000-4000-8000-000000000003', '30000000-0000-4000-8000-000000000001', 'LOCKED',   'danger',  30, true),
('31000000-0000-4000-8000-000000000011', '30000000-0000-4000-8000-000000000002', 'INFO',     'info',    10, true),
('31000000-0000-4000-8000-000000000012', '30000000-0000-4000-8000-000000000002', 'WARN',     'warning', 20, true),
('31000000-0000-4000-8000-000000000013', '30000000-0000-4000-8000-000000000002', 'URGENT',   'danger',  30, true),
('31000000-0000-4000-8000-000000000021', '30000000-0000-4000-8000-000000000003', 'DRAFT',     'info',    10, true),
('31000000-0000-4000-8000-000000000022', '30000000-0000-4000-8000-000000000003', 'PUBLISHED', 'success', 20, true),
('31000000-0000-4000-8000-000000000023', '30000000-0000-4000-8000-000000000003', 'ARCHIVED',  'info',    30, true),
('31000000-0000-4000-8000-000000000024', '30000000-0000-4000-8000-000000000003', 'EXPIRED',   'warning', 40, true),
('31000000-0000-4000-8000-000000000031', '30000000-0000-4000-8000-000000000004', 'ALL',             NULL, 10, true),
('31000000-0000-4000-8000-000000000032', '30000000-0000-4000-8000-000000000004', 'DEPT_AND_CHILD',  NULL, 20, true),
('31000000-0000-4000-8000-000000000033', '30000000-0000-4000-8000-000000000004', 'DEPT',            NULL, 30, true),
('31000000-0000-4000-8000-000000000034', '30000000-0000-4000-8000-000000000004', 'SELF',            NULL, 40, true),
('31000000-0000-4000-8000-000000000035', '30000000-0000-4000-8000-000000000004', 'CUSTOM',          NULL, 50, true),
('31000000-0000-4000-8000-000000000041', '30000000-0000-4000-8000-000000000005', 'WEB', NULL, 10, true),
('31000000-0000-4000-8000-000000000042', '30000000-0000-4000-8000-000000000005', 'APP', NULL, 20, true),
('31000000-0000-4000-8000-000000000051', '30000000-0000-4000-8000-000000000006', 'EMAIL', NULL, 10, true),
('31000000-0000-4000-8000-000000000052', '30000000-0000-4000-8000-000000000006', 'SMS',   NULL, 20, true),
('31000000-0000-4000-8000-000000000053', '30000000-0000-4000-8000-000000000006', 'INAPP', NULL, 30, true);

INSERT INTO sys_dict_item_i18n (item_id, locale, label) VALUES
('31000000-0000-4000-8000-000000000001', 'zh-CN', '启用'),
('31000000-0000-4000-8000-000000000001', 'en',    'Active'),
('31000000-0000-4000-8000-000000000002', 'zh-CN', '禁用'),
('31000000-0000-4000-8000-000000000002', 'en',    'Disabled'),
('31000000-0000-4000-8000-000000000003', 'zh-CN', '锁定'),
('31000000-0000-4000-8000-000000000003', 'en',    'Locked'),
('31000000-0000-4000-8000-000000000011', 'zh-CN', '通知'),
('31000000-0000-4000-8000-000000000011', 'en',    'Info'),
('31000000-0000-4000-8000-000000000012', 'zh-CN', '警告'),
('31000000-0000-4000-8000-000000000012', 'en',    'Warning'),
('31000000-0000-4000-8000-000000000013', 'zh-CN', '紧急'),
('31000000-0000-4000-8000-000000000013', 'en',    'Urgent');

-- ---------- 消息模板 ----------
INSERT INTO sys_message_template (id, code, channel, remark) VALUES
('40000000-0000-4000-8000-000000000001', 'user.welcome',    'EMAIL', '新用户欢迎邮件'),
('40000000-0000-4000-8000-000000000002', 'password.reset',  'EMAIL', '密码重置邮件'),
('40000000-0000-4000-8000-000000000003', 'password.reset',  'SMS',   '密码重置短信');

INSERT INTO sys_message_template_i18n (template_id, locale, subject, body) VALUES
('40000000-0000-4000-8000-000000000001', 'zh-CN', '欢迎加入 {siteName}', '你好 {username}，你的账号已创建。'),
('40000000-0000-4000-8000-000000000001', 'en',    'Welcome to {siteName}', 'Hello {username}, your account has been created.'),
('40000000-0000-4000-8000-000000000002', 'zh-CN', '密码重置', '请在 {minutes} 分钟内点击链接重置密码：{resetUrl}'),
('40000000-0000-4000-8000-000000000002', 'en',    'Password reset', 'Reset your password within {minutes} minutes: {resetUrl}'),
('40000000-0000-4000-8000-000000000003', 'zh-CN', NULL, '验证码 {code}，{minutes} 分钟内有效。'),
('40000000-0000-4000-8000-000000000003', 'en',    NULL, 'Verification code {code}, valid for {minutes} minutes.');

-- ---------- 编号规则示例 ----------
INSERT INTO sys_serial_rule (rule_code, pattern, reset_period, last_value) VALUES
('page.code', 'PG{YYYY}{MM}{seq:4}', 'MONTH', 0);
