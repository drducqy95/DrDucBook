package io.legado.app.domain.usecase

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Opt-in benchmark against downloaded chapters and the AI configuration of the installed app.
 * Run only by class name; it deliberately exercises real providers and writes translated caches.
 */
@RunWith(AndroidJUnit4::class)
class ChapterTranslationBenchmarkInstrumentedTest {

    @Test
    fun translateFiveDownloadedChapters() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val bookName = args.getString("bookName")
            ?: "小司机：从送醉酒老板回家开始"
        val chapterCount = args.getString("chapterCount")?.toIntOrNull()?.coerceIn(1, 20) ?: 5
        val provider = args.getString("provider")
            ?: TranslationConstants.PROVIDER_APP_AI
        val forceRetranslate = args.getString("forceRetranslate")?.toBooleanStrictOrNull() ?: true
        val targetContext = instrumentation.targetContext
        val useCase = GlobalContext.get().get<TranslateChapterUseCase>()
        val book = appDb.bookDao.getAll().firstOrNull { it.name == bookName }
        assertNotNull("Book is not present in the installed app: $bookName", book)
        book!!
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            .filterNot { it.isVolume }
            .take(chapterCount)
        assertEquals("Not enough downloaded chapters", chapterCount, chapters.size)

        val results = chapters.map { chapter ->
            val source = requireNotNull(BookHelp.getContent(book, chapter)) {
                "Chapter ${chapter.index} is not downloaded"
            }
            val startedAtWall = System.currentTimeMillis()
            val startedAt = SystemClock.elapsedRealtime()
            var providerStartedMs: Long? = null
            var firstPreviewMs: Long? = null
            var finalProgressChunks = 0
            var finalTotalChunks = 0
            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = forceRetranslate,
                provider = provider,
                targetLanguage = "vi",
                onProgress = { progress ->
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    if (!progress.mixedContent.isNullOrBlank() && firstPreviewMs == null) {
                        firstPreviewMs = elapsed
                    }
                    finalProgressChunks = progress.currentChunk
                    finalTotalChunks = progress.totalChunks
                },
                onTranslateStarted = {
                    providerStartedMs = SystemClock.elapsedRealtime() - startedAt
                },
            )
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val output = result.getOrNull().orEmpty()
            val attempts = appDb.aiRouterDao.observeRecentAttempts(500).first()
                .filter { it.createdAt >= startedAtWall }
                .sortedBy { it.id }
            BenchmarkChapter(
                index = chapter.index,
                title = chapter.title,
                sourceChars = source.length,
                outputChars = output.length,
                sourceParagraphs = paragraphCount(source),
                outputParagraphs = paragraphCount(output),
                exactSeparatorLayout = separatorLayout(source) == separatorLayout(output),
                providerStartedMs = providerStartedMs,
                firstPreviewMs = firstPreviewMs,
                routerFirstEventMs = attempts.mapNotNull { it.firstEventMs }.minOrNull(),
                elapsedMs = elapsedMs,
                chunksCompleted = finalProgressChunks,
                chunksTotal = finalTotalChunks,
                chineseCharsRemaining = output.count(::isCjk),
                suspiciousEnvelope = output.contains("translated_text", ignoreCase = true) ||
                    output.trimStart().startsWith("```") || output.trimStart().startsWith("{"),
                success = result.isSuccess,
                error = result.exceptionOrNull()?.stackTraceToString(),
                attempts = attempts.map {
                    BenchmarkAttempt(
                        provider = it.providerName,
                        model = it.modelName,
                        credential = it.credentialLabel,
                        success = it.success,
                        failureKind = it.failureKind,
                        firstEventMs = it.firstEventMs,
                        latencyMs = it.latencyMs,
                    )
                },
                output = output,
            )
        }
        val report = BenchmarkReport(
            bookName = book.name,
            bookUrl = book.bookUrl,
            provider = provider,
            forceRetranslate = forceRetranslate,
            createdAt = System.currentTimeMillis(),
            chapters = results,
        )
        val reportDir = requireNotNull(targetContext.getExternalFilesDir("test-reports"))
        val reportFile = File(reportDir, "translation-5-chapters-${report.createdAt}.json")
        reportFile.writeText(GSON.toJson(report))
        instrumentation.sendStatus(
            0,
            android.os.Bundle().apply { putString("benchmarkReport", reportFile.absolutePath) },
        )
        assertEquals(results.joinToString("\n") { it.error.orEmpty() }, chapterCount, results.count { it.success })
    }

    private fun paragraphCount(text: String): Int = ContentChunker.chunk(text, 10_000)
        .flatMap { it.paragraphIndices }
        .distinct()
        .size

    private fun separatorLayout(text: String): List<String> = PARAGRAPH_BREAK
        .findAll(text)
        .map { it.value }
        .toList()

    private fun isCjk(char: Char): Boolean = char.code in 0x3400..0x9FFF

    private data class BenchmarkReport(
        val bookName: String,
        val bookUrl: String,
        val provider: String,
        val forceRetranslate: Boolean,
        val createdAt: Long,
        val chapters: List<BenchmarkChapter>,
    )

    private data class BenchmarkChapter(
        val index: Int,
        val title: String,
        val sourceChars: Int,
        val outputChars: Int,
        val sourceParagraphs: Int,
        val outputParagraphs: Int,
        val exactSeparatorLayout: Boolean,
        val providerStartedMs: Long?,
        val firstPreviewMs: Long?,
        val routerFirstEventMs: Long?,
        val elapsedMs: Long,
        val chunksCompleted: Int,
        val chunksTotal: Int,
        val chineseCharsRemaining: Int,
        val suspiciousEnvelope: Boolean,
        val success: Boolean,
        val error: String?,
        val attempts: List<BenchmarkAttempt>,
        val output: String,
    )

    private data class BenchmarkAttempt(
        val provider: String,
        val model: String,
        val credential: String?,
        val success: Boolean,
        val failureKind: String?,
        val firstEventMs: Long?,
        val latencyMs: Long,
    )

    private companion object {
        val PARAGRAPH_BREAK = Regex("[\\t ]*(?:\\r?\\n[\\t ]*)+")
    }
}
