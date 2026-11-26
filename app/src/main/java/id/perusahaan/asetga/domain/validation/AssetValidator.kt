package id.perusahaan.asetga.domain.validation

import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.AssetStatus
import java.time.LocalDate

object AssetValidator {
    const val FIELD_CODE = "code"
    const val FIELD_NAME = "name"
    const val FIELD_CATEGORY = "category"
    const val FIELD_ACQ_DATE = "acquisitionDate"
    const val FIELD_COST = "acquisitionCost"
    const val FIELD_LOCATION = "location"
    const val FIELD_WARRANTY = "warranty"
    const val FIELD_DISPOSAL_DATE = "disposalDate"
    const val FIELD_QR = "qrCode"

    const val MAX_COST = 1_000_000_000_000_000L // Rp 1 kuadriliun
    private val codeRegex = Regex("^[A-Z0-9][A-Z0-9./-]{1,39}$")

    fun normalizeCode(code: String): String = code.trim().uppercase().replace(Regex("\\s+"), "-")

    fun validate(input: AssetInput, today: LocalDate): Map<String, String> = buildMap {
        val code = normalizeCode(input.code)
        if (code.isNotEmpty() && !codeRegex.matches(code)) put(FIELD_CODE, "Gunakan huruf, angka, titik, garis miring, atau tanda hubung")
        when {
            input.name.isBlank() -> put(FIELD_NAME, "Nama aset wajib diisi")
            input.name.trim().length > 150 -> put(FIELD_NAME, "Maksimal 150 karakter")
        }
        if (input.categoryId == null) put(FIELD_CATEGORY, "Pilih kategori")
        if (input.locationId == null) put(FIELD_LOCATION, "Pilih lokasi")
        when {
            input.acquisitionDate == null -> put(FIELD_ACQ_DATE, "Tanggal perolehan wajib diisi")
            input.acquisitionDate.isAfter(today) -> put(FIELD_ACQ_DATE, "Tanggal perolehan tidak boleh di masa depan")
        }
        when {
            input.acquisitionCost == null -> put(FIELD_COST, "Harga perolehan wajib diisi")
            input.acquisitionCost < 0 -> put(FIELD_COST, "Harga tidak boleh negatif")
            input.acquisitionCost > MAX_COST -> put(FIELD_COST, "Harga terlalu besar")
        }
        if (input.warrantyUntil != null && input.acquisitionDate != null && input.warrantyUntil.isBefore(input.acquisitionDate)) {
            put(FIELD_WARRANTY, "Garansi tidak boleh sebelum tanggal perolehan")
        }
        val qr = input.qrCode.trim()
        if (qr.isNotEmpty()) {
            when {
                qr.length > 120 -> put(FIELD_QR, "Kode QR maksimal 120 karakter")
                qr.any { it.isISOControl() } -> put(FIELD_QR, "Kode QR berisi karakter tidak valid")
            }
        }
        if (input.status == AssetStatus.DIHAPUS_DIJUAL && input.disposalDate != null && input.acquisitionDate != null &&
            input.disposalDate.isBefore(input.acquisitionDate)
        ) {
            put(FIELD_DISPOSAL_DATE, "Tanggal penghapusan tidak boleh sebelum tanggal perolehan")
        }
    }
}
