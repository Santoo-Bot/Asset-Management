package id.perusahaan.asetga

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import id.perusahaan.asetga.ui.feature.auth.LockScreen
import id.perusahaan.asetga.ui.feature.auth.LoginScreen
import id.perusahaan.asetga.ui.feature.auth.SetupAdminScreen
import id.perusahaan.asetga.ui.navigation.MainScaffold
import id.perusahaan.asetga.ui.theme.AsetGaTheme

/** FragmentActivity (bukan ComponentActivity) karena BiometricPrompt membutuhkannya. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val root by vm.rootState.collectAsStateWithLifecycle()
            AsetGaTheme(theme) {
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
