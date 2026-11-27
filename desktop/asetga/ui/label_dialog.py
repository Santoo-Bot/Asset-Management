"""Label QR 50 × 20 mm: pratinjau, simpan PDF / PNG, cetak langsung ke printer."""

from __future__ import annotations

import datetime as _dt
import io
from pathlib import Path

from PySide6.QtCore import QMarginsF, QRectF, QSizeF, Qt
from PySide6.QtGui import QImage, QPageLayout, QPageSize, QPainter, QPixmap
from PySide6.QtWidgets import QApplication, QDialog, QFileDialog, QHBoxLayout, QLabel, QVBoxLayout

from ..core import labels as L
from .widgets import button, error, info, label, primary


def labels_for_assets(ctx, asset_ids: list[str]) -> list[L.LabelData]:
    if not asset_ids:
        return []
    out = []
    for i in range(0, len(asset_ids), 500):
        chunk = asset_ids[i:i + 500]
        rows = ctx.db.q(
            f"SELECT a.code, a.qrCode, c.name AS categoryName FROM assets a JOIN categories c ON c.id = a.categoryId "
            f"WHERE a.id IN ({','.join('?' * len(chunk))})", chunk)
        out += [L.LabelData(r["code"], r["categoryName"], r["qrCode"] or r["code"]) for r in rows]
    return sorted(out, key=lambda x: x.code)


def to_qimage(img) -> QImage:
    buf = io.BytesIO()
    img.save(buf, "PNG")
    q = QImage()
    q.loadFromData(buf.getvalue(), "PNG")
    return q


def print_labels(parent, labels: list[L.LabelData]) -> bool:
    """Cetak lewat dialog printer Windows; ukuran halaman diatur 50 × 20 mm, satu label per halaman."""
    from PySide6.QtPrintSupport import QPrintDialog, QPrinter

    printer = QPrinter(QPrinter.PrinterMode.HighResolution)
    printer.setFullPage(True)
    size = QPageSize(QSizeF(L.LABEL_W_MM, L.LABEL_H_MM), QPageSize.Unit.Millimeter, "Label 50 x 20 mm")
    printer.setPageLayout(QPageLayout(size, QPageLayout.Orientation.Portrait, QMarginsF(0, 0, 0, 0)))
    dlg = QPrintDialog(printer, parent)
    dlg.setWindowTitle(f"Cetak {len(labels)} label")
    if dlg.exec() != QDialog.DialogCode.Accepted:
        return False
    painter = QPainter()
    if not painter.begin(printer):
        error(parent, "Printer tidak dapat dibuka.")
        return False
    QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
    try:
        for i, lb in enumerate(labels):
            if i:
                printer.newPage()
            target = QRectF(printer.pageLayout().fullRectPixels(printer.resolution()))
            painter.drawImage(target, to_qimage(L.render_png(lb, px_per_mm=24)))
    finally:
        painter.end()
        QApplication.restoreOverrideCursor()
    return True


class LabelDialog(QDialog):
    def __init__(self, parent, ctx, labels: list[L.LabelData]):
        super().__init__(parent)
        self.ctx = ctx
        self.labels = labels
        self.page = 0
        self.setWindowTitle("Label QR")
        self.setMinimumWidth(560)
        v = QVBoxLayout(self)
        v.addWidget(label(f"{len(labels)} label · ukuran cetak {L.LABEL_W_MM} × {L.LABEL_H_MM} mm (1 label per halaman)", "subtitle"))
        self.preview = QLabel()
        self.preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.preview.setStyleSheet("background: #E9EDF2; border-radius: 8px; padding: 18px;")
        v.addWidget(self.preview)
        nav = QHBoxLayout()
        self.prev_btn = button("◀")
        self.prev_btn.clicked.connect(lambda: self._show(self.page - 1))
        self.next_btn = button("▶")
        self.next_btn.clicked.connect(lambda: self._show(self.page + 1))
        self.page_label = label("", "muted", wrap=False)
        nav.addStretch(1)
        nav.addWidget(self.prev_btn)
        nav.addWidget(self.page_label)
        nav.addWidget(self.next_btn)
        nav.addStretch(1)
        v.addLayout(nav)
        v.addWidget(label("Untuk printer label, pilih kertas 50 × 20 mm di pengaturan printer. PDF juga bisa dibuka lalu dicetak "
                          "dengan skala 100% (ukuran sebenarnya).", "muted"))
        row = QHBoxLayout()
        pdf = button("Simpan PDF…")
        pdf.clicked.connect(self._pdf)
        png = button("Simpan PNG…")
        png.clicked.connect(self._png)
        prn = primary("Cetak…")
        prn.clicked.connect(self._print)
        close = button("Tutup")
        close.clicked.connect(self.reject)
        row.addWidget(pdf)
        row.addWidget(png)
        row.addStretch(1)
        row.addWidget(close)
        row.addWidget(prn)
        v.addLayout(row)
        self._show(0)

    def _show(self, page: int) -> None:
        if not self.labels:
            return
        self.page = max(0, min(page, len(self.labels) - 1))
        img = to_qimage(L.render_png(self.labels[self.page], px_per_mm=10))
        self.preview.setPixmap(QPixmap.fromImage(img))
        self.page_label.setText(f"{self.page + 1} / {len(self.labels)}")
        self.prev_btn.setEnabled(self.page > 0)
        self.next_btn.setEnabled(self.page < len(self.labels) - 1)

    def _stamp(self) -> str:
        return _dt.datetime.now().strftime("%Y%m%d-%H%M")

    def _pdf(self) -> None:
        name = f"label-{L.safe_file_name(self.labels[0].code) if len(self.labels) == 1 else 'aset'}-{self._stamp()}.pdf"
        path, _ = QFileDialog.getSaveFileName(self, "Simpan label PDF", str(Path(self.ctx.last_dir()) / name), "PDF (*.pdf)")
        if not path:
            return
        self.ctx.remember_dir(path)
        try:
            L.write_pdf(self.labels, path)
        except OSError as e:
            error(self, f"Gagal menyimpan PDF: {e}. Tutup file bila sedang terbuka.")
            return
        info(self, f"PDF tersimpan ({len(self.labels)} halaman):\n{path}")

    def _png(self) -> None:
        folder = QFileDialog.getExistingDirectory(self, "Pilih folder untuk file PNG", self.ctx.last_dir())
        if not folder:
            return
        self.ctx.set_pref("last_dir", folder)
        try:
            files = L.write_pngs(self.labels, folder, self._stamp())
        except OSError as e:
            error(self, f"Gagal menyimpan PNG: {e}")
            return
        info(self, f"{len(files)} file PNG tersimpan di:\n{folder}")

    def _print(self) -> None:
        if print_labels(self, self.labels):
            info(self, f"{len(self.labels)} label dikirim ke printer.")
