"""Dashboard analisis aset: ringkasan nilai, sebaran per kategori/lokasi/divisi/status/kondisi/tahun, dan daftar perhatian.

Aset Write Off tidak ikut ringkasan & sebaran (sama dengan dashboard Android); jumlahnya ditampilkan terpisah.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field

from . import calc, fmt
from .assets import DETAIL_SELECT
from .db import Database
from .models import WRITE_OFF_ID, AssetCondition
from .operations import OperationService
from .stock import StockService, needs_reorder


@dataclass
class Breakdown:
    """Sebaran: label → (jumlah aset, harga perolehan, nilai buku)."""
    rows: dict[str, list[int]] = field(default_factory=lambda: defaultdict(lambda: [0, 0, 0]))

    def add(self, key: str, cost: int, book: int) -> None:
        r = self.rows[key]
        r[0] += 1
        r[1] += cost
        r[2] += book

    def sorted(self, by: int = 0) -> list[tuple[str, int, int, int]]:
        return sorted(((k, *v) for k, v in self.rows.items()), key=lambda x: (-x[1 + by], x[0]))


@dataclass
class Dashboard:
    total: int = 0
    cost: int = 0
    book: int = 0
    accumulated: int = 0
    damaged: int = 0
    in_repair: int = 0
    write_off: int = 0
    warranty_expiring: int = 0
    by_status: Breakdown = field(default_factory=Breakdown)
    by_category: Breakdown = field(default_factory=Breakdown)
    by_location: Breakdown = field(default_factory=Breakdown)
    by_division: Breakdown = field(default_factory=Breakdown)
    by_condition: Breakdown = field(default_factory=Breakdown)
    by_year: Breakdown = field(default_factory=Breakdown)
    by_age: Breakdown = field(default_factory=Breakdown)
    warranty_list: list[tuple[str, str, str, int]] = field(default_factory=list)   # (id, kode, nama, sisa hari)
    service_list: list[tuple[str, str, str, str, str]] = field(default_factory=list)  # (id, kode, nama, jatuh tempo, status)
    damaged_list: list[tuple[str, str, str, str]] = field(default_factory=list)    # (id, kode, nama, lokasi)
    reorder_list: list[tuple[str, str, str, str]] = field(default_factory=list)    # (id, kode, nama, stok)

    @property
    def depreciation_percent(self) -> float:
        return 0.0 if self.cost == 0 else self.accumulated * 100.0 / self.cost


AGE_BUCKETS = [(1, "< 1 tahun"), (3, "1–3 tahun"), (5, "3–5 tahun"), (10, "5–10 tahun"), (10_000, "> 10 tahun")]


def _age_bucket(years: int) -> str:
    for limit, label in AGE_BUCKETS:
        if years < limit:
            return label
    return AGE_BUCKETS[-1][1]


def build(db: Database, ops: OperationService, stock: StockService, location_id: str | None = None,
          division_id: str | None = None) -> Dashboard:
    today = fmt.today()
    default_res = db.default_residual_percent()
    d = Dashboard()
    rows = db.q(DETAIL_SELECT + " WHERE a.deletedAt IS NULL AND (? IS NULL OR a.locationId = ?) AND (? IS NULL OR a.divisionId = ?)",
                (location_id, location_id, division_id, division_id))
    active_ids: set[str] = set()
    for r in rows:
        if r["status"] == WRITE_OFF_ID:
            d.write_off += 1
            continue
        active_ids.add(r["id"])
        acq = fmt.from_epoch_day(r["acquisitionDate"])
        res = r["categoryResidualPercent"] if r["categoryResidualPercent"] is not None else default_res
        dep = calc.depreciation(r["acquisitionCost"], acq, r["usefulLifeYears"], res, today)
        cost, book = r["acquisitionCost"], dep.book_value
        d.total += 1
        d.cost += cost
        d.book += book
        d.accumulated += dep.accumulated
        d.by_status.add(r["statusName"] or r["status"], cost, book)
        d.by_category.add(r["categoryName"], cost, book)
        d.by_location.add(r["locationName"], cost, book)
        d.by_division.add(r["divisionName"] or "Tanpa divisi", cost, book)
        d.by_condition.add(AssetCondition[r["condition"]].label, cost, book)
        d.by_year.add(str(acq.year), cost, book)
        d.by_age.add(_age_bucket(calc.asset_age(acq, today)[0]), cost, book)
        if r["condition"] == "RUSAK":
            d.damaged += 1
            d.damaged_list.append((r["id"], r["code"], r["name"], r["locationName"]))
        if r["status"] == "DALAM_PERBAIKAN":
            d.in_repair += 1
        until = fmt.from_epoch_day(r["warrantyUntil"])
        if calc.warranty_status(until, today) == "Segera habis":
            d.warranty_expiring += 1
            d.warranty_list.append((r["id"], r["code"], r["name"], (until - today).days))
    d.warranty_list.sort(key=lambda x: x[3])
    for s, status in ops.schedules():
        if s["assetId"] in active_ids and status in ("Terlambat", "Jatuh tempo"):
            d.service_list.append((s["assetId"], s["assetCode"], s["assetName"],
                                   fmt.date(fmt.from_epoch_day(s["nextServiceDate"])), status))
    for it in stock.items():
        if location_id and it["storageLocationId"] != location_id:
            continue
        if needs_reorder(it["currentStock"], it["minStock"]):
            d.reorder_list.append((it["id"], it["code"], it["name"],
                                   f"{fmt.qty(it['currentStock'])} / min {fmt.qty(it['minStock'])} {it['unitName']}"))
    return d
