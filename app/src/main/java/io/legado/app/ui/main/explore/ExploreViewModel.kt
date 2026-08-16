package io.legado.app.ui.main.explore

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.domain.usecase.TranslateDynamicUiTextUseCase
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExploreViewModel(
    application: Application,
    private val exploreRepository: ExploreRepository,
    private val exploreKindUseCase: ExploreKindUiUseCase,
    private val translateDynamicUiTextUseCase: TranslateDynamicUiTextUseCase,
    private val localPreferencesRepository: LocalPreferencesRepository,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreUiState())
    private val _effects = MutableSharedFlow<ExploreEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var exploreJob: Job? = null
    private var kindsJob: Job? = null
    private var sourceNamesJob: Job? = null

    init {
        observeGroups()
        viewModelScope.launch {
            val savedSourceUrl = localPreferencesRepository
                .getPreference(LocalPreferencesKeys.EXPLORE_SELECTED_SOURCE_URL, "")
                .first()
                .trim()
                .ifBlank { null }
            _uiState.update { it.copy(expandedId = savedSourceUrl) }
            observeExplore()
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            exploreRepository.getExploreGroups()
                .flowOn(IO)
                .collectLatest { groups ->
                    _uiState.update { it.copy(groups = groups.toImmutableList()) }
                }
        }
    }

    fun search(key: String) {
        _uiState.update { it.copy(searchKey = key, expandedId = null) }
        observeExplore()
    }

    fun setGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group, expandedId = null) }
        observeExplore()
    }

    fun toggleSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(isSearch = visible) }
        if (!visible) {
            search("")
        }
    }

    private fun observeExplore() {
        exploreJob?.cancel()
        exploreJob = viewModelScope.launch {
            val state = _uiState.value
            val query = state.searchKey
            val selectedGroup = state.selectedGroup

            exploreRepository.getExploreSources(query, selectedGroup)
                .flowOn(IO)
                .collectLatest { items ->
                    val previousSourceUrl = _uiState.value.expandedId
                    val selectedSource = items.firstOrNull {
                        it.bookSourceUrl == previousSourceUrl
                    } ?: items.firstOrNull()
                    val selectionChanged = selectedSource?.bookSourceUrl != previousSourceUrl
                    _uiState.update {
                        it.copy(
                            items = items.toImmutableList(),
                            expandedId = selectedSource?.bookSourceUrl,
                            exploreKinds = if (selectionChanged) persistentListOf() else it.exploreKinds,
                            kindDisplayNames = if (selectionChanged) persistentMapOf() else it.kindDisplayNames,
                            kindValues = if (selectionChanged) persistentMapOf() else it.kindValues,
                            sourceBrowserUrl = if (selectionChanged) null else it.sourceBrowserUrl,
                            loadingKinds = selectionChanged && selectedSource != null,
                        )
                    }
                    translateSourceNames(items)
                    if (selectionChanged && selectedSource != null) {
                        localPreferencesRepository.updatePreference(
                            LocalPreferencesKeys.EXPLORE_SELECTED_SOURCE_URL,
                            selectedSource.bookSourceUrl,
                        )
                        loadExploreKinds(selectedSource)
                    }
                }
        }
    }

    fun selectSource(source: BookSourcePart) {
        if (_uiState.value.expandedId == source.bookSourceUrl) return
        _uiState.update {
            it.copy(
                expandedId = source.bookSourceUrl,
                exploreKinds = persistentListOf(),
                kindDisplayNames = persistentMapOf(),
                kindValues = persistentMapOf(),
                sourceBrowserUrl = null,
                loadingKinds = true,
            )
        }
        viewModelScope.launch(IO) {
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.EXPLORE_SELECTED_SOURCE_URL,
                source.bookSourceUrl,
            )
        }
        loadExploreKinds(source)
    }

    fun toggleExpand(source: BookSourcePart) {
        selectSource(source)
    }

    private fun loadExploreKinds(source: BookSourcePart) {
        kindsJob?.cancel()
        kindsJob = viewModelScope.launch(IO) {
            try {
                val sourceBrowserUrl = source.getBookSource()?.let { bookSource ->
                    sequenceOf(bookSource.loginUrl, bookSource.bookSourceUrl)
                        .filterNotNull()
                        .map(String::trim)
                        .firstOrNull { url ->
                            url.startsWith("https://", ignoreCase = true) ||
                                url.startsWith("http://", ignoreCase = true)
                        }
                }
                _uiState.update {
                    if (it.expandedId == source.bookSourceUrl) {
                        it.copy(sourceBrowserUrl = sourceBrowserUrl)
                    } else it
                }
                val kinds = source.exploreKinds()
                exploreKindUseCase.warmUp(source.bookSourceUrl)
                val infoMap = getExploreInfoMap(source.bookSourceUrl)
                val rawDisplayNames = kinds.associate { kind ->
                    kind.title to exploreKindUseCase.resolveDisplayName(
                        kind = kind,
                        sourceUrl = source.bookSourceUrl,
                        infoMap = infoMap
                    )
                }
                val displayNames = translateSourceLabels(
                    sourceUrl = source.bookSourceUrl,
                    labels = rawDisplayNames,
                )
                val values = buildKindValues(kinds, source.bookSourceUrl)
                _uiState.update {
                    if (it.expandedId == source.bookSourceUrl) {
                        it.copy(
                            exploreKinds = kinds.toImmutableList(),
                            kindDisplayNames = displayNames.toImmutableMap(),
                            kindValues = values.toImmutableMap(),
                            loadingKinds = false
                        )
                    } else it
                }
            } catch (e: Exception) {
                val repairedBrowserUrl = source.getBookSource()?.let { bookSource ->
                    sequenceOf(bookSource.loginUrl, bookSource.bookSourceUrl)
                        .filterNotNull()
                        .map(String::trim)
                        .firstOrNull { url ->
                            url.startsWith("https://", ignoreCase = true) ||
                                url.startsWith("http://", ignoreCase = true)
                        }
                }
                _uiState.update {
                    it.copy(
                        sourceBrowserUrl = repairedBrowserUrl ?: it.sourceBrowserUrl,
                        loadingKinds = false,
                    )
                }
            }
        }
    }

    fun refreshExploreKinds(source: BookSourcePart) {
        viewModelScope.launch(IO) {
            source.clearExploreKindsCache()
            if (_uiState.value.expandedId == source.bookSourceUrl) {
                loadExploreKinds(source)
            }
        }
    }

    fun topSource(bookSource: BookSourcePart) {
        execute {
            exploreRepository.topSource(bookSource)
        }
    }

    fun refreshExploreKinds(sourceUrl: String) {
        val source = _uiState.value.items.firstOrNull { it.bookSourceUrl == sourceUrl } ?: return
        refreshExploreKinds(source)
    }

    fun updateKindValue(sourceUrl: String, kind: ExploreKind, value: String) {
        _uiState.update { state ->
            state.copy(kindValues = (state.kindValues + (kind.title to value)).toImmutableMap())
        }
        viewModelScope.launch(IO) {
            getExploreInfoMap(sourceUrl).apply {
                this[kind.title] = value
                saveNow()
            }
        }
    }

    fun requestKindAction(sourceUrl: String, kind: ExploreKind) {
        _effects.tryEmit(ExploreEffect.ExecuteKindAction(sourceUrl, kind))
    }

    fun markBrowserRecovery(sourceUrl: String) {
        _uiState.update { it.copy(pendingBrowserRetrySourceUrl = sourceUrl) }
    }

    fun consumeBrowserRecovery(): String? {
        val sourceUrl = _uiState.value.pendingBrowserRetrySourceUrl ?: return null
        _uiState.update { it.copy(pendingBrowserRetrySourceUrl = null) }
        return sourceUrl
    }

    fun deleteSource(source: BookSourcePart) {
        execute {
            exploreRepository.deleteSource(source.bookSourceUrl)
        }
    }

    @Stable
    data class ExploreUiState(
        override val items: ImmutableList<BookSourcePart> = persistentListOf(),
        override val selectedIds: ImmutableSet<String> = persistentSetOf(),
        override val searchKey: String = "",
        override val isSearch: Boolean = false,
        override val isLoading: Boolean = false,
        val groups: ImmutableList<String> = persistentListOf(),
        val selectedGroup: String = "",
        val expandedId: String? = null,
        val exploreKinds: ImmutableList<ExploreKind> = persistentListOf(),
        val kindDisplayNames: ImmutableMap<String, String> = persistentMapOf(),
        val sourceDisplayNames: ImmutableMap<String, String> = persistentMapOf(),
        val kindValues: ImmutableMap<String, String> = persistentMapOf(),
        val sourceBrowserUrl: String? = null,
        val pendingBrowserRetrySourceUrl: String? = null,
        val loadingKinds: Boolean = false
    ) : ListUiState<BookSourcePart>

    fun buildExploreListItems(state: ExploreUiState): ImmutableList<ExploreListItem> {
        if (state.items.isEmpty()) return persistentListOf()
        val expandedId = state.expandedId
        val kindRows = if (expandedId != null) {
            calculateExploreKindRows(state.exploreKinds, 6)
        } else {
            emptyList()
        }
        return buildList {
            state.items.forEach { source ->
                add(ExploreListItem.Header(source))
                if (source.bookSourceUrl == expandedId) {
                    kindRows.forEachIndexed { index, row ->
                        add(
                            ExploreListItem.KindRow(
                                sourceUrl = source.bookSourceUrl,
                                rowIndex = index,
                                rowItems = row.toImmutableList()
                            )
                        )
                    }
                }
            }
        }.toImmutableList()
    }

    private fun buildKindValues(
        kinds: List<ExploreKind>,
        sourceUrl: String
    ): Map<String, String> {
        val infoMap = getExploreInfoMap(sourceUrl)
        var shouldSave = false
        val values = HashMap<String, String>()
        kinds.forEach { kind ->
            when (kind.type) {
                ExploreKind.Type.text -> {
                    values[kind.title] = infoMap[kind.title].orEmpty()
                }

                ExploreKind.Type.toggle,
                ExploreKind.Type.select -> {
                    val chars = kind.chars
                        ?.filterNotNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: listOf("chars", "is null")
                    val value = infoMap[kind.title]
                        ?.takeUnless { it.isEmpty() }
                        ?: (kind.default ?: chars.first()).also {
                            infoMap[kind.title] = it
                            shouldSave = true
                        }
                    values[kind.title] = value
                }
            }
        }
        if (shouldSave) {
            infoMap.saveNow()
        }
        return values
    }

    private fun translateSourceNames(sources: List<BookSourcePart>) {
        if (!io.legado.app.ui.config.translation.TranslationConfig.dynamicUiTranslationEnabled) {
            _uiState.update { it.copy(sourceDisplayNames = persistentMapOf()) }
            return
        }
        sourceNamesJob?.cancel()
        sourceNamesJob = viewModelScope.launch(IO) {
            val originals = sources.map(BookSourcePart::bookSourceName)
            val translatedLines = translateDynamicUiTextUseCase.executeLines(
                scopeKey = "explore:sources:${sources.joinToString("|") { it.bookSourceUrl }}",
                originalLines = originals,
                contextText = originals.joinToString("\n"),
            ).getOrElse { originals }
            val translated = sources.mapIndexed { index, source ->
                source.bookSourceUrl to translatedLines.getOrElse(index) { source.bookSourceName }
            }.toMap()
            _uiState.update { it.copy(sourceDisplayNames = translated.toImmutableMap()) }
        }
    }

    private suspend fun translateSourceLabels(
        sourceUrl: String,
        labels: Map<String, String>,
    ): Map<String, String> {
        if (!io.legado.app.ui.config.translation.TranslationConfig.dynamicUiTranslationEnabled) {
            return labels
        }
        val contextText = labels.values.joinToString("\n")
        val syntheticBook = Book(bookUrl = "source-ui:$sourceUrl", origin = sourceUrl)
        val entries = labels.entries.toList()
        val originals = entries.map(Map.Entry<String, String>::value)
        val translated = translateDynamicUiTextUseCase.executeLines(
            scopeKey = "source:$sourceUrl:kinds",
            originalLines = originals,
            book = syntheticBook,
            contextText = contextText,
        ).getOrElse { originals }
        return entries.mapIndexed { index, entry ->
            entry.key to translated.getOrElse(index) { entry.value }
        }.toMap()
    }

}

sealed interface ExploreListItem {
    val key: String

    data class Header(val source: BookSourcePart) : ExploreListItem {
        override val key: String = source.bookSourceUrl
    }

    data class KindRow(
        val sourceUrl: String,
        val rowIndex: Int,
        val rowItems: ImmutableList<Pair<ExploreKind, Int>>
    ) : ExploreListItem {
        override val key: String = "${sourceUrl}_$rowIndex"
    }
}

sealed interface ExploreEffect {
    data class ExecuteKindAction(
        val sourceUrl: String,
        val kind: ExploreKind
    ) : ExploreEffect
}
