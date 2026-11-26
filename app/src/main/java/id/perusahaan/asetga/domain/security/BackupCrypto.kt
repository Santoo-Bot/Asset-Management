package id.perusahaan.asetga.domain.security

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi file backup: AES-256-GCM, kunci diturunkan dari passphrase dengan PBKDF2-HMAC-SHA256.
 * Format: MAGIC(8) | versi(1) | iterasi(4) | salt(16) | iv(12) | ciphertext+tag.
 * GCM juga memverifikasi integritas: passphrase salah atau file rusak akan gagal didekripsi.
 */
object BackupCrypto {
    private val MAGIC = "ASETGABK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_PASSPHRASE = 8

    class WrongPassphraseException : IOException("Passphrase salah atau file backup rusak")
    class NotABackupException : IOException("File ini bukan backup Aset GA")

    private fun key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Bungkus [out] dengan stream terenkripsi; tutup stream yang dikembalikan untuk menulis tag. */
    fun encrypting(out: OutputStream, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        out.write(MAGIC)
        out.write(VERSION.toInt())
        out.write(byteArrayOf((iterations ushr 24).toByte(), (iterations ushr 16).toByte(), (iterations ushr 8).toByte(), iterations.toByte()))
        out.write(salt)
        out.write(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        }
        return CipherOutputStream(out, cipher)
    }

    /** Dekripsi seluruh isi [input] ke [out]. Melempar [WrongPassphraseException] bila verifikasi gagal. */
    fun decrypt(input: InputStream, out: OutputStream, passphrase: CharArray) {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (e: IOException) {
            throw NotABackupException()
        }
        if (!magic.contentEquals(MAGIC)) throw NotABackupException()
        val version = data.readByte()
        if (version != VERSION) throw IOException("Versi backup tidak didukung ($version)")
        val iterations = data.readInt()
        if (iterations !in 10_000..5_000_000) throw NotABackupException()
        val salt = ByteArray(SALT_BYTES).also(data::readFully)
        val iv = ByteArray(IV_BYTES).also(data::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        }
        try {
            CipherInputStream(data, cipher).use { it.copyTo(out) }
        } catch (e: IOException) {
            // CipherInputStream membungkus AEADBadTagException sebagai IOException.
            throw WrongPassphraseException()
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }
}
