// Teks bantuan tingkat kolom untuk formulir admin (id). Himpunan kunci harus sama dengan semua bahasa lain (diperiksa field-help.test.ts).
// Disimpan terpisah dari bantuan tingkat halaman di locales/help/<locale>.ts; aturan penggabungan ada di locales/help/index.ts.

const message = {
  "fieldHelp.open": "Bantuan kolom",
  "field.navs.labelKey.help": "Kunci pesan untuk nama menu, misalnya nav.about. Kunci harus sudah ada di sumber daya bahasa, jika tidak portal akan menampilkan kunci mentah.",
  "field.navs.target.help": "Jalur relatif yang dibuka saat diklik, misalnya /, /about, /articles. Antarmuka menambahkan prefiks bahasa sendiri, jangan isi domain lengkap.",
  "field.navs.sortNo.help": "Nilai lebih kecil tampil lebih dulu; menu selevel diurutkan berdasarkan nilai ini. Boleh dikosongkan (default 0).",
  "field.pages.slug.help": "Bagian dari URL halaman (/{locale}/{slug}). Sebaiknya jangan diubah setelah terbit, tautan lama akan 404.",
  "field.articles.slug.help": "Bagian dari URL artikel (/{locale}/articles/{slug}); juga memengaruhi tautan yang ada dan indeks mesin pencari.",
  "field.articles.category.help": "Menentukan halaman kategori tempat artikel tampil (/{locale}/categories/{code}). Kategori satu tingkat dan boleh dikosongkan.",
  "field.articles.tags.help": "Tag artikel, bisa lebih dari satu; portal dapat memfilter berdasarkan tag. Jika belum ada tag yang cocok, buat langsung di sini.",
  "field.articles.newTag.help": "Masukkan kode tag lalu tekan “Tag baru”: tag dibuat dan otomatis dipilih untuk artikel ini.",
  "field.categories.code.help": "Kode kategori masuk ke URL /categories/{code}; jangan diubah setelah dibuat.",
  "field.tags.code.help": "Kode tag unik untuk pemfilteran dan penghapusan duplikat; tag tidak masuk URL sehingga mengganti nama tidak merusak tautan.",
  "field.users.username.help": "Nama masuk, tidak dapat diubah setelah dibuat. Sebaiknya gunakan prefiks email atau nomor pegawai, hindari data yang bisa berubah.",
  "field.users.password.help": "Kata sandi awal hanya untuk akun baru; pengguna diminta menggantinya saat masuk pertama. Kosongkan agar sistem membuatnya sesuai kebijakan kata sandi.",
  "field.users.dept.help": "Organisasi tempat akun bernaung; menentukan kepemilikan cakupan data “organisasi ini” dan “organisasi ini dan di bawahnya”. Salah pilih membuat data tidak terlihat atau data yang seharusnya tidak terlihat ikut tampil.",
  "field.users.roles.help": "Peran menentukan izin menu dan tombol; bisa lebih dari satu dan izin digabung. Minimal satu peran harus dipilih.",
  "field.users.status.help": "Akun yang dinonaktifkan tidak dapat masuk; data dan log tetap disimpan.",
  "field.roles.code.help": "Kode peran dipakai untuk otorisasi API dan pemeriksaan peran bawaan; tidak dapat diubah setelah dibuat, gunakan huruf besar dengan garis bawah.",
  "field.roles.dataScope.help": "Menentukan organisasi mana yang datanya dapat dilihat peran ini. Jika memilih “Kustom”, Anda harus mencentang organisasi di bagian Cakupan organisasi.",
  "field.configs.type.help": "Tipe parameter hanya baca; menentukan cara nilai diurai (string, angka, boolean, JSON). Tipe yang tidak cocok dengan nilai membuat pembacaan gagal.",
  "field.configs.value.help": "Isi nilai sesuai tipenya; JSON harus JSON yang valid. Untuk parameter rahasia gunakan placeholder atau referensi variabel lingkungan, jangan simpan teks polos.",
  "field.dicts.typeCode.help": "Program membaca kamus berdasarkan kode ini; sebaiknya huruf kecil dengan garis bawah, jangan diubah setelah dibuat.",
  "field.dicts.nameKey.help": "Kunci pesan nama tampilan (misalnya dict.someName); harus sudah ada di sumber daya bahasa.",
  "field.dicts.itemValue.help": "Nilai item kamus disimpan dan dikembalikan melalui API; sebaiknya tidak diubah setelah dibuat agar data lama tetap cocok.",
  "field.dicts.labelZh.help": "Label yang tampil di antarmuka bahasa Mandarin; label bahasa Inggris dikelola terpisah di kolom berikutnya.",
  "field.jobs.cron.help": "Ekspresi 6 bidang: detik menit jam hari bulan hari pekan. Contoh 0 0 3 * * * berjalan setiap hari pukul 03:00; verifikasi dengan “Jalankan sekali”.",
  "field.jobs.params.help": "Objek JSON yang diteruskan ke penangan tugas; kosongkan jika tidak ada parameter.",
  "field.jobs.concurrency.help": "Saat konkurensi dilarang, proses dilewati jika proses sebelumnya belum selesai; saat diizinkan, beberapa proses dapat tumpang tindih.",
  "field.banners.image.help": "Unggah berkas ke “Manajemen berkas” dahulu, lalu pilih di sini; sebaiknya gambar lanskap 16:9 dengan ukuran maksimal 500 KB per gambar.",
  "field.banners.link.help": "Tujuan saat diklik: jalur relatif atau URL lengkap. Kosongkan agar tidak dapat diklik.",
  "field.banners.startAt.help": "Banner otomatis muncul di portal pada waktu tersebut; kosongkan agar berlaku segera.",
  "field.banners.endAt.help": "Banner otomatis diturunkan pada waktu tersebut; kosongkan agar berlaku selamanya.",
  "field.mail.variables.help": "Variabel templat diisi sebagai objek JSON; kunci harus cocok dengan placeholder {name} di isi. Hanya untuk pengiriman uji.",
  "field.notices.publishAt.help": "Pengumuman otomatis muncul di portal pada waktu tersebut; kosongkan agar berlaku segera.",
  "field.notices.expireAt.help": "Pengumuman otomatis diturunkan pada waktu tersebut; kosongkan agar berlaku selamanya."
}

export default message
