package id.perusahaan.asetga.ui.feature.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.PhotoStorage
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetFilter
import id.perusahaan.asetga.domain.model.AssetListItem
import id.perusahaan.asetga.domain.model.AssetSort
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.feature.label.LabelSelection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import id.perusahaan.asetga.domain.model.AppResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val repo: AssetRepository,
    master: MasterDataRepository,
    session: SessionManager,
    private val photos: PhotoStorage,
    private val labelSelection: LabelSelection,
) : ViewModel() {

    val canEdit: Boolean = session.currentUser?.role?.canEditData == true
    val canDelete: Boolean = session.currentUser?.role?.canManageMaster == true

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _busy = MutableStateFlow(false)
    /** Sedang memproses hapus massal; UI menampilkan progres dan menonaktifkan tombol. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Hapus PERMANEN semua aset terpilih. Khusus Admin GA. */
    fun deleteSelected(reason: String) {
        val ids = _selection.value.toList()
        if (ids.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val r = repo.delete(ids, reason)
            _busy.value = false
            when (r) {
                is AppResult.Success -> {
                    withContext(Dispatchers.IO) { r.value.photoPaths.forEach(photos::delete) }
                    clearSelection()
                    _messages.send("${r.value.count} aset dihapus permanen")
                }
                is AppResult.Failure -> _messages.send(r.message)
            }
        }
    }

    private val _filter = MutableStateFlow(AssetFilter())
    val filter: StateFlow<AssetFilter> = _filter.asStateFlow()

    /** Pencarian di-debounce agar tidak query setiap ketikan. */
    private val effectiveFilter: Flow<AssetFilter> = combine(
        _filter.debounce { if (it.query.isEmpty()) 0L else 300L },
        _filter,
    ) { debounced, latest -> latest.copy(query = debounced.query) }.distinctUntilChanged()

    val assets: Flow<PagingData<AssetListItem>> = effectiveFilter
        .flatMapLatest { repo.paging(it) }
        .cachedIn(viewModelScope)

    val count: StateFlow<Int?> = effectiveFilter.flatMapLatest { repo.count(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<Category>> = master.observeCategories(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations: StateFlow<List<Location>> = master.observeLocations(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val divisions: StateFlow<List<Division>> = master.observeDivisions(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val departments: StateFlow<List<id.perusahaan.asetga.domain.model.Department>> = master.observeDepartments(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statuses: StateFlow<List<id.perusahaan.asetga.domain.model.AssetStatusOption>> = master.observeStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun setCategory(id: String?) = _filter.update { it.copy(categoryId = id) }
    fun setLocation(id: String?) = _filter.update { it.copy(locationId = id) }
    fun setDivision(id: String?) = _filter.update { it.copy(divisionId = id) }
    fun setDepartment(id: String?) = _filter.update { it.copy(departmentId = id) }
    fun setCondition(c: AssetCondition?) = _filter.update { it.copy(condition = c) }
    fun setStatus(s: AssetStatus?) = _filter.update { it.copy(status = s) }
    fun setSort(s: AssetSort) = _filter.update { it.copy(sort = s) }
    fun resetFilters() = _filter.update { AssetFilter(query = it.query, sort = it.sort) }

    /** Pilih semua aset yang cocok dengan pencarian & filter saat ini (bukan hanya yang tampil di layar). */
    fun selectAll() {
        viewModelScope.launch {
            _selection.value = repo.filteredIds(_filter.value).toSet()
            if (_selection.value.isEmpty()) _messages.send("Tidak ada aset untuk dipilih")
        }
    }

    fun toggleSelection(id: String) = _selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { _selection.value = emptySet() }

    /** Simpan pilihan untuk layar label lalu kosongkan mode pilih. */
    fun prepareLabels(): Boolean {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return false
        labelSelection.assetIds = ids
        clearSelection()
        return true
    }

    fun photoFile(path: String?): File? = photos.resolve(path)
}
