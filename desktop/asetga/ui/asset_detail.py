"""Detail aset sebagai panel di sisi kanan daftar (juga bisa dibuka sebagai jendela dari Scan/Servis):
foto, status, nilai buku & penyusutan, data lengkap, mutasi, servis, riwayat perubahan, dan aksi."""

from __future__ import annotations

from PySide6.QtCore import QSize, Qt, Signal
from PySide6.QtGui import QColor, QFont, QPixmap
from PySide6.QtWidgets import (
    QDialog, QDialogButtonBox, QFrame, QGridLayout, QHBoxLayout, QLabel, QLineEdit, QListWidget, QListWidgetItem, QScrollArea,
    QStackedWidget, QStyledItemDelegate, QTabWidget, QVBoxLayout, QWidget,
)

from ..core import fmt
from ..core.audit import changes_text
from ..core.models import WRITE_OFF_ID, AssetCondition, AuditAction, ServiceType, parse_accessories
from . import icons, theme
from .asset_form import AssetFormDialog
from .widgets import DateField, FormDialog, MoneyField, button, label, primary

PHOTO = 180


class WriteOffDialog(FormDialog):
    def __init__(self, parent, ctx, asset_id: str):
        super().__init__(parent, "Tandai Write Off", "Write Off")
        self.ctx = ctx
        self.asset_id = asset_id
        self.body.insertWidget(1, label("Aset Write Off berhenti disusutkan dan tidak ikut dashboard, stock opname, dan asuransi. "
                                        "Data aset tetap tersimpan.", "info"))
        self.date = self.form.add("Tanggal write off", DateField())
        self.value = self.form.add("Nilai jual (Rp)", MoneyField(), hint="Kosongkan bila tidak dijual")
        self.note = self.form.add("Catatan", QLineEdit())

    def save(self) -> None:
        self.ctx.assets.write_off(self.asset_id, self.date.value(), self.value.value(), self.note.text())


def chip(text: str, write_off_name: str | None = None) -> QLabel:
    bg, fg = theme.chip_colors(text, write_off_name)
    lb = QLabel(text)
    lb.setStyleSheet(f"background: {bg}; color: {fg}; border-radius: 11px; padding: 3px 10px; font-weight: 600; font-size: 9pt;")
    return lb


class _EntryDelegate(QStyledItemDelegate):
    """Baris dua tingkat: judul tebal + keterangan abu-abu (bisa beberapa baris)."""

    def paint(self, painter, option, index):
        from PySide6.QtWidgets import QStyle

        k = theme.c()
        painter.save()
        hover = bool(option.state & QStyle.StateFlag.State_MouseOver)
        if hover and index.data(Qt.ItemDataRole.UserRole + 2):
            painter.fillRect(option.rect, QColor(k["hover"]))
        painter.fillRect(option.rect.adjusted(0, option.rect.height() - 1, 0, 0), QColor(k["border"]))
        r = option.rect.adjusted(4, 8, -4, -8)
        f = QFont(option.font)
        f.setWeight(QFont.Weight.DemiBold)
        painter.setFont(f)
        painter.setPen(QColor(k["text"]))
        title = index.data(Qt.ItemDataRole.DisplayRole) or ""
        flags = int(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignTop) | int(Qt.TextFlag.TextWordWrap)
        painter.drawText(r, flags, title)
        tr = painter.boundingRect(r, flags, title)
        sub = index.data(Qt.ItemDataRole.UserRole + 1) or ""
        f2 = QFont(option.font)
        f2.setPointSizeF(max(8.0, option.font.pointSizeF() - 0.8))
        painter.setFont(f2)
        painter.setPen(QColor(k["muted"]))
        painter.drawText(r.adjusted(0, tr.height() + 3, 0, 0), flags, sub)
        painter.restore()

    def sizeHint(self, option, index):  # noqa: N802 - nama dari Qt
        from PySide6.QtGui import QFontMetrics

        view = self.parent()
        w = max(200, (view.viewport().width() if view else 340)) - 8
        flags = int(Qt.TextFlag.TextWordWrap)
        f = QFont(option.font)
        f.setWeight(QFont.Weight.DemiBold)
        th = QFontMetrics(f).boundingRect(0, 0, w, 2000, flags, index.data(Qt.ItemDataRole.DisplayRole) or "").height()
        f2 = QFont(option.font)
        f2.setPointSizeF(max(8.0, option.font.pointSizeF() - 0.8))
        sh = QFontMetrics(f2).boundingRect(0, 0, w, 4000, flags, index.data(Qt.ItemDataRole.UserRole + 1) or "").height()
        return QSize(w, th + sh + 20)


class EntryList(QListWidget):
    activated_entry = Signal(object)

    def __init__(self, empty_text: str):
        super().__init__()
        self.empty_text = empty_text
        self.setItemDelegate(_EntryDelegate(self))
        self.setMouseTracking(True)
        self.setWordWrap(True)
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setStyleSheet("QListWidget { background: transparent; border: none; outline: 0; }")
        self.setVerticalScrollMode(QListWidget.ScrollMode.ScrollPerPixel)
        self.setResizeMode(QListWidget.ResizeMode.Adjust)
        self.itemDoubleClicked.connect(lambda it: self.activated_entry.emit(it.data(Qt.ItemDataRole.UserRole)))

    def set_entries(self, entries: list[tuple[object, str, str]], clickable: bool = False) -> None:
        self.clear()
        for data, title, sub in entries:
            it = QListWidgetItem(title)
            it.setData(Qt.ItemDataRole.UserRole, data)
            it.setData(Qt.ItemDataRole.UserRole + 1, sub)
            it.setData(Qt.ItemDataRole.UserRole + 2, clickable)
            self.addItem(it)

    def paintEvent(self, event):  # noqa: N802 - nama dari Qt
        super().paintEvent(event)
        if self.count() == 0:
            from PySide6.QtGui import QPainter

            p = QPainter(self.viewport())
            p.setPen(QColor(theme.c()["muted"]))
            p.drawText(self.viewport().rect().adjusted(0, 20, 0, 0), int(Qt.AlignmentFlag.AlignHCenter | Qt.AlignmentFlag.AlignTop),
                       self.empty_text)
            p.end()


class AssetDetailPanel(QFrame):
    """Panel detail. Sinyal [changed] dipancarkan setelah aset diubah (untuk menyegarkan daftar)."""

    changed = Signal()

    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        self.asset_id: str | None = None
        self.setObjectName("card")
        outer = QVBoxLayout(self)
        outer.setContentsMargins(1, 1, 1, 1)
        self.stack = QStackedWidget()
        outer.addWidget(self.stack)
        empty = QWidget()
        ev = QVBoxLayout(empty)
        ev.addStretch(1)
        self.empty_icon = QLabel()
        self.empty_icon.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ev.addWidget(self.empty_icon)
        t = label("Pilih aset di daftar\nuntuk melihat detailnya", "muted")
        t.setAlignment(Qt.AlignmentFlag.AlignCenter)
        ev.addWidget(t)
        ev.addStretch(2)
        self.stack.addWidget(empty)
        self.content = QWidget()
        self.stack.addWidget(self.content)
        self.render()

    # ------------------------------------------------------------------
    def show_asset(self, asset_id: str | None) -> None:
        self.asset_id = asset_id
        self.render()

    def render(self) -> None:
        d = self.ctx.assets.detail(self.asset_id) if self.asset_id else None
        if d is None:
            self.asset_id = None
            self.empty_icon.setPixmap(icons.icon("kosong", theme.c()["muted"], 40, 1.4).pixmap(40, 40))
            self.stack.setCurrentIndex(0)
            return
        r = d.row
        old = self.content
        self.content = QWidget()
        self.stack.addWidget(self.content)
        self.stack.setCurrentWidget(self.content)
        self.stack.removeWidget(old)
        old.deleteLater()
        k = theme.c()
        is_wo = r["status"] == WRITE_OFF_ID
        wo_name = self.ctx.masters.status_names().get(WRITE_OFF_ID)
        role = self.ctx.session.role
        can_edit = bool(role and role.can_edit_data)

        root = QVBoxLayout(self.content)
        root.setContentsMargins(0, 0, 0, 0)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        inner = QWidget()
        v = QVBoxLayout(inner)
        v.setContentsMargins(0, 0, 0, 12)
        v.setSpacing(12)

        # Foto 1:1 di atas pita abu-abu.
        band = QFrame()
        band.setStyleSheet(f"QFrame {{ background: {k['photo']}; border-top-left-radius: 13px; border-top-right-radius: 13px; }}")
        bl = QHBoxLayout(band)
        bl.setContentsMargins(0, 14, 0, 14)
        photo = QLabel()
        photo.setFixedSize(PHOTO, PHOTO)
        photo.setAlignment(Qt.AlignmentFlag.AlignCenter)
        f = self.ctx.photos.resolve(r["photoPath"])
        if f:
            pm = QPixmap(str(f)).scaled(PHOTO, PHOTO, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
            photo.setPixmap(pm.copy((pm.width() - PHOTO) // 2, (pm.height() - PHOTO) // 2, PHOTO, PHOTO))
        else:
            photo.setPixmap(icons.icon("foto", k["muted"], 36, 1.4).pixmap(36, 36))
            photo.setStyleSheet(f"background: {k['surface']}; border-radius: 12px;")
        bl.addStretch(1)
        bl.addWidget(photo)
        bl.addStretch(1)
        v.addWidget(band)

        body = QVBoxLayout()
        body.setContentsMargins(18, 2, 18, 0)
        body.setSpacing(10)
        chips = QHBoxLayout()
        chips.setSpacing(6)
        chips.addWidget(chip(r["statusName"] or r["status"], wo_name))
        chips.addWidget(chip(AssetCondition[r["condition"]].label))
        chips.addStretch(1)
        body.addLayout(chips)
        name = label(r["name"], "h2")
        name.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        body.addWidget(name)
        body.addWidget(label(f"{r['code']}  ·  {r['categoryName']}", "muted"))

        acts = QGridLayout()
        acts.setSpacing(8)
        b_lbl = button("Label QR", icon_name="qr")
        b_lbl.clicked.connect(self._label)
        if can_edit:
            b_edit = primary("Ubah", "ubah")
            b_edit.clicked.connect(self._edit)
            b_mut = button("Mutasi", icon_name="mutasi")
            b_mut.clicked.connect(self._mutation)
            b_svc = button("Catat servis", icon_name="servis")
            b_svc.clicked.connect(lambda: self._service(None))
            acts.addWidget(b_edit, 0, 0)
            acts.addWidget(b_mut, 0, 1)
            acts.addWidget(b_svc, 1, 0)
            acts.addWidget(b_lbl, 1, 1)
            if not is_wo:
                b_wo = button("Write Off…", danger=True)
                b_wo.clicked.connect(self._write_off)
                acts.addWidget(b_wo, 2, 0, 1, 2)
        else:
            acts.addWidget(b_lbl, 0, 0)
        body.addLayout(acts)

        tabs = QTabWidget()
        tabs.setDocumentMode(True)
        tabs.addTab(self._data_tab(d), "Data")
        muts = self.ctx.ops.mutations(self.asset_id)
        mut_list = EntryList("Belum ada mutasi")
        mut_list.set_entries([
            (m["id"], f"{m['fromLocation'] or '-'}  →  {m['toLocation'] or '-'}",
             f"{fmt.date(fmt.from_epoch_day(m['mutationDate']))} · {m['createdBy'] or '-'}\n"
             f"Pengguna: {m['fromUserLocation'] or '-'} → {m['toUserLocation'] or '-'}")
            for m in muts
        ])
        tabs.addTab(mut_list, f"Mutasi ({len(muts)})")
        svcs = self.ctx.ops.services_for_asset(self.asset_id)
        svc_list = EntryList("Belum ada catatan servis")
        svc_list.set_entries([
            (x["id"], f"{ServiceType[x['type']].label} · {fmt.rupiah(x['cost'])}",
             fmt.date(fmt.from_epoch_day(x["serviceDate"])) + (f" · {x['vendorName']}" if x["vendorName"] else "") + f"\n{x['description']}"
             + (f"\nBerikutnya: {fmt.date(fmt.from_epoch_day(x['nextServiceDate']))}" if x["nextServiceDate"] else ""))
            for x in svcs
        ], clickable=can_edit)
        if can_edit:
            svc_list.activated_entry.connect(self._service)
            svc_list.setToolTip("Klik dua kali untuk mengubah catatan servis")
        tabs.addTab(svc_list, f"Servis ({len(svcs)})")
        hist = EntryList("Belum ada riwayat")
        hist.set_entries([
            (h["id"], h["summary"], f"{fmt.date_time(h['timestamp'])} · {h['userName']} · {AuditAction[h['action']].label}"
             + (f"\n{changes_text(h['changesJson'])}" if h["changesJson"] else ""))
            for h in self.ctx.assets.history(self.asset_id)
        ])
        tabs.addTab(hist, "Riwayat")
        tabs.setMinimumHeight(440)
        body.addWidget(tabs, 1)
        v.addLayout(body, 1)
        scroll.setWidget(inner)
        root.addWidget(scroll)

    def _data_tab(self, d) -> QWidget:
        r = d.row
        w = QWidget()
        v = QVBoxLayout(w)
        v.setContentsMargins(0, 10, 0, 0)
        v.setSpacing(10)
        boxes = QHBoxLayout()
        boxes.setSpacing(8)
        for title, value in (("Nilai buku", fmt.rupiah(d.dep.book_value)), ("Harga perolehan", fmt.rupiah(r["acquisitionCost"]))):
            b = QFrame()
            b.setObjectName("soft")
            bv = QVBoxLayout(b)
            bv.setContentsMargins(12, 9, 12, 9)
            bv.setSpacing(1)
            bv.addWidget(label(title, "small", wrap=False))
            val = label(value, None, wrap=False)
            val.setStyleSheet("font-weight: 700; font-size: 11pt;")
            bv.addWidget(val)
            boxes.addWidget(b)
        v.addLayout(boxes)
        y, m = d.age
        o = lambda x: x or "-"  # noqa: E731
        dd = lambda x: fmt.date(fmt.from_epoch_day(x)) if x is not None else "-"  # noqa: E731
        wo = r["status"] == WRITE_OFF_ID
        dep = [("Akumulasi", fmt.rupiah(d.dep.accumulated)), ("Per tahun", fmt.rupiah(d.dep.annual_depreciation)),
               ("Umur ekonomis", f"{r['usefulLifeYears']} th"), ("Residu", fmt.percent(d.residual_percent)), ("Umur berjalan", fmt.age(y, m))]
        if wo:
            dep.append(("Berhenti", dd(r["disposalDate"])))
            if r["disposalValue"] is not None:
                dep.append(("Nilai jual", fmt.rupiah(r["disposalValue"])))
        ident = [("Kode QR", r["qrCode"] or r["code"]), ("No. seri", o(r["serialNumber"])),
                 ("Kelengkapan", ", ".join(parse_accessories(r["accessories"])) or "-"), ("Catatan", o(r["notes"]))]
        if r["brandModel"]:
            ident.append(("Merk/Tipe (lama)", r["brandModel"]))
        if r["picName"]:
            ident.append(("PIC (lama)", r["picName"]))
        sections = [
            ("PENEMPATAN", [("Lokasi", r["locationName"]), ("Pengguna", o(r["userLocationDetail"])), ("Divisi", o(r["divisionName"])),
                            ("Departemen", o(r["departmentName"]))]),
            ("PENYUSUTAN", dep),
            ("PEMBELIAN", [("Tgl perolehan", dd(r["acquisitionDate"])), ("Vendor", o(r["vendorName"])),
                           ("Garansi s/d", dd(r["warrantyUntil"]) + (f"  ({d.warranty})" if r["warrantyUntil"] else "")),
                           ("Nomor PR", o(r["prNumber"])), ("Nomor PO", o(r["poNumber"])), ("Nomor GR", o(r["grNumber"])),
                           ("Pemohon", o(r["requester"])), ("Nomor memo", o(r["memoNumber"])), ("Tanggal memo", dd(r["memoDate"])),
                           ("Tindak lanjut", o(r["followUp"])), ("BAST", o(r["handoverDoc"]))]),
            ("IDENTITAS", ident),
        ]
        for title, pairs in sections:
            v.addSpacing(4)
            v.addWidget(label(title, "section", wrap=False))
            g = QGridLayout()
            g.setHorizontalSpacing(12)
            g.setVerticalSpacing(6)
            g.setColumnStretch(1, 1)
            g.setColumnMinimumWidth(0, 110)
            for i, (key, val) in enumerate(pairs):
                g.addWidget(label(key, "muted", wrap=False), i, 0, alignment=Qt.AlignmentFlag.AlignTop)
                vl = label(str(val))
                vl.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
                g.addWidget(vl, i, 1)
            v.addLayout(g)
        v.addStretch(1)
        return w

    # ------------------------------------------------------------------
    def _done(self) -> None:
        self.render()
        self.changed.emit()

    def _edit(self) -> None:
        from .widgets import notify

        if AssetFormDialog(self, self.ctx, self.asset_id).exec() == QDialog.DialogCode.Accepted:
            self._done()
            notify(self, "Perubahan aset tersimpan")

    def _label(self) -> None:
        from .label_dialog import LabelDialog, labels_for_assets

        LabelDialog(self, self.ctx, labels_for_assets(self.ctx, [self.asset_id])).exec()

    def _mutation(self) -> None:
        from .operation_dialogs import MutationDialog
        from .widgets import notify

        if MutationDialog(self, self.ctx, self.asset_id).exec() == QDialog.DialogCode.Accepted:
            self._done()
            notify(self, "Mutasi tersimpan")

    def _service(self, service_id) -> None:
        from .operation_dialogs import ServiceDialog
        from .widgets import notify

        if ServiceDialog(self, self.ctx, self.asset_id, service_id).exec() == QDialog.DialogCode.Accepted:
            self._done()
            notify(self, "Catatan servis tersimpan")

    def _write_off(self) -> None:
        from .widgets import notify

        if WriteOffDialog(self, self.ctx, self.asset_id).exec() == QDialog.DialogCode.Accepted:
            self._done()
            notify(self, "Aset ditandai Write Off")


class AssetDetailDialog(QDialog):
    """Panel detail dalam jendela sendiri (dipakai dari Scan dan Servis)."""

    def __init__(self, parent, ctx, asset_id: str):
        super().__init__(parent)
        self.ctx = ctx
        self.asset_id = asset_id
        self.changed = False
        self.setWindowTitle("Detail aset")
        self.resize(480, 780)
        v = QVBoxLayout(self)
        v.setContentsMargins(14, 14, 14, 14)
        self.panel = AssetDetailPanel(ctx)
        self.panel.changed.connect(self._mark)
        v.addWidget(self.panel, 1)
        bb = QDialogButtonBox()
        bb.addButton("Tutup", QDialogButtonBox.ButtonRole.RejectRole)
        bb.rejected.connect(self.reject)
        v.addWidget(bb)
        self._render()

    def _mark(self) -> None:
        self.changed = True

    def _render(self) -> None:
        self.panel.show_asset(self.asset_id)
        r = self.ctx.assets.get_row(self.asset_id)
        if r:
            self.setWindowTitle(f"{r['code']} — {r['name']}")
