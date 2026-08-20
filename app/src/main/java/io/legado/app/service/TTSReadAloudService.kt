@file:Suppress("DEPRECATION")
package io.legado.app.service

import android.app.PendingIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.drducbook.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.MediaHelp
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/**
 * 本地朗读
 */
class TTSReadAloudService : BaseReadAloudService(), TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false
    private val ttsUtteranceListener = TTSUtteranceListener()
    private var speakJob: Coroutine<*>? = null
    private var utteranceStartPos = 0
    private var utteranceStartReadAloudNumber = 0
    private var utteranceTextMapping = SpeechTextMapping("", IntArray(0))
    private var needParagraphInterval = false // 是否需要进行段落间隔延迟
    private val TAG = "TTSReadAloudService"

    override fun onCreate() {
        super.onCreate()
        initTts()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTTS()
    }

    @Synchronized
    private fun initTts() {
        ttsInitFinish = false
        val engine = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
        LogUtils.d(TAG, "initTts engine:$engine")
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this, this)
        } else {
            TextToSpeech(this, this, engine)
        }
        upSpeechRate()
    }

    @Synchronized
    fun clearTTS() {
        textToSpeech?.runCatching {
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitFinish = false
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let {
                it.setOnUtteranceProgressListener(ttsUtteranceListener)
                ttsInitFinish = true
                play()
            }
        } else {
            toastOnUi(R.string.tts_init_failed)
        }
    }

    @Synchronized
    override fun play() {
        if (!ttsInitFinish) return
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
            return
        }
        super.play()
        MediaHelp.playSilentSound(this@TTSReadAloudService)
        
        // 捕获本次是否需要进行段落延迟，并将标志位复位（防多次触发）
        val isDelay = needParagraphInterval
        needParagraphInterval = false
        
        speakJob?.cancel()
        speakJob = execute {
            val interval = ReadConfig.ttsParagraphInterval.toLong()
            AppLog.putDebug("TTS_PLAY: nowSpeak=$nowSpeak, isDelay=$isDelay, interval=$interval")

            // Do not schedule a paragraph delay for entries that contain only
            // whitespace, control characters, or punctuation. These entries
            // are kept in contentList so the reader position mapping remains
            // intact, but they must be skipped before handing work to the TTS
            // engine.
            if (!skipNonSpeechParagraphs()) {
                nextChapter()
                return@execute
            }
            
            if (interval > 0 && isDelay) {
                AppLog.putDebug("TTS开始延迟: $interval 毫秒")
                delay(interval)
                AppLog.putDebug("TTS延迟结束，准备播放")
            }
            // Always enqueue exactly one utterance. Queueing the whole chapter
            // with QUEUE_ADD makes onRangeStart run before nowSpeak is advanced,
            // which causes the highlight to lag behind the spoken sentence.
            ensureActive()
            val tts = textToSpeech ?: throw NoStackTraceException("tts is null")
            var sourceText = contentList[nowSpeak]
            var sourceStartPos = 0
            if (paragraphStartPos > 0) {
                sourceStartPos = paragraphStartPos.coerceAtMost(sourceText.length)
                sourceText = sourceText.substring(sourceStartPos)
            }
            val mapping = compactSpeechTextWithOffsets(sourceText, sourceStartPos)
            val text = mapping.text
            if (!isSpeakableText(text)) {
                AppLog.putDebug("TTS段落全标点跳过: nowSpeak=$nowSpeak")
                if (skipNonSpeechParagraphs()) {
                    ttsUtteranceListener.onDone(AppConst.APP_TAG + nowSpeak)
                } else {
                    nextChapter()
                }
                return@execute
            }
            utteranceTextMapping = mapping
            AppLog.putDebug("TTS开始Speak: $text")
            val result = tts.runCatching {
                speak(text, TextToSpeech.QUEUE_FLUSH, null, AppConst.APP_TAG + nowSpeak)
            }.getOrElse {
                AppLog.put("tts出错\n${it.localizedMessage}", it, true)
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                AppLog.put("tts出错 尝试重新初始化")
                clearTTS()
                initTts()
                return@execute
            }
            LogUtils.d(TAG, "朗读内容添加完成")
        }.onError {
            AppLog.putDebug("TTS协程异常: ${it.localizedMessage}")
        }
    }

    override fun playStop() {
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * Android TTS engines may turn repeated newlines, NBSPs, or zero-width
     * characters into very long pauses. Keep paragraph tracking unchanged, but
     * always send a compact speech string to the engine.
     */
    private fun normalizeSpeechText(value: String): String =
        compactSpeechTextWithOffsets(value).text

    private fun isSpeakableText(value: String): Boolean {
        val normalized = compactSpeechTextWithOffsets(value).text
        return normalized.isNotEmpty() && !normalized.matches(AppPattern.notReadAloudRegex)
    }

    /**
     * Advance over non-speech entries without applying the user-configured
     * paragraph interval. The original entries are retained for progress
     * accounting, so skipping still advances the chapter position correctly.
     */
    private fun skipNonSpeechParagraphs(): Boolean {
        while (nowSpeak in contentList.indices && !isSpeakableText(contentList[nowSpeak])) {
            readAloudNumber = nextParagraphPosition(
                currentPosition = readAloudNumber,
                paragraphLength = contentList[nowSpeak].length,
                paragraphStartPosition = paragraphStartPos,
            )
            paragraphStartPos = 0
            nowSpeak++
        }
        return nowSpeak in contentList.indices
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        if (ReadConfig.ttsFollowSys) {
            if (reset) {
                clearTTS()
                initTts()
            }
        } else {
            val speechRate = (ReadConfig.ttsSpeechRate + 5) / 10f
            textToSpeech?.setSpeechRate(speechRate)
            if (reset && !pause) {
                play()
            }
        }
    }

    /**
     * 暂停朗读
     */
    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        speakJob?.cancel()
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 恢复朗读
     */
    override fun resumeReadAloud() {
        super.resumeReadAloud()
        play()
    }

    /**
     * 朗读监听
     */
    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        private val TAG = "TTSUtteranceListener"

        override fun onStart(s: String) {
            LogUtils.d(TAG, "onStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$s")
            utteranceStartPos = paragraphStartPos
            utteranceStartReadAloudNumber = readAloudNumber
            textChapter?.let {
                if (pageIndex + 1 < it.pageSize
                    && readAloudNumber + 1 > it.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                    ReadBook.moveToNextPage()
                }
                upTtsProgress(readAloudNumber + 1)
                upMediaMetadata(showContent = true)
            }
        }

        override fun onDone(s: String) {
            LogUtils.d(TAG, "onDone utteranceId:$s")
            val hasNext = nextParagraph()
            if (hasNext && !pause) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                play()
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            super.onRangeStart(utteranceId, start, end, frame)
            val originalStart = utteranceTextMapping.originalOffsetFor(
                normalizedOffset = start,
                fallback = utteranceStartPos + start,
            )
            paragraphStartPos = originalStart
            readAloudNumber = currentRangePosition(
                utteranceStartReadAloudNumber,
                originalStart - utteranceStartPos,
            )
            updateReadAloudProgressSnapshot(readAloudNumber + 1)
            val msg =
                "onRangeStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId start:$start end:$end frame:$frame"
            LogUtils.d(TAG, msg)
            if (moveToReadAloudPage(readAloudNumber)) {
                upTtsProgress(readAloudNumber + 1)
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            LogUtils.d(
                TAG,
                "onError nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId errorCode:$errorCode"
            )
            val hasNext = nextParagraph()
            if (hasNext && !pause) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                play()
            }
        }

        private fun nextParagraph(): Boolean {
            if (nowSpeak !in contentList.indices) return false
            do {
                readAloudNumber = nextParagraphPosition(
                    currentPosition = readAloudNumber,
                    paragraphLength = contentList[nowSpeak].length,
                    paragraphStartPosition = paragraphStartPos,
                )
                paragraphStartPos = 0
                nowSpeak++
            } while (nowSpeak < contentList.size && !isSpeakableText(contentList[nowSpeak]))
            if (nowSpeak >= contentList.size) {
                nextChapter()
                return false
            }
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onError(s: String) {
            LogUtils.d(TAG, "onError nowSpeak:$nowSpeak pageIndex:$pageIndex s:$s")
            val hasNext = nextParagraph()
            if (hasNext && !pause) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                play()
            }
        }

    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<TTSReadAloudService>(actionStr)
    }

}

internal fun nextParagraphPosition(
    currentPosition: Int,
    paragraphLength: Int,
    paragraphStartPosition: Int,
): Int = currentPosition + paragraphLength + 1 - paragraphStartPosition

internal fun currentRangePosition(
    utteranceStartPosition: Int,
    rangeStart: Int,
): Int = utteranceStartPosition + rangeStart
