package io.legado.app.ui.translation.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.usecase.TranslationStoryMemoryUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StoryWikiViewModel(
    private val storyMemoryUseCase: TranslationStoryMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryWikiUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<StoryWikiEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var allRecords = emptyList<AiTranslationStoryWikiRecord>()

    init {
        viewModelScope.launch {
            storyMemoryUseCase.observeLibraryRecords()
                .catch { error ->
                    _uiState.update {
                        it.copy(loading = false, errorMessage = error.localizedMessage)
                    }
                }
                .collect { records ->
                    allRecords = records
                    publishFilteredRecords()
                }
        }
    }

    fun onIntent(intent: StoryWikiIntent) {
        when (intent) {
            is StoryWikiIntent.ChangeQuery -> {
                _uiState.update { it.copy(query = intent.value) }
                publishFilteredRecords()
            }
            is StoryWikiIntent.SelectKind -> {
                _uiState.update { it.copy(selectedKind = intent.value) }
                publishFilteredRecords()
            }
            is StoryWikiIntent.SelectRecord ->
                _uiState.update { it.copy(selectedRecord = intent.value) }
            StoryWikiIntent.DismissRecord ->
                _uiState.update { it.copy(selectedRecord = null) }
            StoryWikiIntent.OpenSelectedBook -> {
                _uiState.value.selectedRecord?.let { selected ->
                    _effects.tryEmit(StoryWikiEffect.OpenBook(selected.bookUrl, selected.bookName))
                }
            }
        }
    }

    private fun publishFilteredRecords() {
        val state = _uiState.value
        val query = state.query.trim()
        val filtered = allRecords.asSequence()
            .filter { state.selectedKind == null || it.kind == state.selectedKind }
            .filter { record ->
                query.isBlank() || listOf(
                    record.bookName,
                    record.title,
                    record.subtitle,
                ).any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(
                compareBy<AiTranslationStoryWikiRecord> { it.bookName.lowercase() }
                    .thenBy { it.chapterIndex ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            .toList()
        _uiState.update {
            it.copy(
                loading = false,
                records = filtered.toImmutableList(),
                errorMessage = null,
            )
        }
    }
}
