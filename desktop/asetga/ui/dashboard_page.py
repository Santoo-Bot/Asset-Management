"""Dashboard analisis: ringkasan nilai, sebaran aset, dan daftar yang perlu perhatian. Bisa disaring per lokasi & divisi."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QComboBox, QGridLayout, QScrollArea, QVBoxLayout, QWidget

from ..core import dashboard, fmt
from ..core.models import MasterType
from . import theme
from .asset_detail import EntryList
from .charts import PALETTE, Donut, HBarChart
from .widgets import Combo, Kpi, card, label, page_header, page_layout


def _panel(title: str, subtitle: str = "") -> tuple[QWidget, QVBoxLayout]:
    box = card("v", (18, 16, 18, 16), 10)
    head = QVBoxLayout()
    head.setSpacing(0)
    head.addWidget(label(title, "h2", wrap=False))
    if subtitle:
        head.addWidget(label(subtitle, "small", wrap=False))
    box.layout().addLayout(head)
    return box, box.layout()


class DashboardPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        inner = QWidget()
        v = page_layout(inner)
        self.f_location = Combo("Semua lokasi")
        self.f_division = Combo("Semua divisi")
        for c in (self.f_location, self.f_division):
            c.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContents)
            c.currentIndexChanged.connect(lambda _: self.refresh())
        self.subtitle = label("", "subtitle", wrap=False)
        v.addLayout(page_header("Dashboard", self.subtitle, self.f_location, self.f_division))

        kpi = QGridLayout()
        kpi.setSpacing(14)
        self.k_total = Kpi("Aset aktif")
        self.k_cost = Kpi("Harga perolehan")
        self.k_book = Kpi("Nilai buku")
        self.k_dep = Kpi("Akumulasi penyusutan")
        self.k_damaged = Kpi("Aset rusak")
        self.k_wo = Kpi("Write Off")
        for i, k in enumerate((self.k_total, self.k_cost, self.k_book, self.k_dep, self.k_damaged, self.k_wo)):
            kpi.addWidget(k, 0, i)
            kpi.setColumnStretch(i, 1)
        v.addLayout(kpi)

        grid = QGridLayout()
        grid.setSpacing(14)
        p, lay = _panel("Status aset", "Jumlah aset aktif per status")
        self.donut = Donut("aset aktif")
        lay.addWidget(self.donut)
        lay.addStretch(1)
        grid.addWidget(p, 0, 0)
        p, lay = _panel("Aset per kategori", "Jumlah aset")
        self.cat_count = HBarChart()
        lay.addWidget(self.cat_count)
        lay.addStretch(1)
        grid.addWidget(p, 0, 1)
        p, lay = _panel("Nilai buku per kategori", "Setelah penyusutan garis lurus")
        self.cat_book = HBarChart()
        lay.addWidget(self.cat_book)
        lay.addStretch(1)
        grid.addWidget(p, 0, 2)
        p, lay = _panel("Aset per lokasi", "Jumlah aset")
        self.loc = HBarChart()
        lay.addWidget(self.loc)
        lay.addStretch(1)
        grid.addWidget(p, 1, 0)
        p, lay = _panel("Aset per divisi", "Jumlah aset")
        self.div = HBarChart()
        lay.addWidget(self.div)
        lay.addStretch(1)
        grid.addWidget(p, 1, 1)
        p, lay = _panel("Harga perolehan per tahun", "Tahun perolehan aset aktif")
        self.year = HBarChart(max_rows=10)
        lay.addWidget(self.year)
        lay.addStretch(1)
        grid.addWidget(p, 1, 2)
        p, lay = _panel("Kondisi aset", "Jumlah aset aktif")
        self.cond = HBarChart()
        lay.addWidget(self.cond)
        lay.addStretch(1)
        grid.addWidget(p, 2, 0)
        p, lay = _panel("Umur aset", "Sejak tanggal perolehan")
        self.age = HBarChart()
        lay.addWidget(self.age)
        lay.addStretch(1)
        grid.addWidget(p, 2, 1)
        p, lay = _panel("Garansi segera habis", "Dalam 90 hari · klik dua kali untuk membuka aset")
        self.warranty = EntryList("Tidak ada garansi yang segera habis")
        self.warranty.setMinimumHeight(170)
        self.warranty.activated_entry.connect(self._open_asset)
        lay.addWidget(self.warranty)
        grid.addWidget(p, 2, 2)
        p, lay = _panel("Servis jatuh tempo & terlambat", "Klik dua kali untuk membuka aset")
        self.services = EntryList("Tidak ada servis yang jatuh tempo")
        self.services.setMinimumHeight(170)
        self.services.activated_entry.connect(self._open_asset)
        lay.addWidget(self.services)
        grid.addWidget(p, 3, 0)
        p, lay = _panel("Aset rusak", "Kondisi Rusak · klik dua kali untuk membuka aset")
        self.damaged = EntryList("Tidak ada aset rusak")
        self.damaged.setMinimumHeight(170)
        self.damaged.activated_entry.connect(self._open_asset)
        lay.addWidget(self.damaged)
        grid.addWidget(p, 3, 1)
        p, lay = _panel("Stok perlu reorder", "Stok ≤ minimum")
        self.reorder = EntryList("Semua stok aman")
        self.reorder.setMinimumHeight(170)
        lay.addWidget(self.reorder)
        grid.addWidget(p, 3, 2)
        for i in range(3):
            grid.setColumnStretch(i, 1)
        v.addLayout(grid)
        v.addStretch(1)
        scroll.setWidget(inner)
        outer.addWidget(scroll)

    def on_show(self) -> None:
        m = self.ctx.masters
        for combo, t in ((self.f_location, MasterType.LOCATION), (self.f_division, MasterType.DIVISION)):
            combo.blockSignals(True)
            combo.set_items([(x["name"], x["id"]) for x in m.list(t)])
            combo.blockSignals(False)
        self.refresh()

    def refresh(self) -> None:
        d = dashboard.build(self.ctx.db, self.ctx.ops, self.ctx.stock, self.f_location.value(), self.f_division.value())
        where = " · ".join(x for x in (self.f_location.currentText() if self.f_location.value() else "",
                                       self.f_division.currentText() if self.f_division.value() else "") if x) or "Semua lokasi & divisi"
        self.subtitle.setText(f"Analisis aset · {where} · per {fmt.date(fmt.today())}")
        self.k_total.set(fmt.number(d.total), f"{d.in_repair} dalam perbaikan")
        self.k_cost.set(fmt.rupiah_short(d.cost), "total harga perolehan")
        self.k_book.set(fmt.rupiah_short(d.book), f"{100 - d.depreciation_percent:.0f}% dari harga perolehan".replace(".", ","))
        self.k_dep.set(fmt.rupiah_short(d.accumulated), f"{d.depreciation_percent:.1f}% tersusutkan".replace(".", ","))
        self.k_damaged.set(fmt.number(d.damaged), f"{d.warranty_expiring} garansi segera habis", "red" if d.damaged else None)
        self.k_wo.set(fmt.number(d.write_off), "tidak ikut perhitungan", None)

        status_rows = []
        for i, (lab, n, _, _) in enumerate(d.by_status.sorted()):
            tone = theme.CHIP_TONES.get(lab)
            color = {"green": "#2BA673", "blue": theme.PRIMARY, "orange": "#F28C28", "gray": "#9AA6B4", "red": "#D64550"}.get(tone or "")
            status_rows.append((lab, n, color or PALETTE[(i + 3) % len(PALETTE)]))
        self.donut.set_rows(status_rows)
        cnt = lambda b: [(k, n, fmt.number(n)) for k, n, _, _ in b.sorted(0)]  # noqa: E731
        self.cat_count.set_rows(cnt(d.by_category))
        self.cat_book.set_rows([(k, book, fmt.rupiah_short(book)) for k, _, _, book in d.by_category.sorted(2)])
        self.loc.set_rows(cnt(d.by_location))
        self.div.set_rows(cnt(d.by_division))
        years = sorted(d.by_year.rows.items(), key=lambda kv: kv[0], reverse=True)
        self.year.set_rows([(y, v[1], fmt.rupiah_short(v[1])) for y, v in years])
        self.cond.set_rows(cnt(d.by_condition))
        order = [b[1] for b in dashboard.AGE_BUCKETS]
        self.age.set_rows([(lab, d.by_age.rows[lab][0], fmt.number(d.by_age.rows[lab][0])) for lab in order if lab in d.by_age.rows])
        self.cond.color = "#F28C28"
        self.age.color = "#2BA673"
        self.year.color = "#7B5CC4"
        self.warranty.set_entries([(i, f"{c} · {n}", f"Garansi habis dalam {days} hari") for i, c, n, days in d.warranty_list], True)
        self.services.set_entries([(i, f"{c} · {n}", f"{st} · jatuh tempo {due}") for i, c, n, due, st in d.service_list], True)
        self.damaged.set_entries([(i, f"{c} · {n}", loc) for i, c, n, loc in d.damaged_list], True)
        self.reorder.set_entries([(i, f"{c} · {n}", s) for i, c, n, s in d.reorder_list])

    def _open_asset(self, asset_id) -> None:
        opener = getattr(self.ctx, "open_asset", None)
        if asset_id and opener:
            opener(asset_id)
