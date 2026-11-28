"""Komponen UI bersama: isian form dengan pesan galat, tanggal dd/mm/yyyy, Rupiah, dialog konfirmasi."""

from __future__ import annotations

import datetime as _dt
from typing import Callable

from PySide6.QtCore import QDate, Qt, Signal
from PySide6.QtGui import QStandardItem, QStandardItemModel
from PySide6.QtWidgets import (
    QAbstractItemView, QCheckBox, QComboBox, QDateEdit, QDialog, QDialogButtonBox, QFormLayout, QFrame, QHBoxLayout,
    QHeaderView, QLabel, QLineEdit, QMessageBox, QPlainTextEdit, QPushButton, QSizePolicy, QStyledItemDelegate, QTableView, QVBoxLayout,
    QWidget,
)

from ..core import fmt
from ..core.models import AppError


# ---------------------------------------------------------------- pesan
def info(parent, text: str, title: str = "Aset GA") -> None:
    QMessageBox.information(parent, title, text)


def error(parent, text: str, title: str = "Tidak berhasil") -> None:
    QMessageBox.warning(parent, title, text)


def confirm(parent, text: str, title: str = "Konfirmasi", yes: str = "Ya", danger: bool = False) -> bool:
    box = QMessageBox(QMessageBox.Icon.Warning if danger else QMessageBox.Icon.Question, title, text, parent=parent)
    ok = box.addButton(yes, QMessageBox.ButtonRole.AcceptRole)
    box.addButton("Batal", QMessageBox.ButtonRole.RejectRole)
    box.exec()
    return box.clickedButton() is ok


def run_safely(parent, action: Callable, on_error: Callable[[AppError], None] | None = None):
    """Jalankan aksi; AppError ditampilkan ke pengguna (atau diteruskan ke [on_error])."""
    try:
        return action()
    except AppError as e:
        if on_error:
            on_error(e)
        else:
            error(parent, e.message)
    return None


def primary(text: str, icon_name: str | None = None) -> QPushButton:
    b = QPushButton(text)
    b.setObjectName("primary")
    b.setCursor(Qt.CursorShape.PointingHandCursor)
    b.setSizePolicy(QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Fixed)
    if icon_name:
        from . import icons

        b.setIcon(icons.icon(icon_name, "#FFFFFF", 16, 2.2))
    return b


def button(text: str, danger: bool = False, icon_name: str | None = None) -> QPushButton:
    b = QPushButton(text)
    if danger:
        b.setObjectName("danger")
    b.setCursor(Qt.CursorShape.PointingHandCursor)
    b.setSizePolicy(QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Fixed)
    if icon_name:
        from . import icons, theme

        # Abu-abu sedang: tetap terlihat di tema terang maupun gelap (ikon tidak digambar ulang saat tema diganti).
        b.setIcon(icons.icon(icon_name, theme.ERROR if danger else "#7A8696", 16))
    return b


def label(text: str, name: str | None = None, wrap: bool = True) -> QLabel:
    lb = QLabel(text)
    if name:
        lb.setObjectName(name)
    lb.setWordWrap(wrap)
    return lb


class Banner(QLabel):
    """Galat di dalam dialog (seperti ErrorBanner di Android)."""

    def __init__(self):
        super().__init__()
        self.setObjectName("banner")
        self.setWordWrap(True)
        self.hide()

    def show_text(self, text: str | None) -> None:
        if text:
            self.setText(text)
            self.show()
        else:
            self.hide()


# ---------------------------------------------------------------- isian
class DateField(QWidget):
    """Tanggal dd/MM/yyyy; [optional] menambah kotak centang "Isi tanggal"."""

    changed = Signal()

    def __init__(self, optional: bool = False):
        super().__init__()
        lay = QHBoxLayout(self)
        lay.setContentsMargins(0, 0, 0, 0)
        self.edit = QDateEdit()
        self.edit.setDisplayFormat("dd/MM/yyyy")
        self.edit.setCalendarPopup(True)
        self.edit.setDate(QDate.currentDate())
        self.edit.dateChanged.connect(lambda _: self.changed.emit())
        self.check: QCheckBox | None = None
        if optional:
            self.check = QCheckBox("Isi")
            self.check.toggled.connect(self._toggle)
            self.check.toggled.connect(lambda _: self.changed.emit())
            lay.addWidget(self.check)
            self._toggle(False)
        lay.addWidget(self.edit, 1)

    def _toggle(self, on: bool) -> None:
        self.edit.setEnabled(on)

    def value(self) -> _dt.date | None:
        if self.check is not None and not self.check.isChecked():
            return None
        d = self.edit.date()
        return _dt.date(d.year(), d.month(), d.day())

    def set_value(self, v: _dt.date | None) -> None:
        if v is None:
            if self.check is not None:
                self.check.setChecked(False)
            return
        if self.check is not None:
            self.check.setChecked(True)
        self.edit.setDate(QDate(v.year, v.month, v.day))


class MoneyField(QLineEdit):
    """Rupiah dengan pemisah ribuan saat diketik: 1.250.000."""

    def __init__(self):
        super().__init__()
        self.setPlaceholderText("0")
        self.textEdited.connect(self._reformat)

    def _reformat(self, text: str) -> None:
        v = fmt.parse_rupiah(text)
        new = fmt.number(v) if v is not None else ""
        if new != text:
            self.setText(new)

    def value(self) -> int | None:
        return fmt.parse_rupiah(self.text())

    def set_value(self, v: int | None) -> None:
        self.setText("" if v is None else fmt.number(v))


class Combo(QComboBox):
    """Combo berisi pasangan (teks, id). [none_text] = pilihan kosong di atas."""

    def __init__(self, none_text: str | None = None):
        super().__init__()
        self.none_text = none_text
        self.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToMinimumContentsLengthWithIcon)
        self.setMinimumContentsLength(12)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

    def set_items(self, items: list[tuple[str, object]], keep: bool = True) -> None:
        current = self.value() if keep else None
        self.blockSignals(True)
        self.clear()
        if self.none_text is not None:
            self.addItem(self.none_text, None)
        for text, data in items:
            self.addItem(text, data)
        self.blockSignals(False)
        self.set_value(current)

    def value(self):
        return self.currentData()

    def set_value(self, data) -> None:
        i = self.findData(data) if data is not None else (0 if self.none_text is not None else -1)
        self.setCurrentIndex(max(i, 0) if self.count() else -1)


class Form(QWidget):
    """QFormLayout dengan pesan galat di bawah setiap isian (kunci = nama field dari service)."""

    def __init__(self):
        super().__init__()
        self.layout_ = QFormLayout(self)
        self.layout_.setLabelAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.layout_.setHorizontalSpacing(16)
        self.layout_.setVerticalSpacing(10)
        self.layout_.setFieldGrowthPolicy(QFormLayout.FieldGrowthPolicy.ExpandingFieldsGrow)
        self._errors: dict[str, QLabel] = {}
        self._widgets: dict[str, QWidget] = {}

    def add(self, text: str, widget: QWidget, key: str | None = None, hint: str | None = None) -> QWidget:
        box = QWidget()
        v = QVBoxLayout(box)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(2)
        v.addWidget(widget)
        if hint:
            v.addWidget(label(hint, "muted"))
        if key:
            err = label("", "error")
            err.hide()
            v.addWidget(err)
            self._errors[key] = err
            self._widgets[key] = widget
        self.layout_.addRow(text, box)
        return widget

    def add_section(self, text: str) -> None:
        lb = QLabel(text.upper())
        lb.setObjectName("section")
        lb.setContentsMargins(0, 12, 0, 0)
        self.layout_.addRow(lb)

    def set_errors(self, errors: dict[str, str]) -> None:
        for key, lb in self._errors.items():
            msg = errors.get(key)
            lb.setText(msg or "")
            lb.setVisible(bool(msg))
            w = self._widgets[key]
            w.setProperty("invalid", bool(msg))
            w.style().unpolish(w)
            w.style().polish(w)


class FormDialog(QDialog):
    """Dialog form standar: banner galat, isi, tombol Simpan/Batal. Subkelas mengisi [form] dan [save()]."""

    def __init__(self, parent, title: str, ok_text: str = "Simpan"):
        super().__init__(parent)
        self.setWindowTitle(title)
        self.setMinimumWidth(480)
        self.banner = Banner()
        self.form = Form()
        self.body = QVBoxLayout(self)
        self.body.setContentsMargins(22, 20, 22, 18)
        self.body.setSpacing(12)
        head = label(title, "h2", wrap=False)
        self.body.addWidget(head)
        self.body.addWidget(self.banner)
        self.body.addWidget(self.form)
        self.buttons = QDialogButtonBox()
        self.ok = primary(ok_text)
        self.buttons.addButton(self.ok, QDialogButtonBox.ButtonRole.AcceptRole)
        self.buttons.addButton("Batal", QDialogButtonBox.ButtonRole.RejectRole)
        self.buttons.accepted.connect(self._submit)
        self.buttons.rejected.connect(self.reject)
        self.body.addWidget(self.buttons)

    def save(self) -> None:
        raise NotImplementedError

    def _submit(self) -> None:
        try:
            self.save()
        except AppError as e:
            self.form.set_errors(e.fields)
            self.banner.show_text(e.message)
            return
        self.accept()


class PasswordConfirmDialog(QDialog):
    """Konfirmasi kata sandi untuk tindakan berbahaya (restore, hapus pengguna, dll.)."""

    def __init__(self, parent, ctx, reason: str):
        super().__init__(parent)
        self.ctx = ctx
        self.setWindowTitle("Konfirmasi kata sandi")
        lay = QVBoxLayout(self)
        lay.addWidget(label(reason))
        self.banner = Banner()
        lay.addWidget(self.banner)
        self.pw = QLineEdit()
        self.pw.setEchoMode(QLineEdit.EchoMode.Password)
        self.pw.setPlaceholderText("Kata sandi Anda")
        lay.addWidget(self.pw)
        bb = QDialogButtonBox()
        ok = primary("Lanjutkan")
        bb.addButton(ok, QDialogButtonBox.ButtonRole.AcceptRole)
        bb.addButton("Batal", QDialogButtonBox.ButtonRole.RejectRole)
        bb.accepted.connect(self._check)
        bb.rejected.connect(self.reject)
        lay.addWidget(bb)

    def _check(self) -> None:
        from ..core.users import LoginThrottle

        wait = self.ctx.users.throttle.remaining_seconds(self.ctx.session.user.id)
        if wait > 0:
            self.banner.show_text(LoginThrottle.message(wait))
            return
        if self.ctx.users.verify_current_password(self.pw.text()):
            self.accept()
        else:
            self.pw.clear()
            self.banner.show_text("Kata sandi salah")


def confirm_password(parent, ctx, reason: str) -> bool:
    return PasswordConfirmDialog(parent, ctx, reason).exec() == QDialog.DialogCode.Accepted


def ask_text(parent, title: str, prompt: str, ok_text: str = "Lanjutkan", required_msg: str = "Wajib diisi") -> str | None:
    dlg = QDialog(parent)
    dlg.setWindowTitle(title)
    lay = QVBoxLayout(dlg)
    lay.addWidget(label(prompt))
    banner = Banner()
    lay.addWidget(banner)
    edit = QPlainTextEdit()
    edit.setFixedHeight(80)
    lay.addWidget(edit)
    bb = QDialogButtonBox()
    bb.addButton(primary(ok_text), QDialogButtonBox.ButtonRole.AcceptRole)
    bb.addButton("Batal", QDialogButtonBox.ButtonRole.RejectRole)

    def ok():
        if edit.toPlainText().strip():
            dlg.accept()
        else:
            banner.show_text(required_msg)

    bb.accepted.connect(ok)
    bb.rejected.connect(dlg.reject)
    lay.addWidget(bb)
    return edit.toPlainText().strip() if dlg.exec() == QDialog.DialogCode.Accepted else None


# ---------------------------------------------------------------- tabel
class _Delegate(QStyledItemDelegate):
    """Chip status berwarna di kolom tertentu dan garis aksen biru di kiri baris terpilih."""

    def __init__(self, table: "Table"):
        super().__init__(table)
        self.table = table

    def _is_first_visible(self, col: int) -> bool:
        hh = self.table.horizontalHeader()
        for v in range(hh.count()):
            logical = hh.logicalIndex(v)
            if not hh.isSectionHidden(logical):
                return logical == col
        return col == 0

    def paint(self, painter, option, index):
        from PySide6.QtCore import QRectF
        from PySide6.QtGui import QColor, QFont, QFontMetrics, QPainter
        from PySide6.QtWidgets import QStyle

        from . import theme

        col = index.column()
        selected = bool(option.state & QStyle.StateFlag.State_Selected)
        hover = bool(option.state & QStyle.StateFlag.State_MouseOver)
        k = theme.c()
        painter.save()
        bg = k["select"] if selected else (k["hover"] if hover else k["surface"])
        painter.fillRect(option.rect, QColor(bg))
        painter.fillRect(option.rect.adjusted(0, option.rect.height() - 1, 0, 0), QColor(k["border"]))
        if selected and self._is_first_visible(col):
            painter.fillRect(option.rect.adjusted(0, 0, -(option.rect.width() - 3), 0), QColor(theme.PRIMARY))
        text = index.data(Qt.ItemDataRole.DisplayRole) or ""
        if col in self.table.chip_columns and text:
            chip_bg, chip_fg = theme.chip_colors(text.rstrip(" ⊘"), self.table.write_off_name)
            f = option.font
            f.setPointSizeF(max(8.0, f.pointSizeF() - 1))
            f.setWeight(QFont.Weight.DemiBold)
            fm = QFontMetrics(f)
            w = min(fm.horizontalAdvance(text) + 20, option.rect.width() - 16)
            h = fm.height() + 6
            r = QRectF(option.rect.x() + 10, option.rect.center().y() - h / 2 + 1, w, h)
            painter.setRenderHint(QPainter.RenderHint.Antialiasing)
            painter.setPen(Qt.PenStyle.NoPen)
            painter.setBrush(QColor(chip_bg))
            painter.drawRoundedRect(r, h / 2, h / 2)
            painter.setFont(f)
            painter.setPen(QColor(chip_fg))
            painter.drawText(r, int(Qt.AlignmentFlag.AlignCenter), fm.elidedText(text, Qt.TextElideMode.ElideRight, int(w - 12)))
        else:
            fg = index.data(Qt.ItemDataRole.ForegroundRole)
            painter.setPen(fg.color() if fg is not None else QColor(k["select_text"] if selected else k["text"]))
            f = option.font
            if col == 0 and self.table.bold_first:
                f.setWeight(QFont.Weight.DemiBold)
            painter.setFont(f)
            raw = index.data(Qt.ItemDataRole.TextAlignmentRole)
            align = int(raw) if raw else int(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
            rect = option.rect.adjusted(12, 0, -12, 0)
            lines = text.split("\n")
            if len(lines) > 1 and self.table.wordWrap():
                painter.drawText(rect, align | int(Qt.TextFlag.TextWordWrap), text)
            else:
                painter.drawText(rect, align,
                                 option.fontMetrics.elidedText(lines[0], Qt.TextElideMode.ElideRight, rect.width()))
        painter.restore()


class Table(QTableView):
    """Tabel baca-saja bergaya kartu. Data baris disimpan di UserRole kolom pertama.
    [chips] = indeks kolom yang ditampilkan sebagai chip berwarna (status, kondisi, dll.)."""

    activated_row = Signal(object)
    current_changed = Signal(object)

    def __init__(self, headers: list[str], multi: bool = False, chips: set[int] | None = None, empty_text: str = "Belum ada data",
                 bold_first: bool = True):
        super().__init__()
        self.chip_columns = chips or set()
        self.empty_text = empty_text
        self.bold_first = bold_first
        self.write_off_name: str | None = None
        self.model_ = QStandardItemModel(0, len(headers))
        self.model_.setHorizontalHeaderLabels([h.upper() for h in headers])
        self.setModel(self.model_)
        self.setItemDelegate(_Delegate(self))
        self.setMouseTracking(True)
        self.setShowGrid(False)
        self.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection if multi else QAbstractItemView.SelectionMode.SingleSelection)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.verticalHeader().hide()
        self.verticalHeader().setDefaultSectionSize(44)
        hh = self.horizontalHeader()
        hh.setStretchLastSection(True)
        hh.setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        hh.setDefaultAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        hh.setHighlightSections(False)
        self.setWordWrap(False)
        self.setHorizontalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.doubleClicked.connect(lambda idx: self.activated_row.emit(self.row_data(idx.row())))
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)

    def currentChanged(self, current, previous):  # noqa: N802 - nama dari Qt
        super().currentChanged(current, previous)
        if current.isValid():
            self.current_changed.emit(self.row_data(current.row()))

    def paintEvent(self, event):  # noqa: N802 - nama dari Qt
        super().paintEvent(event)
        if self.model_.rowCount() == 0 and self.empty_text:
            from PySide6.QtGui import QColor, QPainter

            from . import theme

            p = QPainter(self.viewport())
            p.setPen(QColor(theme.c()["muted"]))
            p.drawText(self.viewport().rect().adjusted(0, 24, 0, 0), Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop,
                       self.empty_text)
            p.end()

    def set_rows(self, rows: list[tuple[object, list[str]]], muted: Callable[[object], bool] | None = None,
                 align_right: set[int] | None = None) -> None:
        from PySide6.QtGui import QBrush, QColor

        from . import theme

        keep = self.current_data()
        self.model_.setRowCount(0)
        right = align_right or set()
        for data, cells in rows:
            items = []
            for i, text in enumerate(cells):
                it = QStandardItem(text)
                if i in right:
                    it.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
                items.append(it)
            items[0].setData(data, Qt.ItemDataRole.UserRole)
            if muted and muted(data):
                for it in items:
                    it.setForeground(QBrush(QColor(theme.c()["muted"])))
            self.model_.appendRow(items)
        if keep is not None:
            for r in range(self.model_.rowCount()):
                if self.row_data(r) == keep:
                    self.selectRow(r)
                    break

    def row_data(self, row: int):
        it = self.model_.item(row, 0)
        return it.data(Qt.ItemDataRole.UserRole) if it else None

    def selected_data(self) -> list:
        sm = self.selectionModel()
        if sm is None:
            return []
        rows = sorted({i.row() for i in sm.selectedRows()})
        return [self.row_data(r) for r in rows]

    def current_data(self):
        sel = self.selected_data()
        return sel[0] if sel else None

    def set_widths(self, widths: list[int]) -> None:
        for i, w in enumerate(widths):
            self.setColumnWidth(i, w)


# ---------------------------------------------------------------- tata letak
def card(layout: str | None = None, margins: tuple[int, int, int, int] = (0, 0, 0, 0), spacing: int = 0) -> QFrame:
    """Kartu putih bersudut membulat. [layout] "v"/"h" langsung membuat layout di dalamnya."""
    f = QFrame()
    f.setObjectName("card")
    if layout:
        lay = QVBoxLayout(f) if layout == "v" else QHBoxLayout(f)
        lay.setContentsMargins(*margins)
        lay.setSpacing(spacing)
    return f


def hline() -> QFrame:
    f = QFrame()
    f.setObjectName("divider")
    return f


def app_logo(size: int) -> QLabel:
    """Ikon aplikasi (sama dengan ikon launcher Android: kubus putih di latar biru)."""
    from PySide6.QtGui import QPixmap

    from ..paths import resource_dir

    lb = QLabel()
    pm = QPixmap(str(resource_dir() / "icon.png"))
    if not pm.isNull():
        dpr = 2
        pm = pm.scaled(size * dpr, size * dpr, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        pm.setDevicePixelRatio(dpr)
        lb.setPixmap(pm)
    lb.setFixedSize(size, size)
    return lb


def search_field(placeholder: str) -> QLineEdit:
    from . import icons, theme

    e = QLineEdit()
    e.setObjectName("search")
    e.setPlaceholderText(placeholder)
    e.setClearButtonEnabled(True)
    e.addAction(icons.icon("cari", theme.c()["muted"], 16), QLineEdit.ActionPosition.LeadingPosition)
    return e


def page_header(title: str, subtitle: QLabel | None = None, *actions: QWidget) -> QHBoxLayout:
    """Judul halaman + keterangan kecil di bawahnya + aksi di kanan."""
    row = QHBoxLayout()
    row.setSpacing(10)
    col = QVBoxLayout()
    col.setSpacing(2)
    col.addWidget(label(title, "title", wrap=False))
    if subtitle is not None:
        col.addWidget(subtitle)
    row.addLayout(col, 1)
    for a in actions:
        row.addWidget(a)
    return row


def page_layout(widget: QWidget) -> QVBoxLayout:
    widget.setObjectName("page")
    v = QVBoxLayout(widget)
    v.setContentsMargins(28, 22, 28, 22)
    v.setSpacing(16)
    return v


class Kpi(QFrame):
    """Kartu ringkasan: label kecil, angka besar, keterangan berwarna."""

    def __init__(self, title: str):
        super().__init__()
        self.setObjectName("card")
        v = QVBoxLayout(self)
        v.setContentsMargins(18, 14, 18, 14)
        v.setSpacing(2)
        v.addWidget(label(title, "muted", wrap=False))
        self.value = label("-", "kpiValue", wrap=False)
        v.addWidget(self.value)
        self.note = label("", "small", wrap=False)
        v.addWidget(self.note)

    def set(self, value: str, note: str = "", tone: str | None = None) -> None:
        from . import theme

        self.value.setText(value)
        self.note.setText(note)
        self.note.setStyleSheet(f"color: {theme.tone(tone)[1]};" if tone else "")


# ---------------------------------------------------------------- toast
class Toast(QLabel):
    """Notifikasi kecil di pojok kanan bawah jendela utama; hilang sendiri."""

    def __init__(self, host: QWidget):
        super().__init__(host)
        from PySide6.QtCore import QTimer

        self.setObjectName("toast")
        self.hide()
        self._timer = QTimer(self, singleShot=True)
        self._timer.timeout.connect(self.hide)

    def show_text(self, text: str, ms: int = 2800) -> None:
        self.setText("✓  " + text)
        self.adjustSize()
        host = self.parentWidget()
        self.move(host.width() - self.width() - 24, host.height() - self.height() - 24)
        self.raise_()
        self.show()
        self._timer.start(ms)


_toast: Toast | None = None


def install_toast(host: QWidget) -> Toast:
    global _toast
    _toast = Toast(host)
    return _toast


def notify(parent, text: str) -> None:
    """Pesan sukses singkat: toast bila jendela utama ada, selain itu kotak pesan biasa."""
    if _toast is not None:
        try:
            _toast.show_text(text)
            return
        except RuntimeError:  # jendela sudah ditutup
            pass
    info(parent, text)
