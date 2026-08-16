package io.legado.app.domain.agent

import io.legado.app.domain.model.AiToolCall
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolLoopGuardTest {

    @Test
    fun detectsRepeatedToolCallIgnoringCallIdAndJsonKeyOrder() {
        val guard = AgentToolLoopGuard()

        assertFalse(guard.recordAndIsLoop(call("call_1", """{"query":"book","limit":5}""")))
        assertFalse(guard.recordAndIsLoop(call("call_2", """{ "limit" : 5, "query" : "book" }""")))
        assertTrue(guard.recordAndIsLoop(call("call_3", """{"query":"book","limit":5}""")))
    }

    @Test
    fun differentArgumentsDoNotShareLoopCount() {
        val guard = AgentToolLoopGuard()

        assertFalse(guard.recordAndIsLoop(call("call_1", """{"query":"one"}""")))
        assertFalse(guard.recordAndIsLoop(call("call_2", """{"query":"two"}""")))
        assertFalse(guard.recordAndIsLoop(call("call_3", """{"query":"one"}""")))
    }

    private fun call(id: String, arguments: String): AiToolCall {
        return AiToolCall(
            id = id,
            name = "search_books",
            arguments = arguments,
        )
    }
}
