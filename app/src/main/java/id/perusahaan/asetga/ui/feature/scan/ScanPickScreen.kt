package id.perusahaan.asetga.ui.feature.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.BarcodeScannerView
import id.perusahaan.asetga.ui.components.CameraPermissionGate
import id.perusahaan.asetga.ui.components.ManualCodeButton

/** Scan satu kode lalu kembalikan ke pemanggil. */
@Composable
fun ScanPickScreen(title: String, onBack: () -> Unit, onCode: (String) -> Unit) {
    var done by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    val deliver: (String) -> Unit = { code -> if (!done) { done = true; onCode(code) } }
    Scaffold(topBar = { AppTopBar(title, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CameraPermissionGate(
                modifier = Modifier.fillMaxWidth().weight(1f),
                fallback = { ManualCodeButton(onClick = { manual = true }) },
            ) {
                BarcodeScannerView(onCode = deliver, paused = done || manual, modifier = Modifier.fillMaxWidth().weight(1f))
                Text(
                    "Arahkan kamera ke barcode/QR",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 8.dp),
                )
                ManualCodeButton(onClick = { manual = true }, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            }
        }
    }
    if (manual) ManualCodeDialog(onDismiss = { manual = false }, onSubmit = { manual = false; deliver(it) })
}
