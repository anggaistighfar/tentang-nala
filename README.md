# Tentang Nala

Aplikasi jurnal harian untuk Android: tulis harimu, pilih mood, simpan foto atau sketsa S Pen, dan kumpulkan XP setiap kali menulis. Semua catatan tersimpan di HP; tidak ada akun dan tidak ada server.

Dioptimalkan untuk Samsung Galaxy S25 Ultra dan S Pen. Berjalan di Android 8.0 ke atas.

## ⬇️ Download

**[Download APK terbaru](https://github.com/anggaistighfar/tentang-nala/releases/latest/download/TentangNala-v1.0.0.apk)** (±3 MB)

Atau buka halaman [Releases](https://github.com/anggaistighfar/tentang-nala/releases).

### Cara pasang di HP

1. Buka link download di atas dari browser HP (Chrome atau Samsung Internet).
2. Setelah selesai, ketuk file `TentangNala-v1.0.0.apk`.
3. Kalau muncul peringatan, ketuk **Pengaturan** lalu aktifkan **Izinkan dari sumber ini**, kembali, lalu ketuk **Instal**.
4. Kalau Play Protect bertanya, pilih **Tetap instal**. Peringatan ini muncul karena aplikasi belum ada di Play Store.

## Fitur

| | |
|---|---|
| Catatan bebas | Judul + isi, pancingan menulis sesuai mood, hitungan kata |
| Mood | 5 wajah mood + pilihan emosi (bisa tambah sendiri) |
| Pengingat harian | Jam bebas; tidak muncul kalau kamu sudah menulis hari itu |
| Foto & sketsa | Kamera, galeri, atau gambar dengan S Pen |
| Arsip & statistik | Per bulan, cari, filter mood; kalender mood minggu/bulan/tahun |
| XP & level | XP per hari menulis, bonus mood/emosi/foto/streak |
| Kunci jurnal | Isi tulisan dienkripsi (AES-256), dibuka dengan sidik jari/wajah/PIN; opsi kunci seluruh aplikasi |
| Font per jurnal | Lora, Space Grotesk, Nunito, Caveat, Courier Prime, DM Serif, font sistem |
| Hemat penyimpanan | Foto dikompres ke WebP (biasanya puluhan sampai ratusan KB), sketsa ±10 KB |
| S Pen | Tekanan mengatur tebal garis, tombol S Pen untuk menghapus, telapak tangan diabaikan |
| Tema | Kertas (terang) dan Malam (gelap) |

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
  domain/      Mood & emosi, aturan XP, pancingan menulis
  reminder/    Alarm & notifikasi harian
  security/    Biometrik / PIN
  ui/          Tema, komponen, layar
```

## Lisensi pihak ketiga

Font yang dibundel memakai SIL Open Font License 1.1; teks lisensinya ada di [`licenses/fonts/`](licenses/fonts).
