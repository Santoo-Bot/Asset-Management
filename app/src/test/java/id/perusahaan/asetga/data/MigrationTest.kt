package id.perusahaan.asetga.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.DefaultData
import id.perusahaan.asetga.data.local.Migrations
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mensimulasikan database yang sudah terpasang di HP (skema lama), lalu membukanya dengan aplikasi baru.
 * Room memvalidasi seluruh skema setelah migrasi; bila migrasi tidak cocok, test ini gagal.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migrasi-test.db"

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

    private val v1AssetIndices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_assets_code` ON `assets` (`code`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_name` ON `assets` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_categoryId` ON `assets` (`categoryId`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_locationId` ON `assets` (`locationId`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_departmentId` ON `assets` (`departmentId`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_condition` ON `assets` (`condition`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_status` ON `assets` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_assets_serialNumber` ON `assets` (`serialNumber`)",
    )

    /** Tambahan kolom v2 persis seperti MIGRATION_1_2. */
    private val v2AssetAdditions = listOf(
        "qrCode TEXT", "userLocationDetail TEXT", "division TEXT", "prNumber TEXT", "poNumber TEXT",
        "grNumber TEXT", "requester TEXT", "memoNumber TEXT", "memoDate INTEGER", "followUp TEXT", "handoverDoc TEXT",
    ).map { "ALTER TABLE `assets` ADD COLUMN $it" } + "CREATE UNIQUE INDEX IF NOT EXISTS `index_assets_qrCode` ON `assets` (`qrCode`)"

    private val v1Departments = listOf(
        "CREATE TABLE IF NOT EXISTS `departments` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_departments_name` ON `departments` (`name`)",
    )

    private val v1Mutations = listOf(
        "CREATE TABLE IF NOT EXISTS `asset_mutations` (`id` TEXT NOT NULL, `assetId` TEXT NOT NULL, `mutationDate` INTEGER NOT NULL, " +
            "`fromLocationId` TEXT, `toLocationId` TEXT, `fromDepartmentId` TEXT, `toDepartmentId` TEXT, `fromPic` TEXT, `toPic` TEXT, " +
            "`reason` TEXT NOT NULL, `approvedBy` TEXT, `createdByUserId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`deletedAt` INTEGER, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_asset_mutations_assetId` ON `asset_mutations` (`assetId`)",
        "CREATE INDEX IF NOT EXISTS `index_asset_mutations_mutationDate` ON `asset_mutations` (`mutationDate`)",
    )

    /** Tabel stock opname versi 1–10 (sesi terikat lokasi). */
    private val v10Audit = listOf(
        "DROP TABLE audit_items",
        "DROP TABLE audit_sessions",
        "CREATE TABLE IF NOT EXISTS `audit_sessions` (`id` TEXT NOT NULL, `locationId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `finishedAt` INTEGER, " +
            "`startedByUserId` TEXT, `note` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`locationId`) REFERENCES `locations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_audit_sessions_locationId` ON `audit_sessions` (`locationId`)",
        "CREATE INDEX IF NOT EXISTS `index_audit_sessions_startedAt` ON `audit_sessions` (`startedAt`)",
        "CREATE TABLE IF NOT EXISTS `audit_items` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `assetId` TEXT, `scannedCode` TEXT NOT NULL, " +
            "`result` TEXT NOT NULL, `scannedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`sessionId`) REFERENCES `audit_sessions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
            "FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        "CREATE INDEX IF NOT EXISTS `index_audit_items_sessionId` ON `audit_items` (`sessionId`)",
        "CREATE INDEX IF NOT EXISTS `index_audit_items_assetId` ON `audit_items` (`assetId`)",
        "CREATE INDEX IF NOT EXISTS `index_audit_items_sessionId_scannedCode` ON `audit_items` (`sessionId`, `scannedCode`)",
    )

    @After fun cleanUp() {
        ctx.deleteDatabase(name)
    }

    /** Buat database versi lama: tabel lain mengikuti skema terbaru, tabel yang berubah dikembalikan ke bentuk lama. */
    private fun legacyDb(version: Int, insertSql: (categoryId: String) -> List<String>) {
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).addCallback(DefaultData.callback).build().apply {
            openHelper.writableDatabase
            close()
        }
        val raw = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        val categoryId = raw.rawQuery("SELECT id FROM categories LIMIT 1", null).use { it.moveToFirst(); it.getString(0) }
        raw.execSQL("DROP TABLE assets")
        raw.execSQL("DROP TABLE departments")
        raw.execSQL("DROP TABLE divisions")
        raw.execSQL("DROP TABLE insurance_items")
        raw.execSQL("DROP TABLE insurance_policies")
        raw.execSQL("DROP TABLE insurers")
        raw.execSQL("DROP TABLE insurance_types")
        raw.execSQL("DROP TABLE asset_mutations")
        v10Audit.forEach(raw::execSQL)
        v1Departments.forEach(raw::execSQL)
        raw.execSQL(v1Assets)
        v1AssetIndices.forEach(raw::execSQL)
        if (version >= 2) v2AssetAdditions.forEach(raw::execSQL)
        v1Mutations.forEach(raw::execSQL)
        raw.execSQL("INSERT INTO locations (id, name, address, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('loc', 'Kantor', NULL, 1, 1, 1, NULL, 'PENDING')")
        insertSql(categoryId).forEach(raw::execSQL)
        raw.version = version
        raw.close()
    }

    private fun open(): AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*Migrations.ALL).build()

    private val assetCols = "id, code, name, categoryId, acquisitionDate, acquisitionCost, locationId, condition, status, createdAt, updatedAt, syncState"

    @Test fun `migrasi 1 ke 3 mempertahankan data aset`() {
        legacyDb(1) { cat ->
            listOf("INSERT INTO assets ($assetCols) VALUES ('a1', 'IT-2024-001', 'Laptop lama', '$cat', 19723, 12000000, 'loc', 'BAIK', 'AKTIF', 1, 1, 'PENDING')")
        }
        val db = open()
        val asset = runBlocking { db.assetDao().getById("a1") }
        assertNotNull(asset)
        assertEquals("Laptop lama", asset!!.name)
        assertEquals(12_000_000L, asset.acquisitionCost)
        assertNull(asset.qrCode)
        assertNull(asset.divisionId)
        assertEquals(13, db.openHelper.readableDatabase.version)
        assertEquals(AssetStatus.DIGUNAKAN, asset.status)
        db.close()
    }

    @Test fun `migrasi 2 ke 3 mengubah teks divisi menjadi master divisi`() {
        legacyDb(2) { cat ->
            listOf(
                "INSERT INTO departments (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('dep', 'GA', 1, 1, 1, NULL, 'PENDING')",
                // Data nonaktif sisa versi lama: 'dep-off' tidak dipakai, lokasi 'loc' dipakai aset tetapi nonaktif.
                "INSERT INTO departments (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('dep-off', 'Lama', 0, 1, 1, NULL, 'PENDING')",
                "INSERT INTO locations (id, name, address, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('loc-off', 'Gudang lama', NULL, 0, 1, 1, NULL, 'PENDING')",
                "UPDATE locations SET isActive = 0 WHERE id = 'loc'",
                "INSERT INTO assets ($assetCols, division, departmentId) VALUES ('a1', 'IT-1', 'A', '$cat', 19723, 1, 'loc', 'BAIK', 'AKTIF', 1, 1, 'PENDING', 'Operasional', 'dep')",
                "INSERT INTO assets ($assetCols, division) VALUES ('a2', 'IT-2', 'B', '$cat', 19723, 1, 'loc', 'BAIK', 'AKTIF', 1, 1, 'PENDING', ' operasional ')",
                "INSERT INTO assets ($assetCols, division) VALUES ('a3', 'IT-3', 'C', '$cat', 19723, 1, 'loc', 'RUSAK_BERAT', 'DIHAPUS_DIJUAL', 1, 1, 'PENDING', 'Keuangan')",
                "INSERT INTO assets ($assetCols) VALUES ('a4', 'IT-4', 'D', '$cat', 19723, 1, 'loc', 'CUKUP', 'DISIMPAN', 1, 1, 'PENDING')",
                "INSERT INTO assets ($assetCols) VALUES ('a5', 'IT-5', 'E', '$cat', 19723, 1, 'loc', 'RUSAK_RINGAN', 'DIPINJAM', 1, 1, 'PENDING')",
                "INSERT INTO asset_mutations (id, assetId, mutationDate, fromLocationId, toLocationId, reason, createdAt, updatedAt, syncState) " +
                    "VALUES ('m1', 'a1', 19723, 'loc', 'loc', 'Pindah', 1, 1, 'PENDING')",
            )
        }
        val db = open()
        runBlocking {
            val divisions = db.divisionDao().observeAll(true).first()
            assertEquals(2, divisions.size) // "Operasional" & " operasional " jadi satu
            val a1 = db.assetDao().getById("a1")!!
            val a2 = db.assetDao().getById("a2")!!
            assertNotNull(a1.divisionId)
            assertEquals(a1.divisionId, a2.divisionId)
            assertEquals("Keuangan", divisions.first { it.id == db.assetDao().getById("a3")!!.divisionId }.name)
            assertNull(db.assetDao().getById("a4")!!.divisionId)
            assertNull(db.departmentDao().getById("dep")!!.divisionId)

            // v4: kondisi & status lama dipetakan ke nilai baru.
            suspend fun cs(id: String) = db.assetDao().getById(id)!!.let { it.condition to it.status }
            assertEquals(AssetCondition.BAIK to AssetStatus.DIGUNAKAN, cs("a1"))
            assertEquals(AssetCondition.RUSAK to AssetStatus.WRITE_OFF, cs("a3"))
            assertEquals(AssetCondition.BAIK to AssetStatus.TERSEDIA, cs("a4"))
            assertEquals(AssetCondition.RUSAK to AssetStatus.DIGUNAKAN, cs("a5"))

            // v5: riwayat mutasi lama tetap ada, kolom pengguna / detail lokasi kosong.
            val m = db.mutationDao().observeForAsset("a1").first().single()
            assertEquals("Pindah", m.reason)
            assertNull(m.toUserLocation)

            // v7: data master nonaktif yang tidak dipakai dihapus, yang dipakai diaktifkan kembali.
            assertNull(db.departmentDao().getById("dep-off"))
            assertNull(db.locationDao().getById("loc-off"))
            assertEquals(true, db.locationDao().getById("loc")!!.isActive)
            assertEquals(true, db.departmentDao().getById("dep")!!.isActive)
        }
        db.close()
    }

    @Test fun `migrasi 7 ke 9 menghapus polis terhapus dan memulihkan aset terhapus`() {
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).addCallback(DefaultData.callback).build().apply { openHelper.writableDatabase; close() }
        val raw = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        val cat = raw.rawQuery("SELECT id FROM categories LIMIT 1", null).use { it.moveToFirst(); it.getString(0) }
        listOf(
            "INSERT INTO locations (id, name, address, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('loc', 'Kantor', NULL, 1, 1, 1, NULL, 'PENDING')",
            "INSERT INTO assets ($assetCols, deletedAt) VALUES ('a1', 'IT-1', 'Terhapus', '$cat', 19723, 1, 'loc', 'BAIK', 'DIGUNAKAN', 1, 1, 'PENDING', 5)",
            "INSERT INTO assets ($assetCols) VALUES ('a2', 'IT-2', 'Aktif', '$cat', 19723, 1, 'loc', 'BAIK', 'DIGUNAKAN', 1, 1, 'PENDING')",
            "INSERT INTO insurance_types (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('t', 'All Risk', 1, 1, 1, NULL, 'PENDING')",
            "INSERT INTO insurers (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('i', 'PT A', 1, 1, 1, NULL, 'PENDING')",
            "INSERT INTO insurance_policies (id, policyNumber, typeId, insurerId, startDate, endDate, createdAt, updatedAt, deletedAt, syncState) " +
                "VALUES ('p-del', 'LAMA', 't', 'i', 19000, 19365, 1, 1, 9, 'PENDING')",
            "INSERT INTO insurance_policies (id, policyNumber, typeId, insurerId, startDate, endDate, createdAt, updatedAt, deletedAt, syncState) " +
                "VALUES ('p-ok', 'AKTIF', 't', 'i', 19000, 19365, 1, 1, NULL, 'PENDING')",
            "INSERT INTO insurance_items (id, policyId, assetId, createdAt, updatedAt, syncState) VALUES ('x1', 'p-del', 'a2', 1, 1, 'PENDING')",
            "INSERT INTO insurance_items (id, policyId, assetId, createdAt, updatedAt, syncState) VALUES ('x2', 'p-ok', 'a2', 1, 1, 'PENDING')",
            "INSERT INTO users (id, username, fullName, role, passwordHash, pinHash, isActive, createdAt, updatedAt, deletedAt, syncState) " +
                "VALUES ('u-off', 'lama', 'Lama', 'STAFF', 'h', NULL, 0, 1, 1, NULL, 'PENDING')",
            "INSERT INTO users (id, username, fullName, role, passwordHash, pinHash, isActive, createdAt, updatedAt, deletedAt, syncState) " +
                "VALUES ('u-on', 'aktif', 'Aktif', 'ADMIN', 'h', NULL, 1, 1, 1, NULL, 'PENDING')",
        ).forEach(raw::execSQL)
        v10Audit.forEach(raw::execSQL)
        raw.version = 7
        raw.close()

        val db = open()
        runBlocking {
            assertNull(db.insuranceDao().getPolicy("p-del"))
            assertNotNull(db.insuranceDao().getPolicy("p-ok"))
            assertEquals(listOf("a2"), db.insuranceDao().assetIds("p-ok"))
            assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM insurance_items").use { it.moveToFirst(); it.getInt(0) })
            assertNull(db.assetDao().getById("a1")!!.deletedAt) // v9: aset terhapus dipulihkan menjadi aktif
            assertNotNull(db.assetDao().getById("a2"))
            assertNull(db.userDao().getById("u-off")) // akun nonaktif dihapus
            assertNotNull(db.userDao().getById("u-on"))
        }
        db.close()
    }

    @Test fun `migrasi 10 ke 11 mempertahankan sesi opname lama`() {
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).addCallback(DefaultData.callback).build().apply { openHelper.writableDatabase; close() }
        val raw = SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        val cat = raw.rawQuery("SELECT id FROM categories LIMIT 1", null).use { it.moveToFirst(); it.getString(0) }
        v10Audit.forEach(raw::execSQL)
        val startedAt = 1_735_700_000_000L // 1 Jan 2025
        listOf(
            "INSERT INTO locations (id, name, address, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES ('loc', 'Kantor', NULL, 1, 1, 1, NULL, 'PENDING')",
            "INSERT INTO assets ($assetCols) VALUES ('a1', 'IT-1', 'Laptop', '$cat', 19723, 1, 'loc', 'BAIK', 'DIGUNAKAN', 1, 1, 'PENDING')",
            "INSERT INTO audit_sessions (id, locationId, startedAt, finishedAt, startedByUserId, note, createdAt, updatedAt, deletedAt, syncState) " +
                "VALUES ('s1', 'loc', $startedAt, $startedAt, NULL, 'Lama', 1, 1, NULL, 'PENDING')",
            "INSERT INTO audit_items (id, sessionId, assetId, scannedCode, result, scannedAt, createdAt, updatedAt, syncState) " +
                "VALUES ('i1', 's1', 'a1', 'IT-1', 'DITEMUKAN', $startedAt, 1, 1, 'PENDING')",
        ).forEach(raw::execSQL)
        raw.version = 10
        raw.close()

        val db = open()
        runBlocking {
            val s = db.opnameDao().getSession("s1")!!
            assertEquals("Stock opname Kantor", s.name)
            assertEquals(java.time.LocalDate.of(2025, 1, 1), s.opnameDate)
            assertEquals(1, s.total)
            assertEquals(1, s.found)
            assertEquals("Lama", s.note)
            val item = db.opnameDao().items("s1").single()
            assertEquals("Kantor", item.locationName)
            assertNull(item.condition)
        }
        assertEquals(13, db.openHelper.readableDatabase.version)
        db.close()
    }
}
