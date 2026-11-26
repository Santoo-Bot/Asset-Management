package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.perusahaan.asetga.data.local.entity.AuditItemEntity
import id.perusahaan.asetga.data.local.entity.AuditSessionEntity
import id.perusahaan.asetga.domain.model.AuditResult
import kotlinx.coroutines.flow.Flow

data class OpnameSessionRow(
    val id: String,
    val locationId: String,
    val locationName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val startedBy: String?,
    val note: String?,
    val expected: Int,
    val found: Int,
    val unregistered: Int,
)

data class OpnameItemRow(
    val id: String,
    val assetId: String?,
    val scannedCode: String,
    val result: AuditResult,
    val scannedAt: Long?,
    val assetCode: String?,
    val assetName: String?,
    val assetLocation: String?,
)

private const val SESSION_SELECT = """
    SELECT s.id, s.locationId, l.name AS locationName, s.startedAt, s.finishedAt, u.fullName AS startedBy, s.note,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result != 'TAK_TERDAFTAR') AS expected,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result = 'DITEMUKAN') AS found,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result = 'TAK_TERDAFTAR') AS unregistered
    FROM audit_sessions s JOIN locations l ON l.id = s.locationId LEFT JOIN users u ON u.id = s.startedByUserId
"""

@Dao
interface OpnameDao {
    @Insert
    suspend fun insertSession(entity: AuditSessionEntity)

    @Insert
    suspend fun insertItems(items: List<AuditItemEntity>)

    @Insert
    suspend fun insertItem(item: AuditItemEntity)

    @Query("$SESSION_SELECT WHERE s.deletedAt IS NULL ORDER BY s.startedAt DESC")
    fun observeSessions(): Flow<List<OpnameSessionRow>>

    @Query("$SESSION_SELECT WHERE s.id = :id")
    fun observeSession(id: String): Flow<OpnameSessionRow?>

    @Query("$SESSION_SELECT WHERE s.id = :id")
    suspend fun getSession(id: String): OpnameSessionRow?

    @Query(
        """SELECT i.id, i.assetId, i.scannedCode, i.result, i.scannedAt, a.code AS assetCode, a.name AS assetName, l.name AS assetLocation
           FROM audit_items i LEFT JOIN assets a ON a.id = i.assetId LEFT JOIN locations l ON l.id = a.locationId
           WHERE i.sessionId = :sessionId
           ORDER BY CASE i.result WHEN 'TIDAK_DITEMUKAN' THEN 0 WHEN 'TAK_TERDAFTAR' THEN 1 ELSE 2 END, i.scannedAt DESC, a.code""",
    )
    fun observeItems(sessionId: String): Flow<List<OpnameItemRow>>

    @Query(
        """SELECT i.id, i.assetId, i.scannedCode, i.result, i.scannedAt, a.code AS assetCode, a.name AS assetName, l.name AS assetLocation
           FROM audit_items i LEFT JOIN assets a ON a.id = i.assetId LEFT JOIN locations l ON l.id = a.locationId
           WHERE i.sessionId = :sessionId ORDER BY i.result, a.code""",
    )
    suspend fun items(sessionId: String): List<OpnameItemRow>

    @Query("SELECT * FROM audit_items WHERE sessionId = :sessionId AND (assetId = :assetId OR scannedCode = :code) LIMIT 1")
    suspend fun findItem(sessionId: String, assetId: String?, code: String): AuditItemEntity?

    @Query("UPDATE audit_items SET result = :result, scannedAt = :at, scannedCode = :code, updatedAt = :at, syncState = 'PENDING' WHERE id = :id")
    suspend fun markItem(id: String, result: AuditResult, at: Long, code: String)

    @Query("UPDATE audit_sessions SET finishedAt = :at, note = :note, updatedAt = :at, syncState = 'PENDING' WHERE id = :id")
    suspend fun finish(id: String, at: Long, note: String?)

    @Query("SELECT id FROM assets WHERE locationId = :locationId AND deletedAt IS NULL AND status != 'DIHAPUS_DIJUAL'")
    suspend fun assetIdsAt(locationId: String): List<String>

    @Query("SELECT code FROM assets WHERE id = :id")
    suspend fun assetCode(id: String): String?
}
