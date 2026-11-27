"""Stok barang habis pakai: barang, transaksi masuk/keluar, status Reorder. Aturan sama dengan StockRepositoryImpl Android."""

from __future__ import annotations

import datetime as _dt
import re
from dataclasses import dataclass

from . import fmt
from .audit import AuditLog, ChangeSet
from .db import Database, new_id
from .models import AppError, AuditAction, AuditEntityType, StockTxnType
from .session import Session

FIELD_CODE = "code"
FIELD_BARCODE = "barcode"
FIELD_NAME = "name"
FIELD_UNIT = "unit"
FIELD_INITIAL = "initialStock"
FIELD_MIN = "minStock"
FIELD_QTY = "qty"
FIELD_DATE = "date"

_ITEM_CODE = re.compile(r"^[A-Z0-9][A-Z0-9./-]{1,29}$")
_EPS = 1e-9

# Stok akhir = stok awal + Σ masuk − Σ keluar.
CURRENT_STOCK = """
    (i.initialStock + COALESCE((SELECT SUM(CASE WHEN t.type = 'IN' THEN t.qty ELSE -t.qty END)
        FROM stock_txns t WHERE t.itemId = i.id AND t.deletedAt IS NULL), 0))
"""
ITEM_SELECT = f"""
    SELECT i.id, i.code, i.barcode, i.name, i.unitId, u.name AS unitName, i.storageLocationId,
           l.name AS storageLocationName, i.initialStock, i.minStock, {CURRENT_STOCK} AS currentStock, i.notes, i.isActive
    FROM items i JOIN units u ON u.id = i.unitId LEFT JOIN locations l ON l.id = i.storageLocationId
"""


def round2(v: float) -> float:
    return round(v + (1e-12 if v >= 0 else -1e-12), 2)


def needs_reorder(current: float, minimum: float) -> bool:
    return current <= minimum + _EPS


def _trim(v: str | None) -> str | None:
    v = (v or "").strip()
    return v or None


@dataclass(frozen=True)
class ItemInput:
    id: str | None = None
    code: str = ""
    barcode: str = ""
    name: str = ""
    unit_id: str | None = None
    storage_location_id: str | None = None
    initial_stock: str = "0"
    min_stock: str = "0"
    notes: str = ""


def validate_item(inp: ItemInput) -> dict[str, str]:
    e: dict[str, str] = {}
    code = inp.code.strip().upper()
    if not code:
        e[FIELD_CODE] = "Kode barang wajib diisi"
    elif not _ITEM_CODE.match(code):
        e[FIELD_CODE] = "Gunakan huruf, angka, titik, garis miring, atau tanda hubung"
    if not inp.name.strip():
        e[FIELD_NAME] = "Nama barang wajib diisi"
    if inp.unit_id is None:
        e[FIELD_UNIT] = "Pilih satuan"
    initial = fmt.parse_decimal(inp.initial_stock.strip() or "0")
    if initial is None or initial < 0:
        e[FIELD_INITIAL] = "Stok awal tidak valid"
    minimum = fmt.parse_decimal(inp.min_stock.strip() or "0")
    if minimum is None or minimum < 0:
        e[FIELD_MIN] = "Minimum stok tidak valid"
    return e


class StockService:
    def __init__(self, db: Database, session: Session, audit: AuditLog):
        self.db = db
        self.session = session
        self.audit = audit

    def items(self, query: str = "", reorder_only: bool = False):
        q = query.strip()
        return self.db.q(
            ITEM_SELECT + " WHERE i.deletedAt IS NULL AND i.isActive = 1 "
            "AND (? = '' OR instr(lower(i.code), lower(?)) > 0 OR instr(lower(i.name), lower(?)) > 0 OR i.barcode = ?) "
            f"AND (? = 0 OR {CURRENT_STOCK} <= i.minStock) ORDER BY i.name COLLATE NOCASE",
            (q, q, q, q, 1 if reorder_only else 0),
        )

    def reorder_count(self) -> int:
        return self.db.scalar(f"SELECT COUNT(*) FROM items i WHERE i.deletedAt IS NULL AND i.isActive = 1 AND {CURRENT_STOCK} <= i.minStock") or 0

    def item(self, item_id: str):
        return self.db.one(ITEM_SELECT + " WHERE i.id = ?", (item_id,))

    def find_by_code_or_barcode(self, code: str):
        c = code.strip()
        return self.db.one(ITEM_SELECT + " WHERE i.deletedAt IS NULL AND (i.code = ? COLLATE NOCASE OR i.barcode = ?) LIMIT 1", (c, c))

    def txns(self, item_id: str):
        return self.db.q(
            "SELECT t.*, u.fullName AS createdBy FROM stock_txns t LEFT JOIN users u ON u.id = t.createdByUserId "
            "WHERE t.itemId = ? AND t.deletedAt IS NULL ORDER BY t.txnDate DESC, t.createdAt DESC",
            (item_id,),
        )

    def all_txns(self):
        return self.db.q(
            "SELECT t.*, u.fullName AS createdBy, i.code AS itemCode, i.name AS itemName FROM stock_txns t "
            "JOIN items i ON i.id = t.itemId LEFT JOIN users u ON u.id = t.createdByUserId "
            "WHERE t.deletedAt IS NULL ORDER BY t.txnDate DESC, t.createdAt DESC"
        )

    @staticmethod
    def item_input(row) -> ItemInput:
        return ItemInput(id=row["id"], code=row["code"], barcode=row["barcode"] or "", name=row["name"], unit_id=row["unitId"],
                         storage_location_id=row["storageLocationId"], initial_stock=fmt.qty(row["initialStock"]),
                         min_stock=fmt.qty(row["minStock"]), notes=row["notes"] or "")

    def save_item(self, inp: ItemInput) -> str:
        self.session.require(lambda r: r.can_edit_data)
        errors = validate_item(inp)
        code = inp.code.strip().upper()
        barcode = _trim(inp.barcode)
        exclude = inp.id or ""
        if FIELD_CODE not in errors and self.db.scalar("SELECT COUNT(*) FROM items WHERE code = ? COLLATE NOCASE AND id != ?", (code, exclude)):
            errors[FIELD_CODE] = "Kode barang sudah dipakai"
        if barcode and self.db.scalar("SELECT COUNT(*) FROM items WHERE barcode = ? AND id != ? AND deletedAt IS NULL", (barcode, exclude)):
            errors[FIELD_BARCODE] = "Barcode sudah dipakai barang lain"
        if errors:
            raise AppError("Periksa kembali isian", errors)
        if not self.db.scalar("SELECT 1 FROM units WHERE id = ?", (inp.unit_id,)):
            raise AppError("Satuan tidak ditemukan")
        if inp.storage_location_id and not self.db.scalar("SELECT 1 FROM locations WHERE id = ?", (inp.storage_location_id,)):
            raise AppError("Lokasi tidak ditemukan")
        initial = round2(fmt.parse_decimal(inp.initial_stock.strip() or "0"))
        minimum = round2(fmt.parse_decimal(inp.min_stock.strip() or "0"))
        name, notes = inp.name.strip(), _trim(inp.notes)
        now = fmt.now_ms()
        old = self.db.one("SELECT * FROM items WHERE id = ?", (inp.id,)) if inp.id else None
        with self.db.tx():
            if old is None:
                item_id = new_id()
                self.db.exec(
                    "INSERT INTO items (id, code, barcode, name, unitId, initialStock, minStock, storageLocationId, notes, isActive, "
                    "createdAt, updatedAt, deletedAt, syncState) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, NULL, 'PENDING')",
                    (item_id, code, barcode, name, inp.unit_id, initial, minimum, inp.storage_location_id, notes, now, now),
                )
                self.audit.log(AuditEntityType.ITEM, item_id, AuditAction.CREATE,
                               f"Barang {code} {name} ditambahkan (stok awal {fmt.qty(initial)})")
            else:
                item_id = old["id"]
                self.db.exec(
                    "UPDATE items SET code = ?, barcode = ?, name = ?, unitId = ?, initialStock = ?, minStock = ?, storageLocationId = ?, "
                    "notes = ?, updatedAt = ?, syncState = 'PENDING' WHERE id = ?",
                    (code, barcode, name, inp.unit_id, initial, minimum, inp.storage_location_id, notes, now, item_id),
                )
                nm = lambda t, i: self.db.scalar(f"SELECT name FROM {t} WHERE id = ?", (i,)) if i else None  # noqa: E731
                ch = ChangeSet().field("Kode", old["code"], code).field("Barcode", old["barcode"], barcode).field("Nama", old["name"], name) \
                    .field("Satuan", nm("units", old["unitId"]), nm("units", inp.unit_id)) \
                    .field("Stok awal", fmt.qty(old["initialStock"]), fmt.qty(initial)) \
                    .field("Min. stok", fmt.qty(old["minStock"]), fmt.qty(minimum)) \
                    .field("Lokasi simpan", nm("locations", old["storageLocationId"]), nm("locations", inp.storage_location_id)) \
                    .field("Catatan", old["notes"], notes)
                if ch:
                    self.audit.log(AuditEntityType.ITEM, item_id, AuditAction.UPDATE, f"Barang {code} diubah", ch)
        return item_id

    def delete_item(self, item_id: str) -> None:
        self.session.require(lambda r: r.can_manage_master, "Hanya Admin GA yang dapat menghapus barang")
        item = self.db.one("SELECT * FROM items WHERE id = ?", (item_id,))
        if item is None:
            raise AppError("Barang tidak ditemukan")
        with self.db.tx():
            n = self.db.exec("DELETE FROM stock_txns WHERE itemId = ?", (item_id,))
            self.db.exec("DELETE FROM items WHERE id = ?", (item_id,))
            self.audit.log(AuditEntityType.ITEM, item_id, AuditAction.DELETE,
                           f"Barang {item['code']} {item['name']} dihapus permanen beserta {n} transaksi")

    def record_txn(self, item_id: str, type_: StockTxnType, date: _dt.date | None, qty_text: str, reference: str = "", note: str = "") -> None:
        self.session.require(lambda r: r.can_edit_data)
        errors: dict[str, str] = {}
        qty = fmt.parse_decimal(qty_text)
        if qty is None or qty <= 0:
            errors[FIELD_QTY] = "Jumlah harus lebih dari 0"
        if date is None:
            errors[FIELD_DATE] = "Tanggal wajib diisi"
        elif date > fmt.today():
            errors[FIELD_DATE] = "Tanggal tidak boleh di masa depan"
        if errors:
            raise AppError("Periksa kembali isian", errors)
        qty = round2(qty)
        # Stok dicek di dalam transaksi agar pengeluaran tidak membuat stok minus.
        with self.db.tx():
            item = self.item(item_id)
            if item is None:
                raise AppError("Barang tidak ditemukan")
            if not item["isActive"]:
                raise AppError("Barang nonaktif")
            current = round2(item["currentStock"])
            if type_ is StockTxnType.OUT and not (qty > 0 and qty <= current + _EPS):
                raise AppError("Stok tidak cukup", {FIELD_QTY: f"Stok tersedia {fmt.qty(current)} {item['unitName']}"})
            now = fmt.now_ms()
            u = self.session.user
            self.db.exec(
                "INSERT INTO stock_txns (id, itemId, txnDate, type, qty, reference, note, createdByUserId, createdAt, updatedAt, deletedAt, syncState) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'PENDING')",
                (new_id(), item_id, fmt.to_epoch_day(date), type_.name, qty, _trim(reference), _trim(note), u.id if u else None, now, now),
            )
            after = round2(current + qty if type_ is StockTxnType.IN else current - qty)
            self.audit.log(AuditEntityType.STOCK, item_id, AuditAction.CREATE,
                           f"Stok {type_.label.lower()} {item['code']}: {fmt.qty(qty)} {item['unitName']} "
                           f"(stok {fmt.qty(current)} → {fmt.qty(after)})")
