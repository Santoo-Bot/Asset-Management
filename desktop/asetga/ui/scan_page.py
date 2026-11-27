"""Scan: scanner barcode/QR USB (bekerja seperti keyboard: mengetik kode lalu Enter) atau ketik kode manual.
Cocok dengan kode aset atau kode QR; kode barang/barcode stok juga dikenali."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtGui import QFont
from PySide6.QtWidgets import QHBoxLayout, QLineEdit, QVBoxLayout, QWidget

from ..core import fmt
from .asset_detail import AssetDetailDialog
from .stock_page import ItemDetailDialog
from .widgets import Banner, Table, card, label, primary


class ScanPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = QVBoxLayout(self)
        v.addWidget(label("Scan", "title"))
        box = card()
        b = QVBoxLayout(box)
        b.setContentsMargins(20, 16, 20, 16)
        b.addWidget(label("Arahkan scanner barcode/QR USB ke label, atau ketik kode aset / kode QR / kode barang lalu tekan Enter.", "muted"))
        row = QHBoxLayout()
        self.input = QLineEdit()
        self.input.setPlaceholderText("Kode aset, kode QR, atau barcode barang")
        f = QFont()
        f.setPointSize(14)
        self.input.setFont(f)
        self.input.setMinimumHeight(44)
        self.input.returnPressed.connect(self._lookup)
        go = primary("Cari")
        go.setMinimumHeight(44)
        go.clicked.connect(self._lookup)
        row.addWidget(self.input, 1)
        row.addWidget(go)
        b.addLayout(row)
        self.banner = Banner()
        b.addWidget(self.banner)
        v.addWidget(box)
        v.addWidget(label("Hasil scan terakhir", None))
        self.history = Table(["Waktu", "Kode", "Hasil"])
        self.history.activated_row.connect(self._reopen)
        v.addWidget(self.history, 1)
        self._log: list[tuple[tuple[str, str] | None, list[str]]] = []

    def on_show(self) -> None:
        self.banner.show_text(None)
        self.input.setFocus()

    def _remember(self, target, code: str, result: str) -> None:
        import datetime as _dt

        self._log.insert(0, (target, [_dt.datetime.now().strftime("%H:%M:%S"), code, result]))
        del self._log[50:]
        self.history.set_rows([(t, cells) for t, cells in self._log])
        self.history.setColumnWidth(0, 80)
        self.history.setColumnWidth(1, 200)

    def _lookup(self) -> None:
        code = self.input.text().strip()
        self.input.clear()
        if not code:
            return
        self.banner.show_text(None)
        a = self.ctx.assets.find_by_code(code)
        if a:
            self._remember(("asset", a["id"]), code, f"Aset {a['code']} — {a['name']}")
            self._open(("asset", a["id"]))
            return
        it = self.ctx.stock.find_by_code_or_barcode(code)
        if it:
            self._remember(("item", it["id"]), code, f"Barang {it['code']} — {it['name']} (stok {fmt.qty(it['currentStock'])} {it['unitName']})")
            self._open(("item", it["id"]))
            return
        self._remember(None, code, "Tidak ditemukan")
        self.banner.show_text(f"Kode \"{code}\" tidak ditemukan sebagai kode aset, kode QR, maupun barang stok.")

    def _open(self, target) -> None:
        kind, ident = target
        if kind == "asset":
            if self.ctx.assets.get_row(ident):
                AssetDetailDialog(self, self.ctx, ident).exec()
        elif self.ctx.stock.item(ident):
            ItemDetailDialog(self, self.ctx, ident).exec()
        self.input.setFocus(Qt.FocusReason.OtherFocusReason)

    def _reopen(self, target) -> None:
        if target:
            self._open(target)
