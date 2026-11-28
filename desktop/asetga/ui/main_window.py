"""Jendela utama: layar masuk ↔ aplikasi (menu samping + halaman), kunci otomatis saat tidak dipakai."""

from __future__ import annotations

import time

from PySide6.QtCore import QEvent, QObject, QSize, Qt, QTimer
from PySide6.QtWidgets import (
    QApplication, QFrame, QHBoxLayout, QListWidget, QListWidgetItem, QMainWindow, QSizePolicy, QStackedWidget, QToolButton,
    QVBoxLayout, QWidget,
)

from .. import APP_NAME, VERSION
from . import icons, theme
from .account_page import AccountPage
from .assets_page import AssetsPage
from .audit_page import AuditPage
from .auth import LockPage, LoginPage, SetupPage
from .backup_page import BackupPage
from .dashboard_page import DashboardPage
from .masters_page import MastersPage
from .scan_page import ScanPage
from .services_page import ServicesPage
from .stock_page import StockPage
from .users_page import UsersPage
from .widgets import app_logo, hline, install_toast, label

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
    """Menu samping berikon + halaman. Menu mengikuti peran pengguna."""

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        h = QHBoxLayout(self)
        h.setContentsMargins(0, 0, 0, 0)
        h.setSpacing(0)
        self.compact = False
        side = QFrame()
        side.setObjectName("sidebar")
        side.setFixedWidth(236)
        self.side = side
        sv = QVBoxLayout(side)
        sv.setContentsMargins(14, 18, 14, 14)
        sv.setSpacing(4)
        brand = QHBoxLayout()
        brand.setSpacing(10)
        brand.addWidget(app_logo(36))
        self.names = QWidget()
        names = QVBoxLayout(self.names)
        names.setContentsMargins(0, 0, 0, 0)
        names.setSpacing(0)
        title = label(APP_NAME, None, wrap=False)
        title.setStyleSheet("font-weight: 700; font-size: 11.5pt;")
        names.addWidget(title)
        names.addWidget(label("Manajemen aset", "small", wrap=False))
        brand.addWidget(self.names, 1)
        sv.addLayout(brand)
        sv.addSpacing(14)
        self.nav = QListWidget()
        self.nav.setObjectName("nav")
        self.nav.setIconSize(QSize(18, 18))
        self.nav.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.nav.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.nav.setCursor(Qt.CursorShape.PointingHandCursor)
        sv.addWidget(self.nav, 1)
        # Tombol ciutkan/lebarkan menu (pilihan diingat di desktop.ini).
        self.toggle = QToolButton()
        self.toggle.setObjectName("navToggle")
        self.toggle.setCursor(Qt.CursorShape.PointingHandCursor)
        self.toggle.setIconSize(QSize(18, 18))
        self.toggle.setAutoRaise(True)
        self.toggle.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.toggle.clicked.connect(lambda: self.set_compact(not self.compact, save=True))
        sv.addWidget(self.toggle)
        sv.addWidget(hline())
        user_row = QHBoxLayout()
        user_row.setContentsMargins(4, 10, 4, 0)
        user_row.setSpacing(10)
        self.avatar = label("", "avatar", wrap=False)
        self.avatar.setFixedSize(34, 34)
        self.avatar.setAlignment(Qt.AlignmentFlag.AlignCenter)
        user_row.addWidget(self.avatar)
        self.who = QWidget()
        who = QVBoxLayout(self.who)
        who.setContentsMargins(0, 0, 0, 0)
        who.setSpacing(0)
        self.user_name = label("", None, wrap=False)
        self.user_name.setStyleSheet("font-weight: 600;")
        self.user_role = label("", "small", wrap=False)
        who.addWidget(self.user_name)
        who.addWidget(self.user_role)
        user_row.addWidget(self.who, 1)
        sv.addLayout(user_row)
        h.addWidget(side)
        self.stack = QStackedWidget()
        h.addWidget(self.stack, 1)

        self.dashboard = DashboardPage(ctx)
        self.assets = AssetsPage(ctx)
        self.scan = ScanPage(ctx)
        self.stock = StockPage(ctx)
        self.services = ServicesPage(ctx)
        self.masters = MastersPage(ctx)
        self.backup = BackupPage(ctx)
        self.users = UsersPage(ctx)
        self.audit = AuditPage(ctx)
        self.account = AccountPage(ctx)
        # (judul menu, ikon, halaman, boleh tampil?)
        self.pages = [
            ("Dashboard", "dashboard", self.dashboard, lambda r: True),
            ("Aset", "aset", self.assets, lambda r: True),
            ("Scan", "scan", self.scan, lambda r: True),
            ("Stok", "stok", self.stock, lambda r: True),
            ("Servis", "servis", self.services, lambda r: True),
            ("Master data", "master", self.masters, lambda r: True),
            ("Backup & restore", "backup", self.backup, lambda r: r.can_manage_master),
            ("Pengguna", "pengguna", self.users, lambda r: r.can_manage_users),
            ("Audit log", "audit", self.audit, lambda r: True),
            ("Akun & tampilan", "akun", self.account, lambda r: True),
        ]
        for _, _, page, _ in self.pages:
            self.stack.addWidget(page)
        self.nav.currentRowChanged.connect(self._go)
        ctx.open_asset = self.open_asset
        self.set_compact(ctx.pref("sidebar/compact", "false") == "true")
        self._update_toggle()

    def open_asset(self, asset_id: str) -> None:
        """Buka halaman detail aset (dipanggil dari Dashboard, Scan, Servis)."""
        for row, (_, _, page) in enumerate(self.visible):
            if page is self.assets:
                self.nav.blockSignals(True)
                self.nav.setCurrentRow(row)
                self.nav.blockSignals(False)
                self.stack.setCurrentWidget(self.assets)
                self.assets.list.on_show()
                self.assets.open_detail(asset_id)
                return

    def rebuild(self) -> None:
        u = self.ctx.session.user
        self.user_name.setText(u.full_name if u else "")
        self.user_role.setText(u.role.label if u else "")
        initials = "".join(w[0] for w in (u.full_name.split()[:2] if u else []) if w).upper()
        self.avatar.setText(initials or "?")
        self.visible = [(t, ic, p) for t, ic, p, ok in self.pages if u and ok(u.role)]
        muted = theme.c()["text"]
        self.nav.blockSignals(True)
        self.nav.clear()
        for t, ic, _ in self.visible:
            item = QListWidgetItem(icons.nav_icon(ic, "#FFFFFF", muted), "" if self.compact else t)
            item.setToolTip(t)
            self.nav.addItem(item)
        self.nav.blockSignals(False)
        self.nav.setCurrentRow(0)
        self._go(0)

    def set_compact(self, on: bool, save: bool = False) -> None:
        """Menu diciutkan (hanya ikon) atau dilebarkan. Hanya berubah lewat tombol, tidak mengikuti ukuran jendela."""
        if save:
            self.ctx.set_pref("sidebar/compact", "true" if on else "false")
        if on == self.compact:
            return
        self.compact = on
        self.side.setFixedWidth(72 if on else 236)
        self.names.setVisible(not on)
        self.who.setVisible(not on)
        self.nav.setProperty("compact", "true" if on else "false")
        self.nav.style().unpolish(self.nav)
        self.nav.style().polish(self.nav)
        for i, (t, _, _) in enumerate(getattr(self, "visible", [])):
            self.nav.item(i).setText("" if on else t)
        self._update_toggle()

    def _update_toggle(self) -> None:
        on = self.compact
        self.toggle.setIcon(icons.icon("lebarkan" if on else "ciutkan", "#7A8696"))
        self.toggle.setText("" if on else "Ciutkan menu")
        self.toggle.setToolTip("Lebarkan menu" if on else "Ciutkan menu")
        self.toggle.setToolButtonStyle(
            Qt.ToolButtonStyle.ToolButtonIconOnly if on else Qt.ToolButtonStyle.ToolButtonTextBesideIcon)
        self.toggle.setProperty("compact", "true" if on else "false")
        self.toggle.style().unpolish(self.toggle)
        self.toggle.style().polish(self.toggle)

    def _go(self, row: int) -> None:
        if 0 <= row < len(self.visible):
            page = self.visible[row][2]
            self.stack.setCurrentWidget(page)
            page.on_show()


class MainWindow(QMainWindow):
    def __init__(self, ctx, app: QApplication):
        super().__init__()
        self.ctx = ctx
        self.app = app
        self.setWindowTitle(f"{APP_NAME} — Sistem Manajemen Aset & Inventaris" + ("" if VERSION == "dev" else f" (build {VERSION})"))
        self.resize(1366, 820)
        self.setMinimumSize(980, 640)
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
        self.shell.backup.restored.connect(self.show_login)
        self.shell.account.theme_changed.connect(self._theme_changed)
        self.shell.account.lock_requested.connect(self.lock_now)
        self.shell.account.logout_requested.connect(self.logout)

        self.toast = install_toast(self)
        self.idle = IdleWatcher()
        app.installEventFilter(self.idle)
        self.timer = QTimer(self, interval=15_000)
        self.timer.timeout.connect(self._check_idle)
        self.timer.start()
        self.show_login()

    def _theme_changed(self, mode: str) -> None:
        theme.apply(self.app, mode)
        # Ikon & warna yang digambar sendiri ikut diperbarui.
        current = self.shell.nav.currentRow()
        self.shell.rebuild()
        self.shell.nav.setCurrentRow(current)

    def resizeEvent(self, event):  # noqa: N802 - nama dari Qt
        super().resizeEvent(event)
        if self.toast.isVisible():
            self.toast.move(self.width() - self.toast.width() - 24, self.height() - self.toast.height() - 24)

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
