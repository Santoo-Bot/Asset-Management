package id.perusahaan.asetga.ui.feature.operation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import id.perusahaan.asetga.ui.feature.data.ExportDialog
import id.perusahaan.asetga.ui.feature.data.DataFilesViewModel
import id.perusahaan.asetga.ui.feature.data.DataFilesEffect
import id.perusahaan.asetga.data.file.ExportKind
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.calc.ServiceDueStatus
import id.perusahaan.asetga.domain.model.ServiceRecord
import id.perusahaan.asetga.domain.model.ServiceSchedule
import id.perusahaan.asetga.domain.repository.OperationRepository
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.StatusChip
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ServiceListViewModel @Inject constructor(private val ops: OperationRepository) : ViewModel() {
    private val _statusFilter = MutableStateFlow<ServiceDueStatus?>(null)
    val statusFilter: StateFlow<ServiceDueStatus?> = _statusFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val schedules: StateFlow<List<ServiceSchedule>> = combine(ops.observeSchedules(), _statusFilter) { list, f ->
        if (f == null) list else list.filter { it.status == f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: Flow<PagingData<ServiceRecord>> = _query.debounce(300).flatMapLatest { ops.pagingServices(it) }.cachedIn(viewModelScope)

    fun setStatus(s: ServiceDueStatus?) { _statusFilter.value = s }
    fun setQuery(q: String) { _query.value = q }
}

@Composable
fun ServiceStatusChip(status: ServiceDueStatus) {
    val s = AppTheme.status
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        ServiceDueStatus.OVERDUE -> s.dangerContainer to s.onDangerContainer
        ServiceDueStatus.DUE_SOON -> s.warningContainer to s.onWarningContainer
        ServiceDueStatus.SCHEDULED -> s.successContainer to s.onSuccessContainer
        ServiceDueStatus.NONE -> cs.surfaceContainerHigh to cs.onSurfaceVariant
    }
    StatusChip(status.label, bg, fg)
}

@Composable
fun ServiceListScreen(onBack: () -> Unit, onOpenAsset: (String) -> Unit, vm: ServiceListViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val files: DataFilesViewModel = hiltViewModel()
    DataFilesEffect(files) { snackbar.showSnackbar(it) }
    var exportDialog by remember { mutableStateOf(false) }
    if (exportDialog) ExportDialog(ExportKind.SERVICES, files) { exportDialog = false }
    Scaffold(
        topBar = {
            AppTopBar("Servis & perawatan", onBack = onBack, actions = {
                IconButton(onClick = { exportDialog = true }) { Icon(Icons.Outlined.IosShare, contentDescription = "Export riwayat servis") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Jadwal") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Riwayat") })
            }
            if (tab == 0) ScheduleTab(vm, onOpenAsset) else HistoryTab(vm, onOpenAsset)
        }
    }
}

@Composable
private fun ScheduleTab(vm: ServiceListViewModel, onOpenAsset: (String) -> Unit) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val filter by vm.statusFilter.collectAsStateWithLifecycle()
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = filter == null, onClick = { vm.setStatus(null) }, label = { Text("Semua") })
        listOf(ServiceDueStatus.OVERDUE, ServiceDueStatus.DUE_SOON, ServiceDueStatus.SCHEDULED).forEach { s ->
            FilterChip(selected = filter == s, onClick = { vm.setStatus(s) }, label = { Text(s.label) })
        }
    }
    if (schedules.isEmpty()) {
        EmptyState(Icons.Outlined.Build, "Tidak ada jadwal servis", "Isi \"Servis berikutnya\" saat mencatat servis untuk membuat jadwal.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(schedules, key = { it.assetId }) { s ->
            ListItem(
                modifier = Modifier.clickable { onOpenAsset(s.assetId) },
                overlineContent = { Text(s.assetCode) },
                headlineContent = { Text(s.assetName) },
                supportingContent = { Text("Jatuh tempo ${Fmt.date(s.nextServiceDate)} · terakhir ${Fmt.date(s.lastServiceDate)}") },
                trailingContent = { ServiceStatusChip(s.status) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun HistoryTab(vm: ServiceListViewModel, onOpenAsset: (String) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val items = vm.history.collectAsLazyPagingItems()
    OutlinedTextField(
        value = query, onValueChange = vm::setQuery, singleLine = true,
        placeholder = { Text("Cari aset, vendor, deskripsi") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
    )
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = items.itemCount, key = items.itemKey { it.id }) { i ->
            val s = items[i] ?: return@items
            ListItem(
                modifier = Modifier.clickable { onOpenAsset(s.assetId) },
                overlineContent = { Text("${Fmt.date(s.serviceDate)} · ${s.type.label}") },
                headlineContent = { Text("${s.assetCode} · ${s.assetName}") },
                supportingContent = { Text(s.description, maxLines = 2) },
                trailingContent = { Text(Fmt.rupiah(s.cost), style = MaterialTheme.typography.labelLarge) },
            )
            HorizontalDivider()
        }
    }
}
