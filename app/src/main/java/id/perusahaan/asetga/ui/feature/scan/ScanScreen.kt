package id.perusahaan.asetga.ui.feature.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BarcodeScannerView
import id.perusahaan.asetga.ui.components.CameraPermissionGate
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.ManualCodeButton

@Composable
fun ScanScreen(
    onOpenAsset: (String) -> Unit,
    onOpenOpname: (() -> Unit)? = null,
    /** Diisi bila pengguna boleh menambah aset: kode yang belum terdaftar bisa langsung didaftarkan. */
    onAddAsset: ((String) -> Unit)? = null,
    vm: ScanViewModel = hiltViewModel(),
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var manualOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is ScanEvent.OpenAsset -> onOpenAsset(e.id)
                is ScanEvent.NotFound -> {
                    val result = snackbar.showSnackbar(
                        "Kode \"${e.code}\" tidak terdaftar sebagai aset",
                        actionLabel = if (onAddAsset != null) "Daftarkan" else null,
                    )
                    if (result == SnackbarResult.ActionPerformed) onAddAsset?.invoke(e.code)
                }
            }
        }
    }

    Scaffold(topBar = { AppTopBar("Scan label aset") }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CameraPermissionGate(
                modifier = Modifier.fillMaxWidth().weight(1f),
                fallback = { ManualCodeButton(onClick = { manualOpen = true }) },
            ) {
                BarcodeScannerView(onCode = vm::onCode, paused = busy || manualOpen, modifier = Modifier.fillMaxWidth().weight(1f))
                Text(
                    "Arahkan kamera ke QR/barcode pada label aset",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ManualCodeButton(onClick = { manualOpen = true }, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            }
            if (onOpenOpname != null) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenOpname),
                    leadingContent = { Icon(Icons.Outlined.TaskAlt, contentDescription = null) },
                    headlineContent = { Text("Stock opname / audit aset") },
                    supportingContent = { Text("Scan banyak aset per lokasi dan lihat selisihnya") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }
    }

    if (manualOpen) {
        ManualCodeDialog(onDismiss = { manualOpen = false }, onSubmit = { manualOpen = false; vm.onCode(it) })
    }
}

@Composable
fun ManualCodeDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit, title: String = "Masukkan kode") {
    var code by remember { mutableStateOf("") }
    val submit = { if (code.isNotBlank()) onSubmit(code.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FormTextField(code, { code = it.uppercase() }, "Kode", imeAction = ImeAction.Done, onImeAction = submit)
        },
        confirmButton = { TextButton(onClick = submit, enabled = code.isNotBlank()) { Text("Cari") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        modifier = Modifier,
    )
}
