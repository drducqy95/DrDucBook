package io.legado.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateChapterPromptPolicyTest {

    @Test
    fun retransPrompt_isIncludedForExplicitRetranslation() {
        assertTrue(
            shouldIncludeRetranslatePrompt(
                isExplicitRetranslation = true,
                hasRetryReason = false,
            )
        )
    }

    @Test
    fun retransPrompt_isIncludedForAutomaticRetry() {
        assertTrue(
            shouldIncludeRetranslatePrompt(
                isExplicitRetranslation = false,
                hasRetryReason = true,
            )
        )
    }

    @Test
    fun retransPrompt_isExcludedForFirstNormalAttempt() {
        assertFalse(
            shouldIncludeRetranslatePrompt(
                isExplicitRetranslation = false,
                hasRetryReason = false,
            )
        )
    }
}
