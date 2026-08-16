package io.legado.app.domain.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAuditSanitizerTest {

    @Test
    fun redactsJsonAuthorizationQueryAndKnownTokens() {
        val raw = """{"apiKey":"sk-1234567890abcdefghijkl","note":"Bearer abcdefghijklmnopqrstuvwxyz123"} url?access_token=secret-value cookie=raw-cookie"""

        val sanitized = raw.sanitizeForAgentAudit()

        assertFalse(sanitized.contains("sk-1234567890abcdefghijkl"))
        assertFalse(sanitized.contains("abcdefghijklmnopqrstuvwxyz123"))
        assertFalse(sanitized.contains("secret-value"))
        assertFalse(sanitized.contains("raw-cookie"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }
}
