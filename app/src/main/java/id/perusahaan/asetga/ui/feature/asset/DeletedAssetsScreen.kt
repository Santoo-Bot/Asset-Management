package id.perusahaan.asetga.ui.feature.asset

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.DeletedAsset
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeletedAssetsViewModel @Inject constructor(private val repo: AssetRepository) : ViewModel() {
    val items: StateFlow<List<DeletedAsset>?> = repo.observeDeleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun restore(asset: DeletedAsset) = viewModelScope.launch {
        when (val r = repo.restore(asset.id)) {
            is AppResult.Success -> _messages.send("${asset.code} dipulihkan")
            is AppResult.Failure -> _messages.send(r.message)
        }
    }
}

/** Daftar aset yang dihapus karena salah input; Admin dapat memulihkannya. */
@Composable
fun DeletedAssetsScreen(onBack: () -> Unit, vm: DeletedAssetsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<DeletedAsset?>(null) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { AppTopBar("Aset terhapus", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val list = items ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.DeleteOutline, "Tidak ada aset terhapus", "Aset yang dihapus karena salah input muncul di sini dan bisa dipulihkan.", modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.id }) { a ->
                ListItem(
                    overlineContent = { Text(a.code) },
                    headlineContent = { Text(a.name) },
                    supportingContent = { Text("${a.categoryName} · ${a.locationName} · dihapus ${Fmt.dateTime(a.deletedAt)}") },
                    trailingContent = {
                        IconButton(onClick = { confirm = a }) { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Pulihkan") }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    confirm?.let { a ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Pulihkan ${a.code}?") },
            text = { Text("${a.name} akan muncul kembali di daftar aset dan laporan.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.restore(a) }) { Text("Pulihkan") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}
