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

/** Disederhanakan di skema v4 (Cukup → Baik; Rusak Ringan/Berat → Rusak). */
enum class AssetCondition(val label: String) {
    BAIK("Baik"),
    RUSAK("Rusak");

    val isDamaged: Boolean get() = this == RUSAK
}

/**
 * Status aset (skema v4). WRITE_OFF = aset sudah dihapusbukukan: tidak ikut daftar aktif/laporan
 * dan penyusutan berhenti pada tanggal write off. Data tidak dihapus permanen.
 */
enum class AssetStatus(val label: String) {
    DIGUNAKAN("Digunakan"),
    TERSEDIA("Tersedia"),
    DALAM_PERBAIKAN("Dalam Perbaikan"),
    PROPOSE_WRITE_OFF("Propose To Write Off"),
    WRITE_OFF("Write Off"),
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
    TAK_TERDAFTAR("Di luar daftar"),
}

enum class AuditEntityType(val label: String) {
    ASSET("Aset"),
    ITEM("Barang"),
    STOCK("Stok"),
    MUTATION("Mutasi"),
    SERVICE("Servis"),
    MASTER("Master data"),
    USER("Pengguna"),
    INSURANCE("Asuransi"),
    SYSTEM("Sistem"),
}

enum class AuditAction(val label: String) {
    CREATE("Tambah"),
    UPDATE("Ubah"),
    DEACTIVATE("Nonaktifkan"),
    DELETE("Hapus"),
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
    DIVISION("Divisi", "divisi"),
    DEPARTMENT("Departemen", "departemen"),
    VENDOR("Vendor", "vendor"),
    UNIT("Satuan", "satuan"),
    INSURANCE_TYPE("Tipe asuransi", "tipe asuransi"),
    INSURER("Perusahaan asuransi", "perusahaan asuransi"),
}
