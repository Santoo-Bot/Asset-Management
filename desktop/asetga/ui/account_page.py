"""Akun & tampilan: ganti kata sandi sendiri, tema, kunci, keluar."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QDialog, QGroupBox, QHBoxLayout, QVBoxLayout, QWidget

from .. import VERSION
from .users_page import ResetPasswordDialog
from .widgets import Combo, button, info, label

THEMES = [("Ikuti sistem", "SYSTEM"), ("Terang", "LIGHT"), ("Gelap", "DARK")]


class AccountPage(QWidget):
    theme_changed = Signal(str)
    lock_requested = Signal()
    logout_requested = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = QVBoxLayout(self)
        v.addWidget(label("Akun & tampilan", "title"))
        g = QGroupBox("Akun")
        gl = QVBoxLayout(g)
        self.who = label("")
        gl.addWidget(self.who)
        row = QHBoxLayout()
        pw = button("Ganti kata sandi")
        pw.clicked.connect(self._change_password)
        lock = button("Kunci sekarang")
        lock.clicked.connect(self.lock_requested.emit)
        out = button("Keluar", danger=True)
        out.clicked.connect(self.logout_requested.emit)
        for b in (pw, lock, out):
            row.addWidget(b)
        row.addStretch(1)
        gl.addLayout(row)
        gl.addWidget(label("Aplikasi terkunci otomatis setelah 5 menit tidak dipakai.", "muted"))
        v.addWidget(g)

        t = QGroupBox("Tampilan")
        tl = QHBoxLayout(t)
        tl.addWidget(label("Tema", "muted", wrap=False))
        self.theme = Combo()
        self.theme.set_items(THEMES)
        self.theme.currentIndexChanged.connect(lambda _: self._theme())
        tl.addWidget(self.theme)
        tl.addStretch(1)
        v.addWidget(t)
        v.addWidget(label(f"Aset GA Desktop · build {VERSION}", "muted"))
        v.addStretch(1)

    def on_show(self) -> None:
        u = self.ctx.session.user
        self.who.setText(f"{u.full_name} (@{u.username}) · {u.role.label}" if u else "")
        self.theme.blockSignals(True)
        self.theme.set_value(self.ctx.pref("theme_mode", "SYSTEM"))
        self.theme.blockSignals(False)

    def _theme(self) -> None:
        mode = self.theme.value() or "SYSTEM"
        self.ctx.set_pref("theme_mode", mode)
        self.theme_changed.emit(mode)

    def _change_password(self) -> None:
        from .widgets import confirm_password

        u = self.ctx.session.user
        if u is None or not confirm_password(self, self.ctx, "Masukkan kata sandi Anda saat ini."):
            return
        if ResetPasswordDialog(self, self.ctx, u, "Ganti kata sandi").exec() == QDialog.DialogCode.Accepted:
            info(self, "Kata sandi sudah diubah.")
