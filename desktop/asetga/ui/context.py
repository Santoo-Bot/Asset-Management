"""Wadah layanan aplikasi (database, sesi, service) yang dipakai semua layar."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import QSettings

from ..core.assets import AssetService
from ..core.audit import AuditLog
from ..core.db import DB_NAME, Database
from ..core.excel import ExcelService
from ..core.masters import MasterService
from ..core.photos import PhotoStorage, referenced_photos
from ..core.session import Session
from ..core.users import UserService


class AppContext:
    def __init__(self, data_dir: Path):
        self.data_dir = Path(data_dir)
        self.session = Session()
        self.photos = PhotoStorage(self.data_dir)
        # Preferensi khusus laptop ini (tema, folder terakhir), disimpan di folder data, bukan di database.
        self.prefs = QSettings(str(self.data_dir / "desktop.ini"), QSettings.Format.IniFormat)
        self.db: Database | None = None
        self.open_db()

    @property
    def db_path(self) -> Path:
        return self.data_dir / DB_NAME

    def open_db(self) -> None:
        self.db = Database(self.db_path)
        self.audit = AuditLog(self.db, self.session)
        self.users = UserService(self.db, self.session, self.audit)
        self.masters = MasterService(self.db, self.session, self.audit)
        self.assets = AssetService(self.db, self.session, self.audit)
        self.excel = ExcelService(self.assets, self.masters)

    def cleanup_photos(self) -> int:
        """Foto yatim (tidak dirujuk data) dibersihkan saat aplikasi dibuka, sama dengan Android."""
        try:
            return self.photos.delete_unreferenced(referenced_photos(self.db))
        except OSError:
            return 0

    # ---- preferensi
    def pref(self, key: str, default: str = "") -> str:
        v = self.prefs.value(key, default)
        return str(v) if v is not None else default

    def set_pref(self, key: str, value: str) -> None:
        self.prefs.setValue(key, value)
        self.prefs.sync()

    def last_dir(self) -> str:
        return self.pref("last_dir", str(Path.home() / "Documents"))

    def remember_dir(self, path: str) -> None:
        self.set_pref("last_dir", str(Path(path).parent))
