# Konteks proyek — Aset GA

> Unggah file ini (dan `CLAUDE.md` di root repo) sebagai **pengetahuan project** di Claude Project. Perbarui bila ada keputusan baru.

## 1. Ringkasan
- **Aplikasi:** Aset GA — Sistem Manajemen Aset & Inventaris untuk tim General Affair.
- **Platform:** Android (minSdk 26), offline-first. Data tersimpan di database lokal HP; **belum ada server/sinkronisasi** dan belum ada versi web.
- **Repo:** GitHub `Santoo-Bot/Asset-Management`, branch `main`.
- **Distribusi saat ini:** APK debug dari GitHub Releases (`build-N`, `aset-ga-debug-N.apk`), dipasang lewat GitHub Mobile. APK debug bisa di-update tanpa uninstall karena kunci debug tetap.
- **Bahasa:** seluruh aplikasi dan komunikasi dalam Bahasa Indonesia. Tema warna BCA Life (biru #0060AF), terang/gelap.

## 2. Peran pengguna
| Peran | Hak |
|---|---|
| Admin GA | Semua, termasuk master data, pengguna, hapus permanen, backup/restore, reset data |
| Staf GA | Tambah/ubah aset, mutasi, servis, stok, opname, import, polis |
| Viewer | Hanya melihat |

Login kata sandi + PIN/biometrik, kunci otomatis setelah 1 menit di latar belakang, pembatasan percobaan login (jeda bertahap). Tindakan berbahaya (reset data, restore, hapus pengguna, atur PIN, mematikan kunci) meminta kata sandi lagi.

## 3. Fitur
| Modul | Isi |
|---|---|
| Aset | Daftar dengan cari, filter (kategori, lokasi, divisi, departemen, kondisi, status), urutkan, pilih semua & hapus permanen. Form dengan foto di paling atas. Detail dengan foto 1:1, nilai buku & penyusutan garis lurus, riwayat perubahan |
| Field aset | Kode Aset, Kode QR, Nama, Kategori, No. Seri, Tgl & Harga Perolehan, Lokasi, Divisi, Departemen, Kondisi (Baik/Rusak), Status (master), Garansi, Catatan, Pengguna/Detail Lokasi, **Kelengkapan** (dipisah koma), **Vendor** (master), Nomor PR/PO/GR, Pemohon Pembelian, Nomor & Tanggal Memo, Tindak Lanjut, Berita Acara Serah Terima |
| Scan & label | Scan QR/barcode (kode aset atau kode QR). Label 50 × 20 mm: QR, kode QR (1 baris), kategori; PDF 1 label/halaman, PNG, cetak |
| Mutasi | Hanya Lokasi dan Pengguna/Detail Lokasi |
| Servis | Jenis, vendor, biaya, foto, jadwal berikutnya |
| Stok | Barang habis pakai, transaksi masuk/keluar, status Reorder |
| Stock opname | Buat sesi (nama, tanggal) → tambah aset (pilih dari daftar / upload Excel kode aset atau kode QR) → cek fisik (scan atau tandai Ditemukan/Tidak ditemukan + kondisi fisik + catatan) → Selesai & laporan Excel (ringkasan, hasil, tidak ditemukan, rusak, di luar daftar). Hasil opname tidak mengubah data aset |
| Asuransi | Master tipe asuransi & perusahaan asuransi; polis (periode, premi, nilai pertanggungan); aset per polis lewat pilih manual, scan, atau upload Excel; status polis Aktif/Segera berakhir/Berakhir |
| Dashboard & pengingat | Ringkasan aset, nilai buku, garansi, servis jatuh tempo, stok reorder; notifikasi harian |
| Import/Export | Template import aset dengan dropdown; mode tambah baru atau **perbarui aset yang ada** (dicocokkan lewat Kode Aset, sel kosong = nilai lama). Export Excel/CSV |
| Backup | Backup terenkripsi (AES-256) database + foto, khusus Admin; passphrase tidak disimpan di mana pun |
| Master data | Kategori, Lokasi, Divisi, Departemen (di bawah Divisi), Vendor, Satuan, Tipe asuransi, Perusahaan asuransi, **Status aset** |
| Lainnya | Audit log, pengguna & peran, pengaturan, hapus seluruh data, laporan crash |

## 4. Urutan kolom template import aset
Kode Aset*, Kode QR*, Nama Aset*, Kategori*, No. Seri, Tgl Perolehan*, Harga Perolehan*, Lokasi*, Divisi, Departemen, Kondisi*, Status*, Garansi s/d, Catatan, Pengguna / Detail Lokasi, Nomor PR, Nomor PO, Nomor GR, Pemohon Pembelian, Nomor Memo, Tanggal Memo, Tindak Lanjut, Berita Acara Serah Terima, Kelengkapan, Vendor.
Kolom baru selalu ditambahkan di akhir.

## 5. Keputusan yang sudah disepakati
- Tidak ada soft delete di mana pun; aset yang dulu "terhapus" sudah dipulihkan menjadi aktif.
- Master data bisa dihapus (bukan dinonaktifkan) bila tidak dipakai. Tombol "Tampilkan nonaktif" dihapus.
- Aset Write Off tetap tampil di daftar aset (bertanda) dan ikut "Pilih semua".
- Riwayat mutasi/opname tidak menahan penghapusan lokasi/departemen.
- Pengguna tidak ingin menu/tombol tambahan yang tidak diminta.
- Kunci debug disimpan di repo atas permintaan pengguna; keystore rilis tidak boleh di-commit.

## 6. Belum dikerjakan / rencana
1. **Build rilis**: buat keystore rilis, isi secret GitHub (`ASETGA_KEYSTORE_BASE64`, `ASETGA_KEYSTORE_PASSWORD`, `ASETGA_KEY_ALIAS`, `ASETGA_KEY_PASSWORD`), uji APK rilis, pindahkan data, uninstall APK debug. Distribusi internal (MDM kantor atau APK manual, repo private).
2. **Sinkronisasi server / versi web** (opsi: Supabase + web React/Next.js). Perlu keputusan IT: lokasi data (cloud atau server kantor) dan metode login (akun terpisah atau SSO).
3. Opsional: enkripsi database lokal, kelengkapan ikut dicek saat opname, hasil opname memperbarui kondisi aset, laporan opname PDF/berita acara.

## 7. Cara kerja pengembangan
- Pengembangan dilakukan di Claude Code (sesi cloud) dengan repo di atas. Build lokal tidak tersedia; GitHub Actions menjalankan test dan build pada setiap push ke `main`, lalu menerbitkan Release `build-N`.
- Setiap perubahan skema database disertai migrasi yang aman dijalankan ulang dan test migrasi.
