package io.legado.app.ui.main

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.normalizeTypeFromSource
import io.legado.app.help.media.MediaChapterPolicy
import io.legado.app.domain.model.SourceKeyType
import io.legado.app.model.Download
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.account.AccountRouteScreen
import io.legado.app.ui.ai.agent.AgentDashboardRouteScreen
import io.legado.app.ui.ai.agent.tools.CustomAgentToolManagerRouteScreen
import io.legado.app.ui.ai.chat.AiChatRouteScreen
import io.legado.app.ui.assetdelivery.AssetDeliveryRouteScreen
import io.legado.app.ui.authoring.ebook.EbookEditorRouteScreen
import io.legado.app.ui.authoring.ebook.EbookPreviewRouteScreen
import io.legado.app.ui.authoring.writing.WritingRouteScreen
import io.legado.app.ui.translation.memory.StoryWikiRouteScreen
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.entity.EntityAnalyzerRouteScreen
import io.legado.app.ui.book.explore.ExploreShowIntent
import io.legado.app.ui.book.explore.ExploreShowScreen
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.import.local.ImportBookScreen
import io.legado.app.ui.book.import.remote.RemoteBookScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewScreen
import io.legado.app.ui.book.readRecord.ReadRecordScreen
import io.legado.app.ui.book.search.SearchIntent
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.constant.FeatureFlags
import io.legado.app.ui.config.ai.AiConfigRouteScreen
import io.legado.app.ui.ai.router.AiRouterRouteScreen
import io.legado.app.ui.config.ai.AiModelEditRouteScreen
import io.legado.app.ui.config.ai.AiProviderEditRouteScreen
import io.legado.app.ui.config.ai.prompt.AiPromptEditorRouteScreen
import io.legado.app.ui.config.ai.summary.AiSummaryConfigRouteScreen
import io.legado.app.ui.config.backupConfig.BackupConfigScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigScreen
import io.legado.app.ui.config.customTheme.CustomThemeScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigScreen
import io.legado.app.ui.config.labConfig.LabConfigScreen
import io.legado.app.ui.config.otherConfig.OtherConfigScreen
import io.legado.app.ui.config.readConfig.ReadConfigScreen
import io.legado.app.ui.config.tts.TtsModelManagerRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigScreen
import io.legado.app.ui.config.themeManage.ThemeManageScreen
import io.legado.app.ui.personalization.PersonalizationRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigScreen
import io.legado.app.ui.config.translation.dictionary.QuickDictionaryManagerRouteScreen
import io.legado.app.ui.config.translation.mlkit.MlKitModelsRouteScreen
import io.legado.app.ui.vbook.importer.VbookImportRouteScreen
import io.legado.app.ui.book.source.health.SourceHealthRouteScreen
import io.legado.app.ui.browser.BrowserRouteScreen
import io.legado.app.ui.config.translation.prompt.TranslationPromptConfigRouteScreen
import io.legado.app.ui.config.readMangaConfig.ReadMangaConfig
import io.legado.app.ui.highlightTagRule.HighlightTagRuleScreen
import io.legado.app.ui.media.player.MediaPlayerRouteScreen
import io.legado.app.ui.media.player.MediaPlayerViewModel
import io.legado.app.ui.media.download.MediaDownloadsRouteScreen
import io.legado.app.ui.media.audiobook.AudiobookImportRouteScreen
import io.legado.app.ui.translation.revision.TranslationRevisionRouteScreen
import io.legado.app.ui.rss.article.MainRouteRssSort
import io.legado.app.ui.rss.article.RssSortRouteScreen
import io.legado.app.ui.rss.favorites.RssFavoritesScreen
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.main.rss.RssScreen
import io.legado.app.ui.rss.read.MainRouteRssRead
import io.legado.app.ui.rss.read.RssReadRouteScreen
import io.legado.app.ui.rss.subscription.RuleSubScreen
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

internal enum class ExploreBookOpenMode {
    TEXT_READER,
    VIDEO_PLAYER,
    LEGACY_READER,
}

internal fun resolveExploreBookOpenMode(
    book: Book,
    showMangaUi: Boolean,
): ExploreBookOpenMode = when {
    book.isVideo -> ExploreBookOpenMode.VIDEO_PLAYER
    book.isAudio || (!book.isLocal && book.isImage && showMangaUi) ->
        ExploreBookOpenMode.LEGACY_READER
    else -> ExploreBookOpenMode.TEXT_READER
}

internal fun shouldRedirectReadBookToMediaPlayer(
    book: Book,
    chapters: List<BookChapter>,
): Boolean = book.isVideo || MediaChapterPolicy.hasVideoChapter(chapters)

private suspend fun resolveReadBookMediaRedirect(route: MainRouteReadBook): MainRouteMediaPlayer? {
    val bookUrl = route.bookUrl?.takeIf { it.isNotBlank() } ?: return null
    return withContext(IO) {
        val book = appDb.bookDao.getBook(bookUrl) ?: return@withContext null
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            .filterNot { it.isVolume }
        if (!shouldRedirectReadBookToMediaPlayer(book, chapters)) return@withContext null
        if (MediaChapterPolicy.normalizeVideoBookType(book, chapters)) {
            appDb.bookDao.update(book)
        }
        MainRouteMediaPlayer(
            bookUrl = book.bookUrl,
            chapterIndex = book.durChapterIndex.takeIf { it >= 0 },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateToRoute: (NavKey) -> Unit,
    onNavigateBack: () -> Unit,
    onRegisterVariableSetter: (((String, String?) -> Unit)?) -> Unit
) = entryProvider {
    entry<MainRouteHome> {
        MainScreen(
            useRail = useRail,
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettings)
            },
            onOpenAccount = {
                onNavigateToRoute(MainRouteSettingsAccount)
            },
            onNavigateToChat = {
                onNavigateToRoute(MainRouteAiChat)
            },
            onNavigateToAiRouter = {
                onNavigateToRoute(MainRouteAiRouter)
            },
            onNavigateToAgent = {
                onNavigateToRoute(MainRouteAiAgentDashboard)
            },
            onNavigateToWriting = {
                onNavigateToRoute(MainRouteWriting)
            },
            onNavigateToEbookEditor = {
                onNavigateToRoute(MainRouteEbookEditor)
            },
            onNavigateToRss = {
                onNavigateToRoute(MainRouteRss)
            },
            onNavigateToStoryWiki = {
                onNavigateToRoute(MainRouteStoryWiki)
            },
            onNavigateToSearch = { key ->
                onNavigateToRoute(
                    MainRouteSearch(
                        key = key?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            },
            onNavigateToRemoteImport = {
                onNavigateToRoute(MainRouteImportRemote)
            },
            onNavigateToLocalImport = {
                onNavigateToRoute(MainRouteImportLocal)
            },
            onNavigateToCache = { groupId ->
                onNavigateToRoute(MainRouteCache(groupId))
            },
            onNavigateToBookCacheManage = {
                onNavigateToRoute(MainRouteBookCacheManage)
            },
            onNavigateToBackupSettings = {
                onNavigateToRoute(MainRouteSettingsBackup)
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(
                    MainRouteExploreShow(
                        title = title,
                        sourceUrl = sourceUrl,
                        exploreUrl = exploreUrl
                    )
                )
            },
            onNavigateToReadRecord = {
                onNavigateToRoute(MainRouteReadRecord)
            },
            onNavigateToReadRecordOverview = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            },
            onNavigateToHighlightTagRule = {
                onNavigateToRoute(MainRouteHighlightTagRule)
            },
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            onNavigateToQuickDictionary = { projectKey, initialText ->
                onNavigateToRoute(
                    MainRouteQuickDictionaryManager(
                        projectKey = projectKey,
                        initialText = initialText,
                    )
                )
            },
            onNavigateToTranslation = {
                onNavigateToRoute(MainRouteSettingsTranslation)
            },
            onNavigateToBrowser = { sourceUrl, initialUrl ->
                onNavigateToRoute(MainRouteBrowser(url = initialUrl, sourceUrl = sourceUrl))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteSettings> {
        ConfigNavScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToOther = { backStack.add(MainRouteSettingsOther) },
            onNavigateToRead = { backStack.add(MainRouteSettingsRead) },
            onNavigateToCover = { backStack.add(MainRouteSettingsCover) },
            onNavigateToTheme = { backStack.add(MainRouteSettingsTheme) },
            onNavigateToBackup = { backStack.add(MainRouteSettingsBackup) },
            onNavigateToAi = { backStack.add(MainRouteSettingsAi) },
            onNavigateToDownloadCache = { backStack.add(MainRouteSettingsDownloadCache) },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToLab = { backStack.add(MainRouteSettingsLabConfig) }
        )
    }

    entry<MainRouteSettingsOther> {
        OtherConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsRead> {
        ReadConfigScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToTtsModelManager = {
                backStack.add(MainRouteSettingsTtsModels)
            },
        )
    }

    entry<MainRouteSettingsTtsModels> {
        TtsModelManagerRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCover> {
        CoverConfigScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCoverAlbums = {
                backStack.add(MainRouteSettingsCoverAlbums)
            },
        )
    }

    entry<MainRouteSettingsCoverAlbums> {
        CoverAlbumManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTheme> {
        ThemeConfigScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToPersonalization = {
                backStack.add(MainRouteSettingsPersonalization)
            },
            onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
            onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) }
        )
    }

    entry<MainRouteSettingsPersonalization> {
        PersonalizationRouteScreen(onBack = { onNavigateBack() })
    }

    entry<MainRouteSettingsBackup> {
        BackupConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAccount> {
        AccountRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteAssetDelivery> { route ->
        AssetDeliveryRouteScreen(
            rawUri = route.rawUri,
            onBack = { onNavigateBack() },
            onOpenAccount = { backStack.add(MainRouteSettingsAccount) },
        )
    }

    entry<MainRouteSettingsAi> {
        AiConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
            onNavigateToAiSummary = { backStack.add(MainRouteSettingsAiSummary) },
            onNavigateToPromptEditor = { backStack.add(MainRouteSettingsAiPrompts) },
            onNavigateToAgentDashboard = { onNavigateToRoute(MainRouteAiAgentDashboard) },
        )
    }

    entry<MainRouteSettingsAiRouter> {
        LaunchedEffect(Unit) {
            onNavigateToRoute(MainRouteAiRouter)
        }
    }

    entry<MainRouteAiRouter> {
        if (FeatureFlags.aiRouterV2) {
            AiRouterRouteScreen(onBackClick = { onNavigateBack() })
        } else {
            AiConfigRouteScreen(
                onBackClick = { onNavigateBack() },
                onNavigateToTranslation = { backStack.add(MainRouteSettingsTranslation) },
                onNavigateToAiSummary = { backStack.add(MainRouteSettingsAiSummary) },
                onNavigateToPromptEditor = { backStack.add(MainRouteSettingsAiPrompts) },
                onNavigateToAgentDashboard = { onNavigateToRoute(MainRouteAiAgentDashboard) },
            )
        }
    }

    entry<MainRouteAiAgentDashboard> {
        AgentDashboardRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCustomTools = { onNavigateToRoute(MainRouteAiCustomTools) },
        )
    }

    entry<MainRouteAiCustomTools> {
        CustomAgentToolManagerRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAiSummary> {
        AiSummaryConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAiPrompts> {
        AiPromptEditorRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsAiProviderEdit> { route ->
        AiProviderEditRouteScreen(
            providerId = route.providerId,
            onBackClick = { onNavigateBack() },
            onNavigateToPromptEditor = { backStack.add(MainRouteSettingsAiPrompts) },
        )
    }

    entry<MainRouteAiChat> {
        AiChatRouteScreen(
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { book ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverPath
                    )
                )
            }
        )
    }

    entry<MainRouteSettingsAiModelEdit> { route ->
        AiModelEditRouteScreen(
            providerId = route.providerId,
            modelProfileId = route.modelProfileId,
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsDownloadCache> {
        DownloadCacheConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTranslation> {
        TranslationConfigScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToAi = { backStack.add(MainRouteSettingsAi) },
            onNavigateToAiPromptEditor = { backStack.add(MainRouteSettingsAiPrompts) },
            onNavigateToPrompts = { backStack.add(MainRouteSettingsTranslationPrompts) },
            onNavigateToQuickDictionary = { requestImportFile ->
                backStack.add(
                    MainRouteQuickDictionaryManager(requestImportFile = requestImportFile)
                )
            },
            onNavigateToMlKitModels = { backStack.add(MainRouteSettingsMlKitModels) },
        )
    }

    entry<MainRouteSettingsMlKitModels> {
        MlKitModelsRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteVbookImport> {
        VbookImportRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSourceHealth> { route ->
        SourceHealthRouteScreen(
            sourceUrl = route.sourceUrl,
            onBackClick = { onNavigateBack() },
            onOpenBrowser = { sourceUrl, initialUrl ->
                backStack.add(MainRouteBrowser(url = initialUrl, sourceUrl = sourceUrl))
            },
            onOpenEdit = { sourceUrl, sourceType ->
                when (sourceType) {
                    SourceKeyType.BOOK -> this@mainEntryProvider.startActivity<BookSourceEditActivity> {
                        putExtra("sourceUrl", sourceUrl)
                    }

                    SourceKeyType.RSS -> this@mainEntryProvider.startActivity<RssSourceEditActivity> {
                        putExtra("sourceUrl", sourceUrl)
                    }
                }
            },
        )
    }

    entry<MainRouteBrowser> { route ->
        BrowserRouteScreen(
            initialUrl = route.url,
            sourceProbeUrl = route.sourceUrl,
            onBackClick = { onNavigateBack() },
            onExitClick = { onNavigateBack() },
            onOpenSourceHealth = { sourceUrl -> backStack.add(MainRouteSourceHealth(sourceUrl)) },
        )
    }

    entry<MainRouteTranslationRevision> { route ->
        TranslationRevisionRouteScreen(
            bookUrl = route.bookUrl,
            chapterIndex = route.chapterIndex,
            targetLanguage = route.targetLanguage,
            provider = route.provider,
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteSettingsTranslationPrompts> {
        TranslationPromptConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteQuickDictionaryManager> { route ->
        QuickDictionaryManagerRouteScreen(
            projectKey = route.projectKey,
            initialText = route.initialText,
            requestImportFile = route.requestImportFile,
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteSettingsLabConfig> {
        LabConfigScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCustomTheme> {
        CustomThemeScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsThemeManage> {
        ThemeManageScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteImportLocal> {
        ImportBookScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote> {
        RemoteBookScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteCache> { route ->
        BookshelfManageRouteScreen(
            groupId = route.groupId,
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl
                    )
                )
            }
        )
    }

    entry<MainRouteBookCacheManage> {
        BookCacheManageRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadBook> { route ->
        val readRouteReady = remember(route) {
            mutableStateOf(route.bookUrl.isNullOrBlank())
        }
        LaunchedEffect(route) {
            readRouteReady.value = route.bookUrl.isNullOrBlank()
            val redirect = resolveReadBookMediaRedirect(route)
            if (redirect != null) {
                onNavigateToRoute(redirect)
            } else {
                readRouteReady.value = true
            }
        }
        if (!readRouteReady.value) {
            return@entry
        }

        val readBookViewModel = koinViewModel<ReadBookViewModel>(
            key = "ReadBook:${route.bookUrl ?: "last-read"}"
        )
        val controller = remember(readBookViewModel) {
            ReadBookController(this@mainEntryProvider, readBookViewModel)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val readIntent = remember(route) {
            MainActivity.createReadBookIntent(
                context = this@mainEntryProvider,
                bookUrl = route.bookUrl,
                readAloud = route.readAloud,
                inBookshelf = route.inBookshelf,
                chapterChanged = route.chapterChanged,
            )
        }
        val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
        val readerResumeState = remember(controller, lifecycleOwner) { booleanArrayOf(false) }
        val collectorReady = remember(readBookViewModel) { booleanArrayOf(false) }
        fun resumeReader() {
            if (readerResumeState[0]) return
            readerResumeState[0] = true
            controller.onResume()
            readBookViewModel.onIntent(ReadBookIntent.OnResume)
        }

        fun pauseReader() {
            if (!readerResumeState[0]) return
            readerResumeState[0] = false
            controller.onPause()
            readBookViewModel.onIntent(ReadBookIntent.OnPause)
        }

        ReadBookRouteScreen(
            viewModel = readBookViewModel,
            host = controller,
            controller = controller,
            onEffectsReady = { effectsReady.complete(Unit) },
            onOpenSearch = { word, bookUrl ->
                onNavigateToRoute(
                    MainRouteSearchContent(
                        bookUrl = bookUrl,
                        searchWord = word,
                        searchResultIndex = readBookViewModel.uiState.value.searchResultIndex
                    )
                )
            },
            onOpenEntityAnalyzer = { bookUrl ->
                onNavigateToRoute(MainRouteEntityAnalyzer(bookUrl))
            },
            onOpenTranslationRevision = { bookUrl, chapterIndex, targetLanguage, provider ->
                onNavigateToRoute(
                    MainRouteTranslationRevision(
                        bookUrl = bookUrl,
                        chapterIndex = chapterIndex,
                        targetLanguage = targetLanguage,
                        provider = provider,
                    )
                )
            },
        )

        DisposableEffect(controller, lifecycleOwner, route.readAloud) {
            activeReadBookInputHandler = controller
            activeReadBookRoute = route
            MainActivity.hasActiveReadBookRoute = true
            controller.onClose = { onNavigateBack() }
            controller.onStartContentLoadFinish = {
                if (route.readAloud) {
                    io.legado.app.model.ReadBook.readAloud()
                }
            }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (collectorReady[0]) resumeReader()
                    }
                    Lifecycle.Event.ON_PAUSE -> pauseReader()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose {
                pauseReader()
                readBookViewModel.onIntent(ReadBookIntent.OnDispose)
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                if (activeReadBookInputHandler === controller) {
                    activeReadBookInputHandler = null
                }
                if (activeReadBookRoute == route) {
                    activeReadBookRoute = null
                }
                MainActivity.hasActiveReadBookRoute = false
                controller.clearTts()
                this@mainEntryProvider.toggleSystemBar(AppConfig.showStatusBar)
            }
        }

        LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
            effectsReady.await()
            collectorReady[0] = true
            readBookViewModel.initReadBookConfig(readIntent)
            readBookViewModel.initData(readIntent) {
                readBookViewModel.markJustInitData()
                controller.onRouteInitialized()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    resumeReader()
                }
            }
        }
    }

    entry<MainRouteMediaPlayer> { route ->
        val viewModel = koinViewModel<MediaPlayerViewModel>(
            key = "MediaPlayer:${route.bookUrl}"
        )
        MediaPlayerRouteScreen(
            bookUrl = route.bookUrl,
            chapterIndex = route.chapterIndex,
            viewModel = viewModel,
            onBack = { onNavigateBack() },
            onOpenExternal = { url -> this@mainEntryProvider.openUrl(url) },
            onOpenDownloads = { onNavigateToRoute(MainRouteMediaDownloads) },
        )
    }

    entry<MainRouteMediaDownloads> {
        MediaDownloadsRouteScreen(
            onBack = { onNavigateBack() },
            onImportAudiobook = { onNavigateToRoute(MainRouteAudiobookImport) },
        )
    }

    entry<MainRouteAudiobookImport> {
        AudiobookImportRouteScreen(
            onBack = { onNavigateBack() },
            onCreated = { bookUrl ->
                onNavigateToRoute(MainRouteMediaPlayer(bookUrl = bookUrl))
            },
        )
    }

    entry<MainRouteEntityAnalyzer> { route ->
        EntityAnalyzerRouteScreen(
            bookUrl = route.bookUrl,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteSearchContent> { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentScreen(
            viewModel = viewModel,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteSearch> { route ->
        val searchViewModel = koinViewModel<SearchViewModel>()

        LaunchedEffect(route.key, route.scopeRaw, searchViewModel) {
            searchViewModel.onIntent(
                SearchIntent.Initialize(
                    key = route.key,
                    scopeRaw = route.scopeRaw
                )
            )
        }

        SearchScreen(
            viewModel = searchViewModel,
            onBack = {
                onNavigateBack()
            },
            onOpenBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onOpenSourceManage = {
                this@mainEntryProvider.startActivity<BookSourceActivity>()
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteRss> {
        RssScreen(
            onBackClick = { onNavigateBack() },
            onOpenSort = { sourceUrl, sortUrl, key ->
                onNavigateToRoute(MainRouteRssSort(sourceUrl, sortUrl, key))
            },
            onOpenRead = { title, origin, link, openUrl, startPage ->
                onNavigateToRoute(
                    MainRouteRssRead(
                        title = title,
                        origin = origin,
                        link = link,
                        openUrl = openUrl,
                        startPage = startPage,
                    )
                )
            },
            onOpenFavorites = { onNavigateToRoute(MainRouteRssFavorites) },
            onOpenRuleSub = { onNavigateToRoute(MainRouteRuleSub) },
        )
    }

    entry<MainRouteRssSort> { route ->
        RssSortRouteScreen(
            sourceUrl = route.sourceUrl,
            initialSortUrl = route.sortUrl,
            initialSearchKey = route.key,
            onBackClick = { onNavigateBack() },
            onSearch = { key ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = route.sourceUrl,
                        key = key
                    )
                )
            },
            onOpenRead = { title, origin, link, openUrl ->
                if (link?.contains("@js:") == true) {
                    onNavigateToRoute(
                        MainRouteRssSort(
                            sourceUrl = origin,
                            sortUrl = link
                        )
                    )
                } else {
                    onNavigateToRoute(
                        MainRouteRssRead(
                            title = title,
                            origin = origin,
                            link = link,
                            openUrl = openUrl
                        )
                    )
                }
            }
        )
    }

    entry<MainRouteRssRead> { route ->
        RssReadRouteScreen(
            title = route.title,
            origin = route.origin,
            link = route.link,
            openUrl = route.openUrl,
            startPage = route.startPage,
            onBackClick = { onNavigateBack() },
            onOpenArticles = { sortUrl ->
                onNavigateToRoute(
                    MainRouteRssSort(
                        sourceUrl = route.origin,
                        sortUrl = sortUrl
                    )
                )
            }
        )
    }

    entry<MainRouteRssFavorites> {
        RssFavoritesScreen(
            onBackClick = { onNavigateBack() },
            onOpenRead = { title, origin, link, openUrl ->
                onNavigateToRoute(
                    MainRouteRssRead(
                        title = title,
                        origin = origin,
                        link = link,
                        openUrl = openUrl
                    )
                )
            }
        )
    }

    entry<MainRouteRuleSub> {
        RuleSubScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadRecord> {
        ReadRecordScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        onNavigateToRoute(MainRouteSearch(key = name))
                    }
                }
            },
            onSummaryClick = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            }
        )
    }

    entry<MainRouteReadRecordOverview> {
        ReadRecordOverviewScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        onNavigateToRoute(MainRouteSearch(key = name))
                    }
                }
            }
        )
    }

    entry<MainRouteBookInfo>(
        metadata = NavDisplay.transitionSpec {
            val from = initialState.key
            val fromStr = from.toString()
            if (from is MainRouteHome || from is MainRouteExploreShow || from is MainRouteSearch ||
                fromStr.startsWith("MainRouteHome") || fromStr.startsWith("MainRouteExploreShow") || fromStr.startsWith(
                    "MainRouteSearch"
                )
            ) {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            } else null
        } + NavDisplay.popTransitionSpec {
            val to = targetState.key
            val toStr = to.toString()
            if (to is MainRouteHome || to is MainRouteExploreShow || to is MainRouteSearch ||
                toStr.startsWith("MainRouteHome") || toStr.startsWith("MainRouteExploreShow") || toStr.startsWith(
                    "MainRouteSearch"
                )
            ) {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            } else null
        } + NavDisplay.predictivePopTransitionSpec { _ ->
            if (!AppConfig.isPredictiveBackEnabled) {
                null
            } else {
                val to = targetState.key
                val toStr = to.toString()
                if (to is MainRouteHome || to is MainRouteExploreShow || to is MainRouteSearch ||
                    toStr.startsWith("MainRouteHome") || toStr.startsWith("MainRouteExploreShow") || toStr.startsWith(
                        "MainRouteSearch"
                    )
                ) {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                } else null
            }
        }
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = "BookInfo:${route.bookUrl}")
        BookInfoRouteScreen(
            bookUrl = route.bookUrl,
            name = route.name,
            author = route.author,
            origin = route.origin,
            coverPath = route.coverPath,
            viewModel = bookInfoViewModel,
            onBack = { onNavigateBack() },
            onFinish = { _, _ -> onNavigateBack() },
            onOpenSearch = { keyword ->
                onNavigateToRoute(MainRouteSearch(key = keyword))
            },
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                    )
                )
            },
            onOpenMediaPlayer = { bookUrl, chapterIndex ->
                onNavigateToRoute(MainRouteMediaPlayer(bookUrl, chapterIndex))
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl, origin, coverPath))
            },
            onNavigateToExploreShow = { title, sourceUrl, exploreUrl ->
                onNavigateToRoute(MainRouteExploreShow(title, sourceUrl, exploreUrl))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
            onRegisterVariableSetter = { setter ->
                onRegisterVariableSetter(setter)
            }
        )
    }

    entry<MainRouteExploreShow> { route ->
        val exploreViewModel = koinViewModel<ExploreShowViewModel>()
        val context = LocalContext.current

        LaunchedEffect(route.sourceUrl, route.exploreUrl, exploreViewModel) {
            exploreViewModel.onIntent(
                ExploreShowIntent.InitData(
                    sourceUrl = route.sourceUrl,
                    exploreUrl = route.exploreUrl,
                    title = route.title,
                )
            )
        }

        ExploreShowScreen(
            viewModel = exploreViewModel,
            title = route.title ?: stringResource(com.drducbook.app.R.string.discovery),
            onBack = { onNavigateBack() },
            onOpenReader = { book ->
                lifecycleScope.launch {
                    val resolvedBook = withContext(IO) {
                        val source = appDb.bookSourceDao.getBookSource(
                            book.origin.ifBlank { route.sourceUrl }
                        )
                        book.toBook().normalizeTypeFromSource(source)
                    }
                    when (resolveExploreBookOpenMode(resolvedBook, ReadMangaConfig.showMangaUi)) {
                        ExploreBookOpenMode.VIDEO_PLAYER -> onNavigateToRoute(
                            MainRouteMediaPlayer(resolvedBook.bookUrl, null)
                        )
                        ExploreBookOpenMode.LEGACY_READER -> {
                            context.startActivityForBook(resolvedBook)
                        }
                        ExploreBookOpenMode.TEXT_READER -> onNavigateToRoute(
                            MainRouteReadBook(
                                bookUrl = resolvedBook.bookUrl,
                                inBookshelf = true,
                            )
                        )
                    }
                }
            },
            onBookInfo = { book, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = book.name,
                        author = book.author,
                        bookUrl = book.bookUrl,
                        origin = book.origin,
                        coverPath = book.coverUrl,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onQuickDictionary = { projectKey, initialText ->
                onNavigateToRoute(
                    MainRouteQuickDictionaryManager(
                        projectKey = projectKey,
                        initialText = initialText,
                    )
                )
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteWriting> {
        WritingRouteScreen(onBack = { onNavigateBack() })
    }

    entry<MainRouteEbookEditor> {
        EbookEditorRouteScreen(
            onBack = { onNavigateBack() },
            onPreview = { projectId -> onNavigateToRoute(MainRouteEbookPreview(projectId)) },
        )
    }

    entry<MainRouteStoryWiki> {
        StoryWikiRouteScreen(
            onBack = { onNavigateBack() },
            onOpenBook = { bookUrl, bookName ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = bookName,
                        author = null,
                        bookUrl = bookUrl,
                    )
                )
            },
        )
    }

    entry<MainRouteEbookPreview> { route ->
        EbookPreviewRouteScreen(
            projectId = route.projectId,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteHighlightTagRule> {
        HighlightTagRuleScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteAbout> {
        val viewModel = koinViewModel<AboutViewModel>()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is AboutEffect.OpenUrl -> context.openUrl(effect.url)
                    is AboutEffect.ShowToast -> context.toastOnUi(effect.message)
                    is AboutEffect.StartDownload -> Download.start(
                        context,
                        effect.url,
                        effect.fileName
                    )
                }
            }
        }
        AboutScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = { onNavigateBack() },
        )
    }
}
