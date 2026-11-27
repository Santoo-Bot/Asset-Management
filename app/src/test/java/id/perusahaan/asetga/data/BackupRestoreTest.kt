package id.perusahaan.asetga.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.file.BackupManager
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.security.BackupCrypto
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager
    private val pass = "passphrase-uji"

    private val dbFile get() = ctx.getDatabasePath(AppDatabase.NAME)
    private val photoDir get() = File(ctx.filesDir, "photos")

    @Before fun setUp() {
        db = inMemoryDb()
        manager = BackupManager(ctx, db, AuditLogRepositoryImpl(db.auditLogDao(), session, clock), session)
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("DATA-LAMA")
        photoDir.deleteRecursively()
        photoDir.mkdirs()
        File(photoDir, "lama.jpg").writeText("foto-lama")
    }

    @After fun tearDown() {
        db.close()
        photoDir.deleteRecursively()
    }

    /** Membuat file backup terenkripsi berisi database SQLite berversi [schemaVersion] dan foto [photos]. */
    private fun makeBackup(schemaVersion: Int, photos: Map<String, String> = emptyMap()): Uri {
        val raw = File(ctx.cacheDir, "fake-$schemaVersion.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(raw, null).use { it.version = schemaVersion; it.execSQL("CREATE TABLE t(a)") }
        val out = File(ctx.cacheDir, "uji-$schemaVersion.gabak")
        out.outputStream().use { fos ->
            BackupCrypto.encrypting(fos, pass.toCharArray(), iterations = 10_000).use { enc ->
                ZipOutputStream(enc).use { zip ->
                    zip.putNextEntry(ZipEntry(BackupManager.DB_ENTRY)); raw.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    photos.forEach { (name, text) -> zip.putNextEntry(ZipEntry("photos/$name")); zip.write(text.toByteArray()); zip.closeEntry() }
                }
            }
        }
        return Uri.fromFile(out)
    }

    @Test fun `backup dari versi aplikasi lebih baru ditolak dan data lama utuh`() = runTest {
        val r = manager.restore(makeBackup(schemaVersion = 99), pass)
        assertTrue(r is AppResult.Failure && r.message.contains("lebih baru"))
        assertEquals("DATA-LAMA", dbFile.readText())
        assertTrue(File(photoDir, "lama.jpg").exists())
        // Database masih terbuka dan bisa dipakai.
        assertEquals(0, db.userDao().count())
    }

    @Test fun `restore valid mengganti database dan foto`() = runTest {
        val current = db.openHelper.readableDatabase.version
        val r = manager.restore(makeBackup(current, mapOf("baru.jpg" to "foto-baru")), pass)
        assertTrue(r is AppResult.Success)
        assertEquals("SQLite format 3", dbFile.readBytes().copyOfRange(0, 15).toString(Charsets.US_ASCII))
        assertEquals("foto-baru", File(photoDir, "baru.jpg").readText())
        assertFalse(File(photoDir, "lama.jpg").exists())
    }

    @Test fun `passphrase salah tidak mengubah data`() = runTest {
        val r = manager.restore(makeBackup(db.openHelper.readableDatabase.version), "passphrase-salah")
        assertTrue(r is AppResult.Failure)
        assertEquals("DATA-LAMA", dbFile.readText())
        assertTrue(File(photoDir, "lama.jpg").exists())
    }

    @Test fun `hanya admin yang boleh backup dan restore`() = runTest {
        session.login(User("st", "staf", "Staf", Role.STAFF, hasPin = false, isActive = true))
        val back = manager.backup(Uri.fromFile(File(ctx.cacheDir, "keluar.gabak")), pass)
        assertTrue(back is AppResult.Failure && back.message.contains("Admin GA"))
        assertTrue(manager.restore(makeBackup(db.openHelper.readableDatabase.version), pass) is AppResult.Failure)
        assertEquals("DATA-LAMA", dbFile.readText())
    }
}
