package id.perusahaan.asetga.domain

import id.perusahaan.asetga.domain.security.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class BackupCryptoTest {
    private val payload = Random(1).nextBytes(200_000)

    private fun encrypt(pass: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypting(out, pass.toCharArray(), iterations = 20_000).use { it.write(payload) }
        return out.toByteArray()
    }

    @Test fun `enkripsi lalu dekripsi mengembalikan data asli`() {
        val enc = encrypt("rahasia-kantor")
        assertFalse(String(enc, Charsets.ISO_8859_1).contains(String(payload.copyOf(64), Charsets.ISO_8859_1)))
        val out = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(enc), out, "rahasia-kantor".toCharArray())
        assertArrayEquals(payload, out.toByteArray())
    }

    @Test fun `passphrase salah ditolak`() {
        val enc = encrypt("rahasia-kantor")
        try {
            BackupCrypto.decrypt(ByteArrayInputStream(enc), ByteArrayOutputStream(), "salah-sekali".toCharArray())
            fail("Seharusnya gagal")
        } catch (expected: BackupCrypto.WrongPassphraseException) {
            // OK
        }
    }

    @Test fun `file rusak atau bukan backup ditolak`() {
        val enc = encrypt("rahasia-kantor")
        enc[enc.size - 5] = (enc[enc.size - 5].toInt() xor 0xFF).toByte()
        try {
            BackupCrypto.decrypt(ByteArrayInputStream(enc), ByteArrayOutputStream(), "rahasia-kantor".toCharArray())
            fail("Seharusnya gagal")
        } catch (expected: BackupCrypto.WrongPassphraseException) {
            // OK
        }
        try {
            BackupCrypto.decrypt(ByteArrayInputStream("bukan backup".toByteArray()), ByteArrayOutputStream(), "x".toCharArray())
            fail("Seharusnya gagal")
        } catch (expected: BackupCrypto.NotABackupException) {
            // OK
        }
    }
}
