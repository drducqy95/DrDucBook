package io.legado.app.domain.model

enum class VbookPluginKind {
    TEXT,
    COMIC,
    AUDIOBOOK,
    VIDEO,
    TTS,
    TRANSLATOR,
    UNKNOWN;

    companion object {
        fun fromDeclaredType(value: String): VbookPluginKind {
            val normalized = value.trim()
                .lowercase()
                .replace('-', '_')
                .replace(' ', '_')
            return when (normalized) {
                "", "novel", "chinese_novel", "text", "book", "books", "source",
                "reading", "read", "truyen", "story", "stories", "fiction",
                "web_novel", "light_novel", "ln", "cn_novel", "novel_cn",
                "chinese", "text_novel" -> TEXT

                "comic", "comics", "manga", "manhua", "manhwa", "image", "images",
                "picture", "pictures", "truyen_tranh", "truyen_tranh_viet",
                "tranh", "comic_source", "manga_source" -> COMIC

                "audio", "audiobook", "audio_book", "audio_novel", "radio",
                "podcast", "voice" -> AUDIOBOOK

                "video", "movie", "film", "phim", "anime", "tv", "media",
                "video_source" -> VIDEO

                "tts" -> TTS
                "translate", "translator", "translation", "trans" -> TRANSLATOR
                else -> UNKNOWN
            }
        }
    }
}

enum class VbookCapability {
    EXPLORE,
    SEARCH,
    DETAIL,
    EPISODE_LIST,
    TEXT_CONTENT,
    IMAGE_CONTENT,
    AUDIO_CONTENT,
    VIDEO_CONTENT,
    MEDIA_TRACK,
    TTS_VOICE_LIST,
    TTS_SYNTHESIS,
    HLS,
    DASH,
    DIRECT_AUDIO,
    DIRECT_VIDEO,
    CUSTOM_HEADERS,
    REFERER,
    COOKIES,
    SUBTITLES,
    AUDIO_TRACKS,
    EXTERNAL_PLAYER,
    DOWNLOAD,
    EXPORT,
}

enum class VbookCapabilityEvidenceKind {
    DECLARED_TYPE,
    MANIFEST_ROLE,
    SCRIPT_HINT,
    RUNTIME_RESULT,
}

data class VbookCapabilityEvidence(
    val capability: VbookCapability,
    val kind: VbookCapabilityEvidenceKind,
    val detail: String,
)

data class VbookCapabilityProfile(
    val pluginId: String,
    val pluginVersion: Int,
    val declaredKind: VbookPluginKind,
    val scriptRoles: Set<String>,
    val capabilities: Set<VbookCapability>,
    val evidence: List<VbookCapabilityEvidence>,
    val inspectedAt: Long,
)

data class VbookRegistryMetadata(
    val id: String,
    val slug: String,
    val name: String,
    val author: String,
    val description: String,
    val version: Int,
    val generatedAt: String,
    val declaredItemCount: Int,
)

data class VbookRegistryItem(
    val pluginId: String,
    val name: String,
    val author: String,
    val downloadUrl: String,
    val version: Int,
    val source: String,
    val iconUrl: String,
    val description: String,
    val declaredType: String,
    val declaredKind: VbookPluginKind,
    val locale: String,
)

enum class VbookRegistryOrigin {
    NETWORK,
    CACHE_FRESH,
    CACHE_VALIDATED,
    CACHE_STALE_FALLBACK,
}

data class VbookRegistrySnapshot(
    val metadata: VbookRegistryMetadata,
    val items: List<VbookRegistryItem>,
    val rejectedItemCount: Int,
    val fetchedAt: Long,
    val origin: VbookRegistryOrigin,
)
