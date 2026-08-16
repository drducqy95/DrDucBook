package io.legado.app.help.media

import io.legado.app.domain.model.MediaContentKind
import io.legado.app.domain.model.ResolvedAudioTrack
import io.legado.app.domain.model.ResolvedMedia
import io.legado.app.domain.model.ResolvedMediaVariant
import io.legado.app.domain.model.ResolvedSubtitleTrack
import io.legado.app.help.vbook.VbookMediaParser
import io.legado.app.utils.NetworkUtils
import java.net.URI

object MediaSourceRuleResultParser {

    fun parse(
        sourceId: String,
        contentId: String,
        title: String,
        raw: String,
        defaultKind: MediaContentKind,
        fallbackHeaders: Map<String, String> = emptyMap(),
        baseUrl: String = "",
        resolvedAt: Long = System.currentTimeMillis(),
    ): ResolvedMedia {
        val text = raw.trim()
        require(text.isNotBlank()) { "Media content rule returned an empty result" }

        val jsonParsed = parseJsonStyle(
            raw = text,
            contentId = contentId,
            defaultKind = defaultKind,
            fallbackHeaders = fallbackHeaders,
            baseUrl = baseUrl,
        )
        val parsed = jsonParsed ?: parseTextStyle(
            raw = text,
            contentId = contentId,
            title = title,
            defaultKind = defaultKind,
            fallbackHeaders = fallbackHeaders,
            baseUrl = baseUrl,
        )
        require(parsed.variants.isNotEmpty()) {
            "Media content rule did not return a supported media URL"
        }
        return ResolvedMedia(
            sourceId = sourceId,
            contentId = contentId,
            title = title,
            variants = parsed.variants.distinctBy { "${it.uri}\u0000${it.title}" },
            subtitles = parsed.subtitles.distinctBy { it.uri },
            audioTracks = parsed.audioTracks.distinctBy { it.uri },
            resolvedAt = resolvedAt,
        )
    }

    private fun parseJsonStyle(
        raw: String,
        contentId: String,
        defaultKind: MediaContentKind,
        fallbackHeaders: Map<String, String>,
        baseUrl: String,
    ): ParsedMedia? {
        val candidates = VbookMediaParser.parseServers(raw, fallbackUrl = "")
            .map { candidate ->
                candidate.copy(
                    data = candidate.data.normalizeCandidateUrl(baseUrl),
                    headers = fallbackHeaders + candidate.headers,
                )
            }
            .filter { it.data.isHttpUrl() }
        if (candidates.isEmpty()) return null

        val variants = arrayListOf<ResolvedMediaVariant>()
        val subtitles = arrayListOf<ResolvedSubtitleTrack>()
        val audioTracks = arrayListOf<ResolvedAudioTrack>()
        candidates.forEachIndexed { index, candidate ->
            val parsed = VbookMediaParser.parseTrack(
                json = raw,
                candidate = candidate,
                defaultKind = defaultKind,
                idPrefix = "$contentId\u0000sourceRule\u0000$index",
            ).withFallbackHeaders(fallbackHeaders)
            variants += parsed.variants
            subtitles += parsed.subtitles
            audioTracks += parsed.audioTracks
        }
        return ParsedMedia(
            variants = variants,
            subtitles = subtitles,
            audioTracks = audioTracks,
        ).takeIf { it.variants.isNotEmpty() }
    }

    private fun parseTextStyle(
        raw: String,
        contentId: String,
        title: String,
        defaultKind: MediaContentKind,
        fallbackHeaders: Map<String, String>,
        baseUrl: String,
    ): ParsedMedia {
        val candidates = findTextCandidates(raw, baseUrl)
        val variants = candidates.mapIndexed { index, candidate ->
            MediaUriResolver.resolve(
                sourceId = "source-rule",
                contentId = "$contentId\u0000$index",
                title = title,
                uri = candidate.uri,
                defaultKind = defaultKind,
                headers = fallbackHeaders + candidate.headers,
            ).variants.single().let { variant ->
                val headers = fallbackHeaders + candidate.headers + variant.headers
                variant.copy(
                    title = candidate.title.ifBlank { variant.title },
                    headers = headers,
                    referer = headers.referer(),
                )
            }
        }
        return ParsedMedia(
            variants = variants,
            subtitles = emptyList(),
            audioTracks = emptyList(),
        )
    }

    private fun findTextCandidates(raw: String, baseUrl: String): List<TextCandidate> {
        val single = raw.lineSequence().map(String::trim).filter(String::isNotEmpty).singleOrNull()
        single?.singleCandidateUrl(baseUrl)?.let {
            return listOf(TextCandidate(title = "", uri = it, headers = emptyMap()))
        }
        val candidates = arrayListOf<TextCandidate>()
        raw.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                MEDIA_URL.findAll(line).forEach { match ->
                    val uri = match.value.normalizeCandidateUrl(baseUrl)
                    if (uri.isHttpUrl() && uri.hasMediaExtension()) {
                        candidates += TextCandidate(
                            title = line.substring(0, match.range.first).cleanLabel(),
                            uri = uri,
                            headers = emptyMap(),
                        )
                    }
                }
            }
        return candidates.distinctBy { it.uri }
    }

    private fun String.singleCandidateUrl(baseUrl: String): String? {
        if (any(Char::isWhitespace)) return null
        val uri = normalizeCandidateUrl(baseUrl)
        return uri.takeIf { it.isHttpUrl() }
    }

    private fun String.normalizeCandidateUrl(baseUrl: String): String {
        val candidate = trim().trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
        return if (baseUrl.isNotBlank()) {
            NetworkUtils.getAbsoluteURL(baseUrl, candidate)
        } else {
            candidate
        }
    }

    private fun String.cleanLabel(): String =
        trim()
            .trim('-', '+', '|', ':', '$', '#', '/', '\\')
            .trim()

    private fun String.isHttpUrl(): Boolean = runCatching {
        val parsed = URI(this)
        parsed.scheme?.lowercase() in setOf("http", "https") && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun String.hasMediaExtension(): Boolean = runCatching {
        val path = URI(this).path.orEmpty().lowercase()
        MEDIA_EXTENSIONS.any(path::endsWith)
    }.getOrDefault(false)

    private fun Map<String, String>.referer(): String =
        entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value.orEmpty()

    private fun VbookMediaParser.ParsedTrack.withFallbackHeaders(
        fallbackHeaders: Map<String, String>,
    ): VbookMediaParser.ParsedTrack = copy(
        variants = variants.map { variant ->
            val headers = fallbackHeaders + variant.headers
            variant.copy(headers = headers, referer = headers.referer())
        },
        subtitles = subtitles.map { subtitle ->
            subtitle.copy(headers = fallbackHeaders + subtitle.headers)
        },
        audioTracks = audioTracks.map { audio ->
            audio.copy(headers = fallbackHeaders + audio.headers)
        },
    )

    private data class TextCandidate(
        val title: String,
        val uri: String,
        val headers: Map<String, String>,
    )

    private data class ParsedMedia(
        val variants: List<ResolvedMediaVariant>,
        val subtitles: List<ResolvedSubtitleTrack>,
        val audioTracks: List<ResolvedAudioTrack>,
    )

    private val MEDIA_URL = Regex("""(?i)\bhttps?://[^\s"'<>]+""")
    private val MEDIA_EXTENSIONS = setOf(
        ".m3u8",
        ".mpd",
        ".mp4",
        ".webm",
        ".mkv",
        ".mp3",
        ".m4a",
        ".aac",
        ".flac",
        ".wav",
        ".ogg",
        ".opus",
    )
}
