"""Stok barang habis pakai: daftar (cari / scan barcode, filter Reorder), detail + transaksi, import Excel."""

from __future__ import annotations

from dataclasses import replace
from pathlib import Path

from PySide6.QtCore import Qt, QTimer
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QDialog, QDialogButtonBox, QFileDialog, QGridLayout, QGroupBox, QHBoxLayout, QLineEdit, QVBoxLayout,
    QWidget,
)

from ..core import excel, fmt
from ..core import stock as S
from ..core.models import AppError, MasterType, StockTxnType
from ..core.stock import ItemInput, needs_reorder
from .widgets import Combo, DateField, FormDialog, Table, button, confirm, error, info, label, primary, run_safely


class ItemDialog(FormDialog):
    def __init__(self, parent, ctx, item_id: str | None = None, barcode: str = ""):
        super().__init__(parent, "Ubah barang" if item_id else "Tambah barang")
        self.ctx = ctx
        row = ctx.stock.item(item_id) if item_id else None
        self.base = S.StockService.item_input(row) if row else ItemInput(barcode=barcode)
        self.saved_id: str | None = None
        f = self.form
        b = self.base
        self.code = f.add("Kode barang", QLineEdit(b.code), S.FIELD_CODE, "Mis. ATK-001")
        self.barcode = f.add("Barcode", QLineEdit(b.barcode), S.FIELD_BARCODE, "Boleh kosong; bisa diisi dengan scanner barcode")
        self.name = f.add("Nama barang", QLineEdit(b.name), S.FIELD_NAME)
        self.unit = Combo()
        self.unit.set_items([(u["name"], u["id"]) for u in ctx.masters.list(MasterType.UNIT)])
        self.unit.set_value(b.unit_id)
        f.add("Satuan", self.unit, S.FIELD_UNIT)
        self.loc = Combo("— Tidak diisi —")
        self.loc.set_items([(x["name"], x["id"]) for x in ctx.masters.list(MasterType.LOCATION)])
        self.loc.set_value(b.storage_location_id)
        f.add("Lokasi simpan", self.loc)
        self.initial = f.add("Stok awal", QLineEdit(b.initial_stock), S.FIELD_INITIAL)
        self.minimum = f.add("Minimum stok", QLineEdit(b.min_stock), S.FIELD_MIN, "Status Reorder bila stok ≤ minimum")
        self.notes = f.add("Catatan", QLineEdit(b.notes))

    def save(self) -> None:
        self.saved_id = self.ctx.stock.save_item(replace(
            self.base, code=self.code.text(), barcode=self.barcode.text(), name=self.name.text(), unit_id=self.unit.value(),
            storage_location_id=self.loc.value(), initial_stock=self.initial.text(), min_stock=self.minimum.text(), notes=self.notes.text(),
        ))


class TxnDialog(FormDialog):
    def __init__(self, parent, ctx, item_id: str, type_: StockTxnType):
        super().__init__(parent, f"Barang {type_.label.lower()}")
        self.ctx, self.item_id, self.type = ctx, item_id, type_
        it = ctx.stock.item(item_id)
        self.body.insertWidget(0, label(f"{it['code']} — {it['name']}\nStok saat ini: {fmt.qty(it['currentStock'])} {it['unitName']}", "info"))
        f = self.form
        self.date = f.add("Tanggal", DateField(), S.FIELD_DATE)
        self.qty = f.add(f"Jumlah ({it['unitName']})", QLineEdit(), S.FIELD_QTY)
        self.ref = f.add("Referensi", QLineEdit(), hint="Mis. nomor PO / permintaan")
        self.note = f.add("Catatan", QLineEdit())
        self.qty.setFocus()

    def save(self) -> None:
        self.ctx.stock.record_txn(self.item_id, self.type, self.date.value(), self.qty.text(), self.ref.text(), self.note.text())


class ItemDetailDialog(QDialog):
    def __init__(self, parent, ctx, item_id: str):
        super().__init__(parent)
        self.ctx, self.item_id = ctx, item_id
        self.changed = False
        self.setMinimumSize(760, 520)
        v = QVBoxLayout(self)
        self.title = label("", "title")
        v.addWidget(self.title)
        self.summary = QGroupBox("Stok")
        self.grid = QGridLayout(self.summary)
        v.addWidget(self.summary)
        role = ctx.session.role
        row = QHBoxLayout()
        if role and role.can_edit_data:
            bin_ = primary("Barang masuk")
            bin_.clicked.connect(lambda: self._txn(StockTxnType.IN))
            bout = primary("Barang keluar")
            bout.clicked.connect(lambda: self._txn(StockTxnType.OUT))
            edit = button("Ubah barang")
            edit.clicked.connect(self._edit)
            for b in (bin_, bout, edit):
                row.addWidget(b)
        row.addStretch(1)
        if role and role.can_manage_master:
            delete = button("Hapus barang", danger=True)
            delete.clicked.connect(self._delete)
            row.addWidget(delete)
        v.addLayout(row)
        v.addWidget(label("Riwayat transaksi", None))
        self.table = Table(["Tanggal", "Jenis", "Jumlah", "Referensi", "Catatan", "Dicatat oleh"])
        v.addWidget(self.table, 1)
        bb = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        bb.rejected.connect(self.reject)
        v.addWidget(bb)
        self._render()

    def _render(self) -> None:
        it = self.ctx.stock.item(self.item_id)
        if it is None:
            self.reject()
            return
        self.setWindowTitle(f"{it['code']} — {it['name']}")
        self.title.setText(f"{it['code']} — {it['name']}")
        while self.grid.count():
            w = self.grid.takeAt(0).widget()
            if w:
                w.deleteLater()
        status = "Reorder" if needs_reorder(it["currentStock"], it["minStock"]) else "Aman"
        pairs = [
            ("Stok akhir", f"{fmt.qty(it['currentStock'])} {it['unitName']}"), ("Status", status),
            ("Minimum", f"{fmt.qty(it['minStock'])} {it['unitName']}"), ("Stok awal", f"{fmt.qty(it['initialStock'])} {it['unitName']}"),
            ("Barcode", it["barcode"] or "-"), ("Lokasi simpan", it["storageLocationName"] or "-"), ("Catatan", it["notes"] or "-"),
        ]
        for i, (k, val) in enumerate(pairs):
            self.grid.addWidget(label(k, "muted"), i // 2, (i % 2) * 2)
            vl = label(val)
            if k == "Status" and status == "Reorder":
                vl.setStyleSheet("color: #B3261E; font-weight: 600;")
            self.grid.addWidget(vl, i // 2, (i % 2) * 2 + 1)
        self.table.set_rows([
            (t["id"], [fmt.date_numeric(fmt.from_epoch_day(t["txnDate"])), StockTxnType[t["type"]].label,
                       ("+" if t["type"] == "IN" else "−") + f"{fmt.qty(t['qty'])} {it['unitName']}", t["reference"] or "",
                       t["note"] or "", t["createdBy"] or "-"])
            for t in self.ctx.stock.txns(self.item_id)
        ], align_right={2})
        for i, w in enumerate((90, 70, 110, 140, 180)):
            self.table.setColumnWidth(i, w)

    def _txn(self, t: StockTxnType) -> None:
        if TxnDialog(self, self.ctx, self.item_id, t).exec() == QDialog.DialogCode.Accepted:
            self.changed = True
            self._render()

    def _edit(self) -> None:
        if ItemDialog(self, self.ctx, self.item_id).exec() == QDialog.DialogCode.Accepted:
            self.changed = True
            self._render()

    def _delete(self) -> None:
        it = self.ctx.stock.item(self.item_id)
        if not confirm(self, f"Hapus barang {it['code']}?\n\nBarang beserta seluruh riwayat transaksi masuk/keluarnya dihapus selamanya "
                             "dan tidak bisa dipulihkan.", "Hapus barang", yes="Hapus permanen", danger=True):
            return
        if run_safely(self, lambda: self.ctx.stock.delete_item(self.item_id) or True):
            self.changed = True
            self.accept()


class StockImportDialog(QDialog):
    def __init__(self, parent, preview: excel.StockPreview):
        super().__init__(parent)
        self.setWindowTitle("Pratinjau import barang")
        self.setMinimumSize(720, 460)
        v = QVBoxLayout(self)
        v.addWidget(label(f"Siap disimpan: {len(preview.valid)}   ·   Bermasalah: {len(preview.invalid)}", "info"))
        v.addWidget(label("Import hanya menambah barang baru; kode atau barcode yang sudah ada ditolak.", "muted"))
        t = Table(["Baris", "Kode", "Nama", "Keterangan"])
        t.setWordWrap(True)
        t.set_rows([(r, [str(r.row_number), r.input.code if r.input else "", r.input.name if r.input else "",
                         "Siap disimpan" if r.is_valid else "; ".join(r.errors)])
                    for r in sorted(preview.rows, key=lambda x: (x.is_valid, x.row_number))])
        for i, w in enumerate((60, 120, 200)):
            t.setColumnWidth(i, w)
        t.resizeRowsToContents()
        v.addWidget(t, 1)
        bb = QDialogButtonBox()
        ok = primary(f"Simpan {len(preview.valid)} barang")
        ok.setEnabled(bool(preview.valid))
        bb.addButton(ok, QDialogButtonBox.ButtonRole.AcceptRole)
        bb.addButton("Batal", QDialogButtonBox.ButtonRole.RejectRole)
        bb.accepted.connect(self.accept)
        bb.rejected.connect(self.reject)
        v.addWidget(bb)


class StockPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = QVBoxLayout(self)
        top = QHBoxLayout()
        top.addWidget(label("Stok", "title"))
        self.count = label("", "subtitle")
        top.addWidget(self.count)
        top.addStretch(1)
        self.add_btn = primary("+ Tambah barang")
        self.add_btn.clicked.connect(lambda: self._add())
        top.addWidget(self.add_btn)
        v.addLayout(top)
        bar = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Cari kode, nama, atau scan barcode lalu Enter")
        self.search.setClearButtonEnabled(True)
        self.search.returnPressed.connect(self._enter)
        self.reorder = QCheckBox("Perlu reorder")
        self.reorder.toggled.connect(lambda _: self.refresh())
        self.template_btn = button("Template import barang")
        self.template_btn.clicked.connect(self._template)
        self.import_btn = button("Import barang…")
        self.import_btn.clicked.connect(self._import)
        bar.addWidget(self.search, 1)
        bar.addWidget(self.reorder)
        bar.addWidget(self.template_btn)
        bar.addWidget(self.import_btn)
        v.addLayout(bar)
        self.table = Table(["Kode", "Nama barang", "Stok akhir", "Minimum", "Satuan", "Lokasi simpan", "Barcode", "Status"])
        self.table.activated_row.connect(self._open)
        v.addWidget(self.table, 1)
        self._t = QTimer(self, singleShot=True, interval=250)
        self._t.timeout.connect(self.refresh)
        self.search.textChanged.connect(lambda _: self._t.start())

    def on_show(self) -> None:
        role = self.ctx.session.role
        can = bool(role and role.can_edit_data)
        for b in (self.add_btn, self.import_btn):
            b.setVisible(can)
        self.refresh()
        self.search.setFocus()

    def refresh(self) -> None:
        rows = self.ctx.stock.items(self.search.text(), self.reorder.isChecked())
        self.table.set_rows([
            (r["id"], [r["code"], r["name"], fmt.qty(r["currentStock"]), fmt.qty(r["minStock"]), r["unitName"],
                       r["storageLocationName"] or "", r["barcode"] or "",
                       "Reorder" if needs_reorder(r["currentStock"], r["minStock"]) else "Aman"])
            for r in rows
        ], align_right={2, 3})
        for i, w in enumerate((110, 240, 90, 90, 80, 150, 130)):
            self.table.setColumnWidth(i, w)
        self.count.setText(f"{len(rows)} barang · {self.ctx.stock.reorder_count()} perlu reorder")

    def _enter(self) -> None:
        """Scanner barcode USB mengetik kode lalu Enter: buka barangnya, atau tawarkan menambah barang baru."""
        code = self.search.text().strip()
        if not code:
            return
        it = self.ctx.stock.find_by_code_or_barcode(code)
        if it:
            self.search.clear()
            self._open(it["id"])
            return
        role = self.ctx.session.role
        if role and role.can_edit_data and confirm(self, f"Barang dengan kode/barcode \"{code}\" belum ada. Tambah barang baru?",
                                                   "Barang tidak ditemukan", yes="Tambah barang"):
            self.search.clear()
            self._add(code)

    def _add(self, barcode: str = "") -> None:
        dlg = ItemDialog(self, self.ctx, barcode=barcode)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.refresh()
            self._open(dlg.saved_id)

    def _open(self, item_id) -> None:
        if not item_id:
            return
        dlg = ItemDetailDialog(self, self.ctx, item_id)
        dlg.exec()
        if dlg.changed:
            self.refresh()

    def _template(self) -> None:
        path, _ = QFileDialog.getSaveFileName(self, "Simpan template", str(Path(self.ctx.last_dir()) / "template-import-barang.xlsx"),
                                              "Excel (*.xlsx)")
        if not path:
            return
        self.ctx.remember_dir(path)
        try:
            excel.write_stock_template(self.ctx.masters, path)
        except OSError as e:
            error(self, f"Gagal menyimpan file: {e}")
            return
        info(self, f"Template tersimpan:\n{path}")

    def _import(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "Pilih file Excel", self.ctx.last_dir(), "Excel (*.xlsx)")
        if not path:
            return
        self.ctx.remember_dir(path)
        QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        try:
            p = excel.preview_stock(self.ctx.stock, self.ctx.masters, path)
        finally:
            QApplication.restoreOverrideCursor()
        if p.header_error:
            error(self, p.header_error)
            return
        if not p.rows:
            info(self, "Tidak ada baris data di file.")
            return
        if StockImportDialog(self, p).exec() != QDialog.DialogCode.Accepted:
            return
        try:
            res = excel.commit_stock(self.ctx.stock, p, self.ctx.audit)
        except AppError as e:
            error(self, e.message)
            return
        msg = f"Import selesai: {res['imported']} barang ditambahkan, {len(res['failed'])} gagal."
        if res["failed"]:
            msg += "\n\n" + "\n".join(f"Baris {r.row_number}: {'; '.join(r.errors)}" for r in res["failed"][:15])
        info(self, msg)
        self.refresh()
