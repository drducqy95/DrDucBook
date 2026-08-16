package io.legado.app.ui.book.read.manga

import androidx.lifecycle.ViewModel
import io.legado.app.domain.manga.MangaTranslationResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MangaTranslationEditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MangaTranslationEditorUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MangaTranslationEditorEffect>(extraBufferCapacity = 4)
    val effects = _effects.asSharedFlow()
    private var original: MangaTranslationResult? = null

    fun onIntent(intent: MangaTranslationEditorIntent) {
        when (intent) {
            is MangaTranslationEditorIntent.Load -> load(intent.result)
            is MangaTranslationEditorIntent.SetTranslatedText -> _uiState.update {
                it.copy(translatedText = intent.value)
            }
            is MangaTranslationEditorIntent.SetFontSize -> _uiState.update {
                it.copy(fontSizeSp = intent.value.coerceIn(8f, 48f))
            }
            MangaTranslationEditorIntent.CycleTextColor -> _uiState.update {
                it.copy(textColor = nextColor(it.textColor, TEXT_COLORS))
            }
            MangaTranslationEditorIntent.CycleBackgroundColor -> _uiState.update {
                it.copy(backgroundColor = nextColor(it.backgroundColor, BACKGROUND_COLORS))
            }
            is MangaTranslationEditorIntent.ChangeOrder -> _uiState.update {
                it.copy(order = (it.order + intent.delta).coerceAtLeast(0))
            }
            MangaTranslationEditorIntent.Save -> save()
            MangaTranslationEditorIntent.Dismiss -> _uiState.update { it.copy(visible = false) }
        }
    }

    private fun load(result: MangaTranslationResult) {
        original = result
        _uiState.value = MangaTranslationEditorUiState(
            visible = true,
            sourceText = result.region.sourceText,
            translatedText = result.translatedText,
            fontSizeSp = result.style.textSizeSp,
            textColor = result.style.textColor,
            backgroundColor = result.style.backgroundColor,
            order = result.region.userAdjustedOrder ?: result.region.readingOrder,
        )
    }

    private fun save() {
        val source = original ?: return
        val state = _uiState.value
        if (state.translatedText.isBlank()) return
        val result = source.copy(
            translatedText = state.translatedText.trim(),
            style = source.style.copy(
                textColor = state.textColor,
                backgroundColor = state.backgroundColor,
                textSizeSp = state.fontSizeSp,
            ),
            region = source.region.copy(userAdjustedOrder = state.order),
            userEdited = true,
        )
        original = result
        _uiState.update { it.copy(visible = false) }
        _effects.tryEmit(MangaTranslationEditorEffect.Saved(result))
    }

    private fun nextColor(current: Long, values: LongArray): Long {
        val index = values.indexOf(current)
        return values[(index + 1).mod(values.size)]
    }

    private companion object {
        val TEXT_COLORS = longArrayOf(0xFF111111, 0xFFFFFFFF, 0xFF8B0000, 0xFF003366)
        val BACKGROUND_COLORS = longArrayOf(0xEFFFFFFF, 0xE8111111, 0xEFFFF4CC, 0xE5DDEEFF)
    }
}
