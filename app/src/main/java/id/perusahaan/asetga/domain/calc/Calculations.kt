package id.perusahaan.asetga.domain.calc

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class DepreciationResult(
    /** Nilai residu (Rp) = harga × residu%. */
    val residualValue: Long,
    /** Nilai yang dapat disusutkan = harga − residu. */
    val depreciableAmount: Long,
    /** Penyusutan per tahun = harga × (1 − residu%) / umur ekonomis. */
    val annualDepreciation: Long,
    val monthsElapsed: Long,
    /** Tidak pernah melebihi [depreciableAmount]. */
    val accumulated: Long,
    val bookValue: Long,
) {
    val fullyDepreciated: Boolean get() = accumulated >= depreciableAmount
}

/** Penyusutan garis lurus, dihitung prorata per bulan penuh sejak tanggal perolehan. */
object Depreciation {
    fun calculate(
        cost: Long,
        acquisitionDate: LocalDate,
        usefulLifeYears: Int,
        residualPercent: Double,
        asOf: LocalDate,
    ): DepreciationResult {
        require(cost >= 0) { "Harga tidak boleh negatif" }
        val life = usefulLifeYears.coerceAtLeast(1)
        val residualPct = residualPercent.coerceIn(0.0, 100.0)
        val residualValue = (cost * residualPct / 100.0).roundToLong()
        val depreciable = cost - residualValue
        val annual = (depreciable.toDouble() / life).roundToLong()
        val months = if (asOf.isAfter(acquisitionDate)) ChronoUnit.MONTHS.between(acquisitionDate, asOf) else 0L
        val totalMonths = life * 12L
        // Dihitung dari nilai yang dapat disusutkan (bukan dari angka tahunan yang dibulatkan) agar tidak melebihi batas.
        val accumulated = if (months >= totalMonths) {
            depreciable
        } else {
            (depreciable.toDouble() * months / totalMonths).roundToLong().coerceAtMost(depreciable)
        }
        return DepreciationResult(
            residualValue = residualValue,
            depreciableAmount = depreciable,
            annualDepreciation = annual,
            monthsElapsed = months,
            accumulated = accumulated,
            bookValue = cost - accumulated,
        )
    }
}

data class AssetAge(val years: Int, val months: Int)

object AssetAgeCalc {
    fun of(acquisitionDate: LocalDate, asOf: LocalDate): AssetAge {
        if (!asOf.isAfter(acquisitionDate)) return AssetAge(0, 0)
        val p = Period.between(acquisitionDate, asOf)
        return AssetAge(p.years, p.months)
    }
}

enum class WarrantyStatus(val label: String) {
    NONE("Tidak ada"),
    ACTIVE("Aktif"),
    EXPIRING("Segera habis"),
    EXPIRED("Habis"),
}

object WarrantyCalc {
    const val EXPIRING_DAYS = 90L

    fun status(warrantyUntil: LocalDate?, today: LocalDate): WarrantyStatus = when {
        warrantyUntil == null -> WarrantyStatus.NONE
        warrantyUntil.isBefore(today) -> WarrantyStatus.EXPIRED
        ChronoUnit.DAYS.between(today, warrantyUntil) <= EXPIRING_DAYS -> WarrantyStatus.EXPIRING
        else -> WarrantyStatus.ACTIVE
    }

    fun daysLeft(warrantyUntil: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, warrantyUntil)
}

enum class ServiceDueStatus(val label: String) {
    NONE("Belum dijadwalkan"),
    OVERDUE("Terlambat"),
    DUE_SOON("Jatuh tempo"),
    SCHEDULED("Terjadwal"),
}

object ServiceDueCalc {
    const val DUE_SOON_DAYS = 30L

    fun status(nextServiceDate: LocalDate?, today: LocalDate): ServiceDueStatus = when {
        nextServiceDate == null -> ServiceDueStatus.NONE
        nextServiceDate.isBefore(today) -> ServiceDueStatus.OVERDUE
        ChronoUnit.DAYS.between(today, nextServiceDate) <= DUE_SOON_DAYS -> ServiceDueStatus.DUE_SOON
        else -> ServiceDueStatus.SCHEDULED
    }
}

object StockCalc {
    /** Stok akhir = stok awal + Σ masuk − Σ keluar. */
    fun current(initial: Double, totalIn: Double, totalOut: Double): Double = round2(initial + totalIn - totalOut)

    /** Status "Reorder" bila stok ≤ minimum. */
    fun needsReorder(current: Double, minStock: Double): Boolean = current <= minStock + EPS

    /** Barang keluar tidak boleh melebihi stok tersedia. */
    fun canTakeOut(current: Double, qty: Double): Boolean = qty > 0 && qty <= current + EPS

    fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    private const val EPS = 1e-9
}

/** Kode aset: PREFIX-TAHUN-NNN, mis. IT-2024-001. */
object AssetCodeGenerator {
    fun format(prefix: String, year: Int, sequence: Int): String =
        "${prefix.uppercase()}-$year-${sequence.toString().padStart(3, '0')}"

    /** Nomor urut berikutnya dari kode yang sudah ada untuk prefix & tahun yang sama. */
    fun nextSequence(prefix: String, year: Int, existingCodes: Collection<String>): Int {
        val head = "${prefix.uppercase()}-$year-"
        val max = existingCodes.asSequence()
            .filter { it.uppercase().startsWith(head) }
            .mapNotNull { it.substring(head.length).toIntOrNull() }
            .maxOrNull() ?: 0
        return max + 1
    }
}
