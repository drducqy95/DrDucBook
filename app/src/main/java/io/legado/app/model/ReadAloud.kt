package io.legado.app.model

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.HttpReadAloudService
import io.legado.app.service.LocalTtsReadAloudService
import io.legado.app.service.TTSReadAloudService
import io.legado.app.model.tts.parseLocalTtsEngine
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.File

object ReadAloud {
    private var aloudClass: Class<*> = getReadAloudClass()
    val ttsEngine get() = ReadBook.book?.getTtsEngine() ?: ReadConfig.ttsEngine
    var httpTTS: HttpTTS? = null

    /** BaseReadAloudService.isRun is process-local; keep a marker for the local TTS process. */
    @Volatile
    private var localSessionStarted = false

    @Volatile
    private var localSessionPaused = false

    @Volatile
    private var localSessionId = 0L

    val isLocalSessionRunning: Boolean
        get() = localSessionStarted

    val isLocalSessionPaused: Boolean
        get() = localSessionPaused

    fun updateLocalSessionState(state: Int, sessionId: Long = 0L) {
        if (sessionId != 0L && localSessionId != 0L && sessionId != localSessionId) return
        localSessionStarted = state != Status.STOP
        localSessionPaused = state == Status.PAUSE
    }

    private fun getReadAloudClass(): Class<*> {
        val ttsEngine = ttsEngine
        if (ttsEngine.isNullOrBlank()) {
            return TTSReadAloudService::class.java
        }
        if (parseLocalTtsEngine(ttsEngine) != null) {
            return LocalTtsReadAloudService::class.java
        }
        if (StringUtils.isNumeric(ttsEngine)) {
            httpTTS = appDb.httpTTSDao.get(ttsEngine.toLong())
            if (httpTTS != null) {
                return HttpReadAloudService::class.java
            }
        }
        return TTSReadAloudService::class.java
    }

    fun upReadAloudClass() {
        stop(appCtx)
        aloudClass = getReadAloudClass()
    }

    fun play(
        context: Context,
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0
    ) {
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.play
        intent.putExtra("play", play)
        intent.putExtra("pageIndex", pageIndex)
        intent.putExtra("startPos", startPos)
        if (aloudClass == LocalTtsReadAloudService::class.java) {
            localSessionId = System.nanoTime().takeIf { it != 0L } ?: 1L
            val chapterText = ReadBook.curTextChapter
                ?.getNeedReadAloud(0, ReadConfig.readAloudByPage, 0)
                .orEmpty()
            if (chapterText.isBlank()) {
                context.toastOnUi("Chương hiện tại chưa sẵn sàng để đọc")
                return
            }
            val sessionFile = File(appCtx.cacheDir, "local_tts_read_aloud_session.txt")
            runCatching {
                sessionFile.parentFile?.mkdirs()
                sessionFile.writeText(chapterText, Charsets.UTF_8)
            }.onFailure { error ->
                AppLog.put("Không thể chuẩn bị nội dung cho TTS local\n${error.localizedMessage}", error)
                context.toastOnUi("Không thể chuẩn bị nội dung đọc")
                return
            }
            intent.putExtra("localTtsSessionFile", sessionFile.absolutePath)
            intent.putExtra("localTtsEngine", ttsEngine.orEmpty())
            intent.putExtra("localTtsSessionId", localSessionId)
            localSessionStarted = true
            localSessionPaused = !play
        }
        LogUtils.d("ReadAloud", intent.toString())
        try {
            context.startForegroundServiceCompat(intent)
        } catch (e: Exception) {
            val msg = "启动朗读服务出错\n${e.localizedMessage}"
            AppLog.put(msg, e)
            context.toastOnUi(msg)
        }
    }

    fun playByEventBus(
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0
    ) {
        val bundle = Bundle().apply {
            putBoolean("play", play)
            putInt("pageIndex", pageIndex)
            putInt("startPos", startPos)
        }
        postEvent(EventBus.READ_ALOUD_PLAY, bundle)
    }

    fun pause(context: Context) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            localSessionPaused = true
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.pause
            if (aloudClass == LocalTtsReadAloudService::class.java) {
                intent.putExtra("localTtsSessionId", localSessionId)
            }
            context.startForegroundServiceCompat(intent)
        }
    }

    fun resume(context: Context) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            localSessionPaused = false
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.resume
            if (aloudClass == LocalTtsReadAloudService::class.java) {
                intent.putExtra("localTtsSessionId", localSessionId)
            }
            context.startForegroundServiceCompat(intent)
        }
    }

    fun stop(context: Context) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.stop
            if (aloudClass == LocalTtsReadAloudService::class.java) {
                intent.putExtra("localTtsSessionId", localSessionId)
            }
            context.startForegroundServiceCompat(intent)
            localSessionStarted = false
            localSessionPaused = false
        }
    }

    fun prevParagraph(context: Context) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.prevParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun nextParagraph(context: Context) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.nextParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun upTtsSpeechRate(context: Context) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.upTtsSpeechRate
            context.startForegroundServiceCompat(intent)
        }
    }

    fun syncLayout(context: Context = appCtx) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.syncReadAloudLayout
            context.startForegroundServiceCompat(intent)
        }
    }

    fun setTimer(context: Context, minute: Int) {
        if (BaseReadAloudService.isRun || localSessionStarted) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", PlaybackTimer.normalize(minute))
            context.startForegroundServiceCompat(intent)
        }
    }

}
