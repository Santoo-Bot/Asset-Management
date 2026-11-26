package id.perusahaan.asetga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.perusahaan.asetga.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Field baca-saja yang membuka pemilih ketika diketuk. */
@Composable
private fun ClickableField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(if (required) "$label *" else label) },
            isError = error != null,
            supportingText = (error ?: supporting)?.let { msg -> { Text(msg) } },
            trailingIcon = trailing,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Lapisan transparan agar ketukan di mana pun pada field membuka pemilih.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = if (error != null || supporting != null) 20.dp else 0.dp)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    required: Boolean = false,
    clearable: Boolean = !required,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    ClickableField(
        value = value?.let(Fmt::date).orEmpty(),
        label = label,
        onClick = { open = true },
        modifier = modifier,
        error = error,
        supporting = supporting,
        required = required,
        enabled = enabled,
        trailing = {
            if (clearable && value != null) {
                IconButton(onClick = { onValueChange(null) }) { Icon(Icons.Filled.Clear, contentDescription = "Hapus tanggal") }
            } else {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            }
        },
    )
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Batal") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * Pilihan dari daftar (kategori, lokasi, dll.) lewat bottom sheet dengan pencarian.
 * [noneLabel] != null menambahkan opsi kosong di paling atas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String,
    options: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    required: Boolean = false,
    noneLabel: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    ClickableField(
        value = selected?.let(itemLabel).orEmpty(),
        label = label,
        onClick = { open = true },
        modifier = modifier,
        error = error,
        supporting = supporting,
        required = required,
        enabled = enabled,
        trailing = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )
    if (open) {
        OptionSheet(
            title = label,
            options = options,
            selected = selected,
            itemLabel = itemLabel,
            noneLabel = noneLabel,
            onDismiss = { open = false },
            onSelect = { onSelect(it); open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T?) -> Unit,
    noneLabel: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        if (query.isBlank()) options else options.filter { itemLabel(it).contains(query.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = options.size > 8)) {
        Column(Modifier.navigationBarsPadding()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (options.size > 8) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cari") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                if (noneLabel != null && query.isBlank()) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable { onSelect(null) },
                            headlineContent = { Text(noneLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = { if (selected == null) Icon(Icons.Filled.Check, contentDescription = null) },
                        )
                        HorizontalDivider()
                    }
                }
                items(filtered) { option ->
                    ListItem(
                        modifier = Modifier.clickable { onSelect(option) },
                        headlineContent = { Text(itemLabel(option)) },
                        trailingContent = {
                            if (option == selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                }
            }
        }
    }
}

/** Input Rupiah: hanya angka, ditampilkan dengan pemisah ribuan (1.250.000). */
@Composable
fun MoneyField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    required: Boolean = false,
) {
    FormTextField(
        value = value?.let(Fmt::number).orEmpty(),
        onValueChange = { text -> onValueChange(Fmt.parseRupiah(text)) },
        label = label,
        modifier = modifier,
        error = error,
        supporting = supporting,
        required = required,
        prefix = "Rp ",
        keyboardType = KeyboardType.Number,
    )
}

/** Input jumlah (boleh desimal dengan koma). */
@Composable
fun QuantityField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    required: Boolean = false,
    suffix: String? = null,
) {
    FormTextField(
        value = value,
        onValueChange = { t -> onValueChange(t.filter { it.isDigit() || it == ',' || it == '.' }.take(12)) },
        label = if (suffix != null) "$label ($suffix)" else label,
        modifier = modifier,
        error = error,
        supporting = supporting,
        required = required,
        keyboardType = KeyboardType.Decimal,
    )
}
