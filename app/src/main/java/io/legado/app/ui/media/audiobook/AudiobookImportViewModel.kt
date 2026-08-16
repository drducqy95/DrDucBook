package io.legado.app.ui.media.audiobook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AudiobookImportGateway
import io.legado.app.domain.model.AudiobookCreateRequest
import io.legado.app.domain.model.AudiobookTrackCandidate
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudiobookImportViewModel(
    private val gateway: AudiobookImportGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookImportUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AudiobookImportEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: AudiobookImportIntent) {
        when (intent) {
            AudiobookImportIntent.PickFiles -> _effects.tryEmit(AudiobookImportEffect.PickFiles)
            AudiobookImportIntent.PickFolder -> _effects.tryEmit(AudiobookImportEffect.PickFolder)
            is AudiobookImportIntent.ScanFiles -> scan { gateway.scanFiles(intent.uris) }
            is AudiobookImportIntent.ScanFolder -> scan { gateway.scanTree(intent.uri) }
            is AudiobookImportIntent.UpdateTitle -> _uiState.update { it.copy(title = intent.value) }
            is AudiobookImportIntent.UpdateAuthor -> _uiState.update { it.copy(author = intent.value) }
            is AudiobookImportIntent.UpdateTrackTitle -> updateTrack(intent.id) { it.copy(title = intent.value) }
            is AudiobookImportIntent.ToggleTrack -> updateTrack(intent.id) { it.copy(selected = !it.selected) }
            AudiobookImportIntent.Create -> create()
        }
    }

    private fun scan(block: suspend () -> io.legado.app.domain.model.AudiobookImportPreview) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { block() }
                .onSuccess { preview ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            title = preview.title,
                            author = preview.author,
                            tracks = preview.tracks.map { it.toUi() }.toImmutableList(),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false) }
                    _effects.tryEmit(AudiobookImportEffect.ShowMessage(error.localizedMessage.orEmpty()))
                }
        }
    }

    private fun updateTrack(id: String, transform: (AudiobookTrackUi) -> AudiobookTrackUi) {
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { if (it.id == id) transform(it) else it }.toImmutableList()
            )
        }
    }

    private fun create() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true) }
            runCatching {
                gateway.createBook(
                    AudiobookCreateRequest(
                        title = state.title,
                        author = state.author,
                        tracks = state.tracks.map { it.toDomain() },
                    )
                )
            }.onSuccess { bookUrl ->
                _effects.tryEmit(AudiobookImportEffect.Created(bookUrl))
            }.onFailure { error ->
                _effects.tryEmit(AudiobookImportEffect.ShowMessage(error.localizedMessage.orEmpty()))
            }
            _uiState.update { it.copy(creating = false) }
        }
    }

    private fun AudiobookTrackCandidate.toUi() = AudiobookTrackUi(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        trackNumber = trackNumber,
        durationMs = durationMs,
        startMs = startMs,
        endMs = endMs,
        mimeType = mimeType,
        selected = selected,
    )

    private fun AudiobookTrackUi.toDomain() = AudiobookTrackCandidate(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        album = _uiState.value.title,
        trackNumber = trackNumber,
        durationMs = durationMs,
        startMs = startMs,
        endMs = endMs,
        mimeType = mimeType,
        selected = selected,
    )
}
