// 後台「欄位級」說明文案（zh-TW）。鍵集合必須與其他語言完全一致（field-help.test.ts 強制校驗）。
// 與 locales/help/<locale>.ts 的頁面級說明分檔案維護，合併規則見 locales/help/index.ts。

const message = {
  "fieldHelp.open": "欄位說明",
  "field.navs.labelKey.help": "選單名稱的文案 key，形如 nav.about。該 key 必須已在語言資源中存在，否則入口網站會直接顯示原始 key。",
  "field.navs.target.help": "點擊後跳轉的相對路徑，例如 /、/about、/articles。前台會自動補上語言前綴，不要寫完整網域。",
  "field.navs.sortNo.help": "數字越小越前面；同層選單依此值排序，可留空（預設 0）。",
  "field.pages.slug.help": "頁面網址的一部分（/{locale}/{slug}）。發佈後盡量不要再改，舊連結會 404。",
  "field.articles.slug.help": "文章網址的一部分（/{locale}/articles/{slug}），同時影響既有連結與搜尋引擎收錄。",
  "field.articles.category.help": "決定文章出現在哪個分類頁（/{locale}/categories/{code}）。分類為單層結構，可留空。",
  "field.articles.tags.help": "文章標籤，可多選；入口網站可依標籤篩選。沒有合適的標籤時可以現場新建。",
  "field.articles.newTag.help": "輸入標籤編碼後點「新建標籤」，會建立標籤並自動選取它。",
  "field.categories.code.help": "分類編碼會進入網址 /categories/{code}，建立後不要修改。",
  "field.tags.code.help": "標籤的唯一編碼，用於篩選與去重；標籤不進入網址，改名不會影響連結。",
  "field.users.username.help": "登入名稱，建立後不可修改。建議使用信箱前綴或員工編號，避免手機號等會變動的資訊。",
  "field.users.password.help": "僅新建時填寫初始密碼；首次登入會要求修改。留空則由系統依密碼策略產生。",
  "field.users.dept.help": "帳號所屬組織，決定「本組織」「本組織及以下」資料範圍的歸屬；選錯會導致看不到資料或看到不該看的資料。",
  "field.users.roles.help": "角色決定選單與按鈕權限，可多選，權限取聯集；至少要選一個角色。",
  "field.users.status.help": "停用後該帳號無法登入，資料與日誌保留。",
  "field.roles.code.help": "角色編碼用於介面授權與內建角色判斷，建立後不可修改，建議大寫加底線。",
  "field.roles.dataScope.help": "決定該角色能看到哪些組織的資料。選擇「自訂」時需要再到「組織授權」裡勾選具體組織。",
  "field.configs.type.help": "參數類型唯讀，決定值如何解析（字串、數字、布林、JSON）。類型與值不符會導致讀取失敗。",
  "field.configs.value.help": "依類型填寫值；JSON 必須是合法 JSON。金鑰類參數只放佔位或環境變數參照，不要明文入庫。",
  "field.dicts.typeCode.help": "程式依這個編碼讀取字典，建議小寫加底線，建立後不要修改。",
  "field.dicts.nameKey.help": "介面顯示名稱使用的文案 key（如 dict.someName），需要已在語言資源中存在。",
  "field.dicts.itemValue.help": "字典項的值，會存庫並透過介面回傳；建立後不建議修改，避免歷史資料對不上。",
  "field.dicts.labelZh.help": "中文介面顯示的標籤；英文標籤在下一項單獨維護。",
  "field.jobs.cron.help": "6 位運算式：秒 分 時 日 月 週。例如 0 0 3 * * * 表示每天 03:00 執行；可用「執行一次」驗證。",
  "field.jobs.params.help": "傳給任務處理邏輯的 JSON 物件，沒有參數就留空。",
  "field.jobs.concurrency.help": "禁止併發時，上一次還沒結束會跳過本次；允許併發則同時執行多次。",
  "field.banners.image.help": "先上傳到「檔案管理」，再在這裡選擇；建議 16:9 橫圖，單張不超過 500 KB。",
  "field.banners.link.help": "點擊輪播後跳轉的位址，可寫相對路徑或完整 URL；留空表示不可點擊。",
  "field.banners.startAt.help": "到點後自動出現在入口網站，留空表示立即生效。",
  "field.banners.endAt.help": "到點後自動下線，留空表示長期有效。",
  "field.mail.variables.help": "範本變數用 JSON 物件填寫，鍵要與正文裡的 {name} 佔位符對應，僅用於寄送測試。",
  "field.notices.publishAt.help": "到點後自動出現在入口網站，留空表示立即生效。",
  "field.notices.expireAt.help": "到點後自動下線，留空表示長期有效。"
}

export default message
