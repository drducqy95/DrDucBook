package io.legado.app.data.repository

import io.legado.app.domain.model.AiCredentialStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiOAuthVerificationStateTest {

    @Test
    fun fallsBackToNextModelAndActivatesCredentialAfterProbe() = runBlocking {
        val statuses = mutableListOf<String>()
        val result = verifyOAuthCredentialModels(
            models = listOf("model-1", "model-2"),
            updateStatus = { statuses += it },
            probe = { model ->
                if (model == "model-1") error("first model rejected")
            },
        )

        assertEquals("model-2", result)
        assertEquals(
            listOf(AiCredentialStatus.VERIFYING, AiCredentialStatus.ACTIVE),
            statuses,
        )
    }

    @Test
    fun keepsCredentialActiveWhenEveryModelProbeFailsTransiently() = runBlocking {
        val statuses = mutableListOf<String>()
        val selected = verifyOAuthCredentialModels(
            models = listOf("model-1", "model-2"),
            updateStatus = { statuses += it },
            probe = { error("model temporarily unavailable") },
        )

        assertEquals("model-1", selected)
        assertEquals(
            listOf(AiCredentialStatus.VERIFYING, AiCredentialStatus.ACTIVE),
            statuses,
        )
    }

    @Test
    fun marksCredentialForReloginWhenTokenIsRejected() = runBlocking {
        val statuses = mutableListOf<String>()
        try {
            verifyOAuthCredentialModels(
                models = listOf("model-1"),
                updateStatus = { statuses += it },
                probe = { error("HTTP 401: token_invalid") },
            )
            fail("Expected OAuth authentication failure")
        } catch (error: IllegalStateException) {
            assertEquals("HTTP 401: token_invalid", error.message)
        }

        assertEquals(
            listOf(AiCredentialStatus.VERIFYING, AiCredentialStatus.RELOGIN_REQUIRED),
            statuses,
        )
    }

    @Test
    fun keepsPersistedCredentialActiveBeforePropagatingCancellation() = runBlocking {
        val statuses = mutableListOf<String>()
        try {
            verifyOAuthCredentialModels(
                models = listOf("model-1"),
                updateStatus = { statuses += it },
                probe = { throw CancellationException("cancelled") },
            )
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertTrue(error.message == "cancelled")
        }

        assertEquals(
            listOf(AiCredentialStatus.VERIFYING, AiCredentialStatus.ACTIVE),
            statuses,
        )
    }
}
