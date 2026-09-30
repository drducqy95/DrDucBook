package io.legado.app.ui.download.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.DownloadCenterGateway
import io.legado.app.domain.model.DownloadCenterSnapshot
import io.legado.app.domain.model.DownloadDisplayState
import io.legado.app.domain.model.DownloadItemKind
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadCenterViewModel(
    private val gateway: DownloadCenterGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadCenterUiState())
    val uiState = _uiState.asStateFlow()

    private var latestSnapshot = DownloadCenterSnapshot()

    init {
        viewModelScope.launch {
            gateway.observeSnapshot().collect { snapshot ->
                latestSnapshot = snapshot
                rebuild()
            }
        }
    }

    fun onIntent(intent: DownloadCenterIntent) {
        when (intent) {
            DownloadCenterIntent.Refresh -> rebuild()
            is DownloadCenterIntent.SetFilter -> {
                _uiState.update { it.copy(filter = intent.filter) }
                rebuild()
            }
            is DownloadCenterIntent.PerformAction -> viewModelScope.launch {
                runCatching { gateway.perform(intent.action) }
            }
        }
    }

    private fun rebuild() {
        val state = _uiState.value
        val visible = latestSnapshot.items.filter { item ->
            when (state.filter) {
                DownloadCenterFilter.ALL -> true
                DownloadCenterFilter.BOOK -> item.kind == DownloadItemKind.BOOK
                DownloadCenterFilter.MEDIA -> item.kind == DownloadItemKind.MEDIA
                DownloadCenterFilter.ACTIVE -> item.state == DownloadDisplayState.WAITING ||
                    item.state == DownloadDisplayState.RUNNING ||
                    item.state == DownloadDisplayState.PAUSED
                DownloadCenterFilter.COMPLETED -> item.state == DownloadDisplayState.COMPLETED
                DownloadCenterFilter.FAILED -> item.state == DownloadDisplayState.FAILED
            }
        }
        _uiState.update {
            it.copy(
                loading = false,
                items = visible.toImmutableList(),
                activeCount = latestSnapshot.attention.activeCount,
                recoverableCount = latestSnapshot.attention.recoverableCount,
                completedCount = latestSnapshot.items.count { item ->
                    item.state == DownloadDisplayState.COMPLETED
                },
                failedCount = latestSnapshot.items.count { item ->
                    item.state == DownloadDisplayState.FAILED
                },
            )
        }
    }
}
