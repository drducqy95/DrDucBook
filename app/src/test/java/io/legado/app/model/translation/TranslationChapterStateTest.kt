package io.legado.app.model.translation

import io.legado.app.domain.model.AiFailureKind
import io.legado.app.domain.model.AiProviderFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationChapterStateTest {

    @Test
    fun `failure keeps completed chunks and visible mixed content`() {
        val failure = AiProviderFailure(
            kind = AiFailureKind.EMPTY_OUTPUT,
            provider = "app-ai",
            model = "reasoning-model",
            attempt = 3,
        )
        val state = TranslationChapterState(
            key = TranslationChapterKey("book", 4, "app-ai", "vi"),
            status = TranslationChapterStatus.Translating,
            currentChunk = 7,
            totalChunks = 10,
            mixedContent = "Bảy phần đã dịch\n\nPhần gốc còn lại",
            translatedContent = "Bản dịch đã lưu",
        )

        val failed = state.failPreservingProgress(
            errorMessage = "Provider returned empty content",
            failure = failure,
            logs = listOf(TranslationLogEntry(TranslationLogType.FAILED)),
            updatedAt = 42L,
        )

        assertEquals(TranslationChapterStatus.Failed, failed.status)
        assertEquals(7, failed.currentChunk)
        assertEquals(10, failed.totalChunks)
        assertEquals(state.mixedContent, failed.mixedContent)
        assertEquals(state.translatedContent, failed.translatedContent)
        assertEquals(failure, failed.failure)
        assertEquals(42L, failed.updatedAt)
    }

    @Test
    fun `translation log keeps every entry beyond the previous 200 item limit`() {
        val entries = (1..250).map { index ->
            TranslationLogEntry(
                type = TranslationLogType.PIPELINE_STAGE,
                detail = "stage-$index",
            )
        }

        val result = entries.fold(emptyList<TranslationLogEntry>()) { logs, entry ->
            appendTranslationLog(logs, entry)
        }

        assertEquals(250, result.size)
        assertTrue(result.first().detail == "stage-1")
        assertTrue(result.last().detail == "stage-250")
    }
}
