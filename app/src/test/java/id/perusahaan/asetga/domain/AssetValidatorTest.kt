package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.validation.AssetValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AssetValidatorTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val valid = AssetInput(
        name = "Laptop Dell Latitude", categoryId = "c", locationId = "l",
        acquisitionDate = LocalDate.of(2024, 1, 10), acquisitionCost = 15_000_000,
    )

    @Test fun `input lengkap valid dan kode boleh kosong`() {
        assertTrue(AssetValidator.validate(valid, today).isEmpty())
        assertTrue(AssetValidator.validate(valid.copy(code = "it-2024-001"), today).isEmpty())
    }

    @Test fun `field wajib`() {
        val e = AssetValidator.validate(AssetInput(), today)
        assertEquals(
            setOf(AssetValidator.FIELD_NAME, AssetValidator.FIELD_CATEGORY, AssetValidator.FIELD_LOCATION, AssetValidator.FIELD_ACQ_DATE, AssetValidator.FIELD_COST),
            e.keys,
        )
    }

    @Test fun `tanggal masa depan, harga negatif, garansi sebelum perolehan`() {
        val e = AssetValidator.validate(
            valid.copy(acquisitionDate = today.plusDays(1), acquisitionCost = -1, warrantyUntil = today.minusYears(5)), today,
        )
        assertEquals(setOf(AssetValidator.FIELD_ACQ_DATE, AssetValidator.FIELD_COST, AssetValidator.FIELD_WARRANTY), e.keys)
    }

    @Test fun `normalisasi kode`() {
        assertEquals("IT-2024-001", AssetValidator.normalizeCode(" it-2024-001 "))
        assertTrue(AssetValidator.FIELD_CODE in AssetValidator.validate(valid.copy(code = "#@!"), today))
    }
}
