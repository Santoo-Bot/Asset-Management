package id.perusahaan.asetga.ui.feature.master

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Category
import id.perusahaan.asetga.domain.model.Department
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.InsuranceType
import id.perusahaan.asetga.domain.model.Insurer
import id.perusahaan.asetga.domain.model.Location
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.model.MeasureUnit
import id.perusahaan.asetga.domain.model.Vendor
import id.perusahaan.asetga.domain.repository.MasterDataRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.validation.MasterValidator
import id.perusahaan.asetga.ui.navigation.MasterListRoute
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MasterRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val badge: String?,
    val isActive: Boolean,
    val form: MasterForm,
)

/** Semua field form master dalam bentuk teks; field yang tidak relevan untuk tipe tertentu diabaikan. */
data class MasterForm(
    val id: String = "",
    val name: String = "",
    val codePrefix: String = "",
    val usefulLifeYears: String = "",
    val residualPercent: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val divisionId: String? = null,
)

data class MasterEditorState(
    val form: MasterForm,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    /** Pesan galat ditampilkan DI DALAM panel (snackbar tertutup oleh bottom sheet). */
    val message: String? = null,
    /** Hapus ditolak karena masih dipakai: tampilkan tombol Lihat aset / Pindahkan lalu hapus. */
    val blockedByUsage: Boolean = false,
) {
    val isNew: Boolean get() = form.id.isEmpty()
}

data class MasterListUiState(
    val rows: List<MasterRow> = emptyList(),
    val loading: Boolean = true,
    val query: String = "",
    val showInactive: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MasterListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MasterDataRepository,
    settings: SettingsRepository,
    session: SessionManager,
) : ViewModel() {

    val type: MasterType = MasterType.valueOf(savedStateHandle.toRoute<MasterListRoute>().type)
    val canEdit: Boolean = session.currentUser?.role?.canManageMaster == true

    private val query = MutableStateFlow("")
    /** Data nonaktif (sisa versi lama) selalu ditampilkan agar bisa diaktifkan kembali atau dihapus. */
    private val showInactive = MutableStateFlow(true)
    private val defaultResidual = settings.settings.map { it.defaultResidualPercent }

    val uiState: StateFlow<MasterListUiState> =
        combine(showInactive.flatMapLatest { rowsFor(it) }, query, showInactive) { rows, q, inactive ->
            val filtered = if (q.isBlank()) rows else rows.filter { r ->
                r.title.contains(q, ignoreCase = true) || r.subtitle?.contains(q, ignoreCase = true) == true
            }
            MasterListUiState(rows = filtered, loading = false, query = q, showInactive = inactive)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MasterListUiState())

    /** Pilihan divisi induk untuk form departemen. */
    val divisions: StateFlow<List<Division>> = repo.observeDivisions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow<MasterEditorState?>(null)
    val editor: StateFlow<MasterEditorState?> = _editor.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setQuery(q: String) { query.value = q }

    fun openNew() { if (canEdit) _editor.value = MasterEditorState(MasterForm()) }
    fun openEdit(row: MasterRow) { if (canEdit) _editor.value = MasterEditorState(row.form) }
    fun closeEditor() { _editor.value = null }
    fun updateForm(transform: (MasterForm) -> MasterForm) = _editor.update { it?.copy(form = transform(it.form), message = null) }

    fun save() {
        val current = _editor.value ?: return
        if (current.saving) return
        val f = current.form
        _editor.value = current.copy(saving = true, errors = emptyMap(), message = null)
        viewModelScope.launch {
            val result: AppResult<Any> = when (type) {
                MasterType.CATEGORY -> {
                    val life = f.usefulLifeYears.trim().toIntOrNull()
                    val residual = f.residualPercent.takeIf { it.isNotBlank() }?.let(Fmt::parseDecimal)
                    val parseErrors = buildMap {
                        if (life == null) put(MasterValidator.FIELD_LIFE, "Isi angka tahun")
                        if (f.residualPercent.isNotBlank() && residual == null) put(MasterValidator.FIELD_RESIDUAL, "Isi angka, mis. 10 atau 12,5")
                    }
                    if (parseErrors.isNotEmpty()) {
                        AppResult.Failure("Periksa kembali isian", parseErrors)
                    } else {
                        repo.saveCategory(Category(f.id, f.name, f.codePrefix, life!!, residual))
                    }
                }
                MasterType.LOCATION -> repo.saveLocation(Location(f.id, f.name, f.address))
                MasterType.DIVISION -> repo.saveDivision(Division(f.id, f.name))
                MasterType.DEPARTMENT -> repo.saveDepartment(Department(f.id, f.name, f.divisionId))
                MasterType.VENDOR -> repo.saveVendor(Vendor(f.id, f.name, f.phone, f.email, f.address))
                MasterType.UNIT -> repo.saveUnit(MeasureUnit(f.id, f.name))
                MasterType.INSURANCE_TYPE -> repo.saveInsuranceType(InsuranceType(f.id, f.name))
                MasterType.INSURER -> repo.saveInsurer(Insurer(f.id, f.name, f.phone, f.email, f.address))
            }
            when (result) {
                is AppResult.Success -> {
                    _editor.value = null
                    _messages.send("${type.label} ${if (f.id.isEmpty()) "ditambahkan" else "disimpan"}")
                }
                is AppResult.Failure -> {
                    _editor.update { it?.copy(saving = false, errors = result.fieldErrors, message = result.message.takeIf { result.fieldErrors.isEmpty() }) }
                }
            }
        }
    }

    /** Pindah-lalu-hapus hanya untuk departemen & divisi. */
    val canMove: Boolean = type == MasterType.DEPARTMENT || type == MasterType.DIVISION

    /** Pilihan tujuan pemindahan (selain data yang sedang dihapus). */
    val moveTargets: StateFlow<List<Pair<String, String>>> = when (type) {
        MasterType.DEPARTMENT -> repo.observeDepartments().map { l -> l.map { it.id to (it.name + (it.divisionName?.let { d -> " · $d" } ?: "")) } }
        MasterType.DIVISION -> repo.observeDivisions().map { l -> l.map { it.id to it.name } }
        else -> kotlinx.coroutines.flow.flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveAndDelete(row: MasterRow, targetId: String) {
        if (_editor.value?.saving == true) return
        _editor.update { it?.copy(saving = true, message = null) }
        viewModelScope.launch {
            when (val r = repo.moveUsersAndDelete(type, row.id, targetId)) {
                is AppResult.Success -> {
                    _editor.value = null
                    _messages.send("${r.value} aset dipindahkan, ${row.title} dihapus")
                }
                is AppResult.Failure -> _editor.update { it?.copy(saving = false, message = r.message) }
            }
        }
    }

    fun delete(row: MasterRow) {
        if (_editor.value?.saving == true) return
        _editor.update { it?.copy(saving = true, message = null) }
        viewModelScope.launch {
            when (val r = repo.delete(type, row.id)) {
                is AppResult.Success -> {
                    _editor.value = null
                    _messages.send("${row.title} dihapus")
                }
                // Alasan penolakan (mis. masih dipakai aset) ditampilkan di panel agar terlihat.
                is AppResult.Failure -> _editor.update { it?.copy(saving = false, message = r.message, blockedByUsage = canMove) }
            }
        }
    }

    private fun rowsFor(includeInactive: Boolean): Flow<List<MasterRow>> = when (type) {
        MasterType.CATEGORY -> combine(repo.observeCategories(includeInactive), defaultResidual) { list, defResidual ->
            list.map { c ->
                val residual = c.residualPercent ?: defResidual
                MasterRow(
                    id = c.id,
                    title = c.name,
                    subtitle = "Umur ekonomis ${c.usefulLifeYears} th · Residu ${Fmt.percent(residual)}" +
                        if (c.residualPercent == null) " (default)" else "",
                    badge = c.codePrefix,
                    isActive = c.isActive,
                    form = MasterForm(
                        id = c.id, name = c.name, codePrefix = c.codePrefix,
                        usefulLifeYears = c.usefulLifeYears.toString(),
                        residualPercent = c.residualPercent?.let { Fmt.qty(it) } ?: "",
                    ),
                )
            }
        }
        MasterType.LOCATION -> repo.observeLocations(includeInactive).map { list ->
            list.map { MasterRow(it.id, it.name, it.address, null, it.isActive, MasterForm(id = it.id, name = it.name, address = it.address.orEmpty())) }
        }
        MasterType.DIVISION -> combine(repo.observeDivisions(includeInactive), repo.observeDepartments(includeInactive = false)) { divs, deps ->
            val count = deps.groupingBy { it.divisionId }.eachCount()
            divs.map { MasterRow(it.id, it.name, "${count[it.id] ?: 0} departemen", null, it.isActive, MasterForm(id = it.id, name = it.name)) }
        }
        MasterType.DEPARTMENT -> repo.observeDepartments(includeInactive).map { list ->
            list.map {
                MasterRow(
                    it.id, it.name, it.divisionName?.let { d -> "Divisi $d" } ?: "Belum ada divisi — ketuk untuk memilih", null, it.isActive,
                    MasterForm(id = it.id, name = it.name, divisionId = it.divisionId),
                )
            }
        }
        MasterType.VENDOR -> repo.observeVendors(includeInactive).map { list ->
            list.map { v ->
                MasterRow(
                    v.id, v.name, listOfNotNull(v.phone, v.email).joinToString(" · ").ifEmpty { null }, null, v.isActive,
                    MasterForm(id = v.id, name = v.name, phone = v.phone.orEmpty(), email = v.email.orEmpty(), address = v.address.orEmpty()),
                )
            }
        }
        MasterType.INSURANCE_TYPE -> repo.observeInsuranceTypes(includeInactive).map { list ->
            list.map { MasterRow(it.id, it.name, null, null, it.isActive, MasterForm(id = it.id, name = it.name)) }
        }
        MasterType.INSURER -> repo.observeInsurers(includeInactive).map { list ->
            list.map { v ->
                MasterRow(
                    v.id, v.name, listOfNotNull(v.phone, v.email).joinToString(" · ").ifEmpty { null }, null, v.isActive,
                    MasterForm(id = v.id, name = v.name, phone = v.phone.orEmpty(), email = v.email.orEmpty(), address = v.address.orEmpty()),
                )
            }
        }
        MasterType.UNIT -> repo.observeUnits(includeInactive).map { list ->
            list.map { MasterRow(it.id, it.name, null, null, it.isActive, MasterForm(id = it.id, name = it.name)) }
        }
    }
}
