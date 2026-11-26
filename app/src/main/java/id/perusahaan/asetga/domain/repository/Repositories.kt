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
import id.perusahaan.asetga.domain.model.AddAssetsResult
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.InsurancePolicy
import id.perusahaan.asetga.domain.model.InsuranceImportPreview
import id.perusahaan.asetga.domain.model.InsuranceType
import id.perusahaan.asetga.domain.model.InsuredAsset
import id.perusahaan.asetga.domain.model.Insurer
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.model.PurgeResult
import id.perusahaan.asetga.domain.model.OpnameItem
import id.perusahaan.asetga.domain.model.OpnameScanOutcome
import id.perusahaan.asetga.domain.model.OpnameSession
import id.perusahaan.asetga.domain.model.DashboardSummary
import id.perusahaan.asetga.domain.model.ReminderSummary
import id.perusahaan.asetga.domain.model.AssetMutation
import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.model.MutationInput
import id.perusahaan.asetga.domain.model.ServiceInput
import id.perusahaan.asetga.domain.model.ServiceRecord
import id.perusahaan.asetga.domain.model.ServiceSchedule
import id.perusahaan.asetga.domain.model.StockItem
import id.perusahaan.asetga.domain.model.StockTxn
import id.perusahaan.asetga.domain.model.StockTxnInput
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.MeasureUnit
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.ThemeMode
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.model.Vendor
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/*
 * Kontrak repository yang dipakai lapisan UI/use case. Implementasi saat ini memakai Room
 * (lihat data/repository). Di fase sinkronisasi cukup menambah implementasi yang
 * menggabungkan sumber lokal + remote tanpa mengubah layar.
 */

interface MasterDataRepository {
    fun observeCategories(includeInactive: Boolean = false): Flow<List<Category>>
    fun observeLocations(includeInactive: Boolean = false): Flow<List<Location>>
    fun observeDivisions(includeInactive: Boolean = false): Flow<List<Division>>
    fun observeInsuranceTypes(includeInactive: Boolean = false): Flow<List<InsuranceType>>
    fun observeInsurers(includeInactive: Boolean = false): Flow<List<Insurer>>
    fun observeDepartments(includeInactive: Boolean = false): Flow<List<Department>>
    fun observeVendors(includeInactive: Boolean = false): Flow<List<Vendor>>
    fun observeUnits(includeInactive: Boolean = false): Flow<List<MeasureUnit>>

    suspend fun saveCategory(category: Category): AppResult<Category>
    suspend fun saveLocation(location: Location): AppResult<Location>
    suspend fun saveDivision(division: Division): AppResult<Division>
    suspend fun saveInsuranceType(type: InsuranceType): AppResult<InsuranceType>
    suspend fun saveInsurer(insurer: Insurer): AppResult<Insurer>
    suspend fun saveDepartment(department: Department): AppResult<Department>
    suspend fun saveVendor(vendor: Vendor): AppResult<Vendor>
    suspend fun saveUnit(unit: MeasureUnit): AppResult<MeasureUnit>

    /** Soft delete: master tidak pernah dihapus, hanya dinonaktifkan. */
    /**
     * Hapus permanen data master yang belum pernah dipakai. Bila masih dipakai (aset, stok, riwayat),
     * ditolak dengan pesan rincian pemakaiannya.
     */
    suspend fun delete(type: MasterType, id: String): AppResult<Unit>
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
    /** Hapus akun secara permanen. Tidak bisa menghapus diri sendiri atau Admin aktif terakhir. */
    suspend fun deleteUser(id: String): AppResult<Unit>
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

    /** Catat banyak entri sekaligus (untuk operasi massal ribuan aset). Tiap pasangan = (entityId, ringkasan). */
    suspend fun logBatch(entityType: AuditEntityType, action: AuditAction, entries: List<Pair<String, String>>)

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
    /** Semua id aset yang cocok dengan filter (tanpa paging). */
    suspend fun filteredIds(filter: AssetFilter): List<String>
    /** Usulan kode otomatis PREFIX-TAHUN-NNN berdasarkan kategori & tahun perolehan. */
    suspend fun suggestCode(categoryId: String, year: Int): String
    suspend fun save(input: AssetInput): AppResult<Asset>

    /** Tandai aset Write Off (aset tetap tercatat, penyusutan berhenti di [date]). Admin & Staf. */
    suspend fun dispose(id: String, date: LocalDate, value: Long?, note: String): AppResult<Unit>

    /**
     * Hapus aset secara PERMANEN beserta riwayat mutasi, servis, catatan opname, dan keanggotaan polisnya.
     * Khusus Admin GA, wajib alasan (tercatat di audit log).
     */
    suspend fun delete(ids: List<String>, reason: String): AppResult<PurgeResult>
}

interface OperationRepository {
    fun observeMutations(assetId: String): Flow<List<AssetMutation>>
    suspend fun recordMutation(input: MutationInput): AppResult<Unit>

    fun observeServices(assetId: String): Flow<List<ServiceRecord>>
    fun pagingServices(query: String): Flow<PagingData<ServiceRecord>>
    fun observeSchedules(): Flow<List<ServiceSchedule>>
    suspend fun getService(id: String): ServiceRecord?
    suspend fun saveService(input: ServiceInput): AppResult<Unit>
}

interface StockRepository {
    fun observeItems(query: String, reorderOnly: Boolean, includeInactive: Boolean = false): Flow<List<StockItem>>
    fun observeItem(id: String): Flow<StockItem?>
    fun observeTxns(itemId: String): Flow<List<StockTxn>>
    fun observeReorderCount(): Flow<Int>
    suspend fun findByCodeOrBarcode(code: String): StockItem?
    suspend fun saveItem(input: ItemInput): AppResult<String>
    /** Hapus barang secara permanen beserta riwayat transaksinya. Khusus Admin GA. */
    suspend fun deleteItem(id: String): AppResult<Unit>
    suspend fun recordTxn(input: StockTxnInput): AppResult<Unit>
}

interface DashboardRepository {
    /** Dihitung ulang setiap kali data aset, servis, atau stok berubah. */
    fun observeSummary(): Flow<DashboardSummary>
    suspend fun reminderSummary(): ReminderSummary
}

interface OpnameRepository {
    fun observeSessions(): Flow<List<OpnameSession>>
    fun observeSession(id: String): Flow<OpnameSession?>
    fun observeItems(sessionId: String): Flow<List<OpnameItem>>
    fun observeAssetIds(sessionId: String): Flow<Set<String>>

    /** Langkah 1: buat sesi (nama & tanggal). */
    suspend fun create(name: String, date: java.time.LocalDate, note: String?): AppResult<String>

    /** Langkah 2: tambah aset ke daftar sesi (manual / upload). Aset Write Off dilewati. */
    suspend fun addAssets(sessionId: String, assetIds: List<String>): AppResult<id.perusahaan.asetga.domain.model.OpnameAddResult>
    suspend fun previewImport(sessionId: String, sheet: List<List<String>>): id.perusahaan.asetga.domain.model.OpnameImportPreview
    suspend fun commitImport(sessionId: String, preview: id.perusahaan.asetga.domain.model.OpnameImportPreview): AppResult<id.perusahaan.asetga.domain.model.OpnameAddResult>
    suspend fun removeItem(sessionId: String, itemId: String): AppResult<Unit>

    /** Langkah 3: cek fisik (scan atau tandai manual). */
    suspend fun scan(sessionId: String, code: String): OpnameScanOutcome
    suspend fun updateItem(sessionId: String, itemId: String, found: Boolean, condition: id.perusahaan.asetga.domain.model.AssetCondition?, note: String?): AppResult<Unit>

    /** Langkah 4: selesaikan sesi; laporan dibuat dari hasilnya. */
    suspend fun finish(sessionId: String, note: String): AppResult<Unit>

    /** Hapus sesi beserta daftar & hasilnya (khusus Admin). */
    suspend fun delete(sessionId: String): AppResult<Unit>
}

interface InsuranceRepository {
    fun observePolicies(): Flow<List<InsurancePolicy>>
    fun observePolicy(id: String): Flow<InsurancePolicy?>
    fun observePoliciesForAsset(assetId: String): Flow<List<InsurancePolicy>>
    fun observeInsuredAssets(policyId: String): Flow<List<InsuredAsset>>
    fun observeAssetIdsInPolicy(policyId: String): Flow<Set<String>>
    suspend fun getPolicyInput(id: String): PolicyInput?
    suspend fun savePolicy(input: PolicyInput): AppResult<String>
    /** Hapus polis secara permanen (aset tidak ikut terhapus). */
    suspend fun deletePolicy(id: String): AppResult<Unit>

    suspend fun addAssets(policyId: String, assetIds: List<String>): AppResult<AddAssetsResult>
    /** Tambah aset dari hasil scan (kode aset atau kode QR). */
    suspend fun addAssetByCode(policyId: String, code: String): AppResult<String>
    suspend fun removeAsset(policyId: String, assetId: String): AppResult<Unit>
    suspend fun setSumInsured(policyId: String, assetId: String, value: Long?): AppResult<Unit>

    suspend fun previewImport(policyId: String, sheet: List<List<String>>): InsuranceImportPreview
    suspend fun commitImport(policyId: String, preview: InsuranceImportPreview): AppResult<AddAssetsResult>
}
