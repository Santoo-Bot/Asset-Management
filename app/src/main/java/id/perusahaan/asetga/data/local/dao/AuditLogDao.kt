package id.perusahaan.asetga.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import id.perusahaan.asetga.data.local.entity.AuditLogEntity
import id.perusahaan.asetga.domain.model.AuditEntityType
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entity: AuditLogEntity)

    @Insert
    suspend fun insertAll(entities: List<AuditLogEntity>)

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE (:entityType IS NULL OR entityType = :entityType)
          AND (:query = '' OR instr(lower(summary), lower(:query)) > 0 OR instr(lower(userName), lower(:query)) > 0)
        ORDER BY timestamp DESC
        """,
    )
    fun paging(entityType: AuditEntityType?, query: String): PagingSource<Int, AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    fun observeForEntity(entityType: AuditEntityType, entityId: String): Flow<List<AuditLogEntity>>
}
