# Aset GA — Sistem Manajemen Aset & Inventaris

Aplikasi Android offline-first untuk tim General Affair: register aset, scan QR, mutasi, servis, stok barang habis pakai, dashboard, notifikasi, import/export Excel, dan backup terenkripsi.

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture ringan · Hilt · Room · Navigation Compose · WorkManager.

## Status pengerjaan

| Tahap | Isi | Status |
|---|---|---|
| a | Setup proyek, tema terang/gelap, navigasi, master data, pengguna & peran, login + biometrik/PIN, konfigurasi release | ✅ selesai |
| b | Register aset + foto | ⏳ |
| c | Scan QR/barcode + label QR | ⏳ |
| d | Mutasi + servis | ⏳ |
| e | Stok barang habis pakai | ⏳ |
| f | Dashboard + notifikasi | ⏳ |
| g | Import/export, backup, audit log, stock opname | ⏳ |

## Mendapatkan APK siap install (tanpa Android Studio)

Repo ini dilengkapi GitHub Actions (`.github/workflows/android.yml`). Setiap push akan:
1. menjalankan seluruh unit test (termasuk test DAO Room lewat Robolectric),
2. mem-build **APK debug** dan mengunggahnya sebagai artifact `aset-ga-debug-apk`.

Unduh APK: tab **Actions** → run terbaru → bagian **Artifacts** → `aset-ga-debug-apk` (ZIP berisi `.apk`). Salin ke HP, buka, dan izinkan "Instal dari sumber tidak dikenal".

## Build di komputer sendiri

Prasyarat: Android Studio terbaru (berisi Android SDK) dan JDK 17+.

1. Buka folder proyek di Android Studio, tunggu Gradle sync selesai.
2. Pilih emulator atau HP (aktifkan *USB debugging*), lalu tekan **Run**.

Atau lewat terminal:

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew installDebug
```

Build debug memakai `applicationId` berakhiran `.debug` dan otomatis mengisi data contoh (lokasi, departemen, vendor; aset menyusul di tahap b). Build release tidak pernah berisi data contoh.

### Pertama kali dibuka
Aplikasi meminta pembuatan akun **Admin GA** pertama. Setelah itu Admin dapat menambah pengguna lain (Staf GA / Viewer) dari **Lainnya → Pengguna & peran**.

## Build release (APK/AAB)

Keystore **tidak pernah** di-commit (`*.jks`, `*.keystore` ada di `.gitignore`). Signing dibaca dari variabel lingkungan.

1. Buat keystore sekali saja, lalu simpan di tempat aman (di luar repo):
   ```bash
   keytool -genkeypair -v -keystore ~/keys/aset-ga-release.jks -alias asetga -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Set variabel lingkungan lalu build:
   ```bash
   export ASETGA_KEYSTORE_PATH=~/keys/aset-ga-release.jks ASETGA_KEYSTORE_PASSWORD='***' ASETGA_KEY_ALIAS=asetga ASETGA_KEY_PASSWORD='***'
   ```
   ```bash
   ./gradlew assembleRelease bundleRelease
   ```
   Hasil: `app/build/outputs/apk/release/app-release.apk` dan `app/build/outputs/bundle/release/app-release.aab`.

Untuk build release di GitHub Actions, isi secret repo: `ASETGA_KEYSTORE_BASE64` (hasil `base64 -w0 aset-ga-release.jks`), `ASETGA_KEYSTORE_PASSWORD`, `ASETGA_KEY_ALIAS`, `ASETGA_KEY_PASSWORD`. Job `release` lalu mengunggah APK & AAB bertanda tangan.

Release memakai R8 (minify + shrink resources).

## Struktur kode

```
app/src/main/java/id/perusahaan/asetga/
├─ data/      local (Room: entity, dao, database), repository (implementasi), seed
├─ domain/    model, repository (interface), security, session, validation
├─ ui/        theme, navigation, components, feature/<layar>
├─ di/        modul Hilt
└─ util/      format Rupiah & tanggal Indonesia
app/src/debug/    data contoh (debug saja)
app/src/release/  pengganti no-op untuk data contoh
```

Lapisan UI hanya bergantung pada interface di `domain/repository`. Untuk sinkronisasi multi-pengguna (Supabase/Firebase) di fase lanjutan, cukup tambah implementasi repository baru; setiap tabel sudah memakai UUID, `updatedAt`, `deletedAt`, dan `syncState`.

## Keamanan
- Kata sandi & PIN di-hash PBKDF2-HMAC-SHA256 dengan salt acak; tidak ada plaintext.
- Kunci aplikasi: biometrik / kunci layar HP / PIN aplikasi, otomatis terkunci setelah 1 menit di latar belakang.
- Peran diperiksa di UI **dan** di lapisan repository.
- Backup otomatis Android dimatikan; gunakan fitur Backup terenkripsi di aplikasi (tahap g).
- Data tidak pernah dihapus permanen: master data dinonaktifkan, aset berstatus Dihapus/Dijual.
