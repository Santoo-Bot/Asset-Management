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
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
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
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(), db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(), session, audit, clock,
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

    @Test fun `departemen wajib punya divisi dan divisi berisi departemen aktif tidak bisa dinonaktifkan`() = runTest {
        createAdmin()
        val noDivision = master.saveDepartment(Department("", "Keuangan")) as AppResult.Failure
        assertTrue(MasterValidator.FIELD_DIVISION in noDivision.fieldErrors)

        val div = (master.saveDivision(Division("", "Keuangan & SDM")) as AppResult.Success).value
        val dept = (master.saveDepartment(Department("", "Keuangan", div.id)) as AppResult.Success).value
        assertEquals("Keuangan & SDM", master.observeDepartments().first().single().divisionName)
        assertTrue(master.saveDivision(Division("", "keuangan & sdm")) is AppResult.Failure) // nama ganda

        assertTrue(master.setActive(MasterType.DIVISION, div.id, false) is AppResult.Failure)
        assertTrue(master.setActive(MasterType.DEPARTMENT, dept.id, false) is AppResult.Success)
        assertTrue(master.setActive(MasterType.DIVISION, div.id, false) is AppResult.Success)
    }

    @Test fun `master yang belum dipakai bisa dihapus, yang dipakai ditolak dengan rincian`() = runTest {
        createAdmin()
        val loc = (master.saveLocation(Location("", "Gudang Lama", null)) as AppResult.Success).value
        val used = (master.saveLocation(Location("", "Kantor Pusat", null)) as AppResult.Success).value
        val cat = master.observeCategories().first().first { it.codePrefix == "IT" }
        db.assetDao().insert(
            id.perusahaan.asetga.data.local.entity.AssetEntity(
                id = "a1", code = "IT-2024-001", name = "Laptop", categoryId = cat.id, acquisitionDate = java.time.LocalDate.of(2024, 1, 1),
                acquisitionCost = 1, locationId = used.id, condition = id.perusahaan.asetga.domain.model.AssetCondition.BAIK,
                status = id.perusahaan.asetga.domain.model.AssetStatus.DIGUNAKAN, createdAt = 1, updatedAt = 1,
            ),
        )

        assertTrue(master.delete(MasterType.LOCATION, loc.id) is AppResult.Success)
        assertTrue(master.observeLocations(includeInactive = true).first().none { it.id == loc.id })

        val blocked = master.delete(MasterType.LOCATION, used.id) as AppResult.Failure
        assertTrue(blocked.message, blocked.message.contains("1 aset"))
        val blockedCat = master.delete(MasterType.CATEGORY, cat.id) as AppResult.Failure
        assertTrue(blockedCat.message.contains("1 aset"))

        // Kategori bawaan yang belum dipakai boleh dihapus; prefix-nya bisa dipakai lagi.
        val furniture = master.observeCategories().first().first { it.codePrefix == "FUR" }
        assertTrue(master.delete(MasterType.CATEGORY, furniture.id) is AppResult.Success)
        assertTrue(master.saveCategory(Category("", "Mebel", "FUR", 8, null)) is AppResult.Success)

        val logs = audit.observeForEntity(AuditEntityType.MASTER, loc.id).first()
        assertTrue(logs.any { it.action == id.perusahaan.asetga.domain.model.AuditAction.DELETE })
    }

    @Test fun `staf tidak boleh menghapus master`() = runTest {
        createAdmin()
        val loc = (master.saveLocation(Location("", "Gudang", null)) as AppResult.Success).value
        val staf = (users.createUser("staf", "Staf", Role.STAFF, "staf12345") as AppResult.Success).value
        session.login(staf)
        assertTrue(master.delete(MasterType.LOCATION, loc.id) is AppResult.Failure)
    }
}
