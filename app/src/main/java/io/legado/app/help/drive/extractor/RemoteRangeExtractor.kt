package io.legado.app.help.drive.extractor

import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Low-level HTTP Range request helper for zero-download metadata extraction.
 */
object RemoteRangeExtractor {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Reads a specific byte range [startOffset, endOffset] (inclusive) from a remote URL.
     */
    suspend fun readRange(
        url: String,
        auth: Authorization? = null,
        startOffset: Long,
        endOffset: Long
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=$startOffset-$endOffset")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) DrDucBook/3.0")

            if (auth != null && auth.username.isNotBlank()) {
                reqBuilder.header(auth.name, auth.data)
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                response.close()
                return@withContext null
            }

            val bytes = response.body.bytes()
            response.close()
            bytes
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads the first [length] bytes of a remote file.
     */
    suspend fun readHead(
        url: String,
        auth: Authorization? = null,
        length: Long = 8192
    ): ByteArray? {
        return readRange(url, auth, 0L, length - 1)
    }

    /**
     * Reads the last [length] bytes of a remote file given its known [totalSize].
     */
    suspend fun readTail(
        url: String,
        auth: Authorization? = null,
        totalSize: Long,
        length: Long = 65536
    ): ByteArray? {
        if (totalSize <= 0) return null
        val actualLen = minOf(totalSize, length)
        val startOffset = maxOf(0L, totalSize - actualLen)
        return readRange(url, auth, startOffset, totalSize - 1)
    }
}
