"""Aset: daftar & filter, detail (nilai buku), simpan, Write Off, hapus permanen."""

from __future__ import annotations

import datetime as _dt
import re
from dataclasses import dataclass, field, replace

from . import calc, fmt
from .audit import AuditLog, ChangeSet
from .db import Database, new_id
from .models import WRITE_OFF_ID, AppError, AssetCondition, AuditAction, AuditEntityType, normalize_accessories
from .session import Session

FIELD_CODE = "code"
FIELD_NAME = "name"
FIELD_CATEGORY = "category"
FIELD_ACQ_DATE = "acquisitionDate"
FIELD_COST = "acquisitionCost"
FIELD_LOCATION = "location"
FIELD_WARRANTY = "warranty"
FIELD_DISPOSAL_DATE = "disposalDate"
FIELD_QR = "qrCode"
FIELD_STATUS = "status"

MAX_COST = 1_000_000_000_000_000  # Rp 1 kuadriliun
_CODE = re.compile(r"^[A-Z0-9][A-Z0-9./-]{1,39}$")


def normalize_code(code: str) -> str:
    return re.sub(r"\s+", "-", (code or "").strip().upper())


def _trim(v: str | None) -> str | None:
    v = (v or "").strip()
    return v or None


@dataclass(frozen=True)
class AssetInput:
    """Isian form/import. code kosong = dibuat otomatis. Teks kosong = tidak diisi."""

    id: str | None = None
    code: str = ""
    name: str = ""
    category_id: str | None = None
    brand_model: str = ""
    serial_number: str = ""
    acquisition_date: _dt.date | None = None
    acquisition_cost: int | None = None
    location_id: str | None = None
    department_id: str | None = None
    pic_name: str = ""
    condition: AssetCondition = AssetCondition.BAIK
    status: str = "DIGUNAKAN"
    warranty_until: _dt.date | None = None
    notes: str = ""
    photo_path: str | None = None
    disposal_date: _dt.date | None = None
    disposal_value: int | None = None
    qr_code: str = ""
    user_location_detail: str = ""
    division_id: str | None = None
    pr_number: str = ""
    po_number: str = ""
    gr_number: str = ""
    requester: str = ""
    memo_number: str = ""
    memo_date: _dt.date | None = None
    follow_up: str = ""
    handover_doc: str = ""
    accessories: str = ""
    vendor_id: str | None = None

    @staticmethod
    def from_row(a) -> "AssetInput":
        """Data aset tersimpan sebagai AssetInput (untuk form ubah & import mode perbarui)."""
        d = fmt.from_epoch_day
        return AssetInput(
            id=a["id"], code=a["code"], name=a["name"], category_id=a["categoryId"], brand_model=a["brandModel"] or "",
            serial_number=a["serialNumber"] or "", acquisition_date=d(a["acquisitionDate"]), acquisition_cost=a["acquisitionCost"],
            location_id=a["locationId"], department_id=a["departmentId"], pic_name=a["picName"] or "",
            condition=AssetCondition[a["condition"]], status=a["status"], warranty_until=d(a["warrantyUntil"]), notes=a["notes"] or "",
            photo_path=a["photoPath"], disposal_date=d(a["disposalDate"]), disposal_value=a["disposalValue"],
            qr_code=a["qrCode"] or a["code"], user_location_detail=a["userLocationDetail"] or "", division_id=a["divisionId"],
            pr_number=a["prNumber"] or "", po_number=a["poNumber"] or "", gr_number=a["grNumber"] or "",
            requester=a["requester"] or "", memo_number=a["memoNumber"] or "", memo_date=d(a["memoDate"]),
            follow_up=a["followUp"] or "", handover_doc=a["handoverDoc"] or "", accessories=a["accessories"] or "",
            vendor_id=a["vendorId"],
        )


def validate(inp: AssetInput, today: _dt.date) -> dict[str, str]:
    e: dict[str, str] = {}
    code = normalize_code(inp.code)
    if not code:
        e[FIELD_CODE] = "Kode aset wajib diisi"
    elif not _CODE.match(code):
        e[FIELD_CODE] = "Gunakan huruf, angka, titik, garis miring, atau tanda hubung"
    if not inp.name.strip():
        e[FIELD_NAME] = "Nama aset wajib diisi"
    elif len(inp.name.strip()) > 150:
        e[FIELD_NAME] = "Maksimal 150 karakter"
    if inp.category_id is None:
        e[FIELD_CATEGORY] = "Pilih kategori"
    if inp.location_id is None:
        e[FIELD_LOCATION] = "Pilih lokasi"
    if inp.acquisition_date is None:
        e[FIELD_ACQ_DATE] = "Tanggal perolehan wajib diisi"
    elif inp.acquisition_date > today:
        e[FIELD_ACQ_DATE] = "Tanggal perolehan tidak boleh di masa depan"
    if inp.acquisition_cost is None:
        e[FIELD_COST] = "Harga perolehan wajib diisi"
    elif inp.acquisition_cost < 0:
        e[FIELD_COST] = "Harga tidak boleh negatif"
    elif inp.acquisition_cost > MAX_COST:
        e[FIELD_COST] = "Harga terlalu besar"
    if inp.warranty_until and inp.acquisition_date and inp.warranty_until < inp.acquisition_date:
        e[FIELD_WARRANTY] = "Garansi tidak boleh sebelum tanggal perolehan"
    qr = inp.qr_code.strip()
    if not qr:
        e[FIELD_QR] = "Kode QR wajib diisi (boleh sama dengan kode aset)"
    elif len(qr) > 120:
        e[FIELD_QR] = "Kode QR maksimal 120 karakter"
    elif any(ord(c) < 32 or 127 <= ord(c) < 160 for c in qr):
        e[FIELD_QR] = "Kode QR berisi karakter tidak valid"
    if inp.status == WRITE_OFF_ID and inp.disposal_date and inp.acquisition_date and inp.disposal_date < inp.acquisition_date:
        e[FIELD_DISPOSAL_DATE] = "Tanggal penghapusan tidak boleh sebelum tanggal perolehan"
    return e


@dataclass
class AssetFilter:
    query: str = ""
    category_id: str | None = None
    location_id: str | None = None
    division_id: str | None = None
    department_id: str | None = None
    condition: str | None = None
    status: str | None = None
    sort: str = "CODE"
    exclude_write_off: bool = False


_FILTER_WHERE = """
    a.deletedAt IS NULL
    AND (:query = '' OR instr(lower(a.code), lower(:query)) > 0 OR instr(lower(a.name), lower(:query)) > 0
         OR instr(lower(a.serialNumber), lower(:query)) > 0 OR instr(lower(a.picName), lower(:query)) > 0
         OR instr(lower(a.brandModel), lower(:query)) > 0 OR instr(lower(a.qrCode), lower(:query)) > 0
         OR instr(lower(a.userLocationDetail), lower(:query)) > 0 OR instr(lower(a.prNumber), lower(:query)) > 0
         OR instr(lower(a.poNumber), lower(:query)) > 0 OR instr(lower(a.grNumber), lower(:query)) > 0
         OR instr(lower(a.accessories), lower(:query)) > 0)
    AND (:categoryId IS NULL OR a.categoryId = :categoryId)
    AND (:locationId IS NULL OR a.locationId = :locationId)
    AND (:divisionId IS NULL OR a.divisionId = :divisionId)
    AND (:departmentId IS NULL OR a.departmentId = :departmentId)
    AND (:condition IS NULL OR a.condition = :condition)
    AND (:status IS NULL OR a.status = :status)
    AND (:excludeWriteOff = 0 OR a.status != 'WRITE_OFF')
"""

_ORDER = {
    "CODE": "a.code ASC",
    "NAME": "a.name COLLATE NOCASE ASC, a.code ASC",
    "NEWEST": "a.acquisitionDate DESC, a.code ASC",
    "OLDEST": "a.acquisitionDate ASC, a.code ASC",
    "COST_DESC": "a.acquisitionCost DESC, a.code ASC",
}

DETAIL_SELECT = """
    SELECT a.*, c.name AS categoryName, c.usefulLifeYears AS usefulLifeYears, c.residualPercent AS categoryResidualPercent,
           l.name AS locationName, d.name AS departmentName, v.name AS divisionName, vd.name AS vendorName,
           s.name AS statusName
    FROM assets a
    JOIN categories c ON c.id = a.categoryId
    JOIN locations l ON l.id = a.locationId
    LEFT JOIN departments d ON d.id = a.departmentId
    LEFT JOIN divisions v ON v.id = a.divisionId
    LEFT JOIN vendors vd ON vd.id = a.vendorId
    LEFT JOIN asset_statuses s ON s.id = a.status
"""


@dataclass
class AssetDetail:
    row: object
    residual_percent: float
    age: tuple[int, int]
    dep: calc.Depreciation
    warranty: str
    extra: dict = field(default_factory=dict)


class AssetService:
    def __init__(self, db: Database, session: Session, audit: AuditLog):
        self.db = db
        self.session = session
        self.audit = audit

    # ------------------------------------------------------------ daftar
    @staticmethod
    def _params(f: AssetFilter) -> dict:
        return {
            "query": f.query.strip(), "categoryId": f.category_id, "locationId": f.location_id, "divisionId": f.division_id,
            "departmentId": f.department_id, "condition": f.condition, "status": f.status,
            "excludeWriteOff": 1 if f.exclude_write_off else 0,
        }

    def list(self, f: AssetFilter):
        return self.db.q(
            "SELECT a.id, a.code, a.name, a.qrCode, a.condition, a.status, a.acquisitionDate, a.acquisitionCost, a.picName, "
            "a.userLocationDetail, a.serialNumber, c.name AS categoryName, l.name AS locationName, v.name AS divisionName, "
            "d.name AS departmentName, s.name AS statusName "
            "FROM assets a JOIN categories c ON c.id = a.categoryId JOIN locations l ON l.id = a.locationId "
            "LEFT JOIN divisions v ON v.id = a.divisionId LEFT JOIN departments d ON d.id = a.departmentId "
            "LEFT JOIN asset_statuses s ON s.id = a.status "
            f"WHERE {_FILTER_WHERE} ORDER BY {_ORDER.get(f.sort, _ORDER['CODE'])}",
            self._params(f),
        )

    def get_row(self, asset_id: str):
        return self.db.one("SELECT * FROM assets WHERE id = ?", (asset_id,))

    def get(self, asset_id: str) -> AssetInput | None:
        r = self.get_row(asset_id)
        return AssetInput.from_row(r) if r else None

    def find_by_code(self, code: str):
        """Cocokkan kode aset atau kode QR (seperti hasil scan)."""
        c = code.strip()
        return self.db.one("SELECT * FROM assets WHERE code = ? COLLATE NOCASE OR qrCode = ? COLLATE NOCASE LIMIT 1", (c, c))

    def detail(self, asset_id: str) -> AssetDetail | None:
        r = self.db.one(DETAIL_SELECT + " WHERE a.id = ?", (asset_id,))
        if r is None:
            return None
        today = fmt.today()
        acq = fmt.from_epoch_day(r["acquisitionDate"])
        # Aset Write Off berhenti disusutkan pada tanggal penghapusan.
        as_of = (fmt.from_epoch_day(r["disposalDate"]) or today) if r["status"] == WRITE_OFF_ID else today
        residual = r["categoryResidualPercent"] if r["categoryResidualPercent"] is not None else self.db.default_residual_percent()
        return AssetDetail(
            row=r, residual_percent=residual, age=calc.asset_age(acq, as_of),
            dep=calc.depreciation(r["acquisitionCost"], acq, r["usefulLifeYears"], residual, as_of),
            warranty=calc.warranty_status(fmt.from_epoch_day(r["warrantyUntil"]), today),
        )

    def suggest_code(self, category_id: str, year: int) -> str:
        prefix = self.db.scalar("SELECT codePrefix FROM categories WHERE id = ?", (category_id,)) or "AST"
        codes = [r[0] for r in self.db.q("SELECT code FROM assets WHERE code LIKE ?", (f"{prefix}-{year}-%",))]
        return calc.format_asset_code(prefix, year, calc.next_sequence(prefix, year, codes))

    def _count_code_or_qr(self, value: str, exclude_id: str) -> int:
        return self.db.scalar(
            "SELECT COUNT(*) FROM assets WHERE (code = ?1 COLLATE NOCASE OR qrCode = ?1 COLLATE NOCASE) AND id != ?2",
            (value, exclude_id),
        ) or 0

    # ------------------------------------------------------------ simpan
    def save(self, inp: AssetInput, qr_same_as_code: bool = False) -> str:
        """Simpan aset baru/ubah. [qr_same_as_code] = Kode QR diisi sama dengan kode aset (juga bila kode dibuat otomatis)."""
        self.session.require(lambda r: r.can_edit_data)
        today = fmt.today()
        check = replace(inp, code=inp.code or "AUTO", qr_code="X" if qr_same_as_code else inp.qr_code)
        errors = validate(check, today)
        if errors:
            raise AppError("Periksa kembali isian", errors)
        if not self.db.scalar("SELECT 1 FROM categories WHERE id = ?", (inp.category_id,)):
            raise AppError("Kategori tidak ditemukan")
        if not self.db.scalar("SELECT 1 FROM locations WHERE id = ?", (inp.location_id,)):
            raise AppError("Lokasi tidak ditemukan")
        dept_div = None
        if inp.department_id:
            dept = self.db.one("SELECT * FROM departments WHERE id = ?", (inp.department_id,))
            if dept is None:
                raise AppError("Departemen tidak ditemukan")
            dept_div = dept["divisionId"]
        if inp.division_id and not self.db.scalar("SELECT 1 FROM divisions WHERE id = ?", (inp.division_id,)):
            raise AppError("Divisi tidak ditemukan")
        # Departemen menentukan divisi; bila departemen kosong, pakai divisi yang dipilih.
        division_id = dept_div or inp.division_id

        with self.db.tx():
            old = self.get_row(inp.id) if inp.id else None
            code = normalize_code(inp.code) or (old["code"] if old else self.suggest_code(inp.category_id, inp.acquisition_date.year))
            if not _CODE.match(code):
                raise AppError("Periksa kembali isian", {FIELD_CODE: "Gunakan huruf, angka, titik, garis miring, atau tanda hubung"})
            exclude = old["id"] if old else ""
            if self._count_code_or_qr(code, exclude) > 0:
                raise AppError("Kode aset sudah dipakai", {FIELD_CODE: f"Kode {code} sudah dipakai aset lain"})
            qr_raw = code if qr_same_as_code else inp.qr_code.strip()
            # Kode QR sama dengan kode aset sendiri tidak disimpan terpisah (sama dengan Android).
            qr = None if qr_raw.upper() == code.upper() else qr_raw
            if qr and self._count_code_or_qr(qr, exclude) > 0:
                raise AppError("Kode QR sudah dipakai", {FIELD_QR: f"Kode QR {qr} sudah dipakai aset lain"})
            if not self.db.scalar("SELECT 1 FROM asset_statuses WHERE id = ?", (inp.status,)):
                raise AppError("Status tidak dikenal", {FIELD_STATUS: "Pilih status dari master Status"})
            vendor_id = inp.vendor_id if inp.vendor_id and self.db.scalar("SELECT 1 FROM vendors WHERE id = ?", (inp.vendor_id,)) else None
            now = fmt.now_ms()
            disposed = inp.status == WRITE_OFF_ID
            ed = fmt.to_epoch_day
            values = {
                "code": code, "name": inp.name.strip(), "categoryId": inp.category_id, "brandModel": _trim(inp.brand_model),
                "serialNumber": _trim(inp.serial_number), "acquisitionDate": ed(inp.acquisition_date),
                "acquisitionCost": inp.acquisition_cost, "locationId": inp.location_id, "departmentId": inp.department_id,
                "picName": _trim(inp.pic_name), "condition": inp.condition.name, "status": inp.status,
                "warrantyUntil": ed(inp.warranty_until), "notes": _trim(inp.notes), "photoPath": inp.photo_path,
                "disposalDate": ed(inp.disposal_date or today) if disposed else None,
                "disposalValue": inp.disposal_value if disposed else None,
                "qrCode": qr, "userLocationDetail": _trim(inp.user_location_detail), "divisionId": division_id,
                "prNumber": _trim(inp.pr_number), "poNumber": _trim(inp.po_number), "grNumber": _trim(inp.gr_number),
                "requester": _trim(inp.requester), "memoNumber": _trim(inp.memo_number), "memoDate": ed(inp.memo_date),
                "followUp": _trim(inp.follow_up), "handoverDoc": _trim(inp.handover_doc),
                "accessories": normalize_accessories(inp.accessories), "vendorId": vendor_id,
                "updatedAt": now, "syncState": "PENDING",
            }
            if old is None:
                asset_id = new_id()
                values.update({"id": asset_id, "createdAt": now, "deletedAt": None, "division": None})
                cols = ", ".join(f"`{k}`" for k in values)
                self.db.exec(f"INSERT INTO assets ({cols}) VALUES ({', '.join('?' * len(values))})", tuple(values.values()))
                self.audit.log(AuditEntityType.ASSET, asset_id, AuditAction.CREATE, f"Aset {code} {values['name']} ditambahkan")
            else:
                asset_id = old["id"]
                sets = ", ".join(f"`{k}` = ?" for k in values)
                self.db.exec(f"UPDATE assets SET {sets} WHERE id = ?", (*values.values(), asset_id))
                ch = self._changes(old, values)
                if ch:
                    self.audit.log(AuditEntityType.ASSET, asset_id, AuditAction.UPDATE, f"Aset {code} diubah", ch)
        return asset_id

    def _name(self, table: str, item_id: str | None) -> str | None:
        return self.db.scalar(f"SELECT name FROM {table} WHERE id = ?", (item_id,)) if item_id else None

    def _changes(self, old, new: dict) -> ChangeSet:
        d = lambda v: fmt.date(fmt.from_epoch_day(v)) if v is not None else None  # noqa: E731
        cond = lambda v: AssetCondition[v].label  # noqa: E731
        st = lambda v: self._name("asset_statuses", v) or v  # noqa: E731
        ch = ChangeSet()
        ch.field("Kode", old["code"], new["code"]).field("Nama", old["name"], new["name"])
        ch.field("Kategori", self._name("categories", old["categoryId"]), self._name("categories", new["categoryId"]))
        ch.field("Merk/Tipe", old["brandModel"], new["brandModel"]).field("No. seri", old["serialNumber"], new["serialNumber"])
        ch.field("Tgl perolehan", d(old["acquisitionDate"]), d(new["acquisitionDate"]))
        ch.field("Harga perolehan", fmt.rupiah(old["acquisitionCost"]), fmt.rupiah(new["acquisitionCost"]))
        ch.field("Lokasi", self._name("locations", old["locationId"]), self._name("locations", new["locationId"]))
        ch.field("Departemen", self._name("departments", old["departmentId"]), self._name("departments", new["departmentId"]))
        ch.field("PIC", old["picName"], new["picName"])
        ch.field("Kondisi", cond(old["condition"]), cond(new["condition"]))
        ch.field("Status", st(old["status"]), st(new["status"]))
        ch.field("Garansi s/d", d(old["warrantyUntil"]), d(new["warrantyUntil"]))
        ch.field("Catatan", old["notes"], new["notes"]).field("Kode QR", old["qrCode"], new["qrCode"])
        ch.field("Pengguna / detail lokasi", old["userLocationDetail"], new["userLocationDetail"])
        ch.field("Divisi", self._name("divisions", old["divisionId"]), self._name("divisions", new["divisionId"]))
        ch.field("Nomor PR", old["prNumber"], new["prNumber"]).field("Nomor PO", old["poNumber"], new["poNumber"])
        ch.field("Nomor GR", old["grNumber"], new["grNumber"]).field("Pemohon pembelian", old["requester"], new["requester"])
        ch.field("Nomor memo", old["memoNumber"], new["memoNumber"])
        ch.field("Tanggal memo", d(old["memoDate"]), d(new["memoDate"]))
        ch.field("Tindak lanjut", old["followUp"], new["followUp"])
        ch.field("Berita acara serah terima", old["handoverDoc"], new["handoverDoc"])
        ch.field("Kelengkapan", old["accessories"], new["accessories"])
        ch.field("Vendor", self._name("vendors", old["vendorId"]), self._name("vendors", new["vendorId"]))
        new_photo = None
        if new["photoPath"]:
            new_photo = "ada" if new["photoPath"] == old["photoPath"] else "diganti"
        ch.field("Foto", "ada" if old["photoPath"] else None, new_photo)
        return ch

    # ------------------------------------------------------------ Write Off & hapus
    def write_off(self, asset_id: str, date: _dt.date, value: int | None, note: str = "") -> None:
        self.session.require(lambda r: r.can_edit_data)
        old = self.get_row(asset_id)
        if old is None:
            raise AppError("Aset tidak ditemukan")
        if old["status"] == WRITE_OFF_ID:
            raise AppError("Aset sudah berstatus Write Off")
        if date > fmt.today():
            raise AppError("Tanggal tidak boleh di masa depan")
        if date < fmt.from_epoch_day(old["acquisitionDate"]):
            raise AppError("Tanggal sebelum tanggal perolehan")
        if value is not None and value < 0:
            raise AppError("Nilai jual tidak valid")
        wo_name = self._name("asset_statuses", WRITE_OFF_ID) or "Write Off"
        with self.db.tx():
            self.db.exec(
                "UPDATE assets SET status = ?, disposalDate = ?, disposalValue = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                (WRITE_OFF_ID, fmt.to_epoch_day(date), value, fmt.now_ms(), asset_id),
            )
            summary = f"Aset {old['code']} ditandai Write Off ({fmt.date(date)})" + (f": {note.strip()}" if note.strip() else "")
            ch = ChangeSet().field("Status", self._name("asset_statuses", old["status"]) or old["status"], wo_name) \
                .field("Nilai jual", None, fmt.rupiah(value) if value is not None else None)
            self.audit.log(AuditEntityType.ASSET, asset_id, AuditAction.UPDATE, summary, ch)

    def delete(self, ids: list[str], reason: str) -> tuple[int, list[str]]:
        """Hapus permanen beserta riwayatnya. Mengembalikan (jumlah, path foto yang perlu dibersihkan)."""
        self.session.require(lambda r: r.can_manage_master, "Hanya Admin GA yang dapat menghapus aset")
        if not reason.strip():
            raise AppError("Alasan penghapusan wajib diisi")
        count = 0
        photos: list[str] = []
        unique = list(dict.fromkeys(ids))
        with self.db.tx():
            for i in range(0, len(unique), 500):
                chunk = unique[i:i + 500]
                marks = ",".join("?" * len(chunk))
                brief = self.db.q(f"SELECT id, code, name FROM assets WHERE id IN ({marks})", chunk)
                valid = [b["id"] for b in brief]
                if not valid:
                    continue
                vm = ",".join("?" * len(valid))
                photos += [r[0] for r in self.db.q(f"SELECT photoPath FROM assets WHERE id IN ({vm}) AND photoPath IS NOT NULL", valid)]
                photos += [r[0] for r in self.db.q(f"SELECT photoPath FROM service_records WHERE assetId IN ({vm}) AND photoPath IS NOT NULL", valid)]
                # Urutan anak → induk: riwayat terkait dihapus dulu, lalu asetnya.
                for table in ("asset_mutations", "service_records", "audit_items", "insurance_items"):
                    self.db.exec(f"DELETE FROM {table} WHERE assetId IN ({vm})", valid)
                count += self.db.exec(f"DELETE FROM assets WHERE id IN ({vm})", valid)
                self.audit.log_batch(AuditEntityType.ASSET, AuditAction.DELETE,
                                     [(b["id"], f"Aset {b['code']} {b['name']} dihapus permanen: {reason.strip()}") for b in brief])
        return count, photos

    def history(self, asset_id: str):
        return self.audit.for_entity(AuditEntityType.ASSET, asset_id)
