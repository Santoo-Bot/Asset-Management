package id.perusahaan.asetga.ui.feature.operation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.calc.ServiceDueCalc
import id.perusahaan.asetga.domain.model.AssetMutation
import id.perusahaan.asetga.domain.model.ServiceRecord
import id.perusahaan.asetga.domain.repository.OperationRepository
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.feature.asset.InfoCard
import id.perusahaan.asetga.ui.navigation.AssetDetailRoute
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AssetOperationsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ops: OperationRepository,
) : ViewModel() {
    private val assetId = savedStateHandle.toRoute<AssetDetailRoute>().id
    val mutations: StateFlow<List<AssetMutation>> = ops.observeMutations(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val services: StateFlow<List<ServiceRecord>> = ops.observeServices(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Tombol Mutasi & Servis di bar bawah detail aset. */
@Composable
fun AssetOperationActions(canEdit: Boolean, onMutation: () -> Unit, onService: () -> Unit) {
    if (!canEdit) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onMutation, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Mutasi")
        }
        FilledTonalButton(onClick = onService, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(Icons.Outlined.Build, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Catat servis")
        }
    }
}

@Composable
fun AssetOperationsSection(
    canEdit: Boolean,
    onEditService: (String) -> Unit,
    vm: AssetOperationsViewModel = hiltViewModel(),
) {
    val mutations by vm.mutations.collectAsStateWithLifecycle()
    val services by vm.services.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    val latestNext = services.firstOrNull()?.nextServiceDate
    InfoCard("Servis & perawatan") {
        val status = ServiceDueCalc.status(latestNext, today)
        Text(
            if (latestNext == null) "Belum ada jadwal servis" else "Servis berikutnya ${Fmt.date(latestNext)} · ${status.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = when (status) {
                id.perusahaan.asetga.domain.calc.ServiceDueStatus.OVERDUE -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            "Total biaya servis: ${Fmt.rupiah(services.sumOf { it.cost })} (${services.size} catatan)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader("Riwayat servis")
    if (services.isEmpty()) Text("Belum ada catatan servis", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    services.forEach { s ->
        Column(
            Modifier.fillMaxWidth().clickable(enabled = canEdit) { onEditService(s.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("${Fmt.date(s.serviceDate)} · ${s.type.label} · ${Fmt.rupiah(s.cost)}", style = MaterialTheme.typography.bodyMedium)
            Text(s.description, style = MaterialTheme.typography.bodySmall)
            Text(
                listOfNotNull(s.vendorName, s.nextServiceDate?.let { "berikutnya ${Fmt.date(it)}" }).joinToString(" · ").ifEmpty { "Internal" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }

    SectionHeader("Riwayat mutasi")
    if (mutations.isEmpty()) Text("Belum pernah dimutasi", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    mutations.forEach { m ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(Fmt.date(m.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (m.fromLocation != m.toLocation) Text("Lokasi: ${m.fromLocation ?: "-"} → ${m.toLocation ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            if (m.fromDepartment != m.toDepartment) Text("Departemen: ${m.fromDepartment ?: "-"} → ${m.toDepartment ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            if (m.fromPic != m.toPic) Text("PIC: ${m.fromPic ?: "-"} → ${m.toPic ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Alasan: ${m.reason}" + (m.approvedBy?.let { " · Disetujui: $it" } ?: "") + (m.createdBy?.let { " · oleh $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}
