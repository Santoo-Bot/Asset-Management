package id.perusahaan.asetga.ui.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.EmptyState

/** Penanda sementara untuk fitur yang dibangun di tahap berikutnya. */
@Composable
fun PlaceholderScreen(title: String, message: String) {
    Scaffold(topBar = { AppTopBar(title) }) { padding ->
        EmptyState(
            icon = Icons.Outlined.Construction,
            title = "Segera hadir",
            message = message,
            modifier = Modifier.padding(padding),
        )
    }
}
