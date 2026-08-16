package io.legado.app.ui.book.read.manga

import androidx.compose.runtime.Stable
import io.legado.app.domain.manga.MangaTranslationResult

@Stable
data class MangaTranslationEditorUiState(
    val visible: Boolean = false,
    val sourceText: String = "",
    val translatedText: String = "",
    val fontSizeSp: Float = 16f,
    val textColor: Long = 0xFF111111,
    val backgroundColor: Long = 0xEFFFFFFF,
    val order: Int = 0,
)

sealed interface MangaTranslationEditorIntent {
    data class Load(val result: MangaTranslationResult) : MangaTranslationEditorIntent
    data class SetTranslatedText(val value: String) : MangaTranslationEditorIntent
    data class SetFontSize(val value: Float) : MangaTranslationEditorIntent
    data object CycleTextColor : MangaTranslationEditorIntent
    data object CycleBackgroundColor : MangaTranslationEditorIntent
    data class ChangeOrder(val delta: Int) : MangaTranslationEditorIntent
    data object Save : MangaTranslationEditorIntent
    data object Dismiss : MangaTranslationEditorIntent
}

sealed interface MangaTranslationEditorEffect {
    data class Saved(val result: MangaTranslationResult) : MangaTranslationEditorEffect
}
