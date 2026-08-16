package io.legado.app.help.vbook

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.MediaProtocol
import io.legado.app.domain.model.ResolvedAudioTrack
import io.legado.app.domain.model.ResolvedMediaVariant
import io.legado.app.domain.model.ResolvedSubtitleTrack
import java.net.URI
import java.security.MessageDigest

internal object VbookMediaParser {

    data class ServerCandidate(
        val title: String,
        val data: String,
        val headers: Map<String, String>,
    )

    data class ParsedTrack(
        val variants: List<ResolvedMediaVariant>,
        val subtitles: List<ResolvedSubtitleTrack>,
        val audioTracks: List<ResolvedAudioTrack>,
    )

    fun parseServers(json: String, fallbackUrl: String): List<ServerCandidate> {
        val element = json.parseJsonOrNull()
        val candidates = mutableListOf<ServerCandidate>()
        collectServerCandidates(element, candidates)
        if (candidates.isEmpty() && fallbackUrl.isHttpUrl()) {
            candidates += ServerCandidate(
                title = "Mặc định",
                data = fallbackUrl,
                headers = emptyMap(),
            )
        }
        return candidates.distinctBy { "${it.title}\u0000${it.data}" }
    }

    fun parseTrack(
        json: String,
        candidate: ServerCandidate,
        defaultKind: MediaContentKind,
        idPrefix: String,
    ): ParsedTrack {
        val element = json.parseJsonOrNull()
        val root = element?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val commonHeaders = candidate.headers + root.headers()
        val explicitType = root?.string("type").orEmpty()
        val variantElements = when {
            root == null -> listOfNotNull(element)
            root.arrayOrNull("variants") != null -> root.arrayOrNull("variants")!!.toList()
            root.arrayOrNull("sources") != null -> root.arrayOrNull("sources")!!.toList()
            root.arrayOrNull("streams") != null -> root.arrayOrNull("streams")!!.toList()
            root.get("data")?.isJsonArray == true -> root.getAsJsonArray("data").toList()
            else -> listOf(root)
        }
        val variants = variantElements.mapIndexedNotNull { index, item ->
            parseVariant(
                element = item,
                candidate = candidate,
                defaultKind = defaultKind,
                commonHeaders = commonHeaders,
                explicitType = explicitType,
                idPrefix = idPrefix,
                index = index,
            )
        }.ifEmpty {
            candidate.data.takeIf { it.isHttpUrl() }?.let { uri ->
                listOf(
                    createVariant(
                        idPrefix = idPrefix,
                        index = 0,
                        title = candidate.title,
                        uri = uri,
                        defaultKind = defaultKind,
                        explicitType = explicitType,
                        mimeType = "",
                        headers = commonHeaders,
                        expiresAt = null,
                    )
                )
            }.orEmpty()
        }.distinctBy { "${it.uri}\u0000${it.title}" }
        return ParsedTrack(
            variants = variants,
            subtitles = parseSubtitleTracks(root, commonHeaders, idPrefix),
            audioTracks = parseAudioTracks(root, commonHeaders, idPrefix),
        )
    }

    private fun collectServerCandidates(
        element: JsonElement?,
        output: MutableList<ServerCandidate>,
        inheritedTitle: String = "Mặc định",
        inheritedHeaders: Map<String, String> = emptyMap(),
    ) {
        when {
            element == null || element.isJsonNull -> Unit
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val value = element.asString.trim()
                if (value.isNotBlank()) {
                    output += ServerCandidate(inheritedTitle, value, inheritedHeaders)
                }
            }

            element.isJsonArray -> element.asJsonArray.forEach {
                collectServerCandidates(it, output, inheritedTitle, inheritedHeaders)
            }

            element.isJsonObject -> {
                val objectValue = element.asJsonObject
                if (objectValue.string("type").equals("section", ignoreCase = true)) return
                val title = objectValue.firstString("title", "name", "label", "quality")
                    .orEmpty()
                    .ifBlank { inheritedTitle }
                val headers = inheritedHeaders + objectValue.headers()
                val direct = objectValue.firstString("data", "url", "uri", "file", "src", "link")
                if (!direct.isNullOrBlank()) {
                    output += ServerCandidate(title, direct, headers)
                } else {
                    listOf("servers", "variants", "sources", "streams", "data").forEach { key ->
                        objectValue.get(key)?.let {
                            collectServerCandidates(it, output, title, headers)
                        }
                    }
                }
            }
        }
    }

    private fun parseVariant(
        element: JsonElement,
        candidate: ServerCandidate,
        defaultKind: MediaContentKind,
        commonHeaders: Map<String, String>,
        explicitType: String,
        idPrefix: String,
        index: Int,
    ): ResolvedMediaVariant? {
        val objectValue = element.takeIf(JsonElement::isJsonObject)?.asJsonObject
        val uri = when {
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
            objectValue != null -> objectValue.firstString(
                "data",
                "url",
                "uri",
                "file",
                "src",
                "link",
            )

            else -> null
        }?.trim()?.takeIf { it.isHttpUrl() } ?: return null
        val title = objectValue?.firstString("title", "name", "label", "quality", "resolution")
            .orEmpty()
            .ifBlank { candidate.title }
        val type = objectValue?.string("type").orEmpty().ifBlank { explicitType }
        val mimeType = objectValue?.firstString("mime", "mimeType", "contentType").orEmpty()
        val expiresAt = objectValue?.long("expiresAt")
            ?: objectValue?.long("expiry")
            ?: expirationFromUrl(uri)
        return createVariant(
            idPrefix = idPrefix,
            index = index,
            title = title,
            uri = uri,
            defaultKind = defaultKind,
            explicitType = type,
            mimeType = mimeType,
            headers = commonHeaders + objectValue.headers(),
            expiresAt = expiresAt,
        )
    }

    private fun createVariant(
        idPrefix: String,
        index: Int,
        title: String,
        uri: String,
        defaultKind: MediaContentKind,
        explicitType: String,
        mimeType: String,
        headers: Map<String, String>,
        expiresAt: Long?,
    ): ResolvedMediaVariant {
        val protocol = inferProtocol(uri, explicitType)
        val contentKind = inferContentKind(uri, mimeType, defaultKind)
        val normalizedMime = mimeType.ifBlank { inferMimeType(uri, protocol, contentKind) }
        val referer = headers.entries
            .firstOrNull { it.key.equals("referer", ignoreCase = true) }
            ?.value
            .orEmpty()
        return ResolvedMediaVariant(
            id = stableId("$idPrefix\u0000$index\u0000$uri"),
            title = title.ifBlank { "Mặc định" },
            uri = uri,
            contentKind = contentKind,
            protocol = protocol,
            mimeType = normalizedMime,
            headers = headers,
            referer = referer,
            expiresAt = expiresAt?.normalizeEpochMillis(),
            downloadSupported = protocol in setOf(
                MediaProtocol.DIRECT,
                MediaProtocol.HLS,
                MediaProtocol.DASH,
            ),
            externalPlayerRequired = protocol == MediaProtocol.IFRAME,
        )
    }

    private fun parseSubtitleTracks(
        root: JsonObject?,
        commonHeaders: Map<String, String>,
        idPrefix: String,
    ): List<ResolvedSubtitleTrack> {
        val array = root.firstArray("subtitles", "subtitle", "captions", "textTracks")
            ?: return emptyList()
        return array.mapIndexedNotNull { index, element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapIndexedNotNull null
            val uri = item.firstString("url", "uri", "file", "src")
                ?.takeIf { it.isHttpUrl() }
                ?: return@mapIndexedNotNull null
            ResolvedSubtitleTrack(
                id = stableId("$idPrefix\u0000subtitle\u0000$index\u0000$uri"),
                label = item.firstString("label", "title", "name").orEmpty().ifBlank { "Phụ đề" },
                language = item.firstString("language", "lang", "locale").orEmpty(),
                uri = uri,
                mimeType = item.firstString("mime", "mimeType").orEmpty()
                    .ifBlank { subtitleMime(uri) },
                headers = commonHeaders + item.headers(),
                isDefault = item.boolean("default") || item.boolean("isDefault"),
            )
        }
    }

    private fun parseAudioTracks(
        root: JsonObject?,
        commonHeaders: Map<String, String>,
        idPrefix: String,
    ): List<ResolvedAudioTrack> {
        val array = root.firstArray("audioTracks", "audios", "audio")
            ?: return emptyList()
        return array.mapIndexedNotNull { index, element ->
            val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@mapIndexedNotNull null
            val uri = item.firstString("url", "uri", "file", "src")
                ?.takeIf { it.isHttpUrl() }
                ?: return@mapIndexedNotNull null
            ResolvedAudioTrack(
                id = stableId("$idPrefix\u0000audio\u0000$index\u0000$uri"),
                label = item.firstString("label", "title", "name").orEmpty().ifBlank { "Âm thanh" },
                language = item.firstString("language", "lang", "locale").orEmpty(),
                uri = uri,
                mimeType = item.firstString("mime", "mimeType").orEmpty()
                    .ifBlank { inferMimeType(uri, MediaProtocol.DIRECT, MediaContentKind.AUDIO) },
                headers = commonHeaders + item.headers(),
                isDefault = item.boolean("default") || item.boolean("isDefault"),
            )
        }
    }

    private fun inferProtocol(uri: String, explicitType: String): MediaProtocol {
        val type = explicitType.trim().lowercase()
        val path = uri.substringBefore('?').lowercase()
        return when {
            type == "iframe" || type == "embed" -> MediaProtocol.IFRAME
            type == "hls" || path.endsWith(".m3u8") -> MediaProtocol.HLS
            type == "dash" || path.endsWith(".mpd") -> MediaProtocol.DASH
            type == "native" || path.endsWith(".mp4") || path.endsWith(".mp3") ||
                path.endsWith(".m4a") || path.endsWith(".aac") ||
                path.endsWith(".webm") || path.endsWith(".mkv") -> MediaProtocol.DIRECT

            else -> MediaProtocol.UNKNOWN
        }
    }

    private fun inferContentKind(
        uri: String,
        mimeType: String,
        defaultKind: MediaContentKind,
    ): MediaContentKind {
        val mime = mimeType.lowercase()
        val path = uri.substringBefore('?').lowercase()
        return when {
            mime.startsWith("audio/") ||
                listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav")
                    .any(path::endsWith) -> MediaContentKind.AUDIO

            mime.startsWith("video/") ||
                listOf(".mp4", ".m3u8", ".mpd", ".webm", ".mkv")
                    .any(path::endsWith) -> MediaContentKind.VIDEO

            else -> defaultKind
        }
    }

    private fun inferMimeType(
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
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            kind == MediaContentKind.VIDEO -> "video/*"
            kind == MediaContentKind.AUDIO -> "audio/*"
            else -> ""
        }
    }

    private fun subtitleMime(uri: String): String {
        val path = uri.substringBefore('?').lowercase()
        return when {
            path.endsWith(".vtt") -> "text/vtt"
            path.endsWith(".srt") -> "application/x-subrip"
            path.endsWith(".ass") || path.endsWith(".ssa") -> "text/x-ssa"
            else -> "text/plain"
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
    }.getOrNull()

    private fun Long.normalizeEpochMillis(): Long = if (this in 1..9_999_999_999L) {
        this * 1_000L
    } else {
        this
    }

    private fun JsonObject?.headers(): Map<String, String> {
        val objectValue = this ?: return emptyMap()
        val headers = objectValue.get("headers")
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: return emptyMap()
        return buildMap {
            headers.entrySet().forEach { (key, value) ->
                if (value.isJsonPrimitive) {
                    runCatching { value.asString }.getOrNull()
                        ?.takeIf(String::isNotBlank)
                        ?.let { put(key, it) }
                }
            }
        }
    }

    private fun JsonObject?.firstArray(vararg names: String): JsonArray? {
        val objectValue = this ?: return null
        return names.firstNotNullOfOrNull { objectValue.arrayOrNull(it) }
    }

    private fun JsonObject.arrayOrNull(name: String): JsonArray? =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.firstString(vararg names: String): String? =
        names.firstNotNullOfOrNull { string(it) }

    private fun JsonObject.string(name: String): String? = runCatching {
        get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
    }.getOrNull()

    private fun JsonObject.long(name: String): Long? = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asLong
    }.getOrNull()

    private fun JsonObject.boolean(name: String): Boolean = runCatching {
        get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
    }.getOrDefault(false)

    private fun String.parseJsonOrNull(): JsonElement? =
        runCatching { JsonParser.parseString(this) }.getOrNull()

    private fun String.isHttpUrl(): Boolean = runCatching {
        val parsed = URI(this)
        parsed.scheme?.lowercase() in setOf("http", "https") && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun stableId(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }
}
