"""Pengguna, login, dan pembatasan percobaan login (sama dengan Android)."""

from __future__ import annotations

from . import fmt
from .audit import AuditLog, ChangeSet
from .db import Database, new_id
from .models import MSG_NO_ACCESS, AppError, AuditAction, AuditEntityType, Role
from .security import DUMMY_HASH, hash_password, validate_password, validate_username, verify_password
from .session import Session, User

FIELD_USERNAME = "username"
FIELD_FULL_NAME = "fullName"
FIELD_PASSWORD = "password"


class LoginThrottle:
    """Setelah 5 kali salah berturut-turut harus menunggu; jeda naik 2× tiap gagal (maks. 15 menit).
    Disimpan di app_settings `throttle.<kunci>` = `<jumlah gagal>;<terkunci sampai ms>` (sama dengan Android)."""

    PREFIX = "throttle."
    FREE_ATTEMPTS = 5
    BASE_LOCK_MS = 30_000
    MAX_LOCK_MS = 15 * 60_000
    UNKNOWN_KEY = "unknown"

    def __init__(self, db: Database):
        self.db = db

    @classmethod
    def lock_millis(cls, failures: int) -> int:
        if failures < cls.FREE_ATTEMPTS:
            return 0
        shift = min(failures - cls.FREE_ATTEMPTS, 10)
        return min(cls.BASE_LOCK_MS << shift, cls.MAX_LOCK_MS)

    @staticmethod
    def message(seconds: int) -> str:
        wait = f"{(seconds + 59) // 60} menit" if seconds >= 120 else f"{seconds} detik"
        return f"Terlalu banyak percobaan. Coba lagi dalam {wait}."

    def _read(self, key: str) -> tuple[int, int]:
        raw = self.db.setting(self.PREFIX + key)
        if not raw:
            return 0, 0
        parts = raw.split(";")
        try:
            return int(parts[0]), int(parts[1]) if len(parts) > 1 else 0
        except ValueError:
            return 0, 0

    def remaining_seconds(self, key: str) -> int:
        left = self._read(key)[1] - fmt.now_ms()
        return 0 if left <= 0 else (left + 999) // 1000

    def record_failure(self, key: str) -> None:
        failures = self._read(key)[0] + 1
        lock = self.lock_millis(failures)
        self.db.put_setting(self.PREFIX + key, f"{failures};{fmt.now_ms() + lock if lock > 0 else 0}")

    def reset(self, key: str) -> None:
        if self._read(key)[0] != 0:
            self.db.put_setting(self.PREFIX + key, "0;0")


class UserService:
    def __init__(self, db: Database, session: Session, audit: AuditLog):
        self.db = db
        self.session = session
        self.audit = audit
        self.throttle = LoginThrottle(db)

    def count(self) -> int:
        return self.db.scalar("SELECT COUNT(*) FROM users") or 0

    def all(self) -> list[User]:
        return [User.from_row(r) for r in self.db.q("SELECT * FROM users ORDER BY fullName COLLATE NOCASE")]

    def get(self, user_id: str) -> User | None:
        r = self.db.one("SELECT * FROM users WHERE id = ?", (user_id,))
        return User.from_row(r) if r else None

    def authenticate(self, username: str, password: str) -> User:
        row = self.db.one("SELECT * FROM users WHERE username = ?", (username.strip().lower(),))
        key = row["id"] if row else LoginThrottle.UNKNOWN_KEY
        wait = self.throttle.remaining_seconds(key)
        if wait > 0:
            raise AppError(LoginThrottle.message(wait))
        # Tetap hitung hash walau user tidak ada agar waktu respons tidak membocorkan keberadaan username.
        ok = verify_password(password, row["passwordHash"] if row else DUMMY_HASH)
        if row is None or not ok:
            self.throttle.record_failure(key)
            raise AppError("Username atau kata sandi salah")
        self.throttle.reset(key)
        if not row["isActive"]:
            raise AppError("Akun ini dinonaktifkan. Hubungi Admin GA.")
        return User.from_row(row)

    def verify_current_password(self, password: str) -> bool:
        """Konfirmasi kata sandi untuk tindakan berbahaya (ikut pembatasan percobaan)."""
        u = self.session.user
        if u is None:
            return False
        if self.throttle.remaining_seconds(u.id) > 0:
            return False
        stored = self.db.scalar("SELECT passwordHash FROM users WHERE id = ?", (u.id,))
        ok = verify_password(password, stored)
        if ok:
            self.throttle.reset(u.id)
        else:
            self.throttle.record_failure(u.id)
        return ok

    def create(self, username: str, full_name: str, role: Role, password: str) -> User:
        first = self.count() == 0
        if first and role is not Role.ADMIN:
            raise AppError("Pengguna pertama harus Admin GA")
        if not first:
            self.session.require(lambda r: r.can_manage_users)
        uname = username.strip().lower()
        errors: dict[str, str] = {}
        if e := validate_username(uname):
            errors[FIELD_USERNAME] = e
        if not full_name.strip():
            errors[FIELD_FULL_NAME] = "Nama lengkap wajib diisi"
        if e := validate_password(password):
            errors[FIELD_PASSWORD] = e
        if FIELD_USERNAME not in errors and self.db.scalar("SELECT 1 FROM users WHERE username = ?", (uname,)):
            errors[FIELD_USERNAME] = "Username sudah dipakai"
        if errors:
            raise AppError("Periksa kembali isian", errors)
        now = fmt.now_ms()
        uid = new_id()
        with self.db.tx():
            self.db.exec(
                "INSERT INTO users (id, username, fullName, role, passwordHash, pinHash, isActive, createdAt, updatedAt, deletedAt, syncState) "
                "VALUES (?, ?, ?, ?, ?, NULL, 1, ?, ?, NULL, 'PENDING')",
                (uid, uname, full_name.strip(), role.name, hash_password(password), now, now),
            )
            if first:
                # Pengguna pertama belum masuk; catat atas namanya sendiri.
                self.session.login(User(uid, uname, full_name.strip(), role))
            self.audit.log(AuditEntityType.USER, uid, AuditAction.CREATE, f"Pengguna {full_name.strip()} ({role.label}) ditambahkan")
        return self.get(uid)

    def _active_admins(self) -> int:
        return self.db.scalar("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND isActive = 1") or 0

    def update(self, user_id: str, full_name: str, role: Role, is_active: bool) -> User:
        self.session.require(lambda r: r.can_manage_users)
        old = self.get(user_id)
        if old is None:
            raise AppError("Pengguna tidak ditemukan")
        if not full_name.strip():
            raise AppError("Periksa kembali isian", {FIELD_FULL_NAME: "Nama lengkap wajib diisi"})
        loses_admin = old.role is Role.ADMIN and old.is_active and (role is not Role.ADMIN or not is_active)
        if loses_admin and self._active_admins() <= 1:
            raise AppError("Harus ada minimal satu Admin GA yang aktif")
        if user_id == self.session.user.id and not is_active:
            raise AppError("Anda tidak dapat menonaktifkan akun sendiri")
        with self.db.tx():
            self.db.exec(
                "UPDATE users SET fullName = ?, role = ?, isActive = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                (full_name.strip(), role.name, 1 if is_active else 0, fmt.now_ms(), user_id),
            )
            ch = ChangeSet().field("Nama", old.full_name, full_name.strip()).field("Peran", old.role.label, role.label) \
                .field("Aktif", old.is_active, is_active)
            if ch:
                self.audit.log(AuditEntityType.USER, user_id, AuditAction.UPDATE, f"Data pengguna {full_name.strip()} diubah", ch)
        updated = self.get(user_id)
        if self.session.user and self.session.user.id == user_id:
            self.session.user = updated
        return updated

    def change_password(self, user_id: str, new_password: str) -> None:
        if self.session.user is None:
            raise AppError(MSG_NO_ACCESS)
        if user_id != self.session.user.id:
            self.session.require(lambda r: r.can_manage_users)
        if e := validate_password(new_password):
            raise AppError(e, {FIELD_PASSWORD: e})
        old = self.get(user_id)
        if old is None:
            raise AppError("Pengguna tidak ditemukan")
        with self.db.tx():
            self.db.exec(
                "UPDATE users SET passwordHash = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                (hash_password(new_password), fmt.now_ms(), user_id),
            )
            self.audit.log(AuditEntityType.USER, user_id, AuditAction.UPDATE, f"Kata sandi {old.full_name} diubah")

    def delete(self, user_id: str) -> None:
        self.session.require(lambda r: r.can_manage_users)
        if user_id == self.session.user.id:
            raise AppError("Anda tidak dapat menghapus akun sendiri")
        u = self.get(user_id)
        if u is None:
            raise AppError("Pengguna tidak ditemukan")
        if u.role is Role.ADMIN and u.is_active and self._active_admins() <= 1:
            raise AppError("Harus ada minimal satu Admin GA")
        with self.db.tx():
            self.db.exec("DELETE FROM users WHERE id = ?", (user_id,))
            self.audit.log(AuditEntityType.USER, user_id, AuditAction.DELETE, f"Pengguna {u.full_name} (@{u.username}) dihapus permanen")
