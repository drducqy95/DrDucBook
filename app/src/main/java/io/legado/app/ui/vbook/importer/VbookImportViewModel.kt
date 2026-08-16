package io.legado.app.ui.vbook.importer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookImportPreview
import io.legado.app.domain.usecase.ImportVbookRegistryUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VbookImportViewModel(
    private val application: Application,
    private val importUseCase: ImportVbookRegistryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VbookImportUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VbookImportEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var loadedPreview: VbookImportPreview? = null

    fun onIntent(intent: VbookImportIntent) {
        when (intent) {
            is VbookImportIntent.ChangeInput -> _uiState.update { it.copy(input = intent.value) }
            is VbookImportIntent.ChangeSearch -> _uiState.update {
                it.copy(searchQuery = intent.value)
            }
            VbookImportIntent.PickJsonFile -> _effects.tryEmit(VbookImportEffect.OpenJsonFilePicker)
            is VbookImportIntent.FileSelected -> {
                _uiState.update { it.copy(input = intent.uri) }
                loadPreview()
            }
            VbookImportIntent.Preview -> loadPreview()
            is VbookImportIntent.TogglePlugin -> togglePlugin(intent.pluginId)
            VbookImportIntent.SelectAllInstallable -> selectAllInstallable()
            VbookImportIntent.ClearSelection -> _uiState.update {
                it.copy(selectedPluginIds = emptySet<String>().toImmutableSet())
            }
            VbookImportIntent.InstallSelected -> installSelected()
        }
    }

    private fun loadPreview() {
        if (_uiState.value.loading || _uiState.value.installing) return
        val input = _uiState.value.input.trim()
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preview = importUseCase.preview(input)
                loadedPreview = preview
                val installable = preview.items
                    .filter { item -> item.isSelectable() }
                    .map { item -> item.pluginId }
                    .toSet()
                _uiState.update {
                    it.copy(
                        loading = false,
                        sourceLabel = preview.sourceLabel,
                        rejectedItemCount = preview.rejectedItemCount,
                        items = preview.items.map { item ->
                            VbookImportItemUi(
                                pluginId = item.pluginId,
                                name = item.name,
                                author = item.author,
                                version = item.version,
                                description = item.description,
                                iconUrl = item.iconUrl,
                                declaredKind = item.declaredKind,
                                capabilities = item.capabilities.toImmutableSet(),
                                action = item.action,
                                compatible = item.compatible,
                                compatibilityMessage = item.compatibilityMessage,
                            )
                        }.toImmutableList(),
                        selectedPluginIds = installable.toImmutableSet(),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(loading = false) }
                showError(error)
            }
        }
    }

    private fun togglePlugin(pluginId: String) {
        val item = loadedPreview?.items?.firstOrNull { it.pluginId == pluginId }
        if (item?.isSelectable() != true) return
        _uiState.update { state ->
            val selected = state.selectedPluginIds.toMutableSet()
            if (!selected.add(pluginId)) selected.remove(pluginId)
            state.copy(selectedPluginIds = selected.toImmutableSet())
        }
    }

    private fun selectAllInstallable() {
        val selected = loadedPreview?.items
            .orEmpty()
            .filter { it.isSelectable() }
            .map { it.pluginId }
            .toSet()
        _uiState.update { it.copy(selectedPluginIds = selected.toImmutableSet()) }
    }

    private fun installSelected() {
        val preview = loadedPreview ?: return
        val selected = _uiState.value.selectedPluginIds
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(
                installing = true,
                progressCompleted = 0,
                progressTotal = selected.size,
                progressName = "",
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = importUseCase.install(
                    preview = preview,
                    selectedPluginIds = selected,
                ) { completed, total, name ->
                    _uiState.update {
                        it.copy(
                            progressCompleted = completed,
                            progressTotal = total,
                            progressName = name,
                        )
                    }
                }
                _uiState.update { it.copy(installing = false) }
                _effects.tryEmit(
                    VbookImportEffect.ShowMessage(
                        application.getString(
                            R.string.vbook_registry_import_result,
                            report.installedCount,
                            report.failedCount,
                        )
                    )
                )
                loadPreview()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(installing = false) }
                showError(error)
            }
        }
    }

    private fun showError(error: Throwable) {
        _effects.tryEmit(
            VbookImportEffect.ShowMessage(
                error.localizedMessage ?: application.getString(R.string.error)
            )
        )
    }

    private fun io.legado.app.domain.model.VbookImportPreviewItem.isSelectable(): Boolean =
        compatible && action in setOf(VbookImportAction.INSTALL, VbookImportAction.UPDATE)
}
