package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.calc.AssetAgeCalc
import id.perusahaan.asetga.domain.calc.AssetCodeGenerator
import id.perusahaan.asetga.domain.calc.Depreciation
import id.perusahaan.asetga.domain.calc.ServiceDueCalc
import id.perusahaan.asetga.domain.calc.ServiceDueStatus
import id.perusahaan.asetga.domain.calc.StockCalc
import id.perusahaan.asetga.domain.calc.WarrantyCalc
import id.perusahaan.asetga.domain.calc.WarrantyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalculationsTest {
    private val d = LocalDate.of(2024, 1, 15)

    @Test fun `penyusutan garis lurus tanpa residu`() {
        // Laptop Rp 12.000.000, umur 4 th → 3.000.000/th; 18 bulan berjalan → 4.500.000
        val r = Depreciation.calculate(12_000_000, d, 4, 0.0, LocalDate.of(2025, 7, 15))
        assertEquals(3_000_000, r.annualDepreciation)
        assertEquals(18, r.monthsElapsed)
        assertEquals(4_500_000, r.accumulated)
        assertEquals(7_500_000, r.bookValue)
    }

    @Test fun `penyusutan dengan residu`() {
        // Mobil Rp 300 jt, residu 10%, umur 8 th → (300jt × 0,9)/8 = 33.750.000/th
        val r = Depreciation.calculate(300_000_000, d, 8, 10.0, LocalDate.of(2026, 1, 15))
        assertEquals(30_000_000, r.residualValue)
        assertEquals(270_000_000, r.depreciableAmount)
        assertEquals(33_750_000, r.annualDepreciation)
        assertEquals(67_500_000, r.accumulated)
        assertEquals(232_500_000, r.bookValue)
    }

    @Test fun `akumulasi tidak melebihi nilai yang dapat disusutkan`() {
        val r = Depreciation.calculate(10_000_000, d, 4, 5.0, LocalDate.of(2040, 1, 1))
        assertEquals(9_500_000, r.accumulated)
        assertEquals(500_000, r.bookValue)
        assertTrue(r.fullyDepreciated)
    }

    @Test fun `bulan belum penuh belum disusutkan dan tanggal masa depan aman`() {
        assertEquals(0, Depreciation.calculate(1_200_000, d, 1, 0.0, LocalDate.of(2024, 2, 14)).accumulated)
        assertEquals(100_000, Depreciation.calculate(1_200_000, d, 1, 0.0, LocalDate.of(2024, 2, 15)).accumulated)
        assertEquals(1_200_000, Depreciation.calculate(1_200_000, d, 1, 0.0, LocalDate.of(2023, 1, 1)).bookValue)
    }

    @Test fun `pembulatan tidak membuat nilai buku negatif`() {
        val r = Depreciation.calculate(1_000, d, 3, 0.0, LocalDate.of(2026, 12, 31))
        assertTrue(r.bookValue >= 0)
        assertTrue(r.accumulated <= r.depreciableAmount)
    }

    @Test fun `umur berjalan`() {
        val age = AssetAgeCalc.of(d, LocalDate.of(2026, 4, 20))
        assertEquals(2, age.years)
        assertEquals(3, age.months)
    }

    @Test fun `status garansi`() {
        val today = LocalDate.of(2026, 11, 26)
        assertEquals(WarrantyStatus.NONE, WarrantyCalc.status(null, today))
        assertEquals(WarrantyStatus.EXPIRED, WarrantyCalc.status(today.minusDays(1), today))
        assertEquals(WarrantyStatus.EXPIRING, WarrantyCalc.status(today, today))
        assertEquals(WarrantyStatus.EXPIRING, WarrantyCalc.status(today.plusDays(90), today))
        assertEquals(WarrantyStatus.ACTIVE, WarrantyCalc.status(today.plusDays(91), today))
    }

    @Test fun `status servis`() {
        val today = LocalDate.of(2026, 11, 26)
        assertEquals(ServiceDueStatus.NONE, ServiceDueCalc.status(null, today))
        assertEquals(ServiceDueStatus.OVERDUE, ServiceDueCalc.status(today.minusDays(1), today))
        assertEquals(ServiceDueStatus.DUE_SOON, ServiceDueCalc.status(today, today))
        assertEquals(ServiceDueStatus.DUE_SOON, ServiceDueCalc.status(today.plusDays(30), today))
        assertEquals(ServiceDueStatus.SCHEDULED, ServiceDueCalc.status(today.plusDays(31), today))
    }

    @Test fun `perhitungan stok dan reorder`() {
        val cur = StockCalc.current(initial = 10.0, totalIn = 25.0, totalOut = 30.0)
        assertEquals(5.0, cur, 0.0)
        assertTrue(StockCalc.needsReorder(cur, 5.0))
        assertFalse(StockCalc.needsReorder(cur, 4.0))
        assertTrue(StockCalc.canTakeOut(cur, 5.0))
        assertFalse(StockCalc.canTakeOut(cur, 5.5))
        assertFalse(StockCalc.canTakeOut(cur, 0.0))
        assertEquals(0.3, StockCalc.current(0.1, 0.2, 0.0), 0.0)
    }

    @Test fun `kode aset otomatis`() {
        assertEquals("IT-2024-001", AssetCodeGenerator.format("it", 2024, 1))
        assertEquals("IT-2024-1000", AssetCodeGenerator.format("IT", 2024, 1000))
        val existing = listOf("IT-2024-001", "IT-2024-007", "IT-2023-050", "FUR-2024-099", "IT-2024-ABC")
        assertEquals(8, AssetCodeGenerator.nextSequence("IT", 2024, existing))
        assertEquals(1, AssetCodeGenerator.nextSequence("IT", 2025, existing))
    }
}
