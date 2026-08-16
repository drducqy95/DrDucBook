package io.legado.app.domain.model

import androidx.annotation.Keep

/**
 * A pair of original text and its translation, used for maintaining
 * consistent terminology across multiple translation chunks.
 */
@Keep
data class DictPair(
    val original: String,
    val translation: String,
    val type: QuickDictionaryType = QuickDictionaryType.VIETPHRASE,
)

fun DictPair.normalizedForRuntime(): DictPair = DictPair(
    original = runCatching { original }.getOrNull().orEmpty(),
    translation = runCatching { translation }.getOrNull().orEmpty(),
    type = runCatching { type }.getOrNull() ?: QuickDictionaryType.VIETPHRASE,
)

/**
 * Collection of dictionary pairs with metadata.
 */
@Keep
data class BookDictionary(
    val bookUrl: String,
    val pairs: List<DictPair> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A chunk of text for translation, with its index and paragraph mapping.
 */
@Keep
data class TextChunk(
    val index: Int,
    val content: String,
    val paragraphIndices: List<Int>,
    /** Exact source whitespace before the first paragraph represented by this chunk. */
    val leadingWhitespace: String = "",
    /** Exact source separators between paragraphs represented by this chunk. */
    val paragraphSeparators: List<String> = emptyList(),
    /** Exact source whitespace after the final paragraph of the complete input. */
    val trailingWhitespace: String = "",
)

/**
 * Reasons for retrying a translation request.
 * Used to analyze failures and decide appropriate retry strategies.
 */
enum class RetryReason {
    /** Network connectivity issues */
    NETWORK_ERROR,

    /** API returned rate limit error (429) */
    RATE_LIMIT,

    /** Every target in the selected route is temporarily unavailable */
    ROUTE_UNAVAILABLE,

    /** API returned server error (5xx) */
    SERVER_ERROR,

    /** API returned authentication/permission error (401, 403) */
    AUTH_ERROR,

    /** Provider/model/base URL configuration is invalid */
    CONFIG_ERROR,

    /** Account quota or billing allocation is exhausted */
    QUOTA_ERROR,

    /** Endpoint or response schema does not match the configured protocol */
    PROTOCOL_ERROR,

    /** Request timeout */
    TIMEOUT,

    /** API returned empty response */
    EMPTY_RESPONSE,

    /** Malformed response that couldn't be parsed */
    PARSE_ERROR,

    /** Unknown error that might be transient */
    UNKNOWN,

    /** No retry needed, permanent failure */
    PERMANENT_FAILURE,

    /** User or lifecycle cancelled the task */
    CANCELLED,
}
