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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val vendors by vm.vendors.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
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

    // Isian awal dicatat saat data selesai dimuat; beda dari itu berarti ada perubahan yang belum disimpan.
    val initial = remember(state.loading) { state.input }
    val dirty = !state.loading && !state.saving && input != initial
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { confirmLeave = true }
    val leave = { if (dirty) confirmLeave = true else onBack() }

    // Isian yang salah bisa berada di luar layar, jadi sebutkan namanya.
    LaunchedEffect(err) {
        if (err.isNotEmpty()) {
            val names = FIELD_NAMES.filter { (key, _) -> key in err }.map { it.second }
            snackbar.showSnackbar(
                if (names.isEmpty()) "Periksa kembali isian yang ditandai merah"
                else "Perlu diperbaiki: ${names.joinToString(", ")}",
            )
        }
    }

    Scaffold(
        topBar = { AppTopBar(if (state.isNew) "Tambah aset" else "Ubah aset", onBack = leave) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomActionBar("Simpan aset", vm::save, loading = state.saving, enabled = !state.loading && !state.photoBusy, modifier = Modifier.imePadding()) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        // Foto di paling atas, lalu field sesuai urutan template import aset.
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader("Foto aset", Modifier.padding(start = 0.dp))
            PhotoPicker(
                photoFile = vm.photoFile(),
                busy = state.photoBusy,
                newCameraUri = vm::newCameraUri,
                onPicked = vm::onPhotoPicked,
                onRemove = vm::removePhoto,
            )

            SectionHeader("Data aset", Modifier.padding(start = 0.dp))
            FormTextField(
                value = input.code, onValueChange = { v -> vm.update { it.copy(code = v.uppercase()) } },
                label = "Kode aset", required = true, error = err[AssetValidator.FIELD_CODE],
                supporting = if (state.isNew) "Terisi otomatis (KATEGORI-TAHUN-NOMOR) setelah memilih kategori; boleh diubah" else "Harus unik",
            )
            FormTextField(
                value = input.qrCode, onValueChange = { v -> vm.update { it.copy(qrCode = v.trim()) } },
                label = "Kode QR", required = true, error = err[AssetValidator.FIELD_QR],
                supporting = "Isi label QR. Boleh sama dengan kode aset",
            )
            FormTextField(
                input.name, { v -> vm.update { it.copy(name = v) } }, "Nama aset", required = true, error = err[AssetValidator.FIELD_NAME],
                capitalization = KeyboardCapitalization.Words,
            )
            SelectField(
                label = "Kategori", options = options.categories,
                selected = options.categories.firstOrNull { it.id == input.categoryId },
                itemLabel = { "${it.name} (${it.codePrefix})" },
                onSelect = { c -> vm.update { it.copy(categoryId = c?.id) } },
                required = true, error = err[AssetValidator.FIELD_CATEGORY],
            )
            FormTextField(input.serialNumber, { v -> vm.update { it.copy(serialNumber = v) } }, "No. seri")
            DateField(input.acquisitionDate, { d -> vm.update { it.copy(acquisitionDate = d) } }, "Tgl perolehan", required = true, error = err[AssetValidator.FIELD_ACQ_DATE])
            MoneyField(input.acquisitionCost, { v -> vm.update { it.copy(acquisitionCost = v) } }, "Harga perolehan", required = true, error = err[AssetValidator.FIELD_COST])
            SelectField(
                label = "Lokasi", options = options.locations,
                selected = options.locations.firstOrNull { it.id == input.locationId }, itemLabel = { it.name },
                onSelect = { l -> vm.update { it.copy(locationId = l?.id) } }, required = true, error = err[AssetValidator.FIELD_LOCATION],
            )
            SelectField(
                label = "Divisi", options = options.divisions,
                selected = options.divisions.firstOrNull { it.id == input.divisionId }, itemLabel = { it.name },
                onSelect = vm::selectDivision, noneLabel = "Tidak ada",
            )
            SelectField(
                label = "Departemen", options = options.departments,
                selected = options.departments.firstOrNull { it.id == input.departmentId },
                itemLabel = { d -> if (input.divisionId == null && d.divisionName != null) "${d.name} · ${d.divisionName}" else d.name },
                onSelect = vm::selectDepartment, noneLabel = "Tidak ada",
                supporting = if (input.divisionId != null) "Hanya departemen di divisi terpilih" else "Memilih departemen otomatis mengisi divisi",
            )
            SelectField(
                label = "Kondisi", options = AssetCondition.entries, selected = input.condition, itemLabel = { it.label },
                onSelect = { c -> if (c != null) vm.update { it.copy(condition = c) } }, required = true,
            )
            SelectField(
                label = "Status", options = statuses.map { it.status }, selected = input.status, itemLabel = { it.label },
                onSelect = { s -> if (s != null) vm.update { it.copy(status = s) } }, required = true, error = err[AssetValidator.FIELD_STATUS],
                supporting = if (input.status == AssetStatus.WRITE_OFF) "Aset tidak dihapus permanen; hanya ditandai Write Off" else null,
            )
            if (input.status == AssetStatus.WRITE_OFF) {
                DateField(
                    input.disposalDate, { d -> vm.update { it.copy(disposalDate = d) } }, "Tanggal write off",
                    error = err[AssetValidator.FIELD_DISPOSAL_DATE], supporting = "Kosongkan untuk tanggal hari ini",
                )
                MoneyField(input.disposalValue, { v -> vm.update { it.copy(disposalValue = v) } }, "Nilai jual (opsional)")
            }
            DateField(input.warrantyUntil, { d -> vm.update { it.copy(warrantyUntil = d) } }, "Garansi s/d", error = err[AssetValidator.FIELD_WARRANTY])
            FormTextField(
                input.notes, { v -> vm.update { it.copy(notes = v) } }, "Catatan", singleLine = false, minLines = 2,
                capitalization = KeyboardCapitalization.Sentences,
            )
            FormTextField(
                input.userLocationDetail, { v -> vm.update { it.copy(userLocationDetail = v) } }, "Pengguna / detail lokasi",
                supporting = "Mis. Budi · Ruang 301, meja 4",
            )
            FormTextField(
                input.accessories, { v -> vm.update { it.copy(accessories = v) } }, "Kelengkapan",
                supporting = "Pisahkan dengan koma, mis. Charger, Casing", singleLine = false,
            )

            SectionHeader("Pengadaan", Modifier.padding(start = 0.dp))
            SelectField(
                label = "Vendor", options = vendors.filter { it.isActive || it.id == input.vendorId },
                selected = vendors.firstOrNull { it.id == input.vendorId }, itemLabel = { it.name },
                onSelect = { v -> vm.update { it.copy(vendorId = v?.id) } }, noneLabel = "Tidak ada",
                supporting = "Pemasok aset, dari master Vendor",
            )
            FormTextField(input.prNumber, { v -> vm.update { it.copy(prNumber = v) } }, "Nomor PR")
            FormTextField(input.poNumber, { v -> vm.update { it.copy(poNumber = v) } }, "Nomor PO")
            FormTextField(input.grNumber, { v -> vm.update { it.copy(grNumber = v) } }, "Nomor GR")
            FormTextField(input.requester, { v -> vm.update { it.copy(requester = v) } }, "Pemohon pembelian")

            SectionHeader("Memo & serah terima", Modifier.padding(start = 0.dp))
            FormTextField(input.memoNumber, { v -> vm.update { it.copy(memoNumber = v) } }, "Nomor memo")
            DateField(input.memoDate, { d -> vm.update { it.copy(memoDate = d) } }, "Tanggal memo")
            FormTextField(input.followUp, { v -> vm.update { it.copy(followUp = v) } }, "Tindak lanjut", singleLine = false, minLines = 2)
            FormTextField(
                input.handoverDoc, { v -> vm.update { it.copy(handoverDoc = v) } }, "Berita acara serah terima",
                supporting = "Nomor / keterangan BAST", singleLine = false,
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Isian yang belum disimpan akan hilang.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Buang") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Lanjut mengisi") } },
        )
    }
}

/** Urutan isian di form, dipakai untuk menyebut kolom yang salah. */
private val FIELD_NAMES = listOf(
    AssetValidator.FIELD_CODE to "Kode aset",
    AssetValidator.FIELD_QR to "Kode QR",
    AssetValidator.FIELD_NAME to "Nama aset",
    AssetValidator.FIELD_CATEGORY to "Kategori",
    AssetValidator.FIELD_ACQ_DATE to "Tgl perolehan",
    AssetValidator.FIELD_COST to "Harga perolehan",
    AssetValidator.FIELD_LOCATION to "Lokasi",
    AssetValidator.FIELD_STATUS to "Status",
    AssetValidator.FIELD_DISPOSAL_DATE to "Tanggal write off",
    AssetValidator.FIELD_WARRANTY to "Garansi",
)
