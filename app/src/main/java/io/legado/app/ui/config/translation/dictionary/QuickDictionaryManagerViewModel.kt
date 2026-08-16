package io.legado.app.ui.config.translation.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.model.ExternalAssetCatalog
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.help.book.isNotShelf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class QuickDictionaryManagerViewModel(
    private val dictionaryGateway: QuickDictionaryGateway,
    private val translationGateway: QuickTranslationGateway,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickDictionaryManagerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<QuickDictionaryManagerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var allEntries: List<QuickDictionaryEntry> = emptyList()
    private var initialized = false
    private var filterJob: Job? = null

    private fun startLoading() {
        viewModelScope.launch {
            dictionaryGateway.observeEntries().collect { entries ->
                allEntries = entries
                refreshRows()
            }
        }
        viewModelScope.launch {
            dictionaryGateway.observePacks().collect { packs ->
                _uiState.update { state ->
                    state.copy(
                        packs = packs.map { pack ->
                            QuickDictionaryPackUi(
                                id = pack.id,
                                name = pack.name,
                                type = pack.type,
                                scope = pack.scope,
                                scopeKey = pack.scopeKey,
                                entryCount = pack.entryCount,
                                indexBytes = pack.indexBytes,
                            )
                        }.toImmutableList()
                    )
                }
            }
        }
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                val projects = books.asSequence()
                    .filterNot { it.isNotShelf }
                    .map { QuickDictionaryProjectUi(it.bookUrl, it.name, it.author) }
                    .sortedBy { it.name.lowercase() }
                    .toList()
                    .toImmutableList()
                _uiState.update { state ->
                    val selectedKey = when {
                        state.selectedScope != QuickDictionaryScope.PROJECT -> state.selectedScopeKey
                        projects.any { it.key == state.selectedScopeKey } -> state.selectedScopeKey
                        else -> projects.firstOrNull()?.key.orEmpty()
                    }
                    state.copy(projects = projects, selectedScopeKey = selectedKey)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val catalogs = translationGateway.getBuiltInCatalogs().toImmutableList()
            val universes = dictionaryGateway.getUniverses().toImmutableList()
            _uiState.update { state ->
                state.copy(
                    catalogs = catalogs,
                    universes = universes,
                    selectedCatalogId = state.selectedCatalogId.ifBlank {
                        catalogs.firstOrNull { it.type == state.selectedType }?.id.orEmpty()
                    },
                    loading = false,
                )
            }
            refreshRows()
        }
    }

    fun onIntent(intent: QuickDictionaryManagerIntent) {
        when (intent) {
            is QuickDictionaryManagerIntent.Initialize -> initialize(intent)
            is QuickDictionaryManagerIntent.SelectType -> {
                _uiState.update { it.copy(selectedType = intent.type, selectedCatalogId = "") }
                refreshRows()
            }
            is QuickDictionaryManagerIntent.SelectCatalog -> {
                _uiState.update {
                    it.copy(selectedType = intent.type, selectedCatalogId = intent.id)
                }
                refreshRows()
            }
            is QuickDictionaryManagerIntent.SelectScope -> selectScope(intent.scope)
            is QuickDictionaryManagerIntent.SelectScopeKey -> {
                _uiState.update { it.copy(selectedScopeKey = intent.key) }
                refreshRows()
            }
            is QuickDictionaryManagerIntent.Search -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
                refreshRows()
            }
            QuickDictionaryManagerIntent.Add -> openEditor()
            is QuickDictionaryManagerIntent.Edit -> openEditor(intent.row)
            QuickDictionaryManagerIntent.CloseEditor -> _uiState.update { it.copy(editor = null) }
            is QuickDictionaryManagerIntent.UpdateRaw -> updateEditor { copy(raw = intent.value, errorRes = null) }
            is QuickDictionaryManagerIntent.UpdateHanViet -> updateEditor { copy(hanViet = intent.value, errorRes = null) }
            is QuickDictionaryManagerIntent.UpdateTarget -> updateEditor { copy(target = intent.value, errorRes = null) }
            is QuickDictionaryManagerIntent.UpdateEditorType -> updateEditor { copy(type = intent.type, errorRes = null) }
            is QuickDictionaryManagerIntent.UpdateEditorScope -> updateEditorScope(intent.scope)
            is QuickDictionaryManagerIntent.UpdateEditorScopeKey -> updateEditor { copy(scopeKey = intent.key, errorRes = null) }
            QuickDictionaryManagerIntent.Save -> saveEditor()
            is QuickDictionaryManagerIntent.Delete -> delete(intent.id)
            QuickDictionaryManagerIntent.RequestImportFile -> _effects.tryEmit(QuickDictionaryManagerEffect.OpenImportFile)
            QuickDictionaryManagerIntent.OpenDownloadCatalog -> _effects.tryEmit(
                QuickDictionaryManagerEffect.OpenUrl(ExternalAssetCatalog.quickTranslationCleanZipUrl)
            )
            is QuickDictionaryManagerIntent.ImportFile -> importFile(intent.fileName, intent.localPath)
            QuickDictionaryManagerIntent.ImportFileFailed -> {
                _uiState.update { it.copy(importing = false) }
                _effects.tryEmit(
                    QuickDictionaryManagerEffect.ShowMessage(R.string.quick_dictionary_import_failed)
                )
            }
            is QuickDictionaryManagerIntent.DeletePack -> deletePack(intent.id)
            is QuickDictionaryManagerIntent.UpdateSelection -> _uiState.update {
                it.copy(selectionStart = intent.start, selectionEnd = intent.end)
            }
            QuickDictionaryManagerIntent.AddSelection -> addSelection()
            QuickDictionaryManagerIntent.CloseSelection -> _uiState.update { it.copy(selectionText = null) }
        }
    }

    private fun initialize(intent: QuickDictionaryManagerIntent.Initialize) {
        if (!initialized) {
            initialized = true
            val projectKey = intent.projectKey.orEmpty()
            _uiState.update { state ->
                state.copy(
                    selectedScope = if (projectKey.isBlank()) state.selectedScope else QuickDictionaryScope.PROJECT,
                    selectedScopeKey = projectKey.ifBlank { state.selectedScopeKey },
                    selectionText = intent.initialText?.takeIf(String::isNotBlank),
                    selectionStart = 0,
                    selectionEnd = intent.initialText?.length ?: 0,
                )
            }
            startLoading()
        }
    }

    private fun selectScope(scope: QuickDictionaryScope) {
        _uiState.update { state ->
            state.copy(
                selectedScope = scope,
                selectedScopeKey = defaultScopeKey(scope, state),
            )
        }
        refreshRows()
    }

    private fun defaultScopeKey(
        scope: QuickDictionaryScope,
        state: QuickDictionaryManagerUiState = _uiState.value,
    ): String = when (scope) {
        QuickDictionaryScope.GLOBAL -> ""
        QuickDictionaryScope.UNIVERSE -> state.universes
            .firstOrNull { it.key == state.selectedScopeKey }?.key
            ?: state.universes.firstOrNull()?.key.orEmpty()
        QuickDictionaryScope.PROJECT -> state.projects
            .firstOrNull { it.key == state.selectedScopeKey }?.key
            ?: state.projects.firstOrNull()?.key.orEmpty()
    }

    private fun refreshRows() {
        filterJob?.cancel()
        val state = _uiState.value
        if (!initialized || state.loading) return
        filterJob = viewModelScope.launch(Dispatchers.IO) {
            val query = state.searchQuery.trim()
            if (query.isNotEmpty()) {
                delay(250)
            }
            val customRows = allEntries.asSequence()
                .filter { it.type == state.selectedType }
                .filter { it.scope == state.selectedScope }
                .filter { state.selectedScope == QuickDictionaryScope.GLOBAL || it.scopeKey == state.selectedScopeKey }
                .filter {
                    query.isEmpty() || it.raw.contains(query, true) ||
                        it.hanViet.contains(query, true) || it.target.contains(query, true)
                }
                .map(QuickDictionaryEntry::toManagerRow)
                .toList()
            val customKeys = customRows.mapTo(hashSetOf()) {
                "${it.type}\u0000${it.raw.trim().lowercase()}"
            }
            val bundledRows = if (state.selectedScope == QuickDictionaryScope.GLOBAL) {
                translationGateway.searchBuiltInEntries(
                    type = state.selectedType,
                    query = query,
                    catalogId = state.selectedCatalogId.ifBlank { null },
                ).map {
                    QuickDictionaryRowUi(
                        catalogId = it.catalogId,
                        raw = it.raw,
                        hanViet = it.hanViet,
                        target = it.target,
                        type = it.type,
                        bundled = true,
                    )
                }.filterNot {
                    "${it.type}\u0000${it.raw.trim().lowercase()}" in customKeys
                }
            } else {
                emptyList()
            }
            ensureActive()
            _uiState.update {
                it.copy(
                    rows = (customRows + bundledRows).toImmutableList(),
                    totalCustomEntries = allEntries.size,
                )
            }
        }
    }

    private fun openEditor(row: QuickDictionaryRowUi? = null) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                editor = QuickDictionaryEditorUi(
                    id = row?.id,
                    raw = row?.raw.orEmpty(),
                    hanViet = row?.hanViet.orEmpty(),
                    target = row?.target.orEmpty(),
                    type = row?.type ?: state.selectedType,
                    scope = state.selectedScope,
                    scopeKey = defaultScopeKey(state.selectedScope, state),
                )
            )
        }
        val raw = row?.raw.orEmpty()
        if (raw.isNotBlank() && row?.hanViet.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.Default) {
                val reading = translationGateway.hanViet(raw)
                _uiState.update { current ->
                    val editor = current.editor
                    if (editor?.raw == raw && editor.hanViet.isBlank()) {
                        current.copy(editor = editor.copy(hanViet = reading))
                    } else {
                        current
                    }
                }
            }
        }
    }

    private inline fun updateEditor(
        transform: QuickDictionaryEditorUi.() -> QuickDictionaryEditorUi,
    ) {
        _uiState.update { state -> state.copy(editor = state.editor?.transform()) }
    }

    private fun updateEditorScope(scope: QuickDictionaryScope) {
        updateEditor {
            copy(scope = scope, scopeKey = defaultScopeKey(scope), errorRes = null)
        }
    }

    private fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        val invalidScope = editor.scope != QuickDictionaryScope.GLOBAL && editor.scopeKey.isBlank()
        val invalidValue = editor.raw.isBlank() || when (editor.type) {
            QuickDictionaryType.IGNORE -> false
            QuickDictionaryType.PHONETIC -> editor.raw.codePointCount(0, editor.raw.length) != 1 ||
                editor.hanViet.isBlank() && editor.target.isBlank()
            else -> editor.hanViet.isBlank() && editor.target.isBlank()
        }
        if (invalidScope || invalidValue) {
            updateEditor { copy(errorRes = R.string.quick_dictionary_required_error) }
            return
        }
        updateEditor { copy(saving = true, errorRes = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dictionaryGateway.save(
                    QuickDictionaryEntry(
                        id = editor.id ?: 0,
                        raw = editor.raw,
                        hanViet = editor.hanViet,
                        target = editor.target,
                        type = editor.type,
                        scope = editor.scope,
                        scopeKey = if (editor.scope == QuickDictionaryScope.GLOBAL) "" else editor.scopeKey,
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(editor = null) }
                _effects.tryEmit(QuickDictionaryManagerEffect.ShowMessage(R.string.quick_dictionary_saved))
            }.onFailure {
                updateEditor { copy(saving = false, errorRes = R.string.quick_dictionary_required_error) }
            }
        }
    }

    private fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dictionaryGateway.deleteEntry(id) }
                .onSuccess {
                    _effects.tryEmit(QuickDictionaryManagerEffect.ShowMessage(R.string.quick_dictionary_deleted))
                }
        }
    }

    private fun importFile(fileName: String?, localPath: String) {
        val state = _uiState.value
        if (state.importing) {
            File(localPath).delete()
            return
        }
        val type = inferType(fileName) ?: state.selectedType
        val scopeKey = defaultScopeKey(state.selectedScope, state)
        if (state.selectedScope != QuickDictionaryScope.GLOBAL && scopeKey.isBlank()) {
            File(localPath).delete()
            _effects.tryEmit(QuickDictionaryManagerEffect.ShowMessage(R.string.quick_dictionary_scope_required))
            return
        }
        _uiState.update {
            it.copy(
                importing = true,
                importProcessed = 0,
                importSucceeded = 0,
                importProcessedBytes = 0,
                importTotalBytes = 0,
                importDuplicates = 0,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val input = File(localPath)
            runCatching {
                dictionaryGateway.importPack(
                    localPath = localPath,
                    displayName = fileName
                        ?.substringBeforeLast('.')
                        ?.takeIf(String::isNotBlank)
                        ?: input.nameWithoutExtension,
                    type = type,
                    scope = state.selectedScope,
                    scopeKey = if (state.selectedScope == QuickDictionaryScope.GLOBAL) "" else scopeKey,
                ) { progress ->
                    _uiState.update {
                        it.copy(
                            importProcessed = progress.processedLines,
                            importSucceeded = progress.importedEntries,
                            importProcessedBytes = progress.processedBytes,
                            importTotalBytes = progress.totalBytes,
                            importDuplicates = progress.duplicateLines,
                        )
                    }
                }
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        importing = false,
                        importProcessed = 0,
                        importSucceeded = 0,
                        importProcessedBytes = 0,
                        importTotalBytes = 0,
                        importDuplicates = 0,
                    )
                }
                _effects.tryEmit(
                    QuickDictionaryManagerEffect.ShowMessage(
                        R.string.quick_dictionary_imported,
                        result.importedEntries,
                        result.duplicateLines,
                    )
                )
            }.onFailure {
                _uiState.update {
                    it.copy(
                        importing = false,
                        importProcessed = 0,
                        importSucceeded = 0,
                        importProcessedBytes = 0,
                        importTotalBytes = 0,
                        importDuplicates = 0,
                    )
                }
                _effects.tryEmit(
                    QuickDictionaryManagerEffect.ShowMessage(R.string.quick_dictionary_import_failed)
                )
            }
            input.delete()
        }
    }

    private fun deletePack(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { dictionaryGateway.deletePack(id) }
                .onSuccess {
                    _effects.tryEmit(
                        QuickDictionaryManagerEffect.ShowMessage(
                            R.string.quick_dictionary_pack_deleted
                        )
                    )
                }
        }
    }

    private fun inferType(fileName: String?): QuickDictionaryType? {
        val name = fileName?.lowercase().orEmpty()
        return when {
            "ignore" in name -> QuickDictionaryType.IGNORE
            "luat" in name || "rule" in name -> QuickDictionaryType.LUAT_NHAN
            "pronoun" in name || "daithu" in name -> QuickDictionaryType.PRONOUN
            "phienam" in name || "phonetic" in name -> QuickDictionaryType.PHONETIC
            "vietphrase" in name || "viet_phrase" in name -> QuickDictionaryType.VIETPHRASE
            "name" in name -> QuickDictionaryType.NAME
            else -> null
        }
    }

    private fun addSelection() {
        val state = _uiState.value
        val text = state.selectionText ?: return
        val start = minOf(state.selectionStart, state.selectionEnd).coerceIn(0, text.length)
        val end = maxOf(state.selectionStart, state.selectionEnd).coerceIn(start, text.length)
        val selected = text.substring(start, end).trim().ifEmpty { text.trim() }
        _uiState.update { it.copy(selectionText = null) }
        openEditor(QuickDictionaryRowUi(raw = selected, type = state.selectedType))
    }

    companion object {
        private val IMPORT_SEPARATORS = listOf("\t", "=>", "→", "=", "|")
    }
}
