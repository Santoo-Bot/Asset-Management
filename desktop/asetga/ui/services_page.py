"""Servis: jadwal servis berikutnya (dari catatan terakhir tiap aset) dan riwayat semua servis."""

from __future__ import annotations

from PySide6.QtCore import QTimer
from PySide6.QtWidgets import QHBoxLayout, QTabWidget, QVBoxLayout, QWidget

from ..core import fmt
from .asset_detail import AssetDetailDialog
from .operation_dialogs import service_cells
from .widgets import Combo, Table, card, label, page_header, page_layout, search_field

STATUSES = ["Terlambat", "Jatuh tempo", "Terjadwal"]


class ServicesPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = page_layout(self)
        v.addLayout(page_header("Servis", label("Catat servis baru dari panel detail aset (tombol Catat servis)", "subtitle", wrap=False)))
        box = card("v", (16, 8, 16, 0))
        self.tabs = QTabWidget()
        self.tabs.setDocumentMode(True)
        # Jadwal
        sched = QWidget()
        sv = QVBoxLayout(sched)
        sv.setContentsMargins(0, 10, 0, 0)
        row = QHBoxLayout()
        self.status = Combo("Semua status")
        self.status.set_items([(s, s) for s in STATUSES])
        self.status.currentIndexChanged.connect(lambda _: self.refresh())
        row.addWidget(self.status)
        row.addStretch(1)
        sv.addLayout(row)
        self.sched = Table(["Kode aset", "Nama aset", "Servis terakhir", "Jatuh tempo", "Status"], chips={4},
                           empty_text="Belum ada jadwal servis. Isi jadwal berikutnya saat mencatat servis.")
        self.sched.activated_row.connect(self._open_asset)
        sv.addWidget(self.sched, 1)
        self.tabs.addTab(sched, "Jadwal")
        # Riwayat
        hist = QWidget()
        hv = QVBoxLayout(hist)
        hv.setContentsMargins(0, 10, 0, 0)
        self.search = search_field("Cari aset, vendor, deskripsi")
        hv.addWidget(self.search)
        self.hist = Table(["Kode aset", "Nama aset", "Tanggal", "Jenis", "Vendor", "Biaya", "Deskripsi", "Berikutnya"],
                          empty_text="Belum ada catatan servis")
        self.hist.activated_row.connect(self._open_asset)
        hv.addWidget(self.hist, 1)
        self.tabs.addTab(hist, "Riwayat")
        self.tabs.currentChanged.connect(lambda _: self.refresh())
        box.layout().addWidget(self.tabs, 1)
        v.addWidget(box, 1)
        self._t = QTimer(self, singleShot=True, interval=250)
        self._t.timeout.connect(self.refresh)
        self.search.textChanged.connect(lambda _: self._t.start())

    def on_show(self) -> None:
        self.refresh()

    def refresh(self) -> None:
        want = self.status.value()
        rows = [(r, st) for r, st in self.ctx.ops.schedules() if want is None or st == want]
        self.sched.set_rows(
            [(r["assetId"], [r["assetCode"], r["assetName"], fmt.date_numeric(fmt.from_epoch_day(r["lastServiceDate"])),
                             fmt.date_numeric(fmt.from_epoch_day(r["nextServiceDate"])), st]) for r, st in rows],
            muted=None,
        )
        for i, w in enumerate((120, 240, 110, 110)):
            self.sched.setColumnWidth(i, w)
        self.hist.set_rows([(s["assetId"], [s["assetCode"], s["assetName"], *service_cells(s)]) for s in self.ctx.ops.services(self.search.text())],
                           align_right={5})
        for i, w in enumerate((120, 200, 90, 80, 130, 110, 220)):
            self.hist.setColumnWidth(i, w)

    def _open_asset(self, asset_id) -> None:
        if asset_id and self.ctx.assets.get_row(asset_id):
            dlg = AssetDetailDialog(self, self.ctx, asset_id)
            dlg.exec()
            if dlg.changed:
                self.refresh()
