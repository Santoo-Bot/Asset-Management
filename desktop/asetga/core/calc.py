"""Perhitungan murni: penyusutan, umur, garansi, kode aset. Rumus sama dengan Android (domain/calc)."""

from __future__ import annotations

import datetime as _dt
from dataclasses import dataclass


def _months_between(start: _dt.date, end: _dt.date) -> int:
    """Jumlah bulan penuh dari [start] ke [end] (setara ChronoUnit.MONTHS.between)."""
    months = (end.year - start.year) * 12 + (end.month - start.month)
    if end.day < start.day:
        months -= 1
    return max(months, 0)


def _round_half_up(x: float) -> int:
    """Sama dengan Kotlin roundToLong() untuk nilai positif."""
    import math

    return int(math.floor(x + 0.5))


@dataclass(frozen=True)
class Depreciation:
    residual_value: int
    depreciable_amount: int
    annual_depreciation: int
    months_elapsed: int
    accumulated: int
    book_value: int

    @property
    def fully_depreciated(self) -> bool:
        return self.accumulated >= self.depreciable_amount


def depreciation(cost: int, acquisition: _dt.date, useful_life_years: int, residual_percent: float, as_of: _dt.date) -> Depreciation:
    """Garis lurus, prorata per bulan penuh sejak tanggal perolehan."""
    if cost < 0:
        raise ValueError("Harga tidak boleh negatif")
    life = max(useful_life_years, 1)
    pct = min(max(residual_percent, 0.0), 100.0)
    residual = _round_half_up(cost * pct / 100.0)
    depreciable = cost - residual
    annual = _round_half_up(depreciable / life)
    months = _months_between(acquisition, as_of) if as_of > acquisition else 0
    total = life * 12
    accumulated = depreciable if months >= total else min(_round_half_up(depreciable * months / total), depreciable)
    return Depreciation(residual, depreciable, annual, months, accumulated, cost - accumulated)


def asset_age(acquisition: _dt.date, as_of: _dt.date) -> tuple[int, int]:
    """(tahun, bulan) setara Period.between."""
    if as_of <= acquisition:
        return 0, 0
    months = _months_between(acquisition, as_of)
    return months // 12, months % 12


WARRANTY_EXPIRING_DAYS = 90


def warranty_status(until: _dt.date | None, today: _dt.date) -> str:
    if until is None:
        return "Tidak ada"
    if until < today:
        return "Habis"
    if (until - today).days <= WARRANTY_EXPIRING_DAYS:
        return "Segera habis"
    return "Aktif"


def format_asset_code(prefix: str, year: int, sequence: int) -> str:
    """PREFIX-TAHUN-NNN, mis. IT-2024-001."""
    return f"{prefix.upper()}-{year}-{sequence:03d}"


def next_sequence(prefix: str, year: int, existing_codes) -> int:
    head = f"{prefix.upper()}-{year}-"
    best = 0
    for code in existing_codes:
        c = code.upper()
        if c.startswith(head):
            tail = c[len(head):]
            if tail.isdigit():
                best = max(best, int(tail))
    return best + 1
