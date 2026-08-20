package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.Status
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.postEvent
import splitties.init.appCtx

/** Bridges chapter navigation from the isolated local TTS process to the reader process. */
class LocalTtsBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            IntentAction.localTtsState -> {
                val state = intent.getIntExtra("state", Status.STOP)
                val sessionId = intent.getLongExtra("localTtsSessionId", 0L)
                ReadAloud.updateLocalSessionState(state, sessionId)
                postEvent(EventBus.ALOUD_STATE, state)
            }
            IntentAction.localTtsProgress -> {
                val sessionId = intent.getLongExtra("localTtsSessionId", 0L)
                ReadAloud.updateLocalSessionState(Status.PLAY, sessionId)
                postEvent(EventBus.TTS_PROGRESS, intent.getIntExtra("chapterStart", 0))
            }
            IntentAction.localTtsNext -> {
                if (ReadBook.moveToNextChapter(upContent = true)) {
                    ReadAloud.play(appCtx, play = true, pageIndex = 0, startPos = 0)
                } else {
                    ReadAloud.stop(appCtx)
                }
            }

            IntentAction.localTtsPrev -> {
                if (ReadBook.moveToPrevChapter(upContent = true)) {
                    ReadAloud.play(appCtx, play = true, pageIndex = 0, startPos = 0)
                }
            }
        }
    }
}
