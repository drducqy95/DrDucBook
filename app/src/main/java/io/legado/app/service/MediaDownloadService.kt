package io.legado.app.service

import android.content.Context
import android.content.Intent
import android.os.StatFs
import android.media.MediaExtractor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drducbook.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.FeatureFlags
import io.legado.app.constant.NotificationId
import io.legado.app.domain.gateway.MediaDownloadGateway
import io.legado.app.domain.model.MediaDownloadItem
import io.legado.app.domain.model.MediaDownloadState
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.usecase.ResolveBookMediaUseCase
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.media.MediaDownloadTransferPolicy
import io.legado.app.utils.NetworkUtils
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.koin.android.ext.android.inject
import splitties.systemservices.notificationManager
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaDownloadService : BaseService() {

    private val gateway: MediaDownloadGateway by inject()
    private val resolveBookMedia: ResolveBookMediaUseCase by inject()
    private val workers = mutableListOf<Job>()
    private val activeWorkers = AtomicInteger(0)
    private var completedItems = 0
    private var totalItems = 0
    private var recoverableItems = 0
    @Volatile
    private var hasQueuedWork = false

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            gateway.observeTasks().collect { tasks ->
                val items = tasks.flatMap { it.items }
                completedItems = items.count { it.state == MediaDownloadState.COMPLETED }
                totalItems = items.count { it.state != MediaDownloadState.CANCELED }
                recoverableItems = items.count {
                    it.state in setOf(MediaDownloadState.PAUSED, MediaDownloadState.FAILED)
                }
                hasQueuedWork = items.any {
                    it.state in setOf(MediaDownloadState.PENDING, MediaDownloadState.RUNNING)
                }
                updateNotification()
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            gateway.reconcileAfterProcessStart()
            withContext(Dispatchers.Main) { ensureWorkersStarted() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE_ALL -> lifecycleScope.launch(Dispatchers.IO) {
                gateway.pauseActive()
                withContext(Dispatchers.Main) { updateNotification() }
            }
            ACTION_RESUME_ALL -> lifecycleScope.launch(Dispatchers.IO) {
                gateway.resumeRecoverable()
                withContext(Dispatchers.Main) {
                    ensureWorkersStarted()
                    updateNotification()
                }
            }
            ACTION_CANCEL_ALL -> lifecycleScope.launch(Dispatchers.IO) {
                gateway.cancelActive()
                withContext(Dispatchers.Main) {
                    updateNotification()
                    stopWhenIdle()
                }
            }
            else -> ensureWorkersStarted()
        }
        return START_NOT_STICKY
    }

    private fun ensureWorkersStarted() {
        if (workers.any { it.isActive }) return
        workers.clear()
        repeat(MAX_PARALLEL_DOWNLOADS) {
            workers += lifecycleScope.launch(Dispatchers.IO) { workerLoop() }
        }
    }

    private suspend fun workerLoop() {
        var idleRounds = 0
        while (lifecycleScope.isActive) {
            if (!NetworkUtils.isAvailable()) {
                if (!hasQueuedWork) {
                    idleRounds++
                    if (idleRounds >= IDLE_STOP_ROUNDS && activeWorkers.get() == 0) {
                        withContext(Dispatchers.Main) { stopWhenIdle() }
                        return
                    }
                } else {
                    idleRounds = 0
                }
                delay(QUEUE_POLL_MS)
                continue
            }
            val item = gateway.claimNext()
            if (item == null) {
                idleRounds++
                if (idleRounds >= IDLE_STOP_ROUNDS && activeWorkers.get() == 0) {
                    withContext(Dispatchers.Main) { stopWhenIdle() }
                    return
                }
                delay(QUEUE_POLL_MS)
                continue
            }
            idleRounds = 0
            activeWorkers.incrementAndGet()
            try {
                download(item)
            } catch (error: DownloadInterruptedException) {
                // Pause and cancel are persisted by the UI action.
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                gateway.fail(item.id, error.localizedMessage ?: error.javaClass.simpleName)
            } finally {
                activeWorkers.decrementAndGet()
            }
        }
    }

    private suspend fun download(original: MediaDownloadItem) {
        val item = refreshExpiredSource(original)
        when (item.protocol) {
            MediaProtocol.HLS -> downloadHls(item)
            MediaProtocol.DASH -> downloadDash(item, allowRefresh = true)
            MediaProtocol.DIRECT, MediaProtocol.UNKNOWN -> downloadDirect(item, allowRefresh = true)
            else -> throw IOException("Định dạng ${item.protocol} chưa hỗ trợ tải ngoại tuyến")
        }
    }

    private suspend fun downloadDirect(item: MediaDownloadItem, allowRefresh: Boolean) {
        val target = targetFile(item)
        val temporary = item.tempPath.takeIf(String::isNotBlank)?.let(::File)
            ?: File(target.parentFile, "${target.name}.downloading")
        temporary.parentFile?.mkdirs()
        var existing = temporary.takeIf(File::isFile)?.length() ?: 0L
        val request = Request.Builder().url(item.sourceUri).apply {
            item.headers.forEach { (name, value) -> header(name, value) }
            if (existing > 0L) header("Range", "bytes=$existing-")
        }.build()
        var expectedTotal = item.responseContentLength
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code in listOf(401, 403) && allowRefresh) {
                val refreshed = refreshSource(item)
                return downloadDirect(refreshed, allowRefresh = false)
            }
            if (response.code == 416 && existing > 0L) {
                val expected = MediaDownloadTransferPolicy.contentRangeTotal(response.header("Content-Range"))
                if (expected == existing) {
                    finalizeDownload(item, temporary, target)
                    return
                }
                temporary.delete()
                gateway.updateProgress(item.id, 0L, expected ?: 0L, 0, "")
                return downloadDirect(item.copy(tempPath = "", bytesDownloaded = 0L), allowRefresh)
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} khi tải media")
            val responseLength = response.body.contentLength().coerceAtLeast(0L)
            val remoteTotal = MediaDownloadTransferPolicy.contentRangeTotal(
                response.header("Content-Range")
            ) ?: if (responseLength > 0L) {
                if (response.code == 206) existing + responseLength else responseLength
            } else 0L
            val responseEtag = response.header("ETag")
            val responseLastModified = response.header("Last-Modified")
            if (response.code == 206 && MediaDownloadTransferPolicy.resumeIdentityChanged(
                    existingBytes = existing,
                    storedEtag = item.responseEtag,
                    responseEtag = responseEtag,
                    storedLastModified = item.responseLastModified,
                    responseLastModified = responseLastModified,
                    storedContentLength = item.responseContentLength,
                    responseContentLength = remoteTotal,
                )
            ) {
                temporary.delete()
                gateway.updateProgress(item.id, 0L, remoteTotal, 0, "")
                gateway.updateTransferIdentity(
                    item.id,
                    responseEtag,
                    responseLastModified,
                    remoteTotal,
                )
                return downloadDirect(
                    item.copy(
                        bytesDownloaded = 0L,
                        tempPath = "",
                        responseEtag = responseEtag,
                        responseLastModified = responseLastModified,
                        responseContentLength = remoteTotal,
                    ),
                    allowRefresh,
                )
            }
            expectedTotal = remoteTotal
            gateway.updateTransferIdentity(
                item.id,
                responseEtag,
                responseLastModified,
                remoteTotal,
            )
            val plan = MediaDownloadTransferPolicy.directTransferPlan(existing, response.code)
            val append = plan.append
            if (!append && temporary.exists()) {
                temporary.delete()
            }
            existing = plan.initialBytes
            var downloaded = plan.initialBytes
            val remaining = responseLength
            val total = remoteTotal
            ensureFreeSpace(temporary, remaining)
            response.body.byteStream().buffered().use { input ->
                java.io.FileOutputStream(temporary, append).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastUpdate = 0L
                    while (true) {
                        ensureItemActive(item.id)
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= PROGRESS_UPDATE_MS) {
                            gateway.updateProgress(item.id, downloaded, total, 0, temporary.absolutePath)
                            lastUpdate = now
                        }
                    }
                }
            }
            gateway.updateProgress(item.id, downloaded, total, 0, temporary.absolutePath)
        }
        finalizeDownload(item.copy(responseContentLength = expectedTotal), temporary, target)
    }

    private suspend fun downloadHls(item: MediaDownloadItem) {
        val playlist = resolveMediaPlaylist(item.sourceUri, item.headers)
        val extension = if (playlist.lines.any { it.startsWith("#EXT-X-MAP") }) "mp4" else "ts"
        val target = targetFile(item, forcedExtension = extension)
        val temporary = item.tempPath.takeIf(String::isNotBlank)?.let(::File)
            ?: File(target.parentFile, "${target.name}.downloading")
        temporary.parentFile?.mkdirs()
        if (playlist.lines.any {
                it.startsWith("#EXT-X-KEY") &&
                    !it.contains("METHOD=AES-128", ignoreCase = true) &&
                    !it.contains("METHOD=NONE", ignoreCase = true)
            }
        ) {
            throw IOException("HLS mã hóa chưa hỗ trợ tải ngoại tuyến")
        }
        val segments = MediaDownloadTransferPolicy.parseHlsDownloadSegments(playlist.url, playlist.lines)
        val segmentUris = segments
        if (segmentUris.isEmpty()) throw IOException("Danh sách HLS không có segment")
        var segmentIndex = item.segmentIndex.coerceIn(0, segments.size)
        if (!temporary.exists()) segmentIndex = 0
        if (segmentIndex == 0 && temporary.exists()) temporary.delete()
        var downloaded = temporary.takeIf(File::isFile)?.length() ?: 0L
        val encryptionKeys = mutableMapOf<String, ByteArray>()
        val segmentScratch = File(temporary.parentFile, "${temporary.name}.segment")
        segmentScratch.delete()
        while (segmentIndex < segments.size) {
            ensureItemActive(item.id)
            val segment = segments[segmentIndex]
            downloadHlsSegmentWithRetry(
                item = item,
                segment = segment,
                segmentNumber = segmentIndex + 1,
                encryptionKeys = encryptionKeys,
                scratch = segmentScratch,
            )
            ensureFreeSpace(temporary, segmentScratch.length())
            java.io.FileOutputStream(temporary, true).buffered().use { output ->
                segmentScratch.inputStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }
            downloaded += segmentScratch.length()
            segmentScratch.delete()
            segmentIndex++
            gateway.updateProgress(
                item.id,
                downloaded,
                0L,
                segmentIndex,
                temporary.absolutePath,
            )
        }
        finalizeDownload(item, temporary, target)
    }

    private suspend fun downloadHlsSegmentWithRetry(
        item: MediaDownloadItem,
        segment: MediaDownloadTransferPolicy.HlsDownloadSegment,
        segmentNumber: Int,
        encryptionKeys: MutableMap<String, ByteArray>,
        scratch: File,
    ) {
        var lastError: IOException? = null
        repeat(HLS_SEGMENT_RETRY_ATTEMPTS) { attempt ->
            ensureItemActive(item.id)
            scratch.delete()
            try {
                downloadHlsSegment(
                    segment = segment,
                    segmentNumber = segmentNumber,
                    headers = item.headers,
                    encryptionKeys = encryptionKeys,
                    scratch = scratch,
                )
                return
            } catch (error: IOException) {
                lastError = error
                scratch.delete()
                if (attempt < HLS_SEGMENT_RETRY_ATTEMPTS - 1) {
                    delay(HLS_RETRY_DELAY_MS * (attempt + 1L))
                }
            }
        }
        throw lastError ?: IOException("HLS segment $segmentNumber failed")
    }

    private fun downloadHlsSegment(
        segment: MediaDownloadTransferPolicy.HlsDownloadSegment,
        segmentNumber: Int,
        headers: Map<String, String>,
        encryptionKeys: MutableMap<String, ByteArray>,
        scratch: File,
    ) {
        val request = Request.Builder().url(segment.url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            segment.byteRange?.let { header("Range", it.toHttpRangeHeader()) }
        }.build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} tại HLS segment $segmentNumber")
            }
            scratch.parentFile?.mkdirs()
            response.body.byteStream().buffered().use { rawInput ->
                val input = segment.keyUrl?.let { keyUrl ->
                    val key = encryptionKeys.getOrPut(keyUrl) {
                        downloadHlsKey(keyUrl, headers)
                    }
                    val iv = segment.iv ?: throw IOException("Encrypted HLS segment is missing IV")
                    CipherInputStream(
                        rawInput,
                        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                            init(
                                Cipher.DECRYPT_MODE,
                                SecretKeySpec(key, "AES"),
                                IvParameterSpec(iv),
                            )
                        },
                    )
                } ?: rawInput
                scratch.outputStream().buffered().use { output ->
                    input.use { resolvedInput ->
                        resolvedInput.copyTo(output)
                    }
                }
            }
        }
        if (!scratch.isFile) throw IOException("HLS segment $segmentNumber failed")
    }

    private suspend fun downloadDash(item: MediaDownloadItem, allowRefresh: Boolean) {
        val manifest = Request.Builder().url(item.sourceUri).apply {
            item.headers.forEach { (name, value) -> header(name, value) }
        }.build().let { request ->
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code in listOf(401, 403) && allowRefresh) {
                    val refreshed = refreshSource(item)
                    return downloadDash(refreshed, allowRefresh = false)
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} khi tải DASH manifest")
                response.body.string()
            }
        }
        val plan = MediaDownloadTransferPolicy.parseDashDownloadPlan(item.sourceUri, manifest)
        if (plan.segments.isEmpty()) throw IOException("DASH manifest không có segment")
        val target = targetFile(item, forcedExtension = plan.extension)
        val temporary = item.tempPath.takeIf(String::isNotBlank)?.let(::File)
            ?: File(target.parentFile, "${target.name}.downloading")
        temporary.parentFile?.mkdirs()
        var segmentIndex = item.segmentIndex.coerceIn(0, plan.segments.size)
        if (!temporary.exists()) segmentIndex = 0
        if (segmentIndex == 0 && temporary.exists()) temporary.delete()
        var downloaded = temporary.takeIf(File::isFile)?.length() ?: 0L
        val segmentScratch = File(temporary.parentFile, "${temporary.name}.dash-segment")
        segmentScratch.delete()
        while (segmentIndex < plan.segments.size) {
            ensureItemActive(item.id)
            val segment = plan.segments[segmentIndex]
            downloadDashSegmentWithRetry(
                itemId = item.id,
                segment = segment,
                segmentNumber = segmentIndex + 1,
                headers = item.headers,
                scratch = segmentScratch,
            )
            ensureFreeSpace(temporary, segmentScratch.length())
            java.io.FileOutputStream(temporary, true).buffered().use { output ->
                segmentScratch.inputStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }
            downloaded += segmentScratch.length()
            segmentScratch.delete()
            segmentIndex++
            gateway.updateProgress(
                item.id,
                downloaded,
                0L,
                segmentIndex,
                temporary.absolutePath,
            )
        }
        finalizeDownload(
            item.copy(mimeType = plan.mimeType ?: item.mimeType.ifBlank { "video/mp4" }),
            temporary,
            target,
        )
    }

    private suspend fun downloadDashSegmentWithRetry(
        itemId: String,
        segment: MediaDownloadTransferPolicy.DashDownloadSegment,
        segmentNumber: Int,
        headers: Map<String, String>,
        scratch: File,
    ) {
        var lastError: IOException? = null
        repeat(DASH_SEGMENT_RETRY_ATTEMPTS) { attempt ->
            ensureItemActive(itemId)
            scratch.delete()
            try {
                downloadDashSegment(segment, segmentNumber, headers, scratch)
                return
            } catch (error: IOException) {
                lastError = error
                scratch.delete()
                if (attempt < DASH_SEGMENT_RETRY_ATTEMPTS - 1) {
                    delay(DASH_RETRY_DELAY_MS * (attempt + 1L))
                }
            }
        }
        throw lastError ?: IOException("DASH segment $segmentNumber failed")
    }

    private fun downloadDashSegment(
        segment: MediaDownloadTransferPolicy.DashDownloadSegment,
        segmentNumber: Int,
        headers: Map<String, String>,
        scratch: File,
    ) {
        val request = Request.Builder().url(segment.url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            segment.byteRange?.let { header("Range", it.toHttpRangeHeader()) }
        }.build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} khi tải DASH segment $segmentNumber")
            }
            scratch.parentFile?.mkdirs()
            response.body.byteStream().buffered().use { input ->
                scratch.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (!scratch.isFile) throw IOException("DASH segment $segmentNumber failed")
    }

    private fun downloadHlsKey(url: String, headers: Map<String, String>): ByteArray {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} while downloading HLS key")
            response.body.bytes().also { key ->
                if (key.size !in setOf(16, 24, 32)) throw IOException("Invalid HLS AES key length")
            }
        }
    }

    private fun resolveMediaPlaylist(url: String, headers: Map<String, String>): HlsPlaylist {
        var currentUrl = url
        val visited = mutableSetOf<String>()
        repeat(MAX_HLS_PLAYLIST_DEPTH) {
            if (!visited.add(currentUrl)) throw IOException("HLS playlist loop")
            val request = Request.Builder().url(currentUrl).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build()
            val lines = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} khi tải playlist HLS")
                response.body.string().lineSequence().map(String::trim).filter(String::isNotBlank).toList()
            }
            val selectedVariant = MediaDownloadTransferPolicy.selectBestHlsVariant(currentUrl, lines)
                ?: return HlsPlaylist(currentUrl, lines)
            currentUrl = selectedVariant.url
        }
        throw IOException("Playlist HLS lồng quá sâu")
    }

    private suspend fun refreshExpiredSource(item: MediaDownloadItem): MediaDownloadItem {
        return if (item.expiresAt != null && item.expiresAt <= System.currentTimeMillis()) {
            refreshSource(item)
        } else item
    }

    private suspend fun refreshSource(item: MediaDownloadItem): MediaDownloadItem {
        val resolved = resolveBookMedia.execute(item.bookUrl, item.chapterIndex).getOrThrow()
        val variant = resolved.media.variants.firstOrNull { it.id == item.variantId }
            ?: resolved.media.variants.firstOrNull { it.downloadSupported && !it.externalPlayerRequired }
            ?: throw IOException("Không tìm thấy biến thể media để làm mới link tải")
        gateway.updateSource(item.id, variant.uri, variant.headers, variant.expiresAt)
        return item.copy(
            sourceUri = variant.uri,
            headers = variant.headers,
            expiresAt = variant.expiresAt,
        )
    }

    private suspend fun ensureItemActive(itemId: String) {
        val state = gateway.getItem(itemId)?.state ?: throw DownloadInterruptedException()
        if (state != MediaDownloadState.RUNNING) throw DownloadInterruptedException()
    }

    private fun ensureFreeSpace(target: File, expectedBytes: Long) {
        val required = expectedBytes.coerceAtLeast(0L) + MIN_FREE_BYTES
        val available = StatFs(target.parentFile?.absolutePath ?: filesDir.absolutePath).availableBytes
        if (available < required) throw IOException("Không đủ dung lượng trống để tải media")
    }

    private fun targetFile(item: MediaDownloadItem, forcedExtension: String? = null): File {
        val extension = forcedExtension ?: extensionFor(item)
        val root = File(getExternalFilesDir(null) ?: filesDir, "media_downloads/${item.bookUrl.hashCode()}")
            .apply { mkdirs() }
        return File(root, "%05d-%s.%s".format(item.chapterIndex, item.id.take(8), extension))
    }

    private fun extensionFor(item: MediaDownloadItem): String = when {
        item.mimeType.contains("mp4", ignoreCase = true) -> "mp4"
        item.mimeType.contains("mpeg", ignoreCase = true) -> "mp3"
        item.mimeType.contains("aac", ignoreCase = true) -> "aac"
        item.mimeType.contains("ogg", ignoreCase = true) -> "ogg"
        item.mimeType.contains("webm", ignoreCase = true) -> "webm"
        else -> item.sourceUri.substringBefore('?').substringAfterLast('.', "bin").take(5)
    }

    private suspend fun finalizeDownload(item: MediaDownloadItem, temporary: File, target: File) {
        if (item.protocol != MediaProtocol.HLS && item.responseContentLength > 0L &&
            temporary.length() != item.responseContentLength
        ) {
            throw IOException("Downloaded media size does not match the remote file")
        }
        probeMediaFile(temporary)
        if (!temporary.isFile || temporary.length() == 0L) throw IOException("Tệp tải xuống đang trống")
        if (target.exists() && !target.delete()) throw IOException("Không thể thay tệp media cũ")
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        val checksum = withContext(Dispatchers.IO) { sha256(target) }
        val offlineMimeType = if (item.protocol == MediaProtocol.HLS) {
            if (target.extension.equals("mp4", ignoreCase = true)) "video/mp4" else "video/mp2t"
        } else {
            item.mimeType
        }
        gateway.complete(
            item.id,
            target.length(),
            target.absolutePath,
            checksum,
            offlineMimeType,
        )
    }

    private fun probeMediaFile(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.trackCount <= 0) throw IOException("Downloaded file has no playable media track")
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("Unable to verify downloaded media", error)
        } finally {
            extractor.release()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun createNotification(): android.app.Notification {
        val progress = if (totalItems > 0) (completedItems * 100 / totalItems) else 0
        return NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(hasQueuedWork || activeWorkers.get() > 0 || recoverableItems > 0)
            .setContentTitle(getString(R.string.media_download_notification_title))
            .setContentText(getString(R.string.media_download_notification_progress, completedItems, totalItems))
            .setProgress(100, progress, totalItems == 0)
            .setContentIntent(activityPendingIntent<MainActivity>("media-downloads"))
            .addAction(
                R.drawable.ic_pause,
                getString(R.string.media_download_pause_all),
                servicePendingIntent<MediaDownloadService>(ACTION_PAUSE_ALL, ACTION_PAUSE_ALL_REQUEST),
            )
            .addAction(
                R.drawable.ic_play,
                getString(R.string.media_download_resume_all),
                servicePendingIntent<MediaDownloadService>(ACTION_RESUME_ALL, ACTION_RESUME_ALL_REQUEST),
            )
            .addAction(
                R.drawable.ic_stop_black_24dp,
                getString(R.string.media_download_cancel_all),
                servicePendingIntent<MediaDownloadService>(ACTION_CANCEL_ALL, ACTION_CANCEL_ALL_REQUEST),
            )
            .build()
    }

    private fun updateNotification() {
        notificationManager.notify(NotificationId.MediaDownloadService, createNotification())
    }

    override fun startForegroundNotification() {
        startForeground(NotificationId.MediaDownloadService, createNotification())
    }

    private fun stopWhenIdle() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        workers.forEach(Job::cancel)
        notificationManager.cancel(NotificationId.MediaDownloadService)
        super.onDestroy()
    }

    private data class HlsPlaylist(val url: String, val lines: List<String>)
    private class DownloadInterruptedException : IOException()

    companion object {
        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val QUEUE_POLL_MS = 1_000L
        private const val IDLE_STOP_ROUNDS = 10
        private const val PROGRESS_UPDATE_MS = 500L
        private const val MIN_FREE_BYTES = 100L * 1024L * 1024L
        private const val MAX_HLS_PLAYLIST_DEPTH = 4
        private const val HLS_SEGMENT_RETRY_ATTEMPTS = 3
        private const val HLS_RETRY_DELAY_MS = 500L
        private const val DASH_SEGMENT_RETRY_ATTEMPTS = 3
        private const val DASH_RETRY_DELAY_MS = 500L
        private const val ACTION_PAUSE_ALL = "media_download_pause_all"
        private const val ACTION_RESUME_ALL = "media_download_resume_all"
        private const val ACTION_CANCEL_ALL = "media_download_cancel_all"
        private const val ACTION_PAUSE_ALL_REQUEST = 1
        private const val ACTION_RESUME_ALL_REQUEST = 2
        private const val ACTION_CANCEL_ALL_REQUEST = 3

        fun start(context: Context) {
            if (!FeatureFlags.mediaDownload) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, MediaDownloadService::class.java),
            )
        }
    }
}
