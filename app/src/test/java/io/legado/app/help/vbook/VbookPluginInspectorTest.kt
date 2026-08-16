package io.legado.app.help.vbook

import io.legado.app.constant.BookSourceType
import io.legado.app.domain.model.VbookCapability
import io.legado.app.domain.model.VbookPluginKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VbookPluginInspectorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `detects video media and request requirements from manifest and scripts`() {
        val profile = VbookPluginInspector.inspect(
            manifestJson = manifest(
                type = "video",
                scripts = """
                    "home": "home.js",
                    "detail": "detail.js",
                    "search": "search.js",
                    "toc": "toc.js",
                    "chap": "chap.js",
                    "track": "track.js"
                """.trimIndent(),
            ),
            scripts = mapOf(
                "chap.js" to """return { data: "https://cdn.test/master.m3u8" };""",
                "track.js" to """
                    return {
                      type: "iframe",
                      headers: { Referer: "https://video.test/" }
                    };
                """.trimIndent(),
            ),
            pluginId = PLUGIN_ID,
            inspectedAt = 1L,
        )

        assertEquals(VbookPluginKind.VIDEO, profile.declaredKind)
        assertTrue(VbookCapability.VIDEO_CONTENT in profile.capabilities)
        assertTrue(VbookCapability.MEDIA_TRACK in profile.capabilities)
        assertTrue(VbookCapability.HLS in profile.capabilities)
        assertTrue(VbookCapability.CUSTOM_HEADERS in profile.capabilities)
        assertTrue(VbookCapability.REFERER in profile.capabilities)
        assertTrue(VbookCapability.EXTERNAL_PLAYER in profile.capabilities)
        assertTrue(VbookCapability.DOWNLOAD in profile.capabilities)
        assertTrue(VbookCapability.EXPORT in profile.capabilities)
        assertEquals(BookSourceType.video, VbookPluginInspector.preferredBookSourceType(profile))
    }

    @Test
    fun `does not classify a text plugin as audiobook from its name or description`() {
        val profile = VbookPluginInspector.inspect(
            manifestJson = manifest(
                type = "novel",
                name = "Audio Truyện Full",
                description = "Đọc truyện chữ, truyện audio online",
                scripts = """
                    "home": "home.js",
                    "search": "search.js",
                    "toc": "toc.js",
                    "chap": "chap.js"
                """.trimIndent(),
            ),
            scripts = mapOf("chap.js" to "return Response.success(document.html());"),
            pluginId = PLUGIN_ID,
            inspectedAt = 1L,
        )

        assertTrue(VbookCapability.TEXT_CONTENT in profile.capabilities)
        assertFalse(VbookCapability.AUDIO_CONTENT in profile.capabilities)
        assertEquals(BookSourceType.default, VbookPluginInspector.preferredBookSourceType(profile))
    }

    @Test
    fun `can discover audiobook capability independently from declared novel type`() {
        val profile = VbookPluginInspector.inspect(
            manifestJson = manifest(
                type = "novel",
                scripts = """
                    "toc": "toc.js",
                    "chap": "chap.js",
                    "track": "track.js"
                """.trimIndent(),
            ),
            scripts = mapOf(
                "track.js" to """return Response.success({data: "https://cdn.test/book.m4a"});"""
            ),
            pluginId = PLUGIN_ID,
            inspectedAt = 1L,
        )

        assertTrue(VbookCapability.AUDIO_CONTENT in profile.capabilities)
        assertTrue(VbookCapability.DIRECT_AUDIO in profile.capabilities)
        assertEquals(BookSourceType.audio, VbookPluginInspector.preferredBookSourceType(profile))
    }

    @Test
    fun `tts is a service plugin and never becomes a book source`() {
        val profile = VbookPluginInspector.inspect(
            manifestJson = manifest(
                type = "tts",
                scripts = """
                    "voice": "voice.js",
                    "tts": "tts.js"
                """.trimIndent(),
            ),
            scripts = mapOf(
                "tts.js" to """return fetch(url, {headers: {format: "mp3"}});"""
            ),
            pluginId = PLUGIN_ID,
            inspectedAt = 1L,
        )

        assertTrue(VbookCapability.TTS_VOICE_LIST in profile.capabilities)
        assertTrue(VbookCapability.TTS_SYNTHESIS in profile.capabilities)
        assertFalse(VbookCapability.DIRECT_AUDIO in profile.capabilities)
        assertFalse(VbookCapability.DOWNLOAD in profile.capabilities)
        assertNull(VbookPluginInspector.preferredBookSourceType(profile))
    }

    @Test
    fun `persists profile and merges new runtime media evidence`() {
        val directory = temporaryFolder.newFolder("plugin")
        val profile = VbookPluginInspector.inspect(
            manifestJson = manifest(
                type = "video",
                scripts = """"track": "track.js"""",
            ),
            scripts = mapOf("track.js" to "return Response.success(data);"),
            pluginId = PLUGIN_ID,
            inspectedAt = 1L,
        )
        VbookPluginInspector.writeProfile(directory, profile)

        val restored = VbookPluginInspector.readProfile(directory)!!
        val merged = VbookPluginInspector.mergeRuntimeResult(
            profile = restored,
            role = "track",
            resultJson = """
                {
                  "success": true,
                  "data": {
                    "data": "https://cdn.test/master.m3u8",
                    "headers": {"Referer": "https://video.test/"}
                  }
                }
            """.trimIndent(),
            inspectedAt = 2L,
        )

        assertTrue(VbookCapability.HLS in merged.capabilities)
        assertTrue(VbookCapability.CUSTOM_HEADERS in merged.capabilities)
        assertTrue(VbookCapability.REFERER in merged.capabilities)
        assertTrue(VbookCapability.DOWNLOAD in merged.capabilities)
        assertEquals(2L, merged.inspectedAt)
    }

    @Test
    fun `inspectInstalled reads safe nested script paths`() {
        val directory = temporaryFolder.newFolder("nested-plugin")
        val scripts = directory.resolve("src/nested").apply { mkdirs() }
        directory.resolve("plugin.json").writeText(
            manifest(
                type = "novel",
                scripts = """"chap": "nested/chap.js"""",
            ),
            Charsets.UTF_8,
        )
        scripts.resolve("chap.js").writeText(
            """function execute() { return Response.success("chapter"); }""",
            Charsets.UTF_8,
        )

        val profile = VbookPluginInspector.inspectInstalled(
            pluginDirectory = directory,
            pluginId = PLUGIN_ID,
            inspectedAt = 3L,
        )

        assertTrue("chap" in profile.scriptRoles)
        assertTrue(VbookCapability.TEXT_CONTENT in profile.capabilities)
        assertEquals(BookSourceType.default, VbookPluginInspector.preferredBookSourceType(profile))
    }

    private fun manifest(
        type: String,
        scripts: String,
        name: String = "Test",
        description: String = "",
    ): String {
        return """
            {
              "metadata": {
                "name": "$name",
                "author": "Tester",
                "version": 1,
                "source": "https://example.com",
                "description": "$description",
                "type": "$type"
              },
              "script": {
                $scripts
              }
            }
        """.trimIndent()
    }

    private companion object {
        const val PLUGIN_ID = "0123456789abcdef01234567"
    }
}
