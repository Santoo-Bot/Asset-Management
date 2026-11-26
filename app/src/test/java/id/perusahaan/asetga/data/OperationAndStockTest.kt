package id.perusahaan.asetga.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.repository.AuditLogRepositoryImpl
import id.perusahaan.asetga.data.repository.OperationRepositoryImpl
import id.perusahaan.asetga.data.repository.StockRepositoryImpl
import id.perusahaan.asetga.domain.calc.ServiceDueStatus
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.model.MutationInput
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.ServiceInput
import id.perusahaan.asetga.domain.model.StockTxnInput
import id.perusahaan.asetga.domain.model.StockTxnType
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
class OperationAndStockTest {
    private lateinit var db: AppDatabase
    private lateinit var ops: OperationRepositoryImpl
    private lateinit var stock: StockRepositoryImpl
    private val clock = Clock.systemDefaultZone()
    private val session = SessionManager(clock)
    private val today = LocalDate.now()

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        val audit = AuditLogRepositoryImpl(db.auditLogDao(), session, clock)
        ops = OperationRepositoryImpl(
            db, db.assetDao(), db.mutationDao(), db.serviceDao(), db.locationDao(), db.departmentDao(), db.vendorDao(), session, audit, clock,
        )
        stock = StockRepositoryImpl(db, db.stockDao(), db.unitDao(), db.locationDao(), session, audit, clock)
        session.login(User("u1", "staf", "Staf GA", Role.STAFF, hasPin = false, isActive = true))
        db.locationDao().insert(LocationEntity("A", "Gedung A", null, true, 1, 1))
        db.locationDao().insert(LocationEntity("B", "Gedung B", null, true, 1, 1))
        val cat = db.categoryDao().observeAll(false).first().first().id
        db.assetDao().insert(
            AssetEntity(
                id = "as1", code = "IT-2024-001", name = "Laptop", categoryId = cat, acquisitionDate = today.minusYears(1),
                acquisitionCost = 10_000_000, locationId = "A", picName = "Andi", condition = AssetCondition.BAIK,
                status = AssetStatus.DIGUNAKAN, createdAt = 1, updatedAt = 1,
            ),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun `mutasi memperbarui lokasi dan pengguna aset serta riwayat lama-baru`() = runTest {
        val r = ops.recordMutation(MutationInput("as1", toLocationId = "B", toUserLocation = "Budi · Ruang 301"))
        assertTrue(r is AppResult.Success)
        val asset = db.assetDao().getById("as1")!!
        assertEquals("B", asset.locationId)
        assertEquals("Budi · Ruang 301", asset.userLocationDetail)
        val history = ops.observeMutations("as1").first().single()
        assertEquals("Gedung A", history.fromLocation)
        assertEquals("Gedung B", history.toLocation)
        assertEquals(null, history.fromUserLocation)
        assertEquals("Budi · Ruang 301", history.toUserLocation)
        assertEquals(today, history.date)

        val log = db.auditLogDao().observeForEntity(id.perusahaan.asetga.domain.model.AuditEntityType.ASSET, "as1").first()
            .first { it.summary.startsWith("Mutasi") }
        val changes = id.perusahaan.asetga.data.repository.parseChanges(log.changesJson)
        assertEquals(
            listOf(
                id.perusahaan.asetga.domain.model.FieldChange("Lokasi", "Gedung A", "Gedung B"),
                id.perusahaan.asetga.domain.model.FieldChange("Pengguna / detail lokasi", null, "Budi · Ruang 301"),
            ),
            changes,
        )
    }

    @Test fun `mutasi tanpa perubahan ditolak, cukup ganti pengguna diterima`() = runTest {
        val same = ops.recordMutation(MutationInput("as1", toLocationId = "A")) as AppResult.Failure
        assertTrue(OperationValidator.FIELD_TARGET in same.fieldErrors)
        assertTrue(ops.recordMutation(MutationInput("as1", toLocationId = "A", toUserLocation = "Citra")) is AppResult.Success)
        assertEquals("A", db.assetDao().getById("as1")!!.locationId)
        assertEquals("Citra", db.assetDao().getById("as1")!!.userLocationDetail)
    }

    @Test fun `jadwal servis diambil dari catatan terakhir`() = runTest {
        ops.saveService(ServiceInput(assetId = "as1", serviceDate = today.minusMonths(6), description = "Servis 1", nextServiceDate = today.minusDays(3)))
        assertEquals(ServiceDueStatus.OVERDUE, ops.observeSchedules().first().single().status)
        ops.saveService(ServiceInput(assetId = "as1", serviceDate = today, description = "Servis 2", nextServiceDate = today.plusDays(20), cost = 250_000))
        val schedule = ops.observeSchedules().first().single()
        assertEquals(ServiceDueStatus.DUE_SOON, schedule.status)
        assertEquals(today.plusDays(20), schedule.nextServiceDate)
        assertEquals(2, ops.observeServices("as1").first().size)
    }

    @Test fun `stok akhir, reorder, dan keluar tidak boleh melebihi stok`() = runTest {
        val unit = db.unitDao().observeAll(false).first().first().id
        val id = (stock.saveItem(ItemInput(code = "ATK-001", name = "Kertas A4", unitId = unit, initialStock = "10", minStock = "5")) as AppResult.Success).value

        assertTrue(stock.recordTxn(StockTxnInput(id, StockTxnType.IN, "20", today)) is AppResult.Success)
        assertTrue(stock.recordTxn(StockTxnInput(id, StockTxnType.OUT, "24", today)) is AppResult.Success)
        var item = stock.observeItem(id).first()!!
        assertEquals(6.0, item.currentStock, 0.0)
        assertTrue(!item.needsReorder)

        val tooMuch = stock.recordTxn(StockTxnInput(id, StockTxnType.OUT, "7", today)) as AppResult.Failure
        assertTrue(OperationValidator.FIELD_QTY in tooMuch.fieldErrors)

        assertTrue(stock.recordTxn(StockTxnInput(id, StockTxnType.OUT, "1", today)) is AppResult.Success)
        item = stock.observeItem(id).first()!!
        assertEquals(5.0, item.currentStock, 0.0)
        assertTrue(item.needsReorder)
        assertEquals(1, stock.observeReorderCount().first())
        assertEquals(listOf(item.id), stock.observeItems("", reorderOnly = true).first().map { it.id })
        assertEquals(id, stock.findByCodeOrBarcode("atk-001")?.id)
    }

    @Test fun `kode barang ganda ditolak dan viewer tidak bisa transaksi`() = runTest {
        val unit = db.unitDao().observeAll(false).first().first().id
        stock.saveItem(ItemInput(code = "ATK-001", name = "Kertas", unitId = unit))
        val dup = stock.saveItem(ItemInput(code = "atk-001", name = "Lain", unitId = unit)) as AppResult.Failure
        assertTrue(OperationValidator.FIELD_CODE in dup.fieldErrors)
        session.login(User("v", "viewer", "Viewer", Role.VIEWER, hasPin = false, isActive = true))
        val item = stock.findByCodeOrBarcode("ATK-001")!!
        assertTrue(stock.recordTxn(StockTxnInput(item.id, StockTxnType.IN, "1", today)) is AppResult.Failure)
    }
}
