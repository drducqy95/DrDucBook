package io.legado.app.data.repository.vbook

import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.domain.model.VbookRegistryOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VbookRegistryParserTest {

    @Test
    fun `parses supported kinds, sanitizes icon, rejects invalid item and keeps newest duplicate`() {
        val snapshot = VbookRegistryParser.parse(
            json = registryJson(
                """
                {
                  "name": "OPhim",
                  "author": "kychi",
                  "path": "https://example.com/ophim.zip",
                  "version": 1,
                  "source": "https://ophim.test",
                  "icon": "javascript:alert(1)",
                  "description": "Video",
                  "type": "video",
                  "locale": "vi_VN"
                },
                {
                  "name": "Audio Truyện Full",
                  "author": "kychi",
                  "path": "https://example.com/audio.zip",
                  "version": 1,
                  "source": "https://audio.test",
                  "icon": "",
                  "description": "Có cả nội dung audio nhưng manifest vẫn là novel",
                  "type": "novel",
                  "locale": "vi_VN"
                },
                {
                  "name": "FPT AI TTS",
                  "author": "kychi",
                  "path": "https://example.com/tts.zip",
                  "version": 1,
                  "source": "https://tts.test",
                  "icon": "https://example.com/tts.png",
                  "description": "TTS",
                  "type": "tts",
                  "locale": "vi_VN"
                },
                {
                  "name": "OPhim",
                  "author": "kychi",
                  "path": "https://example.com/ophim-v2.zip",
                  "version": 2,
                  "source": "https://ophim.test",
                  "icon": "https://example.com/ophim.png",
                  "description": "Video update",
                  "type": "video",
                  "locale": "vi_VN"
                },
                {
                  "name": "Invalid",
                  "author": "nobody",
                  "path": "file:///tmp/plugin.zip",
                  "version": 1,
                  "source": "",
                  "icon": "",
                  "description": "",
                  "type": "novel",
                  "locale": "vi"
                }
                """.trimIndent()
            ),
            fetchedAt = 123L,
            origin = VbookRegistryOrigin.NETWORK,
        )

        assertEquals(3, snapshot.items.size)
        assertEquals(1, snapshot.rejectedItemCount)
        assertEquals(123L, snapshot.fetchedAt)
        val video = snapshot.items.first { it.name == "OPhim" }
        assertEquals(2, video.version)
        assertEquals(VbookPluginKind.VIDEO, video.declaredKind)
        assertEquals("https://example.com/ophim.png", video.iconUrl)
        assertEquals(
            VbookPluginKind.TEXT,
            snapshot.items.first { it.name == "Audio Truyện Full" }.declaredKind,
        )
        assertEquals(
            VbookPluginKind.TTS,
            snapshot.items.first { it.name == "FPT AI TTS" }.declaredKind,
        )
        assertTrue(video.pluginId.matches(Regex("[a-f0-9]{24}")))
    }

    @Test
    fun `rejects registry without any valid plugins`() {
        val result = runCatching {
            VbookRegistryParser.parse(
                json = registryJson(
                    """
                    {
                      "name": "",
                      "path": "not-a-url",
                      "version": -1,
                      "source": "",
                      "type": ""
                    }
                    """.trimIndent()
                ),
                fetchedAt = 0L,
                origin = VbookRegistryOrigin.NETWORK,
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `keeps large compatible registry with alternate field names`() {
        val items = (1..120).joinToString(",\n") { index ->
            val typeKey = if (index % 2 == 0) "kind" else "sourceType"
            val urlKey = if (index % 3 == 0) "downloadUrl" else "url"
            val sourceKey = if (index % 4 == 0) "baseUrl" else "host"
            """
            {
              "title": "Alt source $index",
              "creator": "Author $index",
              "$urlKey": "https://cdn.example/plugin-$index.zip",
              "$sourceKey": "https://source-$index.example",
              "$typeKey": "book",
              "versionCode": "$index"
            }
            """.trimIndent()
        }

        val snapshot = VbookRegistryParser.parse(
            json = registryJson(items),
            fetchedAt = 456L,
            origin = VbookRegistryOrigin.NETWORK,
        )

        assertEquals(120, snapshot.items.size)
        assertEquals(0, snapshot.rejectedItemCount)
        assertTrue(snapshot.items.all { it.declaredKind == VbookPluginKind.TEXT })
        assertTrue(snapshot.items.map { it.pluginId }.toSet().size == 120)
    }

    private fun registryJson(items: String): String {
        return """
            {
              "metadata": {
                "id": "registry-id",
                "slug": "vbook-test",
                "name": "Test",
                "author": "Tester",
                "description": "Fixture",
                "version": 1,
                "generatedAt": "2026-07-18T00:00:00Z",
                "totalItems": 5
              },
              "data": [
                $items
              ]
            }
        """.trimIndent()
    }
}
