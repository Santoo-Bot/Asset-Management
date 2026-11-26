package id.perusahaan.asetga.domain

import id.perusahaan.asetga.data.worker.ReminderNotifier
import id.perusahaan.asetga.data.worker.ReminderScheduler
import id.perusahaan.asetga.domain.model.ReminderSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ReminderTest {
    @Test fun `jeda ke jam 08 berikutnya`() {
        assertEquals(Duration.ofHours(1), ReminderScheduler.delayUntil(8, LocalDateTime.of(2026, 11, 26, 7, 0)))
        assertEquals(Duration.ofHours(23), ReminderScheduler.delayUntil(8, LocalDateTime.of(2026, 11, 26, 9, 0)))
        assertEquals(Duration.ofHours(24), ReminderScheduler.delayUntil(8, LocalDateTime.of(2026, 11, 26, 8, 0)))
    }

    @Test fun `isi notifikasi hanya untuk yang ada`() {
        assertTrue(ReminderSummary(0, 0, 0, 0).isEmpty)
        val lines = ReminderNotifier.lines(ReminderSummary(warrantyExpiring = 3, serviceOverdue = 1, serviceDueSoon = 0, reorderItems = 2))
        assertEquals(listOf("1 aset servis terlambat", "3 aset garansi segera habis (≤90 hari)", "2 barang stok di bawah minimum"), lines)
    }
}
