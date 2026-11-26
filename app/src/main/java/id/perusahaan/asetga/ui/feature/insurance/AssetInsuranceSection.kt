package id.perusahaan.asetga.ui.feature.insurance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
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
import id.perusahaan.asetga.domain.model.InsurancePolicy
import id.perusahaan.asetga.domain.repository.InsuranceRepository
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.navigation.AssetDetailRoute
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AssetInsuranceViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repo: InsuranceRepository) : ViewModel() {
    private val assetId = savedStateHandle.toRoute<AssetDetailRoute>().id
    val policies: StateFlow<List<InsurancePolicy>> = repo.observePoliciesForAsset(assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Bagian "Asuransi" di detail aset: polis yang menanggung aset ini. */
@Composable
fun AssetInsuranceSection(onOpenPolicy: (String) -> Unit, vm: AssetInsuranceViewModel = hiltViewModel()) {
    val policies by vm.policies.collectAsStateWithLifecycle()
    SectionHeader("Asuransi")
    if (policies.isEmpty()) {
        Text("Belum diasuransikan", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    policies.forEach { p ->
        ListItem(
            modifier = Modifier.clickable { onOpenPolicy(p.id) },
            overlineContent = { Text("${p.typeName} · ${p.insurerName}") },
            headlineContent = { Text(p.policyNumber) },
            supportingContent = { Text("s/d ${Fmt.date(p.endDate)}") },
            trailingContent = { PolicyStatusChip(p.status) },
        )
    }
}
