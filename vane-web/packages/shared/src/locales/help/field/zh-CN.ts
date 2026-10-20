// 后台「字段级」帮助文案（zh-CN）。键集合必须与其他语言完全一致（field-help.test.ts 强制校验）。
// 与 locales/help/<locale>.ts 的页面级帮助分文件维护，合并规则见 locales/help/index.ts。

const message = {
  "fieldHelp.open": "字段说明",
  "field.navs.labelKey.help": "菜单名称的文案 key，形如 nav.about。该 key 必须已在语言资源中存在，否则门户会直接显示原始 key。",
  "field.navs.target.help": "点击后跳转的相对路径，例如 /、/about、/articles。前台会自动补上语言前缀，不要写完整域名。",
  "field.navs.sortNo.help": "数字越小越靠前；同级菜单按该值排序，可留空（默认 0）。",
  "field.pages.slug.help": "页面网址的一部分（/{locale}/{slug}）。发布后尽量不要再改，旧链接会 404。",
  "field.articles.slug.help": "文章网址的一部分（/{locale}/articles/{slug}），同时影响已有链接与搜索引擎收录。",
  "field.articles.category.help": "决定文章出现在哪个分类页（/{locale}/categories/{code}）。分类为一级结构，可留空。",
  "field.articles.tags.help": "文章标签，可多选；门户可按标签筛选。没有合适的标签时可以现场新建。",
  "field.articles.newTag.help": "输入标签编码后点「新建标签」，会创建标签并自动选中它。",
  "field.categories.code.help": "分类编码会进入网址 /categories/{code}，创建后不要修改。",
  "field.tags.code.help": "标签的唯一编码，用于筛选与去重；标签不进入网址，改名不会影响链接。",
  "field.users.username.help": "登录名，创建后不可修改。建议使用邮箱前缀或工号，避免手机号等会变的信息。",
  "field.users.password.help": "仅新建时填写初始密码；首次登录会要求修改。留空则由系统按密码策略生成。",
  "field.users.dept.help": "账号所属组织，决定「本组织」「本组织及以下」数据范围的归属；选错会导致看不到数据或看到不该看的数据。",
  "field.users.roles.help": "角色决定菜单与按钮权限，可多选，权限取并集；至少要选一个角色。",
  "field.users.status.help": "停用后该账号无法登录，数据与日志保留。",
  "field.roles.code.help": "角色编码用于接口鉴权与内置角色判断，创建后不可修改，建议大写加下划线。",
  "field.roles.dataScope.help": "决定该角色能看到哪些组织的数据。选择「自定义」时需要再到「组织授权」里勾选具体组织。",
  "field.configs.type.help": "参数类型只读，决定值如何解析（字符串、数字、布尔、JSON）。类型与值不匹配会导致读取失败。",
  "field.configs.value.help": "按类型填写值；JSON 必须是合法 JSON。密钥类参数只放占位或环境变量引用，不要明文入库。",
  "field.dicts.typeCode.help": "程序按这个编码读取字典，建议小写加下划线，创建后不要修改。",
  "field.dicts.nameKey.help": "界面显示名称使用的文案 key（如 dict.someName），需要已在语言资源中存在。",
  "field.dicts.itemValue.help": "字典项的值，会存库并通过接口返回；创建后不建议修改，避免历史数据对不上。",
  "field.dicts.labelZh.help": "中文界面显示的标签；英文标签在下一项单独维护。",
  "field.jobs.cron.help": "6 位表达式：秒 分 时 日 月 周。例如 0 0 3 * * * 表示每天 03:00 执行；可用「执行一次」验证。",
  "field.jobs.params.help": "传给任务处理逻辑的 JSON 对象，没有参数就留空。",
  "field.jobs.concurrency.help": "禁止并发时，上一次还没结束会跳过本次；允许并发则同时运行多次。",
  "field.banners.image.help": "先上传到「文件管理」，再在这里选择；建议 16:9 横图，单张不超过 500 KB。",
  "field.banners.link.help": "点击轮播后跳转的地址，可写相对路径或完整 URL；留空表示不可点击。",
  "field.banners.startAt.help": "到点后自动出现在门户，留空表示立即生效。",
  "field.banners.endAt.help": "到点后自动下线，留空表示长期有效。",
  "field.mail.variables.help": "模板变量用 JSON 对象填写，键要与正文里的 {name} 占位符对应，仅用于发送测试。",
  "field.notices.publishAt.help": "到点后自动出现在门户，留空表示立即生效。",
  "field.notices.expireAt.help": "到点后自动下线，留空表示长期有效。"
}

export default message
