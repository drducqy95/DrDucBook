package io.legado.app.ui.media.audiobook

import android.net.Uri
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AudiobookTrackUi(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val trackNumber: Int,
    val durationMs: Long,
    val startMs: Long?,
    val endMs: Long?,
    val mimeType: String,
    val selected: Boolean,
)

@Stable
data class AudiobookImportUiState(
    val loading: Boolean = false,
    val creating: Boolean = false,
    val title: String = "",
    val author: String = "",
    val tracks: ImmutableList<AudiobookTrackUi> = persistentListOf(),
)

sealed interface AudiobookImportIntent {
    data object PickFiles : AudiobookImportIntent
    data object PickFolder : AudiobookImportIntent
    data class ScanFiles(val uris: List<Uri>) : AudiobookImportIntent
    data class ScanFolder(val uri: Uri) : AudiobookImportIntent
    data class UpdateTitle(val value: String) : AudiobookImportIntent
    data class UpdateAuthor(val value: String) : AudiobookImportIntent
    data class UpdateTrackTitle(val id: String, val value: String) : AudiobookImportIntent
    data class ToggleTrack(val id: String) : AudiobookImportIntent
    data object Create : AudiobookImportIntent
}

sealed interface AudiobookImportEffect {
    data object PickFiles : AudiobookImportEffect
    data object PickFolder : AudiobookImportEffect
    data class Created(val bookUrl: String) : AudiobookImportEffect
    data class ShowMessage(val message: String) : AudiobookImportEffect
}
