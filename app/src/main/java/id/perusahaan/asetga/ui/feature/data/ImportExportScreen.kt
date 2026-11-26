package id.perusahaan.asetga.ui.feature.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.ExportFormat
import id.perusahaan.asetga.data.file.ExportImportService
import id.perusahaan.asetga.data.file.ExportKind
import id.perusahaan.asetga.data.file.ImportResult
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.domain.excel.ImportPreview
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.ui.feature.asset.InfoCard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ImportExportState(
    val exportKind: ExportKind = ExportKind.ASSETS,
    val exportFormat: ExportFormat = ExportFormat.XLSX,
    val busy: Boolean = false,
    val preview: ImportPreview? = null,
    val progress: Pair<Int, Int>? = null,
    val result: ImportResult? = null,
)

sealed interface FileAction {
    data class Share(val file: File, val mime: String, val title: String) : FileAction
    data class Message(val text: String) : FileAction
}

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val service: ExportImportService,
    session: SessionManager,
) : ViewModel() {
    val canImport = session.currentUser?.role?.canEditData == true

    private val _state = MutableStateFlow(ImportExportState())
    val state: StateFlow<ImportExportState> = _state.asStateFlow()
    private val _actions = Channel<FileAction>(Channel.BUFFERED)
    val actions: Flow<FileAction> = _actions.receiveAsFlow()

    fun setKind(k: ExportKind) = _state.update { it.copy(exportKind = k) }
    fun setFormat(f: ExportFormat) = _state.update { it.copy(exportFormat = f) }

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _actions.send(FileAction.Message("Gagal: ${e.message}"))
            } finally {
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    fun export() = busy {
        val s = _state.value
        val file = service.export(s.exportKind, s.exportFormat)
        _actions.send(FileAction.Share(file, s.exportFormat.mime, s.exportKind.label))
    }

    fun shareTemplate() = busy {
        _actions.send(FileAction.Share(service.importTemplate(), ExportFormat.XLSX.mime, "Template import aset"))
    }

    fun onFilePicked(uri: Uri) = busy {
        _state.update { it.copy(preview = null, result = null) }
        _state.update { it.copy(preview = service.preview(uri)) }
    }

    fun commit() {
        val preview = _state.value.preview ?: return
        busy {
            when (val r = service.commit(preview) { done, total -> _state.update { it.copy(progress = done to total) } }) {
                is AppResult.Success -> _state.update { it.copy(preview = null, result = r.value) }
                is AppResult.Failure -> _actions.send(FileAction.Message(r.message))
            }
        }
    }

    fun clearImport() = _state.update { it.copy(preview = null, result = null) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportExportScreen(onBack: () -> Unit, vm: ImportExportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::onFilePicked) }
    LaunchedEffect(vm) {
        vm.actions.collect { a ->
            when (a) {
                is FileAction.Share -> ShareHelper.share(context, listOf(a.file), a.mime, a.title)
                is FileAction.Message -> snackbar.showSnackbar(a.text)
            }
        }
    }

    Scaffold(topBar = { AppTopBar("Import & export", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionHeader("Export")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportKind.entries.forEach { k -> FilterChip(selected = state.exportKind == k, onClick = { vm.setKind(k) }, label = { Text(k.label) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { f -> FilterChip(selected = state.exportFormat == f, onClick = { vm.setFormat(f) }, label = { Text(f.label) }) }
                }
                if (state.exportFormat == ExportFormat.CSV && state.exportKind == ExportKind.STOCK) {
                    Text("CSV hanya berisi lembar Stok. Pilih Excel untuk menyertakan transaksi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = vm::export, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Outlined.IosShare, null); Spacer(Modifier.padding(4.dp)); Text("Export & bagikan")
                }
            }

            if (vm.canImport) {
                SectionHeader("Import aset dari Excel")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "1. Unduh template, isi di Excel/Google Sheets (lihat lembar Petunjuk untuk nilai yang valid).\n2. Pilih file .xlsx untuk diperiksa. Baris yang salah ditampilkan sebelum disimpan.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = vm::shareTemplate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Download, null); Spacer(Modifier.padding(4.dp)); Text("Unduh / bagikan template")
                    }
                    OutlinedButton(
                        onClick = { pickFile.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.padding(4.dp)); Text("Pilih file .xlsx")
                    }
                }
                state.progress?.let { (done, total) ->
                    Text("Menyimpan $done / $total…", Modifier.padding(16.dp))
                    LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
                state.preview?.let { p -> PreviewSection(p, busy = state.busy, onCommit = vm::commit, onCancel = vm::clearImport) }
                state.result?.let { r -> ResultSection(r, onDone = vm::clearImport) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PreviewSection(p: ImportPreview, busy: Boolean, onCommit: () -> Unit, onCancel: () -> Unit) {
    InfoCard("Hasil pemeriksaan") {
        if (p.headerError != null) {
            Text(p.headerError, color = MaterialTheme.colorScheme.error)
            return@InfoCard
        }
        Text("${p.valid.size} baris valid · ${p.invalid.size} baris bermasalah", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCommit, enabled = !busy && p.valid.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Import ${p.valid.size} baris valid")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
    }
    ErrorRows(p.invalid.map { it.rowNumber to it.errors })
}

@Composable
private fun ResultSection(r: ImportResult, onDone: () -> Unit) {
    InfoCard("Import selesai") {
        Text("${r.imported} aset berhasil diimport", style = MaterialTheme.typography.titleMedium)
        if (r.failed.isNotEmpty()) Text("${r.failed.size} baris tidak diimport (lihat di bawah)", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Selesai") }
    }
    ErrorRows(r.failed.map { it.rowNumber to it.errors })
}

@Composable
private fun ErrorRows(rows: List<Pair<Int, List<String>>>) {
    if (rows.isEmpty()) return
    SectionHeader("Baris bermasalah")
    rows.take(200).forEach { (row, errors) ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("Baris $row", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
    if (rows.size > 200) Text("…dan ${rows.size - 200} baris lainnya", Modifier.padding(16.dp))
}
