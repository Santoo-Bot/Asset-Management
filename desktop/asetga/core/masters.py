"""Master data: kategori, lokasi, divisi, departemen, vendor, satuan, tipe & perusahaan asuransi, status aset.

Hapus = permanen, hanya bila tidak dipakai (pesan menyebut pemakainya). Write Off tidak bisa dihapus.
"""

from __future__ import annotations

import re

from . import fmt
from .audit import AuditLog, ChangeSet
from .db import Database, new_id
from .models import WRITE_OFF_ID, AppError, AuditAction, AuditEntityType, MasterType
from .session import Session

FIELD_NAME = "name"
FIELD_PREFIX = "codePrefix"
FIELD_LIFE = "usefulLifeYears"
FIELD_RESIDUAL = "residualPercent"
FIELD_EMAIL = "email"
FIELD_PHONE = "phone"
FIELD_DIVISION = "division"

_PREFIX = re.compile(r"^[A-Z]{2,5}$")
_EMAIL = re.compile(r"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")
_PHONE = re.compile(r"^[0-9+()\-\s]{6,20}$")

ASSET_TYPES = {MasterType.CATEGORY, MasterType.LOCATION, MasterType.DIVISION, MasterType.DEPARTMENT, MasterType.VENDOR}


def _trim(v: str | None) -> str | None:
    v = (v or "").strip()
    return v or None


def validate_name(name: str) -> str | None:
    if not name.strip():
        return "Nama wajib diisi"
    if len(name.strip()) > 100:
        return "Nama maksimal 100 karakter"
    return None


def validate_contact(name: str, phone: str | None, email: str | None) -> dict[str, str]:
    errors: dict[str, str] = {}
    if e := validate_name(name):
        errors[FIELD_NAME] = e
    if email and not _EMAIL.match(email.strip()):
        errors[FIELD_EMAIL] = "Format email tidak valid"
    if phone and not _PHONE.match(phone.strip()):
        errors[FIELD_PHONE] = "Nomor telepon tidak valid"
    return errors


class MasterService:
    def __init__(self, db: Database, session: Session, audit: AuditLog):
        self.db = db
        self.session = session
        self.audit = audit

    # ------------------------------------------------------------ baca
    def list(self, t: MasterType):
        if t is MasterType.STATUS:
            return self.db.q("SELECT * FROM asset_statuses WHERE deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
        if t is MasterType.DEPARTMENT:
            return self.db.q(
                "SELECT d.*, v.name AS divisionName FROM departments d LEFT JOIN divisions v ON v.id = d.divisionId "
                "WHERE d.deletedAt IS NULL AND d.isActive = 1 ORDER BY d.name COLLATE NOCASE"
            )
        return self.db.q(f"SELECT * FROM {t.table} WHERE deletedAt IS NULL AND isActive = 1 ORDER BY name COLLATE NOCASE")

    def get(self, t: MasterType, item_id: str):
        return self.db.one(f"SELECT * FROM {t.table} WHERE id = ?", (item_id,))

    def _dup(self, t: MasterType, name: str, exclude_id: str) -> bool:
        return bool(self.db.scalar(
            f"SELECT COUNT(*) FROM {t.table} WHERE name = ? COLLATE NOCASE AND id != ? AND deletedAt IS NULL", (name, exclude_id or "")
        ))

    def _name_check(self, t: MasterType, name: str, exclude_id: str) -> None:
        err = validate_name(name) or (f"Nama {t.singular} sudah ada" if self._dup(t, name, exclude_id) else None)
        if err:
            raise AppError("Periksa kembali isian", {FIELD_NAME: err})

    def _log_update(self, label: str, item_id: str, name: str, ch: ChangeSet) -> None:
        if ch:
            self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.UPDATE, f"{label} {name} diubah", ch)

    def _admin(self) -> None:
        self.session.require(lambda r: r.can_manage_master)

    # ------------------------------------------------------------ simpan (item_id kosong = baru)
    def save_simple(self, t: MasterType, item_id: str, name: str) -> str:
        """Divisi, satuan, tipe asuransi."""
        assert t in (MasterType.DIVISION, MasterType.UNIT, MasterType.INSURANCE_TYPE)
        self._admin()
        name = name.strip()
        self._name_check(t, name, item_id)
        now = fmt.now_ms()
        old = self.get(t, item_id) if item_id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                self.db.exec(
                    f"INSERT INTO {t.table} (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES (?, ?, 1, ?, ?, NULL, 'PENDING')",
                    (item_id, name, now, now),
                )
                self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.CREATE, f"{t.label} {name} ditambahkan")
            else:
                self.db.exec(f"UPDATE {t.table} SET name = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?", (name, now, item_id))
                self._log_update(t.label, item_id, name, ChangeSet().field("Nama", old["name"], name))
        return item_id

    def save_status(self, item_id: str, name: str) -> str:
        self._admin()
        t = MasterType.STATUS
        name = name.strip()
        self._name_check(t, name, item_id)
        now = fmt.now_ms()
        old = self.get(t, item_id) if item_id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                order = (self.db.scalar("SELECT COALESCE(MAX(sortOrder), 0) FROM asset_statuses") or 0) + 1
                self.db.exec(
                    "INSERT INTO asset_statuses (id, name, sortOrder, isActive, createdAt, updatedAt, deletedAt, syncState) "
                    "VALUES (?, ?, ?, 1, ?, ?, NULL, 'PENDING')",
                    (item_id, name, order, now, now),
                )
                self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.CREATE, f"Status {name} ditambahkan")
            else:
                self.db.exec("UPDATE asset_statuses SET name = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?", (name, now, item_id))
                self._log_update("Status", item_id, name, ChangeSet().field("Nama", old["name"], name))
        return item_id

    def save_category(self, item_id: str, name: str, prefix: str, life_years: int, residual_percent: float | None) -> str:
        self._admin()
        name, prefix = name.strip(), prefix.strip().upper()
        errors: dict[str, str] = {}
        if e := validate_name(name):
            errors[FIELD_NAME] = e
        if not _PREFIX.match(prefix):
            errors[FIELD_PREFIX] = "Kode 2–5 huruf kapital, mis. IT"
        if not 1 <= life_years <= 50:
            errors[FIELD_LIFE] = "Umur ekonomis 1–50 tahun"
        if residual_percent is not None and not 0.0 <= residual_percent < 100.0:
            errors[FIELD_RESIDUAL] = "Nilai residu 0–99,99%"
        if FIELD_NAME not in errors and self._dup(MasterType.CATEGORY, name, item_id):
            errors[FIELD_NAME] = "Nama kategori sudah ada"
        if FIELD_PREFIX not in errors and self.db.scalar(
            "SELECT COUNT(*) FROM categories WHERE codePrefix = ? COLLATE NOCASE AND id != ?", (prefix, item_id or "")
        ):
            errors[FIELD_PREFIX] = "Kode sudah dipakai kategori lain"
        if errors:
            raise AppError("Periksa kembali isian", errors)
        now = fmt.now_ms()
        old = self.get(MasterType.CATEGORY, item_id) if item_id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                self.db.exec(
                    "INSERT INTO categories (id, name, codePrefix, usefulLifeYears, residualPercent, isActive, createdAt, updatedAt, deletedAt, syncState) "
                    "VALUES (?, ?, ?, ?, ?, 1, ?, ?, NULL, 'PENDING')",
                    (item_id, name, prefix, life_years, residual_percent, now, now),
                )
                self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.CREATE, f"Kategori {name} ditambahkan")
            else:
                self.db.exec(
                    "UPDATE categories SET name = ?, codePrefix = ?, usefulLifeYears = ?, residualPercent = ?, updatedAt = ?, "
                    "syncState = 'PENDING' WHERE id = ?",
                    (name, prefix, life_years, residual_percent, now, item_id),
                )
                self._log_update("Kategori", item_id, name, ChangeSet()
                                 .field("Nama", old["name"], name).field("Kode", old["codePrefix"], prefix)
                                 .field("Umur ekonomis (th)", old["usefulLifeYears"], life_years)
                                 .field("Residu (%)", old["residualPercent"], residual_percent))
        return item_id

    def save_location(self, item_id: str, name: str, address: str | None) -> str:
        self._admin()
        name, address = name.strip(), _trim(address)
        self._name_check(MasterType.LOCATION, name, item_id)
        now = fmt.now_ms()
        old = self.get(MasterType.LOCATION, item_id) if item_id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                self.db.exec(
                    "INSERT INTO locations (id, name, address, isActive, createdAt, updatedAt, deletedAt, syncState) "
                    "VALUES (?, ?, ?, 1, ?, ?, NULL, 'PENDING')",
                    (item_id, name, address, now, now),
                )
                self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.CREATE, f"Lokasi {name} ditambahkan")
            else:
                self.db.exec("UPDATE locations SET name = ?, address = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                             (name, address, now, item_id))
                self._log_update("Lokasi", item_id, name, ChangeSet().field("Nama", old["name"], name).field("Alamat", old["address"], address))
        return item_id

    def save_department(self, item_id: str, name: str, division_id: str | None) -> str:
        self._admin()
        name = name.strip()
        errors: dict[str, str] = {}
        err = validate_name(name) or ("Nama departemen sudah ada" if self._dup(MasterType.DEPARTMENT, name, item_id) else None)
        if err:
            errors[FIELD_NAME] = err
        division = self.get(MasterType.DIVISION, division_id) if division_id else None
        if division is None:
            errors[FIELD_DIVISION] = "Pilih divisi induk"
        if errors:
            raise AppError("Periksa kembali isian", errors)
        now = fmt.now_ms()
        old = self.get(MasterType.DEPARTMENT, item_id) if item_id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                self.db.exec(
                    "INSERT INTO departments (id, name, isActive, createdAt, updatedAt, deletedAt, syncState, divisionId) "
                    "VALUES (?, ?, 1, ?, ?, NULL, 'PENDING', ?)",
                    (item_id, name, now, now, division["id"]),
                )
                self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.CREATE, f"Departemen {name} (Divisi {division['name']}) ditambahkan")
            else:
                old_div = self.get(MasterType.DIVISION, old["divisionId"]) if old["divisionId"] else None
                self.db.exec("UPDATE departments SET name = ?, divisionId = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                             (name, division["id"], now, item_id))
                # Aset di departemen ini ikut pindah divisi agar data tetap konsisten.
                if old["divisionId"] != division["id"]:
                    self.db.exec("UPDATE assets SET divisionId = ?, updatedAt = ?, syncState = 'PENDING' WHERE departmentId = ?",
                                 (division["id"], now, item_id))
                self._log_update("Departemen", item_id, name, ChangeSet().field("Nama", old["name"], name)
                                 .field("Divisi", old_div["name"] if old_div else None, division["name"]))
        return item_id

    def save_contact(self, t: MasterType, item_id: str, name: str, phone: str | None, email: str | None, address: str | None) -> str:
        """Vendor & perusahaan asuransi."""
        assert t in (MasterType.VENDOR, MasterType.INSURER)
        self._admin()
        name, phone, email, address = name.strip(), _trim(phone), _trim(email), _trim(address)
        errors = validate_contact(name, phone, email)
        if FIELD_NAME not in errors and self._dup(t, name, item_id):
            errors[FIELD_NAME] = f"Nama {t.singular} sudah ada"
        if errors:
            raise AppError("Periksa kembali isian", errors)
        now = fmt.now_ms()
        old = self.get(t, item_id) if item_id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                self.db.exec(
                    f"INSERT INTO {t.table} (id, name, phone, email, address, isActive, createdAt, updatedAt, deletedAt, syncState) "
                    "VALUES (?, ?, ?, ?, ?, 1, ?, ?, NULL, 'PENDING')",
                    (item_id, name, phone, email, address, now, now),
                )
                self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.CREATE, f"{t.label} {name} ditambahkan")
            else:
                self.db.exec(
                    f"UPDATE {t.table} SET name = ?, phone = ?, email = ?, address = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                    (name, phone, email, address, now, item_id),
                )
                self._log_update(t.label, item_id, name, ChangeSet().field("Nama", old["name"], name).field("Telepon", old["phone"], phone)
                                 .field("Email", old["email"], email).field("Alamat", old["address"], address))
        return item_id

    # ------------------------------------------------------------ hapus permanen
    def usage(self, t: MasterType, item_id: str) -> list[str]:
        """Daftar pemakaian, mis. ["12 aset", "2 catatan servis"]. Kosong = aman dihapus."""
        out: list[str] = []

        def add(sql: str, label: str) -> None:
            n = self.db.scalar(sql, (item_id,)) or 0
            if n > 0:
                out.append(f"{n} {label}")

        if t is MasterType.CATEGORY:
            add("SELECT COUNT(*) FROM assets WHERE categoryId = ?", "aset")
        elif t is MasterType.LOCATION:
            add("SELECT COUNT(*) FROM assets WHERE locationId = ?", "aset")
            add("SELECT COUNT(*) FROM items WHERE storageLocationId = ?", "barang stok")
        elif t is MasterType.DIVISION:
            add("SELECT COUNT(*) FROM departments WHERE divisionId = ?", "departemen")
            add("SELECT COUNT(*) FROM assets WHERE divisionId = ?", "aset")
        elif t is MasterType.DEPARTMENT:
            add("SELECT COUNT(*) FROM assets WHERE departmentId = ?", "aset")
        elif t is MasterType.VENDOR:
            add("SELECT COUNT(*) FROM assets WHERE vendorId = ?", "aset")
            add("SELECT COUNT(*) FROM service_records WHERE vendorId = ?", "catatan servis")
        elif t is MasterType.STATUS:
            add("SELECT COUNT(*) FROM assets WHERE status = ?", "aset")
        elif t is MasterType.UNIT:
            add("SELECT COUNT(*) FROM items WHERE unitId = ?", "barang stok")
        elif t in (MasterType.INSURANCE_TYPE, MasterType.INSURER):
            col = "typeId" if t is MasterType.INSURANCE_TYPE else "insurerId"
            nums = [r[0] for r in self.db.q(
                f"SELECT policyNumber FROM insurance_policies WHERE {col} = ? AND deletedAt IS NULL ORDER BY policyNumber", (item_id,))]
            if nums:
                out.append("polis " + ", ".join(nums[:3]) + (f" dan {len(nums) - 3} lainnya" if len(nums) > 3 else ""))
        return out

    def delete(self, t: MasterType, item_id: str) -> None:
        self._admin()
        row = self.get(t, item_id)
        if row is None:
            raise AppError("Data tidak ditemukan")
        name = row["name"]
        if t is MasterType.STATUS and item_id == WRITE_OFF_ID:
            raise AppError(f"{name} adalah status sistem untuk aset yang dibuang/dijual (penyusutan berhenti, tidak ikut opname & "
                           "asuransi), jadi tidak bisa dihapus. Namanya boleh diubah.")
        used = self.usage(t, item_id)
        if used:
            hint = {
                MasterType.DIVISION: "Hapus atau pindahkan dulu departemen di bawahnya, dan ubah divisi aset aktif tersebut.",
                MasterType.DEPARTMENT: "Ubah departemen aset aktif tersebut dulu.",
                MasterType.CATEGORY: f"Pindahkan dulu ke {t.singular} lain (ubah aset / mutasi).",
                MasterType.LOCATION: f"Pindahkan dulu ke {t.singular} lain (ubah aset / mutasi).",
                MasterType.VENDOR: "Ubah vendor pada aset / catatan servis tersebut dulu.",
                MasterType.STATUS: "Ubah status aset tersebut dulu.",
                MasterType.UNIT: "Ubah satuan barang tersebut dulu.",
                MasterType.INSURANCE_TYPE: "Ubah atau hapus polis tersebut dulu.",
                MasterType.INSURER: "Ubah atau hapus polis tersebut dulu.",
            }[t]
            note = ""
            if t in ASSET_TYPES:
                wo = self.db.scalar(
                    "SELECT COUNT(*) FROM assets WHERE status = 'WRITE_OFF' AND (categoryId = ?1 OR locationId = ?1 OR divisionId = ?1 "
                    "OR departmentId = ?1 OR vendorId = ?1)", (item_id,)) or 0
                if wo > 0:
                    note = f" ({wo} di antaranya berstatus Write Off)"
            raise AppError(f"{name} tidak bisa dihapus karena dipakai {', '.join(used)}{note}. {hint}")

        with self.db.tx():
            if t is MasterType.LOCATION:
                # Riwayat mutasi tidak menahan; rujukannya dikosongkan.
                self.db.exec("UPDATE asset_mutations SET fromLocationId = NULL WHERE fromLocationId = ?", (item_id,))
                self.db.exec("UPDATE asset_mutations SET toLocationId = NULL WHERE toLocationId = ?", (item_id,))
            elif t is MasterType.DEPARTMENT:
                self.db.exec("UPDATE asset_mutations SET fromDepartmentId = NULL WHERE fromDepartmentId = ?", (item_id,))
                self.db.exec("UPDATE asset_mutations SET toDepartmentId = NULL WHERE toDepartmentId = ?", (item_id,))
            elif t in (MasterType.INSURANCE_TYPE, MasterType.INSURER):
                col = "typeId" if t is MasterType.INSURANCE_TYPE else "insurerId"
                self.db.exec(f"DELETE FROM insurance_items WHERE policyId IN (SELECT id FROM insurance_policies WHERE {col} = ? "
                             "AND deletedAt IS NOT NULL)", (item_id,))
                self.db.exec(f"DELETE FROM insurance_policies WHERE {col} = ? AND deletedAt IS NOT NULL", (item_id,))
            self.db.exec(f"DELETE FROM {t.table} WHERE id = ?", (item_id,))
            self.audit.log(AuditEntityType.MASTER, item_id, AuditAction.DELETE, f"{t.label} {name} dihapus")

    # ------------------------------------------------------------ bantu
    def status_names(self) -> dict[str, str]:
        return {r["id"]: r["name"] for r in self.list(MasterType.STATUS)}
