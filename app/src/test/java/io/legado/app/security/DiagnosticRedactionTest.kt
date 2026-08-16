package io.legado.app.security

import io.legado.app.domain.agent.sanitizeForAgentAudit
import io.legado.app.ui.ai.context.AiScreenContextRegistry
import io.legado.app.ui.ai.context.AiScreenContextSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactionTest {

    @After
    fun tearDown() {
        AiScreenContextRegistry.clearAll()
    }

    @Test
    fun diagnosticPayloadRedactsApiOAuthAuthorizationAndBackupSecrets() {
        val raw = """
            {"apiKey":"sk-1234567890abcdefghijkl","refreshToken":"oauth-refresh-secret",
             "password":"backup-password","authorization":"Bearer header-secret-token"}
            https://example.com/callback?access_token=query-secret
            Authorization: Bearer request-header-secret
        """.trimIndent()

        val sanitized = raw.sanitizeForAgentAudit()

        listOf(
            "sk-1234567890abcdefghijkl",
            "oauth-refresh-secret",
            "backup-password",
            "header-secret-token",
            "query-secret",
            "request-header-secret",
        ).forEach { secret -> assertFalse(secret, sanitized.contains(secret)) }
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun screenContextNeverPublishesCredentialAttributes() {
        AiScreenContextRegistry.register(
            AiScreenContextSnapshot(
                ownerId = "phase08-security",
                screen = "ProviderSettings",
                attributes = mapOf(
                    "provider" to "openai",
                    "apiKey" to "plain-api-key",
                    "accessToken" to "plain-access-token",
                    "password" to "plain-password",
                ),
            )
        )

        val attributes = AiScreenContextRegistry.current.value!!.attributes
        assertEquals("openai", attributes["provider"])
        assertEquals("[redacted]", attributes["apiKey"])
        assertEquals("[redacted]", attributes["accessToken"])
        assertEquals("[redacted]", attributes["password"])
    }
}
