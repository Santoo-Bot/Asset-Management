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

## Fitur (tahap 1)

- Login dengan peran Admin GA / Staf GA / Viewer. Login dibatasi bila terlalu banyak salah. Aplikasi terkunci otomatis setelah 5 menit tidak dipakai.
- Aset: daftar dengan cari, filter, urutkan, pilih semua, dan hapus permanen. Form dengan foto. Detail berisi nilai buku, penyusutan, dan riwayat perubahan. Ada juga Write Off.
- Master data: kategori, lokasi, divisi, departemen, vendor, satuan, tipe & perusahaan asuransi, status aset.
- Import/export Excel dengan template dan kolom yang sama dengan Android, serta export CSV.
- Backup & restore terenkripsi, audit log, dan kelola pengguna.

Menyusul di tahap berikutnya: mutasi, servis, stok, label QR & cetak, scan, stock opname, asuransi, dashboard, dan pengingat.

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
