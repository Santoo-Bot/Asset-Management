package id.perusahaan.asetga.ui.feature.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.ExportImportService
import id.perusahaan.asetga.data.file.ImportResult
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
import javax.inject.Inject

const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

data class AssetImportState(
    val busy: Boolean = false,
    val preview: ImportPreview? = null,
    val progress: Pair<Int, Int>? = null,
    val result: ImportResult? = null,
)

@HiltViewModel
class AssetImportViewModel @Inject constructor(
    private val service: ExportImportService,
    session: SessionManager,
) : ViewModel() {
    val canImport = session.currentUser?.role?.canEditData == true

    private val _state = MutableStateFlow(AssetImportState())
    val state: StateFlow<AssetImportState> = _state.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.send("Gagal: ${e.message}")
            } finally {
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
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
                is AppResult.Failure -> _messages.send(r.message)
            }
        }
    }

    fun clearImport() = _state.update { it.copy(preview = null, result = null) }
}

/** Import aset dari Excel: baru ditambahkan, yang kodenya sudah ada diperbarui. Dibuka dari tombol Tambah aset. */
@Composable
fun AssetImportScreen(onBack: () -> Unit, vm: AssetImportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::onFilePicked) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // Langsung tawarkan pemilih file saat layar dibuka (sekali saja, tidak diulang saat layar diputar).
    var offered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!offered && vm.canImport) {
            offered = true
            pickFile.launch(arrayOf(XLSX_MIME_TYPE, "application/octet-stream"))
        }
    }

    Scaffold(topBar = { AppTopBar("Import aset", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!vm.canImport) {
                Text("Anda tidak memiliki akses untuk import.", Modifier.padding(16.dp))
                return@Column
            }
            Text(
                "Kode Aset yang belum ada ditambahkan sebagai aset baru (kolom bertanda * wajib diisi). " +
                    "Kode yang sudah ada diperbarui: sel kosong = nilai lama dibiarkan, sel terisi menimpa. Kode tidak bisa diubah. " +
                    "Setiap baris diperiksa dan ditandai baru atau diperbarui sebelum disimpan. " +
                    "Unduh template dari tombol Tambah aset, atau pakai hasil export aset.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            OutlinedButton(
                onClick = { pickFile.launch(arrayOf(XLSX_MIME_TYPE, "application/octet-stream")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.padding(4.dp)); Text("Pilih file .xlsx")
            }
            state.progress?.let { (done, total) ->
                Text("Menyimpan $done / $total…", Modifier.padding(16.dp))
                LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            state.preview?.let { p -> PreviewSection(p, busy = state.busy, onCommit = vm::commit, onCancel = vm::clearImport) }
            state.result?.let { r -> ResultSection(r, onDone = vm::clearImport) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Hasil muncul di bawah tombol; geser layar otomatis agar tidak terlewat di layar kecil. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScrollIntoView(content: @Composable () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    Box(Modifier.bringIntoViewRequester(requester)) { content() }
}

@Composable
private fun PreviewSection(p: ImportPreview, busy: Boolean, onCommit: () -> Unit, onCancel: () -> Unit) {
    ScrollIntoView { PreviewCard(p, busy, onCommit, onCancel) }
    ErrorRows(p.invalid.map { it.rowNumber to it.errors })
}

@Composable
private fun PreviewCard(p: ImportPreview, busy: Boolean, onCommit: () -> Unit, onCancel: () -> Unit) {
    InfoCard("Hasil pemeriksaan") {
        if (p.headerError != null) {
            Text(p.headerError, color = MaterialTheme.colorScheme.error)
            return@InfoCard
        }
        Text(
            "${p.created.size} aset baru · ${p.updated.size} diperbarui · ${p.unchanged.size} tanpa perubahan · ${p.invalid.size} bermasalah",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCommit, enabled = !busy && p.toSave.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Simpan ${p.toSave.size} aset")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Batal") }
    }
}

@Composable
private fun ResultSection(r: ImportResult, onDone: () -> Unit) {
    ScrollIntoView { ResultCard(r, onDone) }
    ErrorRows(r.failed.map { it.rowNumber to it.errors })
}

@Composable
private fun ResultCard(r: ImportResult, onDone: () -> Unit) {
    InfoCard("Import selesai") {
        Text(
            "${r.created} aset baru · ${r.updated} diperbarui" + if (r.unchanged > 0) " · ${r.unchanged} tanpa perubahan" else "",
            style = MaterialTheme.typography.titleMedium,
        )
        if (r.failed.isNotEmpty()) Text("${r.failed.size} baris tidak diimport (lihat di bawah)", color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Selesai") }
    }
}

@Composable
internal fun ErrorRows(rows: List<Pair<Int, List<String>>>) {
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
