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
data class AssetStatus(val id: String) {
    /** Kompatibel dengan nilai yang tersimpan di kolom assets.status. */
    val name: String get() = id
    /** Nama dari master Status (bisa diubah Admin). */
    val label: String get() = AssetStatusNames.nameOf(id)
    val isWriteOff: Boolean get() = id == WRITE_OFF_ID
    val isBuiltIn: Boolean get() = id in DEFAULT_LABELS

    override fun toString(): String = id

    companion object {
        const val WRITE_OFF_ID = "WRITE_OFF"
        val DIGUNAKAN = AssetStatus("DIGUNAKAN")
        val TERSEDIA = AssetStatus("TERSEDIA")
        val DALAM_PERBAIKAN = AssetStatus("DALAM_PERBAIKAN")
        val PROPOSE_WRITE_OFF = AssetStatus("PROPOSE_WRITE_OFF")
        val WRITE_OFF = AssetStatus(WRITE_OFF_ID)

        /** Status bawaan (id tetap) beserta nama awalnya, berurutan. */
        val DEFAULT_LABELS: Map<String, String> = linkedMapOf(
            "DIGUNAKAN" to "Digunakan",
            "TERSEDIA" to "Tersedia",
            "DALAM_PERBAIKAN" to "Dalam Perbaikan",
            "PROPOSE_WRITE_OFF" to "Propose To Write Off",
            WRITE_OFF_ID to "Write Off",
        )
        val BUILT_IN: List<AssetStatus> = DEFAULT_LABELS.keys.map(::AssetStatus)

        fun valueOf(id: String): AssetStatus = AssetStatus(id)
    }
}

/** Nama status terbaru dari master Status; diperbarui otomatis saat master berubah. */
object AssetStatusNames {
    @Volatile private var names: Map<String, String> = emptyMap()

    fun update(map: Map<String, String>) { names = map }

    fun nameOf(id: String): String = names[id] ?: AssetStatus.DEFAULT_LABELS[id] ?: id
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
    STATUS("Status aset", "status"),
}
