"""Audit log: siapa mengubah apa dan kapan."""

from __future__ import annotations

from PySide6.QtCore import QTimer
from PySide6.QtWidgets import QHBoxLayout, QLineEdit, QVBoxLayout, QWidget

from ..core import fmt
from ..core.audit import changes_text
from ..core.models import AuditAction, AuditEntityType
from .widgets import Combo, Table, button, label

PAGE = 500


class AuditPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        self.limit = PAGE
        v = QVBoxLayout(self)
        v.addWidget(label("Audit log", "title"))
        bar = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Cari ringkasan atau nama pengguna…")
        self.search.setClearButtonEnabled(True)
        self.type = Combo("Semua jenis")
        self.type.set_items([(t.label, t.name) for t in AuditEntityType])
        bar.addWidget(self.search, 1)
        bar.addWidget(self.type)
        v.addLayout(bar)
        self.table = Table(["Waktu", "Oleh", "Jenis", "Aksi", "Ringkasan", "Perubahan (lama → baru)"])
        self.table.setWordWrap(True)
        v.addWidget(self.table, 1)
        more_row = QHBoxLayout()
        self.info = label("", "muted")
        self.more = button("Muat lebih banyak")
        self.more.clicked.connect(self._more)
        more_row.addWidget(self.info)
        more_row.addStretch(1)
        more_row.addWidget(self.more)
        v.addLayout(more_row)
        self._t = QTimer(self, singleShot=True, interval=250)
        self._t.timeout.connect(self._reset)
        self.search.textChanged.connect(lambda _: self._t.start())
        self.type.currentIndexChanged.connect(lambda _: self._reset())

    def on_show(self) -> None:
        self._reset()

    def _reset(self) -> None:
        self.limit = PAGE
        self.refresh()

    def _more(self) -> None:
        self.limit += PAGE
        self.refresh()

    def refresh(self) -> None:
        t = AuditEntityType[self.type.value()] if self.type.value() else None
        rows = self.ctx.audit.search(t, self.search.text(), limit=self.limit)
        self.table.set_rows([
            (r["id"], [fmt.date_time(r["timestamp"]), r["userName"], AuditEntityType[r["entityType"]].label,
                       AuditAction[r["action"]].label, r["summary"], changes_text(r["changesJson"])])
            for r in rows
        ])
        for i, w in enumerate((130, 140, 90, 80, 340)):
            self.table.setColumnWidth(i, w)
        self.table.resizeRowsToContents()
        self.info.setText(f"Menampilkan {len(rows)} catatan terbaru")
        self.more.setVisible(len(rows) >= self.limit)
