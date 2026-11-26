package id.perusahaan.asetga.data.seed

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.VendorEntity
import java.util.UUID
import javax.inject.Inject

/** Data contoh untuk build debug saja. Dijalankan sekali (ditandai di app_settings). */
class DebugSampleDataSeeder @Inject constructor(
    private val db: AppDatabase,
) : SampleDataSeeder {

    override suspend fun seedIfNeeded() {
        if (db.settingsDao().get(KEY) != null) return
        val now = System.currentTimeMillis()
        fun id() = UUID.randomUUID().toString()
        db.withTransaction {
            listOf(
                "Kantor Pusat Lt. 1" to "Jl. Jend. Sudirman No. 1, Jakarta",
                "Kantor Pusat Lt. 2" to "Jl. Jend. Sudirman No. 1, Jakarta",
                "Kantor Pusat Lt. 3" to "Jl. Jend. Sudirman No. 1, Jakarta",
                "Gudang Cikarang" to "Kawasan Industri Jababeka",
                "Cabang Surabaya" to "Jl. Basuki Rahmat No. 10",
                "Cabang Bandung" to "Jl. Asia Afrika No. 5",
            ).forEach { (n, a) -> db.locationDao().insert(LocationEntity(id(), n, a, true, now, now)) }

            listOf("Keuangan", "HRD", "General Affair", "IT", "Operasional", "Pemasaran", "Direksi")
                .forEach { db.departmentDao().insert(DepartmentEntity(id(), it, true, now, now)) }

            listOf(
                Triple("PT Sinar Komputindo", "021-5550101", "servis@sinarkomputindo.co.id"),
                Triple("CV Mebel Jaya", "021-5550202", null),
                Triple("PT Dingin Sejuk AC", "021-5550303", "cs@dinginsejuk.co.id"),
                Triple("Bengkel Mobil Prima", "021-5550404", null),
            ).forEach { (n, p, e) -> db.vendorDao().insert(VendorEntity(id(), n, p, e, null, true, now, now)) }

            db.settingsDao().put(AppSettingEntity(KEY, now.toString()))
        }
    }

    private companion object {
        const val KEY = "debug_seed_v1"
    }
}
