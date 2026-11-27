package id.perusahaan.asetga.ui.feature.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.data.file.BackupManager
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.security.BackupCrypto
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.PasswordField
import id.perusahaan.asetga.ui.components.REAUTH_LABEL
import id.perusahaan.asetga.ui.components.ReauthViewModel
import id.perusahaan.asetga.ui.components.SectionHeader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.exitProcess

sealed interface BackupEvent {
    data class Message(val text: String) : BackupEvent
    data object RestoreDone : BackupEvent
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
    session: SessionManager,
) : ViewModel() {
    val canRestore = session.currentUser?.role?.canManageMaster == true
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _events = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = _events.receiveAsFlow()

    fun suggestedName() = manager.suggestedFileName()

    fun backup(uri: Uri, passphrase: String) = launchOp {
        when (val r = manager.backup(uri, passphrase)) {
            is AppResult.Success -> _events.send(BackupEvent.Message("Backup tersimpan (${r.value / 1024} KB). Simpan passphrase di tempat aman."))
            is AppResult.Failure -> _events.send(BackupEvent.Message(r.message))
        }
    }

    fun restore(uri: Uri, passphrase: String) = launchOp {
        when (val r = manager.restore(uri, passphrase)) {
            is AppResult.Success -> _events.send(BackupEvent.RestoreDone)
            is AppResult.Failure -> _events.send(BackupEvent.Message(r.message))
        }
    }

    private fun launchOp(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() } finally { _busy.value = false }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Mulai ulang proses agar database hasil restore dibuka dari awal. */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.findActivity()?.finishAffinity()
    intent?.let(context::startActivity)
    exitProcess(0)
}

@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel(), reauth: ReauthViewModel = hiltViewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePass by remember { mutableStateOf("") }
    var accountPass by remember { mutableStateOf("") }
    var accountPassError by remember { mutableStateOf<String?>(null) }
    val routine = rememberCoroutineScope()
    var restoreDone by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        if (uri != null) vm.backup(uri, pass)
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri; restorePass = ""; accountPass = ""; accountPassError = null }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is BackupEvent.Message -> snackbar.showSnackbar(e.text)
                BackupEvent.RestoreDone -> restoreDone = true
            }
        }
    }

    val passError = when {
        pass.isNotEmpty() && pass.length < BackupCrypto.MIN_PASSPHRASE -> "Minimal ${BackupCrypto.MIN_PASSPHRASE} karakter"
        confirm.isNotEmpty() && confirm != pass -> "Passphrase tidak sama"
        else -> null
    }

    Scaffold(topBar = { AppTopBar("Backup & restore", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            SectionHeader("Buat backup")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Database dan semua foto dienkripsi (AES-256) dengan passphrase Anda, lalu disimpan ke lokasi pilihan: " +
                        "penyimpanan HP, kartu SD, atau Google Drive (pilih Drive di jendela simpan).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(pass, { pass = it }, "Passphrase backup")
                PasswordField(confirm, { confirm = it }, "Ulangi passphrase", error = passError)
                Text(
                    "Passphrase tidak dapat dipulihkan. Tanpa passphrase, backup tidak bisa dibuka.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { createDoc.launch(vm.suggestedName()) },
                    enabled = !busy && pass.length >= BackupCrypto.MIN_PASSPHRASE && pass == confirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Simpan backup…") }
            }

            if (vm.canRestore) {
                SectionHeader("Restore dari backup")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Restore mengganti SELURUH data di HP ini dengan isi file backup. Buat backup terbaru dulu bila perlu.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { openDoc.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Pilih file backup (.gabak)…")
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!busy) restoreUri = null },
            title = { Text("Restore data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Semua data saat ini akan diganti. Aplikasi akan dimulai ulang setelah restore.")
                    PasswordField(restorePass, { restorePass = it }, "Passphrase backup")
                    PasswordField(accountPass, { accountPass = it; accountPassError = null }, REAUTH_LABEL, error = accountPassError)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        routine.launch {
                            accountPassError = reauth.verify(accountPass)
                            if (accountPassError == null) vm.restore(uri, restorePass)
                        }
                    },
                    enabled = !busy && restorePass.isNotEmpty() && accountPass.isNotEmpty(),
                ) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }, enabled = !busy) { Text("Batal") } },
        )
    }

    if (restoreDone) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restore berhasil") },
            text = { Text("Aplikasi akan dimulai ulang untuk memuat data dari backup. Masuk kembali dengan akun yang ada di backup.") },
            confirmButton = { TextButton(onClick = { restartApp(context) }) { Text("Mulai ulang") } },
        )
    }
}
