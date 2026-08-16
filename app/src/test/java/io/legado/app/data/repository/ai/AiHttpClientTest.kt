package io.legado.app.data.repository.ai

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHttpClientTest {

    @Test
    fun `ai client has finite generous streaming timeouts`() {
        val client = OkHttpClient.Builder().configureAiTimeouts().build()

        assertEquals(AI_READ_TIMEOUT_MILLIS, client.readTimeoutMillis.toLong())
        assertEquals(AI_CALL_TIMEOUT_MILLIS, client.callTimeoutMillis.toLong())
        assertTrue(client.readTimeoutMillis > 0)
        assertTrue(client.callTimeoutMillis > client.readTimeoutMillis)
    }
}
