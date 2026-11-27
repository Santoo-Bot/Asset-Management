"""Master data. Hapus = permanen, hanya bila tidak dipakai. Write Off tidak bisa dihapus."""

from __future__ import annotations

from PySide6.QtWidgets import QDialog, QDoubleSpinBox, QHBoxLayout, QLineEdit, QSpinBox, QTabWidget, QVBoxLayout, QWidget

from ..core import fmt
from ..core import masters as M
from ..core.models import WRITE_OFF_ID, MasterType
from .widgets import Combo, FormDialog, Table, button, confirm, info, label, primary, run_safely

COLUMNS = {
    MasterType.CATEGORY: ["Nama", "Kode", "Umur ekonomis", "Residu"],
    MasterType.LOCATION: ["Nama", "Alamat"],
    MasterType.DIVISION: ["Nama"],
    MasterType.DEPARTMENT: ["Nama", "Divisi"],
    MasterType.VENDOR: ["Nama", "Telepon", "Email", "Alamat"],
    MasterType.UNIT: ["Nama"],
    MasterType.INSURANCE_TYPE: ["Nama"],
    MasterType.INSURER: ["Nama", "Telepon", "Email", "Alamat"],
    MasterType.STATUS: ["Nama", "Keterangan"],
}


def _cells(t: MasterType, r) -> list[str]:
    if t is MasterType.CATEGORY:
        res = "Ikuti pengaturan" if r["residualPercent"] is None else fmt.percent(r["residualPercent"])
        return [r["name"], r["codePrefix"], f"{r['usefulLifeYears']} th", res]
    if t is MasterType.LOCATION:
        return [r["name"], r["address"] or ""]
    if t is MasterType.DEPARTMENT:
        return [r["name"], r["divisionName"] or "-"]
    if t in (MasterType.VENDOR, MasterType.INSURER):
        return [r["name"], r["phone"] or "", r["email"] or "", r["address"] or ""]
    if t is MasterType.STATUS:
        return [r["name"], "Status sistem: tidak bisa dihapus, tidak ikut dashboard/opname/asuransi" if r["id"] == WRITE_OFF_ID else ""]
    return [r["name"]]


class MasterDialog(FormDialog):
    def __init__(self, parent, ctx, t: MasterType, row=None):
        super().__init__(parent, f"{'Ubah' if row else 'Tambah'} {t.singular}")
        self.ctx, self.t, self.row = ctx, t, row
        f = self.form
        self.name = f.add("Nama", QLineEdit(row["name"] if row else ""), M.FIELD_NAME)
        if t is MasterType.CATEGORY:
            self.prefix = f.add("Kode prefix", QLineEdit(row["codePrefix"] if row else ""), M.FIELD_PREFIX,
                                "2–5 huruf kapital, dipakai untuk kode aset (mis. IT-2024-001)")
            self.life = QSpinBox()
            self.life.setRange(1, 50)
            self.life.setSuffix(" tahun")
            self.life.setValue(row["usefulLifeYears"] if row else 4)
            f.add("Umur ekonomis", self.life, M.FIELD_LIFE)
            self.residual = QDoubleSpinBox()
            self.residual.setRange(-1, 99.99)
            self.residual.setDecimals(2)
            self.residual.setSuffix(" %")
            self.residual.setSpecialValueText("Ikuti pengaturan umum")
            self.residual.setValue(row["residualPercent"] if row and row["residualPercent"] is not None else -1)
            f.add("Nilai residu", self.residual, M.FIELD_RESIDUAL)
        elif t is MasterType.LOCATION:
            self.address = f.add("Alamat", QLineEdit(row["address"] or "" if row else ""))
        elif t is MasterType.DEPARTMENT:
            self.division = Combo()
            self.division.set_items([(d["name"], d["id"]) for d in ctx.masters.list(MasterType.DIVISION)])
            if row:
                self.division.set_value(row["divisionId"])
            f.add("Divisi induk", self.division, M.FIELD_DIVISION)
        elif t in (MasterType.VENDOR, MasterType.INSURER):
            self.phone = f.add("Telepon", QLineEdit(row["phone"] or "" if row else ""), M.FIELD_PHONE)
            self.email = f.add("Email", QLineEdit(row["email"] or "" if row else ""), M.FIELD_EMAIL)
            self.address = f.add("Alamat", QLineEdit(row["address"] or "" if row else ""))
        elif t is MasterType.STATUS and row and row["id"] == WRITE_OFF_ID:
            self.body.insertWidget(0, label("Write Off adalah status sistem. Namanya boleh diubah, tetapi tidak bisa dihapus.", "info"))

    def save(self) -> None:
        m, t = self.ctx.masters, self.t
        rid = self.row["id"] if self.row else ""
        name = self.name.text()
        if t is MasterType.CATEGORY:
            res = self.residual.value()
            m.save_category(rid, name, self.prefix.text(), self.life.value(), None if res < 0 else res)
        elif t is MasterType.LOCATION:
            m.save_location(rid, name, self.address.text())
        elif t is MasterType.DEPARTMENT:
            m.save_department(rid, name, self.division.value())
        elif t in (MasterType.VENDOR, MasterType.INSURER):
            m.save_contact(t, rid, name, self.phone.text(), self.email.text(), self.address.text())
        elif t is MasterType.STATUS:
            m.save_status(rid, name)
        else:
            m.save_simple(t, rid, name)


class MasterTab(QWidget):
    def __init__(self, ctx, t: MasterType):
        super().__init__()
        self.ctx, self.t = ctx, t
        v = QVBoxLayout(self)
        bar = QHBoxLayout()
        self.add_btn = primary(f"+ Tambah {t.singular}")
        self.add_btn.clicked.connect(self._add)
        self.edit_btn = button("Ubah")
        self.edit_btn.clicked.connect(self._edit)
        self.del_btn = button("Hapus", danger=True)
        self.del_btn.clicked.connect(self._delete)
        bar.addWidget(self.add_btn)
        bar.addWidget(self.edit_btn)
        bar.addWidget(self.del_btn)
        bar.addStretch(1)
        self.count = label("", "muted")
        bar.addWidget(self.count)
        v.addLayout(bar)
        self.table = Table(COLUMNS[t])
        self.table.activated_row.connect(lambda _: self._edit())
        v.addWidget(self.table, 1)

    def refresh(self) -> None:
        admin = bool(self.ctx.session.role and self.ctx.session.role.can_manage_master)
        for b in (self.add_btn, self.edit_btn, self.del_btn):
            b.setVisible(admin)
        rows = self.ctx.masters.list(self.t)
        self.table.set_rows([(r, _cells(self.t, r)) for r in rows])
        self.table.setColumnWidth(0, 260)
        self.count.setText(f"{len(rows)} data")

    def _add(self) -> None:
        if MasterDialog(self, self.ctx, self.t).exec() == QDialog.DialogCode.Accepted:
            self.refresh()

    def _edit(self) -> None:
        if not (self.ctx.session.role and self.ctx.session.role.can_manage_master):
            return
        row = self.table.current_data()
        if row is None:
            return
        if MasterDialog(self, self.ctx, self.t, row).exec() == QDialog.DialogCode.Accepted:
            self.refresh()

    def _delete(self) -> None:
        row = self.table.current_data()
        if row is None:
            info(self, f"Pilih {self.t.singular} yang akan dihapus.")
            return
        if not confirm(self, f"Hapus {self.t.singular} \"{row['name']}\" secara permanen?", "Hapus", yes="Hapus", danger=True):
            return
        if run_safely(self, lambda: self.ctx.masters.delete(self.t, row["id"]) or True):
            self.refresh()


class MastersPage(QWidget):
    def __init__(self, ctx):
        super().__init__()
        v = QVBoxLayout(self)
        v.addWidget(label("Master data", "title"))
        self.tabs = QTabWidget()
        self.tab_list = [MasterTab(ctx, t) for t in MasterType]
        for tab in self.tab_list:
            self.tabs.addTab(tab, tab.t.label)
        self.tabs.currentChanged.connect(lambda i: self.tab_list[i].refresh())
        v.addWidget(self.tabs, 1)

    def on_show(self) -> None:
        self.tab_list[self.tabs.currentIndex()].refresh()
