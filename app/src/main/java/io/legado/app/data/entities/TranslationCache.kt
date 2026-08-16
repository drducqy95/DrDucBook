package io.legado.app.data.entities

import androidx.annotation.Keep
import io.legado.app.domain.model.RevisionStatus

/**
 * Chunk-level translation cache record stored in .chunks.jsonl files.
 * File path already contains bookUrl/chapterIndex/targetLanguage — chunkIndex is the key.
 */
@Keep
data class TranslationCache(
    val chunkIndex: Int,
    val originalChunkContent: String,
    val translatedChunkContent: String?,
    val status: Int = STATUS_PENDING,
    val errorMessage: String? = null,
    val originalContentHash: String,
    val provider: String = ""
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_TRANSLATING = 1
        const val STATUS_SUCCESS = 2
        const val STATUS_FAILED = 3
    }

    val isSuccess: Boolean get() = status == STATUS_SUCCESS
    val isFailed: Boolean get() = status == STATUS_FAILED
    val isPending: Boolean get() = status == STATUS_PENDING
}

typealias TranslationRevisionStatus = RevisionStatus

/**
 * Metadata for the final chapter translation file.
 *
 * Keeping this next to the payload lets the reader reject a stale translation when the original
 * chapter, provider, or target language changes. Older payload-only cache files remain readable by
 * export, but are not treated as current by the reader.
 */
@Keep
data class TranslationCacheMetadata(
    val revisionId: String = "",
    val originalContentHash: String,
    val rawContentHash: String? = null,
    val provider: String,
    val targetLanguage: String,
    val dictionaryRevision: String? = null,
    val providerModelPromptRevision: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val status: TranslationRevisionStatus? = TranslationRevisionStatus.MACHINE_DRAFT,
    val createdAt: Long = updatedAt,
    val finalizedAt: Long? = null,
    val actor: String = "machine",
    val parentRevisionId: String? = null,
) {
    val normalizedStatus: TranslationRevisionStatus
        get() = status ?: TranslationRevisionStatus.MACHINE_DRAFT

    val protectsMachineDraft: Boolean
        get() = normalizedStatus == TranslationRevisionStatus.USER_EDITED ||
            normalizedStatus == TranslationRevisionStatus.FINAL ||
            normalizedStatus == TranslationRevisionStatus.STALE
}
