package id.perusahaan.asetga.ui.feature.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import id.perusahaan.asetga.domain.model.MasterType
import id.perusahaan.asetga.ui.components.AppTopBar

fun MasterType.icon(): ImageVector = when (this) {
    MasterType.CATEGORY -> Icons.Outlined.Category
    MasterType.LOCATION -> Icons.Outlined.Place
    MasterType.DEPARTMENT -> Icons.Outlined.Apartment
    MasterType.VENDOR -> Icons.Outlined.LocalShipping
    MasterType.UNIT -> Icons.Outlined.Straighten
}

private fun MasterType.description(): String = when (this) {
    MasterType.CATEGORY -> "Kode prefix, umur ekonomis, nilai residu"
    MasterType.LOCATION -> "Gedung, lantai, ruangan, gudang"
    MasterType.DEPARTMENT -> "Unit kerja pemakai aset"
    MasterType.VENDOR -> "Pemasok & penyedia jasa servis"
    MasterType.UNIT -> "Satuan barang habis pakai"
}

@Composable
fun MasterMenuScreen(onBack: () -> Unit, onOpen: (MasterType) -> Unit) {
    Scaffold(topBar = { AppTopBar("Master data", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MasterType.entries.forEach { type ->
                ListItem(
                    modifier = Modifier.clickable { onOpen(type) },
                    leadingContent = { Icon(type.icon(), contentDescription = null) },
                    headlineContent = { Text(type.label) },
                    supportingContent = { Text(type.description()) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }
    }
}
