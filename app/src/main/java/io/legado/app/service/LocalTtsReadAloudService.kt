package io.legado.app.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.IntentAction
import io.legado.app.model.ReadAloud
import io.legado.app.model.tts.LocalTtsSynthesis
import io.legado.app.model.tts.parseLocalTtsEngine
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** On-device, file-backed TTS playback for imported ONNX voice models. */
class LocalTtsReadAloudService : BaseReadAloudService(), Player.Listener {
    private val player by lazy { ExoPlayer.Builder(this).build() }
    private var generationJob: Job? = null
    private var prefetchJob: Deferred<PrefetchedLocalTts?>? = null
    private var prefetchRequest: LocalTtsRequest? = null
    private var localSessionId = 0L
    private var pendingProgress: Int? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        localSessionId = intent?.getLongExtra("localTtsSessionId", localSessionId) ?: localSessionId
        return super.onStartCommand(intent, flags, startId)
    }

    override fun publishReadAloudState(state: Int) {
        super.publishReadAloudState(state)
        sendBroadcast(
            Intent(IntentAction.localTtsState)
                .setPackage(packageName)
                .putExtra("state", state)
                .putExtra("localTtsSessionId", localSessionId)
        )
    }

    override fun onCreate() {
        super.onCreate()
        player.addListener(this)
        applySpeechRate()
    }

    override fun onDestroy() {
        generationJob?.cancel()
        clearPrefetch()
        player.release()
        super.onDestroy()
    }

    /**
     * The local model service is intentionally isolated in :tts_onnx. ReadBook and the event bus
     * are process-local, so receive a file-backed chapter snapshot prepared by ReadAloud instead
     * of trying to resolve the main-process singleton here.
     */
    override fun newReadAloud(play: Boolean, pageIndex: Int, startPos: Int) {
        generationJob?.cancel()
        clearPrefetch()
        val path = sessionFilePath
        if (path.isNullOrBlank()) {
            failPlayback("Không nhận được nội dung chương cho TTS local")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { File(path).readText(Charsets.UTF_8) }
                .onSuccess { chapterText ->
                    val paragraphs = chapterText
                        .split('\n')
                    if (paragraphs.none(::isSpeakableText)) {
                        launch(Dispatchers.Main) { failPlayback("Chương hiện tại không có nội dung để đọc") }
                        return@onSuccess
                    }
                    var remaining = startPos.coerceAtLeast(0)
                    var paragraph = 0
                    while (paragraph < paragraphs.lastIndex && remaining > paragraphs[paragraph].length) {
                        remaining -= paragraphs[paragraph].length + 1
                        paragraph++
                    }
                    contentList = paragraphs
                    nowSpeak = paragraph.coerceIn(0, paragraphs.lastIndex)
                    paragraphStartPos = remaining.coerceAtLeast(0)
                    readAloudNumber = startPos.coerceAtLeast(0)
                    this@LocalTtsReadAloudService.pageIndex = pageIndex
                    lifecycleScope.launch(Dispatchers.Main) {
                        upMediaMetadata(showContent = true)
                        if (play) play() else pageChanged = true
                    }
                }
                .onFailure { error ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        AppLog.put("Không thể đọc snapshot TTS local\n${error.localizedMessage}", error)
                        failPlayback("Không thể đọc nội dung chương")
                    }
                }
        }
    }

    private fun normalizeTtsParagraph(value: String): String = value
        .replace('\u00A0', ' ')
        .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    override fun play() {
        pageChanged = false
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("Danh sách đọc bằng model local đang trống")
            failPlayback("Chương hiện tại chưa sẵn sàng để đọc")
            return
        }
        super.play()
        synthesizeCurrentParagraph()
    }

    private fun synthesizeCurrentParagraph() {
        generationJob?.cancel()
        player.stop()
        val engineReference = sessionTtsEngine ?: ReadAloud.ttsEngine.orEmpty()
        if (parseLocalTtsEngine(engineReference) == null) {
            failPlayback("Cấu hình model TTS local không hợp lệ")
            return
        }
        val request = speechRequestFor(
            paragraphIndex = nowSpeak,
            paragraphStartPosition = paragraphStartPos,
            engineReference = engineReference,
        )
        if (request == null) {
            lifecycleScope.launch { advanceAndPlay(applyInterval = false) }
            return
        }
        generationJob = lifecycleScope.launch {
            try {
                val wav = wavFor(request)
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(wav)))
                applySpeechRate()
                pendingProgress = readAloudNumber + 1
                player.prepare()
                upMediaMetadata(showContent = true)
                prefetchAfter(request)
            } catch (error: Throwable) {
                if (error is CancellationException) return@launch
                AppLog.put("Lỗi tổng hợp giọng đọc local\n${error.localizedMessage}", error, true)
                failPlayback(error.localizedMessage ?: "Không thể tổng hợp giọng đọc local")
            }
        }
    }

    private suspend fun advanceAndPlay(applyInterval: Boolean = true) {
        readAloudNumber += contentList[nowSpeak].length + 1 - paragraphStartPos
        paragraphStartPos = 0
        while (nowSpeak < contentList.lastIndex) {
            nowSpeak++
            if (isSpeakableText(contentList[nowSpeak])) break
            readAloudNumber += contentList[nowSpeak].length + 1
        }
        if (nowSpeak < contentList.size && isSpeakableText(contentList[nowSpeak])) {
            upMediaMetadata(showContent = true)
            if (applyInterval) {
                val interval = ReadConfig.ttsParagraphInterval.toLong().coerceAtLeast(0)
                if (interval > 0) delay(interval)
            }
            if (!pause) synthesizeCurrentParagraph()
        } else {
            nextChapter()
        }
    }

    private fun isSpeakableText(value: String): Boolean {
        val normalized = normalizeTtsParagraph(value)
        return normalized.isNotEmpty() && !normalized.matches(AppPattern.notReadAloudRegex)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> if (!pause) {
                player.play()
                publishPendingProgress()
            }
            Player.STATE_ENDED -> lifecycleScope.launch { advanceAndPlay() }
        }
    }

    override fun playStop() {
        generationJob?.cancel()
        clearPrefetch()
        player.stop()
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        player.pause()
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        if (pageChanged || player.playbackState == Player.STATE_IDLE) {
            play()
        } else {
            player.play()
            publishPendingProgress()
        }
    }

    override fun upSpeechRate(reset: Boolean) {
        applySpeechRate()
        if (reset && !pause && player.playbackState == Player.STATE_IDLE) play()
    }

    override fun nextChapter() {
        sendBroadcast(android.content.Intent(IntentAction.localTtsNext).setPackage(packageName))
        stopSelf()
    }

    override fun prevChapter() {
        sendBroadcast(android.content.Intent(IntentAction.localTtsPrev).setPackage(packageName))
        stopSelf()
    }

    private fun applySpeechRate() {
        val speed = if (ReadConfig.ttsFollowSys) 1f else (ReadConfig.ttsSpeechRate + 5) / 10f
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 3f))
    }

    private fun failPlayback(message: String) {
        toastOnUi(message)
        if (!pause) pauseReadAloud()
    }

    private fun speechRequestFor(
        paragraphIndex: Int,
        paragraphStartPosition: Int,
        engineReference: String,
    ): LocalTtsRequest? {
        val source = contentList.getOrNull(paragraphIndex) ?: return null
        val start = paragraphStartPosition.coerceIn(0, source.length)
        val text = normalizeTtsParagraph(source.substring(start))
        if (!isSpeakableText(text)) return null
        return LocalTtsRequest(
            paragraphIndex = paragraphIndex,
            paragraphStartPosition = start,
            engineReference = engineReference,
            text = text,
        )
    }

    private fun nextSpeechRequestAfter(
        paragraphIndex: Int,
        engineReference: String,
    ): LocalTtsRequest? {
        var index = paragraphIndex + 1
        while (index < contentList.size) {
            speechRequestFor(index, 0, engineReference)?.let { return it }
            index++
        }
        return null
    }

    private suspend fun wavFor(request: LocalTtsRequest): File {
        val activePrefetch = prefetchJob
        if (prefetchRequest == request && activePrefetch != null) {
            activePrefetch.await()?.takeIf { it.request == request }?.let { prefetched ->
                if (prefetchJob == activePrefetch) {
                    prefetchJob = null
                    prefetchRequest = null
                }
                AppLog.putDebug("Local TTS dùng audio đã nạp trước: paragraph=${request.paragraphIndex}")
                return prefetched.wav
            }
        }
        return synthesizeToWav(request)
    }

    private suspend fun synthesizeToWav(request: LocalTtsRequest): File =
        withContext(Dispatchers.Default) {
            LocalTtsSynthesis.synthesizeToWav(
                context = this@LocalTtsReadAloudService,
                engineReference = request.engineReference,
                text = request.text,
            )
        }

    private fun prefetchAfter(request: LocalTtsRequest) {
        val nextRequest = nextSpeechRequestAfter(request.paragraphIndex, request.engineReference)
            ?: run {
                clearPrefetch()
                return
            }
        if (prefetchRequest == nextRequest) return
        clearPrefetch()
        prefetchRequest = nextRequest
        prefetchJob = lifecycleScope.async {
            try {
                PrefetchedLocalTts(nextRequest, synthesizeToWav(nextRequest))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                AppLog.putDebug(
                    "Local TTS nạp trước thất bại: paragraph=${nextRequest.paragraphIndex}, ${error.localizedMessage}"
                )
                null
            }
        }
    }

    private fun clearPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchRequest = null
    }

    private fun publishPendingProgress() {
        val progress = pendingProgress ?: return
        pendingProgress = null
        upTtsProgress(progress)
        sendBroadcast(
            Intent(IntentAction.localTtsProgress)
                .setPackage(packageName)
                .putExtra("chapterStart", progress)
                .putExtra("localTtsSessionId", localSessionId)
        )
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? =
        servicePendingIntent<LocalTtsReadAloudService>(actionStr)

}

private data class LocalTtsRequest(
    val paragraphIndex: Int,
    val paragraphStartPosition: Int,
    val engineReference: String,
    val text: String,
)

private data class PrefetchedLocalTts(
    val request: LocalTtsRequest,
    val wav: File,
)
