package io.legado.app.ui.ai.chat

import io.legado.app.domain.model.AiToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolConfirmationFormatterTest {

    @Test
    fun `confirmation includes every tool request without truncating the last arguments`() {
        val calls = (1..24).map { index ->
            AiToolCall(
                id = "call-$index",
                name = "tool_$index",
                arguments = "{\"payload\":\"${"x".repeat(150)}-end-$index\"}",
            )
        }

        val confirmation = buildAiToolConfirmation(
            toolCalls = calls,
            proposalId = "proposal-42",
        )

        assertEquals(24, confirmation.requestCount)
        assertTrue(confirmation.description.contains("tool_1"))
        assertTrue(confirmation.description.contains("tool_24"))
        assertTrue(confirmation.description.contains("end-24"))
        assertFalse(confirmation.description.endsWith("…"))
    }
}
