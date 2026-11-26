package id.perusahaan.asetga.ui.feature.opname

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TaskAlt
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.ExportFormat
import id.perusahaan.asetga.data.file.OpnameFileService
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.OpnameImportPreview
import id.perusahaan.asetga.domain.model.OpnameItem
import id.perusahaan.asetga.domain.model.OpnameScanOutcome
import id.perusahaan.asetga.domain.model.OpnameSession
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.OpnameRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.AssetPickerScreen
import id.perusahaan.asetga.ui.components.AssetPickerViewModel
import id.perusahaan.asetga.ui.components.BarcodeScannerView
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.CameraPermissionGate
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.LabeledValue
import id.perusahaan.asetga.ui.components.ManualCodeButton
import id.perusahaan.asetga.ui.feature.scan.ManualCodeDialog
import id.perusahaan.asetga.ui.navigation.OpnameAssetPickerRoute
import id.perusahaan.asetga.ui.navigation.OpnameSessionRoute
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

// ---------------- Daftar sesi ----------------

@HiltViewModel
class OpnameListViewModel @Inject constructor(
    private val repo: OpnameRepository,
    session: SessionManager,
) : ViewModel() {
    val canEdit = session.currentUser?.role?.canEditData == true
    val sessions: StateFlow<List<OpnameSession>?> = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = Channel<Pair<String?, String?>>(Channel.BUFFERED) // (sessionId, error)
    val events: Flow<Pair<String?, String?>> = _events.receiveAsFlow()

    fun create(name: String, date: LocalDate, note: String) = viewModelScope.launch {
        when (val r = repo.create(name, date, note)) {
            is AppResult.Success -> _events.send(r.value to null)
            is AppResult.Failure -> _events.send(null to r.message)
        }
    }
}

@Composable
fun OpnameListScreen(onBack: () -> Unit, onOpenSession: (String) -> Unit, vm: OpnameListViewModel = hiltViewModel()) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.events.collect { (id, err) -> if (id != null) onOpenSession(id) else if (err != null) snackbar.showSnackbar(err) } }

    Scaffold(
        topBar = { AppTopBar("Stock opname aset", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.canEdit) ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Buat sesi") })
        },
    ) { padding ->
        val list = sessions ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(
                Icons.Outlined.TaskAlt, "Belum ada sesi stock opname",
                "1. Buat sesi  2. Tambah aset yang akan dicek (pilih manual / upload Excel)  3. Cek fisik dengan scan atau tandai  4. Selesai & buat laporan.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.id }) { s ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenSession(s.id) },
                        overlineContent = { Text(Fmt.date(s.date) + if (s.isFinished) " · selesai" else " · berjalan") },
                        headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                "Ditemukan ${s.found}/${s.total} · ${if (s.isFinished) "tidak ditemukan" else "belum dicek"} ${s.notFound}" +
                                    if (s.outside > 0) " · di luar daftar ${s.outside}" else "",
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (creating) CreateSessionDialog(onDismiss = { creating = false }, onCreate = { n, d, note -> creating = false; vm.create(n, d, note) })
}

@Composable
private fun CreateSessionDialog(onDismiss: () -> Unit, onCreate: (String, LocalDate, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat sesi opname") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(name, { name = it }, "Nama sesi", required = true, supporting = "Mis. Opname Kantor Pusat Q4 2026")
                DateField(date, { date = it }, "Tanggal opname", required = true)
                FormTextField(note, { note = it }, "Catatan (opsional)", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, date ?: LocalDate.now(), note) }, enabled = name.isNotBlank() && date != null) { Text("Buat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ---------------- Detail sesi ----------------

enum class OpnameFilter { ALL, FOUND, NOT_FOUND, DAMAGED, OUTSIDE }

sealed interface OpnameEvent {
    data class Message(val text: String) : OpnameEvent
    data class Share(val file: File) : OpnameEvent
    data object Deleted : OpnameEvent
}

@HiltViewModel
class OpnameSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: OpnameRepository,
    private val files: OpnameFileService,
    session: SessionManager,
) : ViewModel() {
    val sessionId = savedStateHandle.toRoute<OpnameSessionRoute>().id
    val canEdit = session.currentUser?.role?.canEditData == true
    val canDelete = session.currentUser?.role?.canManageMaster == true

    val session: StateFlow<OpnameSession?> = repo.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val allItems = repo.observeItems(sessionId)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow(OpnameFilter.ALL)
    val filter: StateFlow<OpnameFilter> = _filter.asStateFlow()
    val items: StateFlow<List<OpnameItem>?> = combine(allItems, _query, _filter) { list, q, f ->
        list.filter { item ->
            val matchFilter = when (f) {
                OpnameFilter.ALL -> true
                OpnameFilter.FOUND -> item.result == AuditResult.DITEMUKAN
                OpnameFilter.NOT_FOUND -> item.result == AuditResult.TIDAK_DITEMUKAN
                OpnameFilter.DAMAGED -> item.result != AuditResult.TIDAK_DITEMUKAN && item.condition == AssetCondition.RUSAK
                OpnameFilter.OUTSIDE -> item.result == AuditResult.TAK_TERDAFTAR
            }
            matchFilter && (q.isBlank() || listOf(item.code, item.name.orEmpty(), item.qrCode.orEmpty(), item.locationName.orEmpty(), item.userLocation.orEmpty())
                .any { it.contains(q.trim(), ignoreCase = true) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _lastOutcome = MutableStateFlow<OpnameScanOutcome?>(null)
    val lastOutcome: StateFlow<OpnameScanOutcome?> = _lastOutcome.asStateFlow()
    private val _scanBusy = MutableStateFlow(false)
    val scanBusy: StateFlow<Boolean> = _scanBusy.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _preview = MutableStateFlow<OpnameImportPreview?>(null)
    val preview: StateFlow<OpnameImportPreview?> = _preview.asStateFlow()
    private val _events = Channel<OpnameEvent>(Channel.BUFFERED)
    val events: Flow<OpnameEvent> = _events.receiveAsFlow()

    fun setQuery(q: String) { _query.value = q }
    fun setFilter(f: OpnameFilter) { _filter.value = f }

    private fun op(block: suspend () -> OpnameEvent?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val e = runCatching { block() }.getOrElse { OpnameEvent.Message("Gagal: ${it.message}") }
            _busy.value = false
            e?.let { _events.send(it) }
        }
    }

    private fun AppResult<*>.event(ok: String?): OpnameEvent? = when (this) {
        is AppResult.Success -> ok?.let { OpnameEvent.Message(it) }
        is AppResult.Failure -> OpnameEvent.Message(message)
    }

    fun onCode(code: String) {
        if (_scanBusy.value) return
        _scanBusy.value = true
        viewModelScope.launch {
            _lastOutcome.value = repo.scan(sessionId, code)
            _scanBusy.value = false
        }
    }

    fun updateItem(item: OpnameItem, found: Boolean, condition: AssetCondition?, note: String) =
        op { repo.updateItem(sessionId, item.id, found, condition, note).event(null) }

    fun removeItem(item: OpnameItem) = op { repo.removeItem(sessionId, item.id).event("${item.code} dikeluarkan dari sesi") }

    fun onFilePicked(uri: Uri) = op {
        _preview.value = repo.previewImport(sessionId, files.readSheet(uri))
        null
    }

    fun commitImport() {
        val p = _preview.value ?: return
        op {
            when (val r = repo.commitImport(sessionId, p)) {
                is AppResult.Success -> {
                    _preview.value = null
                    OpnameEvent.Message(r.value.message() + if (p.invalid.isNotEmpty()) ", ${p.invalid.size} baris dilewati" else "")
                }
                is AppResult.Failure -> OpnameEvent.Message(r.message)
            }
        }
    }

    fun cancelImport() { _preview.value = null }

    fun shareTemplate() = op { OpnameEvent.Share(files.template()) }

    /** Selesaikan sesi lalu langsung buat laporan. */
    fun finish(note: String) = op {
        when (val r = repo.finish(sessionId, note)) {
            is AppResult.Success -> report()
            is AppResult.Failure -> OpnameEvent.Message(r.message)
        }
    }

    fun exportReport() = op { report() }

    private suspend fun report(): OpnameEvent {
        val s = repo.observeSession(sessionId).first() ?: return OpnameEvent.Message("Sesi tidak ditemukan")
        return OpnameEvent.Share(files.report(s, allItems.first()))
    }

    fun delete() = op {
        when (val r = repo.delete(sessionId)) {
            is AppResult.Success -> OpnameEvent.Deleted
            is AppResult.Failure -> OpnameEvent.Message(r.message)
        }
    }
}

@Composable
fun OpnameSessionScreen(
    onBack: () -> Unit,
    onOpenAsset: (String) -> Unit,
    onPickAssets: (String) -> Unit,
    message: String? = null,
    onMessageConsumed: () -> Unit = {},
    vm: OpnameSessionViewModel = hiltViewModel(),
) {
    val session by vm.session.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val outcome by vm.lastOutcome.collectAsStateWithLifecycle()
    val scanBusy by vm.scanBusy.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var scanning by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<OpnameItem?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::onFilePicked) }

    LaunchedEffect(message) { if (message != null) { onMessageConsumed(); snackbar.showSnackbar(message) } }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is OpnameEvent.Message -> snackbar.showSnackbar(e.text)
                is OpnameEvent.Share -> ShareHelper.share(context, listOf(e.file), ExportFormat.XLSX.mime, e.file.name)
                OpnameEvent.Deleted -> onBack()
            }
        }
    }

    val s = session
    val active = s != null && !s.isFinished && vm.canEdit
    val showCamera = scanning && active
    Scaffold(
        topBar = {
            AppTopBar(s?.name ?: "Stock opname", onBack = onBack, actions = {
                if (vm.canDelete) {
                    Box {
                        IconButton(onClick = { topMenu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Hapus sesi", color = MaterialTheme.colorScheme.error) },
                                onClick = { topMenu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            when {
                s == null -> Unit
                active -> Surface(tonalElevation = 3.dp) {
                    Row(Modifier.navigationBarsPadding().padding(12.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { addMenu = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null); Spacer(Modifier.width(4.dp)); Text("Tambah aset", maxLines = 1)
                            }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(text = { Text("Pilih dari daftar aset") }, onClick = { addMenu = false; onPickAssets(vm.sessionId) })
                                DropdownMenuItem(text = { Text("Upload Excel") }, onClick = {
                                    addMenu = false
                                    pickFile.launch(arrayOf(XLSX_MIME, "application/octet-stream"))
                                })
                                DropdownMenuItem(text = { Text("Unduh template upload") }, onClick = { addMenu = false; vm.shareTemplate() })
                            }
                        }
                        FilledTonalButton(onClick = { scanning = !showCamera }, modifier = Modifier.height(52.dp)) {
                            Icon(if (showCamera) Icons.Filled.Close else Icons.Filled.QrCodeScanner, null)
                            Spacer(Modifier.width(4.dp)); Text(if (showCamera) "Tutup" else "Scan")
                        }
                        Button(onClick = { finishing = true }, enabled = (s?.total ?: 0) > 0, modifier = Modifier.height(52.dp)) { Text("Selesai") }
                    }
                }
                s?.isFinished == true -> BottomActionBar("Buat laporan (Excel)", onClick = vm::exportReport, loading = busy)
            }
        },
    ) { padding ->
        if (s == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (showCamera) {
                CameraPermissionGate(modifier = Modifier.fillMaxWidth().height(220.dp), fallback = { ManualCodeButton(onClick = { manual = true }) }) {
                    BarcodeScannerView(onCode = vm::onCode, paused = scanBusy || manual || finishing, modifier = Modifier.fillMaxWidth().weight(1f))
                }
                outcome?.let { OutcomeBanner(it) }
                TextButton(onClick = { manual = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Ketik kode manual") }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    if (!showCamera) SessionHeader(s)
                    SummaryRow(s)
                    if (s.total == 0 && active) {
                        Text(
                            "Langkah berikutnya: tekan Tambah aset untuk memilih aset dari daftar atau upload Excel (kolom Kode Aset / Kode QR). " +
                                "Setelah itu cek fisik dengan Scan atau ketuk aset untuk menandai.",
                            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.total + s.outside > 0) {
                        if (s.total + s.outside > 10) {
                            OutlinedTextField(
                                value = query, onValueChange = vm::setQuery, singleLine = true,
                                placeholder = { Text("Cari di sesi ini") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, "Hapus") } },
                                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                            )
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OpnameFilter.entries.forEach { f ->
                                FilterChip(selected = filter == f, onClick = { vm.setFilter(f) }, label = { Text(filterLabel(f, s.isFinished)) })
                            }
                        }
                    }
                }
                items(items.orEmpty(), key = { it.id }) { item ->
                    OpnameRow(item, s.isFinished, onClick = { selected = item })
                    HorizontalDivider()
                }
            }
        }
    }

    if (manual) ManualCodeDialog(onDismiss = { manual = false }, onSubmit = { manual = false; vm.onCode(it) }, title = "Kode aset / kode QR")
    preview?.let { ImportPreviewSheet(it, busy, onCommit = vm::commitImport, onDismiss = vm::cancelImport) }
    selected?.let { item ->
        OpnameItemDialog(
            item = item, editable = active,
            onDismiss = { selected = null },
            onOpenAsset = item.assetId?.let { id -> { selected = null; onOpenAsset(id) } },
            onSave = { found, cond, note -> selected = null; vm.updateItem(item, found, cond, note) },
            onRemove = { selected = null; vm.removeItem(item) },
            finished = s?.isFinished == true,
        )
    }
    if (finishing && s != null) {
        var note by remember { mutableStateOf(s.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Selesaikan sesi?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ditemukan ${s.found} dari ${s.total} aset (${s.foundPercent}%).")
                    if (s.notFound > 0) Text("${s.notFound} aset yang belum dicek akan dicatat sebagai Tidak ditemukan.", color = MaterialTheme.colorScheme.error)
                    Text("Setelah selesai, sesi tidak bisa diubah. Laporan Excel langsung dibuat.", style = MaterialTheme.typography.bodySmall)
                    FormTextField(note, { note = it }, "Catatan / kesimpulan (opsional)", singleLine = false, minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { finishing = false; vm.finish(note) }) { Text("Selesai & buat laporan") } },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Lanjut cek") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus sesi ${s?.name}?") },
            text = { Text("Sesi beserta daftar dan hasil opnamenya dihapus permanen. Aset tidak ikut terhapus. Tercatat di audit log.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

private fun filterLabel(f: OpnameFilter, finished: Boolean) = when (f) {
    OpnameFilter.ALL -> "Semua"
    OpnameFilter.FOUND -> "Ditemukan"
    OpnameFilter.NOT_FOUND -> if (finished) "Tidak ditemukan" else "Belum dicek"
    OpnameFilter.DAMAGED -> "Rusak"
    OpnameFilter.OUTSIDE -> "Di luar daftar"
}

@Composable
private fun SessionHeader(s: OpnameSession) {
    Card(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LabeledValue("Tanggal opname", Fmt.date(s.date))
            LabeledValue("Dibuat oleh", s.startedBy ?: "-")
            LabeledValue("Status", if (s.isFinished) "Selesai ${Fmt.dateTime(s.finishedAt!!)}" else "Berjalan")
            s.note?.let { LabeledValue("Catatan", it) }
        }
    }
}

@Composable
private fun SummaryRow(s: OpnameSession) {
    val st = AppTheme.status
    val neutralBg = MaterialTheme.colorScheme.surfaceContainerLow
    val neutralFg = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Ditemukan", "${s.found}/${s.total}", st.successContainer, st.onSuccessContainer, Modifier.weight(1f))
        SummaryCard(
            if (s.isFinished) "Tidak ditemukan" else "Belum dicek", s.notFound.toString(),
            if (s.isFinished && s.notFound > 0) st.dangerContainer else neutralBg, if (s.isFinished && s.notFound > 0) st.onDangerContainer else neutralFg, Modifier.weight(1f),
        )
        SummaryCard("Rusak", s.damaged.toString(), if (s.damaged > 0) st.warningContainer else neutralBg, if (s.damaged > 0) st.onWarningContainer else neutralFg, Modifier.weight(1f))
        SummaryCard("Di luar daftar", s.outside.toString(), if (s.outside > 0) st.warningContainer else neutralBg, if (s.outside > 0) st.onWarningContainer else neutralFg, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OutcomeBanner(o: OpnameScanOutcome) {
    val st = AppTheme.status
    val (text, bg, fg) = when (o) {
        is OpnameScanOutcome.Found -> Triple("✓ ${o.code} · ${o.name} ditemukan", st.successContainer, st.onSuccessContainer)
        is OpnameScanOutcome.AlreadyScanned -> Triple("${o.code} sudah dicek sebelumnya", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        is OpnameScanOutcome.NotInList -> Triple("! ${o.code} · ${o.name} tidak ada di daftar sesi ini", st.warningContainer, st.onWarningContainer)
        is OpnameScanOutcome.Unknown -> Triple("! Kode \"${o.code}\" tidak terdaftar sebagai aset", st.dangerContainer, st.onDangerContainer)
        is OpnameScanOutcome.Error -> Triple(o.message, st.dangerContainer, st.onDangerContainer)
    }
    Surface(color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OpnameRow(item: OpnameItem, finished: Boolean, onClick: () -> Unit) {
    val st = AppTheme.status
    val (icon, tint) = when (item.result) {
        AuditResult.DITEMUKAN -> Icons.Outlined.CheckCircle to if (item.condition == AssetCondition.RUSAK) st.warning else st.success
        AuditResult.TIDAK_DITEMUKAN -> if (finished) Icons.Outlined.HighlightOff to st.danger else Icons.Outlined.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
        AuditResult.TAK_TERDAFTAR -> Icons.AutoMirrored.Outlined.HelpOutline to st.warning
    }
    val status = OpnameFileService.resultLabel(item.result, finished) + (item.condition?.takeIf { item.result != AuditResult.TIDAK_DITEMUKAN }?.let { " · ${it.label}" } ?: "")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = status, tint = tint) },
        overlineContent = { Text(item.code + (item.qrCode?.let { " · QR $it" } ?: "")) },
        headlineContent = { Text(item.name ?: "Kode tidak dikenal", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(status, item.locationName, item.userLocation, item.note).joinToString(" · "),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun OpnameItemDialog(
    item: OpnameItem,
    editable: Boolean,
    finished: Boolean,
    onDismiss: () -> Unit,
    onOpenAsset: (() -> Unit)?,
    onSave: (Boolean, AssetCondition?, String) -> Unit,
    onRemove: () -> Unit,
) {
    val outside = item.result == AuditResult.TAK_TERDAFTAR
    var found by remember { mutableStateOf(item.result != AuditResult.TIDAK_DITEMUKAN) }
    var condition by remember { mutableStateOf(item.condition ?: item.systemCondition ?: AssetCondition.BAIK) }
    var note by remember { mutableStateOf(item.note.orEmpty()) }
    var confirmRemove by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.code) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name ?: "Kode tidak dikenal", style = MaterialTheme.typography.titleSmall)
                listOfNotNull(item.categoryName, item.locationName, item.userLocation).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                item.systemCondition?.let { Text("Kondisi di sistem: ${it.label}", style = MaterialTheme.typography.bodySmall) }
                if (editable) {
                    if (outside) {
                        Text("Hasil scan di luar daftar sesi ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Hasil", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = found, onClick = { found = true }, label = { Text("Ditemukan") })
                            FilterChip(selected = !found, onClick = { found = false }, label = { Text("Tidak ditemukan") })
                        }
                    }
                    if ((found || outside) && item.assetId != null) {
                        Text("Kondisi fisik", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssetCondition.entries.forEach { c -> FilterChip(selected = condition == c, onClick = { condition = c }, label = { Text(c.label) }) }
                        }
                    }
                    FormTextField(note, { note = it }, "Catatan (opsional)", singleLine = false, minLines = 2)
                    if (confirmRemove) {
                        Text("Keluarkan ${item.code} dari sesi ini?", color = MaterialTheme.colorScheme.error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRemove) { Text("Ya, keluarkan", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { confirmRemove = false }) { Text("Batal") }
                        }
                    } else {
                        TextButton(onClick = { confirmRemove = true }) { Text("Keluarkan dari sesi", color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    LabeledValue("Hasil", OpnameFileService.resultLabel(item.result, finished))
                    item.condition?.let { LabeledValue("Kondisi fisik", it.label) }
                    item.checkedAt?.let { LabeledValue("Waktu cek", Fmt.dateTime(it)) }
                    item.note?.let { LabeledValue("Catatan", it) }
                }
                onOpenAsset?.let { TextButton(onClick = it) { Text("Buka detail aset") } }
            }
        },
        confirmButton = {
            if (editable) {
                TextButton(onClick = { onSave(found, if ((found || outside) && item.assetId != null) condition else null, note) }) { Text("Simpan") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewSheet(p: OpnameImportPreview, busy: Boolean, onCommit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hasil pemeriksaan file", style = MaterialTheme.typography.titleLarge)
            if (p.headerError != null) {
                Text(p.headerError, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Tutup") }
                Spacer(Modifier.height(16.dp))
                return@Column
            }
            val newCount = p.valid.count { !it.alreadyInSession }
            val existing = p.valid.count { it.alreadyInSession }
            Text("$newCount aset baru · $existing sudah ada di sesi · ${p.invalid.size} baris bermasalah", style = MaterialTheme.typography.titleSmall)
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
                        Text(if (r.alreadyInSession) "sudah ada" else "baru", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = onCommit, enabled = !busy && newCount > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Tambahkan $newCount aset")
            }
            OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------- Pilih aset untuk sesi ----------------

@HiltViewModel
class OpnameAssetPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    assets: AssetRepository,
    private val repo: OpnameRepository,
    master: MasterDataRepository,
) : AssetPickerViewModel(assets, master) {
    val sessionId = savedStateHandle.toRoute<OpnameAssetPickerRoute>().sessionId

    override fun existingIds(): Flow<Set<String>> = repo.observeAssetIds(sessionId)

    override suspend fun addAssets(ids: List<String>): String = when (val r = repo.addAssets(sessionId, ids)) {
        is AppResult.Success -> r.value.message()
        is AppResult.Failure -> r.message
    }
}

@Composable
fun OpnameAssetPickerScreen(onDone: (String) -> Unit, onBack: () -> Unit, vm: OpnameAssetPickerViewModel = hiltViewModel()) =
    AssetPickerScreen(vm, existingLabel = "Sudah ada di sesi", onDone = onDone, onBack = onBack)
