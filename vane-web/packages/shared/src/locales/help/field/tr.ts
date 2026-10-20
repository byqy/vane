// Yönetici formları için alan düzeyinde yardım metinleri (tr). Anahtar kümesi diğer tüm dillerle aynı olmalıdır (field-help.test.ts ile zorunlu).
// locales/help/<locale>.ts içindeki sayfa düzeyinde yardımdan ayrı dosyada tutulur; birleştirme kuralı locales/help/index.ts içindedir.

const message = {
  "fieldHelp.open": "Alan açıklaması",
  "field.navs.labelKey.help": "Menü adının mesaj anahtarı, örneğin nav.about. Bu anahtar dil kaynaklarında zaten bulunmalıdır; aksi halde portal ham anahtarı gösterir.",
  "field.navs.target.help": "Tıklandığında açılacak göreli yol, örneğin /, /about, /articles. Ön yüz dil önekini kendisi ekler, tam alan adı yazmayın.",
  "field.navs.sortNo.help": "Küçük değer önce gelir; aynı düzeydeki menüler bu değere göre sıralanır. Varsayılan 0 için boş bırakılabilir.",
  "field.pages.slug.help": "Sayfa adresinin bir parçası (/{locale}/{slug}). Yayından sonra değiştirmemeye çalışın, eski bağlantılar 404 verir.",
  "field.articles.slug.help": "Makale adresinin bir parçası (/{locale}/articles/{slug}); mevcut bağlantıları ve arama motoru dizinlemesini de etkiler.",
  "field.articles.category.help": "Makalenin hangi kategori sayfasında görüneceğini belirler (/{locale}/categories/{code}). Kategoriler tek düzeylidir, boş bırakılabilir.",
  "field.articles.tags.help": "Makale etiketleri, birden fazla seçilebilir; portal etikete göre filtreleyebilir. Uygun etiket yoksa burada yenisi oluşturulabilir.",
  "field.articles.newTag.help": "Etiket kodunu girip “Yeni etiket” düğmesine basın; etiket oluşturulur ve bu makale için otomatik seçilir.",
  "field.categories.code.help": "Kategori kodu /categories/{code} adresine girer; oluşturduktan sonra değiştirmeyin.",
  "field.tags.code.help": "Filtreleme ve yinelenenleri ayıklama için kullanılan benzersiz etiket kodu; etiketler adrese girmez, yeniden adlandırma bağlantıları bozmaz.",
  "field.users.username.help": "Oturum açma adı, oluşturulduktan sonra değiştirilemez. Değişebilecek bilgiler yerine e-posta öneki veya personel numarası kullanın.",
  "field.users.password.help": "Yalnızca yeni hesap için ilk parola; ilk oturum açılışında değiştirilmesi istenir. Boş bırakılırsa sistem parola ilkesine göre üretir.",
  "field.users.dept.help": "Hesabın bağlı olduğu kuruluş; “bu kuruluş” ve “bu kuruluş ve altı” veri kapsamlarının aidiyetini belirler. Yanlış seçim verilerin görünmemesine ya da görmemeniz gereken verilerin görünmesine yol açar.",
  "field.users.roles.help": "Roller menü ve düğme izinlerini belirler; birden fazla rol seçilebilir ve izinler birleşir. En az bir rol seçilmelidir.",
  "field.users.status.help": "Devre dışı bırakılan hesap oturum açamaz; verileri ve günlükleri korunur.",
  "field.roles.code.help": "Rol kodu API yetkilendirmesi ve yerleşik rol denetimi için kullanılır; oluşturulduktan sonra değiştirilemez. Büyük harf ve alt çizgi önerilir.",
  "field.roles.dataScope.help": "Rolün hangi kuruluşların verilerini görebileceğini belirler. “Özel” seçildiğinde ayrıca Kuruluş kapsamı bölümünden kuruluşları işaretlemeniz gerekir.",
  "field.configs.type.help": "Parametre türü salt okunurdur; değerin nasıl ayrıştırılacağını belirler (metin, sayı, mantıksal, JSON). Tür ile değer uyuşmazsa okuma başarısız olur.",
  "field.configs.value.help": "Değeri türüne göre girin; JSON geçerli JSON olmalıdır. Gizli parametrelerde yalnızca yer tutucu veya ortam değişkeni başvurusu kullanın, düz metin olarak kaydetmeyin.",
  "field.dicts.typeCode.help": "Programlar sözlüğü bu koda göre okur; küçük harf ve alt çizgi önerilir, oluşturulduktan sonra değiştirmeyin.",
  "field.dicts.nameKey.help": "Arayüzde görünen adın mesaj anahtarı (örneğin dict.someName); dil kaynaklarında zaten bulunmalıdır.",
  "field.dicts.itemValue.help": "Sözlük öğesinin değeri veritabanında saklanır ve API ile döner; geçmiş verilerin eşleşmesi için oluşturduktan sonra değiştirmemeniz önerilir.",
  "field.dicts.labelZh.help": "Çince arayüzde gösterilen etiket; İngilizce etiket bir sonraki alanda ayrıca tutulur.",
  "field.jobs.cron.help": "6 alanlı ifade: saniye dakika saat gün ay haftanın günü. Örneğin 0 0 3 * * * her gün 03:00'te çalışır; “Bir kez çalıştır” ile doğrulayabilirsiniz.",
  "field.jobs.params.help": "Görev işleyicisine aktarılan JSON nesnesi; parametre yoksa boş bırakın.",
  "field.jobs.concurrency.help": "Eşzamanlılık kapalıyken önceki çalışma bitmemişse bu çalışma atlanır; açıkken aynı anda birden çok kez çalışabilir.",
  "field.banners.image.help": "Dosyayı önce “Dosya yönetimi” altına yükleyin, sonra burada seçin; 16:9 yatay görsel ve görsel başına en fazla 500 KB önerilir.",
  "field.banners.link.help": "Tıklamada gidilecek adres; göreli yol veya tam URL yazılabilir. Boş bırakılırsa tıklanamaz.",
  "field.banners.startAt.help": "Belirtilen zamanda portalda otomatik görünür; boş bırakılırsa hemen yürürlüğe girer.",
  "field.banners.endAt.help": "Belirtilen zamanda otomatik olarak yayından kalkar; boş bırakılırsa süresiz geçerli olur.",
  "field.mail.variables.help": "Şablon değişkenleri JSON nesnesi olarak girilir; anahtarlar gövdedeki {name} yer tutucularıyla eşleşmelidir. Yalnızca test gönderimi içindir.",
  "field.notices.publishAt.help": "Belirtilen zamanda portalda otomatik görünür; boş bırakılırsa hemen yürürlüğe girer.",
  "field.notices.expireAt.help": "Belirtilen zamanda otomatik olarak yayından kalkar; boş bırakılırsa süresiz geçerli olur."
}

export default message
