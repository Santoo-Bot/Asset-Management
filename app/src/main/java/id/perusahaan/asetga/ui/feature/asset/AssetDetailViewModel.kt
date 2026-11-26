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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

sealed interface AssetDetailState {
    data object Loading : AssetDetailState
    data object NotFound : AssetDetailState
    data class Loaded(val detail: AssetDetail) : AssetDetailState
}

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: AssetRepository,
    audit: AuditLogRepository,
    session: SessionManager,
    private val photos: PhotoStorage,
    private val labelSelection: LabelSelection,
) : ViewModel() {
    val assetId: String = savedStateHandle.toRoute<AssetDetailRoute>().id
    val canEdit: Boolean = session.currentUser?.role?.canEditData == true

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
