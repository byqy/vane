-- 消息模板：把 ops:mail 从按钮点升级为菜单，挂到「运维管理」下

UPDATE iam_permission
   SET type      = 'MENU',
       name_key  = 'menu.ops.mail',
       path      = '/ops/mail',
       component = 'ops/mail/index',
       icon      = 'mail',
       sort_no   = 40
 WHERE code = 'ops:mail';
