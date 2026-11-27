"""Aset: kartu ringkasan, daftar (cari, filter, urutkan, pilih semua, hapus permanen) dan panel detail di kanan."""

from __future__ import annotations

from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QKeySequence, QShortcut
from PySide6.QtWidgets import QDialog, QGridLayout, QHBoxLayout, QSplitter, QVBoxLayout, QWidget

from ..core import fmt
from ..core.assets import AssetFilter, summary
from ..core.models import ASSET_SORTS, WRITE_OFF_ID, AssetCondition, MasterType
from .asset_detail import AssetDetailPanel
from .asset_form import AssetFormDialog
from .widgets import Combo, Kpi, Table, ask_text, button, card, confirm, info, label, notify, page_header, page_layout, primary, \
    run_safely, search_field

HEADERS = ["Kode", "Nama aset", "Kategori", "Lokasi", "Pengguna / detail lokasi", "Kondisi", "Status", "Harga perolehan"]
COND_COL, STATUS_COL, COST_COL = 5, 6, 7


class AssetsPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = page_layout(self)
        self.subtitle = label("", "subtitle", wrap=False)
        self.search = search_field("Cari kode, nama, no. seri, kode QR, pengguna…   (Ctrl+F)")
        self.search.setMinimumWidth(340)
        self.add_btn = primary("Tambah aset", "tambah")
        self.add_btn.setToolTip("Ctrl+N")
        self.add_btn.clicked.connect(self._add)
        v.addLayout(page_header("Aset", self.subtitle, self.search, self.add_btn))

        kpis = QHBoxLayout()
        kpis.setSpacing(14)
        self.k_total = Kpi("Total aset")
        self.k_book = Kpi("Nilai buku")
        self.k_warranty = Kpi("Garansi segera habis")
        self.k_damaged = Kpi("Aset rusak")
        for k in (self.k_total, self.k_book, self.k_warranty, self.k_damaged):
            kpis.addWidget(k, 1)
        v.addLayout(kpis)

        split = QSplitter(Qt.Orientation.Horizontal)
        split.setChildrenCollapsible(False)
        split.setHandleWidth(16)
        lst = card("v")
        top = QVBoxLayout()
        top.setContentsMargins(16, 14, 16, 12)
        top.setSpacing(10)
        grid = QGridLayout()
        grid.setHorizontalSpacing(8)
        self.f_category = Combo("Semua kategori")
        self.f_location = Combo("Semua lokasi")
        self.f_division = Combo("Semua divisi")
        self.f_department = Combo("Semua departemen")
        self.f_condition = Combo("Semua kondisi")
        self.f_status = Combo("Semua status")
        self.sort = Combo()
        self.sort.set_items([(f"Urut: {v_}", k) for k, v_ in ASSET_SORTS.items()])
        for i, w in enumerate((self.f_category, self.f_location, self.f_division, self.f_department, self.f_condition, self.f_status,
                               self.sort)):
            w.setMinimumContentsLength(9)
            grid.addWidget(w, i // 4, i % 4)
        top.addLayout(grid)
        bar = QHBoxLayout()
        bar.setSpacing(8)
        self.select_all = button("Pilih semua")
        self.select_all.clicked.connect(lambda: self.table.selectAll())
        self.label_btn = button("Label QR", icon_name="qr")
        self.label_btn.clicked.connect(self._labels)
        self.delete_btn = button("Hapus permanen…", danger=True, icon_name="hapus")
        self.delete_btn.clicked.connect(self._delete)
        reset = button("Reset filter")
        reset.setObjectName("ghost")
        reset.clicked.connect(self._reset_filters)
        self.sel_info = label("", "small", wrap=False)
        for b in (self.select_all, self.label_btn, self.delete_btn):
            bar.addWidget(b)
        bar.addWidget(self.sel_info)
        bar.addStretch(1)
        bar.addWidget(reset)
        top.addLayout(bar)
        lst.layout().addLayout(top)
        self.table = Table(HEADERS, multi=True, chips={COND_COL, STATUS_COL}, empty_text="Tidak ada aset yang cocok dengan pencarian / filter")
        self.table.current_changed.connect(self._preview)
        self.table.activated_row.connect(self._preview)
        lst.layout().addWidget(self.table, 1)
        split.addWidget(lst)
        self.detail = AssetDetailPanel(ctx)
        self.detail.changed.connect(self.refresh)
        split.addWidget(self.detail)
        split.setStretchFactor(0, 3)
        split.setStretchFactor(1, 2)
        split.setSizes([760, 420])
        v.addWidget(split, 1)

        QShortcut(QKeySequence.StandardKey.Find, self, activated=self.search.setFocus)
        QShortcut(QKeySequence.StandardKey.New, self, activated=self._shortcut_add)
        self._debounce = QTimer(self, singleShot=True, interval=250)
        self._debounce.timeout.connect(self.refresh)
        self.search.textChanged.connect(lambda _: self._debounce.start())
        for c in (self.sort, self.f_category, self.f_location, self.f_division, self.f_department, self.f_condition, self.f_status):
            c.currentIndexChanged.connect(lambda _: self.refresh())
        self.table.selectionModel().selectionChanged.connect(lambda *_: self._update_selection())

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
        self.detail.render()

    def _shortcut_add(self) -> None:
        if self.add_btn.isVisible():
            self._add()

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

    def _kpis(self) -> None:
        s = summary(self.ctx.db)
        self.k_total.set(fmt.number(s.total), f"+{s.added_this_month} bulan ini" if s.added_this_month else "aktif (tanpa Write Off)")
        self.k_book.set(fmt.rupiah_short(s.book_value), f"dari harga perolehan {fmt.rupiah_short(s.cost)}")
        self.k_warranty.set(fmt.number(s.warranty_expiring), "dalam 90 hari", "orange" if s.warranty_expiring else None)
        self.k_damaged.set(fmt.number(s.damaged), f"{s.in_repair} dalam perbaikan", "red" if s.damaged else None)
        self.subtitle.setText(f"{fmt.number(s.total)} aset aktif · {s.locations} lokasi")

    def refresh(self) -> None:
        rows = self.ctx.assets.list(self._filter())
        self.table.write_off_name = self.ctx.masters.status_names().get(WRITE_OFF_ID)
        wo = {r["id"] for r in rows if r["status"] == WRITE_OFF_ID}
        self.table.set_rows(
            [(r["id"], [
                r["code"], r["name"], r["categoryName"], r["locationName"], r["userLocationDetail"] or "-",
                AssetCondition[r["condition"]].label, r["statusName"] or r["status"], fmt.rupiah(r["acquisitionCost"]),
            ]) for r in rows],
            # Aset Write Off tetap tampil (abu-abu) dan ikut "Pilih semua".
            muted=lambda aid: aid in wo,
            align_right={COST_COL},
        )
        self.table.set_widths([125, 230, 140, 140, 170, 90, 150])
        self._kpis()
        self._update_selection()
        if self.detail.asset_id and not self.ctx.assets.get_row(self.detail.asset_id):
            self.detail.show_asset(None)

    def _update_selection(self) -> None:
        n = len(self.table.selected_data())
        total = self.table.model_.rowCount()
        self.sel_info.setText(f"{n} dipilih dari {total}" if n > 1 else f"{total} ditampilkan")

    # ------------------------------------------------------------------
    def _preview(self, asset_id) -> None:
        if asset_id and asset_id != self.detail.asset_id:
            self.detail.show_asset(asset_id)

    def _add(self) -> None:
        dlg = AssetFormDialog(self, self.ctx)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.refresh()
            self.detail.show_asset(dlg.saved_id)
            notify(self, f"Aset {self.ctx.assets.get_row(dlg.saved_id)['code']} ditambahkan")

    def _open(self, asset_id) -> None:
        """Tampilkan aset di panel detail."""
        if asset_id:
            self.detail.show_asset(asset_id)

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
