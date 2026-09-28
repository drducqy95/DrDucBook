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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** On-device, file-backed TTS playback for imported ONNX voice models. */
class LocalTtsReadAloudService : BaseReadAloudService(), Player.Listener {
    companion object {
        private const val PREFETCH_LOOKAHEAD_COUNT = 2
    }

    private val player by lazy { ExoPlayer.Builder(this).build() }
    private var generationJob: Job? = null
    private var prefetchJob: Job? = null
    private var currentlyPrefetchingIndex: Int? = null
    private var localSessionId = 0L
    private var pendingProgress: Int? = null
    private var paragraphOffsets: List<Int> = emptyList()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        localSessionId = intent.getLongExtra("localTtsSessionId", localSessionId)
        when (intent.action) {
            IntentAction.prevParagraph -> {
                skipToPrevParagraph()
                return START_NOT_STICKY
            }
            IntentAction.nextParagraph -> {
                skipToNextParagraph()
                return START_NOT_STICKY
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
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
        LocalTtsSynthesis.clearCache()
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
        player.stop()
        player.clearMediaItems()
        val path = sessionFilePath
        if (path.isNullOrBlank()) {
            failPlayback("Không nhận được nội dung chương cho TTS local")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { File(path).readText(Charsets.UTF_8) }
                .onSuccess { rawContent ->
                    var paragraphs: List<String> = emptyList()
                    var offsets: List<Int> = emptyList()
                    var initialParagraphIndex = -1
                    var initialParagraphStartPos = 0
                    var initialCharPos = -1

                    if (rawContent.trimStart().startsWith("{")) {
                        runCatching {
                            val json = org.json.JSONObject(rawContent)
                            val pArray = json.optJSONArray("paragraphs")
                            if (pArray != null) {
                                val pList = ArrayList<String>(pArray.length())
                                val oList = ArrayList<Int>(pArray.length())
                                for (i in 0 until pArray.length()) {
                                    val item = pArray.getJSONObject(i)
                                    pList.add(item.getString("text"))
                                    oList.add(item.getInt("chapterPosition"))
                                }
                                paragraphs = pList
                                offsets = oList
                            }
                            initialParagraphIndex = json.optInt("initialParagraphIndex", -1)
                            initialParagraphStartPos = json.optInt("initialParagraphStartPos", 0)
                            initialCharPos = json.optInt("initialCharPos", -1)
                        }
                    }

                    if (paragraphs.isEmpty()) {
                        paragraphs = rawContent.split('\n')
                        var currentPos = 0
                        offsets = paragraphs.map { p ->
                            val pos = currentPos
                            currentPos += p.length + 1
                            pos
                        }
                    }

                    if (paragraphs.none(::isSpeakableText)) {
                        launch(Dispatchers.Main) { failPlayback("Chương hiện tại không có nội dung để đọc") }
                        return@onSuccess
                    }

                    var targetIndex = if (initialParagraphIndex in paragraphs.indices) {
                        initialParagraphIndex
                    } else if (initialCharPos >= 0) {
                        offsets.indexOfLast { it <= initialCharPos }.coerceAtLeast(0)
                    } else {
                        var remaining = startPos.coerceAtLeast(0)
                        var p = 0
                        while (p < paragraphs.lastIndex && remaining > paragraphs[p].length) {
                            remaining -= paragraphs[p].length + 1
                            p++
                        }
                        p
                    }

                    while (targetIndex < paragraphs.size && !isSpeakableText(paragraphs[targetIndex])) {
                        targetIndex++
                    }
                    if (targetIndex >= paragraphs.size) {
                        targetIndex = paragraphs.indices.firstOrNull { isSpeakableText(paragraphs[it]) } ?: 0
                    }

                    contentList = paragraphs
                    paragraphOffsets = offsets
                    nowSpeak = targetIndex.coerceIn(0, paragraphs.lastIndex)
                    paragraphStartPos = if (targetIndex == initialParagraphIndex) {
                        initialParagraphStartPos.coerceAtLeast(0)
                    } else {
                        0
                    }
                    readAloudNumber = paragraphOffsets.getOrElse(nowSpeak) { 0 } + paragraphStartPos
                    this@LocalTtsReadAloudService.pageIndex = pageIndex

                    launch(Dispatchers.Main) {
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

        // If prefetch is actively synthesizing this exact paragraph, do not cancel it; join it to avoid duplicated synthesis
        val isPrefetchingTarget = (currentlyPrefetchingIndex == request.paragraphIndex && prefetchJob?.isActive == true)
        if (!isPrefetchingTarget) {
            clearPrefetch()
        }
        generationJob?.cancel()

        generationJob = lifecycleScope.launch {
            try {
                if (isPrefetchingTarget) {
                    AppLog.putDebug("Đang chờ prefetch hoàn thành cho đoạn ${request.paragraphIndex} thay vì hủy tính lại")
                    prefetchJob?.join()
                }
                val wav = synthesizeToWav(request)
                player.clearMediaItems()
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(wav))
                    .setMediaId(request.paragraphIndex.toString())
                    .build()
                player.setMediaItem(mediaItem)
                applySpeechRate()
                pendingProgress = readAloudNumber + 1
                player.prepare()
                upMediaMetadata(showContent = true)
                triggerPrefetchPipeline(request.paragraphIndex, engineReference)
            } catch (error: Throwable) {
                if (error is CancellationException) return@launch
                AppLog.put("Lỗi tổng hợp giọng đọc local\n${error.localizedMessage}", error, true)
                failPlayback(error.localizedMessage ?: "Không thể tổng hợp giọng đọc local")
            }
        }
    }

    private fun chapterOffsetForParagraph(paragraphIndex: Int): Int {
        if (paragraphIndex in paragraphOffsets.indices) {
            return paragraphOffsets[paragraphIndex]
        }
        var offset = 0
        val limit = paragraphIndex.coerceIn(0, contentList.size)
        for (i in 0 until limit) {
            offset += contentList[i].length + 1
        }
        return offset
    }

    private suspend fun advanceAndPlay(applyInterval: Boolean = true) {
        val engineReference = sessionTtsEngine ?: ReadAloud.ttsEngine.orEmpty()
        val nextReq = nextSpeechRequestAfter(nowSpeak, engineReference)
        if (nextReq != null) {
            nowSpeak = nextReq.paragraphIndex
            readAloudNumber = chapterOffsetForParagraph(nowSpeak)
            paragraphStartPos = 0
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
            Player.STATE_ENDED -> lifecycleScope.launch { advanceAndPlay(applyInterval = true) }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        val nextParagraph = mediaItem?.mediaId?.toIntOrNull() ?: return
        if (nextParagraph == nowSpeak && player.mediaItemCount == 1) return
        onParagraphTransition(nextParagraph)
    }

    private fun onParagraphTransition(nextParagraph: Int) {
        nowSpeak = nextParagraph
        readAloudNumber = chapterOffsetForParagraph(nextParagraph)
        paragraphStartPos = 0
        pendingProgress = readAloudNumber + 1
        publishPendingProgress()
        upMediaMetadata(showContent = true)

        if (player.mediaItemCount > 1 && player.currentMediaItemIndex > 0) {
            player.removeMediaItem(0)
        }

        val engineReference = sessionTtsEngine ?: ReadAloud.ttsEngine.orEmpty()
        triggerPrefetchPipeline(nextParagraph, engineReference)
    }

    private fun queueNextMediaItemIfReady(paragraphIndex: Int, wav: File) {
        if (pause) return
        if (ReadConfig.ttsParagraphInterval > 0) return
        val engineReference = sessionTtsEngine ?: ReadAloud.ttsEngine.orEmpty()
        val immediateNext = nextSpeechRequestAfter(nowSpeak, engineReference) ?: return
        if (immediateNext.paragraphIndex != paragraphIndex) return
        if (player.mediaItemCount >= 2) return

        val nextItem = MediaItem.Builder()
            .setUri(Uri.fromFile(wav))
            .setMediaId(paragraphIndex.toString())
            .build()
        player.addMediaItem(nextItem)
        AppLog.putDebug("Local TTS đã xếp hàng đoạn tiếp theo: paragraph=$paragraphIndex")
    }

    private fun triggerPrefetchPipeline(fromIndex: Int, engineReference: String) {
        prefetchJob?.cancel()
        currentlyPrefetchingIndex = null
        prefetchJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                delay(200) // Give audio track startup CPU priority (ERR_0032)
                var currentParagraph = fromIndex
                for (step in 1..PREFETCH_LOOKAHEAD_COUNT) {
                    ensureActive()
                    val nextRequest = nextSpeechRequestAfter(currentParagraph, engineReference) ?: break
                    currentParagraph = nextRequest.paragraphIndex
                    currentlyPrefetchingIndex = currentParagraph

                    val cached = LocalTtsSynthesis.getCachedWav(
                        context = this@LocalTtsReadAloudService,
                        engineReference = nextRequest.engineReference,
                        text = nextRequest.text,
                    )
                    val wav = if (cached != null) {
                        cached
                    } else {
                        ensureActive()
                        synthesizeToWav(nextRequest)
                    }

                    withContext(Dispatchers.Main) {
                        queueNextMediaItemIfReady(nextRequest.paragraphIndex, wav)
                    }
                    delay(100)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                AppLog.putDebug("Local TTS prefetch pipeline: ${error.localizedMessage}")
            } finally {
                currentlyPrefetchingIndex = null
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        AppLog.put("Lỗi phát audio local: ${error.localizedMessage}", error)
        lifecycleScope.launch {
            var recovered = false
            for (attempt in 1..3) {
                delay(1000L * attempt)
                if (pause) return@launch
                runCatching {
                    player.prepare()
                    player.play()
                }.onSuccess {
                    if (player.playerError == null) {
                        recovered = true
                        return@launch
                    }
                }
            }
            if (!recovered && !pause) {
                advanceAndPlay(applyInterval = false)
            }
        }
    }

    override fun playStop() {
        generationJob?.cancel()
        clearPrefetch()
        player.stop()
        player.clearMediaItems()
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        generationJob?.cancel()
        clearPrefetch()
        player.pause()
    }

    private fun skipToPrevParagraph() {
        if (contentList.isEmpty()) return
        playStop()
        var prev = nowSpeak - 1
        while (prev >= 0 && !isSpeakableText(contentList[prev])) {
            prev--
        }
        if (prev >= 0) {
            nowSpeak = prev
            paragraphStartPos = 0
            readAloudNumber = chapterOffsetForParagraph(prev)
            pendingProgress = readAloudNumber + 1
            publishPendingProgress()
            upMediaMetadata(showContent = true)
            play()
        } else {
            prevChapter()
        }
    }

    private fun skipToNextParagraph() {
        if (contentList.isEmpty()) return
        playStop()
        var next = nowSpeak + 1
        while (next < contentList.size && !isSpeakableText(contentList[next])) {
            next++
        }
        if (next < contentList.size) {
            nowSpeak = next
            paragraphStartPos = 0
            readAloudNumber = chapterOffsetForParagraph(next)
            pendingProgress = readAloudNumber + 1
            publishPendingProgress()
            upMediaMetadata(showContent = true)
            play()
        } else {
            nextChapter()
        }
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
        player.stop()
        sendBroadcast(android.content.Intent(IntentAction.localTtsNext).setPackage(packageName))
    }

    override fun prevChapter() {
        player.stop()
        sendBroadcast(android.content.Intent(IntentAction.localTtsPrev).setPackage(packageName))
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

    private suspend fun synthesizeToWav(request: LocalTtsRequest): File =
        withContext(Dispatchers.Default) {
            LocalTtsSynthesis.synthesizeToWav(
                context = this@LocalTtsReadAloudService,
                engineReference = request.engineReference,
                text = request.text,
            )
        }

    private fun clearPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        currentlyPrefetchingIndex = null
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
