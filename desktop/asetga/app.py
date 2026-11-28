"""Titik masuk aplikasi desktop."""

from __future__ import annotations

import sys
import traceback
from datetime import datetime

from PySide6.QtCore import QLockFile
from PySide6.QtWidgets import QApplication, QMessageBox

from . import APP_NAME
from .core.db import SchemaMismatchError
from .paths import data_dir


def _write_crash(folder, exc: BaseException) -> str:
    path = folder / "crash.log"
    try:
        with open(path, "a", encoding="utf-8") as f:
            f.write(f"\n==== {datetime.now():%d/%m/%Y %H:%M:%S}\n")
            f.write("".join(traceback.format_exception(type(exc), exc, exc.__traceback__)))
    except OSError:
        pass
    return str(path)


def main() -> int:
    app = QApplication(sys.argv)
    app.setApplicationName(APP_NAME)
    app.setOrganizationName("General Affair")
    from PySide6.QtGui import QIcon

    from .paths import resource_dir

    app.setWindowIcon(QIcon(str(resource_dir() / "icon.png")))
    folder = data_dir()

    from .ui import theme

    # Satu folder data hanya boleh dibuka satu jendela aplikasi.
    lock = QLockFile(str(folder / "aplikasi.lock"))
    lock.setStaleLockTime(0)
    if not lock.tryLock(100):
        QMessageBox.warning(None, APP_NAME, "Aset GA sudah terbuka di jendela lain.")
        return 1

    import os

    smoke = os.environ.get("ASETGA_SMOKE_EXIT") == "1"  # dipakai CI: buka lalu tutup sendiri

    def excepthook(t, e, tb):
        log = _write_crash(folder, e)
        if smoke:
            os._exit(3)
        QMessageBox.critical(None, APP_NAME, f"Terjadi galat tak terduga. Detail disimpan di:\n{log}\n\n{e}")

    sys.excepthook = excepthook

    from .ui.context import AppContext
    from .ui.main_window import MainWindow

    try:
        ctx = AppContext(folder)
    except SchemaMismatchError as e:
        QMessageBox.critical(None, APP_NAME, f"Database di folder data tidak bisa dibuka.\n\n{e}\n\nFolder: {folder}\n\n"
                                             "Gunakan versi aplikasi desktop yang sesuai, atau pindahkan folder data lalu restore dari backup.")
        return 2
    theme.apply(app, ctx.pref("theme_mode", "SYSTEM"))
    ctx.cleanup_photos()
    win = MainWindow(ctx, app)
    win.showMaximized()  # otomatis memenuhi layar
    if smoke:
        from PySide6.QtCore import QTimer

        # Pastikan modul label (reportlab + Pillow) ikut terbungkus di exe.
        from .core import labels

        sample = [labels.LabelData("IT-2024-001", "Elektronik & IT", "IT-2024-001")]
        labels.write_pdf(sample, folder / "uji-label.pdf")
        labels.render_png(sample[0]).save(folder / "uji-label.png")

        QTimer.singleShot(2000, app.quit)
    code = app.exec()
    lock.unlock()
    return code


if __name__ == "__main__":
    sys.exit(main())
