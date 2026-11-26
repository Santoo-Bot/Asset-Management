package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.perusahaan.asetga.data.local.entity.UserEntity
import id.perusahaan.asetga.domain.model.Role
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE deletedAt IS NULL ORDER BY isActive DESC, fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM users WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE role = :role AND isActive = 1 AND deletedAt IS NULL")
    suspend fun countActiveByRole(role: Role): Int

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun getByUsername(username: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: UserEntity)

    @Update
    suspend fun update(entity: UserEntity)
}
