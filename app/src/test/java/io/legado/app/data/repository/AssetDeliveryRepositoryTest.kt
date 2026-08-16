package io.legado.app.data.repository

import android.app.Application
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.domain.model.AssetDeliveryArtifact
import io.legado.app.domain.model.AssetDeliveryArtifactKind
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class AssetDeliveryRepositoryTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        assetRoot()?.deleteRecursively()
    }

    @After
    fun tearDown() {
        assetRoot()?.deleteRecursively()
    }

    @Test
    fun largeDownloadHasNoWholeCallDeadlineAndResumesAfterTimeout() = runBlocking {
        val payload = "verified-model-payload".toByteArray()
        val interceptor = ResumeDownloadInterceptor(payload, failAfterBytes = 7L)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val artifact = AssetDeliveryArtifact(
            id = "test-model",
            displayName = "Test model",
            fileName = "test-model.zip",
            sizeBytes = payload.size.toLong(),
            sha256 = payload.sha256(),
            kind = AssetDeliveryArtifactKind.TRANSLATION,
            detail = "test",
        )
        val repository = AssetDeliveryRepository(
            context = application,
            configProvider = { TEST_CONFIG },
            client = client,
        )

        val downloaded = repository.downloadArtifact(artifact, "access-token") {}

        assertArrayEquals(payload, java.io.File(downloaded.path).readBytes())
        assertEquals(listOf(null, "bytes=7-"), interceptor.downloadRanges)
        assertEquals(0L, interceptor.downloadCallTimeoutNanos)
    }

    private fun assetRoot() = application.getExternalFilesDir("asset_delivery")

    private class ResumeDownloadInterceptor(
        private val payload: ByteArray,
        private val failAfterBytes: Long,
    ) : Interceptor {
        val downloadRanges = mutableListOf<String?>()
        var downloadCallTimeoutNanos: Long = -1L
        private var downloadCount = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.method == "POST") {
                return response(
                    request = request,
                    code = 200,
                    body = "{\"ticket\":\"ticket-${downloadCount + 1}\"}"
                        .toResponseBody(JSON_MEDIA_TYPE),
                )
            }

            val range = request.header("Range")
            downloadRanges += range
            downloadCallTimeoutNanos = chain.call().timeout().timeoutNanos()
            downloadCount++
            val start = range?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
            val body = if (downloadCount == 1) {
                FailingResponseBody(payload, failAfterBytes)
            } else {
                payload.copyOfRange(start, payload.size).toResponseBody(BINARY_MEDIA_TYPE)
            }
            return response(
                request = request,
                code = if (start > 0) 206 else 200,
                body = body,
            ).newBuilder()
                .header("x-drducbook-size", payload.size.toString())
                .header("x-drducbook-sha256", payload.sha256())
                .build()
        }

        private fun response(
            request: Request,
            code: Int,
            body: ResponseBody,
        ): Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Partial Content")
            .body(body)
            .build()
    }

    private class FailingResponseBody(
        private val payload: ByteArray,
        private val failAfterBytes: Long,
    ) : ResponseBody() {
        override fun contentType(): MediaType = BINARY_MEDIA_TYPE

        override fun contentLength(): Long = payload.size.toLong()

        override fun source(): BufferedSource {
            val source = Buffer().write(payload)
            return object : ForwardingSource(source) {
                private var emitted = 0L

                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (emitted >= failAfterBytes) throw SocketTimeoutException("timeout")
                    val read = super.read(sink, min(byteCount, failAfterBytes - emitted))
                    if (read > 0L) emitted += read
                    return read
                }
            }.buffer()
        }
    }

    private companion object {
        val TEST_CONFIG = SupabasePublicConfig(
            url = "https://example.test",
            publishableKey = "sb_publishable_test",
            googleAuthClientId = "",
            googleDriveClientId = "",
        )
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
