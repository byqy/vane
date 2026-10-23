import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const base = 'D:/ProgramData/Code/byqy/tby/vane-web'
const dataDir = join(base, '.locale-data')
const localeDir = join(base, 'packages/shared/src/locales')

const data = {}
for (const f of ['a.json', 'b.json', 'c.json', 'd.json']) {
  const parsed = JSON.parse(readFileSync(join(dataDir, f), 'utf8'))
  for (const [locale, map] of Object.entries(parsed)) {
    data[locale] = map
  }
}

const reference = Object.keys(data['zh-CN'])
for (const [locale, map] of Object.entries(data)) {
  const keys = Object.keys(map)
  if (keys.length !== reference.length) {
    throw new Error(`${locale} 键数量不一致: ${keys.length} != ${reference.length}`)
  }
  for (const key of reference) {
    if (!(key in map)) {
      throw new Error(`${locale} 缺少键 ${key}`)
    }
  }
  for (const key of keys) {
    if (!reference.includes(key)) {
      throw new Error(`${locale} 多余键 ${key}`)
    }
  }
}

const anchor = '"portal.articles.readMore"'
for (const [locale, map] of Object.entries(data)) {
  const file = join(localeDir, `${locale}.ts`)
  let source = readFileSync(file, 'utf8')
  if (source.includes('"portal.hero.cta"')) {
    console.log(`${locale}: 已存在，跳过`)
    continue
  }
  const index = source.indexOf(anchor)
  if (index < 0) {
    throw new Error(`${locale}: 找不到锚点 ${anchor}`)
  }
  const lineEnd = source.indexOf('\n', index)
  const indent = '  '
  const lines = reference.map(
    (key) => `${indent}${JSON.stringify(key)}: ${JSON.stringify(map[key])},`
  )
  const block = `\n${lines.join('\n')}`
  source = source.slice(0, lineEnd) + block + source.slice(lineEnd)
  writeFileSync(file, source, 'utf8')
  console.log(`${locale}: 已插入 ${reference.length} 个键`)
}
