package id.perusahaan.asetga.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.data.file.ResetScope
import id.perusahaan.asetga.domain.model.ThemeMode
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.OptionSheet
import id.perusahaan.asetga.ui.components.PasswordField
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.launch

private enum class SettingsDialog { PIN, PASSWORD, RESIDUAL, COMPANY, HOUR, RESET, LOGOUT }

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { AppTopBar("Pengaturan", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader("Tampilan")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                headlineContent = { Text("Tema") },
                supportingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(selected = settings.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(m.label) })
                        }
                    }
                },
            )

            SectionHeader("Notifikasi")
            ListItem(
                modifier = Modifier.clickable { vm.setNotifications(!settings.notificationsEnabled) },
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                headlineContent = { Text("Pengingat harian") },
                supportingContent = { Text("Garansi segera habis, servis jatuh tempo/terlambat, stok di bawah minimum") },
                trailingContent = { Switch(checked = settings.notificationsEnabled, onCheckedChange = { vm.setNotifications(it) }) },
            )
            if (settings.notificationsEnabled) {
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.HOUR },
                    leadingContent = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                    headlineContent = { Text("Jam pengingat") },
                    supportingContent = { Text("Setiap hari pukul ${"%02d".format(settings.reminderHour)}:00") },
                )
            }

            SectionHeader("Keamanan")
            ListItem(
                modifier = Modifier.clickable { vm.setAppLock(!settings.appLockEnabled) },
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                headlineContent = { Text("Kunci aplikasi") },
                supportingContent = { Text("Minta biometrik/PIN saat dibuka atau setelah 1 menit di latar belakang") },
                trailingContent = { Switch(checked = settings.appLockEnabled, onCheckedChange = { vm.setAppLock(it) }) },
            )
            ListItem(
                modifier = Modifier.clickable { dialog = SettingsDialog.PIN },
                leadingContent = { Icon(Icons.Outlined.Pin, contentDescription = null) },
                headlineContent = { Text("PIN aplikasi") },
                supportingContent = { Text(if (user?.hasPin == true) "PIN sudah diatur · ketuk untuk mengubah" else "Belum diatur · alternatif biometrik") },
            )
            ListItem(
                modifier = Modifier.clickable { dialog = SettingsDialog.PASSWORD },
                leadingContent = { Icon(Icons.Outlined.Password, contentDescription = null) },
                headlineContent = { Text("Ubah kata sandi") },
            )

            if (user?.role?.canManageMaster == true) {
                SectionHeader("Label QR")
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.COMPANY },
                    leadingContent = { Icon(Icons.Outlined.Business, contentDescription = null) },
                    headlineContent = { Text("Nama perusahaan pada label") },
                    supportingContent = { Text(settings.companyName.ifBlank { "Belum diisi" }) },
                )
                SectionHeader("Penyusutan")
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.RESIDUAL },
                    leadingContent = { Icon(Icons.Outlined.Percent, contentDescription = null) },
                    headlineContent = { Text("Nilai residu default") },
                    supportingContent = { Text("${Fmt.percent(settings.defaultResidualPercent)} · dipakai bila kategori tidak mengatur residu sendiri") },
                )
            }

            if (user?.role?.canManageMaster == true) {
                SectionHeader("Data")
                ListItem(
                    modifier = Modifier.clickable { dialog = SettingsDialog.RESET },
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Hapus seluruh data", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Kosongkan data operasional, atau kembalikan aplikasi seperti baru dipasang") },
                )
            }

            SectionHeader("Akun")
            ListItem(
                modifier = Modifier.clickable { dialog = SettingsDialog.LOGOUT },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Keluar", color = MaterialTheme.colorScheme.error) },
                supportingContent = { user?.let { Text("Masuk sebagai ${it.fullName}") } },
            )
        }
    }

    when (dialog) {
        SettingsDialog.PIN -> PinDialog(hasPin = user?.hasPin == true, vm = vm, onDismiss = { dialog = null })
        SettingsDialog.PASSWORD -> PasswordDialog(vm = vm, onDismiss = { dialog = null })
        SettingsDialog.RESIDUAL -> ResidualDialog(
            initial = Fmt.qty(settings.defaultResidualPercent),
            onSave = { vm.setDefaultResidual(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.COMPANY -> TextInputDialog(
            title = "Nama perusahaan",
            initial = settings.companyName,
            label = "Mis. PT BCA Life",
            onSave = { vm.setCompanyName(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.HOUR -> OptionSheet(
            title = "Jam pengingat",
            options = (5..21).toList(),
            selected = settings.reminderHour,
            itemLabel = { "%02d:00".format(it) },
            onDismiss = { dialog = null },
            onSelect = { h -> if (h != null) vm.setReminderHour(h); dialog = null },
        )
        SettingsDialog.RESET -> ResetDialog(onConfirm = { scope -> dialog = null; vm.resetData(scope) }, onDismiss = { dialog = null })
        SettingsDialog.LOGOUT -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Keluar dari akun?") },
            text = { Text("Anda perlu memasukkan username dan kata sandi lagi untuk masuk.") },
            confirmButton = { TextButton(onClick = { dialog = null; vm.logout() }) { Text("Keluar") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } },
        )
        null -> Unit
    }
}

@Composable
private fun PinDialog(hasPin: Boolean, vm: SettingsViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Ubah PIN" else "Atur PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, "PIN baru (4–8 digit)", keyboardType = KeyboardType.NumberPassword)
                PasswordField(confirm, { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it }, "Ulangi PIN", keyboardType = KeyboardType.NumberPassword, error = error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin != confirm) {
                    error = "PIN tidak sama"
                } else {
                    scope.launch { error = vm.setPin(pin); if (error == null) onDismiss() }
                }
            }) { Text("Simpan") }
        },
        dismissButton = {
            Row {
                if (hasPin) TextButton(onClick = { scope.launch { if (vm.setPin(null) == null) onDismiss() } }) { Text("Hapus PIN") }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    )
}

@Composable
private fun PasswordDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah kata sandi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(current, { current = it }, "Kata sandi saat ini")
                PasswordField(new, { new = it }, "Kata sandi baru", supporting = "Minimal 8 karakter, huruf dan angka")
                PasswordField(confirm, { confirm = it }, "Ulangi kata sandi baru", error = error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    error = vm.changePassword(current, new, confirm)
                    busy = false
                    if (error == null) onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ResidualDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nilai residu default") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FormTextField(
                    text, { v -> text = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) }, "Residu (%)",
                    keyboardType = KeyboardType.Decimal,
                    supporting = "Persentase harga perolehan yang tersisa di akhir umur ekonomis",
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun TextInputDialog(title: String, initial: String, label: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { FormTextField(text, { text = it.take(60) }, label) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ResetDialog(onConfirm: (ResetScope) -> Unit, onDismiss: () -> Unit) {
    var scope by remember { mutableStateOf(ResetScope.OPERATIONAL) }
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus seluruh data?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResetScope.entries.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { scope = s }, verticalAlignment = Alignment.Top) {
                        RadioButton(selected = scope == s, onClick = { scope = s })
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(s.label, style = MaterialTheme.typography.bodyLarge)
                            Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Tidak bisa dibatalkan. Buat backup dulu di Lainnya → Backup & restore bila data masih diperlukan.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
                FormTextField(typed, { typed = it }, "Ketik HAPUS untuk konfirmasi")
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(scope) }, enabled = typed.trim().equals("HAPUS", ignoreCase = true)) {
                Text("Hapus", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
