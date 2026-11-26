package id.perusahaan.asetga.ui.feature.operation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.PhotoStorage
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.ServiceInput
import id.perusahaan.asetga.domain.model.Vendor
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.OperationRepository
import id.perusahaan.asetga.ui.navigation.ServiceFormRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ServiceFormState(
    val assetLabel: String = "",
    val input: ServiceInput,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val photoBusy: Boolean = false,
) {
    val isNew: Boolean get() = input.id == null
}

@HiltViewModel
class ServiceFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    assets: AssetRepository,
    master: MasterDataRepository,
    private val ops: OperationRepository,
    private val photos: PhotoStorage,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ServiceFormRoute>()

    private val _state = MutableStateFlow(ServiceFormState(input = ServiceInput(assetId = route.assetId)))
    val state: StateFlow<ServiceFormState> = _state.asStateFlow()

    val vendors: StateFlow<List<Vendor>> = master.observeVendors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _results = Channel<String?>(Channel.BUFFERED)
    val results: Flow<String?> = _results.receiveAsFlow()

    private val createdPhotos = mutableSetOf<String>()
    private var originalPhoto: String? = null

    init {
        viewModelScope.launch {
            assets.get(route.assetId)?.let { a -> _state.update { it.copy(assetLabel = "${a.code} · ${a.name}") } }
            route.serviceId?.let { id ->
                ops.getService(id)?.let { s ->
                    originalPhoto = s.photoPath
                    _state.update {
                        it.copy(
                            input = ServiceInput(
                                id = s.id, assetId = s.assetId, serviceDate = s.serviceDate, type = s.type, vendorId = s.vendorId,
                                cost = s.cost, description = s.description, photoPath = s.photoPath, nextServiceDate = s.nextServiceDate,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun update(transform: (ServiceInput) -> ServiceInput) = _state.update { it.copy(input = transform(it.input), errors = emptyMap()) }

    fun newCameraUri(): Uri = photos.newCameraUri()
    fun photoFile(): File? = photos.resolve(_state.value.input.photoPath)

    fun onPhotoPicked(uri: Uri) {
        _state.update { it.copy(photoBusy = true) }
        viewModelScope.launch {
            val path = photos.importCompressed(uri)
            if (path != null) {
                createdPhotos += path
                _state.value.input.photoPath?.takeIf { it in createdPhotos }?.let { photos.delete(it); createdPhotos -= it }
                _state.update { it.copy(input = it.input.copy(photoPath = path)) }
            } else {
                _results.send("Foto tidak dapat dibaca")
            }
            _state.update { it.copy(photoBusy = false) }
        }
    }

    fun removePhoto() {
        _state.value.input.photoPath?.takeIf { it in createdPhotos }?.let { photos.delete(it); createdPhotos -= it }
        _state.update { it.copy(input = it.input.copy(photoPath = null)) }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.photoBusy) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = ops.saveService(s.input)) {
                is AppResult.Success -> {
                    if (originalPhoto != null && originalPhoto != s.input.photoPath) photos.delete(originalPhoto)
                    createdPhotos.clear()
                    _results.send(null)
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(saving = false, errors = r.fieldErrors) }
                    _results.send(r.message)
                }
            }
        }
    }

    override fun onCleared() {
        createdPhotos.forEach(photos::delete)
        super.onCleared()
    }
}
