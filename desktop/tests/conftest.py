import datetime as dt
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from asetga.core.assets import AssetService  # noqa: E402
from asetga.core.audit import AuditLog  # noqa: E402
from asetga.core.db import Database  # noqa: E402
from asetga.core.excel import ExcelService  # noqa: E402
from asetga.core.masters import MasterService  # noqa: E402
from asetga.core.models import MasterType, Role  # noqa: E402
from asetga.core.session import Session  # noqa: E402
from asetga.core.users import UserService  # noqa: E402


class Env:
    def __init__(self, folder: Path):
        self.dir = folder
        self.db = Database(folder / "aset_ga.db")
        self.session = Session()
        self.audit = AuditLog(self.db, self.session)
        self.users = UserService(self.db, self.session, self.audit)
        self.masters = MasterService(self.db, self.session, self.audit)
        self.assets = AssetService(self.db, self.session, self.audit)
        self.excel = ExcelService(self.assets, self.masters)

    def category(self, prefix: str = "IT") -> str:
        return next(c["id"] for c in self.masters.list(MasterType.CATEGORY) if c["codePrefix"] == prefix)


@pytest.fixture
def env(tmp_path, monkeypatch):
    from asetga.core import fmt

    monkeypatch.setattr(fmt, "today", lambda: dt.date(2026, 11, 27))
    e = Env(tmp_path)
    e.users.create("admin", "Admin GA", Role.ADMIN, "Rahasia123")
    yield e
    e.db.close()
