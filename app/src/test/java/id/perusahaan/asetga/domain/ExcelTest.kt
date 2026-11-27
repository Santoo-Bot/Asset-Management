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

    private fun xlsxWithSheet(sheetXml: String): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun sheetXml(rowAttr: String, cellRef: String) =
        """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="$rowAttr">""" +
            """<c r="$cellRef" t="inlineStr"><is><t>x</t></is></c></row></sheetData></worksheet>"""

    @Test fun `xlsx dengan nomor baris atau kolom di luar batas Excel ditolak`() {
        // Sebelumnya nomor baris sebesar ini membuat pembaca membuat dua miliar daftar kosong sampai kehabisan memori.
        listOf(
            sheetXml("2000000000", "A2000000000"),
            sheetXml("1048577", "A1048577"),
            sheetXml("1", "XFE1"), // kolom ke-16.385
            sheetXml("1", "ZZZZZZZZZZZZZZ1"), // referensi sangat panjang
        ).forEach { xml ->
            val r = runCatching { XlsxReader.readFirstSheet(ByteArrayInputStream(xlsxWithSheet(xml))) }
            assertTrue(r.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun `xlsx dengan DOCTYPE atau entity ditolak`() {
        val bomb = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaa">]>""" + sheetXml("1", "A1")
        val r = runCatching { XlsxReader.readFirstSheet(ByteArrayInputStream(xlsxWithSheet(bomb))) }
        assertTrue(r.exceptionOrNull() is IllegalArgumentException)
        val lower = runCatching { XlsxReader.readFirstSheet(ByteArrayInputStream(xlsxWithSheet(bomb.replace("DOCTYPE", "doctype")))) }
        assertTrue(lower.exceptionOrNull() is IllegalArgumentException)
        // Lembar biasa tidak terpengaruh.
        assertEquals("x", XlsxReader.readFirstSheet(ByteArrayInputStream(xlsxWithSheet(sheetXml("1", "A1"))))[0][0])
    }

    @Test fun `xlsx dengan baris dan kolom terakhir yang masih sah tetap terbaca`() {
        val rows = XlsxReader.readFirstSheet(ByteArrayInputStream(xlsxWithSheet(sheetXml("5", "XFD5"))))
        assertEquals(5, rows.size)
        assertEquals(16_384, rows[4].size)
        assertEquals("x", rows[4].last())
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
            row(ImportColumn.CODE to "IT-2024-101", ImportColumn.QR to "IT-2024-101", ImportColumn.NAME to "Laptop", ImportColumn.CATEGORY to "Elektronik & IT",
                ImportColumn.ACQ_DATE to "10/01/2024", ImportColumn.COST to "15.000.000", ImportColumn.LOCATION to "Kantor Pusat",
                ImportColumn.CONDITION to "baik", ImportColumn.STATUS to "Digunakan", ImportColumn.DEPARTMENT to "Keuangan"),
            row(), // baris kosong diabaikan
            row(ImportColumn.CODE to "IT-2024-001", ImportColumn.NAME to "", ImportColumn.CATEGORY to "Mebel", ImportColumn.ACQ_DATE to "2099-01-01",
                ImportColumn.COST to "x", ImportColumn.LOCATION to "Gudang", ImportColumn.CONDITION to "Hancur", ImportColumn.STATUS to "Write Off"),
            row(ImportColumn.CODE to "IT-2024-102", ImportColumn.QR to "STIKER-LAMA-7", ImportColumn.NAME to "Kursi", ImportColumn.CATEGORY to "IT",
                ImportColumn.ACQ_DATE to "45292", ImportColumn.COST to "1500000", ImportColumn.LOCATION to "kantor pusat",
                ImportColumn.CONDITION to "Rusak Ringan", ImportColumn.STATUS to "Propose To Write Off"),
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
        assertEquals(AssetCondition.RUSAK, second.condition) // label lama "Rusak Ringan" tetap diterima
        assertEquals(AssetStatus.PROPOSE_WRITE_OFF, second.status)
        assertEquals("STIKER-LAMA-7", second.qrCode)
        assertEquals(LocalDate.of(2024, 1, 1), second.acquisitionDate)
    }

    private val current = id.perusahaan.asetga.domain.model.AssetInput(
        id = "a1", code = "IT-2024-001", name = "Laptop lama", categoryId = "cat-it", serialNumber = "SN1",
        acquisitionDate = LocalDate.of(2024, 1, 10), acquisitionCost = 10_000_000, locationId = "loc-1", departmentId = "dep-1",
        condition = AssetCondition.BAIK, status = AssetStatus.DIGUNAKAN, notes = "catatan lama", qrCode = "IT-2024-001",
        photoPath = "photos/x.jpg", accessories = "Charger",
    )
    private val existing = mapOf("IT-2024-001" to current, "IT-2024-002" to current.copy(id = "a2", code = "IT-2024-002", qrCode = "STIKER-2"))

    @Test fun `mode perbarui hanya mengubah sel terisi dan mempertahankan sisanya`() {
        val h = listOf("Kode Aset*", "Nama Aset*", "Harga Perolehan*", "Catatan", "Kondisi* (Baik,Rusak)")
        val p = AssetImportParser.parseUpdate(
            listOf(h, listOf("it-2024-001", "Laptop baru", "12.500.000", "", "Rusak")), lookups, existing, today,
        )
        assertNull(p.headerError)
        val i = p.valid.single().input!!
        assertEquals("a1", i.id)
        assertEquals("Laptop baru", i.name)
        assertEquals(12_500_000L, i.acquisitionCost)
        assertEquals(AssetCondition.RUSAK, i.condition)
        assertEquals("catatan lama", i.notes) // sel kosong: nilai lama dipertahankan
        assertEquals("SN1", i.serialNumber)
        assertEquals("photos/x.jpg", i.photoPath)
        assertEquals("Charger", i.accessories)
        assertEquals(1, p.toSave.size)
    }

    @Test fun `mode perbarui menolak kode tak dikenal, ganda, dan nilai tak valid, serta mengenali baris tanpa perubahan`() {
        val h = listOf("Kode Aset*", "Nama Aset*", "Lokasi*", "Kode QR*")
        val p = AssetImportParser.parseUpdate(
            listOf(
                h,
                listOf("IT-9999", "X", "", ""),
                listOf("IT-2024-001", "Laptop lama", "", ""), // sama dengan data sekarang
                listOf("IT-2024-001", "Lain", "", ""), // ganda
                listOf("IT-2024-002", "", "Gudang", "IT-2024-001"),
            ),
            lookups, existing, today,
        )
        assertEquals(1, p.unchanged.size)
        assertEquals(0, p.toSave.size)
        val byRow = p.invalid.associate { it.rowNumber to it.errors.joinToString("|") }
        assertTrue(byRow.getValue(2).contains("tidak ditemukan"))
        assertTrue(byRow.getValue(4).contains("ganda"))
        assertTrue(byRow.getValue(5).contains("Lokasi \"Gudang\""))
    }

    @Test fun `mode perbarui hanya wajib kolom kode`() {
        assertNotNull(AssetImportParser.parseUpdate(listOf(listOf("Nama Aset*"), listOf("x")), lookups, existing, today).headerError)
        assertNotNull(AssetImportParser.parseUpdate(listOf(listOf("Kode Aset*"), listOf("IT-2024-001")), lookups, existing, today).headerError)
    }

    @Test fun `satu alur menambah kode baru dan memperbarui kode yang sudah ada dengan nomor baris asli`() {
        val h = listOf("Kode Aset*", "Kode QR*", "Nama Aset*", "Kategori*", "Tgl Perolehan (dd/mm/yyyy)*", "Harga Perolehan*", "Lokasi*", "Kondisi* (Baik,Rusak)", "Status* (pilih dari master Status)")
        val p = AssetImportParser.parseAuto(
            listOf(
                h,
                listOf("IT-2024-001", "", "Laptop diganti", "", "", "", "", "", ""), // ada: perbarui nama saja
                listOf("IT-2024-777", "IT-2024-777", "Printer", "IT", "01/02/2024", "2000000", "Kantor Pusat", "Baik", "Aktif"), // baru
                listOf("", "", "", "", "", "", "", "", ""), // kosong dilewati
                listOf("IT-2024-888", "", "Tanpa data", "", "", "", "", "", ""), // baru tapi tidak lengkap
                listOf("IT-2024-002", "", "", "", "", "", "", "", ""), // ada, tanpa perubahan
            ),
            lookups, existing, today,
        )
        assertNull(p.headerError)
        assertEquals(listOf(2, 3, 5, 6), p.rows.map { it.rowNumber })
        assertEquals(1, p.updated.size)
        assertEquals(2, p.updated.single().rowNumber)
        assertEquals("Laptop diganti", p.updated.single().input!!.name)
        assertEquals(1, p.created.size)
        assertEquals(3, p.created.single().rowNumber)
        assertNull(p.created.single().input!!.id)
        assertEquals(1, p.unchanged.size)
        assertEquals(6, p.unchanged.single().rowNumber)
        assertEquals(5, p.invalid.single().rowNumber)
        assertNotNull(AssetImportParser.parseAuto(listOf(listOf("Nama Aset*"), listOf("x")), lookups, existing, today).headerError)
    }

    @Test fun `satu alur dengan file tanpa kolom wajib tetap memperbarui tetapi aset baru ditolak jelas`() {
        val p = AssetImportParser.parseAuto(
            listOf(listOf("Kode Aset*", "Nama Aset*"), listOf("IT-2024-001", "Baru"), listOf("IT-2024-555", "Item")),
            lookups, existing, today,
        )
        assertEquals(1, p.updated.size)
        assertTrue(p.invalid.single().errors.single().contains("Aset baru memerlukan kolom"))
    }

    @Test fun `header tidak sesuai ditolak dan kode ganda dalam file`() {
        assertNotNull(AssetImportParser.parse(listOf(listOf("A", "B")), lookups, today).headerError)
        val base = arrayOf(ImportColumn.CODE to "X-1", ImportColumn.NAME to "A", ImportColumn.CATEGORY to "IT", ImportColumn.ACQ_DATE to "01/01/2024",
            ImportColumn.COST to "1", ImportColumn.LOCATION to "Kantor Pusat", ImportColumn.CONDITION to "Baik", ImportColumn.STATUS to "Aktif", ImportColumn.QR to "X-1")
        val p = AssetImportParser.parse(listOf(header, row(*base), row(*base)), lookups, today)
        assertEquals(1, p.valid.size)
        assertTrue(p.invalid.single().errors.any { it.contains("ganda") })
    }

    @Test fun `csv memakai titik koma, BOM, dan mencegah formula`() {
        val out = ByteArrayOutputStream()
        CsvWriter.write(listOf("a", "b"), listOf(listOf("x;y", "=SUM(A1)"), listOf("\"q\"", "-5")), out)
        val s = out.toString(Charsets.UTF_8.name())
        assertTrue(s.startsWith("﻿"))
        assertTrue(s.contains("\"x;y\";'=SUM(A1)"))
        assertTrue(s.contains("\"\"\"q\"\"\";-5"))
    }

    @Test fun `status dari master Status dan vendor dari master Vendor`() {
        val withMaster = lookups.copy(
            statuses = mapOf(AssetImportParser.statusKey("Digunakan") to "DIGUNAKAN", AssetImportParser.statusKey("Dipinjam Karyawan") to "st-pinjam"),
            vendors = mapOf("pt sumber komputer" to "v-1"),
        )
        val base = arrayOf(
            ImportColumn.NAME to "Tablet", ImportColumn.CATEGORY to "IT", ImportColumn.ACQ_DATE to "10/01/2024",
            ImportColumn.COST to "1000000", ImportColumn.LOCATION to "Kantor Pusat", ImportColumn.CONDITION to "Baik",
        )
        val sheet = listOf(
            header,
            row(ImportColumn.CODE to "IT-2024-201", ImportColumn.QR to "IT-2024-201", *base, ImportColumn.STATUS to "dipinjam karyawan", ImportColumn.VENDOR to "PT Sumber Komputer"),
            row(ImportColumn.CODE to "IT-2024-202", ImportColumn.QR to "IT-2024-202", *base, ImportColumn.STATUS to "Write Off", ImportColumn.VENDOR to "PT Lain"),
        )
        val preview = AssetImportParser.parse(sheet, withMaster, today)
        val ok = preview.valid.single().input!!
        assertEquals("st-pinjam", ok.status.id)
        assertEquals("v-1", ok.vendorId)
        val errors = preview.invalid.single().errors.joinToString("|")
        assertTrue(errors, errors.contains("Status \"Write Off\" tidak ada di master Status")) // Write Off tidak ada di master contoh
        assertTrue(errors, errors.contains("Vendor \"PT Lain\""))
    }

    @Test fun `template punya dropdown kondisi dan status`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(
            listOf(Sheet("T", listOf("A", "Kondisi"), emptyList(), validations = listOf(id.perusahaan.asetga.domain.excel.ListValidation(1, listOf("Baik", "Rusak"))))),
            out,
        )
        val zip = java.util.zip.ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        var xml = ""
        while (true) {
            val e = zip.nextEntry ?: break
            if (e.name == "xl/worksheets/sheet1.xml") xml = zip.readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(xml, xml.contains("""<dataValidation type="list""""))
        assertTrue(xml.contains("sqref=\"B2:B5000\""))
        assertTrue(xml.contains("&quot;Baik,Rusak&quot;"))
        assertEquals("Kondisi", ImportColumn.CONDITION.header)
        assertEquals(listOf("Kode Aset*", "Kode QR*", "Nama Aset*"), ImportColumn.entries.take(3).map { it.templateHeader })
    }
}
