package id.perusahaan.asetga

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import id.perusahaan.asetga.data.file.CrashReporter
import id.perusahaan.asetga.ui.feature.auth.LockScreen
import id.perusahaan.asetga.ui.feature.auth.LoginScreen
import id.perusahaan.asetga.ui.feature.auth.SetupAdminScreen
import id.perusahaan.asetga.ui.navigation.MainScaffold
import id.perusahaan.asetga.ui.theme.AsetGaTheme

/** FragmentActivity (bukan ComponentActivity) karena BiometricPrompt membutuhkannya. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

    // Saat aplikasi tidak di layar depan, isi layar disembunyikan dari pratinjau "aplikasi terbaru".
    // Tangkapan layar saat aplikasi dipakai tetap berfungsi.
    override fun onPause() {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val root by vm.rootState.collectAsStateWithLifecycle()
            AsetGaTheme(theme) {
                var crashReport by remember { mutableStateOf(CrashReporter.pendingReport(this@MainActivity)) }
                crashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Aplikasi sempat tertutup karena error") },
                        text = { Text("Laporan berisi rincian teknis error (versi aplikasi, perangkat, jejak error). Tidak dikirim otomatis; Anda memilih ke mana membagikannya.") },
                        confirmButton = {
                            TextButton(onClick = {
                                CrashReporter.share(this@MainActivity, report)
                                CrashReporter.clear(this@MainActivity)
                                crashReport = null
                            }) { Text("Bagikan laporan") }
                        },
                        dismissButton = {
                            TextButton(onClick = { CrashReporter.clear(this@MainActivity); crashReport = null }) { Text("Abaikan") }
                        },
                    )
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val s = root) {
                        RootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        RootState.NeedsSetup -> SetupAdminScreen()
                        RootState.LoggedOut -> LoginScreen()
                        is RootState.Locked -> LockScreen(s.user)
                        is RootState.Active -> MainScaffold(s.user)
                    }
                }
            }
        }
    }
}
