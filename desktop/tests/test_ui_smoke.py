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
    from asetga.ui.data_page import ImportPreviewDialog
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
    assert win.shell.nav.count() == 6

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

    win.shell.nav.setCurrentRow(0)
    assert win.shell.assets.table.model_.rowCount() == 1
    win.shell.assets.search.setText("tidak-ada")
    win.shell.assets.refresh()
    assert win.shell.assets.table.model_.rowCount() == 0

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
    assert "Pengguna" not in [win.shell.nav.item(i).text() for i in range(win.shell.nav.count())]
    win.shell.nav.setCurrentRow(0)
    assert win.shell.assets.add_btn.isHidden()
