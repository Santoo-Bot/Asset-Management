package id.perusahaan.asetga.data.seed

import androidx.room.withTransaction
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AppSettingEntity
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.DepartmentEntity
import id.perusahaan.asetga.data.local.entity.DivisionEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.data.local.entity.VendorEntity
import id.perusahaan.asetga.domain.calc.AssetCodeGenerator
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random
import javax.inject.Inject

/** Data contoh untuk build debug saja. Dijalankan sekali (ditandai di app_settings). */
class DebugSampleDataSeeder @Inject constructor(
    private val db: AppDatabase,
) : SampleDataSeeder {

    override suspend fun seedIfNeeded() {
        seedMasters()
        seedDivisions()
        seedAssets()
    }

    /** Divisi contoh + tautan departemen → divisi (juga untuk data contoh lama dari versi sebelumnya). */
    private suspend fun seedDivisions() {
        if (db.settingsDao().get(KEY_DIVISIONS) != null) return
        val now = System.currentTimeMillis()
        val plan = mapOf(
            "Operasional" to listOf("Operasional", "General Affair", "IT"),
            "Keuangan & SDM" to listOf("Keuangan", "HRD"),
            "Komersial" to listOf("Pemasaran"),
            "Direksi" to listOf("Direksi"),
        )
        db.withTransaction {
            val departments = db.departmentDao().observeAll(true).first()
            plan.forEach { (divisionName, deptNames) ->
                val divisionId = UUID.randomUUID().toString()
                db.divisionDao().insert(DivisionEntity(divisionId, divisionName, true, now, now))
                departments.filter { it.name in deptNames && it.divisionId == null }.forEach {
                    db.departmentDao().update(it.copy(divisionId = divisionId, updatedAt = now))
                }
            }
            db.openHelper.writableDatabase.execSQL(
                "UPDATE assets SET divisionId = (SELECT d.divisionId FROM departments d WHERE d.id = assets.departmentId) " +
                    "WHERE divisionId IS NULL AND departmentId IS NOT NULL",
            )
            db.settingsDao().put(AppSettingEntity(KEY_DIVISIONS, now.toString()))
        }
    }

    private suspend fun seedMasters() {
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

    /** 5.000 aset contoh untuk menguji performa daftar (Paging 3) dan dashboard. */
    private suspend fun seedAssets() {
        if (db.settingsDao().get(KEY_ASSETS) != null) return
        val categories = db.categoryDao().observeAll(false).first()
        val locations = db.locationDao().observeAll(false).first()
        val departments = db.departmentDao().observeAll(false).first()
        val deptDivision = departments.associate { it.id to it.divisionId }
        if (categories.isEmpty() || locations.isEmpty()) return

        val names = mapOf(
            "IT" to listOf("Laptop Dell Latitude 5440", "Laptop Lenovo ThinkPad E14", "PC Desktop HP ProDesk", "Monitor LG 24\"", "Printer Epson L3210", "Proyektor Epson EB-X51", "Router MikroTik", "Switch Cisco 24 Port", "Scanner Canon", "UPS APC 1200VA"),
            "FUR" to listOf("Meja Kerja", "Kursi Ergonomis", "Lemari Arsip Besi", "Meja Rapat 8 Orang", "Sofa Tamu", "Rak Buku", "Filing Cabinet 4 Laci"),
            "KND" to listOf("Toyota Avanza", "Honda Vario 125", "Mitsubishi L300", "Toyota Innova Zenix", "Daihatsu Gran Max"),
            "PK" to listOf("Mesin Fotokopi Canon", "Mesin Penghancur Kertas", "Telepon IP", "Brankas", "Mesin Absensi Sidik Jari", "Dispenser Air"),
            "MSN" to listOf("Genset 20 kVA", "AC Split Daikin 1 PK", "AC Split Panasonic 2 PK", "Pompa Air", "Kompresor"),
            "BGN" to listOf("Instalasi Listrik Lt. 1", "Partisi Ruang Rapat", "CCTV System", "Instalasi Jaringan LAN"),
            "LN" to listOf("Kamera Dokumentasi", "Papan Tulis Kaca", "Tangga Lipat Aluminium"),
        )
        val pics = listOf("Andi", "Budi", "Citra", "Dewi", "Eko", "Fajar", "Gita", "Hendra", "Indah", "Joko", null)
        val conditions = AssetCondition.entries
        val rnd = Random(42)
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val sequences = mutableMapOf<String, Int>()

        val all = (1..TOTAL_ASSETS).map {
            val cat = categories[rnd.nextInt(categories.size)]
            val acq = today.minusDays(rnd.nextLong(15, 365L * 9))
            val key = "${cat.codePrefix}-${acq.year}"
            val seq = (sequences[key] ?: 0) + 1
            sequences[key] = seq
            val baseCost = when (cat.codePrefix) {
                "KND" -> 150_000_000L; "BGN" -> 80_000_000L; "MSN" -> 25_000_000L; "IT" -> 12_000_000L; else -> 3_000_000L
            }
            val roll = rnd.nextInt(100)
            AssetEntity(
                id = UUID.randomUUID().toString(),
                code = AssetCodeGenerator.format(cat.codePrefix, acq.year, seq),
                name = names[cat.codePrefix].orEmpty().ifEmpty { listOf(cat.name) }.let { it[rnd.nextInt(it.size)] },
                categoryId = cat.id,
                brandModel = null,
                serialNumber = if (cat.codePrefix == "IT") "SN" + rnd.nextInt(10_000_000, 99_999_999) else null,
                acquisitionDate = acq,
                acquisitionCost = (baseCost * (0.5 + rnd.nextDouble())).toLong() / 1000 * 1000,
                locationId = locations[rnd.nextInt(locations.size)].id,
                departmentId = departments.getOrNull(rnd.nextInt(departments.size + 1))?.id,
                picName = pics[rnd.nextInt(pics.size)],
                condition = when {
                    roll < 70 -> AssetCondition.BAIK
                    roll < 88 -> AssetCondition.CUKUP
                    roll < 96 -> AssetCondition.RUSAK_RINGAN
                    else -> conditions.last()
                },
                status = when {
                    roll < 85 -> AssetStatus.AKTIF
                    roll < 90 -> AssetStatus.DISIMPAN
                    roll < 94 -> AssetStatus.DIPINJAM
                    roll < 97 -> AssetStatus.DALAM_PERBAIKAN
                    else -> AssetStatus.DIHAPUS_DIJUAL
                },
                // Sebagian aset garansinya segera habis agar dashboard & notifikasi punya data.
                warrantyUntil = if (rnd.nextInt(3) == 0) today.plusDays(rnd.nextLong(-200, 400)) else null,
                createdAt = now,
                updatedAt = now,
            )
        }.map { if (it.status == AssetStatus.DIHAPUS_DIJUAL) it.copy(disposalDate = today.minusDays(rnd.nextLong(1, 300))) else it }
            .map { a -> a.copy(divisionId = a.departmentId?.let(deptDivision::get)) }

        db.withTransaction {
            all.chunked(500).forEach { db.assetDao().insertAll(it) }
            db.settingsDao().put(AppSettingEntity(KEY_ASSETS, now.toString()))
        }
    }

    private companion object {
        const val KEY = "debug_seed_v1"
        const val KEY_ASSETS = "debug_seed_assets_v1"
        const val KEY_DIVISIONS = "debug_seed_divisions_v1"
        const val TOTAL_ASSETS = 5_000
    }
}
