package id.perusahaan.asetga.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.DashboardRepositoryImpl
import id.perusahaan.asetga.data.repository.InsuranceRepositoryImpl
import id.perusahaan.asetga.data.repository.MasterDataRepositoryImpl
import id.perusahaan.asetga.data.repository.SettingsRepositoryImpl
import id.perusahaan.asetga.domain.calc.PolicyStatus
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.InsuranceType
import id.perusahaan.asetga.domain.model.Insurer
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.OperationValidator
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
class InsuranceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: InsuranceRepositoryImpl
    private lateinit var master: MasterDataRepositoryImpl
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private val today = LocalDate.now()
    private lateinit var typeId: String
    private lateinit var insurerId: String
    private val admin = User("adm", "admin", "Admin GA", Role.ADMIN, hasPin = false, isActive = true)
    private val staff = User("stf", "staf", "Staf GA", Role.STAFF, hasPin = false, isActive = true)

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        repo = InsuranceRepositoryImpl(db, db.insuranceDao(), db.insuranceTypeDao(), db.insurerDao(), db.assetDao(), session, audit, clock)
        master = MasterDataRepositoryImpl(
            db, db.assetDao(), db.categoryDao(), db.locationDao(), db.divisionDao(), db.departmentDao(), db.vendorDao(), db.unitDao(),
            db.masterUsageDao(), db.insuranceTypeDao(), db.insurerDao(), session, audit, clock,
        )
        session.login(admin)
        typeId = (master.saveInsuranceType(InsuranceType("", "All Risk")) as AppResult.Success).value.id
        insurerId = (master.saveInsurer(Insurer("", "PT Asuransi Contoh", "021-5550000", "klaim@contoh.co.id")) as AppResult.Success).value.id
        db.locationDao().insert(LocationEntity("L", "Kantor Pusat", null, true, 1, 1))
        val cat = db.categoryDao().observeAll(false).first().first().id
        fun asset(id: String, code: String, qr: String? = null, status: AssetStatus = AssetStatus.DIGUNAKAN) = AssetEntity(
            id = id, code = code, name = "Aset $id", categoryId = cat, acquisitionDate = today.minusYears(1), acquisitionCost = 10_000_000,
            locationId = "L", condition = AssetCondition.BAIK, status = status, qrCode = qr, createdAt = 1, updatedAt = 1,
        )
        db.assetDao().insertAll(
            listOf(asset("a1", "IT-1"), asset("a2", "IT-2", qr = "QR-LAMA-2"), asset("a3", "IT-3"), asset("a4", "IT-4", status = AssetStatus.WRITE_OFF)),
        )
        session.login(staff)
    }

    @After fun tearDown() = db.close()

    private suspend fun newPolicy(number: String = "POL-001", end: LocalDate = today.plusMonths(6)): String =
        (repo.savePolicy(PolicyInput(policyNumber = number, typeId = typeId, insurerId = insurerId, startDate = today.minusMonths(6), endDate = end, premium = 2_500_000)) as AppResult.Success).value

    @Test fun `buat polis dengan validasi dan nomor unik`() = runTest {
        val id = newPolicy()
        val p = repo.observePolicy(id).first()!!
        assertEquals("All Risk", p.typeName)
        assertEquals("PT Asuransi Contoh", p.insurerName)
        assertEquals(PolicyStatus.ACTIVE, p.status)
        val dup = repo.savePolicy(PolicyInput(policyNumber = "pol-001", typeId = typeId, insurerId = insurerId)) as AppResult.Failure
        assertTrue(OperationValidator.FIELD_POLICY_NUMBER in dup.fieldErrors)
    }

    @Test fun `tambah aset manual, scan kode QR, tanpa duplikat`() = runTest {
        val id = newPolicy()
        val r = (repo.addAssets(id, listOf("a1", "a3", "a1")) as AppResult.Success).value
        assertEquals(2, r.added)
        assertEquals(1, (repo.addAssets(id, listOf("a1", "a2")) as AppResult.Success).value.alreadyInPolicy)
        assertTrue((repo.addAssetByCode(id, "IT-3") as AppResult.Success).value.contains("sudah ada"))
        assertTrue(repo.addAssetByCode(id, "IT-4") is AppResult.Failure) // Write Off
        assertTrue(repo.addAssetByCode(id, "TIDAK-ADA") is AppResult.Failure)

        val p = repo.observePolicy(id).first()!!
        assertEquals(3, p.assetCount)
        assertEquals(30_000_000, p.itemsAcquisitionCost)
        assertEquals(listOf(id), repo.observePoliciesForAsset("a2").first().map { it.id })
    }

    @Test fun `upload file dengan kode aset atau kode QR`() = runTest {
        val id = newPolicy()
        repo.addAssets(id, listOf("a1"))
        val preview = repo.previewImport(
            id,
            listOf(
                listOf("Kode Aset / Kode QR*", "Nilai Pertanggungan"),
                listOf("IT-1", "9.000.000"),   // sudah ada → nilai diperbarui
                listOf("QR-LAMA-2", "12000000"), // kode QR
                listOf("IT-2", ""),            // aset sama dengan baris sebelumnya
                listOf("IT-4", ""),            // Write Off
                listOf("XX-99", ""),           // tidak dikenal
            ),
        )
        assertEquals(2, preview.valid.size)
        assertEquals(3, preview.invalid.size)
        assertTrue(preview.valid.first().alreadyInPolicy)
        assertTrue(preview.invalid.any { it.errors.single().contains("lebih dari sekali") })
        assertTrue(preview.invalid.any { it.errors.single().contains("Write Off") })
        assertTrue(preview.invalid.any { it.errors.single().contains("tidak terdaftar") })

        val r = (repo.commitImport(id, preview) as AppResult.Success).value
        assertEquals(1, r.added)
        val items = repo.observeInsuredAssets(id).first().associateBy { it.assetId }
        assertEquals(9_000_000L, items["a1"]!!.sumInsured)
        assertEquals(12_000_000L, items["a2"]!!.sumInsured)
        assertEquals(21_000_000, repo.observePolicy(id).first()!!.itemsSumInsured)
    }

    @Test fun `keluarkan aset, ubah nilai, hapus polis hanya admin`() = runTest {
        val id = newPolicy()
        repo.addAssets(id, listOf("a1", "a2"))
        assertTrue(repo.setSumInsured(id, "a1", 5_000_000) is AppResult.Success)
        assertTrue(repo.removeAsset(id, "a2") is AppResult.Success)
        assertEquals(listOf("a1"), repo.observeInsuredAssets(id).first().map { it.assetId })

        assertTrue(repo.deletePolicy(id) is AppResult.Failure) // staf
        session.login(admin)
        assertTrue(repo.deletePolicy(id) is AppResult.Success)
        assertEquals(null, repo.observePolicy(id).first())
        assertTrue(repo.observePolicies().first().isEmpty())
    }

    @Test fun `master tipe dan perusahaan yang dipakai polis tidak bisa dihapus`() = runTest {
        newPolicy()
        session.login(admin)
        val r = master.delete(MasterType.INSURANCE_TYPE, typeId) as AppResult.Failure
        assertTrue(r.message, r.message.contains("polis POL-001"))
        assertTrue(master.delete(MasterType.INSURER, insurerId) is AppResult.Failure)
        val unused = (master.saveInsuranceType(InsuranceType("", "Kendaraan")) as AppResult.Success).value
        assertTrue(master.delete(MasterType.INSURANCE_TYPE, unused.id) is AppResult.Success)
    }

    @Test fun `polis segera berakhir muncul di dashboard dan pengingat`() = runTest {
        newPolicy("POL-A", end = today.plusDays(10))
        newPolicy("POL-B", end = today.plusMonths(5))
        val dashboard = DashboardRepositoryImpl(db.assetDao(), db.serviceDao(), db.stockDao(), db.insuranceDao(), SettingsRepositoryImpl(db.settingsDao()), clock)
        val s = dashboard.observeSummary().first()
        assertEquals(1, s.policiesExpiring)
        assertTrue(s.actions.any { it.title.startsWith("POL-A") })
        assertEquals(1, dashboard.reminderSummary().policiesExpiring)
        assertEquals(PolicyStatus.EXPIRING, repo.observePolicies().first().first { it.policyNumber == "POL-A" }.status)
    }

    @Test fun `polis yang sudah dihapus tidak menghalangi hapus perusahaan dan tipe asuransi`() = runTest {
        val id = newPolicy()
        repo.addAssets(id, listOf("a1"))
        session.login(admin)
        assertTrue(master.delete(MasterType.INSURER, insurerId) is AppResult.Failure)
        assertTrue(repo.deletePolicy(id) is AppResult.Success)
        assertTrue(master.delete(MasterType.INSURER, insurerId) is AppResult.Success)
        assertTrue(master.delete(MasterType.INSURANCE_TYPE, typeId) is AppResult.Success)
        assertTrue(master.observeInsurers(includeInactive = true).first().isEmpty())
        assertTrue(db.assetDao().getById("a1") != null) // aset tidak ikut terhapus
    }
}
