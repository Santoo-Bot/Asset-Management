package id.perusahaan.asetga.ui.feature.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.validation.AssetValidator
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.MoneyField
import id.perusahaan.asetga.ui.components.PhotoPicker
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.components.SelectField

@Composable
fun AssetFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    vm: AssetFormViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is AssetFormEvent.Saved -> onSaved(e.assetId)
                is AssetFormEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    val input = state.input
    val err = state.errors

    Scaffold(
        topBar = { AppTopBar(if (state.isNew) "Tambah aset" else "Ubah aset", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomActionBar("Simpan aset", vm::save, loading = state.saving, enabled = !state.loading && !state.photoBusy, modifier = Modifier.imePadding()) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("Foto", Modifier.padding(start = 0.dp))
            PhotoPicker(
                photoFile = vm.photoFile(),
                busy = state.photoBusy,
                newCameraUri = vm::newCameraUri,
                onPicked = vm::onPhotoPicked,
                onRemove = vm::removePhoto,
            )

            SectionHeader("Identitas", Modifier.padding(start = 0.dp))
            SelectField(
                label = "Kategori", options = options.categories,
                selected = options.categories.firstOrNull { it.id == input.categoryId },
                itemLabel = { "${it.name} (${it.codePrefix})" },
                onSelect = { c -> vm.update { it.copy(categoryId = c?.id) } },
                required = true, error = err[AssetValidator.FIELD_CATEGORY],
            )
            FormTextField(
                value = input.code, onValueChange = { v -> vm.update { it.copy(code = v.uppercase()) } },
                label = "Kode aset", error = err[AssetValidator.FIELD_CODE],
                supporting = when {
                    !state.isNew -> "Ubah hanya bila perlu; kode harus unik"
                    state.suggestedCode.isNotEmpty() -> "Kosongkan untuk kode otomatis: ${state.suggestedCode}"
                    else -> "Kosongkan untuk kode otomatis (KATEGORI-TAHUN-NOMOR)"
                },
            )
            FormTextField(input.name, { v -> vm.update { it.copy(name = v) } }, "Nama aset", required = true, error = err[AssetValidator.FIELD_NAME])
            FormTextField(input.brandModel, { v -> vm.update { it.copy(brandModel = v) } }, "Merk / tipe")
            FormTextField(input.serialNumber, { v -> vm.update { it.copy(serialNumber = v) } }, "No. seri")

            SectionHeader("Perolehan", Modifier.padding(start = 0.dp))
            DateField(input.acquisitionDate, { d -> vm.update { it.copy(acquisitionDate = d) } }, "Tanggal perolehan", required = true, error = err[AssetValidator.FIELD_ACQ_DATE])
            MoneyField(input.acquisitionCost, { v -> vm.update { it.copy(acquisitionCost = v) } }, "Harga perolehan", required = true, error = err[AssetValidator.FIELD_COST])
            DateField(input.warrantyUntil, { d -> vm.update { it.copy(warrantyUntil = d) } }, "Garansi s/d", error = err[AssetValidator.FIELD_WARRANTY])

            SectionHeader("Penempatan", Modifier.padding(start = 0.dp))
            SelectField(
                label = "Lokasi", options = options.locations,
                selected = options.locations.firstOrNull { it.id == input.locationId }, itemLabel = { it.name },
                onSelect = { l -> vm.update { it.copy(locationId = l?.id) } }, required = true, error = err[AssetValidator.FIELD_LOCATION],
            )
            SelectField(
                label = "Departemen", options = options.departments,
                selected = options.departments.firstOrNull { it.id == input.departmentId }, itemLabel = { it.name },
                onSelect = { d -> vm.update { it.copy(departmentId = d?.id) } }, noneLabel = "Tidak ada",
            )
            FormTextField(input.picName, { v -> vm.update { it.copy(picName = v) } }, "PIC (penanggung jawab)")

            SectionHeader("Kondisi & status", Modifier.padding(start = 0.dp))
            SelectField(
                label = "Kondisi", options = AssetCondition.entries, selected = input.condition, itemLabel = { it.label },
                onSelect = { c -> if (c != null) vm.update { it.copy(condition = c) } }, required = true,
            )
            SelectField(
                label = "Status", options = AssetStatus.entries, selected = input.status, itemLabel = { it.label },
                onSelect = { s -> if (s != null) vm.update { it.copy(status = s) } }, required = true,
                supporting = if (input.status == AssetStatus.DIHAPUS_DIJUAL) "Aset tidak dihapus permanen; hanya ditandai Dihapus/Dijual" else null,
            )
            if (input.status == AssetStatus.DIHAPUS_DIJUAL) {
                DateField(
                    input.disposalDate, { d -> vm.update { it.copy(disposalDate = d) } }, "Tanggal penghapusan/penjualan",
                    error = err[AssetValidator.FIELD_DISPOSAL_DATE], supporting = "Kosongkan untuk tanggal hari ini",
                )
                MoneyField(input.disposalValue, { v -> vm.update { it.copy(disposalValue = v) } }, "Nilai jual (opsional)")
            }
            FormTextField(input.notes, { v -> vm.update { it.copy(notes = v) } }, "Catatan", singleLine = false, minLines = 3)
            Spacer(Modifier.height(24.dp))
        }
    }
}
