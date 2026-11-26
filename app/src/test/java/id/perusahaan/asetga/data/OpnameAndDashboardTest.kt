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
        opname = OpnameRepositoryImpl(db, db.opnameDao(), db.assetDao(), db.locationDao(), session, audit, clock)
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

    @Test fun `sesi opname menghitung ditemukan, tidak ditemukan, dan tidak terdaftar`() = runTest {
        val id = (opname.start("A") as AppResult.Success).value
        var s = opname.observeSession(id).first()!!
        assertEquals(3, s.expected) // aset Write Off tidak ikut

        assertTrue(opname.scan(id, "it-2024-001") is OpnameScanOutcome.Found)
        assertTrue(opname.scan(id, "IT-2024-001") is OpnameScanOutcome.AlreadyScanned)
        assertTrue(opname.scan(id, "IT-2024-004") is OpnameScanOutcome.OtherLocation)
        assertTrue(opname.scan(id, "TIDAK-ADA") is OpnameScanOutcome.Unknown)
        assertTrue(opname.finish(id, "Ruang server terkunci") is AppResult.Success)
        assertTrue(opname.finish(id, "Diselesaikan dua kali") is AppResult.Failure)

        s = opname.observeSession(id).first()!!
        assertEquals(1, s.found)
        assertEquals(2, s.missing)
        assertEquals(2, s.unregistered)
        assertTrue(s.isFinished)
        assertTrue(opname.scan(id, "IT-2024-003") is OpnameScanOutcome.Error)
        val results = opname.observeItems(id).first().groupingBy { it.result }.eachCount()
        assertEquals(mapOf(AuditResult.DITEMUKAN to 1, AuditResult.TIDAK_DITEMUKAN to 2, AuditResult.TAK_TERDAFTAR to 2), results)
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
