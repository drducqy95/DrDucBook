package io.legado.app.data.repository

import android.content.Context
import com.drducbook.app.cloud.AssetDeliveryClientContract
import com.drducbook.app.cloud.AssetFunctionRequest
import com.drducbook.app.cloud.SupabaseClientProvider
import com.drducbook.app.cloud.SupabasePublicConfig
import io.legado.app.domain.gateway.AssetDeliveryDownloadedFile
import io.legado.app.domain.gateway.AssetDeliveryGateway
import io.legado.app.domain.gateway.AssetDownloadProgress
import io.legado.app.domain.model.AssetDeliveryArtifact
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class AssetDeliveryRepository(
    context: Context,
    private val configProvider: () -> SupabasePublicConfig = { SupabaseClientProvider.config },
    private val client: OkHttpClient = okHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AssetDeliveryGateway {

    private val appContext = context.applicationContext
    private val downloadClient = client.newBuilder()
        // Model packages can legitimately take many minutes on a slow connection. Keep the
        // per-read timeout, but remove the shared client's 60-second whole-call deadline.
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(DOWNLOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build()

    override val configured: Boolean
        get() = configProvider().isConfigured

    override suspend fun downloadArtifact(
        artifact: AssetDeliveryArtifact,
        accessToken: String,
        onProgress: (AssetDownloadProgress) -> Unit,
    ): AssetDeliveryDownloadedFile {
        val target = targetFile(artifact)
        if (target.isFile && verifyFile(target, artifact)) {
            onProgress(AssetDownloadProgress(target.length(), artifact.sizeBytes))
            return target.toDownloadedFile(artifact)
        }

        completedPartialFile(artifact, target)?.let { completed ->
            onProgress(AssetDownloadProgress(completed.length(), artifact.sizeBytes))
            return completed.toDownloadedFile(artifact)
        }

        var lastError: IOException? = null
        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            coroutineContext.ensureActive()
            try {
                val ticket = fetchTicket(artifact, accessToken)
                return downloadWithTicket(artifact, ticket, target, onProgress)
            } catch (error: IOException) {
                lastError = error
                if (!error.isRetryableAssetFailure() || attempt == DOWNLOAD_ATTEMPTS - 1) {
                    throw error
                }
                delay(DOWNLOAD_RETRY_DELAY_MS * (attempt + 1L))
            }
        }
        throw lastError ?: IOException("Asset download failed")
    }

    private fun targetFile(artifact: AssetDeliveryArtifact): File {
        val root = appContext.getExternalFilesDir(ASSET_DIR)
            ?: File(appContext.filesDir, ASSET_DIR)
        val dir = File(root, artifact.kind.name.lowercase())
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Cannot create asset directory")
        }
        return File(dir, artifact.safeFileName())
    }

    private fun fetchTicket(
        artifact: AssetDeliveryArtifact,
        accessToken: String,
    ): String {
        val request = AssetDeliveryClientContract.buildTicketRequest(
            config = configProvider(),
            artifactId = artifact.id,
            supabaseAccessToken = accessToken,
        ).toOkHttpRequest()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AssetHttpException(
                    statusCode = response.code,
                    message = "Asset ticket request failed (${response.code}): " +
                        response.body.string().take(ERROR_BODY_LIMIT)
                )
            }
            val ticketResponse = json.decodeFromString<AssetTicketResponse>(
                response.body.string(),
            )
            val ticket = ticketResponse.ticket.takeIf { it.isNotBlank() }
                ?: throw IOException("Asset ticket response is missing a ticket")
            ticketResponse.artifact?.validateAgainst(artifact)
            return ticket
        }
    }

    private suspend fun downloadWithTicket(
        artifact: AssetDeliveryArtifact,
        ticket: String,
        target: File,
        onProgress: (AssetDownloadProgress) -> Unit,
    ): AssetDeliveryDownloadedFile {
        val temp = File(target.parentFile, "${artifact.id}.part")
        var existingBytes = temp.takeIf(File::isFile)?.length() ?: 0L
        if (existingBytes > artifact.sizeBytes) {
            if (!temp.delete()) throw IOException("Cannot reset invalid partial asset file")
            existingBytes = 0L
        }

        val request = AssetDeliveryClientContract.buildDownloadRequest(
            config = configProvider(),
            artifactId = artifact.id,
            ticket = ticket,
            rangeHeader = existingBytes.takeIf { it > 0L }?.let { "bytes=$it-" },
        ).toOkHttpRequest()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AssetHttpException(
                    statusCode = response.code,
                    message = "Asset download failed (${response.code}): " +
                        response.body.string().take(ERROR_BODY_LIMIT)
                )
            }
            response.header("x-drducbook-size")
                ?.toLongOrNull()
                ?.validateSize(artifact)
            response.header("x-drducbook-sha256")
                ?.takeIf { it.isNotBlank() }
                ?.validateSha256(artifact)

            val totalBytes = response.header("x-drducbook-size")?.toLongOrNull()
                ?: response.body.contentLength().takeIf { it > 0L }?.let { length ->
                    if (response.code == 206) existingBytes + length else length
                }
                ?: artifact.sizeBytes
            val append = existingBytes > 0L && response.code == 206
            if (!append && existingBytes > 0L) {
                if (!temp.delete()) throw IOException("Cannot reset partial asset file")
                existingBytes = 0L
            }
            val digest = MessageDigest.getInstance("SHA-256")
            if (append) updateDigest(digest, temp)
            var downloaded = existingBytes
            var lastProgress = existingBytes
            if (existingBytes > 0L) {
                onProgress(AssetDownloadProgress(existingBytes, totalBytes))
            }
            response.body.byteStream().use { input ->
                java.io.FileOutputStream(temp, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (
                            downloaded == totalBytes ||
                            downloaded - lastProgress >= PROGRESS_STEP_BYTES
                        ) {
                            lastProgress = downloaded
                            onProgress(AssetDownloadProgress(downloaded, totalBytes))
                        }
                    }
                }
            }

            if (downloaded != artifact.sizeBytes) {
                if (downloaded > artifact.sizeBytes) temp.delete()
                throw IncompleteAssetDownloadException(downloaded, artifact.sizeBytes)
            }
            val actualSha256 = digest.digest().toHexString()
            if (!actualSha256.equals(artifact.sha256, ignoreCase = true)) {
                temp.delete()
                throw IOException("Downloaded asset checksum mismatch")
            }
        }

        replaceAtomically(temp, target)
        onProgress(AssetDownloadProgress(target.length(), artifact.sizeBytes))
        return target.toDownloadedFile(artifact)
    }

    private fun completedPartialFile(
        artifact: AssetDeliveryArtifact,
        target: File,
    ): File? {
        val temp = File(target.parentFile, "${artifact.id}.part")
        if (!temp.isFile || temp.length() != artifact.sizeBytes) return null
        if (!verifyFile(temp, artifact)) {
            temp.delete()
            return null
        }
        replaceAtomically(temp, target)
        return target
    }

    private fun replaceAtomically(temp: File, target: File) {
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IOException("Cannot replace existing asset file")
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun verifyFile(file: File, artifact: AssetDeliveryArtifact): Boolean {
        if (file.length() != artifact.sizeBytes) return false
        return sha256(file).equals(artifact.sha256, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        updateDigest(digest, file)
        return digest.digest().toHexString()
    }

    private fun updateDigest(digest: MessageDigest, file: File) {
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
    }

    private fun AssetFunctionRequest.toOkHttpRequest(): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return when (method.uppercase()) {
            "POST" -> builder
                .post((body ?: "").toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            "GET" -> builder.get().build()
            else -> throw IllegalArgumentException("Unsupported asset request method")
        }
    }

    private fun TicketArtifact.validateAgainst(expected: AssetDeliveryArtifact) {
        if (id != expected.id) {
            throw IOException("Asset ticket artifact mismatch")
        }
        sizeBytes?.validateSize(expected)
        sha256?.takeIf { it.isNotBlank() }?.validateSha256(expected)
    }

    private fun Long.validateSize(expected: AssetDeliveryArtifact) {
        if (this != expected.sizeBytes) {
            throw IOException("Asset metadata size mismatch")
        }
    }

    private fun String.validateSha256(expected: AssetDeliveryArtifact) {
        if (!equals(expected.sha256, ignoreCase = true)) {
            throw IOException("Asset metadata checksum mismatch")
        }
    }

    private fun AssetDeliveryArtifact.safeFileName(): String {
        val cleaned = fileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(unsafeFileNameChars, "_")
            .trim()
        require(cleaned.isNotBlank()) { "Invalid asset file name" }
        return cleaned
    }

    private fun File.toDownloadedFile(artifact: AssetDeliveryArtifact): AssetDeliveryDownloadedFile =
        AssetDeliveryDownloadedFile(
            path = absolutePath,
            fileName = name,
            sizeBytes = length(),
            sha256 = artifact.sha256,
            mimeType = artifact.mimeType,
        )

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    @Serializable
    private data class AssetTicketResponse(
        val ticket: String,
        val expiresAt: String? = null,
        val artifact: TicketArtifact? = null,
    )

    @Serializable
    private data class TicketArtifact(
        val id: String,
        val fileName: String? = null,
        val sizeBytes: Long? = null,
        val sha256: String? = null,
        val deliveryClass: String? = null,
        val inventoryState: String? = null,
    )

    private class AssetHttpException(
        val statusCode: Int,
        message: String,
    ) : IOException(message)

    private class IncompleteAssetDownloadException(
        downloaded: Long,
        expected: Long,
    ) : IOException("Downloaded asset is incomplete ($downloaded/$expected bytes)")

    private fun IOException.isRetryableAssetFailure(): Boolean {
        val httpError = this as? AssetHttpException ?: return true
        return httpError.statusCode in RETRYABLE_HTTP_STATUS_CODES
    }

    private companion object {
        const val ASSET_DIR = "asset_delivery"
        const val PROGRESS_STEP_BYTES = 256L * 1024L
        const val ERROR_BODY_LIMIT = 240
        const val DOWNLOAD_ATTEMPTS = 3
        const val DOWNLOAD_RETRY_DELAY_MS = 750L
        const val DOWNLOAD_READ_TIMEOUT_MINUTES = 2L
        val RETRYABLE_HTTP_STATUS_CODES = setOf(408, 425, 429) + (500..599)
        val unsafeFileNameChars = Regex("[\\u0000-\\u001f\\\\/:*?\"<>|]")
    }
}
