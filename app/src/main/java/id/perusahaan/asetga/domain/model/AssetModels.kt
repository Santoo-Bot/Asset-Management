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
    val qrCode: String? = null,
    val userLocationDetail: String? = null,
    val divisionId: String? = null,
    val prNumber: String? = null,
    val poNumber: String? = null,
    val grNumber: String? = null,
    val requester: String? = null,
    val memoNumber: String? = null,
    val memoDate: LocalDate? = null,
    val followUp: String? = null,
    val handoverDoc: String? = null,
    /** Kelengkapan, dipisah koma. */
    val accessories: String? = null,
    val vendorId: String? = null,
) {
    val accessoryList: List<String> get() = Accessories.parse(accessories)

    /** Isi yang dicetak di label QR. */
    val qrContent: String get() = qrCode ?: code
}

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
    val status: AssetStatus = AssetStatus.DIGUNAKAN,
    val warrantyUntil: LocalDate? = null,
    val notes: String = "",
    val photoPath: String? = null,
    val disposalDate: LocalDate? = null,
    val disposalValue: Long? = null,
    val qrCode: String = "",
    val userLocationDetail: String = "",
    val divisionId: String? = null,
    val prNumber: String = "",
    val poNumber: String = "",
    val grNumber: String = "",
    val requester: String = "",
    val memoNumber: String = "",
    val memoDate: LocalDate? = null,
    val followUp: String = "",
    val handoverDoc: String = "",
    /** Kelengkapan, dipisah koma, mis. "Charger, Casing". */
    val accessories: String = "",
    val vendorId: String? = null,
)

/** Kelengkapan aset (charger, casing, dll.) disimpan sebagai teks dipisah koma. */
object Accessories {
    fun parse(text: String?): List<String> = text.orEmpty()
        .split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

    /** Bentuk baku untuk disimpan: "Charger, Casing"; null bila kosong. */
    fun normalize(text: String?): String? = parse(text).joinToString(", ").ifEmpty { null }
}

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
    val qrCode: String? = null,
) {
    val qrContent: String get() = qrCode ?: code
}

data class AssetDetail(
    val asset: Asset,
    val categoryName: String,
    val usefulLifeYears: Int,
    /** Residu efektif (kategori, atau default bila kategori kosong). */
    val residualPercent: Double,
    val locationName: String,
    val departmentName: String?,
    val divisionName: String?,
    val vendorName: String? = null,
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
    val divisionId: String? = null,
    val departmentId: String? = null,
    val condition: AssetCondition? = null,
    /** null = semua status (termasuk Write Off). */
    val status: AssetStatus? = null,
    /** Sembunyikan aset Write Off (dipakai pemilih aset polis asuransi). */
    val excludeWriteOff: Boolean = false,
    val sort: AssetSort = AssetSort.CODE,
) {
    val activeFilterCount: Int get() = listOfNotNull(categoryId, locationId, divisionId, departmentId, condition, status).size
}

/** Hasil hapus aset (selalu permanen); [photoPaths] = file foto yang perlu dihapus dari penyimpanan. */
data class PurgeResult(val count: Int, val photoPaths: List<String>)
