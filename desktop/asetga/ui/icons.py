"""Ikon garis (stroke) sederhana, digambar dari SVG agar tajam di semua skala layar dan mengikuti warna tema."""

from __future__ import annotations

from PySide6.QtCore import QByteArray, QRectF, Qt
from PySide6.QtGui import QIcon, QPainter, QPixmap

PATHS = {
    "aset": "M3 7l9-4 9 4-9 4-9-4zM3 12l9 4 9-4M3 17l9 4 9-4",
    "scan": "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10",
    "stok": "M4 7h16v13H4zM9 7V4h6v3M4 12h16",
    "servis": "M14.5 6.5a4 4 0 0 0-5 5L3 18l3 3 6.5-6.5a4 4 0 0 0 5-5l-2.5 2.5-3-1-1-3z",
    "master": "M4 6h16M4 12h16M4 18h10",
    "data": "M12 3v12M7 10l5 5 5-5M5 21h14",
    "pengguna": "M16 19a4 4 0 0 0-8 0M12 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM20 19a3 3 0 0 0-3-3M17 5.5a2.5 2.5 0 0 1 0 5",
    "audit": "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z",
    "akun": "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 "
            "1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 "
            ".3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 "
            "0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 "
            "1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    "cari": "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-3.5-3.5",
    "tambah": "M12 5v14M5 12h14",
    "ubah": "M4 20h4L19 9l-4-4L4 16v4zM14 6l4 4",
    "hapus": "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
    "qr": "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 14h2M14 18h2v2M18 18h2v2h-2",
    "mutasi": "M4 8h13l-3-3M20 16H7l3 3",
    "kunci": "M6 11h12v9H6zM8 11V8a4 4 0 0 1 8 0v3",
    "keluar": "M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10",
    "foto": "M4 7h3l2-3h6l2 3h3v13H4zM12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
    "filter": "M4 5h16l-6 8v6l-4-2v-4z",
    "kosong": "M3 7l9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4M12 11v10",
    "cek": "M5 12l5 5 9-10",
    "unduh": "M12 4v11M7 10l5 5 5-5M5 20h14",
    "unggah": "M12 20V9M7 14l5-5 5 5M5 4h14",
    "cetak": "M7 9V4h10v5M7 17H5v-6h14v6h-2M7 14h10v6H7z",
    "dashboard": "M4 4h7v9H4zM13 4h7v5h-7zM13 11h7v9h-7zM4 15h7v5H4z",
    "kembali": "M15 6l-6 6 6 6M9 12h11",
    "kolom": "M4 5h16v14H4zM10 5v14M15 5v14",
    "export": "M12 15V4M7 9l5-5 5 5M5 20h14",
    "panah_bawah": "M6 9l6 6 6-6",
    "backup": "M12 3l8 4v5c0 5-3.5 8-8 9-4.5-1-8-4-8-9V7zM9 12l2 2 4-4",
}


def icon(name: str, color: str = "#5B6675", size: int = 20, stroke: float = 1.8) -> QIcon:
    from PySide6.QtSvg import QSvgRenderer

    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="{stroke}" '
           f'stroke-linecap="round" stroke-linejoin="round"><path d="{PATHS[name]}"/></svg>')
    renderer = QSvgRenderer(QByteArray(svg.encode()))
    ic = QIcon()
    for scale in (1, 2):
        pm = QPixmap(size * scale, size * scale)
        pm.fill(Qt.GlobalColor.transparent)
        p = QPainter(pm)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        renderer.render(p, QRectF(0, 0, size * scale, size * scale))
        p.end()
        pm.setDevicePixelRatio(scale)
        ic.addPixmap(pm)
    return ic


def nav_icon(name: str, active_color: str, normal_color: str) -> QIcon:
    """Ikon menu: warna berbeda saat terpilih (putih di atas biru)."""
    on = icon(name, active_color)
    off = icon(name, normal_color)
    ic = QIcon()
    for sz in (20, 40):
        ic.addPixmap(off.pixmap(sz, sz), QIcon.Mode.Normal, QIcon.State.Off)
        ic.addPixmap(on.pixmap(sz, sz), QIcon.Mode.Selected, QIcon.State.Off)
    return ic
