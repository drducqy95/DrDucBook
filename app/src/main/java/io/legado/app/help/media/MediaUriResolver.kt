package io.legado.app.help.media

import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.model.ResolvedMedia
import io.legado.app.domain.model.ResolvedMediaVariant
import java.net.URI
import java.security.MessageDigest

object MediaUriResolver {

    fun resolve(
        sourceId: String,
        contentId: String,
        title: String,
        uri: String,
        defaultKind: MediaContentKind,
        headers: Map<String, String> = emptyMap(),
        resolvedAt: Long = System.currentTimeMillis(),
    ): ResolvedMedia {
        val normalizedUri = uri.trim()
        require(normalizedUri.isSupportedMediaUri()) {
            "Media URL không được hỗ trợ: $normalizedUri"
        }
        val protocol = inferProtocol(normalizedUri)
        val kind = inferKind(normalizedUri, defaultKind)
        val referer = headers.entries
            .firstOrNull { it.key.equals("referer", ignoreCase = true) }
            ?.value
            .orEmpty()
        val variant = ResolvedMediaVariant(
            id = stableId("$contentId\u0000$normalizedUri"),
            title = "Mặc định",
            uri = normalizedUri,
            contentKind = kind,
            protocol = protocol,
            mimeType = inferMime(normalizedUri, protocol, kind),
            headers = headers,
            referer = referer,
            expiresAt = expirationFromUrl(normalizedUri),
            downloadSupported = protocol in setOf(
                MediaProtocol.DIRECT,
                MediaProtocol.HLS,
                MediaProtocol.DASH,
            ),
            externalPlayerRequired = false,
        )
        return ResolvedMedia(
            sourceId = sourceId,
            contentId = contentId,
            title = title,
            variants = listOf(variant),
            subtitles = emptyList(),
            audioTracks = emptyList(),
            resolvedAt = resolvedAt,
        )
    }

    private fun String.isSupportedMediaUri(): Boolean = runCatching {
        URI(this).scheme?.lowercase() in setOf("http", "https", "file", "content")
    }.getOrDefault(false)

    private fun inferProtocol(uri: String): MediaProtocol {
        val path = uri.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> MediaProtocol.HLS
            path.endsWith(".mpd") -> MediaProtocol.DASH
            else -> MediaProtocol.DIRECT
        }
    }

    private fun inferKind(uri: String, defaultKind: MediaContentKind): MediaContentKind {
        val path = uri.substringBefore('?').lowercase()
        return when {
            AUDIO_EXTENSIONS.any(path::endsWith) -> MediaContentKind.AUDIO
            VIDEO_EXTENSIONS.any(path::endsWith) -> MediaContentKind.VIDEO
            else -> defaultKind
        }
    }

    private fun inferMime(
        uri: String,
        protocol: MediaProtocol,
        kind: MediaContentKind,
    ): String {
        val path = uri.substringBefore('?').lowercase()
        return when {
            protocol == MediaProtocol.HLS -> "application/x-mpegURL"
            protocol == MediaProtocol.DASH -> "application/dash+xml"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            kind == MediaContentKind.VIDEO -> "video/*"
            kind == MediaContentKind.AUDIO -> "audio/*"
            else -> ""
        }
    }

    private fun expirationFromUrl(uri: String): Long? = runCatching {
        URI(uri).rawQuery
            ?.split('&')
            ?.asSequence()
            ?.mapNotNull { part ->
                val key = part.substringBefore('=').lowercase()
                val value = part.substringAfter('=', "").toLongOrNull()
                value?.takeIf { key in setOf("exp", "expires", "expiry", "expires_at") }
            }
            ?.firstOrNull()
            ?.let { if (it in 1..9_999_999_999L) it * 1_000L else it }
    }.getOrNull()

    private fun stableId(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    private val AUDIO_EXTENSIONS = listOf(
        ".mp3",
        ".m4a",
        ".aac",
        ".ogg",
        ".opus",
        ".flac",
        ".wav",
    )
    private val VIDEO_EXTENSIONS = listOf(
        ".mp4",
        ".m3u8",
        ".mpd",
        ".webm",
        ".mkv",
    )
}
