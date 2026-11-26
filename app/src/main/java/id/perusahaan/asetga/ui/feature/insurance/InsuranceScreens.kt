package id.perusahaan.asetga.ui.feature.insurance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import id.perusahaan.asetga.data.file.ExportFormat
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.domain.calc.PolicyStatus
import id.perusahaan.asetga.domain.model.InsurancePolicy
import id.perusahaan.asetga.domain.model.InsuranceImportPreview
import id.perusahaan.asetga.domain.model.InsuredAsset
import id.perusahaan.asetga.domain.validation.OperationValidator
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.LabeledValue
import id.perusahaan.asetga.ui.components.MoneyField
import id.perusahaan.asetga.ui.components.OptionSheet
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.components.SelectField
import id.perusahaan.asetga.ui.components.StatusChip
import id.perusahaan.asetga.ui.feature.asset.AssetStatusChip
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.util.Fmt

@Composable
fun PolicyStatusChip(status: PolicyStatus) {
    val s = AppTheme.status
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        PolicyStatus.ACTIVE -> s.successContainer to s.onSuccessContainer
        PolicyStatus.EXPIRING -> s.warningContainer to s.onWarningContainer
        PolicyStatus.EXPIRED -> s.dangerContainer to s.onDangerContainer
        PolicyStatus.NOT_STARTED -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    StatusChip(status.label, bg, fg)
}

private fun daysText(p: InsurancePolicy): String = when (p.status) {
    PolicyStatus.EXPIRED -> "berakhir ${-p.daysLeft} hari lalu"
    PolicyStatus.NOT_STARTED -> "mulai ${Fmt.date(p.startDate)}"
    else -> "${p.daysLeft} hari lagi"
}

// ---------------- Daftar polis ----------------

@Composable
fun InsuranceListScreen(
    onBack: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    onAddPolicy: () -> Unit,
    onOpenMaster: () -> Unit,
    vm: InsuranceListViewModel = hiltViewModel(),
) {
    val policies by vm.policies.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val ready by vm.mastersReady.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar("Asuransi", onBack = onBack) },
        floatingActionButton = {
            if (vm.canEdit && ready) ExtendedFloatingActionButton(onClick = onAddPolicy, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Tambah polis") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!ready) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.status.warningContainer, contentColor = AppTheme.status.onWarningContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lengkapi master data dulu", style = MaterialTheme.typography.titleSmall)
                        Text("Tambahkan minimal satu Tipe asuransi dan satu Perusahaan asuransi sebelum membuat polis.")
                        OutlinedButton(onClick = onOpenMaster) { Text("Buka Master data") }
                    }
                }
            }
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("Cari nomor polis, tipe, perusahaan") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = status == null, onClick = { vm.setStatus(null) }, label = { Text("Semua") })
                listOf(PolicyStatus.ACTIVE, PolicyStatus.EXPIRING, PolicyStatus.EXPIRED, PolicyStatus.NOT_STARTED).forEach { s ->
                    FilterChip(selected = status == s, onClick = { vm.setStatus(s) }, label = { Text("${s.label} (${counts[s] ?: 0})") })
                }
            }
            val list = policies ?: return@Column
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Shield, if (query.isBlank() && status == null) "Belum ada polis" else "Tidak ada polis yang cocok",
                    if (vm.canEdit && ready && query.isBlank()) "Ketuk Tambah polis untuk mencatat polis asuransi." else null)
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.id }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenPolicy(p.id) },
                        overlineContent = { Text("${p.typeName} · ${p.insurerName}") },
                        headlineContent = { Text(p.policyNumber, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${Fmt.date(p.startDate)} – ${Fmt.date(p.endDate)} · ${daysText(p)} · ${p.assetCount} aset") },
                        trailingContent = { PolicyStatusChip(p.status) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ---------------- Form polis ----------------

@Composable
fun PolicyFormScreen(onBack: () -> Unit, onSaved: (String, Boolean) -> Unit, vm: PolicyFormViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val insurers by vm.insurers.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.results.collect { (id, msg) -> if (id != null) onSaved(id, vm.state.value.isNew) else if (msg != null) snackbar.showSnackbar(msg) } }
    val i = state.input
    val e = state.errors
    Scaffold(
        topBar = { AppTopBar(if (state.isNew) "Tambah polis" else "Ubah polis", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomActionBar("Simpan polis", vm::save, loading = state.saving, enabled = !state.loading, modifier = Modifier.imePadding()) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            FormTextField(i.policyNumber, { v -> vm.update { it.copy(policyNumber = v) } }, "Nomor polis", required = true, error = e[OperationValidator.FIELD_POLICY_NUMBER])
            SelectField(
                "Tipe asuransi", types.filter { it.isActive || it.id == i.typeId }, types.firstOrNull { it.id == i.typeId }, { it.name },
                onSelect = { t -> vm.update { it.copy(typeId = t?.id) } }, required = true, error = e[OperationValidator.FIELD_TYPE],
            )
            SelectField(
                "Perusahaan asuransi", insurers.filter { it.isActive || it.id == i.insurerId }, insurers.firstOrNull { it.id == i.insurerId }, { it.name },
                onSelect = { r -> vm.update { it.copy(insurerId = r?.id) } }, required = true, error = e[OperationValidator.FIELD_INSURER],
            )
            DateField(i.startDate, { d -> vm.update { it.copy(startDate = d) } }, "Tanggal mulai", required = true, error = e[OperationValidator.FIELD_START])
            DateField(i.endDate, { d -> vm.update { it.copy(endDate = d) } }, "Tanggal berakhir", required = true, error = e[OperationValidator.FIELD_END],
                supporting = "Pengingat muncul 30 hari sebelum polis berakhir")
            MoneyField(i.premium, { v -> vm.update { it.copy(premium = v) } }, "Premi", error = e[OperationValidator.FIELD_PREMIUM])
            MoneyField(i.sumInsured, { v -> vm.update { it.copy(sumInsured = v) } }, "Nilai pertanggungan polis")
            FormTextField(i.notes, { v -> vm.update { it.copy(notes = v) } }, "Catatan", singleLine = false, minLines = 2)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- Detail polis ----------------

@Composable
fun PolicyDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onPickAssets: (String) -> Unit,
    onScan: () -> Unit,
    onOpenAsset: (String) -> Unit,
    scannedCode: String?,
    onScanConsumed: () -> Unit,
    message: String? = null,
    onMessageConsumed: () -> Unit = {},
    vm: PolicyDetailViewModel = hiltViewModel(),
) {
    val policy by vm.policy.collectAsStateWithLifecycle()
    val assets by vm.assets.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InsuredAsset?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::onFilePicked) }

    LaunchedEffect(scannedCode) { if (scannedCode != null) { vm.addByCode(scannedCode); onScanConsumed() } }
    LaunchedEffect(message) { if (message != null) { onMessageConsumed(); snackbar.showSnackbar(message) } }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is PolicyEvent.Message -> snackbar.showSnackbar(e.text)
                is PolicyEvent.Share -> ShareHelper.share(context, listOf(e.file), ExportFormat.XLSX.mime, e.file.name)
                PolicyEvent.Deleted -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(policy?.policyNumber ?: "Polis", onBack = onBack, actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (vm.canEdit) DropdownMenuItem(text = { Text("Ubah polis") }, onClick = { menu = false; onEdit(vm.policyId) })
                        DropdownMenuItem(text = { Text("Export ke Excel") }, onClick = { menu = false; vm.exportPolicy() })
                        if (vm.canEdit) DropdownMenuItem(text = { Text("Unduh template upload") }, onClick = { menu = false; vm.shareTemplate() })
                        if (vm.canDelete) DropdownMenuItem(text = { Text("Hapus polis", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (vm.canEdit && policy != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.navigationBarsPadding().padding(12.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPickAssets(vm.policyId) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null); Spacer(Modifier.width(4.dp)); Text("Pilih aset")
                        }
                        FilledTonalButton(onClick = onScan, modifier = Modifier.height(52.dp)) { Icon(Icons.Filled.QrCodeScanner, "Scan") }
                        FilledTonalButton(
                            onClick = { pickFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                            modifier = Modifier.height(52.dp),
                        ) { Icon(Icons.Outlined.FileUpload, "Upload Excel"); Spacer(Modifier.width(4.dp)); Text("Upload") }
                    }
                }
            }
        },
    ) { padding ->
        val p = policy ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.typeName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(p.insurerName, style = MaterialTheme.typography.titleMedium)
                            }
                            PolicyStatusChip(p.status)
                        }
                        Spacer(Modifier.height(8.dp))
                        LabeledValue("Periode", "${Fmt.date(p.startDate)} – ${Fmt.date(p.endDate)}")
                        LabeledValue("Sisa", daysText(p))
                        LabeledValue("Premi", p.premium?.let(Fmt::rupiah) ?: "-")
                        LabeledValue("Nilai pertanggungan polis", p.sumInsured?.let(Fmt::rupiah) ?: "-")
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        LabeledValue("Jumlah aset", Fmt.number(p.assetCount.toLong()))
                        LabeledValue("Total pertanggungan per aset", Fmt.rupiah(p.itemsSumInsured))
                        LabeledValue("Total harga perolehan aset", Fmt.rupiah(p.itemsAcquisitionCost))
                        p.notes?.let { LabeledValue("Catatan", it) }
                    }
                }
                SectionHeader("Aset yang diasuransikan (${p.assetCount})")
                if (p.assetCount > 0) {
                    OutlinedTextField(
                        value = query, onValueChange = vm::setQuery, singleLine = true,
                        placeholder = { Text("Cari di polis ini") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                } else {
                    Text(
                        if (vm.canEdit) "Belum ada aset. Tambahkan lewat Pilih aset, Scan QR, atau Upload Excel (kolom Kode Aset / Kode QR)." else "Belum ada aset.",
                        Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(assets.orEmpty(), key = { it.assetId }) { a ->
                ListItem(
                    modifier = Modifier.clickable { selected = a },
                    overlineContent = { Text(a.code + (a.qrCode?.let { " · QR $it" } ?: "")) },
                    headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${a.categoryName} · ${a.locationName} · pertanggungan ${a.sumInsured?.let(Fmt::rupiah) ?: "-"}") },
                    trailingContent = { if (a.status != id.perusahaan.asetga.domain.model.AssetStatus.DIGUNAKAN) AssetStatusChip(a.status) },
                )
                HorizontalDivider()
            }
        }
    }

    preview?.let { ImportPreviewSheet(it, busy, onCommit = vm::commitImport, onDismiss = vm::cancelImport) }
    selected?.let { a ->
        InsuredAssetDialog(
            asset = a, canEdit = vm.canEdit,
            onDismiss = { selected = null },
            onOpen = { selected = null; onOpenAsset(a.assetId) },
            onSave = { v -> selected = null; vm.setSum(a, v) },
            onRemove = { selected = null; vm.remove(a) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus polis ${policy?.policyNumber}?") },
            text = { Text("Polis dan daftar asetnya tidak lagi tampil. Aset itu sendiri tidak terhapus. Tercatat di audit log.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun InsuredAssetDialog(
    asset: InsuredAsset,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSave: (Long?) -> Unit,
    onRemove: () -> Unit,
) {
    var value by remember { mutableStateOf(asset.sumInsured) }
    var confirmRemove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(asset.code) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(asset.name, style = MaterialTheme.typography.titleSmall)
                Text("Harga perolehan ${Fmt.rupiah(asset.acquisitionCost)}", style = MaterialTheme.typography.bodySmall)
                if (canEdit) {
                    MoneyField(value, { value = it }, "Nilai pertanggungan")
                    if (confirmRemove) {
                        Text("Keluarkan ${asset.code} dari polis ini?", color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRemove) { Text("Ya, keluarkan", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { confirmRemove = false }) { Text("Batal") }
                        }
                    } else {
                        TextButton(onClick = { confirmRemove = true }) { Text("Keluarkan dari polis", color = MaterialTheme.colorScheme.error) }
                    }
                }
                TextButton(onClick = onOpen) { Text("Buka detail aset") }
            }
        },
        confirmButton = { if (canEdit) TextButton(onClick = { onSave(value) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewSheet(p: InsuranceImportPreview, busy: Boolean, onCommit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hasil pemeriksaan file", style = MaterialTheme.typography.titleLarge)
            if (p.headerError != null) {
                Text(p.headerError, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Tutup") }
                Spacer(Modifier.height(16.dp))
                return@Column
            }
            val newCount = p.valid.count { !it.alreadyInPolicy }
            val existing = p.valid.count { it.alreadyInPolicy }
            Text("$newCount aset baru · $existing sudah ada di polis · ${p.invalid.size} baris bermasalah", style = MaterialTheme.typography.titleSmall)
            if (existing > 0) {
                Text("Aset yang sudah ada tidak digandakan; nilai pertanggungannya diperbarui bila diisi.", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().height(320.dp)) {
                items(p.invalid, key = { "e${it.rowNumber}" }) { r ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("Baris ${r.rowNumber} · ${r.code.ifEmpty { "(kosong)" }}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        r.errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider()
                }
                items(p.valid.take(300), key = { "v${it.rowNumber}" }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("${r.assetLabel}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (r.alreadyInPolicy) "sudah ada" else (r.sumInsured?.let(Fmt::rupiah) ?: "baru"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = onCommit, enabled = !busy && p.valid.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Tambahkan ${p.valid.size} aset valid")
            }
            OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------- Pilih aset ----------------

@Composable
fun InsuranceAssetPickerScreen(onDone: (String) -> Unit, onBack: () -> Unit, vm: InsuranceAssetPickerViewModel = hiltViewModel()) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val inPolicy by vm.inPolicy.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.done.collect { onDone(it) } }

    Scaffold(
        topBar = { AppTopBar("Pilih aset", onBack = onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(12.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirmAll = true }, enabled = !busy && (count ?: 0) > 0, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text("Semua hasil (${count ?: 0})", maxLines = 1)
                        }
                        Button(onClick = vm::addSelected, enabled = !busy && selection.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp)) {
                            Text("Tambahkan (${selection.size})")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter.query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("Cari kode, nama, no. seri") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Hapus") } },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            )
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter.categoryId != null, onClick = { sheet = "cat" },
                    label = { Text(categories.firstOrNull { it.id == filter.categoryId }?.name ?: "Kategori") })
                FilterChip(selected = filter.locationId != null, onClick = { sheet = "loc" },
                    label = { Text(locations.firstOrNull { it.id == filter.locationId }?.name ?: "Lokasi") })
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { i ->
                    val a = items[i] ?: return@items
                    val already = a.id in inPolicy
                    val checked = already || a.id in selection
                    ListItem(
                        modifier = Modifier.clickable(enabled = !already) { vm.toggle(a.id) }
                            .background(if (a.id in selection) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        leadingContent = {
                            Icon(
                                if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, contentDescription = null,
                                tint = if (already) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            )
                        },
                        overlineContent = { Text(a.code) },
                        headlineContent = { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (already) "Sudah ada di polis" else "${a.categoryName} · ${a.locationName}") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    when (sheet) {
        "cat" -> OptionSheet("Kategori", categories, categories.firstOrNull { it.id == filter.categoryId }, { it.name },
            onDismiss = { sheet = null }, onSelect = { vm.setCategory(it?.id); sheet = null }, noneLabel = "Semua kategori")
        "loc" -> OptionSheet("Lokasi", locations, locations.firstOrNull { it.id == filter.locationId }, { it.name },
            onDismiss = { sheet = null }, onSelect = { vm.setLocation(it?.id); sheet = null }, noneLabel = "Semua lokasi")
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Tambahkan ${count ?: 0} aset?") },
            text = { Text("Semua aset yang cocok dengan pencarian & filter saat ini akan ditambahkan. Aset yang sudah ada di polis dilewati.") },
            confirmButton = { TextButton(onClick = { confirmAll = false; vm.addAllFiltered() }) { Text("Tambahkan") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Batal") } },
        )
    }
}
