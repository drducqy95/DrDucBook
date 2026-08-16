package io.legado.app.ui.translation.revision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.RevisionStatus
import io.legado.app.domain.usecase.ManageTranslationRevisionUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TranslationRevisionViewModel(
    private val useCase: ManageTranslationRevisionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranslationRevisionUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TranslationRevisionEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var request: TranslationRevisionIntent.Load? = null
    private var snapshot: ManageTranslationRevisionUseCase.Snapshot? = null

    fun onIntent(intent: TranslationRevisionIntent) {
        when (intent) {
            is TranslationRevisionIntent.Load -> load(intent)
            is TranslationRevisionIntent.Edit -> _uiState.update { it.copy(editedContent = intent.content) }
            TranslationRevisionIntent.Save -> save()
            TranslationRevisionIntent.RequestFinalize -> showDialog(TranslationRevisionDialog.Finalize)
            TranslationRevisionIntent.RequestUnlock -> showDialog(TranslationRevisionDialog.Unlock)
            is TranslationRevisionIntent.RequestRestore -> showDialog(
                TranslationRevisionDialog.Restore(intent.revisionId)
            )
            TranslationRevisionIntent.ConfirmDialog -> confirmDialog()
            TranslationRevisionIntent.DismissDialog -> showDialog(null)
            TranslationRevisionIntent.Refresh -> request?.let(::load)
        }
    }

    private fun load(nextRequest: TranslationRevisionIntent.Load) {
        request = nextRequest
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                useCase.load(
                    nextRequest.bookUrl,
                    nextRequest.chapterIndex,
                    nextRequest.targetLanguage,
                    nextRequest.provider,
                )
            }.onSuccess(::showSnapshot).onFailure(::showError)
        }
    }

    private fun save() {
        val currentSnapshot = snapshot ?: return
        val currentRequest = request ?: return
        val content = _uiState.value.editedContent
        if (content.isBlank()) {
            showError(IllegalArgumentException("Translation cannot be empty"))
            return
        }
        mutate {
            useCase.saveUserEdit(
                currentSnapshot,
                currentRequest.targetLanguage,
                currentRequest.provider,
                content,
            )
        }
    }

    private fun confirmDialog() {
        val dialog = _uiState.value.dialog ?: return
        val currentSnapshot = snapshot ?: return
        val currentRequest = request ?: return
        showDialog(null)
        mutate {
            when (dialog) {
                TranslationRevisionDialog.Finalize -> {
                    val latestSnapshot = if (shouldSaveBeforeFinalize(currentSnapshot)) {
                        useCase.saveUserEdit(
                            currentSnapshot,
                            currentRequest.targetLanguage,
                            currentRequest.provider,
                            _uiState.value.editedContent,
                        )
                        useCase.load(
                            currentRequest.bookUrl,
                            currentRequest.chapterIndex,
                            currentRequest.targetLanguage,
                            currentRequest.provider,
                        )
                    } else {
                        currentSnapshot
                    }
                    useCase.finalize(
                        latestSnapshot,
                        currentRequest.targetLanguage,
                        currentRequest.provider,
                    )
                }
                TranslationRevisionDialog.Unlock -> useCase.unlock(
                    currentSnapshot,
                    currentRequest.targetLanguage,
                    currentRequest.provider,
                )
                is TranslationRevisionDialog.Restore -> useCase.restore(
                    currentSnapshot,
                    currentRequest.targetLanguage,
                    currentRequest.provider,
                    dialog.revisionId,
                )
            }
        }
    }

    private fun shouldSaveBeforeFinalize(
        currentSnapshot: ManageTranslationRevisionUseCase.Snapshot,
    ): Boolean {
        val editedContent = _uiState.value.editedContent
        return editedContent.isNotBlank() && editedContent != currentSnapshot.current?.content
    }

    private fun mutate(action: suspend () -> Unit) {
        val currentRequest = request ?: return
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                action()
                useCase.load(
                    currentRequest.bookUrl,
                    currentRequest.chapterIndex,
                    currentRequest.targetLanguage,
                    currentRequest.provider,
                )
            }.onSuccess { updated ->
                showSnapshot(updated)
                _effects.tryEmit(TranslationRevisionEffect.ShowMessage("Translation revision saved"))
            }.onFailure(::showError)
        }
    }

    private fun showSnapshot(value: ManageTranslationRevisionUseCase.Snapshot) {
        snapshot = value
        _uiState.update {
            it.copy(
                loading = false,
                saving = false,
                bookTitle = value.book.name,
                chapterTitle = value.chapter.title,
                rawContent = value.rawContent,
                editedContent = value.current?.content.orEmpty(),
                status = value.current?.status,
                history = value.history.map { revision ->
                    TranslationRevisionItemUi(
                        revisionId = revision.revisionId,
                        content = revision.content,
                        status = revision.status,
                        actor = revision.actor,
                        updatedAt = revision.updatedAt,
                        stale = revision.status == RevisionStatus.STALE,
                    )
                }.toImmutableList(),
                errorMessage = null,
            )
        }
    }

    private fun showDialog(dialog: TranslationRevisionDialog?) {
        _uiState.update { it.copy(dialog = dialog) }
    }

    private fun showError(error: Throwable) {
        val message = error.localizedMessage ?: "Translation revision operation failed"
        _uiState.update {
            it.copy(loading = false, saving = false, errorMessage = message)
        }
        _effects.tryEmit(TranslationRevisionEffect.ShowMessage(message))
    }
}
