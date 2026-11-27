"""Import/Export Excel dan Backup & restore."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QApplication, QDialog, QDialogButtonBox, QFileDialog, QGroupBox, QHBoxLayout, QProgressDialog, QVBoxLayout, QWidget,
)

from ..core import fmt
from ..core.excel import ImportPreview
from ..core.models import AppError
from . import backup_flow
from .widgets import Table, button, confirm_password, error, info, label, primary


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


class DataPage(QWidget):
    # Dipancarkan setelah restore berhasil: aplikasi kembali ke layar masuk.
    restored = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        v = QVBoxLayout(self)
        v.addWidget(label("Data", "title"))

        g1 = QGroupBox("Import & export aset (Excel)")
        l1 = QVBoxLayout(g1)
        l1.addWidget(label("Template dan hasil export memakai kolom yang sama dengan aplikasi Android, jadi file Excel bisa dipakai "
                           "bergantian di HP dan laptop. Import otomatis menambah aset baru dan memperbarui aset yang Kode Aset-nya "
                           "sudah ada.", "muted"))
        r1 = QHBoxLayout()
        self.template_btn = button("Unduh template import")
        self.template_btn.clicked.connect(self._template)
        self.import_btn = primary("Import dari Excel…")
        self.import_btn.clicked.connect(self._import)
        self.export_xlsx = button("Export aset (.xlsx)")
        self.export_xlsx.clicked.connect(lambda: self._export("xlsx"))
        self.export_csv = button("Export aset (.csv)")
        self.export_csv.clicked.connect(lambda: self._export("csv"))
        for b in (self.template_btn, self.import_btn, self.export_xlsx, self.export_csv):
            r1.addWidget(b)
        r1.addStretch(1)
        l1.addLayout(r1)
        v.addWidget(g1)

        self.g2 = QGroupBox("Backup & restore (khusus Admin GA)")
        l2 = QVBoxLayout(self.g2)
        l2.addWidget(label(
            "Data hanya tersimpan di laptop ini. Buat backup terenkripsi secara rutin dan simpan di tempat lain (flashdisk / drive kantor).\n"
            "Pindah data HP ↔ laptop: buat backup di satu perangkat, lalu restore di perangkat lain. Restore MENGGANTI seluruh data, "
            "jadi pakai satu perangkat saja untuk mengubah data pada satu waktu.", "muted"))
        r2 = QHBoxLayout()
        self.backup_btn = primary("Buat backup…")
        self.backup_btn.clicked.connect(self._backup)
        self.restore_btn = button("Restore dari backup…", danger=True)
        self.restore_btn.clicked.connect(self._restore)
        r2.addWidget(self.backup_btn)
        r2.addWidget(self.restore_btn)
        r2.addStretch(1)
        l2.addLayout(r2)
        self.location = label("", "muted")
        self.location.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        l2.addWidget(self.location)
        v.addWidget(self.g2)
        v.addStretch(1)

    def on_show(self) -> None:
        role = self.ctx.session.role
        self.import_btn.setVisible(bool(role and role.can_edit_data))
        self.g2.setVisible(bool(role and role.can_manage_master))
        self.location.setText(f"Folder data: {self.ctx.data_dir}")

    # ------------------------------------------------------------ excel
    def _save_path(self, title: str, name: str, flt: str) -> str | None:
        path, _ = QFileDialog.getSaveFileName(self, title, str(Path(self.ctx.last_dir()) / name), flt)
        if path:
            self.ctx.remember_dir(path)
        return path or None

    def _template(self) -> None:
        path = self._save_path("Simpan template", "template-import-aset.xlsx", "Excel (*.xlsx)")
        if path:
            try:
                self.ctx.excel.write_template(path)
            except OSError as e:
                error(self, f"Gagal menyimpan file: {e}. Tutup file di Excel bila sedang terbuka.")
                return
            info(self, f"Template tersimpan:\n{path}")

    def _export(self, kind: str) -> None:
        stamp = fmt.today().strftime("%Y%m%d")
        flt = "Excel (*.xlsx)" if kind == "xlsx" else "CSV (*.csv)"
        path = self._save_path("Export aset", f"aset-{stamp}.{kind}", flt)
        if not path:
            return
        try:
            n = (self.ctx.excel.export_xlsx if kind == "xlsx" else self.ctx.excel.export_csv)(path, self.ctx.audit)
        except OSError as e:
            error(self, f"Gagal menyimpan file: {e}. Tutup file di Excel bila sedang terbuka.")
            return
        info(self, f"{n} aset diexport ke:\n{path}")

    def _import(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "Pilih file Excel", self.ctx.last_dir(), "Excel (*.xlsx)")
        if not path:
            return
        self.ctx.remember_dir(path)
        QApplication.setOverrideCursor(Qt.CursorShape.WaitCursor)
        try:
            preview = self.ctx.excel.preview(path)
        finally:
            QApplication.restoreOverrideCursor()
        if preview.header_error:
            error(self, preview.header_error)
            return
        if not preview.rows:
            info(self, "Tidak ada baris data di file.")
            return
        if ImportPreviewDialog(self, preview).exec() != QDialog.DialogCode.Accepted:
            return
        total = len(preview.to_save)
        prog = QProgressDialog("Menyimpan data…", None, 0, total, self)
        prog.setWindowModality(Qt.WindowModality.WindowModal)
        prog.setMinimumDuration(300)

        def step(i, n):
            prog.setValue(i)
            QApplication.processEvents()

        try:
            result = self.ctx.excel.commit(preview, self.ctx.audit, step)
        except AppError as e:
            prog.close()
            error(self, e.message)
            return
        prog.close()
        failed = result["failed"]
        msg = (f"Import selesai: {result['created']} baru, {result['updated']} diperbarui, "
               f"{result['unchanged']} tanpa perubahan, {len(failed)} gagal.")
        if failed:
            msg += "\n\nBaris gagal:\n" + "\n".join(f"Baris {r.row_number}: {'; '.join(r.errors)}" for r in failed[:15])
            if len(failed) > 15:
                msg += f"\n… dan {len(failed) - 15} baris lainnya"
        info(self, msg)

    # ------------------------------------------------------------ backup
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
