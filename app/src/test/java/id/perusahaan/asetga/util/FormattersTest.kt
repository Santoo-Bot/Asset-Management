package id.perusahaan.asetga.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FormattersTest {
    @Test fun `format rupiah`() {
        assertEquals("Rp 0", Fmt.rupiah(0))
        assertEquals("Rp 1.250.000", Fmt.rupiah(1_250_000))
        assertEquals("-Rp 15.000", Fmt.rupiah(-15_000))
    }

    @Test fun `parse rupiah dari teks masukan`() {
        assertEquals(1_250_000L, Fmt.parseRupiah("Rp 1.250.000"))
        assertEquals(5_000L, Fmt.parseRupiah("5000"))
        assertNull(Fmt.parseRupiah(""))
        assertNull(Fmt.parseRupiah("abc"))
    }

    @Test fun `format tanggal Indonesia`() {
        assertEquals("05 Jan 2024", Fmt.date(LocalDate.of(2024, 1, 5)))
        assertEquals("26 Nov 2026", Fmt.date(LocalDate.of(2026, 11, 26)))
        assertEquals("01 Mei 2025", Fmt.date(LocalDate.of(2025, 5, 1)))
        assertEquals("-", Fmt.date(null))
    }

    @Test fun `format tanggal dan jam`() {
        val millis = LocalDate.of(2026, 3, 2).atTime(14, 5).atZone(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
        assertEquals("02 Mar 2026 14:05", Fmt.dateTime(millis, ZoneId.of("Asia/Jakarta")))
    }

    @Test fun `angka desimal dan persen`() {
        assertEquals(12.5, Fmt.parseDecimal("12,5")!!, 0.0)
        assertEquals(12.5, Fmt.parseDecimal("12.5")!!, 0.0)
        assertEquals("12,5%", Fmt.percent(12.5))
        assertEquals("1.000,25", Fmt.qty(1000.25))
    }

    @Test fun `umur berjalan`() {
        assertEquals("< 1 bln", Fmt.age(0, 0))
        assertEquals("3 bln", Fmt.age(0, 3))
        assertEquals("2 th", Fmt.age(2, 0))
        assertEquals("2 th 3 bln", Fmt.age(2, 3))
    }
}
