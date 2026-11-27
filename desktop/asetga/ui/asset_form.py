"""Form tambah/ubah aset. Foto di paling atas (sama dengan Android)."""

from __future__ import annotations

from dataclasses import replace

from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import QCheckBox, QFileDialog, QHBoxLayout, QLabel, QLineEdit, QPlainTextEdit, QScrollArea, QVBoxLayout, QWidget

from ..core import assets as A
from ..core.assets import AssetInput
from ..core.models import WRITE_OFF_ID, AssetCondition, MasterType
from .widgets import Combo, DateField, FormDialog, MoneyField, button

IMAGE_FILTER = "Gambar (*.jpg *.jpeg *.png *.bmp *.webp *.heic);;Semua file (*)"


def _text(multiline: bool = False):
    if multiline:
        e = QPlainTextEdit()
        e.setFixedHeight(64)
        return e
    return QLineEdit()


def _get(w) -> str:
    return w.toPlainText() if isinstance(w, QPlainTextEdit) else w.text()


def _set(w, v: str) -> None:
    (w.setPlainText if isinstance(w, QPlainTextEdit) else w.setText)(v or "")


class PhotoBox(QWidget):
    def __init__(self, ctx):
        super().__init__()
        self.ctx = ctx
        self.path: str | None = None
        h = QHBoxLayout(self)
        h.setContentsMargins(0, 0, 0, 0)
        self.preview = QLabel("Belum ada foto")
        self.preview.setFixedSize(140, 140)
        self.preview.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.preview.setStyleSheet("border: 1px dashed #9AA6B4; border-radius: 8px;")
        h.addWidget(self.preview)
        v = QVBoxLayout()
        choose = button("Pilih foto…")
        choose.clicked.connect(self._choose)
        self.remove = button("Hapus foto")
        self.remove.clicked.connect(lambda: self.set_path(None))
        v.addWidget(choose)
        v.addWidget(self.remove)
        v.addStretch(1)
        h.addLayout(v)
        h.addStretch(1)

    def set_path(self, rel: str | None) -> None:
        self.path = rel
        f = self.ctx.photos.resolve(rel)
        if f:
            pm = QPixmap(str(f)).scaled(140, 140, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
            self.preview.setPixmap(pm.copy((pm.width() - 140) // 2, (pm.height() - 140) // 2, 140, 140))
        else:
            self.preview.setPixmap(QPixmap())
            self.preview.setText("Belum ada foto")
        self.remove.setEnabled(rel is not None)

    def _choose(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "Pilih foto aset", self.ctx.last_dir(), IMAGE_FILTER)
        if not path:
            return
        self.ctx.remember_dir(path)
        try:
            self.set_path(self.ctx.photos.import_compressed(path))
        except Exception:  # noqa: BLE001 - format gambar tidak didukung / file rusak
            from .widgets import error

            error(self, "Gambar tidak dapat dibaca. Gunakan file JPG atau PNG.")


class AssetFormDialog(FormDialog):
    def __init__(self, parent, ctx, asset_id: str | None = None):
        super().__init__(parent, "Ubah aset" if asset_id else "Tambah aset")
        self.ctx = ctx
        self.original = ctx.assets.get(asset_id) if asset_id else None
        self.saved_id: str | None = None
        self.setMinimumSize(640, 640)

        # Form panjang → dibungkus scroll.
        self.body.removeWidget(self.form)
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setWidget(self.form)
        self.body.insertWidget(2, scroll, 1)

        f = self.form
        m = ctx.masters
        self.photo = f.add("Foto", PhotoBox(ctx))
        f.add_section("Identitas")
        self.code = f.add("Kode aset", QLineEdit(), A.FIELD_CODE, "Kosongkan untuk dibuat otomatis (PREFIX-TAHUN-NNN)")
        self.qr_same = QCheckBox("Kode QR sama dengan kode aset")
        self.qr = QLineEdit()
        qr_box = QWidget()
        qv = QVBoxLayout(qr_box)
        qv.setContentsMargins(0, 0, 0, 0)
        qv.addWidget(self.qr_same)
        qv.addWidget(self.qr)
        self.qr_same.toggled.connect(lambda on: self.qr.setEnabled(not on))
        f.add("Kode QR", qr_box, A.FIELD_QR)
        self.name = f.add("Nama aset", QLineEdit(), A.FIELD_NAME)
        self.category = f.add("Kategori", Combo(), A.FIELD_CATEGORY)
        self.category.set_items([(c["name"], c["id"]) for c in m.list(MasterType.CATEGORY)])
        self.serial = f.add("No. seri", QLineEdit())
        self.condition = f.add("Kondisi", Combo())
        self.condition.set_items([(c.label, c.name) for c in AssetCondition])
        self.status = f.add("Status", Combo(), A.FIELD_STATUS)
        self.status.set_items([(s["name"], s["id"]) for s in m.list(MasterType.STATUS)])
        self.accessories = f.add("Kelengkapan", QLineEdit(), hint="Pisahkan dengan koma, mis. Charger, Tas laptop")

        f.add_section("Penempatan")
        self.location = f.add("Lokasi", Combo(), A.FIELD_LOCATION)
        self.location.set_items([(x["name"], x["id"]) for x in m.list(MasterType.LOCATION)])
        self.user_location = f.add("Pengguna / detail lokasi", QLineEdit(), hint="Mis. Budi · Ruang 301")
        self.division = f.add("Divisi", Combo("— Tidak diisi —"))
        self.division.set_items([(x["name"], x["id"]) for x in m.list(MasterType.DIVISION)])
        self.departments = m.list(MasterType.DEPARTMENT)
        self.department = f.add("Departemen", Combo("— Tidak diisi —"))
        self.department.set_items([(f"{d['name']}" + (f" — {d['divisionName']}" if d["divisionName"] else ""), d["id"]) for d in self.departments])
        self.department.currentIndexChanged.connect(self._department_changed)
        self.division.currentIndexChanged.connect(self._division_changed)

        f.add_section("Pembelian")
        self.acq_date = f.add("Tgl perolehan", DateField(), A.FIELD_ACQ_DATE)
        self.cost = f.add("Harga perolehan (Rp)", MoneyField(), A.FIELD_COST)
        self.vendor = f.add("Vendor", Combo("— Tidak diisi —"))
        self.vendor.set_items([(x["name"], x["id"]) for x in m.list(MasterType.VENDOR)])
        self.warranty = f.add("Garansi s/d", DateField(optional=True), A.FIELD_WARRANTY)
        self.pr = f.add("Nomor PR", QLineEdit())
        self.po = f.add("Nomor PO", QLineEdit())
        self.gr = f.add("Nomor GR", QLineEdit())
        self.requester = f.add("Pemohon pembelian", QLineEdit())
        self.memo_no = f.add("Nomor memo", QLineEdit())
        self.memo_date = f.add("Tanggal memo", DateField(optional=True))
        self.follow_up = f.add("Tindak lanjut", QLineEdit())
        self.handover = f.add("Berita acara serah terima", QLineEdit())
        self.notes = f.add("Catatan", _text(multiline=True))

        self._load()

    def _department_changed(self) -> None:
        dep = self.department.value()
        if dep:
            div = next((d["divisionId"] for d in self.departments if d["id"] == dep), None)
            if div:
                self.division.blockSignals(True)
                self.division.set_value(div)
                self.division.blockSignals(False)

    def _division_changed(self) -> None:
        div, dep = self.division.value(), self.department.value()
        if dep and div and next((d["divisionId"] for d in self.departments if d["id"] == dep), None) != div:
            self.department.blockSignals(True)
            self.department.set_value(None)
            self.department.blockSignals(False)

    def _load(self) -> None:
        a = self.original
        if a is None:
            self.qr_same.setChecked(True)
            self.photo.set_path(None)
            self.status.set_value("DIGUNAKAN")
            return
        self.photo.set_path(a.photo_path)
        self.code.setText(a.code)
        same = a.qr_code.upper() == a.code.upper()
        self.qr_same.setChecked(same)
        self.qr.setText("" if same else a.qr_code)
        self.name.setText(a.name)
        self.category.set_value(a.category_id)
        self.serial.setText(a.serial_number)
        self.condition.set_value(a.condition.name)
        self.status.set_value(a.status)
        self.accessories.setText(a.accessories)
        self.location.set_value(a.location_id)
        self.user_location.setText(a.user_location_detail)
        self.division.set_value(a.division_id)
        self.department.set_value(a.department_id)
        self.acq_date.set_value(a.acquisition_date)
        self.cost.set_value(a.acquisition_cost)
        self.vendor.set_value(a.vendor_id)
        self.warranty.set_value(a.warranty_until)
        for w, v in ((self.pr, a.pr_number), (self.po, a.po_number), (self.gr, a.gr_number), (self.requester, a.requester),
                     (self.memo_no, a.memo_number), (self.follow_up, a.follow_up), (self.handover, a.handover_doc)):
            w.setText(v)
        self.memo_date.set_value(a.memo_date)
        _set(self.notes, a.notes)

    def save(self) -> None:
        base = self.original or AssetInput()
        status = self.status.value() or "DIGUNAKAN"
        inp = replace(
            base,
            code=self.code.text(), qr_code=self.qr.text(), name=self.name.text(), category_id=self.category.value(),
            serial_number=self.serial.text(), condition=AssetCondition[self.condition.value() or "BAIK"], status=status,
            accessories=self.accessories.text(), location_id=self.location.value(), user_location_detail=self.user_location.text(),
            division_id=self.division.value(), department_id=self.department.value(), acquisition_date=self.acq_date.value(),
            acquisition_cost=self.cost.value(), vendor_id=self.vendor.value(), warranty_until=self.warranty.value(),
            pr_number=self.pr.text(), po_number=self.po.text(), gr_number=self.gr.text(), requester=self.requester.text(),
            memo_number=self.memo_no.text(), memo_date=self.memo_date.value(), follow_up=self.follow_up.text(),
            handover_doc=self.handover.text(), notes=_get(self.notes), photo_path=self.photo.path,
            # Status diubah ke Write Off lewat form: tanggal penghapusan = hari ini (bila belum ada).
            disposal_date=base.disposal_date if status == WRITE_OFF_ID else None,
        )
        self.saved_id = self.ctx.assets.save(inp, qr_same_as_code=self.qr_same.isChecked())
