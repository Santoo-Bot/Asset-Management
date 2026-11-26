package id.perusahaan.asetga.ui.feature.label

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.perusahaan.asetga.data.file.LabelRenderer
import id.perusahaan.asetga.data.file.ShareHelper
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.EmptyState

@Composable
fun LabelScreen(onBack: () -> Unit, vm: LabelViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.actions.collect { a ->
            when (a) {
                is LabelAction.Print -> ShareHelper.printPdf(context, a.file, "Label aset")
                is LabelAction.SharePdf -> ShareHelper.share(context, listOf(a.file), "application/pdf", "Label QR aset")
                is LabelAction.SharePng -> ShareHelper.share(context, a.files, "image/png", "Label QR aset")
                is LabelAction.Message -> snackbar.showSnackbar(a.text)
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar("Label QR", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.labels.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = vm::sharePng, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Image, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("PNG")
                            }
                            OutlinedButton(onClick = vm::sharePdf, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("PDF")
                            }
                        }
                        Button(onClick = vm::print, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            if (state.busy) {
                                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Outlined.Print, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Cetak")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.labels.isEmpty() -> EmptyState(
                Icons.Outlined.QrCode2, "Belum ada aset dipilih",
                "Buka daftar aset, tekan lama untuk memilih beberapa aset, lalu ketuk Label QR.",
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    "${state.labels.size} label ${LabelRenderer.LABEL_W_MM}×${LabelRenderer.LABEL_H_MM} mm · ${state.pageCount} lembar A4 " +
                        "(${LabelRenderer.COLUMNS}×${LabelRenderer.ROWS} per lembar)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Isi label: QR, kode QR, dan kategori. PNG satu aset berukuran pas 50×20 mm untuk printer label.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                state.preview?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = "Pratinjau halaman ${state.page + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                if (state.pageCount > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.showPage(state.page - 1) }, enabled = state.page > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Sebelumnya")
                        }
                        Text("Halaman ${state.page + 1} / ${state.pageCount}")
                        IconButton(onClick = { vm.showPage(state.page + 1) }, enabled = state.page < state.pageCount - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Berikutnya")
                        }
                    }
                }
            }
        }
    }
}
