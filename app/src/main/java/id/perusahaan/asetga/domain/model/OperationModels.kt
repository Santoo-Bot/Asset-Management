package id.perusahaan.asetga.domain.model

import id.perusahaan.asetga.domain.calc.ServiceDueStatus
import id.perusahaan.asetga.domain.calc.StockCalc
import java.time.LocalDate

data class MutationInput(
    val assetId: String,
    val date: LocalDate? = LocalDate.now(),
    val toLocationId: String? = null,
    val toDepartmentId: String? = null,
    val toPic: String = "",
    val reason: String = "",
    val approvedBy: String = "",
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
