package id.perusahaan.asetga.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.file.BackupManager
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.Migrations
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.security.BackupCrypto
import id.perusahaan.asetga.domain.security.PasswordHasher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Backup buatan aplikasi desktop (Windows) harus bisa dibuka aplikasi Android.
 * File uji dibuat oleh `desktop/tools/make_android_fixture.py`; buat ulang bila skema Room berubah.
 */
@RunWith(AndroidJUnit4::class)
class DesktopCompatTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "desktop-compat.db"
    private var db: AppDatabase? = null

    @After fun tearDown() {
        db?.close()
        ctx.deleteDatabase(name)
    }

    private fun extractDatabase() {
        val encrypted = javaClass.classLoader!!.getResourceAsStream("desktop-compat/desktop-backup.gabak")!!.readBytes()
        val zip = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(encrypted), zip, "passphrase-uji".toCharArray())
        var found = false
        ZipInputStream(ByteArrayInputStream(zip.toByteArray())).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == BackupManager.DB_ENTRY) {
                    val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs() }
                    file.outputStream().use { z.copyTo(it) }
                    found = true
                }
                e = z.nextEntry
            }
        }
        assertTrue("Backup desktop harus berisi ${BackupManager.DB_ENTRY}", found)
    }

    @Test fun `database dari aplikasi desktop dibuka Room tanpa migrasi destruktif`() = runTest {
        extractDatabase()
        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        db = room

        // Kata sandi yang dibuat di desktop bisa dipakai login di HP.
        val admin = room.userDao().getByUsername("admin")
        assertNotNull(admin)
        assertTrue(PasswordHasher().verify("Rahasia123", admin!!.passwordHash))

        val laptop = room.assetDao().getByCode("IT-2024-001")
        assertNotNull(laptop)
        assertEquals("QR-0001", laptop!!.qrCode)
        assertEquals(15_000_000L, laptop.acquisitionCost)
        assertEquals("Charger, Tas", laptop.accessories)
        assertNotNull(laptop.vendorId)
        assertNotNull(laptop.divisionId)

        val printer = room.assetDao().getByCode("IT-2023-005")
        assertEquals(AssetStatus.WRITE_OFF, printer!!.status)

        assertEquals(2, room.assetDao().exportRows().size)
        assertEquals(5, room.assetStatusDao().all().size)
    }
}
