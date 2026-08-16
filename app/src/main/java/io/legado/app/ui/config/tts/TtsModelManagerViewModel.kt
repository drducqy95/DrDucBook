package io.legado.app.ui.config.tts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.gateway.LocalTtsModelGateway
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.model.LocalTtsModelInfo
import io.legado.app.domain.usecase.TestLocalTtsModelUseCase
import io.legado.app.domain.usecase.AccountEntitlementUseCase
import io.legado.app.model.tts.LocalTtsModelRegistry
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TtsModelManagerViewModel(
    private val context: Context,
    private val gateway: LocalTtsModelGateway,
    private val testModel: TestLocalTtsModelUseCase,
    private val accountEntitlement: AccountEntitlementUseCase,
) : ViewModel() {

    private var importJob: Job? = null

    private val _uiState = MutableStateFlow(TtsModelManagerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TtsModelManagerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            gateway.observeModels().collect { models ->
                val previousVoices = _uiState.value.models.associate { it.id to it.selectedVoiceId }
                _uiState.update { state ->
                    state.copy(
                        models = models.map { model ->
                            model.toUi(previousVoices[model.id])
                        }.toImmutableList(),
                    )
                }
            }
        }
        refresh()
    }

    fun onIntent(intent: TtsModelManagerIntent) {
        when (intent) {
            TtsModelManagerIntent.Refresh -> refresh()
            TtsModelManagerIntent.PickImportFile -> requestImportFile()
            TtsModelManagerIntent.OpenCatalog ->
                _effects.tryEmit(TtsModelManagerEffect.OpenUrl(ExternalAssetCatalog.ttsPiperVoiceFolderUrl))
            is TtsModelManagerIntent.ImportFile -> importModel(intent.uri)
            TtsModelManagerIntent.CancelImport -> cancelImport()
            is TtsModelManagerIntent.SelectVoice -> selectVoice(intent.modelId, intent.voiceId)
            is TtsModelManagerIntent.TestModel -> test(intent.modelId)
            is TtsModelManagerIntent.SetDefault -> setDefault(intent.modelId)
            is TtsModelManagerIntent.RequestDelete ->
                _uiState.update { it.copy(deletingModelId = intent.modelId) }
            TtsModelManagerIntent.ConfirmDelete -> deleteSelected()
            TtsModelManagerIntent.DismissDelete ->
                _uiState.update { it.copy(deletingModelId = null) }
        }
    }

    private fun requestImportFile() {
        viewModelScope.launch {
            runCatching {
                accountEntitlement.requireLocalTtsImportAllowed(installedUserModelCount())
            }.onSuccess {
                _effects.tryEmit(TtsModelManagerEffect.PickImportFile)
            }.onFailure(::showError)
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            runCatching { gateway.refresh() }
                .onFailure(::showError)
            _uiState.update { it.copy(loading = false) }
        }
    }

    private fun importModel(uri: android.net.Uri) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            _uiState.update { it.copy(importing = true, importProgress = null) }
            runCatching {
                accountEntitlement.requireLocalTtsImportAllowed(installedUserModelCount())
                gateway.importModel(uri) { progress ->
                    _uiState.update { it.copy(importProgress = progress) }
                }
            }
                .onSuccess { model ->
                    _effects.tryEmit(
                        TtsModelManagerEffect.ShowMessage(
                            context.getString(R.string.local_tts_model_imported, model.name)
                        )
                    )
                }
                .onFailure { error ->
                    if (error !is CancellationException) showError(error)
                }
            _uiState.update { it.copy(importing = false, importProgress = null) }
        }
    }

    private fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _uiState.update { it.copy(importing = false, importProgress = null) }
        _effects.tryEmit(
            TtsModelManagerEffect.ShowMessage(context.getString(R.string.local_tts_import_cancelled))
        )
    }

    private fun selectVoice(modelId: String, voiceId: Int) {
        _uiState.update { state ->
            state.copy(
                models = state.models.map { model ->
                    if (model.id == modelId) model.copy(selectedVoiceId = voiceId) else model
                }.toImmutableList(),
            )
        }
    }

    private fun test(modelId: String) {
        val model = _uiState.value.models.firstOrNull { it.id == modelId } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(testingModelId = modelId) }
            val result = testModel(modelId, model.selectedVoiceId)
            val message = if (result.success) {
                context.getString(
                    R.string.local_tts_test_passed,
                    result.frameCount,
                    result.sampleRate,
                )
            } else {
                context.getString(R.string.local_tts_test_failed, result.message)
            }
            _effects.tryEmit(TtsModelManagerEffect.ShowMessage(message))
            _uiState.update { it.copy(testingModelId = null) }
        }
    }

    private fun setDefault(modelId: String) {
        val model = _uiState.value.models.firstOrNull { it.id == modelId } ?: return
        viewModelScope.launch {
            runCatching { gateway.selectDefaultModel(modelId, model.selectedVoiceId) }
                .onSuccess {
                    _effects.tryEmit(
                        TtsModelManagerEffect.ShowMessage(
                            context.getString(R.string.local_tts_default_selected, model.name)
                        )
                    )
                }
                .onFailure(::showError)
        }
    }

    private fun deleteSelected() {
        val modelId = _uiState.value.deletingModelId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingModelId = null) }
            runCatching { gateway.deleteModel(modelId) }
                .onSuccess {
                    _effects.tryEmit(
                        TtsModelManagerEffect.ShowMessage(
                            context.getString(R.string.local_tts_model_deleted)
                        )
                    )
                }
                .onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        _effects.tryEmit(
            TtsModelManagerEffect.ShowMessage(
                error.localizedMessage ?: context.getString(R.string.unknown_error)
            )
        )
    }

    private fun installedUserModelCount(): Int = _uiState.value.models.count {
        it.id != LocalTtsModelRegistry.BUNDLED_DEBUG_ID
    }

    private fun LocalTtsModelInfo.toUi(previousVoiceId: Int?): TtsModelItemUi {
        val voiceId = previousVoiceId
            ?.takeIf { candidate -> voices.any { it.id == candidate } }
            ?: selectedVoiceId
            ?: defaultVoiceId
        return TtsModelItemUi(
            id = id,
            name = name,
            engine = engine,
            language = language,
            sampleRate = sampleRate,
            voices = voices.map { TtsVoiceItemUi(it.id, it.name) }.toImmutableList(),
            selectedVoiceId = voiceId,
            isDefault = selectedVoiceId != null,
            attribution = attribution,
            license = license,
            checksum = checksum,
            sizeBytes = sizeBytes,
            runtimeReady = runtimeReady,
        )
    }
}
