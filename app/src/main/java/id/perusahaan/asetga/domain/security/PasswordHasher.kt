package id.perusahaan.asetga.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash kata sandi / PIN dengan PBKDF2-HMAC-SHA256 + salt acak.
 * Format tersimpan: `pbkdf2-sha256$<iterasi>$<salt base64>$<hash base64>`.
 */
class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(secret.toCharArray(), salt, iterations)
        return listOf(PREFIX, iterations.toString(), b64(salt), b64(hash)).joinToString("$")
    }

    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iter = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = derive(secret.toCharArray(), salt, iter)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(secret: CharArray, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iter, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    companion object {
        const val PREFIX = "pbkdf2-sha256"
        const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
