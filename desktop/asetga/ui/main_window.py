"""Jendela utama: layar masuk ↔ aplikasi (menu samping + halaman), kunci otomatis saat tidak dipakai."""

from __future__ import annotations

import time

from PySide6.QtCore import QEvent, QObject, QTimer
from PySide6.QtWidgets import QApplication, QHBoxLayout, QListWidget, QListWidgetItem, QMainWindow, QStackedWidget, QVBoxLayout, QWidget

from .. import APP_NAME, VERSION
from . import theme
from .account_page import AccountPage
from .assets_page import AssetsPage
from .audit_page import AuditPage
from .auth import LockPage, LoginPage, SetupPage
from .data_page import DataPage
from .masters_page import MastersPage
from .scan_page import ScanPage
from .services_page import ServicesPage
from .stock_page import StockPage
from .users_page import UsersPage
from .widgets import label

IDLE_LOCK_SECONDS = 5 * 60


class IdleWatcher(QObject):
    """Mencatat waktu input terakhir (mouse/keyboard) di seluruh aplikasi."""

    def __init__(self):
        super().__init__()
        self.last = time.monotonic()

    def eventFilter(self, obj, event):  # noqa: N802 - nama dari Qt
        if event.type() in (QEvent.Type.MouseButtonPress, QEvent.Type.KeyPress, QEvent.Type.Wheel, QEvent.Type.MouseMove):
            self.last = time.monotonic()
        return False


class Shell(QWidget):
    """Menu samping + halaman. Menu mengikuti peran pengguna."""

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        h = QHBoxLayout(self)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(0)
        side = QWidget()
        side.setFixedWidth(220)
        sv = QVBoxLayout(side)
        sv.setContentsMargins(0, 0, 0, 0)
        sv.setSpacing(0)
        sv.addWidget(label(APP_NAME, "navHeader"))
        self.user_label = label("", "navUser")
        sv.addWidget(self.user_label)
        self.nav = QListWidget()
        self.nav.setObjectName("nav")
        sv.addWidget(self.nav, 1)
        h.addWidget(side)
        self.stack = QStackedWidget()
        page_wrap = QWidget()
        pv = QVBoxLayout(page_wrap)
        pv.setContentsMargins(18, 14, 18, 14)
        pv.addWidget(self.stack)
        h.addWidget(page_wrap, 1)

        self.assets = AssetsPage(ctx)
        self.scan = ScanPage(ctx)
        self.stock = StockPage(ctx)
        self.services = ServicesPage(ctx)
        self.masters = MastersPage(ctx)
        self.data = DataPage(ctx)
        self.users = UsersPage(ctx)
        self.audit = AuditPage(ctx)
        self.account = AccountPage(ctx)
        # (judul menu, halaman, boleh tampil?)
        self.pages = [
            ("Aset", self.assets, lambda r: True),
            ("Scan", self.scan, lambda r: True),
            ("Stok", self.stock, lambda r: True),
            ("Servis", self.services, lambda r: True),
            ("Master data", self.masters, lambda r: True),
            ("Import / Export & Backup", self.data, lambda r: True),
            ("Pengguna", self.users, lambda r: r.can_manage_users),
            ("Audit log", self.audit, lambda r: True),
            ("Akun & tampilan", self.account, lambda r: True),
        ]
        for _, page, _ in self.pages:
            self.stack.addWidget(page)
        self.nav.currentRowChanged.connect(self._go)

    def rebuild(self) -> None:
        u = self.ctx.session.user
        self.user_label.setText(f"{u.full_name}\n{u.role.label}" if u else "")
        self.visible = [(t, p) for t, p, ok in self.pages if u and ok(u.role)]
        self.nav.blockSignals(True)
        self.nav.clear()
        for t, _ in self.visible:
            self.nav.addItem(QListWidgetItem(t))
        self.nav.blockSignals(False)
        self.nav.setCurrentRow(0)
        self._go(0)

    def _go(self, row: int) -> None:
        if 0 <= row < len(self.visible):
            page = self.visible[row][1]
            self.stack.setCurrentWidget(page)
            page.on_show()


class MainWindow(QMainWindow):
    def __init__(self, ctx, app: QApplication):
        super().__init__()
        self.ctx = ctx
        self.app = app
        self.setWindowTitle(f"{APP_NAME} — Sistem Manajemen Aset & Inventaris" + ("" if VERSION == "dev" else f" (build {VERSION})"))
        self.resize(1280, 800)
        self.stack = QStackedWidget()
        self.setCentralWidget(self.stack)
        self.setup = SetupPage(ctx)
        self.login = LoginPage(ctx)
        self.lock = LockPage(ctx)
        self.shell = Shell(ctx)
        for w in (self.setup, self.login, self.lock, self.shell):
            self.stack.addWidget(w)
        self.setup.done.connect(self._after_auth)
        self.login.done.connect(self._after_auth)
        self.lock.unlocked.connect(lambda: self.stack.setCurrentWidget(self.shell))
        self.lock.switch_user.connect(self.logout)
        self.shell.data.restored.connect(self.show_login)
        self.shell.account.theme_changed.connect(lambda m: theme.apply(self.app, m))
        self.shell.account.lock_requested.connect(self.lock_now)
        self.shell.account.logout_requested.connect(self.logout)

        self.idle = IdleWatcher()
        app.installEventFilter(self.idle)
        self.timer = QTimer(self, interval=15_000)
        self.timer.timeout.connect(self._check_idle)
        self.timer.start()
        self.show_login()

    def show_login(self) -> None:
        if self.ctx.users.count() == 0:
            self.stack.setCurrentWidget(self.setup)
        else:
            self.login.reset()
            self.stack.setCurrentWidget(self.login)

    def _after_auth(self) -> None:
        if self.ctx.session.user is None:  # mis. setelah restore di layar awal
            self.show_login()
            return
        self.idle.last = time.monotonic()
        self.shell.rebuild()
        self.stack.setCurrentWidget(self.shell)

    def lock_now(self) -> None:
        if self.ctx.session.user and self.stack.currentWidget() is self.shell:
            # Tutup dialog yang sedang terbuka agar data tidak terlihat saat terkunci.
            for w in self.app.topLevelWidgets():
                if w is not self and w.isVisible() and hasattr(w, "reject"):
                    w.reject()
            self.ctx.session.locked = True
            self.lock.reset()
            self.stack.setCurrentWidget(self.lock)

    def logout(self) -> None:
        self.ctx.session.logout()
        self.show_login()

    def _check_idle(self) -> None:
        if self.ctx.db.setting("app_lock_enabled", "true") != "true":
            return
        if time.monotonic() - self.idle.last >= IDLE_LOCK_SECONDS:
            self.lock_now()

    def closeEvent(self, event):  # noqa: N802 - nama dari Qt
        try:
            self.ctx.db.close()
        except Exception:  # noqa: BLE001
            pass
        super().closeEvent(event)
