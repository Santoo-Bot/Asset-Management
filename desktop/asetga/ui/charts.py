"""Grafik sederhana yang digambar sendiri (tanpa pustaka tambahan) agar seragam dengan tema terang/gelap."""

from __future__ import annotations

from PySide6.QtCore import QRectF, QSize, Qt
from PySide6.QtGui import QColor, QFont, QFontMetrics, QPainter, QPen
from PySide6.QtWidgets import QSizePolicy, QWidget

from . import theme

# Palet kategorikal (biru BCA lebih dulu); dibedakan juga lewat terang-gelapnya.
PALETTE = ["#0060AF", "#F28C28", "#2BA673", "#7B5CC4", "#D64550", "#14A3C7", "#9AA6B4", "#B8860B"]


class HBarChart(QWidget):
    """Batang mendatar: label | batang | nilai. [rows] = (label, nilai angka, teks nilai)."""

    ROW = 30

    def __init__(self, max_rows: int = 8):
        super().__init__()
        self.max_rows = max_rows
        self.rows: list[tuple[str, float, str]] = []
        self.color = theme.PRIMARY
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

    def set_rows(self, rows: list[tuple[str, float, str]], other_label: str = "Lainnya") -> None:
        rows = list(rows)
        if len(rows) > self.max_rows:
            rest = rows[self.max_rows - 1:]
            total = sum(r[1] for r in rest)
            rows = rows[:self.max_rows - 1] + [(f"{other_label} ({len(rest)})", total, self._fmt_rest(rest, total))]
        self.rows = rows
        self.setFixedHeight(max(1, len(rows)) * self.ROW + 4)
        self.update()

    @staticmethod
    def _fmt_rest(rest, total) -> str:
        # Teks nilai "Lainnya" mengikuti format baris pertama (angka biasa atau Rupiah).
        from ..core import fmt

        sample = rest[0][2]
        return fmt.rupiah_short(int(total)) if sample.startswith("Rp") else fmt.number(int(total))

    def sizeHint(self) -> QSize:  # noqa: N802 - nama dari Qt
        return QSize(320, max(1, len(self.rows)) * self.ROW + 4)

    def paintEvent(self, _):  # noqa: N802 - nama dari Qt
        k = theme.c()
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        if not self.rows:
            p.setPen(QColor(k["muted"]))
            p.drawText(self.rect(), int(Qt.AlignmentFlag.AlignCenter), "Belum ada data")
            return
        fm = QFontMetrics(self.font())
        w = self.width()
        label_w = min(int(w * 0.38), max(fm.horizontalAdvance(r[0]) for r in self.rows) + 8)
        value_w = max(fm.horizontalAdvance(r[2]) for r in self.rows) + 10
        bar_x = label_w + 6
        bar_w = max(20, w - bar_x - value_w)
        top = max(r[1] for r in self.rows) or 1
        for i, (lab, val, txt) in enumerate(self.rows):
            y = i * self.ROW + 2
            p.setPen(QColor(k["text"]))
            p.drawText(QRectF(0, y, label_w, self.ROW - 4), int(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft),
                       fm.elidedText(lab, Qt.TextElideMode.ElideRight, label_w))
            track = QRectF(bar_x, y + 8, bar_w, self.ROW - 20)
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor(k["surface2"]))
            p.drawRoundedRect(track, 5, 5)
            fill = QRectF(bar_x, y + 8, max(4.0, bar_w * val / top), self.ROW - 20)
            p.setBrush(QColor(self.color))
            p.drawRoundedRect(fill, 5, 5)
            p.setPen(QColor(k["text"]))
            f = QFont(self.font())
            f.setWeight(QFont.Weight.DemiBold)
            p.setFont(f)
            p.drawText(QRectF(w - value_w, y, value_w, self.ROW - 4), int(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignRight), txt)
            p.setFont(self.font())


class Donut(QWidget):
    """Donat dengan legenda di kanan. [rows] = (label, nilai, warna)."""

    def __init__(self, center_label: str = "aset"):
        super().__init__()
        self.rows: list[tuple[str, float, str]] = []
        self.center_label = center_label
        self.setMinimumHeight(190)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

    def set_rows(self, rows: list[tuple[str, float, str]]) -> None:
        self.rows = [r for r in rows if r[1] > 0]
        self.setFixedHeight(max(190, len(self.rows) * 24 + 20))
        self.update()

    def paintEvent(self, _):  # noqa: N802 - nama dari Qt
        k = theme.c()
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        total = sum(r[1] for r in self.rows)
        size = min(170, self.height() - 10)
        rect = QRectF(8, (self.height() - size) / 2, size, size)
        if total <= 0:
            p.setPen(QPen(QColor(k["surface2"]), 22))
            p.drawEllipse(rect.adjusted(11, 11, -11, -11))
            p.setPen(QColor(k["muted"]))
            p.drawText(rect, int(Qt.AlignmentFlag.AlignCenter), "Belum ada data")
            return
        start = 90 * 16
        for _, val, color in self.rows:
            span = -int(round(360 * 16 * val / total))
            pen = QPen(QColor(color), 22)
            pen.setCapStyle(Qt.PenCapStyle.FlatCap)
            p.setPen(pen)
            p.drawArc(rect.adjusted(11, 11, -11, -11), start, span)
            start += span
        f = QFont(self.font())
        f.setPointSizeF(self.font().pointSizeF() + 6)
        f.setWeight(QFont.Weight.Bold)
        p.setFont(f)
        p.setPen(QColor(k["text"]))
        from ..core import fmt

        p.drawText(rect.adjusted(0, -10, 0, -10), int(Qt.AlignmentFlag.AlignCenter), fmt.number(int(total)))
        p.setFont(self.font())
        p.setPen(QColor(k["muted"]))
        p.drawText(rect.adjusted(0, 26, 0, 26), int(Qt.AlignmentFlag.AlignCenter), self.center_label)
        # Legenda
        fm = QFontMetrics(self.font())
        x = rect.right() + 22
        y = (self.height() - len(self.rows) * 24) / 2
        for lab, val, color in self.rows:
            p.setPen(Qt.PenStyle.NoPen)
            p.setBrush(QColor(color))
            p.drawRoundedRect(QRectF(x, y + 7, 10, 10), 3, 3)
            p.setPen(QColor(k["text"]))
            pct = f"{val * 100 / total:.0f}%"
            text = f"{lab}  {fmt.number(int(val))} ({pct})"
            p.drawText(QRectF(x + 18, y, self.width() - x - 18, 24), int(Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft),
                       fm.elidedText(text, Qt.TextElideMode.ElideRight, int(self.width() - x - 18)))
            y += 24
