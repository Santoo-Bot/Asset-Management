package id.perusahaan.asetga.ui.feature.opname

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HighlightOff
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.file.ExportFormat
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.domain.excel.Sheet
import id.perusahaan.asetga.domain.excel.XlsxWriter
import id.perusahaan.asetga.domain.excel.text
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.OpnameItem
import id.perusahaan.asetga.domain.model.OpnameScanOutcome
import id.perusahaan.asetga.domain.model.OpnameSession
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.OpnameRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BarcodeScannerView
import id.perusahaan.asetga.ui.components.CameraPermissionGate
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.ManualCodeButton
import id.perusahaan.asetga.ui.components.OptionSheet
import id.perusahaan.asetga.ui.feature.scan.ManualCodeDialog
import id.perusahaan.asetga.ui.navigation.OpnameSessionRoute
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

// ---------------- Daftar sesi ----------------

@HiltViewModel
class OpnameListViewModel @Inject constructor(
    private val repo: OpnameRepository,
    master: MasterDataRepository,
    session: SessionManager,
) : ViewModel() {
    val canEdit = session.currentUser?.role?.canEditData == true
    val sessions: StateFlow<List<OpnameSession>> = repo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations: StateFlow<List<Location>> = master.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<Pair<String?, String?>>(Channel.BUFFERED) // (sessionId, error)
    val events: Flow<Pair<String?, String?>> = _events.receiveAsFlow()

    fun start(locationId: String) = viewModelScope.launch {
        when (val r = repo.start(locationId)) {
            is AppResult.Success -> _events.send(r.value to null)
            is AppResult.Failure -> _events.send(null to r.message)
        }
    }
}

@Composable
fun OpnameListScreen(onBack: () -> Unit, onOpenSession: (String) -> Unit, vm: OpnameListViewModel = hiltViewModel()) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var pickLocation by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.events.collect { (id, err) -> if (id != null) onOpenSession(id) else if (err != null) snackbar.showSnackbar(err) } }

    Scaffold(
        topBar = { AppTopBar("Stock opname aset", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.canEdit) ExtendedFloatingActionButton(onClick = { pickLocation = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Mulai sesi") })
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyState(
                Icons.Outlined.TaskAlt, "Belum ada sesi stock opname",
                "Pilih lokasi, lalu scan label aset satu per satu. Di akhir sesi terlihat aset yang tidak ditemukan dan yang tidak terdaftar di lokasi itu.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(sessions, key = { it.id }) { s ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenSession(s.id) },
                        overlineContent = { Text(Fmt.dateTime(s.startedAt) + if (s.isFinished) "" else " · berlangsung") },
                        headlineContent = { Text(s.locationName) },
                        supportingContent = { Text("Ditemukan ${s.found}/${s.expected} · tidak ditemukan ${s.missing} · tidak terdaftar ${s.unregistered}") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (pickLocation) {
        OptionSheet(
            title = "Lokasi yang diaudit", options = locations, selected = null, itemLabel = { it.name },
            onDismiss = { pickLocation = false }, onSelect = { l -> pickLocation = false; if (l != null) vm.start(l.id) },
        )
    }
}

// ---------------- Sesi berjalan / laporan ----------------

@HiltViewModel
class OpnameSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: OpnameRepository,
    session: SessionManager,
) : ViewModel() {
    val sessionId = savedStateHandle.toRoute<OpnameSessionRoute>().id
    val canEdit = session.currentUser?.role?.canEditData == true

    val session: StateFlow<OpnameSession?> = repo.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<OpnameItem>> = repo.observeItems(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastOutcome = MutableStateFlow<OpnameScanOutcome?>(null)
    val lastOutcome: StateFlow<OpnameScanOutcome?> = _lastOutcome.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _share = Channel<File>(Channel.BUFFERED)
    val share: Flow<File> = _share.receiveAsFlow()

    fun onCode(code: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            _lastOutcome.value = repo.scan(sessionId, code)
            _busy.value = false
        }
    }

    fun finish(note: String) = viewModelScope.launch { repo.finish(sessionId, note) }

    fun exportReport() = viewModelScope.launch {
        val s = session.value ?: return@launch
        val rows = items.first()
        val file = withContext(Dispatchers.IO) {
            val f = File(ShareHelper.sharedDir(context), "opname-${s.locationName.replace(Regex("[^A-Za-z0-9]+"), "-")}-${s.startedAt}.xlsx")
            f.outputStream().use { out ->
                XlsxWriter.write(
                    listOf(
                        Sheet(
                            "Hasil opname",
                            listOf("Kode Aset", "Nama Aset", "Hasil", "Lokasi Tercatat", "Waktu Scan"),
                            rows.map {
                                listOf(text(it.assetCode ?: it.scannedCode), text(it.assetName), text(it.result.label), text(it.assetLocation), text(it.scannedAt?.let(Fmt::dateTime)))
                            },
                        ),
                        Sheet(
                            "Ringkasan",
                            listOf("Keterangan", "Nilai"),
                            listOf(
                                listOf(text("Lokasi"), text(s.locationName)),
                                listOf(text("Mulai"), text(Fmt.dateTime(s.startedAt))),
                                listOf(text("Selesai"), text(s.finishedAt?.let(Fmt::dateTime) ?: "Belum selesai")),
                                listOf(text("Petugas"), text(s.startedBy)),
                                listOf(text("Aset seharusnya ada"), text(s.expected.toString())),
                                listOf(text("Ditemukan"), text(s.found.toString())),
                                listOf(text("Tidak ditemukan"), text(s.missing.toString())),
                                listOf(text("Tidak terdaftar di lokasi ini"), text(s.unregistered.toString())),
                                listOf(text("Catatan"), text(s.note)),
                            ),
                        ),
                    ),
                    out,
                )
            }
            f
        }
        _share.send(file)
    }
}

@Composable
fun OpnameSessionScreen(onBack: () -> Unit, onOpenAsset: (String) -> Unit, vm: OpnameSessionViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val outcome by vm.lastOutcome.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var manual by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<AuditResult?>(null) }
    LaunchedEffect(vm) { vm.share.collect { ShareHelper.share(context, listOf(it), ExportFormat.XLSX.mime, "Laporan stock opname") } }

    val s = session
    val active = s != null && !s.isFinished && vm.canEdit
    Scaffold(
        topBar = {
            AppTopBar(s?.locationName ?: "Stock opname", onBack = onBack, actions = {
                IconButton(onClick = vm::exportReport) { Icon(Icons.Outlined.IosShare, contentDescription = "Export laporan") }
            })
        },
        bottomBar = {
            if (active) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.navigationBarsPadding().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { manual = true }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Ketik kode") }
                        Button(onClick = { finishing = true }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Selesai") }
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (active) {
                CameraPermissionGate(modifier = Modifier.fillMaxWidth().height(240.dp), fallback = { ManualCodeButton(onClick = { manual = true }) }) {
                    BarcodeScannerView(onCode = vm::onCode, paused = busy || manual || finishing, modifier = Modifier.fillMaxWidth().weight(1f))
                }
                outcome?.let { OutcomeBanner(it) }
            }
            SummaryRow(s)
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Semua") })
                AuditResult.entries.forEach { r -> FilterChip(selected = filter == r, onClick = { filter = r }, label = { Text(shortLabel(r)) }) }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items.filter { filter == null || it.result == filter }, key = { it.id }) { item ->
                    OpnameRow(item, onClick = { item.assetId?.let(onOpenAsset) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (manual) ManualCodeDialog(onDismiss = { manual = false }, onSubmit = { manual = false; vm.onCode(it) }, title = "Kode aset")
    if (finishing) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Selesaikan sesi?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s?.missing ?: 0} aset belum ditemukan akan dicatat sebagai Tidak ditemukan. Laporan tersimpan dan bisa diexport.")
                    FormTextField(note, { note = it }, "Catatan (opsional)", singleLine = false, minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { finishing = false; vm.finish(note) }) { Text("Selesai") } },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Lanjut scan") } },
        )
    }
}

private fun shortLabel(r: AuditResult) = when (r) {
    AuditResult.DITEMUKAN -> "Ditemukan"
    AuditResult.TIDAK_DITEMUKAN -> "Tidak ditemukan"
    AuditResult.TAK_TERDAFTAR -> "Tidak terdaftar"
}

@Composable
private fun SummaryRow(s: OpnameSession) {
    val st = AppTheme.status
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryCard("Ditemukan", "${s.found}/${s.expected}", st.successContainer, st.onSuccessContainer, Modifier.weight(1f))
        SummaryCard("Tidak ditemukan", s.missing.toString(), if (s.missing > 0) st.dangerContainer else MaterialTheme.colorScheme.surfaceContainerLow, if (s.missing > 0) st.onDangerContainer else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        SummaryCard("Tidak terdaftar", s.unregistered.toString(), if (s.unregistered > 0) st.warningContainer else MaterialTheme.colorScheme.surfaceContainerLow, if (s.unregistered > 0) st.onWarningContainer else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OutcomeBanner(o: OpnameScanOutcome) {
    val st = AppTheme.status
    val (text, bg, fg) = when (o) {
        is OpnameScanOutcome.Found -> Triple("✓ ${o.code} · ${o.name} ditemukan", st.successContainer, st.onSuccessContainer)
        is OpnameScanOutcome.AlreadyScanned -> Triple("${o.code} sudah di-scan sebelumnya", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        is OpnameScanOutcome.OtherLocation -> Triple("! ${o.code} · ${o.name} tercatat di ${o.location}", st.warningContainer, st.onWarningContainer)
        is OpnameScanOutcome.Unknown -> Triple("! Kode \"${o.code}\" tidak terdaftar sebagai aset", st.dangerContainer, st.onDangerContainer)
        is OpnameScanOutcome.Error -> Triple(o.message, st.dangerContainer, st.onDangerContainer)
    }
    Surface(color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun OpnameRow(item: OpnameItem, onClick: () -> Unit) {
    val st = AppTheme.status
    val (icon, tint) = when (item.result) {
        AuditResult.DITEMUKAN -> Icons.Outlined.CheckCircle to st.success
        AuditResult.TIDAK_DITEMUKAN -> Icons.Outlined.HighlightOff to st.danger
        AuditResult.TAK_TERDAFTAR -> Icons.AutoMirrored.Outlined.HelpOutline to st.warning
    }
    ListItem(
        modifier = Modifier.clickable(enabled = item.assetId != null, onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = item.result.label, tint = tint) },
        overlineContent = { Text(item.assetCode ?: item.scannedCode) },
        headlineContent = { Text(item.assetName ?: "Kode tidak dikenal") },
        supportingContent = {
            Text(
                item.result.label + (if (item.result == AuditResult.TAK_TERDAFTAR && item.assetLocation != null) " · tercatat di ${item.assetLocation}" else "") +
                    (item.scannedAt?.let { " · ${Fmt.dateTime(it)}" } ?: ""),
            )
        },
    )
}
