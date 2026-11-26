package id.perusahaan.asetga.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.DefaultData
import id.perusahaan.asetga.data.local.Migrations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mensimulasikan database yang sudah terpasang di HP (skema v1) lalu membukanya dengan aplikasi baru (v2).
 * Room memvalidasi seluruh skema setelah migrasi; bila migrasi tidak cocok, test ini gagal.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val v1Assets = """
        CREATE TABLE IF NOT EXISTS `assets` (`id` TEXT NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `categoryId` TEXT NOT NULL,
        `brandModel` TEXT, `serialNumber` TEXT, `acquisitionDate` INTEGER NOT NULL, `acquisitionCost` INTEGER NOT NULL, `locationId` TEXT NOT NULL,
        `departmentId` TEXT, `picName` TEXT, `condition` TEXT NOT NULL, `status` TEXT NOT NULL, `warrantyUntil` INTEGER, `notes` TEXT,
        `photoPath` TEXT, `disposalDate` INTEGER, `disposalValue` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
        `deletedAt` INTEGER, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`),
        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT ,
        FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT ,
        FOREIGN KEY(`departmentId`) REFERENCES `departments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )
    """.trimIndent()

    private val v1Indices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_assets_code` ON `assets` (`code`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_name` ON `assets` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_categoryId` ON `assets` (`categoryId`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_locationId` ON `assets` (`locationId`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_departmentId` ON `assets` (`departmentId`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_condition` ON `assets` (`condition`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_status` ON `assets` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_serialNumber` ON `assets` (`serialNumber`)",
    )

    @Test fun `migrasi 1 ke 2 mempertahankan data aset`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migrasi-test.db"
        ctx.deleteDatabase(name)

        // 1. Buat database lengkap, lalu kembalikan tabel aset ke bentuk v1 + isi satu aset.
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).addCallback(DefaultData.callback).build().apply {
            openHelper.writableDatabase
            close()
        }
        val raw = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        val categoryId = raw.rawQuery("SELECT id FROM categories LIMIT 1", null).use { it.moveToFirst(); it.getString(0) }
        raw.execSQL("INSERT INTO locations (id, name, address, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('loc', 'Kantor', NULL, 1, 1, 1, NULL, 'PENDING')")
        raw.execSQL("DROP TABLE assets")
        raw.execSQL(v1Assets)
        v1Indices.forEach(raw::execSQL)
        raw.execSQL(
            "INSERT INTO assets (id, code, name, categoryId, acquisitionDate, acquisitionCost, locationId, condition, status, createdAt, updatedAt, syncState) " +
                "VALUES ('a1', 'IT-2024-001', 'Laptop lama', ?, 19723, 12000000, 'loc', 'BAIK', 'AKTIF', 1, 1, 'PENDING')",
            arrayOf(categoryId),
        )
        raw.version = 1
        raw.close()

        // 2. Buka dengan aplikasi versi baru.
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).build()
        val asset = runBlocking { db.assetDao().getById("a1") }
        assertNotNull(asset)
        assertEquals("Laptop lama", asset!!.name)
        assertEquals(12_000_000L, asset.acquisitionCost)
        assertNull(asset.qrCode)
        assertNull(asset.prNumber)
        assertEquals(2, db.openHelper.readableDatabase.version)
        db.close()
        ctx.deleteDatabase(name)
    }
}
