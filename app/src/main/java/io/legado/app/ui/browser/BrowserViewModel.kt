package io.legado.app.ui.browser

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drducbook.app.R
import io.legado.app.constant.FeatureFlags
import io.legado.app.data.repository.BrowserBookmarkRepository
import io.legado.app.data.repository.BookSourceHealthRepository
import io.legado.app.domain.model.BrowserBookmark
import io.legado.app.domain.model.BookSourceHealthRow
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BrowserPageTextNode
import io.legado.app.domain.model.SourceDomainIndex
import io.legado.app.domain.model.SourceBookmarkPreference
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.usecase.ResolveBrowserSourceContextUseCase
import io.legado.app.domain.usecase.TranslateBrowserPageUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserViewModel(
    private val application: Application,
    private val tabStore: BrowserTabStore,
    private val translateBrowserPageUseCase: TranslateBrowserPageUseCase,
    private val resolveBrowserSourceContextUseCase: ResolveBrowserSourceContextUseCase,
    private val bookSourceHealthRepository: BookSourceHealthRepository,
    private val browserBookmarkRepository: BrowserBookmarkRepository,
) : ViewModel() {

    private val restoredSession = tabStore.restore()
    private val restoredActiveTab = restoredSession.tabs
        .firstOrNull { tab -> tab.id == restoredSession.activeTabId }
    private val _uiState = MutableStateFlow(
        BrowserUiState(
            tabs = restoredSession.tabs.toImmutableList(),
            activeTabId = restoredSession.activeTabId,
            addressBarText = if (restoredActiveTab?.isHome == true) {
                ""
            } else {
                restoredActiveTab?.url.orEmpty()
            },
            isHomeMode = restoredActiveTab?.isHome == true,
            loadGeneration = if (restoredActiveTab?.isHome == true) 0 else 1,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BrowserEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var initialized = false
    private var translationJob: Job? = null
    private var mutationJob: Job? = null
    private var sourceIndex = SourceDomainIndex.empty()
    private var sourceHealthRows: List<BookSourceHealthRow> = emptyList()
    private var browserBookmarks: List<BrowserBookmark> = emptyList()
    private var sourceBookmarkPreferences: Map<SourceKey, SourceBookmarkPreference> = emptyMap()

    init {
        viewModelScope.launch {
            resolveBrowserSourceContextUseCase.index.collect { index ->
                sourceIndex = index
                reconcileSourceContexts()
                rebuildHomeData()
            }
        }
        viewModelScope.launch {
            bookSourceHealthRepository.observeRows().collect { rows ->
                sourceHealthRows = rows
                rebuildHomeData()
            }
        }
        viewModelScope.launch {
            browserBookmarkRepository.observeBookmarks().collect { bookmarks ->
                browserBookmarks = bookmarks
                rebuildHomeData()
            }
        }
        viewModelScope.launch {
            browserBookmarkRepository.observeSourcePreferences().collect { preferences ->
                sourceBookmarkPreferences = preferences.associateBy { preference -> preference.sourceKey }
                refreshActiveSourcePreference()
                rebuildHomeData()
            }
        }
    }

    fun onIntent(intent: BrowserIntent) {
        when (intent) {
            is BrowserIntent.Initialize -> initialize(intent.initialUrl, intent.sourceProbeUrl)
            is BrowserIntent.ChangeAddress -> _uiState.update { it.copy(addressBarText = intent.value) }
            BrowserIntent.NavigateAddress -> navigateAddress()
            is BrowserIntent.OpenShortcut -> navigateTo(intent.url)
            is BrowserIntent.ChangeHomeQuery -> changeHomeQuery(intent.value)
            BrowserIntent.ShowAddBookmark -> showAddBookmark()
            is BrowserIntent.EditBookmark -> showEditBookmark(intent.id)
            BrowserIntent.DismissBookmarkEditor -> _uiState.update { it.copy(bookmarkEditor = null) }
            is BrowserIntent.SaveBookmark -> saveBookmark(intent)
            is BrowserIntent.DeleteBookmark -> deleteBookmark(intent.id)
            is BrowserIntent.ToggleSourcePinned -> toggleSourcePinned(intent.sourceKey)
            is BrowserIntent.ToggleSourceHidden -> toggleSourceHidden(intent.sourceKey)
            BrowserIntent.ToggleActiveSourcePinned -> {
                _uiState.value.activeSourceContext?.key?.let(::toggleSourcePinned)
            }
            BrowserIntent.ToggleActiveSourceHidden -> {
                _uiState.value.activeSourceContext?.key?.let(::toggleSourceHidden)
            }
            BrowserIntent.GoBack -> {
                if (_uiState.value.isHomeMode) exitHomeMode() else _effects.tryEmit(BrowserEffect.GoBack)
            }
            BrowserIntent.GoForward -> if (!_uiState.value.isHomeMode) {
                _effects.tryEmit(BrowserEffect.GoForward)
            }
            BrowserIntent.ReloadOrStop -> if (!_uiState.value.isHomeMode) {
                _effects.tryEmit(
                    if (_uiState.value.isLoading) BrowserEffect.Stop else BrowserEffect.Reload
                )
            }
            BrowserIntent.GoHome -> showHomeMode()
            BrowserIntent.ExitHome -> exitHomeMode()
            BrowserIntent.AddTab -> addTab()
            BrowserIntent.ShowTabs -> _uiState.update { it.copy(showTabs = true, showMenu = false) }
            BrowserIntent.ShowMenu -> _uiState.update { it.copy(showMenu = true, showTabs = false) }
            BrowserIntent.DismissOverlays -> _uiState.update {
                it.copy(showTabs = false, showMenu = false)
            }
            is BrowserIntent.SwitchTab -> switchTab(intent.tabId)
            is BrowserIntent.CloseTab -> closeTab(intent.tabId)
            BrowserIntent.ToggleDesktopMode -> toggleDesktopMode()
            BrowserIntent.TogglePageTranslation -> togglePageTranslation()
            BrowserIntent.OpenExternal -> activeTab()?.url?.takeIf(::isSafeBrowserUrl)?.let {
                _uiState.update { state -> state.copy(showMenu = false) }
                _effects.tryEmit(BrowserEffect.OpenExternal(it))
            }
            BrowserIntent.SharePage -> activeTab()?.takeIf { tab -> isSafeBrowserUrl(tab.url) }?.let {
                _uiState.update { state -> state.copy(showMenu = false) }
                _effects.tryEmit(BrowserEffect.SharePage(it.url, it.title))
            }
            BrowserIntent.CopyLink -> activeTab()?.url?.takeIf(::isSafeBrowserUrl)?.let {
                _uiState.update { state -> state.copy(showMenu = false) }
                _effects.tryEmit(BrowserEffect.CopyLink(it))
            }
            BrowserIntent.OpenSourceHealth -> openSourceHealth()
            BrowserIntent.OpenSourceLogin -> openSourceLogin()
            BrowserIntent.OpenSourceEdit -> openSourceEdit()
            BrowserIntent.ClearSourceCookie -> clearSourceCookie()
            BrowserIntent.ExitBrowser -> _effects.tryEmit(BrowserEffect.ExitBrowser)
            BrowserIntent.ConfirmLoginAndProbe -> confirmLoginAndProbe()
            is BrowserIntent.PageStarted -> onPageStarted(intent.url)
            is BrowserIntent.PageProgress -> _uiState.update {
                it.copy(progress = intent.progress.coerceIn(0, 100))
            }
            is BrowserIntent.PageFinished -> onPageFinished(intent)
            is BrowserIntent.PageError -> _uiState.update {
                it.copy(isLoading = false, errorMessage = intent.message)
            }
            is BrowserIntent.PageSnapshotReady -> translateSnapshot(intent.nodes)
            BrowserIntent.PageMutationDetected -> scheduleMutationTranslation()
        }
    }

    private fun initialize(initialUrl: String?, sourceProbeUrl: String?) {
        if (initialized) return
        initialized = true
        val normalizedInitial = initialUrl
            ?.takeIf(String::isNotBlank)
            ?.let(::normalizeBrowserInput)
        _uiState.update { state ->
            if (normalizedInitial == null) {
                state.copy(
                    initialized = true,
                    sourceProbeUrl = sourceProbeUrl,
                    addressBarText = "",
                    isHomeMode = true,
                    isLoading = false,
                    progress = 0,
                    errorMessage = null,
                )
            } else {
                val existing = state.tabs.firstOrNull { tab -> tab.url == normalizedInitial }
                if (existing != null) {
                    state.copy(
                        initialized = true,
                        activeTabId = existing.id,
                        addressBarText = existing.url,
                        isHomeMode = false,
                        sourceProbeUrl = sourceProbeUrl,
                        loadGeneration = state.loadGeneration + 1,
                    )
                } else {
                    val newTab = tabStore.newTab(normalizedInitial)
                    state.copy(
                        initialized = true,
                        tabs = (state.tabs + newTab).takeLast(MAX_TABS).toImmutableList(),
                        activeTabId = newTab.id,
                        addressBarText = newTab.url,
                        isHomeMode = false,
                        sourceProbeUrl = sourceProbeUrl,
                        loadGeneration = state.loadGeneration + 1,
                    )
                }
            }
        }
        persist()
    }

    private fun navigateAddress() {
        if (_uiState.value.addressBarText.isBlank()) {
            showHomeMode()
            return
        }
        val url = normalizeBrowserInput(_uiState.value.addressBarText)
        navigateTo(url)
    }

    private fun navigateTo(url: String) {
        if (url.isBlank()) {
            showHomeMode()
            return
        }
        updateActiveTab { tab -> tab.copy(url = url, title = url, progress = 0, isHome = false) }
        _uiState.update {
            it.copy(
                addressBarText = url,
                isHomeMode = false,
                isLoading = true,
                progress = 0,
                errorMessage = null,
                translationState = BrowserPageTranslationState.ORIGINAL,
                showTabs = false,
                loadGeneration = it.loadGeneration + 1,
            )
        }
        resolveActiveSourceContext(url)
        persist()
    }

    private fun changeHomeQuery(value: String) {
        _uiState.update { state -> state.copy(home = state.home.copy(query = value)) }
        rebuildHomeData()
    }

    private fun showAddBookmark() {
        val tab = activeTab()?.takeIf { tab -> isSafeBrowserUrl(tab.url) }
        if (tab == null) {
            _effects.tryEmit(BrowserEffect.ShowMessage(application.getString(R.string.browser_invalid_url)))
            return
        }
        val existing = browserBookmarks.firstOrNull { bookmark -> bookmark.url == tab.url }
        _uiState.update {
            it.copy(
                showMenu = false,
                bookmarkEditor = BrowserBookmarkEditorUi(
                    id = existing?.id,
                    title = existing?.title ?: tab.title.ifBlank { tab.url },
                    url = tab.url,
                    folder = existing?.folder ?: BrowserBookmark.DEFAULT_FOLDER,
                ),
            )
        }
    }

    private fun showEditBookmark(id: String) {
        val bookmark = browserBookmarks.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                bookmarkEditor = BrowserBookmarkEditorUi(
                    id = bookmark.id,
                    title = bookmark.title,
                    url = bookmark.url,
                    folder = bookmark.folder,
                )
            )
        }
    }

    private fun saveBookmark(intent: BrowserIntent.SaveBookmark) {
        val url = intent.url.trim()
        if (!isSafeBrowserUrl(url)) {
            _effects.tryEmit(BrowserEffect.ShowMessage(application.getString(R.string.browser_invalid_url)))
            return
        }
        _uiState.update { it.copy(bookmarkEditor = null, showMenu = false) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                browserBookmarkRepository.saveBookmark(
                    id = intent.id,
                    title = intent.title,
                    url = url,
                    folder = intent.folder,
                )
            }.onSuccess {
                _effects.emit(BrowserEffect.ShowMessage(application.getString(R.string.browser_bookmark_saved)))
            }.onFailure { error ->
                _effects.emit(
                    BrowserEffect.ShowMessage(
                        error.localizedMessage ?: application.getString(R.string.browser_bookmark_save_failed)
                    )
                )
            }
        }
    }

    private fun deleteBookmark(id: String) {
        _uiState.update { it.copy(bookmarkEditor = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                browserBookmarkRepository.deleteBookmark(id)
            }.onSuccess {
                _effects.emit(BrowserEffect.ShowMessage(application.getString(R.string.browser_bookmark_deleted)))
            }.onFailure { error ->
                _effects.emit(
                    BrowserEffect.ShowMessage(
                        error.localizedMessage ?: application.getString(R.string.browser_bookmark_delete_failed)
                    )
                )
            }
        }
    }

    private fun toggleSourcePinned(sourceKey: SourceKey) {
        val nextPinned = sourceBookmarkPreferences[sourceKey]?.pinned != true
        _uiState.update { it.copy(showMenu = false) }
        viewModelScope.launch(Dispatchers.IO) {
            browserBookmarkRepository.setSourcePinned(sourceKey, nextPinned)
        }
    }

    private fun toggleSourceHidden(sourceKey: SourceKey) {
        val nextHidden = sourceBookmarkPreferences[sourceKey]?.hidden != true
        _uiState.update { it.copy(showMenu = false) }
        viewModelScope.launch(Dispatchers.IO) {
            browserBookmarkRepository.setSourceHidden(sourceKey, nextHidden)
        }
    }

    private fun showHomeMode() {
        _uiState.update {
            it.copy(
                addressBarText = "",
                isHomeMode = true,
                showTabs = false,
                showMenu = false,
                errorMessage = null,
            )
        }
    }

    private fun exitHomeMode() {
        val tab = activeTab() ?: return
        if (tab.isHome || tab.url.isBlank()) return
        _uiState.update {
            it.copy(
                addressBarText = tab.url,
                isHomeMode = false,
                showTabs = false,
                showMenu = false,
                errorMessage = null,
            )
        }
    }

    private fun addTab() {
        val tab = tabStore.newTab()
        _uiState.update {
            it.copy(
                tabs = (it.tabs + tab).takeLast(MAX_TABS).toImmutableList(),
                activeTabId = tab.id,
                addressBarText = "",
                isHomeMode = true,
                isLoading = false,
                progress = 0,
                canGoBack = false,
                canGoForward = false,
                errorMessage = null,
                translationState = BrowserPageTranslationState.ORIGINAL,
                showTabs = false,
            )
        }
        resolveActiveSourceContext(null)
        persist()
    }

    private fun switchTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        if (tabId == _uiState.value.activeTabId) return
        translationJob?.cancel()
        val isHomeTab = tab.isHome || tab.url.isBlank()
        _uiState.update {
            it.copy(
                activeTabId = tabId,
                addressBarText = if (isHomeTab) "" else tab.url,
                isHomeMode = isHomeTab,
                isLoading = !isHomeTab,
                progress = tab.progress,
                canGoBack = false,
                canGoForward = false,
                errorMessage = null,
                translationState = BrowserPageTranslationState.ORIGINAL,
                showTabs = false,
                loadGeneration = if (isHomeTab) it.loadGeneration else it.loadGeneration + 1,
            )
        }
        resolveActiveSourceContext(tab.url.takeUnless { isHomeTab })
        persist()
    }

    private fun closeTab(tabId: String) {
        val state = _uiState.value
        val index = state.tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val remaining = state.tabs.filterNot { it.id == tabId }.toMutableList()
        if (remaining.isEmpty()) remaining += tabStore.newTab()
        val active = if (state.activeTabId == tabId) {
            remaining[index.coerceAtMost(remaining.lastIndex)]
        } else {
            remaining.firstOrNull { it.id == state.activeTabId } ?: remaining.first()
        }
        val isHomeTab = active.isHome || active.url.isBlank()
        _uiState.update {
            it.copy(
                tabs = remaining.toImmutableList(),
                activeTabId = active.id,
                addressBarText = if (isHomeTab) "" else active.url,
                isHomeMode = isHomeTab,
                isLoading = !isHomeTab,
                canGoBack = false,
                canGoForward = false,
                translationState = BrowserPageTranslationState.ORIGINAL,
                loadGeneration = if (isHomeTab) it.loadGeneration else it.loadGeneration + 1,
            )
        }
        resolveActiveSourceContext(active.url.takeUnless { isHomeTab })
        persist()
    }

    private fun toggleDesktopMode() {
        _uiState.update { it.copy(isDesktopMode = !it.isDesktopMode, showMenu = false) }
        _effects.tryEmit(BrowserEffect.SetDesktopMode(_uiState.value.isDesktopMode))
    }

    private fun togglePageTranslation() {
        if (!FeatureFlags.browserPageTranslation) {
            _effects.tryEmit(BrowserEffect.ShowMessage(application.getString(R.string.feature_disabled_in_lab)))
            return
        }
        when (_uiState.value.translationState) {
            BrowserPageTranslationState.ORIGINAL -> {
                _uiState.update { it.copy(translationState = BrowserPageTranslationState.TRANSLATING) }
                _effects.tryEmit(BrowserEffect.RequestPageSnapshot)
            }
            BrowserPageTranslationState.TRANSLATING -> translationJob?.cancel()
            BrowserPageTranslationState.TRANSLATED -> {
                translationJob?.cancel()
                _effects.tryEmit(BrowserEffect.RestoreOriginalPage)
                _uiState.update { it.copy(translationState = BrowserPageTranslationState.ORIGINAL) }
            }
        }
    }

    private fun translateSnapshot(nodes: List<BrowserPageTextNode>) {
        if (!FeatureFlags.browserPageTranslation) return
        val mutationRefresh = _uiState.value.translationState == BrowserPageTranslationState.TRANSLATED
        if (nodes.isEmpty()) {
            if (!mutationRefresh) {
                _uiState.update { it.copy(translationState = BrowserPageTranslationState.ORIGINAL) }
                _effects.tryEmit(
                    BrowserEffect.ShowMessage(application.getString(R.string.browser_no_translatable_text))
                )
            }
            return
        }
        translationJob?.cancel()
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = translateBrowserPageUseCase.execute(nodes)
                if (result.translations.isNotEmpty()) {
                    _effects.emit(BrowserEffect.ApplyPageTranslations(result.translations))
                    _uiState.update { it.copy(translationState = BrowserPageTranslationState.TRANSLATED) }
                } else if (!mutationRefresh) {
                    _uiState.update { it.copy(translationState = BrowserPageTranslationState.ORIGINAL) }
                }
                if (result.failedCount > 0) {
                    _effects.emit(
                        BrowserEffect.ShowMessage(
                            application.getString(
                                R.string.browser_translation_partial,
                                result.failedCount,
                            )
                        )
                    )
                }
            } catch (error: CancellationException) {
                if (!mutationRefresh) {
                    _uiState.update { it.copy(translationState = BrowserPageTranslationState.ORIGINAL) }
                }
            } catch (error: Throwable) {
                if (!mutationRefresh) {
                    _uiState.update { it.copy(translationState = BrowserPageTranslationState.ORIGINAL) }
                }
                _effects.emit(
                    BrowserEffect.ShowMessage(
                        error.localizedMessage
                            ?: application.getString(R.string.browser_translation_failed)
                    )
                )
            }
        }
    }

    private fun scheduleMutationTranslation() {
        if (_uiState.value.translationState != BrowserPageTranslationState.TRANSLATED) return
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            delay(MUTATION_DEBOUNCE_MS)
            _effects.emit(BrowserEffect.RequestPageSnapshot)
        }
    }

    private fun onPageStarted(url: String) {
        translationJob?.cancel()
        mutationJob?.cancel()
        updateActiveTab { tab -> tab.copy(url = url, progress = 0, isHome = false) }
        _uiState.update {
            it.copy(
                addressBarText = if (it.isHomeMode) it.addressBarText else url,
                isLoading = true,
                progress = 0,
                errorMessage = null,
                translationState = BrowserPageTranslationState.ORIGINAL,
            )
        }
        resolveActiveSourceContext(url)
    }

    private fun onPageFinished(intent: BrowserIntent.PageFinished) {
        updateActiveTab { tab ->
            tab.copy(
                url = intent.url,
                title = intent.title.ifBlank { intent.url },
                progress = 100,
                isHome = false,
            )
        }
        _uiState.update {
            it.copy(
                addressBarText = if (it.isHomeMode) it.addressBarText else intent.url,
                isLoading = false,
                progress = 100,
                canGoBack = intent.canGoBack,
                canGoForward = intent.canGoForward,
            )
        }
        resolveActiveSourceContext(intent.url)
        persist()
    }

    private fun reconcileSourceContexts() {
        _uiState.update { state ->
            val tabs = state.tabs.map { tab ->
                val context = sourceIndex.match(
                    url = tab.url,
                    preferredKey = tab.sourceKey,
                    preferredSourceId = state.sourceProbeUrl,
                )
                tab.copy(sourceKey = context?.key)
            }.toImmutableList()
            val active = tabs.firstOrNull { it.id == state.activeTabId }
            state.copy(
                tabs = tabs,
                activeSourceContext = active?.let { tab ->
                    sourceIndex.match(
                        url = tab.url,
                        preferredKey = tab.sourceKey,
                        preferredSourceId = state.sourceProbeUrl,
                    )
                },
                activeSourcePreference = active?.sourceKey.toSourcePreferenceUi(),
            )
        }
        persist()
    }

    private fun rebuildHomeData() {
        val query = _uiState.value.home.query.trim()
        val bookmarks = browserBookmarks
            .asSequence()
            .filter { bookmark -> bookmark.matchesBrowserQuery(query) }
            .sortedWith(
                compareBy<BrowserBookmark> { it.folder.lowercase() }
                    .thenBy { it.sortOrder }
                    .thenBy { it.title.lowercase() }
            )
            .map { bookmark ->
                BrowserBookmarkUi(
                    id = bookmark.id,
                    title = bookmark.title,
                    url = bookmark.url,
                    folder = bookmark.folder,
                    sortOrder = bookmark.sortOrder,
                )
            }
            .toList()
            .toImmutableList()
        val shortcuts = buildBrowserSourceShortcuts(
            entries = sourceIndex.entries,
            healthRows = sourceHealthRows,
            preferences = sourceBookmarkPreferences,
            query = query,
            maxItems = MAX_HOME_SOURCE_SHORTCUTS,
        ).toImmutableList()
        _uiState.update { state ->
            state.copy(
                home = BrowserHomeUiState(
                    query = state.home.query,
                    manualBookmarks = bookmarks,
                    sourceShortcuts = shortcuts,
                    healthSummary = sourceHealthRows.toBrowserHealthSummary(),
                )
            )
        }
    }

    private fun resolveActiveSourceContext(url: String?) {
        _uiState.update { state ->
            val current = state.tabs.firstOrNull { it.id == state.activeTabId }
            val context = url?.let {
                sourceIndex.match(
                    url = it,
                    preferredKey = current?.sourceKey,
                    preferredSourceId = state.sourceProbeUrl,
                )
            }
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.activeTabId) {
                        tab.copy(sourceKey = context?.key)
                    } else {
                        tab
                    }
                }.toImmutableList(),
                activeSourceContext = context,
                activeSourcePreference = context?.key.toSourcePreferenceUi(),
            )
        }
    }

    private fun refreshActiveSourcePreference() {
        _uiState.update { state ->
            state.copy(activeSourcePreference = state.activeSourceContext?.key.toSourcePreferenceUi())
        }
    }

    private fun confirmLoginAndProbe() {
        val state = _uiState.value
        val sourceUrl = state.sourceProbeUrl ?: state.activeSourceContext?.sourceUrl ?: return
        val pageUrl = activeTab()?.url?.takeIf(::isSafeBrowserUrl) ?: return
        _uiState.update { it.copy(showMenu = false) }
        _effects.tryEmit(BrowserEffect.SyncLoginAndProbe(pageUrl, sourceUrl))
    }

    private fun openSourceHealth() {
        val sourceUrl = _uiState.value.activeSourceContext?.sourceUrl
        _uiState.update { it.copy(showMenu = false) }
        _effects.tryEmit(BrowserEffect.OpenSourceHealth(sourceUrl))
    }

    private fun openSourceLogin() {
        val sourceContext = _uiState.value.activeSourceContext ?: return
        if (sourceContext.loginUrl.isNullOrBlank()) {
            _effects.tryEmit(
                BrowserEffect.ShowMessage(application.getString(R.string.source_missing_login))
            )
            return
        }
        _uiState.update { it.copy(showMenu = false) }
        _effects.tryEmit(BrowserEffect.OpenSourceLogin(sourceContext))
    }

    private fun openSourceEdit() {
        val sourceContext = _uiState.value.activeSourceContext ?: return
        _uiState.update { it.copy(showMenu = false) }
        _effects.tryEmit(BrowserEffect.OpenSourceEdit(sourceContext))
    }

    private fun clearSourceCookie() {
        val sourceContext = _uiState.value.activeSourceContext ?: return
        _uiState.update { it.copy(showMenu = false) }
        _effects.tryEmit(BrowserEffect.ClearSourceCookie(sourceContext))
    }

    private fun updateActiveTab(transform: (BrowserTabUi) -> BrowserTabUi) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.activeTabId) transform(tab) else tab
                }.toImmutableList()
            )
        }
    }

    private fun activeTab(): BrowserTabUi? = _uiState.value.tabs
        .firstOrNull { it.id == _uiState.value.activeTabId }

    private fun persist() {
        val state = _uiState.value
        tabStore.save(state.tabs, state.activeTabId)
    }

    private fun SourceKey?.toSourcePreferenceUi(): BrowserSourcePreferenceUi {
        val preference = this?.let(sourceBookmarkPreferences::get)
        return BrowserSourcePreferenceUi(
            pinned = preference?.pinned == true,
            hidden = preference?.hidden == true,
        )
    }

    private companion object {
        const val MAX_TABS = 12
        const val MAX_HOME_SOURCE_SHORTCUTS = 12
        const val MUTATION_DEBOUNCE_MS = 900L
    }
}

private fun List<BookSourceHealthRow>.toBrowserHealthSummary(): BrowserSourceHealthSummaryUi {
    val statuses = map { row -> row.health?.statusValue ?: BookSourceHealthStatus.UNKNOWN_OFFLINE }
    return BrowserSourceHealthSummaryUi(
        total = size,
        healthy = statuses.count { it == BookSourceHealthStatus.HEALTHY },
        needsAttention = statuses.count {
            it !in setOf(
                BookSourceHealthStatus.HEALTHY,
                BookSourceHealthStatus.DEGRADED,
                BookSourceHealthStatus.UNKNOWN_OFFLINE,
                BookSourceHealthStatus.STALE,
            )
        },
        authRequired = statuses.count { it == BookSourceHealthStatus.AUTH_REQUIRED },
        captchaRequired = statuses.count { it == BookSourceHealthStatus.CAPTCHA_REQUIRED },
    )
}

private fun BrowserBookmark.matchesBrowserQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        url.contains(query, ignoreCase = true) ||
        folder.contains(query, ignoreCase = true)
}
