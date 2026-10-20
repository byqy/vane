-- 删除两个从未被代码读取的 i18n 系统参数：
--   i18n.supported-locales：语言清单由前后端资源文件决定（20 种），该值已过期且无人读取
--   i18n.default-locale   ：默认语言由 LocaleContext / 前端资源决定，该值无人读取

DELETE FROM sys_config
 WHERE config_key IN ('i18n.supported-locales', 'i18n.default-locale');
