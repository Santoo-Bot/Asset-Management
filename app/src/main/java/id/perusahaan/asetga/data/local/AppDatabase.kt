package id.perusahaan.asetga.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import id.perusahaan.asetga.data.local.dao.AuditLogDao
import id.perusahaan.asetga.data.local.dao.CategoryDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.SettingsDao
import id.perusahaan.asetga.data.local.dao.UnitDao
import id.perusahaan.asetga.data.local.dao.UserDao
import id.perusahaan.asetga.data.local.dao.VendorDao
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.AssetMutationEntity
import id.perusahaan.asetga.data.local.entity.AuditItemEntity
import id.perusahaan.asetga.data.local.entity.AuditLogEntity
import id.perusahaan.asetga.data.local.entity.AuditSessionEntity
import id.perusahaan.asetga.data.local.entity.CategoryEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.ItemEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.ServiceRecordEntity
import id.perusahaan.asetga.data.local.entity.StockTxnEntity
import id.perusahaan.asetga.data.local.entity.UnitEntity
import id.perusahaan.asetga.data.local.entity.UserEntity
import id.perusahaan.asetga.data.local.entity.VendorEntity
import java.util.UUID

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        LocationEntity::class,
        DepartmentEntity::class,
        VendorEntity::class,
        UnitEntity::class,
        AppSettingEntity::class,
        AssetEntity::class,
        AssetMutationEntity::class,
        ServiceRecordEntity::class,
        ItemEntity::class,
        StockTxnEntity::class,
        AuditSessionEntity::class,
        AuditItemEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun locationDao(): LocationDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun vendorDao(): VendorDao
    abstract fun unitDao(): UnitDao
    abstract fun settingsDao(): SettingsDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val NAME = "aset_ga.db"
    }
}

/** Data awal wajib (semua build): kategori dengan umur ekonomis, satuan, dan pengaturan default. */
object DefaultData {
    data class DefaultCategory(val name: String, val prefix: String, val lifeYears: Int)

    val categories = listOf(
        DefaultCategory("Elektronik & IT", "IT", 4),
        DefaultCategory("Furnitur", "FUR", 8),
        DefaultCategory("Kendaraan", "KND", 8),
        DefaultCategory("Peralatan Kantor", "PK", 4),
        DefaultCategory("Mesin & Peralatan", "MSN", 8),
        DefaultCategory("Bangunan & Instalasi", "BGN", 20),
        DefaultCategory("Lainnya", "LN", 4),
    )

    val units = listOf("Pcs", "Unit", "Rim", "Box", "Pak", "Lusin", "Botol", "Liter", "Roll", "Set")

    fun seed(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        categories.forEach { c ->
            db.execSQL(
                "INSERT INTO categories (id, name, codePrefix, usefulLifeYears, residualPercent, isActive, createdAt, updatedAt, deletedAt, syncState) " +
                    "VALUES (?, ?, ?, ?, NULL, 1, ?, ?, NULL, 'PENDING')",
                arrayOf<Any>(UUID.randomUUID().toString(), c.name, c.prefix, c.lifeYears, now, now),
            )
        }
        units.forEach { u ->
            db.execSQL(
                "INSERT INTO units (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) VALUES (?, ?, 1, ?, ?, NULL, 'PENDING')",
                arrayOf<Any>(UUID.randomUUID().toString(), u, now, now),
            )
        }
        SettingKeys.defaults.forEach { (k, v) ->
            db.execSQL("INSERT OR IGNORE INTO app_settings (`key`, value) VALUES (?, ?)", arrayOf<Any>(k, v))
        }
    }

    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            seed(db)
        }
    }
}

object SettingKeys {
    const val THEME_MODE = "theme_mode"
    const val APP_LOCK_ENABLED = "app_lock_enabled"
    const val LAST_USER_ID = "last_user_id"
    const val DEFAULT_RESIDUAL_PERCENT = "default_residual_percent"
    const val REMINDER_HOUR = "reminder_hour"
    const val NOTIFICATIONS_ENABLED = "notifications_enabled"

    val defaults = mapOf(
        THEME_MODE to "SYSTEM",
        APP_LOCK_ENABLED to "true",
        DEFAULT_RESIDUAL_PERCENT to "0",
        REMINDER_HOUR to "8",
        NOTIFICATIONS_ENABLED to "true",
    )
}
