package io.legado.app.domain.model

import androidx.annotation.Keep
import androidx.compose.runtime.Stable

@Keep
enum class RevisionStatus {
    MACHINE_DRAFT,
    USER_EDITED,
    FINAL,
    STALE,
}

@Stable
@Keep
data class TranslationRevision(
    val revisionId: String,
    val content: String,
    val status: RevisionStatus,
    val sourceStatus: RevisionStatus = status,
    val rawContentHash: String,
    val cacheContentHash: String = rawContentHash,
    val dictionaryRevision: String? = null,
    val providerModelPromptRevision: String? = null,
    val provider: String,
    val targetLanguage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val finalizedAt: Long? = null,
    val actor: String,
    val parentRevisionId: String? = null,
)

val TranslationRevision.isStale: Boolean
    get() = status == RevisionStatus.STALE

val TranslationRevision.protectsMachineTranslation: Boolean
    get() = sourceStatus == RevisionStatus.USER_EDITED || sourceStatus == RevisionStatus.FINAL
