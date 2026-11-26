package id.perusahaan.asetga.data.file

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.DefaultData
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ResetScope(val label: String, val description: String) {
    OPERATIONAL(
        "Data operasional",
        "Aset & foto, mutasi, servis, stok & transaksi, stock opname, polis asuransi, dan audit log. Pengguna, master data, dan pengaturan tetap.",
    ),
    EVERYTHING(
        "Semua (seperti baru dipasang)",
        "Seluruh data termasuk pengguna & master data. Kategori dan satuan bawaan dibuat ulang, lalu Anda membuat akun Admin baru.",
    ),
}

/** Menghapus seluruh data sesuai [ResetScope]. Khusus Admin GA; tidak bisa dibatalkan. */
@Singleton
class DataResetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val audit: AuditLogRepository,
    private val session: SessionManager,
) {
    suspend fun reset(scope: ResetScope): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (session.currentUser?.role?.canManageMaster != true) return@withContext AppResult.Failure("Hanya Admin GA yang dapat menghapus seluruh data")
        val sql = db.openHelper.writableDatabase
        db.withTransaction {
            // Urutan anak → induk agar foreign key tidak dilanggar.
            OPERATIONAL_TABLES.forEach { sql.execSQL("DELETE FROM `$it`") }
            if (scope == ResetScope.EVERYTHING) {
                MASTER_TABLES.forEach { sql.execSQL("DELETE FROM `$it`") }
                // Penanda data contoh (debug) dipertahankan agar 5.000 aset contoh tidak terisi ulang.
                sql.execSQL("DELETE FROM app_settings WHERE `key` NOT LIKE 'debug_seed%'")
                DefaultData.seed(sql)
            }
        }
        File(context.filesDir, PhotoStorage.DIR).deleteRecursively()
        File(context.cacheDir, "shared").deleteRecursively()
        if (scope == ResetScope.EVERYTHING) {
            session.logout()
        } else {
            audit.log(AuditEntityType.SYSTEM, "reset", AuditAction.DELETE, "Seluruh data operasional dihapus")
        }
        AppResult.Success(Unit)
    }

    companion object {
        val OPERATIONAL_TABLES = listOf(
            "insurance_items", "insurance_policies", "audit_items", "audit_sessions", "stock_txns", "items",
            "service_records", "asset_mutations", "assets", "audit_logs",
        )
        val MASTER_TABLES = listOf(
            "insurance_types", "insurers", "departments", "divisions", "categories", "locations", "vendors", "units", "users",
        )
    }
}
