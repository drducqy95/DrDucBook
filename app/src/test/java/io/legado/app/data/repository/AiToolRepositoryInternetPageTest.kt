package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AiToolRepositoryInternetPageTest {

    @Test
    fun validateInternetFetchUrlRejectsNonHttpAndLocalTargets() {
        assertTrue(validateInternetFetchUrl("file:///tmp/page.html").isFailure)
        assertTrue(validateInternetFetchUrl("http://localhost:8080").isFailure)
        assertTrue(validateInternetFetchUrl("http://127.0.0.1/page").isFailure)
        assertTrue(
            validateInternetFetchUrl("https://public.example/page") {
                arrayOf(ipv4(10, 0, 0, 8))
            }.isFailure
        )
    }

    @Test
    fun validateInternetFetchUrlAllowsResolvedPublicHttpUrl() {
        val result = validateInternetFetchUrl("https://public.example/a?q=1#fragment") {
            arrayOf(ipv4(93, 184, 216, 34))
        }

        assertEquals("https://public.example/a?q=1", result.getOrThrow())
    }

    @Test
    fun readableInternetContentTypeRejectsBinaryContent() {
        assertTrue(isReadableInternetContentType("text/html; charset=utf-8"))
        assertTrue(isReadableInternetContentType("text/plain"))
        assertTrue(isReadableInternetContentType("application/xhtml+xml"))
        assertFalse(isReadableInternetContentType("image/png"))
        assertFalse(isReadableInternetContentType("application/octet-stream"))
    }

    @Test
    fun extractInternetPageContentStripsUnsafeHtmlAndCapsText() {
        val page = extractInternetPageContent(
            raw = """
                <html>
                  <head>
                    <title>Sample Page</title>
                    <meta name="description" content="Readable description">
                  </head>
                  <body>
                    <script>window.secret = 'bad';</script>
                    <input value="hidden input">
                    <h1>Hello title</h1>
                    <p>Visible paragraph one.</p>
                    <p>Visible paragraph two.</p>
                  </body>
                </html>
            """.trimIndent(),
            baseUrl = "https://public.example/page",
            contentType = "text/html; charset=utf-8",
            maxChars = 28,
        )

        assertEquals("Sample Page", page.title)
        assertEquals("Readable description", page.description)
        assertTrue(page.text.contains("Hello title"))
        assertFalse(page.text.contains("window.secret"))
        assertFalse(page.text.contains("hidden input"))
        assertTrue(page.truncated)
        assertTrue(page.text.length <= 28)
    }

    private fun ipv4(
        first: Int,
        second: Int,
        third: Int,
        fourth: Int,
    ): InetAddress = InetAddress.getByAddress(
        byteArrayOf(
            first.toByte(),
            second.toByte(),
            third.toByte(),
            fourth.toByte(),
        )
    )
}
