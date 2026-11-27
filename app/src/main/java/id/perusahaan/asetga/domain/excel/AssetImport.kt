package id.perusahaan.asetga.domain.excel

import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.Accessories
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
    STATUS("Status* (pilih dari master Status)", true),
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
    ACCESSORIES("Kelengkapan (pisahkan dengan koma)"),
    VENDOR("Vendor");

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
    /** Master Status: nama ternormalisasi ([AssetImportParser.statusKey]) → id. Kosong = pakai status bawaan. */
    val statuses: Map<String, String> = emptyMap(),
    /** nama vendor (huruf kecil) → id. */
    val vendors: Map<String, String> = emptyMap(),
    /** Kode aset & kode QR yang sudah ada di database (huruf besar). */
    val existingCodes: Set<String>,
)

/** NEW: kode belum ada, dibuat sebagai aset baru. UPDATE: kode sudah ada, aset diperbarui. */
enum class ImportAction { NEW, UPDATE }

data class ImportRow(
    /** Nomor baris di Excel (header = baris 1). */
    val rowNumber: Int,
    val input: AssetInput?,
    val errors: List<String>,
    /** Baris UPDATE yang valid tetapi seluruh isinya sama dengan data yang sudah ada. */
    val unchanged: Boolean = false,
    val action: ImportAction = ImportAction.NEW,
) {
    val isValid: Boolean get() = input != null && errors.isEmpty()
}

data class ImportPreview(val rows: List<ImportRow>, val headerError: String? = null) {
    val valid: List<ImportRow> get() = rows.filter { it.isValid }
    val invalid: List<ImportRow> get() = rows.filterNot { it.isValid }
    /** Baris valid yang benar-benar akan disimpan (mode UPDATE melewati baris tanpa perubahan). */
    val toSave: List<ImportRow> get() = valid.filterNot { it.unchanged }
    val unchanged: List<ImportRow> get() = valid.filter { it.unchanged }
    val created: List<ImportRow> get() = toSave.filter { it.action == ImportAction.NEW }
    val updated: List<ImportRow> get() = toSave.filter { it.action == ImportAction.UPDATE }
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

    /** Kunci pencocokan nama status: huruf kecil, tanpa spasi & tanda baca. */
    fun statusKey(name: String): String = name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun matchEnum(raw: String, entries: List<Pair<String, String>>, aliases: Map<String, String> = emptyMap()): String? {
        val key = raw.trim().lowercase().replace(Regex("[^a-z]"), "")
        if (key.isEmpty()) return null
        return entries.firstOrNull { (name, label) ->
            key == name.lowercase().replace("_", "") || key == label.lowercase().replace(Regex("[^a-z]"), "")
        }?.first ?: aliases[key]
    }

    /**
     * Mode perbarui: baris dicocokkan ke aset yang sudah ada lewat Kode Aset ([existing]: kode huruf besar → data saat ini).
     * Sel kosong berarti "biarkan nilai lama"; sel terisi menimpa. Kode Aset adalah kunci dan tidak bisa diubah.
     */
    fun parseUpdate(sheet: List<List<String>>, lookups: ImportLookups, existing: Map<String, AssetInput>, today: LocalDate): ImportPreview {
        if (sheet.isEmpty()) return ImportPreview(emptyList(), "File kosong")
        val header = sheet.first().map(::normalizeHeader)
        val index = ImportColumn.entries.associateWith { col -> header.indexOf(normalizeHeader(col.header)) }
        if (index.getValue(ImportColumn.CODE) < 0) {
            return ImportPreview(emptyList(), "Kolom ${ImportColumn.CODE.header} tidak ditemukan. Gunakan template atau hasil export dari aplikasi.")
        }
        if (index.filterKeys { it != ImportColumn.CODE }.values.none { it >= 0 }) {
            return ImportPreview(emptyList(), "Tidak ada kolom yang akan diperbarui selain Kode Aset")
        }

        val codesInFile = mutableSetOf<String>()
        val qrInFile = mutableSetOf<String>()
        val rows = sheet.drop(1).mapIndexedNotNull { i, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            val rowNumber = i + 2
            fun cell(c: ImportColumn): String = index[c]?.takeIf { it >= 0 }?.let { cells.getOrNull(it) }?.trim().orEmpty()
            val errors = mutableListOf<String>()

            val code = AssetValidator.normalizeCode(cell(ImportColumn.CODE))
            if (code.isEmpty()) return@mapIndexedNotNull ImportRow(rowNumber, null, listOf("Kode Aset kosong"))
            if (!codesInFile.add(code)) return@mapIndexedNotNull ImportRow(rowNumber, null, listOf("Kode $code ganda di file"))
            val base = existing[code]
                ?: return@mapIndexedNotNull ImportRow(rowNumber, null, listOf("Kode $code tidak ditemukan di aplikasi (mode perbarui hanya untuk aset yang sudah ada)"))

            /** Sel kosong = nilai lama. */
            fun keep(c: ImportColumn, current: String): String = cell(c).ifEmpty { current }

            var categoryId = base.categoryId
            cell(ImportColumn.CATEGORY).takeIf { it.isNotEmpty() }?.let { raw ->
                lookups.categories[raw.lowercase()]?.let { categoryId = it } ?: run { errors += "Kategori \"$raw\" tidak dikenal" }
            }
            var locationId = base.locationId
            cell(ImportColumn.LOCATION).takeIf { it.isNotEmpty() }?.let { raw ->
                lookups.locations[raw.lowercase()]?.let { locationId = it } ?: run { errors += "Lokasi \"$raw\" tidak dikenal" }
            }

            // Departemen menentukan divisi. Hanya divisi: departemen lama dikosongkan bila bukan bagian divisi itu.
            var departmentId = base.departmentId
            var divisionId = base.divisionId
            val deptRaw = cell(ImportColumn.DEPARTMENT)
            val divRaw = cell(ImportColumn.DIVISION)
            val chosenDivision = divRaw.takeIf { it.isNotEmpty() }?.let { lookups.divisions[it.lowercase()] }
            if (divRaw.isNotEmpty() && chosenDivision == null) errors += "Divisi \"$divRaw\" tidak dikenal"
            if (deptRaw.isNotEmpty()) {
                val d = lookups.departments[deptRaw.lowercase()]
                if (d == null) {
                    errors += "Departemen \"$deptRaw\" tidak dikenal"
                } else {
                    val dDivision = lookups.departmentDivision[d]
                    if (chosenDivision != null && dDivision != null && chosenDivision != dDivision) {
                        errors += "Departemen \"$deptRaw\" bukan bagian dari divisi \"$divRaw\""
                    }
                    departmentId = d
                    divisionId = dDivision ?: chosenDivision ?: divisionId
                }
            } else if (chosenDivision != null) {
                divisionId = chosenDivision
                if (departmentId != null && lookups.departmentDivision[departmentId] != chosenDivision) departmentId = null
            }

            var qr = base.qrCode
            val qrRaw = cell(ImportColumn.QR)
            if (qrRaw.isNotEmpty() && !qrRaw.equals(base.qrCode, ignoreCase = true)) {
                val own = setOf(base.code.uppercase(), base.qrCode.uppercase())
                val key = qrRaw.uppercase()
                if (!qrRaw.equals(code, ignoreCase = true)) {
                    if (key in lookups.existingCodes && key !in own) errors += "Kode QR $qrRaw sudah dipakai aset lain"
                    if (!qrInFile.add(key)) errors += "Kode QR $qrRaw ganda di file"
                }
                qr = qrRaw
            }

            fun date(c: ImportColumn, label: String, current: java.time.LocalDate?): java.time.LocalDate? {
                val raw = cell(c)
                if (raw.isEmpty()) return current
                return parseDate(raw) ?: current.also { errors += "$label \"$raw\" tidak valid (gunakan dd/mm/yyyy)" }
            }
            val acq = date(ImportColumn.ACQ_DATE, "Tgl perolehan", base.acquisitionDate)
            val warranty = date(ImportColumn.WARRANTY, "Garansi", base.warrantyUntil)
            val memoDate = date(ImportColumn.MEMO_DATE, "Tanggal memo", base.memoDate)
            var cost = base.acquisitionCost
            cell(ImportColumn.COST).takeIf { it.isNotEmpty() }?.let { raw ->
                parseMoney(raw)?.let { cost = it } ?: run { errors += "Harga \"$raw\" tidak valid" }
            }

            var condition = base.condition
            cell(ImportColumn.CONDITION).takeIf { it.isNotEmpty() }?.let { raw ->
                matchEnum(raw, AssetCondition.entries.map { it.name to it.label }, conditionAliases)?.let { condition = AssetCondition.valueOf(it) }
                    ?: run { errors += "Kondisi \"$raw\" tidak dikenal (Baik/Rusak)" }
            }
            var status = base.status
            cell(ImportColumn.STATUS).takeIf { it.isNotEmpty() }?.let { raw ->
                val builtIn = matchEnum(raw, AssetStatus.DEFAULT_LABELS.toList(), statusAliases)
                val id = lookups.statuses[statusKey(raw)] ?: builtIn?.takeIf { lookups.statuses.isEmpty() || it in lookups.statuses.values }
                if (id != null) status = AssetStatus(id) else errors += "Status \"$raw\" tidak ada di master Status"
            }
            var vendorId = base.vendorId
            cell(ImportColumn.VENDOR).takeIf { it.isNotEmpty() }?.let { raw ->
                lookups.vendors[raw.lowercase()]?.let { vendorId = it } ?: run { errors += "Vendor \"$raw\" tidak ada di master Vendor" }
            }

            val merged = base.copy(
                name = keep(ImportColumn.NAME, base.name), categoryId = categoryId, serialNumber = keep(ImportColumn.SERIAL, base.serialNumber),
                acquisitionDate = acq, acquisitionCost = cost, locationId = locationId, departmentId = departmentId, divisionId = divisionId,
                condition = condition, status = status, warrantyUntil = warranty, notes = keep(ImportColumn.NOTES, base.notes), qrCode = qr,
                userLocationDetail = keep(ImportColumn.USER_LOCATION, base.userLocationDetail),
                prNumber = keep(ImportColumn.PR, base.prNumber), poNumber = keep(ImportColumn.PO, base.poNumber),
                grNumber = keep(ImportColumn.GR, base.grNumber), requester = keep(ImportColumn.REQUESTER, base.requester),
                memoNumber = keep(ImportColumn.MEMO_NO, base.memoNumber), memoDate = memoDate,
                followUp = keep(ImportColumn.FOLLOW_UP, base.followUp), handoverDoc = keep(ImportColumn.HANDOVER, base.handoverDoc),
                accessories = cell(ImportColumn.ACCESSORIES).takeIf { it.isNotEmpty() }?.let { Accessories.normalize(it).orEmpty() } ?: base.accessories,
                vendorId = vendorId,
            )
            if (errors.isEmpty()) AssetValidator.validate(merged, today).values.forEach { errors += it }
            ImportRow(rowNumber, if (errors.isEmpty()) merged else null, errors, unchanged = errors.isEmpty() && merged == base)
        }
        return ImportPreview(rows)
    }

    /**
     * Satu alur untuk tambah dan perbarui: baris yang Kode Aset-nya sudah ada diperbarui ([parseUpdate]),
     * sisanya dibuat sebagai aset baru ([parse]). Nomor baris tetap mengacu ke file asli.
     */
    fun parseAuto(sheet: List<List<String>>, lookups: ImportLookups, existing: Map<String, AssetInput>, today: LocalDate): ImportPreview {
        if (sheet.isEmpty()) return ImportPreview(emptyList(), "File kosong")
        val headerRow = sheet.first()
        val index = headerRow.map(::normalizeHeader).let { h -> ImportColumn.entries.associateWith { c -> h.indexOf(normalizeHeader(c.header)) } }
        val codeIdx = index.getValue(ImportColumn.CODE)
        if (codeIdx < 0) return ImportPreview(emptyList(), "Kolom ${ImportColumn.CODE.header} tidak ditemukan. Gunakan template atau hasil export dari aplikasi.")

        val dataRows = sheet.drop(1).mapIndexed { i, cells -> (i + 2) to cells }.filter { (_, cells) -> !cells.all { it.isBlank() } }
        val (updRows, addRows) = dataRows.partition { (_, cells) -> AssetValidator.normalizeCode(cells.getOrNull(codeIdx).orEmpty()) in existing }

        fun errorRows(src: List<Pair<Int, List<String>>>, message: String, action: ImportAction) =
            src.map { (n, _) -> ImportRow(n, null, listOf(message), action = action) }

        val newRows = if (addRows.isEmpty()) {
            emptyList()
        } else {
            val missing = index.filter { (c, i) -> c.required && i < 0 }.keys
            if (missing.isNotEmpty()) {
                errorRows(addRows, "Aset baru memerlukan kolom ${missing.joinToString { it.header }} (tidak ada di file)", ImportAction.NEW)
            } else {
                val p = parse(listOf(headerRow) + addRows.map { it.second }, lookups, today)
                p.rows.map { it.copy(rowNumber = addRows[it.rowNumber - 2].first, action = ImportAction.NEW) }
            }
        }
        val changedRows = if (updRows.isEmpty()) {
            emptyList()
        } else {
            val p = parseUpdate(listOf(headerRow) + updRows.map { it.second }, lookups, existing, today)
            if (p.headerError != null) {
                errorRows(updRows, p.headerError, ImportAction.UPDATE)
            } else {
                p.rows.map { it.copy(rowNumber = updRows[it.rowNumber - 2].first, action = ImportAction.UPDATE) }
            }
        }
        return ImportPreview((newRows + changedRows).sortedBy { it.rowNumber })
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
            val builtIn = matchEnum(statusRaw, AssetStatus.DEFAULT_LABELS.toList(), statusAliases)
            val statusId = lookups.statuses[statusKey(statusRaw)]
                ?: builtIn?.takeIf { lookups.statuses.isEmpty() || it in lookups.statuses.values }
            val status = statusId?.let(::AssetStatus)
            if (status == null) errors += if (statusRaw.isEmpty()) "Status kosong" else "Status \"$statusRaw\" tidak ada di master Status"
            val vendorRaw = cell(ImportColumn.VENDOR)
            val vendorId = vendorRaw.lowercase().takeIf { it.isNotEmpty() }?.let { lookups.vendors[it] }
            if (vendorRaw.isNotEmpty() && vendorId == null) errors += "Vendor \"$vendorRaw\" tidak ada di master Vendor"

            val input = AssetInput(
                code = code, name = cell(ImportColumn.NAME), categoryId = categoryId,
                serialNumber = cell(ImportColumn.SERIAL), acquisitionDate = acq, acquisitionCost = cost, locationId = locationId,
                departmentId = departmentId, condition = condition ?: AssetCondition.BAIK,
                status = status ?: AssetStatus.DIGUNAKAN, warrantyUntil = warranty, notes = cell(ImportColumn.NOTES),
                qrCode = qr, userLocationDetail = cell(ImportColumn.USER_LOCATION), divisionId = divisionId,
                prNumber = cell(ImportColumn.PR), poNumber = cell(ImportColumn.PO), grNumber = cell(ImportColumn.GR),
                requester = cell(ImportColumn.REQUESTER), memoNumber = cell(ImportColumn.MEMO_NO), memoDate = memoDate,
                followUp = cell(ImportColumn.FOLLOW_UP), handoverDoc = cell(ImportColumn.HANDOVER),
                accessories = cell(ImportColumn.ACCESSORIES), vendorId = vendorId,
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
