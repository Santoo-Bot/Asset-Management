"""Tangkapan layar semua halaman dengan data contoh (dipakai CI untuk memeriksa tampilan).

    python tools/screenshots.py <folder-keluaran>
"""

from __future__ import annotations

import datetime as dt
import os
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


def seed(ctx) -> None:
    from asetga.core.assets import AssetInput
    from asetga.core.models import AssetCondition, MasterType, Role, ServiceType, StockTxnType
    from asetga.core.operations import ServiceInput
    from asetga.core.stock import ItemInput

    ctx.users.create("andi", "Andi Saputra", Role.ADMIN, "Rahasia123")
    ctx.users.create("sari", "Sari Wulandari", Role.STAFF, "Rahasia123")
    ctx.users.create("budi", "Budi Hartono", Role.VIEWER, "Rahasia123")
    m = ctx.masters
    locs = {n: m.save_location("", n, None) for n in ("Kantor Pusat", "Cabang Bandung", "Gudang", "Pool Kendaraan", "Ruang Rapat 1")}
    ops = m.save_simple(MasterType.DIVISION, "", "Operasional")
    fin = m.save_simple(MasterType.DIVISION, "", "Keuangan")
    ga = m.save_department("", "General Affair", ops)
    acc = m.save_department("", "Akuntansi", fin)
    ven = m.save_contact(MasterType.VENDOR, "", "PT Sinar Komputer", "021-5550123", "sales@sinarkomputer.co.id", None)
    cat = {c["codePrefix"]: c["id"] for c in m.list(MasterType.CATEGORY)}
    today = dt.date.today()
    data = [
        ("Laptop Dell Latitude 5440", "IT", "Kantor Pusat", AssetCondition.BAIK, "DIGUNAKAN", 15_000_000, dt.date(2024, 1, 15), "Budi · Ruang 301", ga),
        ("Monitor LG 27\"", "IT", "Kantor Pusat", AssetCondition.BAIK, "TERSEDIA", 3_200_000, dt.date(2024, 2, 2), "", ga),
        ("Printer Epson L3250", "IT", "Cabang Bandung", AssetCondition.RUSAK, "DALAM_PERBAIKAN", 2_750_000, dt.date(2024, 3, 10), "Sari · Lt 2", acc),
        ("Meja kerja L-shape", "FUR", "Kantor Pusat", AssetCondition.BAIK, "DIGUNAKAN", 4_200_000, dt.date(2023, 8, 21), "Ruang Direksi", ga),
        ("Toyota Avanza 1.5 G", "KND", "Pool Kendaraan", AssetCondition.BAIK, "DIGUNAKAN", 260_000_000, dt.date(2022, 5, 5), "Driver: Joko", ga),
        ("Mesin penghancur kertas", "PK", "Gudang", AssetCondition.RUSAK, "DIGUNAKAN", 1_900_000, dt.date(2021, 11, 12), "", acc),
        ("Proyektor Epson EB-X51", "IT", "Ruang Rapat 1", AssetCondition.BAIK, "DIGUNAKAN", 6_400_000, dt.date(2024, 4, 18), "Ruang Rapat 1", ga),
        ("Kursi ergonomis", "FUR", "Kantor Pusat", AssetCondition.BAIK, "TERSEDIA", 2_100_000, dt.date(2024, 6, 6), "", ga),
        ("AC Daikin 1 PK", "MSN", "Cabang Bandung", AssetCondition.BAIK, "PROPOSE_WRITE_OFF", 5_600_000, dt.date(2019, 7, 1), "Ruang staf", acc),
        ("Lemari arsip besi", "FUR", "Gudang", AssetCondition.BAIK, "DIGUNAKAN", 3_400_000, dt.date(2022, 9, 9), "", acc),
    ]
    ids = []
    for name, pfx, loc, cond, status, cost, acq, user, dep in data:
        ids.append(ctx.assets.save(AssetInput(
            name=name, category_id=cat[pfx], acquisition_date=acq, acquisition_cost=cost, location_id=locs[loc], condition=cond,
            status=status, user_location_detail=user, department_id=dep, vendor_id=ven if pfx == "IT" else None,
            warranty_until=today + dt.timedelta(days=40) if name.startswith("Monitor") else (acq.replace(year=acq.year + 3)),
            accessories="Charger, Tas laptop" if name.startswith("Laptop") else "",
        ), qr_same_as_code=True))
    ctx.assets.write_off(ids[5], today - dt.timedelta(days=30), 150_000, "Rusak total")
    ctx.ops.record_mutation(ids[0], today - dt.timedelta(days=60), locs["Kantor Pusat"], "Budi · Ruang 302")
    ctx.ops.record_mutation(ids[2], today - dt.timedelta(days=20), locs["Gudang"], "Sari · Lt 3")
    ctx.ops.save_service(ServiceInput(ids[0], service_date=today - dt.timedelta(days=90), type=ServiceType.PREVENTIF, vendor_id=ven,
                                      cost=350_000, description="Pembersihan & ganti pasta termal", next_service_date=today + dt.timedelta(days=12)))
    ctx.ops.save_service(ServiceInput(ids[2], service_date=today - dt.timedelta(days=5), type=ServiceType.KOREKTIF, cost=450_000,
                                      description="Ganti head printer", next_service_date=today - dt.timedelta(days=1)))
    ctx.ops.save_service(ServiceInput(ids[4], service_date=today - dt.timedelta(days=30), type=ServiceType.PREVENTIF, cost=1_250_000,
                                      description="Servis berkala 40.000 km", next_service_date=today + dt.timedelta(days=150)))
    unit = {u["name"]: u["id"] for u in m.list(MasterType.UNIT)}
    for code, name, u, init, mn, out in (("ATK-001", "Kertas A4 80 gram", "Rim", 20, 5, 17), ("ATK-002", "Pulpen hitam", "Pcs", 100, 24, 40),
                                         ("ATK-003", "Map plastik", "Pcs", 50, 10, 12), ("KBR-001", "Tisu wajah", "Box", 30, 10, 3)):
        iid = ctx.stock.save_item(ItemInput(code=code, name=name, unit_id=unit[u], initial_stock=str(init), min_stock=str(mn),
                                            storage_location_id=locs["Gudang"]))
        ctx.stock.record_txn(iid, StockTxnType.OUT, today, str(out), "Permintaan GA")
    ctx.session.logout()


def main(out: str) -> None:
    os.environ["ASETGA_DATA_DIR"] = tempfile.mkdtemp()
    from PySide6.QtWidgets import QApplication

    app = QApplication(sys.argv)
    from asetga.ui import theme
    from asetga.ui.context import AppContext
    from asetga.ui.main_window import MainWindow

    out_dir = Path(out)
    out_dir.mkdir(parents=True, exist_ok=True)
    ctx = AppContext(Path(os.environ["ASETGA_DATA_DIR"]))
    theme.apply(app, "LIGHT")
    win = MainWindow(ctx, app)
    win.resize(1440, 900)
    win.show()

    def shot(name: str, widget=None) -> None:
        for _ in range(5):
            app.processEvents()
        (widget or win).grab().save(str(out_dir / f"{name}.png"))

    shot("00-setup")
    seed(ctx)
    win.show_login()
    shot("01-login")
    win.login.username.setText("andi")
    win.login.pw.setText("Rahasia123")
    win.login._login()
    nav = win.shell.nav
    names = [nav.item(i).toolTip() for i in range(nav.count())]
    for i, n in enumerate(names):
        nav.setCurrentRow(i)
        if n == "Aset":
            win.shell.assets.table.selectRow(0)
        if n == "Stok":
            pass
        shot(f"{i + 2:02d}-{n.split()[0].lower()}")
    nav.setCurrentRow(0)
    win.shell.assets.table.selectRow(2)
    win.toast.show_text("Aset IT-2024-003 tersimpan")
    shot("20-aset-detail-toast")
    from asetga.ui.asset_form import AssetFormDialog

    f = AssetFormDialog(win, ctx, win.shell.assets.table.current_data())
    f.resize(680, 820)
    f.show()
    shot("21-form-aset", f)
    f.close()
    win.shell.set_compact(False)
    shot("23-aset-menu-penuh")
    win.shell.set_compact(True)
    theme.apply(app, "DARK")
    win._theme_changed("DARK")
    nav.setCurrentRow(0)
    win.shell.assets.table.selectRow(0)
    shot("22-aset-gelap")
    win.close()


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "screenshots"
    try:
        main(target)
    except BaseException:
        # Log CI tidak selalu bisa dibaca: simpan galat bersama tangkapan layar.
        import traceback

        Path(target).mkdir(parents=True, exist_ok=True)
        (Path(target) / "GALAT.txt").write_text(traceback.format_exc(), encoding="utf-8")
        raise
