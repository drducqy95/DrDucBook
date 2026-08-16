package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey

@Serializable
data object MainRouteHome : MainRoute

@Serializable
data object MainRouteSettings : MainRoute

@Serializable
data object MainRouteSettingsOther : MainRoute

@Serializable
data object MainRouteSettingsRead : MainRoute

@Serializable
data object MainRouteSettingsTtsModels : MainRoute

@Serializable
data object MainRouteSettingsCover : MainRoute

@Serializable
data object MainRouteSettingsCoverAlbums : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsPersonalization : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsAccount : MainRoute

@Serializable
data class MainRouteAssetDelivery(
    val rawUri: String,
) : MainRoute

@Serializable
data object MainRouteSettingsAi : MainRoute

@Serializable
data object MainRouteSettingsAiRouter : MainRoute

@Serializable
data object MainRouteAiRouter : MainRoute

@Serializable
data object MainRouteAiChat : MainRoute

@Serializable
data object MainRouteAiAgentDashboard : MainRoute

@Serializable
data object MainRouteAiCustomTools : MainRoute

@Serializable
data object MainRouteWriting : MainRoute

@Serializable
data object MainRouteEbookEditor : MainRoute

@Serializable
data object MainRouteRss : MainRoute

@Serializable
data object MainRouteStoryWiki : MainRoute

@Serializable
data class MainRouteEbookPreview(val projectId: String) : MainRoute

@Serializable
data class MainRouteSettingsAiProviderEdit(
    val providerId: String? = null
) : MainRoute

@Serializable
data class MainRouteSettingsAiModelEdit(
    val providerId: String? = null,
    val modelProfileId: String? = null
) : MainRoute

@Serializable
data object MainRouteSettingsAiSummary : MainRoute

@Serializable
data object MainRouteSettingsAiPrompts : MainRoute

@Serializable
data object MainRouteSettingsCustomTheme : MainRoute

@Serializable
data object MainRouteSettingsThemeManage : MainRoute

@Serializable
data object MainRouteSettingsLabConfig : MainRoute

@Serializable
data object MainRouteSettingsDownloadCache : MainRoute

@Serializable
data object MainRouteSettingsTranslation : MainRoute

@Serializable
data object MainRouteSettingsTranslationPrompts : MainRoute

@Serializable
data object MainRouteSettingsMlKitModels : MainRoute

@Serializable
data object MainRouteVbookImport : MainRoute

@Serializable
data class MainRouteSourceHealth(
    val sourceUrl: String? = null,
) : MainRoute

@Serializable
data class MainRouteBrowser(
    val url: String? = null,
    val sourceUrl: String? = null,
) : MainRoute

@Serializable
data class MainRouteTranslationRevision(
    val bookUrl: String,
    val chapterIndex: Int,
    val targetLanguage: String,
    val provider: String,
) : MainRoute

@Serializable
data class MainRouteQuickDictionaryManager(
    val projectKey: String? = null,
    val initialText: String? = null,
    val requestImportFile: Boolean = false,
) : MainRoute

@Serializable
data object MainRouteImportLocal : MainRoute

@Serializable
data object MainRouteImportRemote : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data class MainRouteCache(val groupId: Long) : MainRoute

@Serializable
data object MainRouteBookCacheManage : MainRoute

@Serializable
data class MainRouteReadBook(
    val bookUrl: String? = null,
    val readAloud: Boolean = false,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
) : MainRoute

@Serializable
data class MainRouteMediaPlayer(
    val bookUrl: String,
    val chapterIndex: Int? = null,
) : MainRoute

@Serializable
data object MainRouteMediaDownloads : MainRoute

@Serializable
data object MainRouteAudiobookImport : MainRoute

@Serializable
data class MainRouteEntityAnalyzer(
    val bookUrl: String,
) : MainRoute

@Serializable
data class MainRouteSearch(
    val key: String?,
    val scopeRaw: String? = null
) : MainRoute

@Serializable
data class MainRouteBookInfo(
    val name: String?,
    val author: String?,
    val bookUrl: String,
    val origin: String? = null,
    val coverPath: String? = null,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data object MainRouteRssFavorites : MainRoute

@Serializable
data object MainRouteRuleSub : MainRoute

@Serializable
data class MainRouteExploreShow(
    val title: String?,
    val sourceUrl: String,
    val exploreUrl: String?,
) : MainRoute

@Serializable
data class MainRouteSearchContent(
    val bookUrl: String,
    val searchWord: String? = null,
    val searchResultIndex: Int = 0,
) : MainRoute

@Serializable
data object MainRouteHighlightTagRule : MainRoute

@Serializable
data object MainRouteAbout : MainRoute

object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_TTS_MODELS = "settings/read/tts_models"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_COVER_ALBUMS = "settings/cover/albums"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_PERSONALIZATION = "settings/theme/personalization"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_ACCOUNT = "settings/account"
    const val ROUTE_ASSET_DELIVERY = "asset/delivery"
    const val ROUTE_SETTINGS_AI = "settings/ai"
    const val ROUTE_SETTINGS_AI_ROUTER = "settings/ai/router"
    const val ROUTE_AI_ROUTER = "ai/router"
    const val ROUTE_SETTINGS_AI_SUMMARY = "settings/ai/summary"
    const val ROUTE_AI_CHAT = "ai/chat"
    const val ROUTE_AI_AGENT_DASHBOARD = "ai/agent/dashboard"
    const val ROUTE_AI_CUSTOM_TOOLS = "ai/agent/custom_tools"
    const val ROUTE_WRITING = "writing"
    const val ROUTE_EBOOK_EDITOR = "ebook_editor"
    const val ROUTE_RSS = "rss"
    const val ROUTE_SETTINGS_CUSTOM_THEME = "settings/custom_theme"
    const val ROUTE_SETTINGS_LAB_CONFIG = "settings/lab_config"
    const val ROUTE_SETTINGS_DOWNLOAD_CACHE = "settings/download_cache"
    const val ROUTE_SETTINGS_TRANSLATION = "settings/translation"
    const val ROUTE_SETTINGS_MLKIT_MODELS = "settings/translation/mlkit"
    const val ROUTE_VBOOK_IMPORT = "book/source/vbook/import"
    const val ROUTE_SOURCE_HEALTH = "book/source/health"
    const val ROUTE_BROWSER = "browser"
    const val ROUTE_IMPORT_LOCAL = "import/local"
    const val ROUTE_IMPORT_REMOTE = "import/remote"
    const val ROUTE_CACHE = "cache"
    const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
    const val ROUTE_READ_BOOK = "book/read"
    const val ROUTE_MEDIA_PLAYER = "media/player"
    const val ROUTE_MEDIA_DOWNLOADS = "media/downloads"
    const val ROUTE_AUDIOBOOK_IMPORT = "media/audiobook/import"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_SEARCH_CONTENT = "book/searchContent"
    const val ROUTE_BOOK_INFO = "book/info"
    const val ROUTE_EXPLORE_SHOW = "explore/show"
    const val ROUTE_RSS_SORT = "rss/sort"
    const val ROUTE_RSS_READ = "rss/read"
    const val ROUTE_RSS_FAVORITES = "rss/favorites"
    const val ROUTE_RULE_SUB = "rss/rule_sub"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
}
