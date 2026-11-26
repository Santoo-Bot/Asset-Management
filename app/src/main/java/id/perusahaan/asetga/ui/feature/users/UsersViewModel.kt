package id.perusahaan.asetga.ui.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.session.SessionManager
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
import javax.inject.Inject

data class UserEditorState(
    val existing: User? = null,
    val username: String = "",
    val fullName: String = "",
    val role: Role = Role.STAFF,
    val isActive: Boolean = true,
    val password: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val repo: UserRepository,
    session: SessionManager,
) : ViewModel() {

    val currentUserId: String? = session.currentUser?.id

    val users: StateFlow<List<User>> = repo.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow<UserEditorState?>(null)
    val editor: StateFlow<UserEditorState?> = _editor.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun openNew() { _editor.value = UserEditorState() }

    fun openEdit(user: User) {
        _editor.value = UserEditorState(existing = user, username = user.username, fullName = user.fullName, role = user.role, isActive = user.isActive)
    }

    fun close() { _editor.value = null }

    fun update(transform: (UserEditorState) -> UserEditorState) = _editor.update { it?.let(transform) }

    fun delete() {
        val existing = _editor.value?.existing ?: return
        viewModelScope.launch {
            when (val r = repo.deleteUser(existing.id)) {
                is AppResult.Success -> {
                    _editor.value = null
                    _messages.send("Pengguna ${existing.fullName} dihapus")
                }
                is AppResult.Failure -> _editor.update { it?.copy(message = r.message) }
            }
        }
    }

    fun save() {
        val s = _editor.value ?: return
        if (s.saving) return
        _editor.value = s.copy(saving = true, errors = emptyMap(), message = null)
        viewModelScope.launch {
            val result = if (s.existing == null) {
                repo.createUser(s.username, s.fullName, s.role, s.password)
            } else {
                val r = repo.updateUser(s.existing.id, s.fullName, s.role, s.isActive)
                if (r is AppResult.Success && s.password.isNotEmpty()) {
                    when (val pr = repo.changePassword(s.existing.id, s.password)) {
                        is AppResult.Failure -> pr
                        is AppResult.Success -> r
                    }
                } else {
                    r
                }
            }
            when (result) {
                is AppResult.Success -> {
                    _editor.value = null
                    _messages.send(if (s.existing == null) "Pengguna ditambahkan" else "Perubahan disimpan")
                }
                is AppResult.Failure -> {
                    _editor.update { it?.copy(saving = false, errors = result.fieldErrors, message = result.message.takeIf { result.fieldErrors.isEmpty() }) }
                }
            }
        }
    }
}
