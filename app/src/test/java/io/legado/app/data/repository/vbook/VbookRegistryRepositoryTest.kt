package io.legado.app.data.repository.vbook

import io.legado.app.domain.model.VbookRegistryOrigin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class VbookRegistryRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `uses fresh cache, validates with etag and falls back to stale cache offline`() = runBlocking {
        var clock = 1_000L
        val requestCount = AtomicInteger()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount.incrementAndGet()
                if (chain.request().header("If-None-Match") == "\"registry-v1\"") {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(304)
                        .message("Not Modified")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("ETag", "\"registry-v1\"")
                        .body(validRegistry.toResponseBody())
                        .build()
                }
            }
            .build()
        val root = temporaryFolder.newFolder("registry")
        val repository = VbookRegistryRepository(
            root = root,
            client = client,
            registryUrl = "https://registry.test/vbook.json",
            now = { clock },
        )

        assertEquals(VbookRegistryOrigin.NETWORK, repository.load().getOrThrow().origin)
        assertEquals(1, requestCount.get())

        clock += 10_000L
        assertEquals(VbookRegistryOrigin.CACHE_FRESH, repository.load().getOrThrow().origin)
        assertEquals(1, requestCount.get())

        clock += TWO_DAYS_MS
        assertEquals(
            VbookRegistryOrigin.CACHE_VALIDATED,
            repository.load(forceRefresh = true).getOrThrow().origin,
        )
        assertEquals(2, requestCount.get())

        clock += TWO_DAYS_MS
        val offlineClient = OkHttpClient.Builder()
            .addInterceptor { throw IOException("offline") }
            .build()
        val offlineRepository = VbookRegistryRepository(
            root = root,
            client = offlineClient,
            registryUrl = "https://registry.test/vbook.json",
            now = { clock },
        )
        assertEquals(
            VbookRegistryOrigin.CACHE_STALE_FALLBACK,
            offlineRepository.load().getOrThrow().origin,
        )
    }

    @Test
    fun `detects a modified cache using checksum`() = runBlocking {
        val root = temporaryFolder.newFolder("tampered")
        val repository = VbookRegistryRepository(
            root = root,
            client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validRegistry.toResponseBody())
                        .build()
                }
                .build(),
            registryUrl = "https://registry.test/vbook.json",
            now = { 1_000L },
        )
        repository.load().getOrThrow()
        root.resolve("registry.json").appendText("tampered")

        assertTrue(repository.loadCached().isFailure)
    }

    private val validRegistry = """
        {
          "metadata": {
            "id": "registry",
            "slug": "test",
            "name": "Test",
            "author": "Tester",
            "description": "",
            "version": 1,
            "generatedAt": "2026-07-18T00:00:00Z",
            "totalItems": 1
          },
          "data": [
            {
              "name": "OPhim",
              "author": "kychi",
              "path": "https://example.com/plugin.zip",
              "version": 1,
              "source": "https://ophim.test",
              "icon": "https://example.com/icon.png",
              "description": "Video",
              "type": "video",
              "locale": "vi_VN"
            }
          ]
        }
    """.trimIndent()

    private companion object {
        const val TWO_DAYS_MS = 2L * 24L * 60L * 60L * 1_000L
    }
}
