package id.perusahaan.asetga.domain.excel

import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.validation.OperationValidator

/** Template import barang habis pakai. Urutan kolom template; kolom dicocokkan lewat judul, bukan urutan. */
object StockImportTemplate {
    val headers = listOf("Kode Barang*", "Barcode", "Nama Barang*", "Satuan*", "Lokasi Simpan", "Stok Awal", "Min. Stok", "Catatan")

    data class Lookups(
        /** nama satuan (huruf kecil) → id */
        val units: Map<String, String>,
        /** nama lokasi (huruf kecil) → id */
        val locations: Map<String, String>,
        /** Kode barang dan barcode yang sudah ada (huruf besar). */
        val existingCodes: Set<String>,
        val existingBarcodes: Set<String>,
    )

    data class Row(val rowNumber: Int, val input: ItemInput?, val errors: List<String>) {
        val isValid: Boolean get() = input != null && errors.isEmpty()
    }

    data class Preview(val rows: List<Row>, val headerError: String? = null) {
        val valid: List<Row> get() = rows.filter { it.isValid }
        val invalid: List<Row> get() = rows.filterNot { it.isValid }
    }

    private fun key(h: String) = AssetImportParser.normalizeHeader(h)

    fun parse(sheet: List<List<String>>, lookups: Lookups): Preview {
        if (sheet.isEmpty()) return Preview(emptyList(), "File kosong")
        val header = sheet.first().map(::key)
        fun col(vararg names: String): Int = header.indexOfFirst { it in names }
        val code = col("kodebarang", "kode")
        val barcode = col("barcode")
        val name = col("namabarang", "nama")
        val unit = col("satuan")
        val location = col("lokasisimpan", "lokasi")
        val initial = col("stokawal")
        val min = col("minstok", "minimumstok")
        val notes = col("catatan")
        val missing = buildList {
            if (code < 0) add("Kode Barang")
            if (name < 0) add("Nama Barang")
            if (unit < 0) add("Satuan")
        }
        if (missing.isNotEmpty()) return Preview(emptyList(), "Kolom wajib tidak ditemukan: ${missing.joinToString()}. Gunakan template dari aplikasi.")

        val seenCodes = mutableSetOf<String>()
        val seenBarcodes = mutableSetOf<String>()
        val rows = sheet.drop(1).mapIndexedNotNull { i, cells ->
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null
            fun cell(idx: Int) = if (idx < 0) "" else cells.getOrNull(idx)?.trim().orEmpty()
            val errors = mutableListOf<String>()

            val c = cell(code).uppercase()
            if (c.isNotEmpty()) {
                if (c in lookups.existingCodes) errors += "Kode $c sudah ada di aplikasi"
                if (!seenCodes.add(c)) errors += "Kode $c ganda di file"
            }
            val b = cell(barcode)
            if (b.isNotEmpty()) {
                if (b.uppercase() in lookups.existingBarcodes) errors += "Barcode $b sudah dipakai barang lain"
                if (!seenBarcodes.add(b.uppercase())) errors += "Barcode $b ganda di file"
            }
            val unitRaw = cell(unit)
            val unitId = unitRaw.lowercase().takeIf { it.isNotEmpty() }?.let { lookups.units[it] }
            if (unitId == null) errors += if (unitRaw.isEmpty()) "Satuan kosong" else "Satuan \"$unitRaw\" tidak dikenal"
            val locRaw = cell(location)
            val locId = locRaw.lowercase().takeIf { it.isNotEmpty() }?.let { lookups.locations[it] }
            if (locRaw.isNotEmpty() && locId == null) errors += "Lokasi \"$locRaw\" tidak dikenal"

            val input = ItemInput(
                code = c, barcode = b, name = cell(name), unitId = unitId, storageLocationId = locId,
                initialStock = cell(initial).ifEmpty { "0" }, minStock = cell(min).ifEmpty { "0" }, notes = cell(notes),
            )
            // Pesan umum (kode/nama wajib, stok tidak valid) yang belum tercakup di atas.
            OperationValidator.validateItem(input).forEach { (field, msg) ->
                if (field != OperationValidator.FIELD_UNIT) errors += msg
            }
            Row(i + 2, if (errors.isEmpty()) input else null, errors)
        }
        return Preview(rows)
    }
}
