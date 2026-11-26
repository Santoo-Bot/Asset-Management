package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.perusahaan.asetga.data.local.entity.AssetStatusEntity
import id.perusahaan.asetga.data.local.entity.CategoryEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.UnitEntity
import id.perusahaan.asetga.data.local.entity.VendorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM categories WHERE codePrefix = :prefix COLLATE NOCASE AND id != :excludeId")
    suspend fun countByPrefix(prefix: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity)

    @Update
    suspend fun update(entity: CategoryEntity)

}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: String): LocationEntity?

    @Query("SELECT COUNT(*) FROM locations WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: LocationEntity)

    @Update
    suspend fun update(entity: LocationEntity)

}

@Dao
interface DivisionDao {
    @Query("SELECT * FROM divisions WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<DivisionEntity>>

    @Query("SELECT * FROM divisions WHERE id = :id")
    suspend fun getById(id: String): DivisionEntity?

    @Query("SELECT COUNT(*) FROM divisions WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Query("SELECT COUNT(*) FROM departments WHERE divisionId = :divisionId AND isActive = 1 AND deletedAt IS NULL")
    suspend fun countActiveDepartments(divisionId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DivisionEntity)

    @Update
    suspend fun update(entity: DivisionEntity)

}

@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE id = :id")
    suspend fun getById(id: String): DepartmentEntity?

    @Query("SELECT COUNT(*) FROM departments WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DepartmentEntity)

    @Update
    suspend fun update(entity: DepartmentEntity)

}

@Dao
interface VendorDao {
    @Query("SELECT * FROM vendors WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<VendorEntity>>

    @Query("SELECT * FROM vendors WHERE id = :id")
    suspend fun getById(id: String): VendorEntity?

    @Query("SELECT COUNT(*) FROM vendors WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: VendorEntity)

    @Update
    suspend fun update(entity: VendorEntity)

}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units WHERE deletedAt IS NULL AND (:includeInactive OR isActive = 1) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeInactive: Boolean): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun getById(id: String): UnitEntity?

    @Query("SELECT COUNT(*) FROM units WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UnitEntity)

    @Update
    suspend fun update(entity: UnitEntity)

}

@Dao
interface AssetStatusDao {
    @Query("SELECT * FROM asset_statuses WHERE deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<AssetStatusEntity>>

    @Query("SELECT * FROM asset_statuses WHERE deletedAt IS NULL ORDER BY sortOrder, name COLLATE NOCASE")
    suspend fun all(): List<AssetStatusEntity>

    @Query("SELECT * FROM asset_statuses WHERE id = :id")
    suspend fun getById(id: String): AssetStatusEntity?

    @Query("SELECT COUNT(*) FROM asset_statuses WHERE name = :name COLLATE NOCASE AND id != :excludeId AND deletedAt IS NULL")
    suspend fun countByName(name: String, excludeId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM asset_statuses")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AssetStatusEntity)

    @Update
    suspend fun update(entity: AssetStatusEntity)

    @Query("DELETE FROM asset_statuses WHERE id = :id")
    suspend fun delete(id: String): Int
}

/** Menghitung pemakaian data master agar data yang masih dipakai tidak terhapus. */
@Dao
interface MasterUsageDao {
    @Query("SELECT COUNT(*) FROM assets WHERE categoryId = :id")
    suspend fun assetsInCategory(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE categoryId = :id AND deletedAt IS NOT NULL")
    suspend fun deletedAssetsInCategory(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE locationId = :id")
    suspend fun assetsInLocation(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE locationId = :id AND deletedAt IS NOT NULL")
    suspend fun deletedAssetsInLocation(id: String): Int

    @Query("SELECT COUNT(*) FROM items WHERE storageLocationId = :id")
    suspend fun itemsInLocation(id: String): Int


    @Query("SELECT COUNT(*) FROM asset_mutations WHERE fromLocationId = :id OR toLocationId = :id")
    suspend fun mutationsWithLocation(id: String): Int

    @Query("SELECT COUNT(*) FROM departments WHERE divisionId = :id")
    suspend fun departmentsInDivision(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE divisionId = :id")
    suspend fun assetsInDivision(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE divisionId = :id AND deletedAt IS NOT NULL")
    suspend fun deletedAssetsInDivision(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE departmentId = :id")
    suspend fun assetsInDepartment(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE departmentId = :id AND deletedAt IS NOT NULL")
    suspend fun deletedAssetsInDepartment(id: String): Int

    @Query("SELECT COUNT(*) FROM asset_mutations WHERE fromDepartmentId = :id OR toDepartmentId = :id")
    suspend fun mutationsWithDepartment(id: String): Int

    /** Lepas departemen dari riwayat mutasi (data lama) agar departemen bisa dihapus. */
    @Query("UPDATE assets SET departmentId = NULL WHERE departmentId = :id AND deletedAt IS NOT NULL")
    suspend fun detachDepartmentFromTrashedAssets(id: String)

    @Query("UPDATE asset_mutations SET fromDepartmentId = NULL WHERE fromDepartmentId = :id")
    suspend fun detachDepartmentFromMutationsFrom(id: String)

    @Query("UPDATE asset_mutations SET toDepartmentId = NULL WHERE toDepartmentId = :id")
    suspend fun detachDepartmentFromMutationsTo(id: String)

    @Query("UPDATE assets SET divisionId = NULL WHERE divisionId = :id AND deletedAt IS NOT NULL")
    suspend fun detachDivisionFromTrashedAssets(id: String)

    @Query("SELECT COUNT(*) FROM assets WHERE status = 'WRITE_OFF' AND (categoryId = :id OR locationId = :id OR divisionId = :id OR departmentId = :id OR vendorId = :id)")
    suspend fun writeOffAssetsUsing(id: String): Int

    @Query("UPDATE asset_mutations SET fromLocationId = NULL WHERE fromLocationId = :id")
    suspend fun detachLocationFromMutationsFrom(id: String)

    @Query("UPDATE asset_mutations SET toLocationId = NULL WHERE toLocationId = :id")
    suspend fun detachLocationFromMutationsTo(id: String)



    @Query("SELECT COUNT(*) FROM service_records WHERE vendorId = :id")
    suspend fun servicesWithVendor(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE vendorId = :id")
    suspend fun assetsWithVendor(id: String): Int

    @Query("SELECT COUNT(*) FROM assets WHERE status = :id")
    suspend fun assetsWithStatus(id: String): Int

    @Query("SELECT COUNT(*) FROM items WHERE unitId = :id")
    suspend fun itemsWithUnit(id: String): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String): Int

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocation(id: String): Int

    @Query("DELETE FROM divisions WHERE id = :id")
    suspend fun deleteDivision(id: String): Int

    @Query("DELETE FROM departments WHERE id = :id")
    suspend fun deleteDepartment(id: String): Int

    @Query("DELETE FROM vendors WHERE id = :id")
    suspend fun deleteVendor(id: String): Int

    @Query("DELETE FROM units WHERE id = :id")
    suspend fun deleteUnit(id: String): Int
}
