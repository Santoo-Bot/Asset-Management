"""Pratinjau import Excel (aset)."""

from __future__ import annotations

from PySide6.QtWidgets import QDialog, QDialogButtonBox, QVBoxLayout

from ..core.excel import ImportPreview
from .widgets import Table, label, primary


class ImportPreviewDialog(QDialog):
    def __init__(self, parent, preview: ImportPreview):
        super().__init__(parent)
        self.setWindowTitle("Pratinjau import aset")
        self.setMinimumSize(820, 520)
        v = QVBoxLayout(self)
        p = preview
        v.addWidget(label(
            f"Baru: {len(p.created)}   ·   Diperbarui: {len(p.updated)}   ·   Tanpa perubahan: {len(p.unchanged)}   ·   "
            f"Bermasalah: {len(p.invalid)}", "info"))
        v.addWidget(label("Baris yang Kode Aset-nya sudah ada akan diperbarui (sel kosong = nilai lama tetap). "
                          "Baris bermasalah tidak disimpan; perbaiki di Excel lalu import ulang.", "muted"))
        t = Table(["Baris", "Aksi", "Kode", "Nama", "Keterangan"])
        t.setWordWrap(True)
        rows = []
        for r in sorted(p.rows, key=lambda x: (x.is_valid, x.row_number)):
            if r.is_valid:
                note = "Tanpa perubahan (dilewati)" if r.unchanged else "Siap disimpan"
            else:
                note = "; ".join(r.errors)
            inp = r.input
            rows.append((r, [str(r.row_number), r.action.value, inp.code if inp else "", inp.name if inp else "", note]))
        t.set_rows(rows, muted=lambda r: r.is_valid and r.unchanged)
        for i, w in enumerate((60, 80, 130, 200)):
            t.setColumnWidth(i, w)
        t.resizeRowsToContents()
        v.addWidget(t, 1)
        bb = QDialogButtonBox()
        n = len(p.to_save)
        self.ok = primary(f"Simpan {n} baris")
        self.ok.setEnabled(n > 0)
        bb.addButton(self.ok, QDialogButtonBox.ButtonRole.AcceptRole)
        bb.addButton("Batal", QDialogButtonBox.ButtonRole.RejectRole)
        bb.accepted.connect(self.accept)
        bb.rejected.connect(self.reject)
        v.addWidget(bb)
