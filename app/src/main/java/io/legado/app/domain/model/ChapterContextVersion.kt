package io.legado.app.domain.model

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class ChapterContextVersionUi(
    val id: String,
    val title: String,
    val provider: String? = null,
    val charCount: Int = 0,
    val statusDescription: String? = null,
    val isAvailable: Boolean = true,
    val previewText: String = "",
)

@Stable
data class ChapterContextCopyUiState(
    val chapterTitle: String = "",
    val versions: ImmutableList<ChapterContextVersionUi> = persistentListOf(),
    val isLoading: Boolean = false,
)
