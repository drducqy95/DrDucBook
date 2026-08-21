package io.legado.app.domain.usecase

import android.app.Application
import io.legado.app.constant.BookType
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.entities.TranslationRevisionStatus
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.MlKitLanguageModel
import io.legado.app.domain.gateway.MlKitTranslationGateway
import io.legado.app.domain.gateway.NmtDecodeConfig
import io.legado.app.domain.gateway.NmtTranslationGateway
import io.legado.app.domain.gateway.NmtTranslationResult
import io.legado.app.domain.gateway.QuickDictionaryGateway
import io.legado.app.domain.gateway.QuickTranslationGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerateResponse
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProviderAuthType
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskPresetConfig
import io.legado.app.domain.model.AiTaskPresetDraft
import io.legado.app.domain.model.AiTaskRuntimeOptions
import io.legado.app.domain.model.BookDictionary
import io.legado.app.domain.model.DictPair
import io.legado.app.domain.model.QuickDictionaryCatalog
import io.legado.app.domain.model.QuickDictionaryCatalogEntry
import io.legado.app.domain.model.QuickDictionaryEntry
import io.legado.app.domain.model.QuickDictionaryImportProgress
import io.legado.app.domain.model.QuickDictionaryImportResult
import io.legado.app.domain.model.QuickDictionaryPack
import io.legado.app.domain.model.QuickDictionaryScope
import io.legado.app.domain.model.QuickDictionaryType
import io.legado.app.domain.model.QuickDictionaryUniverse
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.domain.model.TranslationRevision
import io.legado.app.domain.model.dictionaryAwareContentHash
import io.legado.app.help.book.BookHelp
import io.legado.app.ui.config.translation.TranslationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TranslateChapterAiRetryTest {

    @Before
    fun setUpAppCtx() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun truncatedAiChunkFallsBackByHalvingUntilSafeMinimum() {
        assertEquals(500, aiTranslationFallbackSplitMaxChars(1_000, splitDepth = 0))
        assertEquals(250, aiTranslationFallbackSplitMaxChars(500, splitDepth = 1))
        assertEquals(160, aiTranslationFallbackSplitMaxChars(250, splitDepth = 2))
        assertEquals(null, aiTranslationFallbackSplitMaxChars(160, splitDepth = 2))
        assertEquals(null, aiTranslationFallbackSplitMaxChars(1_000, splitDepth = 3))
    }

    @Test
    fun storyMemorySerializesAiChunksButLeavesStatelessAiConcurrencyAvailable() {
        assertEquals(
            1,
            resolveTranslationChunkConcurrency(
                provider = TranslationConstants.PROVIDER_APP_AI,
                hasLocalAiBudget = false,
                storyMemoryEnabled = true,
                aiConcurrentRequests = 4,
                standardConcurrentRequests = 3,
            )
        )
        assertEquals(
            4,
            resolveTranslationChunkConcurrency(
                provider = TranslationConstants.PROVIDER_APP_AI,
                hasLocalAiBudget = false,
                storyMemoryEnabled = false,
                aiConcurrentRequests = 4,
                standardConcurrentRequests = 3,
            )
        )
    }

    @Test
    fun vietnameseQualityCheckRejectsAnyCjkOutputFromMlKit() {
        assertTrue(
            hasUntranslatedCjkForVietnamese(
                source = "\u7b2c\u4e00\u3002",
                translated = "Doan \u4e00.",
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
        assertFalse(
            hasUntranslatedCjkForVietnamese(
                source = "\u7b2c\u4e00\u3002",
                translated = "Doan mot.",
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun vietnameseQualityCheckDetectsSupplementaryHanCodePoint() {
        val rareHan = String(Character.toChars(0x20000))

        assertTrue(
            hasUntranslatedCjkForVietnamese(
                source = "Nhan vat $rareHan",
                translated = "Nhan vat $rareHan",
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
    }

    @Test
    fun vietnameseQualityCheckDoesNotRejectOnlyCjkPunctuation() {
        val translated = "Nhan vat da den\u3002"

        assertFalse(
            hasUntranslatedCjkForVietnamese(
                source = "\u53f6\u957f\u751f\u6765\u4e86\u3002",
                translated = translated,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            )
        )
        assertEquals(
            "Nhan vat da den.",
            repairResidualCjkForVietnamese(
                text = translated,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                translateResidual = { it },
                phoneticResidual = { "" },
            )
        )
    }

    @Test
    fun mlKitResidualCjkIsRepairedWithoutReplacingTranslatedText() {
        val repaired = repairResidualCjkForVietnamese(
            text = "Nhan vat \u53f6\u957f\u751fsap den\u3002",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            translateResidual = { residual ->
                if (residual == "\u53f6\u957f\u751f") "Diep Truong Sinh" else residual
            },
            phoneticResidual = { "" },
        )

        assertEquals("Nhan vat Diep Truong Sinh sap den.", repaired)
        assertFalse(repaired.any(::isCjkForTest))
    }

    @Test
    fun mlKitResidualRepairKeepsTranslatedTextWhenOnlyPartOfRunIsCjk() {
        val repaired = repairResidualCjkForVietnamese(
            text = "Nhan vat \u53f6\u957f\u751f sap den.",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            translateResidual = { residual ->
                if (residual == "\u53f6\u957f\u751f") "Diep \u957f Sinh" else residual
            },
            phoneticResidual = { residual ->
                if (residual == "\u957f") "Truong" else ""
            },
        )

        assertEquals("Nhan vat Diep Truong Sinh sap den.", repaired)
        assertFalse(repaired.any(::isCjkForTest))
    }

    @Test
    fun mlKitResidualRepairFallsBackToPerCharacterPhonetics() {
        val repaired = repairResidualCjkForVietnamese(
            text = "Nhan vat \u53f6\u957f\u751f den.",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            translateResidual = { it },
            phoneticResidual = { residual ->
                when (residual) {
                    "\u53f6" -> "Diep"
                    "\u957f" -> "Truong"
                    "\u751f" -> "Sinh"
                    else -> ""
                }
            },
        )

        assertEquals("Nhan vat Diep Truong Sinh den.", repaired)
        assertFalse(repaired.any(::isCjkForTest))
    }

    @Test
    fun mlKitResidualRepairScansSupplementaryAndBmpHanAsOneRun() {
        val rareHan = String(Character.toChars(0x20000))
        val unresolved = rareHan + "\u53f6"
        val repaired = repairResidualCjkForVietnamese(
            text = "Nhan vat $unresolved den.",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            translateResidual = { it },
            phoneticResidual = { residual ->
                when (residual) {
                    rareHan -> "Hy"
                    "\u53f6" -> "Diep"
                    else -> ""
                }
            },
        )

        assertEquals("Nhan vat Hy Diep den.", repaired)
        assertFalse(repaired.codePoints().anyMatch(::isCjkCodePointForTest))
    }

    @Test
    fun mlKitResidualRepairUsesVisibleUnicodeLabelForUnknownCharacter() {
        val rareHan = String(Character.toChars(0x31350))

        val repaired = repairResidualCjkForVietnamese(
            text = "Name $rareHan remains",
            targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
            translateResidual = { it },
            phoneticResidual = { it },
        )

        assertEquals("Name U+31350 remains", repaired)
        assertFalse(repaired.codePoints().anyMatch(::isCjkCodePointForTest))
    }

    @Test
    fun aiTranslationRetriesCjkOutputWithNextRouteOffsetBeforeCaching() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 1
        TranslationConfig.aiMaxCharsPerChunk = 1000
        TranslationConfig.aiConcurrentChunks = 1

        val textGateway = RecordingAiGateway(
            outputs = listOf(
                refinerJson("Diep \u957f Sinh den."),
                refinerJson("Diep Truong Sinh den."),
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-retry-${System.nanoTime()}",
            name = "AI retry test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u53f6\u957f\u751f\u6765\u4e86\u3002")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Diep Truong Sinh den.", result.getOrThrow())
            assertEquals(listOf(0, 1), textGateway.requests.map { it.routeRetryOffset })
            assertEquals("test://ai-retry", textGateway.requests.first().routeSessionKey?.substringBeforeLast("-"))
            val firstPrompt = textGateway.requests.first().messages.joinToString("\n") { it.content }
            assertTrue(firstPrompt.contains("CONTEXT_PACK_JSON"))
            assertTrue(firstPrompt.contains("SEGMENTS_RAW_QT"))
            assertTrue(firstPrompt.contains("refined_segments"))
            assertFalse(firstPrompt.contains("Keep every marker exactly once"))
            assertEquals(1, cacheGateway.savedChunks.size)
            assertEquals(TranslationCache.STATUS_SUCCESS, cacheGateway.savedChunks.single().status)
            assertFalse(cacheGateway.savedChunks.single().translatedChunkContent.orEmpty().any(::isCjkForTest))
            assertEquals("Diep Truong Sinh den.", cacheGateway.writtenTranslation)
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun explicitRetranslationKeepsPreviousSuccessfulChunkVisibleWhenReplacementFails() = runBlocking {
        val source = "叶长生来了。"
        val oldTranslation = "Diep Truong Sinh da den."
        val cacheGateway = RecordingTranslationCacheGateway().apply {
            cachedChunk = TranslationCache(
                chunkIndex = 0,
                originalChunkContent = source,
                translatedChunkContent = oldTranslation,
                status = TranslationCache.STATUS_SUCCESS,
                originalContentHash = "old-hash",
                provider = TranslationConstants.PROVIDER_APP_AI,
            )
        }
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = RecordingAiGateway(emptyList()),
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(
                translationPreset().copy(
                    runtimeOptions = AiTaskRuntimeOptions(
                        maxInputChars = 1_000,
                        concurrentRequests = 1,
                        retryCount = 0,
                    )
                )
            ),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-force-cache-${System.nanoTime()}",
            name = "AI force cache test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        val progress = mutableListOf<TranslateChapterUseCase.TranslationProgress>()
        try {
            BookHelp.saveText(book, chapter, source)

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = { progress += it },
                onTranslateStarted = {},
            )

            assertTrue(result.isFailure)
            assertTrue(progress.isNotEmpty())
            assertEquals(oldTranslation, progress.last().mixedContent)
            assertTrue(cacheGateway.savedChunks.isEmpty())
            assertEquals(oldTranslation, cacheGateway.cachedChunk?.translatedChunkContent)
        } finally {
            BookHelp.delContent(book, chapter)
        }
    }

    @Test
    fun aiTranslationIgnoresStaleCachedOutputWithCjkOrLayoutMarkers() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 0
        TranslationConfig.aiMaxCharsPerChunk = 1000
        TranslationConfig.aiConcurrentChunks = 1

        val textGateway = RecordingAiGateway(
            outputs = listOf(refinerJson("Diep Truong Sinh den."))
        )
        val cacheGateway = RecordingTranslationCacheGateway().apply {
            currentTranslation = "[[P0]]\nDiep \u957f Sinh den."
        }
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-cache-${System.nanoTime()}",
            name = "AI cache test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u53f6\u957f\u751f\u6765\u4e86\u3002")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = false,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Diep Truong Sinh den.", result.getOrThrow())
            assertEquals(1, textGateway.requests.size)
            assertEquals("Diep Truong Sinh den.", cacheGateway.writtenTranslation)
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun aiTranslationIgnoresStaleCachedChunkWithLegacyContractMarkers() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 0
        TranslationConfig.aiMaxCharsPerChunk = 1000
        TranslationConfig.aiConcurrentChunks = 1

        val source = "\u53f6\u957f\u751f\u6765\u4e86\u3002"
        val contentHash = dictionaryAwareContentHash(
            originalContentHash = source.hashCode().toString(),
            provider = TranslationConstants.PROVIDER_APP_AI,
            dictionaryRevision = 0,
            quickTranslationPackVersion = "test",
        )
        val textGateway = RecordingAiGateway(
            outputs = listOf(refinerJson("Diep Truong Sinh den."))
        )
        val cacheGateway = RecordingTranslationCacheGateway().apply {
            cachedChunk = TranslationCache(
                chunkIndex = 0,
                originalChunkContent = source,
                translatedChunkContent = "[result]\nDiep Truong Sinh den.\n[dictionary]\n\u53f6\u957f\u751f -> Diep Truong Sinh",
                status = TranslationCache.STATUS_SUCCESS,
                originalContentHash = contentHash,
                provider = TranslationConstants.PROVIDER_APP_AI,
            )
        }
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-chunk-cache-${System.nanoTime()}",
            name = "AI legacy chunk cache test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, source)

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = false,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Diep Truong Sinh den.", result.getOrThrow())
            assertEquals(1, textGateway.requests.size)
            assertEquals(TranslationCache.STATUS_SUCCESS, cacheGateway.savedChunks.single().status)
            assertFalse(cacheGateway.savedChunks.single().translatedChunkContent.orEmpty().any(::isCjkForTest))
            assertEquals("Diep Truong Sinh den.", cacheGateway.writtenTranslation)
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun failedAiChapterResumesSuccessfulChunkAfterLearningDictionaryTerms() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 0
        TranslationConfig.aiMaxCharsPerChunk = 10
        TranslationConfig.aiConcurrentChunks = 1

        val textGateway = RecordingAiGateway(
            outputs = listOf(
                refinerJsonWithEntities(
                    translations = listOf("Diep Truong Sinh den."),
                    entities = listOf("\u53f6\u957f\u751f" to "Diep Truong Sinh"),
                ),
                refinerJson("\u4ecd\u7136\u662f\u4e2d\u6587\u3002"),
                refinerJson("Cua lon mo ra."),
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = RecordingDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(
                translationPreset().copy(
                    runtimeOptions = AiTaskRuntimeOptions(
                        maxInputChars = 10,
                        concurrentRequests = 1,
                        retryCount = 0,
                    )
                )
            ),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-resume-${System.nanoTime()}",
            name = "AI resume test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u53f6\u957f\u751f\u6765\u4e86\u3002\n\u5927\u95e8\u6253\u5f00\u4e86\u3002")

            val firstResult = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = false,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )
            assertTrue(firstResult.isFailure)
            assertEquals(2, textGateway.requests.size)
            assertEquals(
                listOf(DictPair("\u53f6\u957f\u751f", "Diep Truong Sinh", QuickDictionaryType.NAME)),
                dictionaryGateway.pairs,
            )

            val resumedResult = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = false,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Diep Truong Sinh den.\nCua lon mo ra.", resumedResult.getOrThrow())
            assertEquals(3, textGateway.requests.size)
            assertEquals(
                "Diep Truong Sinh den.",
                cacheGateway.savedChunks.last { it.chunkIndex == 0 }.translatedChunkContent,
            )
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun aiTranslationNormalizesFullwidthPunctuationWithoutRetrying() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 1
        TranslationConfig.aiMaxCharsPerChunk = 1000
        TranslationConfig.aiConcurrentChunks = 1

        val textGateway = RecordingAiGateway(
            outputs = listOf(
                refinerJson("Diep Truong Sinh\uff0c den\u3002"),
                refinerJson("Diep Truong Sinh den."),
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-cjk-punctuation-${System.nanoTime()}",
            name = "AI CJK punctuation test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u53f6\u957f\u751f\u6765\u4e86\u3002")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Diep Truong Sinh, den.", result.getOrThrow())
            assertEquals(listOf(0), textGateway.requests.map { it.routeRetryOffset })
            assertEquals(1, cacheGateway.savedChunks.size)
            assertEquals(TranslationCache.STATUS_SUCCESS, cacheGateway.savedChunks.single().status)
            assertEquals("Diep Truong Sinh, den.", cacheGateway.writtenTranslation)
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun aiTranslationRetriesLegacyUnframedOutputBeforeCaching() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 1
        TranslationConfig.aiMaxCharsPerChunk = 1000
        TranslationConfig.aiConcurrentChunks = 1

        val textGateway = RecordingAiGateway(
            outputs = listOf(
                "[result]\nDoan mot.\nDoan hai.",
                refinerJson("Doan mot.", "Doan hai."),
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-layout-retry-${System.nanoTime()}",
            name = "AI layout retry test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u7b2c\u4e00\u6bb5\u3002\n\u7b2c\u4e8c\u6bb5\u3002")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Doan mot.\nDoan hai.", result.getOrThrow())
            assertEquals(listOf(0, 1), textGateway.requests.map { it.routeRetryOffset })
            assertEquals(1, cacheGateway.savedChunks.size)
            assertEquals(TranslationCache.STATUS_SUCCESS, cacheGateway.savedChunks.single().status)
            assertEquals("Doan mot.\nDoan hai.", cacheGateway.writtenTranslation)
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun aiTranslationRetriesMissingJsonSegmentBeforeCaching() = runBlocking {
        val textGateway = RecordingAiGateway(
            outputs = listOf(
                """{"refined_segments":[{"id":1,"refined_translation":"Doan mot va doan hai."}]}""",
                refinerJson("Doan mot da den.", "Doan hai da den."),
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(
                translationPreset().copy(
                    runtimeOptions = AiTaskRuntimeOptions(
                        maxInputChars = 1_000,
                        concurrentRequests = 1,
                        retryCount = 1,
                    )
                )
            ),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-layout-reflow-${System.nanoTime()}",
            name = "AI layout reflow test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u7b2c\u4e00\u6bb5\u3002\n\u7b2c\u4e8c\u6bb5\u3002")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Doan mot da den.\nDoan hai da den.", result.getOrThrow())
            assertEquals(listOf(0, 1), textGateway.requests.map { it.routeRetryOffset })
        } finally {
            BookHelp.delContent(book, chapter)
        }
    }

    @Test
    fun rejectedAiAttemptCannotUpdateDictionaryBeforeProtectedLayoutPasses() = runBlocking {
        val oldRetryCount = TranslationConfig.llmRetryCount
        val oldAiMaxChars = TranslationConfig.aiMaxCharsPerChunk
        val oldAiConcurrency = TranslationConfig.aiConcurrentChunks
        TranslationConfig.llmRetryCount = 1
        TranslationConfig.aiMaxCharsPerChunk = 1000
        TranslationConfig.aiConcurrentChunks = 1

        val textGateway = RecordingAiGateway(
            outputs = listOf(
                refinerJsonWithEntities(
                    translations = listOf("Mo lien ket ngay."),
                    entities = listOf("phantom" to "Sai"),
                ),
                refinerJson("Mo __LG_KEEP_000__ ngay."),
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = RecordingDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = textGateway,
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = UnusedMlKitGateway(),
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://ai-protected-side-effect-${System.nanoTime()}",
            name = "AI protected side-effect test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "Mo https://example.test ngay.")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_APP_AI,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Mo https://example.test ngay.", result.getOrThrow())
            assertEquals(2, textGateway.requests.size)
            assertTrue(dictionaryGateway.pairs.isEmpty())
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmRetryCount = oldRetryCount
            TranslationConfig.aiMaxCharsPerChunk = oldAiMaxChars
            TranslationConfig.aiConcurrentChunks = oldAiConcurrency
        }
    }

    @Test
    fun mlKitTranslationPreservesChunkParagraphLayout() = runBlocking {
        val oldMaxChars = TranslationConfig.llmMaxCharsPerChunk
        val oldConcurrency = TranslationConfig.llmConcurrentChunks
        TranslationConfig.llmMaxCharsPerChunk = 1000
        TranslationConfig.llmConcurrentChunks = 1

        val mlKitGateway = RecordingMlKitGateway(
            mapOf(
                "\u7b2c\u4e00\u6bb5\u3002" to "Doan mot.",
                "\u7b2c\u4e8c\u6bb5\u3002" to "Doan hai.",
            )
        )
        val cacheGateway = RecordingTranslationCacheGateway()
        val dictionaryGateway = EmptyDictionaryGateway()
        val quickTranslationGateway = NoopQuickTranslationGateway()
        val quickDictionaryGateway = EmptyQuickDictionaryGateway()
        val useCase = TranslateChapterUseCase(
            aiTextGateway = RecordingAiGateway(emptyList()),
            translationCacheGateway = cacheGateway,
            dictionaryGateway = dictionaryGateway,
            aiProfileGateway = SinglePresetGateway(translationPreset()),
            quickTranslationGateway = quickTranslationGateway,
            quickDictionaryGateway = quickDictionaryGateway,
            nmtTranslationGateway = UnusedNmtGateway(),
            mlKitTranslationGateway = mlKitGateway,
            aiPromptPresetGateway = EmptyPromptPresetGateway(),
            translateDynamicUiTextUseCase = TranslateDynamicUiTextUseCase(
                translationCacheGateway = cacheGateway,
                dictionaryGateway = dictionaryGateway,
                quickTranslationGateway = quickTranslationGateway,
                quickDictionaryGateway = quickDictionaryGateway,
            ),
        )
        val book = Book(
            bookUrl = "test://mlkit-layout-${System.nanoTime()}",
            name = "ML Kit layout test",
            author = "test",
            type = BookType.text or BookType.local,
        )
        val chapter = BookChapter(
            url = "chapter-1",
            title = "Chapter 1",
            bookUrl = book.bookUrl,
            index = 0,
        )
        try {
            BookHelp.saveText(book, chapter, "\u7b2c\u4e00\u6bb5\u3002\n\u7b2c\u4e8c\u6bb5\u3002")

            val result = useCase.execute(
                book = book,
                bookChapter = chapter,
                forceRetranslate = true,
                provider = TranslationConstants.PROVIDER_ML_KIT,
                targetLanguage = TranslationConstants.TARGET_VIETNAMESE,
                onProgress = {},
                onTranslateStarted = {},
            )

            assertEquals("Doan mot.\nDoan hai.", result.getOrThrow())
            assertEquals(
                listOf("\u7b2c\u4e00\u6bb5\u3002", "\u7b2c\u4e8c\u6bb5\u3002"),
                mlKitGateway.requests,
            )
            assertEquals(listOf("zh", "zh"), mlKitGateway.sourceLanguages)
            assertEquals(TranslationCache.STATUS_SUCCESS, cacheGateway.savedChunks.single().status)
        } finally {
            BookHelp.delContent(book, chapter)
            TranslationConfig.llmMaxCharsPerChunk = oldMaxChars
            TranslationConfig.llmConcurrentChunks = oldConcurrency
        }
    }

    private fun translationPreset() = AiTaskPresetConfig(
        id = "preset_translate",
        taskType = TranslationConstants.PROVIDER_APP_AI,
        name = "Translate",
        model = AiModelConfig(
            id = "model_ai",
            provider = AiProviderConfig(
                id = "provider_ai",
                name = "AI",
                protocol = "openai_responses",
                baseUrl = "https://example.test/v1",
                apiKey = "token",
                authType = AiProviderAuthType.BEARER,
            ),
            displayName = "AI Model",
            modelId = "gpt-test",
            maxOutputTokens = 2048,
        ),
        promptTemplate = TranslationConstants.DEFAULT_PROMPT,
        params = AiGenerationParams(maxOutputTokens = 512),
    )
}

private fun refinerJson(vararg translations: String): String =
    refinerJsonWithEntities(translations = translations.toList())

private fun refinerJsonWithEntities(
    translations: List<String>,
    entities: List<Pair<String, String>> = emptyList(),
): String {
    val segments = translations.mapIndexed { index, text ->
        """{"id":${index + 1},"refined_translation":"${jsonEscape(text)}"}"""
    }.joinToString(",")
    val entityJson = entities.joinToString(",") { (raw, target) ->
        """{"raw":"${jsonEscape(raw)}","target":"${jsonEscape(target)}","type":"character","origin":"chinese","name_type":"person"}"""
    }
    return """{"refined_segments":[$segments],"new_entities":[$entityJson],"relationships":[],"grammar_notes":[]}"""
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

private class RecordingAiGateway(
    private val outputs: List<String>,
) : AiTextGateway {
    val requests = mutableListOf<AiGenerateRequest>()

    override suspend fun generate(request: AiGenerateRequest): Result<AiGenerateResponse> =
        Result.success(AiGenerateResponse(""))

    override fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        requests += request
        emit(AiStreamEvent.Content(outputs.getOrElse(requests.lastIndex) { error("unexpected request") }))
    }

    override suspend fun fetchModels(provider: AiProviderConfig): Result<List<AiAvailableModel>> =
        Result.success(emptyList())
}

private class RecordingTranslationCacheGateway : TranslationCacheGateway {
    val savedChunks = mutableListOf<TranslationCache>()
    var currentTranslation: String? = null
    var cachedChunk: TranslationCache? = null
    var writtenTranslation: String? = null

    override fun getCacheFile(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ): File = File("unused")

    override fun readCurrentTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        originalContentHash: String,
        provider: String,
    ): String? = currentTranslation

    override suspend fun readTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ): String? = writtenTranslation

    override suspend fun writeTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        content: String,
        originalContentHash: String?,
        provider: String?,
        revisionStatus: TranslationRevisionStatus,
        actor: String,
        parentRevisionId: String?,
        rawContentHash: String?,
        dictionaryRevision: String?,
        providerModelPromptRevision: String?,
    ) {
        writtenTranslation = content
    }

    override suspend fun getCurrentRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        currentRawContentHash: String?,
    ): TranslationRevision? = null

    override suspend fun readCacheIgnoringHash(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        expectedContentHash: String?,
    ): TranslationRevision? = null

    override suspend fun listProviderCaches(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
    ): List<TranslationRevision> = emptyList()

    override suspend fun getRevisionHistory(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        currentRawContentHash: String?,
    ): List<TranslationRevision> = emptyList()

    override suspend fun saveUserEdit(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        content: String,
        originalContentHash: String,
        rawContentHash: String,
        actor: String,
    ): TranslationRevision = error("Not used")

    override suspend fun finalizeChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        actor: String,
    ): TranslationRevision = error("Not used")

    override suspend fun unlockChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        originalContentHash: String,
        rawContentHash: String,
        actor: String,
    ): TranslationRevision = error("Not used")

    override suspend fun restoreRevision(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String,
        revisionId: String,
        originalContentHash: String,
        rawContentHash: String,
        actor: String,
    ): TranslationRevision = error("Not used")

    override suspend fun deleteTranslation(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ) = Unit

    override suspend fun deleteTranslationForBook(book: Book, targetLanguage: String) = Unit
    override suspend fun deleteAllTranslation() = Unit
    override fun getTranslationCacheSize(): Long = 0L
    override fun computeContentHash(content: String): String = content.hashCode().toString()

    override fun computeCacheKey(
        bookUrl: String,
        chapterIndex: Int,
        chunkIndex: Int,
        targetLanguage: String,
    ): String = "$bookUrl:$chapterIndex:$chunkIndex:$targetLanguage"

    override suspend fun getCachedChunks(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        provider: String,
    ): List<TranslationCache> = emptyList()

    override suspend fun getCachedChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        provider: String,
    ): TranslationCache? = savedChunks.lastOrNull {
        it.chunkIndex == chunkIndex && it.provider == provider
    } ?: cachedChunk?.takeIf {
        it.chunkIndex == chunkIndex && it.provider == provider
    }

    override suspend fun saveChunk(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        chunkIndex: Int,
        originalChunkContent: String,
        originalContentHash: String,
        provider: String,
        status: Int,
        translatedContent: String?,
        errorMessage: String?,
    ) {
        val chunk = TranslationCache(
            chunkIndex = chunkIndex,
            originalChunkContent = originalChunkContent,
            translatedChunkContent = translatedContent,
            status = status,
            errorMessage = errorMessage,
            originalContentHash = originalContentHash,
            provider = provider,
        )
        val existingIndex = savedChunks.indexOfFirst {
            it.chunkIndex == chunkIndex && it.provider == provider
        }
        if (existingIndex >= 0) {
            savedChunks[existingIndex] = chunk
        } else {
            savedChunks += chunk
        }
    }

    override suspend fun clearChunkCacheForChapter(
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        provider: String?,
    ) = Unit

    override suspend fun clearChunkCacheForBook(book: Book, targetLanguage: String) = Unit
    override suspend fun clearAllChunkCache() = Unit

    override fun readDynamicUiTranslation(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
    ): String? = null

    override suspend fun writeDynamicUiTranslation(
        scopeKey: String,
        originalText: String,
        targetLanguage: String,
        provider: String,
        translatedText: String,
    ) = Unit

    override suspend fun clearDynamicUiTranslations() = Unit
}

private class EmptyDictionaryGateway : DictionaryGateway {
    override fun getBookDictionaries(book: Book): BookDictionary = BookDictionary(book.bookUrl)
    override fun updateBookDic(book: Book, newPairs: List<DictPair>) = Unit
    override fun replaceBookDictionary(book: Book, pairs: List<DictPair>) = Unit
    override fun clearBookDictionary(book: Book) = Unit
}

private class RecordingDictionaryGateway : DictionaryGateway {
    var pairs: List<DictPair> = emptyList()
        private set

    override fun getBookDictionaries(book: Book): BookDictionary =
        BookDictionary(book.bookUrl, pairs)

    override fun updateBookDic(book: Book, newPairs: List<DictPair>) {
        pairs = newPairs.toList()
    }

    override fun replaceBookDictionary(book: Book, pairs: List<DictPair>) {
        this.pairs = pairs.toList()
    }

    override fun clearBookDictionary(book: Book) {
        pairs = emptyList()
    }
}

private class NoopQuickTranslationGateway : QuickTranslationGateway {
    override val packVersion: String = "test"
    override fun translate(
        text: String,
        projectTerms: List<DictPair>,
        customPhonetics: List<DictPair>,
    ): String = text

    override fun hanViet(text: String, customPhonetics: List<DictPair>): String = text
    override fun getBuiltInCatalogs(): List<QuickDictionaryCatalog> = emptyList()
    override fun searchBuiltInEntries(
        type: QuickDictionaryType,
        query: String,
        limit: Int,
        catalogId: String?,
    ): List<QuickDictionaryCatalogEntry> = emptyList()
}

private class EmptyQuickDictionaryGateway : QuickDictionaryGateway {
    override val currentRevision: Long = 0L
    override fun observeEntries(): Flow<List<QuickDictionaryEntry>> = flowOf(emptyList())
    override fun observePacks(): Flow<List<QuickDictionaryPack>> = flowOf(emptyList())
    override suspend fun getEffectiveEntries(book: Book, context: String): List<QuickDictionaryEntry> =
        emptyList()

    override suspend fun getUniverses(): List<QuickDictionaryUniverse> = emptyList()
    override suspend fun saveUniverse(universe: QuickDictionaryUniverse) = Unit
    override suspend fun save(entry: QuickDictionaryEntry) = Unit
    override suspend fun saveAll(entries: List<QuickDictionaryEntry>): Int = entries.size
    override suspend fun importPack(
        localPath: String,
        displayName: String,
        type: QuickDictionaryType,
        scope: QuickDictionaryScope,
        scopeKey: String,
        onProgress: (QuickDictionaryImportProgress) -> Unit,
    ): QuickDictionaryImportResult = error("unused")

    override suspend fun deletePack(id: String) = Unit
    override suspend fun deleteEntry(id: Long) = Unit
    override suspend fun deleteUniverse(key: String) = Unit
}

private class SinglePresetGateway(
    private val preset: AiTaskPresetConfig,
) : AiProfileGateway {
    override fun observeProviders(): Flow<List<AiProviderProfile>> = emptyFlow()
    override fun observeModels(): Flow<List<AiModelProfile>> = emptyFlow()
    override fun observePresets(): Flow<List<AiTaskPreset>> = emptyFlow()
    override suspend fun getProvider(id: String): AiProviderProfile? = null
    override suspend fun getModel(id: String): AiModelProfile? = null
    override suspend fun getModelConfig(id: String): AiModelConfig? = null
    override suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig? = preset
    override suspend fun getProviderApiKey(providerId: String): String = ""
    override suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile = error("unused")
    override suspend fun saveModel(draft: AiModelDraft): AiModelProfile = error("unused")
    override suspend fun importProviderModels(
        providerId: String,
        models: List<AiAvailableModel>,
    ): List<AiModelProfile> = error("unused")

    override suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig = error("unused")
    override suspend fun saveDefaultChatProfile(draft: AiProfileDraft): AiTaskPresetConfig = error("unused")
    override suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int,
    ): AiTaskPresetConfig = error("unused")

    override suspend fun saveTaskPreset(draft: AiTaskPresetDraft): AiTaskPresetConfig = error("unused")
    override suspend fun setDefaultTaskPreset(presetId: String): AiTaskPresetConfig = error("unused")
    override suspend fun deleteTaskPreset(presetId: String) = Unit
    override suspend fun deleteProvider(providerId: String) = Unit
    override suspend fun deleteModel(modelId: String) = Unit
}

private class EmptyPromptPresetGateway : AiPromptPresetGateway {
    override fun getEnabledByTaskType(taskType: String): List<AiPromptPreset> = emptyList()
    override suspend fun getByTaskTypePrefix(taskTypePrefix: String): List<AiPromptPreset> = emptyList()
    override fun countByTaskTypeSync(taskType: String): Int = 0
    override fun savePresetsSync(presets: List<AiPromptPreset>) = Unit
    override fun deletePresetSync(id: String) = Unit
    override suspend fun countByTaskType(taskType: String): Int = 0
    override suspend fun savePreset(preset: AiPromptPreset) = Unit
    override suspend fun savePresets(presets: List<AiPromptPreset>) = Unit
    override suspend fun deletePreset(id: String) = Unit
}

private class UnusedNmtGateway : NmtTranslationGateway {
    override suspend fun translate(
        text: String,
        dictionary: List<DictPair>,
        config: NmtDecodeConfig,
        onProgress: suspend (completedSegments: Int, totalSegments: Int, mixedText: String) -> Unit,
    ): NmtTranslationResult = error("unused")

    override suspend fun close() = Unit
}

private class RecordingMlKitGateway(
    private val translations: Map<String, String>,
) : MlKitTranslationGateway {
    val requests = mutableListOf<String>()
    val sourceLanguages = mutableListOf<String?>()

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?,
    ): String {
        requests += text
        sourceLanguages += sourceLanguage
        require('\n' !in text) { "ML Kit should receive one structural paragraph at a time" }
        return translations[text] ?: error("unexpected ML Kit input: $text")
    }

    override suspend fun getLanguageModels(): List<MlKitLanguageModel> = emptyList()
    override suspend fun downloadLanguage(languageTag: String) = Unit
    override suspend fun deleteLanguage(languageTag: String) = Unit
}

private class UnusedMlKitGateway : MlKitTranslationGateway {
    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String?,
    ): String = error("unused")

    override suspend fun getLanguageModels(): List<MlKitLanguageModel> = emptyList()
    override suspend fun downloadLanguage(languageTag: String) = Unit
    override suspend fun deleteLanguage(languageTag: String) = Unit
}

private fun isCjkForTest(char: Char): Boolean =
    char.code in 0x3000..0x303F ||
        char.code in 0x3040..0x30FF ||
        char.code in 0x3400..0x4DBF ||
        char.code in 0x4E00..0x9FFF ||
        char.code in 0xAC00..0xD7AF ||
        char.code in 0xF900..0xFAFF ||
        char.code in 0xFF01..0xFF0F ||
        char.code in 0xFF1A..0xFF20 ||
        char.code in 0xFF3B..0xFF40 ||
        char.code in 0xFF5B..0xFF65

private fun isCjkCodePointForTest(value: Int): Boolean =
    value in 0x3040..0x30FF ||
        value in 0x3400..0x4DBF ||
        value in 0x4E00..0x9FFF ||
        value in 0xAC00..0xD7AF ||
        value in 0xF900..0xFAFF ||
        value in 0x20000..0x323AF
