package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.excel.AssetImportParser
import id.perusahaan.asetga.domain.excel.CsvWriter
import id.perusahaan.asetga.domain.excel.ImportColumn
import id.perusahaan.asetga.domain.excel.ImportLookups
import id.perusahaan.asetga.domain.excel.Sheet
import id.perusahaan.asetga.domain.excel.XlsxReader
import id.perusahaan.asetga.domain.excel.XlsxWriter
import id.perusahaan.asetga.domain.excel.num
import id.perusahaan.asetga.domain.excel.text
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class ExcelTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val lookups = ImportLookups(
        categories = mapOf("elektronik & it" to "cat-it", "it" to "cat-it"),
        locations = mapOf("kantor pusat" to "loc-1"),
        departments = mapOf("keuangan" to "dep-1"),
        existingCodes = setOf("IT-2024-001"),
    )
    private val header = ImportColumn.entries.map { it.templateHeader }

    private fun row(vararg pairs: Pair<ImportColumn, String>): List<String> {
        val m = pairs.toMap()
        return ImportColumn.entries.map { m[it].orEmpty() }
    }

    @Test fun `tulis lalu baca xlsx menghasilkan data yang sama`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(
            listOf(
                Sheet("Aset", listOf("Kode", "Nama", "Harga"), listOf(listOf(text("IT-1"), text("Laptop & <Mouse>"), num(12_500_000)), listOf(text("IT-2"), text(""), num(1.5)))),
                Sheet("Petunjuk", listOf("Info"), listOf(listOf(text("lembar kedua")))),
            ),
            out,
        )
        val rows = XlsxReader.readFirstSheet(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf("Kode", "Nama", "Harga"), rows[0])
        assertEquals(listOf("IT-1", "Laptop & <Mouse>", "12500000"), rows[1])
        assertEquals(listOf("IT-2", "", "1.5"), rows[2])
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals(27, XlsxReader.columnIndex("AB12"))
    }

    @Test fun `parse tanggal dan harga format Indonesia dan Excel`() {
        assertEquals(LocalDate.of(2024, 3, 5), AssetImportParser.parseDate("05/03/2024"))
        assertEquals(LocalDate.of(2024, 3, 5), AssetImportParser.parseDate("5-3-2024"))
        assertEquals(LocalDate.of(2024, 3, 5), AssetImportParser.parseDate("2024-03-05"))
        assertEquals(LocalDate.of(2024, 1, 1), AssetImportParser.parseDate("45292")) // nomor seri Excel
        assertNull(AssetImportParser.parseDate("31/02/2024"))
        assertEquals(1_250_000L, AssetImportParser.parseMoney("Rp 1.250.000"))
        assertEquals(1_250_000L, AssetImportParser.parseMoney("1.250.000,00"))
        assertEquals(12_500_000L, AssetImportParser.parseMoney("1.25E7"))
        assertEquals(1_250_001L, AssetImportParser.parseMoney("1250000.6"))
        assertNull(AssetImportParser.parseMoney("abc"))
    }

    @Test fun `validasi per baris memisahkan baris benar dan salah`() {
        val sheet = listOf(
            header,
            row(ImportColumn.NAME to "Laptop", ImportColumn.CATEGORY to "Elektronik & IT", ImportColumn.ACQ_DATE to "10/01/2024",
                ImportColumn.COST to "15.000.000", ImportColumn.LOCATION to "Kantor Pusat", ImportColumn.CONDITION to "baik", ImportColumn.STATUS to "Aktif",
                ImportColumn.DEPARTMENT to "Keuangan"),
            row(), // baris kosong diabaikan
            row(ImportColumn.CODE to "IT-2024-001", ImportColumn.NAME to "", ImportColumn.CATEGORY to "Mebel", ImportColumn.ACQ_DATE to "2099-01-01",
                ImportColumn.COST to "x", ImportColumn.LOCATION to "Gudang", ImportColumn.CONDITION to "Hancur", ImportColumn.STATUS to "Dihapus/Dijual"),
            row(ImportColumn.NAME to "Kursi", ImportColumn.CATEGORY to "IT", ImportColumn.ACQ_DATE to "45292", ImportColumn.COST to "1500000",
                ImportColumn.LOCATION to "kantor pusat", ImportColumn.CONDITION to "Rusak Ringan", ImportColumn.STATUS to "DALAM_PERBAIKAN"),
        )
        val preview = AssetImportParser.parse(sheet, lookups, today)
        assertNull(preview.headerError)
        assertEquals(3, preview.rows.size)
        assertEquals(2, preview.valid.size)

        val ok = preview.valid.first().input!!
        assertEquals("cat-it", ok.categoryId)
        assertEquals("dep-1", ok.departmentId)
        assertEquals(15_000_000L, ok.acquisitionCost)

        val bad = preview.invalid.single()
        assertEquals(4, bad.rowNumber)
        val joined = bad.errors.joinToString("|")
        listOf("sudah ada", "Kategori \"Mebel\"", "Lokasi \"Gudang\"", "Harga \"x\"", "Kondisi \"Hancur\"", "Nama aset wajib", "masa depan").forEach {
            assertTrue("pesan '$it' tidak ditemukan di: $joined", joined.contains(it))
        }

        val second = preview.valid[1].input!!
        assertEquals(AssetCondition.RUSAK_RINGAN, second.condition)
        assertEquals(AssetStatus.DALAM_PERBAIKAN, second.status)
        assertEquals(LocalDate.of(2024, 1, 1), second.acquisitionDate)
    }

    @Test fun `header tidak sesuai ditolak dan kode ganda dalam file`() {
        assertNotNull(AssetImportParser.parse(listOf(listOf("A", "B")), lookups, today).headerError)
        val base = arrayOf(ImportColumn.CODE to "X-1", ImportColumn.NAME to "A", ImportColumn.CATEGORY to "IT", ImportColumn.ACQ_DATE to "01/01/2024",
            ImportColumn.COST to "1", ImportColumn.LOCATION to "Kantor Pusat", ImportColumn.CONDITION to "Baik", ImportColumn.STATUS to "Aktif")
        val p = AssetImportParser.parse(listOf(header, row(*base), row(*base)), lookups, today)
        assertEquals(1, p.valid.size)
        assertTrue(p.invalid.single().errors.single().contains("ganda"))
    }

    @Test fun `csv memakai titik koma, BOM, dan mencegah formula`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(listOf("a", "b"), listOf(listOf("x;y", "=SUM(A1)"), listOf("\"q\"", "-5")), out)
        val s = out.toString(Charsets.UTF_8.name())
        assertTrue(s.startsWith("﻿"))
        assertTrue(s.contains("\"x;y\";'=SUM(A1)"))
        assertTrue(s.contains("\"\"\"q\"\"\";-5"))
    }
}
