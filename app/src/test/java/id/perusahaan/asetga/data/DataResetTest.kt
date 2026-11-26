package id.perusahaan.asetga.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.file.DataResetManager
import id.perusahaan.asetga.data.file.ResetScope
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.UserEntity
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.session.SessionState
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
class DataResetTest {
    private lateinit var db: AppDatabase
    private lateinit var reset: DataResetManager
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        reset = DataResetManager(ApplicationProvider.getApplicationContext(), db, AuditLogRepositoryImpl(db.auditLogDao(), session, clock), session)
        db.userDao().insert(UserEntity("adm", "admin", "Admin GA", Role.ADMIN, "hash-uji", createdAt = 1, updatedAt = 1))
        db.locationDao().insert(LocationEntity("L", "Kantor", null, true, 1, 1))
        val cat = db.categoryDao().observeAll(false).first().first().id
        db.assetDao().insert(
            AssetEntity(
                id = "a1", code = "IT-1", name = "Laptop", categoryId = cat, acquisitionDate = LocalDate.of(2024, 1, 1), acquisitionCost = 1,
                locationId = "L", condition = AssetCondition.BAIK, status = AssetStatus.DIGUNAKAN, createdAt = 1, updatedAt = 1,
            ),
        )
        db.settingsDao().put(AppSettingEntity("debug_seed_assets_v1", "1"))
        db.settingsDao().put(AppSettingEntity("company_name", "PT Contoh"))
    }

    @After fun tearDown() = db.close()

    @Test fun `staf tidak boleh menghapus seluruh data`() = runTest {
        session.login(User("s", "staf", "Staf", Role.STAFF, hasPin = false, isActive = true))
        assertTrue(reset.reset(ResetScope.OPERATIONAL) is AppResult.Failure)
        assertEquals(1, db.assetDao().countAll())
    }

    @Test fun `data operasional dihapus, pengguna dan master tetap`() = runTest {
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        assertTrue(reset.reset(ResetScope.OPERATIONAL) is AppResult.Success)
        assertEquals(0, db.assetDao().countAll())
        assertEquals(1, db.userDao().count())
        assertEquals(1, db.locationDao().observeAll(false).first().size)
        assertEquals("PT Contoh", db.settingsDao().get("company_name"))
        assertTrue(session.state.value is SessionState.Active)
    }

    @Test fun `hapus semua mengembalikan aplikasi seperti baru`() = runTest {
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        assertTrue(reset.reset(ResetScope.EVERYTHING) is AppResult.Success)
        assertEquals(0, db.assetDao().countAll())
        assertEquals(0, db.userDao().count())
        assertTrue(db.locationDao().observeAll(true).first().isEmpty())
        assertEquals(7, db.categoryDao().observeAll(false).first().size) // kategori bawaan dibuat ulang
        assertEquals("8", db.settingsDao().get("reminder_hour"))
        assertEquals("", db.settingsDao().get("company_name"))
        assertEquals("1", db.settingsDao().get("debug_seed_assets_v1")) // data contoh tidak terisi ulang
        assertEquals(SessionState.LoggedOut, session.state.value)
    }
}
