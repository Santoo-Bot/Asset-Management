package id.perusahaan.asetga.ui.feature.operation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetDetail
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MutationInput
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.OperationRepository
import id.perusahaan.asetga.ui.navigation.MutationFormRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MutationFormState(
    val asset: AssetDetail? = null,
    val input: MutationInput,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
)

@HiltViewModel
class MutationFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    assets: AssetRepository,
    master: MasterDataRepository,
    private val ops: OperationRepository,
) : ViewModel() {
    private val assetId = savedStateHandle.toRoute<MutationFormRoute>().assetId

    private val _state = MutableStateFlow(MutationFormState(input = MutationInput(assetId)))
    val state: StateFlow<MutationFormState> = _state.asStateFlow()

    val locations: StateFlow<List<Location>> = master.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val departments: StateFlow<List<Department>> = master.observeDepartments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _done = Channel<String?>(Channel.BUFFERED)
    /** null = sukses, teks = pesan galat. */
    val results: Flow<String?> = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val d = assets.observeDetail(assetId).first() ?: return@launch
            // Isi awal = nilai saat ini; pengguna mengubah yang dipindahkan.
            _state.update {
                it.copy(
                    asset = d,
                    input = it.input.copy(
                        toLocationId = d.asset.locationId,
                        toDepartmentId = d.asset.departmentId,
                        toPic = d.asset.picName.orEmpty(),
                    ),
                )
            }
        }
    }

    fun update(transform: (MutationInput) -> MutationInput) = _state.update { it.copy(input = transform(it.input), errors = emptyMap()) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = ops.recordMutation(s.input)) {
                is AppResult.Success -> _done.send(null)
                is AppResult.Failure -> {
                    _state.update { it.copy(saving = false, errors = r.fieldErrors) }
                    _done.send(r.message)
                }
            }
        }
    }
}
