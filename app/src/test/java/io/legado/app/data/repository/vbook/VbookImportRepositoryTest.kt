package io.legado.app.data.repository.vbook

import android.app.Application
import android.net.Uri
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.domain.model.ImportClassification
import io.legado.app.domain.model.VbookImportAction
import io.legado.app.domain.model.VbookImportPreviewItem
import io.legado.app.domain.model.VbookPluginKind
import io.legado.app.help.vbook.VbookPluginImporter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class VbookImportRepositoryTest {

    @Test
    fun previewsRegistryFromUrl() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(registryJson.toResponseBody())
                    .build()
            }
            .build()
        val repository = repository(client)

        val preview = repository.preview(
            "https://www.vbookext.me/api/registry/vbook-fd1246b6.json"
        )

        assertEquals(ImportClassification.REGISTRY, preview.classification)
        assertEquals(2, preview.items.size)
        assertEquals(VbookImportAction.INSTALL, preview.items.first().action)
        assertFalse(preview.items.last().compatible)
    }

    @Test
    fun previewsCompatibleArrayFromJsonFile() = runBlocking {
        val context: Application = RuntimeEnvironment.getApplication()
        val file = context.cacheDir.resolve("vbook-compatible.json")
        file.writeText(compatibleArrayJson)
        val repository = repository(OkHttpClient())

        val preview = repository.preview(Uri.fromFile(file).toString())

        assertEquals(ImportClassification.COMPATIBLE_ARRAY, preview.classification)
        assertEquals("Novel source", preview.items.single().name)
    }

    @Test
    fun installAllowsDownloadedPluginIdentityDrift() = runBlocking {
        val expectedPluginId = VbookPluginImporter.stablePluginId(
            source = "https://novel.example",
            author = "A",
            name = "Novel source",
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pluginZipBytes(source = "https://other.example").toResponseBody())
                    .build()
            }
            .build()
        val repository = repository(client)

        val installedName = repository.install(
            VbookImportPreviewItem(
                pluginId = expectedPluginId,
                name = "Novel source",
                author = "A",
                version = 1,
                description = "Novel",
                iconUrl = "",
                downloadUrl = "https://cdn.example/novel.zip",
                declaredKind = VbookPluginKind.TEXT,
                capabilities = emptySet(),
                action = VbookImportAction.INSTALL,
            )
        )

        assertEquals("Novel source", installedName)
    }

    private fun repository(client: OkHttpClient) = VbookImportRepository(
        context = RuntimeEnvironment.getApplication(),
        bookSourceDao = emptyBookSourceDao(),
        client = client,
        validateUrl = { it },
    )

    @Suppress("UNCHECKED_CAST")
    private fun emptyBookSourceDao(): BookSourceDao = Proxy.newProxyInstance(
        BookSourceDao::class.java.classLoader,
        arrayOf(BookSourceDao::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getBookSource" -> null
            "toString" -> "EmptyBookSourceDao"
            else -> defaultValue(method.returnType)
        }
    } as BookSourceDao

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    private fun pluginZipBytes(
        source: String = "https://novel.example",
        author: String = "A",
        name: String = "Novel source",
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("plugin.json"))
            zip.write(
                """
                {
                  "metadata": {
                    "name": "$name",
                    "author": "$author",
                    "version": 1,
                    "source": "$source",
                    "type": "novel"
                  },
                  "script": {"chap": "chap.js"}
                }
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("src/chap.js"))
            zip.write(
                """function execute() { return JSON.stringify({success:true,data:"ok"}); }"""
                    .toByteArray(),
            )
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private val registryJson = """
        {
          "metadata": {"id":"main","name":"Registry","version":1,"totalItems":2},
          "data": [
            {"name":"Novel source","author":"A","path":"https://cdn.example/novel.zip","version":1,"source":"https://novel.example","type":"novel"},
            {"name":"TTS voice","author":"B","path":"https://cdn.example/tts.zip","version":1,"source":"https://tts.example","type":"tts"}
          ]
        }
    """.trimIndent()

    private val compatibleArrayJson = """
        [
          {"name":"Novel source","author":"A","path":"https://cdn.example/novel.zip","version":1,"source":"https://novel.example","type":"novel"}
        ]
    """.trimIndent()
}
