package id.perusahaan.asetga.ui.feature.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import id.perusahaan.asetga.domain.calc.WarrantyCalc
import id.perusahaan.asetga.domain.model.AssetDetail
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.DateField
import id.perusahaan.asetga.ui.components.FormTextField
import id.perusahaan.asetga.ui.components.MoneyField
import id.perusahaan.asetga.ui.components.EmptyState
import id.perusahaan.asetga.ui.components.LabeledValue
import id.perusahaan.asetga.ui.components.SectionHeader
import id.perusahaan.asetga.util.Fmt
import java.io.File
import java.time.LocalDate

@Composable
fun AssetDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onPrintLabel: () -> Unit,
    extraActions: @Composable (assetId: String, canEdit: Boolean) -> Unit = { _, _ -> },
    extraSections: @Composable (assetId: String) -> Unit = {},
    vm: AssetDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                DetailEvent.Deleted -> onBack()
                is DetailEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar("Detail aset", onBack = onBack, actions = {
                if (vm.canEdit && state is AssetDetailState.Loaded) {
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus aset") }
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state is AssetDetailState.Loaded) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        extraActions(vm.assetId, vm.canEdit)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.prepareLabel(); onPrintLabel() },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Label QR")
                            }
                            if (vm.canEdit) {
                                Button(onClick = { onEdit(vm.assetId) }, modifier = Modifier.weight(1f).height(48.dp)) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Ubah")
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            AssetDetailState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            AssetDetailState.NotFound -> EmptyState(Icons.Outlined.SearchOff, "Aset tidak ditemukan", modifier = Modifier.padding(padding))
            is AssetDetailState.Loaded -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                DetailBody(s.detail, vm.photoFile(s.detail.asset.photoPath))
                extraSections(vm.assetId)
                HistorySection(history)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val loaded = state as? AssetDetailState.Loaded
    if (showDelete && loaded != null) {
        DeleteAssetDialog(
            detail = loaded.detail,
            canDelete = vm.canDelete,
            busy = busy,
            onDismiss = { showDelete = false },
            onDispose = { date, value, note -> showDelete = false; vm.dispose(date, value, note) },
            onDelete = { reason -> showDelete = false; vm.delete(reason) },
        )
    }
}

/**
 * Dua cara "menghapus" tanpa menghapus permanen:
 * 1. Tandai Dihapus/Dijual: aset dibuang/dijual, tetap tercatat (Admin & Staf).
 * 2. Hapus karena salah input: disembunyikan dari semua daftar, bisa dipulihkan (Admin).
 */
@Composable
private fun DeleteAssetDialog(
    detail: AssetDetail,
    canDelete: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onDispose: (LocalDate, Long?, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val alreadyDisposed = detail.asset.status == AssetStatus.DIHAPUS_DIJUAL
    var mode by remember { mutableStateOf(if (alreadyDisposed && canDelete) 1 else 0) }
    var date by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var value by remember { mutableStateOf<Long?>(null) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus ${detail.asset.code}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.asset.name, style = MaterialTheme.typography.titleSmall)
                if (!alreadyDisposed) {
                    ChoiceRow(mode == 0, "Tandai Dihapus/Dijual", "Aset dibuang, rusak total, atau dijual. Tetap tercatat dan bisa dilihat dengan filter status.") { mode = 0 }
                }
                if (canDelete) {
                    ChoiceRow(mode == 1, "Hapus karena salah input", "Hilang dari daftar, laporan, dan dashboard. Masih bisa dipulihkan Admin dari menu Aset terhapus.") { mode = 1 }
                } else if (alreadyDisposed) {
                    Text("Aset ini sudah berstatus Dihapus/Dijual. Penghapusan karena salah input hanya bisa dilakukan Admin GA.")
                }
                if (mode == 0 && !alreadyDisposed) {
                    DateField(date, { date = it }, "Tanggal penghapusan/penjualan", required = true)
                    MoneyField(value, { value = it }, "Nilai jual (opsional)")
                    FormTextField(text, { text = it }, "Keterangan (opsional)")
                } else if (mode == 1 && canDelete) {
                    FormTextField(text, { text = it }, "Alasan penghapusan", required = true, singleLine = false, minLines = 2)
                }
            }
        },
        confirmButton = {
            val enabled = !busy && when {
                mode == 0 && !alreadyDisposed -> date != null
                mode == 1 && canDelete -> text.isNotBlank()
                else -> false
            }
            TextButton(
                onClick = { if (mode == 0) onDispose(date!!, value, text) else onDelete(text) },
                enabled = enabled,
            ) { Text(if (mode == 0) "Tandai Dihapus/Dijual" else "Hapus", color = if (enabled) MaterialTheme.colorScheme.error else Color.Unspecified) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(d: AssetDetail, photo: File?) {
    val a = d.asset
    if (photo != null) {
        AsyncImage(
            model = photo, contentDescription = "Foto ${a.name}", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(a.code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(a.name, style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssetStatusChip(a.status)
            ConditionChip(a.condition)
            WarrantyChip(d.warrantyStatus)
        }
    }

    InfoCard("Informasi") {
        LabeledValue("Kategori", d.categoryName)
        LabeledValue("Kode QR", a.qrCode ?: "${a.code} (sama dengan kode aset)")
        LabeledValue("Merk / tipe", a.brandModel ?: "-")
        LabeledValue("No. seri", a.serialNumber ?: "-")
        LabeledValue("Lokasi", d.locationName)
        LabeledValue("Departemen", d.departmentName ?: "-")
        LabeledValue("Divisi", a.division ?: "-")
        LabeledValue("PIC", a.picName ?: "-")
        LabeledValue("Pengguna / detail lokasi", a.userLocationDetail ?: "-")
        LabeledValue("Tgl perolehan", Fmt.date(a.acquisitionDate))
        LabeledValue("Umur berjalan", Fmt.age(d.age.years, d.age.months))
        if (a.notes != null) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Catatan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(a.notes, style = MaterialTheme.typography.bodyMedium)
        }
    }

    val dep = d.depreciation
    InfoCard("Nilai & penyusutan") {
        LabeledValue("Harga perolehan", Fmt.rupiah(a.acquisitionCost))
        LabeledValue("Umur ekonomis", "${d.usefulLifeYears} tahun")
        LabeledValue("Nilai residu", "${Fmt.percent(d.residualPercent)} · ${Fmt.rupiah(dep.residualValue)}")
        LabeledValue("Penyusutan / tahun", Fmt.rupiah(dep.annualDepreciation))
        LabeledValue("Akumulasi penyusutan", Fmt.rupiah(dep.accumulated))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Nilai buku", style = MaterialTheme.typography.titleMedium)
            Text(Fmt.rupiah(dep.bookValue), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        val progress = if (dep.depreciableAmount > 0) dep.accumulated.toFloat() / dep.depreciableAmount else 1f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(
            if (dep.fullyDepreciated) "Sudah habis disusutkan" else "Tersusutkan ${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    InfoCard("Pengadaan") {
        LabeledValue("Nomor PR", a.prNumber ?: "-")
        LabeledValue("Nomor PO", a.poNumber ?: "-")
        LabeledValue("Nomor GR", a.grNumber ?: "-")
        LabeledValue("Pemohon pembelian", a.requester ?: "-")
    }

    if (listOf(a.memoNumber, a.memoDate, a.followUp, a.handoverDoc).any { it != null }) {
        InfoCard("Memo & serah terima") {
            LabeledValue("Nomor memo", a.memoNumber ?: "-")
            LabeledValue("Tanggal memo", Fmt.date(a.memoDate))
            LabeledValue("Berita acara serah terima", a.handoverDoc ?: "-")
            a.followUp?.let { f ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Tindak lanjut", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(f, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    InfoCard("Garansi") {
        LabeledValue("Berlaku s/d", Fmt.date(a.warrantyUntil))
        LabeledValue("Status", d.warrantyStatus.label)
        a.warrantyUntil?.let { w ->
            val days = WarrantyCalc.daysLeft(w, LocalDate.now())
            if (days >= 0) LabeledValue("Sisa", "$days hari")
        }
    }

    if (a.status == AssetStatus.DIHAPUS_DIJUAL) {
        InfoCard("Penghapusan / penjualan") {
            LabeledValue("Tanggal", Fmt.date(a.disposalDate))
            LabeledValue("Nilai jual", a.disposalValue?.let(Fmt::rupiah) ?: "-")
        }
    }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun HistorySection(history: List<AuditLogEntry>) {
    SectionHeader("Riwayat perubahan")
    if (history.isEmpty()) {
        Text("Belum ada riwayat", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    history.take(30).forEach { h ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(h.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${Fmt.dateTime(h.timestamp)} · ${h.userName}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
