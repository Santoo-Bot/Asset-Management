package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.calc.PolicyCalc
import id.perusahaan.asetga.domain.calc.PolicyStatus
import id.perusahaan.asetga.domain.excel.InsuranceImportTemplate
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.validation.OperationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsuranceTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test fun `status polis`() {
        assertEquals(PolicyStatus.NOT_STARTED, PolicyCalc.status(today.plusDays(1), today.plusYears(1), today))
        assertEquals(PolicyStatus.ACTIVE, PolicyCalc.status(today.minusMonths(1), today.plusDays(31), today))
        assertEquals(PolicyStatus.EXPIRING, PolicyCalc.status(today.minusMonths(11), today.plusDays(30), today))
        assertEquals(PolicyStatus.EXPIRING, PolicyCalc.status(today.minusMonths(11), today, today))
        assertEquals(PolicyStatus.EXPIRED, PolicyCalc.status(today.minusYears(1), today.minusDays(1), today))
    }

    @Test fun `validasi polis`() {
        val e = OperationValidator.validatePolicy(PolicyInput(startDate = today, endDate = today.minusDays(1)))
        assertEquals(
            setOf(OperationValidator.FIELD_POLICY_NUMBER, OperationValidator.FIELD_TYPE, OperationValidator.FIELD_INSURER, OperationValidator.FIELD_END),
            e.keys,
        )
        assertTrue(OperationValidator.validatePolicy(PolicyInput(policyNumber = "POL-1", typeId = "t", insurerId = "i")).isEmpty())
    }

    @Test fun `parse file upload kode aset atau kode QR`() {
        val (rows, err) = InsuranceImportTemplate.parse(
            listOf(
                listOf("Kode Aset / Kode QR*", "Nilai Pertanggungan"),
                listOf("IT-2024-001", "15.000.000"),
                listOf("", ""),
                listOf("QR-LAMA-7", ""),
                listOf("it-2024-001", ""),
                listOf("", "5"),
                listOf("IT-9", "abc"),
            ),
        )
        assertEquals(null, err)
        assertEquals(5, rows.size)
        assertEquals(15_000_000L, rows[0].sumInsured)
        assertTrue(rows[1].errors.isEmpty())
        assertTrue(rows[2].errors.single().contains("ganda"))
        assertEquals("Kode kosong", rows[3].errors.single())
        assertTrue(rows[4].errors.single().contains("tidak valid"))
        assertEquals(7, rows[4].rowNumber)
    }

    @Test fun `kolom tanpa judul cocok memakai kolom pertama`() {
        val (rows, _) = InsuranceImportTemplate.parse(listOf(listOf("Aset"), listOf("IT-1")))
        assertEquals("IT-1", rows.single().code)
    }
}
