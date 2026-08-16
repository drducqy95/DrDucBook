package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.MlKitTranslationGateway
import io.legado.app.domain.model.BrowserPageTextNode
import io.legado.app.domain.model.BrowserPageTextTranslation
import io.legado.app.domain.model.BrowserPageTranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class TranslateBrowserPageUseCase(
    private val mlKitTranslationGateway: MlKitTranslationGateway,
) {

    suspend fun execute(
        nodes: List<BrowserPageTextNode>,
        targetLanguage: String = TARGET_LANGUAGE,
    ): BrowserPageTranslationResult = withTimeout(OVERALL_TIMEOUT_MS) {
        val bounded = boundNodes(nodes)
        if (bounded.isEmpty()) {
            return@withTimeout BrowserPageTranslationResult(emptyList(), nodes.size, 0)
        }
        val semaphore = Semaphore(MAX_CONCURRENT_TRANSLATIONS)
        val attempts = coroutineScope {
            bounded.map { node ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { translateNode(node, targetLanguage) }
                }
            }.awaitAll()
        }
        val failures = attempts.count { it.failure != null }
        val translations = attempts.mapNotNull { it.translation }
        if (translations.isEmpty() && failures > 0) {
            throw attempts.firstNotNullOf { it.failure }
        }
        BrowserPageTranslationResult(
            translations = translations,
            skippedCount = nodes.size - bounded.size,
            failedCount = failures,
        )
    }

    private suspend fun translateNode(
        node: BrowserPageTextNode,
        targetLanguage: String,
    ): TranslationAttempt {
        return try {
            val translated = withContext(Dispatchers.IO) {
                buildString {
                    for (chunk in chunkText(node.text)) {
                        append(
                            mlKitTranslationGateway.translate(
                                text = chunk,
                                targetLanguage = targetLanguage,
                            )
                        )
                    }
                }
            }.trim()
            if (translated.isBlank() || hasExcessiveCjkResidual(node.text, translated)) {
                TranslationAttempt(failure = IllegalStateException("Translation output is empty or still contains too much CJK text"))
            } else {
                TranslationAttempt(
                    translation = BrowserPageTextTranslation(
                        id = node.id,
                        originalText = node.text,
                        translatedText = translated,
                        contentHash = node.contentHash,
                    )
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            TranslationAttempt(failure = error)
        }
    }

    private fun boundNodes(nodes: List<BrowserPageTextNode>): List<BrowserPageTextNode> {
        var totalChars = 0
        return buildList {
            nodes.asSequence()
                .distinctBy { node -> node.id }
                .take(MAX_NODE_COUNT)
                .forEach { node ->
                    val text = node.text.trim()
                    if (!shouldTranslate(text) || totalChars + text.length > MAX_TOTAL_CHARS) return@forEach
                    add(node.copy(text = text))
                    totalChars += text.length
                }
        }
    }

    private fun shouldTranslate(text: String): Boolean {
        if (text.length < 2 || text.length > MAX_NODE_CHARS) return false
        if (text.startsWith("http://", true) || text.startsWith("https://", true)) return false
        return text.codePoints().anyMatch(Character::isLetter)
    }

    private fun chunkText(text: String): List<String> {
        if (text.length <= TRANSLATION_CHUNK_CHARS) return listOf(text)
        return buildList {
            var start = 0
            while (start < text.length) {
                var end = (start + TRANSLATION_CHUNK_CHARS).coerceAtMost(text.length)
                if (end < text.length) {
                    val boundary = text.lastIndexOfAny(
                        charArrayOf('\n', '.', '!', '?', ';', '。', '！', '？'),
                        startIndex = end,
                    )
                    if (boundary > start + TRANSLATION_CHUNK_CHARS / 2) end = boundary + 1
                }
                add(text.substring(start, end))
                start = end
            }
        }
    }

    private fun hasExcessiveCjkResidual(original: String, translated: String): Boolean {
        val originalCount = original.codePoints().filter(::isCjkCodePoint).count()
        if (originalCount < 2) return false
        val translatedCount = translated.codePoints().filter(::isCjkCodePoint).count()
        val allowed = maxOf(1L, originalCount / 5)
        return translatedCount > allowed
    }

    private fun isCjkCodePoint(value: Int): Boolean =
        value in 0x3400..0x4DBF ||
            value in 0x4E00..0x9FFF ||
            value in 0xF900..0xFAFF ||
            value in 0x20000..0x2EE5F ||
            value in 0x30000..0x323AF

    private data class TranslationAttempt(
        val translation: BrowserPageTextTranslation? = null,
        val failure: Throwable? = null,
    )

    private companion object {
        const val TARGET_LANGUAGE = "vi"
        const val MAX_NODE_COUNT = 120
        const val MAX_NODE_CHARS = 6_000
        const val MAX_TOTAL_CHARS = 24_000
        const val TRANSLATION_CHUNK_CHARS = 1_800
        const val MAX_CONCURRENT_TRANSLATIONS = 3
        const val OVERALL_TIMEOUT_MS = 90_000L
    }
}
