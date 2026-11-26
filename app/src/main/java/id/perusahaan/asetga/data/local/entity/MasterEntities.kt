package id.perusahaan.asetga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.SyncState

/*
 * Semua tabel memakai primary key UUID (String) serta kolom createdAt/updatedAt/deletedAt/syncState
 * agar siap disinkronkan ke backend di fase lanjutan tanpa bentrok ID antar-perangkat.
 * Data tidak pernah dihapus permanen: master memakai isActive, transaksi memakai deletedAt.
 */

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val fullName: String,
    val role: Role,
    /** Format: pbkdf2-sha256$iterasi$salt$hash (Base64). Tidak pernah plaintext. */
    val passwordHash: String,
    val pinHash: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["codePrefix"], unique = true), Index(value = ["name"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val codePrefix: String,
    val usefulLifeYears: Int,
    val residualPercent: Double? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(tableName = "locations", indices = [Index(value = ["name"])])
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(tableName = "divisions", indices = [Index(value = ["name"])])
data class DivisionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

/** Departemen berada di bawah satu Divisi ([divisionId], ditambahkan di skema v3). */
@Entity(tableName = "departments", indices = [Index(value = ["name"]), Index(value = ["divisionId"])])
data class DepartmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
    val divisionId: String? = null,
)

@Entity(tableName = "vendors", indices = [Index(value = ["name"])])
data class VendorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(tableName = "units", indices = [Index(value = ["name"])])
data class UnitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Master status aset. Lima status bawaan memakai id tetap (DIGUNAKAN, ..., WRITE_OFF). (skema v13) */
@Entity(tableName = "asset_statuses", indices = [Index(value = ["name"])])
data class AssetStatusEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncState: SyncState = SyncState.PENDING,
)
