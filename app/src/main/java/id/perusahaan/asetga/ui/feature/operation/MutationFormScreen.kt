package id.perusahaan.asetga.ui.feature.operation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.validation.OperationValidator
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.LabeledValue
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.components.SelectField
import id.perusahaan.asetga.ui.feature.asset.InfoCard

@Composable
fun MutationFormScreen(onBack: () -> Unit, onSaved: () -> Unit, vm: MutationFormViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val departments by vm.departments.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.results.collect { msg -> if (msg == null) onSaved() else snackbar.showSnackbar(msg) } }
    val input = state.input
    val err = state.errors

    Scaffold(
        topBar = { AppTopBar("Mutasi aset", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomActionBar("Simpan mutasi", vm::save, loading = state.saving, enabled = state.asset != null, modifier = Modifier.imePadding()) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.asset?.let { d ->
                InfoCard("Posisi saat ini · ${d.asset.code}") {
                    Text(d.asset.name, style = MaterialTheme.typography.titleMedium)
                    LabeledValue("Lokasi", d.locationName)
                    LabeledValue("Departemen", d.departmentName ?: "-")
                    LabeledValue("PIC", d.asset.picName ?: "-")
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeader("Tujuan", Modifier.padding(start = 0.dp))
                SelectField(
                    "Lokasi tujuan", locations, locations.firstOrNull { it.id == input.toLocationId }, { it.name },
                    onSelect = { l -> vm.update { it.copy(toLocationId = l?.id) } }, required = true,
                    error = err[OperationValidator.FIELD_TARGET],
                )
                SelectField(
                    "Departemen tujuan", departments, departments.firstOrNull { it.id == input.toDepartmentId }, { it.name },
                    onSelect = { d -> vm.update { it.copy(toDepartmentId = d?.id) } }, noneLabel = "Tidak ada",
                )
                FormTextField(input.toPic, { v -> vm.update { it.copy(toPic = v) } }, "PIC tujuan")
                SectionHeader("Keterangan", Modifier.padding(start = 0.dp))
                DateField(input.date, { d -> vm.update { it.copy(date = d) } }, "Tanggal mutasi", required = true, error = err[OperationValidator.FIELD_DATE])
                FormTextField(
                    input.reason, { v -> vm.update { it.copy(reason = v) } }, "Alasan", required = true,
                    error = err[OperationValidator.FIELD_REASON], singleLine = false, minLines = 2,
                )
                FormTextField(input.approvedBy, { v -> vm.update { it.copy(approvedBy = v) } }, "Disetujui oleh")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
