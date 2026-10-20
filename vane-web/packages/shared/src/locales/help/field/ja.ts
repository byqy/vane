// 管理画面「フィールド単位」のヘルプ文言（ja）。キー集合は他の言語と完全に一致させること（field-help.test.ts で強制）。
// locales/help/<locale>.ts のページ単位ヘルプとは別ファイルで管理し、マージ規則は locales/help/index.ts を参照。

const message = {
  "fieldHelp.open": "項目の説明",
  "field.navs.labelKey.help": "メニュー名のテキストキー（例: nav.about）。このキーは言語リソースに存在している必要があり、存在しない場合はポータルに生のキーが表示されます。",
  "field.navs.target.help": "クリック時の遷移先の相対パス（例: /、/about、/articles）。言語プレフィックスはフロント側で自動付与されるため、ドメイン名は入力しないでください。",
  "field.navs.sortNo.help": "値が小さいほど上位に表示されます。同じ階層のメニューはこの値で並び、空欄の場合は 0 になります。",
  "field.pages.slug.help": "ページ URL の一部（/{locale}/{slug}）。公開後は変更しないでください。古いリンクが 404 になります。",
  "field.articles.slug.help": "記事 URL の一部（/{locale}/articles/{slug}）。既存のリンクと検索エンジンのインデックスにも影響します。",
  "field.articles.category.help": "記事をどのカテゴリページに表示するかを決めます（/{locale}/categories/{code}）。カテゴリは 1 階層のみで、空欄でも構いません。",
  "field.articles.tags.help": "記事のタグ。複数選択でき、ポータルではタグで絞り込めます。適切なタグがない場合はその場で作成できます。",
  "field.articles.newTag.help": "タグコードを入力して「新しいタグ」を押すと、タグが作成され自動的に選択されます。",
  "field.categories.code.help": "カテゴリコードは URL /categories/{code} に含まれます。作成後は変更しないでください。",
  "field.tags.code.help": "絞り込みと重複排除に使うタグの一意なコード。タグは URL に含まれないため、名前を変えてもリンクは壊れません。",
  "field.users.username.help": "ログイン名で、作成後は変更できません。電話番号など変わりうる情報は避け、メールのプレフィックスや社員番号を推奨します。",
  "field.users.password.help": "新規作成時のみ初期パスワードを入力します。初回ログイン時に変更が求められます。空欄の場合はパスワードポリシーに従って自動生成されます。",
  "field.users.dept.help": "アカウントが所属する組織で、「自組織」「自組織および下位」のデータ範囲の基準になります。誤るとデータが見えない、または見えてはいけないデータが見えます。",
  "field.users.roles.help": "ロールはメニューとボタンの権限を決めます。複数選択でき、権限は和集合になります。少なくとも 1 つ選択してください。",
  "field.users.status.help": "無効化するとこのアカウントはログインできません。データとログは保持されます。",
  "field.roles.code.help": "ロールコードは API の認可と組み込みロールの判定に使われます。作成後は変更できません。大文字とアンダースコアを推奨します。",
  "field.roles.dataScope.help": "このロールが参照できる組織のデータを決めます。「カスタム」を選んだ場合は「組織の認可」で対象組織を選択してください。",
  "field.configs.type.help": "パラメータ型は読み取り専用で、値の解釈方法（文字列、数値、真偽値、JSON）を決めます。型と値が一致しないと読み取りに失敗します。",
  "field.configs.value.help": "型に従って値を入力してください。JSON は有効な JSON である必要があります。秘密情報はプレースホルダーか環境変数参照のみとし、平文で保存しないでください。",
  "field.dicts.typeCode.help": "プログラムはこのコードで辞書を読み込みます。小文字とアンダースコアを推奨し、作成後は変更しないでください。",
  "field.dicts.nameKey.help": "画面に表示する名称のテキストキー（例: dict.someName）。言語リソースに存在している必要があります。",
  "field.dicts.itemValue.help": "辞書項目の値として保存され、API で返されます。過去データと食い違わないよう、作成後は変更しないでください。",
  "field.dicts.labelZh.help": "中国語画面に表示するラベル。英語のラベルは次の項目で別に管理します。",
  "field.jobs.cron.help": "6 桁の式：秒 分 時 日 月 曜日。たとえば 0 0 3 * * * は毎日 03:00 に実行する意味です。「一度実行」で確認できます。",
  "field.jobs.params.help": "ジョブの処理ロジックに渡す JSON オブジェクト。パラメータがない場合は空欄にします。",
  "field.jobs.concurrency.help": "同時実行を禁止すると、前回が終わっていない場合は今回をスキップします。許可すると同時に複数回実行されます。",
  "field.banners.image.help": "先に「ファイル管理」へアップロードし、ここで選択します。16:9 の横長画像、1 枚 500 KB 以下を推奨します。",
  "field.banners.link.help": "クリック時の遷移先。相対パスまたは完全な URL を入力できます。空欄の場合はクリックできません。",
  "field.banners.startAt.help": "指定時刻になるとポータルに自動表示されます。空欄の場合はすぐに反映されます。",
  "field.banners.endAt.help": "指定時刻になると自動的に非表示になります。空欄の場合は無期限で有効です。",
  "field.mail.variables.help": "テンプレート変数を JSON オブジェクトで入力します。キーは本文の {name} プレースホルダーと対応させてください。テスト送信でのみ使用します。",
  "field.notices.publishAt.help": "指定時刻になるとポータルに自動表示されます。空欄の場合はすぐに反映されます。",
  "field.notices.expireAt.help": "指定時刻になると自動的に非表示になります。空欄の場合は無期限で有効です。"
}

export default message
