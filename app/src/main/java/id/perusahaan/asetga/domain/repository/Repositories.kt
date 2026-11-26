package id.perusahaan.asetga.domain.repository

import androidx.paging.PagingData
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Asset
import id.perusahaan.asetga.domain.model.AssetDetail
import id.perusahaan.asetga.domain.model.AssetFilter
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.AssetListItem
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.MeasureUnit
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.ThemeMode
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.model.Vendor
import kotlinx.coroutines.flow.Flow

/*
 * Kontrak repository yang dipakai lapisan UI/use case. Implementasi saat ini memakai Room
 * (lihat data/repository). Di fase sinkronisasi cukup menambah implementasi yang
 * menggabungkan sumber lokal + remote tanpa mengubah layar.
 */

interface MasterDataRepository {
    fun observeCategories(includeInactive: Boolean = false): Flow<List<Category>>
    fun observeLocations(includeInactive: Boolean = false): Flow<List<Location>>
    fun observeDepartments(includeInactive: Boolean = false): Flow<List<Department>>
    fun observeVendors(includeInactive: Boolean = false): Flow<List<Vendor>>
    fun observeUnits(includeInactive: Boolean = false): Flow<List<MeasureUnit>>

    suspend fun saveCategory(category: Category): AppResult<Category>
    suspend fun saveLocation(location: Location): AppResult<Location>
    suspend fun saveDepartment(department: Department): AppResult<Department>
    suspend fun saveVendor(vendor: Vendor): AppResult<Vendor>
    suspend fun saveUnit(unit: MeasureUnit): AppResult<MeasureUnit>

    /** Soft delete: master tidak pernah dihapus, hanya dinonaktifkan. */
    suspend fun setActive(type: MasterType, id: String, active: Boolean): AppResult<Unit>
}

interface UserRepository {
    fun observeUsers(): Flow<List<User>>
    fun observeUserCount(): Flow<Int>
    suspend fun getUser(id: String): User?
    suspend fun authenticate(username: String, password: String): AppResult<User>
    suspend fun verifyPin(userId: String, pin: String): Boolean
    suspend fun createUser(username: String, fullName: String, role: Role, password: String): AppResult<User>
    suspend fun updateUser(id: String, fullName: String, role: Role, isActive: Boolean): AppResult<User>
    suspend fun changePassword(id: String, newPassword: String): AppResult<Unit>
    suspend fun setPin(id: String, pin: String?): AppResult<Unit>
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appLockEnabled: Boolean = true,
    val lastUserId: String? = null,
    val defaultResidualPercent: Double = 0.0,
    val reminderHour: Int = 8,
    val notificationsEnabled: Boolean = true,
    /** Ditampilkan pada label QR, mis. "Milik PT ...". */
    val companyName: String = "",
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setLastUserId(id: String?)
    suspend fun setDefaultResidualPercent(percent: Double): AppResult<Unit>
    suspend fun setReminderHour(hour: Int)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setCompanyName(name: String)
}

interface AuditLogRepository {
    suspend fun log(
        entityType: AuditEntityType,
        entityId: String,
        action: AuditAction,
        summary: String,
        changes: Map<String, Pair<String?, String?>> = emptyMap(),
    )

    fun paging(entityType: AuditEntityType?, query: String): Flow<PagingData<AuditLogEntry>>
    fun observeForEntity(entityType: AuditEntityType, entityId: String): Flow<List<AuditLogEntry>>
}

interface AssetRepository {
    fun paging(filter: AssetFilter): Flow<PagingData<AssetListItem>>
    fun count(filter: AssetFilter): Flow<Int>
    fun observeDetail(id: String): Flow<AssetDetail?>
    suspend fun get(id: String): Asset?
    suspend fun findByCode(code: String): Asset?
    suspend fun getListItems(ids: List<String>): List<AssetListItem>
    /** Usulan kode otomatis PREFIX-TAHUN-NNN berdasarkan kategori & tahun perolehan. */
    suspend fun suggestCode(categoryId: String, year: Int): String
    suspend fun save(input: AssetInput): AppResult<Asset>
}
