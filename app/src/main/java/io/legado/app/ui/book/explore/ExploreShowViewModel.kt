package io.legado.app.ui.book.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.BookShelfKey
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.domain.usecase.TranslateDynamicBookUiUseCase
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.data.entities.Book
import android.content.res.Configuration
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.help.vbook.VbookPluginErrorKind
import io.legado.app.help.vbook.VbookPluginException
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import splitties.init.appCtx

private data class ExploreShowLoadState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEnd: Boolean = false,
    val errorMsg: String? = null,
)

private data class ExploreShowKindState(
    val kinds: List<ExploreKind> = emptyList(),
    val displayNames: Map<String, String> = emptyMap(),
    val selectedKindTitle: String? = null,
)

private data class ExploreShowKindsResult(
    val kinds: List<ExploreKind>,
    val displayNames: Map<String, String>,
)

private data class ExploreShowDisplayState(
    val sourceUrl: String? = null,
    val title: String? = null,
    val layoutState: Int,
    val gridCount: Int,
    val sheet: ExploreShowSheet = ExploreShowSheet.None,
)

class ExploreShowViewModel(
    private val repository: ExploreRepository,
    private val resolveBookShelfStateUseCase: ResolveBookShelfStateUseCase,
    private val exploreBooksUseCase: ExploreBooksUseCase,
    private val saveSearchBooksUseCase: SaveSearchBooksUseCase,
    private val addToBookshelfUseCase: AddToBookshelfUseCase,
    private val localPreferencesRepository: LocalPreferencesRepository,
    private val translateDynamicBookUiUseCase: TranslateDynamicBookUiUseCase,
    private val translateChapterUseCase: TranslateChapterUseCase,
) : ViewModel() {

    private val _rawBooks = MutableStateFlow<List<SearchBook>>(emptyList())
    private val _translatedBooks = MutableStateFlow<Map<String, SearchBook>>(emptyMap())
    private val _bookshelf = MutableStateFlow<Set<BookShelfKey>>(emptySet())
    private val _loadState = MutableStateFlow(ExploreShowLoadState())
    private val _kindState = MutableStateFlow(ExploreShowKindState())
    private val _displayState = MutableStateFlow(
        ExploreShowDisplayState(
            layoutState = 0,
            gridCount = if (appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 7 else 3,
        )
    )

    private var sourceUrl: String? = null
    private var exploreUrl: String? = null
    private var initialExploreUrl: String? = null
    private var initialTitle: String? = null
    private var initialized = false
    private var dynamicTranslationJob: Job? = null
    private var kindLoadJob: Job? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var page = 1
    private var autoPageCount = 0

    companion object {
        private const val MAX_AUTO_PAGES = 3
        private const val AUTO_PAGE_DELAY_MS = 500L
    }

    private val _uiState = MutableStateFlow(
        ExploreShowUiState(
            layoutState = _displayState.value.layoutState,
            gridCount = _displayState.value.gridCount,
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ExploreShowEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        observeBookshelf()
        combineUiState()
        loadLayoutMode()
        loadGridCount()
    }

    fun onIntent(intent: ExploreShowIntent) {
        when (intent) {
            is ExploreShowIntent.InitData -> initData(
                intent.sourceUrl,
                intent.exploreUrl,
                intent.title,
            )
            ExploreShowIntent.LoadMore -> loadMore()
            ExploreShowIntent.ForceLoadNext -> loadMore(forceLoad = true)
            ExploreShowIntent.Refresh -> loadMore(isRefresh = true)
            is ExploreShowIntent.SwitchKind -> switchKind(intent.kind)
            ExploreShowIntent.ToggleLayout -> toggleLayout()
            is ExploreShowIntent.SaveGridCount -> saveGridCount(intent.count)
            is ExploreShowIntent.ShowSheet -> _displayState.update { it.copy(sheet = intent.sheet) }
            ExploreShowIntent.DismissSheet -> _displayState.update { it.copy(sheet = ExploreShowSheet.None) }
            is ExploreShowIntent.OpenBook -> openReader(intent.book)
            is ExploreShowIntent.OpenBookInfo -> emitEffect(
                ExploreShowEffect.OpenBookInfo(
                    name = intent.book.name,
                    author = intent.book.author,
                    bookUrl = intent.book.bookUrl,
                    origin = intent.book.origin,
                    coverPath = intent.book.coverUrl,
                    sharedCoverKey = intent.sharedCoverKey,
                )
            )
            is ExploreShowIntent.SelectBookText -> emitEffect(
                ExploreShowEffect.OpenQuickDictionary(
                    projectKey = intent.book.bookUrl,
                    initialText = listOfNotNull(
                        intent.displayBook.name,
                        intent.displayBook.author,
                        intent.displayBook.originName,
                        intent.displayBook.latestChapterTitle,
                        intent.displayBook.kind,
                        intent.displayBook.intro,
                    ).filter(String::isNotBlank).joinToString("\n"),
                )
            )

            is ExploreShowIntent.AddToShelf -> viewModelScope.launch {
                addToBookshelfUseCase.execute(intent.book)
            }
        }
    }

    private fun openReader(book: SearchBook) {
        viewModelScope.launch {
            val shelfState = resolveBookShelfStateUseCase.execute(
                book.name,
                book.author,
                book.bookUrl,
                _bookshelf.value,
            )
            runCatching {
                if (shelfState != io.legado.app.domain.model.BookShelfState.IN_SHELF) {
                    addToBookshelfUseCase.execute(book)
                }
            }.onSuccess {
                emitEffect(ExploreShowEffect.OpenReader(book))
            }.onFailure { error ->
                emitEffect(ExploreShowEffect.ShowMessage(error.message.orEmpty()))
            }
        }
    }

    private fun observeBookshelf() {
        viewModelScope.launch {
            repository.getBookshelfItems().collect { list ->
                _bookshelf.value = list.map {
                    BookShelfKey(it.name, it.author, it.bookUrl)
                }.toSet()
            }
        }
    }

    private fun combineUiState() {
        viewModelScope.launch {
            combine(
                combine(_rawBooks, _translatedBooks) { rawBooks, translations ->
                    rawBooks to translations
                },
                _bookshelf,
                _loadState,
                _kindState,
                _displayState,
            ) { booksAndTranslations, bookshelf, loadState, kindState, displayState ->
                val (rawBooks, translations) = booksAndTranslations
                val books = rawBooks.map { item ->
                    ExploreBookItemUi(
                        book = item,
                        displayBook = translations[item.bookUrl] ?: item,
                        shelfState = resolveBookShelfStateUseCase.execute(
                            name = item.name,
                            author = item.author,
                            url = item.bookUrl,
                            shelf = bookshelf,
                        )
                    )
                }

                ExploreShowUiState(
                    sourceUrl = displayState.sourceUrl,
                    title = displayState.title,
                    books = books.toImmutableList(),
                    kinds = kindState.kinds.toImmutableList(),
                    kindDisplayNames = kindState.displayNames.toImmutableMap(),
                    selectedKindTitle = kindState.selectedKindTitle?.let { title ->
                        kindState.displayNames[title] ?: title
                    },
                    layoutState = displayState.layoutState,
                    gridCount = displayState.gridCount,
                    isLoading = loadState.isLoading,
                    isRefreshing = loadState.isRefreshing,
                    isEnd = loadState.isEnd,
                    errorMsg = loadState.errorMsg,
                    sheet = displayState.sheet,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun initData(
        incomingSourceUrl: String,
        incomingExploreUrl: String?,
        incomingTitle: String?,
    ) {
        if (initialized && sourceUrl == incomingSourceUrl &&
            initialExploreUrl == incomingExploreUrl && initialTitle == incomingTitle
        ) {
            return
        }
        initialized = true
        kindLoadJob?.cancel()
        cancelActiveLoad()
        sourceUrl = incomingSourceUrl
        initialExploreUrl = incomingExploreUrl
        initialTitle = incomingTitle
        exploreUrl = incomingExploreUrl
        page = 1
        autoPageCount = 0
        _rawBooks.value = emptyList()
        _translatedBooks.value = emptyMap()
        dynamicTranslationJob?.cancel()
        _loadState.value = ExploreShowLoadState()
        _kindState.value = ExploreShowKindState()
        _displayState.update {
            it.copy(
                sourceUrl = incomingSourceUrl,
                title = incomingTitle,
                sheet = ExploreShowSheet.None,
            )
        }

        translateExploreTitle(incomingSourceUrl, incomingTitle)

        if (incomingExploreUrl == null) {
            val generation = loadGeneration
            kindLoadJob = viewModelScope.launch {
                val result = try {
                    loadKinds(incomingSourceUrl)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    ExploreShowKindsResult(emptyList(), emptyMap())
                }
                if (generation != loadGeneration || sourceUrl != incomingSourceUrl) return@launch
                val kinds = result.kinds
                _kindState.update {
                    it.copy(kinds = kinds, displayNames = result.displayNames)
                }
                kinds.firstOrNull { it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() }
                    ?.let { kind ->
                        exploreUrl = kind.url
                        _kindState.update { it.copy(selectedKindTitle = kind.title) }
                    }
                loadMore(isRefresh = true)
            }
        } else {
            loadMore(isRefresh = true)
        }
    }

    private fun translateExploreTitle(sourceUrl: String, title: String?) {
        if (!io.legado.app.ui.config.translation.TranslationConfig.dynamicUiTranslationEnabled ||
            title.isNullOrBlank()
        ) {
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val translated = translateChapterUseCase.executeDynamicUiText(
                scopeKey = "source:$sourceUrl",
                originalText = title,
                book = Book(bookUrl = "source-ui:$sourceUrl", origin = sourceUrl),
                contextText = title,
            ).getOrElse { title }
            if (this@ExploreShowViewModel.sourceUrl == sourceUrl && initialTitle == title) {
                _displayState.update { it.copy(title = translated) }
            }
        }
    }

    private suspend fun loadKinds(sourceUrl: String): ExploreShowKindsResult {
        val kinds = repository.getSourceExploreKinds(sourceUrl)
        val displayNames = if (
            io.legado.app.ui.config.translation.TranslationConfig.dynamicUiTranslationEnabled
        ) {
            val contextText = kinds.joinToString("\n") { it.title }
            val sourceBook = Book(bookUrl = "source-ui:$sourceUrl", origin = sourceUrl)
            kinds.associate { kind ->
                kind.title to translateChapterUseCase.executeDynamicUiText(
                    scopeKey = "source:$sourceUrl",
                    originalText = kind.title,
                    book = sourceBook,
                    contextText = contextText,
                ).getOrElse { kind.title }
            }
        } else {
            emptyMap()
        }
        return ExploreShowKindsResult(kinds, displayNames)
    }

    private fun switchKind(kind: ExploreKind) {
        _kindState.update { it.copy(selectedKindTitle = kind.title) }
        exploreUrl = kind.url
        _loadState.update { it.copy(isEnd = false) }
        autoPageCount = 0
        loadMore(isRefresh = true)
    }

    private fun toggleLayout() {
        _displayState.update {
            val layoutState = if (it.layoutState == 0) 1 else 0
            viewModelScope.launch {
                localPreferencesRepository.updatePreference(LocalPreferencesKeys.EXPLORE_LAYOUT_MODE, layoutState)
            }
            it.copy(layoutState = layoutState)
        }
    }

    private fun loadLayoutMode() {
        viewModelScope.launch {
            val mode = localPreferencesRepository.getPreference(LocalPreferencesKeys.EXPLORE_LAYOUT_MODE, 0).first()
            _displayState.update { it.copy(layoutState = mode) }
        }
    }

    private fun loadGridCount() {
        viewModelScope.launch {
            val isLandscape = appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val key = if (isLandscape) {
                LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_LANDSCAPE
            } else {
                LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_PORTRAIT
            }
            val default = if (isLandscape) 7 else 3
            val count = localPreferencesRepository.getPreference(key, default).first()
            _displayState.update { it.copy(gridCount = count) }
        }
    }

    private fun saveGridCount(count: Int) {
        val isLandscape = appCtx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val key = if (isLandscape) {
            LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_LANDSCAPE
        } else {
            LocalPreferencesKeys.EXPLORE_LAYOUT_GRID_PORTRAIT
        }
        viewModelScope.launch {
            localPreferencesRepository.updatePreference(key, count)
        }
        _displayState.update { it.copy(gridCount = count) }
    }

    private fun loadMore(isRefresh: Boolean = false, forceLoad: Boolean = false) {
        val source = sourceUrl
        val url = exploreUrl
        if (source == null) return
        if (isRefresh) {
            cancelActiveLoad()
            page = 1
            autoPageCount = 0
            _rawBooks.value = emptyList()
        }
        val loadState = _loadState.value
        if ((!isRefresh && (loadJob?.isActive == true || loadState.isLoading)) ||
            (loadState.isEnd && !isRefresh && !forceLoad)
        ) return

        _loadState.update {
            it.copy(
                isLoading = true,
                isRefreshing = isRefresh,
                isEnd = if (isRefresh || forceLoad) false else it.isEnd,
                errorMsg = null,
            )
        }

        val generation = loadGeneration
        loadJob = viewModelScope.launch {
            if (forceLoad) {
                autoPageCount = 0
            }
            fetchPage(source, url, generation)
        }
    }

    private suspend fun fetchPage(sourceUrl: String, url: String?, generation: Long) {
        try {
            val result = exploreBooksUseCase.execute(sourceUrl, url, args = null, page)
            if (!isCurrentLoad(sourceUrl, url, generation)) return
            val currentList = _rawBooks.value
            val existingUrls = currentList.map { it.bookUrl }.toSet()
            val uniqueNewBooks = result.books
                .filter { it.bookUrl !in existingUrls }
                .distinctBy { it.bookUrl }

            if (result.books.isNotEmpty()) {
                saveSearchBooksUseCase.save(result.books)
            }

            if (uniqueNewBooks.isEmpty()) {
                fetchNextAutoPageOrFinish(sourceUrl, url, generation)
            } else {
                _rawBooks.value = currentList + uniqueNewBooks
                translateDynamicBooks(currentList + uniqueNewBooks)
                page++
                autoPageCount = 0
                _loadState.update { it.copy(isEnd = false) }
                finishLoading(generation)
            }
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            if (!isCurrentLoad(sourceUrl, url, generation)) return
            _loadState.update { it.copy(errorMsg = friendlyExploreError(throwable)) }
            finishLoading(generation)
        }
    }

    private fun friendlyExploreError(throwable: Throwable): String {
        val vbookError = generateSequence(throwable) { it.cause }
            .filterIsInstance<VbookPluginException>()
            .firstOrNull()
        return when (vbookError?.kind) {
            VbookPluginErrorKind.AUTH_REQUIRED ->
                "Nguồn yêu cầu đăng nhập lại. Hãy mở trang nguồn, đăng nhập rồi thử lại."

            VbookPluginErrorKind.RATE_LIMITED ->
                "Nguồn đang giới hạn lượt truy cập. Hãy chờ một lúc rồi thử lại."

            VbookPluginErrorKind.NETWORK ->
                "Không thể kết nối tới nguồn. Kiểm tra mạng rồi thử lại."

            VbookPluginErrorKind.INVALID_RESPONSE ->
                "Nguồn trả về dữ liệu không hợp lệ. Hãy thử lại hoặc mở trang nguồn."

            else -> throwable.message?.lineSequence()?.firstOrNull()?.takeIf(String::isNotBlank)
                ?: "Không thể tải dữ liệu khám phá từ nguồn này."
        }
    }

    private suspend fun fetchNextAutoPageOrFinish(
        sourceUrl: String,
        url: String?,
        generation: Long,
    ) {
        if (!isCurrentLoad(sourceUrl, url, generation)) return
        page++
        autoPageCount++
        if (autoPageCount >= MAX_AUTO_PAGES) {
            _loadState.update { it.copy(isEnd = true) }
            finishLoading(generation)
        } else {
            delay(AUTO_PAGE_DELAY_MS)
            fetchPage(sourceUrl, url, generation)
        }
    }

    private fun finishLoading(generation: Long) {
        if (generation != loadGeneration) return
        _loadState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
            )
        }
    }

    private fun cancelActiveLoad() {
        loadGeneration++
        loadJob?.cancel()
        loadJob = null
    }

    private fun isCurrentLoad(source: String, url: String?, generation: Long): Boolean =
        isCurrentExploreLoad(
            requestGeneration = generation,
            activeGeneration = loadGeneration,
            requestSource = source,
            activeSource = sourceUrl,
            requestUrl = url,
            activeUrl = exploreUrl,
        )

    private fun emitEffect(effect: ExploreShowEffect) {
        _effects.tryEmit(effect)
    }

    private fun translateDynamicBooks(books: List<SearchBook>) {
        if (!io.legado.app.ui.config.translation.TranslationConfig.dynamicUiTranslationEnabled) {
            _translatedBooks.value = emptyMap()
            return
        }
        dynamicTranslationJob?.cancel()
        dynamicTranslationJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            books.forEach { book ->
                if (_translatedBooks.value.containsKey(book.bookUrl)) return@forEach
                val displayBook = translateDynamicBookUiUseCase.execute(book)
                _translatedBooks.update { it + (book.bookUrl to displayBook) }
            }
        }
    }
}

internal fun isCurrentExploreLoad(
    requestGeneration: Long,
    activeGeneration: Long,
    requestSource: String,
    activeSource: String?,
    requestUrl: String?,
    activeUrl: String?,
): Boolean = requestGeneration == activeGeneration &&
    requestSource == activeSource &&
    requestUrl == activeUrl
