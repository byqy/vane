import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const base = 'D:/ProgramData/Code/byqy/tby/vane-web'
const localeDir = join(base, 'packages/shared/src/locales')
const permDir = join(localeDir, 'perm')

const baseKeys = [
  'menu.content.product',
  'product.status.DRAFT',
  'product.status.PUBLISHED',
  'product.status.ARCHIVED',
  'product.publishedAt',
  'product.keyword',
  'product.translations',
  'product.chooseFile'
]

const data = {
  'zh-CN': {
    'menu.content.product': '产品管理',
    'product.status.DRAFT': '草稿',
    'product.status.PUBLISHED': '已发布',
    'product.status.ARCHIVED': '已归档',
    'product.publishedAt': '上线时间',
    'product.keyword': '搜索产品名称或编码',
    'product.translations': '多语言内容',
    'product.chooseFile': '选择已有文件'
  },
  'zh-TW': {
    'menu.content.product': '產品管理',
    'product.status.DRAFT': '草稿',
    'product.status.PUBLISHED': '已發布',
    'product.status.ARCHIVED': '已封存',
    'product.publishedAt': '上線時間',
    'product.keyword': '搜尋產品名稱或編碼',
    'product.translations': '多語言內容',
    'product.chooseFile': '選擇已有檔案'
  },
  en: {
    'menu.content.product': 'Product Management',
    'product.status.DRAFT': 'Draft',
    'product.status.PUBLISHED': 'Published',
    'product.status.ARCHIVED': 'Archived',
    'product.publishedAt': 'Published at',
    'product.keyword': 'Search product name or code',
    'product.translations': 'Translations',
    'product.chooseFile': 'Choose existing file'
  },
  ja: {
    'menu.content.product': '製品管理',
    'product.status.DRAFT': '下書き',
    'product.status.PUBLISHED': '公開済み',
    'product.status.ARCHIVED': 'アーカイブ済み',
    'product.publishedAt': '公開日時',
    'product.keyword': '製品名またはコードで検索',
    'product.translations': '翻訳',
    'product.chooseFile': '既存ファイルを選択'
  },
  ko: {
    'menu.content.product': '제품 관리',
    'product.status.DRAFT': '초안',
    'product.status.PUBLISHED': '게시됨',
    'product.status.ARCHIVED': '보관됨',
    'product.publishedAt': '게시 시간',
    'product.keyword': '제품명 또는 코드로 검색',
    'product.translations': '번역문',
    'product.chooseFile': '기존 파일 선택'
  },
  ru: {
    'menu.content.product': 'Управление продуктами',
    'product.status.DRAFT': 'Черновик',
    'product.status.PUBLISHED': 'Опубликован',
    'product.status.ARCHIVED': 'В архиве',
    'product.publishedAt': 'Время публикации',
    'product.keyword': 'Поиск по названию или коду',
    'product.translations': 'Переводы',
    'product.chooseFile': 'Выбрать существующий файл'
  },
  de: {
    'menu.content.product': 'Produktverwaltung',
    'product.status.DRAFT': 'Entwurf',
    'product.status.PUBLISHED': 'Veröffentlicht',
    'product.status.ARCHIVED': 'Archiviert',
    'product.publishedAt': 'Veröffentlicht am',
    'product.keyword': 'Produktnamen oder Code suchen',
    'product.translations': 'Übersetzungen',
    'product.chooseFile': 'Vorhandene Datei wählen'
  },
  fr: {
    'menu.content.product': 'Gestion des produits',
    'product.status.DRAFT': 'Brouillon',
    'product.status.PUBLISHED': 'Publié',
    'product.status.ARCHIVED': 'Archivé',
    'product.publishedAt': 'Publié le',
    'product.keyword': 'Rechercher par nom ou code',
    'product.translations': 'Traductions',
    'product.chooseFile': 'Choisir un fichier existant'
  },
  es: {
    'menu.content.product': 'Gestión de productos',
    'product.status.DRAFT': 'Borrador',
    'product.status.PUBLISHED': 'Publicado',
    'product.status.ARCHIVED': 'Archivado',
    'product.publishedAt': 'Publicado el',
    'product.keyword': 'Buscar por nombre o código',
    'product.translations': 'Traducciones',
    'product.chooseFile': 'Elegir archivo existente'
  },
  'pt-BR': {
    'menu.content.product': 'Gestão de produtos',
    'product.status.DRAFT': 'Rascunho',
    'product.status.PUBLISHED': 'Publicado',
    'product.status.ARCHIVED': 'Arquivado',
    'product.publishedAt': 'Publicado em',
    'product.keyword': 'Buscar por nome ou código',
    'product.translations': 'Traduções',
    'product.chooseFile': 'Escolher arquivo existente'
  },
  it: {
    'menu.content.product': 'Gestione prodotti',
    'product.status.DRAFT': 'Bozza',
    'product.status.PUBLISHED': 'Pubblicato',
    'product.status.ARCHIVED': 'Archiviato',
    'product.publishedAt': 'Pubblicato il',
    'product.keyword': 'Cerca per nome o codice',
    'product.translations': 'Traduzioni',
    'product.chooseFile': 'Scegli file esistente'
  },
  nl: {
    'menu.content.product': 'Productbeheer',
    'product.status.DRAFT': 'Concept',
    'product.status.PUBLISHED': 'Gepubliceerd',
    'product.status.ARCHIVED': 'Gearchiveerd',
    'product.publishedAt': 'Gepubliceerd op',
    'product.keyword': 'Zoek op naam of code',
    'product.translations': 'Vertalingen',
    'product.chooseFile': 'Bestaand bestand kiezen'
  },
  pl: {
    'menu.content.product': 'Zarządzanie produktami',
    'product.status.DRAFT': 'Szkic',
    'product.status.PUBLISHED': 'Opublikowany',
    'product.status.ARCHIVED': 'Zarchiwizowany',
    'product.publishedAt': 'Data publikacji',
    'product.keyword': 'Szukaj według nazwy lub kodu',
    'product.translations': 'Tłumaczenia',
    'product.chooseFile': 'Wybierz istniejący plik'
  },
  tr: {
    'menu.content.product': 'Ürün Yönetimi',
    'product.status.DRAFT': 'Taslak',
    'product.status.PUBLISHED': 'Yayında',
    'product.status.ARCHIVED': 'Arşivlendi',
    'product.publishedAt': 'Yayın tarihi',
    'product.keyword': 'Ürün adı veya kodu ile ara',
    'product.translations': 'Çeviriler',
    'product.chooseFile': 'Mevcut dosya seç'
  },
  uk: {
    'menu.content.product': 'Керування продукцією',
    'product.status.DRAFT': 'Чернетка',
    'product.status.PUBLISHED': 'Опубліковано',
    'product.status.ARCHIVED': 'В архіві',
    'product.publishedAt': 'Час публікації',
    'product.keyword': 'Пошук за назвою або кодом',
    'product.translations': 'Переклади',
    'product.chooseFile': 'Вибрати наявний файл'
  },
  vi: {
    'menu.content.product': 'Quản lý sản phẩm',
    'product.status.DRAFT': 'Bản nháp',
    'product.status.PUBLISHED': 'Đã xuất bản',
    'product.status.ARCHIVED': 'Đã lưu trữ',
    'product.publishedAt': 'Thời gian xuất bản',
    'product.keyword': 'Tìm theo tên hoặc mã',
    'product.translations': 'Bản dịch',
    'product.chooseFile': 'Chọn tệp có sẵn'
  },
  th: {
    'menu.content.product': 'การจัดการสินค้า',
    'product.status.DRAFT': 'ฉบับร่าง',
    'product.status.PUBLISHED': 'เผยแพร่แล้ว',
    'product.status.ARCHIVED': 'เก็บถาวร',
    'product.publishedAt': 'เวลาเผยแพร่',
    'product.keyword': 'ค้นหาชื่อหรือรหัสสินค้า',
    'product.translations': 'คำแปล',
    'product.chooseFile': 'เลือกไฟล์ที่มีอยู่'
  },
  id: {
    'menu.content.product': 'Manajemen Produk',
    'product.status.DRAFT': 'Draf',
    'product.status.PUBLISHED': 'Dipublikasikan',
    'product.status.ARCHIVED': 'Diarsipkan',
    'product.publishedAt': 'Waktu terbit',
    'product.keyword': 'Cari nama atau kode produk',
    'product.translations': 'Terjemahan',
    'product.chooseFile': 'Pilih file yang ada'
  },
  ar: {
    'menu.content.product': 'إدارة المنتجات',
    'product.status.DRAFT': 'مسودة',
    'product.status.PUBLISHED': 'منشور',
    'product.status.ARCHIVED': 'مؤرشف',
    'product.publishedAt': 'وقت النشر',
    'product.keyword': 'ابحث باسم المنتج أو الرمز',
    'product.translations': 'الترجمات',
    'product.chooseFile': 'اختيار ملف موجود'
  },
  hi: {
    'menu.content.product': 'उत्पाद प्रबंधन',
    'product.status.DRAFT': 'प्रारूप',
    'product.status.PUBLISHED': 'प्रकाशित',
    'product.status.ARCHIVED': 'संग्रहीत',
    'product.publishedAt': 'प्रकाशन समय',
    'product.keyword': 'उत्पाद का नाम या कोड खोजें',
    'product.translations': 'अनुवाद',
    'product.chooseFile': 'मौजूदा फ़ाइल चुनें'
  }
}

const permKeys = ['perm.content.product', 'perm.content.product.media']

const permData = {
  'zh-CN': ['产品管理', '产品媒体维护'],
  'zh-TW': ['產品管理', '產品媒體維護'],
  en: ['Product management', 'Product media maintenance'],
  ja: ['製品管理', '製品メディア管理'],
  ko: ['제품 관리', '제품 미디어 관리'],
  ru: ['Управление продуктами', 'Управление медиа продукта'],
  de: ['Produktverwaltung', 'Produktmedienverwaltung'],
  fr: ['Gestion des produits', 'Gestion des médias produit'],
  es: ['Gestión de productos', 'Gestión de multimedia de productos'],
  'pt-BR': ['Gestão de produtos', 'Gestão de mídia de produtos'],
  it: ['Gestione prodotti', 'Gestione media dei prodotti'],
  nl: ['Productbeheer', 'Productmedia-beheer'],
  pl: ['Zarządzanie produktami', 'Zarządzanie mediami produktów'],
  tr: ['Ürün yönetimi', 'Ürün medya yönetimi'],
  uk: ['Керування продукцією', 'Керування медіа продукту'],
  vi: ['Quản lý sản phẩm', 'Quản lý media sản phẩm'],
  th: ['การจัดการสินค้า', 'การจัดการสื่อของสินค้า'],
  id: ['Manajemen produk', 'Pemeliharaan media produk'],
  ar: ['إدارة المنتجات', 'صيانة وسائط المنتجات'],
  hi: ['उत्पाद प्रबंधन', 'उत्पाद मीडिया रखरखाव']
}

for (const [locale, map] of Object.entries(data)) {
  const keys = Object.keys(map)
  if (keys.length !== baseKeys.length || !baseKeys.every((key) => key in map)) {
    throw new Error(`${locale} 基础键集合不匹配`)
  }
}

const baseAnchor = '"product.empty"'
for (const [locale, map] of Object.entries(data)) {
  const file = join(localeDir, `${locale}.ts`)
  let source = readFileSync(file, 'utf8')
  if (source.includes('"product.chooseFile"')) {
    console.log(`${locale}: 已存在，跳过`)
    continue
  }
  const index = source.indexOf(baseAnchor)
  if (index < 0) {
    throw new Error(`${locale}: 找不到锚点 ${baseAnchor}`)
  }
  const lineEnd = source.indexOf('\n', index)
  const lines = baseKeys.map((key) => `  ${JSON.stringify(key)}: ${JSON.stringify(map[key])},`)
  source = source.slice(0, lineEnd) + `\n${lines.join('\n')}` + source.slice(lineEnd)
  writeFileSync(file, source, 'utf8')
  console.log(`${locale}: 追加 ${baseKeys.length} 个键`)
}

for (const [locale, values] of Object.entries(permData)) {
  const file = join(permDir, `${locale}.ts`)
  let source = readFileSync(file, 'utf8')
  if (source.includes('"perm.content.product"')) {
    console.log(`perm ${locale}: 已存在，跳过`)
    continue
  }
  const closeIndex = source.lastIndexOf('}\n')
  if (closeIndex < 0) {
    throw new Error(`${locale}: 找不到结尾`)
  }
  const before = source.slice(0, closeIndex)
  const lastComma = before.trimEnd().endsWith(',') ? '' : ','
  const lines = permKeys.map(
    (key, index) => `  ${JSON.stringify(key)}: ${JSON.stringify(values[index])}${index === permKeys.length - 1 ? '' : ','}`
  )
  const insert = `\n${lines.join('\n')}${lastComma}\n`
  source = before.replace(/\s*$/, '') + ',' + insert + '}\n'
  writeFileSync(file, source, 'utf8')
  console.log(`perm ${locale}: 追加 ${permKeys.length} 个键`)
}
