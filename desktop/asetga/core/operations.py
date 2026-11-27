"""Mutasi (lokasi & pengguna/detail lokasi) dan servis aset. Aturan sama dengan OperationRepositoryImpl Android."""

from __future__ import annotations

import datetime as _dt
from dataclasses import dataclass

from . import fmt
from .audit import AuditLog, ChangeSet
from .db import Database, new_id
from .models import WRITE_OFF_ID, AppError, AuditAction, AuditEntityType, ServiceType
from .session import Session

FIELD_DATE = "date"
FIELD_TARGET = "target"
FIELD_DESCRIPTION = "description"
FIELD_COST = "cost"
FIELD_NEXT = "nextServiceDate"

SERVICE_DUE_SOON_DAYS = 30


def _trim(v: str | None) -> str | None:
    v = (v or "").strip()
    return v or None


def service_due_status(next_date: _dt.date | None, today: _dt.date) -> str:
    if next_date is None:
        return "Belum dijadwalkan"
    if next_date < today:
        return "Terlambat"
    if (next_date - today).days <= SERVICE_DUE_SOON_DAYS:
        return "Jatuh tempo"
    return "Terjadwal"


@dataclass(frozen=True)
class ServiceInput:
    asset_id: str
    id: str | None = None
    service_date: _dt.date | None = None
    type: ServiceType = ServiceType.PREVENTIF
    vendor_id: str | None = None
    cost: int | None = 0
    description: str = ""
    photo_path: str | None = None
    next_service_date: _dt.date | None = None


def validate_service(inp: ServiceInput, today: _dt.date) -> dict[str, str]:
    e: dict[str, str] = {}
    if inp.service_date is None:
        e[FIELD_DATE] = "Tanggal servis wajib diisi"
    elif inp.service_date > today:
        e[FIELD_DATE] = "Tanggal servis tidak boleh di masa depan"
    if not inp.description.strip():
        e[FIELD_DESCRIPTION] = "Deskripsi pekerjaan wajib diisi"
    if inp.cost is None or inp.cost < 0:
        e[FIELD_COST] = "Biaya tidak valid"
    if inp.next_service_date and inp.service_date and inp.next_service_date <= inp.service_date:
        e[FIELD_NEXT] = "Jadwal berikutnya harus setelah tanggal servis"
    return e


SERVICE_SELECT = """
    SELECT s.*, a.code AS assetCode, a.name AS assetName, v.name AS vendorName, u.fullName AS createdBy
    FROM service_records s JOIN assets a ON a.id = s.assetId
    LEFT JOIN vendors v ON v.id = s.vendorId LEFT JOIN users u ON u.id = s.createdByUserId
"""


class OperationService:
    def __init__(self, db: Database, session: Session, audit: AuditLog):
        self.db = db
        self.session = session
        self.audit = audit

    # ------------------------------------------------------------ mutasi
    def record_mutation(self, asset_id: str, date: _dt.date | None, to_location_id: str | None, to_user_location: str) -> None:
        """Mutasi hanya mencatat lokasi dan pengguna / detail lokasi. Data aset ikut diperbarui."""
        self.session.require(lambda r: r.can_edit_data)
        with self.db.tx():
            a = self.db.one("SELECT * FROM assets WHERE id = ?", (asset_id,))
            if a is None:
                raise AppError("Aset tidak ditemukan")
            errors: dict[str, str] = {}
            today = fmt.today()
            acq = fmt.from_epoch_day(a["acquisitionDate"])
            if date is None:
                errors[FIELD_DATE] = "Tanggal wajib diisi"
            elif date > today:
                errors[FIELD_DATE] = "Tanggal tidak boleh di masa depan"
            elif date < acq:
                errors[FIELD_DATE] = "Tanggal sebelum tanggal perolehan aset"
            to_user = _trim(to_user_location)
            if to_location_id is None:
                errors[FIELD_TARGET] = "Pilih lokasi tujuan"
            elif to_location_id == a["locationId"] and to_user == a["userLocationDetail"]:
                errors[FIELD_TARGET] = "Ubah lokasi atau pengguna / detail lokasi tujuan"
            if errors:
                raise AppError("Periksa kembali isian", errors)
            to_name = self.db.scalar("SELECT name FROM locations WHERE id = ?", (to_location_id,))
            if to_name is None:
                raise AppError("Lokasi tujuan tidak ditemukan")
            from_name = self.db.scalar("SELECT name FROM locations WHERE id = ?", (a["locationId"],))
            now = fmt.now_ms()
            u = self.session.user
            self.db.exec(
                "INSERT INTO asset_mutations (id, assetId, mutationDate, fromLocationId, toLocationId, fromDepartmentId, toDepartmentId, "
                "fromPic, toPic, reason, approvedBy, createdByUserId, createdAt, updatedAt, deletedAt, syncState, fromUserLocation, toUserLocation) "
                "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, '', NULL, ?, ?, ?, NULL, 'PENDING', ?, ?)",
                (new_id(), asset_id, fmt.to_epoch_day(date), a["locationId"], to_location_id, u.id if u else None, now, now,
                 a["userLocationDetail"], to_user),
            )
            self.db.exec("UPDATE assets SET locationId = ?, userLocationDetail = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                         (to_location_id, to_user, now, asset_id))
            ch = ChangeSet().field("Lokasi", from_name, to_name).field("Pengguna / detail lokasi", a["userLocationDetail"], to_user)
            self.audit.log(AuditEntityType.ASSET, asset_id, AuditAction.UPDATE, f"Mutasi aset {a['code']}", ch)

    def mutations(self, asset_id: str):
        return self.db.q(
            "SELECT m.*, lf.name AS fromLocation, lt.name AS toLocation, u.fullName AS createdBy FROM asset_mutations m "
            "LEFT JOIN locations lf ON lf.id = m.fromLocationId LEFT JOIN locations lt ON lt.id = m.toLocationId "
            "LEFT JOIN users u ON u.id = m.createdByUserId WHERE m.assetId = ? AND m.deletedAt IS NULL "
            "ORDER BY m.mutationDate DESC, m.createdAt DESC",
            (asset_id,),
        )

    # ------------------------------------------------------------ servis
    def services_for_asset(self, asset_id: str):
        return self.db.q(SERVICE_SELECT + " WHERE s.assetId = ? AND s.deletedAt IS NULL ORDER BY s.serviceDate DESC, s.createdAt DESC",
                         (asset_id,))

    def services(self, query: str = ""):
        q = query.strip()
        return self.db.q(
            SERVICE_SELECT + " WHERE s.deletedAt IS NULL AND (? = '' OR instr(lower(a.code), lower(?)) > 0 OR instr(lower(a.name), lower(?)) > 0 "
            "OR instr(lower(v.name), lower(?)) > 0 OR instr(lower(s.description), lower(?)) > 0) ORDER BY s.serviceDate DESC, s.createdAt DESC",
            (q, q, q, q, q),
        )

    def get_service(self, service_id: str):
        return self.db.one(SERVICE_SELECT + " WHERE s.id = ?", (service_id,))

    def schedules(self):
        """Jadwal servis berikutnya per aset (dari catatan servis terakhir), aset Write Off tidak ikut."""
        today = fmt.today()
        rows = self.db.q(
            "SELECT a.id AS assetId, a.code AS assetCode, a.name AS assetName, s.serviceDate AS lastServiceDate, "
            "s.nextServiceDate AS nextServiceDate FROM assets a JOIN service_records s ON s.assetId = a.id "
            f"WHERE a.deletedAt IS NULL AND a.status != '{WRITE_OFF_ID}' AND s.nextServiceDate IS NOT NULL "
            "AND s.id = (SELECT s2.id FROM service_records s2 WHERE s2.assetId = a.id AND s2.deletedAt IS NULL "
            "ORDER BY s2.serviceDate DESC, s2.createdAt DESC LIMIT 1) ORDER BY s.nextServiceDate ASC"
        )
        return [(r, service_due_status(fmt.from_epoch_day(r["nextServiceDate"]), today)) for r in rows]

    def save_service(self, inp: ServiceInput) -> str:
        self.session.require(lambda r: r.can_edit_data)
        errors = validate_service(inp, fmt.today())
        if errors:
            raise AppError("Periksa kembali isian", errors)
        asset = self.db.one("SELECT id, code FROM assets WHERE id = ?", (inp.asset_id,))
        if asset is None:
            raise AppError("Aset tidak ditemukan")
        if inp.vendor_id and not self.db.scalar("SELECT 1 FROM vendors WHERE id = ?", (inp.vendor_id,)):
            raise AppError("Vendor tidak ditemukan")
        now = fmt.now_ms()
        old = self.db.one("SELECT * FROM service_records WHERE id = ?", (inp.id,)) if inp.id else None
        desc = inp.description.strip()
        cost = inp.cost or 0
        ed = fmt.to_epoch_day
        with self.db.tx():
            if old is None:
                sid = new_id()
                u = self.session.user
                self.db.exec(
                    "INSERT INTO service_records (id, assetId, serviceDate, type, vendorId, cost, description, photoPath, nextServiceDate, "
                    "createdByUserId, createdAt, updatedAt, deletedAt, syncState) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'PENDING')",
                    (sid, asset["id"], ed(inp.service_date), inp.type.name, inp.vendor_id, cost, desc, inp.photo_path,
                     ed(inp.next_service_date), u.id if u else None, now, now),
                )
                self.audit.log(AuditEntityType.SERVICE, sid, AuditAction.CREATE,
                               f"Servis {inp.type.label} {asset['code']} {fmt.date(inp.service_date)} · {fmt.rupiah(cost)}")
            else:
                sid = old["id"]
                self.db.exec(
                    "UPDATE service_records SET assetId = ?, serviceDate = ?, type = ?, vendorId = ?, cost = ?, description = ?, photoPath = ?, "
                    "nextServiceDate = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                    (asset["id"], ed(inp.service_date), inp.type.name, inp.vendor_id, cost, desc, inp.photo_path,
                     ed(inp.next_service_date), now, sid),
                )
                d = lambda v: fmt.date(fmt.from_epoch_day(v)) if v is not None else None  # noqa: E731
                ch = ChangeSet().field("Tanggal", d(old["serviceDate"]), fmt.date(inp.service_date)) \
                    .field("Jenis", ServiceType[old["type"]].label, inp.type.label) \
                    .field("Biaya", fmt.rupiah(old["cost"]), fmt.rupiah(cost)) \
                    .field("Deskripsi", old["description"], desc) \
                    .field("Jadwal berikutnya", d(old["nextServiceDate"]), fmt.date(inp.next_service_date) if inp.next_service_date else None)
                self.audit.log(AuditEntityType.SERVICE, sid, AuditAction.UPDATE, f"Catatan servis {asset['code']} diubah", ch)
            # Tampilkan juga di riwayat aset.
            self.audit.log(AuditEntityType.ASSET, asset["id"], AuditAction.CREATE if old is None else AuditAction.UPDATE,
                           f"Servis {inp.type.label}: {desc[:60]}")
        return sid

    @staticmethod
    def service_input(row) -> ServiceInput:
        d = fmt.from_epoch_day
        return ServiceInput(asset_id=row["assetId"], id=row["id"], service_date=d(row["serviceDate"]), type=ServiceType[row["type"]],
                            vendor_id=row["vendorId"], cost=row["cost"], description=row["description"], photo_path=row["photoPath"],
                            next_service_date=d(row["nextServiceDate"]))

