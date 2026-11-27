package id.perusahaan.asetga.data

import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.repository.AssetRepositoryImpl
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.LoginThrottle
import id.perusahaan.asetga.data.repository.MasterDataRepositoryImpl
import id.perusahaan.asetga.data.repository.OpnameRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.data.repository.UserRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.security.PasswordHasher
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

/** Audit log yang selalu gagal: perubahan data harus ikut dibatalkan (tidak ada perubahan tanpa catatan). */
private class FailingAudit : AuditLogRepository {
    override suspend fun log(entityType: AuditEntityType, entityId: String, action: AuditAction, summary: String, changes: Map<String, Pair<String?, String?>>) {
        throw IllegalStateException("audit gagal")
    }
    override suspend fun logBatch(entityType: AuditEntityType, action: AuditAction, entries: List<Pair<String, String>>) {
        throw IllegalStateException("audit gagal")
    }
    override fun paging(entityType: AuditEntityType?, query: String): Flow<PagingData<AuditLogEntry>> = emptyFlow()
    override fun observeForEntity(entityType: AuditEntityType, entityId: String): Flow<List<AuditLogEntry>> = emptyFlow()
}

@RunWith(AndroidJUnit4::class)
class AuditAtomicityTest {
    private lateinit var db: AppDatabase
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private val failing = FailingAudit()
    private val today = LocalDate.now()

    @Before fun setUp() {
        db = inMemoryDb()
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
    }

    @After fun tearDown() = db.close()

    private fun users(audit: AuditLogRepository) =
        UserRepositoryImpl(db, db.userDao(), PasswordHasher(iterations = 1_000), session, audit, clock, LoginThrottle(db.settingsDao(), clock))

    private fun master(audit: AuditLogRepository) = MasterDataRepositoryImpl(
        db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(),
        db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(), session, audit, clock,
    )

    private fun opname(audit: AuditLogRepository) = OpnameRepositoryImpl(db, db.opnameDao(), db.assetDao(), session, audit, clock)

    private fun assets(audit: AuditLogRepository) = AssetRepositoryImpl(
        db, db.assetDao(), db.categoryDao(), db.locationDao(), db.departmentDao(), db.divisionDao(),
        SettingsRepositoryImpl(db.settingsDao()), session, audit, clock,
    )

    @Test fun `pengguna tidak jadi dibuat atau dihapus bila audit gagal`() = runTest {
        val good = users(AuditLogRepositoryImpl(db.auditLogDao(), session, clock))
        // Pengguna pertama wajib Admin; baru setelah itu Staf bisa ditambahkan.
        assertTrue(good.createUser("admin", "Admin GA", Role.ADMIN, "admin1234") is AppResult.Success)
        val staf = (good.createUser("staf", "Staf GA", Role.STAFF, "staf12345") as AppResult.Success).value
        assertEquals(2, db.userDao().count())

        assertTrue(runCatching { users(failing).createUser("baru", "Orang Baru", Role.VIEWER, "baru12345") }.isFailure)
        assertEquals(2, db.userDao().count())

        assertTrue(runCatching { users(failing).deleteUser(staf.id) }.isFailure)
        assertNotNull(db.userDao().getById(staf.id))
    }

    @Test fun `master data tidak berubah bila audit gagal`() = runTest {
        val before = db.categoryDao().observeAll(true).first().size
        val r = runCatching { master(failing).saveCategory(Category("", "Kategori Uji", "UJI", 4, 10.0)) }
        assertTrue(r.isFailure)
        assertEquals(before, db.categoryDao().observeAll(true).first().size)
    }

    @Test fun `sesi opname tidak dibuat atau dihapus bila audit gagal`() = runTest {
        assertTrue(runCatching { opname(failing).create("Sesi", today, null) }.isFailure)
        assertEquals(0, db.opnameDao().observeSessions().first().size)

        val id = (opname(AuditLogRepositoryImpl(db.auditLogDao(), session, clock)).create("Sesi", today, null) as AppResult.Success).value
        assertTrue(runCatching { opname(failing).delete(id) }.isFailure)
        assertEquals(1, db.opnameDao().observeSessions().first().size)
    }

    @Test fun `write off dibatalkan bila audit gagal`() = runTest {
        db.locationDao().insert(LocationEntity("L", "Kantor", null, true, 1, 1))
        val cat = db.categoryDao().observeAll(false).first().first().id
        db.assetDao().insert(
            AssetEntity(
                id = "a1", code = "IT-1", name = "Laptop", categoryId = cat, acquisitionDate = today.minusYears(1), acquisitionCost = 1,
                locationId = "L", condition = AssetCondition.BAIK, status = AssetStatus.DIGUNAKAN, createdAt = 1, updatedAt = 1,
            ),
        )
        assertTrue(runCatching { assets(failing).dispose("a1", today, null, "") }.isFailure)
        assertEquals(AssetStatus.DIGUNAKAN, db.assetDao().getById("a1")!!.status)
    }
}
