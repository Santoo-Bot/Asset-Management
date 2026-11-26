package id.perusahaan.asetga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.SyncState
import java.time.LocalDate

/** Sesi stock opname: daftar aset yang diperiksa dipilih sendiri (manual / upload), tidak terikat satu lokasi. */
@Entity(
    tableName = "audit_sessions",
    indices = [Index(value = ["startedAt"])],
)
data class AuditSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val opnameDate: LocalDate,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val startedByUserId: String?,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "audit_items",
    foreignKeys = [
        ForeignKey(entity = AuditSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["assetId"]), Index(value = ["sessionId", "scannedCode"])],
)
data class AuditItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val assetId: String?,
    val scannedCode: String,
    val result: AuditResult,
    val scannedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING,
    /** Kondisi fisik saat dicek (hanya untuk aset yang ditemukan). */
    val condition: AssetCondition? = null,
    val note: String? = null,
)

/** Catatan siapa mengubah apa dan kapan. Hanya ditambah, tidak pernah diubah. */
@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["timestamp"]), Index(value = ["entityType", "entityId"])],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val userId: String?,
    val userName: String,
    val entityType: AuditEntityType,
    val entityId: String,
    val action: AuditAction,
    val summary: String,
    /** JSON {"field": ["lama", "baru"]} bila ada. */
    val changesJson: String? = null,
    val syncState: SyncState = SyncState.PENDING,
)
