"""Database SQLite yang strukturnya identik dengan database Room di Android.

Struktur tabel diambil dari skema Room hasil build Android (`resources/room_schema.json`), termasuk
`identity_hash` di `room_master_table` dan `user_version`. Dengan begitu file database dari laptop bisa
dibuka aplikasi Android (lewat restore backup) tanpa dianggap rusak, dan sebaliknya.
"""

from __future__ import annotations

import json
import os
import sqlite3
import threading
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path

from . import fmt
from .models import DEFAULT_STATUS_LABELS

DB_NAME = "aset_ga.db"
SCHEMA_FILE = Path(__file__).resolve().parent.parent / "resources" / "room_schema.json"

# Data awal wajib, sama dengan DefaultData di Android.
DEFAULT_CATEGORIES = [
    ("Elektronik & IT", "IT", 4),
    ("Furnitur", "FUR", 8),
    ("Kendaraan", "KND", 8),
    ("Peralatan Kantor", "PK", 4),
    ("Mesin & Peralatan", "MSN", 8),
    ("Bangunan & Instalasi", "BGN", 20),
    ("Lainnya", "LN", 4),
]
DEFAULT_UNITS = ["Pcs", "Unit", "Rim", "Box", "Pak", "Lusin", "Botol", "Liter", "Roll", "Set"]
DEFAULT_SETTINGS = {
    "theme_mode": "SYSTEM",
    "app_lock_enabled": "true",
    "default_residual_percent": "0",
    "reminder_hour": "8",
    "notifications_enabled": "true",
    "company_name": "",
}


class SchemaMismatchError(Exception):
    pass


@dataclass(frozen=True)
class RoomSchema:
    version: int
    identity_hash: str
    statements: list[str]  # CREATE TABLE/INDEX + setup room_master_table, berurutan

    @staticmethod
    def load(path: Path = SCHEMA_FILE) -> "RoomSchema":
        data = json.loads(Path(path).read_text(encoding="utf-8"))["database"]
        stmts: list[str] = []
        for e in data["entities"]:
            table = e["tableName"]
            stmts.append(e["createSql"].replace("${TABLE_NAME}", table))
            for idx in e.get("indices", []):
                stmts.append(idx["createSql"].replace("${TABLE_NAME}", table))
        for v in data.get("views", []):
            stmts.append(f"CREATE VIEW `{v['viewName']}` AS {v['createSql'].replace('${VIEW_NAME}', v['viewName'])}")
        stmts.extend(data["setupQueries"])
        return RoomSchema(int(data["version"]), data["identityHash"], stmts)


def new_id() -> str:
    return str(uuid.uuid4())


def file_user_version(path: str | os.PathLike) -> int:
    """`user_version` (= versi skema Room) dari header file SQLite: 4 byte big-endian di offset 60."""
    try:
        with open(path, "rb") as f:
            head = f.read(100)
        if len(head) < 100 or not head.startswith(b"SQLite format 3\x00"):
            return 0
        return int.from_bytes(head[60:64], "big")
    except OSError:
        return 0


def is_sqlite(path: str | os.PathLike) -> bool:
    try:
        with open(path, "rb") as f:
            return f.read(16) == b"SQLite format 3\x00"
    except OSError:
        return False


def read_identity_hash(conn: sqlite3.Connection) -> str | None:
    try:
        row = conn.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()
        return row[0] if row else None
    except sqlite3.DatabaseError:
        return None


class Database:
    """Satu koneksi untuk seluruh aplikasi. Transaksi bersarang digabung ke transaksi terluar."""

    def __init__(self, path: str | os.PathLike, schema: RoomSchema | None = None):
        self.path = str(path)
        self.schema = schema or RoomSchema.load()
        fresh = not os.path.exists(self.path) or os.path.getsize(self.path) == 0
        self.conn = sqlite3.connect(self.path, isolation_level=None, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self._lock = threading.RLock()
        self._depth = 0
        self.conn.execute("PRAGMA journal_mode=WAL")
        self.conn.execute("PRAGMA foreign_keys=ON")  # Room juga mengaktifkan foreign key saat dibuka
        if fresh or self.conn.execute("PRAGMA user_version").fetchone()[0] == 0:
            self._create()
        else:
            self._verify()

    # ------------------------------------------------------------ pembuatan & verifikasi
    def _create(self) -> None:
        with self.tx():
            for sql in self.schema.statements:
                self.conn.execute(sql)
            self._seed()
            self.conn.execute(f"PRAGMA user_version = {self.schema.version}")

    def _seed(self) -> None:
        now = fmt.now_ms()
        for name, prefix, life in DEFAULT_CATEGORIES:
            self.conn.execute(
                "INSERT INTO categories (id, name, codePrefix, usefulLifeYears, residualPercent, isActive, createdAt, updatedAt, deletedAt, syncState) "
                "VALUES (?, ?, ?, ?, NULL, 1, ?, ?, NULL, 'PENDING')",
                (new_id(), name, prefix, life, now, now),
            )
        for u in DEFAULT_UNITS:
            self.conn.execute(
                "INSERT INTO units (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES (?, ?, 1, ?, ?, NULL, 'PENDING')",
                (new_id(), u, now, now),
            )
        for i, (sid, name) in enumerate(DEFAULT_STATUS_LABELS.items()):
            self.conn.execute(
                "INSERT OR IGNORE INTO asset_statuses (id, name, sortOrder, isActive, createdAt, updatedAt, deletedAt, syncState) "
                "VALUES (?, ?, ?, 1, ?, ?, NULL, 'PENDING')",
                (sid, name, i + 1, now, now),
            )
        for k, v in DEFAULT_SETTINGS.items():
            self.conn.execute("INSERT OR IGNORE INTO app_settings (`key`, value) VALUES (?, ?)", (k, v))

    def _verify(self) -> None:
        version = self.conn.execute("PRAGMA user_version").fetchone()[0]
        if version != self.schema.version:
            raise SchemaMismatchError(
                f"Versi database ({version}) tidak sama dengan versi aplikasi desktop ({self.schema.version})."
            )
        if read_identity_hash(self.conn) != self.schema.identity_hash:
            raise SchemaMismatchError("Struktur database tidak sama dengan aplikasi Android versi ini.")

    def close(self) -> None:
        try:
            self.conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        except sqlite3.DatabaseError:
            pass
        self.conn.close()

    # ------------------------------------------------------------ transaksi & query
    @contextmanager
    def tx(self):
        """Semua perubahan + catatan audit dalam satu transaksi (atomik)."""
        with self._lock:
            outer = self._depth == 0
            if outer:
                self.conn.execute("BEGIN IMMEDIATE")
            self._depth += 1
            try:
                yield self
            except BaseException:
                self._depth -= 1
                if outer:
                    self.conn.execute("ROLLBACK")
                raise
            else:
                self._depth -= 1
                if outer:
                    self.conn.execute("COMMIT")

    def q(self, sql: str, params=()) -> list[sqlite3.Row]:
        with self._lock:
            return self.conn.execute(sql, params).fetchall()

    def one(self, sql: str, params=()) -> sqlite3.Row | None:
        with self._lock:
            return self.conn.execute(sql, params).fetchone()

    def scalar(self, sql: str, params=()):
        row = self.one(sql, params)
        return None if row is None else row[0]

    def exec(self, sql: str, params=()) -> int:
        with self._lock:
            return self.conn.execute(sql, params).rowcount

    # ------------------------------------------------------------ pengaturan (tabel app_settings)
    def setting(self, key: str, default: str | None = None) -> str | None:
        v = self.scalar("SELECT value FROM app_settings WHERE `key` = ?", (key,))
        return default if v is None else v

    def put_setting(self, key: str, value: str) -> None:
        self.exec("INSERT OR REPLACE INTO app_settings (`key`, value) VALUES (?, ?)", (key, value))

    def default_residual_percent(self) -> float:
        return fmt.parse_decimal(self.setting("default_residual_percent", "0") or "0") or 0.0
