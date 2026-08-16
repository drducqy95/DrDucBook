package io.legado.app.di

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.drducbook.app.cloud.SupabaseClientProvider
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.data.AppDatabase
import io.legado.app.data.local.preferences.LocalPreferencesRepository
import io.legado.app.data.repository.AiArtifactRepository
import io.legado.app.data.repository.AiAgentRepository
import io.legado.app.data.repository.AiChatRepository
import io.legado.app.data.repository.AiMemoryRepository
import io.legado.app.data.repository.AiProfileRepository
import io.legado.app.data.repository.AiRouterRepository
import io.legado.app.data.repository.AiSkillRepository
import io.legado.app.data.repository.AiOAuthRepository
import io.legado.app.data.repository.AndroidAiSecretStore
import io.legado.app.data.repository.AiPromptPresetRepository
import io.legado.app.data.repository.AiTextRepositoryImpl
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.data.repository.AppearanceRepository
import io.legado.app.data.repository.AuthoringProjectRepository
import io.legado.app.data.repository.AppStartupRepository
import io.legado.app.data.repository.BackupRestoreRepository
import io.legado.app.data.repository.BookCacheCleanupRepository
import io.legado.app.data.repository.BookContentProcessRepository
import io.legado.app.data.repository.BookDomainRepositoryImpl
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceCallbackRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.BookSourceProbeRepository
import io.legado.app.data.repository.BookSourceHealthRepository
import io.legado.app.data.repository.BrowserBookmarkRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.data.repository.CacheBookDownloadRepository
import io.legado.app.data.repository.CachedChapterRepository
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.CustomAgentToolRepository
import io.legado.app.data.repository.DatabaseMaintenanceRepository
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.data.repository.DictionaryRepositoryImpl
import io.legado.app.data.repository.DirectLinkUploadRepository
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ExploreRepositoryImpl
import io.legado.app.data.repository.GoogleDriveAppDataBackupRepository
import io.legado.app.data.repository.SafBackupRepository
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.data.repository.HomeDashboardRepository
import io.legado.app.data.repository.HomepageModulesRepository
import io.legado.app.data.repository.LocalBookRepository
import io.legado.app.data.repository.LocalAiEngineRepository
import io.legado.app.data.repository.LocalTtsModelRepository
import io.legado.app.data.repository.MangaOcrRepository
import io.legado.app.data.repository.MangaTextTranslationRepository
import io.legado.app.data.repository.MangaTranslationCacheRepository
import io.legado.app.data.repository.MangaTranslationExportRepository
import io.legado.app.data.repository.MediaResolverRepository
import io.legado.app.data.repository.MediaDownloadRepository
import io.legado.app.data.repository.EntitledMediaDownloadGateway
import io.legado.app.data.repository.AudiobookImportRepository
import io.legado.app.data.repository.AssetDeliveryRepository
import io.legado.app.data.repository.AssetDeliveryImportRepository
import io.legado.app.data.cookie.AndroidCookieVaultCodec
import io.legado.app.data.cookie.CookieVaultCodec
import io.legado.app.data.cookie.CookieVaultRepository
import io.legado.app.help.media.MediaPlaybackConnection
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.QuickTranslationRepository
import io.legado.app.data.repository.MlKitTranslationRepository
import io.legado.app.data.repository.QuickDictionaryRepository
import io.legado.app.data.repository.QuickDictionaryPackStore
import io.legado.app.data.repository.NmtTranslationRepository
import io.legado.app.data.repository.ReadBookStyleConfigRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.ReadStyleRepository
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.data.repository.RssRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.data.repository.SearchRepositoryImpl
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.data.repository.StoryImageStorageRepository
import io.legado.app.data.repository.SupabaseAccountAuthRepository
import io.legado.app.data.repository.SupabaseAccountAccessRepository
import io.legado.app.data.repository.AnonymousAccountQuotaRepository
import io.legado.app.data.repository.SupabaseAccountCloudBackupRepository
import io.legado.app.data.repository.SupabaseCloudSyncRepository
import io.legado.app.data.repository.sourcehealth.BookSourceHealthProbeRepository
import io.legado.app.data.repository.sourcehealth.RssSourceHealthProbeRepository
import io.legado.app.data.repository.sourcehealth.SourceCheckEngine
import io.legado.app.data.repository.sourcehealth.SourceCheckRepository
import io.legado.app.data.repository.sourcehealth.VbookSourceHealthProbeRepository
import io.legado.app.data.repository.SourceDomainIndexRepository
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.data.repository.TranslationCacheRepositoryImpl
import io.legado.app.data.repository.UploadRepository
import io.legado.app.data.repository.WebDavBackupRepository
import io.legado.app.data.repository.WebDavReadingProgressRepository
import io.legado.app.data.repository.ai.OpenAiImageRepository
import io.legado.app.data.repository.vbook.VbookRegistryRepository
import io.legado.app.data.repository.vbook.VbookImportRepository
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.gateway.AiAgentGateway
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.domain.gateway.AccountAccessGateway
import io.legado.app.domain.gateway.AnonymousAccountQuotaGateway
import io.legado.app.domain.gateway.AccountCloudBackupGateway
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiImageGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiRouterGateway
import io.legado.app.domain.gateway.AiSecretStore
import io.legado.app.domain.gateway.AiSkillGateway
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiOAuthGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.AppearanceGateway
import io.legado.app.domain.gateway.AppStartupGateway
import io.legado.app.domain.gateway.BackupRestoreGateway
import io.legado.app.domain.gateway.BookCacheCleanupGateway
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.gateway.BookSourceCallbackGateway
import io.legado.app.domain.gateway.BookSourceHealthProbeGateway
import io.legado.app.domain.gateway.BookSourceProbeGateway
import io.legado.app.domain.gateway.CachedChapterGateway
import io.legado.app.domain.gateway.CloudSyncGateway
import io.legado.app.domain.gateway.CoverAlbumGateway
import io.legado.app.domain.gateway.CustomAgentToolGateway
import io.legado.app.domain.gateway.DatabaseMaintenanceGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.ExploreBooksGateway
import io.legado.app.domain.gateway.GoogleDriveBackupGateway
import io.legado.app.domain.gateway.SafBackupGateway
import io.legado.app.domain.gateway.StoryImageStorageGateway
import io.legado.app.domain.gateway.HomeDashboardGateway
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.gateway.LocalBookGateway
import io.legado.app.domain.gateway.LocalAiEngineGateway
import io.legado.app.domain.gateway.LocalTtsModelGateway
import io.legado.app.domain.gateway.MediaResolverGateway
import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.gateway.AudiobookImportGateway
import io.legado.app.domain.gateway.AssetDeliveryGateway
import io.legado.app.domain.gateway.AssetDeliveryImportGateway
import io.legado.app.domain.gateway.MediaPlaybackGateway
import io.legado.app.domain.gateway.SourceCookieGateway
import io.legado.app.domain.gateway.SourceDomainIndexGateway
import io.legado.app.domain.gateway.MangaOcrGateway
import io.legado.app.domain.gateway.MangaTextTranslationGateway
import io.legado.app.domain.gateway.MangaTranslationCacheGateway
import io.legado.app.domain.gateway.MlKitTranslationGateway
import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.RssSourceHealthProbeGateway
import io.legado.app.domain.gateway.NmtTranslationGateway
import io.legado.app.domain.gateway.AuthoringProjectGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.WebDavBackupGateway
import io.legado.app.domain.gateway.VbookRegistryGateway
import io.legado.app.domain.gateway.VbookImportGateway
import io.legado.app.domain.gateway.VbookSourceHealthProbeGateway
import io.legado.app.domain.repository.BookDomainRepository
import io.legado.app.domain.usecase.AddBookUseCase
import io.legado.app.domain.usecase.AccountAuthUseCase
import io.legado.app.domain.usecase.AccountAccessUseCase
import io.legado.app.domain.usecase.AccountCloudBackupUseCase
import io.legado.app.domain.usecase.AccountEntitlementUseCase
import io.legado.app.domain.usecase.WebServiceAccessUseCase
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.AnalyzeDownloadedEntitiesUseCase
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.AssetDeliveryUseCase
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.AiToolAwareGenerationUseCase
import io.legado.app.domain.usecase.AppearanceUseCase
import io.legado.app.domain.usecase.AuthoringProjectUseCase
import io.legado.app.domain.usecase.AuthoringWorkflowUseCase
import io.legado.app.domain.usecase.CloneDownloadedBookUseCase
import io.legado.app.domain.usecase.AppStartupMaintenanceUseCase
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.ChangeSourceSearchUseCase
import io.legado.app.domain.usecase.CleanSelectedTextUseCase
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.CloudSyncUseCase
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.domain.usecase.DeleteBooksUseCase
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.domain.usecase.ExportBookshelfUseCase
import io.legado.app.domain.usecase.ExportAuthoringProjectUseCase
import io.legado.app.domain.usecase.ValidateEbookProjectUseCase
import io.legado.app.domain.usecase.ExecuteApprovedAgentActionUseCase
import io.legado.app.domain.usecase.GenerateChapterSummaryUseCase
import io.legado.app.domain.usecase.GetChapterContentUseCase
import io.legado.app.domain.usecase.GoogleDriveBackupUseCase
import io.legado.app.domain.usecase.SafBackupUseCase
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.HomeDashboardUseCase
import io.legado.app.domain.usecase.ImportBookshelfUseCase
import io.legado.app.domain.usecase.ImportEntityCandidatesUseCase
import io.legado.app.domain.usecase.ImportVbookRegistryUseCase
import io.legado.app.domain.usecase.ManageTranslationRevisionUseCase
import io.legado.app.domain.usecase.TranslateMangaPageUseCase
import io.legado.app.domain.usecase.TranslateBrowserPageUseCase
import io.legado.app.domain.usecase.MigrateAiProviderApiKeysUseCase
import io.legado.app.domain.usecase.RefreshTocUseCase
import io.legado.app.domain.usecase.ProbeBookSourceUseCase
import io.legado.app.domain.usecase.RepairAiRouteBindingsUseCase
import io.legado.app.domain.usecase.TestAiProviderDraftUseCase
import io.legado.app.domain.usecase.TestLocalTtsModelUseCase
import io.legado.app.domain.usecase.RemoveBookGroupAssignmentUseCase
import io.legado.app.domain.usecase.ResolveBookMediaUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.ResolveBrowserSourceContextUseCase
import io.legado.app.domain.usecase.RunAiAgentUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.SaveBookContentProcessUseCase
import io.legado.app.domain.usecase.ShrinkDatabaseUseCase
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.domain.usecase.TranslateDynamicBookUiUseCase
import io.legado.app.domain.usecase.TranslateDynamicUiTextUseCase
import io.legado.app.domain.usecase.TranslationStoryMemoryUseCase
import io.legado.app.domain.usecase.StoryIllustrationUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.domain.usecase.readRecord.GetReadRecordOverviewUseCase
import io.legado.app.help.coil.CoverFetcher
import io.legado.app.help.coil.CoverInterceptor
import io.legado.app.help.config.ThemePackageManager
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.okHttpClientManga
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.account.AccountViewModel
import io.legado.app.ui.ai.agent.AgentDashboardViewModel
import io.legado.app.ui.ai.agent.tools.CustomAgentToolManagerViewModel
import io.legado.app.ui.ai.chat.AiChatViewModel
import io.legado.app.ui.assetdelivery.AssetDeliveryViewModel
import io.legado.app.ui.authoring.ebook.EbookEditorViewModel
import io.legado.app.ui.authoring.ebook.EbookPreviewViewModel
import io.legado.app.ui.authoring.writing.WritingViewModel
import io.legado.app.ui.book.bookmark.AllBookmarkViewModel
import io.legado.app.ui.book.cache.manage.BookCacheManageViewModel
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceViewModel
import io.legado.app.ui.book.changesource.ChangeChapterSourceViewModel
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.translation.revision.TranslationRevisionViewModel
import io.legado.app.ui.book.read.manga.MangaTranslationEditorViewModel
import io.legado.app.ui.book.source.health.SourceHealthViewModel
import io.legado.app.ui.browser.BrowserTabStore
import io.legado.app.ui.browser.BrowserViewModel
import io.legado.app.worker.BookSourceHealthCheckProcessor
import io.legado.app.ui.vbook.importer.VbookImportViewModel
import io.legado.app.ui.book.entity.EntityAnalyzerViewModel
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.book.import.local.ImportBookViewModel
import io.legado.app.ui.book.import.remote.RemoteBookViewModel
import io.legado.app.ui.book.import.remote.ServerConfigViewModel
import io.legado.app.ui.book.import.remote.ServersViewModel
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.info.edit.BookInfoEditViewModel
import io.legado.app.ui.book.manage.BookshelfManageScreenViewModel
import io.legado.app.ui.book.manga.ReadMangaViewModel
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewViewModel
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.TxtTocRuleViewModel
import io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewViewModel
import io.legado.app.ui.config.ai.AiConfigViewModel
import io.legado.app.ui.config.ai.AiModelEditViewModel
import io.legado.app.ui.config.ai.AiProviderEditViewModel
import io.legado.app.ui.ai.router.AiRouterViewModel
import io.legado.app.ui.config.ai.prompt.AiPromptEditorViewModel
import io.legado.app.ui.config.ai.summary.AiSummaryConfigViewModel
import io.legado.app.ui.config.translation.prompt.TranslationPromptConfigViewModel
import io.legado.app.ui.config.translation.dictionary.QuickDictionaryManagerViewModel
import io.legado.app.ui.config.translation.mlkit.MlKitModelsViewModel
import io.legado.app.ui.config.tts.TtsModelManagerViewModel
import io.legado.app.ui.config.backupConfig.BackupConfigViewModel
import io.legado.app.ui.config.bookshelfConfig.BookshelfManageScreenConfig
import io.legado.app.ui.config.coverConfig.CoverAlbumManageViewModel
import io.legado.app.ui.config.coverConfig.CoverConfigViewModel
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigViewModel
import io.legado.app.ui.config.otherConfig.OtherConfigViewModel
import io.legado.app.ui.config.readConfig.ReadConfigViewModel
import io.legado.app.ui.config.themeConfig.ThemeConfigViewModel
import io.legado.app.ui.config.themeManage.ThemeManageViewModel
import io.legado.app.ui.personalization.PersonalizationViewModel
import io.legado.app.ui.dict.DictViewModel
import io.legado.app.ui.dict.rule.DictRuleViewModel
import io.legado.app.ui.highlightTagRule.HighlightTagRuleViewModel
import io.legado.app.ui.main.MainRouteSearchContent
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.workspace.WorkspaceViewModel
import io.legado.app.ui.translation.memory.StoryWikiViewModel
import io.legado.app.ui.translation.memory.BookStoryMemoryViewModel
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.main.home.HomeViewModel
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.main.my.MyViewModel
import io.legado.app.ui.main.rss.RssViewModel
import io.legado.app.ui.media.player.MediaPlayerViewModel
import io.legado.app.ui.media.download.MediaDownloadsViewModel
import io.legado.app.ui.media.audiobook.AudiobookImportViewModel
import io.legado.app.ui.quickdict.QuickDictionaryEditorViewModel
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleViewModel
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.rss.article.RssArticlesViewModel
import io.legado.app.ui.rss.article.RssSortViewModel
import io.legado.app.ui.rss.favorites.RssFavoritesViewModel
import io.legado.app.ui.rss.read.ReadRssViewModel
import io.legado.app.ui.rss.source.manage.RssSourceViewModel
import io.legado.app.ui.rss.subscription.RuleSubViewModel
import io.legado.app.ui.tagGroupRule.TagGroupRuleViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.time.Clock

val appModule = module {

    single { get<AppDatabase>().readRecordDao }
    single { get<AppDatabase>().bookDao }
    single { get<AppDatabase>().bookChapterDao }
    single { get<AppDatabase>().bookGroupDao }
    single { get<AppDatabase>().bookSourceDao }

    singleOf(::ReadRecordRepository)
    single<HomeDashboardGateway> { HomeDashboardRepository(get(), get()) }
    singleOf(::BookRepository)
    singleOf(::BookGroupRepository)
    singleOf(::BookSourceRepository)
    singleOf(::BookshelfRepository)
    singleOf(::DictRuleRepository)
    singleOf(::TxtTocRuleRepository)
    singleOf(::SearchContentRepository)
    singleOf(::RemoteBookRepository)
    singleOf(::SettingsRepository)
    single<AuthoringProjectGateway> { AuthoringProjectRepository(get<Context>()) }
    singleOf(::AuthoringProjectUseCase)
    singleOf(::CloneDownloadedBookUseCase)
    single { ExportAuthoringProjectUseCase(get(), get(), get(), get()) }
    singleOf(::ValidateEbookProjectUseCase)
    singleOf(::ReadSettingsRepository)
    singleOf(::ReadAloudSettingsRepository)
    singleOf(::HighlightRuleRepository)
    singleOf(::ReadStyleRepository)
    singleOf(::ReadBookStyleConfigRepository)
    singleOf(::LocalPreferencesRepository)
    singleOf(::ExploreBooksUseCase)
    singleOf(::ExploreKindUiUseCase)
    singleOf(::SaveSearchBooksUseCase)
    singleOf(::AppStartupMaintenanceUseCase)
    singleOf(::BackupRestoreUseCase)
    single { BatchCacheDownloadUseCase(get(), get(), get()) }
    single { CacheBookChaptersUseCase(get(), get()) }
    singleOf(::ChangeBookSourceUseCase)
    singleOf(::ClearBookCacheUseCase)
    singleOf(::CoverAlbumUseCase)
    singleOf(::DeleteBooksUseCase)
    singleOf(::GetReadingProgressUseCase)
    single { HomeDashboardUseCase(get(), Clock.systemDefaultZone()) }
    singleOf(::RemoveBookGroupAssignmentUseCase)
    singleOf(::UpdateBooksGroupUseCase)
    singleOf(::UploadReadingProgressUseCase)
    singleOf(::ResolveBookShelfStateUseCase)
    singleOf(::RefreshTocUseCase)
    singleOf(::AddBookUseCase)
    singleOf(::AddToBookshelfUseCase)
    singleOf(::ImportBookshelfUseCase)
    singleOf(::ExportBookshelfUseCase)
    factory { GetReadRecordOverviewUseCase() }
    singleOf(::ShrinkDatabaseUseCase)
    singleOf(::WebDavBackupUseCase)
    singleOf(::BookshelfManageScreenConfig)
    singleOf(::ThemePackageManager)
    single<AppearanceGateway> { AppearanceRepository(get()) }
    singleOf(::AppearanceUseCase)
    single<SupabasePublicConfig> { SupabaseClientProvider.config }
    single<AccountAuthGateway> { SupabaseAccountAuthRepository() }
    singleOf(::AccountAuthUseCase)
    single<AccountAccessGateway> {
        SupabaseAccountAccessRepository(
            config = get(),
            accountAuthGateway = get(),
        )
    }
    singleOf(::AccountAccessUseCase)
    single<AnonymousAccountQuotaGateway> { AnonymousAccountQuotaRepository(androidContext()) }
    singleOf(::AccountEntitlementUseCase)
    singleOf(::WebServiceAccessUseCase)
    single<AccountCloudBackupGateway> {
        SupabaseAccountCloudBackupRepository(
            context = androidContext(),
            config = get(),
            accountAuthGateway = get(),
            backupRestoreGateway = get(),
        )
    }
    singleOf(::AccountCloudBackupUseCase)
    single<AssetDeliveryGateway> { AssetDeliveryRepository(androidContext()) }
    single<AssetDeliveryImportGateway> {
        AssetDeliveryImportRepository(
            context = androidContext(),
            quickDictionaryGateway = get(),
            localAiEngineGateway = get(),
        )
    }
    singleOf(::AssetDeliveryUseCase)
    single<CloudSyncGateway> { SupabaseCloudSyncRepository() }
    singleOf(::CloudSyncUseCase)
    single<GoogleDriveBackupGateway> {
        GoogleDriveAppDataBackupRepository(
            context = androidContext(),
            backupRestoreGateway = get(),
        )
    }
    singleOf(::GoogleDriveBackupUseCase)
    single<SafBackupGateway> {
        SafBackupRepository(
            context = androidContext(),
            backupRestoreGateway = get(),
            secretStore = get(),
        )
    }
    singleOf(::SafBackupUseCase)

    single<UploadRepository> { DirectLinkUploadRepository() }
    single<TranslationCacheGateway> { TranslationCacheRepositoryImpl() }
    single<QuickTranslationGateway> { QuickTranslationRepository() }
    single { QuickDictionaryPackStore() }
    single<QuickDictionaryGateway> { QuickDictionaryRepository(get(), get(), get()) }
      single<NmtTranslationGateway> { NmtTranslationRepository(androidContext()) }
    single<MlKitTranslationGateway> { MlKitTranslationRepository() }
    single<LocalTtsModelGateway> { LocalTtsModelRepository(get()) }
    singleOf(::TestLocalTtsModelUseCase)
    single<MangaOcrGateway> { MangaOcrRepository() }
    single<MangaTranslationCacheGateway> { MangaTranslationCacheRepository(get()) }
    singleOf(::MangaTranslationExportRepository)
    single<CookieVaultCodec> { AndroidCookieVaultCodec(androidContext()) }
    single<SourceCookieGateway> { CookieVaultRepository(get(), get(), get()) }
    single<AiProfileGateway> { AiProfileRepository(get(), get()) }
    single<AiArtifactGateway> { AiArtifactRepository(get()) }
    single<AiAgentGateway> { AiAgentRepository(get()) }
    single<AiChatGateway> { AiChatRepository(get()) }
    single<AiMemoryGateway> { AiMemoryRepository(get()) }
    single<AiImageGateway> { OpenAiImageRepository() }
    single<StoryImageStorageGateway> { StoryImageStorageRepository(androidContext()) }
    single<AiSkillGateway> { AiSkillRepository(get(), get()) }
    single<CustomAgentToolGateway> { CustomAgentToolRepository(get()) }
    single {
        AgentPermissionBroker(
            mutationEnabled = { io.legado.app.constant.FeatureFlags.agentMutation },
            skillEnabled = { io.legado.app.constant.FeatureFlags.agentSkill },
            pluginEnabled = { io.legado.app.constant.FeatureFlags.agentPlugin },
        )
    }
    single<AiPromptPresetGateway> { AiPromptPresetRepository(get()) }
    single<LocalAiEngineGateway> { LocalAiEngineRepository(get()) }
    single<AiSecretStore> { AndroidAiSecretStore(get()) }
    single<AiOAuthGateway> {
        AiOAuthRepository(
            dao = get(),
            secretStore = get(),
            profileGateway = get(),
            aiTextGateway = get(named(RAW_AI_TEXT_GATEWAY)),
            clock = Clock.systemUTC(),
        )
    }
    single<AiTextGateway>(named(RAW_AI_TEXT_GATEWAY)) { AiTextRepositoryImpl(get()) }
    single {
        AiRouterRepository(
            dao = get(),
            profileGateway = get(),
            secretStore = get(),
            oauthGateway = get(),
            delegate = get(named(RAW_AI_TEXT_GATEWAY)),
            clock = Clock.systemUTC(),
        )
    }
    single<AiRouterGateway> { get<AiRouterRepository>() }
    single<AiTextGateway> { get<AiRouterRepository>() }
    single { TestAiProviderDraftUseCase(get()) }
    singleOf(::MigrateAiProviderApiKeysUseCase)
    singleOf(::RepairAiRouteBindingsUseCase)
    single<AiToolGateway> {
        AiToolRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    single<AppStartupGateway> { AppStartupRepository(get()) }
    single<VbookRegistryGateway> { VbookRegistryRepository() }
    single<VbookImportGateway> { VbookImportRepository(get(), get()) }
    single<BookSourceProbeGateway> { BookSourceProbeRepository() }
    single<BookSourceHealthProbeGateway> { BookSourceHealthProbeRepository() }
    single<RssSourceHealthProbeGateway> { RssSourceHealthProbeRepository() }
    single<VbookSourceHealthProbeGateway> { VbookSourceHealthProbeRepository() }
    singleOf(::SourceCheckRepository)
    single {
        SourceCheckEngine(
            sourceCheckRepository = get(),
            bookSourceHealthProbeGateway = get(),
            rssSourceHealthProbeGateway = get(),
            vbookSourceHealthProbeGateway = get(),
        )
    }
    singleOf(::BookSourceHealthCheckProcessor)
    singleOf(::BookSourceHealthRepository)
    singleOf(::BrowserBookmarkRepository)
    single<SourceDomainIndexGateway> { SourceDomainIndexRepository(get(), get()) }
    singleOf(::BrowserTabStore)
    singleOf(::ImportVbookRegistryUseCase)
    singleOf(::ProbeBookSourceUseCase)
    singleOf(::ResolveBrowserSourceContextUseCase)
    single<BackupRestoreGateway> { BackupRestoreRepository() }
    single<BookCacheDownloadGateway> { CacheBookDownloadRepository(get()) }
    single<BookCacheCleanupGateway> { BookCacheCleanupRepository(get()) }
    single<CachedChapterGateway> { CachedChapterRepository(get(), get()) }
    single<CoverAlbumGateway> { CoverAlbumRepository(get(), get()) }
    single<BookSourceCallbackGateway> { BookSourceCallbackRepository(get(), get()) }
    single<LocalBookGateway> { LocalBookRepository(get()) }
    single<MediaResolverGateway> { MediaResolverRepository(get(), get(), get(), get()) }
    single { MediaDownloadRepository(get()) }
    single<MediaDownloadGateway> {
        EntitledMediaDownloadGateway(get<MediaDownloadRepository>(), get())
    }
    single<AudiobookImportGateway> { AudiobookImportRepository(get(), get()) }
    single { MediaPlaybackConnection(get()) }
    single<MediaPlaybackGateway> { get<MediaPlaybackConnection>() }
    single<DatabaseMaintenanceGateway> { DatabaseMaintenanceRepository(get()) }
    single<WebDavBackupGateway> { WebDavBackupRepository() }
    single<ReadingProgressGateway> { WebDavReadingProgressRepository() }
    single<HomepageModulesGateway> { HomepageModulesRepository(get(), get()) }
    single<BookDomainRepository> { BookDomainRepositoryImpl(get(), get()) }
    single<BookContentProcessGateway> { BookContentProcessRepository(get()) }
    single { ExploreRepositoryImpl(get()) }
    single<ExploreRepository> { get<ExploreRepositoryImpl>() }
    single<ExploreBooksGateway> { get<ExploreRepositoryImpl>() }
    singleOf(::RssRepository)
    single {
        SearchRepositoryImpl(get())
    }
    single<SearchRepository> { get<SearchRepositoryImpl>() }
    single<BookSearchGateway> { get<SearchRepositoryImpl>() }
    singleOf(::SearchBooksUseCase)
    singleOf(::ChangeSourceSearchUseCase)
    singleOf(::GetChapterContentUseCase)
    singleOf(::RunAiAgentUseCase)
    singleOf(::ExecuteApprovedAgentActionUseCase)
    singleOf(::AiToolAwareGenerationUseCase)
    singleOf(::GenerateChapterSummaryUseCase)
    singleOf(::AiTextFactoryUseCase)
    singleOf(::AuthoringWorkflowUseCase)
    singleOf(::CleanSelectedTextUseCase)
    singleOf(::AnalyzeDownloadedEntitiesUseCase)
    singleOf(::ImportEntityCandidatesUseCase)
    singleOf(::SaveBookContentProcessUseCase)
    singleOf(::TranslateDynamicUiTextUseCase)
    singleOf(::TranslateBrowserPageUseCase)
    singleOf(::ReplaceRuleRepository)
    single<DictionaryGateway> { DictionaryRepositoryImpl() }
    singleOf(::TranslationStoryMemoryUseCase)
    singleOf(::StoryIllustrationUseCase)
    singleOf(::TranslateChapterUseCase)
    single<MangaTextTranslationGateway> { MangaTextTranslationRepository(get()) }
    singleOf(::TranslateMangaPageUseCase)
    singleOf(::ManageTranslationRevisionUseCase)
    singleOf(::TranslateDynamicBookUiUseCase)
    singleOf(::ResolveBookMediaUseCase)
    singleOf(::AiChatGenerationUseCase)

    single<ImageLoader> {
        ImageLoader.Builder(get())
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(platformImageDecoderFactory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(CoverInterceptor())
                add(CoverFetcher.Factory(okHttpClient, okHttpClientManga))
            }
            .crossfade(true)
            .build()
    }

    viewModelOf(::DictRuleViewModel)
    viewModelOf(::HighlightTagRuleViewModel)
    viewModelOf(::TagGroupRuleViewModel)
    viewModelOf(::DictViewModel)
    viewModelOf(::QuickDictionaryEditorViewModel)
    viewModelOf(::RssSourceViewModel)
    viewModelOf(::RssSortViewModel)
    viewModelOf(::RssArticlesViewModel)
    viewModelOf(::ReadRssViewModel)
    viewModelOf(::RssFavoritesViewModel)
    viewModelOf(::RuleSubViewModel)
    viewModelOf(::ReadRecordViewModel)
    viewModelOf(::ReadRecordOverviewViewModel)
    viewModelOf(::ExploreShowViewModel)
    viewModelOf(::MyViewModel)
    viewModelOf(::BookshelfViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::WorkspaceViewModel)
    viewModelOf(::StoryWikiViewModel)
    viewModel { (bookUrl: String) ->
        BookStoryMemoryViewModel(
            bookUrl = bookUrl,
            storyMemoryUseCase = get(),
            storyIllustrationUseCase = get(),
            cachedChapterGateway = get(),
            aiProfileGateway = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::HomepageViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::AccountViewModel)
    viewModel { (rawUri: String) ->
        AssetDeliveryViewModel(
            rawUri = rawUri,
            assetDeliveryUseCase = get(),
        )
    }
    viewModel { (bookUrl: String) ->
        EntityAnalyzerViewModel(
            bookUrl = bookUrl,
            analyzeDownloadedEntities = get(),
            importEntityCandidates = get(),
        )
    }
    viewModelOf(::GroupViewModel)
    viewModelOf(::ReplaceRuleViewModel)
    viewModelOf(::AllBookmarkViewModel)
    viewModelOf(::TxtTocRuleViewModel)
    viewModel { TxtTocRulePreviewViewModel(app = get(), repository = get()) }
    viewModelOf(::OtherConfigViewModel)
    viewModelOf(::ReadConfigViewModel)
    viewModelOf(::CoverConfigViewModel)
    viewModelOf(::CoverAlbumManageViewModel)
    viewModelOf(::DownloadCacheConfigViewModel)
    viewModelOf(::ThemeConfigViewModel)
    viewModelOf(::ThemeManageViewModel)
    viewModelOf(::PersonalizationViewModel)
    viewModelOf(::BackupConfigViewModel)
    viewModelOf(::AiConfigViewModel)
    viewModelOf(::AiRouterViewModel)
    viewModelOf(::AgentDashboardViewModel)
    viewModelOf(::CustomAgentToolManagerViewModel)
    viewModelOf(::AiPromptEditorViewModel)
    viewModelOf(::AiSummaryConfigViewModel)
    viewModelOf(::TranslationPromptConfigViewModel)
    viewModelOf(::QuickDictionaryManagerViewModel)
    viewModelOf(::MlKitModelsViewModel)
    viewModelOf(::TtsModelManagerViewModel)
    viewModelOf(::TranslationRevisionViewModel)
    viewModelOf(::MangaTranslationEditorViewModel)
    viewModelOf(::SourceHealthViewModel)
    viewModelOf(::VbookImportViewModel)
    viewModelOf(::BrowserViewModel)
    viewModelOf(::AiChatViewModel)
    viewModelOf(::WritingViewModel)
    viewModelOf(::EbookEditorViewModel)
    viewModelOf(::EbookPreviewViewModel)
    viewModelOf(::MediaPlayerViewModel)
    viewModelOf(::MediaDownloadsViewModel)
    viewModelOf(::AudiobookImportViewModel)
    viewModel { (providerId: String?) ->
        AiProviderEditViewModel(
            initialProviderId = providerId,
            aiProfileGateway = get(),
            aiRouterGateway = get(),
            aiTextGateway = get(),
            localAiEngineGateway = get(),
        )
    }
    viewModel { (providerId: String?, modelProfileId: String?) ->
        AiModelEditViewModel(
            initialProviderId = providerId,
            initialModelProfileId = modelProfileId,
            aiProfileGateway = get(),
            aiTextGateway = get()
        )
    }
    viewModelOf(::TocViewModel)
    viewModelOf(::ImportBookViewModel)
    viewModelOf(::RemoteBookViewModel)
    viewModelOf(::ServerConfigViewModel)
    viewModelOf(::ServersViewModel)
    viewModelOf(::BookInfoViewModel)
    viewModelOf(::BookInfoEditViewModel)
    viewModelOf(::ReadMangaViewModel)
    viewModel {
        ReadBookViewModel(
            application = get(),
            getReadingProgressUseCase = get(),
            uploadReadingProgressUseCase = get(),
            translateChapterUseCase = get(),
            readSettingsRepository = get(),
            readBookStyleConfigRepository = get(),
            readAloudSettingsRepository = get(),
            localPreferencesRepository = get(),
            highlightRuleRepository = get(),
            uploadRepository = get(),
            changeBookSourceUseCase = get(),
            generateChapterSummaryUseCase = get(),
            cleanSelectedTextUseCase = get(),
            aiTextFactoryUseCase = get(),
            saveBookContentProcessUseCase = get(),
            bookContentProcessGateway = get(),
            aiArtifactGateway = get(),
            aiPromptPresetGateway = get(),
            aiProfileGateway = get(),
            aiRouterGateway = get(),
            dictionaryGateway = get(),
            quickDictionaryGateway = get(),
            quickTranslationGateway = get(),
            accountEntitlementUseCase = get(),
        )
    }
    viewModelOf(::ChangeCoverViewModel)
    viewModelOf(::ChangeBookSourceComposeViewModel)
    viewModelOf(::ChangeBookSourceViewModel)
    viewModelOf(::ChangeChapterSourceViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::RssViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::BookCacheManageViewModel)
    viewModel {
        BookshelfManageScreenViewModel(
            application = get(),
            bookRepository = get(),
            bookGroupRepository = get(),
            searchRepository = get(),
            bookshelfManageScreenConfig = get(),
            batchCacheDownloadUseCase = get(),
            cacheBookChaptersUseCase = get(),
            changeBookSourceUseCase = get(),
            clearBookCacheUseCase = get(),
            deleteBooksUseCase = get(),
            updateBooksGroupUseCase = get()
        )
    }

    viewModel { (route: ReplaceEditRoute) ->
        ReplaceEditViewModel(
            app = get(),
            replaceRuleDao = get(),
            route = route
        )
    }

    viewModel { (route: MainRouteSearchContent) ->
        SearchContentViewModel(
            bookUrl = route.bookUrl,
            initialSearchWord = route.searchWord,
            searchResultIndex = route.searchResultIndex,
            bookRepository = get(),
            searchContentRepository = get()
        )
    }
}

@TargetApi(Build.VERSION_CODES.P)
private fun platformImageDecoderFactory(): ImageDecoderDecoder.Factory =
    ImageDecoderDecoder.Factory()

private const val RAW_AI_TEXT_GATEWAY = "rawAiTextGateway"
