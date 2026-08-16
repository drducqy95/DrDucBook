package io.legado.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolRepositoryLegadoSourceValidatorTest {

    @Test
    fun parserAcceptsStandardPublicLegadoSourceJson() {
        val source = parseAgentLegadoBookSourceJson(
            """
            {
              "bookSourceUrl": "https://books.example",
              "bookSourceName": "Example Books",
              "bookSourceType": 0,
              "enabledCookieJar": true,
              "searchUrl": "https://books.example/search?q={{key}}",
              "ruleSearch": {
                "bookList": ".book",
                "name": ".title@text",
                "bookUrl": ".title@href"
              },
              "ruleBookInfo": {"name": "h1@text"},
              "ruleToc": {"chapterList": ".chapter"},
              "ruleContent": {"content": ".content@html"}
            }
            """.trimIndent(),
        )

        assertEquals("Example Books", source.bookSourceName)
        assertEquals("https://books.example", source.bookSourceUrl)
        assertTrue(source.enabledCookieJar == true)
    }

    @Test
    fun parserRejectsMissingIdentityPrivateTargetsAndPlatformApis() {
        listOf(
            """{"bookSourceUrl":"https://books.example"}""",
            """{"bookSourceName":"Local","bookSourceUrl":"http://127.0.0.1:8080"}""",
            """{"bookSourceName":"Unsafe","bookSourceUrl":"https://books.example","searchUrl":"@js:java.lang.Runtime.getRuntime()"}""",
            """{"bookSourceName":"Wrong type","bookSourceUrl":"https://books.example","bookSourceType":9}""",
        ).forEach { json ->
            assertTrue(json, runCatching { parseAgentLegadoBookSourceJson(json) }.isFailure)
        }
    }
}
