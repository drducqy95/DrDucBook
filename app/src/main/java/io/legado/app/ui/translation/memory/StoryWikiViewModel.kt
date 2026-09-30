package io.legado.app.ui.translation.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.AiTranslationStoryMemoryKind
import io.legado.app.domain.model.AiTranslationStoryWikiRecord
import io.legado.app.domain.model.StoryWikiCharacterGraph
import io.legado.app.domain.model.StoryWikiSnapshot
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

    private var allSnapshots = emptyList<StoryWikiSnapshot>()

    init {
        viewModelScope.launch {
            storyMemoryUseCase.observeLibraryWikiSnapshots()
                .catch { error ->
                    _uiState.update {
                        it.copy(loading = false, errorMessage = error.localizedMessage)
                    }
                }
                .collect { snapshots ->
                    allSnapshots = snapshots
                    val selected = _uiState.value.selectedBookUrl
                        ?.takeIf { url -> snapshots.any { it.bookUrl == url } }
                        ?: snapshots.firstOrNull()?.bookUrl
                    _uiState.update { it.copy(books = snapshots.toImmutableList(), selectedBookUrl = selected) }
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
            is StoryWikiIntent.SelectBook -> {
                _uiState.update {
                    it.copy(
                        selectedBookUrl = intent.value,
                        selectedRecord = null,
                        selectedGraphNode = null,
                    )
                }
                publishFilteredRecords()
            }
            is StoryWikiIntent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = intent.value, selectedRecord = null) }
                publishFilteredRecords()
            }
            is StoryWikiIntent.SelectRecord -> _uiState.update { it.copy(selectedRecord = intent.value) }
            is StoryWikiIntent.SelectGraphNode -> {
                val node = currentSnapshot()?.characterGraph?.nodes?.firstOrNull { it.id == intent.value }
                _uiState.update { it.copy(selectedGraphNode = node) }
            }
            StoryWikiIntent.DismissRecord -> _uiState.update { it.copy(selectedRecord = null) }
            StoryWikiIntent.DismissGraphNode -> _uiState.update { it.copy(selectedGraphNode = null) }
            StoryWikiIntent.Refresh -> publishFilteredRecords()
            StoryWikiIntent.OpenSelectedBook -> {
                _uiState.value.selectedRecord?.let { selected ->
                    _effects.tryEmit(StoryWikiEffect.OpenBook(selected.bookUrl, selected.bookName))
                }
            }
        }
    }

    private fun currentSnapshot(): StoryWikiSnapshot? =
        allSnapshots.firstOrNull { it.bookUrl == _uiState.value.selectedBookUrl }

    private fun publishFilteredRecords() {
        val state = _uiState.value
        val snapshot = currentSnapshot()
        val query = state.query.trim()
        fun matches(record: AiTranslationStoryWikiRecord): Boolean = query.isBlank() || listOf(
            record.bookName,
            record.title,
            record.subtitle,
            record.raw,
            record.senseKey,
            record.category,
            record.description,
        ).any { it.contains(query, ignoreCase = true) }

        val glossary = snapshot?.glossaryRecords.orEmpty()
            .filter { record ->
                when (state.selectedTab) {
                    StoryWikiTab.ENTITY -> record.kind == AiTranslationStoryMemoryKind.ENTITY
                    StoryWikiTab.WORLD_BUILDING -> record.kind == AiTranslationStoryMemoryKind.WORLD_BUILDING
                    else -> true
                } && matches(record)
            }
            .sortedWith(compareBy<AiTranslationStoryWikiRecord> { it.title.lowercase() }.thenBy { it.senseKey })
        val timelines = snapshot?.timelineRecords.orEmpty()
            .filter(::matches)
            .sortedBy { it.chapterIndex ?: Int.MAX_VALUE }
        _uiState.update {
            it.copy(
                loading = false,
                glossaryRecords = glossary.toImmutableList(),
                timelineRecords = timelines.toImmutableList(),
                relationshipTags = snapshot?.relationshipTags.orEmpty().toImmutableList(),
                characterGraph = snapshot?.characterGraph ?: StoryWikiCharacterGraph(),
                errorMessage = null,
            )
        }
    }
}
