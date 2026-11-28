"""Modul Backup & restore (khusus Admin GA): backup terenkripsi .gabak, restore, dan pindah data HP ↔ laptop."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QGridLayout, QHBoxLayout, QLabel, QVBoxLayout, QWidget

from ..core.models import AppError
from . import backup_flow, icons, theme
from .widgets import button, card, confirm_password, error, label, page_header, page_layout, primary


def _step(n: str, title: str, text: str) -> QWidget:
    w = QWidget()
    h = QHBoxLayout(w)
    h.setContentsMargins(0, 0, 0, 0)
    h.setSpacing(12)
    num = label(n, "avatar", wrap=False)
    num.setFixedSize(30, 30)
    num.setAlignment(Qt.AlignmentFlag.AlignCenter)
    h.addWidget(num, alignment=Qt.AlignmentFlag.AlignTop)
    col = QVBoxLayout()
    col.setSpacing(2)
    t = label(title, None, wrap=False)
    t.setStyleSheet("font-weight: 600;")
    col.addWidget(t)
    col.addWidget(label(text, "muted"))
    h.addLayout(col, 1)
    return w


class BackupPage(QWidget):
    # Dipancarkan setelah restore berhasil: aplikasi kembali ke layar masuk.
    restored = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = page_layout(self)
        v.addLayout(page_header("Backup & restore", label("File backup terenkripsi (.gabak) — formatnya sama dengan aplikasi Android",
                                                          "subtitle", wrap=False)))
        grid = QGridLayout()
        grid.setSpacing(16)

        b = card("v", (22, 20, 22, 20), 12)
        head = QHBoxLayout()
        ic = QLabel()
        ic.setPixmap(icons.icon("backup", theme.PRIMARY, 30, 1.8).pixmap(30, 30))
        head.addWidget(ic)
        head.addWidget(label("Buat backup", "h2", wrap=False), 1)
        b.layout().addLayout(head)
        b.layout().addWidget(label("Seluruh data (aset, master, stok, servis, pengguna, riwayat) dan foto disimpan dalam satu file yang "
                                   "dienkripsi AES-256 dengan passphrase pilihan Anda. Passphrase tidak disimpan di mana pun.", "muted"))
        self.backup_btn = primary("Buat backup…", "backup")
        self.backup_btn.clicked.connect(self._backup)
        b.layout().addWidget(self.backup_btn, alignment=Qt.AlignmentFlag.AlignLeft)
        b.layout().addStretch(1)
        grid.addWidget(b, 0, 0)

        r = card("v", (22, 20, 22, 20), 12)
        head = QHBoxLayout()
        ic = QLabel()
        ic.setPixmap(icons.icon("unggah", theme.ERROR, 30, 1.8).pixmap(30, 30))
        head.addWidget(ic)
        head.addWidget(label("Restore dari backup", "h2", wrap=False), 1)
        r.layout().addLayout(head)
        r.layout().addWidget(label("Mengganti SELURUH data di laptop ini dengan isi file backup (dari HP atau laptop lain). "
                                   "Perubahan yang belum dibackup akan hilang. Diperlukan konfirmasi kata sandi.", "muted"))
        self.restore_btn = button("Restore dari backup…", danger=True, icon_name="unggah")
        self.restore_btn.clicked.connect(self._restore)
        r.layout().addWidget(self.restore_btn, alignment=Qt.AlignmentFlag.AlignLeft)
        r.layout().addStretch(1)
        grid.addWidget(r, 0, 1)

        how = card("v", (22, 20, 22, 20), 14)
        how.layout().addWidget(label("Pindah data HP ↔ laptop", "h2", wrap=False))
        how.layout().addWidget(_step("1", "Buat backup di perangkat asal",
                                     "Di HP: menu Lainnya → Backup & restore → Buat backup. Di laptop: tombol Buat backup di atas."))
        how.layout().addWidget(_step("2", "Salin file .gabak ke perangkat tujuan", "Lewat kabel USB, flashdisk, email, atau Google Drive."))
        how.layout().addWidget(_step("3", "Restore di perangkat tujuan",
                                     "Restore mengganti seluruh data. Pakai satu perangkat saja untuk mengubah data pada satu waktu."))
        grid.addWidget(how, 1, 0, 1, 2)
        grid.setColumnStretch(0, 1)
        grid.setColumnStretch(1, 1)
        v.addLayout(grid)
        self.location = label("", "small")
        self.location.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        v.addWidget(self.location)
        v.addStretch(1)

    def on_show(self) -> None:
        self.location.setText(f"Folder data di laptop ini: {self.ctx.data_dir}")

    def _backup(self) -> None:
        try:
            backup_flow.make_backup(self, self.ctx)
        except AppError as e:
            error(self, e.message)

    def _restore(self) -> None:
        if not (self.ctx.session.role and self.ctx.session.role.can_manage_master):
            error(self, "Hanya Admin GA yang dapat me-restore data")
            return
        if not confirm_password(self, self.ctx, "Restore akan mengganti seluruh data di laptop ini. Masukkan kata sandi Anda untuk melanjutkan."):
            return
        if backup_flow.restore(self, self.ctx):
            self.restored.emit()
