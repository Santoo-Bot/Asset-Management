package id.perusahaan.asetga.ui.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_CONFIRM
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_FULL_NAME
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_PASSWORD
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_USERNAME
import id.perusahaan.asetga.ui.components.BottomActionBar
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.PasswordField
import id.perusahaan.asetga.ui.theme.AppTheme
import id.perusahaan.asetga.ui.theme.BrandColors
import id.perusahaan.asetga.ui.theme.LightStatusBarIcons

@Composable
private fun AuthLayout(
    title: String,
    subtitle: String,
    actionText: String,
    loading: Boolean,
    onAction: () -> Unit,
    message: String?,
    content: @Composable () -> Unit,
) {
    Scaffold(
        // Header biru menempati area status bar; inset diatur sendiri oleh BrandHeader & BottomActionBar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { BottomActionBar(actionText, onAction, loading = loading, modifier = Modifier.imePadding()) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            BrandHeader(title = title, subtitle = subtitle)
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
                if (message != null) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Panel biru bergradasi di bagian atas layar masuk. */
@Composable
private fun BrandHeader(title: String, subtitle: String) {
    if (!AppTheme.isDark) LightStatusBarIcons()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(BrandColors.BcaNavy, BrandColors.BcaBlue)))
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(56.dp).background(Color.White, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = BrandColors.BcaBlue, modifier = Modifier.size(32.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun SetupAdminScreen(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var fullName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val submit = { vm.setupAdmin(fullName, username, password, confirm) }

    AuthLayout(
        title = "Selamat datang",
        subtitle = "Buat akun Admin GA pertama. Akun ini dipakai untuk mengelola pengguna dan master data.",
        actionText = "Buat akun Admin",
        loading = state.loading,
        onAction = submit,
        message = state.message.takeIf { state.fieldErrors.isEmpty() },
    ) {
        FormTextField(fullName, { fullName = it }, "Nama lengkap", required = true, error = state.fieldErrors[FIELD_FULL_NAME])
        FormTextField(
            username, { username = it.lowercase().trim() }, "Username", required = true,
            error = state.fieldErrors[FIELD_USERNAME], supporting = "Huruf kecil, angka, titik, atau garis bawah",
        )
        PasswordField(password, { password = it }, "Kata sandi", error = state.fieldErrors[FIELD_PASSWORD], supporting = "Minimal 8 karakter, berisi huruf dan angka")
        PasswordField(confirm, { confirm = it }, "Ulangi kata sandi", error = state.fieldErrors[FIELD_CONFIRM], imeAction = ImeAction.Done, onImeAction = submit)
    }
}

@Composable
fun LoginScreen(vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val submit = { vm.login(username, password) }

    AuthLayout(
        title = "Masuk",
        subtitle = "Sistem Manajemen Aset & Inventaris GA",
        actionText = "Masuk",
        loading = state.loading,
        onAction = submit,
        message = state.message,
    ) {
        FormTextField(username, { username = it.lowercase().trim() }, "Username")
        PasswordField(password, { password = it }, "Kata sandi", imeAction = ImeAction.Done, onImeAction = submit)
        Text(
            "Lupa kata sandi? Minta Admin GA mengatur ulang dari menu Pengguna.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LockScreen(user: User, vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()
    val deviceUnlockAvailable = remember(activity) { activity?.canUseDeviceUnlock() == true }
    var usePassword by rememberSaveable { mutableStateOf(!deviceUnlockAvailable && !user.hasPin) }
    var secret by remember { mutableStateOf("") }
    var deviceError by remember { mutableStateOf<String?>(null) }

    val promptDevice = {
        activity?.showUnlockPrompt(
            subtitle = "Masuk sebagai ${user.fullName}",
            onSuccess = vm::unlockWithDevice,
            onError = { deviceError = it },
        )
        Unit
    }
    LaunchedEffect(Unit) { if (deviceUnlockAvailable) promptDevice() }

    val submit = {
        if (usePassword) vm.unlockWithPassword(user, secret) else vm.unlockWithPin(user, secret)
        secret = ""
    }
    val showSecretField = usePassword || user.hasPin

    Scaffold(
        bottomBar = {
            if (showSecretField) {
                BottomActionBar("Buka", submit, enabled = secret.isNotEmpty(), loading = state.loading, modifier = Modifier.imePadding())
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.fullName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text("Halo, ${user.fullName}", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Aplikasi terkunci", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            if (deviceUnlockAvailable) {
                OutlinedButton(onClick = promptDevice, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Buka dengan biometrik / kunci layar")
                }
            }
            if (showSecretField) {
                if (usePassword) {
                    PasswordField(secret, { secret = it }, "Kata sandi", imeAction = ImeAction.Done, onImeAction = submit)
                } else {
                    PasswordField(
                        secret, { if (it.length <= 8 && it.all(Char::isDigit)) secret = it }, "PIN aplikasi",
                        keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, onImeAction = submit,
                    )
                }
            }
            (state.message ?: deviceError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (user.hasPin || deviceUnlockAvailable) {
                TextButton(onClick = { usePassword = !usePassword; secret = "" }) {
                    Text(if (usePassword) (if (user.hasPin) "Gunakan PIN" else "Sembunyikan kata sandi") else "Masuk dengan kata sandi")
                }
            }
            TextButton(onClick = vm::switchUser) { Text("Ganti pengguna") }
        }
    }
}
