# Tentang Nala

Aplikasi jurnal harian untuk Android: tulis harimu, pilih mood, simpan foto atau sketsa S Pen, dan kumpulkan XP setiap kali menulis. Semua catatan tersimpan di HP; tidak ada akun dan tidak ada server.

Dioptimalkan untuk Samsung Galaxy S25 Ultra dan S Pen. Berjalan di Android 8.0 ke atas.

## ⬇️ Download

**[Download APK terbaru](https://github.com/anggaistighfar/tentang-nala/releases/latest/download/TentangNala.apk)** (±3 MB)

Atau buka halaman [Releases](https://github.com/anggaistighfar/tentang-nala/releases).

### Cara pasang di HP

1. Buka link download di atas dari browser HP (Chrome atau Samsung Internet).
2. Setelah selesai, ketuk file `TentangNala.apk`.
3. Kalau muncul peringatan, ketuk **Pengaturan** lalu aktifkan **Izinkan dari sumber ini**, kembali, lalu ketuk **Instal**.
4. Kalau Play Protect bertanya, pilih **Tetap instal**. Peringatan ini muncul karena aplikasi belum ada di Play Store.

## Fitur

| | |
|---|---|
| Catatan bebas | Judul + isi, pancingan menulis sesuai mood, hitungan kata; halaman ikut turun otomatis saat mengetik |
| Format teks | Judul, subjudul, daftar bernomor dan daftar poin (tombol atau ketik `1. ` / `- `; Enter melanjutkan, Enter di baris kosong mengakhiri), rata kiri/tengah/kanan/kiri-kanan, tombol **Rapikan** |
| Tulis langsung dengan S Pen | Mode pena: tulis tangan langsung di halaman jurnal seperti Samsung Notes; jari tetap menggeser halaman |
| Mood | 5 wajah mood + pilihan emosi (bisa tambah sendiri) |
| Pengingat harian | Jam bebas; tidak muncul kalau kamu sudah menulis hari itu |
| Foto & sketsa | Kamera, galeri, atau gambar dengan S Pen |
| Arsip & statistik | Per bulan, cari, filter mood; kalender mood minggu/bulan/tahun |
| XP & level | XP per hari menulis, bonus mood/emosi/foto/streak |
| Kunci jurnal | Isi tulisan dienkripsi (AES-256), dibuka dengan sidik jari/wajah/PIN; opsi kunci seluruh aplikasi |
| Font per jurnal | Lora, Space Grotesk, Nunito, Caveat, Courier Prime, DM Serif, font sistem |
| Hemat penyimpanan | Foto dikompres ke WebP (biasanya puluhan sampai ratusan KB), sketsa ±10 KB |
| S Pen | Tekanan mengatur tebal garis, tahan tombol S Pen untuk menghapus, telapak tangan diabaikan, pratinjau saat pena melayang |
| Tema | Kertas (terang) dan Malam (gelap) |
| Analisis Nala | Analisis pola mood dengan pendekatan psikologi, plus saran yang bisa dilakukan (lihat di bawah) |

## Analisis Nala

Halaman **Analisis Nala** (dari Beranda atau Statistik) membaca jurnal 7, 30, atau 90 hari terakhir dan menunjukkan:

- **Ringkasan mood**: rata-rata, tren dibanding periode sebelumnya, naik-turunnya mood, grafik per hari.
- **Emosi yang paling sering muncul** dan keseimbangan emosi positif dan negatif.
- **Apa yang memengaruhi mood-mu**: topik dan aktivitas di tulisanmu (pekerjaan, keluarga, tidur, olahraga, teman, dll.) beserta rata-rata mood saat topik itu muncul.
- **Pola pikir yang perlu dicermati**, dari teknik CBT: memberi label buruk pada diri, generalisasi berlebihan, membayangkan yang terburuk, tuntutan "harusnya", menebak isi pikiran orang. Disertai kutipan dari tulisanmu dan pertanyaan untuk menguji pikiran itu.
- **Yang bisa kamu lakukan**: latihan langkah demi langkah sesuai polamu (catatan pikiran, grounding, aktivasi perilaku, welas asih diri, kebiasaan tidur, menghubungi orang lain, waktu khawatir, tiga hal baik). Tombol "Coba tulis sekarang" membuka jurnal dengan panduan latihannya.
- **Ringkasan untuk psikolog**: angka mood, emosi, topik, dan pola dalam bentuk teks yang bisa dibagikan ke psikolog atau konselor. Isi tulisan jurnal tidak ikut dibagikan.

Setiap catatan juga mendapat **Catatan dari Nala**: tanggapan singkat dan satu saran.

**Keselamatan.** Kalau tulisan menyebut keinginan untuk mati atau menyakiti diri, aplikasi tidak menampilkan pop-up XP. Yang muncul adalah kartu bantuan dengan **Healing119 Kemenkes (telepon 119, ekstensi 8, gratis)** dan chat di **healing119.id**. Kalau mood rendah bertahan sekitar dua minggu, aplikasi menyarankan bicara dengan psikolog.

Analisis berjalan sepenuhnya di HP dengan aturan yang bisa dibaca di [`Insights.kt`](app/src/main/java/com/lembar/journal/domain/Insights.kt) dan [`Lexicon.kt`](app/src/main/java/com/lembar/journal/domain/Lexicon.kt). Tidak ada data yang dikirim ke internet. Isi jurnal terkunci hanya ikut dianalisis kalau kamu membukanya dengan sidik jari/PIN di halaman itu.

> Analisis ini otomatis dan **bukan diagnosis atau pengganti psikolog**. Kalau perasaanmu terasa berat atau berlangsung lama, bicaralah dengan tenaga profesional.

## Cadangkan & Pulihkan

**Pengaturan → Cadangan**

- **Buat cadangan**: semua jurnal (termasuk yang terkunci), foto, sketsa, dan tulisan tangan disimpan ke satu file `.nala` yang dikunci dengan password pilihanmu (AES-256-GCM, kunci dari PBKDF2 210.000 iterasi). Kamu memilih sendiri lokasinya: folder Download, Google Drive, dll.
- **Pulihkan dari cadangan**: pilih file `.nala` dan masukkan password-nya. Jurnal yang belum ada di HP ditambahkan; yang sudah ada dilewati. **Tidak ada jurnal yang dihapus atau ditimpa.** Password salah atau file rusak ditolak sebelum apa pun berubah.
- Bisa dipakai untuk pindah ke HP baru, atau memulihkan jurnal setelah aplikasi terhapus.
- Password tidak bisa dipulihkan. Catat di tempat aman.

## Update aplikasi tanpa kehilangan data

Pasang APK versi baru **di atas** versi lama (ketuk file APK → Perbarui). Semua versi memakai ID aplikasi dan kunci tanda tangan yang sama, jadi Android memperlakukannya sebagai update dan jurnal tetap ada. **Jangan uninstall** sebelum update; uninstall menghapus semua jurnal di HP. Untuk berjaga-jaga, buat cadangan dulu sebelum update.

## Privasi

- Data hanya ada di HP: database dan foto berada di penyimpanan privat aplikasi, tidak muncul di Galeri.
- Data tidak ikut backup Google, karena kunci enkripsi terikat ke HP. Uninstall aplikasi = jurnal ikut terhapus.
- Aplikasi tidak memakai izin internet.

## Build dari source

Butuh JDK 17 dan Android SDK (API 35). Atau buka folder ini di Android Studio.

```sh
./gradlew :app:assembleDebug      # APK debug
./gradlew :app:assembleRelease    # APK rilis (perlu keystore.properties)
```

Untuk build rilis bertanda tangan, buat `keystore.properties` di root project (file ini tidak di-commit):

```properties
storeFile=keystore/tentang-nala-release.jks
storePassword=...
keyAlias=tentangnala
keyPassword=...
```

Tanpa file itu, build rilis memakai debug key.

### Struktur

```
app/src/main/java/com/lembar/journal/
  data/        Room DB, enkripsi, penyimpanan foto, pengaturan
  domain/      Mood & emosi, aturan XP, pancingan menulis, analisis (Insights, Lexicon)
  reminder/    Alarm & notifikasi harian
  security/    Biometrik / PIN
  ui/          Tema, komponen, layar
```

## Lisensi pihak ketiga

Font yang dibundel memakai SIL Open Font License 1.1; teks lisensinya ada di [`licenses/fonts/`](licenses/fonts).
