"""Uji asap antarmuka (tanpa layar, QT_QPA_PLATFORM=offscreen): semua layar & dialog bisa dibuat dan dipakai.
Dilewati bila PySide6 belum terpasang."""

import datetime as dt
import os

import pytest

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
QtWidgets = pytest.importorskip("PySide6.QtWidgets")


@pytest.fixture(scope="module")
def app():
    return QtWidgets.QApplication.instance() or QtWidgets.QApplication([])


@pytest.fixture
def win(app, tmp_path, monkeypatch):
    from PySide6.QtWidgets import QMessageBox

    from asetga.ui import theme
    from asetga.ui.context import AppContext
    from asetga.ui.main_window import MainWindow

    shown = []
    for name in ("information", "warning", "critical"):
        monkeypatch.setattr(QMessageBox, name, lambda *a, _n=name, **k: shown.append((_n, a[2] if len(a) > 2 else "")))
    ctx = AppContext(tmp_path)
    theme.apply(app, "LIGHT")
    w = MainWindow(ctx, app)
    w.shown = shown
    yield w
    w.close()


def test_alur_lengkap_tahap_1(win, app):
    from PySide6.QtWidgets import QDialog

    from asetga.core.models import MasterType, Role
    from asetga.ui import theme
    from asetga.ui.asset_detail import AssetDetailDialog, WriteOffDialog
    from asetga.ui.asset_form import AssetFormDialog
    from asetga.ui.import_dialogs import ImportPreviewDialog
    from asetga.ui.masters_page import MasterDialog
    from asetga.ui.users_page import ResetPasswordDialog, UserDialog

    ctx = win.ctx
    # Layar awal: buat Admin pertama.
    assert win.stack.currentWidget() is win.setup
    win.setup.username.setText("admin")
    win.setup.full_name.setText("Admin GA")
    win.setup.pw.setText("Rahasia123")
    win.setup.pw2.setText("Rahasia123")
    win.setup._create()
    assert win.stack.currentWidget() is win.shell
    assert ctx.session.user.role is Role.ADMIN

    # Semua menu bisa dibuka.
    for row in range(win.shell.nav.count()):
        win.shell.nav.setCurrentRow(row)
    assert win.shell.nav.count() == 10
    assert win.shell.stack.currentWidget() is win.shell.dashboard

    # Master data: setiap dialog bisa dibuat; simpan lokasi & divisi/departemen.
    for t in MasterType:
        MasterDialog(win, ctx, t).close()
    d = MasterDialog(win, ctx, MasterType.LOCATION)
    d.name.setText("Kantor Pusat")
    d._submit()
    assert d.result() == QDialog.DialogCode.Accepted
    div = ctx.masters.save_simple(MasterType.DIVISION, "", "Operasional")
    ctx.masters.save_department("", "General Affair", div)
    bad = MasterDialog(win, ctx, MasterType.CATEGORY)
    bad._submit()  # nama kosong → tetap terbuka dengan pesan galat
    assert bad.result() != QDialog.DialogCode.Accepted and not bad.banner.isHidden()
    win.shell.masters.on_show()

    # Form aset: galat per isian, lalu simpan.
    f = AssetFormDialog(win, ctx)
    f._submit()
    assert "name" in {k for k, lb in f.form._errors.items() if not lb.isHidden()}
    f.name.setText("Laptop Dell")
    f.category.set_value(next(c["id"] for c in ctx.masters.list(MasterType.CATEGORY) if c["codePrefix"] == "IT"))
    f.location.set_value(ctx.masters.list(MasterType.LOCATION)[0]["id"])
    f.department.setCurrentIndex(1)
    assert f.division.value() == div
    f.acq_date.set_value(dt.date(2024, 1, 15))
    f.cost.setText("15.000.000")
    f.accessories.setText("Charger, Tas")
    f.warranty.set_value(dt.date(2027, 1, 15))
    f._submit()
    assert f.result() == QDialog.DialogCode.Accepted, f.banner.text()
    aid = f.saved_id
    assert ctx.assets.get_row(aid)["code"] == "IT-2024-001"

    # Ubah aset lewat form yang sama.
    e = AssetFormDialog(win, ctx, aid)
    assert e.cost.text() == "15.000.000" and e.qr_same.isChecked()
    e.name.setText("Laptop Dell Latitude")
    e._submit()
    assert ctx.assets.get_row(aid)["name"] == "Laptop Dell Latitude"

    det = AssetDetailDialog(win, ctx, aid)
    wo = WriteOffDialog(det, ctx, aid)
    wo.date.set_value(dt.date(2025, 1, 1))
    wo._submit()
    assert ctx.assets.get_row(aid)["status"] == "WRITE_OFF"
    det._render()
    det.close()

    names = [win.shell.nav.item(i).toolTip() for i in range(win.shell.nav.count())]
    win.shell.nav.setCurrentRow(names.index("Aset"))
    t = win.shell.assets.table
    assert t.proxy.rowCount() == 1
    # Cari per kolom: kotak cari di bawah judul kolom "Nama aset".
    name_col = [c.key for c in t.columns].index("name")
    t.header_.editors[name_col].setText("tidak-ada")
    assert t.proxy.rowCount() == 0
    t.clear_filters()
    assert t.proxy.rowCount() == 1
    # Kolom bisa disembunyikan/ditampilkan dan urutannya digeser; disimpan per laptop.
    vendor_col = [c.key for c in t.columns].index("vendor")
    assert t.isColumnHidden(vendor_col)
    t.set_column_visible(vendor_col, True)
    t.header_.moveSection(t.header_.visualIndex(vendor_col), 1)
    assert "vendor" in ctx.pref("assets/table/order").split(",")[:2]
    assert "vendor" not in ctx.pref("assets/table/hidden")
    t.column_menu(win).close()
    # Tidak ada teks terpotong: lebar kolom >= lebar isi.
    from PySide6.QtGui import QFontMetrics
    fm = QFontMetrics(t.font())
    name_text = t.proxy.index(0, name_col).data()
    assert t.columnWidth(name_col) >= fm.horizontalAdvance(name_text)
    # Klik dua kali baris → halaman detail sendiri, tombol kembali ke daftar.
    win.shell.assets.open_detail(aid)
    assert win.shell.assets.stack.currentWidget() is win.shell.assets.detail
    assert win.shell.assets.detail.asset_id == aid
    win.grab()  # gambar seluruh jendela (delegate chip, tabel, ikon)
    win.shell.assets.detail.back.emit()
    assert win.shell.assets.stack.currentWidget() is win.shell.assets.list
    # Dashboard
    win.shell.nav.setCurrentRow(names.index("Dashboard"))
    assert win.shell.dashboard.k_wo.value.text() == "1"
    win.grab()

    # Pengguna.
    u = UserDialog(win, ctx)
    u.username.setText("staf.satu")
    u.full_name.setText("Staf Satu")
    u.pw.setText("Rahasia123")
    u._submit()
    assert u.result() == QDialog.DialogCode.Accepted
    staff = next(x for x in ctx.users.all() if x.username == "staf.satu")
    ResetPasswordDialog(win, ctx, staff).close()
    UserDialog(win, ctx, staff).close()

    # Import: pratinjau dari template.
    import tempfile
    from pathlib import Path

    tpl = Path(tempfile.mkdtemp()) / "t.xlsx"
    ctx.excel.write_template(tpl)
    ImportPreviewDialog(win, ctx.excel.preview(tpl)).close()

    # Audit log & akun.
    win.shell.audit.on_show()
    assert win.shell.audit.table.model_.rowCount() > 0
    win.shell.account.on_show()
    theme.apply(app, "DARK")

    # Kunci & buka kunci.
    win.lock_now()
    assert win.stack.currentWidget() is win.lock
    win.lock.pw.setText("Rahasia123")
    win.lock._unlock()
    assert win.stack.currentWidget() is win.shell

    # Peran Viewer: menu Pengguna tidak tampil, tombol tambah aset tersembunyi.
    ctx.users.create("lihat", "Viewer Satu", Role.VIEWER, "Rahasia123")
    win.logout()
    assert win.stack.currentWidget() is win.login
    win.login.username.setText("lihat")
    win.login.pw.setText("Rahasia123")
    win.login._login()
    assert win.stack.currentWidget() is win.shell
    assert "Pengguna" not in [win.shell.nav.item(i).toolTip() for i in range(win.shell.nav.count())]
    win.shell.nav.setCurrentRow(0)
    assert win.shell.assets.list.add_btn.isHidden()


def test_tahap_2_mutasi_servis_stok_label_scan(win, app, monkeypatch):
    from PySide6.QtWidgets import QDialog

    from asetga.core.assets import AssetInput
    from asetga.core.models import MasterType, Role, StockTxnType
    from asetga.ui.asset_detail import AssetDetailDialog
    from asetga.ui.label_dialog import LabelDialog, labels_for_assets
    from asetga.ui.operation_dialogs import MutationDialog, ServiceDialog
    from asetga.ui.stock_page import ItemDetailDialog, ItemDialog, TxnDialog

    # Dialog modal tidak ditunggu di test.
    opened = []
    monkeypatch.setattr(QDialog, "exec", lambda self: opened.append(type(self).__name__) or 0)
    ctx = win.ctx
    ctx.users.create("admin", "Admin GA", Role.ADMIN, "Rahasia123")
    win.login.username.setText("admin")
    win.login.pw.setText("Rahasia123")
    win.login._login()
    loc = ctx.masters.save_location("", "Kantor Pusat", None)
    gudang = ctx.masters.save_location("", "Gudang", None)
    cat = next(c["id"] for c in ctx.masters.list(MasterType.CATEGORY) if c["codePrefix"] == "IT")
    aid = ctx.assets.save(AssetInput(name="Laptop", category_id=cat, acquisition_date=dt.date(2024, 1, 15), acquisition_cost=10,
                                     location_id=loc), qr_same_as_code=True)

    m = MutationDialog(win, ctx, aid)
    m.location.set_value(gudang)
    m.user_loc.setText("Sari")
    m._submit()
    assert m.result() == QDialog.DialogCode.Accepted, m.banner.text()
    assert ctx.assets.get_row(aid)["locationId"] == gudang

    s = ServiceDialog(win, ctx, aid)
    s._submit()
    assert "description" in {k for k, lb in s.form._errors.items() if not lb.isHidden()}
    s.desc.setPlainText("Bersihkan kipas")
    s.cost.setText("150.000")
    s.next.set_value(dt.date.today() + dt.timedelta(days=10))
    s._submit()
    assert s.result() == QDialog.DialogCode.Accepted, s.banner.text()
    sid = ctx.ops.services_for_asset(aid)[0]["id"]
    ServiceDialog(win, ctx, aid, sid).close()

    det = AssetDetailDialog(win, ctx, aid)
    det._render()
    det.close()

    labels = labels_for_assets(ctx, [aid])
    assert labels[0].qr.startswith("IT-2024-")
    ld = LabelDialog(win, ctx, labels)
    assert not ld.preview.pixmap().isNull()
    ld.close()

    # Stok
    unit = ctx.masters.list(MasterType.UNIT)[0]["id"]
    d = ItemDialog(win, ctx, barcode="899000")
    d.code.setText("atk-9")
    d.name.setText("Map plastik")
    d.unit.set_value(unit)
    d.initial.setText("3")
    d.minimum.setText("5")
    d._submit()
    assert d.result() == QDialog.DialogCode.Accepted, d.banner.text()
    iid = d.saved_id
    t = TxnDialog(win, ctx, iid, StockTxnType.OUT)
    t.qty.setText("9")
    t._submit()
    assert t.result() != QDialog.DialogCode.Accepted and "Stok tersedia" in t.form._errors["qty"].text()
    ItemDetailDialog(win, ctx, iid).close()

    # Halaman baru: scan (aset, barang, tidak ditemukan), stok, servis.
    names = [win.shell.nav.item(i).toolTip() for i in range(win.shell.nav.count())]
    win.shell.nav.setCurrentRow(names.index("Scan"))
    win.shell.scan.input.setText(labels[0].qr.lower())
    win.shell.scan._lookup()
    win.shell.scan.input.setText("899000")
    win.shell.scan._lookup()
    win.shell.scan.input.setText("tidak-ada")
    win.shell.scan._lookup()
    assert opened[-1:] == ["ItemDetailDialog"]
    # Aset hasil scan dibuka di halaman detail modul Aset.
    assert win.shell.assets.stack.currentWidget() is win.shell.assets.detail
    assert not win.shell.scan.banner.isHidden() and win.shell.scan.history.model_.rowCount() == 3
    win.shell.nav.setCurrentRow(names.index("Stok"))
    assert win.shell.stock.table.model_.rowCount() == 1 and "1 perlu reorder" in win.shell.stock.count.text()
    win.shell.nav.setCurrentRow(names.index("Servis"))
    assert win.shell.services.sched.model_.rowCount() == 1
    win.shell.services.tabs.setCurrentIndex(1)
    assert win.shell.services.hist.model_.rowCount() == 1
