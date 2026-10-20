/**
 * 后台页面内帮助（标题旁的「?」）的页面标识与文案键约定。
 *
 * 每个页面标识对应 `locales/help/<locale>.ts` 里的两条文案：
 * `help.<page>.what`（这个页面用来做什么）与 `help.<page>.steps`（怎么用，按行分条）。
 * 键集合由 `help.test.ts` 强制校验，新增页面必须同时补齐 20 种语言。
 */
export const helpPages = [
  'users',
  'roles',
  'depts',
  'perms',
  'configs',
  'dicts',
  'notices',
  'pages',
  'navs',
  'banners',
  'articles',
  'categories',
  'tags',
  'jobs',
  'files',
  'mail',
  'health',
  'operLogs',
  'loginLogs'
] as const

export type HelpPage = (typeof helpPages)[number]

/** 帮助抽屉的通用文案键（标题、两个小标题、入口按钮的无障碍名称）。 */
export const helpCommonKeys = {
  title: 'help.title',
  what: 'help.what',
  steps: 'help.steps',
  open: 'help.open'
} as const

/** 某个页面的帮助文案键。 */
export function helpKeys(page: HelpPage): Record<'what' | 'steps', string> {
  return { what: `help.${page}.what`, steps: `help.${page}.steps` }
}

/** 帮助文案按行分条：去掉行首的项目符号，交给列表渲染。 */
export function helpLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => line.replace(/^[·•\-]\s*/, ''))
}
