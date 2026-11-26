package id.perusahaan.asetga.domain.validation

import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Vendor

/** Validasi murni (tanpa akses database). Kunci map = nama field di form. */
object MasterValidator {
    const val FIELD_NAME = "name"
    const val FIELD_PREFIX = "codePrefix"
    const val FIELD_LIFE = "usefulLifeYears"
    const val FIELD_RESIDUAL = "residualPercent"
    const val FIELD_EMAIL = "email"
    const val FIELD_PHONE = "phone"
    const val FIELD_DIVISION = "division"

    private val prefixRegex = Regex("^[A-Z]{2,5}$")
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRegex = Regex("^[0-9+()\\-\\s]{6,20}$")

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Nama wajib diisi"
        name.trim().length > 100 -> "Nama maksimal 100 karakter"
        else -> null
    }

    fun validateResidualPercent(value: Double?): String? =
        if (value != null && (value < 0.0 || value >= 100.0)) "Nilai residu 0–99,99%" else null

    fun validateCategory(c: Category): Map<String, String> = buildMap {
        validateName(c.name)?.let { put(FIELD_NAME, it) }
        if (!prefixRegex.matches(c.codePrefix)) put(FIELD_PREFIX, "Kode 2–5 huruf kapital, mis. IT")
        if (c.usefulLifeYears !in 1..50) put(FIELD_LIFE, "Umur ekonomis 1–50 tahun")
        validateResidualPercent(c.residualPercent)?.let { put(FIELD_RESIDUAL, it) }
    }

    fun validateVendor(v: Vendor): Map<String, String> = buildMap {
        validateName(v.name)?.let { put(FIELD_NAME, it) }
        if (!v.email.isNullOrBlank() && !emailRegex.matches(v.email.trim())) put(FIELD_EMAIL, "Format email tidak valid")
        if (!v.phone.isNullOrBlank() && !phoneRegex.matches(v.phone.trim())) put(FIELD_PHONE, "Nomor telepon tidak valid")
    }
}
