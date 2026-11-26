package id.perusahaan.asetga.domain.model

import id.perusahaan.asetga.domain.calc.PolicyStatus
import java.time.LocalDate

data class InsuranceType(val id: String, val name: String, val isActive: Boolean = true)

data class Insurer(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
)

data class InsurancePolicy(
    val id: String,
    val policyNumber: String,
    val typeId: String,
    val typeName: String,
    val insurerId: String,
    val insurerName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val premium: Long?,
    val sumInsured: Long?,
    val notes: String?,
    val assetCount: Int,
    /** Jumlah nilai pertanggungan per aset yang diisi. */
    val itemsSumInsured: Long,
    /** Jumlah harga perolehan aset yang ditanggung. */
    val itemsAcquisitionCost: Long,
    val status: PolicyStatus,
    val daysLeft: Long,
)

data class PolicyInput(
    val id: String? = null,
    val policyNumber: String = "",
    val typeId: String? = null,
    val insurerId: String? = null,
    val startDate: LocalDate? = LocalDate.now(),
    val endDate: LocalDate? = LocalDate.now().plusYears(1).minusDays(1),
    val premium: Long? = null,
    val sumInsured: Long? = null,
    val notes: String = "",
)

data class InsuredAsset(
    val assetId: String,
    val code: String,
    val qrCode: String?,
    val name: String,
    val categoryName: String,
    val locationName: String,
    val status: AssetStatus,
    val acquisitionCost: Long,
    val sumInsured: Long?,
)

/** Hasil penambahan aset ke polis. */
data class AddAssetsResult(val added: Int, val alreadyInPolicy: Int)

/** Baris file upload aset polis: kode (aset atau QR) + nilai pertanggungan opsional. */
data class InsuranceImportRow(
    val rowNumber: Int,
    val code: String,
    val sumInsured: Long?,
    val assetId: String?,
    val assetLabel: String?,
    val errors: List<String>,
    /** Sudah ada di polis: tidak ditambahkan lagi (nilai pertanggungan diperbarui bila diisi). */
    val alreadyInPolicy: Boolean = false,
) {
    val isValid: Boolean get() = assetId != null && errors.isEmpty()
}

data class InsuranceImportPreview(val rows: List<InsuranceImportRow>, val headerError: String? = null) {
    val valid: List<InsuranceImportRow> get() = rows.filter { it.isValid }
    val invalid: List<InsuranceImportRow> get() = rows.filterNot { it.isValid }
}
