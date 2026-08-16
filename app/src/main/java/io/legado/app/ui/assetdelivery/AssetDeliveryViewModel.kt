package io.legado.app.ui.assetdelivery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.model.AssetDeliveryArtifact
import io.legado.app.domain.model.AssetDeliveryArtifactKind
import io.legado.app.domain.model.AssetDeliveryResolution
import io.legado.app.domain.usecase.AssetDeliveryAuthRequiredException
import io.legado.app.domain.usecase.AssetDeliveryUseCase
import io.legado.app.utils.ConvertUtils
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

class AssetDeliveryViewModel(
    private val rawUri: String,
    private val assetDeliveryUseCase: AssetDeliveryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetDeliveryUiState(rawUri = rawUri))
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AssetDeliveryEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var artifacts: List<AssetDeliveryArtifact> = emptyList()
    private var downloadJob: Job? = null

    init {
        load(autoStartDirect = true)
    }

    fun onIntent(intent: AssetDeliveryIntent) {
        when (intent) {
            AssetDeliveryIntent.Refresh -> load(autoStartDirect = false)
            is AssetDeliveryIntent.SelectArtifact -> selectArtifact(intent.artifactId)
            AssetDeliveryIntent.DownloadSelected -> downloadSelected()
            AssetDeliveryIntent.RetryImport -> retryImport()
            AssetDeliveryIntent.OpenDownloaded -> openDownloaded()
            AssetDeliveryIntent.OpenAccount -> _effects.tryEmit(AssetDeliveryEffect.OpenAccount)
            AssetDeliveryIntent.CancelDownload -> cancelDownload()
        }
    }

    private fun load(autoStartDirect: Boolean) {
        downloadJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    loading = true,
                    configured = assetDeliveryUseCase.configured,
                    needsSignIn = false,
                    status = AssetDeliveryStatus.IDLE,
                    progressBytes = 0L,
                    totalBytes = 0L,
                    downloadedPath = null,
                    downloadedMimeType = null,
                    errorMessage = null,
                )
            }

            val resolution = runCatching { assetDeliveryUseCase.resolve(rawUri) }
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            status = AssetDeliveryStatus.ERROR,
                            errorMessage = error.message ?: "Liên kết gói không hợp lệ",
                        )
                    }
                    return@launch
                }
            val signedIn = runCatching { assetDeliveryUseCase.hasSession() }.getOrDefault(false)

            when (resolution) {
                is AssetDeliveryResolution.Single -> {
                    artifacts = listOf(resolution.artifact)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            configured = assetDeliveryUseCase.configured,
                            needsSignIn = assetDeliveryUseCase.configured && !signedIn,
                            title = resolution.artifact.displayName,
                            items = artifacts.map { it.toUi() }.toImmutableList(),
                            selectedArtifactId = resolution.artifact.id,
                            errorMessage = null,
                        )
                    }
                    if (autoStartDirect && assetDeliveryUseCase.configured && signedIn) {
                        startDownload(resolution.artifact)
                    }
                }

                is AssetDeliveryResolution.Catalog -> {
                    artifacts = resolution.artifacts
                    _uiState.update {
                        it.copy(
                            loading = false,
                            configured = assetDeliveryUseCase.configured,
                            needsSignIn = assetDeliveryUseCase.configured && !signedIn,
                            title = resolution.title,
                            items = artifacts.map { it.toUi() }.toImmutableList(),
                            selectedArtifactId = artifacts.firstOrNull()?.id,
                            errorMessage = null,
                        )
                    }
                }

                is AssetDeliveryResolution.Invalid -> {
                    artifacts = emptyList()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            title = "",
                            items = emptyList<AssetDeliveryItemUi>().toImmutableList(),
                            selectedArtifactId = null,
                            status = AssetDeliveryStatus.ERROR,
                            errorMessage = "Liên kết gói không hợp lệ",
                        )
                    }
                }
            }
        }
    }

    private fun selectArtifact(artifactId: String) {
        if (_uiState.value.downloading) return
        if (artifacts.none { it.id == artifactId }) return
        _uiState.update {
            it.copy(
                selectedArtifactId = artifactId,
                status = AssetDeliveryStatus.IDLE,
                downloadedPath = null,
                downloadedMimeType = null,
                errorMessage = null,
            )
        }
    }

    private fun downloadSelected() {
        val artifact = artifacts.firstOrNull { it.id == _uiState.value.selectedArtifactId }
        if (artifact == null) {
            _effects.tryEmit(AssetDeliveryEffect.ShowMessage("Chưa chọn gói"))
            return
        }
        startDownload(artifact)
    }

    private fun retryImport() {
        val state = _uiState.value
        val artifact = artifacts.firstOrNull { it.id == state.selectedArtifactId }
        val path = state.downloadedPath
        if (artifact == null || path.isNullOrBlank()) {
            _effects.tryEmit(AssetDeliveryEffect.ShowMessage("Downloaded package is no longer available"))
            return
        }
        val file = io.legado.app.domain.gateway.AssetDeliveryDownloadedFile(
            path = path,
            fileName = path.substringAfterLast('/'),
            sizeBytes = java.io.File(path).length(),
            sha256 = artifact.sha256,
            mimeType = state.downloadedMimeType ?: artifact.mimeType,
        )
        importJob(artifact, file)
    }

    private fun importJob(
        artifact: AssetDeliveryArtifact,
        file: io.legado.app.domain.gateway.AssetDeliveryDownloadedFile,
    ) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedArtifactId = artifact.id,
                    status = AssetDeliveryStatus.IMPORTING,
                    progressBytes = file.sizeBytes,
                    totalBytes = file.sizeBytes,
                    downloadedPath = file.path,
                    downloadedMimeType = file.mimeType,
                    errorMessage = null,
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    assetDeliveryUseCase.importArtifact(artifact, file)
                }
            }.onSuccess { message ->
                _uiState.update {
                    it.copy(status = AssetDeliveryStatus.IMPORTED, errorMessage = null)
                }
                _effects.tryEmit(AssetDeliveryEffect.ShowMessage(message))
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        status = AssetDeliveryStatus.IMPORT_FAILED,
                        errorMessage = error.message ?: "Unable to import downloaded package",
                    )
                }
                _effects.tryEmit(
                    AssetDeliveryEffect.ShowMessage(
                        "Download succeeded but import failed; retry is available",
                    ),
                )
            }
        }
    }

    private fun startDownload(artifact: AssetDeliveryArtifact) {
        if (!assetDeliveryUseCase.configured) {
            _uiState.update {
                it.copy(
                    configured = false,
                    status = AssetDeliveryStatus.ERROR,
                    errorMessage = "Supabase asset delivery chưa được cấu hình",
                )
            }
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedArtifactId = artifact.id,
                    needsSignIn = false,
                    status = AssetDeliveryStatus.DOWNLOADING,
                    progressBytes = 0L,
                    totalBytes = artifact.sizeBytes,
                    downloadedPath = null,
                    downloadedMimeType = null,
                    errorMessage = null,
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    assetDeliveryUseCase.downloadArtifact(artifact) { progress ->
                        _uiState.update {
                            it.copy(
                                progressBytes = progress.bytesDownloaded,
                                totalBytes = progress.totalBytes,
                            )
                        }
                    }
                }
            }.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        needsSignIn = false,
                        status = AssetDeliveryStatus.IMPORTING,
                        progressBytes = file.sizeBytes,
                        totalBytes = file.sizeBytes,
                        downloadedPath = file.path,
                        downloadedMimeType = file.mimeType,
                        errorMessage = null,
                    )
                }
                run {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            assetDeliveryUseCase.importArtifact(artifact, file)
                        }
                    }.onSuccess { message ->
                        _uiState.update {
                            it.copy(
                                status = AssetDeliveryStatus.IMPORTED,
                                errorMessage = null,
                            )
                        }
                        _effects.tryEmit(AssetDeliveryEffect.ShowMessage(message))
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(
                                status = AssetDeliveryStatus.IMPORT_FAILED,
                                errorMessage = error.message ?: "Không thể nhập gói đã tải",
                            )
                        }
                        _effects.tryEmit(
                            AssetDeliveryEffect.ShowMessage("Đã tải nhưng nhập gói thất bại")
                        )
                    }
                }
            }.onFailure { error ->
                when (error) {
                    is CancellationException -> {
                        _uiState.update {
                            it.copy(
                                status = AssetDeliveryStatus.IDLE,
                                errorMessage = null,
                            )
                        }
                    }

                    is AssetDeliveryAuthRequiredException -> {
                        _uiState.update {
                            it.copy(
                                needsSignIn = true,
                                status = AssetDeliveryStatus.IDLE,
                                errorMessage = "Cần đăng nhập",
                            )
                        }
                    }

                    else -> {
                        _uiState.update {
                            it.copy(
                                status = AssetDeliveryStatus.ERROR,
                                errorMessage = error.assetDeliveryMessage(),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun Throwable.assetDeliveryMessage(): String {
        val raw = message?.takeIf(String::isNotBlank) ?: return "Tải gói thất bại"
        return when {
            "Requested function was not found" in raw ->
                "Supabase asset delivery chưa được deploy"
            "PGRST205" in raw ->
                "Supabase chưa áp dụng migration asset delivery"
            else -> raw
        }
    }

    private fun openDownloaded() {
        val state = _uiState.value
        val path = state.downloadedPath
        if (path.isNullOrBlank()) {
            _effects.tryEmit(AssetDeliveryEffect.ShowMessage("Tệp đã tải chưa sẵn sàng"))
        } else {
            _effects.tryEmit(AssetDeliveryEffect.OpenFile(path, state.downloadedMimeType))
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        _uiState.update {
            it.copy(
                status = AssetDeliveryStatus.IDLE,
                errorMessage = "Đã hủy tải gói",
            )
        }
    }

    private fun AssetDeliveryArtifact.toUi(): AssetDeliveryItemUi =
        AssetDeliveryItemUi(
            id = id,
            displayName = displayName,
            fileName = fileName,
            sizeText = ConvertUtils.formatFileSize(sizeBytes),
            detail = listOf(kind.label, detail)
                .filter(String::isNotBlank)
                .joinToString(" · "),
            sha256Short = sha256.take(12),
        )

    private val AssetDeliveryArtifactKind.label: String
        get() = when (this) {
            AssetDeliveryArtifactKind.TRANSLATION -> "Dịch"
            AssetDeliveryArtifactKind.TTS -> "TTS"
            AssetDeliveryArtifactKind.LOCAL_AI -> "AI cục bộ"
        }
}
