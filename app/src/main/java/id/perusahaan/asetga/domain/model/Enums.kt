package id.perusahaan.asetga.domain.model

enum class Role(val label: String) {
    ADMIN("Admin GA"),
    STAFF("Staf GA"),
    VIEWER("Viewer");

    /** Tambah/ubah aset, mutasi, servis, stok. */
    val canEditData: Boolean get() = this == ADMIN || this == STAFF

    /** Kelola master data dan pengaturan sistem. */
    val canManageMaster: Boolean get() = this == ADMIN

    val canManageUsers: Boolean get() = this == ADMIN
}

enum class AssetCondition(val label: String) {
    BAIK("Baik"),
    CUKUP("Cukup"),
    RUSAK_RINGAN("Rusak Ringan"),
    RUSAK_BERAT("Rusak Berat");

    val isDamaged: Boolean get() = this == RUSAK_RINGAN || this == RUSAK_BERAT
}

enum class AssetStatus(val label: String) {
    AKTIF("Aktif"),
    DIPINJAM("Dipinjam"),
    DALAM_PERBAIKAN("Dalam Perbaikan"),
    DISIMPAN("Disimpan"),
    DIHAPUS_DIJUAL("Dihapus/Dijual"),
}

enum class ServiceType(val label: String) {
    PREVENTIF("Preventif"),
    KOREKTIF("Korektif"),
    KALIBRASI("Kalibrasi"),
    INSPEKSI("Inspeksi"),
}

enum class StockTxnType(val label: String) {
    IN("Masuk"),
    OUT("Keluar"),
}

enum class AuditResult(val label: String) {
    DITEMUKAN("Ditemukan"),
    TIDAK_DITEMUKAN("Tidak ditemukan"),
    TAK_TERDAFTAR("Tidak terdaftar di lokasi ini"),
}

enum class AuditEntityType(val label: String) {
    ASSET("Aset"),
    ITEM("Barang"),
    STOCK("Stok"),
    MUTATION("Mutasi"),
    SERVICE("Servis"),
    MASTER("Master data"),
    USER("Pengguna"),
    SYSTEM("Sistem"),
}

enum class AuditAction(val label: String) {
    CREATE("Tambah"),
    UPDATE("Ubah"),
    DEACTIVATE("Nonaktifkan"),
    RESTORE("Aktifkan kembali"),
    IMPORT("Import"),
    RESTORE_BACKUP("Restore backup"),
}

/** Disiapkan untuk sinkronisasi backend di fase lanjutan. */
enum class SyncState { PENDING, SYNCED }

enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
}

enum class MasterType(val label: String, val singular: String) {
    CATEGORY("Kategori", "kategori"),
    LOCATION("Lokasi", "lokasi"),
    DEPARTMENT("Departemen", "departemen"),
    VENDOR("Vendor", "vendor"),
    UNIT("Satuan", "satuan"),
}
