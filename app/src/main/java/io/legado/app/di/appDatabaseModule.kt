package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.dao.*
import org.koin.dsl.module

/**
 * 应用程序的数据库和 DAO 模块
 */
val appDatabaseModule = module {

    // 注册 AppDatabase 实例
    // 使用 lazy 属性 appDb，该属性在 AppDatabase.kt 中定义并已初始化
    single<AppDatabase> { appDb }

    // 注册所有的 DAO 接口，通过 AppDatabase 实例获取
    factory<BookDao> { get<AppDatabase>().bookDao }
    factory<QuickDictionaryDao> { get<AppDatabase>().quickDictionaryDao }
    factory<AiProfileDao> { get<AppDatabase>().aiProfileDao }
    factory<AiArtifactDao> { get<AppDatabase>().aiArtifactDao }
    factory<AiAgentDao> { get<AppDatabase>().aiAgentDao }
    factory<AiChatDao> { get<AppDatabase>().aiChatDao }
    factory<AiCustomToolDao> { get<AppDatabase>().aiCustomToolDao }
    factory<AiMemoryDao> { get<AppDatabase>().aiMemoryDao }
    factory<AiPromptPresetDao> { get<AppDatabase>().aiPromptPresetDao }
    factory<AiRouterDao> { get<AppDatabase>().aiRouterDao }
    factory<AiSkillDao> { get<AppDatabase>().aiSkillDao }
    factory<BookGroupDao> { get<AppDatabase>().bookGroupDao }
    factory<BookSourceDao> { get<AppDatabase>().bookSourceDao }
    factory<BookSourceHealthDao> { get<AppDatabase>().bookSourceHealthDao }
    factory<SourceCheckDao> { get<AppDatabase>().sourceCheckDao }
    factory<BookChapterDao> { get<AppDatabase>().bookChapterDao }
    factory<BookContentProcessDao> { get<AppDatabase>().bookContentProcessDao }
    factory<ReplaceRuleDao> { get<AppDatabase>().replaceRuleDao }
    factory<SearchBookDao> { get<AppDatabase>().searchBookDao }
    factory<SearchKeywordDao> { get<AppDatabase>().searchKeywordDao }
    factory<RssSourceDao> { get<AppDatabase>().rssSourceDao }
    factory<BookmarkDao> { get<AppDatabase>().bookmarkDao }
    factory<BrowserBookmarkDao> { get<AppDatabase>().browserBookmarkDao }
    factory<RssArticleDao> { get<AppDatabase>().rssArticleDao }
    factory<RssStarDao> { get<AppDatabase>().rssStarDao }
    factory<RssReadRecordDao> { get<AppDatabase>().rssReadRecordDao }
    factory<CookieDao> { get<AppDatabase>().cookieDao }
    factory<TxtTocRuleDao> { get<AppDatabase>().txtTocRuleDao }
    factory<ReadRecordDao> { get<AppDatabase>().readRecordDao }
    factory<HttpTTSDao> { get<AppDatabase>().httpTTSDao }
    factory<CacheDao> { get<AppDatabase>().cacheDao }
    factory<RuleSubDao> { get<AppDatabase>().ruleSubDao }
    factory<DictRuleDao> { get<AppDatabase>().dictRuleDao }
    factory<KeyboardAssistsDao> { get<AppDatabase>().keyboardAssistsDao }
    factory<ServerDao> { get<AppDatabase>().serverDao }
    factory<HomepageModuleDao> { get<AppDatabase>().homepageModuleDao }
    factory<HomepageCustomSetDao> { get<AppDatabase>().homepageCustomSetDao }
    factory<HighlightRuleDao> { get<AppDatabase>().highlightRuleDao }
    factory<HighlightTagRuleDao> { get<AppDatabase>().highlightTagRuleDao }
    factory<TagGroupRuleDao> { get<AppDatabase>().tagGroupRuleDao }
    factory<MediaDownloadDao> { get<AppDatabase>().mediaDownloadDao }
}
