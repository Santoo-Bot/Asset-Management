package id.perusahaan.asetga.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.file.DataResetManager
import id.perusahaan.asetga.data.file.ResetScope
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.repository.AssetRepositoryImpl
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.InsuranceRepositoryImpl
import id.perusahaan.asetga.data.repository.MasterDataRepositoryImpl
import id.perusahaan.asetga.data.repository.OperationRepositoryImpl
import id.perusahaan.asetga.data.repository.OpnameRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.data.repository.StockRepositoryImpl
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetFilter
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.InsuranceType
import id.perusahaan.asetga.domain.model.Insurer
import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.MutationInput
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.ServiceInput
import id.perusahaan.asetga.domain.model.StockTxnInput
import id.perusahaan.asetga.domain.model.StockTxnType
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.model.Vendor
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

/** Semua jalur hapus dijalankan pada data yang saling terkait; tidak boleh ada error constraint. */
@RunWith(AndroidJUnit4::class)
class DeleteFlowsTest {
    private lateinit var db: AppDatabase
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private val today = LocalDate.now()

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    @Test fun `alur hapus lengkap pada data yang saling terkait`() = runTest {
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        val master = MasterDataRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(),
            db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(), session, audit, clock,
        )
        val assets = AssetRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.departmentDao(), db.divisionDao(),
            SettingsRepositoryImpl(db.settingsDao()), session, audit, clock,
        )
        val ops = OperationRepositoryImpl(db, db.assetDao(), db.mutationDao(), db.serviceDao(), db.locationDao(), db.departmentDao(), db.vendorDao(), session, audit, clock)
        val stock = StockRepositoryImpl(db, db.stockDao(), db.unitDao(), db.locationDao(), session, audit, clock)
        val opname = OpnameRepositoryImpl(db, db.opnameDao(), db.assetDao(), db.locationDao(), session, audit, clock)
        val insurance = InsuranceRepositoryImpl(db, db.insuranceDao(), db.insuranceTypeDao(), db.insurerDao(), db.assetDao(), session, audit, clock)

        fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Success)?.value ?: error("Gagal: $r")

        val locA = ok(master.saveLocation(Location("", "Gedung A", null)))
        val locB = ok(master.saveLocation(Location("", "Gedung B", null)))
        val div = ok(master.saveDivision(Division("", "Operasional")))
        val dept = ok(master.saveDepartment(Department("", "GA", div.id)))
        val vendor = ok(master.saveVendor(Vendor("", "PT Servis", null, null, null)))
        val type = ok(master.saveInsuranceType(InsuranceType("", "All Risk")))
        val insurer = ok(master.saveInsurer(Insurer("", "PT Asuransi")))
        val cat = master.observeCategories().first().first { it.codePrefix == "IT" }
        val unit = master.observeUnits().first().first()

        val ids = (1..30).map { n ->
            val code = assets.suggestCode(cat.id, 2024)
            ok(
                assets.save(
                    AssetInput(code = code, qrCode = code, name = "Aset $n", categoryId = cat.id, locationId = locA.id, departmentId = dept.id,
                        acquisitionDate = LocalDate.of(2024, 1, 1), acquisitionCost = 1_000_000),
                ),
            ).id
        }
        ok(ops.recordMutation(MutationInput(ids[0], toLocationId = locB.id)))
        ok(ops.saveService(ServiceInput(assetId = ids[1], serviceDate = today, description = "Servis", vendorId = vendor.id, nextServiceDate = today.plusDays(10))))
        val policy = ok(insurance.savePolicy(PolicyInput(policyNumber = "P-1", typeId = type.id, insurerId = insurer.id)))
        ok(insurance.addAssets(policy, ids.take(10)))
        val session1 = ok(opname.start(locA.id))
        opname.scan(session1, ids[2].let { db.assetDao().getById(it)!!.code })
        val item = ok(stock.saveItem(ItemInput(code = "ATK-1", name = "Kertas", unitId = unit.id, storageLocationId = locA.id, initialStock = "5")))
        ok(stock.recordTxn(StockTxnInput(item, StockTxnType.OUT, "1", today)))

        // Master yang dipakai ditolak dengan pesan (bukan crash).
        assertTrue((master.delete(MasterType.LOCATION, locA.id) as AppResult.Failure).message.contains("aset"))
        assertTrue(master.delete(MasterType.DIVISION, div.id) is AppResult.Failure)

        // Pilih semua → hapus (langsung permanen).
        val all = assets.filteredIds(AssetFilter())
        assertEquals(30, all.size)
        assertEquals(30, ok(assets.delete(all, "data uji")).count)
        assertEquals(0, db.assetDao().countAll())
        assertEquals(0, insurance.observePolicy(policy).first()!!.assetCount)
        // Riwayat audit tetap tersimpan setelah hapus permanen.
        assertTrue(db.auditLogDao().observeForEntity(id.perusahaan.asetga.domain.model.AuditEntityType.ASSET, ids[5]).first().size >= 2) // dibuat + dihapus

        // Setelah aset hilang, master yang hanya dipakai aset bisa dihapus; yang masih dipakai stok/opname/polis tetap ditolak.
        assertTrue(master.delete(MasterType.DEPARTMENT, dept.id) is AppResult.Success)
        assertTrue(master.delete(MasterType.DIVISION, div.id) is AppResult.Success)
        assertTrue(master.delete(MasterType.LOCATION, locB.id) is AppResult.Success)
        assertTrue(master.delete(MasterType.LOCATION, locA.id) is AppResult.Failure) // stok & opname
        assertTrue(master.delete(MasterType.INSURANCE_TYPE, type.id) is AppResult.Failure) // polis

        // Hapus seluruh data: operasional lalu semua.
        val reset = DataResetManager(ApplicationProvider.getApplicationContext(), db, audit, session)
        assertTrue(reset.reset(ResetScope.OPERATIONAL) is AppResult.Success)
        assertTrue(master.delete(MasterType.LOCATION, locA.id) is AppResult.Success)
        assertTrue(master.delete(MasterType.INSURANCE_TYPE, type.id) is AppResult.Success)
        assertTrue(reset.reset(ResetScope.EVERYTHING) is AppResult.Success)
        assertEquals(0, db.userDao().count())
    }

    @Test fun `departemen dan divisi bisa dihapus walau ada riwayat mutasi lama yang mencatat departemen`() = runTest {
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        val master = MasterDataRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(),
            db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(), session, audit, clock,
        )
        val assets = AssetRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.departmentDao(), db.divisionDao(),
            SettingsRepositoryImpl(db.settingsDao()), session, audit, clock,
        )
        val ops = OperationRepositoryImpl(db, db.assetDao(), db.mutationDao(), db.serviceDao(), db.locationDao(), db.departmentDao(), db.vendorDao(), session, audit, clock)
        fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Success)?.value ?: error("Gagal: $r")

        val locA = ok(master.saveLocation(Location("", "Gedung A", null)))
        val locB = ok(master.saveLocation(Location("", "Gedung B", null)))
        val div = ok(master.saveDivision(Division("", "Operasional")))
        val dept = ok(master.saveDepartment(Department("", "GA", div.id)))
        val cat = master.observeCategories().first().first { it.codePrefix == "IT" }
        fun input(code: String, deptId: String?) = AssetInput(
            code = code, qrCode = code, name = code, categoryId = cat.id, locationId = locA.id, departmentId = deptId,
            acquisitionDate = LocalDate.of(2024, 1, 1), acquisitionCost = 1,
        )
        val inDept = ok(assets.save(input("IT-1", dept.id)))
        val other = ok(assets.save(input("IT-2", null)))
        ok(ops.recordMutation(MutationInput(other.id, toLocationId = locB.id)))
        // Data lama (sebelum perbaikan) yang mencatat departemen di riwayat mutasi aset lain.
        db.openHelper.writableDatabase.execSQL("UPDATE asset_mutations SET fromDepartmentId = '${dept.id}', toDepartmentId = '${dept.id}'")

        // Aset yang memakai departemen menahan; setelah aset dihapus, departemen & divisi bisa dihapus.
        assertTrue((master.delete(MasterType.DEPARTMENT, dept.id) as AppResult.Failure).message.contains("1 aset"))
        ok(assets.delete(listOf(inDept.id), "salah input"))
        assertTrue((master.delete(MasterType.DIVISION, div.id) as AppResult.Failure).message.contains("1 departemen"))
        assertTrue(master.delete(MasterType.DEPARTMENT, dept.id) is AppResult.Success)
        assertTrue(master.delete(MasterType.DIVISION, div.id) is AppResult.Success)
        // Riwayat mutasi aset lain tetap ada.
        assertEquals(1, ops.observeMutations(other.id).first().size)
    }

    @Test fun `pindahkan aset lalu hapus departemen dan divisi`() = runTest {
        session.login(User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true))
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        val master = MasterDataRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(),
            db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(), session, audit, clock,
        )
        val assets = AssetRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.departmentDao(), db.divisionDao(),
            SettingsRepositoryImpl(db.settingsDao()), session, audit, clock,
        )
        fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Success)?.value ?: error("Gagal: $r")
        val loc = ok(master.saveLocation(Location("", "Gedung", null)))
        val ops = ok(master.saveDivision(Division("", "Operasional")))
        val kom = ok(master.saveDivision(Division("", "Komersial")))
        val ga = ok(master.saveDepartment(Department("", "GA", ops.id)))
        val it = ok(master.saveDepartment(Department("", "IT", ops.id)))
        val mkt = ok(master.saveDepartment(Department("", "Pemasaran", kom.id)))
        val cat = master.observeCategories().first().first { c -> c.codePrefix == "IT" }
        val ids = (1..5).map { n ->
            ok(
                assets.save(
                    AssetInput(code = "A-$n", qrCode = "A-$n", name = "A$n", categoryId = cat.id, locationId = loc.id,
                        departmentId = if (n <= 3) mkt.id else ga.id, acquisitionDate = LocalDate.of(2024, 1, 1), acquisitionCost = 1),
                ),
            ).id
        }
        // Lihat aset: filter departemen.
        assertEquals(3, assets.filteredIds(AssetFilter(departmentId = mkt.id)).size)

        // Departemen Pemasaran (3 aset) → pindah ke GA lalu hapus; divisi aset ikut menjadi Operasional.
        assertEquals(3, ok(master.moveUsersAndDelete(MasterType.DEPARTMENT, mkt.id, ga.id)))
        assertEquals(null, db.departmentDao().getById(mkt.id))
        assertTrue(ids.all { db.assetDao().getById(it)!!.departmentId == ga.id })
        assertTrue(ids.all { db.assetDao().getById(it)!!.divisionId == ops.id })

        // Divisi Operasional (2 departemen, 5 aset) → pindah ke Komersial lalu hapus.
        assertEquals(5, ok(master.moveUsersAndDelete(MasterType.DIVISION, ops.id, kom.id)))
        assertEquals(null, db.divisionDao().getById(ops.id))
        assertEquals(kom.id, db.departmentDao().getById(it.id)!!.divisionId)
        assertTrue(ids.all { db.assetDao().getById(it)!!.divisionId == kom.id })
        assertTrue(master.moveUsersAndDelete(MasterType.DIVISION, kom.id, kom.id) is AppResult.Failure)
    }
}
