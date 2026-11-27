"""Daftar aset: cari, filter, urutkan, pilih semua & hapus permanen."""

from __future__ import annotations

from PySide6.QtCore import QTimer
from PySide6.QtGui import QKeySequence, QShortcut
from PySide6.QtWidgets import QDialog, QGridLayout, QHBoxLayout, QLineEdit, QVBoxLayout, QWidget

from ..core import fmt
from ..core.assets import AssetFilter
from ..core.models import ASSET_SORTS, WRITE_OFF_ID, AssetCondition, MasterType
from .asset_detail import AssetDetailDialog
from .asset_form import AssetFormDialog
from .widgets import Combo, Table, ask_text, button, confirm, info, label, primary, run_safely

HEADERS = ["Kode", "Nama", "Kategori", "Lokasi", "Pengguna / detail lokasi", "Divisi", "Departemen", "Kondisi", "Status",
           "Tgl perolehan", "Harga perolehan"]


class AssetsPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = QVBoxLayout(self)
        top = QHBoxLayout()
        top.addWidget(label("Aset", "title"))
        self.count = label("", "subtitle")
        top.addWidget(self.count)
        top.addStretch(1)
        self.add_btn = primary("+ Tambah aset")
        self.add_btn.clicked.connect(self._add)
        top.addWidget(self.add_btn)
        v.addLayout(top)

        grid = QGridLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Cari kode, nama, no. seri, kode QR, pengguna, PR/PO/GR, kelengkapan…")
        self.search.setClearButtonEnabled(True)
        grid.addWidget(self.search, 0, 0, 1, 4)
        self.sort = Combo()
        self.sort.set_items([(v_, k) for k, v_ in ASSET_SORTS.items()])
        grid.addWidget(label("Urutkan", "muted"), 0, 4)
        grid.addWidget(self.sort, 0, 5)
        self.f_category = Combo("Semua kategori")
        self.f_location = Combo("Semua lokasi")
        self.f_division = Combo("Semua divisi")
        self.f_department = Combo("Semua departemen")
        self.f_condition = Combo("Semua kondisi")
        self.f_status = Combo("Semua status")
        for i, w in enumerate((self.f_category, self.f_location, self.f_division, self.f_department, self.f_condition, self.f_status)):
            grid.addWidget(w, 1, i)
        v.addLayout(grid)

        bar = QHBoxLayout()
        self.select_all = button("Pilih semua")
        self.select_all.clicked.connect(lambda: self.table.selectAll())
        self.open_btn = button("Lihat detail")
        self.open_btn.clicked.connect(self._open_current)
        self.delete_btn = button("Hapus permanen…", danger=True)
        self.delete_btn.clicked.connect(self._delete)
        reset = button("Reset filter")
        reset.clicked.connect(self._reset_filters)
        self.label_btn = button("Label QR")
        self.label_btn.clicked.connect(self._labels)
        bar.addWidget(self.open_btn)
        bar.addWidget(self.select_all)
        bar.addWidget(self.label_btn)
        bar.addWidget(self.delete_btn)
        bar.addStretch(1)
        bar.addWidget(reset)
        v.addLayout(bar)

        self.table = Table(HEADERS, multi=True)
        self.table.activated_row.connect(self._open)
        v.addWidget(self.table, 1)
        QShortcut(QKeySequence.StandardKey.Find, self, activated=self.search.setFocus)

        self._debounce = QTimer(self, singleShot=True, interval=250)
        self._debounce.timeout.connect(self.refresh)
        self.search.textChanged.connect(lambda _: self._debounce.start())
        for c in (self.sort, self.f_category, self.f_location, self.f_division, self.f_department, self.f_condition, self.f_status):
            c.currentIndexChanged.connect(lambda _: self.refresh())

    # ------------------------------------------------------------------
    def on_show(self) -> None:
        role = self.ctx.session.role
        self.add_btn.setVisible(bool(role and role.can_edit_data))
        self.delete_btn.setVisible(bool(role and role.can_manage_master))
        m = self.ctx.masters
        for combo, t in ((self.f_category, MasterType.CATEGORY), (self.f_location, MasterType.LOCATION),
                         (self.f_division, MasterType.DIVISION), (self.f_department, MasterType.DEPARTMENT),
                         (self.f_status, MasterType.STATUS)):
            combo.blockSignals(True)
            combo.set_items([(x["name"], x["id"]) for x in m.list(t)])
            combo.blockSignals(False)
        self.f_condition.blockSignals(True)
        self.f_condition.set_items([(c.label, c.name) for c in AssetCondition])
        self.f_condition.blockSignals(False)
        self.refresh()

    def _reset_filters(self) -> None:
        for c in (self.f_category, self.f_location, self.f_division, self.f_department, self.f_condition, self.f_status):
            c.blockSignals(True)
            c.set_value(None)
            c.blockSignals(False)
        self.search.blockSignals(True)
        self.search.clear()
        self.search.blockSignals(False)
        self.refresh()

    def _filter(self) -> AssetFilter:
        return AssetFilter(
            query=self.search.text(), category_id=self.f_category.value(), location_id=self.f_location.value(),
            division_id=self.f_division.value(), department_id=self.f_department.value(), condition=self.f_condition.value(),
            status=self.f_status.value(), sort=self.sort.value() or "CODE",
        )

    def refresh(self) -> None:
        rows = self.ctx.assets.list(self._filter())
        self.table.set_rows(
            [(r["id"], [
                r["code"], r["name"], r["categoryName"], r["locationName"], r["userLocationDetail"] or "", r["divisionName"] or "",
                r["departmentName"] or "", AssetCondition[r["condition"]].label,
                (r["statusName"] or r["status"]) + (" ⊘" if r["status"] == WRITE_OFF_ID else ""),
                fmt.date_numeric(fmt.from_epoch_day(r["acquisitionDate"])), fmt.rupiah(r["acquisitionCost"]),
            ]) for r in rows],
            # Aset Write Off tetap tampil (bertanda abu-abu) dan ikut "Pilih semua".
            muted=lambda aid, wo={r["id"] for r in rows if r["status"] == WRITE_OFF_ID}: aid in wo,
            align_right={10},
        )
        total = self.ctx.db.scalar("SELECT COUNT(*) FROM assets WHERE deletedAt IS NULL") or 0
        self.count.setText(f"{len(rows)} dari {total} aset")
        for i, w in enumerate((120, 220, 130, 130, 170, 110, 120, 70, 120, 95)):
            self.table.setColumnWidth(i, w)

    # ------------------------------------------------------------------
    def _add(self) -> None:
        dlg = AssetFormDialog(self, self.ctx)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.refresh()
            self._open(dlg.saved_id)

    def _open_current(self) -> None:
        aid = self.table.current_data()
        if aid:
            self._open(aid)

    def _open(self, asset_id) -> None:
        if not asset_id:
            return
        dlg = AssetDetailDialog(self, self.ctx, asset_id)
        dlg.exec()
        if dlg.changed:
            self.refresh()

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
            info(self, f"{count} aset dihapus permanen.")
            self.refresh()
