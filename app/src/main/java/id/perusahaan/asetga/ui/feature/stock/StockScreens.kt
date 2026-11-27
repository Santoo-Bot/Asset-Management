package id.perusahaan.asetga.ui.feature.stock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import id.perusahaan.asetga.ui.feature.data.ExportDialog
import id.perusahaan.asetga.ui.feature.data.DataFilesViewModel
import id.perusahaan.asetga.ui.feature.data.DataFilesEffect
import id.perusahaan.asetga.ui.components.AddMenuFab
import id.perusahaan.asetga.data.file.ExportKind
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.model.StockItem
import id.perusahaan.asetga.domain.model.StockTxnType
import id.perusahaan.asetga.domain.validation.OperationValidator
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.ErrorBanner
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.LabeledValue
import id.perusahaan.asetga.ui.components.QuantityField
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.components.SelectField
import id.perusahaan.asetga.ui.components.StatusChip
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.util.Fmt

@Composable
fun ReorderChip(item: StockItem) {
    val s = AppTheme.status
    if (item.needsReorder) StatusChip("Reorder", s.dangerContainer, s.onDangerContainer)
    else StatusChip("Aman", s.successContainer, s.onSuccessContainer)
}

@Composable
fun StockListScreen(
    scannedCode: String?,
    onScanConsumed: () -> Unit,
    onScan: () -> Unit,
    onOpenItem: (String) -> Unit,
    onAddItem: (barcode: String?) -> Unit,
    onImportItems: () -> Unit,
    vm: StockListViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val reorderOnly by vm.reorderOnly.collectAsStateWithLifecycle()
    val reorderCount by vm.reorderCount.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val files: DataFilesViewModel = hiltViewModel()
    DataFilesEffect(files) { snackbar.showSnackbar(it) }
    var exportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(scannedCode) {
        if (scannedCode != null) {
            vm.onScanned(scannedCode)
            onScanConsumed()
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is StockListEvent.OpenItem -> onOpenItem(e.id)
                is StockListEvent.NotFound -> {
                    val r = snackbar.showSnackbar(
                        "Barang dengan kode \"${e.code}\" belum terdaftar",
                        actionLabel = if (vm.canEdit) "Tambah" else null,
                        duration = SnackbarDuration.Long,
                    )
                    if (r == SnackbarResult.ActionPerformed) onAddItem(e.code)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar("Stok barang", actions = {
                IconButton(onClick = { exportDialog = true }) { Icon(Icons.Outlined.IosShare, contentDescription = "Export stok") }
                IconButton(onClick = onScan) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode barang") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.canEdit) {
                AddMenuFab(text = "Tambah barang", onManual = { onAddItem(null) }, onImport = onImportItems, onTemplate = files::stockTemplate)
            }
        },
    ) { padding ->
        if (exportDialog) ExportDialog(ExportKind.STOCK, files) { exportDialog = false }
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("Cari kode, nama, barcode") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Hapus") } },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !reorderOnly, onClick = { vm.setReorderOnly(false) }, label = { Text("Semua") })
                FilterChip(selected = reorderOnly, onClick = { vm.setReorderOnly(true) }, label = { Text("Perlu reorder ($reorderCount)") })
            }
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    Icons.Outlined.Inventory,
                    if (reorderOnly) "Tidak ada barang yang perlu reorder" else "Belum ada barang",
                    if (vm.canEdit && !reorderOnly && query.isBlank()) "Ketuk Tambah barang untuk mendaftarkan barang habis pakai." else null,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(list, key = { it.id }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenItem(item.id) },
                            overlineContent = { Text(item.code) },
                            headlineContent = { Text(item.name) },
                            supportingContent = {
                                Text("Stok ${Fmt.qty(item.currentStock)} ${item.unitName} · min ${Fmt.qty(item.minStock)}" + (item.storageLocationName?.let { " · $it" } ?: ""))
                            },
                            trailingContent = { ReorderChip(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(onBack: () -> Unit, onEdit: (String) -> Unit, vm: ItemDetailViewModel = hiltViewModel()) {
    val item by vm.item.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.deleted.collect { onBack() } }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus barang ${item?.code}?") },
            text = { Text("Barang beserta seluruh riwayat transaksi masuk/keluarnya dihapus selamanya dan tidak bisa dipulihkan.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteItem() }) { Text("Hapus permanen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar("Detail barang", onBack = onBack, actions = {
                if (vm.canEdit) IconButton(onClick = { onEdit(vm.itemId) }) { Icon(Icons.Outlined.Edit, contentDescription = "Ubah") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (vm.canEdit && item != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.navigationBarsPadding().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.openTxn(StockTxnType.IN) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Outlined.SouthWest, null); Spacer(Modifier.width(6.dp)); Text("Barang masuk")
                        }
                        Button(onClick = { vm.openTxn(StockTxnType.OUT) }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Outlined.NorthEast, null); Spacer(Modifier.width(6.dp)); Text("Barang keluar")
                        }
                    }
                }
            }
        },
    ) { padding ->
        val it = item ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(it.code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(it.name, style = MaterialTheme.typography.headlineSmall)
                }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (it.needsReorder) AppTheme.status.dangerContainer else MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Stok akhir", style = MaterialTheme.typography.labelLarge)
                            Text("${Fmt.qty(it.currentStock)} ${it.unitName}", style = MaterialTheme.typography.headlineMedium)
                            Text("Minimum ${Fmt.qty(it.minStock)} ${it.unitName}", style = MaterialTheme.typography.bodySmall)
                        }
                        ReorderChip(it)
                    }
                }
                Column(Modifier.padding(16.dp)) {
                    LabeledValue("Stok awal", "${Fmt.qty(it.initialStock)} ${it.unitName}")
                    LabeledValue("Barcode", it.barcode ?: "-")
                    LabeledValue("Lokasi simpan", it.storageLocationName ?: "-")
                    it.notes?.let { n -> LabeledValue("Catatan", n) }
                    if (vm.canDelete) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Hapus barang", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                SectionHeader("Riwayat transaksi")
                if (txns.isEmpty()) Text("Belum ada transaksi", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(txns, key = { t -> t.id }) { t ->
                val incoming = t.type == StockTxnType.IN
                ListItem(
                    leadingContent = {
                        Icon(
                            if (incoming) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast, contentDescription = t.type.label,
                            tint = if (incoming) AppTheme.status.success else AppTheme.status.danger,
                        )
                    },
                    headlineContent = { Text("${if (incoming) "+" else "−"}${Fmt.qty(t.qty)} ${it.unitName}") },
                    supportingContent = {
                        Text(listOfNotNull(Fmt.date(t.date), t.reference, t.note, t.createdBy).joinToString(" · "))
                    },
                )
                HorizontalDivider()
            }
        }
    }

    sheet?.let { s ->
        val current = item
        ModalBottomSheet(onDismissRequest = vm::closeTxn, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Transaksi stok", style = MaterialTheme.typography.titleLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StockTxnType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = s.input.type == type,
                            onClick = { vm.updateTxn { it.copy(type = type) } },
                            shape = SegmentedButtonDefaults.itemShape(index, StockTxnType.entries.size),
                        ) { Text(type.label) }
                    }
                }
                current?.let { Text("Stok saat ini: ${Fmt.qty(it.currentStock)} ${it.unitName}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                QuantityField(s.input.qty, { v -> vm.updateTxn { it.copy(qty = v) } }, "Jumlah", required = true, error = s.errors[OperationValidator.FIELD_QTY], suffix = current?.unitName)
                DateField(s.input.date, { d -> vm.updateTxn { it.copy(date = d) } }, "Tanggal", required = true, error = s.errors[OperationValidator.FIELD_DATE])
                FormTextField(s.input.reference, { v -> vm.updateTxn { it.copy(reference = v) } }, if (s.input.type == StockTxnType.IN) "No. PO / dokumen" else "Diminta oleh / departemen")
                FormTextField(s.input.note, { v -> vm.updateTxn { it.copy(note = v) } }, "Catatan")
                s.message?.let { ErrorBanner(it) }
                Button(onClick = vm::saveTxn, enabled = !s.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (s.saving) "Menyimpan…" else "Simpan ${s.input.type.label.lowercase()}")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ItemFormScreen(onBack: () -> Unit, onSaved: (String, Boolean) -> Unit, vm: ItemFormViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.results.collect { (id, msg) -> if (id != null) onSaved(id, vm.state.value.isNew) else if (msg != null) snackbar.showSnackbar(msg) }
    }
    val input = state.input
    val err = state.errors

    Scaffold(
        topBar = { AppTopBar(if (state.isNew) "Tambah barang" else "Ubah barang", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomActionBar("Simpan barang", vm::save, loading = state.saving, enabled = !state.loading, modifier = Modifier.imePadding()) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            FormTextField(input.code, { v -> vm.update { it.copy(code = v.uppercase()) } }, "Kode barang", required = true, error = err[OperationValidator.FIELD_CODE])
            FormTextField(input.barcode, { v -> vm.update { it.copy(barcode = v.trim()) } }, "Barcode (opsional)", error = err["barcode"], supporting = "Barcode kemasan agar bisa dipilih dengan scan")
            FormTextField(input.name, { v -> vm.update { it.copy(name = v) } }, "Nama barang", required = true, error = err[OperationValidator.FIELD_NAME])
            SelectField(
                "Satuan", units, units.firstOrNull { it.id == input.unitId }, { it.name },
                onSelect = { u -> vm.update { it.copy(unitId = u?.id) } }, required = true, error = err[OperationValidator.FIELD_UNIT],
            )
            SelectField(
                "Lokasi simpan", locations, locations.firstOrNull { it.id == input.storageLocationId }, { it.name },
                onSelect = { l -> vm.update { it.copy(storageLocationId = l?.id) } }, noneLabel = "Tidak ditentukan",
            )
            QuantityField(
                input.initialStock, { v -> vm.update { it.copy(initialStock = v) } }, "Stok awal", error = err[OperationValidator.FIELD_INITIAL],
                supporting = if (state.isNew) null else "Mengubah stok awal ikut mengubah stok akhir",
            )
            QuantityField(input.minStock, { v -> vm.update { it.copy(minStock = v) } }, "Minimum stok", error = err[OperationValidator.FIELD_MIN], supporting = "Status Reorder bila stok ≤ minimum")
            FormTextField(input.notes, { v -> vm.update { it.copy(notes = v) } }, "Catatan", singleLine = false, minLines = 2)
            Spacer(Modifier.height(24.dp))
        }
    }
}
