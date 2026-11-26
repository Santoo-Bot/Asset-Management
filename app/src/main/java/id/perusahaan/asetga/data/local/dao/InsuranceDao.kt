package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.perusahaan.asetga.data.local.entity.InsuranceItemEntity
import id.perusahaan.asetga.data.local.entity.InsurancePolicyEntity
import id.perusahaan.asetga.data.local.entity.InsuranceTypeEntity
import id.perusahaan.asetga.data.local.entity.InsurerEntity
import id.perusahaan.asetga.domain.model.AssetStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface InsuranceTypeDao {
    @Query("SELECT * FROM insurance_types WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<InsuranceTypeEntity>>

    @Query("SELECT * FROM insurance_types WHERE id = :id")
    suspend fun getById(id: String): InsuranceTypeEntity?

    @Query("SELECT COUNT(*) FROM insurance_types WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: InsuranceTypeEntity)

    @Update
    suspend fun update(entity: InsuranceTypeEntity)

    @Query("UPDATE insurance_types SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)

    @Query("DELETE FROM insurance_types WHERE id = :id")
    suspend fun delete(id: String): Int

    /** Nomor polis AKTIF (belum dihapus) yang memakai data ini. */
    @Query("SELECT policyNumber FROM insurance_policies WHERE typeId = :id AND deletedAt IS NULL ORDER BY policyNumber")
    suspend fun activePolicyNumbers(id: String): List<String>

    /** Polis yang sudah dihapus tidak boleh menghalangi; dibersihkan permanen sebelum master dihapus. */
    @Query("DELETE FROM insurance_items WHERE policyId IN (SELECT id FROM insurance_policies WHERE typeId = :id AND deletedAt IS NOT NULL)")
    suspend fun purgeDeletedPolicyItems(id: String)

    @Query("DELETE FROM insurance_policies WHERE typeId = :id AND deletedAt IS NOT NULL")
    suspend fun purgeDeletedPolicies(id: String)
}

@Dao
interface InsurerDao {
    @Query("SELECT * FROM insurers WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<InsurerEntity>>

    @Query("SELECT * FROM insurers WHERE id = :id")
    suspend fun getById(id: String): InsurerEntity?

    @Query("SELECT COUNT(*) FROM insurers WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: InsurerEntity)

    @Update
    suspend fun update(entity: InsurerEntity)

    @Query("UPDATE insurers SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)

    @Query("DELETE FROM insurers WHERE id = :id")
    suspend fun delete(id: String): Int

    /** Nomor polis AKTIF (belum dihapus) yang memakai data ini. */
    @Query("SELECT policyNumber FROM insurance_policies WHERE insurerId = :id AND deletedAt IS NULL ORDER BY policyNumber")
    suspend fun activePolicyNumbers(id: String): List<String>

    /** Polis yang sudah dihapus tidak boleh menghalangi; dibersihkan permanen sebelum master dihapus. */
    @Query("DELETE FROM insurance_items WHERE policyId IN (SELECT id FROM insurance_policies WHERE insurerId = :id AND deletedAt IS NOT NULL)")
    suspend fun purgeDeletedPolicyItems(id: String)

    @Query("DELETE FROM insurance_policies WHERE insurerId = :id AND deletedAt IS NOT NULL")
    suspend fun purgeDeletedPolicies(id: String)
}

data class PolicyRow(
    val id: String,
    val policyNumber: String,
    val typeId: String,
    val typeName: String,
    val insurerId: String,
    val insurerName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val premium: Long?,
    val sumInsured: Long?,
    val notes: String?,
    val itemCount: Int,
    val itemsSumInsured: Long,
    val itemsAcquisitionCost: Long,
)

data class InsuredAssetRow(
    val itemId: String,
    val assetId: String,
    val code: String,
    val qrCode: String?,
    val name: String,
    val categoryName: String,
    val locationName: String,
    val status: AssetStatus,
    val acquisitionCost: Long,
    val sumInsured: Long?,
)

private const val POLICY_SELECT = """
    SELECT p.id, p.policyNumber, p.typeId, t.name AS typeName, p.insurerId, r.name AS insurerName, p.startDate, p.endDate,
           p.premium, p.sumInsured, p.notes,
           (SELECT COUNT(*) FROM insurance_items i JOIN assets a ON a.id = i.assetId WHERE i.policyId = p.id AND a.deletedAt IS NULL) AS itemCount,
           (SELECT COALESCE(SUM(i.sumInsured), 0) FROM insurance_items i JOIN assets a ON a.id = i.assetId WHERE i.policyId = p.id AND a.deletedAt IS NULL) AS itemsSumInsured,
           (SELECT COALESCE(SUM(a.acquisitionCost), 0) FROM insurance_items i JOIN assets a ON a.id = i.assetId WHERE i.policyId = p.id AND a.deletedAt IS NULL) AS itemsAcquisitionCost
    FROM insurance_policies p
    JOIN insurance_types t ON t.id = p.typeId
    JOIN insurers r ON r.id = p.insurerId
"""

private const val ITEM_SELECT = """
    SELECT i.id AS itemId, a.id AS assetId, a.code, a.qrCode, a.name, c.name AS categoryName, l.name AS locationName,
           a.status, a.acquisitionCost, i.sumInsured
    FROM insurance_items i
    JOIN assets a ON a.id = i.assetId
    JOIN categories c ON c.id = a.categoryId
    JOIN locations l ON l.id = a.locationId
"""

@Dao
interface InsuranceDao {
    @Query("$POLICY_SELECT WHERE p.deletedAt IS NULL ORDER BY p.endDate ASC, p.policyNumber")
    fun observePolicies(): Flow<List<PolicyRow>>

    @Query("$POLICY_SELECT WHERE p.id = :id AND p.deletedAt IS NULL")
    fun observePolicy(id: String): Flow<PolicyRow?>

    @Query("$POLICY_SELECT WHERE p.deletedAt IS NULL AND p.id IN (SELECT i.policyId FROM insurance_items i WHERE i.assetId = :assetId) ORDER BY p.endDate DESC")
    fun observePoliciesForAsset(assetId: String): Flow<List<PolicyRow>>

    @Query("$POLICY_SELECT WHERE p.deletedAt IS NULL AND p.endDate BETWEEN :from AND :to ORDER BY p.endDate ASC")
    suspend fun policiesEndingBetween(from: LocalDate, to: LocalDate): List<PolicyRow>

    @Query("SELECT COUNT(*) FROM insurance_policies WHERE deletedAt IS NULL AND endDate BETWEEN :from AND :to")
    suspend fun countEndingBetween(from: LocalDate, to: LocalDate): Int

    @Query("SELECT COUNT(*) FROM insurance_policies")
    fun observeTick(): Flow<Int>

    @Query("SELECT * FROM insurance_policies WHERE id = :id")
    suspend fun getPolicy(id: String): InsurancePolicyEntity?

    @Query("SELECT COUNT(*) FROM insurance_policies WHERE policyNumber = :number COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByNumber(number: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPolicy(entity: InsurancePolicyEntity)

    @Update
    suspend fun updatePolicy(entity: InsurancePolicyEntity)

    @Query("$ITEM_SELECT WHERE i.policyId = :policyId AND a.deletedAt IS NULL ORDER BY a.code")
    fun observeItems(policyId: String): Flow<List<InsuredAssetRow>>

    @Query("$ITEM_SELECT WHERE i.policyId = :policyId AND a.deletedAt IS NULL ORDER BY a.code")
    suspend fun items(policyId: String): List<InsuredAssetRow>

    @Query("SELECT assetId FROM insurance_items WHERE policyId = :policyId")
    fun observeAssetIds(policyId: String): Flow<List<String>>

    @Query("SELECT assetId FROM insurance_items WHERE policyId = :policyId")
    suspend fun assetIds(policyId: String): List<String>

    /** Aset yang sudah ada di polis diabaikan (rowId -1). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<InsuranceItemEntity>): List<Long>

    @Query("DELETE FROM insurance_items WHERE policyId = :policyId AND assetId = :assetId")
    suspend fun deleteItem(policyId: String, assetId: String): Int

    @Query("UPDATE insurance_items SET sumInsured = :value, updatedAt = :now, syncState = 'PENDING' WHERE policyId = :policyId AND assetId = :assetId")
    suspend fun updateItemSum(policyId: String, assetId: String, value: Long?, now: Long): Int
}
