package id.perusahaan.asetga.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Satu tombol "Excel" dengan submenu Import, Export, dan Unduh template. Isi yang null tidak ditampilkan;
 * bila semuanya null, tombol tidak muncul. [inTopBar] = gaya teks untuk bilah atas, bila tidak = tombol lebar penuh.
 */
@Composable
fun ExcelMenuButton(
    onImport: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onTemplate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    inTopBar: Boolean = false,
    enabled: Boolean = true,
    importLabel: String = "Import dari Excel",
    exportLabel: String = "Export ke Excel",
    templateLabel: String = "Unduh template",
) {
    if (onImport == null && onExport == null && onTemplate == null) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val label: @Composable () -> Unit = {
            Icon(Icons.Outlined.TableChart, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Excel")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        if (inTopBar) {
            TextButton(
                onClick = { open = true }, enabled = enabled,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
            ) { label() }
        } else {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) { label() }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            onImport?.let { act ->
                DropdownMenuItem(text = { Text(importLabel) }, leadingIcon = { Icon(Icons.Outlined.FileUpload, null) }, onClick = { open = false; act() })
            }
            onExport?.let { act ->
                DropdownMenuItem(text = { Text(exportLabel) }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) }, onClick = { open = false; act() })
            }
            onTemplate?.let { act ->
                DropdownMenuItem(text = { Text(templateLabel) }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { open = false; act() })
            }
        }
    }
}
