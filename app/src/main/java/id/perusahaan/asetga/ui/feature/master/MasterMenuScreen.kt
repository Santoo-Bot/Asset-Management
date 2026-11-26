package id.perusahaan.asetga.ui.feature.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Shield
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
    MasterType.DIVISION -> Icons.Outlined.AccountTree
    MasterType.DEPARTMENT -> Icons.Outlined.Apartment
    MasterType.VENDOR -> Icons.Outlined.LocalShipping
    MasterType.UNIT -> Icons.Outlined.Straighten
    MasterType.INSURANCE_TYPE -> Icons.Outlined.Shield
    MasterType.INSURER -> Icons.Outlined.Business
}

private fun MasterType.description(): String = when (this) {
    MasterType.CATEGORY -> "Kode prefix, umur ekonomis, nilai residu"
    MasterType.LOCATION -> "Gedung, lantai, ruangan, gudang"
    MasterType.DIVISION -> "Induk dari beberapa departemen"
    MasterType.DEPARTMENT -> "Unit kerja pemakai aset, berada di bawah divisi"
    MasterType.VENDOR -> "Pemasok & penyedia jasa servis"
    MasterType.UNIT -> "Satuan barang habis pakai"
    MasterType.INSURANCE_TYPE -> "Mis. All Risk, Kendaraan Bermotor, Kebakaran"
    MasterType.INSURER -> "Perusahaan penerbit polis & kontaknya"
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
