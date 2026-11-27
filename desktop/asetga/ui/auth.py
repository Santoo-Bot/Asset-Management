"""Layar awal: buat Admin pertama / pulihkan backup, masuk, dan kunci aplikasi."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QHBoxLayout, QLineEdit, QVBoxLayout, QWidget

from ..core.models import AppError, Role
from ..core.users import FIELD_FULL_NAME, FIELD_PASSWORD, FIELD_USERNAME
from . import backup_flow
from .widgets import Banner, Form, button, card, label, primary


def _centered(inner: QWidget) -> QWidget:
    outer = QWidget()
    h = QHBoxLayout(outer)
    h.addStretch(1)
    v = QVBoxLayout()
    v.addStretch(1)
    v.addWidget(inner)
    v.addStretch(2)
    h.addLayout(v)
    h.addStretch(1)
    return outer


def _password() -> QLineEdit:
    e = QLineEdit()
    e.setEchoMode(QLineEdit.EchoMode.Password)
    return e


class SetupPage(QWidget):
    """Belum ada pengguna: buat Admin GA pertama, atau pulihkan data dari backup HP."""

    done = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        box = card()
        box.setFixedWidth(480)
        v = QVBoxLayout(box)
        v.setContentsMargins(28, 24, 28, 24)
        v.addWidget(label("Selamat datang di Aset GA", "title"))
        v.addWidget(label("Data tersimpan di laptop ini saja (folder data di samping aplikasi). "
                          "Pilih salah satu untuk memulai.", "subtitle"))
        v.addSpacing(8)
        v.addWidget(label("Sudah memakai aplikasi di HP?", None))
        restore = button("Pulihkan dari file backup HP (.gabak)")
        restore.clicked.connect(self._restore)
        v.addWidget(restore)
        v.addSpacing(12)
        v.addWidget(label("Atau mulai baru dengan membuat akun Admin GA:"))
        self.banner = Banner()
        v.addWidget(self.banner)
        self.form = Form()
        self.username = self.form.add("Username", QLineEdit(), FIELD_USERNAME)
        self.full_name = self.form.add("Nama lengkap", QLineEdit(), FIELD_FULL_NAME)
        self.pw = self.form.add("Kata sandi", _password(), FIELD_PASSWORD, "Minimal 8 karakter, berisi huruf dan angka")
        self.pw2 = self.form.add("Ulangi kata sandi", _password(), "confirm")
        v.addWidget(self.form)
        create = primary("Buat akun Admin")
        create.clicked.connect(self._create)
        v.addWidget(create, alignment=Qt.AlignmentFlag.AlignRight)
        QVBoxLayout(self).addWidget(_centered(box))

    def _create(self) -> None:
        if self.pw.text() != self.pw2.text():
            self.form.set_errors({"confirm": "Kata sandi tidak sama"})
            return
        try:
            self.ctx.users.create(self.username.text(), self.full_name.text(), Role.ADMIN, self.pw.text())
        except AppError as e:
            self.form.set_errors(e.fields)
            self.banner.show_text(e.message)
            return
        self.done.emit()

    def _restore(self) -> None:
        if backup_flow.restore(self, self.ctx, first_run=True):
            self.done.emit()


class LoginPage(QWidget):
    done = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        box = card()
        box.setFixedWidth(400)
        v = QVBoxLayout(box)
        v.setContentsMargins(28, 24, 28, 24)
        v.addWidget(label("Aset GA", "title"))
        v.addWidget(label("Sistem Manajemen Aset & Inventaris", "subtitle"))
        v.addSpacing(8)
        self.banner = Banner()
        v.addWidget(self.banner)
        self.username = QLineEdit()
        self.username.setPlaceholderText("Username")
        self.pw = _password()
        self.pw.setPlaceholderText("Kata sandi")
        self.pw.returnPressed.connect(self._login)
        self.username.returnPressed.connect(self.pw.setFocus)
        v.addWidget(self.username)
        v.addWidget(self.pw)
        b = primary("Masuk")
        b.clicked.connect(self._login)
        v.addWidget(b)
        QVBoxLayout(self).addWidget(_centered(box))

    def reset(self) -> None:
        self.banner.show_text(None)
        self.pw.clear()
        last = self.ctx.pref("last_username")
        self.username.setText(last)
        (self.pw if last else self.username).setFocus()

    def _login(self) -> None:
        try:
            user = self.ctx.users.authenticate(self.username.text(), self.pw.text())
        except AppError as e:
            self.pw.clear()
            self.banner.show_text(e.message)
            return
        self.ctx.session.login(user)
        self.ctx.set_pref("last_username", user.username)
        self.done.emit()


class LockPage(QWidget):
    """Aplikasi terkunci setelah tidak dipakai; buka dengan kata sandi."""

    unlocked = Signal()
    switch_user = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        box = card()
        box.setFixedWidth(400)
        v = QVBoxLayout(box)
        v.setContentsMargins(28, 24, 28, 24)
        v.addWidget(label("Aplikasi terkunci", "title"))
        self.who = label("", "subtitle")
        v.addWidget(self.who)
        self.banner = Banner()
        v.addWidget(self.banner)
        self.pw = _password()
        self.pw.setPlaceholderText("Kata sandi")
        self.pw.returnPressed.connect(self._unlock)
        v.addWidget(self.pw)
        row = QHBoxLayout()
        other = button("Ganti pengguna")
        other.clicked.connect(self.switch_user.emit)
        ok = primary("Buka kunci")
        ok.clicked.connect(self._unlock)
        row.addWidget(other)
        row.addStretch(1)
        row.addWidget(ok)
        v.addLayout(row)
        QVBoxLayout(self).addWidget(_centered(box))

    def reset(self) -> None:
        u = self.ctx.session.user
        self.who.setText(f"{u.full_name} (@{u.username})" if u else "")
        self.banner.show_text(None)
        self.pw.clear()
        self.pw.setFocus()

    def _unlock(self) -> None:
        u = self.ctx.session.user
        try:
            user = self.ctx.users.authenticate(u.username, self.pw.text())
        except AppError as e:
            self.pw.clear()
            self.banner.show_text(e.message)
            return
        self.ctx.session.login(user)
        self.unlocked.emit()
