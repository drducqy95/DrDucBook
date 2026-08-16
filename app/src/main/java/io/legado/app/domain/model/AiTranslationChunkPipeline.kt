package io.legado.app.domain.model

data class AiTranslationChunkContext(
    val previous: String = "",
    val next: String = "",
)

object AiTranslationChunkPlanner {

    private const val MAX_CONTEXT_CHARS = 400

    fun contextFor(
        chunks: List<TextChunk>,
        chunkIndex: Int,
        maxCharsPerChunk: Int,
        maxContextChars: Int = MAX_CONTEXT_CHARS,
    ): AiTranslationChunkContext {
        val position = chunks.indexOfFirst { it.index == chunkIndex }
        if (position < 0) return AiTranslationChunkContext()
        val contextChars = maxCharsPerChunk.coerceIn(
            1,
            maxContextChars.coerceIn(1, MAX_CONTEXT_CHARS),
        )
        return AiTranslationChunkContext(
            previous = chunks.getOrNull(position - 1)
                ?.content
                ?.takeContextSuffix(contextChars)
                .orEmpty(),
            next = chunks.getOrNull(position + 1)
                ?.content
                ?.takeContextPrefix(contextChars)
                .orEmpty(),
        )
    }

    private fun String.takeContextSuffix(maxChars: Int): String {
        if (length <= maxChars) return trim()
        val fallbackStart = length - maxChars
        val boundary = (fallbackStart until lastIndex)
            .firstOrNull { index -> isContextBoundaryAt(index) }
        return boundary
            ?.let { substring(it + 1).trim() }
            ?.takeIf(String::isNotEmpty)
            ?: takeLast(maxChars).trim()
    }

    private fun String.takeContextPrefix(maxChars: Int): String {
        if (length <= maxChars) return trim()
        val boundary = ((maxChars - 1).coerceAtMost(lastIndex) downTo 0)
            .firstOrNull { index -> isContextBoundaryAt(index) }
        return boundary
            ?.let { substring(0, it + 1).trim() }
            ?.takeIf(String::isNotEmpty)
            ?: take(maxChars).trim()
    }

    private fun String.isContextBoundaryAt(index: Int): Boolean {
        val char = this[index]
        if (char == '\u3002' || char == '\uFF01' || char == '\uFF1F') return true
        if (char == '\n' || char == '\r' || char == '。' || char == '！' || char == '？') {
            return true
        }
        return (char == '.' || char == '!' || char == '?') &&
            (index == lastIndex || this[index + 1].isWhitespace())
    }
}

object AiTranslationTokenBudget {

    private const val MIN_OUTPUT_TOKENS = 1_024
    private const val REASONING_MIN_OUTPUT_TOKENS = 4_096
    private const val STRUCTURED_MIN_OUTPUT_TOKENS = 2_048
    // Vietnamese uses spaces between syllables, but a 4x cap made short chunks wait for and
    // often produce unnecessarily verbose output. The 2x cap plus reserve is enough for the
    // measured Chinese -> Vietnamese chapters while leaving room for dictionary markers.
    private const val TOKENS_PER_SOURCE_CHAR = 2
    private const val FORMAT_AND_THINKING_RESERVE = 768
    private const val REASONING_FORMAT_AND_THINKING_RESERVE = 3_072
    private const val STRUCTURED_TOKENS_PER_SOURCE_CHAR = 4
    private const val STRUCTURED_FORMAT_RESERVE = 2_048

    /**
     * Chinese-to-Vietnamese benchmark-derived cap. A smaller user cap is respected and a known
     * provider limit is always treated as a hard ceiling.
     */
    fun forSourceChars(
        sourceChars: Int,
        configuredLimit: Int?,
        providerLimit: Int,
        reasoningModel: Boolean = false,
        structuredJson: Boolean = false,
    ): Int {
        val minimum = when {
            reasoningModel -> REASONING_MIN_OUTPUT_TOKENS
            structuredJson -> STRUCTURED_MIN_OUTPUT_TOKENS
            else -> MIN_OUTPUT_TOKENS
        }
        val reserve = when {
            reasoningModel -> REASONING_FORMAT_AND_THINKING_RESERVE
            structuredJson -> STRUCTURED_FORMAT_RESERVE
            else -> FORMAT_AND_THINKING_RESERVE
        }
        val tokensPerSourceChar = if (structuredJson) {
            STRUCTURED_TOKENS_PER_SOURCE_CHAR
        } else {
            TOKENS_PER_SOURCE_CHAR
        }
        val adaptive = (sourceChars.coerceAtLeast(0) * tokensPerSourceChar + reserve)
            .coerceAtLeast(minimum)
        val configuredCeiling = configuredLimit
            ?.takeIf { it > 0 }
            // A lower cap makes forced-reasoning models return reasoning_content with an empty
            // final answer. Provider limits remain hard ceilings below.
            ?.let { if (reasoningModel) it.coerceAtLeast(minimum) else it }
        return listOfNotNull(
            adaptive,
            configuredCeiling,
            providerLimit.takeIf { it > 0 },
        ).min()
    }

    /** Reconciles the preset budget with the model that a fallback route actually selected. */
    fun forRouteTarget(
        requestedLimit: Int?,
        providerLimit: Int,
        reasoningModel: Boolean,
    ): Int? {
        val requested = requestedLimit?.takeIf { it > 0 }
        val adjusted = when {
            reasoningModel -> (requested ?: 0).coerceAtLeast(REASONING_MIN_OUTPUT_TOKENS)
            requested != null -> requested
            else -> return null
        }
        return providerLimit.takeIf { it > 0 }
            ?.let(adjusted::coerceAtMost)
            ?: adjusted
    }
}

/**
 * Normalizes provider content events to one completed response.
 *
 * Most adapters emit deltas, but a few streaming bridges emit the complete accumulated text on
 * every event. The prefix check handles the latter without treating an ordinary delta that merely
 * repeats the previous suffix as a duplicate.
 */
class AiTranslationStreamAccumulator {

    private val value = StringBuilder()

    fun append(fragment: String) {
        if (fragment.isEmpty()) return
        val current = value.toString()
        when {
            current.isEmpty() -> value.append(fragment)
            fragment == current -> Unit
            fragment.startsWith(current) -> {
                value.clear()
                value.append(fragment)
            }
            else -> value.append(fragment)
        }
    }

    override fun toString(): String = value.toString()
}
