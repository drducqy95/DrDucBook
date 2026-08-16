package io.legado.app.ui.ai.context

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiScreenContextRegistryTest {

    @After
    fun tearDown() {
        AiScreenContextRegistry.clearAll()
    }

    @Test
    fun sensitiveContextDropsAllAttributes() {
        AiScreenContextRegistry.register(
            AiScreenContextSnapshot(
                ownerId = "oauth",
                screen = "OAuth",
                attributes = mapOf(
                    "email" to "user@example.com",
                    "accessToken" to "secret-token",
                ),
                sensitive = true,
            )
        )

        val current = AiScreenContextRegistry.current.value!!
        assertTrue(current.sensitive)
        assertEquals(emptyMap<String, String>(), current.attributes)
    }

    @Test
    fun nonSensitiveContextRedactsSecretLikeAttributeKeys() {
        AiScreenContextRegistry.register(
            AiScreenContextSnapshot(
                ownerId = "reader",
                screen = "Reader",
                attributes = mapOf(
                    "bookUrl" to "book://one",
                    "api_key" to "plain-key",
                    "refreshToken" to "plain-token",
                    "password" to "plain-password",
                ),
            )
        )

        val attributes = AiScreenContextRegistry.current.value!!.attributes
        assertEquals("book://one", attributes["bookUrl"])
        assertEquals("[redacted]", attributes["api_key"])
        assertEquals("[redacted]", attributes["refreshToken"])
        assertEquals("[redacted]", attributes["password"])
    }

    @Test
    fun clearingOneOwnerDoesNotLeakOrDropAnotherActiveOwner() {
        AiScreenContextRegistry.register(
            AiScreenContextSnapshot(
                ownerId = "main",
                screen = "MainRouteBookInfo",
                attributes = mapOf("bookUrl" to "book://one"),
            )
        )
        AiScreenContextRegistry.register(
            AiScreenContextSnapshot(
                ownerId = "reader",
                screen = "ReadBookActivity",
                attributes = mapOf("chapter" to "12"),
            )
        )

        AiScreenContextRegistry.clear("main")

        assertEquals("reader", AiScreenContextRegistry.current.value!!.ownerId)
        assertEquals("12", AiScreenContextRegistry.current.value!!.attributes["chapter"])

        AiScreenContextRegistry.clear("reader")

        assertEquals(null, AiScreenContextRegistry.current.value)
    }

    @Test
    fun activityContextHelperBuildsStableOwnerId() {
        assertEquals(
            "com.drducbook.app.Reader@42",
            AiActivityScreenContext.ownerId("com.drducbook.app.Reader", 42)
        )
    }

    @Test
    fun activityContextHelperDropsBlankAttributes() {
        val attributes = AiActivityScreenContext.compactAttributes(
            mapOf(
                "bookUrl" to " book://one ",
                "chapter" to "",
                "source" to "   ",
                "title" to null,
            )
        )

        assertEquals(mapOf("bookUrl" to "book://one"), attributes)
    }

    @Test
    fun activityContextHelperStripsUrlSecrets() {
        assertEquals(
            "https://example.com:8443/read/book",
            AiActivityScreenContext.safeUrlLabel(
                "https://user:pass@example.com:8443/read/book?token=secret#chapter"
            )
        )
        assertEquals("legado-source-id", AiActivityScreenContext.safeUrlLabel("legado-source-id"))
        assertNull(AiActivityScreenContext.safeUrlLabel("  "))
    }
}
