package id.perusahaan.asetga.domain.model

import id.perusahaan.asetga.domain.calc.AssetAge
import id.perusahaan.asetga.domain.calc.DepreciationResult
import id.perusahaan.asetga.domain.calc.WarrantyStatus
import java.time.LocalDate

data class Asset(
    val id: String,
    val code: String,
    val name: String,
    val categoryId: String,
    val brandModel: String?,
    val serialNumber: String?,
    val acquisitionDate: LocalDate,
    val acquisitionCost: Long,
    val locationId: String,
    val departmentId: String?,
    val picName: String?,
    val condition: AssetCondition,
    val status: AssetStatus,
    val warrantyUntil: LocalDate?,
    val notes: String?,
    val photoPath: String?,
    val disposalDate: LocalDate?,
    val disposalValue: Long?,
)

/** Data yang diisi pengguna di form aset. [code] kosong = dibuat otomatis. */
data class AssetInput(
    val id: String? = null,
    val code: String = "",
    val name: String = "",
    val categoryId: String? = null,
    val brandModel: String = "",
    val serialNumber: String = "",
    val acquisitionDate: LocalDate? = null,
    val acquisitionCost: Long? = null,
    val locationId: String? = null,
    val departmentId: String? = null,
    val picName: String = "",
    val condition: AssetCondition = AssetCondition.BAIK,
    val status: AssetStatus = AssetStatus.AKTIF,
    val warrantyUntil: LocalDate? = null,
    val notes: String = "",
    val photoPath: String? = null,
    val disposalDate: LocalDate? = null,
    val disposalValue: Long? = null,
)

data class AssetListItem(
    val id: String,
    val code: String,
    val name: String,
    val categoryName: String,
    val locationName: String,
    val picName: String?,
    val condition: AssetCondition,
    val status: AssetStatus,
    val photoPath: String?,
    val warrantyUntil: LocalDate?,
    val acquisitionCost: Long,
)

data class AssetDetail(
    val asset: Asset,
    val categoryName: String,
    val usefulLifeYears: Int,
    /** Residu efektif (kategori, atau default bila kategori kosong). */
    val residualPercent: Double,
    val locationName: String,
    val departmentName: String?,
    val age: AssetAge,
    val depreciation: DepreciationResult,
    val warrantyStatus: WarrantyStatus,
)

enum class AssetSort(val label: String) {
    CODE("Kode aset"),
    NAME("Nama (A–Z)"),
    NEWEST("Perolehan terbaru"),
    OLDEST("Perolehan terlama"),
    COST_DESC("Harga tertinggi"),
}

data class AssetFilter(
    val query: String = "",
    val categoryId: String? = null,
    val locationId: String? = null,
    val condition: AssetCondition? = null,
    /** null = semua kecuali Dihapus/Dijual. */
    val status: AssetStatus? = null,
    val sort: AssetSort = AssetSort.CODE,
) {
    val activeFilterCount: Int get() = listOfNotNull(categoryId, locationId, condition, status).size
}

data class DeletedAsset(
    val id: String,
    val code: String,
    val name: String,
    val categoryName: String,
    val locationName: String,
    val deletedAt: Long,
)
