package id.perusahaan.asetga.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entity: AppSettingEntity)
}
