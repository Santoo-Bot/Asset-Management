"""Membuat file backup contoh dari aplikasi desktop untuk test kecocokan di Android.

Hasil: app/src/test/resources/desktop-compat/desktop-backup.gabak (passphrase "passphrase-uji").
Test Android `DesktopCompatTest` membuka database di dalamnya dengan Room. Jalankan ulang skrip ini bila
skema Room berubah (setelah resources/room_schema.json diperbarui).

    python tools/make_android_fixture.py
"""

from __future__ import annotations

import datetime as dt
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from asetga.core import backup  # noqa: E402
from asetga.core.assets import AssetInput, AssetService  # noqa: E402
from asetga.core.audit import AuditLog  # noqa: E402
from asetga.core.db import Database  # noqa: E402
from asetga.core.masters import MasterService  # noqa: E402
from asetga.core.models import AssetCondition, MasterType, Role  # noqa: E402
from asetga.core.session import Session  # noqa: E402
from asetga.core.users import UserService  # noqa: E402

OUT = ROOT.parent / "app" / "src" / "test" / "resources" / "desktop-compat" / "desktop-backup.gabak"
PASSPHRASE = "passphrase-uji"


def main() -> None:
    work = Path(tempfile.mkdtemp())
    db = Database(work / "aset_ga.db")
    s = Session()
    audit = AuditLog(db, s)
    UserService(db, s, audit).create("admin", "Admin Desktop", Role.ADMIN, "Rahasia123")
    m = MasterService(db, s, audit)
    loc = m.save_location("", "Kantor Pusat", "Jl. Contoh 1")
    div = m.save_simple(MasterType.DIVISION, "", "Operasional")
    dep = m.save_department("", "General Affair", div)
    ven = m.save_contact(MasterType.VENDOR, "", "PT Vendor Uji", "021-123456", "vendor@contoh.id", None)
    cat = next(c["id"] for c in m.list(MasterType.CATEGORY) if c["codePrefix"] == "IT")
    a = AssetService(db, s, audit)
    a.save(AssetInput(code="IT-2024-001", name="Laptop Uji", category_id=cat, acquisition_date=dt.date(2024, 1, 15),
                      acquisition_cost=15_000_000, location_id=loc, department_id=dep, condition=AssetCondition.BAIK,
                      status="DIGUNAKAN", warranty_until=dt.date(2027, 1, 15), qr_code="QR-0001",
                      accessories="Charger, Tas", vendor_id=ven), qr_same_as_code=False)
    wo = a.save(AssetInput(code="IT-2023-005", name="Printer Lama", category_id=cat, acquisition_date=dt.date(2023, 3, 1),
                           acquisition_cost=2_500_000, location_id=loc, condition=AssetCondition.RUSAK, status="DIGUNAKAN"),
                qr_same_as_code=True)
    a.write_off(wo, dt.date(2025, 6, 30), 100_000, "Rusak total")
    photos = work / "photos"
    photos.mkdir(exist_ok=True)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    backup.create_backup(db, work, OUT, PASSPHRASE, audit)
    db.close()
    print(f"Ditulis: {OUT} ({OUT.stat().st_size} byte)")


if __name__ == "__main__":
    main()
