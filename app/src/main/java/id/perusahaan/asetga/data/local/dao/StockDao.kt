package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import id.perusahaan.asetga.data.local.entity.ItemEntity
import id.perusahaan.asetga.data.local.entity.StockTxnEntity
import id.perusahaan.asetga.domain.model.StockTxnType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ItemRow(
    val id: String,
    val code: String,
    val barcode: String?,
    val name: String,
    val unitId: String,
    val unitName: String,
    val storageLocationId: String?,
    val storageLocationName: String?,
    val initialStock: Double,
    val minStock: Double,
    val currentStock: Double,
    val notes: String?,
    val isActive: Boolean,
)

data class TxnRow(
    val id: String,
    val itemId: String,
    val txnDate: LocalDate,
    val type: StockTxnType,
    val qty: Double,
    val reference: String?,
    val note: String?,
    val createdBy: String?,
    val createdAt: Long,
)

/** Stok akhir = stok awal + Σ masuk − Σ keluar (transaksi tidak dibatalkan). */
private const val CURRENT_STOCK = """
    (i.initialStock + COALESCE((SELECT SUM(CASE WHEN t.type = 'IN' THEN t.qty ELSE -t.qty END)
        FROM stock_txns t WHERE t.itemId = i.id AND t.deletedAt IS NULL), 0))
"""

private const val ITEM_SELECT = """
    SELECT i.id, i.code, i.barcode, i.name, i.unitId, u.name AS unitName, i.storageLocationId,
           l.name AS storageLocationName, i.initialStock, i.minStock, $CURRENT_STOCK AS currentStock, i.notes, i.isActive
    FROM items i JOIN units u ON u.id = i.unitId LEFT JOIN locations l ON l.id = i.storageLocationId
"""

@Dao
interface StockDao {
    @Query("SELECT (SELECT COUNT(*) FROM items) + (SELECT COUNT(*) FROM stock_txns)")
    fun observeTick(): Flow<Int>

    @Query("$ITEM_SELECT WHERE i.deletedAt IS NULL AND i.isActive = 1 AND $CURRENT_STOCK <= i.minStock ORDER BY i.name COLLATE NOCASE")
    suspend fun reorderItems(): List<ItemRow>

    @Query(
        "$ITEM_SELECT WHERE i.deletedAt IS NULL AND (:includeInactive OR i.isActive = 1) " +
            "AND (:query = '' OR i.code LIKE '%' || :query || '%' OR i.name LIKE '%' || :query || '%' OR i.barcode = :query) " +
            "AND (:reorderOnly = 0 OR $CURRENT_STOCK <= i.minStock) " +
            "ORDER BY i.name COLLATE NOCASE",
    )
    fun observeItems(query: String, reorderOnly: Boolean, includeInactive: Boolean): Flow<List<ItemRow>>

    @Query("$ITEM_SELECT WHERE i.deletedAt IS NULL AND i.isActive = 1 ORDER BY i.name COLLATE NOCASE")
    suspend fun activeItems(): List<ItemRow>

    @Query("$ITEM_SELECT WHERE i.id = :id")
    fun observeItem(id: String): Flow<ItemRow?>

    @Query("$ITEM_SELECT WHERE i.id = :id")
    suspend fun getItemRow(id: String): ItemRow?

    @Query("$ITEM_SELECT WHERE i.deletedAt IS NULL AND (i.code = :code COLLATE NOCASE OR i.barcode = :code) LIMIT 1")
    suspend fun findByCodeOrBarcode(code: String): ItemRow?

    @Query("SELECT COUNT(*) FROM items i WHERE i.deletedAt IS NULL AND i.isActive = 1 AND $CURRENT_STOCK <= i.minStock")
    fun observeReorderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items i WHERE i.deletedAt IS NULL AND i.isActive = 1 AND $CURRENT_STOCK <= i.minStock")
    suspend fun reorderCount(): Int

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE code = :code COLLATE NOCASE AND id != :excludeId")
    suspend fun countByCode(code: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM items WHERE barcode = :barcode AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByBarcode(barcode: String, excludeId: String): Int

    @Insert
    suspend fun insertItem(entity: ItemEntity)

    @Update
    suspend fun updateItem(entity: ItemEntity)

    @Query("UPDATE items SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)

    @Insert
    suspend fun insertTxn(entity: StockTxnEntity)

    @Query(
        """SELECT t.id, t.itemId, t.txnDate, t.type, t.qty, t.reference, t.note, u.fullName AS createdBy, t.createdAt
           FROM stock_txns t LEFT JOIN users u ON u.id = t.createdByUserId
           WHERE t.itemId = :itemId AND t.deletedAt IS NULL
           ORDER BY t.txnDate DESC, t.createdAt DESC""",
    )
    fun observeTxns(itemId: String): Flow<List<TxnRow>>

    @Query(
        """SELECT t.id, t.itemId, t.txnDate, t.type, t.qty, t.reference, t.note, u.fullName AS createdBy, t.createdAt
           FROM stock_txns t LEFT JOIN users u ON u.id = t.createdByUserId
           WHERE t.deletedAt IS NULL ORDER BY t.txnDate DESC, t.createdAt DESC""",
    )
    suspend fun allTxns(): List<TxnRow>
}
