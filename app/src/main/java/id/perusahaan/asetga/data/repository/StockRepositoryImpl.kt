package id.perusahaan.asetga.data.repository

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.dao.ItemRow
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.StockDao
import id.perusahaan.asetga.data.local.dao.TxnRow
import id.perusahaan.asetga.data.local.dao.UnitDao
import id.perusahaan.asetga.data.local.entity.ItemEntity
import id.perusahaan.asetga.data.local.entity.StockTxnEntity
import id.perusahaan.asetga.domain.calc.StockCalc
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.model.StockItem
import id.perusahaan.asetga.domain.model.StockTxn
import id.perusahaan.asetga.domain.model.StockTxnInput
import id.perusahaan.asetga.domain.model.StockTxnType
import id.perusahaan.asetga.domain.model.SyncState
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.StockRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.OperationValidator
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun ItemRow.toDomain() = StockItem(
    id, code, barcode, name, unitId, unitName, storageLocationId, storageLocationName,
    initialStock, minStock, StockCalc.round2(currentStock), notes, isActive,
)

internal fun TxnRow.toDomain() = StockTxn(id, itemId, txnDate, type, qty, reference, note, createdBy, createdAt)

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: StockDao,
    private val unitDao: UnitDao,
    private val locationDao: LocationDao,
    private val session: SessionManager,
    private val audit: AuditLogRepository,
    private val clock: Clock,
) : StockRepository {

    override fun observeItems(query: String, reorderOnly: Boolean, includeInactive: Boolean): Flow<List<StockItem>> =
        dao.observeItems(query.trim(), reorderOnly, includeInactive).map { l -> l.map { it.toDomain() } }

    override fun observeItem(id: String): Flow<StockItem?> = dao.observeItem(id).map { it?.toDomain() }

    override fun observeTxns(itemId: String): Flow<List<StockTxn>> = dao.observeTxns(itemId).map { l -> l.map { it.toDomain() } }

    override fun observeReorderCount(): Flow<Int> = dao.observeReorderCount()

    override suspend fun findByCodeOrBarcode(code: String): StockItem? = dao.findByCodeOrBarcode(code.trim())?.toDomain()

    override suspend fun saveItem(input: ItemInput): AppResult<String> {
        session.denyUnless { it.canEditData }?.let { return it }
        val errors = OperationValidator.validateItem(input).toMutableMap()
        val code = input.code.trim().uppercase()
        val barcode = input.barcode.trimToNull()
        val excludeId = input.id ?: ""
        if (OperationValidator.FIELD_CODE !in errors && dao.countByCode(code, excludeId) > 0) {
            errors[OperationValidator.FIELD_CODE] = "Kode barang sudah dipakai"
        }
        if (barcode != null && dao.countByBarcode(barcode, excludeId) > 0) errors["barcode"] = "Barcode sudah dipakai barang lain"
        if (errors.isNotEmpty()) return AppResult.Failure("Periksa kembali isian", errors)
        if (unitDao.getById(input.unitId!!) == null) return AppResult.Failure("Satuan tidak ditemukan")
        if (input.storageLocationId != null && locationDao.getById(input.storageLocationId) == null) return AppResult.Failure("Lokasi tidak ditemukan")

        val initial = Fmt.parseDecimal(input.initialStock.ifBlank { "0" })!!
        val min = Fmt.parseDecimal(input.minStock.ifBlank { "0" })!!
        val now = clock.millis()
        val old = input.id?.let { dao.getById(it) }
        if (old != null) {
            // Stok awal yang diturunkan tidak boleh membuat stok akhir minus (barang sudah terlanjur keluar).
            val current = dao.getItemRow(old.id)?.currentStock ?: old.initialStock
            val after = StockCalc.round2(current - old.initialStock + StockCalc.round2(initial))
            if (after < 0) {
                return AppResult.Failure(
                    "Periksa kembali isian",
                    mapOf(OperationValidator.FIELD_INITIAL to "Stok akhir menjadi minus (${Fmt.qty(after)}). Minimal ${Fmt.qty(StockCalc.round2(old.initialStock - current))}"),
                )
            }
        }
        val entity = ItemEntity(
            id = old?.id ?: UUID.randomUUID().toString(),
            code = code,
            barcode = barcode,
            name = input.name.trim(),
            unitId = input.unitId,
            initialStock = StockCalc.round2(initial),
            minStock = StockCalc.round2(min),
            storageLocationId = input.storageLocationId,
            notes = input.notes.trimToNull(),
            isActive = old?.isActive ?: true,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            syncState = SyncState.PENDING,
        )
        if (old == null) {
            dao.insertItem(entity)
            audit.log(AuditEntityType.ITEM, entity.id, AuditAction.CREATE, "Barang ${entity.code} ${entity.name} ditambahkan (stok awal ${Fmt.qty(entity.initialStock)})")
        } else {
            dao.updateItem(entity)
            val changes = ChangeSet().apply {
                field("Kode", old.code, entity.code)
                field("Barcode", old.barcode, entity.barcode)
                field("Nama", old.name, entity.name)
                field("Satuan", unitDao.getById(old.unitId)?.name, unitDao.getById(entity.unitId)?.name)
                field("Stok awal", Fmt.qty(old.initialStock), Fmt.qty(entity.initialStock))
                field("Min. stok", Fmt.qty(old.minStock), Fmt.qty(entity.minStock))
                field("Lokasi simpan", old.storageLocationId?.let { locationDao.getById(it)?.name }, entity.storageLocationId?.let { locationDao.getById(it)?.name })
                field("Catatan", old.notes, entity.notes)
            }
            if (!changes.isEmpty()) audit.log(AuditEntityType.ITEM, entity.id, AuditAction.UPDATE, "Barang ${entity.code} diubah", changes.build())
        }
        return AppResult.Success(entity.id)
    }

    override suspend fun deleteItem(id: String): AppResult<Unit> {
        session.denyUnless { it.canManageMaster }?.let { return AppResult.Failure("Hanya Admin GA yang dapat menghapus barang") }
        val item = dao.getById(id) ?: return AppResult.Failure("Barang tidak ditemukan")
        db.withTransaction {
            val txns = dao.deleteTxnsOfItem(id)
            dao.deleteItem(id)
            audit.log(AuditEntityType.ITEM, id, AuditAction.DELETE, "Barang ${item.code} ${item.name} dihapus permanen beserta $txns transaksi")
        }
        return AppResult.Success(Unit)
    }

    override suspend fun recordTxn(input: StockTxnInput): AppResult<Unit> {
        session.denyUnless { it.canEditData }?.let { return it }
        val errors = OperationValidator.validateTxn(input, LocalDate.now(clock))
        if (errors.isNotEmpty()) return AppResult.Failure("Periksa kembali isian", errors)
        val qty = StockCalc.round2(Fmt.parseDecimal(input.qty)!!)

        return db.withTransaction {
            // Stok dicek di dalam transaksi agar dua pengeluaran bersamaan tidak membuat stok minus.
            val item = dao.getItemRow(input.itemId) ?: return@withTransaction AppResult.Failure("Barang tidak ditemukan")
            if (!item.isActive) return@withTransaction AppResult.Failure("Barang nonaktif")
            val current = StockCalc.round2(item.currentStock)
            if (input.type == StockTxnType.OUT && !StockCalc.canTakeOut(current, qty)) {
                return@withTransaction AppResult.Failure(
                    "Stok tidak cukup",
                    mapOf(OperationValidator.FIELD_QTY to "Stok tersedia ${Fmt.qty(current)} ${item.unitName}"),
                )
            }
            val now = clock.millis()
            val entity = StockTxnEntity(
                id = UUID.randomUUID().toString(),
                itemId = item.id,
                txnDate = input.date!!,
                type = input.type,
                qty = qty,
                reference = input.reference.trimToNull(),
                note = input.note.trimToNull(),
                createdByUserId = session.currentUser?.id,
                createdAt = now,
                updatedAt = now,
            )
            dao.insertTxn(entity)
            val after = if (input.type == StockTxnType.IN) current + qty else current - qty
            audit.log(
                AuditEntityType.STOCK, item.id, AuditAction.CREATE,
                "Stok ${input.type.label.lowercase()} ${item.code}: ${Fmt.qty(qty)} ${item.unitName} (stok ${Fmt.qty(current)} → ${Fmt.qty(StockCalc.round2(after))})",
            )
            AppResult.Success(Unit)
        }
    }
}
