package id.perusahaan.asetga.ui.feature.asset

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.PhotoStorage
import id.perusahaan.asetga.domain.model.AssetDetail
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.feature.label.LabelSelection
import id.perusahaan.asetga.ui.navigation.AssetDetailRoute
import id.perusahaan.asetga.domain.model.AppResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

sealed interface DetailEvent {
    data class Message(val text: String) : DetailEvent
    data object Deleted : DetailEvent
}

sealed interface AssetDetailState {
    data object Loading : AssetDetailState
    data object NotFound : AssetDetailState
    data class Loaded(val detail: AssetDetail) : AssetDetailState
}

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: AssetRepository,
    audit: AuditLogRepository,
    session: SessionManager,
    private val photos: PhotoStorage,
    private val labelSelection: LabelSelection,
) : ViewModel() {
    val assetId: String = savedStateHandle.toRoute<AssetDetailRoute>().id
    val canEdit: Boolean = session.currentUser?.role?.canEditData == true
    val canDelete: Boolean = session.currentUser?.role?.canManageMaster == true

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun launchOp(block: suspend () -> DetailEvent) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val e = block()
            _busy.value = false
            _events.send(e)
        }
    }

    fun dispose(date: LocalDate, value: Long?, note: String) = launchOp {
        when (val r = repo.dispose(assetId, date, value, note)) {
            is AppResult.Success -> DetailEvent.Message("Aset ditandai Write Off")
            is AppResult.Failure -> DetailEvent.Message(r.message)
        }
    }

    fun delete(reason: String) = launchOp {
        when (val r = repo.delete(listOf(assetId), reason)) {
            is AppResult.Success -> DetailEvent.Deleted
            is AppResult.Failure -> DetailEvent.Message(r.message)
        }
    }

    val state: StateFlow<AssetDetailState> = repo.observeDetail(assetId)
        .map { d -> if (d == null) AssetDetailState.NotFound else AssetDetailState.Loaded(d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetDetailState.Loading)

    val history: StateFlow<List<AuditLogEntry>> = audit.observeForEntity(AuditEntityType.ASSET, assetId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun photoFile(path: String?): File? = photos.resolve(path)

    fun prepareLabel() {
        labelSelection.assetIds = listOf(assetId)
    }
}
