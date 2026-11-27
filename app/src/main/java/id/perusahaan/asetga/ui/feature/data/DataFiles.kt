package id.perusahaan.asetga.ui.feature.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.ExportFormat
import id.perusahaan.asetga.data.file.ExportImportService
import id.perusahaan.asetga.data.file.ExportKind
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.data.file.StockImportService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface DataFileEvent {
    data class Share(val file: File, val mime: String, val title: String) : DataFileEvent
    data class Message(val text: String) : DataFileEvent
}

/** Export dan unduh template untuk daftar aset, stok, dan servis. */
@HiltViewModel
class DataFilesViewModel @Inject constructor(
    private val exports: ExportImportService,
    private val stockImport: StockImportService,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _events = Channel<DataFileEvent>(Channel.BUFFERED)
    val events: Flow<DataFileEvent> = _events.receiveAsFlow()

    private fun run(block: suspend () -> DataFileEvent) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _events.send(block())
            } catch (e: Exception) {
                _events.send(DataFileEvent.Message("Gagal: ${e.message}"))
            } finally {
                _busy.value = false
            }
        }
    }

    fun export(kind: ExportKind, format: ExportFormat) = run { DataFileEvent.Share(exports.export(kind, format), format.mime, kind.label) }
    fun assetTemplate() = run { DataFileEvent.Share(exports.importTemplate(), ExportFormat.XLSX.mime, "Template import aset") }
    fun stockTemplate() = run { DataFileEvent.Share(stockImport.template(), ExportFormat.XLSX.mime, "Template import barang") }
}

/** Membuka share sheet untuk file hasil export/template, dan meneruskan pesan galat ke [onMessage]. */
@Composable
fun DataFilesEffect(vm: DataFilesViewModel, onMessage: suspend (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DataFileEvent.Share -> ShareHelper.share(context, listOf(e.file), e.mime, e.title)
                is DataFileEvent.Message -> onMessage(e.text)
            }
        }
    }
}

/** Pilih format lalu export [kind]. Berkas dibagikan lewat [DataFilesEffect] di layar pemanggil. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(kind: ExportKind, vm: DataFilesViewModel, onDismiss: () -> Unit) {
    var format by remember { mutableStateOf(ExportFormat.XLSX) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export ${kind.label.lowercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f -> FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) }) }
                }
                if (format == ExportFormat.CSV && kind == ExportKind.STOCK) {
                    Text("CSV hanya berisi lembar Stok. Pilih Excel untuk menyertakan transaksi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (format == ExportFormat.CSV && kind == ExportKind.ASSETS) {
                    Text("Excel memuat dropdown Kondisi dan Status; CSV tidak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); vm.export(kind, format) }) { Text("Export & bagikan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
