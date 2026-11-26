package id.perusahaan.asetga.domain.excel

import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.validation.AssetValidator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.math.roundToLong

/** Kolom template import aset, berurutan sesuai template. [templateHeader] = teks judul kolom di Excel. */
enum class ImportColumn(val templateHeader: String, val required: Boolean = false) {
    CODE("Kode Aset*", true),
    QR("Kode QR*", true),
    NAME("Nama Aset*", true),
    CATEGORY("Kategori*", true),
    SERIAL("No. Seri"),
    ACQ_DATE("Tgl Perolehan (dd/mm/yyyy)*", true),
    COST("Harga Perolehan*", true),
    LOCATION("Lokasi*", true),
    DIVISION("Divisi"),
    DEPARTMENT("Departemen"),
    CONDITION("Kondisi* (Baik,Rusak)", true),
    STATUS("Status* (Digunakan,Tersedia,Dalam Perbaikan,Propose To Write Off,Write Off)", true),
    WARRANTY("Garansi s/d (dd/mm/yyyy)"),
    NOTES("Catatan"),
    USER_LOCATION("Pengguna / Detail Lokasi"),
    PR("Nomor PR"),
    PO("Nomor PO"),
    GR("Nomor GR"),
    REQUESTER("Pemohon Pembelian"),
    MEMO_NO("Nomor Memo"),
    MEMO_DATE("Tanggal Memo (dd/mm/yyyy)"),
    FOLLOW_UP("Tindak Lanjut"),
    HANDOVER("Berita Acara Serah Terima"),
    ACCESSORIES("Kelengkapan (pisahkan dengan koma)");

    /** Nama kolom tanpa tanda wajib & keterangan, mis. "Kondisi". */
    val header: String get() = templateHeader.replace("*", "").substringBefore("(").trim()
}

data class ImportLookups(
    /** nama (huruf kecil) → id; kode prefix kategori juga diterima. */
    val categories: Map<String, String>,
    val locations: Map<String, String>,
    val departments: Map<String, String>,
    /** nama divisi (huruf kecil) → id. */
    val divisions: Map<String, String> = emptyMap(),
    /** id departemen → id divisi induknya. */
    val departmentDivision: Map<String, String> = emptyMap(),
    /** Kode aset & kode QR yang sudah ada di database (huruf besar). */
    val existingCodes: Set<String>,
)

data class ImportRow(
    /** Nomor baris di Excel (header = baris 1). */
    val rowNumber: Int,
    val input: AssetInput?,
    val errors: List<String>,
) {
    val isValid: Boolean get() = input != null && errors.isEmpty()
}

data class ImportPreview(val rows: List<ImportRow>, val headerError: String? = null) {
    val valid: List<ImportRow> get() = rows.filter { it.isValid }
    val invalid: List<ImportRow> get() = rows.filterNot { it.isValid }
}

/** Validasi per baris file import; tidak menyentuh database (lookup diberikan pemanggil). */
object AssetImportParser {
    private val dateFormats = listOf("d/M/uuuu", "d-M-uuuu", "uuuu-M-d", "d.M.uuuu").map {
        DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT)
    }
    private val excelEpoch: LocalDate = LocalDate.of(1899, 12, 30)

    fun normalizeHeader(h: String): String =
        h.lowercase().replace("*", "").substringBefore("(").replace(Regex("[^a-z0-9/]"), "")

    fun parseDate(raw: String): LocalDate? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        // Tanggal Excel disimpan sebagai nomor seri hari sejak 30-12-1899.
        t.toDoubleOrNull()?.let { serial -> if (serial in 1.0..2958465.0) return excelEpoch.plusDays(serial.toLong()) }
        for (f in dateFormats) runCatching { return LocalDate.parse(t, f) }
        return null
    }

    fun parseMoney(raw: String): Long? {
        val t = raw.trim().removePrefix("Rp").removePrefix("rp").trim()
        if (t.isEmpty()) return null
        // Angka dari sel numerik Excel: "12500000" / "1.25E7" / "1250000.5"
        if (Regex("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$").matches(t)) return t.toDouble().roundToLong()
        // Format Indonesia "1.250.000" atau "1.250.000,00"
        val noDecimals = t.substringBefore(',')
        val digits = noDecimals.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 15) return null
        return if (t.trim().startsWith("-")) -digits.toLong() else digits.toLong()
    }

    /** Nilai lama dari versi/template sebelumnya tetap diterima dan dipetakan ke nilai baru. */
    private val conditionAliases = mapOf("cukup" to "BAIK", "rusakringan" to "RUSAK", "rusakberat" to "RUSAK")
    private val statusAliases = mapOf(
        "aktif" to "DIGUNAKAN", "dipinjam" to "DIGUNAKAN", "disimpan" to "TERSEDIA",
        "dihapusdijual" to "WRITE_OFF", "dihapus" to "WRITE_OFF", "dijual" to "WRITE_OFF",
        "propose" to "PROPOSE_WRITE_OFF", "proposewriteoff" to "PROPOSE_WRITE_OFF", "writeoff" to "WRITE_OFF",
    )

    private fun matchEnum(raw: String, entries: List<Pair<String, String>>, aliases: Map<String, String> = emptyMap()): String? {
        val key = raw.trim().lowercase().replace(Regex("[^a-z]"), "")
        if (key.isEmpty()) return null
        return entries.firstOrNull { (name, label) ->
            key == name.lowercase().replace("_", "") || key == label.lowercase().replace(Regex("[^a-z]"), "")
        }?.first ?: aliases[key]
    }

    fun parse(sheet: List<List<String>>, lookups: ImportLookups, today: LocalDate): ImportPreview {
        if (sheet.isEmpty()) return ImportPreview(emptyList(), "File kosong")
        val header = sheet.first().map(::normalizeHeader)
        val index = ImportColumn.entries.associateWith { col -> header.indexOf(normalizeHeader(col.header)) }
        val missing = index.filter { (col, i) -> col.required && i < 0 }.keys
        if (missing.isNotEmpty()) {
            return ImportPreview(emptyList(), "Kolom wajib tidak ditemukan: ${missing.joinToString { it.header }}. Gunakan template dari aplikasi.")
        }

        val codesInFile = mutableSetOf<String>()
        val rows = sheet.drop(1).mapIndexedNotNull { i, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            val rowNumber = i + 2
            fun cell(c: ImportColumn): String = index[c]?.takeIf { it >= 0 }?.let { cells.getOrNull(it) }?.trim().orEmpty()
            val errors = mutableListOf<String>()

            val code = AssetValidator.normalizeCode(cell(ImportColumn.CODE))
            if (code.isNotEmpty()) {
                if (code in lookups.existingCodes) errors += "Kode $code sudah ada di aplikasi"
                if (!codesInFile.add(code)) errors += "Kode $code ganda di file"
            }
            val categoryId = cell(ImportColumn.CATEGORY).lowercase().takeIf { it.isNotEmpty() }?.let { lookups.categories[it] }
            if (categoryId == null) errors += if (cell(ImportColumn.CATEGORY).isEmpty()) "Kategori kosong" else "Kategori \"${cell(ImportColumn.CATEGORY)}\" tidak dikenal"
            val locationId = cell(ImportColumn.LOCATION).lowercase().takeIf { it.isNotEmpty() }?.let { lookups.locations[it] }
            if (locationId == null) errors += if (cell(ImportColumn.LOCATION).isEmpty()) "Lokasi kosong" else "Lokasi \"${cell(ImportColumn.LOCATION)}\" tidak dikenal"
            val deptRaw = cell(ImportColumn.DEPARTMENT)
            val departmentId = deptRaw.lowercase().takeIf { it.isNotEmpty() }?.let { lookups.departments[it] }
            if (deptRaw.isNotEmpty() && departmentId == null) errors += "Departemen \"$deptRaw\" tidak dikenal"
            val divRaw = cell(ImportColumn.DIVISION)
            val chosenDivision = divRaw.lowercase().takeIf { it.isNotEmpty() }?.let { lookups.divisions[it] }
            if (divRaw.isNotEmpty() && chosenDivision == null) errors += "Divisi \"$divRaw\" tidak dikenal"
            val deptDivision = departmentId?.let { lookups.departmentDivision[it] }
            if (chosenDivision != null && deptDivision != null && chosenDivision != deptDivision) {
                errors += "Departemen \"$deptRaw\" bukan bagian dari divisi \"$divRaw\""
            }
            val divisionId = deptDivision ?: chosenDivision

            val qr = cell(ImportColumn.QR)
            if (qr.isNotEmpty() && !qr.equals(code, ignoreCase = true)) {
                if (qr.uppercase() in lookups.existingCodes) errors += "Kode QR $qr sudah dipakai aset lain"
                if (!codesInFile.add(qr.uppercase())) errors += "Kode QR $qr ganda di file"
            }
            val memoRaw = cell(ImportColumn.MEMO_DATE)
            val memoDate = parseDate(memoRaw)
            if (memoRaw.isNotEmpty() && memoDate == null) errors += "Tanggal memo \"$memoRaw\" tidak valid (gunakan dd/mm/yyyy)"

            val acqRaw = cell(ImportColumn.ACQ_DATE)
            val acq = parseDate(acqRaw)
            if (acqRaw.isNotEmpty() && acq == null) errors += "Tgl perolehan \"$acqRaw\" tidak valid (gunakan dd/mm/yyyy)"
            val warrantyRaw = cell(ImportColumn.WARRANTY)
            val warranty = parseDate(warrantyRaw)
            if (warrantyRaw.isNotEmpty() && warranty == null) errors += "Garansi \"$warrantyRaw\" tidak valid (gunakan dd/mm/yyyy)"
            val costRaw = cell(ImportColumn.COST)
            val cost = parseMoney(costRaw)
            if (costRaw.isNotEmpty() && cost == null) errors += "Harga \"$costRaw\" tidak valid"

            val conditionRaw = cell(ImportColumn.CONDITION)
            val condition = matchEnum(conditionRaw, AssetCondition.entries.map { it.name to it.label }, conditionAliases)?.let(AssetCondition::valueOf)
            if (condition == null) errors += if (conditionRaw.isEmpty()) "Kondisi kosong" else "Kondisi \"$conditionRaw\" tidak dikenal (Baik/Rusak)"
            val statusRaw = cell(ImportColumn.STATUS)
            val status = matchEnum(statusRaw, AssetStatus.entries.map { it.name to it.label }, statusAliases)?.let(AssetStatus::valueOf)
            if (status == null) errors += if (statusRaw.isEmpty()) "Status kosong" else "Status \"$statusRaw\" tidak dikenal (Digunakan/Tersedia/Dalam Perbaikan/Propose To Write Off/Write Off)"

            val input = AssetInput(
                code = code, name = cell(ImportColumn.NAME), categoryId = categoryId,
                serialNumber = cell(ImportColumn.SERIAL), acquisitionDate = acq, acquisitionCost = cost, locationId = locationId,
                departmentId = departmentId, condition = condition ?: AssetCondition.BAIK,
                status = status ?: AssetStatus.DIGUNAKAN, warrantyUntil = warranty, notes = cell(ImportColumn.NOTES),
                qrCode = qr, userLocationDetail = cell(ImportColumn.USER_LOCATION), divisionId = divisionId,
                prNumber = cell(ImportColumn.PR), poNumber = cell(ImportColumn.PO), grNumber = cell(ImportColumn.GR),
                requester = cell(ImportColumn.REQUESTER), memoNumber = cell(ImportColumn.MEMO_NO), memoDate = memoDate,
                followUp = cell(ImportColumn.FOLLOW_UP), handoverDoc = cell(ImportColumn.HANDOVER),
                accessories = cell(ImportColumn.ACCESSORIES),
            )
            // Pesan validasi umum (wajib isi, tanggal masa depan, dll.) yang belum tercakup di atas.
            AssetValidator.validate(input, today).forEach { (field, msg) ->
                val alreadyReported = when (field) {
                    AssetValidator.FIELD_CATEGORY -> categoryId == null
                    AssetValidator.FIELD_LOCATION -> locationId == null
                    AssetValidator.FIELD_ACQ_DATE -> acqRaw.isNotEmpty() && acq == null
                    AssetValidator.FIELD_COST -> costRaw.isNotEmpty() && cost == null
                    AssetValidator.FIELD_WARRANTY -> warrantyRaw.isNotEmpty() && warranty == null
                    else -> false
                }
                if (!alreadyReported) errors += msg
            }
            ImportRow(rowNumber, if (errors.isEmpty()) input else null, errors)
        }
        return ImportPreview(rows)
    }
}
