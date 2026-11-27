# CLAUDE.md — Aset GA

Aplikasi Android **offline-first** "Aset GA" (Sistem Manajemen Aset & Inventaris) untuk tim General Affair.
Pengguna proyek berkomunikasi dalam **Bahasa Indonesia**; semua teks UI, pesan error, komentar kode, dan pesan commit memakai Bahasa Indonesia.

## Stack
- Kotlin 2.1, Jetpack Compose Material 3, MVVM + Clean Architecture ringan, Hilt, Room 2.7 (DB versi **13**), Navigation Compose (route `@Serializable`), Paging 3, WorkManager, CameraX + ML Kit, ZXing, Coil.
- minSdk 26, target/compileSdk 36, JDK 17. Package `id.perusahaan.asetga` (debug: suffix `.debug`).
- XLSX ditulis/dibaca sendiri (`domain/excel`, tanpa Apache POI).

## Struktur
```
app/src/main/java/id/perusahaan/asetga/
  data/local      Room: entity, dao, AppDatabase (+ object Migrations, DefaultData)
  data/repository implementasi repository (cek peran + audit log di sini)
  data/file       foto, label PDF/PNG, export/import, backup, opname & asuransi file
  domain/         model, repository (interface), validation, calc, excel, security, session
  ui/             theme (BCA Life, biru #0060AF), components, navigation, feature/<layar>
app/src/debug     data contoh (debug saja) · app/src/release  pengganti no-op
```
UI hanya bergantung pada interface `domain/repository`.

## Build & verifikasi
- Build lokal di lingkungan cloud biasanya **tidak bisa** (Google Maven diblokir). Verifikasi lewat GitHub Actions: setiap push ke `main` menjalankan `testDebugUnitTest` + `assembleDebug` dan menerbitkan Release `build-N` berisi `aset-ga-debug-N.apk`.
- Pantau: `gh run list --repo Santoo-Bot/Asset-Management --limit 1`, lalu `gh run view <id> --log-failed`. GraphQL `gh` bisa diblokir; pakai `gh api repos/...` (REST).
- Test: unit (domain) + Room via Robolectric (`app/src/test`). Tambah/ubah test setiap mengubah perilaku.

## Aturan wajib
1. **Jangan menambah menu/tombol yang tidak diminta.** Letakkan aksi baru di menu/dialog yang sudah ada bila memungkinkan.
2. **Tidak ada soft delete.** Hapus = permanen + audit log. Master data hanya bisa dihapus bila tidak dipakai (pesan menyebut pemakainya). Hapus aset, barang, pengguna, master: khusus Admin GA.
3. **Setiap perubahan skema Room = migrasi baru** di `Migrations` (jangan destructive). Migrasi harus **aman dijalankan ulang** (`addColumnIfMissing`, `CREATE ... IF NOT EXISTS`, `INSERT OR IGNORE`). Perbarui `MigrationTest`/`MigrationRerunTest`.
4. **Audit log atomik**: tulis perubahan data dan catatan audit dalam satu transaksi. Riwayat perubahan hanya mencatat field yang berubah (lama → baru).
5. Peran diperiksa di UI **dan** repository (`SessionManager.denyUnless`). Admin GA / Staf GA / Viewer.
6. Tindakan berbahaya (reset data, restore, hapus pengguna, PIN, matikan kunci) minta konfirmasi kata sandi (`ReauthViewModel`).
7. Kolom baru template import aset **ditambahkan di akhir**; urutan kolom yang ada tidak diubah.
8. Galat di dalam bottom sheet/dialog ditampilkan dengan `ErrorBanner` (snackbar tertutup sheet).
9. Format Indonesia: Rupiah `Rp 1.250.000`, tanggal `dd/MM/yyyy` / "5 Jan 2025" (`util/Fmt`).
10. **Keystore rilis tidak pernah di-commit.** `app/signing/debug.keystore` sengaja ada di repo (pengecualian di `.gitignore`) agar APK debug bisa di-update tanpa uninstall.

## Konsep domain penting
- **Status aset = master data** (`asset_statuses`). 5 bawaan memakai id tetap: `DIGUNAKAN`, `TERSEDIA`, `DALAM_PERBAIKAN`, `PROPOSE_WRITE_OFF`, `WRITE_OFF`. `AssetStatus` adalah `data class(id)`; nama dari `AssetStatusNames` (diisi dari master saat app start).
  **Write Off** = status sistem: tidak bisa dihapus (nama boleh diubah); penyusutan berhenti; tidak ikut dashboard, opname, asuransi.
- Kondisi aset: `BAIK`, `RUSAK` (enum).
- Divisi → Departemen (departemen wajib punya divisi; aset mengikuti divisi departemennya).
- Kode aset `PREFIX-TAHUN-NNN`; Kode QR wajib (boleh sama dengan kode aset); scan cocok dengan kode aset atau kode QR.
- Kelengkapan aset: teks dipisah koma (`Accessories.normalize`). Vendor aset dari master Vendor.
- Stock opname: buat sesi → tambah aset (pilih / upload Excel) → cek fisik (scan / tandai, kondisi fisik + catatan) → selesai & laporan Excel. Sesi selesai tidak bisa diubah.
- Asuransi: tipe & perusahaan asuransi (master), polis, aset per polis (manual / scan / upload).
- Label QR: ukuran cetak **50 × 20 mm**, 1 label per halaman PDF; isi: QR, kode QR (1 baris, huruf menyesuaikan), kategori.
- Foto disimpan di `filesDir/photos` (path relatif di DB); foto yatim dibersihkan saat app dibuka.
- Backup terenkripsi (AES-256-GCM, PBKDF2) khusus Admin; passphrase tidak disimpan di mana pun.

## Commit
Pesan commit dalam Bahasa Indonesia, ringkas, menjelaskan apa & kenapa. Jangan commit/push ke branch lain kecuali diminta.
