package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.MangaOcrGateway
import io.legado.app.domain.gateway.MangaOcrResult
import io.legado.app.domain.gateway.MangaTranslationCacheGateway
import io.legado.app.domain.manga.MangaOcrScript
import io.legado.app.domain.manga.MangaOverlayPage
import io.legado.app.domain.manga.MangaRect
import io.legado.app.domain.manga.MangaTextBlock
import io.legado.app.domain.manga.MangaTextOrientation
import io.legado.app.domain.manga.MangaTranslationRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslateMangaPageUseCaseTest {

    @Test
    fun pipelineOrdersTranslatesAndCachesWithoutMutatingSourceBytes() = runBlocking {
        val source = "image-data".toByteArray()
        val original = source.copyOf()
        val ocr = RecordingOcrGateway(
            listOf(block("second", "\u540e", 160), block("first", "\u5148", 10))
        )
        val cache = MemoryMangaCache()
        val useCase = TranslateMangaPageUseCase(
            ocr,
            FakeMangaTranslator { text, _, _, _ -> "vi:$text" },
            cache,
        )
        val request = request(source)

        val first = useCase.execute(request)
        val second = useCase.execute(request)

        assertEquals(original.toList(), source.toList())
        assertEquals(1, ocr.calls)
        assertEquals(first, second)
        assertEquals(listOf("first", "second"), first.blocks.map { it.id })
        assertTrue(first.translations.all { it.translatedText.startsWith("vi:") })
    }

    @Test
    fun lowConfidenceOcrBlockRemainsEditableInResult() = runBlocking {
        val low = block("low", "\u6a21\u7cca", 10).copy(confidence = 0.2f)
        val useCase = TranslateMangaPageUseCase(
            RecordingOcrGateway(listOf(low)),
            FakeMangaTranslator { _, _, _, _ -> "mo ho" },
            MemoryMangaCache(),
        )

        val result = useCase.execute(request("image".toByteArray()))

        assertEquals(0.2f, result.blocks.single().confidence)
        assertEquals("mo ho", result.translations.single().translatedText)
    }

    @Test
    fun failedRegionKeepsSuccessfulCheckpointAndRetriesOnlyMissingRegion() = runBlocking {
        val ocr = RecordingOcrGateway(
            listOf(block("first", "\u5148", 10), block("second", "\u540e", 160))
        )
        val translator = CheckpointTranslator().apply { failText = "\u540e" }
        val cache = MemoryMangaCache()
        val partialSizes = mutableListOf<Int>()
        val useCase = TranslateMangaPageUseCase(ocr, translator, cache)

        val failed = runCatching {
            useCase.execute(request("checkpoint".toByteArray())) {
                partialSizes += it.translations.size
            }
        }

        assertTrue(failed.isFailure)
        assertEquals(1, cache.onlyPage().translations.size)
        assertTrue(1 in partialSizes)

        translator.failText = null
        val completed = useCase.execute(request("checkpoint".toByteArray()))

        assertEquals(2, completed.translations.size)
        assertEquals(1, translator.attempts.count { it == "\u5148" })
        assertEquals(2, translator.attempts.count { it == "\u540e" })
        assertEquals(1, ocr.calls)
    }

    @Test
    fun dependencyChangeRetranslatesOnlyAffectedRegion() = runBlocking {
        val ocr = RecordingOcrGateway(
            listOf(block("first", "\u5148", 10), block("second", "\u540e", 160))
        )
        val translator = CheckpointTranslator()
        val useCase = TranslateMangaPageUseCase(ocr, translator, MemoryMangaCache())
        val request = request("dictionary".toByteArray())

        useCase.execute(request)
        translator.dependencies["\u5148"] = "changed"
        val updated = useCase.execute(request)

        assertFalse(updated.translations.isEmpty())
        assertEquals(2, translator.attempts.count { it == "\u5148" })
        assertEquals(1, translator.attempts.count { it == "\u540e" })
        assertEquals(1, ocr.calls)
    }

    private fun request(bytes: ByteArray) = MangaTranslationRequest(
        imageId = "page-1",
        imageBytes = bytes,
        script = MangaOcrScript.CHINESE,
        verticalReading = false,
        provider = "quick_translator",
        targetLanguage = "vi",
        ocrVersion = "mlkit-16",
        providerModelPromptRevision = "qt-test",
    )

    private fun block(id: String, text: String, left: Int) = MangaTextBlock(
        id = id,
        text = text,
        polygon = emptyList(),
        boundingBox = MangaRect(left, 10, left + 50, 50),
        confidence = 0.9f,
        orientation = MangaTextOrientation.HORIZONTAL,
        script = MangaOcrScript.CHINESE,
    )

    private class RecordingOcrGateway(
        private val blocks: List<MangaTextBlock>,
    ) : MangaOcrGateway {
        var calls = 0
        override suspend fun recognize(imageBytes: ByteArray, script: MangaOcrScript): MangaOcrResult {
            calls++
            return MangaOcrResult(300, 500, blocks)
        }
    }

    private class MemoryMangaCache : MangaTranslationCacheGateway {
        private val pages = mutableMapOf<String, MangaOverlayPage>()
        override suspend fun read(cacheKey: String): MangaOverlayPage? = pages[cacheKey]
        override suspend fun write(page: MangaOverlayPage) { pages[page.cacheKey] = page }
        override suspend fun delete(cacheKey: String) { pages.remove(cacheKey) }
        override suspend fun clear() { pages.clear() }
        fun onlyPage(): MangaOverlayPage = pages.values.single()
    }

    private class CheckpointTranslator :
        io.legado.app.domain.gateway.MangaTextTranslationGateway {
        val attempts = mutableListOf<String>()
        val dependencies = mutableMapOf<String, String>()
        var failText: String? = null

        override suspend fun translate(
            text: String,
            provider: String,
            targetLanguage: String,
            book: io.legado.app.data.entities.Book?,
        ): String {
            attempts += text
            if (text == failText) error("planned region failure")
            return "vi:$text:${attempts.count { it == text }}"
        }

        override suspend fun dependencyHash(
            text: String,
            provider: String,
            book: io.legado.app.data.entities.Book?,
        ): String = dependencies[text] ?: "stable:$text"
    }
}

private fun interface FakeMangaTranslator :
    io.legado.app.domain.gateway.MangaTextTranslationGateway {
    override suspend fun translate(
        text: String,
        provider: String,
        targetLanguage: String,
        book: io.legado.app.data.entities.Book?,
    ): String
}
