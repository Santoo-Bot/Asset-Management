"""Format angka & tanggal gaya Indonesia: Rp 1.250.000, 26 Nov 2026, 26/11/2026."""

from __future__ import annotations

import datetime as _dt
import time

BULAN = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"]
_EPOCH = _dt.date(1970, 1, 1)


# ---- Waktu (bisa diganti di test) ----
def now_ms() -> int:
    return int(time.time() * 1000)


def today() -> _dt.date:
    return _dt.date.today()


# ---- Tanggal disimpan sebagai epochDay (INTEGER), sama dengan Android ----
def to_epoch_day(d: _dt.date | None) -> int | None:
    return None if d is None else (d - _EPOCH).days


def from_epoch_day(v: int | None) -> _dt.date | None:
    return None if v is None else _EPOCH + _dt.timedelta(days=int(v))


# ---- Tampilan ----
def number(value: int | float) -> str:
    return f"{int(round(value)):,}".replace(",", ".")


def rupiah(value: int | None) -> str:
    if value is None:
        return "-"
    return ("-Rp " if value < 0 else "Rp ") + number(abs(value))


def percent(value: float | None) -> str:
    if value is None:
        return "-"
    text = f"{value:,.2f}".rstrip("0").rstrip(".")
    return text.replace(",", "#").replace(".", ",").replace("#", ".") + "%"


def date(d: _dt.date | None) -> str:
    """"05 Jan 2025" (sama dengan Fmt.date di Android)."""
    return "-" if d is None else f"{d.day:02d} {BULAN[d.month - 1]} {d.year}"


def date_numeric(d: _dt.date | None) -> str:
    """dd/MM/yyyy, dipakai template import Excel dan isian form."""
    return "" if d is None else d.strftime("%d/%m/%Y")


def date_time(epoch_ms: int) -> str:
    t = _dt.datetime.fromtimestamp(epoch_ms / 1000)
    return f"{t.day:02d} {BULAN[t.month - 1]} {t.year} {t.hour:02d}:{t.minute:02d}"


def age(years: int, months: int) -> str:
    if years == 0 and months == 0:
        return "< 1 bln"
    if years == 0:
        return f"{months} bln"
    if months == 0:
        return f"{years} th"
    return f"{years} th {months} bln"


def parse_rupiah(text: str) -> int | None:
    """"1.250.000" / "Rp 1.250.000" → 1250000; None bila tidak valid."""
    digits = "".join(ch for ch in (text or "") if ch.isdigit())
    if not digits or len(digits) > 15:
        return None
    return int(digits)


def parse_decimal(text: str) -> float | None:
    """Terima "12,5" maupun "12.5"."""
    try:
        return float((text or "").strip().replace(",", "."))
    except ValueError:
        return None
