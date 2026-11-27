"""Alur backup & restore yang dipakai di layar awal dan di menu Data."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QApplication, QDialog, QDialogButtonBox, QFileDialog, QLineEdit, QVBoxLayout

from ..core import backup, security
from ..core.models import AppError, AuditAction, AuditEntityType
from .widgets import Banner, confirm, error, info, label, primary

FILTER = "Backup Aset GA (*.gabak);;Semua file (*)"


class PassphraseDialog(QDialog):
    def __init__(self, parent, creating: bool):
        super().__init__(parent)
        self.creating = creating
        self.setWindowTitle("Passphrase backup")
        self.setMinimumWidth(420)
        lay = QVBoxLayout(self)
        if creating:
            lay.addWidget(label("Backup dienkripsi dengan passphrase ini. Passphrase tidak disimpan di mana pun: "
                                "bila lupa, backup tidak bisa dibuka. Simpan di tempat aman."))
        else:
            lay.addWidget(label("Masukkan passphrase yang dipakai saat backup dibuat (di HP atau laptop)."))
        self.banner = Banner()
        lay.addWidget(self.banner)
        self.p1 = QLineEdit()
        self.p1.setEchoMode(QLineEdit.EchoMode.Password)
        self.p1.setPlaceholderText(f"Passphrase (min. {security.MIN_PASSPHRASE} karakter)")
        lay.addWidget(self.p1)
        self.p2 = QLineEdit()
        self.p2.setEchoMode(QLineEdit.EchoMode.Password)
        self.p2.setPlaceholderText("Ulangi passphrase")
        if creating:
            lay.addWidget(self.p2)
        bb = QDialogButtonBox()
        bb.addButton(primary("Lanjutkan"), QDialogButtonBox.ButtonRole.AcceptRole)
        bb.addButton("Batal", QDialogButtonBox.ButtonRole.RejectRole)
        bb.accepted.connect(self._ok)
        bb.rejected.connect(self.reject)
        lay.addWidget(bb)

    def _ok(self) -> None:
        if len(self.p1.text()) < security.MIN_PASSPHRASE:
            self.banner.show_text(f"Passphrase minimal {security.MIN_PASSPHRASE} karakter")
        elif self.creating and self.p1.text() != self.p2.text():
            self.banner.show_text("Passphrase tidak sama")
        else:
            self.accept()

    def value(self) -> str:
        return self.p1.text()


def _busy(fn):
    QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
    try:
        return fn()
    finally:
        QApplication.restoreOverrideCursor()


def make_backup(parent, ctx) -> None:
    ctx.session.require(lambda r: r.can_manage_master, "Hanya Admin GA yang dapat membuat backup")
    path, _ = QFileDialog.getSaveFileName(parent, "Simpan backup", str(Path(ctx.last_dir()) / backup.suggested_file_name()), FILTER)
    if not path:
        return
    if not path.lower().endswith(".gabak"):
        path += ".gabak"
    dlg = PassphraseDialog(parent, creating=True)
    if dlg.exec() != QDialog.DialogCode.Accepted:
        return
    ctx.remember_dir(path)
    try:
        size = _busy(lambda: backup.create_backup(ctx.db, ctx.data_dir, path, dlg.value(), ctx.audit))
    except AppError as e:
        error(parent, e.message)
        return
    info(parent, f"Backup tersimpan ({size / 1024 / 1024:.1f} MB):\n{path}\n\n"
                 "File ini bisa di-restore di aplikasi Android (menu Backup & restore) atau di laptop lain.")


def restore(parent, ctx, first_run: bool = False) -> bool:
    """Pulihkan dari file backup. Mengembalikan True bila data sudah diganti (pengguna harus masuk ulang)."""
    path, _ = QFileDialog.getOpenFileName(parent, "Pilih file backup", ctx.last_dir(), FILTER)
    if not path:
        return False
    ctx.remember_dir(path)
    dlg = PassphraseDialog(parent, creating=False)
    if dlg.exec() != QDialog.DialogCode.Accepted:
        return False
    try:
        prepared = _busy(lambda: backup.prepare_restore(path, dlg.value(), ctx.db.schema))
    except AppError as e:
        error(parent, e.message)
        return False
    users = prepared.user_count()
    if not first_run and not confirm(
        parent,
        "Seluruh data di laptop ini akan DIGANTI dengan isi backup (aset, master data, pengguna, foto, riwayat).\n\n"
        "Perubahan di laptop yang belum dibackup akan hilang. Lanjutkan?",
        "Restore data", yes="Ganti data", danger=True,
    ):
        prepared.cleanup()
        return False
    actor = ctx.session.user
    try:
        _busy(lambda: backup.apply_restore(prepared, ctx.data_dir, ctx.db))
    except AppError as e:
        ctx.open_db()  # data lama sudah dikembalikan
        error(parent, e.message)
        return False
    ctx.open_db()
    # Catat di database hasil restore (atas nama pengguna yang melakukannya bila ada).
    ctx.session.user = actor
    ctx.audit.log(AuditEntityType.SYSTEM, "restore", AuditAction.RESTORE_BACKUP, f"Data dipulihkan dari backup di laptop ({Path(path).name})")
    ctx.session.logout()
    info(parent, f"Data berhasil dipulihkan ({users} pengguna). Silakan masuk dengan akun dari backup tersebut.")
    return True
