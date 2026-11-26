package id.perusahaan.asetga.ui.feature.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_FULL_NAME
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_PASSWORD
import id.perusahaan.asetga.domain.security.CredentialRules.FIELD_USERNAME
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.ErrorBanner
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.PasswordField
import id.perusahaan.asetga.ui.components.StatusChip
import id.perusahaan.asetga.ui.theme.AppTheme

@Composable
fun UsersScreen(onBack: () -> Unit, vm: UsersViewModel = hiltViewModel()) {
    val users by vm.users.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { AppTopBar("Pengguna & peran", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::openNew,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Tambah pengguna") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(users, key = { it.id }) { u ->
                ListItem(
                    modifier = Modifier.clickable { vm.openEdit(u) },
                    headlineContent = { Text(u.fullName + if (u.id == vm.currentUserId) " (Anda)" else "") },
                    supportingContent = { Text("@${u.username}") },
                    trailingContent = {
                        val status = AppTheme.status
                        if (u.isActive) {
                            StatusChip(u.role.label, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            StatusChip("Nonaktif", status.dangerContainer, status.onDangerContainer)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    editor?.let { UserEditorSheet(it, isSelf = it.existing?.id == vm.currentUserId, onChange = vm::update, onSave = vm::save, onDelete = vm::delete, onDismiss = vm::close) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserEditorSheet(
    s: UserEditorState,
    isSelf: Boolean,
    onChange: ((UserEditorState) -> UserEditorState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (s.existing == null) "Tambah pengguna" else "Ubah pengguna", style = MaterialTheme.typography.titleLarge)
            FormTextField(
                s.username, { v -> onChange { it.copy(username = v.lowercase().trim()) } }, "Username",
                required = true, enabled = s.existing == null, error = s.errors[FIELD_USERNAME],
            )
            FormTextField(s.fullName, { v -> onChange { it.copy(fullName = v) } }, "Nama lengkap", required = true, error = s.errors[FIELD_FULL_NAME])
            Text("Peran", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Role.entries.forEach { r ->
                    FilterChip(selected = s.role == r, onClick = { onChange { it.copy(role = r) } }, label = { Text(r.label) })
                }
            }
            Text(roleDescription(s.role), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PasswordField(
                s.password, { v -> onChange { it.copy(password = v) } },
                if (s.existing == null) "Kata sandi awal" else "Atur ulang kata sandi (opsional)",
                error = s.errors[FIELD_PASSWORD], supporting = "Minimal 8 karakter, berisi huruf dan angka",
            )
            s.message?.let { ErrorBanner(it) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSave, enabled = !s.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (s.saving) "Menyimpan…" else "Simpan")
            }
            if (s.existing != null && !isSelf) {
                if (confirmDelete) {
                    ErrorBanner("Hapus akun ${s.existing.fullName} secara permanen? Pengguna ini tidak bisa masuk lagi.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDelete) { Text("Ya, hapus permanen", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
                    }
                } else {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Hapus pengguna", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun roleDescription(role: Role) = when (role) {
    Role.ADMIN -> "Akses penuh, termasuk master data, pengguna, dan pengaturan."
    Role.STAFF -> "Tambah/ubah aset, mutasi, servis, dan stok."
    Role.VIEWER -> "Hanya dapat melihat data."
}
