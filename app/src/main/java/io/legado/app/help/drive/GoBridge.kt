package io.legado.app.help.drive

import io.legado.app.gowebdav.bind.Bind
import io.legado.app.gowebdav.bind.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DriveBridgeError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InitializationFailed(message: String, cause: Throwable? = null) : DriveBridgeError(message, cause)
    class StartFailed(message: String, cause: Throwable? = null) : DriveBridgeError(message, cause)
    class StopFailed(message: String, cause: Throwable? = null) : DriveBridgeError(message, cause)
    class TokenUpdateFailed(message: String, cause: Throwable? = null) : DriveBridgeError(message, cause)
}

data class BridgeStartResult(
    val port: Int,
    val sessionSecret: String
)

/**
 * Kotlin coroutine-friendly wrapper around Gomobile Go WebDAV proxy server.
 */
class GoBridge {
    @Volatile
    private var nativeServer: Server? = null

    val isRunning: Boolean
        get() = nativeServer?.isRunning ?: false

    val port: Int
        get() = (nativeServer?.port() ?: 0L).toInt()

    val sessionSecret: String
        get() = nativeServer?.sessionSecret() ?: ""

    suspend fun start(configJson: String = "", accessToken: String = ""): Result<BridgeStartResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                stop()
                val server = try {
                    Bind.newServer(configJson)
                } catch (e: Throwable) {
                    throw DriveBridgeError.InitializationFailed("Failed to initialize Go WebDAV server: ${e.message}", e)
                }

                try {
                    server.start(accessToken)
                } catch (e: Throwable) {
                    val lastErr = server.lastError()
                    throw DriveBridgeError.StartFailed(
                        if (lastErr.isNotBlank()) lastErr else "Failed to start Go WebDAV server: ${e.message}",
                        e
                    )
                }

                nativeServer = server
                BridgeStartResult(
                    port = server.port().toInt(),
                    sessionSecret = server.sessionSecret()
                )
            }
        }

    suspend fun updateAccessToken(token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val server = nativeServer ?: return@runCatching
                try {
                    server.updateAccessToken(token)
                } catch (e: Throwable) {
                    throw DriveBridgeError.TokenUpdateFailed("Failed to update access token: ${e.message}", e)
                }
            }
        }

    suspend fun stop(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val server = nativeServer ?: return@runCatching
                try {
                    server.stop()
                } catch (e: Throwable) {
                    throw DriveBridgeError.StopFailed("Failed to stop Go WebDAV server: ${e.message}", e)
                } finally {
                    nativeServer = null
                }
            }
        }
}
