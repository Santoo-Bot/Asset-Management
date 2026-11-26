package id.perusahaan.asetga.domain.session

import id.perusahaan.asetga.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    /** Belum dipulihkan saat aplikasi baru dibuka. */
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    data class Locked(val user: User) : SessionState
    data class Active(val user: User) : SessionState
}

/**
 * Menyimpan pengguna yang sedang masuk (di memori) dan status kunci aplikasi.
 * Aplikasi otomatis terkunci bila berada di latar belakang lebih lama dari [lockTimeoutMillis].
 */
@Singleton
class SessionManager @Inject constructor(private val clock: Clock) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile var lockEnabled: Boolean = true
    var lockTimeoutMillis: Long = DEFAULT_LOCK_TIMEOUT_MS
    private var backgroundedAt: Long? = null

    /** Pengguna yang tercatat sebagai pelaku perubahan (Active atau Locked). */
    val currentUser: User?
        get() = when (val s = _state.value) {
            is SessionState.Active -> s.user
            is SessionState.Locked -> s.user
            else -> null
        }

    /** Dipanggil sekali saat aplikasi dibuka berdasarkan pengguna terakhir yang tersimpan. */
    fun restore(lastUser: User?, lockEnabled: Boolean) {
        this.lockEnabled = lockEnabled
        if (_state.value != SessionState.Unknown) return
        _state.value = when {
            lastUser == null || !lastUser.isActive -> SessionState.LoggedOut
            lockEnabled -> SessionState.Locked(lastUser)
            else -> SessionState.Active(lastUser)
        }
    }

    fun login(user: User) {
        backgroundedAt = null
        _state.value = SessionState.Active(user)
    }

    fun unlock() {
        _state.update { s -> if (s is SessionState.Locked) SessionState.Active(s.user) else s }
    }

    fun lockNow() {
        _state.update { s -> if (s is SessionState.Active && lockEnabled) SessionState.Locked(s.user) else s }
    }

    fun logout() {
        backgroundedAt = null
        _state.value = SessionState.LoggedOut
    }

    /** Perbarui data pengguna aktif (mis. nama/peran diubah Admin). */
    fun refreshUser(user: User) {
        _state.update { s ->
            when {
                s is SessionState.Active && s.user.id == user.id ->
                    if (user.isActive) SessionState.Active(user) else SessionState.LoggedOut
                s is SessionState.Locked && s.user.id == user.id ->
                    if (user.isActive) SessionState.Locked(user) else SessionState.LoggedOut
                else -> s
            }
        }
    }

    fun onAppBackgrounded() {
        backgroundedAt = clock.millis()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (clock.millis() - since >= lockTimeoutMillis) lockNow()
    }

    companion object {
        const val DEFAULT_LOCK_TIMEOUT_MS = 60_000L
    }
}
