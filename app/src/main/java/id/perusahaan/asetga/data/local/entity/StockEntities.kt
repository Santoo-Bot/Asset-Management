package id.perusahaan.asetga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.perusahaan.asetga.domain.model.StockTxnType
import id.perusahaan.asetga.domain.model.SyncState
import java.time.LocalDate

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["storageLocationId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["barcode"]),
        Index(value = ["name"]),
        Index(value = ["unitId"]),
        Index(value = ["storageLocationId"]),
    ],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val code: String,
    val barcode: String? = null,
    val name: String,
    val unitId: String,
    val initialStock: Double,
    val minStock: Double,
    val storageLocationId: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** Stok akhir dihitung: stok awal + Σ masuk − Σ keluar (transaksi yang tidak dibatalkan). */
@Entity(
    tableName = "stock_txns",
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["itemId"]), Index(value = ["txnDate"])],
)
data class StockTxnEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val txnDate: LocalDate,
    val type: StockTxnType,
    /** Selalu positif; arah ditentukan oleh [type]. */
    val qty: Double,
    val reference: String? = null,
    val note: String? = null,
    val createdByUserId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)
