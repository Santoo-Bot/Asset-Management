package id.perusahaan.asetga.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.MasterDataRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.data.repository.UserRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.security.CredentialRules
import id.perusahaan.asetga.domain.security.PasswordHasher
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.MasterValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private lateinit var audit: AuditLogRepositoryImpl
    private lateinit var users: UserRepositoryImpl
    private lateinit var master: MasterDataRepositoryImpl

    @Before fun setUp() {
        db = inMemoryDb()
        audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        users = UserRepositoryImpl(db.userDao(), PasswordHasher(iterations = 1_000), session, audit, clock)
        master = MasterDataRepositoryImpl(
            db.categoryDao(), db.locationDao(), db.departmentDao(), db.vendorDao(), db.unitDao(), session, audit, clock,
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun createAdmin(): User {
        val r = users.createUser("admin", "Admin GA", Role.ADMIN, "admin1234")
        val admin = (r as AppResult.Success).value
        session.login(admin)
        return admin
    }

    @Test fun `pengguna pertama wajib admin`() = runTest {
        val r = users.createUser("staf", "Staf", Role.STAFF, "staf12345")
        assertTrue(r is AppResult.Failure)
    }

    @Test fun `login berhasil dan gagal`() = runTest {
        createAdmin()
        assertTrue(users.authenticate("ADMIN ", "admin1234") is AppResult.Success)
        assertTrue(users.authenticate("admin", "salah1234") is AppResult.Failure)
        assertTrue(users.authenticate("tidakada", "admin1234") is AppResult.Failure)
        val stored = db.userDao().getByUsername("admin")!!.passwordHash
        assertTrue(!stored.contains("admin1234"))
    }

    @Test fun `username ganda ditolak`() = runTest {
        createAdmin()
        val r = users.createUser("admin", "Lain", Role.STAFF, "lain12345") as AppResult.Failure
        assertTrue(CredentialRules.FIELD_USERNAME in r.fieldErrors)
    }

    @Test fun `admin aktif terakhir tidak bisa diturunkan`() = runTest {
        val admin = createAdmin()
        val staf = (users.createUser("staf", "Staf", Role.STAFF, "staf12345") as AppResult.Success).value
        val demoteSelf = users.updateUser(admin.id, admin.fullName, Role.STAFF, true)
        assertTrue(demoteSelf is AppResult.Failure)
        // Setelah ada admin kedua, penurunan diperbolehkan.
        assertTrue(users.updateUser(staf.id, staf.fullName, Role.ADMIN, true) is AppResult.Success)
        assertTrue(users.updateUser(admin.id, admin.fullName, Role.STAFF, true) is AppResult.Success)
    }

    @Test fun `PIN disimpan sebagai hash dan diverifikasi`() = runTest {
        val admin = createAdmin()
        assertTrue(users.setPin(admin.id, "2580") is AppResult.Success)
        assertTrue(users.verifyPin(admin.id, "2580"))
        assertTrue(!users.verifyPin(admin.id, "0000"))
        assertTrue(db.userDao().getById(admin.id)!!.pinHash!!.startsWith("pbkdf2-sha256"))
    }

    @Test fun `viewer dan staf tidak boleh mengubah master data`() = runTest {
        createAdmin()
        val viewer = (users.createUser("lihat", "Viewer", Role.VIEWER, "lihat1234") as AppResult.Success).value
        val staf = (users.createUser("staf", "Staf", Role.STAFF, "staf12345") as AppResult.Success).value
        session.login(viewer)
        assertTrue(master.saveLocation(Location("", "Ruang Rapat", null)) is AppResult.Failure)
        session.login(staf)
        assertTrue(master.saveLocation(Location("", "Ruang Rapat", null)) is AppResult.Failure)
    }

    @Test fun `admin menambah dan mengubah kategori tercatat di audit log`() = runTest {
        createAdmin()
        val saved = (master.saveCategory(Category("", "Peralatan Medis", "med", 5, 10.0)) as AppResult.Success).value
        assertEquals("MED", saved.codePrefix)

        val dup = master.saveCategory(Category("", "Lain", "IT", 4, null)) as AppResult.Failure
        assertTrue(MasterValidator.FIELD_PREFIX in dup.fieldErrors)

        assertTrue(master.saveCategory(saved.copy(usefulLifeYears = 6)) is AppResult.Success)
        assertTrue(master.setActive(MasterType.CATEGORY, saved.id, false) is AppResult.Success)

        val logs = audit.observeForEntity(AuditEntityType.MASTER, saved.id).first()
        assertEquals(3, logs.size)
        assertTrue(logs.all { it.userName == "Admin GA" })
        assertEquals(false, master.observeCategories(includeInactive = true).first().first { it.id == saved.id }.isActive)
    }

    @Test fun `pengaturan default terbaca dan bisa diubah`() = runTest {
        val repo = SettingsRepositoryImpl(db.settingsDao())
        val s = repo.current()
        assertEquals(8, s.reminderHour)
        assertEquals(0.0, s.defaultResidualPercent, 0.0)
        assertTrue(s.appLockEnabled)
        assertTrue(repo.setDefaultResidualPercent(150.0) is AppResult.Failure)
        assertTrue(repo.setDefaultResidualPercent(5.0) is AppResult.Success)
        assertEquals(5.0, repo.current().defaultResidualPercent, 0.0)
    }
}
