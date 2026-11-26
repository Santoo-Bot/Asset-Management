package id.perusahaan.asetga.ui.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.perusahaan.asetga.domain.repository.AssetRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ScanEvent {
    data class OpenAsset(val id: String) : ScanEvent
    data class NotFound(val code: String) : ScanEvent
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val assets: AssetRepository,
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    fun onCode(code: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val asset = assets.findByCode(code)
            _events.send(if (asset != null) ScanEvent.OpenAsset(asset.id) else ScanEvent.NotFound(code))
            _busy.value = false
        }
    }
}
