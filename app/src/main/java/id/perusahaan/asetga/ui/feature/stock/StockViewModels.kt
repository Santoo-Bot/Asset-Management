package id.perusahaan.asetga.ui.feature.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.ItemInput
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MeasureUnit
import id.perusahaan.asetga.domain.model.StockItem
import id.perusahaan.asetga.domain.model.StockTxn
import id.perusahaan.asetga.domain.model.StockTxnInput
import id.perusahaan.asetga.domain.model.StockTxnType
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.StockRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.navigation.ItemDetailRoute
import id.perusahaan.asetga.ui.navigation.ItemFormRoute
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StockListEvent {
    data class OpenItem(val id: String) : StockListEvent
    data class NotFound(val code: String) : StockListEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class StockListViewModel @Inject constructor(
    private val repo: StockRepository,
    session: SessionManager,
) : ViewModel() {
    val canEdit = session.currentUser?.role?.canEditData == true

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _reorderOnly = MutableStateFlow(false)
    val reorderOnly: StateFlow<Boolean> = _reorderOnly.asStateFlow()

    val items: StateFlow<List<StockItem>?> = combine(_query.debounce(250), _reorderOnly) { q, r -> q to r }
        .flatMapLatest { (q, r) -> repo.observeItems(q, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reorderCount: StateFlow<Int> = repo.observeReorderCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _events = Channel<StockListEvent>(Channel.BUFFERED)
    val events: Flow<StockListEvent> = _events.receiveAsFlow()

    fun setQuery(q: String) { _query.value = q }
    fun setReorderOnly(v: Boolean) { _reorderOnly.value = v }

    fun onScanned(code: String) = viewModelScope.launch {
        val item = repo.findByCodeOrBarcode(code)
        _events.send(if (item != null) StockListEvent.OpenItem(item.id) else StockListEvent.NotFound(code))
    }
}

data class TxnSheetState(
    val input: StockTxnInput,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: StockRepository,
    session: SessionManager,
) : ViewModel() {
    val itemId = savedStateHandle.toRoute<ItemDetailRoute>().id
    val canEdit = session.currentUser?.role?.canEditData == true

    val item: StateFlow<StockItem?> = repo.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val txns: StateFlow<List<StockTxn>> = repo.observeTxns(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _sheet = MutableStateFlow<TxnSheetState?>(null)
    val sheet: StateFlow<TxnSheetState?> = _sheet.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun openTxn(type: StockTxnType) { if (canEdit) _sheet.value = TxnSheetState(StockTxnInput(itemId, type)) }
    fun closeTxn() { _sheet.value = null }
    fun updateTxn(transform: (StockTxnInput) -> StockTxnInput) = _sheet.update { it?.copy(input = transform(it.input), errors = emptyMap()) }

    fun saveTxn() {
        val s = _sheet.value ?: return
        if (s.saving) return
        _sheet.value = s.copy(saving = true)
        viewModelScope.launch {
            when (val r = repo.recordTxn(s.input)) {
                is AppResult.Success -> {
                    _sheet.value = null
                    _messages.send("${s.input.type.label} ${Fmt.qty(Fmt.parseDecimal(s.input.qty) ?: 0.0)} tercatat")
                }
                is AppResult.Failure -> {
                    _sheet.update { it?.copy(saving = false, errors = r.fieldErrors, message = r.message.takeIf { r.fieldErrors.isEmpty() }) }
                }
            }
        }
    }

    fun toggleActive() = viewModelScope.launch {
        val current = item.value ?: return@launch
        when (val r = repo.setItemActive(itemId, !current.isActive)) {
            is AppResult.Success -> _messages.send(if (current.isActive) "Barang dinonaktifkan" else "Barang diaktifkan kembali")
            is AppResult.Failure -> _messages.send(r.message)
        }
    }
}

data class ItemFormState(
    val input: ItemInput = ItemInput(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val loading: Boolean = true,
) {
    val isNew: Boolean get() = input.id == null
}

@HiltViewModel
class ItemFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: StockRepository,
    master: MasterDataRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ItemFormRoute>()

    private val _state = MutableStateFlow(ItemFormState(input = ItemInput(barcode = route.barcode.orEmpty())))
    val state: StateFlow<ItemFormState> = _state.asStateFlow()

    val units: StateFlow<List<MeasureUnit>> = master.observeUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations: StateFlow<List<Location>> = master.observeLocations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _results = Channel<Pair<String?, String?>>(Channel.BUFFERED) // (itemId sukses, pesan galat)
    val results: Flow<Pair<String?, String?>> = _results.receiveAsFlow()

    init {
        viewModelScope.launch {
            val id = route.id
            if (id != null) {
                run {
                    val item = repo.observeItem(id).first()
                    if (item != null) {
                        _state.value = ItemFormState(
                            loading = false,
                            input = ItemInput(
                                id = item.id, code = item.code, barcode = item.barcode.orEmpty(), name = item.name, unitId = item.unitId,
                                storageLocationId = item.storageLocationId, initialStock = Fmt.qty(item.initialStock),
                                minStock = Fmt.qty(item.minStock), notes = item.notes.orEmpty(),
                            ),
                        )
                        return@launch
                    }
                }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun update(transform: (ItemInput) -> ItemInput) = _state.update { it.copy(input = transform(it.input), errors = emptyMap()) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.saveItem(s.input)) {
                is AppResult.Success -> _results.send(r.value to null)
                is AppResult.Failure -> {
                    _state.update { it.copy(saving = false, errors = r.fieldErrors) }
                    _results.send(null to r.message)
                }
            }
        }
    }
}
