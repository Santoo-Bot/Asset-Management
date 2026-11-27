"""Scan: scanner barcode/QR USB (bekerja seperti keyboard: mengetik kode lalu Enter) atau ketik kode manual.
Cocok dengan kode aset atau kode QR; kode barang/barcode stok juga dikenali."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtGui import QFont
from PySide6.QtWidgets import QHBoxLayout, QLabel, QLineEdit, QVBoxLayout, QWidget

from ..core import fmt
from .asset_detail import AssetDetailDialog
from .stock_page import ItemDetailDialog
from . import icons, theme
from .widgets import Banner, Table, card, label, page_header, page_layout, primary


class ScanPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = page_layout(self)
        v.addLayout(page_header("Scan", label("Scanner barcode/QR USB atau ketik kode, lalu Enter", "subtitle", wrap=False)))
        box = card("v", (28, 26, 28, 26), 12)
        b = box.layout()
        head = QHBoxLayout()
        ic = QLabel()
        ic.setPixmap(icons.icon("scan", theme.PRIMARY, 30, 1.8).pixmap(30, 30))
        head.addWidget(ic)
        txt = QVBoxLayout()
        txt.setSpacing(2)
        txt.addWidget(label("Siap memindai", "h2", wrap=False))
        txt.addWidget(label("Arahkan scanner ke label. Kode aset, kode QR, dan kode/barcode barang stok dikenali.", "muted"))
        head.addLayout(txt, 1)
        b.addLayout(head)
        row = QHBoxLayout()
        self.input = QLineEdit()
        self.input.setPlaceholderText("Kode aset, kode QR, atau barcode barang")
        f = QFont()
        f.setPointSize(15)
        self.input.setFont(f)
        self.input.setMinimumHeight(50)
        self.input.returnPressed.connect(self._lookup)
        go = primary("Cari", "cari")
        go.setMinimumHeight(50)
        go.clicked.connect(self._lookup)
        row.addWidget(self.input, 1)
        row.addWidget(go)
        b.addLayout(row)
        self.banner = Banner()
        b.addWidget(self.banner)
        v.addWidget(box)
        hist = card("v", (0, 0, 0, 0))
        hh = QHBoxLayout()
        hh.setContentsMargins(16, 14, 16, 6)
        hh.addWidget(label("Hasil scan terakhir", "h2", wrap=False))
        hist.layout().addLayout(hh)
        self.history = Table(["Waktu", "Kode", "Hasil"], empty_text="Belum ada scan pada sesi ini")
        self.history.activated_row.connect(self._reopen)
        hist.layout().addWidget(self.history, 1)
        v.addWidget(hist, 1)
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
