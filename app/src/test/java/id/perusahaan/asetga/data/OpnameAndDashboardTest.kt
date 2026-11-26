package id.perusahaan.asetga.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.DashboardRepositoryImpl
import id.perusahaan.asetga.data.repository.OpnameRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.OpnameScanOutcome
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OpnameAndDashboardTest {
    private lateinit var db: AppDatabase
    private lateinit var opname: OpnameRepositoryImpl
    private lateinit var dashboard: DashboardRepositoryImpl
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private val today = LocalDate.now()

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        opname = OpnameRepositoryImpl(db, db.opnameDao(), db.assetDao(), session, audit, clock)
        dashboard = DashboardRepositoryImpl(db.assetDao(), db.serviceDao(), db.stockDao(), db.insuranceDao(), SettingsRepositoryImpl(db.settingsDao()), clock)
        session.login(User("u1", "staf", "Staf GA", Role.STAFF, hasPin = false, isActive = true))
        db.locationDao().insert(LocationEntity("A", "Gedung A", null, true, 1, 1))
        db.locationDao().insert(LocationEntity("B", "Gedung B", null, true, 1, 1))
        val it = db.categoryDao().observeAll(false).first().first { c -> c.codePrefix == "IT" }.id
        fun asset(id: String, loc: String, cond: AssetCondition = AssetCondition.BAIK, status: AssetStatus = AssetStatus.DIGUNAKAN, warranty: LocalDate? = null) =
            AssetEntity(
                id = id, code = "IT-2024-$id", name = "Aset $id", categoryId = it, acquisitionDate = today.minusYears(2),
                acquisitionCost = 4_000_000, locationId = loc, condition = cond, status = status, warrantyUntil = warranty, createdAt = 1, updatedAt = 1,
            )
        db.assetDao().insertAll(
            listOf(
                asset("001", "A"),
                asset("002", "A", cond = AssetCondition.RUSAK, warranty = today.plusDays(10)),
                asset("003", "A"),
                asset("004", "B"),
                asset("005", "A", status = AssetStatus.WRITE_OFF),
            ),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun `alur opname - buat sesi, tambah aset manual dan upload, cek fisik, selesai`() = runTest {
        assertTrue(opname.create("  ", today, null) is AppResult.Failure)
        val id = (opname.create("Opname Gedung A", today, null) as AppResult.Success).value

        // Tambah manual: Write Off dilewati, duplikat tidak digandakan.
        val added = (opname.addAssets(id, listOf("001", "002", "005", "001")) as AppResult.Success).value
        assertEquals(2, added.added)
        assertEquals(1, added.skippedWriteOff)

        // Upload: kode aset atau kode QR; yang sudah ada ditandai.
        val preview = opname.previewImport(id, listOf(listOf("Kode Aset / Kode QR*"), listOf("IT-2024-002"), listOf("it-2024-003"), listOf("IT-2024-005"), listOf("X-1")))
        assertEquals(2, preview.valid.size)
        assertEquals(1, preview.valid.count { it.alreadyInSession })
        assertEquals(2, preview.invalid.size) // Write Off & tidak dikenal
        assertEquals(1, (opname.commitImport(id, preview) as AppResult.Success).value.added)
        assertEquals(setOf("001", "002", "003"), opname.observeAssetIds(id).first())

        var s = opname.observeSession(id).first()!!
        assertEquals(3, s.total)
        assertEquals(0, s.found)

        // Cek fisik: scan, scan ulang, aset di luar daftar, kode tidak dikenal, tandai manual.
        assertTrue(opname.scan(id, "it-2024-001") is OpnameScanOutcome.Found)
        assertTrue(opname.scan(id, "IT-2024-001") is OpnameScanOutcome.AlreadyScanned)
        assertTrue(opname.scan(id, "IT-2024-004") is OpnameScanOutcome.NotInList)
        assertTrue(opname.scan(id, "TIDAK-ADA") is OpnameScanOutcome.Unknown)
        val item002 = opname.observeItems(id).first().first { it.assetId == "002" }
        assertTrue(opname.updateItem(id, item002.id, found = true, condition = AssetCondition.RUSAK, note = "Layar retak") is AppResult.Success)
        val found001 = opname.observeItems(id).first().first { it.assetId == "001" }
        assertEquals(AssetCondition.BAIK, found001.condition) // kondisi diambil dari data aset saat scan

        s = opname.observeSession(id).first()!!
        assertEquals(2, s.found)
        assertEquals(1, s.damaged)
        assertEquals(1, s.notFound)
        assertEquals(2, s.outside)
        assertEquals(66, s.foundPercent)

        // Keluarkan aset dari sesi.
        val unknown = opname.observeItems(id).first().first { it.assetId == null }
        assertTrue(opname.removeItem(id, unknown.id) is AppResult.Success)

        assertTrue(opname.finish(id, "Ruang server terkunci") is AppResult.Success)
        s = opname.observeSession(id).first()!!
        assertTrue(s.isFinished)
        assertEquals("Ruang server terkunci", s.note)
        // Sesi selesai tidak bisa diubah.
        assertTrue(opname.scan(id, "IT-2024-003") is OpnameScanOutcome.Error)
        assertTrue(opname.addAssets(id, listOf("004")) is AppResult.Failure)
        val results = opname.observeItems(id).first().groupingBy { it.result }.eachCount()
        assertEquals(mapOf(AuditResult.DITEMUKAN to 2, AuditResult.TIDAK_DITEMUKAN to 1, AuditResult.TAK_TERDAFTAR to 1), results)
        assertEquals("Layar retak", opname.observeItems(id).first().first { it.assetId == "002" }.note)
    }

    @Test fun `sesi kosong tidak bisa diselesaikan dan hapus sesi khusus admin`() = runTest {
        val id = (opname.create("Kosong", today, null) as AppResult.Success).value
        assertTrue(opname.finish(id, "") is AppResult.Failure)
        opname.addAssets(id, listOf("001"))
        assertTrue(opname.delete(id) is AppResult.Failure) // staf
        session.login(User("u2", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        assertTrue(opname.delete(id) is AppResult.Success)
        assertEquals(null, opname.observeSession(id).first())
        assertEquals(0, db.opnameDao().items(id).size)
    }

    @Test fun `ringkasan dashboard`() = runTest {
        val s = dashboard.observeSummary().first()
        assertEquals(4, s.totalAssets) // tanpa Write Off
        assertEquals(16_000_000, s.totalCost)
        assertEquals(8_000_000, s.totalBookValue) // IT 4 th, 2 th berjalan → 50%
        assertEquals(1, s.damaged)
        assertEquals(1, s.warrantyExpiring)
        assertEquals(4, s.byCategory.single().value)
        assertEquals(1, s.byCondition.first { it.label == "Rusak" }.value)
        assertTrue(s.actions.any { it.title.startsWith("IT-2024-002") })
        val reminder = dashboard.reminderSummary()
        assertEquals(1, reminder.warrantyExpiring)
    }
}
