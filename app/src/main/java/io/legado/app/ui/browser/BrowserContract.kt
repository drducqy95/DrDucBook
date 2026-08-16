package io.legado.app.ui.browser

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.BrowserBookmark
import io.legado.app.domain.model.BookSourceHealthStatus
import io.legado.app.domain.model.BrowserSourceContext
import io.legado.app.domain.model.BrowserPageTextNode
import io.legado.app.domain.model.BrowserPageTextTranslation
import io.legado.app.domain.model.SourceKey
import io.legado.app.domain.model.SourceKeyType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class BrowserTabUi(
    val id: String,
    val url: String,
    val title: String,
    val progress: Int = 0,
    val sourceKey: SourceKey? = null,
    val isHome: Boolean = false,
)

@Stable
data class BrowserHomeUiState(
    val query: String = "",
    val manualBookmarks: ImmutableList<BrowserBookmarkUi> = persistentListOf(),
    val sourceShortcuts: ImmutableList<BrowserSourceShortcutUi> = persistentListOf(),
    val healthSummary: BrowserSourceHealthSummaryUi = BrowserSourceHealthSummaryUi(),
)

@Stable
data class BrowserBookmarkUi(
    val id: String,
    val title: String,
    val url: String,
    val folder: String,
    val sortOrder: Int,
)

@Stable
data class BrowserBookmarkEditorUi(
    val id: String?,
    val title: String,
    val url: String,
    val folder: String = BrowserBookmark.DEFAULT_FOLDER,
)

@Stable
data class BrowserSourceShortcutUi(
    val sourceKey: SourceKey,
    val sourceType: SourceKeyType,
    val name: String,
    val group: String?,
    val sourceUrl: String,
    val homeUrl: String,
    val loginUrl: String?,
    val iconPath: String?,
    val enabled: Boolean,
    val isVbook: Boolean,
    val pinned: Boolean,
    val healthStatus: BookSourceHealthStatus?,
    val latencyMs: Long?,
)

@Stable
data class BrowserSourcePreferenceUi(
    val pinned: Boolean = false,
    val hidden: Boolean = false,
)

@Stable
data class BrowserSourceHealthSummaryUi(
    val total: Int = 0,
    val healthy: Int = 0,
    val needsAttention: Int = 0,
    val authRequired: Int = 0,
    val captchaRequired: Int = 0,
)

enum class BrowserPageTranslationState {
    ORIGINAL,
    TRANSLATING,
    TRANSLATED,
}

@Stable
data class BrowserUiState(
    val initialized: Boolean = false,
    val tabs: ImmutableList<BrowserTabUi> = persistentListOf(),
    val activeTabId: String = "",
    val addressBarText: String = "",
    val isHomeMode: Boolean = false,
    val home: BrowserHomeUiState = BrowserHomeUiState(),
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val translationState: BrowserPageTranslationState = BrowserPageTranslationState.ORIGINAL,
    val sourceProbeUrl: String? = null,
    val activeSourceContext: BrowserSourceContext? = null,
    val activeSourcePreference: BrowserSourcePreferenceUi = BrowserSourcePreferenceUi(),
    val showTabs: Boolean = false,
    val showMenu: Boolean = false,
    val bookmarkEditor: BrowserBookmarkEditorUi? = null,
    val loadGeneration: Long = 0,
    val errorMessage: String? = null,
)

sealed interface BrowserIntent {
    data class Initialize(val initialUrl: String?, val sourceProbeUrl: String?) : BrowserIntent
    data class ChangeAddress(val value: String) : BrowserIntent
    data object NavigateAddress : BrowserIntent
    data class OpenShortcut(val url: String) : BrowserIntent
    data class ChangeHomeQuery(val value: String) : BrowserIntent
    data object ShowAddBookmark : BrowserIntent
    data class EditBookmark(val id: String) : BrowserIntent
    data object DismissBookmarkEditor : BrowserIntent
    data class SaveBookmark(
        val id: String?,
        val title: String,
        val url: String,
        val folder: String,
    ) : BrowserIntent
    data class DeleteBookmark(val id: String) : BrowserIntent
    data class ToggleSourcePinned(val sourceKey: SourceKey) : BrowserIntent
    data class ToggleSourceHidden(val sourceKey: SourceKey) : BrowserIntent
    data object ToggleActiveSourcePinned : BrowserIntent
    data object ToggleActiveSourceHidden : BrowserIntent
    data object GoBack : BrowserIntent
    data object GoForward : BrowserIntent
    data object ReloadOrStop : BrowserIntent
    data object GoHome : BrowserIntent
    data object ExitHome : BrowserIntent
    data object AddTab : BrowserIntent
    data object ShowTabs : BrowserIntent
    data object ShowMenu : BrowserIntent
    data object DismissOverlays : BrowserIntent
    data class SwitchTab(val tabId: String) : BrowserIntent
    data class CloseTab(val tabId: String) : BrowserIntent
    data object ToggleDesktopMode : BrowserIntent
    data object TogglePageTranslation : BrowserIntent
    data object OpenExternal : BrowserIntent
    data object SharePage : BrowserIntent
    data object CopyLink : BrowserIntent
    data object OpenSourceHealth : BrowserIntent
    data object OpenSourceLogin : BrowserIntent
    data object OpenSourceEdit : BrowserIntent
    data object ClearSourceCookie : BrowserIntent
    data object ExitBrowser : BrowserIntent
    data object ConfirmLoginAndProbe : BrowserIntent
    data class PageStarted(val url: String) : BrowserIntent
    data class PageProgress(val progress: Int) : BrowserIntent
    data class PageFinished(
        val url: String,
        val title: String,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : BrowserIntent
    data class PageError(val message: String) : BrowserIntent
    data class PageSnapshotReady(val nodes: List<BrowserPageTextNode>) : BrowserIntent
    data object PageMutationDetected : BrowserIntent
}

sealed interface BrowserEffect {
    data object GoBack : BrowserEffect
    data object GoForward : BrowserEffect
    data object Reload : BrowserEffect
    data object Stop : BrowserEffect
    data object RequestPageSnapshot : BrowserEffect
    data object RestoreOriginalPage : BrowserEffect
    data class ApplyPageTranslations(
        val translations: List<BrowserPageTextTranslation>,
    ) : BrowserEffect
    data class SetDesktopMode(val enabled: Boolean) : BrowserEffect
    data class OpenExternal(val url: String) : BrowserEffect
    data class SharePage(val url: String, val title: String) : BrowserEffect
    data class CopyLink(val url: String) : BrowserEffect
    data class SyncLoginAndProbe(val url: String, val sourceUrl: String) : BrowserEffect
    data class ShowMessage(val message: String) : BrowserEffect
    data class OpenSourceHealth(val sourceUrl: String?) : BrowserEffect
    data class OpenSourceLogin(val sourceContext: BrowserSourceContext) : BrowserEffect
    data class OpenSourceEdit(val sourceContext: BrowserSourceContext) : BrowserEffect
    data class ClearSourceCookie(val sourceContext: BrowserSourceContext) : BrowserEffect
    data object ExitBrowser : BrowserEffect
}

enum class BrowserBackTarget {
    WEB_HISTORY,
    CLOSE_TAB,
    APP_ROUTE,
}

internal fun resolveBrowserBackTarget(
    canGoBack: Boolean,
    tabCount: Int,
): BrowserBackTarget = when {
    canGoBack -> BrowserBackTarget.WEB_HISTORY
    tabCount > 1 -> BrowserBackTarget.CLOSE_TAB
    else -> BrowserBackTarget.APP_ROUTE
}
