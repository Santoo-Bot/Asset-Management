package id.perusahaan.asetga.domain.model

import id.perusahaan.asetga.domain.calc.ServiceDueStatus
import id.perusahaan.asetga.domain.calc.StockCalc
import java.time.LocalDate

/** Mutasi = pindah lokasi dan/atau pengguna / detail lokasi. Tanggal = hari ini. */
data class MutationInput(
    val assetId: String,
    val toLocationId: String? = null,
    val toUserLocation: String = "",
    val date: LocalDate? = LocalDate.now(),
)

data class AssetMutation(
    val id: String,
    val assetId: String,
    val date: LocalDate,
    val fromLocation: String?,
    val toLocation: String?,
    val fromDepartment: String?,
    val toDepartment: String?,
    val fromPic: String?,
    val toPic: String?,
    val reason: String,
    val approvedBy: String?,
    val createdBy: String?,
    val fromUserLocation: String? = null,
    val toUserLocation: String? = null,
)

data class ServiceInput(
    val id: String? = null,
    val assetId: String,
    val serviceDate: LocalDate? = LocalDate.now(),
    val type: ServiceType = ServiceType.PREVENTIF,
    val vendorId: String? = null,
    val cost: Long? = 0,
    val description: String = "",
    val photoPath: String? = null,
    val nextServiceDate: LocalDate? = null,
)

data class ServiceRecord(
    val id: String,
    val assetId: String,
    val assetCode: String,
    val assetName: String,
    val serviceDate: LocalDate,
    val type: ServiceType,
    val vendorId: String?,
    val vendorName: String?,
    val cost: Long,
    val description: String,
    val photoPath: String?,
    val nextServiceDate: LocalDate?,
)

/** Jadwal servis per aset = tanggal servis berikutnya dari catatan servis terakhir. */
data class ServiceSchedule(
    val assetId: String,
    val assetCode: String,
    val assetName: String,
    val lastServiceDate: LocalDate,
    val nextServiceDate: LocalDate,
    val status: ServiceDueStatus,
)

// ---------- Stok ----------

data class StockItem(
    val id: String,
    val code: String,
    val barcode: String?,
    val name: String,
    val unitId: String,
    val unitName: String,
    val storageLocationId: String?,
    val storageLocationName: String?,
    val initialStock: Double,
    val minStock: Double,
    val currentStock: Double,
    val notes: String?,
    val isActive: Boolean,
) {
    val needsReorder: Boolean get() = StockCalc.needsReorder(currentStock, minStock)
}

data class ItemInput(
    val id: String? = null,
    val code: String = "",
    val barcode: String = "",
    val name: String = "",
    val unitId: String? = null,
    val storageLocationId: String? = null,
    val initialStock: String = "0",
    val minStock: String = "0",
    val notes: String = "",
)

data class StockTxnInput(
    val itemId: String,
    val type: StockTxnType = StockTxnType.OUT,
    val qty: String = "",
    val date: LocalDate? = LocalDate.now(),
    val reference: String = "",
    val note: String = "",
)

data class StockTxn(
    val id: String,
    val itemId: String,
    val date: LocalDate,
    val type: StockTxnType,
    val qty: Double,
    val reference: String?,
    val note: String?,
    val createdBy: String?,
    val createdAt: Long,
)

// ---------- Stock opname ----------

data class OpnameSession(
    val id: String,
    val name: String,
    val date: java.time.LocalDate,
    val startedAt: Long,
    val finishedAt: Long?,
    val startedBy: String?,
    val note: String?,
    /** Jumlah aset dalam daftar sesi. */
    val total: Int,
    val found: Int,
    /** Ditemukan dengan kondisi fisik Rusak. */
    val damaged: Int,
    /** Hasil scan di luar daftar sesi. */
    val outside: Int,
) {
    /** Belum dicek (sesi berjalan) / tidak ditemukan (sesi selesai). */
    val notFound: Int get() = total - found
    val isFinished: Boolean get() = finishedAt != null
    /** Persentase aset dalam daftar yang ditemukan. */
    val foundPercent: Int get() = if (total == 0) 0 else found * 100 / total
}

data class OpnameItem(
    val id: String,
    val assetId: String?,
    /** Kode aset, atau kode hasil scan bila tidak dikenal. */
    val code: String,
    val name: String?,
    val qrCode: String?,
    val categoryName: String?,
    val locationName: String?,
    val userLocation: String?,
    val result: AuditResult,
    /** Kondisi fisik saat dicek. */
    val condition: AssetCondition?,
    /** Kondisi yang tercatat di data aset. */
    val systemCondition: AssetCondition?,
    val note: String?,
    val checkedAt: Long?,
)

data class OpnameAddResult(val added: Int, val alreadyInSession: Int, val skippedWriteOff: Int = 0) {
    fun message(): String = buildString {
        append("$added aset ditambahkan")
        if (alreadyInSession > 0) append(", $alreadyInSession sudah ada di sesi")
        if (skippedWriteOff > 0) append(", $skippedWriteOff Write Off dilewati")
    }
}

/** Baris file upload daftar aset opname (kode aset atau kode QR). */
data class OpnameImportRow(
    val rowNumber: Int,
    val code: String,
    val assetId: String?,
    val assetLabel: String?,
    val errors: List<String>,
    val alreadyInSession: Boolean = false,
) {
    val isValid: Boolean get() = errors.isEmpty() && assetId != null
}

data class OpnameImportPreview(val rows: List<OpnameImportRow>, val headerError: String? = null) {
    val valid: List<OpnameImportRow> get() = rows.filter { it.isValid }
    val invalid: List<OpnameImportRow> get() = rows.filterNot { it.isValid }
}

sealed interface OpnameScanOutcome {
    data class Found(val code: String, val name: String) : OpnameScanOutcome
    data class AlreadyScanned(val code: String, val name: String) : OpnameScanOutcome
    /** Aset terdaftar tetapi tidak ada di daftar sesi ini. */
    data class NotInList(val code: String, val name: String) : OpnameScanOutcome
    data class Unknown(val code: String) : OpnameScanOutcome
    data class Error(val message: String) : OpnameScanOutcome
}
