package id.perusahaan.asetga.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.AuditLogDao
import id.perusahaan.asetga.data.local.dao.CategoryDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.DivisionDao
import id.perusahaan.asetga.data.local.dao.InsuranceDao
import id.perusahaan.asetga.data.local.dao.InsuranceTypeDao
import id.perusahaan.asetga.data.local.dao.InsurerDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.MasterUsageDao
import id.perusahaan.asetga.data.local.dao.MutationDao
import id.perusahaan.asetga.data.local.dao.OpnameDao
import id.perusahaan.asetga.data.local.dao.ServiceDao
import id.perusahaan.asetga.data.local.dao.StockDao
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
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.InsuranceItemEntity
import id.perusahaan.asetga.data.local.entity.InsurancePolicyEntity
import id.perusahaan.asetga.data.local.entity.InsuranceTypeEntity
import id.perusahaan.asetga.data.local.entity.InsurerEntity
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
        DivisionEntity::class,
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
        InsuranceTypeEntity::class,
        InsurerEntity::class,
        InsurancePolicyEntity::class,
        InsuranceItemEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun locationDao(): LocationDao
    abstract fun divisionDao(): DivisionDao
    abstract fun masterUsageDao(): MasterUsageDao
    abstract fun insuranceTypeDao(): InsuranceTypeDao
    abstract fun insurerDao(): InsurerDao
    abstract fun insuranceDao(): InsuranceDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun vendorDao(): VendorDao
    abstract fun unitDao(): UnitDao
    abstract fun settingsDao(): SettingsDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun assetDao(): AssetDao
    abstract fun mutationDao(): MutationDao
    abstract fun serviceDao(): ServiceDao
    abstract fun stockDao(): StockDao
    abstract fun opnameDao(): OpnameDao

    companion object {
        const val NAME = "aset_ga.db"
    }
}

/**
 * Migrasi skema. Data lama tidak pernah dihapus: hanya menambah kolom/indeks.
 * Wajib menambah migrasi baru setiap kali entity berubah (jangan pakai fallbackToDestructiveMigration).
 */
object Migrations {
    /** v2: kode QR, pengguna/detail lokasi, divisi, PR/PO/GR, pemohon, memo, tindak lanjut, BAST. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "qrCode TEXT", "userLocationDetail TEXT", "division TEXT", "prNumber TEXT", "poNumber TEXT",
                "grNumber TEXT", "requester TEXT", "memoNumber TEXT", "memoDate INTEGER", "followUp TEXT", "handoverDoc TEXT",
            ).forEach { db.execSQL("ALTER TABLE `assets` ADD COLUMN $it") }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_assets_qrCode` ON `assets` (`qrCode`)")
        }
    }

    /**
     * v3: master Divisi; Departemen terhubung ke Divisi; aset menyimpan divisionId.
     * Teks divisi yang sempat diisi di v2 dijadikan master Divisi lalu ditautkan ke aset.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `divisions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_divisions_name` ON `divisions` (`name`)")
            db.execSQL("ALTER TABLE `departments` ADD COLUMN `divisionId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_departments_divisionId` ON `departments` (`divisionId`)")
            db.execSQL("ALTER TABLE `assets` ADD COLUMN `divisionId` TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assets_divisionId` ON `assets` (`divisionId`)")

            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO `divisions` (id, name, isActive, createdAt, updatedAt, deletedAt, syncState) " +
                    "SELECT lower(hex(randomblob(16))), name, 1, $now, $now, NULL, 'PENDING' FROM " +
                    "(SELECT TRIM(division) AS name FROM assets WHERE TRIM(COALESCE(division, '')) != '' GROUP BY TRIM(division) COLLATE NOCASE)",
            )
            db.execSQL(
                "UPDATE assets SET divisionId = (SELECT d.id FROM divisions d WHERE d.name = TRIM(assets.division) COLLATE NOCASE) " +
                    "WHERE TRIM(COALESCE(division, '')) != ''",
            )
        }
    }

    /**
     * v4: kondisi & status aset disederhanakan (tanpa perubahan struktur tabel).
     * Kondisi: Cukup → Baik; Rusak Ringan/Berat → Rusak.
     * Status: Aktif/Dipinjam → Digunakan; Disimpan → Tersedia; Dihapus/Dijual → Write Off.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE assets SET condition = CASE condition WHEN 'CUKUP' THEN 'BAIK' " +
                    "WHEN 'RUSAK_RINGAN' THEN 'RUSAK' WHEN 'RUSAK_BERAT' THEN 'RUSAK' ELSE condition END",
            )
            db.execSQL(
                "UPDATE assets SET status = CASE status WHEN 'AKTIF' THEN 'DIGUNAKAN' WHEN 'DIPINJAM' THEN 'DIGUNAKAN' " +
                    "WHEN 'DISIMPAN' THEN 'TERSEDIA' WHEN 'DIHAPUS_DIJUAL' THEN 'WRITE_OFF' ELSE status END",
            )
        }
    }

    /** v5: mutasi mencatat pengguna / detail lokasi lama & baru. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `asset_mutations` ADD COLUMN `fromUserLocation` TEXT")
            db.execSQL("ALTER TABLE `asset_mutations` ADD COLUMN `toUserLocation` TEXT")
        }
    }

    /** v6: modul asuransi (tipe, perusahaan, polis, aset yang ditanggung). */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val sync = "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncState` TEXT NOT NULL"
            db.execSQL("CREATE TABLE IF NOT EXISTS `insurance_types` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, $sync, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_types_name` ON `insurance_types` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `insurers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT, `email` TEXT, `address` TEXT, " +
                    "`isActive` INTEGER NOT NULL, $sync, PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurers_name` ON `insurers` (`name`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `insurance_policies` (`id` TEXT NOT NULL, `policyNumber` TEXT NOT NULL, `typeId` TEXT NOT NULL, " +
                    "`insurerId` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `premium` INTEGER, `sumInsured` INTEGER, " +
                    "`notes` TEXT, $sync, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`typeId`) REFERENCES `insurance_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`insurerId`) REFERENCES `insurers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_policies_policyNumber` ON `insurance_policies` (`policyNumber`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_policies_typeId` ON `insurance_policies` (`typeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_policies_insurerId` ON `insurance_policies` (`insurerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_policies_endDate` ON `insurance_policies` (`endDate`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `insurance_items` (`id` TEXT NOT NULL, `policyId` TEXT NOT NULL, `assetId` TEXT NOT NULL, " +
                    "`sumInsured` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncState` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`policyId`) REFERENCES `insurance_policies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_insurance_items_policyId_assetId` ON `insurance_items` (`policyId`, `assetId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_insurance_items_assetId` ON `insurance_items` (`assetId`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
    const val COMPANY_NAME = "company_name"

    val defaults = mapOf(
        THEME_MODE to "SYSTEM",
        APP_LOCK_ENABLED to "true",
        DEFAULT_RESIDUAL_PERCENT to "0",
        REMINDER_HOUR to "8",
        NOTIFICATIONS_ENABLED to "true",
        COMPANY_NAME to "",
    )
}
