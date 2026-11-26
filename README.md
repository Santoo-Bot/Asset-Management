# Aset GA — Sistem Manajemen Aset & Inventaris

Aplikasi Android **offline-first** untuk tim General Affair: register aset dengan penyusutan otomatis, scan QR, label QR, mutasi, servis, stok barang habis pakai, dashboard, pengingat harian, import/export Excel, backup terenkripsi, audit log, dan stock opname.

Kotlin · Jetpack Compose (Material 3) · MVVM + Clean Architecture ringan · Hilt · Room · Paging 3 · Navigation Compose · CameraX + ML Kit · ZXing · Coil · WorkManager · BiometricPrompt. minSdk 26, targetSdk 36.

---

## 1. Memasang APK (tanpa Android Studio)

Setiap push ke `main` dibuild otomatis oleh GitHub Actions. Seluruh unit test dijalankan, lalu **APK debug diterbitkan ke tab Releases**.

**Dari HP (GitHub Mobile atau browser):**
1. Buka repo → **Releases** → rilis paling atas (`Aset GA build N`).
2. Unduh `aset-ga-debug-N.apk`, lalu buka file itu.
3. Izinkan "Instal dari sumber ini" bila diminta. Jika Play Protect memperingatkan, pilih **Tetap instal**.

Versi baru dipasang di atas versi lama tanpa menghapus data. Semua APK debug ditandatangani kunci tetap `app/signing/debug.keystore` (khusus debug, bukan rahasia) dan versionCode naik setiap build, sehingga Android menerimanya sebagai update.

### Pertama kali dibuka
1. Buat akun **Admin GA** pertama.
2. (Opsional) **Lainnya → Pengaturan**: isi *Nama perusahaan pada label*, atur PIN, jam pengingat.
3. **Lainnya → Master data**: tambah lokasi, departemen, vendor. Kategori awal sudah terisi:

   | Kategori | Kode | Umur ekonomis |
   |---|---|---|
   | Elektronik & IT | IT | 4 th |
   | Furnitur | FUR | 8 th |
   | Kendaraan | KND | 8 th |
   | Peralatan Kantor | PK | 4 th |
   | Mesin & Peralatan | MSN | 8 th |
   | Bangunan & Instalasi | BGN | 20 th |
   | Lainnya | LN | 4 th |

4. **Lainnya → Pengguna & peran**: tambah Staf GA / Viewer.

Build **debug** otomatis berisi data contoh (6 lokasi, 7 departemen, 4 vendor, **5.000 aset**) untuk mencoba dan menguji performa. Build **release** tidak pernah berisi data contoh.

## 2. Build & menjalankan di emulator/HP

Prasyarat: Android Studio terbaru (berisi Android SDK) dan JDK 17+.

1. `File → Open` folder repo, tunggu Gradle sync.
2. Pilih emulator (API 26+) atau HP dengan *USB debugging* aktif, lalu tekan **Run**.

Lewat terminal:

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew installDebug
```

Kamera di emulator: aktifkan *Virtual scene* / webcam di pengaturan AVD untuk mencoba scan QR. Uji paling akurat di HP fisik.

## 3. Membuat APK / AAB release

Keystore **tidak pernah** di-commit (`*.jks`, `*.keystore`, `keystore.properties` ada di `.gitignore`). Signing dibaca dari variabel lingkungan.

1. Buat keystore sekali, simpan di luar repo dan buat cadangannya. Kehilangan keystore berarti tidak bisa memperbarui aplikasi:
   ```bash
   keytool -genkeypair -v -keystore ~/keys/aset-ga-release.jks -alias asetga -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Set variabel lingkungan:
   ```bash
   export ASETGA_KEYSTORE_PATH=~/keys/aset-ga-release.jks ASETGA_KEYSTORE_PASSWORD='***' ASETGA_KEY_ALIAS=asetga ASETGA_KEY_PASSWORD='***'
   ```
3. Build:
   ```bash
   ./gradlew assembleRelease bundleRelease
   ```
   Hasil: `app/build/outputs/apk/release/app-release.apk` (pasang langsung) dan `app/build/outputs/bundle/release/app-release.aab` (unggah ke Play Console).

Release memakai R8 (minify + shrink resources). `ASETGA_VERSION_CODE` (opsional) mengatur versionCode.

**Release lewat GitHub Actions:** isi secret repo (*Settings → Secrets and variables → Actions*):

| Secret | Isi |
|---|---|
| `ASETGA_KEYSTORE_BASE64` | hasil `base64 -w0 aset-ga-release.jks` |
| `ASETGA_KEYSTORE_PASSWORD` | password keystore |
| `ASETGA_KEY_ALIAS` | alias key, mis. `asetga` |
| `ASETGA_KEY_PASSWORD` | password key |

Job `release` lalu mengunggah APK & AAB bertanda tangan sebagai artifact. Tanpa secret, job ini dilewati.

> APK release memakai `applicationId` berbeda dari debug (`id.perusahaan.asetga` vs `…asetga.debug`), jadi keduanya bisa terpasang bersamaan, dan datanya terpisah. Pindahkan data antar-keduanya lewat Backup & restore.

## 4. Backup & restore data

Semua data tersimpan **hanya di HP** (database Room + folder foto). Backup otomatis Android sengaja dimatikan. Lakukan backup rutin:

1. **Lainnya → Backup & restore → Buat backup.**
2. Isi passphrase (min. 8 karakter) dua kali.
3. Ketuk **Simpan backup…**, lalu pilih lokasi:
   - **Google Drive**: pilih *Drive* di panel kiri jendela simpan.
   - Penyimpanan HP / kartu SD / folder Download.
4. File `aset-ga-backup-YYYYMMDD-HHMM.gabak` berisi database + semua foto, terenkripsi **AES-256-GCM** (kunci dari passphrase lewat PBKDF2-HMAC-SHA256, 210.000 iterasi).

**Restore** (khusus Admin GA): *Restore dari backup → pilih file → masukkan passphrase*. File didekripsi dan diperiksa dulu. Data lama baru diganti bila backup valid, lalu aplikasi dimulai ulang. Masuk dengan akun yang ada di dalam backup.

> Passphrase tidak bisa dipulihkan. Simpan di pengelola kata sandi perusahaan.

## 5. Fitur

| Modul | Isi |
|---|---|
| **Beranda** | Total aset, nilai perolehan, nilai buku, aset rusak, garansi habis ≤90 hari, servis terlambat & jatuh tempo, barang perlu reorder; grafik per kategori & kondisi; daftar *Perlu tindakan* yang bisa diketuk |
| **Aset** | Field (urutan sama dengan template): kode aset*, kode QR*, nama*, kategori*, no. seri, tgl perolehan*, harga perolehan*, lokasi*, divisi, departemen, kondisi* (Baik/Rusak), status* (Digunakan/Tersedia/Dalam Perbaikan/Propose To Write Off/Write Off), garansi, catatan, pengguna/detail lokasi, nomor PR/PO/GR, pemohon pembelian, nomor & tanggal memo, tindak lanjut, berita acara serah terima, foto. Daftar Paging 3 (cepat untuk 5.000+ aset) dengan pencarian, filter kategori/lokasi/kondisi/status, urutan; kode otomatis `KATEGORI-TAHUN-NOMOR`; foto kamera/galeri dikompres; umur berjalan, penyusutan garis lurus, akumulasi, nilai buku, status garansi; riwayat perubahan |
| **Scan** | Scan QR/barcode (kode aset atau kode QR khusus) membuka detail aset; input kode manual; izin kamera diminta saat dibutuhkan, termasuk saat izin ditolak permanen |
| **Label QR** | Tekan lama di daftar aset untuk memilih banyak aset → PDF A4 (3×8 label per lembar), PNG, cetak lewat layanan cetak Android, atau bagikan |
| **Mutasi** | Pindah lokasi/departemen/PIC dengan alasan & penyetuju; data aset ikut diperbarui |
| **Servis** | Preventif/Korektif/Kalibrasi/Inspeksi, vendor, biaya, foto, jadwal berikutnya; status Terlambat / Jatuh tempo ≤30 hari / Terjadwal |
| **Stok** | Master barang, transaksi masuk/keluar (keluar tidak boleh melebihi stok), stok akhir = awal + masuk − keluar, status Reorder, scan barcode untuk memilih barang |
| **Stock opname** | Pilih lokasi, scan aset berurutan, lalu lihat yang ditemukan / tidak ditemukan / tidak terdaftar di lokasi itu; laporan tersimpan & bisa diexport |
| **Import/Export** | Template Excel + import dengan validasi per baris (baris bermasalah ditampilkan); export aset/servis/stok ke XLSX atau CSV (pemisah `;`) lalu bagikan |
| **Notifikasi** | Cek harian (default 08:00, bisa diubah) untuk garansi, servis, dan stok; izin notifikasi Android 13+ |
| **Keamanan** | Kata sandi & PIN di-hash PBKDF2; kunci biometrik / kunci layar / PIN; kunci otomatis setelah 1 menit di latar belakang |
| **Audit log** | Siapa mengubah apa dan kapan pada aset, stok, master, pengguna, beserta nilai lama → baru |

### Peran

| | Admin GA | Staf GA | Viewer |
|---|:-:|:-:|:-:|
| Lihat semua data, scan, label, export | ✓ | ✓ | ✓ |
| Tambah/ubah aset, mutasi, servis, stok, opname, import, backup | ✓ | ✓ | – |
| Master data, nilai residu, nama perusahaan, restore | ✓ | – | – |
| Kelola pengguna & peran | ✓ | – | – |

Peran dicek di UI (tombol disembunyikan) **dan** di lapisan repository.

### Rumus

- Penyusutan per tahun = harga × (1 − residu%) ÷ umur ekonomis kategori
- Akumulasi = prorata per bulan penuh sejak perolehan, tidak melebihi harga × (1 − residu%); berhenti pada tanggal write off untuk aset Write Off
- Nilai buku = harga − akumulasi
- Residu: diatur per kategori; bila kosong memakai *Nilai residu default* di Pengaturan (awal 0%)
- Garansi: Aktif / Segera habis (≤90 hari) / Habis · Servis: Terlambat / Jatuh tempo (≤30 hari) / Terjadwal
- Stok akhir = stok awal + Σ masuk − Σ keluar; Reorder bila stok ≤ minimum

Data tidak pernah dihapus permanen: master data dan barang **dinonaktifkan**, aset diberi status **Write Off**.

## 6. Struktur kode

```
app/src/main/java/id/perusahaan/asetga/
├─ domain/        model, repository (interface), calc (penyusutan, garansi, servis, stok, kode),
│                 validation, security (hash, enkripsi backup), session, excel (XLSX/CSV/import)
├─ data/
│  ├─ local/      Room: entity, dao, AppDatabase (+ data awal)
│  ├─ repository/ implementasi interface domain + audit log
│  ├─ file/       foto, label QR/PDF, share & print, export/import, backup
│  └─ worker/     pengingat harian (WorkManager)
├─ ui/            theme (biru BCA), navigation, components, feature/<layar>
├─ di/            modul Hilt
└─ util/          format Rupiah & tanggal Indonesia (dd MMM yyyy)
app/src/debug/    data contoh (debug saja) · app/src/release/  pengganti no-op
app/schemas/      skema Room (dihasilkan saat build)
```

Perubahan struktur database selalu lewat migrasi (`Migrations` di `AppDatabase.kt`), sehingga data di HP tidak hilang saat aplikasi diperbarui.

```
```

### Persiapan sinkronisasi multi-pengguna (fase lanjutan)

- UI hanya bergantung pada interface di `domain/repository`. Untuk Supabase/Firebase, tambah sumber data remote + `SyncWorker` di `data/`, lalu ganti binding di `di/AppModule.kt`. Layar tidak perlu diubah.
- Semua tabel memakai primary key **UUID**, kolom `createdAt`, `updatedAt`, `deletedAt` (soft delete), dan `syncState` (`PENDING`/`SYNCED`), siap untuk sinkronisasi dua arah.
- Audit log hanya bertambah (append-only), cocok untuk resolusi konflik.

## 7. Test

```bash
./gradlew testDebugUnitTest
```

- **Logika murni (JVM):** penyusutan & akumulasi, umur berjalan, status garansi/servis, stok & reorder, kode aset otomatis, validasi, hash kata sandi, kunci sesi, format Indonesia, XLSX tulis/baca, parser import per baris, CSV, enkripsi backup, jadwal pengingat.
- **Room (Robolectric):** DAO master & aset (filter, paging, unique code), repository pengguna/master/aset, mutasi, jadwal servis, transaksi stok, stock opname, ringkasan dashboard.

Laporan test tersedia sebagai artifact `test-reports` di setiap run GitHub Actions.
