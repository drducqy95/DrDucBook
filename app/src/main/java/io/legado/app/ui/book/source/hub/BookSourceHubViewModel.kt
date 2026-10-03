package io.legado.app.ui.book.source.hub

import android.app.Application
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.repository.online_source.OnlineBookSourceRepository
import io.legado.app.domain.model.OnlineBookSourceItem
import io.legado.app.domain.model.OnlineSourceCollectionItem
import io.legado.app.domain.usecase.TranslateDynamicUiTextUseCase
import io.legado.app.domain.usecase.containsCjk
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookSourceHubViewModel(
    private val application: Application,
    private val repository: OnlineBookSourceRepository,
    private val translateDynamicUiTextUseCase: TranslateDynamicUiTextUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookSourceHubUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookSourceHubEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var searchJob: Job? = null
    private var translateYckceoJob: Job? = null
    private var translateBundlesJob: Job? = null

    private var rawYckceoSources: List<OnlineBookSourceItem> = emptyList()
    private var rawMiaoGongZiBundles: List<OnlineSourceCollectionItem> = emptyList()

    init {
        loadInitialData()
        observeTranslationSetting()
    }

    private fun observeTranslationSetting() {
        viewModelScope.launch {
            snapshotFlow { TranslationConfig.dynamicUiTranslationEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        if (rawYckceoSources.isNotEmpty()) {
                            translateYckceoSources(rawYckceoSources)
                        }
                        if (rawMiaoGongZiBundles.isNotEmpty()) {
                            translateMiaoGongZiBundles(rawMiaoGongZiBundles)
                        }
                    } else {
                        translateYckceoJob?.cancel()
                        translateBundlesJob?.cancel()
                        _uiState.update {
                            it.copy(
                                yckceoSources = rawYckceoSources.toImmutableList(),
                                miaogongziBundles = rawMiaoGongZiBundles.toImmutableList(),
                            )
                        }
                    }
                }
        }
    }

    fun onIntent(intent: BookSourceHubIntent) {
        when (intent) {
            is BookSourceHubIntent.SwitchTab -> {
                _uiState.update { it.copy(currentTab = intent.tab) }
                if (intent.tab == BookSourceHubTab.YCKCEO && _uiState.value.yckceoSources.isEmpty()) {
                    searchYckceo()
                } else if (intent.tab == BookSourceHubTab.MIAOGONGZI && _uiState.value.miaogongziBundles.isEmpty()) {
                    loadMiaoGongZi()
                }
            }
            is BookSourceHubIntent.ChangeSearchQuery -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
                searchJob?.cancel()
                searchJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(500)
                    searchYckceo()
                }
            }
            is BookSourceHubIntent.ChangeCategory -> {
                _uiState.update { it.copy(selectedCategory = intent.category) }
                searchYckceo()
            }
            BookSourceHubIntent.Search -> {
                searchJob?.cancel()
                searchYckceo()
            }
            BookSourceHubIntent.LoadInitialData -> loadInitialData()
            is BookSourceHubIntent.ImportYckceoSource -> importYckceo(intent.item)
            is BookSourceHubIntent.ImportBundle -> importBundle(intent.item)
        }
    }

    private fun loadInitialData() {
        searchYckceo()
        loadMiaoGongZi()
    }

    private fun searchYckceo() {
        searchJob?.cancel()
        val query = _uiState.value.searchQuery
        val category = _uiState.value.selectedCategory
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val results = repository.searchYckceo(query = query, page = 1, category = category)
                rawYckceoSources = results
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        yckceoSources = results.toImmutableList(),
                    )
                }
                if (TranslationConfig.dynamicUiTranslationEnabled) {
                    translateYckceoSources(results)
                }
            } catch (e: Exception) {
                AppLog.put("searchYckceo error", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage,
                    )
                }
            }
        }
    }

    private fun translateYckceoSources(items: List<OnlineBookSourceItem>) {
        if (!TranslationConfig.dynamicUiTranslationEnabled || items.isEmpty()) return
        translateYckceoJob?.cancel()
        translateYckceoJob = viewModelScope.launch(Dispatchers.IO) {
            val names = items.map(OnlineBookSourceItem::name)
            val translatedNames = translateDynamicUiTextUseCase.executeLines(
                scopeKey = "hub:yckceo:names",
                originalLines = names,
                contextText = names.joinToString("\n"),
            ).getOrElse { names }

            val distinctAuthors = items.mapNotNull(OnlineBookSourceItem::author)
                .filter { it.containsCjk() }
                .distinct()
            val authorTranslations = distinctAuthors.associateWith { author ->
                translateDynamicUiTextUseCase.executeAuthorName(
                    scopeKey = "hub:author",
                    originalText = author,
                ).getOrElse { author }
            }

            val distinctTimes = items.mapNotNull(OnlineBookSourceItem::updateTime)
                .filter { it.containsCjk() }
                .distinct()
            val timeTranslations = distinctTimes.associateWith { time ->
                translateDynamicUiTextUseCase.execute(
                    scopeKey = "hub:time",
                    originalText = time,
                ).getOrElse { time }
            }

            val translatedItems = items.mapIndexed { index, item ->
                item.copy(
                    name = translatedNames.getOrElse(index) { item.name },
                    author = item.author?.let { authorTranslations[it] ?: it },
                    updateTime = item.updateTime?.let { timeTranslations[it] ?: it },
                )
            }

            _uiState.update { current ->
                if (current.yckceoSources.map(OnlineBookSourceItem::id) == items.map(OnlineBookSourceItem::id)) {
                    current.copy(yckceoSources = translatedItems.toImmutableList())
                } else {
                    current
                }
            }
        }
    }

    private fun loadMiaoGongZi() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bundles = repository.getMiaoGongZiBundles()
                rawMiaoGongZiBundles = bundles
                _uiState.update {
                    it.copy(miaogongziBundles = bundles.toImmutableList())
                }
                if (TranslationConfig.dynamicUiTranslationEnabled) {
                    translateMiaoGongZiBundles(bundles)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun translateMiaoGongZiBundles(bundles: List<OnlineSourceCollectionItem>) {
        if (!TranslationConfig.dynamicUiTranslationEnabled || bundles.isEmpty()) return
        translateBundlesJob?.cancel()
        translateBundlesJob = viewModelScope.launch(Dispatchers.IO) {
            val titles = bundles.map(OnlineSourceCollectionItem::title)
            val translatedTitles = translateDynamicUiTextUseCase.executeLines(
                scopeKey = "hub:miaogongzi:titles",
                originalLines = titles,
                contextText = titles.joinToString("\n"),
            ).getOrElse { titles }

            val descriptions = bundles.map(OnlineSourceCollectionItem::description)
            val translatedDescriptions = translateDynamicUiTextUseCase.executeLines(
                scopeKey = "hub:miaogongzi:descs",
                originalLines = descriptions,
                contextText = descriptions.joinToString("\n"),
            ).getOrElse { descriptions }

            val distinctAuthors = bundles.map(OnlineSourceCollectionItem::author)
                .filter { it.containsCjk() }
                .distinct()
            val authorTranslations = distinctAuthors.associateWith { author ->
                translateDynamicUiTextUseCase.executeAuthorName(
                    scopeKey = "hub:author",
                    originalText = author,
                ).getOrElse { author }
            }

            val translatedBundles = bundles.mapIndexed { index, bundle ->
                bundle.copy(
                    title = translatedTitles.getOrElse(index) { bundle.title },
                    description = translatedDescriptions.getOrElse(index) { bundle.description },
                    author = authorTranslations[bundle.author] ?: bundle.author,
                )
            }

            _uiState.update { current ->
                if (current.miaogongziBundles.map(OnlineSourceCollectionItem::downloadUrl) == bundles.map(OnlineSourceCollectionItem::downloadUrl)) {
                    current.copy(miaogongziBundles = translatedBundles.toImmutableList())
                } else {
                    current
                }
            }
        }
    }

    private fun importYckceo(item: OnlineBookSourceItem) {
        val id = item.id
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(importingIds = (it.importingIds + id).toImmutableList()) }
            try {
                val count = repository.importFromUrl(item.downloadUrl)
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_imported_success, count)
                    )
                )
            } catch (e: Exception) {
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_import_failed, e.localizedMessage ?: "Lỗi")
                    )
                )
            } finally {
                _uiState.update { it.copy(importingIds = (it.importingIds - id).toImmutableList()) }
            }
        }
    }

    private fun importBundle(item: OnlineSourceCollectionItem) {
        val id = item.downloadUrl
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(importingIds = (it.importingIds + id).toImmutableList()) }
            try {
                val count = repository.importFromUrl(item.downloadUrl)
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_imported_success, count)
                    )
                )
            } catch (e: Exception) {
                _effects.tryEmit(
                    BookSourceHubEffect.ShowToast(
                        application.getString(R.string.source_hub_import_failed, e.localizedMessage ?: "Lỗi")
                    )
                )
            } finally {
                _uiState.update { it.copy(importingIds = (it.importingIds - id).toImmutableList()) }
            }
        }
    }
}
