package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Vendor
import id.perusahaan.asetga.domain.security.CredentialRules
import id.perusahaan.asetga.domain.validation.MasterValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test fun `aturan username`() {
        assertNull(CredentialRules.validateUsername("budi.s"))
        assertNotNull(CredentialRules.validateUsername("ab"))
        assertNotNull(CredentialRules.validateUsername("Budi"))
        assertNotNull(CredentialRules.validateUsername("budi santoso"))
    }

    @Test fun `aturan kata sandi`() {
        assertNull(CredentialRules.validatePassword("abcd1234"))
        assertNotNull(CredentialRules.validatePassword("abc123"))
        assertNotNull(CredentialRules.validatePassword("abcdefgh"))
        assertNotNull(CredentialRules.validatePassword("12345678"))
    }

    @Test fun `aturan PIN`() {
        assertNull(CredentialRules.validatePin("2580"))
        assertNotNull(CredentialRules.validatePin("123"))
        assertNotNull(CredentialRules.validatePin("1111"))
        assertNotNull(CredentialRules.validatePin("12a4"))
    }

    @Test fun `kategori valid`() {
        assertTrue(MasterValidator.validateCategory(Category("", "Elektronik & IT", "IT", 4, null)).isEmpty())
        assertTrue(MasterValidator.validateCategory(Category("", "Kendaraan", "KND", 8, 10.0)).isEmpty())
    }

    @Test fun `kategori tidak valid`() {
        val errors = MasterValidator.validateCategory(Category("", " ", "it1", 0, 100.0))
        assertEquals(
            setOf(MasterValidator.FIELD_NAME, MasterValidator.FIELD_PREFIX, MasterValidator.FIELD_LIFE, MasterValidator.FIELD_RESIDUAL),
            errors.keys,
        )
    }

    @Test fun `vendor email dan telepon`() {
        assertTrue(MasterValidator.validateVendor(Vendor("", "PT A", "021-555 0101", "cs@a.co.id", null)).isEmpty())
        val errors = MasterValidator.validateVendor(Vendor("", "PT A", "abc", "bukan-email", null))
        assertEquals(setOf(MasterValidator.FIELD_EMAIL, MasterValidator.FIELD_PHONE), errors.keys)
    }
}
