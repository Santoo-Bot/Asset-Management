# Rilis Aset GA (APK bertanda tangan)

Versi rilis memakai paket `id.perusahaan.asetga` (tanpa `.debug`) dan **kunci rilis milik perusahaan**, jadi terpasang
terpisah dari versi debug. Data dipindahkan lewat Backup & restore (Admin GA).

## Satu kali: buat keystore rilis (di komputer, bukan di chat)
```
keytool -genkeypair -v -keystore aset-ga-release.jks -alias asetga -keyalg RSA -keysize 4096 -validity 10000
```
Simpan berkas `.jks` dan kedua kata sandi di tempat aman **di luar repo** (mis. password manager perusahaan) dan buat
salinan cadangan. **Bila kunci hilang, aplikasi rilis tidak bisa di-update** (harus uninstall dan pasang ulang).

## Satu kali: isi secret GitHub
Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Isi |
|---|---|
| `ASETGA_KEYSTORE_BASE64` | hasil `base64 -w0 aset-ga-release.jks` |
| `ASETGA_KEYSTORE_PASSWORD` | kata sandi keystore |
| `ASETGA_KEY_ALIAS` | `asetga` |
| `ASETGA_KEY_PASSWORD` | kata sandi kunci |

## Hasil
Setiap push ke `main` (setelah test lolos) menerbitkan Release `rilis-N` berisi `aset-ga-N.apk` bertanda tangan,
selain Release debug `build-N`. Keystore rilis tidak pernah di-commit.

## Pindah dari debug ke rilis
1. Di versi debug: Lainnya → Backup & restore → buat backup, simpan di luar HP/aplikasi.
2. Pasang APK rilis (aplikasi terpisah, data kosong), buat Admin sementara, lalu Restore backup.
3. Setelah yakin data lengkap, uninstall versi debug.
