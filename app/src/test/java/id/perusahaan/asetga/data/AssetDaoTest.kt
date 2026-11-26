package id.perusahaan.asetga.data

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.repository.AssetRepositoryImpl
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.AssetValidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AssetDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: AssetRepositoryImpl
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private lateinit var itCategoryId: String
    private val locId = "loc-1"

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        db.locationDao().insert(LocationEntity(locId, "Kantor Pusat", null, true, 1, 1))
        itCategoryId = db.categoryDao().observeAll(false).first().first { it.codePrefix == "IT" }.id
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        repo = AssetRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.departmentDao(), db.divisionDao(),
            SettingsRepositoryImpl(db.settingsDao()), session, audit, clock,
        )
        session.login(User("u1", "staf", "Staf GA", Role.STAFF, hasPin = false, isActive = true))
    }

    @After fun tearDown() = db.close()

    /** Seperti form: kode kosong diisi usulan otomatis, kode QR = kode aset. */
    private suspend fun input(name: String, date: LocalDate = LocalDate.of(2024, 3, 1), code: String = ""): AssetInput {
        val c = code.ifEmpty { repo.suggestCode(itCategoryId, date.year) }
        return AssetInput(
            name = name, code = c, qrCode = c, categoryId = itCategoryId, locationId = locId,
            acquisitionDate = date, acquisitionCost = 10_000_000,
        )
    }

    private fun entity(id: String, code: String, name: String, condition: AssetCondition = AssetCondition.BAIK, status: AssetStatus = AssetStatus.DIGUNAKAN) =
        AssetEntity(
            id = id, code = code, name = name, categoryId = itCategoryId, acquisitionDate = LocalDate.of(2024, 1, 1),
            acquisitionCost = 1_000, locationId = locId, condition = condition, status = status, createdAt = 1, updatedAt = 1,
        )

    @Test fun `kode otomatis berurutan per kategori dan tahun`() = runTest {
        val a = (repo.save(input("Laptop A")) as AppResult.Success).value
        val b = (repo.save(input("Laptop B")) as AppResult.Success).value
        val c = (repo.save(input("Laptop C", LocalDate.of(2025, 2, 1))) as AppResult.Success).value
        assertEquals("IT-2024-001", a.code)
        assertEquals("IT-2024-002", b.code)
        assertEquals("IT-2025-001", c.code)
    }

    @Test fun `kode aset ganda ditolak`() = runTest {
        repo.save(input("Laptop A", code = "IT-2024-010"))
        val dup = repo.save(input("Laptop B", code = "it-2024-010")) as AppResult.Failure
        assertTrue(AssetValidator.FIELD_CODE in dup.fieldErrors)
        try {
            db.assetDao().insert(entity("x", "IT-2024-010", "Langsung"))
            fail("Unique index seharusnya menolak")
        } catch (expected: SQLiteConstraintException) {
            // OK
        }
    }

    @Test fun `viewer tidak boleh menambah aset`() = runTest {
        session.login(User("v", "viewer", "Viewer", Role.VIEWER, hasPin = false, isActive = true))
        assertTrue(repo.save(input("Laptop")) is AppResult.Failure)
    }

    @Test fun `filter dan pencarian di DAO`() = runTest {
        val dao = db.assetDao()
        dao.insert(entity("1", "IT-2024-001", "Laptop Dell"))
        dao.insert(entity("2", "IT-2024-002", "Printer Epson", condition = AssetCondition.RUSAK))
        dao.insert(entity("3", "IT-2024-003", "Laptop Lama", status = AssetStatus.WRITE_OFF))

        assertEquals(3, dao.count("", null, null, null, null, null, null, false).first()) // default: semua status
        assertEquals(2, dao.count("", null, null, null, null, null, null, true).first()) // pemilih asuransi: tanpa Write Off
        assertEquals(1, dao.count("laptop", null, null, null, null, null, null, true).first())
        assertEquals(1, dao.count("", null, null, "RUSAK", null, null, null, false).first())
        assertEquals(1, dao.count("", null, null, null, "WRITE_OFF", null, null, false).first())

        val page = dao.paging("", null, null, null, null, "NAME", null, null, false)
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
        val names = (page as PagingSource.LoadResult.Page).data.map { it.name }
        assertEquals(listOf("Laptop Dell", "Laptop Lama", "Printer Epson"), names)
    }

    @Test fun `detail menghitung nilai buku dan perubahan tercatat`() = runTest {
        val saved = (repo.save(input("Laptop", LocalDate.now().minusYears(2))) as AppResult.Success).value
        val detail = repo.observeDetail(saved.id).first()!!
        // Kategori IT umur 4 th, residu 0% → setelah 2 th nilai buku 50%.
        assertEquals(5_000_000, detail.depreciation.bookValue)
        assertEquals(2, detail.age.years)

        repo.save(input("Laptop Baru", saved.acquisitionDate).copy(id = saved.id, code = saved.code))
        val logs = db.auditLogDao().observeForEntity(id.perusahaan.asetga.domain.model.AuditEntityType.ASSET, saved.id).first()
        assertEquals(2, logs.size)
    }

    @Test fun `tandai Dihapus Dijual oleh staf`() = runTest {
        val a = (repo.save(input("Printer")) as AppResult.Success).value
        assertTrue(repo.dispose(a.id, LocalDate.now(), 500_000, "Rusak total") is AppResult.Success)
        val e = db.assetDao().getById(a.id)!!
        assertEquals(AssetStatus.WRITE_OFF, e.status)
        assertEquals(500_000L, e.disposalValue)
        assertTrue(repo.dispose(a.id, LocalDate.now(), null, "") is AppResult.Failure) // sudah Write Off
    }

    @Test fun `hapus aset langsung permanen dan hanya admin`() = runTest {
        val a = (repo.save(input("Laptop A")) as AppResult.Success).value
        val b = (repo.save(input("Laptop B")) as AppResult.Success).value

        assertTrue(repo.delete(listOf(a.id), "salah input") is AppResult.Failure) // staf ditolak
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        assertTrue(repo.delete(listOf(a.id), " ") is AppResult.Failure) // alasan wajib
        assertEquals(2, (repo.delete(listOf(a.id, b.id), "data ganda") as AppResult.Success).value.count)

        val dao = db.assetDao()
        assertEquals(0, dao.countAll()) // tidak ada soft delete: baris benar-benar hilang
        assertEquals(null, dao.getById(a.id))
        assertEquals(null, repo.findByCode(a.code))
    }

    @Test fun `field pengadaan, memo, dan kode QR tersimpan dan scan cocok dengan kode QR`() = runTest {
        val a = (
            repo.save(
                input("Laptop").copy(
                    qrCode = "QR-LAMA-0001", userLocationDetail = "Budi · Ruang 301",
                    prNumber = "PR-1", poNumber = "PO-2", grNumber = "GR-3", requester = "Citra",
                    memoNumber = "MEMO/7", memoDate = LocalDate.of(2024, 1, 10), followUp = "Diserahkan", handoverDoc = "BAST/15",
                    accessories = " charger,Casing , charger;  ",
                ),
            ) as AppResult.Success
            ).value
        val saved = repo.get(a.id)!!
        assertEquals("QR-LAMA-0001", saved.qrContent)
        assertEquals("PO-2", saved.poNumber)
        assertEquals(LocalDate.of(2024, 1, 10), saved.memoDate)
        assertEquals("BAST/15", saved.handoverDoc)
        assertEquals("charger, Casing", saved.accessories) // dirapikan, duplikat dibuang
        assertEquals(listOf("charger", "Casing"), saved.accessoryList)
        assertEquals(1, db.assetDao().count("casing", null, null, null, null, null, null, false).first())
        assertEquals(a.id, repo.findByCode("QR-LAMA-0001")?.id)
        assertEquals(a.id, repo.findByCode(a.code)?.id)
        assertEquals(1, db.assetDao().count("PO-2", null, null, null, null, null, null, false).first())

        // Kode QR tidak boleh sama dengan kode/QR aset lain.
        val dupQr = repo.save(input("Lain").copy(qrCode = "qr-lama-0001")) as AppResult.Failure
        assertTrue(AssetValidator.FIELD_QR in dupQr.fieldErrors)
        val qrSameAsOtherCode = repo.save(input("Lain").copy(qrCode = a.code)) as AppResult.Failure
        assertTrue(AssetValidator.FIELD_QR in qrSameAsOtherCode.fieldErrors)
        // Tanpa kode QR, isi QR = kode aset.
        val b = (repo.save(input("Mouse")) as AppResult.Success).value
        assertEquals(b.code, b.qrContent)
    }

    @Test fun `divisi aset mengikuti departemen, termasuk saat departemen dipindah divisi`() = runTest {
        val now = 1L
        db.divisionDao().insert(DivisionEntity("divA", "Operasional", true, now, now))
        db.divisionDao().insert(DivisionEntity("divB", "Komersial", true, now, now))
        db.departmentDao().insert(DepartmentEntity("dep1", "General Affair", true, now, now, divisionId = "divA"))

        // Divisi dipilih "divB" tetapi departemen milik "divA" → yang dipakai divisi departemen.
        val a = (repo.save(input("Laptop").copy(departmentId = "dep1", divisionId = "divB")) as AppResult.Success).value
        assertEquals("divA", a.divisionId)
        assertEquals("Operasional", repo.observeDetail(a.id).first()!!.divisionName)
        // Tanpa departemen, divisi pilihan dipakai.
        val b = (repo.save(input("Meja").copy(divisionId = "divB")) as AppResult.Success).value
        assertEquals("divB", b.divisionId)
        assertEquals(1, db.assetDao().count("", null, null, null, null, "divB", null, false).first())

        // Admin memindah departemen ke divisi lain → aset ikut.
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        val master = id.perusahaan.asetga.data.repository.MasterDataRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(), db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(),
            session, AuditLogRepositoryImpl(db.auditLogDao(), session, clock), clock,
        )
        assertTrue(master.saveDepartment(id.perusahaan.asetga.domain.model.Department("dep1", "General Affair", "divB")) is AppResult.Success)
        assertEquals("divB", db.assetDao().getById(a.id)!!.divisionId)
    }

    @Test fun `kode aset dan kode QR wajib diisi`() = runTest {
        val noCode = repo.save(input("Laptop").copy(code = "", qrCode = "")) as AppResult.Failure
        assertTrue(AssetValidator.FIELD_CODE in noCode.fieldErrors)
        assertTrue(AssetValidator.FIELD_QR in noCode.fieldErrors)
    }

    @Test fun `pilih semua mengikuti filter dan hapus membersihkan riwayat`() = runTest {
        val a = (repo.save(input("Laptop A")) as AppResult.Success).value
        val b = (repo.save(input("Laptop B")) as AppResult.Success).value
        val c = (repo.save(input("Printer C")) as AppResult.Success).value
        // Pilih semua = semua hasil filter.
        val selected = repo.filteredIds(id.perusahaan.asetga.domain.model.AssetFilter(query = "laptop"))
        assertEquals(setOf(a.id, b.id), selected.toSet())

        db.locationDao().insert(LocationEntity("loc-2", "Gudang", null, true, 1, 1))
        val ops = id.perusahaan.asetga.data.repository.OperationRepositoryImpl(
            db, db.assetDao(), db.mutationDao(), db.serviceDao(), db.locationDao(), db.departmentDao(), db.vendorDao(), session,
            AuditLogRepositoryImpl(db.auditLogDao(), session, clock), clock,
        )
        assertTrue(ops.recordMutation(id.perusahaan.asetga.domain.model.MutationInput(a.id, toLocationId = "loc-2")) is AppResult.Success)

        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        assertEquals(2, (repo.delete(selected, "salah input") as AppResult.Success).value.count)
        assertEquals(null, db.assetDao().getById(a.id))
        assertTrue(ops.observeMutations(a.id).first().isEmpty())
        assertTrue(db.assetDao().getById(c.id) != null)
    }
}
