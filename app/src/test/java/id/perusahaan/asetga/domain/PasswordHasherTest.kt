package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.security.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher(iterations = 1_000)

    @Test fun `hash tidak menyimpan plaintext dan bisa diverifikasi`() {
        val stored = hasher.hash("rahasia123")
        assertFalse(stored.contains("rahasia123"))
        assertTrue(stored.startsWith("pbkdf2-sha256$1000$"))
        assertTrue(hasher.verify("rahasia123", stored))
    }

    @Test fun `kata sandi salah ditolak`() {
        val stored = hasher.hash("rahasia123")
        assertFalse(hasher.verify("rahasia124", stored))
        assertFalse(hasher.verify("", stored))
    }

    @Test fun `salt acak menghasilkan hash berbeda untuk kata sandi sama`() {
        assertNotEquals(hasher.hash("sama12345"), hasher.hash("sama12345"))
    }

    @Test fun `format rusak tidak crash dan ditolak`() {
        assertFalse(hasher.verify("x", "bukan-hash"))
        assertFalse(hasher.verify("x", "pbkdf2-sha256\$abc\$###\$###"))
    }

    @Test fun `iterasi tersimpan dipakai saat verifikasi`() {
        val stored = PasswordHasher(iterations = 2_000).hash("abc12345")
        assertTrue(hasher.verify("abc12345", stored))
    }
}
