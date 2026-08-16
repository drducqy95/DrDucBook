package io.legado.app.data.repository

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.domain.model.AiTranslationChunkPlanner
import io.legado.app.domain.model.AiTranslationStreamParser
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.LocalAiTranslationPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalAiNativeRuntimeInstrumentedTest {

    @Test
    fun packagedRuntimeLoadsCpuBackendsAndExposesJniOnCurrentAbi() {
        assertTrue(LocalAiNativeBridge.loadErrorMessage, LocalAiNativeBridge.isAvailable)

        LocalAiNativeBridge.cancel()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missingModel = File(context.cacheDir, "missing-local-ai-model.gguf")
        check(!missingModel.exists())

        try {
            LocalAiNativeBridge.load(
                nativeLibDir = context.applicationInfo.nativeLibraryDir,
                modelPath = missingModel.absolutePath,
                contextWindow = 1_024,
                threads = 1,
                batchThreads = 1,
                batchSize = 32,
                microBatchSize = 16,
                useMmap = true,
                useMlock = false,
                gpuLayers = 0,
            )
            fail("Loading a missing GGUF model should fail after native backend initialization")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("GGUF", ignoreCase = true))
        }
    }

    @Test
    fun hyMt2Stride16GeneratesVietnameseWithPinnedRecommendedSampling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val modelPath = arguments.getString("modelPath").orEmpty()
        val temperature = arguments.getString("temperature")?.toFloatOrNull() ?: 0.7f
        val topP = arguments.getString("topP")?.toFloatOrNull() ?: 0.6f
        val topK = arguments.getString("topK")?.toIntOrNull() ?: 20
        assumeTrue("Pass -e modelPath <GGUF path> to run the real-model test", modelPath.isNotBlank())
        assertTrue("GGUF test model is not readable: $modelPath", File(modelPath).canRead())
        assertTrue(LocalAiNativeBridge.loadErrorMessage, LocalAiNativeBridge.isAvailable)

        val context = instrumentation.targetContext
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val loadStartedAt = SystemClock.elapsedRealtime()
        val handle = LocalAiNativeBridge.load(
            nativeLibDir = context.applicationInfo.nativeLibraryDir,
            modelPath = modelPath,
            contextWindow = 2_048,
            threads = threads,
            batchThreads = threads,
            batchSize = 128,
            microBatchSize = 64,
            useMmap = true,
            useMlock = false,
            gpuLayers = 0,
        )
        val loadMillis = SystemClock.elapsedRealtime() - loadStartedAt
        assertTrue("Native model handle is invalid", handle != 0L)

        val source = "\u4ECA\u5929\u5929\u6C14\u771F\u597D\u3002"
        val output = StringBuilder()
        val generationStartedAt = SystemClock.elapsedRealtime()
        try {
            LocalAiNativeBridge.generate(
                handle = handle,
                roles = arrayOf("user"),
                contents = arrayOf(
                    "Translate the following text into Vietnamese. " +
                        "Only output the translated result without explanation:\n\n" +
                        source
                ),
                maxOutputTokens = 64,
                temperature = temperature,
                topP = topP,
                topK = topK,
                repetitionPenalty = 1.05f,
                callback = object : LocalAiNativeBridge.Callback {
                    override fun onToken(text: String) {
                        output.append(text)
                    }

                    override fun isCancelled(): Boolean = false
                },
            )
        } finally {
            LocalAiNativeBridge.free(handle)
        }
        val generationMillis = SystemClock.elapsedRealtime() - generationStartedAt
        val translated = output.toString().trim()
        println("LOCAL_AI_LOAD_MS=$loadMillis")
        println("LOCAL_AI_GENERATE_MS=$generationMillis")
        println("LOCAL_AI_RESULT=$translated")
        assertTrue("Hy-MT2 returned an empty translation", translated.isNotBlank())
        assertTrue(
            "Hy-MT2 output is not a valid translation: $translated",
            translated != source &&
                Regex("(?iu)(hôm nay|thời tiết|trời|đẹp|tốt)").containsMatchIn(translated),
        )
    }

    @Test
    fun hyMt2TranslatesRealChapterChunkWithContextAndExactLayout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val modelPath = arguments.getString("modelPath").orEmpty()
        val chapterPath = arguments.getString("chapterPath").orEmpty()
        assumeTrue("Pass -e modelPath <GGUF path> to run the real-model test", modelPath.isNotBlank())
        assumeTrue("Pass -e chapterPath <downloaded chapter path>", chapterPath.isNotBlank())
        assertTrue("GGUF test model is not readable: $modelPath", File(modelPath).canRead())
        val chapterFile = File(chapterPath)
        assertTrue("Chapter fixture is not readable: $chapterPath", chapterFile.canRead())
        assertTrue(LocalAiNativeBridge.loadErrorMessage, LocalAiNativeBridge.isAvailable)

        val sourceChapter = chapterFile.readText(Charsets.UTF_8)
        val maxChunkChars = 128
        val chunks = ContentChunker.chunk(sourceChapter, maxCharsPerChunk = maxChunkChars)
        assertTrue("The chapter fixture is too short", chunks.size >= 3)
        val benchmarkChunks = chunks.take(2)
        assertTrue(
            "The benchmark must exercise multi-paragraph layout",
            benchmarkChunks.any { it.paragraphIndices.size > 1 },
        )

        val contextWindow = 4_096
        val deviceContext = instrumentation.targetContext
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val loadStartedAt = SystemClock.elapsedRealtime()
        val handle = LocalAiNativeBridge.load(
            nativeLibDir = deviceContext.applicationInfo.nativeLibraryDir,
            modelPath = modelPath,
            contextWindow = contextWindow,
            threads = threads,
            batchThreads = threads,
            batchSize = 256,
            microBatchSize = 64,
            useMmap = true,
            useMlock = false,
            gpuLayers = 0,
        )
        val loadMillis = SystemClock.elapsedRealtime() - loadStartedAt
        assertTrue("Native model handle is invalid", handle != 0L)

        data class ChunkBenchmark(
            val translated: String,
            val restored: String,
            val firstTokenMillis: Long,
            val generationMillis: Long,
        )

        val results = mutableListOf<ChunkBenchmark>()
        try {
            benchmarkChunks.forEach { chunk ->
                val context = AiTranslationChunkPlanner.contextFor(
                    chunks = chunks,
                    chunkIndex = chunk.index,
                    maxCharsPerChunk = maxChunkChars,
                    maxContextChars = 96,
                )
                assertTrue("The benchmark must exercise adjacent context", context.next.isNotBlank())
                val userPrompt = LocalAiTranslationPrompt.build(
                    text = chunk.content,
                    targetLanguage = "Tiếng Việt",
                    context = context,
                    dictionary = listOf(DictPair("叶长青", "Diệp Trường Thanh")),
                    configuredPrompt = "",
                )
                assertTrue("Raw adjacent context must not contaminate Hy-MT2 input", !userPrompt.contains(context.next))
                val output = StringBuilder()
                var firstTokenMillis = -1L
                val generationStartedAt = SystemClock.elapsedRealtime()
                LocalAiNativeBridge.generate(
                    handle = handle,
                    roles = arrayOf("user"),
                    contents = arrayOf(userPrompt),
                    maxOutputTokens = 256,
                    temperature = 0.7f,
                    topP = 0.6f,
                    topK = 20,
                    repetitionPenalty = 1.05f,
                    callback = object : LocalAiNativeBridge.Callback {
                        override fun onToken(text: String) {
                            if (text.isNotEmpty() && firstTokenMillis < 0L) {
                                firstTokenMillis = SystemClock.elapsedRealtime() - generationStartedAt
                            }
                            output.append(text)
                        }

                        override fun isCancelled(): Boolean = false
                    },
                )
                val generationMillis = SystemClock.elapsedRealtime() - generationStartedAt
                val rawOutput = output.toString()
                val translated = AiTranslationStreamParser.resultPreview(rawOutput)
                assertNotNull("Hy-MT2 did not return a translation result: $rawOutput", translated)
                val restored = ContentChunker.restoreLayout(chunk, translated.orEmpty())
                assertNotNull(
                    "The translated result changed paragraph cardinality or whitespace: $translated",
                    restored,
                )
                assertTrue("Hy-MT2 did not stream any output token", firstTokenMillis >= 0L)
                assertEquals(
                    chunk.leadingWhitespace,
                    restored.orEmpty().take(chunk.leadingWhitespace.length),
                )
                println("LOCAL_AI_REAL_CHAPTER_CHUNK=${chunk.index}")
                println("LOCAL_AI_REAL_CHAPTER_SOURCE_CHARS=${chunk.content.length}")
                println("LOCAL_AI_REAL_CHAPTER_CONTEXT_CHARS=${context.previous.length + context.next.length}")
                println("LOCAL_AI_REAL_CHAPTER_FIRST_TOKEN_MS=$firstTokenMillis")
                println("LOCAL_AI_REAL_CHAPTER_GENERATE_MS=$generationMillis")
                println("LOCAL_AI_REAL_CHAPTER_RAW=$rawOutput")
                println("LOCAL_AI_REAL_CHAPTER_RESTORED=$restored")
                results += ChunkBenchmark(
                    translated = translated.orEmpty(),
                    restored = restored.orEmpty(),
                    firstTokenMillis = firstTokenMillis,
                    generationMillis = generationMillis,
                )
            }
        } finally {
            LocalAiNativeBridge.free(handle)
        }
        println("LOCAL_AI_REAL_CHAPTER_LOAD_MS=$loadMillis")
        assertEquals(2, results.size)
        assertTrue(
            "Prompt KV cache did not reduce warm-chunk latency: ${results.map { it.firstTokenMillis }}",
            results[1].firstTokenMillis < results[0].firstTokenMillis,
        )
        val merged = results.joinToString(separator = "") { it.restored }
        val mergedLayout = ContentChunker.chunk(merged, Int.MAX_VALUE)
        assertEquals(1, mergedLayout.size)
        assertEquals(
            benchmarkChunks.sumOf { it.paragraphIndices.size },
            mergedLayout.single().paragraphIndices.size,
        )
        val normalized = results.joinToString("\n") { it.translated }.lowercase()
        val expectedTerms = listOf(
            Regex("hệ thống", RegexOption.IGNORE_CASE),
            Regex("diệp trường thanh", RegexOption.IGNORE_CASE),
            Regex("thịt (?:lợn|heo)", RegexOption.IGNORE_CASE),
            Regex("(?:nước sốt|tương|gia vị)", RegexOption.IGNORE_CASE),
        )
        assertTrue(
            "Translation lost the chapter's meaning or entity continuity: $normalized",
            expectedTerms.count { it.containsMatchIn(normalized) } >= 3,
        )
        val chineseChars = normalized.count { it in '\u4E00'..'\u9FFF' }
        assertTrue(
            "Too much untranslated Chinese remains in the result: $normalized",
            chineseChars <= 2,
        )
    }
}
