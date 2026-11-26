package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.perusahaan.asetga.data.local.entity.AuditItemEntity
import id.perusahaan.asetga.data.local.entity.AuditSessionEntity
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AuditResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class OpnameSessionRow(
    val id: String,
    val name: String,
    val opnameDate: LocalDate,
    val startedAt: Long,
    val finishedAt: Long?,
    val startedBy: String?,
    val note: String?,
    /** Aset dalam daftar sesi. */
    val total: Int,
    val found: Int,
    val damaged: Int,
    /** Hasil scan di luar daftar (aset lain / kode tidak dikenal). */
    val outside: Int,
)

data class OpnameItemRow(
    val id: String,
    val assetId: String?,
    val scannedCode: String,
    val result: AuditResult,
    val condition: AssetCondition?,
    val note: String?,
    val scannedAt: Long?,
    val assetCode: String?,
    val assetName: String?,
    val qrCode: String?,
    val categoryName: String?,
    val locationName: String?,
    val userLocation: String?,
    val systemCondition: AssetCondition?,
)

data class AssetIdCode(val id: String, val code: String)

private const val SESSION_SELECT = """
    SELECT s.id, s.name, s.opnameDate, s.startedAt, s.finishedAt, u.fullName AS startedBy, s.note,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result != 'TAK_TERDAFTAR') AS total,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result = 'DITEMUKAN') AS found,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result = 'DITEMUKAN' AND i.condition = 'RUSAK') AS damaged,
           (SELECT COUNT(*) FROM audit_items i WHERE i.sessionId = s.id AND i.result = 'TAK_TERDAFTAR') AS outside
    FROM audit_sessions s LEFT JOIN users u ON u.id = s.startedByUserId
"""

private const val ITEM_SELECT = """
    SELECT i.id, i.assetId, i.scannedCode, i.result, i.condition, i.note, i.scannedAt,
           a.code AS assetCode, a.name AS assetName, a.qrCode, c.name AS categoryName, l.name AS locationName,
           a.userLocationDetail AS userLocation, a.condition AS systemCondition
    FROM audit_items i
    LEFT JOIN assets a ON a.id = i.assetId
    LEFT JOIN categories c ON c.id = a.categoryId
    LEFT JOIN locations l ON l.id = a.locationId
    WHERE i.sessionId = :sessionId
    ORDER BY CASE i.result WHEN 'TIDAK_DITEMUKAN' THEN 0 WHEN 'TAK_TERDAFTAR' THEN 1 ELSE 2 END, a.code, i.scannedCode
"""

@Dao
interface OpnameDao {
    @Insert
    suspend fun insertSession(entity: AuditSessionEntity)

    @Insert
    suspend fun insertItems(items: List<AuditItemEntity>)

    @Insert
    suspend fun insertItem(item: AuditItemEntity)

    @Query("$SESSION_SELECT ORDER BY s.opnameDate DESC, s.startedAt DESC")
    fun observeSessions(): Flow<List<OpnameSessionRow>>

    @Query("$SESSION_SELECT WHERE s.id = :id")
    fun observeSession(id: String): Flow<OpnameSessionRow?>

    @Query("$SESSION_SELECT WHERE s.id = :id")
    suspend fun getSession(id: String): OpnameSessionRow?

    @Query(ITEM_SELECT)
    fun observeItems(sessionId: String): Flow<List<OpnameItemRow>>

    @Query(ITEM_SELECT)
    suspend fun items(sessionId: String): List<OpnameItemRow>

    @Query("SELECT assetId FROM audit_items WHERE sessionId = :sessionId AND assetId IS NOT NULL")
    fun observeAssetIds(sessionId: String): Flow<List<String>>

    @Query("SELECT assetId FROM audit_items WHERE sessionId = :sessionId AND assetId IS NOT NULL")
    suspend fun assetIds(sessionId: String): List<String>

    /** Aset yang boleh masuk daftar opname (bukan Write Off). */
    @Query("SELECT id, code FROM assets WHERE id IN (:ids) AND status != 'WRITE_OFF'")
    suspend fun eligibleAssets(ids: List<String>): List<AssetIdCode>

    @Query("SELECT * FROM audit_items WHERE id = :id")
    suspend fun getItem(id: String): AuditItemEntity?

    @Query("SELECT * FROM audit_items WHERE sessionId = :sessionId AND assetId = :assetId LIMIT 1")
    suspend fun findByAsset(sessionId: String, assetId: String): AuditItemEntity?

    @Query("SELECT * FROM audit_items WHERE sessionId = :sessionId AND assetId IS NULL AND scannedCode = :code COLLATE NOCASE LIMIT 1")
    suspend fun findUnknownCode(sessionId: String, code: String): AuditItemEntity?

    @Query(
        """UPDATE audit_items SET result = :result, condition = :condition, note = :note, scannedAt = :scannedAt,
           updatedAt = :at, syncState = 'PENDING' WHERE id = :id""",
    )
    suspend fun updateItem(id: String, result: AuditResult, condition: AssetCondition?, note: String?, scannedAt: Long?, at: Long)

    @Query("DELETE FROM audit_items WHERE sessionId = :sessionId AND id = :id")
    suspend fun deleteItem(sessionId: String, id: String): Int

    @Query("DELETE FROM audit_items WHERE sessionId = :sessionId")
    suspend fun deleteItems(sessionId: String)

    @Query("DELETE FROM audit_sessions WHERE id = :id")
    suspend fun deleteSession(id: String): Int

    @Query("UPDATE audit_sessions SET finishedAt = :at, note = :note, updatedAt = :at, syncState = 'PENDING' WHERE id = :id")
    suspend fun finish(id: String, at: Long, note: String?)
}
