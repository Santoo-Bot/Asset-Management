package id.perusahaan.asetga.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import id.perusahaan.asetga.data.local.entity.AssetMutationEntity
import id.perusahaan.asetga.data.local.entity.ServiceRecordEntity
import id.perusahaan.asetga.domain.model.ServiceType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class MutationRow(
    val id: String,
    val assetId: String,
    val mutationDate: LocalDate,
    val fromLocation: String?,
    val toLocation: String?,
    val fromDepartment: String?,
    val toDepartment: String?,
    val fromPic: String?,
    val toPic: String?,
    val reason: String,
    val approvedBy: String?,
    val createdBy: String?,
)

data class ServiceRow(
    val id: String,
    val assetId: String,
    val assetCode: String,
    val assetName: String,
    val serviceDate: LocalDate,
    val type: ServiceType,
    val vendorId: String?,
    val vendorName: String?,
    val cost: Long,
    val description: String,
    val photoPath: String?,
    val nextServiceDate: LocalDate?,
)

data class ScheduleRow(
    val assetId: String,
    val assetCode: String,
    val assetName: String,
    val lastServiceDate: LocalDate,
    val nextServiceDate: LocalDate,
)

private const val SERVICE_COLUMNS = """
    s.id, s.assetId, a.code AS assetCode, a.name AS assetName, s.serviceDate, s.type, s.vendorId,
    v.name AS vendorName, s.cost, s.description, s.photoPath, s.nextServiceDate
"""

@Dao
interface MutationDao {
    @Insert
    suspend fun insert(entity: AssetMutationEntity)

    @Query(
        """SELECT m.id, m.assetId, m.mutationDate, fl.name AS fromLocation, tl.name AS toLocation,
                  fd.name AS fromDepartment, td.name AS toDepartment, m.fromPic, m.toPic, m.reason, m.approvedBy,
                  u.fullName AS createdBy
           FROM asset_mutations m
           LEFT JOIN locations fl ON fl.id = m.fromLocationId
           LEFT JOIN locations tl ON tl.id = m.toLocationId
           LEFT JOIN departments fd ON fd.id = m.fromDepartmentId
           LEFT JOIN departments td ON td.id = m.toDepartmentId
           LEFT JOIN users u ON u.id = m.createdByUserId
           WHERE m.assetId = :assetId AND m.deletedAt IS NULL
           ORDER BY m.mutationDate DESC, m.createdAt DESC""",
    )
    fun observeForAsset(assetId: String): Flow<List<MutationRow>>
}

@Dao
interface ServiceDao {
    @Query("SELECT COUNT(*) FROM service_records")
    fun observeTick(): Flow<Int>

    @Insert
    suspend fun insert(entity: ServiceRecordEntity)

    @Update
    suspend fun update(entity: ServiceRecordEntity)

    @Query("SELECT * FROM service_records WHERE id = :id")
    suspend fun getById(id: String): ServiceRecordEntity?

    @Query(
        "SELECT $SERVICE_COLUMNS FROM service_records s JOIN assets a ON a.id = s.assetId LEFT JOIN vendors v ON v.id = s.vendorId " +
            "WHERE s.id = :id",
    )
    suspend fun getRow(id: String): ServiceRow?

    @Query(
        "SELECT $SERVICE_COLUMNS FROM service_records s JOIN assets a ON a.id = s.assetId LEFT JOIN vendors v ON v.id = s.vendorId " +
            "WHERE s.assetId = :assetId AND s.deletedAt IS NULL ORDER BY s.serviceDate DESC, s.createdAt DESC",
    )
    fun observeForAsset(assetId: String): Flow<List<ServiceRow>>

    @Query(
        "SELECT $SERVICE_COLUMNS FROM service_records s JOIN assets a ON a.id = s.assetId LEFT JOIN vendors v ON v.id = s.vendorId " +
            "WHERE s.deletedAt IS NULL AND (:query = '' OR a.code LIKE '%' || :query || '%' OR a.name LIKE '%' || :query || '%' " +
            "OR v.name LIKE '%' || :query || '%' OR s.description LIKE '%' || :query || '%') " +
            "ORDER BY s.serviceDate DESC, s.createdAt DESC",
    )
    fun paging(query: String): PagingSource<Int, ServiceRow>

    @Query(
        "SELECT $SERVICE_COLUMNS FROM service_records s JOIN assets a ON a.id = s.assetId LEFT JOIN vendors v ON v.id = s.vendorId " +
            "WHERE s.deletedAt IS NULL ORDER BY s.serviceDate DESC, s.createdAt DESC",
    )
    suspend fun allRows(): List<ServiceRow>

    /** Satu baris per aset aktif: jadwal berikutnya dari catatan servis terakhir. */
    @Query(
        """SELECT a.id AS assetId, a.code AS assetCode, a.name AS assetName,
                  s.serviceDate AS lastServiceDate, s.nextServiceDate AS nextServiceDate
           FROM assets a JOIN service_records s ON s.assetId = a.id
           WHERE a.deletedAt IS NULL AND a.status != 'WRITE_OFF'
             AND s.nextServiceDate IS NOT NULL
             AND s.id = (SELECT s2.id FROM service_records s2 WHERE s2.assetId = a.id AND s2.deletedAt IS NULL
                         ORDER BY s2.serviceDate DESC, s2.createdAt DESC LIMIT 1)
           ORDER BY s.nextServiceDate ASC""",
    )
    fun observeSchedules(): Flow<List<ScheduleRow>>

    @Query(
        """SELECT a.id AS assetId, a.code AS assetCode, a.name AS assetName,
                  s.serviceDate AS lastServiceDate, s.nextServiceDate AS nextServiceDate
           FROM assets a JOIN service_records s ON s.assetId = a.id
           WHERE a.deletedAt IS NULL AND a.status != 'WRITE_OFF'
             AND s.nextServiceDate IS NOT NULL
             AND s.id = (SELECT s2.id FROM service_records s2 WHERE s2.assetId = a.id AND s2.deletedAt IS NULL
                         ORDER BY s2.serviceDate DESC, s2.createdAt DESC LIMIT 1)
           ORDER BY s.nextServiceDate ASC""",
    )
    suspend fun schedules(): List<ScheduleRow>
}
