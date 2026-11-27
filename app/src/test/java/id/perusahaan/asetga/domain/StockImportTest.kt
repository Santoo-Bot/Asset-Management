package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.excel.StockImportTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockImportTest {
    private val lookups = StockImportTemplate.Lookups(
        units = mapOf("rim" to "u-rim", "pcs" to "u-pcs"),
        locations = mapOf("gudang a" to "l-a"),
        existingCodes = setOf("ATK-001"),
        existingBarcodes = setOf("8990000000001"),
    )
    private val header = StockImportTemplate.headers

    @Test fun `baris valid menjadi barang baru dan baris salah dilaporkan dengan nomor baris`() {
        val p = StockImportTemplate.parse(
            listOf(
                header,
                listOf("atk-002", "8991111111111", "Kertas A4", "Rim", "Gudang A", "20", "5", "catatan"),
                listOf("", "", "", "", "", "", "", ""), // kosong dilewati
                listOf("ATK-001", "8990000000001", "Pulpen", "Boks", "Gudang Z", "x", "-1", ""),
                listOf("ATK-003", "", "Map", "pcs", "", "", "", ""), // stok kosong dianggap 0
                listOf("ATK-003", "", "Map lagi", "pcs", "", "1,5", "", ""), // kode ganda
            ),
            lookups,
        )
        assertNull(p.headerError)
        assertEquals(listOf(2, 4, 5, 6), p.rows.map { it.rowNumber })
        val first = p.valid.first().input!!
        assertEquals("ATK-002", first.code)
        assertEquals("u-rim", first.unitId)
        assertEquals("l-a", first.storageLocationId)
        assertEquals("20", first.initialStock)
        assertEquals("0", p.valid[1].input!!.initialStock)
        val bad = p.invalid.associate { it.rowNumber to it.errors.joinToString("|") }
        listOf("sudah ada", "Barcode", "Satuan \"Boks\"", "Lokasi \"Gudang Z\"", "Stok awal").forEach {
            assertTrue("'$it' tidak ada di: ${bad[4]}", bad.getValue(4).contains(it))
        }
        assertTrue(bad.getValue(6).contains("ganda"))
    }

    @Test fun `kolom wajib harus ada dan urutan kolom bebas`() {
        assertNotNull(StockImportTemplate.parse(listOf(listOf("Nama Barang*", "Satuan*"), listOf("x", "Rim")), lookups).headerError)
        val p = StockImportTemplate.parse(listOf(listOf("Satuan*", "Nama Barang*", "Kode Barang*"), listOf("Rim", "Kertas", "ATK-009")), lookups)
        assertNull(p.headerError)
        assertEquals(1, p.valid.size)
        assertEquals("ATK-009", p.valid.single().input!!.code)
    }
}
