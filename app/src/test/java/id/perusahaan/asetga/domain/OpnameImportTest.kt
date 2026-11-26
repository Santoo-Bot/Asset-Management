package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.excel.OpnameImportTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpnameImportTest {
    @Test fun `kolom kode dikenali dan baris ganda ditandai`() {
        val (rows, error) = OpnameImportTemplate.parse(
            listOf(
                listOf("Nama Aset", "Kode Aset*", "Harga Perolehan"),
                listOf("Laptop", "IT-2024-001", "15.000.000"),
                listOf("", "", ""),
                listOf("Printer", "it-2024-001", "x"),
                listOf("Meja", "", ""),
            ),
        )
        assertNull(error)
        assertEquals(listOf(2, 4, 5), rows.map { it.rowNumber })
        assertTrue(rows[0].errors.isEmpty())
        assertTrue(rows[1].errors.single().contains("ganda"))
        assertEquals("Kode kosong", rows[2].errors.single())
    }

    @Test fun `file tanpa data ditolak`() {
        assertEquals("File kosong", OpnameImportTemplate.parse(emptyList()).second)
        assertEquals("Tidak ada baris data", OpnameImportTemplate.parse(listOf(listOf("Kode Aset"))).second)
    }
}
