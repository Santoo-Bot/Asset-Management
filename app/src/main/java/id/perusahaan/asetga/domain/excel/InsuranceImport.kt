package id.perusahaan.asetga.domain.excel

/** Template upload aset polis asuransi. */
object InsuranceImportTemplate {
    const val COL_CODE = "Kode Aset / Kode QR*"
    const val COL_SUM = "Nilai Pertanggungan"
    val headers = listOf(COL_CODE, COL_SUM)

    data class ParsedRow(val rowNumber: Int, val code: String, val sumInsured: Long?, val errors: List<String>)

    /**
     * Membaca baris file. Kolom kode dikenali dari judul yang mengandung "kode" (mis. "Kode Aset", "Kode QR",
     * "Kode Aset / Kode QR"); bila tidak ada judul yang cocok, kolom pertama dipakai.
     */
    fun parse(sheet: List<List<String>>): Pair<List<ParsedRow>, String?> {
        if (sheet.isEmpty()) return emptyList<ParsedRow>() to "File kosong"
        val header = sheet.first().map { AssetImportParser.normalizeHeader(it) }
        val codeIdx = header.indexOfFirst { it.contains("kode") }.takeIf { it >= 0 } ?: 0
        val sumIdx = header.indexOfFirst { it.contains("pertanggungan") || it.contains("nilai") }
        val seen = mutableSetOf<String>()
        val rows = sheet.drop(1).mapIndexedNotNull { i, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            val code = cells.getOrNull(codeIdx)?.trim().orEmpty()
            val sumRaw = if (sumIdx >= 0) cells.getOrNull(sumIdx)?.trim().orEmpty() else ""
            val sum = AssetImportParser.parseMoney(sumRaw)
            val errors = buildList {
                if (code.isEmpty()) add("Kode kosong")
                else if (!seen.add(code.uppercase())) add("Kode $code ganda di file")
                if (sumRaw.isNotEmpty() && (sum == null || sum < 0)) add("Nilai pertanggungan \"$sumRaw\" tidak valid")
            }
            ParsedRow(i + 2, code, sum, errors)
        }
        return rows to null
    }
}
