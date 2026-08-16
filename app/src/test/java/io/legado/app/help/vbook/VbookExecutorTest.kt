package io.legado.app.help.vbook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Dns
import java.net.InetAddress
import java.nio.charset.Charset

class VbookExecutorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `raw response decodes declared non utf8 charset`() {
        val text = "\u6700\u65B0\u5165\u5E93"
        val response = VbookExecutor.RawResponse(
            ok = true,
            status = 200,
            statusText = "OK",
            bodyBytes = text.toByteArray(Charset.forName("GBK")),
            url = "https://m.txt520.test/",
            headers = mapOf("Content-Type" to "text/html; charset=gbk"),
        )

        assertEquals(text, response.getBodyString())
    }

    @Test
    fun `script loader accepts extensionless names and supplies base64 compatibility library`() {
        val plugin = temporaryFolder.newFolder("plugin")
        plugin.resolve("plugin.json").writeText(
            """
            {
              "metadata": {
                "name": "Compat",
                "author": "Tester",
                "version": 1,
                "source": "https://compat.test",
                "type": "video"
              },
              "script": {"chap": "chap.js"}
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        plugin.resolve("src").mkdirs()
        plugin.resolve("src/config.js").writeText("const BASE_URL = 'https://compat.test';")

        val loader = VbookExecutor.ScriptLoader(plugin)

        assertTrue(loader.scriptFile("config").isFile)
        assertTrue(loader.readScript("base64.js")!!.contains("Base64"))
    }

    @Test
    fun `vbook fetch persists session cookies as well as expiring cookies`() {
        val cookie = VbookExecutor.FetchEngine.responseCookiesToPersist(
            url = "https://reader.example.test/login".toHttpUrl(),
            headers = headersOf(
                "Set-Cookie",
                "session=abc123; Path=/; HttpOnly",
                "Set-Cookie",
                "remember=yes; Max-Age=86400; Path=/",
            ),
        )

        assertEquals("session=abc123; remember=yes", cookie)
    }

    @Test
    fun `raw response exposes normalized effective request headers`() {
        val response = VbookExecutor.RawResponse(
            ok = true,
            status = 200,
            statusText = "OK",
            bodyBytes = ByteArray(0),
            url = "https://reader.example.test/account",
            headers = emptyMap(),
            requestHeaders = mapOf("cookie" to "accessToken=abc123"),
        )

        assertEquals("accessToken=abc123", response.requestHeaders["cookie"])
    }

    @Test
    fun `public dns keeps public answers from a mixed result`() {
        val privateAddress = InetAddress.getByName("192.168.1.2")
        val publicAddress = InetAddress.getByName("93.184.216.34")
        val dns = VbookExecutor.FetchEngine.PublicOnlyDns(
            Dns { listOf(privateAddress, publicAddress) },
        )

        assertEquals(listOf(publicAddress), dns.lookup("reader.example.test"))
    }

    @Test
    fun `public dns still rejects private only results`() {
        val dns = VbookExecutor.FetchEngine.PublicOnlyDns(
            Dns { listOf(InetAddress.getByName("192.168.1.2")) },
        )

        assertThrows(SecurityException::class.java) {
            dns.lookup("reader.example.test")
        }
    }

    @Test
    fun `native blocking time does not consume javascript cpu timeout`() {
        val factory = SafeContextFactory()
        val context = factory.enterContext() as SafeContext
        try {
            val scope = context.initStandardObjects()
            context.evaluateString(
                scope,
                "var warmup = 0; for (var j = 0; j < 5000; j++) warmup += j;",
                "timeout-warmup",
                1,
                null,
            )
            context.timeoutMs = 100L
            context.startTime = System.currentTimeMillis()
            context.withoutTimeoutAccounting { Thread.sleep(200L) }

            context.evaluateString(
                scope,
                "var total = 0; for (var i = 0; i < 5000; i++) total += i;",
                "timeout-test",
                1,
                null,
            )
        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }
}
