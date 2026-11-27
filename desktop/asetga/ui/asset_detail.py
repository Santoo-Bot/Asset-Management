"""Detail aset: foto 1:1, data lengkap, nilai buku & penyusutan, riwayat perubahan."""

from __future__ import annotations

from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QDialog, QDialogButtonBox, QGridLayout, QGroupBox, QHBoxLayout, QLabel, QLineEdit, QScrollArea, QTabWidget, QVBoxLayout, QWidget,
)

from ..core import fmt
from ..core.audit import changes_text
from ..core.models import WRITE_OFF_ID, AssetCondition, parse_accessories
from .asset_form import AssetFormDialog
from .widgets import DateField, FormDialog, MoneyField, Table, button, label, primary


class WriteOffDialog(FormDialog):
    def __init__(self, parent, ctx, asset_id: str):
        super().__init__(parent, "Tandai Write Off", "Write Off")
        self.ctx = ctx
        self.asset_id = asset_id
        self.body.insertWidget(0, label("Aset Write Off berhenti disusutkan dan tidak ikut dashboard, stock opname, dan asuransi. "
                                        "Data aset tetap tersimpan.", "info"))
        self.date = self.form.add("Tanggal write off", DateField())
        self.value = self.form.add("Nilai jual (Rp)", MoneyField(), hint="Kosongkan bila tidak dijual")
        self.note = self.form.add("Catatan", QLineEdit())

    def save(self) -> None:
        self.ctx.assets.write_off(self.asset_id, self.date.value(), self.value.value(), self.note.text())


class AssetDetailDialog(QDialog):
    def __init__(self, parent, ctx, asset_id: str):
        super().__init__(parent)
        self.ctx = ctx
        self.asset_id = asset_id
        self.changed = False
        self.setMinimumSize(900, 640)
        self.lay = QVBoxLayout(self)
        self.content = QWidget()
        self.lay.addWidget(self.content, 1)
        bb = QDialogButtonBox()
        role = ctx.session.role
        if role and role.can_edit_data:
            self.edit_btn = primary("Ubah")
            self.edit_btn.clicked.connect(self._edit)
            bb.addButton(self.edit_btn, QDialogButtonBox.ButtonRole.ActionRole)
            self.wo_btn = button("Write Off…", danger=True)
            self.wo_btn.clicked.connect(self._write_off)
            bb.addButton(self.wo_btn, QDialogButtonBox.ButtonRole.ActionRole)
        else:
            self.wo_btn = None
        bb.addButton("Tutup", QDialogButtonBox.ButtonRole.RejectRole)
        bb.rejected.connect(self.reject)
        self.lay.addWidget(bb)
        self._render()

    def _render(self) -> None:
        d = self.ctx.assets.detail(self.asset_id)
        if d is None:
            self.reject()
            return
        r = d.row
        old = self.content
        self.content = QWidget()
        self.lay.replaceWidget(old, self.content)
        old.deleteLater()
        is_wo = r["status"] == WRITE_OFF_ID
        self.setWindowTitle(f"{r['code']} — {r['name']}")
        if self.wo_btn is not None:
            self.wo_btn.setVisible(not is_wo)

        root = QHBoxLayout(self.content)
        # ---- kiri: foto 1:1 + ringkasan nilai
        left = QVBoxLayout()
        photo = QLabel()
        photo.setFixedSize(240, 240)
        photo.setAlignment(Qt.AlignmentFlag.AlignCenter)
        photo.setStyleSheet("border: 1px solid #D5DCE4; border-radius: 10px;")
        f = self.ctx.photos.resolve(r["photoPath"])
        if f:
            pm = QPixmap(str(f)).scaled(240, 240, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
            photo.setPixmap(pm.copy((pm.width() - 240) // 2, (pm.height() - 240) // 2, 240, 240))
        else:
            photo.setText("Tidak ada foto")
        left.addWidget(photo)
        title = label(r["name"], "title")
        left.addWidget(title)
        left.addWidget(label(f"{r['code']}  ·  {r['statusName'] or r['status']}" + ("  (Write Off)" if is_wo and r["statusName"] != "Write Off" else ""), "subtitle"))
        val = QGroupBox("Nilai & penyusutan")
        g = QGridLayout(val)
        y, mth = d.age
        rows = [
            ("Harga perolehan", fmt.rupiah(r["acquisitionCost"])),
            ("Nilai buku", fmt.rupiah(d.dep.book_value)),
            ("Akumulasi penyusutan", fmt.rupiah(d.dep.accumulated)),
            ("Penyusutan / tahun", fmt.rupiah(d.dep.annual_depreciation)),
            ("Umur ekonomis", f"{r['usefulLifeYears']} th"),
            ("Residu", fmt.percent(d.residual_percent)),
            ("Umur berjalan", fmt.age(y, mth)),
        ]
        if is_wo:
            rows.append(("Penyusutan berhenti", fmt.date(fmt.from_epoch_day(r["disposalDate"]))))
            if r["disposalValue"] is not None:
                rows.append(("Nilai jual", fmt.rupiah(r["disposalValue"])))
        for i, (k, v) in enumerate(rows):
            g.addWidget(label(k, "muted"), i, 0)
            vv = label(v)
            vv.setAlignment(Qt.AlignmentFlag.AlignRight)
            g.addWidget(vv, i, 1)
        left.addWidget(val)
        left.addStretch(1)
        root.addLayout(left)

        # ---- kanan: tab data & riwayat
        tabs = QTabWidget()
        d_ = lambda v: fmt.date(fmt.from_epoch_day(v)) if v is not None else "-"  # noqa: E731
        o = lambda v: v or "-"  # noqa: E731
        info = [
            ("Identitas", [
                ("Kode aset", r["code"]), ("Kode QR", r["qrCode"] or r["code"]), ("Kategori", r["categoryName"]),
                ("No. seri", o(r["serialNumber"])), ("Kondisi", AssetCondition[r["condition"]].label),
                ("Status", r["statusName"] or r["status"]),
                ("Kelengkapan", ", ".join(parse_accessories(r["accessories"])) or "-"),
                ("Merk/Tipe (lama)", o(r["brandModel"])), ("PIC (lama)", o(r["picName"])),
            ]),
            ("Penempatan", [
                ("Lokasi", r["locationName"]), ("Pengguna / detail lokasi", o(r["userLocationDetail"])),
                ("Divisi", o(r["divisionName"])), ("Departemen", o(r["departmentName"])),
            ]),
            ("Pembelian", [
                ("Tgl perolehan", d_(r["acquisitionDate"])), ("Harga perolehan", fmt.rupiah(r["acquisitionCost"])),
                ("Vendor", o(r["vendorName"])), ("Garansi s/d", d_(r["warrantyUntil"]) + f"  ({d.warranty})"),
                ("Nomor PR", o(r["prNumber"])), ("Nomor PO", o(r["poNumber"])), ("Nomor GR", o(r["grNumber"])),
                ("Pemohon pembelian", o(r["requester"])), ("Nomor memo", o(r["memoNumber"])), ("Tanggal memo", d_(r["memoDate"])),
                ("Tindak lanjut", o(r["followUp"])), ("Berita acara serah terima", o(r["handoverDoc"])),
                ("Catatan", o(r["notes"])),
            ]),
        ]
        data_tab = QScrollArea()
        data_tab.setWidgetResizable(True)
        inner = QWidget()
        iv = QVBoxLayout(inner)
        for section, pairs in info:
            box = QGroupBox(section)
            gl = QGridLayout(box)
            gl.setColumnStretch(1, 1)
            for i, (k, v) in enumerate(pairs):
                gl.addWidget(label(k, "muted"), i, 0, alignment=Qt.AlignmentFlag.AlignTop)
                vl = label(str(v))
                vl.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
                gl.addWidget(vl, i, 1)
            iv.addWidget(box)
        iv.addStretch(1)
        data_tab.setWidget(inner)
        tabs.addTab(data_tab, "Data aset")

        hist = Table(["Waktu", "Oleh", "Aksi", "Ringkasan", "Perubahan (lama → baru)"])
        hist.setWordWrap(True)
        from ..core.models import AuditAction

        hist.set_rows([
            (h["id"], [fmt.date_time(h["timestamp"]), h["userName"], AuditAction[h["action"]].label, h["summary"], changes_text(h["changesJson"])])
            for h in self.ctx.assets.history(self.asset_id)
        ])
        hist.resizeRowsToContents()
        for i, w in enumerate((130, 120, 70, 220)):
            hist.setColumnWidth(i, w)
        tabs.addTab(hist, "Riwayat perubahan")
        root.addWidget(tabs, 1)

    def _edit(self) -> None:
        dlg = AssetFormDialog(self, self.ctx, self.asset_id)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.changed = True
            self._render()

    def _write_off(self) -> None:
        if WriteOffDialog(self, self.ctx, self.asset_id).exec() == QDialog.DialogCode.Accepted:
            self.changed = True
            self._render()
