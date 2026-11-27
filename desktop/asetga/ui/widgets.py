"""Komponen UI bersama: isian form dengan pesan galat, tanggal dd/mm/yyyy, Rupiah, dialog konfirmasi."""

from __future__ import annotations

import datetime as _dt
from typing import Callable

from PySide6.QtCore import QDate, Qt, Signal
from PySide6.QtGui import QStandardItem, QStandardItemModel
from PySide6.QtWidgets import (
    QAbstractItemView, QCheckBox, QComboBox, QDateEdit, QDialog, QDialogButtonBox, QFormLayout, QFrame, QHBoxLayout,
    QHeaderView, QLabel, QLineEdit, QMessageBox, QPlainTextEdit, QPushButton, QSizePolicy, QTableView, QVBoxLayout, QWidget,
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


def primary(text: str) -> QPushButton:
    b = QPushButton(text)
    b.setObjectName("primary")
    b.setCursor(Qt.CursorShape.PointingHandCursor)
    return b


def button(text: str, danger: bool = False) -> QPushButton:
    b = QPushButton(text)
    if danger:
        b.setObjectName("danger")
    b.setCursor(Qt.CursorShape.PointingHandCursor)
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
        self.setMinimumContentsLength(14)

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
        self.layout_.setLabelAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
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
        lb = QLabel(text)
        lb.setStyleSheet("font-weight: 600; margin-top: 10px;")
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
        self.setMinimumWidth(460)
        self.banner = Banner()
        self.form = Form()
        self.body = QVBoxLayout(self)
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
class Table(QTableView):
    """Tabel baca-saja. Data baris disimpan di UserRole kolom pertama."""

    activated_row = Signal(object)

    def __init__(self, headers: list[str], multi: bool = False):
        super().__init__()
        self.model_ = QStandardItemModel(0, len(headers))
        self.model_.setHorizontalHeaderLabels(headers)
        self.setModel(self.model_)
        self.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection if multi else QAbstractItemView.SelectionMode.SingleSelection)
        self.setAlternatingRowColors(True)
        self.verticalHeader().hide()
        self.verticalHeader().setDefaultSectionSize(30)
        self.horizontalHeader().setStretchLastSection(True)
        self.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        self.setWordWrap(False)
        self.doubleClicked.connect(lambda idx: self.activated_row.emit(self.row_data(idx.row())))
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)

    def set_rows(self, rows: list[tuple[object, list[str]]], muted: Callable[[object], bool] | None = None,
                 align_right: set[int] | None = None) -> None:
        from PySide6.QtGui import QBrush, QColor

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
                    it.setForeground(QBrush(QColor("#8A94A1")))
            self.model_.appendRow(items)

    def row_data(self, row: int):
        it = self.model_.item(row, 0)
        return it.data(Qt.ItemDataRole.UserRole) if it else None

    def selected_data(self) -> list:
        rows = sorted({i.row() for i in self.selectionModel().selectedRows()})
        return [self.row_data(r) for r in rows]

    def current_data(self):
        sel = self.selected_data()
        return sel[0] if sel else None


def card() -> QFrame:
    f = QFrame()
    f.setObjectName("card")
    return f


def hline() -> QFrame:
    f = QFrame()
    f.setFrameShape(QFrame.Shape.HLine)
    f.setStyleSheet("color: #D5DCE4;")
    return f
