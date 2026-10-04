package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlSanitizerTest {

    @Test
    fun sanitizeBookUrlHandlesMalformed52shukuUrl() {
        val malformed = "{Https://www.52shuku.net/yanqing/03_b/bjYOd.html\""
        val expected = "https://www.52shuku.net/yanqing/03_b/bjYOd.html"
        assertEquals(expected, UrlSanitizer.sanitizeBookUrl(malformed))
    }

    @Test
    fun sanitizeBookUrlHandlesLeadingAndTrailingQuotesAndBrackets() {
        assertEquals("https://example.com/book/1", UrlSanitizer.sanitizeBookUrl("\"https://example.com/book/1\""))
        assertEquals("http://example.com/book/1", UrlSanitizer.sanitizeBookUrl("['HTTP://example.com/book/1']"))
        assertEquals("https://example.com/book/1", UrlSanitizer.sanitizeBookUrl("{ \"https://example.com/book/1\" }"))
    }

    @Test
    fun sanitizeBookUrlHandlesEmptyAndWhitespace() {
        assertEquals("", UrlSanitizer.sanitizeBookUrl(null))
        assertEquals("", UrlSanitizer.sanitizeBookUrl("   "))
        assertEquals("", UrlSanitizer.sanitizeBookUrl("{\"\"}"))
    }

    @Test
    fun sanitizeBookUrlPreservesCleanUrls() {
        val clean = "https://fanqienovel.com/page/12345"
        assertEquals(clean, UrlSanitizer.sanitizeBookUrl(clean))
    }
}
