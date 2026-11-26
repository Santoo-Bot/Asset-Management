package id.perusahaan.asetga.ui.feature.audit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.AuditLogEntry
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.ui.components.AppTopBar
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class AuditLogViewModel @Inject constructor(repo: AuditLogRepository) : ViewModel() {
    private val _type = MutableStateFlow<AuditEntityType?>(null)
    val type: StateFlow<AuditEntityType?> = _type.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val logs: Flow<PagingData<AuditLogEntry>> = combine(_type, _query.debounce(300)) { t, q -> t to q }
        .flatMapLatest { (t, q) -> repo.paging(t, q) }
        .cachedIn(viewModelScope)

    fun setType(t: AuditEntityType?) { _type.value = t }
    fun setQuery(q: String) { _query.value = q }
}

@Composable
fun AuditLogScreen(onBack: () -> Unit, vm: AuditLogViewModel = hiltViewModel()) {
    val type by vm.type.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val logs = vm.logs.collectAsLazyPagingItems()
    Scaffold(topBar = { AppTopBar("Audit log", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = vm::setQuery, singleLine = true,
                placeholder = { Text("Cari perubahan atau nama pengguna") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == null, onClick = { vm.setType(null) }, label = { Text("Semua") })
                AuditEntityType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { vm.setType(t) }, label = { Text(t.label) })
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(count = logs.itemCount, key = logs.itemKey { it.id }) { i ->
                    val e = logs[i] ?: return@items
                    ListItem(
                        overlineContent = { Text("${Fmt.dateTime(e.timestamp)} · ${e.entityType.label} · ${e.action.label}") },
                        headlineContent = { Text(e.summary) },
                        supportingContent = { Text("oleh ${e.userName}") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
