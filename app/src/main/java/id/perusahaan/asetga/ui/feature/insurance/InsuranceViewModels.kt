package id.perusahaan.asetga.ui.feature.insurance

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.InsuranceFileService
import id.perusahaan.asetga.domain.calc.PolicyStatus
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetFilter
import id.perusahaan.asetga.domain.model.AssetListItem
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.InsurancePolicy
import id.perusahaan.asetga.domain.model.InsuranceImportPreview
import id.perusahaan.asetga.domain.model.InsuranceType
import id.perusahaan.asetga.domain.model.InsuredAsset
import id.perusahaan.asetga.domain.model.Insurer
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.PolicyInput
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.InsuranceRepository
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.navigation.InsuranceAssetPickerRoute
import id.perusahaan.asetga.ui.navigation.PolicyDetailRoute
import id.perusahaan.asetga.ui.navigation.PolicyFormRoute
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// ---------------- Daftar polis ----------------

@HiltViewModel
class InsuranceListViewModel @Inject constructor(
    repo: InsuranceRepository,
    master: MasterDataRepository,
    session: SessionManager,
) : ViewModel() {
    val canEdit = session.currentUser?.role?.canEditData == true
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _status = MutableStateFlow<PolicyStatus?>(null)
    val status: StateFlow<PolicyStatus?> = _status.asStateFlow()

    private val all = repo.observePolicies()
    val policies: StateFlow<List<InsurancePolicy>?> = combine(all, _query, _status) { list, q, st ->
        list.filter { p ->
            (st == null || p.status == st) &&
                (q.isBlank() || listOf(p.policyNumber, p.typeName, p.insurerName).any { it.contains(q.trim(), ignoreCase = true) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val counts: StateFlow<Map<PolicyStatus, Int>> = combine(all, _query) { list, _ -> list.groupingBy { it.status }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Perlu master tipe & perusahaan sebelum membuat polis. */
    val mastersReady: StateFlow<Boolean> = combine(master.observeInsuranceTypes(), master.observeInsurers()) { t, i -> t.isNotEmpty() && i.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setQuery(q: String) { _query.value = q }
    fun setStatus(s: PolicyStatus?) { _status.value = s }
}

// ---------------- Form polis ----------------

data class PolicyFormState(
    val input: PolicyInput = PolicyInput(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val loading: Boolean = true,
) {
    val isNew get() = input.id == null
}

@HiltViewModel
class PolicyFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: InsuranceRepository,
    master: MasterDataRepository,
) : ViewModel() {
    private val editId = savedStateHandle.toRoute<PolicyFormRoute>().id
    private val _state = MutableStateFlow(PolicyFormState())
    val state: StateFlow<PolicyFormState> = _state.asStateFlow()
    val types: StateFlow<List<InsuranceType>> = master.observeInsuranceTypes(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val insurers: StateFlow<List<Insurer>> = master.observeInsurers(includeInactive = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _results = Channel<Pair<String?, String?>>(Channel.BUFFERED)
    val results: Flow<Pair<String?, String?>> = _results.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = editId?.let { repo.getPolicyInput(it) }
            _state.value = PolicyFormState(input = existing ?: PolicyInput(), loading = false)
        }
    }

    fun update(t: (PolicyInput) -> PolicyInput) = _state.update { it.copy(input = t(it.input), errors = emptyMap()) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.savePolicy(s.input)) {
                is AppResult.Success -> _results.send(r.value to null)
                is AppResult.Failure -> {
                    _state.update { it.copy(saving = false, errors = r.fieldErrors) }
                    _results.send(null to r.message)
                }
            }
        }
    }
}

// ---------------- Detail polis ----------------

sealed interface PolicyEvent {
    data class Message(val text: String) : PolicyEvent
    data class Share(val file: File) : PolicyEvent
    data object Deleted : PolicyEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PolicyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: InsuranceRepository,
    private val files: InsuranceFileService,
    session: SessionManager,
) : ViewModel() {
    val policyId = savedStateHandle.toRoute<PolicyDetailRoute>().id
    val canEdit = session.currentUser?.role?.canEditData == true
    val canDelete = session.currentUser?.role?.canManageMaster == true

    val policy: StateFlow<InsurancePolicy?> = repo.observePolicy(policyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val assets: StateFlow<List<InsuredAsset>?> = combine(repo.observeInsuredAssets(policyId), _query) { list, q ->
        if (q.isBlank()) list else list.filter { a ->
            listOf(a.code, a.name, a.qrCode.orEmpty(), a.locationName, a.categoryName).any { it.contains(q.trim(), ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _preview = MutableStateFlow<InsuranceImportPreview?>(null)
    val preview: StateFlow<InsuranceImportPreview?> = _preview.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _events = Channel<PolicyEvent>(Channel.BUFFERED)
    val events: Flow<PolicyEvent> = _events.receiveAsFlow()

    fun setQuery(q: String) { _query.value = q }

    private fun op(block: suspend () -> PolicyEvent?) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val e = runCatching { block() }.getOrElse { PolicyEvent.Message("Gagal: ${it.message}") }
            _busy.value = false
            e?.let { _events.send(it) }
        }
    }

    private fun AppResult<*>.message(ok: String): PolicyEvent = when (this) {
        is AppResult.Success -> PolicyEvent.Message(ok)
        is AppResult.Failure -> PolicyEvent.Message(message)
    }

    fun addByCode(code: String) = op {
        when (val r = repo.addAssetByCode(policyId, code)) {
            is AppResult.Success -> PolicyEvent.Message(r.value)
            is AppResult.Failure -> PolicyEvent.Message(r.message)
        }
    }

    fun remove(asset: InsuredAsset) = op { repo.removeAsset(policyId, asset.assetId).message("${asset.code} dikeluarkan dari polis") }
    fun setSum(asset: InsuredAsset, value: Long?) = op { repo.setSumInsured(policyId, asset.assetId, value).message("Nilai pertanggungan ${asset.code} disimpan") }

    fun onFilePicked(uri: Uri) = op {
        val sheet = files.readSheet(uri)
        _preview.value = repo.previewImport(policyId, sheet)
        null
    }

    fun commitImport() {
        val p = _preview.value ?: return
        op {
            when (val r = repo.commitImport(policyId, p)) {
                is AppResult.Success -> {
                    _preview.value = null
                    PolicyEvent.Message(
                        "${r.value.added} aset ditambahkan" + (if (r.value.alreadyInPolicy > 0) ", ${r.value.alreadyInPolicy} sudah ada di polis" else "") +
                            (if (p.invalid.isNotEmpty()) ", ${p.invalid.size} baris dilewati" else ""),
                    )
                }
                is AppResult.Failure -> PolicyEvent.Message(r.message)
            }
        }
    }

    fun cancelImport() { _preview.value = null }

    fun shareTemplate() = op { PolicyEvent.Share(files.template()) }
    fun exportPolicy() = op { policy.value?.let { PolicyEvent.Share(files.exportPolicy(it)) } }
    fun delete() = op {
        when (val r = repo.deletePolicy(policyId)) {
            is AppResult.Success -> PolicyEvent.Deleted
            is AppResult.Failure -> PolicyEvent.Message(r.message)
        }
    }
}

// ---------------- Pilih aset (manual) ----------------

@HiltViewModel
class InsuranceAssetPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    assets: AssetRepository,
    private val repo: InsuranceRepository,
    master: MasterDataRepository,
) : id.perusahaan.asetga.ui.components.AssetPickerViewModel(assets, master) {
    val policyId = savedStateHandle.toRoute<InsuranceAssetPickerRoute>().policyId

    override fun existingIds(): Flow<Set<String>> = repo.observeAssetIdsInPolicy(policyId)

    override suspend fun addAssets(ids: List<String>): String = when (val r = repo.addAssets(policyId, ids)) {
        is AppResult.Success -> "${r.value.added} aset ditambahkan" + if (r.value.alreadyInPolicy > 0) " (${r.value.alreadyInPolicy} sudah ada)" else ""
        is AppResult.Failure -> r.message
    }
}
