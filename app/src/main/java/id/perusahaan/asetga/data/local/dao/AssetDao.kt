package id.perusahaan.asetga.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class AssetListRow(
    val id: String,
    val code: String,
    val name: String,
    val categoryName: String,
    val locationName: String,
    val picName: String?,
    val condition: AssetCondition,
    val status: AssetStatus,
    val photoPath: String?,
    val warrantyUntil: LocalDate?,
    val acquisitionCost: Long,
)

data class AssetDetailRow(
    @Embedded val asset: AssetEntity,
    val categoryName: String,
    val usefulLifeYears: Int,
    val categoryResidualPercent: Double?,
    val locationName: String,
    val departmentName: String?,
)

/** Proyeksi ringan untuk menghitung total nilai buku di dashboard. */
data class AssetValuationRow(
    val acquisitionCost: Long,
    val acquisitionDate: LocalDate,
    val usefulLifeYears: Int,
    val residualPercent: Double?,
)

private const val LIST_COLUMNS = """
    a.id, a.code, a.name, c.name AS categoryName, l.name AS locationName, a.picName,
    a.condition, a.status, a.photoPath, a.warrantyUntil, a.acquisitionCost
"""

private const val FILTER_WHERE = """
    a.deletedAt IS NULL
    AND (:query = '' OR a.code LIKE '%' || :query || '%' OR a.name LIKE '%' || :query || '%'
         OR a.serialNumber LIKE '%' || :query || '%' OR a.picName LIKE '%' || :query || '%'
         OR a.brandModel LIKE '%' || :query || '%')
    AND (:categoryId IS NULL OR a.categoryId = :categoryId)
    AND (:locationId IS NULL OR a.locationId = :locationId)
    AND (:condition IS NULL OR a.condition = :condition)
    AND ((:status IS NULL AND a.status != 'DIHAPUS_DIJUAL') OR a.status = :status)
"""

@Dao
interface AssetDao {
    @Query(
        "SELECT $LIST_COLUMNS FROM assets a " +
            "JOIN categories c ON c.id = a.categoryId JOIN locations l ON l.id = a.locationId " +
            "WHERE $FILTER_WHERE " +
            """ORDER BY
                CASE WHEN :sort = 'NAME' THEN a.name END COLLATE NOCASE ASC,
                CASE WHEN :sort = 'NEWEST' THEN a.acquisitionDate END DESC,
                CASE WHEN :sort = 'OLDEST' THEN a.acquisitionDate END ASC,
                CASE WHEN :sort = 'COST_DESC' THEN a.acquisitionCost END DESC,
                a.code ASC""",
    )
    fun paging(
        query: String,
        categoryId: String?,
        locationId: String?,
        condition: String?,
        status: String?,
        sort: String,
    ): PagingSource<Int, AssetListRow>

    @Query(
        "SELECT COUNT(*) FROM assets a WHERE $FILTER_WHERE",
    )
    fun count(query: String, categoryId: String?, locationId: String?, condition: String?, status: String?): Flow<Int>

    @Query(
        """SELECT a.*, c.name AS categoryName, c.usefulLifeYears AS usefulLifeYears,
                  c.residualPercent AS categoryResidualPercent, l.name AS locationName, d.name AS departmentName
           FROM assets a
           JOIN categories c ON c.id = a.categoryId
           JOIN locations l ON l.id = a.locationId
           LEFT JOIN departments d ON d.id = a.departmentId
           WHERE a.id = :id""",
    )
    fun observeDetail(id: String): Flow<AssetDetailRow?>

    @Query("SELECT $LIST_COLUMNS FROM assets a JOIN categories c ON c.id = a.categoryId JOIN locations l ON l.id = a.locationId WHERE a.id IN (:ids) ORDER BY a.code")
    suspend fun listItemsByIds(ids: List<String>): List<AssetListRow>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE code = :code COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun getByCode(code: String): AssetEntity?

    @Query("SELECT COUNT(*) FROM assets WHERE code = :code COLLATE NOCASE AND id != :excludeId")
    suspend fun countByCode(code: String, excludeId: String): Int

    @Query("SELECT code FROM assets WHERE code LIKE :prefixYear || '%'")
    suspend fun codesStartingWith(prefixYear: String): List<String>

    @Query(
        """SELECT a.acquisitionCost, a.acquisitionDate, c.usefulLifeYears, c.residualPercent
           FROM assets a JOIN categories c ON c.id = a.categoryId
           WHERE a.deletedAt IS NULL AND a.status != 'DIHAPUS_DIJUAL'""",
    )
    suspend fun valuationRows(): List<AssetValuationRow>

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<AssetEntity>)

    @Update
    suspend fun update(entity: AssetEntity)
}
