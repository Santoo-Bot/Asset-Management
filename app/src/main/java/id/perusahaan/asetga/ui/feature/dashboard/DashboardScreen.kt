package id.perusahaan.asetga.ui.feature.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.worker.ReminderNotifier
import id.perusahaan.asetga.domain.model.ActionItem
import id.perusahaan.asetga.domain.model.ActionKind
import id.perusahaan.asetga.domain.model.ActionTarget
import id.perusahaan.asetga.domain.model.ChartBar
import id.perusahaan.asetga.domain.model.DashboardSummary
import id.perusahaan.asetga.domain.repository.DashboardRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repo: DashboardRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val summary: StateFlow<DashboardSummary?> = repo.observeSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val notificationsEnabled: StateFlow<Boolean> = settings.settings.map { it.notificationsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

@Composable
fun DashboardScreen(
    userName: String,
    onOpenAsset: (String) -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenAssets: () -> Unit,
    onOpenStock: () -> Unit,
    onOpenServices: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    NotificationPermissionRequest(enabled = notificationsEnabled)

    Scaffold(topBar = { AppTopBar("Beranda") }) { padding ->
        val s = summary
        if (s == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    "Halo, ${userName.substringBefore(' ')}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
                Text(
                    "Ringkasan aset & inventaris per ${Fmt.date(java.time.LocalDate.now())}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                StatGrid(s, onOpenAssets, onOpenStock, onOpenServices)
                SectionHeader("Aset per kategori")
                BarChart(s.byCategory, emptyText = "Belum ada aset")
                SectionHeader("Aset per kondisi")
                BarChart(s.byCondition, emptyText = "Belum ada aset")
                SectionHeader("Perlu tindakan (${s.actions.size})")
                if (s.actions.isEmpty()) {
                    Text(
                        "Semua aman. Tidak ada garansi yang segera habis, servis terlambat, atau stok di bawah minimum.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(s.actions, key = { "${it.kind}-${it.target}" }) { a ->
                ActionRow(a, onClick = {
                    when (val t = a.target) {
                        is ActionTarget.AssetTarget -> onOpenAsset(t.assetId)
                        is ActionTarget.ItemTarget -> onOpenItem(t.itemId)
                    }
                })
                HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }
}

private data class Stat(val label: String, val value: String, val icon: ImageVector, val tone: Tone, val onClick: (() -> Unit)?)
private enum class Tone { NEUTRAL, PRIMARY, WARNING, DANGER }

@Composable
private fun StatGrid(s: DashboardSummary, onOpenAssets: () -> Unit, onOpenStock: () -> Unit, onOpenServices: () -> Unit) {
    val stats = listOf(
        Stat("Total aset", Fmt.number(s.totalAssets.toLong()), Icons.Outlined.Inventory2, Tone.PRIMARY, onOpenAssets),
        Stat("Aset rusak", Fmt.number(s.damaged.toLong()), Icons.Outlined.ReportProblem, if (s.damaged > 0) Tone.WARNING else Tone.NEUTRAL, onOpenAssets),
        Stat("Nilai perolehan", Fmt.rupiah(s.totalCost), Icons.Outlined.Inventory2, Tone.NEUTRAL, null),
        Stat("Nilai buku", Fmt.rupiah(s.totalBookValue), Icons.Outlined.Inventory2, Tone.NEUTRAL, null),
        Stat("Garansi habis ≤90 hari", Fmt.number(s.warrantyExpiring.toLong()), Icons.Outlined.VerifiedUser, if (s.warrantyExpiring > 0) Tone.WARNING else Tone.NEUTRAL, null),
        Stat("Servis terlambat", Fmt.number(s.serviceOverdue.toLong()), Icons.Outlined.Build, if (s.serviceOverdue > 0) Tone.DANGER else Tone.NEUTRAL, onOpenServices),
        Stat("Servis jatuh tempo ≤30 hari", Fmt.number(s.serviceDueSoon.toLong()), Icons.Outlined.Schedule, if (s.serviceDueSoon > 0) Tone.WARNING else Tone.NEUTRAL, onOpenServices),
        Stat("Barang perlu reorder", Fmt.number(s.reorderItems.toLong()), Icons.Outlined.Inventory, if (s.reorderItems > 0) Tone.DANGER else Tone.NEUTRAL, onOpenStock),
    )
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { StatCard(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatCard(stat: Stat, modifier: Modifier) {
    val status = AppTheme.status
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (stat.tone) {
        Tone.NEUTRAL -> cs.surfaceContainerLow to cs.onSurface
        Tone.PRIMARY -> cs.primaryContainer to cs.onPrimaryContainer
        Tone.WARNING -> status.warningContainer to status.onWarningContainer
        Tone.DANGER -> status.dangerContainer to status.onDangerContainer
    }
    Card(
        modifier = modifier.then(if (stat.onClick != null) Modifier.clickable(onClick = stat.onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Column(Modifier.padding(14.dp).height(76.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stat.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stat.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stat.value,
                style = if (stat.value.length > 14) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Grafik batang horizontal satu seri: satu warna (primary), ujung data membulat 4dp,
 * nilai ditulis langsung di ujung batang dengan warna teks (bukan warna seri).
 */
@Composable
private fun BarChart(bars: List<ChartBar>, emptyText: String) {
    val max = bars.maxOfOrNull { it.value } ?: 0
    if (max == 0) {
        Text(emptyText, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val barColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bars.forEach { bar ->
            Row(
                Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "${bar.label}: ${bar.value} aset" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bar.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp).clip(RoundedCornerShape(4.dp)).background(track)) {
                    val fraction = bar.value.toFloat() / max
                    if (bar.value > 0) {
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceAtLeast(0.02f)).fillMaxHeight()
                                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(barColor),
                        )
                    }
                }
                Text(
                    Fmt.number(bar.value.toLong()), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(52.dp).padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(a: ActionItem, onClick: () -> Unit) {
    val status = AppTheme.status
    val (icon, tint) = when (a.kind) {
        ActionKind.SERVICE_OVERDUE -> Icons.Outlined.Build to status.danger
        ActionKind.WARRANTY_EXPIRING -> Icons.Outlined.VerifiedUser to status.warning
        ActionKind.REORDER -> Icons.Outlined.Inventory to status.danger
        ActionKind.SERVICE_DUE -> Icons.Outlined.Schedule to status.warning
        ActionKind.DAMAGED -> Icons.Outlined.ErrorOutline to status.warning
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = a.kind.label, tint = tint) },
        overlineContent = { Text(a.kind.label) },
        headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(a.subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray) },
    )
}

/** Android 13+: minta izin notifikasi sekali, hanya bila pengingat diaktifkan. */
@Composable
private fun NotificationPermissionRequest(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = androidx.compose.ui.platform.LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(enabled) {
        if (enabled && !asked && !ReminderNotifier.canNotify(context)) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
