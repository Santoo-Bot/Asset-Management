package id.perusahaan.asetga.data.repository

import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.ServiceDao
import id.perusahaan.asetga.data.local.dao.StockDao
import id.perusahaan.asetga.domain.calc.Depreciation
import id.perusahaan.asetga.domain.calc.ServiceDueCalc
import id.perusahaan.asetga.domain.calc.ServiceDueStatus
import id.perusahaan.asetga.domain.calc.WarrantyCalc
import id.perusahaan.asetga.domain.model.ActionItem
import id.perusahaan.asetga.domain.model.ActionKind
import id.perusahaan.asetga.domain.model.ActionTarget
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.ChartBar
import id.perusahaan.asetga.domain.model.DashboardSummary
import id.perusahaan.asetga.domain.model.ReminderSummary
import id.perusahaan.asetga.domain.repository.DashboardRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepositoryImpl @Inject constructor(
    private val assetDao: AssetDao,
    private val serviceDao: ServiceDao,
    private val stockDao: StockDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : DashboardRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSummary(): Flow<DashboardSummary> =
        combine(assetDao.observeTick(), serviceDao.observeTick(), stockDao.observeTick(), settings.settings) { _, _, _, s -> s.defaultResidualPercent }
            .conflate()
            .mapLatest { load(it) }
            .flowOn(Dispatchers.Default)

    private suspend fun load(defaultResidual: Double): DashboardSummary {
        val today = LocalDate.now(clock)
        val totals = assetDao.activeTotals()

        // Nilai buku dihitung di Kotlin dari proyeksi ringan (5.000 baris tetap cepat).
        val bookValue = assetDao.valuationRows().sumOf { r ->
            Depreciation.calculate(r.acquisitionCost, r.acquisitionDate, r.usefulLifeYears, r.residualPercent ?: defaultResidual, today).bookValue
        }

        val warrantyTo = today.plusDays(WarrantyCalc.EXPIRING_DAYS)
        val warrantyRows = assetDao.warrantyBetween(today, warrantyTo, ACTION_LIMIT)
        val schedules = serviceDao.schedules()
        val overdue = schedules.filter { ServiceDueCalc.status(it.nextServiceDate, today) == ServiceDueStatus.OVERDUE }
        val dueSoon = schedules.filter { ServiceDueCalc.status(it.nextServiceDate, today) == ServiceDueStatus.DUE_SOON }
        val reorder = stockDao.reorderItems()
        val damagedRows = assetDao.damaged(ACTION_LIMIT)

        val conditionCounts = assetDao.countByCondition().associate { it.label to it.count }
        val actions = buildList {
            overdue.forEach {
                add(ActionItem(ActionKind.SERVICE_OVERDUE, "${it.assetCode} · ${it.assetName}", "Servis terlambat sejak ${Fmt.date(it.nextServiceDate)}", it.nextServiceDate, ActionTarget.AssetTarget(it.assetId)))
            }
            warrantyRows.forEach {
                val days = WarrantyCalc.daysLeft(it.warrantyUntil!!, today)
                add(ActionItem(ActionKind.WARRANTY_EXPIRING, "${it.code} · ${it.name}", "Garansi habis ${Fmt.date(it.warrantyUntil)} ($days hari lagi)", it.warrantyUntil, ActionTarget.AssetTarget(it.id)))
            }
            reorder.forEach {
                add(ActionItem(ActionKind.REORDER, "${it.code} · ${it.name}", "Stok ${Fmt.qty(it.currentStock)} ${it.unitName} (min ${Fmt.qty(it.minStock)})", null, ActionTarget.ItemTarget(it.id)))
            }
            dueSoon.forEach {
                add(ActionItem(ActionKind.SERVICE_DUE, "${it.assetCode} · ${it.assetName}", "Servis jatuh tempo ${Fmt.date(it.nextServiceDate)}", it.nextServiceDate, ActionTarget.AssetTarget(it.assetId)))
            }
            damagedRows.forEach {
                add(ActionItem(ActionKind.DAMAGED, "${it.code} · ${it.name}", "${it.condition.label} · ${it.locationName}", null, ActionTarget.AssetTarget(it.id)))
            }
        }

        return DashboardSummary(
            totalAssets = totals.total,
            totalCost = totals.cost,
            totalBookValue = bookValue,
            damaged = assetDao.countDamaged(),
            warrantyExpiring = assetDao.countWarrantyBetween(today, warrantyTo),
            serviceOverdue = overdue.size,
            serviceDueSoon = dueSoon.size,
            reorderItems = reorder.size,
            byCategory = assetDao.countByCategory().map { ChartBar(it.label, it.count) },
            byCondition = AssetCondition.entries.map { ChartBar(it.label, conditionCounts[it.name] ?: 0) },
            actions = actions,
        )
    }

    override suspend fun reminderSummary(): ReminderSummary {
        val today = LocalDate.now(clock)
        val schedules = serviceDao.schedules()
        return ReminderSummary(
            warrantyExpiring = assetDao.countWarrantyBetween(today, today.plusDays(WarrantyCalc.EXPIRING_DAYS)),
            serviceOverdue = schedules.count { ServiceDueCalc.status(it.nextServiceDate, today) == ServiceDueStatus.OVERDUE },
            serviceDueSoon = schedules.count { ServiceDueCalc.status(it.nextServiceDate, today) == ServiceDueStatus.DUE_SOON },
            reorderItems = stockDao.reorderCount(),
        )
    }

    private companion object {
        const val ACTION_LIMIT = 30
    }
}
