package id.perusahaan.asetga.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.ThemeMode
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.repository.AppSettings
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.session.SessionState
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val users: UserRepository,
    private val session: SessionManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val user: StateFlow<User?> = session.state
        .map { (it as? SessionState.Active)?.user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), session.currentUser)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }

    fun setAppLock(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setAppLockEnabled(enabled)
        _messages.send(if (enabled) "Kunci aplikasi aktif" else "Kunci aplikasi dimatikan")
    }

    fun setDefaultResidual(text: String) = viewModelScope.launch {
        if (session.currentUser?.role?.canManageMaster != true) return@launch
        val value = Fmt.parseDecimal(text)
        if (value == null) {
            _messages.send("Isi angka, mis. 0 atau 10")
            return@launch
        }
        when (val r = settingsRepo.setDefaultResidualPercent(value)) {
            is AppResult.Success -> _messages.send("Nilai residu default disimpan")
            is AppResult.Failure -> _messages.send(r.message)
        }
    }

    fun setCompanyName(name: String) = viewModelScope.launch {
        if (session.currentUser?.role?.canManageMaster != true) return@launch
        settingsRepo.setCompanyName(name)
        _messages.send("Nama perusahaan disimpan")
    }

    /** Mengembalikan pesan error, atau null bila sukses. */
    suspend fun setPin(pin: String?): String? {
        val id = session.currentUser?.id ?: return "Sesi berakhir"
        return when (val r = users.setPin(id, pin)) {
            is AppResult.Success -> {
                _messages.send(if (pin == null) "PIN dihapus" else "PIN disimpan")
                null
            }
            is AppResult.Failure -> r.message
        }
    }

    suspend fun changePassword(current: String, new: String, confirm: String): String? {
        val u = session.currentUser ?: return "Sesi berakhir"
        if (new != confirm) return "Konfirmasi kata sandi tidak sama"
        if (users.authenticate(u.username, current) !is AppResult.Success) return "Kata sandi saat ini salah"
        return when (val r = users.changePassword(u.id, new)) {
            is AppResult.Success -> {
                _messages.send("Kata sandi diubah")
                null
            }
            is AppResult.Failure -> r.message
        }
    }

    fun logout() = viewModelScope.launch {
        settingsRepo.setLastUserId(null)
        session.logout()
    }
}
