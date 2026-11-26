package id.perusahaan.asetga.domain.excel

/** Upload daftar aset untuk sesi opname: satu kolom kode (kode aset atau kode QR). Kolom lain diabaikan. */
object OpnameImportTemplate {
    const val COL_CODE = "Kode Aset / Kode QR*"
    val headers = listOf(COL_CODE)

    data class ParsedRow(val rowNumber: Int, val code: String, val errors: List<String>)

    /**
     * Kolom kode dikenali dari judul yang mengandung "kode" (mis. "Kode Aset", "Kode QR"); bila tidak ada, kolom pertama.
     * Dengan begitu file export aset pun bisa langsung diunggah.
     */
    fun parse(sheet: List<List<String>>): Pair<List<ParsedRow>, String?> {
        if (sheet.isEmpty()) return emptyList<ParsedRow>() to "File kosong"
        val header = sheet.first().map { AssetImportParser.normalizeHeader(it) }
        val codeIdx = header.indexOfFirst { it.contains("kode") }.takeIf { it >= 0 } ?: 0
        val seen = mutableSetOf<String>()
        val rows = sheet.drop(1).mapIndexedNotNull { i, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            val code = cells.getOrNull(codeIdx)?.trim().orEmpty()
            val errors = buildList {
                if (code.isEmpty()) add("Kode kosong")
                else if (!seen.add(code.uppercase())) add("Kode $code ganda di file")
            }
            ParsedRow(i + 2, code, errors)
        }
        if (rows.isEmpty()) return rows to "Tidak ada baris data"
        return rows to null
    }
}
