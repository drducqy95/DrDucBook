package io.legado.app.ui.config.translation.mlkit

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.gateway.MlKitTranslationGateway
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MlKitModelsViewModel(
    private val application: Application,
    private val gateway: MlKitTranslationGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MlKitModelsUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<MlKitModelsEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun onIntent(intent: MlKitModelsIntent) {
        when (intent) {
            MlKitModelsIntent.Refresh -> refresh()
            is MlKitModelsIntent.Download -> changeModel(intent.languageTag, download = true)
            is MlKitModelsIntent.Delete -> changeModel(intent.languageTag, download = false)
            MlKitModelsIntent.RequestDownloadAll -> {
                _uiState.update { it.copy(dialog = MlKitModelsDialog.DownloadAll) }
            }
            MlKitModelsIntent.RequestDeleteAll -> {
                _uiState.update { it.copy(dialog = MlKitModelsDialog.DeleteAll) }
            }
            MlKitModelsIntent.DismissDialog -> _uiState.update { it.copy(dialog = null) }
            MlKitModelsIntent.ConfirmDialog -> confirmDialog()
        }
    }

    private fun refresh() {
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gateway.getLanguageModels() }
                .onSuccess { models ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            models = models.map { model ->
                                MlKitLanguageModelUi(
                                    languageTag = model.languageTag,
                                    displayName = model.displayName,
                                    downloaded = model.downloaded,
                                )
                            }.toImmutableList(),
                        )
                    }
                }
                .onFailure { showError(it) }
        }
    }

    private fun changeModel(languageTag: String, download: Boolean) {
        if (_uiState.value.batchRunning) return
        updateBusy(languageTag, true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (download) gateway.downloadLanguage(languageTag)
                else gateway.deleteLanguage(languageTag)
            }.onSuccess {
                updateModel(languageTag, downloaded = download, busy = false)
                _effects.tryEmit(
                    MlKitModelsEffect.ShowMessage(
                        application.getString(
                            if (download) R.string.mlkit_model_downloaded
                            else R.string.mlkit_model_deleted
                        )
                    )
                )
            }.onFailure { error ->
                updateBusy(languageTag, false)
                showError(error)
            }
        }
    }

    private fun confirmDialog() {
        val dialog = _uiState.value.dialog ?: return
        _uiState.update { it.copy(dialog = null, batchRunning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val targets = when (dialog) {
                MlKitModelsDialog.DownloadAll -> _uiState.value.models.filterNot { it.downloaded }
                MlKitModelsDialog.DeleteAll -> _uiState.value.models.filter { it.downloaded }
            }
            var failures = 0
            targets.forEach { model ->
                updateBusy(model.languageTag, true)
                runCatching {
                    when (dialog) {
                        MlKitModelsDialog.DownloadAll -> gateway.downloadLanguage(model.languageTag)
                        MlKitModelsDialog.DeleteAll -> gateway.deleteLanguage(model.languageTag)
                    }
                }.onSuccess {
                    updateModel(
                        languageTag = model.languageTag,
                        downloaded = dialog == MlKitModelsDialog.DownloadAll,
                        busy = false,
                    )
                }.onFailure {
                    failures++
                    updateBusy(model.languageTag, false)
                }
            }
            _uiState.update { it.copy(batchRunning = false) }
            _effects.tryEmit(
                MlKitModelsEffect.ShowMessage(
                    application.getString(R.string.mlkit_batch_finished, failures)
                )
            )
        }
    }

    private fun updateBusy(languageTag: String, busy: Boolean) {
        _uiState.update { state ->
            state.copy(
                models = state.models.map {
                    if (it.languageTag == languageTag) it.copy(busy = busy) else it
                }.toImmutableList()
            )
        }
    }

    private fun updateModel(languageTag: String, downloaded: Boolean, busy: Boolean) {
        _uiState.update { state ->
            state.copy(
                models = state.models.map {
                    if (it.languageTag == languageTag) {
                        it.copy(downloaded = downloaded, busy = busy)
                    } else {
                        it
                    }
                }.toImmutableList()
            )
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update { it.copy(loading = false, batchRunning = false) }
        _effects.tryEmit(
            MlKitModelsEffect.ShowMessage(
                error.localizedMessage ?: application.getString(R.string.error)
            )
        )
    }
}
