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
import id.perusahaan.asetga.domain.model.ServiceType
import id.perusahaan.asetga.domain.validation.OperationValidator
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.MoneyField
import id.perusahaan.asetga.ui.components.PhotoPicker
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.components.SelectField

@Composable
fun ServiceFormScreen(onBack: () -> Unit, onSaved: () -> Unit, vm: ServiceFormViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val vendors by vm.vendors.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.results.collect { msg -> if (msg == null) onSaved() else snackbar.showSnackbar(msg) } }
    val input = state.input
    val err = state.errors

    Scaffold(
        topBar = { AppTopBar(if (state.isNew) "Catat servis" else "Ubah catatan servis", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomActionBar("Simpan servis", vm::save, loading = state.saving, enabled = !state.photoBusy, modifier = Modifier.imePadding()) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(state.assetLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            SectionHeader("Pekerjaan", Modifier.padding(start = 0.dp))
            DateField(input.serviceDate, { d -> vm.update { it.copy(serviceDate = d) } }, "Tanggal servis", required = true, error = err[OperationValidator.FIELD_DATE])
            SelectField(
                "Jenis", ServiceType.entries, input.type, { it.label },
                onSelect = { t -> if (t != null) vm.update { it.copy(type = t) } }, required = true,
            )
            SelectField(
                "Vendor", vendors, vendors.firstOrNull { it.id == input.vendorId }, { it.name },
                onSelect = { v -> vm.update { it.copy(vendorId = v?.id) } }, noneLabel = "Internal / tanpa vendor",
            )
            MoneyField(input.cost, { v -> vm.update { it.copy(cost = v ?: 0) } }, "Biaya", error = err[OperationValidator.FIELD_COST])
            FormTextField(
                input.description, { v -> vm.update { it.copy(description = v) } }, "Deskripsi pekerjaan",
                required = true, error = err[OperationValidator.FIELD_DESCRIPTION], singleLine = false, minLines = 3,
            )
            SectionHeader("Jadwal berikutnya", Modifier.padding(start = 0.dp))
            DateField(
                input.nextServiceDate, { d -> vm.update { it.copy(nextServiceDate = d) } }, "Servis berikutnya",
                error = err[OperationValidator.FIELD_NEXT], supporting = "Pengingat muncul 30 hari sebelum jatuh tempo",
            )
            SectionHeader("Foto (opsional)", Modifier.padding(start = 0.dp))
            PhotoPicker(
                photoFile = vm.photoFile(), busy = state.photoBusy, newCameraUri = vm::newCameraUri,
                onPicked = vm::onPhotoPicked, onRemove = vm::removePhoto,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
