package io.legado.app.ui.authoring.ebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.usecase.AuthoringProjectUseCase
import io.legado.app.domain.model.resolveEbookDocument
import io.legado.app.service.export.EbookLayoutRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EbookPreviewViewModel(
    private val projects: AuthoringProjectUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EbookPreviewUiState())
    val uiState = _uiState.asStateFlow()
    private var loadedProjectId: String? = null

    fun onIntent(intent: EbookPreviewIntent) {
        when (intent) {
            is EbookPreviewIntent.Load -> load(intent.projectId)
            is EbookPreviewIntent.SelectChapter -> _uiState.update {
                it.copy(selectedChapterId = intent.chapterId)
            }
            is EbookPreviewIntent.SetViewport -> _uiState.update { it.copy(viewport = intent.value) }
            is EbookPreviewIntent.SetFontScale -> _uiState.update {
                it.copy(fontScale = intent.value.coerceIn(0.75f, 1.75f))
            }
            EbookPreviewIntent.ToggleDarkMode -> _uiState.update { it.copy(darkMode = !it.darkMode) }
        }
    }

    private fun load(projectId: String) {
        if (loadedProjectId == projectId) return
        loadedProjectId = projectId
        viewModelScope.launch {
            runCatching { projects.get(projectId) }
                .onSuccess { project ->
                    _uiState.update {
                        it.copy(
                            project = project,
                            rendered = project?.let {
                                EbookLayoutRenderer.render(it.resolveEbookDocument(), it.style)
                            },
                            selectedChapterId = project?.chapters?.firstOrNull()?.id,
                            loading = false,
                            errorMessage = if (project == null) "Project not found" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, errorMessage = error.localizedMessage) }
                }
        }
    }
}
