"""Aset: tabel lengkap (cari per kolom, urut, kolom bisa digeser & dipilih) dan detail aset di halaman sendiri.
Tambah aset: manual, import dari Excel, atau unduh template. Export ke Excel (.xlsx) terpisah."""

from __future__ import annotations

import datetime as _dt
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtGui import QKeySequence, QShortcut
from PySide6.QtWidgets import QApplication, QDialog, QFileDialog, QHBoxLayout, QMenu, QProgressDialog, QStackedWidget, QVBoxLayout, \
    QWidget

from ..core import calc, fmt
from ..core.models import WRITE_OFF_ID, AppError, AssetCondition, parse_accessories
from .asset_detail import AssetDetailPage
from .asset_form import AssetFormDialog
from .data_table import Column, DataTable
from .widgets import ask_text, button, card, confirm, error, info, label, notify, page_header, page_layout, primary, run_safely


def _d(v) -> str:
    return fmt.date_numeric(fmt.from_epoch_day(v)) if v is not None else ""


def _o(v) -> str:
    return v or ""


def asset_columns(ctx) -> list[Column]:
    """Semua field aset. Kolom bawaan yang tampil ditandai visible=True; sisanya bisa diceklis di menu Kolom."""
    today = fmt.today()
    default_res = ctx.db.default_residual_percent()

    def book(r) -> int:
        acq = fmt.from_epoch_day(r["acquisitionDate"])
        as_of = (fmt.from_epoch_day(r["disposalDate"]) or today) if r["status"] == WRITE_OFF_ID else today
        res = r["categoryResidualPercent"] if r["categoryResidualPercent"] is not None else default_res
        return calc.depreciation(r["acquisitionCost"], acq, r["usefulLifeYears"], res, as_of).book_value

    ed = lambda key: (lambda r: r[key] if r[key] is not None else -10**9)  # noqa: E731
    return [
        Column("code", "Kode aset", lambda r: r["code"]),
        Column("qr", "Kode QR", lambda r: r["qrCode"] or r["code"], visible=False),
        Column("name", "Nama aset", lambda r: r["name"]),
        Column("category", "Kategori", lambda r: r["categoryName"]),
        Column("serial", "No. seri", lambda r: _o(r["serialNumber"]), visible=False),
        Column("acq", "Tgl perolehan", lambda r: _d(r["acquisitionDate"]), ed("acquisitionDate")),
        Column("cost", "Harga perolehan", lambda r: fmt.rupiah(r["acquisitionCost"]), lambda r: r["acquisitionCost"], right=True),
        Column("book", "Nilai buku", lambda r: fmt.rupiah(book(r)), book, visible=False, right=True),
        Column("location", "Lokasi", lambda r: r["locationName"]),
        Column("user", "Pengguna / detail lokasi", lambda r: _o(r["userLocationDetail"])),
        Column("division", "Divisi", lambda r: _o(r["divisionName"]), visible=False),
        Column("department", "Departemen", lambda r: _o(r["departmentName"]), visible=False),
        Column("condition", "Kondisi", lambda r: AssetCondition[r["condition"]].label, chip=True),
        Column("status", "Status", lambda r: r["statusName"] or r["status"], chip=True),
        Column("warranty", "Garansi s/d", lambda r: _d(r["warrantyUntil"]), ed("warrantyUntil"), visible=False),
        Column("vendor", "Vendor", lambda r: _o(r["vendorName"]), visible=False),
        Column("accessories", "Kelengkapan", lambda r: ", ".join(parse_accessories(r["accessories"])), visible=False),
        Column("pr", "Nomor PR", lambda r: _o(r["prNumber"]), visible=False),
        Column("po", "Nomor PO", lambda r: _o(r["poNumber"]), visible=False),
        Column("gr", "Nomor GR", lambda r: _o(r["grNumber"]), visible=False),
        Column("requester", "Pemohon pembelian", lambda r: _o(r["requester"]), visible=False),
        Column("memo", "Nomor memo", lambda r: _o(r["memoNumber"]), visible=False),
        Column("memo_date", "Tanggal memo", lambda r: _d(r["memoDate"]), ed("memoDate"), visible=False),
        Column("follow_up", "Tindak lanjut", lambda r: _o(r["followUp"]), visible=False),
        Column("handover", "Berita acara serah terima", lambda r: _o(r["handoverDoc"]), visible=False),
        Column("notes", "Catatan", lambda r: _o(r["notes"]).replace("\n", " "), visible=False),
    ]


class AssetListView(QWidget):
    def __init__(self, page: "AssetsPage"):
        super().__init__()
        self.page = page
        self.ctx = page.ctx
        v = page_layout(self)
        self.subtitle = label("", "subtitle", wrap=False)
        self.export_btn = button("Export", icon_name="export")
        self.export_btn.setToolTip("Export aset yang tampil (hasil pencarian) ke Excel (.xlsx)")
        self.export_btn.clicked.connect(self._export)
        self.add_btn = primary("Tambah aset", "tambah")
        add_menu = QMenu(self.add_btn)
        add_menu.addAction("Tambah manual…", self._add)
        add_menu.addAction("Import dari Excel…", self._import)
        add_menu.addSeparator()
        add_menu.addAction("Unduh template import", self._template)
        self.add_btn.setMenu(add_menu)
        v.addLayout(page_header("Aset", self.subtitle, self.export_btn, self.add_btn))

        box = card("v")
        bar = QHBoxLayout()
        bar.setContentsMargins(16, 12, 16, 10)
        bar.setSpacing(8)
        self.select_all = button("Pilih semua")
        self.select_all.clicked.connect(lambda: self.table.selectAll())
        self.label_btn = button("Label QR", icon_name="qr")
        self.label_btn.setToolTip("Buat label QR untuk aset terpilih")
        self.label_btn.clicked.connect(self._labels)
        self.delete_btn = button("Hapus…", danger=True, icon_name="hapus")
        self.delete_btn.setToolTip("Hapus permanen aset terpilih (khusus Admin GA)")
        self.delete_btn.clicked.connect(self._delete)
        self.sel_info = label("", "small", wrap=False)
        clear = button("Hapus pencarian")
        clear.setObjectName("ghost")
        clear.clicked.connect(lambda: self.table.clear_filters())
        self.cols_btn = button("Kolom", icon_name="kolom")
        self.cols_btn.setToolTip("Pilih kolom yang ditampilkan. Urutan kolom diatur dengan menggeser judul kolom.")
        self.cols_btn.clicked.connect(self._columns)
        for b in (self.select_all, self.label_btn, self.delete_btn):
            bar.addWidget(b)
        bar.addWidget(self.sel_info)
        bar.addStretch(1)
        bar.addWidget(clear)
        bar.addWidget(self.cols_btn)
        box.layout().addLayout(bar)
        self.table = DataTable(asset_columns(self.ctx), self.ctx, "assets/table", "Tidak ada aset yang cocok dengan pencarian")
        self.table.activated_row.connect(self.page.open_detail)
        self.table.filtered.connect(self._update_info)
        self.table.selectionModel().selectionChanged.connect(lambda *_: self._update_info())
        box.layout().addWidget(self.table.filter_row)
        box.layout().addWidget(self.table, 1)
        hint = label("Tip: ketik di kotak Cari… di atas judul kolom untuk menyaring · klik judul untuk mengurutkan · geser judul untuk "
                     "mengatur urutan · klik dua kali baris untuk membuka detail", "small")
        hint.setContentsMargins(16, 6, 16, 10)
        box.layout().addWidget(hint)
        v.addWidget(box, 1)
        QShortcut(QKeySequence.StandardKey.New, self, activated=self._shortcut_add)
        QShortcut(QKeySequence("Return"), self.table, activated=self._open_current)

    def on_show(self) -> None:
        role = self.ctx.session.role
        self.add_btn.setVisible(bool(role and role.can_edit_data))
        self.delete_btn.setVisible(bool(role and role.can_manage_master))
        self.refresh()

    def refresh(self) -> None:
        rows = self.ctx.assets.list_all()
        self.table.write_off_name = self.ctx.masters.status_names().get(WRITE_OFF_ID)
        wo = {r["id"] for r in rows if r["status"] == WRITE_OFF_ID}
        # Aset Write Off tetap tampil (abu-abu) dan ikut "Pilih semua".
        self.table.set_rows([(r["id"], r) for r in rows], muted=lambda aid: aid in wo)
        active = len(rows) - len(wo)
        locs = len({r["locationId"] for r in rows if r["status"] != WRITE_OFF_ID})
        self.subtitle.setText(f"{fmt.number(active)} aset aktif · {len(wo)} Write Off · {locs} lokasi")

    def _update_info(self) -> None:
        n = len(self.table.selected_data())
        shown = self.table.proxy.rowCount()
        total = self.table.source.rowCount()
        base = f"{shown} dari {total} aset" if shown != total else f"{total} aset"
        self.sel_info.setText(f"{n} dipilih · {base}" if n > 1 else base)

    def _columns(self) -> None:
        menu = self.table.column_menu(self)
        menu.exec(self.cols_btn.mapToGlobal(self.cols_btn.rect().bottomLeft()))

    def _open_current(self) -> None:
        aid = self.table.current_data()
        if aid:
            self.page.open_detail(aid)

    # ------------------------------------------------------------ tambah / import / export
    def _shortcut_add(self) -> None:
        if self.add_btn.isVisible():
            self._add()

    def _add(self) -> None:
        dlg = AssetFormDialog(self, self.ctx)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.refresh()
            notify(self, f"Aset {self.ctx.assets.get_row(dlg.saved_id)['code']} ditambahkan")
            self.page.open_detail(dlg.saved_id)

    def _save_path(self, title: str, name: str) -> str | None:
        path, _ = QFileDialog.getSaveFileName(self, title, str(Path(self.ctx.last_dir()) / name), "Excel (*.xlsx)")
        if path:
            self.ctx.remember_dir(path)
            if not path.lower().endswith(".xlsx"):
                path += ".xlsx"
        return path or None

    def _template(self) -> None:
        path = self._save_path("Simpan template import aset", "template-import-aset.xlsx")
        if not path:
            return
        try:
            self.ctx.excel.write_template(path)
        except OSError as e:
            error(self, f"Gagal menyimpan file: {e}. Tutup file di Excel bila sedang terbuka.")
            return
        notify(self, "Template import aset tersimpan")

    def _export(self) -> None:
        ids = self.table.visible_data()
        if not ids:
            info(self, "Tidak ada aset untuk diexport. Hapus pencarian bila daftar kosong.")
            return
        stamp = _dt.date.today().strftime("%Y%m%d")
        path = self._save_path("Export aset ke Excel", f"aset-{stamp}.xlsx")
        if not path:
            return
        try:
            n = self.ctx.excel.export_xlsx(path, self.ctx.audit, ids)
        except OSError as e:
            error(self, f"Gagal menyimpan file: {e}. Tutup file di Excel bila sedang terbuka.")
            return
        notify(self, f"{n} aset diexport ke Excel")

    def _import(self) -> None:
        from .import_dialogs import ImportPreviewDialog

        path, _ = QFileDialog.getOpenFileName(self, "Pilih file Excel aset", self.ctx.last_dir(), "Excel (*.xlsx)")
        if not path:
            return
        self.ctx.remember_dir(path)
        QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        try:
            preview = self.ctx.excel.preview(path)
        finally:
            QApplication.restoreOverrideCursor()
        if preview.header_error:
            error(self, preview.header_error)
            return
        if not preview.rows:
            info(self, "Tidak ada baris data di file.")
            return
        if ImportPreviewDialog(self, preview).exec() != QDialog.DialogCode.Accepted:
            return
        prog = QProgressDialog("Menyimpan data…", None, 0, len(preview.to_save), self)
        prog.setWindowModality(Qt.WindowModality.WindowModal)
        prog.setMinimumDuration(300)

        def step(i, n):
            prog.setValue(i)
            QApplication.processEvents()

        try:
            result = self.ctx.excel.commit(preview, self.ctx.audit, step)
        except AppError as e:
            prog.close()
            error(self, e.message)
            return
        prog.close()
        self.refresh()
        failed = result["failed"]
        msg = (f"Import selesai: {result['created']} baru, {result['updated']} diperbarui, "
               f"{result['unchanged']} tanpa perubahan, {len(failed)} gagal.")
        if failed:
            msg += "\n\nBaris gagal:\n" + "\n".join(f"Baris {r.row_number}: {'; '.join(r.errors)}" for r in failed[:15])
            if len(failed) > 15:
                msg += f"\n… dan {len(failed) - 15} baris lainnya"
            info(self, msg)
        else:
            notify(self, msg)

    # ------------------------------------------------------------ label & hapus
    def _labels(self) -> None:
        from .label_dialog import LabelDialog, labels_for_assets

        ids = self.table.selected_data()
        if not ids:
            info(self, "Pilih aset yang akan dibuatkan label (klik baris, Ctrl/Shift + klik, atau Pilih semua).")
            return
        LabelDialog(self, self.ctx, labels_for_assets(self.ctx, ids)).exec()

    def _delete(self) -> None:
        ids = self.table.selected_data()
        if not ids:
            info(self, "Pilih aset yang akan dihapus terlebih dulu (klik baris, Ctrl/Shift + klik, atau Pilih semua).")
            return
        if not confirm(self, f"Hapus permanen {len(ids)} aset beserta riwayat mutasi, servis, opname, dan asuransinya?\n\n"
                             "Tindakan ini tidak bisa dibatalkan.", "Hapus aset", yes="Lanjutkan", danger=True):
            return
        reason = ask_text(self, "Alasan penghapusan", "Tuliskan alasan penghapusan (dicatat di audit log):", "Hapus permanen",
                          "Alasan penghapusan wajib diisi")
        if reason is None:
            return
        result = run_safely(self, lambda: self.ctx.assets.delete(ids, reason))
        if result:
            count, photos = result
            for p in photos:
                self.ctx.photos.delete(p)
            self.refresh()
            notify(self, f"{count} aset dihapus permanen")


class AssetsPage(QWidget):
    """Wadah modul Aset: daftar ⇄ halaman detail."""

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        lay = QVBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        self.stack = QStackedWidget()
        lay.addWidget(self.stack)
        self.list = AssetListView(self)
        self.detail = AssetDetailPage(ctx)
        self.detail.back.connect(self.show_list)
        self.detail.changed.connect(lambda: self.list.refresh())
        self.stack.addWidget(self.list)
        self.stack.addWidget(self.detail)

    # Akses singkat yang dipakai halaman lain & test.
    @property
    def table(self) -> DataTable:
        return self.list.table

    def on_show(self) -> None:
        self.stack.setCurrentWidget(self.list)
        self.list.on_show()

    def refresh(self) -> None:
        self.list.refresh()

    def open_detail(self, asset_id) -> None:
        if asset_id and self.ctx.assets.get_row(asset_id):
            self.detail.show_asset(asset_id)
            self.stack.setCurrentWidget(self.detail)

    def show_list(self) -> None:
        self.stack.setCurrentWidget(self.list)
        if not self.ctx.assets.get_row(self.detail.asset_id or ""):
            self.list.refresh()
