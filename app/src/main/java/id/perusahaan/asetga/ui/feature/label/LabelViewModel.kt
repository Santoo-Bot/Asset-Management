package id.perusahaan.asetga.ui.feature.label

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.file.LabelData
import id.perusahaan.asetga.data.file.LabelRenderer
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class LabelUiState(
    val loading: Boolean = true,
    val labels: List<LabelData> = emptyList(),
    val company: String = "",
    val preview: Bitmap? = null,
    val page: Int = 0,
    val busy: Boolean = false,
) {
    val pageCount: Int get() = LabelRenderer.pageCount(labels.size)
}

sealed interface LabelAction {
    data class SharePdf(val file: File) : LabelAction
    data class SharePng(val files: List<File>) : LabelAction
    data class Print(val file: File) : LabelAction
    data class Message(val text: String) : LabelAction
}

@HiltViewModel
class LabelViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    selection: LabelSelection,
    private val assets: AssetRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LabelUiState())
    val state: StateFlow<LabelUiState> = _state.asStateFlow()

    private val _actions = Channel<LabelAction>(Channel.BUFFERED)
    val actions: Flow<LabelAction> = _actions.receiveAsFlow()

    init {
        val ids = selection.assetIds
        viewModelScope.launch {
            val items = assets.getListItems(ids)
            val labels = items.map { LabelData(it.code, it.name, it.locationName) }
            val company = settings.current().companyName
            val preview = withContext(Dispatchers.Default) { if (labels.isEmpty()) null else LabelRenderer.renderPage(labels, company, 0, 1.5f) }
            _state.value = LabelUiState(loading = false, labels = labels, company = company, preview = preview)
        }
    }

    fun showPage(page: Int) {
        val s = _state.value
        if (page !in 0 until s.pageCount) return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { LabelRenderer.renderPage(s.labels, s.company, page, 1.5f) }
            _state.update { it.copy(page = page, preview = bmp) }
        }
    }

    private fun launchAction(block: suspend (LabelUiState) -> LabelAction) {
        val s = _state.value
        if (s.busy || s.labels.isEmpty()) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val action = runCatching { withContext(Dispatchers.IO) { block(s) } }
                .getOrElse { LabelAction.Message("Gagal membuat label: ${it.message}") }
            _state.update { it.copy(busy = false) }
            _actions.send(action)
        }
    }

    private fun pdfFile(s: LabelUiState): File {
        val file = File(ShareHelper.sharedDir(context), "label-aset-${System.currentTimeMillis()}.pdf")
        LabelRenderer.writePdf(s.labels, s.company, file)
        return file
    }

    fun print() = launchAction { LabelAction.Print(pdfFile(it)) }
    fun sharePdf() = launchAction { LabelAction.SharePdf(pdfFile(it)) }

    /** 1 aset → PNG satu label; banyak aset → PNG per halaman A4. */
    fun sharePng() = launchAction { s ->
        val dir = ShareHelper.sharedDir(context)
        val stamp = System.currentTimeMillis()
        val files = if (s.labels.size == 1) {
            listOf(writePng(LabelRenderer.renderSingle(s.labels.first(), s.company), File(dir, "label-${s.labels.first().code}-$stamp.png")))
        } else {
            (0 until s.pageCount).map { p -> writePng(LabelRenderer.renderPage(s.labels, s.company, p, 2f), File(dir, "label-aset-$stamp-hal${p + 1}.png")) }
        }
        LabelAction.SharePng(files)
    }

    private fun writePng(bmp: Bitmap, file: File): File {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }
}
