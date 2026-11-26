package id.perusahaan.asetga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.ThemeMode
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.domain.session.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data object LoggedOut : RootState
    data class Locked(val user: User) : RootState
    data class Active(val user: User) : RootState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    users: UserRepository,
    private val settings: SettingsRepository,
    private val session: SessionManager,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val rootState: StateFlow<RootState> = combine(users.observeUserCount(), session.state) { count, s ->
        when {
            count == 0 -> RootState.NeedsSetup
            s is SessionState.Unknown -> RootState.Loading
            s is SessionState.LoggedOut -> RootState.LoggedOut
            s is SessionState.Locked -> RootState.Locked(s.user)
            s is SessionState.Active -> RootState.Active(s.user)
            else -> RootState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    init {
        viewModelScope.launch {
            val current = settings.current()
            val lastUser = current.lastUserId?.let { users.getUser(it) }
            session.restore(lastUser, current.appLockEnabled)
        }
        viewModelScope.launch {
            settings.settings.collect { session.lockEnabled = it.appLockEnabled }
        }
    }
}
