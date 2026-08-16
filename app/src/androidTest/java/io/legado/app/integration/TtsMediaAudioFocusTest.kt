package io.legado.app.integration

import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.media.AudioFocusResumePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TtsMediaAudioFocusTest {

    @Test
    fun activeTtsAndAudioResumeAfterMediaReleasesFocus() {
        assertTrue(
            AudioFocusResumePolicy.shouldResumeWhenFocusReturns(
                AudioManager.AUDIOFOCUS_LOSS,
                wasPlaying = true,
            )
        )
        assertTrue(
            AudioFocusResumePolicy.shouldResumeWhenFocusReturns(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                wasPlaying = true,
            )
        )
        assertFalse(
            AudioFocusResumePolicy.shouldResumeWhenFocusReturns(
                AudioManager.AUDIOFOCUS_LOSS,
                wasPlaying = false,
            )
        )
        assertFalse(
            AudioFocusResumePolicy.shouldResumeWhenFocusReturns(
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                wasPlaying = true,
            )
        )
    }
}
