package id.perusahaan.asetga.data.file

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SimpleSQLiteQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.security.BackupCrypto
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup = ZIP berisi database + folder foto, lalu dienkripsi (BackupCrypto) dan ditulis ke lokasi
 * pilihan pengguna lewat Storage Access Framework (penyimpanan HP, kartu SD, atau Google Drive).
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val audit: AuditLogRepository,
    private val session: SessionManager,
) {
    fun suggestedFileName(): String =
        "aset-ga-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.gabak"

    suspend fun backup(target: Uri, passphrase: String): AppResult<Long> = withContext(Dispatchers.IO) {
        // File backup memuat seluruh database, termasuk hash kata sandi/PIN semua pengguna.
        if (session.currentUser?.role?.canManageMaster != true) return@withContext AppResult.Failure("Hanya Admin GA yang dapat membuat backup")
        if (passphrase.length < BackupCrypto.MIN_PASSPHRASE) return@withContext AppResult.Failure("Passphrase minimal ${BackupCrypto.MIN_PASSPHRASE} karakter")
        // Catat dulu agar log backup ikut tersimpan di dalam file backup.
        audit.log(AuditEntityType.SYSTEM, "backup", AuditAction.UPDATE, "Backup data dibuat")
        try {
            // Pindahkan isi WAL ke file database utama agar salinan konsisten.
            db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
            val dbFile = context.getDatabasePath(AppDatabase.NAME)
            val photoDir = File(context.filesDir, PhotoStorage.DIR)
            val output = context.contentResolver.openOutputStream(target, "wt") ?: return@withContext AppResult.Failure("Lokasi tujuan tidak dapat ditulis")
            output.use { raw ->
                BackupCrypto.encrypting(raw, passphrase.toCharArray()).use { enc ->
                    ZipOutputStream(enc).use { zip ->
                        zip.putNextEntry(ZipEntry(MANIFEST))
                        zip.write("app=aset-ga\nformat=1\ncreated=${LocalDateTime.now()}\n".toByteArray())
                        zip.closeEntry()
                        zip.putNextEntry(ZipEntry(DB_ENTRY))
                        dbFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        photoDir.listFiles()?.filter { it.isFile }?.forEach { f ->
                            zip.putNextEntry(ZipEntry("${PhotoStorage.DIR}/${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            val size = context.contentResolver.openFileDescriptor(target, "r")?.use { it.statSize } ?: -1L
            AppResult.Success(size)
        } catch (e: Exception) {
            AppResult.Failure("Backup gagal: ${e.message}")
        }
    }

    /**
     * Pulihkan dari file backup. Seluruh file didekripsi & diperiksa lebih dulu di folder sementara;
     * data lama baru diganti bila backup valid. Setelah sukses aplikasi harus dimulai ulang.
     */
    suspend fun restore(source: Uri, passphrase: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (session.currentUser?.role?.canManageMaster != true) return@withContext AppResult.Failure("Hanya Admin GA yang dapat me-restore data")
        val work = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            val zipFile = File(work, "backup.zip")
            val input = context.contentResolver.openInputStream(source) ?: return@withContext AppResult.Failure("File tidak dapat dibuka")
            input.use { inp -> zipFile.outputStream().use { BackupCrypto.decrypt(inp, it, passphrase.toCharArray()) } }

            val extracted = File(work, "data").apply { mkdirs() }
            var hasDb = false
            ZipInputStream(zipFile.inputStream()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    // Tolak path traversal (../) dan entri di luar daftar yang dikenal.
                    val allowed = name == DB_ENTRY || name == MANIFEST || (name.startsWith("${PhotoStorage.DIR}/") && !name.contains(".."))
                    if (allowed && !e.isDirectory) {
                        val out = File(extracted, name)
                        if (!out.canonicalPath.startsWith(extracted.canonicalPath + File.separator)) throw IOException("Entri backup tidak valid")
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        if (name == DB_ENTRY) hasDb = true
                    }
                    e = zip.nextEntry
                }
            }
            val newDb = File(extracted, DB_ENTRY)
            if (!hasDb || !isSqlite(newDb)) return@withContext AppResult.Failure("Backup tidak berisi database yang valid")

            // Tolak backup dari versi aplikasi yang lebih baru: Room tidak bisa menurunkan skema dan aplikasi
            // akan crash setiap dibuka. Backup versi lama aman karena dimigrasikan saat database dibuka.
            val backupVersion = sqliteUserVersion(newDb)
            val currentVersion = db.openHelper.readableDatabase.version
            if (backupVersion < 1) return@withContext AppResult.Failure("Backup tidak berisi database Aset GA yang valid")
            if (backupVersion > currentVersion) {
                return@withContext AppResult.Failure("Backup dibuat oleh versi aplikasi yang lebih baru (skema $backupVersion, aplikasi ini $currentVersion). Perbarui aplikasi dulu.")
            }

            // Ganti data. Data lama disimpan dulu di folder sementara; bila ada langkah yang gagal, dikembalikan.
            db.close()
            val dbFile = context.getDatabasePath(AppDatabase.NAME)
            val dbSiblings = listOf("-wal", "-shm").map { File(dbFile.path + it) }
            val photoDir = File(context.filesDir, PhotoStorage.DIR)
            val old = File(work, "old").apply { mkdirs() }
            val oldDbFile = File(old, "db")
            val oldPhotos = File(old, PhotoStorage.DIR)
            val hadDb = dbFile.exists()
            try {
                if (hadDb) dbFile.copyTo(oldDbFile, overwrite = true)
                if (photoDir.exists()) moveDir(photoDir, oldPhotos)
                dbSiblings.forEach { it.delete() }
                newDb.copyTo(dbFile, overwrite = true)
                val newPhotos = File(extracted, PhotoStorage.DIR)
                if (newPhotos.exists()) moveDir(newPhotos, photoDir) else photoDir.mkdirs()
            } catch (e: Exception) {
                runCatching {
                    dbSiblings.forEach { it.delete() }
                    if (hadDb) oldDbFile.copyTo(dbFile, overwrite = true) else dbFile.delete()
                    photoDir.deleteRecursively()
                    if (oldPhotos.exists()) moveDir(oldPhotos, photoDir) else photoDir.mkdirs()
                }
                return@withContext AppResult.Failure("Restore gagal dan data lama dikembalikan. Tutup lalu buka kembali aplikasi. (${e.message})")
            }
            AppResult.Success(Unit)
        } catch (e: BackupCrypto.WrongPassphraseException) {
            AppResult.Failure(e.message ?: "Passphrase salah")
        } catch (e: BackupCrypto.NotABackupException) {
            AppResult.Failure(e.message ?: "Bukan file backup")
        } catch (e: Exception) {
            AppResult.Failure("Restore gagal: ${e.message}")
        } finally {
            work.deleteRecursively()
        }
    }

    private fun isSqlite(file: File): Boolean = runCatching {
        file.inputStream().use { val head = ByteArray(16); it.read(head) == 16 && String(head, 0, 15, Charsets.US_ASCII) == "SQLite format 3" }
    }.getOrDefault(false)

    /** Nilai `user_version` (= versi skema Room) dari header file SQLite: 4 byte big-endian di offset 60. */
    private fun sqliteUserVersion(file: File): Int = runCatching {
        file.inputStream().use { val head = ByteArray(100); if (it.read(head) == 100) java.nio.ByteBuffer.wrap(head, 60, 4).int else 0 }
    }.getOrDefault(0)

    /** Pindahkan folder; bila rename gagal (beda volume) salin lalu hapus asalnya. */
    private fun moveDir(src: File, dst: File) {
        dst.deleteRecursively()
        if (src.renameTo(dst)) return
        if (!src.copyRecursively(dst, overwrite = true)) throw IOException("Gagal menyalin ${src.name}")
        src.deleteRecursively()
    }

    companion object {
        const val DB_ENTRY = "db/${AppDatabase.NAME}"
        const val MANIFEST = "manifest.txt"
        const val MIME = "application/octet-stream"
    }
}
