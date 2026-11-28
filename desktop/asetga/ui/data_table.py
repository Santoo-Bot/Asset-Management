"""Tabel data lengkap: kotak cari di bawah setiap judul kolom, urut dengan klik judul, urutan kolom bisa digeser (drag),
kolom bisa dipilih (ceklis), lebar kolom mengikuti isi sehingga tidak ada teks terpotong. Pengaturan disimpan per laptop."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from PySide6.QtCore import QSortFilterProxyModel, Qt, Signal
from PySide6.QtGui import QStandardItem, QStandardItemModel
from PySide6.QtWidgets import QAbstractItemView, QHeaderView, QLineEdit, QMenu, QSizePolicy, QTableView

from . import theme
from .widgets import _Delegate

SORT_ROLE = Qt.ItemDataRole.UserRole + 10


@dataclass(frozen=True)
class Column:
    key: str
    title: str
    get: Callable[[object], str]
    sort: Callable[[object], object] | None = None   # nilai urut (angka/tanggal); None = teks
    visible: bool = True                              # tampil secara bawaan
    right: bool = False
    chip: bool = False


class FilterHeader(QHeaderView):
    """Judul kolom + kotak cari di bawahnya, ikut bergeser saat kolom diubah ukuran/urutannya."""

    filter_changed = Signal(int, str)

    def __init__(self, parent: QTableView):
        super().__init__(Qt.Orientation.Horizontal, parent)
        self.editors: list[QLineEdit] = []
        self.pad = 6
        self.setSectionsMovable(True)
        self.setSectionsClickable(True)
        self.setSortIndicatorShown(True)
        self.setHighlightSections(False)
        self.setDefaultAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.sectionResized.connect(lambda *_: self.adjust())
        self.sectionMoved.connect(lambda *_: self.adjust())
        parent.horizontalScrollBar().valueChanged.connect(lambda *_: self.adjust())

    def set_editors(self, count: int) -> None:
        for e in self.editors:
            e.deleteLater()
        self.editors = []
        for i in range(count):
            e = QLineEdit(self)
            e.setPlaceholderText("Cari…")
            e.setClearButtonEnabled(True)
            e.setStyleSheet("QLineEdit { padding: 3px 6px; border-radius: 7px; min-height: 16px; font-weight: 400; }")
            e.textChanged.connect(lambda text, col=i: self.filter_changed.emit(col, text))
            self.editors.append(e)
        self.updateGeometries()

    def _editor_height(self) -> int:
        return self.editors[0].sizeHint().height() if self.editors else 0

    def sizeHint(self):  # noqa: N802 - nama dari Qt
        s = super().sizeHint()
        if self.editors:
            s.setHeight(s.height() + self._editor_height() + self.pad * 2)
        return s

    def updateGeometries(self):  # noqa: N802 - nama dari Qt
        if self.editors:
            self.setViewportMargins(0, 0, 0, self._editor_height() + self.pad * 2)
        else:
            self.setViewportMargins(0, 0, 0, 0)
        super().updateGeometries()
        self.adjust()

    def adjust(self) -> None:
        if not self.editors:
            return
        top = super().sizeHint().height() + self.pad
        h = self._editor_height()
        for logical, e in enumerate(self.editors):
            if self.isSectionHidden(logical):
                e.hide()
                continue
            e.setGeometry(self.sectionViewportPosition(logical) + 4, top, max(30, self.sectionSize(logical) - 8), h)
            e.show()


class _Proxy(QSortFilterProxyModel):
    def __init__(self):
        super().__init__()
        self.filters: dict[int, str] = {}
        self.setSortRole(SORT_ROLE)
        self.setSortCaseSensitivity(Qt.CaseSensitivity.CaseInsensitive)

    def set_filter(self, col: int, text: str) -> None:
        t = text.strip().lower()
        if t:
            self.filters[col] = t
        else:
            self.filters.pop(col, None)
        self.invalidateFilter()

    def filterAcceptsRow(self, row, parent):  # noqa: N802 - nama dari Qt
        m = self.sourceModel()
        for col, text in self.filters.items():
            v = m.index(row, col, parent).data(Qt.ItemDataRole.DisplayRole) or ""
            if text not in v.lower():
                return False
        return True


class DataTable(QTableView):
    """Tabel data dengan pencarian per kolom. Data baris di UserRole kolom pertama (sumber)."""

    activated_row = Signal(object)
    filtered = Signal()

    def __init__(self, columns: list[Column], prefs=None, prefs_key: str = "", empty_text: str = "Tidak ada data"):
        super().__init__()
        self.columns = columns
        self.prefs = prefs
        self.prefs_key = prefs_key
        self.empty_text = empty_text
        self.chip_columns = {i for i, c in enumerate(columns) if c.chip}
        self.bold_first = True
        self.write_off_name: str | None = None
        self.source = QStandardItemModel(0, len(columns))
        self.source.setHorizontalHeaderLabels([c.title.upper() for c in columns])
        self.proxy = _Proxy()
        self.proxy.setSourceModel(self.source)
        self.setModel(self.proxy)
        self.header_ = FilterHeader(self)
        self.setHorizontalHeader(self.header_)
        self.header_.set_editors(len(columns))
        self.header_.filter_changed.connect(self._filter)
        self.header_.sectionMoved.connect(lambda *_: self.save_state())
        self.setItemDelegate(_Delegate(self))
        self.setMouseTracking(True)
        self.setShowGrid(False)
        self.setSortingEnabled(True)
        self.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.setSelectionBehavior(QAbstractItemView.SelectionBehavior.SelectRows)
        self.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        self.setHorizontalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        self.setWordWrap(False)
        self.verticalHeader().hide()
        self.verticalHeader().setDefaultSectionSize(42)
        self.header_.setStretchLastSection(False)
        self.header_.setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self.doubleClicked.connect(lambda idx: self.activated_row.emit(self.row_data(idx.row())))
        self._restore_state()

    # ------------------------------------------------------------ data
    def set_rows(self, rows: list[tuple[object, object]], muted: Callable[[object], bool] | None = None) -> None:
        """[rows] = (data, objek baris) — nilai tiap kolom diambil lewat Column.get."""
        from PySide6.QtGui import QBrush, QColor

        sort_col, order = self.header_.sortIndicatorSection(), self.header_.sortIndicatorOrder()
        self.setSortingEnabled(False)
        self.source.setRowCount(0)
        gray = QBrush(QColor(theme.c()["muted"]))
        for data, row in rows:
            items = []
            for c in self.columns:
                text = c.get(row) or ""
                it = QStandardItem(text)
                it.setData(c.sort(row) if c.sort else text.lower(), SORT_ROLE)
                if c.right:
                    it.setTextAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
                if muted and muted(data):
                    it.setForeground(gray)
                items.append(it)
            items[0].setData(data, Qt.ItemDataRole.UserRole)
            self.source.appendRow(items)
        self.setSortingEnabled(True)
        if 0 <= sort_col < len(self.columns):
            self.sortByColumn(sort_col, order)
        self.fit_columns()
        self.filtered.emit()

    def fit_columns(self) -> None:
        """Lebar kolom = isi terpanjang (judul, kotak cari, data) agar tidak ada yang terpotong."""
        self.resizeColumnsToContents()
        for i in range(len(self.columns)):
            self.setColumnWidth(i, max(self.columnWidth(i) + 14, 110))

    def sizeHintForColumn(self, column: int) -> int:  # noqa: N802 - nama dari Qt
        from PySide6.QtGui import QFont, QFontMetrics

        f = QFont(self.font())
        f.setWeight(QFont.Weight.DemiBold)
        fm = QFontMetrics(f)
        extra = 44 if column in self.chip_columns else 28
        n = min(self.proxy.rowCount(), 3000)
        widest = max((fm.horizontalAdvance(self.proxy.index(r, column).data() or "") for r in range(n)), default=0)
        head = fm.horizontalAdvance(self.columns[column].title.upper()) + 30
        return max(widest + extra, head)

    def _filter(self, col: int, text: str) -> None:
        self.proxy.set_filter(col, text)
        self.filtered.emit()

    def clear_filters(self) -> None:
        for e in self.header_.editors:
            e.blockSignals(True)
            e.clear()
            e.blockSignals(False)
        self.proxy.filters.clear()
        self.proxy.invalidateFilter()
        self.filtered.emit()

    def row_data(self, proxy_row: int):
        src = self.proxy.mapToSource(self.proxy.index(proxy_row, 0))
        it = self.source.item(src.row(), 0)
        return it.data(Qt.ItemDataRole.UserRole) if it else None

    def visible_data(self) -> list:
        return [self.row_data(r) for r in range(self.proxy.rowCount())]

    def selected_data(self) -> list:
        sm = self.selectionModel()
        if sm is None:
            return []
        return [self.row_data(r) for r in sorted({i.row() for i in sm.selectedRows()})]

    def current_data(self):
        sel = self.selected_data()
        return sel[0] if sel else None

    def paintEvent(self, event):  # noqa: N802 - nama dari Qt
        super().paintEvent(event)
        if self.proxy.rowCount() == 0 and self.empty_text:
            from PySide6.QtGui import QColor, QPainter

            p = QPainter(self.viewport())
            p.setPen(QColor(theme.c()["muted"]))
            p.drawText(self.viewport().rect().adjusted(0, 24, 0, 0), int(Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop),
                       self.empty_text)
            p.end()

    # ------------------------------------------------------------ kolom
    def column_menu(self, parent) -> QMenu:
        """Menu ceklis kolom yang ditampilkan + kembalikan bawaan."""
        menu = QMenu(parent)
        menu.setToolTipsVisible(True)
        for i, c in enumerate(self.columns):
            act = menu.addAction(c.title)
            act.setCheckable(True)
            act.setChecked(not self.isColumnHidden(i))
            act.toggled.connect(lambda on, col=i: self.set_column_visible(col, on))
            if i == 0:
                act.setEnabled(False)  # kolom pertama (kunci) selalu tampil
        menu.addSeparator()
        menu.addAction("Kembalikan susunan bawaan", self.reset_columns)
        return menu

    def set_column_visible(self, col: int, on: bool) -> None:
        self.setColumnHidden(col, not on)
        if on:
            self.resizeColumnToContents(col)
            self.setColumnWidth(col, max(self.columnWidth(col) + 14, 110))
        self.header_.adjust()
        self.save_state()

    def reset_columns(self) -> None:
        for i, c in enumerate(self.columns):
            vis = self.header_.visualIndex(i)
            if vis != i:
                self.header_.moveSection(vis, i)
            self.setColumnHidden(i, not c.visible)
        self.fit_columns()
        self.header_.adjust()
        self.save_state()

    def save_state(self) -> None:
        if not self.prefs or not self.prefs_key:
            return
        order = [self.header_.logicalIndex(v) for v in range(len(self.columns))]
        self.prefs.set_pref(f"{self.prefs_key}/order", ",".join(self.columns[i].key for i in order))
        self.prefs.set_pref(f"{self.prefs_key}/hidden", ",".join(c.key for i, c in enumerate(self.columns) if self.isColumnHidden(i)))

    def _restore_state(self) -> None:
        keys = [c.key for c in self.columns]
        hidden = None
        order = None
        if self.prefs and self.prefs_key:
            h = self.prefs.pref(f"{self.prefs_key}/hidden", "\0")
            hidden = None if h == "\0" else set(filter(None, h.split(",")))
            o = self.prefs.pref(f"{self.prefs_key}/order", "")
            order = [k for k in o.split(",") if k in keys] if o else None
        for i, c in enumerate(self.columns):
            self.setColumnHidden(i, (c.key in hidden) if hidden is not None else not c.visible)
        self.setColumnHidden(0, False)
        if order:
            # Kolom baru (belum ada di urutan tersimpan) ditaruh di akhir.
            order += [k for k in keys if k not in order]
            for target, key in enumerate(order):
                logical = keys.index(key)
                self.header_.moveSection(self.header_.visualIndex(logical), target)

