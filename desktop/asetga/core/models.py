"""Enum dan konstanta domain. Nilai `name` sama persis dengan yang disimpan aplikasi Android."""

from __future__ import annotations

from enum import Enum


class AppError(Exception):
    """Galat yang pesannya aman ditampilkan ke pengguna. [fields] = galat per isian form."""

    def __init__(self, message: str, fields: dict[str, str] | None = None):
        super().__init__(message)
        self.message = message
        self.fields = fields or {}


MSG_NO_ACCESS = "Anda tidak memiliki akses untuk tindakan ini"


class Role(Enum):
    ADMIN = "Admin GA"
    STAFF = "Staf GA"
    VIEWER = "Viewer"

    @property
    def label(self) -> str:
        return self.value

    @property
    def can_edit_data(self) -> bool:
        """Tambah/ubah aset, mutasi, servis, stok, import."""
        return self in (Role.ADMIN, Role.STAFF)

    @property
    def can_manage_master(self) -> bool:
        """Master data, hapus permanen, backup/restore, pengaturan sistem."""
        return self is Role.ADMIN

    @property
    def can_manage_users(self) -> bool:
        return self is Role.ADMIN


class AssetCondition(Enum):
    BAIK = "Baik"
    RUSAK = "Rusak"

    @property
    def label(self) -> str:
        return self.value


WRITE_OFF_ID = "WRITE_OFF"

# Status bawaan (id tetap, sama dengan Android) beserta nama awalnya, berurutan.
DEFAULT_STATUS_LABELS: dict[str, str] = {
    "DIGUNAKAN": "Digunakan",
    "TERSEDIA": "Tersedia",
    "DALAM_PERBAIKAN": "Dalam Perbaikan",
    "PROPOSE_WRITE_OFF": "Propose To Write Off",
    WRITE_OFF_ID: "Write Off",
}


class AuditEntityType(Enum):
    ASSET = "Aset"
    ITEM = "Barang"
    STOCK = "Stok"
    MUTATION = "Mutasi"
    SERVICE = "Servis"
    MASTER = "Master data"
    USER = "Pengguna"
    INSURANCE = "Asuransi"
    SYSTEM = "Sistem"

    @property
    def label(self) -> str:
        return self.value


class AuditAction(Enum):
    CREATE = "Tambah"
    UPDATE = "Ubah"
    DEACTIVATE = "Nonaktifkan"
    DELETE = "Hapus"
    RESTORE = "Aktifkan kembali"
    IMPORT = "Import"
    RESTORE_BACKUP = "Restore backup"

    @property
    def label(self) -> str:
        return self.value


class MasterType(Enum):
    # (label, singular, tabel)
    CATEGORY = ("Kategori", "kategori", "categories")
    LOCATION = ("Lokasi", "lokasi", "locations")
    DIVISION = ("Divisi", "divisi", "divisions")
    DEPARTMENT = ("Departemen", "departemen", "departments")
    VENDOR = ("Vendor", "vendor", "vendors")
    UNIT = ("Satuan", "satuan", "units")
    INSURANCE_TYPE = ("Tipe asuransi", "tipe asuransi", "insurance_types")
    INSURER = ("Perusahaan asuransi", "perusahaan asuransi", "insurers")
    STATUS = ("Status aset", "status", "asset_statuses")

    @property
    def label(self) -> str:
        return self.value[0]

    @property
    def singular(self) -> str:
        return self.value[1]

    @property
    def table(self) -> str:
        return self.value[2]


ASSET_SORTS: dict[str, str] = {
    "CODE": "Kode aset",
    "NAME": "Nama (A–Z)",
    "NEWEST": "Perolehan terbaru",
    "OLDEST": "Perolehan terlama",
    "COST_DESC": "Harga tertinggi",
}


def parse_accessories(text: str | None) -> list[str]:
    """Kelengkapan dipisah koma/titik koma/baris baru; duplikat (tanpa beda huruf besar) dibuang."""
    seen: set[str] = set()
    out: list[str] = []
    for part in (text or "").replace(";", ",").replace("\n", ",").split(","):
        p = part.strip()
        if p and p.lower() not in seen:
            seen.add(p.lower())
            out.append(p)
    return out


def normalize_accessories(text: str | None) -> str | None:
    """Bentuk baku untuk disimpan: "Charger, Casing"; None bila kosong."""
    return ", ".join(parse_accessories(text)) or None
