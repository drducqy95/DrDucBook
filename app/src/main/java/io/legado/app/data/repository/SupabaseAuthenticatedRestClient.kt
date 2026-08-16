package io.legado.app.data.repository

import com.drducbook.app.cloud.SupabasePublicConfig
import android.util.Base64
import io.legado.app.domain.gateway.AccountAuthGateway
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

internal class SupabaseAuthenticatedRestClient(
    private val config: SupabasePublicConfig,
    private val accountAuthGateway: AccountAuthGateway,
    private val client: OkHttpClient = okHttpClient,
) : AccountAccessRestClient {
    override val configured: Boolean
        get() = config.isConfigured

    override suspend fun get(
        path: String,
        query: Map<String, String>,
    ): String = execute(
        Request.Builder()
            .url(url(path, query))
            .get(),
    )

    override suspend fun post(
        path: String,
        body: String,
        query: Map<String, String>,
        prefer: String?,
    ): String {
        val builder = Request.Builder()
            .url(url(path, query))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        prefer?.let { builder.header("Prefer", it) }
        return execute(builder)
    }

    suspend fun putFile(
        path: String,
        source: File,
        upsert: Boolean = false,
    ) {
        val builder = Request.Builder()
            .url(url(path))
            .header("x-upsert", upsert.toString())
            .put(source.asRequestBody(OCTET_STREAM_MEDIA_TYPE))
        execute(builder)
    }

    /**
     * Uploads large objects using Supabase Storage's TUS endpoint. A single failed
     * request is retried after refreshing the access token; each retry starts from
     * a new TUS session so no partial object is ever published under the final path.
     */
    suspend fun uploadFileResumable(
        bucket: String,
        objectPath: String,
        source: File,
        upsert: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        require(source.isFile) { "Supabase upload source is missing" }
        var refreshed = false
        while (true) {
            try {
                uploadFileResumableOnce(bucket, objectPath, source, upsert)
                return@withContext
            } catch (error: UnauthorizedException) {
                if (refreshed) throw IOException("Supabase phiên đăng nhập đã hết hạn", error)
                refreshed = true
                accountAuthGateway.refreshSession()
            }
        }
    }

    private suspend fun uploadFileResumableOnce(
        bucket: String,
        objectPath: String,
        source: File,
        upsert: Boolean,
    ) {
        val uploadUrl = createTusUpload(bucket, objectPath, source.length(), upsert)
        var offset = 0L
        RandomAccessFile(source, "r").use { input ->
            while (offset < source.length()) {
                coroutineContext.ensureActive()
                val chunkSize = minOf(TUS_CHUNK_BYTES, source.length() - offset).toInt()
                val chunk = ByteArray(chunkSize)
                input.seek(offset)
                input.readFully(chunk)
                val builder = Request.Builder()
                    .url(uploadUrl)
                    .header("Tus-Resumable", TUS_VERSION)
                    .header("Upload-Offset", offset.toString())
                    .patch(chunk.toRequestBody(OCTET_STREAM_MEDIA_TYPE))
                val request = authenticated(builder).build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 401) throw UnauthorizedException()
                    if (response.code != 204 && response.code != 200) {
                        response.requireSuccess()
                    }
                    val nextOffset = response.header("Upload-Offset")?.toLongOrNull()
                    val previousOffset = offset
                    offset = nextOffset ?: (offset + chunkSize)
                    require(offset > previousOffset && offset <= source.length()) {
                        "Supabase TUS trả về offset không hợp lệ"
                    }
                }
            }
        }
    }

    private suspend fun createTusUpload(
        bucket: String,
        objectPath: String,
        size: Long,
        upsert: Boolean,
    ): HttpUrl {
        val metadata = listOf(
            "bucketName" to bucket,
            "objectName" to objectPath,
            "contentType" to "application/octet-stream",
        ).joinToString(",") { (key, value) ->
            "$key ${Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
        }
        val builder = Request.Builder()
            .url(tusEndpoint())
            .header("Tus-Resumable", TUS_VERSION)
            .header("Upload-Length", size.toString())
            .header("Upload-Metadata", metadata)
            .header("x-upsert", upsert.toString())
            .post(ByteArray(0).toRequestBody(OCTET_STREAM_MEDIA_TYPE))
        val request = authenticated(builder).build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedException()
            if (response.code != 201 && response.code != 200) response.requireSuccess()
            val location = response.header("Location")
                ?: throw IOException("Supabase TUS không trả về URL phiên tải lên")
            tusEndpoint().resolve(location)
                ?: throw IOException("Supabase TUS trả về URL phiên tải lên không hợp lệ")
        }
    }

    private fun tusEndpoint(): HttpUrl {
        val base = config.url.toHttpUrl()
        val host = base.host
        return if (host.endsWith(".supabase.co")) {
            base.newBuilder()
                .host(host.removeSuffix(".supabase.co") + ".storage.supabase.co")
                .encodedPath("/storage/v1/upload/resumable")
                .query(null)
                .build()
        } else {
            base.newBuilder()
                .encodedPath("/storage/v1/upload/resumable")
                .query(null)
                .build()
        }
    }

    suspend fun delete(
        path: String,
        query: Map<String, String> = emptyMap(),
    ) {
        execute(Request.Builder().url(url(path, query)).delete())
    }

    suspend fun downloadTo(
        path: String,
        destination: File,
    ): Boolean = withContext(Dispatchers.IO) {
        var refreshed = false
        var result: Boolean? = null
        while (true) {
            val request = authenticated(Request.Builder().url(url(path))).get().build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 && !refreshed) {
                    refreshed = true
                    accountAuthGateway.refreshSession()
                } else {
                    if (response.code == 404) {
                        result = false
                    } else {
                        response.requireSuccess()
                        destination.parentFile?.mkdirs()
                        response.body.byteStream().use { input ->
                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        result = true
                    }
                }
            }
            if (result != null) break
        }
        checkNotNull(result) { "Supabase download did not complete" }
    }

    private suspend fun execute(builder: Request.Builder): String = withContext(Dispatchers.IO) {
        var refreshed = false
        var result: String? = null
        while (true) {
            val request = authenticated(builder).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401 && !refreshed) {
                    refreshed = true
                    accountAuthGateway.refreshSession()
                } else {
                    response.requireSuccess()
                    result = response.body.string()
                }
            }
            if (result != null) break
        }
        checkNotNull(result) { "Supabase request did not complete" }
    }

    private suspend fun authenticated(builder: Request.Builder): Request.Builder {
        config.requireValid()
        val accessToken = accountAuthGateway.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Cần đăng nhập để tiếp tục")
        return builder
            .header("apikey", config.publishableKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
    }

    private fun url(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): HttpUrl {
        val builder = config.url.toHttpUrl().newBuilder()
        path.trim('/').split('/').filter(String::isNotBlank).forEach(builder::addPathSegment)
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private fun okhttp3.Response.requireSuccess() {
        if (isSuccessful) return
        val detail = body.string()
            .take(300)
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
        throw IOException(
            if (detail.isBlank()) "Supabase trả về lỗi HTTP $code"
            else "Supabase trả về lỗi HTTP $code: $detail"
        )
    }

    private class UnauthorizedException : IOException()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        const val TUS_VERSION = "1.0.0"
        const val TUS_CHUNK_BYTES = 6L * 1024L * 1024L
    }
}

internal interface AccountAccessRestClient {
    val configured: Boolean

    suspend fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String

    suspend fun post(
        path: String,
        body: String,
        query: Map<String, String> = emptyMap(),
        prefer: String? = null,
    ): String
}
