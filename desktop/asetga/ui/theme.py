"""Tema BCA Life (biru #0060AF), terang dan gelap."""

from __future__ import annotations

from PySide6.QtGui import QColor, QPalette
from PySide6.QtWidgets import QApplication

PRIMARY = "#0060AF"
PRIMARY_DARK = "#004A87"
PRIMARY_LIGHT = "#E6F0F9"
ERROR = "#B3261E"

LIGHT = {
    "bg": "#F5F7FA", "surface": "#FFFFFF", "text": "#1B1F24", "muted": "#5F6B7A", "border": "#D5DCE4",
    "nav": PRIMARY, "nav_text": "#FFFFFF", "nav_sel": "#FFFFFF", "nav_sel_text": PRIMARY, "row_alt": "#F8FAFC",
    "select": "#CFE2F5", "select_text": "#0B1E33", "muted_row": "#8A94A1",
}
DARK = {
    "bg": "#14181D", "surface": "#1D232A", "text": "#E6EAEF", "muted": "#9AA6B4", "border": "#334050",
    "nav": "#0D2440", "nav_text": "#DCE8F5", "nav_sel": PRIMARY, "nav_sel_text": "#FFFFFF", "row_alt": "#20272F",
    "select": "#164A7A", "select_text": "#FFFFFF", "muted_row": "#7D8896",
}


def is_dark(mode: str) -> bool:
    if mode == "DARK":
        return True
    if mode == "LIGHT":
        return False
    try:
        from PySide6.QtCore import Qt

        return QApplication.styleHints().colorScheme() == Qt.ColorScheme.Dark
    except Exception:  # noqa: BLE001 - Qt lama tidak punya colorScheme
        return False


def colors(mode: str) -> dict:
    return DARK if is_dark(mode) else LIGHT


def apply(app: QApplication, mode: str) -> None:
    c = colors(mode)
    app.setStyle("Fusion")
    pal = QPalette()
    pal.setColor(QPalette.ColorRole.Window, QColor(c["bg"]))
    pal.setColor(QPalette.ColorRole.Base, QColor(c["surface"]))
    pal.setColor(QPalette.ColorRole.AlternateBase, QColor(c["row_alt"]))
    pal.setColor(QPalette.ColorRole.Text, QColor(c["text"]))
    pal.setColor(QPalette.ColorRole.WindowText, QColor(c["text"]))
    pal.setColor(QPalette.ColorRole.Button, QColor(c["surface"]))
    pal.setColor(QPalette.ColorRole.ButtonText, QColor(c["text"]))
    pal.setColor(QPalette.ColorRole.Highlight, QColor(c["select"]))
    pal.setColor(QPalette.ColorRole.HighlightedText, QColor(c["select_text"]))
    pal.setColor(QPalette.ColorRole.PlaceholderText, QColor(c["muted"]))
    pal.setColor(QPalette.ColorRole.ToolTipBase, QColor(c["surface"]))
    pal.setColor(QPalette.ColorRole.ToolTipText, QColor(c["text"]))
    app.setPalette(pal)
    app.setStyleSheet(f"""
        * {{ font-family: "Segoe UI", "Noto Sans", sans-serif; font-size: 10pt; }}
        QMainWindow, QDialog {{ background: {c['bg']}; }}
        QLabel#title {{ font-size: 16pt; font-weight: 600; }}
        QLabel#subtitle {{ color: {c['muted']}; }}
        QLabel#muted {{ color: {c['muted']}; }}
        QLabel#error {{ color: {ERROR}; }}
        QLabel#banner {{ background: #FDECEA; color: {ERROR}; border: 1px solid #F5C2BE; border-radius: 6px; padding: 8px; }}
        QLabel#info {{ background: {PRIMARY_LIGHT}; color: #0B1E33; border-radius: 6px; padding: 8px; }}
        QFrame#card {{ background: {c['surface']}; border: 1px solid {c['border']}; border-radius: 10px; }}
        QListWidget#nav {{ background: {c['nav']}; color: {c['nav_text']}; border: none; padding: 8px 6px; outline: 0; }}
        QListWidget#nav::item {{ padding: 10px 12px; border-radius: 8px; margin: 2px 0; }}
        QListWidget#nav::item:selected {{ background: {c['nav_sel']}; color: {c['nav_sel_text']}; font-weight: 600; }}
        QListWidget#nav::item:hover:!selected {{ background: rgba(255,255,255,0.12); }}
        QLabel#navHeader {{ background: {c['nav']}; color: #FFFFFF; font-size: 13pt; font-weight: 700; padding: 16px 14px 4px 14px; }}
        QLabel#navUser {{ background: {c['nav']}; color: {c['nav_text']}; padding: 0 14px 12px 14px; }}
        QPushButton {{ background: {c['surface']}; border: 1px solid {c['border']}; border-radius: 6px; padding: 6px 14px; }}
        QPushButton:hover {{ border-color: {PRIMARY}; }}
        QPushButton:disabled {{ color: {c['muted']}; }}
        QPushButton#primary {{ background: {PRIMARY}; color: #FFFFFF; border: 1px solid {PRIMARY}; font-weight: 600; }}
        QPushButton#primary:hover {{ background: {PRIMARY_DARK}; }}
        QPushButton#primary:disabled {{ background: #8FB3D6; border-color: #8FB3D6; }}
        QPushButton#danger {{ color: {ERROR}; border-color: #E7B4AF; }}
        QLineEdit, QComboBox, QPlainTextEdit, QSpinBox, QDoubleSpinBox {{
            background: {c['surface']}; border: 1px solid {c['border']}; border-radius: 6px; padding: 5px 8px; }}
        QLineEdit:focus, QComboBox:focus, QPlainTextEdit:focus {{ border: 1px solid {PRIMARY}; }}
        QLineEdit[invalid="true"], QComboBox[invalid="true"] {{ border: 1px solid {ERROR}; }}
        QTableView {{ background: {c['surface']}; border: 1px solid {c['border']}; border-radius: 6px;
            gridline-color: {c['border']}; alternate-background-color: {c['row_alt']}; }}
        QHeaderView::section {{ background: {c['bg']}; border: none; border-bottom: 1px solid {c['border']};
            padding: 6px; font-weight: 600; }}
        QTabWidget::pane {{ border: 1px solid {c['border']}; border-radius: 6px; background: {c['surface']}; }}
        QTabBar::tab {{ padding: 7px 14px; }}
        QTabBar::tab:selected {{ color: {PRIMARY}; border-bottom: 2px solid {PRIMARY}; font-weight: 600; }}
        QGroupBox {{ font-weight: 600; border: 1px solid {c['border']}; border-radius: 8px; margin-top: 12px; padding-top: 8px; }}
        QGroupBox::title {{ subcontrol-origin: margin; left: 10px; padding: 0 4px; }}
    """)
