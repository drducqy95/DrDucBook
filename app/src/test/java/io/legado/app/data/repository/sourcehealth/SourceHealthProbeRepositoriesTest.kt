package io.legado.app.data.repository.sourcehealth

import android.app.Application
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.model.ResolvedMedia
import io.legado.app.domain.model.ResolvedMediaVariant
import io.legado.app.domain.sourcehealth.SourceCheckProfile
import io.legado.app.domain.sourcehealth.SourceCheckStageEvidence
import io.legado.app.domain.sourcehealth.SourceCheckStageStatus
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.help.vbook.VbookPluginInspector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SourceHealthProbeRepositoriesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun bookAdapterBuildsFullStagesAndSkipsMissingOptionalCapabilities() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://books.example.test",
            bookSourceName = "Books",
            bookSourceType = BookSourceType.default,
            searchUrl = "https://books.example.test/search?q={{key}}",
            exploreUrl = null,
            ruleBookInfo = BookInfoRule(tocUrl = ".toc@href"),
            ruleToc = TocRule(chapterList = ".chapter", chapterName = "@text", chapterUrl = "@href"),
            ruleContent = ContentRule(content = "article@html"),
        )
        val searchBook = searchBook(source)
        val adapter = BookSourceHealthProbeRepository(
            now = tickingClock(),
            searchBooks = { _, _ -> listOf(searchBook) },
            getBookInfo = { _, book -> book.apply { tocUrl = bookUrl } },
            getChapters = { _, book ->
                listOf(
                    BookChapter(
                        title = "Chapter 1",
                        url = "chapter-1",
                        baseUrl = book.tocUrl,
                        bookUrl = book.bookUrl,
                    )
                )
            },
            getContent = { _, _, _ -> "Chapter body" },
        )

        val result = adapter.probe(source, SourceCheckProfile.FULL)

        assertEquals(
            listOf("reachability", "search", "explore", "detail", "toc", "content", "media"),
            result.stages.map { it.stageKey },
        )
        val statuses = result.stages.statusByKey()
        assertEquals(SourceCheckStageStatus.PASSED, statuses["reachability"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["search"])
        assertEquals(SourceCheckStageStatus.SKIPPED, statuses["explore"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["detail"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["toc"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["content"])
        assertEquals(SourceCheckStageStatus.SKIPPED, statuses["media"])
    }

    @Test
    fun bookAdapterLimitsStagesByProfileDepth() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://books-depth.example.test",
            bookSourceName = "Books",
            searchUrl = "https://books-depth.example.test/search?q={{key}}",
            ruleBookInfo = BookInfoRule(tocUrl = ".toc@href"),
            ruleToc = TocRule(chapterList = ".chapter", chapterName = "@text", chapterUrl = "@href"),
            ruleContent = ContentRule(content = "article@html"),
        )
        val searchBook = searchBook(source)
        val adapter = BookSourceHealthProbeRepository(
            now = tickingClock(),
            searchBooks = { _, _ -> listOf(searchBook) },
            getBookInfo = { _, book -> book.apply { tocUrl = bookUrl } },
            getChapters = { _, book ->
                listOf(
                    BookChapter(
                        title = "Chapter 1",
                        url = "chapter-1",
                        baseUrl = book.tocUrl,
                        bookUrl = book.bookUrl,
                    )
                )
            },
            getContent = { _, _, _ -> error("Content should only run for Full profile") },
        )

        assertEquals(
            listOf("reachability", "search", "explore"),
            adapter.probe(source, SourceCheckProfile.QUICK).stages.map { it.stageKey },
        )
        assertEquals(
            listOf("reachability", "search", "explore", "detail", "toc"),
            adapter.probe(source, SourceCheckProfile.STANDARD).stages.map { it.stageKey },
        )
    }

    @Test
    fun rssAdapterRunsFeedListArticleAndContentStages() = runBlocking {
        val source = RssSource(
            sourceUrl = "https://rss.example.test/feed",
            sourceName = "RSS",
            ruleContent = "article",
        )
        val article = RssArticle(
            origin = source.sourceUrl,
            sort = source.sourceName,
            title = "Article 1",
            link = "https://rss.example.test/article-1",
        )
        val adapter = RssSourceHealthProbeRepository(
            now = tickingClock(),
            getArticles = { _, _, _, _ -> mutableListOf(article) to null },
            getContent = { _, _, _ -> "Article body" },
        )

        val result = adapter.probe(source, SourceCheckProfile.FULL)

        assertEquals(
            listOf("feed", "list", "article", "content"),
            result.stages.map { it.stageKey },
        )
        assertTrue(result.stages.all { it.status == SourceCheckStageStatus.PASSED })
    }

    @Test
    fun rssAdapterLimitsStagesByProfileDepth() = runBlocking {
        val source = RssSource(
            sourceUrl = "https://rss-depth.example.test/feed",
            sourceName = "RSS",
            ruleContent = "article",
        )
        val article = RssArticle(
            origin = source.sourceUrl,
            sort = source.sourceName,
            title = "Article 1",
            link = "https://rss-depth.example.test/article-1",
        )
        val adapter = RssSourceHealthProbeRepository(
            now = tickingClock(),
            getArticles = { _, _, _, _ -> mutableListOf(article) to null },
            getContent = { _, _, _ -> error("Content should only run for Full profile") },
        )

        assertEquals(
            listOf("feed", "list"),
            adapter.probe(source, SourceCheckProfile.QUICK).stages.map { it.stageKey },
        )
        assertEquals(
            listOf("feed", "list", "article"),
            adapter.probe(source, SourceCheckProfile.STANDARD).stages.map { it.stageKey },
        )
    }

    @Test
    fun vbookAdapterUsesCapabilityProfileAndSkipsReadableContentForVideoPlugin() = runBlocking {
        val pluginId = "0123456789abcdef01234567"
        val manifestJson = vbookManifestJson()
        val pluginRoot = temporaryFolder.newFolder("vbook_plugins")
        val pluginDir = File(pluginRoot, pluginId).apply { mkdirs() }
        File(pluginDir, "plugin.json").writeText(manifestJson)
        val capabilityProfile = VbookPluginInspector.inspect(
            manifestJson = manifestJson,
            scripts = mapOf(
                "track.js" to """return Response.success({data: "https://cdn.example.test/master.m3u8"});""",
            ),
            pluginId = pluginId,
            inspectedAt = 1L,
        )
        val source = BookSource(
            bookSourceUrl = "${VbookPluginAdapter.SOURCE_PREFIX}$pluginId",
            bookSourceName = "Video Plugin",
            bookSourceType = BookSourceType.video,
        )
        val item = searchBook(source)
        val adapter = VbookSourceHealthProbeRepository(
            now = tickingClock(),
            pluginRoot = pluginRoot,
            inspectProfile = { _, _, _ -> capabilityProfile },
            exploreKinds = { listOf(ExploreKind(title = "Home", url = "vbook://discover?input=home")) },
            exploreBooks = { _, _ -> listOf(item) },
            searchBooks = { _, _ -> listOf(item) },
            enrichBookInfo = { _, book -> book.apply { tocUrl = bookUrl } },
            getChapters = { _, book ->
                listOf(
                    BookChapter(
                        title = "Episode 1",
                        url = "episode-1",
                        baseUrl = book.tocUrl,
                        bookUrl = book.bookUrl,
                    )
                )
            },
            getContent = { _, _, _ -> error("Readable content should be skipped for video") },
            resolveMedia = { _, book, chapter ->
                ResolvedMedia(
                    sourceId = source.bookSourceUrl,
                    contentId = chapter.url,
                    title = book.name,
                    variants = listOf(
                        ResolvedMediaVariant(
                            id = "variant-1",
                            title = "1080p",
                            uri = "https://cdn.example.test/master.m3u8",
                            contentKind = MediaContentKind.VIDEO,
                            protocol = MediaProtocol.HLS,
                            mimeType = "application/x-mpegURL",
                            headers = emptyMap(),
                            referer = "",
                            expiresAt = null,
                            downloadSupported = true,
                            externalPlayerRequired = false,
                        )
                    ),
                    subtitles = emptyList(),
                    audioTracks = emptyList(),
                    resolvedAt = 1L,
                )
            },
        )

        assertTrue(adapter.supports(source))
        val result = adapter.probe(source, SourceCheckProfile.FULL)

        assertEquals(
            listOf("manifest", "scripts", "home", "search", "detail", "toc", "content", "track"),
            result.stages.map { it.stageKey },
        )
        val statuses = result.stages.statusByKey()
        assertEquals(SourceCheckStageStatus.PASSED, statuses["manifest"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["scripts"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["home"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["search"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["detail"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["toc"])
        assertEquals(SourceCheckStageStatus.SKIPPED, statuses["content"])
        assertEquals(SourceCheckStageStatus.PASSED, statuses["track"])
    }

    @Test
    fun vbookAdapterLimitsStagesByProfileDepth() = runBlocking {
        val pluginId = "abcdef0123456789abcdef01"
        val manifestJson = vbookManifestJson()
        val pluginRoot = temporaryFolder.newFolder("vbook_profile_depth")
        val pluginDir = File(pluginRoot, pluginId).apply { mkdirs() }
        File(pluginDir, "plugin.json").writeText(manifestJson)
        val capabilityProfile = VbookPluginInspector.inspect(
            manifestJson = manifestJson,
            scripts = mapOf(
                "track.js" to """return Response.success({data: "https://cdn.example.test/master.m3u8"});""",
            ),
            pluginId = pluginId,
            inspectedAt = 1L,
        )
        val source = BookSource(
            bookSourceUrl = "${VbookPluginAdapter.SOURCE_PREFIX}$pluginId",
            bookSourceName = "Video Plugin",
            bookSourceType = BookSourceType.video,
        )
        val item = searchBook(source)
        val adapter = VbookSourceHealthProbeRepository(
            now = tickingClock(),
            pluginRoot = pluginRoot,
            inspectProfile = { _, _, _ -> capabilityProfile },
            exploreKinds = { listOf(ExploreKind(title = "Home", url = "vbook://discover?input=home")) },
            exploreBooks = { _, _ -> listOf(item) },
            searchBooks = { _, _ -> listOf(item) },
            enrichBookInfo = { _, book -> book.apply { tocUrl = bookUrl } },
            getChapters = { _, book ->
                listOf(
                    BookChapter(
                        title = "Episode 1",
                        url = "episode-1",
                        baseUrl = book.tocUrl,
                        bookUrl = book.bookUrl,
                    )
                )
            },
            getContent = { _, _, _ -> error("Content should only run for Full profile") },
            resolveMedia = { _, _, _ -> error("Track should only run for Full profile") },
        )

        assertEquals(
            listOf("manifest", "scripts"),
            adapter.probe(source, SourceCheckProfile.QUICK).stages.map { it.stageKey },
        )
        assertEquals(
            listOf("manifest", "scripts", "home", "search", "detail", "toc"),
            adapter.probe(source, SourceCheckProfile.STANDARD).stages.map { it.stageKey },
        )
    }

    private fun searchBook(source: BookSource): SearchBook = SearchBook(
        bookUrl = "${source.bookSourceUrl}/book/1",
        origin = source.bookSourceUrl,
        originName = source.bookSourceName,
        name = "Sample",
        author = "Author",
        tocUrl = "${source.bookSourceUrl}/book/1",
    )

    private fun List<SourceCheckStageEvidence>.statusByKey(): Map<String, SourceCheckStageStatus> =
        associate { it.stageKey to it.status }

    private fun tickingClock(): () -> Long {
        var value = 1_000L
        return {
            value += 10L
            value
        }
    }

    private fun vbookManifestJson(): String = """
        {
          "metadata": {
            "name": "Video Fixture",
            "author": "Tester",
            "version": 1,
            "source": "https://video.example.test",
            "description": "Synthetic video plugin",
            "type": "video"
          },
          "script": {
            "home": "home.js",
            "search": "search.js",
            "detail": "detail.js",
            "toc": "toc.js",
            "chap": "chap.js",
            "track": "track.js"
          }
        }
    """.trimIndent()
}
