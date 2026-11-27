"""Lokasi folder data. Versi exe portable menyimpan data di folder `data` di samping AsetGA.exe."""

from __future__ import annotations

import os
import sys
from pathlib import Path


def app_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent


def resource_dir() -> Path:
    """Folder resources (skema, ikon). Di exe PyInstaller berada di sys._MEIPASS."""
    base = getattr(sys, "_MEIPASS", None)
    if base:
        return Path(base) / "asetga" / "resources"
    return Path(__file__).resolve().parent / "resources"


def _writable(d: Path) -> bool:
    try:
        d.mkdir(parents=True, exist_ok=True)
        probe = d / ".tulis-uji"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink()
        return True
    except OSError:
        return False


def data_dir() -> Path:
    """`<folder exe>/data`. Bila tidak bisa ditulis (mis. di Program Files), pakai %LOCALAPPDATA%/AsetGA."""
    override = os.environ.get("ASETGA_DATA_DIR")
    if override:
        d = Path(override)
        d.mkdir(parents=True, exist_ok=True)
        return d
    d = app_dir() / "data"
    if _writable(d):
        return d
    fallback = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "AsetGA"
    fallback.mkdir(parents=True, exist_ok=True)
    return fallback
