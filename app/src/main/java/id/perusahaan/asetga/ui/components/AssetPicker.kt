package id.perusahaan.asetga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import id.perusahaan.asetga.domain.model.AssetFilter
import id.perusahaan.asetga.domain.model.AssetListItem
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pemilih aset bersama (polis asuransi, sesi opname): cari + filter, centang satu per satu, atau tambah semua hasil filter.
 * Aset Write Off tidak ditampilkan.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
abstract class AssetPickerViewModel(
    private val assets: AssetRepository,
    master: MasterDataRepository,
) : ViewModel() {
    /** Aset yang sudah ada di tujuan (ditampilkan tercentang & tidak bisa dipilih). */
    protected abstract fun existingIds(): Flow<Set<String>>

    /** Tambahkan aset ke tujuan; mengembalikan pesan hasil. */
    protected abstract suspend fun addAssets(ids: List<String>): String

    private val _filter = MutableStateFlow(AssetFilter(excludeWriteOff = true))
    val filter: StateFlow<AssetFilter> = _filter.asStateFlow()
    val items: Flow<PagingData<AssetListItem>> = _filter.debounce(250).flatMapLatest { assets.paging(it) }.cachedIn(viewModelScope)
    val count: StateFlow<Int?> = _filter.debounce(250).flatMapLatest { assets.count(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val existing: StateFlow<Set<String>> by lazy {
        existingIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    }
    val categories: StateFlow<List<Category>> = master.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations: StateFlow<List<Location>> = master.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _done = Channel<String>(Channel.BUFFERED)
    /** Pesan hasil; layar ditutup setelah menerima pesan. */
    val done: Flow<String> = _done.receiveAsFlow()

    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun setCategory(id: String?) = _filter.update { it.copy(categoryId = id) }
    fun setLocation(id: String?) = _filter.update { it.copy(locationId = id) }
    fun toggle(id: String) = _selection.update { if (id in it) it - id else it + id }

    fun addSelected() = add { _selection.value.toList() }

    /** Tambah semua aset yang cocok dengan filter (mis. semua aset di satu lokasi). */
    fun addAllFiltered() = add { assets.filteredIds(_filter.value) }

    private fun add(ids: suspend () -> List<String>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val message = runCatching { addAssets(ids()) }.getOrElse { "Gagal: ${it.message}" }
            _busy.value = false
            _done.send(message)
        }
    }
}

@Composable
fun AssetPickerScreen(
    vm: AssetPickerViewModel,
    existingLabel: String,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val existing by vm.existing.collectAsStateWithLifecycle()
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
                    val already = a.id in existing
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
                        supportingContent = { Text(if (already) existingLabel else "${a.categoryName} · ${a.locationName}") },
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
            text = { Text("Semua aset yang cocok dengan pencarian & filter saat ini akan ditambahkan. Aset yang sudah ada dilewati.") },
            confirmButton = { TextButton(onClick = { confirmAll = false; vm.addAllFiltered() }) { Text("Tambahkan") } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Batal") } },
        )
    }
}
