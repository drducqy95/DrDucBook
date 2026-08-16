package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorTest {

    @Test
    fun entityAnalyzerRouteIsPushedAboveReader() {
        val reader = MainRouteReadBook(bookUrl = "book-url")
        val route = MainRouteEntityAnalyzer(bookUrl = "book-url")
        val backStack = mutableListOf<NavKey>(MainRouteHome, reader)

        MainNavigator.navigateToRoute(backStack, route)

        assertEquals(listOf(MainRouteHome, reader, route), backStack)
    }

    @Test
    fun quickDictionaryRouteIsNotDroppedFromBookshelf() {
        val backStack = mutableListOf<NavKey>(MainRouteHome)
        val route = MainRouteQuickDictionaryManager(
            projectKey = "book-url",
            initialText = "\u51ED\u7A7A\u51FA\u73B0",
        )

        MainNavigator.navigateToRoute(backStack, route)

        assertEquals(listOf(MainRouteHome, route), backStack)
    }

    @Test
    fun mediaPlayerRouteIsPushedAboveBookInfo() {
        val bookInfo = MainRouteBookInfo(
            name = "Video",
            author = "Tác giả",
            bookUrl = "video-url",
        )
        val route = MainRouteMediaPlayer(bookUrl = "video-url", chapterIndex = 3)
        val backStack = mutableListOf<NavKey>(MainRouteHome, bookInfo)

        MainNavigator.navigateToRoute(backStack, route)

        assertEquals(listOf(MainRouteHome, bookInfo, route), backStack)
    }

    @Test
    fun mediaPlayerRouteDropsBrowserFromSourceStack() {
        val browser = MainRouteBrowser(url = "https://player.example")
        val bookInfo = MainRouteBookInfo(
            name = "Video",
            author = "Tac gia",
            bookUrl = "video-url",
        )
        val route = MainRouteMediaPlayer(bookUrl = "video-url", chapterIndex = 3)
        val backStack = mutableListOf<NavKey>(MainRouteHome, browser, bookInfo)

        MainNavigator.navigateToRoute(backStack, route)

        assertEquals(listOf(MainRouteHome, bookInfo, route), backStack)
    }

    @Test
    fun aiRouterRouteIsApplicationLevel() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)

        MainNavigator.navigateToRoute(backStack, MainRouteAiRouter)

        assertEquals(listOf(MainRouteHome, MainRouteAiRouter), backStack)
    }

    @Test
    fun legacyAiRouterSettingsRouteNormalizesToApplicationRoute() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings, MainRouteSettingsAi)

        MainNavigator.navigateToRoute(backStack, MainRouteSettingsAiRouter)

        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsAi, MainRouteAiRouter), backStack)
    }

    @Test
    fun mlKitModelRouteIsSettingsLevel() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettingsTranslation)

        MainNavigator.navigateToRoute(backStack, MainRouteSettingsMlKitModels)

        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsMlKitModels), backStack)
    }

    @Test
    fun accountRouteIsSettingsLevel() {
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteWriting)

        MainNavigator.navigateToRoute(backStack, MainRouteSettingsAccount)

        assertEquals(listOf(MainRouteHome, MainRouteSettings, MainRouteSettingsAccount), backStack)
    }

    @Test
    fun browserRoutePreservesThePreviousApplicationRoute() {
        val sourceHealth = MainRouteSourceHealth(sourceUrl = "https://source.example")
        val backStack = mutableListOf<NavKey>(MainRouteHome, sourceHealth)
        val browser = MainRouteBrowser(url = "https://example.com")

        MainNavigator.navigateToRoute(backStack, browser)

        assertEquals(listOf(MainRouteHome, sourceHealth, browser), backStack)
    }

    @Test
    fun browserDeepLinkHasHomeAsExitFallback() {
        val browser = MainRouteBrowser(url = "https://example.com")

        assertEquals(
            listOf(MainRouteHome, browser),
            MainNavigator.initialBackStack(browser),
        )
    }

    @Test
    fun authoringRoutesResetToApplicationLevelFromNestedScreen() {
        val writingStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)
        val ebookStack = mutableListOf<NavKey>(MainRouteHome, MainRouteSettings)

        MainNavigator.navigateToRoute(writingStack, MainRouteWriting)
        MainNavigator.navigateToRoute(ebookStack, MainRouteEbookEditor)

        assertEquals(listOf(MainRouteHome, MainRouteWriting), writingStack)
        assertEquals(listOf(MainRouteHome, MainRouteEbookEditor), ebookStack)
    }

    @Test
    fun ebookPreviewStacksAboveEditorForProcessRecreationBackStack() {
        val preview = MainRouteEbookPreview(projectId = "project-1")
        val backStack = mutableListOf<NavKey>(MainRouteHome, MainRouteEbookEditor)

        MainNavigator.navigateToRoute(backStack, preview)

        assertEquals(listOf(MainRouteHome, MainRouteEbookEditor, preview), backStack)
    }
}
