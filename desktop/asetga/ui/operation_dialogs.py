"""Form mutasi dan servis (dibuka dari detail aset)."""

from __future__ import annotations

from dataclasses import replace

from PySide6.QtWidgets import QLineEdit, QPlainTextEdit

from ..core import fmt
from ..core import operations as O
from ..core.models import MasterType, ServiceType
from ..core.operations import ServiceInput
from .asset_form import PhotoBox
from .widgets import Combo, DateField, FormDialog, MoneyField, label


class MutationDialog(FormDialog):
    """Mutasi hanya lokasi dan pengguna / detail lokasi; data aset ikut diperbarui."""

    def __init__(self, parent, ctx, asset_id: str):
        super().__init__(parent, "Mutasi aset", "Simpan mutasi")
        self.ctx, self.asset_id = ctx, asset_id
        a = ctx.assets.get_row(asset_id)
        loc_name = ctx.db.scalar("SELECT name FROM locations WHERE id = ?", (a["locationId"],))
        self.body.insertWidget(1, label(f"{a['code']} — {a['name']}\nSaat ini: {loc_name} · {a['userLocationDetail'] or '-'}", "info"))
        f = self.form
        self.date = f.add("Tanggal mutasi", DateField(), O.FIELD_DATE)
        self.location = Combo()
        self.location.set_items([(x["name"], x["id"]) for x in ctx.masters.list(MasterType.LOCATION)])
        self.location.set_value(a["locationId"])
        f.add("Lokasi tujuan", self.location, O.FIELD_TARGET)
        self.user_loc = f.add("Pengguna / detail lokasi tujuan", QLineEdit(a["userLocationDetail"] or ""), hint="Mis. Budi · Ruang 301")

    def save(self) -> None:
        self.ctx.ops.record_mutation(self.asset_id, self.date.value(), self.location.value(), self.user_loc.text())


class ServiceDialog(FormDialog):
    def __init__(self, parent, ctx, asset_id: str, service_id: str | None = None):
        super().__init__(parent, "Ubah catatan servis" if service_id else "Catat servis")
        self.ctx = ctx
        self.setMinimumWidth(520)
        row = ctx.ops.get_service(service_id) if service_id else None
        self.base = O.OperationService.service_input(row) if row else ServiceInput(asset_id)
        a = ctx.assets.get_row(asset_id)
        self.body.insertWidget(1, label(f"{a['code']} — {a['name']}", "info"))
        f = self.form
        self.photo = f.add("Foto", PhotoBox(ctx))
        self.photo.set_path(self.base.photo_path)
        self.date = f.add("Tanggal servis", DateField(), O.FIELD_DATE)
        self.type = Combo()
        self.type.set_items([(t.label, t.name) for t in ServiceType])
        f.add("Jenis", self.type)
        self.vendor = Combo("— Tidak diisi —")
        self.vendor.set_items([(x["name"], x["id"]) for x in ctx.masters.list(MasterType.VENDOR)])
        f.add("Vendor", self.vendor)
        self.cost = f.add("Biaya (Rp)", MoneyField(), O.FIELD_COST)
        self.desc = QPlainTextEdit()
        self.desc.setFixedHeight(80)
        f.add("Deskripsi pekerjaan", self.desc, O.FIELD_DESCRIPTION)
        self.next = f.add("Jadwal servis berikutnya", DateField(optional=True), O.FIELD_NEXT)
        if row:
            self.date.set_value(self.base.service_date)
            self.type.set_value(self.base.type.name)
            self.vendor.set_value(self.base.vendor_id)
            self.cost.set_value(self.base.cost)
            self.desc.setPlainText(self.base.description)
            self.next.set_value(self.base.next_service_date)
        else:
            self.cost.set_value(0)

    def save(self) -> None:
        self.ctx.ops.save_service(replace(
            self.base, service_date=self.date.value(), type=ServiceType[self.type.value()], vendor_id=self.vendor.value(),
            cost=self.cost.value() if self.cost.text().strip() else 0, description=self.desc.toPlainText(),
            photo_path=self.photo.path, next_service_date=self.next.value(),
        ))


def service_cells(s) -> list[str]:
    nxt = fmt.from_epoch_day(s["nextServiceDate"])
    return [fmt.date_numeric(fmt.from_epoch_day(s["serviceDate"])), ServiceType[s["type"]].label, s["vendorName"] or "-",
            fmt.rupiah(s["cost"]), s["description"], fmt.date_numeric(nxt) if nxt else "-"]
