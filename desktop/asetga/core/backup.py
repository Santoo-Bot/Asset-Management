"""Backup & restore terenkripsi (.gabak) — format sama persis dengan aplikasi Android.

Isi backup (sebelum dienkripsi) = ZIP: `manifest.txt`, `db/aset_ga.db`, `photos/<file>`.
Backup dari laptop bisa di-restore di HP, dan sebaliknya, selama versi skemanya sama.
"""

from __future__ import annotations

import datetime as _dt
import os
import shutil
import sqlite3
import tempfile
import zipfile
from pathlib import Path

from . import security
from .db import DB_NAME, Database, RoomSchema, file_user_version, is_sqlite, read_identity_hash
from .models import AppError, AuditAction, AuditEntityType
from .photos import DIR as PHOTO_DIR

DB_ENTRY = f"db/{DB_NAME}"
MANIFEST = "manifest.txt"
EXTENSION = "gabak"


def suggested_file_name() -> str:
    return f"aset-ga-backup-{_dt.datetime.now():%Y%m%d-%H%M}.{EXTENSION}"


def create_backup(db: Database, data_dir: str | os.PathLike, target: str | os.PathLike, passphrase: str, audit=None) -> int:
    """Tulis backup terenkripsi ke [target]. Mengembalikan ukuran file (byte)."""
    if len(passphrase) < security.MIN_PASSPHRASE:
        raise AppError(f"Passphrase minimal {security.MIN_PASSPHRASE} karakter")
    if audit is not None:
        # Catat dulu agar log backup ikut tersimpan di dalam file backup.
        audit.log(AuditEntityType.SYSTEM, "backup", AuditAction.UPDATE, "Backup data dibuat")
    target = Path(target)
    work = Path(tempfile.mkdtemp(prefix="asetga-backup-"))
    try:
        # Salinan database yang konsisten (termasuk isi WAL) lewat API backup SQLite.
        snap = work / DB_NAME
        dst = sqlite3.connect(snap)
        with db._lock:
            db.conn.backup(dst)
        dst.execute("PRAGMA journal_mode=DELETE")
        dst.close()

        zpath = work / "backup.zip"
        with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr(MANIFEST, f"app=aset-ga\nformat=1\ncreated={_dt.datetime.now().isoformat()}\nsource=desktop\n")
            z.write(snap, DB_ENTRY)
            photos = Path(data_dir) / PHOTO_DIR
            if photos.is_dir():
                for f in sorted(photos.iterdir()):
                    if f.is_file():
                        z.write(f, f"{PHOTO_DIR}/{f.name}")

        tmp_target = target.with_name(target.name + ".tmp")
        with open(zpath, "rb") as src, open(tmp_target, "wb") as out:
            security.encrypt_file(src, out, passphrase)
        os.replace(tmp_target, target)
        return target.stat().st_size
    except AppError:
        raise
    except OSError as e:
        raise AppError(f"Backup gagal: {e}") from e
    finally:
        shutil.rmtree(work, ignore_errors=True)


class PreparedRestore:
    """Backup yang sudah didekripsi & diperiksa di folder sementara; belum mengganti data apa pun."""

    def __init__(self, work: Path, db_file: Path, photos: Path):
        self.work = work
        self.db_file = db_file
        self.photos = photos

    def cleanup(self) -> None:
        shutil.rmtree(self.work, ignore_errors=True)

    def user_count(self) -> int:
        c = sqlite3.connect(self.db_file)
        try:
            return c.execute("SELECT COUNT(*) FROM users").fetchone()[0]
        finally:
            c.close()


def prepare_restore(source: str | os.PathLike, passphrase: str, schema: RoomSchema) -> PreparedRestore:
    """Dekripsi & periksa backup. Melempar AppError dengan pesan yang jelas bila tidak bisa dipakai."""
    work = Path(tempfile.mkdtemp(prefix="asetga-restore-"))
    try:
        zpath = work / "backup.zip"
        try:
            with open(zpath, "wb") as out:
                security.decrypt_file(str(source), out, passphrase)
        except (security.WrongPassphraseError, security.NotABackupError) as e:
            raise AppError(str(e)) from e

        extracted = work / "data"
        extracted.mkdir()
        has_db = False
        try:
            with zipfile.ZipFile(zpath) as z:
                for info in z.infolist():
                    name = info.filename
                    # Tolak path traversal (../) dan entri di luar daftar yang dikenal.
                    allowed = name in (DB_ENTRY, MANIFEST) or (
                        name.startswith(f"{PHOTO_DIR}/") and ".." not in name and "\\" not in name and name.count("/") == 1
                    )
                    if not allowed or info.is_dir():
                        continue
                    out = (extracted / name).resolve()
                    if extracted.resolve() not in out.parents:
                        raise AppError("Entri backup tidak valid")
                    out.parent.mkdir(parents=True, exist_ok=True)
                    with z.open(info) as src, open(out, "wb") as dst:
                        shutil.copyfileobj(src, dst)
                    has_db = has_db or name == DB_ENTRY
        except zipfile.BadZipFile as e:
            raise AppError("Backup tidak dapat dibaca (isi rusak)") from e

        new_db = extracted / DB_ENTRY
        if not has_db or not is_sqlite(new_db):
            raise AppError("Backup tidak berisi database yang valid")
        version = file_user_version(new_db)
        if version < 1:
            raise AppError("Backup tidak berisi database Aset GA yang valid")
        if version > schema.version:
            raise AppError(f"Backup dibuat oleh versi aplikasi yang lebih baru (skema {version}, aplikasi desktop ini {schema.version}). "
                           "Perbarui aplikasi desktop dulu.")
        if version < schema.version:
            raise AppError(f"Backup dibuat oleh aplikasi Android versi lama (skema {version}, aplikasi desktop ini {schema.version}). "
                           "Perbarui aplikasi Android, buka sekali, lalu buat backup baru dari HP.")
        c = sqlite3.connect(new_db)
        try:
            same = read_identity_hash(c) == schema.identity_hash
        finally:
            c.close()
        if not same:
            raise AppError("Struktur database di backup berbeda dengan versi aplikasi desktop ini. "
                           "Pastikan aplikasi Android dan desktop memakai build terbaru.")
        return PreparedRestore(work, new_db, extracted / PHOTO_DIR)
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise


def apply_restore(prepared: PreparedRestore, data_dir: str | os.PathLike, current: Database | None) -> None:
    """Ganti database & foto dengan isi backup. Data lama disimpan dulu; bila gagal dikembalikan.
    [current] ditutup; pemanggil wajib membuka Database baru setelahnya."""
    data_dir = Path(data_dir)
    db_file = data_dir / DB_NAME
    siblings = [Path(str(db_file) + s) for s in ("-wal", "-shm")]
    photos = data_dir / PHOTO_DIR
    old = prepared.work / "old"
    old.mkdir(exist_ok=True)
    if current is not None:
        current.close()
    had_db = db_file.exists()
    try:
        if had_db:
            shutil.copy2(db_file, old / DB_NAME)
        for s in siblings:
            if s.exists():
                shutil.copy2(s, old / s.name)
        if photos.exists():
            shutil.move(str(photos), str(old / PHOTO_DIR))
        for s in siblings:
            s.unlink(missing_ok=True)
        shutil.copy2(prepared.db_file, db_file)
        if prepared.photos.exists():
            shutil.move(str(prepared.photos), str(photos))
        else:
            photos.mkdir(parents=True, exist_ok=True)
    except Exception as e:
        try:
            for s in siblings:
                s.unlink(missing_ok=True)
            if had_db:
                shutil.copy2(old / DB_NAME, db_file)
                for s in siblings:
                    if (old / s.name).exists():
                        shutil.copy2(old / s.name, s)
            else:
                db_file.unlink(missing_ok=True)
            shutil.rmtree(photos, ignore_errors=True)
            if (old / PHOTO_DIR).exists():
                shutil.move(str(old / PHOTO_DIR), str(photos))
        except Exception:
            pass
        raise AppError(f"Restore gagal dan data lama dikembalikan. ({e})") from e
    finally:
        prepared.cleanup()
