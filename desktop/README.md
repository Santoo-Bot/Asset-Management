# Aset GA Desktop (Windows)

Versi laptop dari aplikasi Android **Aset GA**. Berjalan offline, tanpa instalasi (portable). Data tersimpan di folder `data` di samping `AsetGA.exe`.

## Cara memakai

1. Buka GitHub Releases, lalu unduh `AsetGA-desktop-N.zip` dari rilis `desktop-N` yang terbaru.
2. Ekstrak zip ke folder yang bisa ditulis, misalnya `Documents\AsetGA` atau flashdisk. **Jangan** ke `Program Files`.
3. Jalankan `AsetGA.exe`. Saat pertama dibuka, Windows SmartScreen mungkin memberi peringatan karena exe belum bertanda tangan. Pilih **More info → Run anyway**.
4. Di layar awal, pilih salah satu:
   - **Pulihkan dari file backup HP (.gabak)** bila sudah memakai aplikasi di HP. Masuk dengan akun yang sama seperti di HP.
   - **Buat akun Admin GA** untuk mulai dari data kosong.

## Pindah data HP ↔ laptop

Kedua aplikasi memakai format backup yang sama (`.gabak`, terenkripsi AES-256).

- **HP → laptop:** di HP buka Backup & restore, lalu Buat backup. Salin file ke laptop. Di laptop buka menu **Import / Export & Backup**, lalu **Restore dari backup**.
- **Laptop → HP:** di laptop pilih **Buat backup**. Salin file ke HP. Di HP buka Backup & restore, lalu Restore.

Penting:
- Restore **mengganti seluruh data** di perangkat tujuan. Pakai **satu perangkat saja** untuk mengubah data pada satu waktu. Selalu buat backup sebelum pindah perangkat.
- Versi aplikasi HP dan laptop harus memakai struktur database yang sama. Kalau muncul pesan versi berbeda, perbarui aplikasi yang lebih lama.
- Passphrase backup tidak disimpan di mana pun. Kalau lupa, backup tidak bisa dibuka.

## Fitur

**Tahap 1**
- Login dengan peran Admin GA / Staf GA / Viewer. Login dibatasi bila terlalu banyak salah. Aplikasi terkunci otomatis setelah 5 menit tidak dipakai.
- Aset: daftar dengan cari, filter, urutkan, pilih semua, dan hapus permanen. Form dengan foto. Detail berisi nilai buku, penyusutan, dan riwayat perubahan. Ada juga Write Off.
- Master data, pengguna, audit log, import/export Excel aset & CSV, backup & restore terenkripsi.

**Tahap 2**
- **Mutasi** (lokasi dan pengguna / detail lokasi) dan **servis** (jenis, vendor, biaya, foto, jadwal berikutnya), keduanya dari detail aset. Riwayatnya tampil di tab detail aset.
- Menu **Servis**: jadwal servis (terlambat / jatuh tempo / terjadwal) dan riwayat semua servis.
- Menu **Stok**: barang habis pakai, barang masuk/keluar (stok tidak bisa minus), status Reorder, import barang dari Excel, scan barcode di kolom cari.
- **Label QR 50 × 20 mm**: dari detail aset atau beberapa aset terpilih di daftar (tombol Label QR). Bisa disimpan sebagai PDF (1 label per halaman), PNG, atau langsung dicetak ke printer.
- Menu **Scan**: pakai scanner barcode/QR USB (bekerja seperti keyboard) atau ketik kode. Cocok dengan kode aset, kode QR, atau kode/barcode barang.
- Export riwayat servis dan stok & transaksi (Excel/CSV) di menu Import / Export & Backup.

**Tampilan (desktop-8)**
- Gaya modern lembut: kartu putih bersudut di atas latar abu-abu muda, aksen biru BCA Life, tema terang/gelap.
- Menu samping berikon, otomatis hanya ikon di layar sempit (< 1280 px).
- Halaman Aset: kartu ringkasan, status berwarna, dan panel detail di kanan. Pintasan Ctrl+F (cari) dan Ctrl+N (tambah aset).
- Pesan sukses muncul sebagai notifikasi kecil di pojok kanan bawah.

Menyusul di tahap 3: stock opname, asuransi, dashboard, pengingat, dan pengaturan.

## Untuk pengembang

```
cd desktop
pip install -r requirements.txt -r requirements-dev.txt
python run.py                 # jalankan dari kode sumber (data di desktop/data)
python -m pytest -q tests     # test inti + uji asap UI (QT_QPA_PLATFORM=offscreen)
```

- Exe dibuat oleh GitHub Actions (`.github/workflows/desktop.yml`, runner Windows, PyInstaller satu folder). Setiap push ke `main` yang mengubah `desktop/` menerbitkan Release `desktop-N`.
- Struktur database diambil dari skema Room Android (`asetga/resources/room_schema.json`, dilampirkan di Release `build-N` Android). Kalau skema Android berubah, salin skema baru ke sini, sesuaikan kode, lalu jalankan `python tools/make_android_fixture.py`. Skrip ini memperbarui file uji `DesktopCompatTest` di Android.
- Inti (`asetga/core`) tidak bergantung pada Qt dan bisa diuji sendiri. UI ada di `asetga/ui`.
