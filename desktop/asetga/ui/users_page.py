"""Pengguna & peran (khusus Admin GA)."""

from __future__ import annotations

from PySide6.QtWidgets import QCheckBox, QDialog, QHBoxLayout, QLineEdit, QWidget

from ..core.models import Role
from ..core.users import FIELD_FULL_NAME, FIELD_PASSWORD, FIELD_USERNAME
from .widgets import Combo, FormDialog, Table, button, card, confirm, confirm_password, label, notify, page_header, page_layout, primary, run_safely


def _pw() -> QLineEdit:
    e = QLineEdit()
    e.setEchoMode(QLineEdit.EchoMode.Password)
    return e


class UserDialog(FormDialog):
    def __init__(self, parent, ctx, user=None):
        super().__init__(parent, "Ubah pengguna" if user else "Tambah pengguna")
        self.ctx, self.user = ctx, user
        f = self.form
        self.username = f.add("Username", QLineEdit(user.username if user else ""), FIELD_USERNAME)
        self.username.setEnabled(user is None)
        self.full_name = f.add("Nama lengkap", QLineEdit(user.full_name if user else ""), FIELD_FULL_NAME)
        self.role = Combo()
        self.role.set_items([(r.label, r.name) for r in Role])
        self.role.set_value(user.role.name if user else Role.STAFF.name)
        f.add("Peran", self.role, hint="Admin GA: semua · Staf GA: tambah/ubah data · Viewer: hanya melihat")
        if user is None:
            self.pw = f.add("Kata sandi awal", _pw(), FIELD_PASSWORD, "Minimal 8 karakter, berisi huruf dan angka")
        else:
            self.active = QCheckBox("Akun aktif (bisa masuk)")
            self.active.setChecked(user.is_active)
            f.add("Status", self.active)

    def save(self) -> None:
        role = Role[self.role.value()]
        if self.user is None:
            self.ctx.users.create(self.username.text(), self.full_name.text(), role, self.pw.text())
        else:
            self.ctx.users.update(self.user.id, self.full_name.text(), role, self.active.isChecked())


class ResetPasswordDialog(FormDialog):
    def __init__(self, parent, ctx, user, title="Atur ulang kata sandi"):
        super().__init__(parent, title)
        self.ctx, self.user = ctx, user
        self.form.add("Pengguna", label(f"{user.full_name} (@{user.username})"))
        self.pw = self.form.add("Kata sandi baru", _pw(), FIELD_PASSWORD, "Minimal 8 karakter, berisi huruf dan angka")
        self.pw2 = self.form.add("Ulangi kata sandi", _pw(), "confirm")

    def save(self) -> None:
        from ..core.models import AppError

        if self.pw.text() != self.pw2.text():
            raise AppError("Periksa kembali isian", {"confirm": "Kata sandi tidak sama"})
        self.ctx.users.change_password(self.user.id, self.pw.text())


class UsersPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = page_layout(self)
        add = primary("Tambah pengguna", "tambah")
        add.clicked.connect(self._add)
        edit = button("Ubah", icon_name="ubah")
        edit.clicked.connect(self._edit)
        reset = button("Atur ulang kata sandi", icon_name="kunci")
        reset.clicked.connect(self._reset)
        delete = button("Hapus", danger=True, icon_name="hapus")
        delete.clicked.connect(self._delete)
        self.count = label("", "subtitle", wrap=False)
        v.addLayout(page_header("Pengguna", self.count, add))
        box = card("v")
        bar = QHBoxLayout()
        bar.setContentsMargins(16, 12, 16, 10)
        bar.addWidget(label("Admin GA: semua · Staf GA: tambah/ubah data · Viewer: hanya melihat", "small", wrap=False))
        bar.addStretch(1)
        for b in (delete, reset, edit):
            bar.addWidget(b)
        box.layout().addLayout(bar)
        self.table = Table(["Nama", "Username", "Peran", "Status"], chips={2, 3})
        self.table.activated_row.connect(lambda _: self._edit())
        box.layout().addWidget(self.table, 1)
        v.addWidget(box, 1)

    def on_show(self) -> None:
        self.refresh()

    def refresh(self) -> None:
        self.table.set_rows([(u, [u.full_name, "@" + u.username, u.role.label, "Aktif" if u.is_active else "Nonaktif"])
                             for u in self.ctx.users.all()], muted=lambda u: not u.is_active)
        self.count.setText(f"{self.table.model_.rowCount()} pengguna")
        self.table.setColumnWidth(0, 240)
        self.table.setColumnWidth(1, 160)
        self.table.setColumnWidth(2, 120)

    def _add(self) -> None:
        if UserDialog(self, self.ctx).exec() == QDialog.DialogCode.Accepted:
            self.refresh()
            notify(self, "Pengguna ditambahkan")

    def _edit(self) -> None:
        u = self.table.current_data()
        if u and UserDialog(self, self.ctx, u).exec() == QDialog.DialogCode.Accepted:
            self.refresh()

    def _reset(self) -> None:
        u = self.table.current_data()
        if u and ResetPasswordDialog(self, self.ctx, u).exec() == QDialog.DialogCode.Accepted:
            notify(self, f"Kata sandi {u.full_name} sudah diubah")

    def _delete(self) -> None:
        u = self.table.current_data()
        if u is None:
            return
        if not confirm(self, f"Hapus pengguna {u.full_name} (@{u.username}) secara permanen?", "Hapus pengguna", yes="Hapus", danger=True):
            return
        if not confirm_password(self, self.ctx, "Menghapus pengguna adalah tindakan berbahaya. Masukkan kata sandi Anda untuk melanjutkan."):
            return
        if run_safely(self, lambda: self.ctx.users.delete(u.id) or True):
            self.refresh()
