package id.perusahaan.asetga.domain.model

import java.time.LocalDate

enum class ActionKind(val label: String) {
    SERVICE_OVERDUE("Servis terlambat"),
    WARRANTY_EXPIRING("Garansi segera habis"),
    REORDER("Stok perlu reorder"),
    SERVICE_DUE("Servis jatuh tempo"),
    DAMAGED("Aset rusak"),
    POLICY_EXPIRING("Polis asuransi segera berakhir"),
}

sealed interface ActionTarget {
    data class AssetTarget(val assetId: String) : ActionTarget
    data class ItemTarget(val itemId: String) : ActionTarget
    data class PolicyTarget(val policyId: String) : ActionTarget
}

data class ActionItem(
    val kind: ActionKind,
    val title: String,
    val subtitle: String,
    val date: LocalDate?,
    val target: ActionTarget,
)

data class ChartBar(val label: String, val value: Int)

data class DashboardSummary(
    val totalAssets: Int = 0,
    val totalCost: Long = 0,
    val totalBookValue: Long = 0,
    val damaged: Int = 0,
    val warrantyExpiring: Int = 0,
    val serviceOverdue: Int = 0,
    val serviceDueSoon: Int = 0,
    val reorderItems: Int = 0,
    val policiesExpiring: Int = 0,
    val byCategory: List<ChartBar> = emptyList(),
    val byCondition: List<ChartBar> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
)

/** Ringkasan pengingat untuk notifikasi harian. */
data class ReminderSummary(
    val warrantyExpiring: Int,
    val serviceOverdue: Int,
    val serviceDueSoon: Int,
    val reorderItems: Int,
    val policiesExpiring: Int = 0,
) {
    val isEmpty: Boolean get() = warrantyExpiring + serviceOverdue + serviceDueSoon + reorderItems + policiesExpiring == 0
}
