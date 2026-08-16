package io.legado.app.ui.book.entity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.model.EntityAnalysisCandidate
import io.legado.app.domain.usecase.AnalyzeDownloadedEntitiesUseCase
import io.legado.app.domain.usecase.ImportEntityCandidatesUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EntityAnalyzerViewModel(
    private val bookUrl: String,
    private val analyzeDownloadedEntities: AnalyzeDownloadedEntitiesUseCase,
    private val importEntityCandidates: ImportEntityCandidatesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntityAnalyzerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<EntityAnalyzerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var allCandidates: List<EntityCandidateUi> = emptyList()
    private var analysisJob: Job? = null

    init {
        analyze()
    }

    fun onIntent(intent: EntityAnalyzerIntent) {
        when (intent) {
            EntityAnalyzerIntent.Analyze -> analyze()
            EntityAnalyzerIntent.CancelAnalysis -> cancelAnalysis()
            is EntityAnalyzerIntent.Search -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
                publishCandidates()
            }
            is EntityAnalyzerIntent.ToggleCandidate -> updateCandidate(intent.raw) {
                copy(selected = !selected)
            }
            EntityAnalyzerIntent.SelectVisible -> selectVisible()
            EntityAnalyzerIntent.ClearSelection -> {
                allCandidates = allCandidates.map { it.copy(selected = false) }
                publishCandidates()
            }
            is EntityAnalyzerIntent.EditCandidate -> openEditor(intent.raw)
            is EntityAnalyzerIntent.UpdateEditHanViet -> updateEdit {
                copy(hanViet = intent.value)
            }
            is EntityAnalyzerIntent.UpdateEditTarget -> updateEdit {
                copy(target = intent.value)
            }
            is EntityAnalyzerIntent.UpdateEditType -> updateEdit {
                copy(type = intent.value)
            }
            EntityAnalyzerIntent.SaveEdit -> saveEdit()
            EntityAnalyzerIntent.RequestImport -> requestImport()
            EntityAnalyzerIntent.ConfirmImport -> confirmImport()
            EntityAnalyzerIntent.DismissDialog -> _uiState.update { it.copy(dialog = null) }
        }
    }

    private fun analyze() {
        analysisJob?.cancel()
        allCandidates = emptyList()
        _uiState.update {
            EntityAnalyzerUiState(
                bookName = it.bookName,
                analyzing = true,
            )
        }
        analysisJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    analyzeDownloadedEntities(bookUrl) { progress ->
                        _uiState.update {
                            it.copy(
                                scannedChapters = progress.scannedChapters,
                                totalChapters = progress.totalChapters,
                                downloadedChapters = progress.downloadedChapters,
                                trackedCandidates = progress.trackedCandidates,
                            )
                        }
                    }
                }
                allCandidates = result.candidates.map { it.toUi() }
                _uiState.update {
                    it.copy(
                        bookName = result.bookName,
                        analyzing = false,
                        totalChapters = result.totalChapters,
                        scannedChapters = result.totalChapters,
                        downloadedChapters = result.downloadedChapters,
                        trackedCandidates = result.candidates.size,
                        errorRes = if (result.downloadedChapters == 0) {
                            R.string.entity_analyzer_no_downloaded_chapters
                        } else {
                            null
                        },
                    )
                }
                publishCandidates()
            } catch (_: CancellationException) {
                _uiState.update { it.copy(analyzing = false) }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        analyzing = false,
                        errorRes = R.string.entity_analyzer_failed,
                    )
                }
            }
        }
    }

    private fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        _uiState.update { it.copy(analyzing = false) }
    }

    private fun selectVisible() {
        val visible = _uiState.value.candidates.mapTo(hashSetOf()) { it.raw }
        allCandidates = allCandidates.map { candidate ->
            if (candidate.raw in visible) candidate.copy(selected = true) else candidate
        }
        publishCandidates()
    }

    private fun openEditor(raw: String) {
        val candidate = allCandidates.firstOrNull { it.raw == raw } ?: return
        _uiState.update {
            it.copy(
                dialog = EntityAnalyzerDialog.Edit(
                    raw = candidate.raw,
                    hanViet = candidate.hanViet,
                    target = candidate.target,
                    type = candidate.type,
                )
            )
        }
    }

    private inline fun updateEdit(
        transform: EntityAnalyzerDialog.Edit.() -> EntityAnalyzerDialog.Edit,
    ) {
        _uiState.update { state ->
            val editor = state.dialog as? EntityAnalyzerDialog.Edit
            state.copy(dialog = editor?.transform() ?: state.dialog)
        }
    }

    private fun saveEdit() {
        val editor = _uiState.value.dialog as? EntityAnalyzerDialog.Edit ?: return
        updateCandidate(editor.raw) {
            copy(
                hanViet = editor.hanViet.trim(),
                target = editor.target.trim(),
                type = editor.type,
            )
        }
        _uiState.update { it.copy(dialog = null) }
    }

    private fun requestImport() {
        val count = allCandidates.count { it.selected }
        if (count <= 0 || _uiState.value.importing) return
        _uiState.update { it.copy(dialog = EntityAnalyzerDialog.ConfirmImport(count)) }
    }

    private fun confirmImport() {
        if (_uiState.value.importing) return
        val selected = allCandidates.filter { it.selected }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(dialog = null) }
            return
        }
        val entities = selected.map { it.toDomain() }
        _uiState.update { it.copy(importing = true, dialog = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    importEntityCandidates(bookUrl, entities)
                }
            }
            result.onSuccess { imported ->
                val importedSources = selected.mapTo(hashSetOf()) { it.raw }
                allCandidates = allCandidates.filterNot { it.raw in importedSources }
                _uiState.update { it.copy(importing = false) }
                publishCandidates()
                _effects.tryEmit(
                    EntityAnalyzerEffect.ShowMessage(
                        messageRes = R.string.entity_analyzer_imported,
                        count = imported,
                    )
                )
            }.onFailure {
                _uiState.update { it.copy(importing = false) }
                _effects.tryEmit(
                    EntityAnalyzerEffect.ShowMessage(R.string.entity_analyzer_import_failed)
                )
            }
        }
    }

    private inline fun updateCandidate(
        raw: String,
        transform: EntityCandidateUi.() -> EntityCandidateUi,
    ) {
        allCandidates = allCandidates.map { candidate ->
            if (candidate.raw == raw) candidate.transform() else candidate
        }
        publishCandidates()
    }

    private fun publishCandidates() {
        val query = _uiState.value.searchQuery.trim()
        val visible = allCandidates.asSequence()
            .filter { candidate ->
                query.isEmpty() ||
                    candidate.raw.contains(query, ignoreCase = true) ||
                    candidate.hanViet.contains(query, ignoreCase = true) ||
                    candidate.target.contains(query, ignoreCase = true) ||
                    candidate.firstChapterTitle.contains(query, ignoreCase = true)
            }
            .toList()
            .toImmutableList()
        _uiState.update {
            it.copy(
                candidates = visible,
                candidateCount = allCandidates.size,
                selectedCount = allCandidates.count(EntityCandidateUi::selected),
            )
        }
    }

    private fun EntityAnalysisCandidate.toUi() = EntityCandidateUi(
        raw = raw,
        hanViet = hanViet,
        target = target,
        type = type,
        occurrences = occurrences,
        chapterCount = chapterCount,
        firstChapterTitle = firstChapterTitle,
        context = context,
    )

    private fun EntityCandidateUi.toDomain() = EntityAnalysisCandidate(
        raw = raw,
        hanViet = hanViet,
        target = target,
        type = type,
        occurrences = occurrences,
        chapterCount = chapterCount,
        firstChapterTitle = firstChapterTitle,
        context = context,
    )
}
