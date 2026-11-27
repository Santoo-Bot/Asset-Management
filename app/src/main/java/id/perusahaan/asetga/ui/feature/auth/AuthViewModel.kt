package id.perusahaan.asetga.ui.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.repository.LoginThrottle
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_CONFIRM
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = false,
    val message: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val failedAttempts: Int = 0,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val users: UserRepository,
    private val settings: SettingsRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun setupAdmin(fullName: String, username: String, password: String, confirm: String) {
        if (password != confirm) {
            _state.update { it.copy(fieldErrors = mapOf(FIELD_CONFIRM to "Konfirmasi kata sandi tidak sama")) }
            return
        }
        launchOp {
            when (val r = users.createUser(username, fullName, Role.ADMIN, password)) {
                is AppResult.Success -> loginAs(r.value)
                is AppResult.Failure -> fail(r)
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) {
            _state.update { it.copy(message = "Isi username dan kata sandi") }
            return
        }
        launchOp {
            when (val r = users.authenticate(username, password)) {
                is AppResult.Success -> loginAs(r.value)
                is AppResult.Failure -> fail(r, countAttempt = true)
            }
        }
    }

    /** Buka kunci dengan kata sandi milik pengguna yang terkunci. */
    fun unlockWithPassword(user: User, password: String) = login(user.username, password)

    fun unlockWithPin(user: User, pin: String) {
        launchOp {
            users.lockedSeconds(user.id).takeIf { it > 0 }?.let { wait ->
                _state.update { it.copy(message = LoginThrottle.message(wait)) }
                return@launchOp
            }
            if (users.verifyPin(user.id, pin)) {
                _state.value = AuthUiState()
                session.unlock()
            } else {
                _state.update { it.copy(message = "PIN salah", failedAttempts = it.failedAttempts + 1) }
            }
        }
    }

    fun unlockWithDevice() {
        _state.value = AuthUiState()
        session.unlock()
    }

    fun switchUser() {
        viewModelScope.launch {
            settings.setLastUserId(null)
            session.logout()
        }
    }

    private suspend fun loginAs(user: User) {
        settings.setLastUserId(user.id)
        _state.value = AuthUiState()
        session.login(user)
    }

    private fun fail(r: AppResult.Failure, countAttempt: Boolean = false) {
        _state.update {
            it.copy(
                message = r.message,
                fieldErrors = r.fieldErrors,
                failedAttempts = if (countAttempt) it.failedAttempts + 1 else it.failedAttempts,
            )
        }
    }

    private fun launchOp(block: suspend () -> Unit) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, message = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
