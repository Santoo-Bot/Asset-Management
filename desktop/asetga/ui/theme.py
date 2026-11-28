"""Gaya "modern lembut": latar abu-abu muda, kartu putih bersudut membulat, aksen biru BCA Life #0060AF.
Token warna terang/gelap dipakai QSS, delegate tabel (chip status), dan komponen lain."""

from __future__ import annotations

from PySide6.QtGui import QColor, QFont, QPalette
from PySide6.QtWidgets import QApplication

PRIMARY = "#0060AF"
PRIMARY_DARK = "#004A87"
ERROR = "#B3261E"

LIGHT = {
    "bg": "#F4F6F9", "surface": "#FFFFFF", "surface2": "#F7F9FB", "text": "#1B1F24", "muted": "#5B6675",
    "border": "#E3E8EE", "hover": "#F2F5F8", "select": "#EAF2FB", "select_text": "#0B1E33", "photo": "#EEF2F6",
    "primary_soft": "#E6F0F9", "nav_active_text": "#FFFFFF", "banner_bg": "#FDECEA", "banner_fg": "#8C1D18",
    "info_bg": "#E6F0F9", "info_fg": "#0B2E52", "toast": "#1B2733",
}
DARK = {
    "bg": "#14181D", "surface": "#1D232A", "surface2": "#232A32", "text": "#E6EAEF", "muted": "#9AA6B4",
    "border": "#2E3946", "hover": "#232B34", "select": "#16314F", "select_text": "#FFFFFF", "photo": "#26303A",
    "primary_soft": "#16314F", "nav_active_text": "#FFFFFF", "banner_bg": "#4A1E1C", "banner_fg": "#F9D3D0",
    "info_bg": "#16314F", "info_fg": "#D6E7F8", "toast": "#0B1117",
}

# Warna chip: (latar terang, teks terang, latar gelap, teks gelap)
_TONES = {
    "green": ("#E3F5EC", "#146C43", "#123D2B", "#7EE2B1"),
    "blue": ("#E6F0F9", "#004A87", "#13304D", "#9CC7F0"),
    "orange": ("#FFF1E0", "#8A4B00", "#43301A", "#F5C07A"),
    "gray": ("#ECEEF1", "#4A5361", "#2A313A", "#AEB7C2"),
    "red": ("#FDE8E7", "#A1221A", "#4A1E1C", "#F4A39D"),
    "purple": ("#F0EAFB", "#5B2E91", "#2E2342", "#CDB4F2"),
}

# Teks chip → nada warna. Status buatan pengguna (master) yang tidak dikenal memakai ungu.
CHIP_TONES = {
    "Digunakan": "green", "Tersedia": "blue", "Dalam Perbaikan": "orange", "Propose To Write Off": "orange", "Write Off": "gray",
    "Baik": "green", "Rusak": "red", "Aman": "green", "Reorder": "red",
    "Terlambat": "red", "Jatuh tempo": "orange", "Terjadwal": "blue", "Belum dijadwalkan": "gray",
    "Aktif": "green", "Nonaktif": "gray", "Masuk": "green", "Keluar": "orange",
    "Admin GA": "blue", "Staf GA": "green", "Viewer": "gray",
}

_mode = "SYSTEM"


def is_dark(mode: str | None = None) -> bool:
    mode = mode or _mode
    if mode == "DARK":
        return True
    if mode == "LIGHT":
        return False
    try:
        from PySide6.QtCore import Qt

        return QApplication.styleHints().colorScheme() == Qt.ColorScheme.Dark
    except Exception:  # noqa: BLE001 - Qt lama tidak punya colorScheme
        return False


def c() -> dict:
    """Token warna tema yang sedang aktif."""
    return DARK if is_dark() else LIGHT


def tone(name: str) -> tuple[str, str]:
    t = _TONES.get(name, _TONES["gray"])
    return (t[2], t[3]) if is_dark() else (t[0], t[1])


def chip_colors(text: str, write_off_name: str | None = None) -> tuple[str, str]:
    key = "Write Off" if write_off_name and text == write_off_name else text
    return tone(CHIP_TONES.get(key, "purple"))


def apply(app: QApplication, mode: str) -> None:
    global _mode
    _mode = mode
    k = c()
    app.setStyle("Fusion")
    font = QFont()
    font.setFamilies(["Segoe UI Variable Text", "Segoe UI", "Inter", "Noto Sans", "sans-serif"])
    font.setPointSizeF(10)
    app.setFont(font)
    pal = QPalette()
    for role, key in (
        (QPalette.ColorRole.Window, "bg"), (QPalette.ColorRole.Base, "surface"), (QPalette.ColorRole.AlternateBase, "surface2"),
        (QPalette.ColorRole.Text, "text"), (QPalette.ColorRole.WindowText, "text"), (QPalette.ColorRole.Button, "surface"),
        (QPalette.ColorRole.ButtonText, "text"), (QPalette.ColorRole.Highlight, "select"),
        (QPalette.ColorRole.HighlightedText, "select_text"), (QPalette.ColorRole.PlaceholderText, "muted"),
        (QPalette.ColorRole.ToolTipBase, "surface"), (QPalette.ColorRole.ToolTipText, "text"),
    ):
        pal.setColor(role, QColor(k[key]))
    app.setPalette(pal)
    app.setStyleSheet(f"""
        QMainWindow, QDialog {{ background: {k['bg']}; }}
        QWidget#page {{ background: {k['bg']}; }}
        QLabel#title {{ font-size: 18pt; font-weight: 700; }}
        QLabel#h2 {{ font-size: 13pt; font-weight: 700; }}
        QLabel#subtitle, QLabel#muted {{ color: {k['muted']}; }}
        QLabel#small {{ color: {k['muted']}; font-size: 9pt; }}
        QLabel#section {{ color: {k['muted']}; font-size: 8.5pt; font-weight: 600; letter-spacing: 0.5px; }}
        QLabel#error {{ color: {ERROR}; font-size: 9pt; }}
        QLabel#banner {{ background: {k['banner_bg']}; color: {k['banner_fg']}; border-radius: 10px; padding: 10px 12px; }}
        QLabel#info {{ background: {k['info_bg']}; color: {k['info_fg']}; border-radius: 10px; padding: 10px 12px; }}
        QLabel#kpiValue {{ font-size: 17pt; font-weight: 700; }}
        QLabel#logo {{ background: {PRIMARY}; color: #FFFFFF; border-radius: 10px; font-weight: 700; }}
        QLabel#avatar {{ background: {k['primary_soft']}; color: {PRIMARY}; border-radius: 17px; font-weight: 600; }}
        QLabel#photo {{ background: {k['photo']}; border-radius: 12px; color: {k['muted']}; }}
        QFrame#card {{ background: {k['surface']}; border: 1px solid {k['border']}; border-radius: 14px; }}
        QFrame#soft {{ background: {k['bg']}; border: none; border-radius: 10px; }}
        QFrame#sidebar {{ background: {k['surface']}; border: none; border-right: 1px solid {k['border']}; }}
        QFrame#divider {{ background: {k['border']}; max-height: 1px; min-height: 1px; border: none; }}
        QListWidget#nav {{ background: transparent; border: none; outline: 0; }}
        QListWidget#nav::item {{ padding: 9px 10px; border-radius: 10px; margin: 1px 0; color: {k['text']}; }}
        QListWidget#nav::item:hover:!selected {{ background: {k['hover']}; }}
        QListWidget#nav::item:selected {{ background: {PRIMARY}; color: #FFFFFF; font-weight: 600; }}
        QListWidget#nav[compact="true"]::item {{ padding: 9px 0px 9px 13px; }}
        QToolButton#navToggle {{ border: none; border-radius: 10px; padding: 8px 10px; color: {k['muted']}; text-align: left; }}
        QToolButton#navToggle[compact="true"] {{ padding: 8px 0px 8px 13px; }}
        QToolButton#navToggle:hover {{ background: {k['hover']}; color: {k['text']}; }}
        QPushButton {{ background: {k['surface']}; color: {k['text']}; border: 1px solid {k['border']}; border-radius: 9px;
            padding: 7px 14px; min-height: 20px; }}
        QPushButton:hover {{ border-color: {PRIMARY}; }}
        QPushButton:pressed {{ background: {k['hover']}; }}
        QPushButton:disabled {{ color: {k['muted']}; background: {k['surface2']}; }}
        QPushButton#primary {{ background: {PRIMARY}; color: #FFFFFF; border: 1px solid {PRIMARY}; font-weight: 600; }}
        QPushButton#primary:hover {{ background: {PRIMARY_DARK}; border-color: {PRIMARY_DARK}; }}
        QPushButton#primary:disabled {{ background: #8FB3D6; border-color: #8FB3D6; }}
        QPushButton#danger {{ color: {ERROR if not is_dark() else '#F4A39D'}; }}
        QPushButton#danger:hover {{ border-color: {ERROR}; }}
        QPushButton#chip {{ border-radius: 15px; padding: 4px 12px; background: {k['bg']}; }}
        QPushButton#ghost {{ border: none; background: transparent; color: {PRIMARY}; font-weight: 600; }}
        QLineEdit, QComboBox, QPlainTextEdit, QSpinBox, QDoubleSpinBox, QDateEdit {{
            background: {k['surface']}; color: {k['text']}; border: 1px solid {k['border']}; border-radius: 9px; padding: 6px 10px;
            min-height: 20px; selection-background-color: {k['select']}; selection-color: {k['select_text']}; }}
        QLineEdit:focus, QComboBox:focus, QPlainTextEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus, QDateEdit:focus {{
            border: 1px solid {PRIMARY}; }}
        QLineEdit:disabled, QDateEdit:disabled {{ color: {k['muted']}; background: {k['surface2']}; }}
        QLineEdit#search {{ border-radius: 10px; padding-left: 8px; min-height: 26px; }}
        QLineEdit[invalid="true"], QComboBox[invalid="true"], QPlainTextEdit[invalid="true"] {{ border: 1px solid {ERROR}; }}
        QComboBox::drop-down {{ border: none; width: 22px; }}
        QComboBox QAbstractItemView {{ background: {k['surface']}; border: 1px solid {k['border']}; selection-background-color: {k['select']};
            selection-color: {k['select_text']}; outline: 0; padding: 4px; }}
        QCheckBox {{ spacing: 8px; }}
        QTableView {{ background: {k['surface']}; border: none; gridline-color: transparent; outline: 0;
            selection-background-color: {k['select']}; selection-color: {k['select_text']}; }}
        QTableView::item {{ padding: 0 10px; border-bottom: 1px solid {k['border']}; }}
        QHeaderView {{ background: {k['surface']}; }}
        QHeaderView::section {{ background: {k['surface']}; color: {k['muted']}; border: none; border-bottom: 1px solid {k['border']};
            padding: 8px 10px; font-size: 8.5pt; font-weight: 600; }}
        QTabWidget::pane {{ border: none; }}
        QTabBar::tab {{ background: transparent; color: {k['muted']}; padding: 8px 2px; margin-right: 18px; border: none;
            border-bottom: 2px solid transparent; }}
        QTabBar::tab:selected {{ color: {PRIMARY}; border-bottom: 2px solid {PRIMARY}; font-weight: 600; }}
        QTabBar::tab:hover:!selected {{ color: {k['text']}; }}
        QGroupBox {{ background: {k['surface']}; border: 1px solid {k['border']}; border-radius: 14px; margin-top: 14px;
            padding: 16px 14px 12px 14px; font-weight: 600; }}
        QGroupBox::title {{ subcontrol-origin: margin; left: 14px; padding: 0 4px; color: {k['text']}; }}
        QScrollArea {{ background: transparent; border: none; }}
        QScrollArea > QWidget > QWidget {{ background: transparent; }}
        QScrollBar:vertical {{ background: transparent; width: 10px; margin: 2px; }}
        QScrollBar::handle:vertical {{ background: {k['border']}; border-radius: 4px; min-height: 30px; }}
        QScrollBar:horizontal {{ background: transparent; height: 10px; margin: 2px; }}
        QScrollBar::handle:horizontal {{ background: {k['border']}; border-radius: 4px; min-width: 30px; }}
        QScrollBar::add-line, QScrollBar::sub-line {{ width: 0; height: 0; }}
        QSplitter::handle {{ background: transparent; }}
        QToolTip {{ background: {k['surface']}; color: {k['text']}; border: 1px solid {k['border']}; padding: 4px 8px; }}
        QProgressDialog {{ background: {k['surface']}; }}
        QLabel#toast {{ background: {k['toast']}; color: #FFFFFF; border-radius: 12px; padding: 12px 18px; }}
    """)
