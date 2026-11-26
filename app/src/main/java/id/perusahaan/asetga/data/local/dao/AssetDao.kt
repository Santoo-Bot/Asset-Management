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
    val qrCode: String?,
)

data class AssetDetailRow(
    @Embedded val asset: AssetEntity,
    val categoryName: String,
    val usefulLifeYears: Int,
    val categoryResidualPercent: Double?,
    val locationName: String,
    val departmentName: String?,
    val divisionName: String?,
)

data class AssetBrief(val id: String, val code: String, val name: String)

data class AssetTotals(val total: Int, val cost: Long)

data class LabelCount(val label: String, val count: Int)

/** Proyeksi ringan untuk menghitung total nilai buku di dashboard. */
data class AssetValuationRow(
    val acquisitionCost: Long,
    val acquisitionDate: LocalDate,
    val usefulLifeYears: Int,
    val residualPercent: Double?,
)

private const val LIST_COLUMNS = """
    a.id, a.code, a.name, c.name AS categoryName, l.name AS locationName, a.picName,
    a.condition, a.status, a.photoPath, a.warrantyUntil, a.acquisitionCost, a.qrCode
"""

private const val FILTER_WHERE = """
    a.deletedAt IS NULL
    AND (:query = '' OR a.code LIKE '%' || :query || '%' OR a.name LIKE '%' || :query || '%'
         OR a.serialNumber LIKE '%' || :query || '%' OR a.picName LIKE '%' || :query || '%'
         OR a.brandModel LIKE '%' || :query || '%' OR a.qrCode LIKE '%' || :query || '%'
         OR a.userLocationDetail LIKE '%' || :query || '%' OR a.prNumber LIKE '%' || :query || '%'
         OR a.poNumber LIKE '%' || :query || '%' OR a.grNumber LIKE '%' || :query || '%')
    AND (:categoryId IS NULL OR a.categoryId = :categoryId)
    AND (:locationId IS NULL OR a.locationId = :locationId)
    AND (:divisionId IS NULL OR a.divisionId = :divisionId)
    AND (:departmentId IS NULL OR a.departmentId = :departmentId)
    AND (:condition IS NULL OR a.condition = :condition)
    AND ((:status IS NULL AND a.status != 'WRITE_OFF') OR a.status = :status)
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
        divisionId: String?,
        departmentId: String?,
    ): PagingSource<Int, AssetListRow>

    @Query("SELECT a.id FROM assets a WHERE $FILTER_WHERE")
    suspend fun filteredIds(
        query: String, categoryId: String?, locationId: String?, condition: String?, status: String?, divisionId: String?, departmentId: String?,
    ): List<String>

    @Query(
        "SELECT COUNT(*) FROM assets a WHERE $FILTER_WHERE",
    )
    fun count(
        query: String, categoryId: String?, locationId: String?, condition: String?, status: String?, divisionId: String?, departmentId: String?,
    ): Flow<Int>

    @Query(
        """SELECT a.*, c.name AS categoryName, c.usefulLifeYears AS usefulLifeYears,
                  c.residualPercent AS categoryResidualPercent, l.name AS locationName, d.name AS departmentName,
                  dv.name AS divisionName
           FROM assets a
           JOIN categories c ON c.id = a.categoryId
           JOIN locations l ON l.id = a.locationId
           LEFT JOIN departments d ON d.id = a.departmentId
           LEFT JOIN divisions dv ON dv.id = a.divisionId
           WHERE a.id = :id""",
    )
    fun observeDetail(id: String): Flow<AssetDetailRow?>

    @Query("SELECT $LIST_COLUMNS FROM assets a JOIN categories c ON c.id = a.categoryId JOIN locations l ON l.id = a.locationId WHERE a.id IN (:ids) ORDER BY a.code")
    suspend fun listItemsByIds(ids: List<String>): List<AssetListRow>

    @Query(
        """SELECT a.*, c.name AS categoryName, c.usefulLifeYears AS usefulLifeYears,
                  c.residualPercent AS categoryResidualPercent, l.name AS locationName, d.name AS departmentName,
                  dv.name AS divisionName
           FROM assets a
           JOIN categories c ON c.id = a.categoryId
           JOIN locations l ON l.id = a.locationId
           LEFT JOIN departments d ON d.id = a.departmentId
           LEFT JOIN divisions dv ON dv.id = a.divisionId
           WHERE a.deletedAt IS NULL ORDER BY a.code""",
    )
    suspend fun exportRows(): List<AssetDetailRow>

    @Query("SELECT code FROM assets")
    suspend fun allCodes(): List<String>

    @Query("SELECT qrCode FROM assets WHERE qrCode IS NOT NULL")
    suspend fun allQrCodes(): List<String>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): AssetEntity?

    /** Cari dari hasil scan: cocok dengan kode aset atau kode QR. */
    @Query("SELECT * FROM assets WHERE (code = :code COLLATE NOCASE OR qrCode = :code) AND deletedAt IS NULL LIMIT 1")
    suspend fun getByCode(code: String): AssetEntity?

    /** Kode/QR tidak boleh sama dengan kode atau QR aset lain agar hasil scan tidak ambigu. */
    @Query("SELECT COUNT(*) FROM assets WHERE (code = :value COLLATE NOCASE OR qrCode = :value COLLATE NOCASE) AND id != :excludeId")
    suspend fun countByCodeOrQr(value: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE code = :code COLLATE NOCASE AND id != :excludeId")
    suspend fun countByCode(code: String, excludeId: String): Int

    @Query("SELECT code FROM assets WHERE code LIKE :prefixYear || '%'")
    suspend fun codesStartingWith(prefixYear: String): List<String>

    @Query(
        """SELECT a.acquisitionCost, a.acquisitionDate, c.usefulLifeYears, c.residualPercent
           FROM assets a JOIN categories c ON c.id = a.categoryId
           WHERE a.deletedAt IS NULL AND a.status != 'WRITE_OFF'""",
    )
    suspend fun valuationRows(): List<AssetValuationRow>

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun countAll(): Int

    // ---------- Dashboard ----------

    /** Dipakai sebagai pemicu: Room memancarkan ulang setiap kali tabel aset berubah. */
    @Query("SELECT COUNT(*) FROM assets")
    fun observeTick(): Flow<Int>

    @Query("SELECT COUNT(*) AS total, COALESCE(SUM(acquisitionCost), 0) AS cost FROM assets WHERE deletedAt IS NULL AND status != 'WRITE_OFF'")
    suspend fun activeTotals(): AssetTotals

    @Query(
        """SELECT c.name AS label, COUNT(*) AS count FROM assets a JOIN categories c ON c.id = a.categoryId
           WHERE a.deletedAt IS NULL AND a.status != 'WRITE_OFF' GROUP BY c.id ORDER BY count DESC, c.name""",
    )
    suspend fun countByCategory(): List<LabelCount>

    @Query(
        """SELECT condition AS label, COUNT(*) AS count FROM assets
           WHERE deletedAt IS NULL AND status != 'WRITE_OFF' GROUP BY condition""",
    )
    suspend fun countByCondition(): List<LabelCount>

    @Query(
        "SELECT $LIST_COLUMNS FROM assets a JOIN categories c ON c.id = a.categoryId JOIN locations l ON l.id = a.locationId " +
            "WHERE a.deletedAt IS NULL AND a.status != 'WRITE_OFF' AND a.warrantyUntil BETWEEN :from AND :to " +
            "ORDER BY a.warrantyUntil ASC LIMIT :limit",
    )
    suspend fun warrantyBetween(from: LocalDate, to: LocalDate, limit: Int): List<AssetListRow>

    @Query("SELECT COUNT(*) FROM assets WHERE deletedAt IS NULL AND status != 'WRITE_OFF' AND warrantyUntil BETWEEN :from AND :to")
    suspend fun countWarrantyBetween(from: LocalDate, to: LocalDate): Int

    @Query(
        "SELECT $LIST_COLUMNS FROM assets a JOIN categories c ON c.id = a.categoryId JOIN locations l ON l.id = a.locationId " +
            "WHERE a.deletedAt IS NULL AND a.status != 'WRITE_OFF' AND a.condition = 'RUSAK' " +
            "ORDER BY a.condition DESC, a.code LIMIT :limit",
    )
    suspend fun damaged(limit: Int): List<AssetListRow>

    @Query("SELECT COUNT(*) FROM assets WHERE deletedAt IS NULL AND status != 'WRITE_OFF' AND condition = 'RUSAK'")
    suspend fun countDamaged(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<AssetEntity>)

    @Update
    suspend fun update(entity: AssetEntity)

    /** Departemen dipindah ke divisi lain → divisi aset-asetnya ikut diperbarui. */
    @Query("UPDATE assets SET divisionId = :divisionId, updatedAt = :now, syncState = 'PENDING' WHERE departmentId = :departmentId")
    suspend fun syncDivisionForDepartment(departmentId: String, divisionId: String?, now: Long)

    // ---------- Hapus permanen (tidak ada soft delete) ----------

    @Query("SELECT id, code, name FROM assets WHERE id IN (:ids)")
    suspend fun briefByIds(ids: List<String>): List<AssetBrief>

    @Query("SELECT photoPath FROM assets WHERE id IN (:ids) AND photoPath IS NOT NULL")
    suspend fun assetPhotos(ids: List<String>): List<String>

    @Query("SELECT photoPath FROM service_records WHERE assetId IN (:ids) AND photoPath IS NOT NULL")
    suspend fun servicePhotos(ids: List<String>): List<String>

    @Query("DELETE FROM asset_mutations WHERE assetId IN (:ids)")
    suspend fun purgeMutations(ids: List<String>)

    @Query("DELETE FROM service_records WHERE assetId IN (:ids)")
    suspend fun purgeServices(ids: List<String>)

    @Query("DELETE FROM audit_items WHERE assetId IN (:ids)")
    suspend fun purgeOpnameItems(ids: List<String>)

    @Query("DELETE FROM insurance_items WHERE assetId IN (:ids)")
    suspend fun purgeInsuranceItems(ids: List<String>)

    @Query("DELETE FROM assets WHERE id IN (:ids)")
    suspend fun deleteAssets(ids: List<String>): Int
}
