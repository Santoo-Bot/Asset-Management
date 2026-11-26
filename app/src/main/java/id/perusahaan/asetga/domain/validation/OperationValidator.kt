package id.perusahaan.asetga.domain.validation

import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.model.MutationInput
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.model.ServiceInput
import id.perusahaan.asetga.domain.model.StockTxnInput
import id.perusahaan.asetga.util.Fmt
import java.time.LocalDate

object OperationValidator {
    const val FIELD_DATE = "date"
    const val FIELD_TARGET = "target"
    const val FIELD_REASON = "reason"
    const val FIELD_DESCRIPTION = "description"
    const val FIELD_COST = "cost"
    const val FIELD_NEXT = "nextServiceDate"
    const val FIELD_CODE = "code"
    const val FIELD_NAME = "name"
    const val FIELD_UNIT = "unit"
    const val FIELD_INITIAL = "initialStock"
    const val FIELD_MIN = "minStock"
    const val FIELD_QTY = "qty"

    /** Mutasi valid bila lokasi atau pengguna / detail lokasi berubah. */
    fun validateMutation(
        input: MutationInput,
        currentLocationId: String,
        currentUserLocation: String?,
        acquisitionDate: LocalDate,
        today: LocalDate,
    ): Map<String, String> = buildMap {
        when {
            input.date == null -> put(FIELD_DATE, "Tanggal wajib diisi")
            input.date.isAfter(today) -> put(FIELD_DATE, "Tanggal tidak boleh di masa depan")
            input.date.isBefore(acquisitionDate) -> put(FIELD_DATE, "Tanggal sebelum tanggal perolehan aset")
        }
        if (input.toLocationId == null) put(FIELD_TARGET, "Pilih lokasi tujuan")
        val locChanged = input.toLocationId != null && input.toLocationId != currentLocationId
        val userChanged = input.toUserLocation.trim().ifEmpty { null } != currentUserLocation
        if (input.toLocationId != null && !locChanged && !userChanged) {
            put(FIELD_TARGET, "Ubah lokasi atau pengguna / detail lokasi tujuan")
        }
    }

    fun validateService(input: ServiceInput, today: LocalDate): Map<String, String> = buildMap {
        when {
            input.serviceDate == null -> put(FIELD_DATE, "Tanggal servis wajib diisi")
            input.serviceDate.isAfter(today) -> put(FIELD_DATE, "Tanggal servis tidak boleh di masa depan")
        }
        if (input.description.isBlank()) put(FIELD_DESCRIPTION, "Deskripsi pekerjaan wajib diisi")
        if (input.cost == null || input.cost < 0) put(FIELD_COST, "Biaya tidak valid")
        if (input.nextServiceDate != null && input.serviceDate != null && !input.nextServiceDate.isAfter(input.serviceDate)) {
            put(FIELD_NEXT, "Jadwal berikutnya harus setelah tanggal servis")
        }
    }

    const val FIELD_POLICY_NUMBER = "policyNumber"
    const val FIELD_TYPE = "type"
    const val FIELD_INSURER = "insurer"
    const val FIELD_START = "startDate"
    const val FIELD_END = "endDate"
    const val FIELD_PREMIUM = "premium"

    fun validatePolicy(input: PolicyInput): Map<String, String> = buildMap {
        if (input.policyNumber.isBlank()) put(FIELD_POLICY_NUMBER, "Nomor polis wajib diisi")
        else if (input.policyNumber.trim().length > 60) put(FIELD_POLICY_NUMBER, "Maksimal 60 karakter")
        if (input.typeId == null) put(FIELD_TYPE, "Pilih tipe asuransi")
        if (input.insurerId == null) put(FIELD_INSURER, "Pilih perusahaan asuransi")
        if (input.startDate == null) put(FIELD_START, "Tanggal mulai wajib diisi")
        when {
            input.endDate == null -> put(FIELD_END, "Tanggal berakhir wajib diisi")
            input.startDate != null && input.endDate.isBefore(input.startDate) -> put(FIELD_END, "Tanggal berakhir sebelum tanggal mulai")
        }
        if (input.premium != null && input.premium < 0) put(FIELD_PREMIUM, "Premi tidak valid")
    }

    private val itemCodeRegex = Regex("^[A-Z0-9][A-Z0-9./-]{1,29}$")

    fun validateItem(input: ItemInput): Map<String, String> = buildMap {
        val code = input.code.trim().uppercase()
        if (code.isEmpty()) put(FIELD_CODE, "Kode barang wajib diisi")
        else if (!itemCodeRegex.matches(code)) put(FIELD_CODE, "Gunakan huruf, angka, titik, garis miring, atau tanda hubung")
        if (input.name.isBlank()) put(FIELD_NAME, "Nama barang wajib diisi")
        if (input.unitId == null) put(FIELD_UNIT, "Pilih satuan")
        val initial = Fmt.parseDecimal(input.initialStock.ifBlank { "0" })
        if (initial == null || initial < 0) put(FIELD_INITIAL, "Stok awal tidak valid")
        val min = Fmt.parseDecimal(input.minStock.ifBlank { "0" })
        if (min == null || min < 0) put(FIELD_MIN, "Minimum stok tidak valid")
    }

    fun validateTxn(input: StockTxnInput, today: LocalDate): Map<String, String> = buildMap {
        val qty = Fmt.parseDecimal(input.qty)
        if (qty == null || qty <= 0) put(FIELD_QTY, "Jumlah harus lebih dari 0")
        when {
            input.date == null -> put(FIELD_DATE, "Tanggal wajib diisi")
            input.date.isAfter(today) -> put(FIELD_DATE, "Tanggal tidak boleh di masa depan")
        }
    }
}
