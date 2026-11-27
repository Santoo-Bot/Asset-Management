"""Hash kata sandi dan enkripsi backup — formatnya identik dengan aplikasi Android.

- Kata sandi: `pbkdf2-sha256$<iterasi>$<salt base64>$<hash base64>` (PBKDF2-HMAC-SHA256, 256 bit).
- Backup: MAGIC "ASETGABK"(8) | versi(1) | iterasi(4, big-endian) | salt(16) | iv(12) | ciphertext | tag(16).
  AES-256-GCM, kunci dari passphrase via PBKDF2-HMAC-SHA256. GCM sekaligus memeriksa integritas.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import os
import re
import struct
from typing import BinaryIO

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.exceptions import InvalidTag

# ---------------------------------------------------------------- kata sandi
HASH_PREFIX = "pbkdf2-sha256"
HASH_ITERATIONS = 120_000
# Hash acak tetap (bukan kata sandi siapa pun) untuk menyamakan waktu verifikasi username tak dikenal.
DUMMY_HASH = "pbkdf2-sha256$120000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="


def hash_password(secret: str, iterations: int = HASH_ITERATIONS) -> str:
    salt = os.urandom(16)
    digest = hashlib.pbkdf2_hmac("sha256", secret.encode("utf-8"), salt, iterations, 32)
    return "$".join([HASH_PREFIX, str(iterations), base64.b64encode(salt).decode(), base64.b64encode(digest).decode()])


def verify_password(secret: str, stored: str | None) -> bool:
    parts = (stored or "").split("$")
    if len(parts) != 4 or parts[0] != HASH_PREFIX:
        return False
    try:
        iterations = int(parts[1])
        salt = base64.b64decode(parts[2], validate=True)
        expected = base64.b64decode(parts[3], validate=True)
    except (ValueError, TypeError):
        return False
    if iterations <= 0:
        return False
    actual = hashlib.pbkdf2_hmac("sha256", secret.encode("utf-8"), salt, iterations, len(expected) or 32)
    return hmac.compare_digest(expected, actual)


# ---------------------------------------------------------------- aturan kredensial
_USERNAME = re.compile(r"^[a-z0-9._]{3,32}$")


def validate_username(username: str) -> str | None:
    if not username.strip():
        return "Username wajib diisi"
    if not _USERNAME.match(username):
        return "3–32 karakter: huruf kecil, angka, titik, atau garis bawah"
    return None


def validate_password(password: str) -> str | None:
    if len(password) < 8:
        return "Kata sandi minimal 8 karakter"
    if not any(c.isdigit() for c in password) or not any(c.isalpha() for c in password):
        return "Kata sandi harus berisi huruf dan angka"
    return None


# ---------------------------------------------------------------- enkripsi backup
MAGIC = b"ASETGABK"
VERSION = 1
SALT_BYTES = 16
IV_BYTES = 12
TAG_BYTES = 16
DEFAULT_ITERATIONS = 210_000
MIN_PASSPHRASE = 8
_HEADER = len(MAGIC) + 1 + 4 + SALT_BYTES + IV_BYTES
_CHUNK = 1024 * 1024


class WrongPassphraseError(Exception):
    def __init__(self):
        super().__init__("Passphrase salah atau file backup rusak")


class NotABackupError(Exception):
    def __init__(self):
        super().__init__("File ini bukan backup Aset GA")


def _key(passphrase: str, salt: bytes, iterations: int) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", passphrase.encode("utf-8"), salt, iterations, 32)


def encrypt_file(src: BinaryIO, dst: BinaryIO, passphrase: str, iterations: int = DEFAULT_ITERATIONS) -> None:
    """Enkripsi seluruh isi [src] ke [dst] (streaming, aman untuk file besar)."""
    salt = os.urandom(SALT_BYTES)
    iv = os.urandom(IV_BYTES)
    dst.write(MAGIC)
    dst.write(bytes([VERSION]))
    dst.write(struct.pack(">i", iterations))
    dst.write(salt)
    dst.write(iv)
    enc = Cipher(algorithms.AES(_key(passphrase, salt, iterations)), modes.GCM(iv)).encryptor()
    while chunk := src.read(_CHUNK):
        dst.write(enc.update(chunk))
    dst.write(enc.finalize())
    dst.write(enc.tag)


def decrypt_file(src_path: str, dst: BinaryIO, passphrase: str) -> None:
    """
    Dekripsi file backup ke [dst]. Melempar WrongPassphraseError bila verifikasi gagal.
    Catatan: isi [dst] baru boleh dipakai setelah fungsi ini selesai tanpa galat.
    """
    size = os.path.getsize(src_path)
    with open(src_path, "rb") as f:
        magic = f.read(len(MAGIC))
        if magic != MAGIC or size < _HEADER + TAG_BYTES:
            raise NotABackupError()
        version = f.read(1)[0]
        if version != VERSION:
            raise OSError(f"Versi backup tidak didukung ({version})")
        (iterations,) = struct.unpack(">i", f.read(4))
        if not 10_000 <= iterations <= 5_000_000:
            raise NotABackupError()
        salt = f.read(SALT_BYTES)
        iv = f.read(IV_BYTES)
        f.seek(size - TAG_BYTES)
        tag = f.read(TAG_BYTES)
        f.seek(_HEADER)
        remaining = size - _HEADER - TAG_BYTES
        dec = Cipher(algorithms.AES(_key(passphrase, salt, iterations)), modes.GCM(iv, tag)).decryptor()
        while remaining > 0:
            chunk = f.read(min(_CHUNK, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            dst.write(dec.update(chunk))
        try:
            dst.write(dec.finalize())
        except InvalidTag:
            raise WrongPassphraseError() from None
