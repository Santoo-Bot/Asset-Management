package id.perusahaan.asetga.ui.feature.asset

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetListItem
import id.perusahaan.asetga.domain.model.AssetSort
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.OptionSheet
import id.perusahaan.asetga.util.Fmt
import java.io.File

private enum class FilterSheet { CATEGORY, LOCATION, DIVISION, CONDITION, STATUS, SORT }

@Composable
fun AssetListScreen(
    onOpenAsset: (String) -> Unit,
    onAddAsset: () -> Unit,
    onPrintLabels: () -> Unit,
    vm: AssetListViewModel = hiltViewModel(),
) {
    val filter by vm.filter.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val divisions by vm.divisions.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val items = vm.assets.collectAsLazyPagingItems()
    var sheet by remember { mutableStateOf<FilterSheet?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val selecting = selection.isNotEmpty()

    Scaffold(
        topBar = {
            if (selecting) {
                AppTopBar(
                    title = "${selection.size} dipilih",
                    actions = {
                        TextButton(onClick = { if (vm.prepareLabels()) onPrintLabels() }) {
                            Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(4.dp))
                            Text("Label QR", color = MaterialTheme.colorScheme.onPrimary)
                        }
                        if (vm.canDelete) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus terpilih") }
                        }
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, contentDescription = "Batal pilih") }
                    },
                )
            } else {
                AppTopBar(title = "Aset" + (count?.let { " (${Fmt.number(it.toLong())})" } ?: ""))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.canEdit && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = onAddAsset,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Tambah aset") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Cari kode, nama, no. seri, PIC") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Hapus") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = false, onClick = { sheet = FilterSheet.SORT },
                    label = { Text(filter.sort.label) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                )
                FilterChip(
                    selected = filter.categoryId != null, onClick = { sheet = FilterSheet.CATEGORY },
                    label = { Text(categories.firstOrNull { it.id == filter.categoryId }?.name ?: "Kategori") },
                )
                FilterChip(
                    selected = filter.locationId != null, onClick = { sheet = FilterSheet.LOCATION },
                    label = { Text(locations.firstOrNull { it.id == filter.locationId }?.name ?: "Lokasi") },
                )
                FilterChip(
                    selected = filter.divisionId != null, onClick = { sheet = FilterSheet.DIVISION },
                    label = { Text(divisions.firstOrNull { it.id == filter.divisionId }?.name ?: "Divisi") },
                )
                FilterChip(
                    selected = filter.condition != null, onClick = { sheet = FilterSheet.CONDITION },
                    label = { Text(filter.condition?.label ?: "Kondisi") },
                )
                FilterChip(
                    selected = filter.status != null, onClick = { sheet = FilterSheet.STATUS },
                    label = { Text(filter.status?.label ?: "Status") },
                )
                if (filter.activeFilterCount > 0) {
                    FilterChip(
                        selected = false, onClick = vm::resetFilters,
                        label = { Text("Reset") },
                        leadingIcon = { Icon(Icons.Outlined.FilterAltOff, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.error),
                    )
                }
            }
            if (items.loadState.refresh is LoadState.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = if (filter.query.isBlank() && filter.activeFilterCount == 0) "Belum ada aset" else "Tidak ada aset yang cocok",
                    message = if (vm.canEdit && filter.query.isBlank() && filter.activeFilterCount == 0) "Ketuk Tambah aset untuk mendaftarkan aset pertama." else null,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val item = items[index] ?: return@items
                        AssetRow(
                            item = item,
                            photo = vm.photoFile(item.photoPath),
                            selected = item.id in selection,
                            onClick = { if (selecting) vm.toggleSelection(item.id) else onOpenAsset(item.id) },
                            onLongClick = { vm.toggleSelection(item.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus ${selection.size} aset?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gunakan untuk data salah input. Aset hilang dari daftar dan laporan, tetapi masih bisa dipulihkan dari Lainnya → Aset terhapus. Untuk aset yang dibuang/dijual, gunakan status Write Off.")
                    FormTextField(reason, { reason = it }, "Alasan penghapusan", required = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSelected(reason) }, enabled = reason.isNotBlank()) {
                    Text("Hapus", color = if (reason.isNotBlank()) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }

    when (sheet) {
        FilterSheet.CATEGORY -> OptionSheet(
            "Kategori", categories, categories.firstOrNull { it.id == filter.categoryId }, { it.name },
            onDismiss = { sheet = null }, onSelect = { vm.setCategory(it?.id); sheet = null }, noneLabel = "Semua kategori",
        )
        FilterSheet.LOCATION -> OptionSheet(
            "Lokasi", locations, locations.firstOrNull { it.id == filter.locationId }, { it.name },
            onDismiss = { sheet = null }, onSelect = { vm.setLocation(it?.id); sheet = null }, noneLabel = "Semua lokasi",
        )
        FilterSheet.DIVISION -> OptionSheet(
            "Divisi", divisions, divisions.firstOrNull { it.id == filter.divisionId }, { it.name },
            onDismiss = { sheet = null }, onSelect = { vm.setDivision(it?.id); sheet = null }, noneLabel = "Semua divisi",
        )
        FilterSheet.CONDITION -> OptionSheet(
            "Kondisi", AssetCondition.entries, filter.condition, { it.label },
            onDismiss = { sheet = null }, onSelect = { vm.setCondition(it); sheet = null }, noneLabel = "Semua kondisi",
        )
        FilterSheet.STATUS -> OptionSheet(
            "Status", AssetStatus.entries, filter.status, { it.label },
            onDismiss = { sheet = null }, onSelect = { vm.setStatus(it); sheet = null }, noneLabel = "Semua (kecuali Write Off)",
        )
        FilterSheet.SORT -> OptionSheet(
            "Urutkan", AssetSort.entries, filter.sort, { it.label },
            onDismiss = { sheet = null }, onSelect = { it?.let(vm::setSort); sheet = null },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetRow(
    item: AssetListItem,
    photo: File?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssetThumb(photo)
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle, contentDescription = "Dipilih",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.categoryName} · ${item.locationName}" + (item.picName?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ConditionChip(item.condition)
            if (item.status != AssetStatus.DIGUNAKAN) AssetStatusChip(item.status)
        }
    }
}
