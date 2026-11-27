"""Template import, import (tambah baru + perbarui), dan export aset ke Excel/CSV.

Urutan & judul kolom sama dengan aplikasi Android. Kolom baru selalu ditambahkan di AKHIR.
"""

from __future__ import annotations

import csv
import datetime as _dt
import math
import re
from dataclasses import dataclass, field, replace
from enum import Enum
from pathlib import Path

from . import calc, fmt
from .assets import AssetInput, AssetService, normalize_code, validate, FIELD_CATEGORY, FIELD_LOCATION, \
    FIELD_ACQ_DATE, FIELD_COST, FIELD_WARRANTY, DETAIL_SELECT
from .models import WRITE_OFF_ID, AppError, AssetCondition, AuditAction, AuditEntityType, DEFAULT_STATUS_LABELS, \
    MasterType, normalize_accessories

MAX_IMPORT_ROWS = 10_000


class Col(Enum):
    """(judul di template, wajib). Urutan = urutan kolom template."""
    CODE = ("Kode Aset*", True)
    QR = ("Kode QR*", True)
    NAME = ("Nama Aset*", True)
    CATEGORY = ("Kategori*", True)
    SERIAL = ("No. Seri", False)
    ACQ_DATE = ("Tgl Perolehan (dd/mm/yyyy)*", True)
    COST = ("Harga Perolehan*", True)
    LOCATION = ("Lokasi*", True)
    DIVISION = ("Divisi", False)
    DEPARTMENT = ("Departemen", False)
    CONDITION = ("Kondisi* (Baik,Rusak)", True)
    STATUS = ("Status* (pilih dari master Status)", True)
    WARRANTY = ("Garansi s/d (dd/mm/yyyy)", False)
    NOTES = ("Catatan", False)
    USER_LOCATION = ("Pengguna / Detail Lokasi", False)
    PR = ("Nomor PR", False)
    PO = ("Nomor PO", False)
    GR = ("Nomor GR", False)
    REQUESTER = ("Pemohon Pembelian", False)
    MEMO_NO = ("Nomor Memo", False)
    MEMO_DATE = ("Tanggal Memo (dd/mm/yyyy)", False)
    FOLLOW_UP = ("Tindak Lanjut", False)
    HANDOVER = ("Berita Acara Serah Terima", False)
    ACCESSORIES = ("Kelengkapan (pisahkan dengan koma)", False)
    VENDOR = ("Vendor", False)

    @property
    def template_header(self) -> str:
        return self.value[0]

    @property
    def required(self) -> bool:
        return self.value[1]

    @property
    def header(self) -> str:
        """Nama kolom tanpa tanda wajib & keterangan, mis. "Kondisi"."""
        return self.template_header.replace("*", "").split("(")[0].strip()


COLS = list(Col)


def normalize_header(h: str) -> str:
    return re.sub(r"[^a-z0-9/]", "", h.lower().replace("*", "").split("(")[0])


_DATE_FORMATS = ["%d/%m/%Y", "%d-%m-%Y", "%Y-%m-%d", "%d.%m.%Y"]
_EXCEL_EPOCH = _dt.date(1899, 12, 30)


def parse_date(raw: str) -> _dt.date | None:
    t = (raw or "").strip()
    if not t:
        return None
    try:
        serial = float(t)
        if 1.0 <= serial <= 2958465.0:
            return _EXCEL_EPOCH + _dt.timedelta(days=int(serial))
    except ValueError:
        pass
    for f in _DATE_FORMATS:
        try:
            return _dt.datetime.strptime(t, f).date()
        except ValueError:
            continue
    return None


_NUMERIC = re.compile(r"^-?\d+(\.\d+)?([eE][+-]?\d+)?$")


def parse_money(raw: str) -> int | None:
    t = (raw or "").strip()
    for p in ("Rp", "rp"):
        if t.startswith(p):
            t = t[len(p):]
    t = t.strip()
    if not t:
        return None
    if _NUMERIC.match(t):
        return int(math.floor(float(t) + 0.5))
    digits = "".join(c for c in t.split(",")[0] if c.isdigit())
    if not digits or len(digits) > 15:
        return None
    return -int(digits) if t.startswith("-") else int(digits)


_COND_ALIASES = {"cukup": "BAIK", "rusakringan": "RUSAK", "rusakberat": "RUSAK"}
_STATUS_ALIASES = {
    "aktif": "DIGUNAKAN", "dipinjam": "DIGUNAKAN", "disimpan": "TERSEDIA",
    "dihapusdijual": "WRITE_OFF", "dihapus": "WRITE_OFF", "dijual": "WRITE_OFF",
    "propose": "PROPOSE_WRITE_OFF", "proposewriteoff": "PROPOSE_WRITE_OFF", "writeoff": "WRITE_OFF",
}


def status_key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.strip().lower())


def _match_enum(raw: str, entries: list[tuple[str, str]], aliases: dict[str, str]) -> str | None:
    key = re.sub(r"[^a-z]", "", raw.strip().lower())
    if not key:
        return None
    for name, label in entries:
        if key == name.lower().replace("_", "") or key == re.sub(r"[^a-z]", "", label.lower()):
            return name
    return aliases.get(key)


_COND_ENTRIES = [(c.name, c.label) for c in AssetCondition]
_STATUS_ENTRIES = list(DEFAULT_STATUS_LABELS.items())


@dataclass
class Lookups:
    categories: dict[str, str]
    locations: dict[str, str]
    departments: dict[str, str]
    divisions: dict[str, str] = field(default_factory=dict)
    department_division: dict[str, str] = field(default_factory=dict)
    statuses: dict[str, str] = field(default_factory=dict)
    vendors: dict[str, str] = field(default_factory=dict)
    existing_codes: set[str] = field(default_factory=set)


class Action(Enum):
    NEW = "Baru"
    UPDATE = "Perbarui"


@dataclass
class ImportRow:
    row_number: int
    input: AssetInput | None
    errors: list[str]
    unchanged: bool = False
    action: Action = Action.NEW

    @property
    def is_valid(self) -> bool:
        return self.input is not None and not self.errors


@dataclass
class ImportPreview:
    rows: list[ImportRow]
    header_error: str | None = None

    @property
    def valid(self):
        return [r for r in self.rows if r.is_valid]

    @property
    def invalid(self):
        return [r for r in self.rows if not r.is_valid]

    @property
    def to_save(self):
        return [r for r in self.valid if not r.unchanged]

    @property
    def unchanged(self):
        return [r for r in self.valid if r.unchanged]

    @property
    def created(self):
        return [r for r in self.to_save if r.action is Action.NEW]

    @property
    def updated(self):
        return [r for r in self.to_save if r.action is Action.UPDATE]


def _index(header_row: list[str]) -> dict[Col, int]:
    h = [normalize_header(x) for x in header_row]
    return {c: (h.index(normalize_header(c.header)) if normalize_header(c.header) in h else -1) for c in COLS}


def _cell(cells: list[str], idx: dict[Col, int], c: Col) -> str:
    i = idx.get(c, -1)
    return (cells[i] if 0 <= i < len(cells) else "").strip()


def parse_new(sheet: list[list[str]], lk: Lookups, today: _dt.date) -> ImportPreview:
    if not sheet:
        return ImportPreview([], "File kosong")
    idx = _index(sheet[0])
    missing = [c for c in COLS if c.required and idx[c] < 0]
    if missing:
        return ImportPreview([], f"Kolom wajib tidak ditemukan: {', '.join(c.header for c in missing)}. Gunakan template dari aplikasi.")
    codes_in_file: set[str] = set()
    rows: list[ImportRow] = []
    for i, cells in enumerate(sheet[1:]):
        if all(not str(x).strip() for x in cells):
            continue
        n = i + 2
        cell = lambda c: _cell(cells, idx, c)  # noqa: E731
        errors: list[str] = []
        code = normalize_code(cell(Col.CODE))
        if code:
            if code in lk.existing_codes:
                errors.append(f"Kode {code} sudah ada di aplikasi")
            if code in codes_in_file:
                errors.append(f"Kode {code} ganda di file")
            codes_in_file.add(code)
        cat_raw = cell(Col.CATEGORY)
        category_id = lk.categories.get(cat_raw.lower()) if cat_raw else None
        if category_id is None:
            errors.append("Kategori kosong" if not cat_raw else f'Kategori "{cat_raw}" tidak dikenal')
        loc_raw = cell(Col.LOCATION)
        location_id = lk.locations.get(loc_raw.lower()) if loc_raw else None
        if location_id is None:
            errors.append("Lokasi kosong" if not loc_raw else f'Lokasi "{loc_raw}" tidak dikenal')
        dept_raw = cell(Col.DEPARTMENT)
        department_id = lk.departments.get(dept_raw.lower()) if dept_raw else None
        if dept_raw and department_id is None:
            errors.append(f'Departemen "{dept_raw}" tidak dikenal')
        div_raw = cell(Col.DIVISION)
        chosen_div = lk.divisions.get(div_raw.lower()) if div_raw else None
        if div_raw and chosen_div is None:
            errors.append(f'Divisi "{div_raw}" tidak dikenal')
        dept_div = lk.department_division.get(department_id) if department_id else None
        if chosen_div and dept_div and chosen_div != dept_div:
            errors.append(f'Departemen "{dept_raw}" bukan bagian dari divisi "{div_raw}"')
        division_id = dept_div or chosen_div

        qr = cell(Col.QR)
        if qr and qr.upper() != code.upper():
            if qr.upper() in lk.existing_codes:
                errors.append(f"Kode QR {qr} sudah dipakai aset lain")
            if qr.upper() in codes_in_file:
                errors.append(f"Kode QR {qr} ganda di file")
            codes_in_file.add(qr.upper())
        memo_raw = cell(Col.MEMO_DATE)
        memo = parse_date(memo_raw)
        if memo_raw and memo is None:
            errors.append(f'Tanggal memo "{memo_raw}" tidak valid (gunakan dd/mm/yyyy)')
        acq_raw = cell(Col.ACQ_DATE)
        acq = parse_date(acq_raw)
        if acq_raw and acq is None:
            errors.append(f'Tgl perolehan "{acq_raw}" tidak valid (gunakan dd/mm/yyyy)')
        war_raw = cell(Col.WARRANTY)
        warranty = parse_date(war_raw)
        if war_raw and warranty is None:
            errors.append(f'Garansi "{war_raw}" tidak valid (gunakan dd/mm/yyyy)')
        cost_raw = cell(Col.COST)
        cost = parse_money(cost_raw)
        if cost_raw and cost is None:
            errors.append(f'Harga "{cost_raw}" tidak valid')
        cond_raw = cell(Col.CONDITION)
        cond = _match_enum(cond_raw, _COND_ENTRIES, _COND_ALIASES)
        if cond is None:
            errors.append("Kondisi kosong" if not cond_raw else f'Kondisi "{cond_raw}" tidak dikenal (Baik/Rusak)')
        st_raw = cell(Col.STATUS)
        built_in = _match_enum(st_raw, _STATUS_ENTRIES, _STATUS_ALIASES)
        status = lk.statuses.get(status_key(st_raw)) or (built_in if built_in and (not lk.statuses or built_in in lk.statuses.values()) else None)
        if status is None:
            errors.append("Status kosong" if not st_raw else f'Status "{st_raw}" tidak ada di master Status')
        ven_raw = cell(Col.VENDOR)
        vendor_id = lk.vendors.get(ven_raw.lower()) if ven_raw else None
        if ven_raw and vendor_id is None:
            errors.append(f'Vendor "{ven_raw}" tidak ada di master Vendor')

        inp = AssetInput(
            code=code, name=cell(Col.NAME), category_id=category_id, serial_number=cell(Col.SERIAL), acquisition_date=acq,
            acquisition_cost=cost, location_id=location_id, department_id=department_id,
            condition=AssetCondition[cond] if cond else AssetCondition.BAIK, status=status or "DIGUNAKAN",
            warranty_until=warranty, notes=cell(Col.NOTES), qr_code=qr, user_location_detail=cell(Col.USER_LOCATION),
            division_id=division_id, pr_number=cell(Col.PR), po_number=cell(Col.PO), gr_number=cell(Col.GR),
            requester=cell(Col.REQUESTER), memo_number=cell(Col.MEMO_NO), memo_date=memo, follow_up=cell(Col.FOLLOW_UP),
            handover_doc=cell(Col.HANDOVER), accessories=cell(Col.ACCESSORIES), vendor_id=vendor_id,
        )
        for fld, msg in validate(inp, today).items():
            already = {
                FIELD_CATEGORY: category_id is None,
                FIELD_LOCATION: location_id is None,
                FIELD_ACQ_DATE: bool(acq_raw) and acq is None,
                FIELD_COST: bool(cost_raw) and cost is None,
                FIELD_WARRANTY: bool(war_raw) and warranty is None,
            }.get(fld, False)
            if not already:
                errors.append(msg)
        rows.append(ImportRow(n, inp if not errors else None, errors))
    return ImportPreview(rows)


def parse_update(sheet: list[list[str]], lk: Lookups, existing: dict[str, AssetInput], today: _dt.date) -> ImportPreview:
    """Cocokkan lewat Kode Aset. Sel kosong = nilai lama; sel terisi menimpa."""
    if not sheet:
        return ImportPreview([], "File kosong")
    idx = _index(sheet[0])
    if idx[Col.CODE] < 0:
        return ImportPreview([], f"Kolom {Col.CODE.header} tidak ditemukan. Gunakan template atau hasil export dari aplikasi.")
    if all(v < 0 for c, v in idx.items() if c is not Col.CODE):
        return ImportPreview([], "Tidak ada kolom yang akan diperbarui selain Kode Aset")
    codes_in_file: set[str] = set()
    qr_in_file: set[str] = set()
    rows: list[ImportRow] = []
    for i, cells in enumerate(sheet[1:]):
        if all(not str(x).strip() for x in cells):
            continue
        n = i + 2
        cell = lambda c: _cell(cells, idx, c)  # noqa: E731
        errors: list[str] = []
        code = normalize_code(cell(Col.CODE))
        if not code:
            rows.append(ImportRow(n, None, ["Kode Aset kosong"]))
            continue
        if code in codes_in_file:
            rows.append(ImportRow(n, None, [f"Kode {code} ganda di file"]))
            continue
        codes_in_file.add(code)
        base = existing.get(code)
        if base is None:
            rows.append(ImportRow(n, None, [f"Kode {code} tidak ditemukan di aplikasi (mode perbarui hanya untuk aset yang sudah ada)"]))
            continue
        keep = lambda c, cur: cell(c) or cur  # noqa: E731

        category_id = base.category_id
        if raw := cell(Col.CATEGORY):
            category_id = lk.categories.get(raw.lower()) or category_id
            if raw.lower() not in lk.categories:
                errors.append(f'Kategori "{raw}" tidak dikenal')
        location_id = base.location_id
        if raw := cell(Col.LOCATION):
            location_id = lk.locations.get(raw.lower()) or location_id
            if raw.lower() not in lk.locations:
                errors.append(f'Lokasi "{raw}" tidak dikenal')

        department_id, division_id = base.department_id, base.division_id
        dept_raw, div_raw = cell(Col.DEPARTMENT), cell(Col.DIVISION)
        chosen_div = lk.divisions.get(div_raw.lower()) if div_raw else None
        if div_raw and chosen_div is None:
            errors.append(f'Divisi "{div_raw}" tidak dikenal')
        if dept_raw:
            d = lk.departments.get(dept_raw.lower())
            if d is None:
                errors.append(f'Departemen "{dept_raw}" tidak dikenal')
            else:
                d_div = lk.department_division.get(d)
                if chosen_div and d_div and chosen_div != d_div:
                    errors.append(f'Departemen "{dept_raw}" bukan bagian dari divisi "{div_raw}"')
                department_id = d
                division_id = d_div or chosen_div or division_id
        elif chosen_div:
            division_id = chosen_div
            if department_id and lk.department_division.get(department_id) != chosen_div:
                department_id = None

        qr = base.qr_code
        qr_raw = cell(Col.QR)
        if qr_raw and qr_raw.upper() != base.qr_code.upper():
            own = {base.code.upper(), base.qr_code.upper()}
            key = qr_raw.upper()
            if key != code.upper():
                if key in lk.existing_codes and key not in own:
                    errors.append(f"Kode QR {qr_raw} sudah dipakai aset lain")
                if key in qr_in_file:
                    errors.append(f"Kode QR {qr_raw} ganda di file")
                qr_in_file.add(key)
            qr = qr_raw

        def date(c: Col, label: str, cur):
            raw = cell(c)
            if not raw:
                return cur
            v = parse_date(raw)
            if v is None:
                errors.append(f'{label} "{raw}" tidak valid (gunakan dd/mm/yyyy)')
                return cur
            return v

        acq = date(Col.ACQ_DATE, "Tgl perolehan", base.acquisition_date)
        warranty = date(Col.WARRANTY, "Garansi", base.warranty_until)
        memo = date(Col.MEMO_DATE, "Tanggal memo", base.memo_date)
        cost = base.acquisition_cost
        if raw := cell(Col.COST):
            v = parse_money(raw)
            if v is None:
                errors.append(f'Harga "{raw}" tidak valid')
            else:
                cost = v
        cond = base.condition
        if raw := cell(Col.CONDITION):
            m = _match_enum(raw, _COND_ENTRIES, _COND_ALIASES)
            if m:
                cond = AssetCondition[m]
            else:
                errors.append(f'Kondisi "{raw}" tidak dikenal (Baik/Rusak)')
        status = base.status
        if raw := cell(Col.STATUS):
            built_in = _match_enum(raw, _STATUS_ENTRIES, _STATUS_ALIASES)
            sid = lk.statuses.get(status_key(raw)) or (built_in if built_in and (not lk.statuses or built_in in lk.statuses.values()) else None)
            if sid:
                status = sid
            else:
                errors.append(f'Status "{raw}" tidak ada di master Status')
        vendor_id = base.vendor_id
        if raw := cell(Col.VENDOR):
            if raw.lower() in lk.vendors:
                vendor_id = lk.vendors[raw.lower()]
            else:
                errors.append(f'Vendor "{raw}" tidak ada di master Vendor')

        acc_raw = cell(Col.ACCESSORIES)
        merged = replace(
            base, name=keep(Col.NAME, base.name), category_id=category_id, serial_number=keep(Col.SERIAL, base.serial_number),
            acquisition_date=acq, acquisition_cost=cost, location_id=location_id, department_id=department_id,
            division_id=division_id, condition=cond, status=status, warranty_until=warranty, notes=keep(Col.NOTES, base.notes),
            qr_code=qr, user_location_detail=keep(Col.USER_LOCATION, base.user_location_detail),
            pr_number=keep(Col.PR, base.pr_number), po_number=keep(Col.PO, base.po_number), gr_number=keep(Col.GR, base.gr_number),
            requester=keep(Col.REQUESTER, base.requester), memo_number=keep(Col.MEMO_NO, base.memo_number), memo_date=memo,
            follow_up=keep(Col.FOLLOW_UP, base.follow_up), handover_doc=keep(Col.HANDOVER, base.handover_doc),
            accessories=(normalize_accessories(acc_raw) or "") if acc_raw else base.accessories, vendor_id=vendor_id,
        )
        if not errors:
            errors.extend(validate(merged, today).values())
        rows.append(ImportRow(n, merged if not errors else None, errors, unchanged=not errors and merged == base))
    return ImportPreview(rows)


def parse_auto(sheet: list[list[str]], lk: Lookups, existing: dict[str, AssetInput], today: _dt.date) -> ImportPreview:
    """Satu alur: Kode Aset yang sudah ada diperbarui, sisanya ditambahkan sebagai aset baru."""
    if not sheet:
        return ImportPreview([], "File kosong")
    header = sheet[0]
    idx = _index(header)
    if idx[Col.CODE] < 0:
        return ImportPreview([], f"Kolom {Col.CODE.header} tidak ditemukan. Gunakan template atau hasil export dari aplikasi.")
    data = [(i + 2, cells) for i, cells in enumerate(sheet[1:]) if any(str(x).strip() for x in cells)]
    code_of = lambda cells: normalize_code(cells[idx[Col.CODE]] if idx[Col.CODE] < len(cells) else "")  # noqa: E731
    upd = [(n, c) for n, c in data if code_of(c) in existing]
    add = [(n, c) for n, c in data if code_of(c) not in existing]
    out: list[ImportRow] = []
    if add:
        missing = [c for c in COLS if c.required and idx[c] < 0]
        if missing:
            msg = f"Aset baru memerlukan kolom {', '.join(c.header for c in missing)} (tidak ada di file)"
            out += [ImportRow(n, None, [msg], action=Action.NEW) for n, _ in add]
        else:
            p = parse_new([header] + [c for _, c in add], lk, today)
            out += [replace(r, row_number=add[r.row_number - 2][0], action=Action.NEW) for r in p.rows]
    if upd:
        p = parse_update([header] + [c for _, c in upd], lk, existing, today)
        if p.header_error:
            out += [ImportRow(n, None, [p.header_error], action=Action.UPDATE) for n, _ in upd]
        else:
            out += [replace(r, row_number=upd[r.row_number - 2][0], action=Action.UPDATE) for r in p.rows]
    return ImportPreview(sorted(out, key=lambda r: r.row_number))


# ======================================================================== baca/tulis file
def _cell_text(v) -> str:
    if v is None:
        return ""
    if isinstance(v, _dt.datetime):
        return v.strftime("%d/%m/%Y")
    if isinstance(v, _dt.date):
        return v.strftime("%d/%m/%Y")
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, float):
        return str(int(v)) if v.is_integer() else repr(v)
    return str(v)


def read_first_sheet(path: str | Path) -> list[list[str]]:
    from openpyxl import load_workbook

    wb = load_workbook(path, read_only=True, data_only=True)
    try:
        ws = wb.worksheets[0]
        rows = [[_cell_text(v) for v in row] for row in ws.iter_rows(values_only=True)]
    finally:
        wb.close()
    while rows and not any(x.strip() for x in rows[-1]):
        rows.pop()
    return rows


def _style_header(ws, widths: list[int] | None = None) -> None:
    from openpyxl.styles import Font, PatternFill
    from openpyxl.utils import get_column_letter

    for i, c in enumerate(ws[1], start=1):
        c.font = Font(bold=True, color="FFFFFF")
        c.fill = PatternFill("solid", fgColor="0060AF")
        w = widths[i - 1] if widths and i - 1 < len(widths) else max(12, min(40, len(str(c.value or "")) + 2))
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = "A2"


def _add_list_validation(wb, ws, column_index: int, values: list[str], list_col: int, last_row: int = 5000) -> None:
    """Dropdown dari daftar nilai di lembar tersembunyi "_daftar" (tidak terbatas 255 karakter)."""
    from openpyxl.utils import get_column_letter
    from openpyxl.worksheet.datavalidation import DataValidation

    if not values:
        return
    if "_daftar" in wb.sheetnames:
        lst = wb["_daftar"]
    else:
        lst = wb.create_sheet("_daftar")
        lst.sheet_state = "hidden"
    letter = get_column_letter(list_col)
    for r, v in enumerate(values, start=1):
        lst[f"{letter}{r}"] = v
    dv = DataValidation(type="list", formula1=f"='_daftar'!${letter}$1:${letter}${len(values)}", allow_blank=True)
    dv.error = "Pilih dari daftar"
    dv.errorTitle = "Nilai tidak valid"
    ws.add_data_validation(dv)
    col = get_column_letter(column_index + 1)
    dv.add(f"{col}2:{col}{last_row}")


def _safe_text(v: str) -> str:
    """Cegah formula injection saat dibuka di spreadsheet (sama dengan CsvWriter Android)."""
    if v and v[0] in "=+-@":
        try:
            float(v)
        except ValueError:
            return "'" + v
    return v


class ExcelService:
    def __init__(self, assets: AssetService, masters):
        self.assets = assets
        self.masters = masters
        self.db = assets.db

    def _statuses(self):
        return self.masters.list(MasterType.STATUS)

    def _write_assets_sheet(self, wb, ws, headers, rows, widths=None):
        ws.append(headers)
        for r in rows:
            ws.append([_safe_text(v) if isinstance(v, str) else v for v in r])
        _style_header(ws, widths)
        _add_list_validation(wb, ws, COLS.index(Col.CONDITION), [c.label for c in AssetCondition], 1)
        _add_list_validation(wb, ws, COLS.index(Col.STATUS), [s["name"] for s in self._statuses()], 2)

    # ---------------------------------------------------------------- template
    def write_template(self, path: str | Path) -> None:
        from openpyxl import Workbook

        cats = self.masters.list(MasterType.CATEGORY)
        locs = self.masters.list(MasterType.LOCATION)
        deps = self.masters.list(MasterType.DEPARTMENT)
        divs = self.masters.list(MasterType.DIVISION)
        statuses = self._statuses()
        vendors = self.masters.list(MasterType.VENDOR)
        year = fmt.today().year
        prefix = cats[0]["codePrefix"] if cats else "IT"
        sample = {
            Col.CODE: f"{prefix}-{year}-001", Col.QR: f"{prefix}-{year}-001", Col.NAME: "Laptop Dell Latitude 5440",
            Col.CATEGORY: cats[0]["name"] if cats else "Elektronik & IT", Col.SERIAL: "SN123456", Col.ACQ_DATE: "15/01/2024",
            Col.COST: 15_000_000, Col.LOCATION: locs[0]["name"] if locs else "Kantor Pusat",
            Col.DEPARTMENT: deps[0]["name"] if deps else "", Col.CONDITION: AssetCondition.BAIK.label,
            Col.STATUS: statuses[0]["name"] if statuses else "Digunakan", Col.WARRANTY: "15/01/2027",
            Col.NOTES: "Contoh baris, hapus sebelum import. Kode QR boleh sama dengan Kode Aset.",
            Col.USER_LOCATION: "Budi · Ruang 301", Col.DIVISION: divs[0]["name"] if divs else "", Col.PR: "PR-2024-0012",
            Col.PO: "PO-2024-0034", Col.GR: "GR-2024-0056", Col.REQUESTER: "Citra", Col.MEMO_NO: "MEMO/GA/2024/007",
            Col.MEMO_DATE: "10/01/2024", Col.FOLLOW_UP: "Diserahkan ke pengguna", Col.HANDOVER: "BAST/GA/2024/015",
            Col.ACCESSORIES: "Charger, Tas laptop", Col.VENDOR: vendors[0]["name"] if vendors else "",
        }
        wb = Workbook()
        ws = wb.active
        ws.title = "Import Aset"
        self._write_assets_sheet(wb, ws, [c.template_header for c in COLS], [[sample.get(c, "") for c in COLS]],
                                 [34 if c is Col.STATUS else 22 for c in COLS])
        guide = wb.create_sheet("Petunjuk", 1)
        guide.append(["Kategori", "Lokasi", "Divisi", "Departemen (Divisi induk)", "Kondisi", "Status", "Vendor"])
        cols = [
            [f"{c['name']} ({c['codePrefix']})" for c in cats], [l["name"] for l in locs], [d["name"] for d in divs],
            [d["name"] + (f" — Divisi {d['divisionName']}" if d["divisionName"] else "") for d in deps],
            [c.label for c in AssetCondition], [s["name"] for s in statuses], [v["name"] for v in vendors],
        ]
        for i in range(max(len(c) for c in cols)):
            guide.append([c[i] if i < len(c) else "" for c in cols])
        _style_header(guide, [32, 28, 24, 40, 16, 22, 28])
        wb.save(path)

    # ---------------------------------------------------------------- export
    EXTRA_HEADERS = ["Status Garansi", "Umur Berjalan", "Umur Ekonomis (th)", "Residu (%)", "Penyusutan/Tahun",
                     "Akumulasi Penyusutan", "Nilai Buku", "Merk/Tipe (lama)", "PIC (lama)"]

    def export_rows(self) -> tuple[list[str], list[list]]:
        today = fmt.today()
        default_res = self.db.default_residual_percent()
        rows = []
        for r in self.db.q(DETAIL_SELECT + " WHERE a.deletedAt IS NULL ORDER BY a.code"):
            acq = fmt.from_epoch_day(r["acquisitionDate"])
            as_of = (fmt.from_epoch_day(r["disposalDate"]) or today) if r["status"] == WRITE_OFF_ID else today
            res = r["categoryResidualPercent"] if r["categoryResidualPercent"] is not None else default_res
            dep = calc.depreciation(r["acquisitionCost"], acq, r["usefulLifeYears"], res, as_of)
            y, m = calc.asset_age(acq, as_of)
            dn = lambda v: fmt.date_numeric(fmt.from_epoch_day(v)) if v is not None else ""  # noqa: E731
            cells = {
                Col.CODE: r["code"], Col.QR: r["qrCode"] or r["code"], Col.NAME: r["name"], Col.CATEGORY: r["categoryName"],
                Col.SERIAL: r["serialNumber"] or "", Col.ACQ_DATE: dn(r["acquisitionDate"]), Col.COST: r["acquisitionCost"],
                Col.LOCATION: r["locationName"], Col.DIVISION: r["divisionName"] or "", Col.DEPARTMENT: r["departmentName"] or "",
                Col.CONDITION: AssetCondition[r["condition"]].label, Col.STATUS: r["statusName"] or r["status"],
                Col.WARRANTY: dn(r["warrantyUntil"]), Col.NOTES: r["notes"] or "", Col.USER_LOCATION: r["userLocationDetail"] or "",
                Col.PR: r["prNumber"] or "", Col.PO: r["poNumber"] or "", Col.GR: r["grNumber"] or "",
                Col.REQUESTER: r["requester"] or "", Col.MEMO_NO: r["memoNumber"] or "", Col.MEMO_DATE: dn(r["memoDate"]),
                Col.FOLLOW_UP: r["followUp"] or "", Col.HANDOVER: r["handoverDoc"] or "", Col.ACCESSORIES: r["accessories"] or "",
                Col.VENDOR: r["vendorName"] or "",
            }
            rows.append([cells[c] for c in COLS] + [
                calc.warranty_status(fmt.from_epoch_day(r["warrantyUntil"]), today), fmt.age(y, m), r["usefulLifeYears"],
                res, dep.annual_depreciation, dep.accumulated, dep.book_value, r["brandModel"] or "", r["picName"] or "",
            ])
        return [c.template_header for c in COLS] + self.EXTRA_HEADERS, rows

    def export_xlsx(self, path: str | Path, audit=None) -> int:
        from openpyxl import Workbook

        headers, rows = self.export_rows()
        wb = Workbook()
        ws = wb.active
        ws.title = "Aset"
        self._write_assets_sheet(wb, ws, headers, rows)
        wb.save(path)
        if audit:
            audit.log(AuditEntityType.SYSTEM, "ASSETS", AuditAction.UPDATE, "Export Register aset (XLSX)")
        return len(rows)

    def export_csv(self, path: str | Path, audit=None) -> int:
        headers, rows = self.export_rows()

        def text(v) -> str:
            if isinstance(v, float):
                return str(int(v)) if v.is_integer() else str(v).replace(".", ",")
            return str(v)

        with open(path, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f, delimiter=";", lineterminator="\r\n")
            w.writerow(headers)
            for r in rows:
                w.writerow([_safe_text(text(v)) for v in r])
        if audit:
            audit.log(AuditEntityType.SYSTEM, "ASSETS", AuditAction.UPDATE, "Export Register aset (CSV)")
        return len(rows)

    # ---------------------------------------------------------------- import
    def lookups(self) -> Lookups:
        cats = self.masters.list(MasterType.CATEGORY)
        deps = self.masters.list(MasterType.DEPARTMENT)
        codes = {r[0].upper() for r in self.db.q("SELECT code FROM assets UNION SELECT qrCode FROM assets WHERE qrCode IS NOT NULL")}
        return Lookups(
            categories={c["name"].lower(): c["id"] for c in cats} | {c["codePrefix"].lower(): c["id"] for c in cats},
            locations={l["name"].lower(): l["id"] for l in self.masters.list(MasterType.LOCATION)},
            departments={d["name"].lower(): d["id"] for d in deps},
            divisions={d["name"].lower(): d["id"] for d in self.masters.list(MasterType.DIVISION)},
            department_division={d["id"]: d["divisionId"] for d in deps if d["divisionId"]},
            statuses={status_key(s["name"]): s["id"] for s in self._statuses()},
            vendors={v["name"].lower(): v["id"] for v in self.masters.list(MasterType.VENDOR)},
            existing_codes=codes,
        )

    def preview(self, path: str | Path) -> ImportPreview:
        try:
            sheet = read_first_sheet(path)
        except Exception as e:  # noqa: BLE001 - file pengguna bisa rusak dengan berbagai cara
            return ImportPreview([], f"File tidak dapat dibaca: {e or 'format bukan .xlsx'}")
        if len(sheet) > MAX_IMPORT_ROWS + 1:
            return ImportPreview([], f"Maksimal {MAX_IMPORT_ROWS} baris per import")
        existing = {r["code"].upper(): AssetInput.from_row(r) for r in self.db.q("SELECT * FROM assets WHERE deletedAt IS NULL")}
        return parse_auto(sheet, self.lookups(), existing, fmt.today())

    def commit(self, preview: ImportPreview, audit, progress=None) -> dict:
        self.assets.session.require(lambda r: r.can_edit_data, "Anda tidak memiliki akses untuk import")
        rows = preview.to_save
        failed: list[ImportRow] = []
        created = updated = 0
        for i, row in enumerate(rows):
            try:
                self.assets.save(row.input)
                if row.action is Action.UPDATE:
                    updated += 1
                else:
                    created += 1
            except AppError as e:
                failed.append(replace(row, errors=[e.message, *e.fields.values()]))
            if progress:
                progress(i + 1, len(rows))
        failed_count = len(failed) + len(preview.invalid)
        audit.log(AuditEntityType.ASSET, "import", AuditAction.IMPORT,
                  f"Import aset dari Excel: {created} baru, {updated} diperbarui, {len(preview.unchanged)} tanpa perubahan, {failed_count} gagal")
        return {"created": created, "updated": updated, "unchanged": len(preview.unchanged), "failed": preview.invalid + failed}


# ======================================================================== servis & stok
def _write_sheet(wb, title: str, headers: list[str], rows: list[list], widths: list[int] | None = None, first: bool = False):
    ws = wb.active if first else wb.create_sheet(title)
    ws.title = title
    ws.append(headers)
    for r in rows:
        ws.append([_safe_text(v) if isinstance(v, str) else v for v in r])
    _style_header(ws, widths)
    return ws


def _write_csv(path, headers: list[str], rows: list[list]) -> None:
    def text(v) -> str:
        if isinstance(v, float):
            return str(int(v)) if v.is_integer() else str(v).replace(".", ",")
        return "" if v is None else str(v)

    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, delimiter=";", lineterminator="\r\n")
        w.writerow(headers)
        for r in rows:
            w.writerow([_safe_text(text(v)) for v in r])


SERVICE_HEADERS = ["Tanggal", "Kode Aset", "Nama Aset", "Jenis", "Vendor", "Biaya", "Deskripsi", "Servis Berikutnya", "Status Jadwal"]
STOCK_HEADERS = ["Kode", "Barcode", "Nama Barang", "Satuan", "Lokasi Simpan", "Stok Awal", "Min. Stok", "Stok Akhir", "Status"]
TXN_HEADERS = ["Tanggal", "Kode Barang", "Nama Barang", "Jenis", "Jumlah", "Referensi", "Catatan", "Dicatat oleh"]
STOCK_TEMPLATE_HEADERS = ["Kode Barang*", "Barcode", "Nama Barang*", "Satuan*", "Lokasi Simpan", "Stok Awal", "Min. Stok", "Catatan"]


def service_rows(ops) -> list[list]:
    from .models import ServiceType
    from .operations import service_due_status

    today = fmt.today()
    out = []
    for s in ops.services():
        nxt = fmt.from_epoch_day(s["nextServiceDate"])
        out.append([fmt.date(fmt.from_epoch_day(s["serviceDate"])), s["assetCode"], s["assetName"], ServiceType[s["type"]].label,
                    s["vendorName"] or "", s["cost"], s["description"], fmt.date(nxt) if nxt else "",
                    service_due_status(nxt, today) if nxt else ""])
    return out


def stock_rows(stock) -> tuple[list[list], list[list]]:
    from .models import StockTxnType
    from .stock import needs_reorder, round2

    items = [[i["code"], i["barcode"] or "", i["name"], i["unitName"], i["storageLocationName"] or "", i["initialStock"], i["minStock"],
              round2(i["currentStock"]), "Reorder" if needs_reorder(i["currentStock"], i["minStock"]) else "Aman"] for i in stock.items()]
    txns = [[fmt.date(fmt.from_epoch_day(t["txnDate"])), t["itemCode"], t["itemName"], StockTxnType[t["type"]].label,
             t["qty"] if t["type"] == "IN" else -t["qty"], t["reference"] or "", t["note"] or "", t["createdBy"] or ""]
            for t in stock.all_txns()]
    return items, txns


def export_services(ops, path, kind: str, audit=None) -> int:
    from openpyxl import Workbook

    rows = service_rows(ops)
    if kind == "xlsx":
        wb = Workbook()
        _write_sheet(wb, "Servis", SERVICE_HEADERS, rows, first=True)
        wb.save(path)
    else:
        _write_csv(path, SERVICE_HEADERS, rows)
    if audit:
        audit.log(AuditEntityType.SYSTEM, "SERVICES", AuditAction.UPDATE, f"Export Riwayat servis ({kind.upper()})")
    return len(rows)


def export_stock(stock, path, kind: str, audit=None) -> int:
    """XLSX: lembar Stok + Transaksi. CSV hanya lembar Stok (sama dengan Android)."""
    from openpyxl import Workbook

    items, txns = stock_rows(stock)
    if kind == "xlsx":
        wb = Workbook()
        _write_sheet(wb, "Stok", STOCK_HEADERS, items, first=True)
        _write_sheet(wb, "Transaksi", TXN_HEADERS, txns)
        wb.save(path)
    else:
        _write_csv(path, STOCK_HEADERS, items)
    if audit:
        audit.log(AuditEntityType.SYSTEM, "STOCK", AuditAction.UPDATE, f"Export Stok & transaksi ({kind.upper()})")
    return len(items)


def write_stock_template(masters, path) -> None:
    from openpyxl import Workbook

    units = masters.list(MasterType.UNIT)
    locs = masters.list(MasterType.LOCATION)
    wb = Workbook()
    _write_sheet(wb, "Import Barang", STOCK_TEMPLATE_HEADERS,
                 [["ATK-001", "8991234567890", "Kertas A4 80 gram", units[0]["name"] if units else "Rim", locs[0]["name"] if locs else "",
                   20, 5, "Contoh baris, hapus sebelum import"]], [18, 22, 32, 14, 24, 12, 12, 34], first=True)
    guide = [[units[i]["name"] if i < len(units) else "", locs[i]["name"] if i < len(locs) else ""] for i in range(max(len(units), len(locs)))]
    _write_sheet(wb, "Petunjuk", ["Satuan", "Lokasi Simpan"], guide, [24, 30])
    wb.save(path)


@dataclass
class StockRow:
    row_number: int
    input: object | None
    errors: list[str]

    @property
    def is_valid(self) -> bool:
        return self.input is not None and not self.errors


@dataclass
class StockPreview:
    rows: list[StockRow]
    header_error: str | None = None

    @property
    def valid(self):
        return [r for r in self.rows if r.is_valid]

    @property
    def invalid(self):
        return [r for r in self.rows if not r.is_valid]


def parse_stock(sheet: list[list[str]], units: dict[str, str], locations: dict[str, str], codes: set[str], barcodes: set[str]) -> StockPreview:
    """Hanya barang baru; kode/barcode yang sudah ada ditolak. Kolom dicocokkan lewat judul."""
    from .stock import FIELD_UNIT, ItemInput, validate_item

    if not sheet:
        return StockPreview([], "File kosong")
    header = [normalize_header(h) for h in sheet[0]]

    def col(*names: str) -> int:
        return next((i for i, h in enumerate(header) if h in names), -1)

    c_code, c_bar, c_name = col("kodebarang", "kode"), col("barcode"), col("namabarang", "nama")
    c_unit, c_loc = col("satuan"), col("lokasisimpan", "lokasi")
    c_init, c_min, c_notes = col("stokawal"), col("minstok", "minimumstok"), col("catatan")
    missing = [n for n, i in (("Kode Barang", c_code), ("Nama Barang", c_name), ("Satuan", c_unit)) if i < 0]
    if missing:
        return StockPreview([], f"Kolom wajib tidak ditemukan: {', '.join(missing)}. Gunakan template dari aplikasi.")
    seen_c: set[str] = set()
    seen_b: set[str] = set()
    rows: list[StockRow] = []
    for i, cells in enumerate(sheet[1:]):
        if all(not str(x).strip() for x in cells):
            continue
        cell = lambda idx: (cells[idx] if 0 <= idx < len(cells) else "").strip()  # noqa: E731
        errors: list[str] = []
        c = cell(c_code).upper()
        if c:
            if c in codes:
                errors.append(f"Kode {c} sudah ada di aplikasi")
            if c in seen_c:
                errors.append(f"Kode {c} ganda di file")
            seen_c.add(c)
        b = cell(c_bar)
        if b:
            if b.upper() in barcodes:
                errors.append(f"Barcode {b} sudah dipakai barang lain")
            if b.upper() in seen_b:
                errors.append(f"Barcode {b} ganda di file")
            seen_b.add(b.upper())
        u_raw = cell(c_unit)
        unit_id = units.get(u_raw.lower()) if u_raw else None
        if unit_id is None:
            errors.append("Satuan kosong" if not u_raw else f'Satuan "{u_raw}" tidak dikenal')
        l_raw = cell(c_loc)
        loc_id = locations.get(l_raw.lower()) if l_raw else None
        if l_raw and loc_id is None:
            errors.append(f'Lokasi "{l_raw}" tidak dikenal')
        inp = ItemInput(code=c, barcode=b, name=cell(c_name), unit_id=unit_id, storage_location_id=loc_id,
                        initial_stock=cell(c_init) or "0", min_stock=cell(c_min) or "0", notes=cell(c_notes))
        errors += [m for f, m in validate_item(inp).items() if f != FIELD_UNIT]
        rows.append(StockRow(i + 2, inp if not errors else None, errors))
    return StockPreview(rows)


def preview_stock(stock, masters, path) -> StockPreview:
    try:
        sheet = read_first_sheet(path)
    except Exception as e:  # noqa: BLE001
        return StockPreview([], f"File tidak dapat dibaca: {e or 'format bukan .xlsx'}")
    if len(sheet) > MAX_IMPORT_ROWS + 1:
        return StockPreview([], f"Maksimal {MAX_IMPORT_ROWS} baris per import")
    items = stock.items()
    return parse_stock(
        sheet,
        {u["name"].lower(): u["id"] for u in masters.list(MasterType.UNIT)},
        {loc["name"].lower(): loc["id"] for loc in masters.list(MasterType.LOCATION)},
        {i["code"].upper() for i in items},
        {i["barcode"].upper() for i in items if i["barcode"]},
    )


def commit_stock(stock, preview: StockPreview, audit, progress=None) -> dict:
    stock.session.require(lambda r: r.can_edit_data, "Anda tidak memiliki akses untuk import")
    ok = 0
    failed: list[StockRow] = []
    valid = preview.valid
    for i, row in enumerate(valid):
        try:
            stock.save_item(row.input)
            ok += 1
        except AppError as e:
            failed.append(StockRow(row.row_number, None, [e.message, *e.fields.values()]))
        if progress:
            progress(i + 1, len(valid))
    audit.log(AuditEntityType.ITEM, "import", AuditAction.IMPORT,
              f"Import barang dari Excel: {ok} berhasil, {len(failed) + len(preview.invalid)} gagal")
    return {"imported": ok, "failed": preview.invalid + failed}
