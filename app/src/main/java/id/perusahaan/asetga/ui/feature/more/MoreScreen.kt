package id.perusahaan.asetga.ui.feature.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import id.perusahaan.asetga.BuildConfig
import id.perusahaan.asetga.domain.model.User
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.ui.components.SectionHeader

@Composable
fun MoreScreen(
    user: User,
    onOpenMaster: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenOpname: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAuditLog: () -> Unit,
    onOpenInsurance: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar("Lainnya") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text(user.fullName) },
                supportingContent = { Text("${user.role.label} · @${user.username}") },
            )
            HorizontalDivider()
            SectionHeader("Data")
            MenuRow(Icons.Outlined.Build, "Servis & perawatan", "Jadwal servis, terlambat, jatuh tempo, riwayat", onOpenServices)
            MenuRow(Icons.Outlined.Shield, "Asuransi", "Polis, tipe & perusahaan asuransi, aset yang ditanggung", onOpenInsurance)
            MenuRow(Icons.Outlined.TaskAlt, "Stock opname aset", "Audit fisik aset per lokasi dengan scan", onOpenOpname)
            MenuRow(Icons.Outlined.Category, "Master data", "Kategori, lokasi, departemen, vendor, satuan", onOpenMaster)
            SectionHeader("File")
            MenuRow(Icons.Outlined.ImportExport, "Import & export", "Excel/CSV aset, servis, stok; template import", onOpenImportExport)
            MenuRow(Icons.Outlined.Backup, "Backup & restore", "File terenkripsi ke HP atau Google Drive", onOpenBackup)
            SectionHeader("Sistem")
            if (user.role.canManageUsers) {
                MenuRow(Icons.Outlined.Group, "Pengguna & peran", "Tambah pengguna, atur peran, reset kata sandi", onOpenUsers)
            }
            MenuRow(Icons.Outlined.History, "Audit log", "Siapa mengubah apa dan kapan", onOpenAuditLog)
            MenuRow(Icons.Outlined.Settings, "Pengaturan", "Tema, kunci aplikasi, PIN, kata sandi", onOpenSettings)
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                headlineContent = { Text("Versi aplikasi") },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
