package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("UPDATE categories SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)
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

    @Query("UPDATE locations SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)
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

    @Query("UPDATE divisions SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)
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

    @Query("UPDATE departments SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)
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

    @Query("UPDATE vendors SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)
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

    @Query("UPDATE units SET isActive = :active, updatedAt = :now, syncState = 'PENDING' WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, now: Long)
}
