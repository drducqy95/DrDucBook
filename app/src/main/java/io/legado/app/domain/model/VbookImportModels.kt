package io.legado.app.domain.model

enum class ImportClassification {
    SINGLE_PLUGIN,
    REGISTRY,
    COMPATIBLE_ARRAY,
    INVALID_SCHEMA,
}

enum class VbookImportAction {
    INSTALL,
    UPDATE,
    SKIP_SAME,
    DOWNGRADE_WARNING,
    DUPLICATE_URL_WARNING,
}

data class VbookImportPreviewItem(
    val pluginId: String,
    val name: String,
    val author: String,
    val version: Int,
    val description: String,
    val iconUrl: String,
    val downloadUrl: String,
    val declaredKind: VbookPluginKind,
    val capabilities: Set<VbookCapability>,
    val action: VbookImportAction,
    val compatible: Boolean = true,
    val compatibilityMessage: String? = null,
)

data class VbookImportPreview(
    val classification: ImportClassification,
    val sourceLabel: String,
    val items: List<VbookImportPreviewItem>,
    val rejectedItemCount: Int = 0,
)

data class VbookImportItemResult(
    val pluginId: String,
    val name: String,
    val installed: Boolean,
    val message: String,
)

data class VbookImportReport(
    val results: List<VbookImportItemResult>,
) {
    val installedCount: Int get() = results.count { it.installed }
    val failedCount: Int get() = results.size - installedCount
}

fun inferredVbookCapabilities(kind: VbookPluginKind): Set<VbookCapability> = when (kind) {
    VbookPluginKind.TEXT -> setOf(
        VbookCapability.EXPLORE,
        VbookCapability.SEARCH,
        VbookCapability.DETAIL,
        VbookCapability.EPISODE_LIST,
        VbookCapability.TEXT_CONTENT,
    )
    VbookPluginKind.COMIC -> setOf(
        VbookCapability.EXPLORE,
        VbookCapability.SEARCH,
        VbookCapability.DETAIL,
        VbookCapability.EPISODE_LIST,
        VbookCapability.IMAGE_CONTENT,
    )
    VbookPluginKind.AUDIOBOOK -> setOf(
        VbookCapability.SEARCH,
        VbookCapability.EPISODE_LIST,
        VbookCapability.AUDIO_CONTENT,
        VbookCapability.MEDIA_TRACK,
    )
    VbookPluginKind.VIDEO -> setOf(
        VbookCapability.SEARCH,
        VbookCapability.EPISODE_LIST,
        VbookCapability.VIDEO_CONTENT,
        VbookCapability.MEDIA_TRACK,
    )
    VbookPluginKind.TTS -> setOf(
        VbookCapability.TTS_VOICE_LIST,
        VbookCapability.TTS_SYNTHESIS,
    )
    VbookPluginKind.TRANSLATOR -> setOf(VbookCapability.TEXT_CONTENT)
    VbookPluginKind.UNKNOWN -> emptySet()
}
