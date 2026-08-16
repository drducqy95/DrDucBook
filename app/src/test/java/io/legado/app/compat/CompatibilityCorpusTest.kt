package io.legado.app.compat

import android.app.Application
import android.net.Uri
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.RssSource
import io.legado.app.data.repository.vbook.VbookRegistryParser
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.domain.model.VbookRegistryOrigin
import io.legado.app.help.vbook.VbookExecutor
import io.legado.app.help.vbook.VbookPluginAdapter
import io.legado.app.help.vbook.VbookPluginImporter
import io.legado.app.help.vbook.VbookPluginInspector
import io.legado.app.help.book.isVideo
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CompatibilityCorpusTest {

    private val application: Application
        get() = RuntimeEnvironment.getApplication()

    private val pluginRoot: File
        get() = File(application.filesDir, "vbook_plugins")

    @Before
    fun clearPluginState() {
        application.injectAsAppCtx()
        pluginRoot.deleteRecursively()
    }

    @After
    fun cleanPluginState() {
        pluginRoot.deleteRecursively()
    }

    @Test
    fun legadoSourceFixturesParseWithoutLosingJavascriptFields() {
        val bookSource = GSON.fromJson(
            resourceText("compat/legado/book-source.json"),
            BookSource::class.java,
        )
        val rssSource = GSON.fromJson(
            resourceText("compat/legado/rss-source.json"),
            RssSource::class.java,
        )
        val httpTts = HttpTTS.fromJson(resourceText("compat/legado/http-tts.json")).getOrThrow()

        assertEquals("https://books.example.test", bookSource.bookSourceUrl)
        assertTrue(bookSource.enabledCookieJar == true)
        assertTrue(bookSource.loginCheckJs?.contains("cookie.getCookie") == true)
        assertEquals("article@html", bookSource.ruleContent?.content)

        assertEquals("https://rss.example.test/feed", rssSource.sourceUrl)
        assertTrue(rssSource.enabledCookieJar == true)
        assertTrue(rssSource.loginCheckJs?.contains("cookie.getCookie") == true)
        assertEquals("ASK_CROSS_ORIGIN", rssSource.redirectPolicy)

        assertEquals(900000000001L, httpTts.id)
        assertTrue(httpTts.url.startsWith("@js:"))
        assertEquals("audio/mpeg", httpTts.contentType)
    }

    @Test
    fun vbookRegistryContainsEverySupportedCompatibilityKind() {
        val snapshot = VbookRegistryParser.parse(
            json = resourceText("compat/vbook/registry.json"),
            fetchedAt = 1L,
            origin = VbookRegistryOrigin.NETWORK,
        )

        assertEquals(6, snapshot.items.size)
        assertEquals(0, snapshot.rejectedItemCount)
        assertEquals(6, snapshot.items.map { it.pluginId }.toSet().size)
        assertEquals(
            PLUGINS.map { it.kind }.toSet(),
            snapshot.items.map { it.declaredKind }.toSet(),
        )
    }

    @Test
    fun vbookFixturesInspectAndExecuteInsideTheProductionRuntime() {
        PLUGINS.forEach { fixture ->
            val directory = installPlugin(fixture)
            val profile = VbookPluginInspector.inspectInstalled(
                pluginDirectory = directory,
                pluginId = fixture.pluginId,
                inspectedAt = 1L,
            )

            assertEquals(fixture.kind, profile.declaredKind)
            assertEquals(fixture.sourceType, VbookPluginInspector.preferredBookSourceType(profile))

            val executor = VbookExecutor(application, fixture.pluginId, OkHttpClient())
            assertTrue(executor.isVbookSource())
            fixture.scripts.forEach { scriptName ->
                assertTrue(executor.hasScript(scriptName))
                val result = JSONObject(
                    executor.executeScript(
                        scriptName = scriptName,
                        functionName = "execute",
                        args = emptyArray(),
                        configUrl = "https://config.example.test/${fixture.directory}",
                    )
                )
                assertTrue("${fixture.directory}/$scriptName failed", result.getBoolean("success"))
                assertNotNull(result.opt("data"))
            }
        }
    }

    @Test
    fun vbookPublicFixturesImportThroughImporterAndRun() = runBlocking {
        PLUGINS.forEach { fixture ->
            val zipFile = File(application.cacheDir, "${fixture.directory}-fixture.zip")
            zipFile.writeBytes(pluginZipBytes(fixture))
            if (fixture.sourceType == null) {
                assertThrows(IOException::class.java) {
                    runBlocking {
                        VbookPluginImporter.import(application, Uri.fromFile(zipFile))
                    }
                }
                return@forEach
            }

            val source = VbookPluginImporter.import(application, Uri.fromFile(zipFile))
            assertEquals(fixture.sourceType, source.bookSourceType)
            assertTrue(source.bookSourceUrl.startsWith(VbookPluginAdapter.SOURCE_PREFIX))
            val pluginId = source.bookSourceUrl.removePrefix(VbookPluginAdapter.SOURCE_PREFIX)
            val executor = VbookExecutor(application, pluginId, OkHttpClient())
            fixture.scripts.forEach { scriptName ->
                val result = JSONObject(
                    executor.executeScript(
                        scriptName = scriptName,
                        functionName = "execute",
                        args = emptyArray(),
                        configUrl = "https://config.example.test/${fixture.directory}",
                    )
                )
                assertTrue("${fixture.directory}/$scriptName failed after import", result.getBoolean("success"))
            }
            if (fixture.sourceType == BookSourceType.video) {
                val books = VbookPluginAdapter.search(source, "compatibility", page = 1)
                val videoBook = books.single()
                assertEquals(BookType.video, videoBook.type and BookType.allBookType)
                assertTrue(videoBook.toBook().isVideo)
                assertEquals(source.bookSourceUrl, videoBook.origin)
            }
        }
    }

    @Test
    fun publicProviderDeepLinkAndWebContractsAreCompleteAndUnique() {
        val provider = JSONObject(resourceText("compat/contracts/reader-provider.json"))
        val providerOperations = provider.getJSONArray("operations")
        assertEquals("${'$'}{applicationId}.readerProvider", provider.getString("authorityTemplate"))
        assertEquals(16, providerOperations.length())
        assertEquals(16, uniqueOperations(providerOperations))

        val web = JSONObject(resourceText("compat/contracts/web-service.json"))
        val http = web.getJSONArray("http")
        val sockets = web.getJSONArray("webSocket")
        assertEquals(27, http.length())
        assertEquals(27, uniqueOperations(http))
        assertEquals(3, sockets.length())
        assertEquals(3, (0 until sockets.length()).map { sockets.getString(it) }.toSet().size)

        val deepLinks = JSONObject(resourceText("compat/contracts/deep-links.json"))
        val importSchemes = deepLinks.getJSONObject("onlineImport").getJSONArray("schemes")
        assertEquals(setOf("legado", "yuedu"), importSchemes.toStringSet())
        val fileSchemes = deepLinks.getJSONObject("fileAssociation").getJSONArray("schemes")
        assertEquals(setOf("app", "content", "file"), fileSchemes.toStringSet())
    }

    @Test
    fun provenanceCoversEveryPayloadAndMatchesSha256() {
        val provenance = JSONObject(resourceText("compat/provenance.json"))
        val fixtures = provenance.getJSONArray("fixtures")
        val recordedPaths = mutableSetOf<String>()

        for (index in 0 until fixtures.length()) {
            val item = fixtures.getJSONObject(index)
            val path = item.getString("path")
            assertTrue("Duplicate provenance path: $path", recordedPaths.add(path))
            assertEquals("synthetic", item.getString("origin"))
            assertEquals("CC0-1.0", item.getString("license"))
            assertEquals(item.getString("sha256"), sha256(resourceBytes("compat/$path")))
        }

        assertEquals(PAYLOAD_PATHS, recordedPaths)
        assertFalse(recordedPaths.any { it.endsWith("README.md") || it.endsWith("provenance.json") })
    }

    private fun installPlugin(fixture: PluginFixture): File {
        val directory = File(pluginRoot, fixture.pluginId)
        val sourceDirectory = File(directory, "src")
        check(sourceDirectory.mkdirs())
        File(directory, "plugin.json").writeText(
            resourceText("compat/vbook/plugins/${fixture.directory}/plugin.json"),
            Charsets.UTF_8,
        )
        fixture.scripts.forEach { scriptName ->
            File(sourceDirectory, scriptName).writeText(
                resourceText("compat/vbook/plugins/${fixture.directory}/src/$scriptName"),
                Charsets.UTF_8,
            )
        }
        return directory
    }

    private fun pluginZipBytes(fixture: PluginFixture): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("plugin.json"))
            zip.write(resourceBytes("compat/vbook/plugins/${fixture.directory}/plugin.json"))
            zip.closeEntry()
            fixture.scripts.forEach { scriptName ->
                zip.putNextEntry(ZipEntry("src/$scriptName"))
                zip.write(resourceBytes("compat/vbook/plugins/${fixture.directory}/src/$scriptName"))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun uniqueOperations(array: org.json.JSONArray): Int = (0 until array.length())
        .map { index ->
            val item = array.getJSONObject(index)
            "${item.getString("method")}:${item.getString("path")}"
        }
        .toSet()
        .size

    private fun org.json.JSONArray.toStringSet(): Set<String> =
        (0 until length()).map { getString(it) }.toSet()

    private fun resourceText(path: String): String =
        resourceBytes(path).toString(Charsets.UTF_8)

    private fun resourceBytes(path: String): ByteArray =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing compatibility resource: $path"
        }.use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class PluginFixture(
        val directory: String,
        val pluginId: String,
        val kind: VbookPluginKind,
        val sourceType: Int?,
        val scripts: List<String>,
    )

    private companion object {
        val PLUGINS = listOf(
            PluginFixture("text", "000000000000000000000001", VbookPluginKind.TEXT, BookSourceType.default, listOf("chap.js")),
            PluginFixture("comic", "000000000000000000000002", VbookPluginKind.COMIC, BookSourceType.image, listOf("chap.js")),
            PluginFixture("audio", "000000000000000000000003", VbookPluginKind.AUDIOBOOK, BookSourceType.audio, listOf("track.js")),
            PluginFixture("video", "000000000000000000000004", VbookPluginKind.VIDEO, BookSourceType.video, listOf("search.js", "track.js")),
            PluginFixture("tts", "000000000000000000000005", VbookPluginKind.TTS, null, listOf("voice.js", "tts.js")),
            PluginFixture("translator", "000000000000000000000006", VbookPluginKind.TRANSLATOR, null, listOf("translate.js")),
        )

        val PAYLOAD_PATHS = setOf(
            "legado/book-source.json",
            "legado/rss-source.json",
            "legado/http-tts.json",
            "vbook/registry.json",
            "vbook/plugins/text/plugin.json",
            "vbook/plugins/text/src/chap.js",
            "vbook/plugins/comic/plugin.json",
            "vbook/plugins/comic/src/chap.js",
            "vbook/plugins/audio/plugin.json",
            "vbook/plugins/audio/src/track.js",
            "vbook/plugins/video/plugin.json",
            "vbook/plugins/video/src/search.js",
            "vbook/plugins/video/src/track.js",
            "vbook/plugins/tts/plugin.json",
            "vbook/plugins/tts/src/voice.js",
            "vbook/plugins/tts/src/tts.js",
            "vbook/plugins/translator/plugin.json",
            "vbook/plugins/translator/src/translate.js",
            "contracts/reader-provider.json",
            "contracts/web-service.json",
            "contracts/deep-links.json",
        )
    }
}
