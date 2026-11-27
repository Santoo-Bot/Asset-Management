package id.perusahaan.asetga.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.repository.UserRepository
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Konfirmasi ulang dengan kata sandi pengguna yang sedang masuk, untuk tindakan yang tidak bisa dibatalkan atau
 * menurunkan keamanan (hapus data, restore, hapus pengguna, ubah PIN, matikan kunci aplikasi).
 * Memakai alur login yang sama, jadi salah berulang ikut dibatasi (lihat LoginThrottle).
 */
@HiltViewModel
class ReauthViewModel @Inject constructor(
    private val users: UserRepository,
    private val session: SessionManager,
) : ViewModel() {
    /** Null bila kata sandi benar; selain itu pesan kesalahan untuk ditampilkan. */
    suspend fun verify(password: String): String? {
        val user = session.currentUser ?: return "Sesi berakhir"
        if (password.isEmpty()) return "Masukkan kata sandi Anda"
        return when (val r = users.authenticate(user.username, password)) {
            is AppResult.Success -> null
            is AppResult.Failure -> if (r.message.startsWith("Terlalu banyak")) r.message else "Kata sandi salah"
        }
    }
}

const val REAUTH_LABEL = "Kata sandi Anda (konfirmasi)"

/** Dialog sederhana: penjelasan + kolom kata sandi; [onConfirmed] dipanggil hanya bila kata sandi benar. */
@Composable
fun ReauthDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit,
    reauth: ReauthViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                PasswordField(password, { password = it; error = null }, REAUTH_LABEL, error = error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        val e = reauth.verify(password)
                        busy = false
                        if (e == null) onConfirmed() else error = e
                    }
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } },
    )
}
