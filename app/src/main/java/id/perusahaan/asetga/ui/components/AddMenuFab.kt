package id.perusahaan.asetga.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Tombol tambah dengan submenu: isi manual lewat form, import dari Excel, atau unduh template import. */
@Composable
fun AddMenuFab(
    text: String,
    onManual: () -> Unit,
    onImport: () -> Unit,
    onTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ExtendedFloatingActionButton(
            onClick = { open = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(text) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Manual") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { open = false; onManual() })
            DropdownMenuItem(text = { Text("Import dari Excel") }, leadingIcon = { Icon(Icons.Outlined.FileUpload, null) }, onClick = { open = false; onImport() })
            DropdownMenuItem(text = { Text("Unduh template") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { open = false; onTemplate() })
        }
    }
}
