package id.perusahaan.asetga.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.Migrations
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Aturan proyek: migrasi aman dijalankan ulang. Di sini migrasi dijalankan lagi pada database yang
 * sudah berskema terbaru (perubahannya sudah ada); tidak boleh error dan tidak boleh mengubah data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationRerunTest {
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before fun setUp() {
        db = inMemoryDb()
        sql = db.openHelper.writableDatabase
    }

    @After fun tearDown() = db.close()

    private fun count(table: String): Int = sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun columns(table: String): Set<String> =
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            val idx = c.getColumnIndex("name")
            buildSet { while (c.moveToNext()) add(c.getString(idx)) }
        }

    @Test fun `migrasi dijalankan ulang tidak error dan tidak mengubah data`() = runTest {
        db.divisionDao().insert(DivisionEntity("d1", "Umum", createdAt = 1, updatedAt = 1))
        db.divisionDao().insert(DivisionEntity("d2", "Keuangan", createdAt = 1, updatedAt = 1))
        db.locationDao().insert(LocationEntity("L", "Kantor", null, true, 1, 1))
        val cat = db.categoryDao().observeAll(false).first().first().id
        // Teks divisi lama "Keuangan" tetap tersimpan, tetapi pengguna sudah memindahkan aset ke divisi "Umum".
        db.assetDao().insert(
            AssetEntity(
                id = "a1", code = "IT-1", name = "Laptop", categoryId = cat, acquisitionDate = LocalDate.of(2024, 1, 1), acquisitionCost = 1,
                locationId = "L", condition = AssetCondition.BAIK, status = AssetStatus.DIGUNAKAN, createdAt = 1, updatedAt = 1,
                divisionId = "d1", legacyDivisionText = "Keuangan",
            ),
        )
        val tables = listOf("assets", "divisions", "departments", "asset_mutations", "audit_sessions", "audit_items", "asset_statuses", "insurance_types")
        val before = tables.associateWith(::count)
        val columnsBefore = tables.associateWith(::columns)

        listOf(
            Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4, Migrations.MIGRATION_4_5,
            Migrations.MIGRATION_5_6, Migrations.MIGRATION_10_11, Migrations.MIGRATION_11_12, Migrations.MIGRATION_12_13,
        ).forEach { it.migrate(sql) }
        // Dua kali berturut-turut pun tetap aman.
        listOf(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_10_11).forEach { it.migrate(sql) }

        assertEquals(before, tables.associateWith(::count))
        assertEquals(columnsBefore, tables.associateWith(::columns))
        assertEquals("d1", db.assetDao().getById("a1")!!.divisionId)
        assertTrue("name" in columns("audit_sessions"))
    }
}
