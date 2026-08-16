package io.legado.app.help.media

import android.media.AudioManager

object AudioFocusResumePolicy {

    fun shouldResumeWhenFocusReturns(
        focusChange: Int,
        wasPlaying: Boolean,
    ): Boolean = wasPlaying && focusChange in setOf(
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    )
}
