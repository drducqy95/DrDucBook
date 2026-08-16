package io.legado.app.help.vbook

import android.app.Application
import io.legado.app.constant.BookType
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookPluginAdapterTest {

    @Before
    fun setUpAppContext() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun `chapters skips section rows and keeps playable episode urls`() = runBlocking {
        val pluginId = "0123456789abcdef01234567"
        writePlugin(
            pluginId = pluginId,
            type = "video",
            scripts = mapOf(
                "toc.js" to """
                    function execute() {
                        return Response.success([
                            {name: "Server 1", type: "section"},
                            {name: "Tap 1", url: "/episode-1.m3u8"}
                        ]);
                    }
                """.trimIndent(),
            ),
            scriptMap = """"toc": "toc.js"""",
        )

        val chapters = VbookPluginAdapter.chapters(
            source = source(pluginId, BookSourceType.video),
            book = book(pluginId),
        )

        assertEquals(1, chapters.size)
        assertEquals("Tap 1", chapters.single().title)
        assertEquals("https://vbook.test/episode-1.m3u8", chapters.single().url)
    }

    @Test
    fun `chapters unwraps legacy object envelopes and id style urls`() = runBlocking {
        val pluginId = "111111111111111111111111"
        writePlugin(
            pluginId = pluginId,
            type = "comic",
            scripts = mapOf(
                "toc.js" to """
                    function execute() {
                        return Response.success({
                            chapters: [
                                { title: "Chapter 1", chapter_id: "chap-1" },
                                ["Chapter 2", "/comic/chapter-2"]
                            ]
                        });
                    }
                """.trimIndent(),
            ),
            scriptMap = """"toc": "toc.js"""",
        )

        val chapters = VbookPluginAdapter.chapters(
            source = source(pluginId, BookSourceType.image),
            book = book(pluginId),
        )

        assertEquals(2, chapters.size)
        assertEquals("Chapter 1", chapters[0].title)
        assertEquals("https://vbook.test/chap-1", chapters[0].url)
        assertEquals("Chapter 2", chapters[1].title)
        assertEquals("https://vbook.test/comic/chapter-2", chapters[1].url)
    }

    @Test
    fun `text content strips html paragraph tags`() = runBlocking {
        val pluginId = "abcdef0123456789abcdef01"
        writePlugin(
            pluginId = pluginId,
            type = "novel",
            scripts = mapOf(
                "chap.js" to """
                    function execute() {
                        return Response.success("<p>Nguoi chet chim chi len troi.</p><br>Thien Huyen Dai Luc.<br>&nbsp;Dong Huyen Vuc.");
                    }
                """.trimIndent(),
            ),
            scriptMap = """"chap": "chap.js"""",
        )

        val content = VbookPluginAdapter.content(
            source = source(pluginId, BookSourceType.default),
            book = book(pluginId),
            chapter = io.legado.app.data.entities.BookChapter(
                bookUrl = "https://vbook.test/book",
                title = "Chap",
                url = "https://vbook.test/chapter",
            ),
            needSave = false,
        )

        assertEquals("Nguoi chet chim chi len troi.\n\nThien Huyen Dai Luc.\n\nDong Huyen Vuc.", content)
    }

    @Test
    fun `comic content converts legacy image arrays to reader images`() = runBlocking {
        val pluginId = "fedcba987654321001234567"
        writePlugin(
            pluginId = pluginId,
            type = "comic",
            scripts = mapOf(
                "chap.js" to """
                    function execute() {
                        return Response.success({
                            images: [
                                "https://img.test/1.jpg",
                                "https://img.test/2.jpg"
                            ]
                        });
                    }
                """.trimIndent(),
            ),
            scriptMap = """"chap": "chap.js"""",
        )

        val content = VbookPluginAdapter.content(
            source = source(pluginId, BookSourceType.image),
            book = book(pluginId),
            chapter = io.legado.app.data.entities.BookChapter(
                bookUrl = "https://vbook.test/book",
                title = "Chap",
                url = "https://vbook.test/chapter",
            ),
            needSave = false,
        )

        assertEquals(
            """<img src="https://img.test/1.jpg,{"headers":{"Referer":"https://vbook.test/chapter"}}">""" +
                "\n\n" +
                """<img src="https://img.test/2.jpg,{"headers":{"Referer":"https://vbook.test/chapter"}}">""",
            content,
        )
        val imageMatcher = AppPattern.imgPattern.matcher(content)
        assertTrue(imageMatcher.find())
        val analyzedImage = AnalyzeUrl(
            mUrl = requireNotNull(imageMatcher.group(1)),
            headerMapF = emptyMap(),
        )
        assertEquals("https://img.test/1.jpg", analyzedImage.url)
        assertEquals("https://vbook.test/chapter", analyzedImage.headerMap["Referer"])
    }

    @Test
    fun `comic content prefers fallback images over lazy placeholders`() = runBlocking {
        val pluginId = "222222222222222222222222"
        writePlugin(
            pluginId = pluginId,
            type = "comic",
            scripts = mapOf(
                "chap.js" to """
                    function execute() {
                        return Response.success([
                            {
                                link: "https://cdn.test/loading.gif",
                                fallback: ["https://cdn.test/page-1.webp"]
                            },
                            { src: "//cdn.test/page-2.jpg" }
                        ]);
                    }
                """.trimIndent(),
            ),
            scriptMap = """"chap": "chap.js"""",
        )

        val content = VbookPluginAdapter.content(
            source = source(pluginId, BookSourceType.image),
            book = book(pluginId),
            chapter = io.legado.app.data.entities.BookChapter(
                bookUrl = "https://vbook.test/book",
                title = "Chap",
                url = "https://vbook.test/chapter",
            ),
            needSave = false,
        )

        assertEquals(
            """<img src="https://cdn.test/page-1.webp,{"headers":{"Referer":"https://vbook.test/chapter"}}">""" +
                "\n\n" +
                """<img src="https://cdn.test/page-2.jpg,{"headers":{"Referer":"https://vbook.test/chapter"}}">""",
            content,
        )
    }

    @Test
    fun `comic source imported as default repairs source and book type`() = runBlocking {
        val pluginId = "333333333333333333333333"
        writePlugin(
            pluginId = pluginId,
            type = "comic",
            scripts = mapOf(
                "chap.js" to """
                    function execute() {
                        return Response.success(["https://cdn.test/page.jpg"]);
                    }
                """.trimIndent(),
            ),
            scriptMap = """"chap": "chap.js"""",
        )
        val source = source(pluginId, BookSourceType.default)
        val book = book(pluginId).apply { type = BookType.text }

        val content = VbookPluginAdapter.content(
            source = source,
            book = book,
            chapter = io.legado.app.data.entities.BookChapter(
                bookUrl = "https://vbook.test/book",
                title = "Chap",
                url = "https://vbook.test/chapter",
            ),
            needSave = false,
        )

        assertEquals(BookSourceType.image, source.bookSourceType)
        assertEquals(BookType.image, book.type and BookType.image)
        assertEquals(
            """<img src="https://cdn.test/page.jpg,{"headers":{"Referer":"https://vbook.test/chapter"}}">""",
            content,
        )
    }

    @Test
    fun `plugin default host is not replaced by metadata source`() = runBlocking {
        val pluginId = "444444444444444444444444"
        writePlugin(
            pluginId = pluginId,
            type = "novel",
            scripts = mapOf(
                "config.js" to """
                    var BASE_URL = "https://m.vbook.test";
                    if (CONFIG_URL) BASE_URL = CONFIG_URL;
                """.trimIndent(),
                "search.js" to """
                    load("config.js");
                    function execute() {
                        return Response.success([{
                            name: "Mobile",
                            link: BASE_URL + "/book",
                            author: BASE_URL
                        }]);
                    }
                """.trimIndent(),
            ),
            scriptMap = """"search": "search.js"""",
        )

        val result = VbookPluginAdapter.search(source(pluginId, BookSourceType.default), "book", 1)

        assertEquals("https://m.vbook.test/book", result.single().bookUrl)
        assertEquals("https://m.vbook.test", result.single().author)
    }

    @Test
    fun `plugin config is exposed to javascript and enables login settings`() = runBlocking {
        val pluginId = "555555555555555555555555"
        writePlugin(
            pluginId = pluginId,
            type = "novel",
            scripts = mapOf(
                "search.js" to """
                    function execute() {
                        Log.log("configured");
                        return Response.success([{
                            name: "Configured",
                            link: "/book",
                            author: USER_EMAIL + "|" + localConfig.getItem("USER_TOKEN")
                        }]);
                    }
                """.trimIndent(),
            ),
            scriptMap = """"search": "search.js"""",
            config = """
                {
                  "USER_EMAIL": {
                    "title": "Email đăng nhập",
                    "mode": "input",
                    "default": "reader@example.test"
                  },
                  "USER_TOKEN": {
                    "title": "Auth Token",
                    "mode": "input",
                    "format": "multiline",
                    "default": "saved-token"
                  }
                }
            """.trimIndent(),
        )
        val source = source(pluginId, BookSourceType.default)

        val result = VbookPluginAdapter.search(source, "book", 1)

        assertEquals("reader@example.test|saved-token", result.single().author)
        assertNotNull(source.loginUi)
        assertTrue(source.loginUrl.orEmpty().startsWith("@js:"))
        assertEquals(
            "https://vbook.test",
            org.json.JSONObject(source.header.orEmpty()).getString("Referer"),
        )
    }

    private fun writePlugin(
        pluginId: String,
        type: String,
        scripts: Map<String, String>,
        scriptMap: String,
        config: String? = null,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val plugin = File(context.filesDir, "vbook_plugins/$pluginId")
        plugin.deleteRecursively()
        plugin.mkdirs()
        File(plugin, "src").mkdirs()
        File(plugin, "plugin.json").writeText(
            """
            {
              "metadata": {
                "name": "Compat",
                "author": "Tester",
                "version": 1,
                "source": "https://vbook.test",
                "type": "$type"
              },
              "script": {
                $scriptMap
              }${config?.let { ",\n  \"config\": $it" }.orEmpty()}
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        scripts.forEach { (name, body) ->
            File(plugin, "src/$name").writeText(body, Charsets.UTF_8)
        }
    }

    private fun source(pluginId: String, type: Int): BookSource = BookSource(
        bookSourceUrl = VbookPluginAdapter.SOURCE_PREFIX + pluginId,
        bookSourceName = "Compat",
        bookSourceType = type,
        searchUrl = "vbook://search",
        exploreUrl = "vbook://home",
    )

    private fun book(pluginId: String): Book = Book(
        bookUrl = "https://vbook.test/book",
        tocUrl = "https://vbook.test/book",
        origin = VbookPluginAdapter.SOURCE_PREFIX + pluginId,
        name = "Compat Book",
    )
}
