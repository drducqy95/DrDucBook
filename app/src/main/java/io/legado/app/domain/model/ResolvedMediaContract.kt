package io.legado.app.domain.model

import java.util.TreeMap

const val RESOLVED_MEDIA_CONTRACT_VERSION = 1

data class ResolvedMediaContract(
    val schemaVersion: Int,
    val sourceId: String,
    val contentId: String,
    val title: String,
    val variants: List<ResolvedMediaVariantContract>,
    val subtitles: List<ResolvedSubtitleTrackContract>,
    val audioTracks: List<ResolvedAudioTrackContract>,
    val resolvedAt: Long,
)

data class ResolvedMediaVariantContract(
    val id: String,
    val title: String,
    val uri: String,
    val contentKind: String,
    val protocol: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val referer: String,
    val expiresAt: Long?,
    val downloadSupported: Boolean,
    val externalPlayerRequired: Boolean,
    val durationMs: Long?,
    val drmUnsupported: Boolean,
    val downloadFileName: String?,
)

data class ResolvedSubtitleTrackContract(
    val id: String,
    val label: String,
    val language: String,
    val uri: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val isDefault: Boolean,
)

data class ResolvedAudioTrackContract(
    val id: String,
    val label: String,
    val language: String,
    val uri: String,
    val mimeType: String,
    val headers: Map<String, String>,
    val isDefault: Boolean,
)

fun ResolvedMedia.toResolvedMediaContract(
    redactSecrets: Boolean = false,
): ResolvedMediaContract = ResolvedMediaContract(
    schemaVersion = RESOLVED_MEDIA_CONTRACT_VERSION,
    sourceId = sourceId,
    contentId = contentId,
    title = title,
    variants = variants.map { it.toContract(redactSecrets) },
    subtitles = subtitles.map { it.toContract(redactSecrets) },
    audioTracks = audioTracks.map { it.toContract(redactSecrets) },
    resolvedAt = resolvedAt,
)

fun ResolvedMediaContract.toResolvedMedia(): ResolvedMedia {
    require(schemaVersion == RESOLVED_MEDIA_CONTRACT_VERSION) {
        "Unsupported ResolvedMedia contract version: $schemaVersion"
    }
    return ResolvedMedia(
        sourceId = sourceId,
        contentId = contentId,
        title = title,
        variants = variants.map(ResolvedMediaVariantContract::toDomain),
        subtitles = subtitles.map(ResolvedSubtitleTrackContract::toDomain),
        audioTracks = audioTracks.map(ResolvedAudioTrackContract::toDomain),
        resolvedAt = resolvedAt,
    )
}

fun Map<String, String>.toPersistentMediaHeaders(): Map<String, String> =
    filterKeys { !it.isSensitiveMediaHeader() }
        .stableHeaderMap()

fun Map<String, String>.toRedactedMediaHeaders(): Map<String, String> =
    mapValues { (name, value) ->
        if (name.isSensitiveMediaHeader()) REDACTED_MEDIA_SECRET else value
    }.stableHeaderMap()

fun String.redactMediaUriSecrets(): String =
    MEDIA_URI_SECRET.replace(this) { match ->
        match.groupValues[1] + REDACTED_MEDIA_SECRET
    }

private fun ResolvedMediaVariant.toContract(redactSecrets: Boolean): ResolvedMediaVariantContract {
    val contractHeaders = if (redactSecrets) {
        headers.toRedactedMediaHeaders()
    } else {
        headers.stableHeaderMap()
    }
    return ResolvedMediaVariantContract(
        id = id,
        title = title,
        uri = if (redactSecrets) uri.redactMediaUriSecrets() else uri,
        contentKind = contentKind.name,
        protocol = protocol.name,
        mimeType = mimeType,
        headers = contractHeaders,
        referer = referer,
        expiresAt = expiresAt,
        downloadSupported = downloadSupported,
        externalPlayerRequired = externalPlayerRequired,
        durationMs = durationMs,
        drmUnsupported = drmUnsupported,
        downloadFileName = downloadFileName,
    )
}

private fun ResolvedSubtitleTrack.toContract(redactSecrets: Boolean): ResolvedSubtitleTrackContract =
    ResolvedSubtitleTrackContract(
        id = id,
        label = label,
        language = language,
        uri = if (redactSecrets) uri.redactMediaUriSecrets() else uri,
        mimeType = mimeType,
        headers = if (redactSecrets) headers.toRedactedMediaHeaders() else headers.stableHeaderMap(),
        isDefault = isDefault,
    )

private fun ResolvedAudioTrack.toContract(redactSecrets: Boolean): ResolvedAudioTrackContract =
    ResolvedAudioTrackContract(
        id = id,
        label = label,
        language = language,
        uri = if (redactSecrets) uri.redactMediaUriSecrets() else uri,
        mimeType = mimeType,
        headers = if (redactSecrets) headers.toRedactedMediaHeaders() else headers.stableHeaderMap(),
        isDefault = isDefault,
    )

private fun ResolvedMediaVariantContract.toDomain(): ResolvedMediaVariant =
    ResolvedMediaVariant(
        id = id,
        title = title,
        uri = uri,
        contentKind = enumValue(contentKind, MediaContentKind.UNKNOWN),
        protocol = enumValue(protocol, MediaProtocol.UNKNOWN),
        mimeType = mimeType,
        headers = headers.stableHeaderMap(),
        referer = referer,
        expiresAt = expiresAt,
        downloadSupported = downloadSupported,
        externalPlayerRequired = externalPlayerRequired,
        durationMs = durationMs,
        drmUnsupported = drmUnsupported,
        downloadFileName = downloadFileName,
    )

private fun ResolvedSubtitleTrackContract.toDomain(): ResolvedSubtitleTrack =
    ResolvedSubtitleTrack(
        id = id,
        label = label,
        language = language,
        uri = uri,
        mimeType = mimeType,
        headers = headers.stableHeaderMap(),
        isDefault = isDefault,
    )

private fun ResolvedAudioTrackContract.toDomain(): ResolvedAudioTrack =
    ResolvedAudioTrack(
        id = id,
        label = label,
        language = language,
        uri = uri,
        mimeType = mimeType,
        headers = headers.stableHeaderMap(),
        isDefault = isDefault,
    )

private inline fun <reified T : Enum<T>> enumValue(name: String, fallback: T): T =
    runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

private fun Map<String, String>.stableHeaderMap(): Map<String, String> {
    if (isEmpty()) return emptyMap()
    return TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
        putAll(this@stableHeaderMap)
    }.toMap()
}

private fun String.isSensitiveMediaHeader(): Boolean {
    val normalized = trim().lowercase()
    return normalized in SENSITIVE_MEDIA_HEADERS ||
        normalized.endsWith("-token") ||
        normalized.endsWith("-secret") ||
        normalized.endsWith("-api-key")
}

private const val REDACTED_MEDIA_SECRET = "[REDACTED]"

private val SENSITIVE_MEDIA_HEADERS = setOf(
    "authorization",
    "proxy-authorization",
    "cookie",
    "set-cookie",
    "api-key",
    "apikey",
    "x-api-key",
    "token",
    "x-token",
    "access-token",
    "refresh-token",
)

private val MEDIA_URI_SECRET = Regex(
    "(?i)([?&](?:key|api[_-]?key|token|access[_-]?token|refresh[_-]?token|cookie|password|secret|signature|sig)=)[^&#]+"
)
