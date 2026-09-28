package io.legado.app.help.drive

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GoBridgeSpikeTest {

    @Test
    fun testLifecycleAndBasicAuth(): Unit = runBlocking {
        val hasNativeGojni = runCatching { System.loadLibrary("gojni") }.isSuccess
        assumeTrue("Skipping GoBridgeSpikeTest on host JVM (requires Android environment with libgojni.so)", hasNativeGojni)

        val bridge = GoBridge()
        assertFalse(bridge.isRunning)

        val startResult = bridge.start(
            configJson = """{"port": 0, "request_timeout_sec": 5, "read_only": true}""",
            accessToken = "dummy-token"
        )
        if (startResult.isFailure) {
            println("Bridge start failed with: ${startResult.exceptionOrNull()}")
            startResult.exceptionOrNull()?.printStackTrace()
        }
        assertTrue("Bridge should start successfully: ${startResult.exceptionOrNull()?.message}", startResult.isSuccess)

        val port = bridge.port
        assertTrue("Port should be assigned and > 0", port > 0)
        assertTrue("Server should be running", bridge.isRunning)

        val secret = bridge.sessionSecret
        assertNotNull("Session secret should not be null", secret)
        assertEquals("Session secret should be 64 hex characters (32 bytes)", 64, secret.length)

        val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()

        val baseUrl = "http://127.0.0.1:$port/"

        // 1. Request without Authorization header -> 401
        val reqNoAuth = Request.Builder().url(baseUrl).build()
        client.newCall(reqNoAuth).execute().use { resp ->
            assertEquals(401, resp.code)
            assertTrue(resp.header("WWW-Authenticate")?.contains("Basic") == true)
        }

        // 2. Request with Wrong credentials -> 401
        val reqWrongAuth = Request.Builder()
            .url(baseUrl)
            .header("Authorization", Credentials.basic("legado", "wrong-secret"))
            .build()
        client.newCall(reqWrongAuth).execute().use { resp ->
            assertEquals(401, resp.code)
        }

        // 3. Request with Valid credentials (OPTIONS) -> 200
        val reqValidAuth = Request.Builder()
            .url(baseUrl)
            .method("OPTIONS", null)
            .header("Authorization", Credentials.basic("legado", secret))
            .build()
        client.newCall(reqValidAuth).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("1", resp.header("DAV"))
        }

        // 4. Token update
        val updateResult = bridge.updateAccessToken("new-token")
        assertTrue(updateResult.isSuccess)

        // 5. Stop server
        val stopResult = bridge.stop()
        assertTrue("Bridge should stop successfully", stopResult.isSuccess)
        assertFalse("Server should not be running after stop", bridge.isRunning)
    }
}
