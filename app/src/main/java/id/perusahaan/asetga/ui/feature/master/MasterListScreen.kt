package id.perusahaan.asetga.ui.feature.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.domain.model.Division
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.domain.validation.MasterValidator
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.SelectField
import id.perusahaan.asetga.ui.components.StatusChip

@Composable
fun MasterListScreen(
    type: MasterType,
    onBack: () -> Unit,
    vm: MasterListViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val divisions by vm.divisions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { AppTopBar(type.label, onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.canEdit) {
                ExtendedFloatingActionButton(
                    onClick = vm::openNew,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Tambah ${type.singular}") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Cari ${type.singular}") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (!state.loading && state.rows.isEmpty()) {
                EmptyState(
                    icon = type.icon(),
                    title = if (state.query.isBlank()) "Belum ada ${type.singular}" else "Tidak ditemukan",
                    message = if (vm.canEdit && state.query.isBlank()) "Ketuk Tambah untuk membuat data baru." else null,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.rows, key = { it.id }) { row ->
                        ListItem(
                            modifier = Modifier.clickable(enabled = vm.canEdit) { vm.openEdit(row) },
                            leadingContent = row.badge?.let { b ->
                                { StatusChip(b, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                            },
                            headlineContent = {
                                Text(
                                    row.title,
                                    textDecoration = if (row.isActive) null else TextDecoration.LineThrough,
                                    color = if (row.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                )
                            },
                            supportingContent = row.subtitle?.let { s -> { Text(s) } },
                            trailingContent = if (!row.isActive) ({ Text("Nonaktif", style = MaterialTheme.typography.labelSmall) }) else null,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    editor?.let { ed ->
        MasterEditorSheet(
            type = type,
            state = ed,
            activeRow = state.rows.firstOrNull { it.id == ed.form.id },
            divisions = divisions,
            onChange = vm::updateForm,
            onSave = vm::save,
            onToggleActive = vm::toggleActive,
            onDelete = vm::delete,
            onDismiss = vm::closeEditor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MasterEditorSheet(
    type: MasterType,
    state: MasterEditorState,
    activeRow: MasterRow?,
    divisions: List<Division>,
    onChange: ((MasterForm) -> MasterForm) -> Unit,
    onSave: () -> Unit,
    onToggleActive: (MasterRow) -> Unit,
    onDelete: (MasterRow) -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val f = state.form
    val e = state.errors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (state.isNew) "Tambah ${type.singular}" else "Ubah ${type.singular}",
                style = MaterialTheme.typography.titleLarge,
            )
            FormTextField(f.name, { v -> onChange { it.copy(name = v) } }, "Nama", required = true, error = e[MasterValidator.FIELD_NAME])
            when (type) {
                MasterType.CATEGORY -> {
                    FormTextField(
                        f.codePrefix, { v -> onChange { it.copy(codePrefix = v.uppercase().filter(Char::isLetter).take(5)) } },
                        "Kode prefix", required = true, error = e[MasterValidator.FIELD_PREFIX],
                        supporting = "Dipakai untuk kode aset, mis. IT → IT-2026-001",
                    )
                    FormTextField(
                        f.usefulLifeYears, { v -> onChange { it.copy(usefulLifeYears = v.filter(Char::isDigit).take(2)) } },
                        "Umur ekonomis (tahun)", required = true, error = e[MasterValidator.FIELD_LIFE], keyboardType = KeyboardType.Number,
                    )
                    FormTextField(
                        f.residualPercent, { v -> onChange { it.copy(residualPercent = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6)) } },
                        "Nilai residu (%)", error = e[MasterValidator.FIELD_RESIDUAL], keyboardType = KeyboardType.Decimal,
                        supporting = "Kosongkan untuk memakai nilai default di Pengaturan",
                    )
                }
                MasterType.LOCATION -> FormTextField(f.address, { v -> onChange { it.copy(address = v) } }, "Alamat / keterangan", singleLine = false, minLines = 2)
                MasterType.VENDOR -> {
                    FormTextField(f.phone, { v -> onChange { it.copy(phone = v) } }, "Telepon", error = e[MasterValidator.FIELD_PHONE], keyboardType = KeyboardType.Phone)
                    FormTextField(f.email, { v -> onChange { it.copy(email = v.trim()) } }, "Email", error = e[MasterValidator.FIELD_EMAIL], keyboardType = KeyboardType.Email)
                    FormTextField(f.address, { v -> onChange { it.copy(address = v) } }, "Alamat", singleLine = false, minLines = 2)
                }
                MasterType.DEPARTMENT -> SelectField(
                    label = "Divisi induk", options = divisions,
                    selected = divisions.firstOrNull { it.id == f.divisionId }, itemLabel = { it.name },
                    onSelect = { d -> onChange { it.copy(divisionId = d?.id) } }, required = true,
                    error = e[MasterValidator.FIELD_DIVISION],
                    supporting = if (divisions.isEmpty()) "Belum ada divisi. Tambahkan dulu di Master data → Divisi." else null,
                )
                MasterType.DIVISION, MasterType.UNIT -> Unit
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (state.saving) "Menyimpan…" else "Simpan")
            }
            if (activeRow != null) {
                if (!activeRow.isActive) {
                    OutlinedButton(onClick = { onToggleActive(activeRow) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Aktifkan kembali")
                    }
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Hapus ${type.singular}") }
                Text(
                    "Hanya data yang belum dipakai aset, stok, atau riwayat yang bisa dihapus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete && activeRow != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus ${activeRow.title}?") },
            text = { Text("${type.label} ini akan dihapus permanen dari daftar. Penghapusan tercatat di audit log.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(activeRow) }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
