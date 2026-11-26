package id.perusahaan.asetga.ui.feature.asset

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.PhotoStorage
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetInput
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.ui.navigation.AssetFormRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class AssetFormState(
    val loading: Boolean = true,
    val input: AssetInput = AssetInput(acquisitionDate = LocalDate.now()),
    val suggestedCode: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val photoBusy: Boolean = false,
) {
    val isNew: Boolean get() = input.id == null
}

data class AssetFormOptions(
    val categories: List<Category> = emptyList(),
    val locations: List<Location> = emptyList(),
    val departments: List<Department> = emptyList(),
    val divisions: List<Division> = emptyList(),
)

sealed interface AssetFormEvent {
    data class Saved(val assetId: String) : AssetFormEvent
    data class Message(val text: String) : AssetFormEvent
}

@HiltViewModel
class AssetFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: AssetRepository,
    master: MasterDataRepository,
    private val photos: PhotoStorage,
) : ViewModel() {
    private val editId: String? = savedStateHandle.toRoute<AssetFormRoute>().id

    private val _state = MutableStateFlow(AssetFormState())
    val state: StateFlow<AssetFormState> = _state.asStateFlow()

    private val _events = Channel<AssetFormEvent>(Channel.BUFFERED)
    val events: Flow<AssetFormEvent> = _events.receiveAsFlow()

    /** Hanya data aktif + yang sedang dipakai aset ini (agar data lama tetap tampil). */
    val options: StateFlow<AssetFormOptions> = combine(
        master.observeCategories(includeInactive = true),
        master.observeLocations(includeInactive = true),
        master.observeDepartments(includeInactive = true),
        master.observeDivisions(includeInactive = true),
        _state,
    ) { cats, locs, deps, divs, s ->
        val divisionId = s.input.divisionId
        AssetFormOptions(
            categories = cats.filter { it.isActive || it.id == s.input.categoryId },
            locations = locs.filter { it.isActive || it.id == s.input.locationId },
            // Departemen tersaring sesuai divisi yang dipilih.
            departments = deps.filter { (it.isActive || it.id == s.input.departmentId) && (divisionId == null || it.divisionId == divisionId) },
            divisions = divs.filter { it.isActive || it.id == divisionId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetFormOptions())

    val vendors: StateFlow<List<id.perusahaan.asetga.domain.model.Vendor>> = master.observeVendors(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val statuses: StateFlow<List<id.perusahaan.asetga.domain.model.AssetStatusOption>> = master.observeStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Foto yang dibuat selama form terbuka; dihapus bila tidak jadi dipakai. */
    private val createdPhotos = mutableSetOf<String>()
    private var originalPhoto: String? = null

    init {
        viewModelScope.launch {
            if (editId != null) {
                val a = repo.get(editId)
                if (a == null) {
                    _events.send(AssetFormEvent.Message("Aset tidak ditemukan"))
                } else {
                    originalPhoto = a.photoPath
                    _state.value = AssetFormState(
                        loading = false,
                        input = AssetInput(
                            id = a.id, code = a.code, name = a.name, categoryId = a.categoryId,
                            brandModel = a.brandModel.orEmpty(), serialNumber = a.serialNumber.orEmpty(),
                            acquisitionDate = a.acquisitionDate, acquisitionCost = a.acquisitionCost,
                            locationId = a.locationId, departmentId = a.departmentId, picName = a.picName.orEmpty(),
                            condition = a.condition, status = a.status, warrantyUntil = a.warrantyUntil,
                            notes = a.notes.orEmpty(), photoPath = a.photoPath,
                            disposalDate = a.disposalDate, disposalValue = a.disposalValue,
                            qrCode = a.qrContent, userLocationDetail = a.userLocationDetail.orEmpty(),
                            divisionId = a.divisionId, prNumber = a.prNumber.orEmpty(), poNumber = a.poNumber.orEmpty(),
                            grNumber = a.grNumber.orEmpty(), requester = a.requester.orEmpty(), memoNumber = a.memoNumber.orEmpty(),
                            memoDate = a.memoDate, followUp = a.followUp.orEmpty(), handoverDoc = a.handoverDoc.orEmpty(),
                            accessories = a.accessories.orEmpty(), vendorId = a.vendorId,
                        ),
                    )
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Pilih divisi: departemen dikosongkan bila bukan bagian dari divisi baru. */
    fun selectDivision(division: Division?) {
        val deptDivision = options.value.departments.firstOrNull { it.id == _state.value.input.departmentId }?.divisionId
        update { it.copy(divisionId = division?.id, departmentId = if (division != null && deptDivision == division.id) it.departmentId else null) }
    }

    /** Pilih departemen: divisi otomatis mengikuti divisi induk departemen. */
    fun selectDepartment(department: Department?) {
        update { it.copy(departmentId = department?.id, divisionId = department?.divisionId ?: it.divisionId) }
    }

    fun update(transform: (AssetInput) -> AssetInput) {
        val before = _state.value.input
        var after = transform(before)
        // Kode QR mengikuti kode aset selama pengguna belum mengisinya dengan nilai lain.
        if (after.code != before.code && after.qrCode == before.qrCode && (before.qrCode.isBlank() || before.qrCode == before.code)) {
            after = after.copy(qrCode = after.code)
        }
        _state.update { it.copy(input = after, errors = it.errors - changedFields(before, after)) }
        if (after.id == null && (before.categoryId != after.categoryId || before.acquisitionDate?.year != after.acquisitionDate?.year)) {
            refreshSuggestion()
        }
    }

    private fun changedFields(a: AssetInput, b: AssetInput): Set<String> = buildSet {
        if (a.code != b.code) add("code")
        if (a.name != b.name) add("name")
        if (a.categoryId != b.categoryId) add("category")
        if (a.locationId != b.locationId) add("location")
        if (a.acquisitionDate != b.acquisitionDate) add("acquisitionDate")
        if (a.acquisitionCost != b.acquisitionCost) add("acquisitionCost")
        if (a.warrantyUntil != b.warrantyUntil) add("warranty")
        if (a.disposalDate != b.disposalDate) add("disposalDate")
        if (a.qrCode != b.qrCode) add("qrCode")
    }

    private fun refreshSuggestion() {
        val input = _state.value.input
        val cat = input.categoryId ?: return
        val year = (input.acquisitionDate ?: LocalDate.now()).year
        viewModelScope.launch {
            val code = repo.suggestCode(cat, year)
            val previous = _state.value.suggestedCode
            _state.update { it.copy(suggestedCode = code) }
            // Isi kode otomatis bila kosong atau masih berisi usulan sebelumnya (belum diubah pengguna).
            val current = _state.value.input.code
            if (current.isBlank() || current == previous) update { it.copy(code = code) }
        }
    }

    fun newCameraUri(): Uri = photos.newCameraUri()

    fun photoFile(): File? = photos.resolve(_state.value.input.photoPath)

    fun onPhotoPicked(uri: Uri) {
        _state.update { it.copy(photoBusy = true) }
        viewModelScope.launch {
            val path = photos.importCompressed(uri)
            if (path == null) {
                _state.update { it.copy(photoBusy = false) }
                _events.send(AssetFormEvent.Message("Foto tidak dapat dibaca"))
                return@launch
            }
            createdPhotos += path
            discardUnsavedPhoto(_state.value.input.photoPath)
            _state.update { it.copy(photoBusy = false, input = it.input.copy(photoPath = path)) }
        }
    }

    fun removePhoto() {
        discardUnsavedPhoto(_state.value.input.photoPath)
        _state.update { it.copy(input = it.input.copy(photoPath = null)) }
    }

    private fun discardUnsavedPhoto(path: String?) {
        if (path != null && path in createdPhotos) {
            photos.delete(path)
            createdPhotos -= path
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true, errors = emptyMap()) }
        viewModelScope.launch {
            when (val r = repo.save(s.input)) {
                is AppResult.Success -> {
                    // Foto lama dihapus hanya setelah perubahan tersimpan.
                    if (originalPhoto != null && originalPhoto != r.value.photoPath) photos.delete(originalPhoto)
                    createdPhotos.clear()
                    _events.send(AssetFormEvent.Saved(r.value.id))
                }
                is AppResult.Failure -> {
                    _state.update { it.copy(saving = false, errors = r.fieldErrors) }
                    _events.send(AssetFormEvent.Message(r.message))
                }
            }
        }
    }

    override fun onCleared() {
        // Form ditutup tanpa simpan: buang foto yang sempat diambil.
        createdPhotos.forEach(photos::delete)
        super.onCleared()
    }
}
